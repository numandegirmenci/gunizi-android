package com.numandegirmenci.belgeizi.engine

import com.numandegirmenci.belgeizi.data.DocumentPage
import com.numandegirmenci.belgeizi.data.ScanDocument
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Lossless-to-source JPEG PDF exporter. JPEG bytes are copied to the PDF
 * unchanged (DCTDecode); there is no Android bitmap decode/re-encode step.
 * Existing OCR is encoded as invisible, selectable text with a per-page
 * ToUnicode CMap so Turkish letters survive text extraction.
 *
 * Pages with changes to visible text, stamps, signatures or non-JPEG working
 * images use the original PdfDocument renderer instead.
 */
object JpegPdfFastWriter {
    private data class JpegInfo(val width: Int, val height: Int, val channels: Int)
    private data class Prepared(
        val page: DocumentPage,
        val imageFile: File,
        val channels: Int,
        val glyphs: LinkedHashMap<Char, Int>,
        val contents: ByteArray,
        val unicodeCMap: ByteArray,
        val pageWidth: Int,
        val pageHeight: Int
    )

    fun canExport(doc: ScanDocument): Boolean =
        doc.pages.isNotEmpty() && doc.pages.all { prepare(it) != null }

    /**
     * The caller should invoke this on Dispatchers.IO, never the main thread.
     * Returns false without writing when the fast path cannot preserve all
     * elements; caller must then use the normal PdfDocument implementation.
     */
    fun writeIfSupported(doc: ScanDocument, stream: OutputStream): Boolean {
        if (doc.pages.isEmpty()) return false
        val pages = doc.pages.map { prepare(it) ?: return false }
        write(pages, stream)
        return true
    }

    private fun prepare(page: DocumentPage): Prepared? {
        if (page.marks.isNotEmpty() || page.ocr.any { it.edited }) return null
        val file = File(page.path)
        if (!file.isFile || file.length() < 100L) return null
        val jpg = jpegInfo(file) ?: return null
        if (jpg.width != page.width || jpg.height != page.height) return null
        val glyphs = LinkedHashMap<Char, Int>()
        for (line in page.ocr) for (char in line.text) {
            val c = if (char == '\n' || char == '\r' || char == '\t') ' ' else char
            if (Character.isSurrogate(c) || c.code < 32 || c.code == 127) return null
            if (!glyphs.containsKey(c)) {
                if (glyphs.size >= 220) return null
                glyphs[c] = glyphs.size + 33
            }
        }
        val portrait = page.height >= page.width
        val pageWidth = if (portrait) 595 else 842
        val pageHeight = if (portrait) 842 else 595
        return Prepared(page, file, jpg.channels, glyphs,
            content(page, glyphs, pageWidth, pageHeight),
            cmap(glyphs), pageWidth, pageHeight)
    }

    private fun content(
        page: DocumentPage, glyphs: Map<Char, Int>, pageW: Int, pageH: Int
    ): ByteArray {
        val fit = PdfLayoutMath.fit(pageW, pageH, page.width, page.height)
        val imageBottom = pageH - fit.top - fit.height
        val body = StringBuilder(1024 + page.ocr.size * 115)
        body.append("q\n")
            .append(f(fit.width)).append(" 0 0 ").append(f(fit.height))
            .append(' ').append(f(fit.left)).append(' ').append(f(imageBottom))
            .append(" cm\n/Im0 Do\nQ\n")
        for (line in page.ocr) {
            val text = line.text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
            if (text.isBlank()) continue
            val boxW = line.w * fit.width
            val boxH = line.h * fit.height
            if (boxW < 1f || boxH < 1f) continue
            val x = fit.left + line.x * fit.width
            val y = pageH - (fit.top + (line.y + line.h) * fit.height) + boxH * 0.13f
            val fontSize = (boxH * 0.82f).coerceIn(3.5f, 55f)
            val estimatedWidth = text.length * fontSize * 0.52f
            val horizontalScale = (100f * boxW / estimatedWidth.coerceAtLeast(1f))
                .coerceIn(20f, 180f)
            val hex = buildString(text.length * 2) {
                for (c in text) append(glyphs.getValue(c).toString(16).padStart(2, '0'))
            }
            // Tr 3 is invisible text, preserving exactly the JPEG appearance.
            body.append("BT\n/F0 ").append(f(fontSize)).append(" Tf\n3 Tr\n")
                .append(f(horizontalScale)).append(" Tz\n1 0 0 1 ")
                .append(f(x)).append(' ').append(f(y)).append(" Tm\n<")
                .append(hex).append("> Tj\nET\n")
        }
        return body.toString().toByteArray(StandardCharsets.ISO_8859_1)
    }

    private fun cmap(glyphs: Map<Char, Int>): ByteArray {
        val b = StringBuilder(2048 + glyphs.size * 26)
        b.append("/CIDInit /ProcSet findresource begin\n12 dict begin\n")
            .append("begincmap\n/CIDSystemInfo << /Registry (Adobe) ")
            .append("/Ordering (UCS) /Supplement 0 >> def\n")
            .append("/CMapName /BelgeIzi-Identity def\n/CMapType 2 def\n")
            .append("1 begincodespacerange\n<00> <FF>\nendcodespacerange\n")
        if (glyphs.isNotEmpty()) {
            b.append(glyphs.size).append(" beginbfchar\n")
            for ((c, n) in glyphs) {
                b.append('<').append(n.toString(16).padStart(2, '0')).append("> <")
                    .append(c.code.toString(16).padStart(4, '0')).append(">\n")
            }
            b.append("endbfchar\n")
        }
        b.append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
        return b.toString().toByteArray(StandardCharsets.ISO_8859_1)
    }

    private fun write(pages: List<Prepared>, output: OutputStream) {
        val sink = CountingOutput(BufferedOutputStream(output, 256 * 1024))
        val offsets = LongArray(2 + pages.size * 5 + 1)
        fun ascii(value: String) = sink.write(value.toByteArray(StandardCharsets.ISO_8859_1))
        fun obj(n: Int, body: () -> Unit) {
            offsets[n] = sink.count
            ascii("$n 0 obj\n")
            body()
            ascii("\nendobj\n")
        }
        ascii("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n")
        obj(1) { ascii("<< /Type /Catalog /Pages 2 0 R >>") }
        obj(2) {
            ascii("<< /Type /Pages /Count ${pages.size} /Kids [")
            pages.indices.forEach { i -> ascii("${3 + 5 * i} 0 R ") }
            ascii("] >>")
        }
        pages.forEachIndexed { index, item ->
            val n = 3 + 5 * index
            obj(n) {
                ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${item.pageWidth} ${item.pageHeight}] ")
                ascii("/Resources << /ProcSet [/PDF /Text /ImageC] ")
                ascii("/XObject << /Im0 ${n + 2} 0 R >> /Font << /F0 ${n + 3} 0 R >> >> ")
                ascii("/Contents ${n + 1} 0 R >>")
            }
            obj(n + 1) {
                ascii("<< /Length ${item.contents.size} >>\nstream\n")
                sink.write(item.contents)
                ascii("\nendstream")
            }
            obj(n + 2) {
                val colorSpace = if (item.channels == 1) "DeviceGray" else "DeviceRGB"
                ascii("<< /Type /XObject /Subtype /Image /Width ${item.page.width} ")
                ascii("/Height ${item.page.height} /ColorSpace /$colorSpace ")
                ascii("/BitsPerComponent 8 /Filter /DCTDecode /Length ${item.imageFile.length()} >>\nstream\n")
                BufferedInputStream(item.imageFile.inputStream(), 256 * 1024).use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                    }
                }
                ascii("\nendstream")
            }
            obj(n + 3) {
                ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica ")
                ascii("/Encoding /WinAnsiEncoding /ToUnicode ${n + 4} 0 R >>")
            }
            obj(n + 4) {
                ascii("<< /Length ${item.unicodeCMap.size} >>\nstream\n")
                sink.write(item.unicodeCMap)
                ascii("\nendstream")
            }
        }
        val xref = sink.count
        ascii("xref\n0 ${offsets.size}\n0000000000 65535 f \n")
        for (i in 1 until offsets.size) {
            ascii(offsets[i].toString().padStart(10, '0') + " 00000 n \n")
        }
        ascii("trailer\n<< /Size ${offsets.size} /Root 1 0 R >>\n")
        ascii("startxref\n$xref\n%%EOF\n")
        sink.flush()
    }

    private fun f(value: Float): String = String.format(Locale.US, "%.3f", value)

    /**
     * Accept baseline / progressive 8-bit grayscale or 3-channel JPEG only.
     * Skip exotic CMYK, malformed and unsupported JPEGs rather than risk output.
     */
    private fun jpegInfo(file: File): JpegInfo? {
        return runCatching {
        BufferedInputStream(file.inputStream()).use { input ->
            fun readU16(): Int {
                val hi = input.read()
                val lo = input.read()
                if (hi < 0 || lo < 0) throw java.io.EOFException()
                return (hi shl 8) or lo
            }
            if (readU16() != 0xffd8) return null
            var markers = 0
            while (markers++ < 256) {
                if (input.read() != 0xff) return null
                var code = input.read()
                while (code == 0xff) code = input.read()
                if (code < 0 || code == 0xd9 || code == 0xda) return null
                if (code == 0x01 || code in 0xd0..0xd7) continue
                val length = readU16()
                if (length < 2 || length > 65535) return null
                if (code in listOf(0xc0, 0xc1, 0xc2)) {
                    val depth = input.read()
                    val height = readU16()
                    val width = readU16()
                    val channels = input.read()
                    if (depth == 8 && width > 0 && height > 0 &&
                        channels in listOf(1, 3)) return JpegInfo(width, height, channels)
                    return null
                }
                var remaining = length - 2L
                while (remaining > 0L) {
                    val skip = input.skip(remaining)
                    if (skip <= 0) {
                        if (input.read() < 0) return null
                        remaining--
                    } else remaining -= skip
                }
            }
            null
        }
        }.getOrNull()
    }

    private class CountingOutput(private val target: OutputStream) : OutputStream() {
        var count: Long = 0
            private set
        override fun write(value: Int) { target.write(value); count++ }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            target.write(bytes, offset, length); count += length
        }
        override fun flush() = target.flush()
    }
}

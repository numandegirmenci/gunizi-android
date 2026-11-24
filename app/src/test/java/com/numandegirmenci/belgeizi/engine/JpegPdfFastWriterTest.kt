package com.numandegirmenci.belgeizi.engine

import com.numandegirmenci.belgeizi.data.DocumentPage
import com.numandegirmenci.belgeizi.data.MarkType
import com.numandegirmenci.belgeizi.data.OcrLine
import com.numandegirmenci.belgeizi.data.PageMark
import com.numandegirmenci.belgeizi.data.ScanDocument
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

class JpegPdfFastWriterTest {
    private fun fixture(format: String = "jpg"): Pair<File, DocumentPage> {
        val bitmap = BufferedImage(80, 120, BufferedImage.TYPE_INT_RGB)
        val graphics = bitmap.createGraphics()
        try {
            graphics.color = java.awt.Color.WHITE
            graphics.fillRect(0, 0, 80, 120)
            graphics.color = java.awt.Color.BLACK
            graphics.drawString("Islem", 10, 30)
        } finally { graphics.dispose() }
        val file = File.createTempFile("belgeizi_pdf_fixture", ".$format")
        file.deleteOnExit()
        check(ImageIO.write(bitmap, format, file))
        return file to DocumentPage(file.absolutePath, 80, 120)
    }

    @Test fun jpegIsEmbeddedWithoutTouchingPixelsAndTurkishOcrRemainsSearchable() {
        val (file, blank) = fixture()
        val page = blank.copy(ocr = listOf(
            OcrLine(original = "İşlem ğüşöçı", x = 0.10f, y = 0.18f,
                w = 0.75f, h = 0.10f, confidence = 0.95f)
        ))
        val doc = ScanDocument(title = "QA", pages = listOf(page))
        assertTrue(JpegPdfFastWriter.canExport(doc))
        val out = ByteArrayOutputStream()
        assertTrue(JpegPdfFastWriter.writeIfSupported(doc, out))
        val result = out.toByteArray()
        val pdfText = String(result, StandardCharsets.ISO_8859_1)
        assertTrue(pdfText.startsWith("%PDF-1.4"))
        assertTrue(pdfText.contains("/Filter /DCTDecode"))
        assertTrue(pdfText.contains("/ToUnicode"))
        assertTrue(pdfText.contains("<0130>")) // dotted Turkish I
        assertTrue(pdfText.contains("<011f>")) // g-breve
        assertTrue(pdfText.contains("<015f>")) // s-cedilla
        assertTrue(pdfText.contains("3 Tr")) // invisible OCR, original photo appearance
        assertTrue(hasSubsequence(result, file.readBytes()))
        // Object offsets must target the exact object identifier.
        val startIndex = pdfText.lastIndexOf("startxref\n")
        val offset = pdfText.substring(startIndex + 10).substringBefore('\n').toInt()
        assertTrue(pdfText.substring(offset).startsWith("xref\n"))
        val entries = pdfText.substring(offset).lineSequence().drop(3).take(7).toList()
        assertEquals(7, entries.size)
        entries.forEachIndexed { i, entry ->
            val at = entry.substring(0, 10).trim().toInt()
            assertTrue("Object ${i + 1}", pdfText.substring(at).startsWith("${i + 1} 0 obj"))
        }
    }

    @Test fun pdfWithEditedVisibleTextOrSignaturesNeverUsesFastMode() {
        val (_, page) = fixture()
        val edited = page.copy(ocr = listOf(
            OcrLine(original = "Eski", text = "Yeni", x = .1f, y = .2f, w = .5f, h = .1f)
        ))
        assertFalse(JpegPdfFastWriter.canExport(ScanDocument(title = "QA", pages = listOf(edited))))
        val stamped = page.copy(marks = listOf(PageMark(type = MarkType.TEXT_STAMP, text = "ONAY")))
        assertFalse(JpegPdfFastWriter.canExport(ScanDocument(title = "QA", pages = listOf(stamped))))
        val (_, png) = fixture("png")
        assertFalse(JpegPdfFastWriter.canExport(ScanDocument(title = "QA", pages = listOf(png))))
    }

    private fun hasSubsequence(source: ByteArray, part: ByteArray): Boolean {
        if (part.isEmpty()) return true
        for (start in 0..(source.size - part.size)) {
            var same = true
            for (i in part.indices) {
                if (source[start + i] != part[i]) { same = false; break }
            }
            if (same) return true
        }
        return false
    }
}

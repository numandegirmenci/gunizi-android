package com.numandegirmenci.belgeizi.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.numandegirmenci.belgeizi.engine.ImageEnhancer
import com.numandegirmenci.belgeizi.engine.ScanPipelinePolicy
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class DocumentStore(private val context: Context) {
    private val base = File(context.filesDir, "scan_documents").apply { mkdirs() }
    private val assets = File(context.filesDir, "marks").apply { mkdirs() }

    fun all(): List<ScanDocument> = base.listFiles()?.mapNotNull { directory ->
        runCatching {
            val metadata = File(directory, "document.json")
            if (metadata.exists()) fromJson(JSONObject(metadata.readText(Charsets.UTF_8))) else null
        }.getOrNull()
    }?.sortedByDescending { it.createdAt } ?: emptyList()

    fun save(doc: ScanDocument) {
        val folder = File(base, doc.id).apply { mkdirs() }
        val tmp = File(folder, "document.tmp")
        tmp.writeText(toJson(doc).toString(2), Charsets.UTF_8)
        val destination = File(folder, "document.json")
        if (!tmp.renameTo(destination)) {
            tmp.copyTo(destination, overwrite = true)
            tmp.delete()
        }
    }

    fun delete(doc: ScanDocument): Boolean {
        // Do not leave private signature/stamp assets behind when deleting a document.
        val usedByOthers = all().filterNot { it.id == doc.id }
            .flatMap { it.pages }.flatMap { it.marks }.mapNotNull { it.assetPath }.toSet()
        val deleted = File(base, doc.id).deleteRecursively()
        if (deleted) doc.pages.flatMap { it.marks }.mapNotNull { it.assetPath }
            .filterNot { it in usedByOthers }.forEach { path ->
                val asset = File(path)
                if (asset.parentFile?.canonicalPath == assets.canonicalPath) asset.delete()
            }
        return deleted
    }

    fun loadBitmap(page: DocumentPage): Bitmap = BitmapFactory.decodeFile(page.path)
        ?: throw IOException("Belgenin görüntüsü açılamadı.")

    /** Imports content:// documents, including content streamed from cloud providers. */
    fun import(uris: List<Uri>, onProgress: (String) -> Unit = {}): ScanDocument {
        require(uris.isNotEmpty()) { "İçe aktarılacak dosya seçilmedi." }
        val title = displayName(uris.first()).substringBeforeLast('.').ifBlank { "Yeni belge" }
        val doc = ScanDocument(title = title)
        val folder = File(base, doc.id).apply { mkdirs() }
        val pages = mutableListOf<DocumentPage>()
        try {
            uris.forEachIndexed { i, uri ->
                onProgress("Dosya ${i + 1}/${uris.size} aktarılıyor")
                val mime = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
                val name = displayName(uri).lowercase(Locale.ROOT)
                if (mime == "application/pdf" || name.endsWith(".pdf")) {
                    importPdf(uri, folder, pages)
                } else {
                    val bitmap = decodeScaled(uri, 3400)
                    try { pages += persistBitmap(bitmap, folder, pages.size, originalUri = uri) }
                    finally { bitmap.recycle() }
                }
            }
            require(pages.isNotEmpty()) { "Dosyadan sayfa okunamadı." }
            return doc.copy(pages = pages).also(::save)
        } catch (e: Exception) {
            folder.deleteRecursively()
            throw e
        }
    }

    private fun importPdf(uri: Uri, folder: File, target: MutableList<DocumentPage>) {
        val temp = File.createTempFile("belge_", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            } ?: throw IOException("PDF dosyasına erişilemedi.")
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount > 40) {
                        throw IOException("Bir PDF en fazla 40 sayfa olabilir (bu sürüm).")
                    }
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { page ->
                            val scale = 3000f / max(page.width, page.height)
                            val width = max(1, (page.width * scale).roundToInt())
                            val height = max(1, (page.height * scale).roundToInt())
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                target += persistBitmap(bitmap, folder, target.size)
                            } finally { bitmap.recycle() }
                        }
                    }
                }
            }
        } finally { temp.delete() }
    }

    private fun persistBitmap(
        bitmap: Bitmap, folder: File, index: Int, originalUri: Uri? = null
    ): DocumentPage {
        val basename = "page_${(index + 1).toString().padStart(3, '0')}"
        val original = File(folder, "${basename}_original.source")
        val output = File(folder, "${basename}.jpg")
        val fullSize = if (originalUri == null) {
            bitmap.width to bitmap.height
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(originalUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                bounds.outWidth to bounds.outHeight
            } else bitmap.width to bitmap.height
        }
        if (originalUri != null) {
            context.contentResolver.openInputStream(originalUri)?.use { input ->
                FileOutputStream(original).use { dest -> input.copyTo(dest) }
            } ?: throw IOException("Orijinal fotoğraf kopyalanamadı.")
            require(original.length() > 0L) { "Orijinal fotoğraf boş." }
        } else {
            FileOutputStream(original).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 96, stream)) {
                    "Orijinal belge kaydedilemedi."
                }
            }
        }
        val enhanced = ImageEnhancer.apply(bitmap, "AUTO")
        try {
            FileOutputStream(output).use { stream ->
                check(enhanced.compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
                    "Belge geliştirilmiş görüntüsü kaydedilemedi."
                }
            }
        } finally { if (enhanced !== bitmap) enhanced.recycle() }
        return DocumentPage(output.absolutePath, bitmap.width, bitmap.height,
            sourcePath = original.absolutePath,
            originalWidth = fullSize.first, originalHeight = fullSize.second,
            enhanceMode = "AUTO")
    }

    /** Rebuilds the visible scan from its source rather than stacking filters. */
    fun reprocessPage(page: DocumentPage, mode: String): DocumentPage {
        require(ScanPipelinePolicy.validEnhancementMode(mode)) { "Geçersiz görüntü modu." }
        val sourceFile = page.sourcePath?.let(::File)?.takeIf { it.isFile } ?: run {
            val file = File(page.path)
            val backup = File(file.parentFile, file.nameWithoutExtension + "_original.jpg")
            if (!backup.exists()) file.copyTo(backup)
            backup
        }
        // A full 200 MP Bitmap would exhaust the heap; only decode the work preview.
        val src = decodeScaled(Uri.fromFile(sourceFile), 3400)
        try {
            val processed = ImageEnhancer.apply(src, mode)
            try {
                val dst = File(page.path)
                val temporary = File(dst.parentFile, dst.nameWithoutExtension + "_new.jpg")
                FileOutputStream(temporary).use { out ->
                    check(processed.compress(Bitmap.CompressFormat.JPEG,
                        ScanPipelinePolicy.jpegQuality(mode), out))
                }
                if (!temporary.renameTo(dst)) {
                    temporary.copyTo(dst, overwrite = true)
                    temporary.delete()
                }
            } finally { if (processed !== src) processed.recycle() }
        } finally { src.recycle() }
        val hasEdits = page.ocr.any { it.edited }
        return page.copy(sourcePath = sourceFile.absolutePath, enhanceMode = mode,
            ocr = if (hasEdits) page.ocr else emptyList(),
            ocrDone = hasEdits && page.ocrDone,
            ocrVersion = if (hasEdits) page.ocrVersion else 0)
    }

    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap {
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val source = resolver.openInputStream(uri) ?: throw IOException("Görüntü okunamadı.")
        source.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw IOException("Bu görüntü biçimi desteklenmiyor.")
        }
        val sample = BitmapFactory.Options().apply {
            var scale = 1
            while (scale < 128 && max(options.outWidth, options.outHeight) / (scale * 2) >= maxSide) {
                scale *= 2
            }
            inSampleSize = scale
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, sample) }
            ?: throw IOException("Görüntü okunamadı.")
        val factor = maxSide.toFloat() / max(bitmap.width, bitmap.height)
        if (factor < 1f) {
            val scaled = Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * factor).roundToInt().coerceAtLeast(1),
                (bitmap.height * factor).roundToInt().coerceAtLeast(1), true
            )
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (rotation != 0f) {
            val transformed = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height,
                Matrix().apply { postRotate(rotation) }, true
            )
            if (transformed !== bitmap) bitmap.recycle()
            bitmap = transformed
        }
        return bitmap
    }

    private fun displayName(uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull().orEmpty().ifBlank { "Yeni belge" }
    }

    /** Export full-resolution source without decoding a 200 MP Bitmap in RAM. */
    fun originalFormat(page: DocumentPage): Pair<String, String> {
        val source = page.sourcePath?.let(::File)?.takeIf { it.isFile }
            ?: File(page.path)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, options)
        return when (options.outMimeType?.lowercase(Locale.ROOT)) {
            "image/jpeg" -> "image/jpeg" to "jpg"
            "image/png" -> "image/png" to "png"
            "image/webp" -> "image/webp" to "webp"
            "image/heif", "image/heic" -> "image/heic" to "heic"
            else -> "application/octet-stream" to "img"
        }
    }

    fun exportOriginal(page: DocumentPage, destination: Uri) {
        val source = page.sourcePath?.let(::File)?.takeIf { it.isFile }
            ?: File(page.path)
        context.contentResolver.openOutputStream(destination)?.use { out ->
            source.inputStream().use { input -> input.copyTo(out) }
        } ?: throw IOException("Orijinal görüntü kaydedilemedi.")
    }

    fun copyMarkImage(uri: Uri): String {
        val dest = File(assets, "stamp_${newId()}.png")
        val bitmap = decodeScaled(uri, 1600)
        try {
            dest.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException("Kaşe kaydedilemedi.")
                }
            }
        } finally { bitmap.recycle() }
        return dest.absolutePath
    }

    fun storeSignature(bitmap: Bitmap): String {
        val file = File(assets, "signature_${newId()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.absolutePath
    }

    private fun toJson(d: ScanDocument): JSONObject = JSONObject().apply {
        put("schema", 1); put("id", d.id); put("title", d.title); put("createdAt", d.createdAt)
        put("pages", JSONArray().apply { d.pages.forEach { p ->
            put(JSONObject().apply {
                put("path", p.path); put("width", p.width); put("height", p.height)
                put("ocrDone", p.ocrDone); put("ocrVersion", p.ocrVersion)
                put("sourcePath", p.sourcePath); put("enhanceMode", p.enhanceMode)
                put("originalWidth", p.originalWidth); put("originalHeight", p.originalHeight)
                put("ocr", JSONArray().apply { p.ocr.forEach { l -> put(JSONObject().apply {
                    put("id", l.id); put("original", l.original); put("text", l.text)
                    put("x", l.x.toDouble()); put("y", l.y.toDouble())
                    put("w", l.w.toDouble()); put("h", l.h.toDouble())
                    put("confidence", l.confidence.toDouble())
                }) } })
                put("marks", JSONArray().apply { p.marks.forEach { m -> put(JSONObject().apply {
                    put("id", m.id); put("type", m.type.name)
                    put("assetPath", m.assetPath); put("text", m.text)
                    put("x", m.x.toDouble()); put("y", m.y.toDouble())
                    put("w", m.w.toDouble()); put("h", m.h.toDouble())
                }) } })
            })
        } })
    }

    private fun fromJson(j: JSONObject): ScanDocument = ScanDocument(
        id = j.getString("id"), title = j.optString("title", "Yeni belge"),
        createdAt = j.optLong("createdAt", 0),
        pages = j.getJSONArray("pages").let { arr -> (0 until arr.length()).map { n ->
            val p = arr.getJSONObject(n)
            DocumentPage(
                path = p.getString("path"), width = p.getInt("width"), height = p.getInt("height"),
                ocrDone = p.optBoolean("ocrDone"),
                ocrVersion = p.optInt("ocrVersion", 0),
                sourcePath = p.optString("sourcePath").takeIf { it.isNotEmpty() && it != "null" },
                originalWidth = p.optInt("originalWidth", 0),
                originalHeight = p.optInt("originalHeight", 0),
                enhanceMode = p.optString("enhanceMode", "ORIGINAL"),
                ocr = p.optJSONArray("ocr")?.let { a -> (0 until a.length()).map { i ->
                    val l = a.getJSONObject(i)
                    OcrLine(id = l.getString("id"), original = l.getString("original"),
                        text = l.getString("text"), x = l.getDouble("x").toFloat(),
                        y = l.getDouble("y").toFloat(), w = l.getDouble("w").toFloat(),
                        h = l.getDouble("h").toFloat(),
                        confidence = l.optDouble("confidence", 0.0).toFloat())
                } } ?: emptyList(),
                marks = p.optJSONArray("marks")?.let { a -> (0 until a.length()).map { i ->
                    val m = a.getJSONObject(i)
                    PageMark(id = m.getString("id"), type = MarkType.valueOf(m.getString("type")),
                        assetPath = m.optString("assetPath").takeIf { it.isNotEmpty() && it != "null" },
                        text = m.optString("text"), x = m.getDouble("x").toFloat(),
                        y = m.getDouble("y").toFloat(), w = m.getDouble("w").toFloat(),
                        h = m.getDouble("h").toFloat())
                } } ?: emptyList()
            )
        } }
    )
}

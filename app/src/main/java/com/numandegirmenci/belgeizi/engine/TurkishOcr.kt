package com.numandegirmenci.belgeizi.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import com.numandegirmenci.belgeizi.data.OcrLine
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Language-aware, on-device OCR verification. ML Kit and Turkish Tesseract
 * independently read the same word image. No user selection is required.
 * Low-evidence corrections are intentionally rejected.
 */
object TurkishOcr {
    private val turkish = Locale.forLanguageTag("tr-TR")
    private val diacritics = "çğıöşüÇĞİÖŞÜ"
    private val suffix = Regex("^([\\p{L}]{4,})lari([.,;!?]?)$", RegexOption.IGNORE_CASE)

    fun refine(context: Context, page: Bitmap, detected: List<OcrLine>): List<OcrLine> {
        if (detected.isEmpty()) return detected
        val home = try { installModel(context) } catch (_: Exception) {
            return detected.map(::recoverSuffix)
        }
        var api: TessBaseAPI? = null
        return try {
            api = TessBaseAPI()
            val tess = api
            if (!tess.init(home.absolutePath, "tur")) {
                detected.map(::recoverSuffix)
            } else {
                tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_WORD)
                var count = 0
                detected.map { line ->
                    val old = line.text
                    if (!shouldVerify(old, line.confidence) || count >= 240) {
                        return@map recoverSuffix(line)
                    }
                    count++
                    val rect = wordRectangle(line, page) ?: return@map recoverSuffix(line)
                    val crop = Bitmap.createBitmap(page, rect.left, rect.top, rect.width(), rect.height())
                    val input = if (crop.height < 50) {
                        val scale = (70f / crop.height).coerceIn(1.2f, 3f)
                        Bitmap.createScaledBitmap(crop,
                            (crop.width * scale).roundToInt().coerceAtLeast(1),
                            (crop.height * scale).roundToInt().coerceAtLeast(1), true).also {
                            if (it !== crop) crop.recycle()
                        }
                    } else crop
                    try {
                        tess.setImage(input)
                        val alternative = tess.getUTF8Text().orEmpty().trim()
                            .replace(Regex("\\s+"), " ")
                        val conf = (tess.meanConfidence() / 100f).coerceIn(0f, 1f)
                        val selected = choose(old, line.confidence, alternative, conf)
                        val result = if (selected == old) line else line.copy(
                            original = selected, text = selected,
                            confidence = maxOf(line.confidence, conf)
                        )
                        recoverSuffix(result)
                    } catch (_: Exception) {
                        recoverSuffix(line)
                    } finally {
                        input.recycle()
                    }
                }
            }
        } catch (_: Throwable) {
            detected.map(::recoverSuffix)
        } finally {
            api?.recycle()
        }
    }

    private fun installModel(context: Context): File {
        val home = File(context.filesDir, "turkish_ocr")
        val data = File(home, "tessdata").apply { mkdirs() }
        val file = File(data, "tur.traineddata")
        if (file.length() < 1_000_000L) {
            val tmp = File(data, "tur.traineddata.tmp")
            try {
                context.assets.open("tessdata/tur.traineddata").use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                require(tmp.length() > 1_000_000L) { "Turkish OCR model is incomplete." }
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
            } finally { tmp.delete() }
        }
        return home
    }

    private fun wordRectangle(line: OcrLine, image: Bitmap): Rect? {
        if (image.width < 2 || image.height < 2) return null
        val x = (line.x * image.width).roundToInt()
        val y = (line.y * image.height).roundToInt()
        val w = (line.w * image.width).roundToInt()
        val h = (line.h * image.height).roundToInt()
        if (w < 4 || h < 4) return null
        val padx = (w * .07f).roundToInt().coerceIn(3, 18)
        val pady = (h * .28f).roundToInt().coerceIn(4, 16)
        val left = (x - padx).coerceIn(0, image.width - 1)
        val top = (y - pady).coerceIn(0, image.height - 1)
        val right = (x + w + padx).coerceIn(left + 1, image.width)
        val bottom = (y + h + pady).coerceIn(top + 1, image.height)
        return Rect(left, top, right, bottom)
    }

    private fun shouldVerify(text: String, mlConfidence: Float): Boolean {
        if (text.length < 4 || text.count { it.isLetter() } < 3) return false
        return mlConfidence < .93f || text.any { it in "iIıİsSgGcCoOuU" }
    }

    private fun skeleton(value: String) =
        value.lowercase(turkish).map {
            when (it) {
                'ç' -> 'c'; 'ğ' -> 'g'; 'ı' -> 'i'
                'ö' -> 'o'; 'ş' -> 's'; 'ü' -> 'u'
                else -> it
            }
        }.joinToString("")

    private fun core(value: String) = value.trim()
        .trim { !it.isLetterOrDigit() }

    private fun choose(ml: String, mlConfidence: Float, tess: String, tessConfidence: Float): String {
        val a = core(ml)
        val b = core(tess)
        if (b.length < 2 || b.contains(' ') || a.isBlank()) return ml
        // Only accept a diacritic-only disagreement on useful language-model evidence.
        // Do not turn every ASCII i into dotless ı.
        if (skeleton(a) == skeleton(b)) {
            val addedTurkish = b.any { it in diacritics } && a.none { it in diacritics }
            if (addedTurkish && tessConfidence >= .52f) return preserveEdges(ml, b)
            if (tessConfidence >= .80f && tessConfidence >= mlConfidence - .05f) {
                return preserveEdges(ml, b)
            }
        }
        // Completely different words require much stronger evidence.
        if (mlConfidence < .55f && tessConfidence >= .90f &&
            abs(a.length - b.length) <= 1 && oneEditApart(a.lowercase(turkish), b.lowercase(turkish))) {
            return preserveEdges(ml, b)
        }
        return ml
    }

    private fun preserveEdges(original: String, candidate: String): String {
        val start = original.indexOfFirst { it.isLetterOrDigit() }
        if (start < 0) return candidate
        val end = original.indexOfLast { it.isLetterOrDigit() }
        val originalCore = original.substring(start, end + 1)
        val adjusted = when {
            originalCore.all { !it.isLetter() || it.isUpperCase() } -> candidate.uppercase(turkish)
            originalCore.first().isUpperCase() -> candidate.replaceFirstChar { it.titlecase(turkish) }
            else -> candidate
        }
        return original.substring(0, start) + adjusted + original.substring(end + 1)
    }

    private fun oneEditApart(a: String, b: String): Boolean {
        if (a == b) return true
        if (abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var errors = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++errors > 1) return false
            if (a.length > b.length) i++
            else if (b.length > a.length) j++
            else { i++; j++ }
        }
        return errors + (a.length - i) + (b.length - j) <= 1
    }

    /**
     * Safe vowel-harmony fallback for -ları when ML reads the final dotless
     * ı as i. Works on general words rather than a hard-coded book title.
     */
    private fun recoverSuffix(line: OcrLine): OcrLine {
        if (line.confidence !in 0.30f..0.92f) return line
        val match = suffix.matchEntire(line.text) ?: return line
        val stem = match.groupValues[1]
        val lastVowel = stem.lowercase(turkish).lastOrNull { it in "aeıioöuü" }
        if (lastVowel == null || lastVowel !in "aıou") return line
        val replaced = preserveEdges(line.text, stem + "ları" + match.groupValues[2])
        return line.copy(original = replaced, text = replaced)
    }
}

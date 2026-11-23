package com.numandegirmenci.belgeizi.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.ColorMatrix
import android.graphics.Paint
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.numandegirmenci.belgeizi.data.OcrLine

object OcrEngine {
    /**
     * Invoke off the main thread. First pass uses original image; a low-confidence
     * page receives one contrast-enhanced second pass. The better plausible result
     * wins. Both passes have the same coordinates, so the OCR boxes stay aligned.
     * This is a heuristic, not a validated accuracy claim.
     */
    fun recognize(context: Context, image: Bitmap): List<OcrLine> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val initial = read(recognizer, image)
            if (initial.size >= 8 && meanConfidence(initial) >= 0.90f && initial.none { it.confidence in 0.001f..0.65f }) return TurkishOcr.refine(context, image, initial)
            val enhanced = contrastGrayscale(image)
            try {
                val alternate = read(recognizer, enhanced)
                val c1 = meanConfidence(initial)
                val c2 = meanConfidence(alternate)
                val len1 = initial.sumOf { it.text.length }
                val len2 = alternate.sumOf { it.text.length }
                val selected = when {
                    initial.isEmpty() -> alternate
                    alternate.isEmpty() -> initial
                    c2 >= c1 + 0.06f && len2 >= len1 * 0.78f -> alternate
                    len2 >= len1 * 1.22f && c2 >= c1 - 0.03f -> alternate
                    else -> initial
                }
                TurkishOcr.refine(context, image, selected)
            } finally { enhanced.recycle() }
        } finally { recognizer.close() }
    }

    private fun read(recognizer: TextRecognizer, image: Bitmap): List<OcrLine> {
        val result = Tasks.await(recognizer.process(InputImage.fromBitmap(image, 0)))
        val found = mutableListOf<OcrLine>()
        for (block in result.textBlocks) for (line in block.lines) {
            val elements = line.elements.filter { it.boundingBox != null }
            if (elements.isNotEmpty()) {
                elements.forEach { element ->
                    val box = element.boundingBox ?: return@forEach
                    found += OcrLine(
                        original = element.text,
                        x = (box.left.toFloat() / image.width).coerceIn(0f, 1f),
                        y = (box.top.toFloat() / image.height).coerceIn(0f, 1f),
                        w = (box.width().toFloat() / image.width).coerceIn(0f, 1f),
                        h = (box.height().toFloat() / image.height).coerceIn(0f, 1f),
                        confidence = element.confidence.coerceIn(0f, 1f)
                    )
                }
            } else line.boundingBox?.let { box ->
                found += OcrLine(
                    original = line.text,
                    x = (box.left.toFloat() / image.width).coerceIn(0f, 1f),
                    y = (box.top.toFloat() / image.height).coerceIn(0f, 1f),
                    w = (box.width().toFloat() / image.width).coerceIn(0f, 1f),
                    h = (box.height().toFloat() / image.height).coerceIn(0f, 1f),
                    confidence = line.confidence.coerceIn(0f, 1f)
                )
            }
        }
        return found // ML Kit line/block reading order must be preserved.
    }

    private fun meanConfidence(lines: List<OcrLine>): Float {
        val known = lines.map { it.confidence }.filter { it > 0f }
        return if (known.isEmpty()) 0f else known.average().toFloat()
    }

    private fun contrastGrayscale(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val contrast = 1.28f
        val shift = -32f
        val r = .299f * contrast; val g = .587f * contrast; val b = .114f * contrast
        val matrix = ColorMatrix(floatArrayOf(
            r, g, b, 0f, shift,
            r, g, b, 0f, shift,
            r, g, b, 0f, shift,
            0f, 0f, 0f, 1f, 0f
        ))
        Canvas(out).drawBitmap(src, 0f, 0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return out
    }
}

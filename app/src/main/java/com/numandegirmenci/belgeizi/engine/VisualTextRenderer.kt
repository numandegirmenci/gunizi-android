package com.numandegirmenci.belgeizi.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.numandegirmenci.belgeizi.data.OcrLine
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Background-aware scanned text replacement. Original page images are never overwritten.
 * Reconstruction and typeface matching are estimates, not pixel-perfect restoration.
 */
object VisualTextRenderer {
    fun render(source: Bitmap, lines: List<OcrLine>): Bitmap {
        val edits = lines.filter { it.edited }
        if (edits.isEmpty()) return source
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        edits.forEach { paintEdit(out, canvas, it) }
        return out
    }

    private fun luminance(c: Int): Float =
        Color.red(c) * 0.299f + Color.green(c) * 0.587f + Color.blue(c) * 0.114f

    private fun paintEdit(image: Bitmap, canvas: Canvas, line: OcrLine) {
        val r = RectF(line.x * image.width, line.y * image.height,
            (line.x + line.w) * image.width, (line.y + line.h) * image.height)
        if (r.width() < 4 || r.height() < 4) return
        val padding = (r.height() * 0.20f).roundToInt().coerceIn(3, 20)
        val x0 = (r.left.toInt() - padding).coerceAtLeast(0)
        val y0 = (r.top.toInt() - padding).coerceAtLeast(0)
        val x1 = (r.right.roundToInt() + padding).coerceAtMost(image.width)
        val y1 = (r.bottom.roundToInt() + padding).coerceAtMost(image.height)
        val width = x1 - x0
        val height = y1 - y0
        if (width <= 3 || height <= 3) return
        val px = IntArray(width * height)
        image.getPixels(px, 0, width, x0, y0, width, height)
        val l = (r.left.toInt() - x0).coerceIn(0, width - 1)
        val t = (r.top.toInt() - y0).coerceIn(0, height - 1)
        val right = (r.right.roundToInt() - x0).coerceIn(l + 1, width)
        val bottom = (r.bottom.roundToInt() - y0).coerceIn(t + 1, height)
        val samples = mutableListOf<Int>()
        for (y in t until bottom step 2) for (x in l until right step 2) {
            samples.add(px[y * width + x])
        }
        if (samples.size < 5) return
        samples.sortBy(::luminance)
        val dark = luminance(samples[samples.size / 10])
        val middle = luminance(samples[samples.size / 2])
        val light = luminance(samples[samples.size * 9 / 10])
        val brightLetters = light - middle > middle - dark
        val threshold = if (brightLetters) (middle + light) * 0.54f
            else (middle + dark) * 0.46f
        val selected = if (brightLetters) samples.filter { luminance(it) > threshold }
            else samples.filter { luminance(it) < threshold }
        val letters = if (selected.size in 1 until samples.size / 2) selected
            else if (brightLetters) samples.takeLast(max(1, samples.size / 10))
            else samples.take(max(1, samples.size / 10))
        val ink = Color.rgb(
            letters.sumOf { Color.red(it) } / letters.size,
            letters.sumOf { Color.green(it) } / letters.size,
            letters.sumOf { Color.blue(it) } / letters.size
        )
        val mask = BooleanArray(width * height)
        var count = 0
        for (y in t until bottom) for (x in l until right) {
            val id = y * width + x
            val lum = luminance(px[id])
            mask[id] = if (brightLetters) lum > threshold else lum < threshold
            if (mask[id]) count++
        }
        val area = (right - l) * (bottom - t)
        if (count > area * 0.02 && count < area * 0.48) {
            val grown = mask.copyOf()
            for (y in t until bottom) for (x in l until right) if (mask[y * width + x]) {
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx; val yy = y + dy
                    if (xx in l until right && yy in t until bottom) grown[yy * width + xx] = true
                }
            }
            val directions = arrayOf(intArrayOf(0, 1), intArrayOf(0, -1),
                intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(1, 1),
                intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1))
            val restored = px.copyOf()
            val radius = (r.height() * 0.9f).roundToInt().coerceIn(12, 70)
            for (y in t until bottom) for (x in l until right) {
                val id = y * width + x
                if (!grown[id]) continue
                var red = 0f; var green = 0f; var blue = 0f; var total = 0f
                for (dir in directions) for (distance in 1..radius) {
                    val nx = x + dir[0] * distance
                    val ny = y + dir[1] * distance
                    if (nx !in 0 until width || ny !in 0 until height) break
                    val n = ny * width + nx
                    if (!grown[n]) {
                        val weight = 1f / (1f + distance)
                        red += Color.red(px[n]) * weight
                        green += Color.green(px[n]) * weight
                        blue += Color.blue(px[n]) * weight
                        total += weight
                        break
                    }
                }
                if (total > 0f) restored[id] = Color.rgb(
                    (red / total).roundToInt().coerceIn(0, 255),
                    (green / total).roundToInt().coerceIn(0, 255),
                    (blue / total).roundToInt().coerceIn(0, 255))
            }
            image.setPixels(restored, 0, width, x0, y0, width, height)
        } else {
            // Avoid the previous pure-white rectangle: use a nearby paper/background tone.
            val border = mutableListOf<Int>()
            for (x in l until right) {
                border.add(px[(t - 1).coerceAtLeast(0) * width + x])
                border.add(px[bottom.coerceAtMost(height - 1) * width + x])
            }
            val bg = Color.rgb(border.sumOf { Color.red(it) } / border.size,
                border.sumOf { Color.green(it) } / border.size,
                border.sumOf { Color.blue(it) } / border.size)
            canvas.drawRect(r, Paint().apply { color = bg })
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = ink
            typeface = if (r.height() > 36f) Typeface.create("serif", Typeface.BOLD)
                else Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = (r.height() * 0.83f).coerceAtLeast(6f)
        }
        val word = line.text.replace('\n', ' ')
        val measure = paint.measureText(word)
        if (measure > 0f) paint.textScaleX = min(1f, r.width() / measure)
        canvas.drawText(word, r.left, r.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)
    }
}

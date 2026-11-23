package com.numandegirmenci.belgeizi.engine

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.pow

/**
 * Non-destructive post-scan document enhancement; no OCR text is drawn.
 * Keeps the pixel geometry unchanged so recognized word boxes remain aligned.
 * Modes: ORIGINAL, AUTO, VIVID, BW.
 */
object ImageEnhancer {
    fun apply(source: Bitmap, mode: String = "AUTO"): Bitmap {
        if (mode == "ORIGINAL") return source
        val width = source.width
        val height = source.height
        if (width < 4 || height < 4) return source

        // Estimate slowly varying page illumination without treating every
        // printed letter as a shadow.
        val gridSize = 72
        val tiny = Bitmap.createScaledBitmap(source, gridSize, gridSize, true)
        val grid = IntArray(gridSize * gridSize)
        tiny.getPixels(grid, 0, gridSize, 0, 0, gridSize, gridSize)
        if (tiny !== source) tiny.recycle()
        val light = FloatArray(grid.size)
        for (index in grid.indices) {
            val c = grid[index]
            light[index] = 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
        }
        val blurred = FloatArray(grid.size)
        for (gy in 0 until gridSize) for (gx in 0 until gridSize) {
            var sum = 0f
            var count = 0
            for (dy in -3..3) for (dx in -3..3) {
                val x = (gx + dx).coerceIn(0, gridSize - 1)
                val y = (gy + dy).coerceIn(0, gridSize - 1)
                sum += light[y * gridSize + x]
                count++
            }
            blurred[gy * gridSize + gx] = sum / count
        }
        val sorted = blurred.sorted()
        val paperLevel = sorted[(sorted.size * .78f).toInt()].coerceAtLeast(110f)
        val target = if (mode == "VIVID") 245f else 238f
        val contrast = if (mode == "VIVID") 1.24f else 1.15f
        val saturation = if (mode == "VIVID") 1.20f else 1.08f
        val sharpening = if (mode == "VIVID") .49f else .32f
        val illuminationStrength = if (mode == "VIVID") .72f else .55f
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var previous = IntArray(width)
        var current = IntArray(width)
        var next = IntArray(width)
        val output = IntArray(width)
        source.getPixels(current, 0, width, 0, 0, width, 1)
        previous = current.copyOf()
        if (height > 1) source.getPixels(next, 0, width, 0, 1, width, 1)
        else next = current.copyOf()

        val gw = (gridSize - 1).toFloat()
        for (y in 0 until height) {
            val gy = (y.toFloat() / max(1, height - 1)) * gw
            val y0 = gy.toInt().coerceIn(0, gridSize - 1)
            val y1 = (y0 + 1).coerceAtMost(gridSize - 1)
            val yf = gy - y0
            for (x in 0 until width) {
                val gx = (x.toFloat() / max(1, width - 1)) * gw
                val x0 = gx.toInt().coerceIn(0, gridSize - 1)
                val x1 = (x0 + 1).coerceAtMost(gridSize - 1)
                val xf = gx - x0
                val a = blurred[y0 * gridSize + x0] * (1f - xf) + blurred[y0 * gridSize + x1] * xf
                val b = blurred[y1 * gridSize + x0] * (1f - xf) + blurred[y1 * gridSize + x1] * xf
                val illumination = a * (1f - yf) + b * yf
                val gain = ((target / max(100f, illumination)).toDouble()
                    .pow(illuminationStrength.toDouble())).toFloat().coerceIn(.87f, 1.36f)
                val center = current[x]
                val c0 = current[(x - 1).coerceAtLeast(0)]
                val c1 = current[(x + 1).coerceAtMost(width - 1)]
                val c2 = previous[x]
                val c3 = next[x]
                var r = refineChannel(Color.red(center),
                    (Color.red(c0) + Color.red(c1) + Color.red(c2) + Color.red(c3)) / 4f,
                    gain, contrast, sharpening)
                var g = refineChannel(Color.green(center),
                    (Color.green(c0) + Color.green(c1) + Color.green(c2) + Color.green(c3)) / 4f,
                    gain, contrast, sharpening)
                var bl = refineChannel(Color.blue(center),
                    (Color.blue(c0) + Color.blue(c1) + Color.blue(c2) + Color.blue(c3)) / 4f,
                    gain, contrast, sharpening)
                if (mode == "BW") {
                    val lum = .299f * r + .587f * g + .114f * bl
                    val threshold = (illumination * 0.79f).coerceIn(120f, 215f)
                    val value = ((lum - threshold) * 2.1f + 128f).coerceIn(0f, 255f)
                    r = value; g = value; bl = value
                } else {
                    val lum = .299f * r + .587f * g + .114f * bl
                    r = (lum + (r - lum) * saturation).coerceIn(0f, 255f)
                    g = (lum + (g - lum) * saturation).coerceIn(0f, 255f)
                    bl = (lum + (bl - lum) * saturation).coerceIn(0f, 255f)
                    if (paperLevel < 170f) {
                        val keep = .12f
                        r = r * (1f - keep) + Color.red(center) * keep
                        g = g * (1f - keep) + Color.green(center) * keep
                        bl = bl * (1f - keep) + Color.blue(center) * keep
                    }
                }
                output[x] = Color.rgb(r.roundToInt(), g.roundToInt(), bl.roundToInt())
            }
            out.setPixels(output, 0, width, 0, y, width, 1)
            val swap = previous
            previous = current
            current = next
            next = swap
            if (y + 2 < height) source.getPixels(next, 0, width, 0, y + 2, width, 1)
            else next = current
        }
        return out
    }

    private fun refineChannel(value: Int, blurred: Float, gain: Float,
                              contrast: Float, sharpening: Float): Float {
        val p = value.toFloat()
        val edge = (p - blurred).coerceIn(-35f, 35f)
        return ((p * gain - 125f) * contrast + 125f + edge * sharpening)
            .coerceIn(0f, 255f)
    }
}

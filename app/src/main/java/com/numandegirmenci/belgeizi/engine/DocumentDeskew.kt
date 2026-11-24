package com.numandegirmenci.belgeizi.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Text-safe deskew for slight camera tilt. GMS Document Scanner handles
 * four-corner perspective cropping in its scanner UI. This secondary pass
 * only rotates pages with multiple CONSISTENT horizontal text baselines.
 * No text is synthesized, erased or replaced.
 */
object DocumentDeskew {
    fun measureAngle(image: Bitmap): Float? {
        val scale = (1800f / maxOf(image.width, image.height)).coerceAtMost(1f)
        val preview = if (scale < 1f) Bitmap.createScaledBitmap(
            image, (image.width * scale).toInt().coerceAtLeast(1),
            (image.height * scale).toInt().coerceAtLeast(1), true
        ) else image
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val detected = Tasks.await(recognizer.process(InputImage.fromBitmap(preview, 0)))
            val angles = detected.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val points = line.cornerPoints ?: return@mapNotNull null
                if (points.size < 4 || line.text.count { it.isLetterOrDigit() } < 4) {
                    return@mapNotNull null
                }
                val dx = (points[1].x - points[0].x).toFloat()
                val dy = (points[1].y - points[0].y).toFloat()
                if (dx < 35f || abs(dy) > dx * .2f) return@mapNotNull null
                Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            }
            SkewDecision.reliableAngle(angles)
        } finally {
            recognizer.close()
            if (preview !== image) preview.recycle()
        }
    }

    /** Counter-rotate without clipping the source pixels, on an opaque page. */
    fun rotate(image: Bitmap, clockwiseDegrees: Float): Bitmap {
        val radians = Math.toRadians(clockwiseDegrees.toDouble())
        val c = abs(cos(radians))
        val s = abs(sin(radians))
        val outWidth = ceil(image.width * c + image.height * s).toInt()
        val outHeight = ceil(image.height * c + image.width * s).toInt()
        val dst = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        Canvas(dst).apply {
            drawColor(Color.WHITE)
            translate(outWidth / 2f, outHeight / 2f)
            rotate(clockwiseDegrees)
            drawBitmap(image, -image.width / 2f, -image.height / 2f,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        return dst
    }
}

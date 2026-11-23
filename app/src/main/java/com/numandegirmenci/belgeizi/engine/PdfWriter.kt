package com.numandegirmenci.belgeizi.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.content.ContentResolver
import android.net.Uri
import com.numandegirmenci.belgeizi.data.*
import java.io.IOException
import kotlin.math.min

/** Raster appearance + OCR text layer, editable field corrections, visual stamps/signatures. */
object PdfWriter {
    fun export(doc: ScanDocument, resolver: ContentResolver, outputUri: Uri) {
        val pdf = PdfDocument()
        try {
            doc.pages.forEachIndexed { index, page ->
                val image = BitmapFactory.decodeFile(page.path) ?: throw IOException("Sayfa ${index + 1} okunamadı.")
                try {
                    val portrait = image.height >= image.width
                    val width = if (portrait) 595 else 842
                    val height = if (portrait) 842 else 595
                    val info = PdfDocument.PageInfo.Builder(width, height, index + 1).create()
                    val sheet = pdf.startPage(info)
                    try {
                        val canvas = sheet.canvas
                        canvas.drawColor(Color.WHITE)
                        val margin = 18f
                        val scale = min((width - 2 * margin) / image.width, (height - 2 * margin) / image.height)
                        val drawnWidth = image.width * scale
                        val drawnHeight = image.height * scale
                        val dx = (width - drawnWidth) / 2f
                        val dy = (height - drawnHeight) / 2f
                        val box = RectF(dx, dy, dx + drawnWidth, dy + drawnHeight)
                        // OCR text is painted BEFORE the opaque image. This keeps scanned
                        // appearance while embedding searchable character content in the PDF.
                        // We do not insert superseded OCR originals after a user correction.
                        val hiddenText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
                        page.ocr.filterNot { it.edited }.forEach { l ->
                            val target = RectF(dx + l.x * drawnWidth, dy + l.y * drawnHeight,
                                dx + (l.x + l.w) * drawnWidth, dy + (l.y + l.h) * drawnHeight)
                            drawFittedText(canvas, l.text, target, hiddenText)
                        }
                        canvas.drawBitmap(image, null, box, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                        page.ocr.filter { it.edited }.forEach { l ->
                            val target = RectF(dx + l.x * drawnWidth, dy + l.y * drawnHeight,
                                dx + (l.x + l.w) * drawnWidth, dy + (l.y + l.h) * drawnHeight)
                            canvas.drawRect(target, Paint().apply { color = Color.WHITE })
                            drawFittedText(canvas, l.text, target,
                                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })
                        }
                        page.marks.forEach { mark ->
                            val target = RectF(dx + mark.x * drawnWidth, dy + mark.y * drawnHeight,
                                dx + (mark.x + mark.w) * drawnWidth, dy + (mark.y + mark.h) * drawnHeight)
                            when (mark.type) {
                                MarkType.TEXT_STAMP -> drawTextStamp(canvas, target, mark.text)
                                MarkType.IMAGE_STAMP, MarkType.SIGNATURE -> {
                                    val stamp = mark.assetPath?.let(BitmapFactory::decodeFile)
                                    if (stamp != null) {
                                        try { canvas.drawBitmap(stamp, null, target,
                                            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) }
                                        finally { stamp.recycle() }
                                    }
                                }
                            }
                        }
                    } finally { pdf.finishPage(sheet) }
                } finally { image.recycle() }
            }
            resolver.openOutputStream(outputUri, "wt")?.use { pdf.writeTo(it) }
                ?: throw IOException("PDF kaydetme konumu açılamadı.")
        } finally { pdf.close() }
    }

    private fun drawFittedText(canvas: Canvas, text: String, r: RectF, paint: Paint) {
        if (text.isBlank() || r.width() < 1f || r.height() < 1f) return
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = (r.height() * 0.82f).coerceIn(3.5f, 55f)
        val available = r.width().coerceAtLeast(1f)
        val measured = paint.measureText(text).coerceAtLeast(1f)
        paint.textScaleX = min(1f, available / measured)
        canvas.drawText(text.replace('\n', ' '), r.left, r.bottom - r.height() * 0.13f, paint)
        paint.textScaleX = 1f
    }

    private fun drawTextStamp(canvas: Canvas, rect: RectF, text: String) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(174, 59, 73); style = Paint.Style.STROKE; strokeWidth = 1.7f
        }
        canvas.drawRoundRect(rect, 4f, 4f, border)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(174, 59, 73)
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = (rect.height() * 0.50f).coerceAtLeast(4f)
            val measured = measureText(text).coerceAtLeast(1f)
            textScaleX = min(1f, (rect.width() - 6f) / measured)
        }
        canvas.drawText(text, rect.centerX(), rect.centerY() - (ink.ascent() + ink.descent()) / 2f, ink)
    }
}

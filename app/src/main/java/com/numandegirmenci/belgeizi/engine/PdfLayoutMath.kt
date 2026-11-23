package com.numandegirmenci.belgeizi.engine

import kotlin.math.min

/** Pure layout math so page framing is testable without Android graphics. */
object PdfLayoutMath {
    data class Fit(val left: Float, val top: Float, val width: Float, val height: Float)

    fun fit(pageWidth: Int, pageHeight: Int, imageWidth: Int, imageHeight: Int,
            margin: Float = 18f): Fit {
        require(pageWidth > 0 && pageHeight > 0)
        require(imageWidth > 0 && imageHeight > 0)
        require(margin >= 0f && pageWidth > 2 * margin && pageHeight > 2 * margin)
        val scale = min((pageWidth - 2f * margin) / imageWidth,
            (pageHeight - 2f * margin) / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale
        return Fit((pageWidth - width) / 2f, (pageHeight - height) / 2f, width, height)
    }
}

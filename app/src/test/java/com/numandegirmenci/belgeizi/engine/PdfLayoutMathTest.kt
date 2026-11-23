package com.numandegirmenci.belgeizi.engine

import org.junit.Assert.*
import org.junit.Test

class PdfLayoutMathTest {
    @Test fun portraitScanFitsPageWithoutStretching() {
        val b = PdfLayoutMath.fit(595, 842, 2082, 3400)
        assertTrue(b.left >= 17.9f && b.top >= 17.9f)
        assertTrue(b.left + b.width <= 595f - 17.9f)
        assertTrue(b.top + b.height <= 842f - 17.9f)
        assertEquals(2082f / 3400f, b.width / b.height, 0.0001f)
    }

    @Test fun landscapeScanIsCentered() {
        val b = PdfLayoutMath.fit(842, 595, 3464, 2592)
        assertEquals((842f - b.width) / 2f, b.left, 0.001f)
        assertEquals((595f - b.height) / 2f, b.top, 0.001f)
        assertEquals(3464f / 2592f, b.width / b.height, 0.0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotRenderZeroWidth() {
        PdfLayoutMath.fit(595, 842, 0, 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidMarginsAreRejected() {
        PdfLayoutMath.fit(595, 842, 100, 100, 300f)
    }
}

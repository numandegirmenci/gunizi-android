package com.numandegirmenci.belgeizi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkewDecisionTest {
    @Test fun consistentTiltIsCorrected() {
        assertEquals(2.1f,
            SkewDecision.reliableAngle(listOf(2.0f, 2.1f, 2.2f, 2.1f, -5f))!!, 0.15f)
    }
    @Test fun levelPageIsLeftAlone() {
        assertNull(SkewDecision.reliableAngle(listOf(0.15f, 0.2f, 0.1f, 0.0f)))
    }
    @Test fun disagreeingBaselinesAreNotWarped() {
        assertNull(SkewDecision.reliableAngle(listOf(-5f, -4f, 0f, 4f, 5f)))
    }
    @Test fun insufficientTextIsNotTrusted() {
        assertNull(SkewDecision.reliableAngle(listOf(3.0f, 3.2f)))
    }
}

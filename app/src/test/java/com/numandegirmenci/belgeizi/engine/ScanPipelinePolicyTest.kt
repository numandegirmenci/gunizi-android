package com.numandegirmenci.belgeizi.engine

import org.junit.Assert.*
import org.junit.Test

class ScanPipelinePolicyTest {
    @Test fun newlyImportedPageIsRecognizedAutomatically() {
        assertTrue(ScanPipelinePolicy.shouldRecognize(false, 0, false))
    }

    @Test fun completedUpToDateOcrDoesNotRunAgain() {
        assertFalse(ScanPipelinePolicy.shouldRecognize(true, 3, false))
    }

    @Test fun olderOcrGetsMigratedOnOpen() {
        assertTrue(ScanPipelinePolicy.shouldRecognize(true, 2, false))
    }

    @Test fun manualCorrectionsSurviveEvenForcedReScan() {
        assertFalse(ScanPipelinePolicy.shouldRecognize(true, 1, true, force = true))
    }

    @Test fun explicitRefreshAllowedOnlyWithoutManualEdits() {
        assertTrue(ScanPipelinePolicy.shouldRecognize(true, 3, false, force = true))
    }

    @Test fun allModesAreSupportedAndHaveSafeJpegQuality() {
        for (mode in listOf("ORIGINAL", "AUTO", "VIVID", "BW")) {
            assertTrue(ScanPipelinePolicy.validEnhancementMode(mode))
            assertTrue(ScanPipelinePolicy.jpegQuality(mode) in 85..100)
        }
        assertFalse(ScanPipelinePolicy.validEnhancementMode("BAD"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidModeCannotBeSaved() {
        ScanPipelinePolicy.jpegQuality("BAD")
    }
}

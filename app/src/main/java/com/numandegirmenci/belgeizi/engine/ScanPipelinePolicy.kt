package com.numandegirmenci.belgeizi.engine

/**
 * Pure decision logic used by both the scanner and its JVM regression tests.
 * User-approved corrections must never be discarded by an automatic OCR pass.
 */
object ScanPipelinePolicy {
    const val OCR_SCHEMA_VERSION = 3

    val MODES = setOf("ORIGINAL", "AUTO", "VIVID", "BW")

    fun shouldRecognize(ocrDone: Boolean, ocrVersion: Int,
                        hasManualEdits: Boolean, force: Boolean = false): Boolean {
        if (hasManualEdits) return false
        return force || !ocrDone || ocrVersion < OCR_SCHEMA_VERSION
    }

    fun validEnhancementMode(mode: String): Boolean = mode in MODES

    fun jpegQuality(mode: String): Int {
        require(validEnhancementMode(mode)) { "Unknown document enhancement mode." }
        return if (mode == "ORIGINAL") 96 else 92
    }
}

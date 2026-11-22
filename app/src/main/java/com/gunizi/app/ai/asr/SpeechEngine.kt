package com.gunizi.app.ai.asr

import android.content.Context
import android.net.Uri
import com.gunizi.app.AiProgress

/**
 * Independent interface for a local speech-to-text runtime.
 * The current implementation is intentionally the existing tested library.
 */
interface SpeechEngine {
    val runtimeId: String
    fun transcribe(context: Context, audio: Uri, modelId: String,
                   progress: AiProgress): String
}

package com.gunizi.app.ai.asr

import android.content.Context
import android.net.Uri
import com.gunizi.app.AiProgress
import com.gunizi.app.CallVaultWhisperEngine

/** The only production speech backend: pinned CallVault whisper.cpp + Silero VAD. */
class CallVaultSpeechEngine : SpeechEngine {
    override val runtimeId: String = "callvault-whisper.cpp+silero"
    override fun transcribe(context: Context, audio: Uri, modelId: String,
                            progress: AiProgress): String =
        CallVaultWhisperEngine.transcribe(context,audio,modelId,progress)
}
object SpeechEngineRegistry {
    private val engine: SpeechEngine = CallVaultSpeechEngine()
    @JvmStatic fun backendId(): String = engine.runtimeId
    @JvmStatic fun transcribe(context: Context, audio: Uri, modelId: String,
                              progress: AiProgress): String =
        engine.transcribe(context,audio,modelId,progress)
}

package com.gunizi.app

import android.content.Context
import android.net.Uri
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import kotlinx.coroutines.runBlocking
import java.util.Locale

/** Nine-hour safe: streaming decode into 5-minute mono WAV chunks; one Whisper model instance. */
object WhisperDailyTranscriber {
    @JvmStatic
    fun transcribe(context: Context, audio: Uri, progress: DailyReportEngine.Progress): String {
        require(AiModelStore.ready(context, AiModelStore.WHISPER)) { "Whisper modeli kurulmamış" }
        return runBlocking {
            progress.update(0, "Whisper Türkçe modeli yükleniyor")
            val model = Whisper.loadModel(context, AiModelStore.whisper(context).absolutePath)
            val resultText = StringBuilder()
            var count = 0
            try {
                AudioChunkDecoder.decode(context, audio) { file, startOffsetMs, percent ->
                    if (Thread.currentThread().isInterrupted) throw InterruptedException("İptal edildi")
                    progress.update(percent * 70 / 100, "Whisper ile konuşma çözümleme: %$percent")
                    val result = runBlocking {
                        Whisper.transcribe(model, file.absolutePath,
                            WhisperConfig(language = "tr", threads = 4))
                    }
                    for (segment in result.segments) {
                        val text = segment.text.trim()
                        if (text.isBlank()) continue
                        resultText.append("[")
                            .append(time(startOffsetMs + segment.startMs)).append("–")
                            .append(time(startOffsetMs + segment.endMs)).append("] ")
                            .append(text).append('\n')
                        count++
                    }
                }
                progress.update(70, "Konuşma dökümü hazır: $count bölüm")
                resultText.toString()
            } finally {
                Whisper.releaseModel(model)
            }
        }
    }
    private fun time(ms: Long): String {
        val seconds = (ms / 1000).coerceAtLeast(0L)
        return String.format(Locale.forLanguageTag("tr-TR"), "%02d:%02d:%02d",
            seconds / 3600, (seconds / 60) % 60, seconds % 60)
    }
}

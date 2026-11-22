package com.gunizi.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Streaming ASR: 5-minute WAV segments. Stores a checkpoint after EACH segment,
 * so a nine-hour run can resume without re-running completed Whisper segments.
 * Decoding still passes earlier audio, but inference is skipped for cached parts.
 */
object WhisperDailyTranscriber {
    private const val NO_SPEECH = "[[GUNIZI_SESSIZ_BOLUM]]"

    @JvmStatic
    fun transcribe(context: Context, audio: Uri, speechModel: String,
                   progress: AiProgress): String {
        require(AiModelStore.ready(context, speechModel)) { "Seçilen Whisper modeli kurulmamış" }
        return runBlocking {
            progress.update(0, "Yerel konuşma tanıma modeli yükleniyor")
            val checkpoint = File(context.noBackupFilesDir,
                "asr_checkpoints/" + digest(audio.toString() + "|" + speechModel + "|" +
                    contentSize(context, audio)))
            checkpoint.mkdirs()
            val model = Whisper.loadModel(context, AiModelStore.speechFile(context, speechModel).absolutePath)
            val resultText = StringBuilder()
            var count = 0
            var index = 0
            try {
                AudioChunkDecoder.decode(context, audio) { file, startOffsetMs, percent ->
                    if (Thread.currentThread().isInterrupted) throw InterruptedException("Çözümleme iptal edildi")
                    val checkpointFile = File(checkpoint, "part_" +
                        index.toString().padStart(5, '0') + ".txt")
                    val current: String
                    if (checkpointFile.exists()) {
                        current = checkpointFile.readText(Charsets.UTF_8)
                    } else {
                        val output = runBlocking {
                            Whisper.transcribe(model, file.absolutePath,
                                WhisperConfig(language = "tr", threads = 4))
                        }
                        val buffer = StringBuilder()
                        for (segment in output.segments) {
                            val recognized = segment.text.trim()
                            if (recognized.isBlank()) continue
                            buffer.append("[")
                                .append(time(startOffsetMs + segment.startMs))
                                .append("–")
                                .append(time(startOffsetMs + segment.endMs))
                                .append("] ")
                                .append(recognized)
                                .append('\n')
                        }
                        current = if (buffer.isEmpty()) NO_SPEECH else buffer.toString()
                        // Atomic checkpoint: a process kill won't leave a seemingly complete partial file.
                        val tmp = File(checkpoint, checkpointFile.name + ".tmp")
                        tmp.writeText(current, Charsets.UTF_8)
                        if (checkpointFile.exists()) checkpointFile.delete()
                        if (!tmp.renameTo(checkpointFile))
                            throw java.io.IOException("Konuşma kontrol noktası yazılamadı")
                    }
                    if (current != NO_SPEECH) {
                        resultText.append(current)
                        count += current.count { it == '\n' }
                    }
                    index++
                    progress.update(percent * 70 / 100,
                        "Türkçe ses çözülüyor: bölüm $index, ilerleme %$percent")
                }
                progress.update(70, "Tam konuşma dökümü hazır: $index ses bölümü, $count konuşma satırı")
                resultText.toString()
            } finally {
                Whisper.releaseModel(model)
            }
        }
    }

    private fun contentSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE),
                null, null, null)?.use { cur ->
                    if (cur.moveToFirst() && !cur.isNull(0)) cur.getLong(0) else 0L
                } ?: 0L
        } catch (_: Exception) { 0L }
    }
    private fun digest(data: String): String =
        MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8))
            .take(16).joinToString("") { "%02x".format(it) }

    private fun time(ms: Long): String {
        val seconds = (ms / 1000).coerceAtLeast(0L)
        return String.format(Locale.forLanguageTag("tr-TR"), "%02d:%02d:%02d",
            seconds / 3600, (seconds / 60) % 60, seconds % 60)
    }
}

package com.gunizi.app

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.baba.callvault.transcription.SpeechGapSnap
import com.baba.callvault.transcription.TranscriptSegment
import com.baba.callvault.transcription.VadModel
import com.baba.callvault.transcription.WhisperNative
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adapts the pinned CallVault whisper.cpp JNI, Silero VAD and timestamp repair
 * for Günİzi audio documents. GPL-3.0 attribution: THIRD_PARTY_NOTICES.md.
 */
object CallVaultWhisperEngine {
    @JvmStatic fun cancel() {
        try { WhisperNative.requestAbort() } catch (_: Throwable) {}
    }

    @Synchronized @JvmStatic
    fun transcribe(context: Context, audio: Uri, modelId: String,
                   progress: AiProgress): String {
        require(AiModelStore.ready(context,modelId)) { "Whisper modeli eksik veya doğrulanmamış" }
        val prefs=context.getSharedPreferences("gunizi",Context.MODE_PRIVATE)
        val useVad=prefs.getBoolean("aiSileroVad",true)
        val jobId=prefs.getString("analysisWorkId","") ?: ""
        val size=try { context.contentResolver.openAssetFileDescriptor(audio,"r")?.use {
            it.length } ?: 0L }catch(_:Exception){0L}
        val sourceHash=prefs.getString("analysisSourceDigest","") ?: ""
        require(sourceHash.matches(Regex("[0-9a-f]{64}"))){"Kaynak ses kimliği doğrulanamadı"}
        val key=hash(sourceHash+"|"+modelId+"|asr-native-v2|vad="+useVad)
        val cache=File(context.noBackupFilesDir,"cv_asr_checkpoints/"+key)
        check(cache.exists()||cache.mkdirs()) { "Konuşma kontrol noktası açılamadı" }

        AiStageMetrics.enter(context,AiStageMetrics.MODEL_LOADING)
        progress.update(0,"Konuşma modeli belleğe alınıyor")
        val started=SystemClock.elapsedRealtime()
        val ptr=WhisperNative.initContext(
            AiModelStore.speechFile(context,modelId).absolutePath,
            context.applicationInfo.nativeLibraryDir)
        if(ptr==0L)throw IllegalStateException(
            "whisper.cpp modeli açılamadı. Model biçimini ve boş RAM'i kontrol et.")
        prefs.edit().putLong("lastWhisperLoadMs",SystemClock.elapsedRealtime()-started)
            .putString("analysisRuntime","Yerel konuşma motoru").apply()
        val result=StringBuilder()
        var index=0
        try{
            val vad=if(useVad) VadModel.ensureExtracted(context) else null
            progress.update(1,when {
                vad!=null -> "Silero VAD etkin • Konuşma aralıkları ayıklanıyor"
                useVad -> "Silero yüklenemedi • VAD olmadan devam edilecek"
                else -> "Silero VAD kapalı • Sesin tamamı işlenecek"
            })
            val totalMs=prefs.getLong("analysisAudioDuration",0L)
            prefs.edit().putLong("analysisAudioProcessedMs",0L)
                .putInt("analysisChunkIndex",0)
                .putInt("analysisChunkTotal",AiProgressStatus.totalChunks(totalMs))
                .putInt("analysisNativePercent",0)
                .putLong("analysisLastAdvanceAt",System.currentTimeMillis()).apply()
            AiStageMetrics.enter(context,AiStageMetrics.AUDIO_DECODING)
            AudioChunkDecoder.decode(context,audio){chunk,startMs,_ ->
                if(Thread.currentThread().isInterrupted)
                    throw InterruptedException("Konuşma çıkarma iptal edildi")
                val chunkDurationMs=chunk.size*1000L/16000L
                val checkpoint=File(cache,"part_"+index.toString().padStart(5,'0')+".txt")
                prefs.edit().putInt("analysisChunkIndex",index+1)
                    .putInt("analysisNativePercent",0)
                    .putLong("analysisLastAdvanceAt",System.currentTimeMillis()).apply()
                val current=if(checkpoint.isFile){
                    checkpoint.readText(Charsets.UTF_8)
                }else{
                    val floats=chunk
                    AiStageMetrics.enter(context,AiStageMetrics.ASR)
                    progress.update(progressValue(startMs,totalMs,chunkDurationMs,0),
                        "Konuşma aranıyor • ${index+1}. bölüm (${chunkDurationMs/1000} sn)")
                    val polling=AtomicBoolean(true)
                    var lastNativePct=-1
                    val job=Thread({
                        while(polling.get()){
                            try{Thread.sleep(1100)}catch(_:InterruptedException){break}
                            if(!polling.get())break
                            val nativePct=try{WhisperNative.progressPercent()}catch(_:Throwable){0}
                            if(nativePct>lastNativePct){
                                lastNativePct=nativePct
                                prefs.edit().putInt("analysisNativePercent",nativePct)
                                    .putLong("analysisLastAdvanceAt",System.currentTimeMillis()).apply()
                            }
                            progress.update(progressValue(startMs,totalMs,chunkDurationMs,nativePct),
                                "Ses çözülüyor • Bölüm ${index+1} • Yerel motor %$nativePct")
                        }
                    },"GunIzi-WhisperProgress").apply{isDaemon=true}
                    job.start()
                    try{
                        WhisperNative.transcribe(ptr,floats,4,"tr",null,vad,1,-1)
                    }finally{
                        polling.set(false);job.interrupt()
                        try{job.join(300)}catch(_:InterruptedException){}
                    }
                    if(Thread.currentThread().isInterrupted)
                        throw InterruptedException("Konuşma çıkarma iptal edildi")
                    val kept=if(vad!=null) {
                        val n=WhisperNative.vadSegmentCount(ptr)
                        prefs.edit().putString("aiVadLast",
                            "$n konuşma aralığı").apply()
                        (0 until n).map {
                            SpeechGapSnap.Speech(
                                WhisperNative.vadSegmentStartMs(ptr,it),
                                WhisperNative.vadSegmentEndMs(ptr,it))
                        }
                    } else emptyList()
                    val raw=(0 until WhisperNative.segmentCount(ptr)).map {
                        TranscriptSegment(
                            WhisperNative.segmentStartMs(ptr,it),
                            WhisperNative.segmentEndMs(ptr,it),
                            WhisperNative.segmentText(ptr,it).trim())
                    }
                    // A VAD-confirmed empty region is not speech; do not accept ASR hallucinations.
                    // When VAD is disabled or fails to load, use Whisper's real segments.
                    // A missing VAD must never turn valid speech into an empty transcript.
                    val corrected=when{
                        vad==null -> raw
                        kept.isEmpty() -> emptyList<TranscriptSegment>()
                        else -> SpeechGapSnap.apply(raw,kept)
                    }
                    val text=corrected.filter{it.text.isNotBlank()}.joinToString("") {
                        "["+time(startMs+it.startMs)+"–"+time(startMs+it.endMs)+"] "+
                        it.text+"\n"
                    }.ifBlank{"[[GUNIZI_SESSIZ_BOLUM]]"}
                    val partial=File(cache,checkpoint.name+".partial")
                    partial.writeText(text,Charsets.UTF_8)
                    check(partial.renameTo(checkpoint)){"Konuşma parçası kaydedilemedi"}
                    text
                }
                if(current!="[[GUNIZI_SESSIZ_BOLUM]]"){
                    result.append(current)
                    LiveTranscriptStore.append(context,jobId,current)
                    prefs.edit().putString("analysisPreview",
                        current.replace("\n"," ").takeLast(430)).apply()
                }
                index++
                val chunkMs=chunkDurationMs
                val doneMs=if(totalMs>0) (startMs+chunkMs).coerceAtMost(totalMs) else startMs+chunkMs
                prefs.edit().putLong("analysisAudioProcessedMs",doneMs)
                    .putInt("analysisNativePercent",100)
                    .putLong("analysisLastAdvanceAt",System.currentTimeMillis()).apply()
                AiStageMetrics.enter(context,AiStageMetrics.AUDIO_DECODING)
                progress.update(progressValue(doneMs,totalMs,0),
                    "Tamamlanan ses bölümü: $index • "+
                        AiProgressStatus.audioLabel(doneMs,totalMs))
            }
            progress.update(70,"Konuşma metni hazır • $index bölüm incelendi")
            return result.toString()
        } finally {
            WhisperNative.freeContext(ptr)
        }
    }

    private fun progressValue(startMs:Long,totalMs:Long,chunkMs:Long,native:Int):Int{
        if(totalMs<=0)return 1
        val m=startMs+chunkMs*native.coerceIn(0,100)/100L
        return ((m.coerceIn(0,totalMs)*70L)/totalMs).toInt().coerceIn(1,70)
    }
    private fun time(ms:Long):String{
        val seconds=(ms/1000L).coerceAtLeast(0)
        return String.format(Locale.ROOT,"%02d:%02d:%02d",
            seconds/3600,(seconds/60)%60,seconds%60)
    }
    private fun hash(s:String):String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            .take(16).joinToString(""){"%02x".format(it)}
}

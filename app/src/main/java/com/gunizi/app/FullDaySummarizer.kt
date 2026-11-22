package com.gunizi.app

import android.content.Context
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest

/**
 * On-device Qwen3 map-reduce summarization for long Turkish recordings.
 * Source transcript remains intact; checkpoints let retries skip completed chunks.
 */
object FullDaySummarizer {
    private const val CHUNK_LIMIT=4100
    private const val GROUP_LIMIT=6200
    private const val SYSTEM = "Sen bir Türkçe iş günü analiz uzmanısın. " +
        "Yalnızca söylenmiş konuşmalara dayan. İsim, tarih, karar, görev ve kaynak saatlerini koru. " +
        "Belirsiz olanı belirsiz diye belirt; gerçekleşmemiş olay uydurma. " +
        "Düşünce adımlarını yazma. /no_think"

    @JvmStatic
    fun summarize(context: Context, transcript: String,
                  progress: AiProgress): String = runBlocking {
        require(AiModelStore.ready(context,AiModelStore.QWEN)) { "Qwen3 modeli kurulmamış" }
        if (transcript.isBlank()) return@runBlocking "Anlaşılır konuşma bulunamadı."
        progress.update(71,"Qwen3 yerel modeli açılıyor")
        val model=Llama.loadModel(AiModelStore.qwen(context).absolutePath,
            LlamaConfig(contextSize=4096,threads=4,temperature=0.15f,topP=0.85f,topK=30,seed=17))
        try {
            val parts=splitText(transcript,CHUNK_LIMIT)
            val folder=File(context.noBackupFilesDir,"ai_checkpoints/"+digest(transcript))
            folder.mkdirs()
            val notes=ArrayList<String>()
            for ((i,part) in parts.withIndex()) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("Özetleme durduruldu")
                val backup=File(folder,"part_"+i.toString().padStart(4,'0')+".txt")
                val note=if(backup.isFile()&&backup.length()>30)backup.readText() else {
                    val answer=Llama.complete(model,
                        prompt="/no_think\nAşağıdaki kayıt parçasındaki önemli olayları, " +
                            "alınan kararları, sorumluları, verilen sözleri, takip edilecek işleri " +
                            "ve kaynak saatlerini Türkçe maddelerle özetle. Hiçbir bilgi ekleme.\n\n"+part,
                        systemPrompt=SYSTEM,maxTokens=420).text.trim()
                    backup.writeText(answer);answer
                }
                notes.add(note)
                progress.update(72+(i+1)*20/parts.size,
                    "Qwen3: "+(i+1)+"/"+parts.size+" bölüm yorumlandı")
            }
            var level: List<String> = notes
            var round=0
            while(level.size>1&&round++<7) {
                val packs=pack(level,GROUP_LIMIT)
                val next=ArrayList<String>()
                for(group in packs) {
                    val combined=Llama.complete(model,
                        prompt="/no_think\nŞu alt raporları birleştir: tüm saatleri, kesin kararları " +
                            "ve yarına kalan işleri kaybetme. Sadece verilen bilgileri kullan.\n\n"+group,
                        systemPrompt=SYSTEM,maxTokens=900).text.trim()
                    next.add(combined)
                }
                level=if(next.size>=level.size)listOf(next.joinToString("\n").take(8500))else next
                progress.update(94,"Gün içindeki konular birleştiriliyor")
            }
            progress.update(98,"Tam günlük rapor düzenleniyor")
            val overall=Llama.complete(model,
                prompt="/no_think\nAşağıdaki notlardan tam Türkçe günlük rapor oluştur. " +
                    "Bölümler: GÜNÜN ÖZETİ, KRONOLOJİ, ALINAN KARARLAR, YAPILACAKLAR " +
                    "VE SORUMLULAR, BEKLEYEN KONULAR, BELİRSİZ İFADELER. " +
                    "Her maddede varsa kaynak saati yaz. Sadece dökümde geçenleri yaz.\n\n"+
                    level.firstOrNull().orEmpty().take(8200),
                systemPrompt=SYSTEM,maxTokens=1350).text.trim()
            val detailed=notes.mapIndexed { index, item ->
                "KAYIT BÖLÜMÜ "+(index+1).toString()+" / "+notes.size+"\n"+item
            }.joinToString("\n\n")
            overall+"\n\n=== GÜN BOYUNCA AYRINTILI PARÇA ÖZETLERİ ===\n\n"+detailed
        } finally {
            Llama.releaseModel(model)
        }
    }

    private fun splitText(text:String,limit:Int):List<String> {
        val pieces=ArrayList<String>()
        val buffer=StringBuilder()
        for(line in text.lineSequence()) {
            if(buffer.length+line.length>limit&&buffer.isNotEmpty()) {
                pieces.add(buffer.toString());buffer.clear()
            }
            if(line.length>limit){
                if(buffer.isNotEmpty()){pieces.add(buffer.toString());buffer.clear()}
                pieces.addAll(line.chunked(limit))
            }else buffer.appendLine(line)
        }
        if(buffer.isNotEmpty())pieces.add(buffer.toString())
        return if(pieces.isEmpty())listOf("")else pieces
    }
    private fun pack(notes:List<String>,max:Int):List<String> {
        val groups=ArrayList<String>()
        val buffer=StringBuilder()
        for(note in notes){
            if(buffer.length+note.length>max&&buffer.isNotEmpty()){
                groups.add(buffer.toString());buffer.clear()
            }
            buffer.append(note.take(max-1)).append("\n\n")
        }
        if(buffer.isNotEmpty())groups.add(buffer.toString())
        return groups
    }
    private fun digest(text:String):String {
        val bytes=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }
}

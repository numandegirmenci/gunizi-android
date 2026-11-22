# CallVault kaynak karşılaştırması ve lisans

İncelenen kaynak: [madkongo/CallVault](https://github.com/madkongo/CallVault), main commit 1561721d21321cf84a251093075b59b351b2e8c0 (15 Kasım 2026).
Günİzi temeli: numandegirmenci/gunizi-android v0.9.1 Test.

| CallVault dosyası | Kaynakta görülen yöntem | Günİzi'de bağımsız tasarım |
|---|---|---|
| transcription/WhisperNative.kt | whisper.cpp JNI model aç/kapat, ilerleme, backend bilgisi | SpeechEngine sözleşmesi, ölçülen model hazırlama |
| transcription/TranscriptionEngine.kt | model oturum kontrolü, chunk + VAD, ses süresine bağlı ilerleme | StreamingAudioDecoder, VAD, zaman eşleyici |
| transcription/TranscriptionWorker.kt | ön plan arka plan görevi, iptal/yeniden deneme | AnalysisCoordinator, job ID, gerçek hata |
| transcription/VadModel.kt | Silero küçük VAD, başarısızlıkta normal yol | testle açılan opsiyonel VAD |
| summary/SummaryEngine.kt | mutex, modelin güvenli şekilde bırakılması | SummarySessionManager |
| summary/SummaryRunner.kt | bir model oturumunda çoklu chunk ve birleştirme | kaynak ID koruyan gün sentezi |
| summary/SummaryChunking.kt | segmenti bölmeyen konuşma parçaları | segment temelli JSON çıkarımı |
| summary/SummaryPrompt.kt / SummaryGrammar.kt | kaynak/metin odaklı yapılandırılmış özet | Türkçe kaynak kanıtlı karar/görev tablosu |
| ui/common/SummaryCard.kt | hazır/çalışıyor/engelli/başarısız durum kartı ve zaman bağlantısı | tek kayıt merkezli yapay zeka ekranı |

CallVault GPL-3.0 lisanslıdır. Burada programın kaynak kodu kopyalanmamış, yalnızca mimari fikirleri belgelenmiştir. İleride doğrudan kod dahil etmek istersek GPL yükümlülükleri ve diğer bağımlılık lisansları gözden geçirilmelidir.

Cihaz karşılaştırması gereklidir: daha iyi native motorun, Silero VAD'nin veya model boyutunun hangisinin hız kazancına ne kadar katkı verdiği yalnızca GitHub kaynak kodundan kesin ölçülemez.

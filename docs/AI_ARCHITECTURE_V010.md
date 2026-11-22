# Günİzi AI 2.0 — Mimari yeniden tasarım (v0.10 hazırlığı)

Durum: TASARIM. Bu belge çalışan bir APK veya doğrulanmış hız kazanımı değildir.
Tarih: 22 Kasım 2026.
Hedef: CallVault'un yerel yapay zeka mimarisindeki iyi yöntemlerden öğrenmek; Günİzi'nin 9 saatlik mesai analizi hedefini korumak.
Korunacak: RecorderService, Geçmiş Kaydı ve saniyelik sayaç, Mesai modu, kullanıcı dosyaları ve mevcut v0.9.1 Test APK.

## 1. Temel ürün kararı

Üç işlem kesin olarak birbirinden ayrılır:

1. **Konuşma metnini çıkar**: yalnızca yerel ASR, Whisper; çıktı zaman damgalı ve düzenlenebilir dökümdür.
2. **Hazır metni özetle ve tablolaştır**: yalnızca yerel LLM; Whisper ve ses dosyası tekrar işlenmez.
3. **Ses kaydını analiz et**: önce ASR, sonra LLM; kullanıcı ayrı metni ve raporu da görür.

İşlem hattı:

    Kaynak Opus/M4A/WAV
        → Kaynak testi + dosya parmak izi
        → Akış halinde 16 kHz mono ses çözümleme
        → Opsiyonel sessizlik algılama (VAD)
        → Kaynak zamanına eşlenmiş konuşma parçaları
        → Yerel Whisper motoru
        → Kalıcı transkript segmentleri
        ├── Metin / zamanlı ses oynatma
        └── Qwen3 (veya testten geçmiş alternatif)
              → Kaynak segment ID'li yapılandırılmış JSON
              → Konu birleştirme / karar-görev çıkarımı
              → Kaynak doğrulama / eksik parça kontrolü
              → Günlük rapor / konu kartları / notlar / CSV

ASR ve LLM aynı anda iki büyük modeli RAM'de tutmayacak. ASR işi metni kalıcı kaydettikten sonra modeli bırakacak, ardından LLM ayrı oturumda açılacak. Kaynak ses/veri, model indirme haricinde sunuculara gönderilmeyecek.

## 2. CallVault gözlemi ve bağımsız uyarlama

İncelenen kaynak: https://github.com/madkongo/CallVault ; main commit 1561721d21321cf84a251093075b59b351b2e8c0.

| CallVault'ta görülen | Günİzi için karar |
|---|---|
| TranscriptionEngine ve WhisperNative: native model yaşam döngüsü, tek motor ve gerçek chunk progress | Ayrı SpeechEngine arayüzü, tek çalışma oturumu, gerçek yükleme süresi ve hata kodu |
| Silero VAD ve kaynak ses planlama | Boş sessizliği atlayan, zaman damgasını kaydırmayan, kapatılabilir VAD |
| TranscriptionWorker: ilerleme ve ön plan görev takibi | Veritabanına bağlı tek job ID, çalışıyor/iptal/hata durumları |
| SummaryEngine: tek LLM oturumu ve güvenli bellek bırakma | SummarySessionManager, büyük iki motoru aynı anda açık tutmama |
| SummaryChunking, SummaryRunner | Segment sınırını bozmadan parça analizi, bütün parçaları kapsayan rapor |
| SummaryPrompt ve SummaryGrammar | Kaynak ID'li JSON çıktı, şema doğrulama, belirsiz veri ayrımı |
| SummaryCard ve zaman damgalı satırlar | Tek kayıt ekranı, özet satırından sesin ilgili saniyesine git |

CallVault GPL-3.0 lisanslıdır. Bu tasarımda yalnızca yöntem ve API sınırları incelenmiştir; GPL kaynak kodu doğrudan Günİzi'ye kopyalanmamıştır. Doğrudan kod alımı gündeme gelirse lisans yükümlülükleri ayrıca çözülmelidir.
CallVault'un hızının hangi kod bileşeninden kaynaklandığı cihaz benchmark'ı yapılmadan kesin değildir.

## 3. Motor katmanları ve dosya sınırları

Mevcut Android modülü içinde yeni paket:

    com/gunizi/app/ai/
      domain/
        AnalysisMode.kt           - TRANSCRIPT, SUMMARY, FULL
        AnalysisStage.kt          - hazırlık, yükleme, analiz, hata, sonuç
        SourceEvidence.kt         - transkript ID ve ses başlangıç/bitiş
        TopicItem.kt              - konu, karar, görev, tarih, risk; doğrulama durumu
      audio/
        AudioPreflight.kt         - izin, codec, süre, dosya doğrulama
        StreamingAudioDecoder.kt  - AudioChunkDecoder adaptörü
        VoiceActivityDetector.kt  - Silero arayüzü, VAD kapalı fallback
        ChunkTimeMapper.kt        - özgün ses saatini koruma
      asr/
        SpeechEngine.kt           - prepare, transcribeChunk, cancel, close
        LegacyWhisperAdapter.kt   - mevcut kütüphaneyi geçici sarmala
        WhisperCppEngine.kt       - testten sonra bağımsız yeni JNI motor
        TranscriptRepository.kt   - segmentleri Room'da sakla
      summary/
        LanguageModelEngine.kt    - yerel LLM arayüzü
        SummarySessionManager.kt  - tek model oturumu ve RAM bırakma
        TopicExtractor.kt         - her parçadan yapılandırılmış veri
        TopicValidator.kt         - kaynak, şema ve tutarlılık kontrolü
        DailyReportComposer.kt    - bütün parçaların sentezi
      jobs/
        AnalysisCoordinator.kt    - planla / sürdür / iptal
        AnalysisWorker.kt         - WorkManager foreground işi
        AnalysisProgressStore.kt  - gerçek ilerleme, heartbeat ve hata
      storage/
        AiDatabase.kt             - Room ve migration
        AiCheckpointStore.kt      - atomik/hasheli bölüm sonuçları
      ui/
        AiWorkspaceScreen.kt      - seçilen kaydı ve iki ana eylemi göster
        RecordingAnalysisScreen.kt - döküm, özet, konu, görev, not
        AiStatusCard.kt           - durum / bekleme / iptal / hata
      export/
        NotesExportAdapter.kt     - Günİzi notu, CSV/HTML ve Android paylaşımı

Başlangıçta yeni arayüz mevcut Java Activity içine yerleştirilebilir.
Jetpack Compose'a geçiş zorunlu değil; çalışan motor ve veri formatları önce doğrulanır.

Mevcut dosyaların aşamalı geçişi:
- RecorderService.java / HistoryAudioStore.java: dokunmadan korunur.
- AudioChunkDecoder.java → StreamingAudioDecoder adaptörü.
- WhisperDailyTranscriber.kt → LegacyWhisperAdapter; sonra test edilmiş whisper.cpp.
- FullDaySummarizer.kt → TopicExtractor / TopicValidator / DailyReportComposer.
- DailySummaryWorker.java → AnalysisCoordinator'a iş devreden uyumluluk adaptörü.
- TopicReportRenderer.java → doğrulanmış TopicItem listesini görüntüler.
- SummaryActivity.java → kayıt merkezli ana ekran; işlemin gerçek job durumunu gözler.
- UserNotesStore.java / ExportManager.java → eski not ve kayıtlarla uyumluluk korunur.

## 4. Ses ve ASR doğruluğu

- Ses dosyası seçilince URI izinleri, format, süre, boş/bozuk medya ve model desteği test edilir.
- Dosya kimliği yalnızca URI + boyut değildir. Boyut, değiştirilme bilgisi ve içerik parmak izi ile kaynak değişimi izlenir; gerekli yerde tam SHA-256 doğrulanır.
- Dokuz saatlik kayıt tek bir PCM dizisi olarak RAM'e yüklenmez.
- Konuşma akışı uygun parçalara ayrılır; aday hedef 30–120 saniye konuşma, 2–5 dakika kaynak penceresi. Nihai sayılar cihaz testiyle seçilir.
- VAD sessizliği keserse gerçek kaynak saat eşlemesi ayrı saklanır; 15:32'deki söz 00:17'ye kaymamalıdır.
- Sessiz bölümleri atarken ilk/son kelime kaybı, düşük sesli konuşmalar ve kısa aralar A/B test edilir. Sorun varsa VAD kapalıya dönülür.
- Native model yükleme, dosya decode, ilk token/ilk transkript ve toplam inference ayrı ölçülür.
- Model uyumluluğu dosya indirilmiş olması ile değil, gerçek yükleme + kısa sesli inference testiyle doğrulanır.
- Tek yerel motor oturumu, thread seçimi, kontrollü bellek bırakma ve yanlış modelde görünür hata.
- Native JNI tamamen kilitlenirse Java Thread.interrupt tek başına çözüm değildir; gerekirse izole işlem hizmeti ve native iptal protokolü gerekir.

## 5. Kalıcı veri ve kaynak kanıtı

Room tabloları:
- recordings: id, fileUri, sourceFingerprint, codec, durationMs, createdAt.
- analysis_jobs: id, recordingId, mode, stage, modelVersion, focusHash, createdAt, stageStartedAt, heartbeatAt, completedAt, progressDone/Total, errorCode.
- transcript_segments: id, recordingId, startMs, endMs, text, speakerNullable, confidenceNullable, chunkIndex, engineModel.
- analysis_chunks: id, recordingId, chunkIndex, inputHash, modelId, promptVersion, status, validatedJson.
- topic_items: id, topicKey, itemType, description, sourceSegmentIds, sourceStartMs, sourceEndMs, ownerNullable, dueDateNullable, certaintyState.
- user_notes: id, title, text, sourceItemIds, updatedAt.

Kritik ayrım: modelin ürettiği bir cümle **olgu** kabul edilmez. Her karar/görev kaynak segment ID'siyle ilişkili olmalı. Kaynağı bulunamayan madde belirsiz olarak işaretlenmeli; kullanıcı incelemeden doğrulanmış görünmemeli.

Mevcut kullanıcı notları/kayıtları hiçbir veri geçişinde otomatik silinmez. Yeni Room formatına geçiş için salt-okunur uyumluluk ve kullanıcı denetimli içe aktarma kullanılır.

## 6. Özetleme ve tablo

- Kullanıcının talimatı bir iş başında sabitlenir (promptVersion).
- Transkript, cümle/segment sınırları korunarak parçalanır. 4–6 bin karakter **aday** sınırdır; gerçek token ölçümüne göre ayarlanır.
- Bir parça için Qwen3 yapılandırılmış JSON üretir. Token sınırı veya JSON hatası sessizce kabul edilmez; retry veya doğrulanabilir hata.
- JSON modeli: topic, type, summary, decisionStatus, owner, dueDate, sourceSegmentIds, sourceStartMs, sourceEndMs, uncertainty.
- İlk aşama her parçayı ayrı çıkarır. İkinci aşama benzer konu anahtarlarını birleştirir. Son aşama kullanıcı için Türkçe genel değerlendirme üretir.
- Kaynak ve tüm parça kimlikleri korunur. LLM birleştirmesi maddeleri silerse rapor kapsama testi bu durumu açıkça uyarır.
- “Alınan karar”, “Takip edilecek iş”, “Sorumlu”, “Son tarih”, “Belirsiz” ayrı alanlardır.
- Aynı konuşma metninden farklı kullanıcı talimatlarıyla tekrar özet yapılabilir; Whisper tekrar açılmaz.
- Samsung Notes'a otomatik gizli kayıt yok; kullanıcı onayıyla Notlarım ve Android paylaş menüsü vardır.

Örnek, gerçek kayda ait değildir:

    {
      "topic": "Ruhsat",
      "type": "TASK",
      "summary": "Eksik evrakların kontrolü istendi",
      "decisionStatus": "BELIRSIZ",
      "owner": null,
      "dueDate": null,
      "sourceSegmentIds": ["seg-101", "seg-102"],
      "sourceStartMs": 1830000,
      "sourceEndMs": 1862000
    }

## 7. İş durumu ve Android sınırları

Durum makinesi:

    CREATED → PREFLIGHT → MODEL_LOADING → VAD → TRANSCRIBING
            → TRANSCRIPT_READY → LLM_LOADING → EXTRACTING
            → MERGING → VALIDATING → READY
    Her aşama → PAUSED / CANCELLING / CANCELLED / RETRYABLE_ERROR / FAILED

Ekran açıldığında son iş, iş ID'siyle Room'dan okunur; eski mesaj “aktif işlem” olarak gösterilmez.
- Yükleme yüzdeyle ölçülemiyorsa %0'da takılıymış gibi gösterilmez; “Whisper hazırlanıyor, geçen 00:42” gösterilir.
- Ses işlemenin gerçek ilerlemesi çözülen ses süresi/toplam ses süresidir.
- Özetlemede tamamlanan parça/toplam parça gösterilir; iki ilerleme tek sahte yüzdeye zorlanmaz.
- Heartbeat örneğin 10 sn'de bir; native uzun blokta heartbeat kesilirse “Yanıt gecikiyor” açıklaması, kesin “çöktü” hükmü değil.
- İş sonuçları atomik kaydedilir. Ara dosya/kontrol noktası kaynak hash + model + promptVersion ile eşleşmiyorsa yeniden üretilir.
- Bildirimde “Konuşma dökümü hazır” ve “Konu tablosu hazır” farklı mesajlardır.
- Kullanıcı “Durdur”, “Sürdür” ve “Hata ayrıntısı” seçeneği görür.
- WorkManager foreground çalışsa bile Android 15+ dataSync/mediaProcessing türü işler için 24 saatte toplam 6 saat kısıtı olabilir. Dokuz saatlik **kaydı işlemek** ile tek servis içinde dokuz saat kesintisiz **analiz yapmak** aynı şey değildir.
- Uzun iş kesilirse sonraki uygun çalışma aralığında kaldığı yerden devam edebilmelidir.

## 8. Ekran mimarisi

Yapay Zeka ana sekmesi:
1. Seçilen kayıt, dalga formu, süresi ve oynatma.
2. İki birincil eylem: **Konuşma metnini çıkar** / **Özetle ve tablolaştır**.
3. Tek durum kartı: gerçek aşama, geçen süre, kaynak bölüm, hata, iptal.
4. Alt sekmeler: **Metin**, **Özet**, **Konular**, **Kararlar ve Yapılacaklar**, **Notlarım**.
5. Her özet maddesi ilgili ses saniyesine basılarak dinlenebilir.
6. Model dosyalarının kurulumu Ayarlar bölümündedir.

Ayarlar sonunda Geliştiren: Numan Değirmenci, e-posta geri bildirim ve GitHub bağlantısı korunur.

## 9. Karar ve ölçülebilir kabul koşulları

Öncelik 1: 203 saniyelik kaydın %0'da neden kaldığını model yükleme / native / codec süreleriyle saptamak. Önce Whisper base/small ile gerçek metin üretmek.
Öncelik 2: Dökümü kaynak saatleriyle kalıcı saklamak, sesi yeniden işlemeden Qwen3 ile özetlemek.
Öncelik 3: JSON şema + kaynak segment doğrulaması ve doğru konu/grup tablosu.
Öncelik 4: VAD, yeni whisper.cpp JNI ve hız için A/B benchmark.
Öncelik 5: 30 saniye, 3 dakika, 30 dakika ve 9 saat için cihaz testleri; arka plan dur-kaldığı yerden devam.
Öncelik 6: Kayıt merkezli yeni tasarım, notlar, yayın imzası ve güvenli in-place güncelleme.

**Henüz doğrulanmamış:** CallVault'un ölçülen hızının Günİzi'ye birebir aktarılacağı; Turbo Q5 uyumluluğu; 9 saatin ne kadar sürede analiz edileceği; yeni JNI'nin mevcut telefonda sorunsuz çalışacağı.

## 10. Lisans ve gizlilik

CallVault'un GPL-3.0 dosyalarını izinsiz kopyalamadan bağımsız uygulama geliştirilecek. VAD ve kullanılan model dosyalarının lisansları ayrıca denetlenecek. Ses, transkript veya özel içerik uygulama hata loglarına yazılmayacak. Geçici parçalar iş bitince temizlenecek; kullanıcı kaynak sesi, dökümü ve notları üzerinde açık saklama/silme kontrolü sahibi olacak.

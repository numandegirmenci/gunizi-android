# Günİzi v0.11.1 — Canlı işlem paneli ve durum sözleşmesi

Bu belge yeni `AiLiveStatusCard.java`,
`AiProgressStatus.java`, `CallVaultWhisperEngine.kt` ve
`DailySummaryWorker.java` arasındaki görev durumunu açıklar.

### Durumların kaynakları

| Alan | Veri kaynağı | İfade |
|---|---|---|
| Geçen süre | Android monotonic `elapsedRealtime` ve iş başlangıcı | saniyede bir |
| Bulunulan aşama | `AiStageMetrics` | ses kontrolü / model / konuşma / LLM / sonuç |
| Aşama süresi | `AiStageMetrics.stageMs` | bu aşamada geçen süre |
| Genel analiz yüzdesi | `DailySummaryWorker.progress` | yalnızca ölçülebilen aşamalarda |
| Model yükleme | native `initContext` | **yüzde uydurulmaz** |
| Bölüm | `analysisChunkIndex` ve `analysisChunkTotal` | bölüm 6/7 |
| İşlenen ses | kaynak süresi ve tamamlanan WAV bölüm boyutu | 00:02:30 / 00:03:23 |
| Native bölüm yüzdesi | `WhisperNative.progressPercent()` | ayrı teknik ayrıntı |
| Son metin | ASR/LLM'nin son kaydettiği kısa önizleme | yalnızca oluşunca |
| İş sonucu | WorkManager UUID ve kalıcı durum | RUNNING/FAILED/CANCELLED/SUCCEEDED |

### Hata yönetimi

- 90 saniyeyi aşan yükleme kullanıcıya gecikme uyarısı verir;
  native çağrının kilitlendiği **kanıtlanmadan** "çöktü" yazılmaz.
- Son native yüzdesi uzun süre artmazsa ilerleme duraksaması
  açıklanır; kaynak kayıt korunur.
- Yeni iş, eski işi REPLACE eder. Sadece etkin UUID'nin ilerleme
  ve sonuç kaydetmesine izin verilir.
- İptal kullanıcıya görünür. Eski tamamlanan ses parçaları kontrol
  noktası olarak kalabilir.
- Android ön plan işi kısıtları veya bellek azlığı başarı garantisi
  verilmeden kullanıcıya durum olarak yansıtılır.

### Ekran düzeni

- Küçük ekranda iki ölçüm kartı; geniş ekranda üç ölçüm kartı.
- Son okunan konuşma başlangıçta boş yer kaplamaz, metin gelince
  otomatik görünür.
- Aşama ve kaynak bilgisi kısa tutulur; ayrıntı ve tanı isteğe bağlıdır.
- Samsung Notes paylaşımı ayrı kullanıcı eylemi olarak kalır.
- Kullanıcı transkript veya rapor kaydını değiştirirse önceki
  kaydın sonuçları yeni kayda bağlanmaz.

### Kabul testleri

1. 203 saniyelik ses / 30 saniyelik parçalar = 7 bölüm.
2. 150/203 saniyelik kaynak işlendiğinde ses yüzdesi %73.
3. Native bölüm %0'a dönse bile genel ilerleme geriye gitmemeli.
4. Model yükleme sırasında sayı uydurulmamalı.
5. Açılır panel kapalıyken ekranın büyük bir kısmı boş görünmemeli.
6. Kayıt seçimi değiştiğinde eski metin yanlış özetlenmemeli.
7. İptal/başarı/hata durumları gerçek WorkManager işine bağlı olmalı.
8. 30 sn ve 3 dk gerçek Samsung kayıt testi ayrıca yapılmalı.

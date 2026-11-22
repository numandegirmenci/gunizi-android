# AI 2.0 — Uygulama ve test sırası

Ana tasarım: [AI_ARCHITECTURE_V010.md](AI_ARCHITECTURE_V010.md).
Mevcut sürüm: v0.9.1 Test. Bu plan yeni APK değil; geçiş yol haritasıdır.

| Faz | Yapılacak | Kabul kriteri |
|---|---|---|
| A — Motor tanılaması | Whisper yükleme, PCM decode ve inference ayrı ölçülür; model uyumluluğu kontrol edilir | 30 sn ve 203 sn gerçek ses: ya okunur Türkçe döküm ya açık hata |
| B — ASR sözleşmesi | SpeechEngine + LegacyWhisperAdapter; ayrı izole JNI deneyi | Kayıt hizmeti etkilenmez, native yaşam döngüsü testli |
| C — VAD ve veri | AudioPreflight, VoiceActivityDetector, ChunkTimeMapper, Room transkript segmentleri | Zaman damgası korunur, sessizliği atarken söz kaybı yok |
| D — Özet ve tablo | JSON kaynaklı TopicExtractor, TopicValidator, DailyReportComposer | Kaynaksız görev/karar doğrulanmış görünmez |
| E — Gerçek ilerleme | AnalysisCoordinator, tek job ID, WorkManager, heartbeat ve checkpoint | Ekran kapanınca kalan iş durumu/sonucu kaybolmaz |
| F — UI ve notlar | Kayıt merkezli analiz sekmesi, dalga formu, zaman bağlantılı özet, Notlarım | Bir özete dokununca kaydın ilgili saniyesi açılır |
| G — Dokuz saat ve yayın | Yük, kesinti, termal, pil, 9 saat kapsama, model karşılaştırması | Eksik konu ve kesintiler raporlanır; in-place imza kontrolü |

## Öncelikli, ölçülebilir test matrisi

1. **30 sn temiz Türkçe** — ASR base, small, Turbo seçeneğinin uyumluluğu ayrı.
2. **3 dk kaynak ve 1 dk sessizlik** — VAD açık/kapalı farkı, cümle başı-sonu kayıp testi.
3. **Birden fazla kişi/tarih/sayı** — ad, görev ve kesin karar doğruluğu.
4. **Bozuk Opus / izin iptali / az RAM** — normal hata ve veri kaybı olmaması.
5. **30 dk ses** — chunk checkpoint, saatlerin doğruluğu ve yinelenen söz testi.
6. **9 saat** — işlem iptali/yeniden başlatma, kaynak kapsama tablosu, pil/termal davranışı.
7. **Önceden çıkarılmış metin** — yeni rapor için Whisper'ın tekrar açılmadığı kanıtı.
8. **Notlar ve güncelleme** — eski kullanıcı dosyaları korunur; debug test paketi ana uygulamanın üzerine kurulmaz.

## Mevcut kodu koruyan geçiş

- v0.9.1 Test çalışmaya devam eder; RecorderService / Mesai / Geçmiş moduna ilk fazlarda dokunulmaz.
- İlk kod adımı ayrı AI domain ve motor sözleşmeleridir. Hemen devasa native değiştirme yapılmaz.
- Farklı native motorlar aynı örnek ses ve aynı metrikle A/B test edilir.
- Yeni mimari doğrulanıncaya kadar ana Release otomatik yayımlanmaz.
- İmza anahtarları GitHub Secrets'a özel olarak yüklenmeden gerçek silmeden güncelleme garantisi verilmez.

## Başarı tanımı

GitHub Actions derlemesinin yeşil olması tek başına yeterli değildir.
“Başarılı yerel AI” demek için cihazda kısa kayıttan doğru metin, metinden ayrı rapor,
gerçek stage bildirimi, kaynak segmentli tablo ve veri kaybı olmadan sürdürme kanıtı gerekir.

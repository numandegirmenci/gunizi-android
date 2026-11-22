# Günİzi v0.9 – Yapay Zeka arayüzü ve işlem güvenirliği

## Bölümlerin görevleri

| Sekme | Kullanıcının burada yaptığı |
|---|---|
| Kayıt | Geçici geçmiş dinleme; son 1 dk–6 saati kaydet |
| Mesai | Gün/saat planı ve otomatik kayıt |
| Yapay Zeka | Ses seç, yazıya dök veya konulara ayır; ilerlemeyi ve sonuçları incele |
| Ayarlar | Kayıt klasörü, Whisper/Qwen3 model yükleme, güncellemeler ve izinler |

### Yapay Zeka ana ekranı

1. **Ne yapmak istiyorsun?** Yazıya dök veya özetle+tablo oluştur.
2. **Canlı işlem durumu.** Kaynak dosya, yürüyen iş, aşama ve % ilerleme,
   son okunan konuşma veya o anda özetlenen konu.
3. **Sonuçlar.** Konuşma dökümü, genişletilebilir konu tablosu, Notlarım.
4. **Özetleme hedefi.** Kullanıcının talimatı yalnızca özetleme için uygulanır.
5. **Model durumu.** Kurulu/eksik bilgisi ve model ayarlarına bağlantı.

## Arka plan

Model indirmesi, Activity'den açılan sıradan `Thread` yerine
`AiModelDownloadWorker` ile yürütülür. Android WorkManager
`ForegroundInfo` bildirimini gösterir. İndirme kesilirse `.part`
dosyası korunur. Günlük özetleme de ayrı bir WorkManager foreground işidir.
Uygulamanın başka ekrana geçmesi, işin doğrudan iptal nedeni değildir.

Ancak Android, düşük bellek, iş kotası, pil optimizasyonu veya 6 saatlik
foreground service limitleri nedeniyle işleri durdurabilir. Bu senaryoda
kaydedilmiş kontrol noktaları, mümkün olan işlemleri yeniden kullanır.

## Neden Qwen3 %58'deyken özet çalışmıyordu?

Kullanıcı görselinde Whisper modelinin kullanıma hazır olduğu,
Qwen3 indirmesinin ise %58'de olduğu görülüyordu. Eski arayüz
hem yazıya dökmeyi hem özetlemeyi aynı yerde iki modele bağımlı kılıyordu.
Artık Whisper ile tek başına transkripsiyon seçeneği var. Özet/tablo
için Qwen3 tam indirilmeli ve SHA-256 doğrulanmalıdır.

## Kullanım testleri

- Whisper hazır / Qwen eksik: Yazıya dök aktif olmalı, özet bilgilendirmesi model kurulumuna yönlendirmeli.
- Model indirme başladı: uygulamadan ayrılınca bildirim ve ilerleme sürmeli.
- İndirme sırasında bağlantı kesildi: aynı modele tekrar başlatınca kaldığı yerden devam edilmeli.
- 1 dakikalık kayıt: döküm görüntülenip paylaşılmalı.
- Qwen hazır: özet ve konu tablosu ayrı sonuç vermeli.
- 9 saatlik kayıt: saat damgaları, kontrol noktası, süre ve RAM/pil davranışı ayrıca test edilmeli.
- Güncelleme ekranı: GitHub ile yüklü sürüm eşitse "Güncel" gösterilmeli.
- Mevcut imzayla uyumsuz APK eski uygulamanın üzerine önerilmemeli.

# Günİzi v0.9.1 – model yükleme sorunu ve test yöntemi

Ekran görüntülerinde 203 saniyelik bir kayıt seçildiğinde uygulama dört dakika boyunca
"Yerel konuşma tanıma modeli yükleniyor" adımında kalıyordu. Bu görüntü,
Whisper modelinin açılmaya çalışıldığını gösterir ancak açıldığını **kanıtlamaz**.

## Teknik olarak değişenler

- Monoton sistem saatinden saniyelik Geçmiş Kaydı görünümü;
  kayıt ve mesai hizmeti aynen korunur.
- Whisper modelinin açılması ile audio döküm aşamasının ayrılması.
- Geçen toplam süre, uzun bekleme açıklaması, WorkManager görev sonucu.
- Tamamlanmış yerel konuşma dökümünün tekrar Whisper çalıştırmadan Qwen3'e verilmesi.
- Ses dökümü olmadan "özet hazır" sonucunun üretilmemesi.
- Tek tıkla e-posta/GitHub geliştirici iletişimi.

## Model uyumluluğu

Kullanılan `dev.ffmpegkit-maintained:whisper-android:1.0.0` paketinin açık
dokümanında **base** ve **small** çok dilli modellerin desteklendiği belirtiliyor.
Modelin `ggml-large-v3-turbo-q5_0.bin` quantize biçiminin bu ücretsiz paketle
uyumluluğu henüz doğrulanmadı. Bu model deneysel olarak gösterilir.

Kısa bir konuşma testi için:
1. Ayarlar → Yapay Zeka Modelleri → **Whisper base** seç ve indir (yaklaşık 148 MB).
2. Yapay Zeka → **Konuşma metnini çıkar** → 30 saniye–3 dakika ses kaydı seç.
3. Model hazırlanıyor, ses bölümü işleniyor, tam konuşma metni hazır
   aşamalarını izle. Sonuç yoksa "Hata" metnini paylaş.
4. Qwen3 kuruluysa **Çıkarılan konuşma metnini özetle ve tablolaştır** ile
   aynı metin tekrar Whisper çalışmadan işlenir.
5. Yerel sonuçların doğruluğunu orijinal sesle karşılaştır.

## Sınırlar

Bir modelin native (JNI) işlem çağrısı tamamen kilitlenirse Java/Kotlin
tarafındaki iptal isteği onu anında sonlandıramayabilir. Bu sürümde
durum raporu geliştirilmiştir; tüm cihazlarda dört dakika bekleme hatasının
kesin çözüldüğü iddia edilmez.

## İmza

Sonraki yayın için tek, kalıcı, güvenli release anahtarı oluşturulmalı.
[İmza rehberi](UPDATE_SETUP.md). Debug APK'ların imza uyumsuzluğu uygulamayı
kaldırmadan güncellemeyi engelleyebilir.

# Günİzi v0.8 – Yapay zeka tasarım notları

## Kullanıcı ne istiyorsa onun özetini çıkarma

Rapor ekranındaki **"Yapay zeka neye dikkat etsin?"** kutusu bir varsayılan
talimatla gelir. Kullanıcı iş konularını, kararları, kimden ne beklendiğini,
son tarihleri, belirli kurum ve projeleri belirtebilir. Metin analizin her
parçasına uygulanır ve o analiz sırasında kullanılan ayar ayrıca saklanır.

Kullanıcı sessizlik, gündelik sohbet veya konu dışı konuşmayı kısaltabilir.
**Belirsiz kişi/tarih/karar** otomatik olarak kesin kabul edilmemelidir.

## Modeller

| Aşama | Hafif / dengeli | Daha güçlü |
|---|---|---|
| Türkçe ASR | whisper.cpp small multilingual | whisper.cpp large-v3-turbo-q5_0 |
| Metin yorumlama | Qwen3-1.7B Q4_K_M | Qwen3-4B Q4_K_M |

Model dosyaları Hugging Face üzerinden kullanıcının açık onayıyla indirilir.
SHA-256 doğrulaması yapılır. Turbo modeli kaynağı:
https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-large-v3-turbo-q5_0.bin

## Günlük rapor algoritması

1. MediaCodec ile uzun ses 16 kHz mono akışta çözülür, 5 dakikalık WAV
   parçaları elde edilir. Bütün 9 saati aynı anda RAM'e yüklemez.
2. Whisper parça parça Türkçe konuşmayı saat damgalarıyla çıkarır.
   Tamamlanan her parça için özel, kalıcı olmayan **yerel kontrol noktası**
   tutulur. Yeniden denemede tamamlanan parçalar atlanır.
3. Qwen3, her metin parçası için **kısa özet + 6 alanlı konu/karar/görev
   tablosu** üretir. Her parçanın kontrol noktası ayrıca saklanır.
4. Bütün günün parça analizleri birleştirilip genel değerlendirme üretilir.
   Her bölümün ayrıntısı raporda korunur. İlk 9500 karakter sınırı kaldırıldı.
5. Konular başlıklarına göre birleştirilir. Saat/olay satırları atılmaz.
   HTML tabloları, CSV ve tam konuşma dökümü seçilen klasöre kaydedilir.
6. **Notlarıma Ekle** ile kullanıcı gözden geçirip düzenleyerek özel not
   olarak kaydedebilir ve gerekirse Android paylaş menüsünden Samsung Notes'a
   aktarabilir.

## Cihaz ve güvenilirlik

Android 15+ uzun süren foreground mediaProcessing işlemlerine sınırlamalar
getirebilir. 9 saatlik yerel analizin 9 saatten kısa süreceği garanti edilmez.
İşlem durursa kontrol noktaları kalan işin tekrarlanmasını azaltmayı hedefler.

Whisper'ın yazdığı metin, sesin mükemmel bir kopyası değildir. Qwen3
görev/karar üretebilir veya yanlış çıkarabilir. İş günü kayıtlarından
türetilen kararları kullanmadan önce ses kaydındaki zamanları inceleyin.

Notlar uygulamanın özel alanında saklanır. Uygulama kaldırılırsa kullanıcı
notları da silinebilir; bu nedenle TXT olarak dış klasöre aktarma veya
Samsung Notes paylaşımını kullanın. Bu uygulama Samsung Notes veri tabanına
gizli/yetkisiz erişim yapmaz.

## Yayın

v0.8.0 sürümü için kaynak kod / test derlemesi bulunabilir; kalıcı özel
imzalama anahtarı olmadan güvenli in-place güncelleme dağıtımı tamamlanmaz.
[İmza kurulum rehberi](UPDATE_SETUP.md).

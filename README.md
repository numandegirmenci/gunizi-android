# Günİzi Android • v0.3 (Deneysel)

**Durum:** Android debug APK üretiliyor; gerçek cihazda 9 saatlik yük testi henüz yapılmadı.

Günİzi, Türkçe çevrimdışı ses kaydı ve yerel yapay zeka ile günlük rapor amaçlayan Android uygulamasıdır.

## Ekran tasarımı
Ana ekran artık tek uzun liste değil: **Kayıt, Mesai, Rapor, Ayarlar** olmak üzere dört kompakt sekme bulunur.

## Kayıt modları

**İsteğe Bağlı Kayıt**
- Kullanıcı **15 dakika / 1 saat / 6 saat** seçer ve kayıt düğmesine basar.
- Mikrofon kullanıcı başlatmadan çalışmaz.
- Seçilen süre bittiğinde kayıt durdurulur ve seçilen klasöre Opus/OGG olarak aktarılır.
- Durdur ve Kaydet düğmesi kaydı erken bitirir.
- Bu mod **geçmişe dönük kayıt değildir**; mikrofon önceden kapalıysa eski sesler geri getirilemez.
- 6 saatlik kayıt yaklaşık 43,2 MB teorik Opus ses alanı tutar (kapsayıcı + yedek ek olabilir).

**Mesai Kaydı**
- Çalışma günleri, başlangıç/bitiş saatleri ve klasör bir kez ayarlanır.
- Kullanıcı hizmeti uygulama açıkken etkinleştirir; hizmet çalışmaya devam ettiği sürece saatlerde otomatik kayıt tutar.
- Android 14+ mikrofon kısıtlamaları nedeniyle sistem hizmeti kapatmışsa uygulama kendi başına, izinsiz yeniden başlayamaz.
- Mesai bitiminde kayıt klasöre taşınır; tamamlanmış kayıt için geçici şifreli yedek kullanılır.
- Aktif kayıt dosyası henüz sürekli olarak şifrelenmemektedir.

## Gerçek yerel yapay zeka (Whisper + Qwen3)

Bu prototipte artık iki ayrı model katmanı bulunmaktadır:

1. **Whisper small multilingual**: yaklaşık 488 MB. Türkçe dahil çevrimdışı konuşma tanıma.
2. **Qwen3-1.7B Q4_K_M**: yaklaşık 1,28 GB. Yerel dil modeli ile üretken Türkçe özetleme.

Her iki model de **Rapor** sekmesinden kullanıcının açık onayıyla indirilir. Özgün model indirme URL'leri:
- Whisper: https://huggingface.co/ggerganov/whisper.cpp
- Qwen3: https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF

İndirme sonrasında SHA-256 dosya bütünlüğü doğrulanır. Kayıt, çözümleme ve özetleme sırasında ses veya döküm hiçbir internet sunucusuna gönderilmez.

### 9 saatlik rapor yöntemi
- Android MediaCodec ile ses **akış halinde çözülür** ve beşer dakikalık geçici WAV parçaları oluşturulur.
- Whisper, parçaları tek tek Türkçe metne dönüştürür; saatler korunur.
- Tam döküm kaydedilir; model hatası özetlemeden önce oluşsa bile verinin korunması hedeflenir.
- Qwen3, zaman damgalı metni parçalara ayırarak özetler ve sonra parçaları birleştirerek genel rapor hazırlar.
- Ara Qwen3 özetleri uygulamanın özel alanında kontrol noktası olarak saklanır.
- Sonuç: **günün özeti + kronoloji + kararlar + yapılacaklar + bekleyenler + tam konuşma dökümü**.
- İşlem saatler sürebilir. Android'in arka plan çalışma sınırları veya ısı/pil kısıtlamaları yüzünden durdurulabilir. Cihazda uçtan uca doğrulama henüz yapılmadı.

**Önemli:** Model konuşmayı hatalı algılayabilir ve dili yanlış yorumlayabilir. Özet, ses kaydıyla doğrulanmalıdır. Sınırsız doğruluk veya tüm konuşmaların mutlaka eksiksiz çözüleceği vaat edilmez.

## İzinler ve pil
- Mikrofon izni kullanıcıdan açıkça istenir.
- Bildirim izni kullanıcıya bırakılır.
- Seçilen kayıt klasörüne Android Storage Access Framework üzerinden kalıcı erişim alınır.
- Pil karşılaştırma ekranı seçili manuel kayıt süresi ile mesai süresini gösterir; **gerçek ölçümler yerine varsayımsal hesapları açıkça belirtir**.
- Yerel yapay zeka yoğun CPU/RAM kullanabilir. Otomatik raporlar varsayılan olarak şarj koşulu sağlanınca başlar.
- Bir cihazın gizlilik ve mikrofon göstergeleri Android tarafından yönetilir; uygulama bunları gizlemez.

## APK derleme
**GitHub Actions → Android Debug APK → son başarılı çalışma → Artifacts → GunIzi-debug-apk**.

APK, test amaçlı debug paketidir. Eski debug sürümü farklı imzalanmışsa güncelleme yüklenmeyebilir; eski sürümü kaldırmadan önce ses kayıtlarını ayrıca yedekleyin.

## Kaynak/krediler
Gecko projesinin RAM tabanlı ses belleği fikri ilk prototipe ilham vermiştir; Günİzi'nin bu sürümü iki farklı çalışma mantığı ve ayrı kod tabanıyla geliştirilmektedir.
- https://github.com/anantdark/Gecko
- https://github.com/ffmpegkit-maintained/whisper
- https://github.com/ffmpegkit-maintained/llama-android
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

Başkalarının konuşmalarının kaydı için gerekli bilgilendirme ve izinlere dikkat edin.

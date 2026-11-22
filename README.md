# Günİzi Android • v0.5 (Deneysel)

**Durum:** Android debug APK deneysel; gerçek cihazda 9 saatlik yük testi henüz yapılmadı.

Günİzi, Türkçe çevrimdışı ses kaydı ve yerel yapay zeka ile günlük rapor amaçlayan Android uygulamasıdır.

## Ekran tasarımı
Ana ekran artık tek uzun liste değil: **Kayıt, Mesai, Rapor, Ayarlar** olmak üzere dört kompakt sekme bulunur.

## Kayıt modları (v0.4)

**Kayıt sekmesi — Geçmiş Kaydı**
- **Dinlemeyi Aç** düğmesiyle kullanıcı mikrofonu bilinçli olarak açar ve geçmiş birikmeye başlar.
- **1 dk / 5 dk / 15 dk / 1 saat / 2 saat / 4 saat / 6 saat** seçimleri gelecekteki kayıt süresi DEĞİLDİR. Kullanıcı düğmeye bastığı **andan geriye doğru** birikmiş ses seçilen klasöre kaydedilir.
- Örnek: Dinleme 38 dakikadır açıksa "Son 15 dakikayı kaydet" düğmesi önceki 15 dakikayı aktarır.
- Örnek: Sadece 8 dakika birikmiş ve 15 dakika seçilmişse mevcut 8 dakikalık geçmiş kaydedilebilir; olmayan geçmiş uydurulmaz.
- Canlı dairesel panel, biriken geçmişin gerçek süresini gösterir.
- Kaydetmek dinlemeyi durdurmaz. Dinlemeyi durdurmak kaydedilmemiş geçici geçmişi siler.
- Sesler **yalnızca kullanıcı dinlemeyi başlattıktan itibaren** elde edilir. Mikrofon daha önce kapalıysa eski sesler yoktur.
- 6 saatlik ham PCM yaklaşık 659 MiB gerektirdiğinden bu prototip **RAM-only** değil, cihaz içindeki **geçici özel önbellekte 30 saniyelik Opus/OGG dosya parçaları** kullanır. 16 kbps 6 saat teorik ses büyüklüğü ~43,2 MB'dir. Kaydetmeden dış klasöre aktarılmaz, dinleme durduğunda temizlenir.
- Dışa aktarma seçilen geçmişe ait Opus örneklerini yeniden sıkıştırmadan OGG içine birleştirmeyi dener; segment geçişlerinde kısa süreli kesinti olabilir. Bu yeni işlev henüz gerçek cihazda ve 6 saatlik yükte doğrulanmadı.
- Geçici ses parçaları şu an **şifrelenmemiş uygulama özel önbelleğindedir**. Kalıcı olarak seçilen klasöre aktarmadan önce güvenli yedekleme uygulanır. Gelecek sürümde geçici kayıt şifrelemesi hedeflenmelidir.

**Mesai sekmesi — Otomatik Mesai Kaydı**
- Çalışma günleri, başlangıç / bitiş saatleri ve klasör bir kez seçilir.
- **Telefonun yerel saati** kullanılır; kullanıcıya teknik saat dilimi ifadesi gösterilmez.
- Kullanıcı mikrofon hizmetini uygulama açıkken etkinleştirir; hizmet çalıştığı sürece seçilen saat aralığında otomatik kayıt başlar / biter.
- Mesai sonunda dosya seçilen klasöre taşınır ve tamamlanmış kayıt için şifreli yedekleme yapılır.
- Android 14+ arka plandan yeni mikrofon hizmeti başlatmayı kısıtlar. Sistem hizmeti kapatırsa 08.00'de kendi kendine yeniden başlayacağı garanti edilemez.
- **Mesai kayıt mantığı v0.3 ile aynıdır**, yalnızca saat algısı telefonun yerel zamanına uyarlanmıştır.

## Gerçek yerel yapay zeka (Whisper + Qwen3)

Bu prototipte artık iki ayrı model katmanı bulunmaktadır:

1. **Whisper small multilingual**: yaklaşık 488 MB. Türkçe dahil çevrimdışı konuşma tanıma.
2. **Qwen3-1.7B Q4_K_M**: yaklaşık 1,28 GB; dengeli çalışma. **Qwen3-4B Q4_K_M**: yaklaşık 2,5 GB; güçlü cihazlarda daha ayrıntılı özetleme seçeneği.

Her iki model de **Rapor** sekmesinden kullanıcının açık onayıyla indirilir. Özgün model indirme URL'leri:
- Whisper: https://huggingface.co/ggerganov/whisper.cpp
- Qwen3 1.7B: https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF
- Qwen3 4B: https://huggingface.co/ggml-org/Qwen3-4B-GGUF

İndirme sonrasında SHA-256 dosya bütünlüğü doğrulanır. Kayıt, çözümleme ve özetleme sırasında ses veya döküm hiçbir internet sunucusuna gönderilmez.

### 9 saatlik rapor yöntemi
- Android MediaCodec ile ses **akış halinde çözülür** ve beşer dakikalık geçici WAV parçaları oluşturulur.
- Whisper, parçaları tek tek Türkçe metne dönüştürür; saatler korunur.
- Tam döküm kaydedilir; model hatası özetlemeden önce oluşsa bile verinin korunması hedeflenir.
- Seçilen Qwen3 modeli, zaman damgalı metni parçalara ayırarak özetler ve sonra parçaları birleştirerek genel rapor hazırlar. Parça özetleri genel rapora eklenir.
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

## GitHub'dan otomatik sürüm kontrolü (v0.5)

- **Ayarlar → Uygulama güncellemeleri → Otomatik güncelleme kontrolü** varsayılan olarak açıktır.
- Android WorkManager yaklaşık 12 saatte bir **GitHub Releases / latest** bilgilerini kontrol eder (Android görevi geciktirebilir).
- Yeni kararlı sürüm varsa bildirim gösterilir. Wi-Fi / ölçüsüz bağlantıda APK ancak GitHub Release SHA-256 digest bilgisi olduğunda otomatik indirilir ve doğrulanır.
- **Android otomatik/sessiz APK kurulumuna izin vermez.** Kullanıcı bildirimden yükleyiciyi açar ve onaylar.
- Sadece `main` dalına kaynak kod yüklemek güncelleme yayınlamak değildir: GitHub'da **imzalı bir Release** oluşturulmalıdır.
- Uygulamaların birbiri üzerine kurulabilmesi için APK'ların **aynı kalıcı anahtarla imzalanması gerekir**. Her GitHub Actions debug derlemesinin geçici anahtarı farklı olabilir. Eski debug APK'dan imzalı yayın sürümüne doğrudan güncelleme mümkün olmayabilir.

### Tek seferlik imza kurulumu (gerekli)

GitHub deposu **Settings → Secrets and variables → Actions** altında dört özel secret tanımlanmalı:

- `GUNIZI_KEYSTORE_BASE64`: kişisel release keystore dosyasının Base64 içeriği
- `GUNIZI_KEYSTORE_PASSWORD`: keystore şifresi
- `GUNIZI_KEY_ALIAS`: anahtar takma adı
- `GUNIZI_KEY_PASSWORD`: anahtar şifresi

**Keystore'u veya parolasını herkese açık GitHub deposuna ASLA yükleme.** Key kaybolursa sonraki güncellemeler eski kurulumların üzerine yüklenemez. `.github/workflows/release-apk.yml` dosyası kalıcı anahtarı GitHub secrets üzerinden kullanarak imzalı APK üretir. Secrets tanımlanmadan çalıştırılırsa güvenli biçimde hata verir.

Secret kurulumundan sonra `v0.5.0` gibi bir git etiketi yayınlandığında **Signed Android Release** workflow tetiklenir, imzalı APK GitHub Releases'e yüklenir. APK'nın `versionCode` değeri bir öncekinden yüksek tutulmalı. Kayıt ve modellerin silinmemesi için cihazdaki sürümle yeni sürümün imzası eşleşmelidir.

## Konu konu günlük özet tablosu (v0.5)

Yerel **Whisper + seçilen Qwen3** modelleri kuruluysa tamamlanan ses dosyası işlem sırasında 5 dakikalık bölümler halinde yazıya çevrilir, konu başlıkları ve saatler çıkarılır. Raporun yanında şu dosyalar oluşur:

- `GunIzi_Ozet_*.txt`: genel ve ayrıntılı günlük rapor
- `GunIzi_Dokum_*.txt`: algılanan konuşmanın tam dökümü
- `GunIzi_KonuTablosu_*.html`: telefon veya bilgisayarda açılan konu tablosu
- `GunIzi_KonuTablosu_*.csv`: Excel'e aktarılabilen konu tablosu

**Rapor → Konu konu rapor tablosu** ekranında aynı konu tablosunun yerel bir kopyası gösterilir. Sütunlar **Saat / Aralık, Konu, Konuşulanlar, Alınan karar, Takip edilecek iş, Sorumlu**. Model bir konuyu sınıflandıramazsa gerçek dışı karar veya sorumlu uydurulmaz. Modellerin gerçek telefonda tüm gün çalışması henüz uçtan uca doğrulanmadı.

## Bildirimler ve telefon görüşmeleri

- Mikrofon aktifken foreground bildirimini Android zorunlu tutar. Günİzi bunu **sessiz, düşük öncelikli** gösterir; "Daha fazla bildirim" konumunu Samsung belirler.
- Ayarlar'da doğrudan kanal bildirim ayarlarına gitme seçeneği bulunur.
- Normal üçüncü taraf uygulamalar hücresel aramanın iki tarafını güvenilir biçimde kaydedemez. Bu yetki genellikle sistem arama uygulamasıyla sınırlıdır. Günİzi arama sesi kaydı özelliği olduğunu iddia etmez.

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

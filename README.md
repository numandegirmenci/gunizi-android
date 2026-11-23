> **GitHub derleme dalı:** `belgeizi-v0.1.1-build`. Bu dal, başka bir projeye ait `main` dalını etkilemeden derleme amacıyla ayrılmıştır. Ayrı bir depo oluşturulduğunda taşınmalıdır.

# Belge İzi — Android v0.1.5 (Otomatik Test ve APK Dağıtımı)

Türkçe belge tarama, OCR ve PDF işleme için **orijinal tasarımlı** Android uygulaması.

> Durum: GitHub Actions ile APK oluşturulan test sürümüdür. Fiziksel telefonda kalite ve kararlılık ayrıca doğrulanmalıdır.

## v0.1.5 — Otomatik kalite kapısı

- `.github/workflows/android.yml`: Her kod değişikliğinde Python kalite aracı testleri, sentetik Türkçe belge görüntüleri, Android JVM regresyonları, lint ve APK derlemesi çalışır.
- Testlerden biri başarısız olursa yayımlama durdurulur.
- Başarılı derlemede APK, GitHub Actions'ta arşivlenir; derleme dalında GitHub Releases üzerinden ZIP açmadan doğrudan indirilebilen bir APK yayımlanır.
- Gün İzi'nin `main` dalına ve `Latest` sürümüne dokunulmaz (`--latest=false`).
- Sabit imza için `BELGEIZI_*` GitHub sırları gerekir. Sırlar yoksa yalnız **test APK** yayımlanır; farklı debug imzasıyla kurulmuş APK'lar veriler silinmeden güncellenemeyebilir.
- Doğrulama kodu ve örnekler: [qa/README.md](qa/README.md); anahtar güvenliği: [docs/SIGNING.md](docs/SIGNING.md).
- Bu otomatik denetimler gerçek cihazdaki OCR başarısını kanıtlamaz. Kaynak/görüntü testleri için aynı belgeyi gerçek cihazda ayrıca ölçmek gerekir.

## v0.1.4 Görüntü geliştirme
- Tarama sonrasında AUTO görüntü geliştirme otomatik uygulanır.
- Geliştir: Otomatik, Canlı renk, Keskin siyah-beyaz, Orijinal seçenekleri.
- Yeni belgelerde orijinal JPEG ayrı korunur; geliştirmeyi kapatmak mümkündür.
- OCR otomatik çalışır. OCR düğmesi ve sayı/uyarı balonu belge ekranından kaldırılır.
- Metin seçimi isteğe bağlı "Metin" aracıyla açılır; görünür OCR katmanı yoktur.
- ML Kit okuma sırası korunarak parça-karışma problemi azaltılır.
- PDF boyut optimizasyonu ve kitap kıvrımı düzeltme bu sürümde tamamlanmış değildir.

## v0.1.3: Türkçe karakterleri otomatik tanıma

- Dosya açma ve tarama sonrasında Türkçe OCR otomatik uygulanır.
- ML Kit'in sonucunu çevrimdışı Tesseract Türkçe `tessdata_best` modeli de inceler.
- Birbiriyle uyuşmayan kelimelerde model güveni ve Türkçe harf ayrımı kullanılır; bütün i harfleri ı'ya çevrilmez.
- Örnekteki gibi `Masallari` okumasında otomatik Türkçe düzeltme motoru devreye girer.
- Önceki sürümde tanınmış belgeler, kullanıcı düzeltmesi içermiyorsa açılınca tekrar otomatik taranır.
- Orijinal sayfa görüntüsü ve kullanıcı tarafından yapılmış düzenlemeler korunur.
- Türkçe model APK içinde yer alır; ek indirme ve internet gerekmez.
- Bu APK kolu ARM64 Android içindir; canlı cihaz doğruluğu henüz test edilmemiştir.

## İlk sürümde kodlanan işlevler

- Google ML Kit Document Scanner ile kamera taraması (çok sayfa, kırpma, filtreler)
- PDF ve görüntü açma; Android dosya seçici üzerinden OneDrive ve diğer dosya sağlayıcıları
- Android `ACTION_VIEW`, `ACTION_SEND`, `ACTION_SEND_MULTIPLE` ile e-posta / paylaşım eklentilerinden açılma
- PDF'yi görsele dönüştürerek sayfalara ayırma (en fazla 40 sayfa)
- Cihazla birlikte gelen (bundled) ML Kit Latin OCR ile Türkçe karakterleri de içeren metin tanıma
- OCR satırlarına dokunarak düzeltme, düzenlemeleri yerel JSON metadata ile kaydetme
- Parmakla imza çizimi ve sayfalara şeffaf PNG olarak ekleme
- Metin kaşesi veya dosyadan görsel kaşe ekleme; taşıma, büyütme/küçültme, silme
- Orijinal görüntü + OCR metin katmanı + işaretler ile PDF oluşturma
- OCR metnini tüm sayfalardan panoya kopyalama
- İnternet gerektirmeyen yerel belge kasası (kamera taramasının **ilk kullanımında** Google Play services modülü indirilebilir)

## Kurulum ve APK üretme

1. Bilgisayara güncel Android Studio kur; Android SDK Platform 36 ve Build Tools'u Android Studio içinden indir.
2. Bu klasörü **File > Open** ile Android Studio'da aç.
3. Projenin **JDK 17 veya uyumlu yeni bir JDK** kullandığından emin ol. Gradle 8.13 gerekir.
4. Windows'ta proje klasöründeki `build-apk.ps1` dosyasını PowerShell'de `powershell -ExecutionPolicy Bypass -File .\build-apk.ps1` ile çalıştır. Bu betik resmi Gradle 8.13 dağıtımını indirir, resmi SHA-256 özetini doğrular, derleme başlatır.
5. Başarılı derleme sonunda `app/build/outputs/apk/debug/app-debug.apk` oluşur. Dosyayı Android telefona aktar ve kur.
6. Android 8.0+ (API 26+) gerekir. Google Play hizmetleri bulunan cihazlarda kamera taraması çalışır.

**Not:** Resmi Gradle wrapper JAR bu arşivde bulunmuyor. Bu nedenle Android Studio'da Gradle konumunu kurulu Gradle 8.13 olarak göstermek veya yukarıdaki `build-apk.ps1` yolunu kullanmak gerekir. `gradlew.bat` betiği aynı Windows derleme akışını başlatır.

### GitHub Actions ile otomatik derleme ve tek tık APK

Kaynak dalı: `belgeizi-v0.1.1-build` (isim geçmişten kalmıştır, Android uygulama sürümü `0.1.5`).
Geliştirme dalı: `feature/belgeizi-ci-v015`.

Geliştirme dalında otomatik testler yapılır. Kontrollerden geçen sürüm derleme dalına aktarıldığında Actions testi tekrar gerçekleştirir ve başarılıysa **GitHub Releases > Belge İzi** altında doğrudan .apk indirilebilir.

Sabit imza sırları henüz eklenmemişse yalnızca test APK'sı çıkar. Test APK'sı üretim sürümü değildir. [İmzalama kurulumu](docs/SIGNING.md).

## Kullanım

1. **Yeni belge tara** ile çok sayfalı çekim yap; veya **Dosyadan aç** ile PDF/fotoğraf seç.
2. Belge açıldığında Türkçe metin tanıma otomatik yapılır; ayrı OCR düğmesi yoktur.
3. Gerekirse **Metin** aracıyla kelimelere dokunarak metin düzenle; görüntüyü **Geliştir** menüsünden ayarla.
4. **İmza** ile parmakla çiz; **Kaşe** ile metin veya görsel ekle. İşaretler sürüklenebilir, dokunarak boyutlandırılabilir.
5. Üstteki **PDF** butonuna bas; telefona veya bir bulut sağlayıcısına kaydet.
6. **Kopyala** ile tanınan tüm metni al.

## Bilinen sınırlamalar

- Bu MVP **Adobe Scan'in birebir klonu değildir**; arayüz tasarımı özgündür.
- OCR tek motor (ML Kit Latin). **PaddleOCR henüz ekli değil.** Türkçe başarı oranı ölçülmedi. Tablo hücresi/kolon yapısını tanıma, belge anlamsal analizi, düşük güven skorlu alan tespiti sonraki fazdır.
- Mevcut görseldeki OCR metni düzenleme, eski metin bölgesini **düz beyazla kapatarak** yeni yazıyı yerleştirir. Zemin, çizgi, kaşe üzerine gelen düzenlemelerde görüntü bozulabilir. Gerçek fon/karakter rekonstrüksiyonu ve belge tipografisi eşleştirme **henüz yok**.
- PDF'de OCR metni görüntünün altına çizilerek metin katmanı amaçlanır. Farklı PDF okuyucularda **metin arama ve seçim davranışı henüz test edilmedi**.
- Metin düzeltme satır bazlıdır; çok satırlı akış/reflow ve yazı tipi eşleştirme yoktur.
- PDF içerikleri sayfaya yeniden rasterize edilir; dijital imzalar, interaktif PDF formları, vektörel katmanlar ve özgün şifreleme **korunmaz**.
- İmza görseldir, nitelikli elektronik imza değildir. **Kamu/kurumsal belgelerde izinli ve yetkili kullanım gerekir.**
- İleri düzey fotoğraf filtreleri, özel CameraX arayüzü, çok sayfalı toplu düzenleme, kırpma sonrası yeniden düzeltme, zoom, doküman senkronizasyonu yoktur.
- Yerel dosyalar uygulama kaldırılırsa silinir; önemli belgeleri PDF olarak dışa aktar.
- PDF sayısı 40 ile sınırlıdır; büyük PDF'lerde telefonun RAM'ine bağlı olarak yavaşlık görülebilir.

## Mimari

- `MainActivity` — Android paylaşımdan / dosya açma akışından alınan URI'ler
- `ui/ScanApp.kt` — Compose uygulama ve belge düzenleyici
- `ui/SignaturePad.kt` — şeffaf el yazısı imzası
- `data/DocumentStore.kt` — `content://` içe alma, JPG sayfalar, JSON kayıt
- `engine/OcrEngine.kt` — ML Kit V2 Latin OCR
- `engine/PdfWriter.kt` — sayfa görüntüleri + metin + imza/kaşe dışa aktarma

## Kalite kontrolü için önerilen test belgeleri

- Net basılmış Türkçe metin: i/ı/İ/I, ğ/Ğ, ş/Ş, ö/Ö, ü/Ü
- Düşük ışık, gölge, yamuk sayfa, el yazısı, karmaşık mühür ve kaşe
- Kira sözleşmesi, ruhsat evrağı, tapu bilgisi, satır/sütunlu tablo
- OneDrive'dan PDF, Gmail/Outlook'tan PDF veya JPEG paylaşma
- 1, 10, 40 sayfalı PDF
- İmza/kaşe: yer değiştirme, boyutlandırma, kaydetme ve PDF'de kontrol
- OCR metnini düzenle, kapat/aç, veri kaybı var mı kontrol et

## Sonraki sürüm v0.2 hedefi

Gerçek cihazda ilk derleme/test sonrası OCR değerlendirme veri kümesi, zor belge türleri için PaddleOCR ikinci motor, sayfa yakınlaştırma ve daha güvenilir metin katmanı/düzenleme uygulanmalı.

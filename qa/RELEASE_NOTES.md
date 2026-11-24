Belge İzi v0.1.6 — Modern düzenleme ekranı ve diğer uygulamalardan düzenleme (TEST)

- Yenilenmiş ana ekran: belirgin belge tarama, dosyadan açma, belge arama ve belge kartları.
- Yenilenmiş belge düzenleyici: geniş belge alanı, sayfa gezintisi ve yatay araç çubuğu.
- Geliştir seçenekleri yeniden tasarlandı: Otomatik, Canlı Renk, Keskin Siyah-Beyaz, Orijinal.
- Üstte Kaydet ve Paylaş işlemleri. Paylaş için özel bir FileProvider kullanılır.
- Android'in dosya düzenleme, birlikte aç ve paylaş menülerinde Belge İzi ile düzelt seçeneği desteklenir.
- Bu seçenek, dış uygulamanın desteklediği Android intent türüne ve dosya izinlerine göre görünür.
- Harici dosyalar önce uygulamaya kopyalanır; orijinal dosya üzerine otomatik yazılmaz.
- Kaydedilen PDF, kullanıcı seçtiği konuma aktarılır. Diğer uygulamaya doğrudan dönüş onun entegrasyonuna bağlıdır.
- Otomatik Türkçe OCR, imza, kaşe ve mevcut görüntü iyileştirme altyapısı korunur.
- GitHub Actions üzerinden Android testleri, lint, APK imza/paket kontrolü ve Release yayımlanır.

Bu bir TEST APK'sıdır. Gerçek cihazda düzenleme menüleri, uygulama paylaşımı ve
OCR kalitesi ayrıca denenmelidir. Kalıcı imzalama anahtarı yoksa mevcut APK'nin
üzerine sorunsuz yükleme garanti edilemez. Belgelerinizi yedeklemeden kurulu
uygulamayı silmeyin.

Gün İzi uygulaması bu çalışmadan etkilenmez.

Belge İzi v0.1.8 — Otomatik hizalama ve hızlı görüntüleme (TEST)

- Ana ekranda Otomatik düzelt anahtarı: çekim sonrasında eğim ölçümü arka planda yapılır.
- Düzenleme araçlarında Düzelt ve Geri al: yalnızca güvenilir ve tutarlı metin satırlarıyla hafif eğim düzeltir.
- Belge tarayıcının hazır kenar/perspektif düzeltme akışı korunur. Yeni Düzelt aracı bir perspektif iyileştirme motoru değil, kontrollü sayfa döndürmedir.
- Düzeltme kaynak görüntüyü değiştirmez; dönüştürülen çalışma kopyası PNG olarak kaydedilir.
- Açı tespiti belirsizse sayfa değiştirilmez. Üzerinde imza, kaşe veya manuel OCR düzenlemesi olan sayfa korunur.
- Düzeltme sonrası OCR koordinatları geçersiz sayılır ve yeniden hesaplanır.
- Kamera JPEG'i ilk kayıt sırasında tekrar renk işlenmez. Orijinal tam çözünürlüklü JPEG bozulmadan korunur.
- Çekim sonrası editör açılması OCR bitişine bağlı değildir; OCR arka planda çalışır.
- Önizleme en fazla yaklaşık 2100 px örneklenerek daha az RAM tüketir.
- Eski belgelerin metadata şemasıyla uyumluluk korunur.

Sınırlama: Düşük ışık, kıvrımlı sayfalar, perspektif yamukluğu veya çok az yazı bulunan
belgelerde güvenilir otomatik döndürme yapılamayabilir. Görüntü rotasyonu yeniden
örnekleme içerdiği için tüm baskı türlerinde birebir piksel eşdeğerliği garanti edilmez.
Google Belge Tarayıcı kenar/perspektif düzeltme için ayrı kullanılabilir.

CI, JVM regresyonları, lint ve debug APK kontrolü vardır; gerçek cihazda kamera
ve performans ölçümü halen gereklidir. Test APK üretim imzası değildir.
Gün İzi ana dalı değiştirilmemiştir.

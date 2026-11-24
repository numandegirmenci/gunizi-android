Belge İzi v0.1.9 — PDF kaydetme performansı (TEST)

DOĞRULANAN MEVCUT ÖZELLİKLER (v0.1.8):
- Ana ekranda Otomatik düzelt (varsayılan açık) bulunur.
- Düzenleyicide Düzelt / Geri al seçenekleri vardır.
- Güvenilir kısa metin baselinesinden hesaplanan hafif eğiklik düzeltilir; özgün görüntü saklanır.
- Dört köşe perspektif düzeltme için ayrı "Otomatik belge tarama" ekranı kullanılır.
- Düzeltme, metinleri yeniden üretmez; döndürme sırasında yeniden örnekleme olabilir.

YENİ PDF HIZ OPTİMİZASYONU:
- JPEG çalışma sayfaları, görünür metin düzenlemesi, imza ya da kaşe yoksa
  doğrudan PDF JPEG XObject olarak aktarılır; JPEG baytları yeniden sıkıştırılmaz.
- Mevcut OCR kelimeleri görünmez metin katmanı ve UTF-16 ToUnicode CMap ile korunur;
  Türkçe harfler aramada ve kopyalamada kalması için eşlenir.
- Sayfaların her biri doğrudan kaynaktan aktarılır; büyük bitmap belleğine gerek olmaz.
- PDF ile birlikte görüntü pikselleri değiştirilmez; PDF içindeki JPEG baytları
  taranan kaynak JPEG ile birebir aynıdır.
- Karma belgelerde kalite ve düzenlemelerin korunması için önceki güvenli yol kullanılır.
- PDF kaydedildiğinde geçen süre ekranın alt kısmında saniye olarak görünür.
- Yeni JVM regresyon testleri JPEG bayt eşitliği, PDF xref ve Türkçe Unicode eşlemesini denetler.

SINIRLAMALAR:
- İmza, kaşe, değiştirilen yazı, deskew sonucu PNG ve başka görüntü formatlarında
  halen eski PdfDocument yöntemi çalışır. Bu belgelerde 10 saniye bekleme süresinin
  ortadan kalktığı garanti edilmez; hız cihaz, sayfa sayısı ve dosya boyutuna bağlıdır.
- Gerçek cihazda süre ölçümü henüz yapılmadı. Üretim imzası yapılandırılmadıysa
  yayınlanan APK "TEST" olarak işaretlenir.
- Lütfen kaynak/orijinal resmi belgeleri yedekleyin.

Gün İzi ana dalı değiştirilmemiştir.

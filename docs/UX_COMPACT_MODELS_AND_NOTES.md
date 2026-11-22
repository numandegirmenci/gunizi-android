# Günİzi — Yapay Zeka / Notlarım arayüz düzeni (kaynak kod güncellemesi)

> Durum: Android cihaz testi ve APK derleme doğrulaması yapılmadı. Bu belge yeni bir yayımlanmış APK duyurusu değildir.

## Kullanıcının ilk gördüğü ekran

- Yapay zeka sayfası: kayıt, iki temel eylem, canlı durum, sonuçlar. Analiz tercihleri ve teknik modeller varsayılan olarak kapalı "Tercihler ve model ayarları" alanında.
- Model yönetimi: öneri seçimi yatay, seçilmiş konuşma ve özet modeli iki kısa kart halinde. Diğer 5+3 model seçenekleri başlangıçta kapalıdır.
- Notlarım: önce kayıtlı notlar ve arama. Not düzenleyici yeni not veya mevcut not açılınca görünür; kaydet/paylaş yan yana, TXT ve diğer işlemler menüdedir.
- Motor markası günlük işlem ekranlarında gösterilmez. GPL lisansları ve gerekli üçüncü taraf atıfları Ayarlar > Yasal bilgiler altında erişilebilir kalır.

## Öneri mantığı

| Kayıt ihtiyacı | Yazıya dökme | Özet istenirse | Sınır |
| --- | --- | --- | --- |
| Yaklaşık 5 dk, dengeli | Whisper Small Q5 | Qwen3 1.7B | İlk deneme; kalite ortam sesine bağlı |
| 5–60 dk, ayrıntılı | Whisper Turbo Q8 | Gemma 4 E2B | Daha fazla depolama ve RAM gerekir |
| 1 saat+, daha hafif | Whisper Small Q5 | Qwen3 1.7B | İşleme uzun sürebilir; ayrıntı kaçabilir |

Öneriyi seçmek modelleri **indirmez**. İndirme ayrıca onaylanır. Öneriler cihaz üzerinde ölçülmüş kesin performans iddiası değildir.

## Korunan sözleşmeler

- `AnalysisPreferences.speechModel` / `Prefs.aiModel` model seçimlerini saklar.
- `aiSileroVad` önceki ayarla uyumludur.
- `AiModelDownloadWorker` ve `AiModelStore` indirme ve doğrulamayı yapmaya devam eder.
- `UserNotesStore` notların eski `.txt` dosya biçimini korur; arama sadece yerelde yapılır.
- Seçili kayıt / rapor eşleştirmesi ve canlı analiz durumunu taşıyan kod değiştirilmedi.
- Varsayılan olarak açık kaynak atıfları gizlenebilir, fakat kullanıcı isterse erişebilir.

## APK öncesi test

1. 5 dk seçeneği -> Small Q5 / Qwen 1.7B; 5–60 dk -> Turbo Q8 / Gemma; seçimler ekran kapanıp açıldığında korunuyor mu?
2. Modelin indirilmesi açık onay istiyor mu; kısmi indirme / hazır / hata durumları güncelleniyor mu?
3. VAD ayarı aç/kapat ve gerçek ses dökümü halen çalışıyor mu?
4. Yapay Zeka ekranından Notlarım açıldığında arama, yeni not, kayıt açma, düzenleme, paylaş ve silme düzgün mü?
5. Düzenlenmemiş/kaydedilmemiş not geçişinde uyarı çalışıyor mu?
6. Rapor içinden "Notlarıma ekle" taslağı açılıyor mu?
7. Küçük ekran, uzun başlık, erişilebilirlik büyük yazı ve yatay öneri kaydırması test edildi mi?
8. Gerçek Samsung cihazda Android build, yaşam döngüsü, arka plan ve konuşma çözümleme test edildi mi?

**Not:** Statik kaynak kontrolü gerçek APK derlemesi veya cihaz testi yerine geçmez.

# Günİzi

**Günİzi**, Android'de geçmişe dönük ses kaydı, mesai kaydı, Türkçe konuşma dökümü ve cihaz içinde yapay zeka destekli özet hazırlama uygulamasıdır. Ses kayıtlarının işlenmesi yerel olarak yapılır; modeller yalnızca kullanıcı isterse indirilir.

## Günİzi v0.15.0 — Daha hızlı kullanılan, daha kompakt arayüz

**Kayıt, Mesai, Yapay Zeka ve Ayarlar** ekranlarındaki gereksiz dikey sıralama azaltıldı. Kayıt süresi için yatay seçim düğmeleri, analiz ve rapor işlemleri için iki sütunlu kısayollar, ikincil ayarlar için katlanabilir bölümler eklendi. Güncelleme kontrolü ilk sırada ve artık bağımsız kurulumlarda da **açılıp kapatılabiliyor**.

- **Kayıt:** Canlı geçmiş ve kaydetme ilk ekranda, zamanlı plan ayrıntıları katlanabilir.
- **Yapay Zeka:** Konuşma → Metin / Karar ve görevler yan yana, sonuçlar ikincil ayarlardan önce.
- **Model merkezi:** Öneriler model değiştirmeden önce onay ister; var olan yüksek kaliteli modeller korunur.
- **Güncelleme:** Bağımsız paketlerde sürüm kontrolü çalışır; farklı imza veya paketli APK'nın yanlışlıkla mevcut kuruluma yüklenmesi engellenir.
- **İndirme ve kurulum:** [v0.15.0 değişiklik ve uyumluluk notları](release-notes/v0.15.0.md).

**Önemli:** GitHub Actions debug APK'sı `com.gunizi.app.ai11` kimliğiyle ayrı kurulur; önceki uygulama ve kayıtları otomatik taşınmaz. Üretim paketinin mevcut kurulumun üzerine yüklenmesi aynı kalıcı üretim imzasını gerektirir. Android CI testleri gerçek Samsung pil ve ses analiz testini karşılamaz.

---

## Günİzi v0.14.0 — Zamanlı Geçmiş Dinleme

**Pil kullanımı için yeni özellik:** Kayıt → Zamanlı geçmiş dinleme alanında, Mesai'den bağımsız **günleri, başlangıç ve bitiş saatlerini** belirle. Planı uygulama açıkken etkinleştir. Seçilen saat aralığı dışında mikrofon kaydı ve Opus kodlama durur; saatleri izleyen düşük sıklıklı ön plan hizmeti çalışmaya devam eder.

- Haftanın 7 günü ayrı seçilebilir; geceye taşan vardiyalar desteklenir.
- Plan dışındayken durum: **Mikrofon kapalı**. Uygulama ve plan bildirimi görünürdür.
- Eski Mesai otomatik dosya kaydı değişmez. Geçmiş kayıt RAM'de sınırsız tutulmaz; şifreli özel önbellekte en fazla 6 saat birikir.
- **Dikkat:** Plan bittiğinde kaydedilmemiş geçici geçmiş silinir; önemli sesi süre dolmadan kaydet.
- Android'in mikrofon foreground service kısıtları nedeniyle hizmet sistem tarafından durdurulursa uygulamayı açarak planı yeniden etkinleştirmek gerekir. Doze/Samsung pil yönetimi saat geçişlerini geciktirebilir. Gerçek pil tasarrufu henüz cihazda ölçülmedi.
- Tam kalite Whisper Turbo Q8 ve Gemma 4 varsayılanları değiştirilmedi.

[Değişiklik notları](release-notes/v0.14.0.md) · [Başarılı Android derlemesi ve APK](https://github.com/numandegirmenci/gunizi-android/actions/runs/45026044048)

**Kurulum:** GitHub Actions'taki APK `com.gunizi.app.ai10` kimliğiyle ayrı kurulur; mevcut Günİzi verilerini otomatik taşımaz. Önceki sürümü silme. Android CI testleri ve imza kontrolü başarılıdır, ancak Samsung cihazında gerçek saat geçişi ve pil ölçümü henüz yapılmamıştır.

---

## Günİzi v0.13.1 — Canlı analiz ve işlem başlatma düzeltmesi

v0.13.0'da kullanıcı testinde tespit edilen **anlaşılmayan bekleme, gizli ilerleme paneli ve geç gelen konuşma akışı** sorunları için düzeltme sürümüdür.

- Manuel analiz isteği hemen durum panelinde onaylanır; işlem kuyruğuna alınmadıysa uyarılır.
- Android pil kısıtı manuel analizi süresiz bekletmez; sırada bekleme ve gerçek çalışma süreleri ayrılır.
- Canlı panel ilk ekranda görünür; ilk döküm için 12 saniyelik ses bölümü denenir, kalanlar 30 saniyedir.
- Oynatıcı ve teknik seçenekler katlanabilir; yeni kurulumun varsayılan konuşma modeli Whisper Small Q5'tir.
- VAD kapalı veya kullanılamazken gerçek Whisper dökümü korunur; tamamen sessiz kayıtta hata yerine açıklayıcı sonuç verilir.

[Değişiklikler, test sınırları ve kurulum notları](release-notes/v0.13.1.md)

[GitHub Actions v0.13.1 APK derleme ekranı](https://github.com/numandegirmenci/gunizi-android/actions/workflows/build-v0131-stability.yml) • Önceki APK'yı silme. Ayrı uygulama kimliği ile dağıtılan geçici sürümdür; gerçek telefon testi ayrıca gerekir.

---

## Günİzi v0.13.0 — Kanıtlı günlük iş hafızası

**Hedef:** Uzun ses kaydından “Bugün ne konuşuldu?”, “Hangi işler bana kaldı?”, “Kimden dönüş bekliyorum?”, “Neler sonuçlandı?” sorularına kaynakla ilişkilendirilmiş yanıtlar hazırlamak.

- Ses içeriğinin SHA-256 kimliği + analiz UUID'si sayesinde aynı isimli kayıtlar farklı sonuç dosyalarına yazılır.
- Konuşma dökümü arayüzü dokuz saatlik kaydı tek büyük TextView olarak yenilemek yerine sadece dosyaya eklenen yeni satırları okur, ListView ile sanal satır kullanır.
- Karar, görev, konuşma ve durumlar özel iş hafızası klasöründe kayıt bazında saklanır. Kullanıcıya düşen işler, beklenen yanıtlar ve sonuçlananlar için kullanıcı onayı ayrıca tutulur.
- Kaynak zamanı bilinen satıra dokunulduğunda ilgili ses bölümünü dinleme sayfası açılır. Bilinmeyen zaman için bağlantı uydurulmaz.
- İkinci aşama hiyerarşik yerel özetleme ve kontrol noktaları; tabloda 1200 satırlık sessiz kesme kaldırılmıştır. **Yapay zeka çıkarımı hata içerebilir; eksiksizlik garantisi değildir.**
- Geçmiş dinleme modunda tamamlanmış geçici Opus parçaları Android Keystore AES-GCM ile şifrelenir. Konuşma motorunda WAV ara dosyaları yerine bellek içi PCM kullanılır. Aktif olarak kaydedilen dosyanın MediaRecorder tarafından doğrudan şifreli üretilmediği özellikle not edilmelidir.
- [v0.13.0 teknik değişiklik ve veri güvenliği notları](release-notes/v0.13.0.md)

**Doğrulama / APK:** [Günİzi 0.13.0 GitHub Actions derlemesi](https://github.com/numandegirmenci/gunizi-android/actions/workflows/build-v0130-memory.yml) — yalnızca başarıyla sonuçlanan derlemelerin Artifacts bölümündeki APK'lar kullanılmalı. Debug APK `com.gunizi.app.ai7` kimliğiyle ayrı kurulur; önceki kayıtları silmeyin.

**Kalıcı güncelleme:** Resmi `com.gunizi.app` paketinin veri silmeden güncellenebilmesi için aynı özel imza anahtarının GitHub Secrets üzerinden sağlanması gerekir. Şu anda bunun tamamlandığı iddia edilmiyor.

---

## Günİzi v0.12.0 — Canlı konuşma akışı

- Konuşmalar, ses bölümleri tamamlandıkça **zaman damgalı ve aşağı doğru sıralanan satırlar** halinde görünür.
- **İçeriğe gir** ile analiz devam ederken konuşma dökümü açılır; **Canlı takip** ile yeni metne otomatik kaydırma kontrol edilir.
- Konuşma bulunmayan VAD bölümlerinde yanlış metin üretimini azaltan kontrol.
- Telefon RAM kapasitesini dikkate alan model önerileri ve isteğe bağlı alternatif uygulamada ses açma.
- Aranabilir **Notlarım**, kompakt ayarlar, kaynak ses/kayıt eşleşmesi ve açık rızaya dayalı paylaşım.

**Kaynak ve sürüm notları:** [v0.12.0 değişiklikleri](release-notes/v0.12.0.md)

**APK derlemesi:** [Günİzi v0.12.0 Android APK — GitHub Actions](https://github.com/numandegirmenci/gunizi-android/actions/runs/44986054092) → `Artifacts / GunIzi-v0.12.0-APK`. Build, Android APK imza doğrulaması ve JVM testleri başarılıdır. GitHub Releases oluşturma aşaması GitHub Actions token yetkisi nedeniyle HTTP 403 verdi; Release yüklemesi bu nedenle tamamlanmadı.

> **Kurulum güvenliği:** Actions'taki APK `com.gunizi.app.ai6` kimliğiyle ayrı kurulur; mevcut Günİzi kayıtlarını ve modellerini silmeden yan yana çalışır. Bu dosya önceki uygulamanın üzerine yüklenen kalıcı imzalı güncelleme değildir. Eski uygulamayı kaldırmayın. Kalıcı üretim imza anahtarı tanımlandığında aynı `com.gunizi.app` kimliğiyle resmî sürüm yayımlanabilir.

**Durum:** APK derlenmiştir fakat cihaz üzerinde saatlerce süren gerçek ses işleme testi tamamlanmamıştır. İşleme hızı ve doğruluğu telefon, RAM, model ve ses kalitesine göre değişir. Açık kaynak atıfları [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) içindedir.

## Önceki geliştirme sürümleri

## v0.11.1 • Canlı durum ve kararlılık testi

Yeni AI ekranında genel ilerleme, **saniye sayaçlı aşama**, işlenen ses
süresi ve **bölüm sayısı** ayrıdır. Uzun model yükleme veya bölüm duraksamasında
net uyarı çıkar. Son metin ve teknik ayrıntılar isteğe bağlı açılır.
İptal edilen işlerin durumu kaybolmaz, bir kaydın dökümü diğer kayda karışmaz.
Orijinal CallVault whisper.cpp, Silero VAD ve llama.cpp/Gemma motoru korunur.

- [v0.11.1 sürüm notları](release-notes/v0.11.1.md)
- [Canlı durum teknik sözleşmesi](docs/AI_LIVE_STATUS_V0111.md)

Ayrı **Günİzi Stabilite Test** APK paketi `com.gunizi.app.ai4`,
eski kurulum verilerini silmemek için farklı kimlikle dağıtılır.
Önceki beta modelleri bu uygulamaya otomatik taşınmaz.

## Günİzi v0.11 • CallVault native yapay zeka (TEST)

Bu sürümde **CallVault'un gerçek native whisper.cpp + Silero VAD +
llama.cpp kodları** doğrudan ve commit'e sabitlenmiş bir Git
submodule üzerinden Günİzi'ye bağlandı. Önceki hazır Whisper/Llama Android
kütüphaneleri kaldırıldı. Varsayılan özet modeli Gemma 4 E2B (2,62 GB);
Whisper seçenekleri Small Q5 ve Turbo Q8 dahil.

**Arayüz:** Kayıt oynatma, zaman çizelgesi, konuşma metni veya ayrıntılı
konu/karar tablosu, gerçek işlem durumu, Metin / Konular / Notlarım
sekmeleri. Geçmiş ve Mesai kayıt mantığı korunur.

Bu, gerçek kod güncellemesidir; fakat native modelin Samsung'da
uçtan uca çalışması cihaz testinden önce garanti edilemez.

- [v0.11.0 ayrıntılı sürüm notları](release-notes/v0.11.0.md)
- [Kaynak ve derleme mimarisi](docs/CALLVAULT_NATIVE_INTEGRATION.md)
- [GPL-3.0 ve atıflar](THIRD_PARTY_NOTICES.md)

## AI 2.0 Test v0.10.0 — Çalışan ilk uygulama güncellemesi

Yeni AI 2.0 planının ilk **gerçek uygulama kodu** GitHub'a işlendi:
ses ön inceleme, kayıt merkezli oynatıcı, bağımsız konuşma/rapor komutları,
model ve işleme aşamalarının ayrı takibi ve 30 saniyelik Whisper parçaları.

Test APK paketi `com.gunizi.app.ai2` olarak ana uygulamanın **yanına**
kurulur. Diğer test sürümlerinin yerel modellerini devralmaz.
Bu sürüm henüz yeni whisper.cpp JNI veya VAD motoru değildir.

[Açık sürüm notları](release-notes/v0.10.0.md) ·
[AI 2.0 mimari yol haritası](docs/AI_ARCHITECTURE_V010.md)

## Günİzi AI 2.0 (v0.10 hazırlığı) — Mimari tasarım

CallVault'un yerel Whisper / VAD / LLM iş akışından yararlanarak Günİzi'nin
AI mimarisi yeniden tasarlanmıştır. Henüz bir APK / çalışan motor güncellemesi değildir.
Önce gerçek cihazda %0 model yükleme sorununu teşhis edeceğiz; ardından kaynaklı
konuşma dökümü, JSON konu/karar/görev analizi ve kaldığı yerden devam mimarisine geçeceğiz.

- [Tam teknik mimari](docs/AI_ARCHITECTURE_V010.md)
- [Dosya/sınıf bazlı yol haritası ve test kriterleri](docs/AI_MIGRATION_V010.md)
- [CallVault incelemesi ve lisans notları](docs/CALLVAULT_REVIEW.md)

Geçmiş kaydı, Mesai modu ve mevcut v0.9.1 Test verileri korunacaktır.

## v0.9.1 • Konuşma metni, metinden tablo ve gerçek durum takibi

- Geçmiş ses sayacı her saniye ilerler, önceki sürümlerdeki canlı deneyim korunur.
- Yapay Zeka ekranında **Konuşma metnini çıkar**, **Ses kaydını özetle ve tablolaştır**
  ve **Önceden çıkarılan konuşma metnini özetle ve tablolaştır** ayrı işlemlerdir.
- Model yüklenirken geçen süre gösterilir; 90 saniyeden uzun bekleme için açıklama
  yapılır ve işlem başarısız/iptal durumları ayrıca gösterilir.
- `Whisper base` hızlı doğrulama seçeneği eklendi.
- Ayarlar sonunda **Geliştiren: Numan Değirmenci**, e-posta ve GitHub bağlantısı bulunur.
- **İlk test için:** 30 saniye–3 dakika ses → Whisper base ile metin → Qwen3 ile tablo.

[Değişiklikler](release-notes/v0.9.1.md) · [Test notları](docs/AI_DEBUG_V091.md)

## v0.9 • Yapay Zeka alanında anlaşılır çalışma

**Model indirmeleri ve kayıt klasörü artık Ayarlar altında yönetilir.**
Yapay Zeka sayfası şu iki işlemi açıkça ayırır:
- **Konuşmayı Yazıya Dök:** yalnızca Whisper gerekir. Qwen3 olmadan da
  konuşmanın Türkçe metni alınabilir; döküm cihazda gösterilir ve not uygulamasına paylaşılabilir.
- **Özetle + Ayrıntılı Tablo:** Whisper ve Qwen3 tam olarak kurulmuş olmalı.

**Canlı işlem paneli** seçilen ses kaydını, iş aşamasını, yüzdeyi, son
okunan/özetlenen parçadan kısa bir metni ve sonunda sonuç düğmelerini gösterir.
İndirmeler uygulamanın ekranından bağımsız WorkManager foreground görevi
olarak sürdürülür, indirme ilerlemesi ve sonuçları bildirimde gösterilir.

**Kullanıcı görselindeki sorun:** Whisper hazırdı ama Qwen3 indirmesi %58'deydi.
Önceki sürüm iki model kurulu değilken yazıya dökmeyi de kilitliyordu.
Yeni ayrım bunu düzeltir. Bağlantı kesintisinde .part dosyasını kullanarak
indirmeyi yeniden deneme davranışı da güçlendirilmiştir.

**Güncelleme ve imza:** Son yayımlanmış test GitHub Release v0.8.0.
v0.9 kaynak kodu/test derlemesi ile kalıcı imzalı yayın aynı şey değildir.
Aynı sürüm zaten kuruluysa yeni sürüm bildirimi gelmemesi normaldir.
GitHub Actions'ın debug imzası değişebilir; uygulamayı silmeden üstüne kurma
yalnızca imzalar uyumlu olduğunda mümkündür. Önce
[kalıcı imzalama rehberini](docs/UPDATE_SETUP.md) tamamlayın.

[v0.9 sürüm notları](release-notes/v0.9.0.md) •
[Yapay zeka v0.9 test planı](docs/AI_UX_V09.md)

## Günİzi Yapay Zeka • v0.8.0

**Amaç:** 9 saatlik çalışma gününü sadece kısaltmak değil; konuşulan her önemli
konuyu, kararları, görevleri, sorumluları ve açık kalan maddeleri kaynak saatleriyle
**Samsung tarzı ayrıntılı konu kartlarına** dönüştürmek. Yapay zeka verileri
otomatik çıkarır ama bunlar doğrulama gerektiren önerilerdir.

**Kullanıcı talimatları:** Rapor ekranında "Yapay zeka neye dikkat etsin?" alanı
vardır. Kullanıcı kendisi için önemli kurumları, projeleri, tarihleri, riskleri ve
takip edilmesi gereken görevleri yazabilir. Talimatlar yalnızca cihazda saklanır.

**Yerel model seçenekleri:** Türkçe Whisper small (denge) ve **Whisper
Large-v3-Turbo Q5 (yüksek kalite)**; Türkçe rapor için Qwen3 1.7B veya Qwen3 4B.
Modeller açık onayla bir kez indirilir ve dosyalar SHA-256 ile doğrulanır.
Büyük modellerde yeterli RAM ve şarj gerekir.

**Uzun kayıt yöntemi:** 5 dakikalık ses parçaları, kalıcı işlem kontrol noktaları,
tüm parçaların ayrı konu tablosu ve birleştirilmiş gün raporu. Böylece önceki
sürümün konu raporundaki ilk ~9500 karakter sınırlaması kaldırılmıştır.
Döküm, HTML konu kartları, CSV tablo ve ayrıntılı TXT raporu üretilir.

**Notlarım:** Kullanıcı rapordan taslak açabilir, düzenleyip **Notlarıma kaydet**
diyebilir, TXT dosyasını seçili klasöre koyabilir veya Android paylaşım ekranıyla
**Samsung Notes** / diğer not uygulamalarına gönderebilir.
Notlar uygulamanın özel alanındadır, kaldırmada silinebilir.

**Sınırlamalar:** Android 15+ uzun çalışan yerel işlem sınırlamaları,
mikrofon erişimi, konuşma kalitesi, farklı konuşmacılar ve kişilerin adları
hata yaratabilir. 9 saatlik canlı cihaz yük testi henüz tamamlanmadı.

[AI teknik tasarımı](docs/AI_DESIGN.md) ·
[v0.8 sürüm notları](release-notes/v0.8.0.md) ·
[Silmeden güncelleme rehberi](docs/UPDATE_SETUP.md)

## Ekran tasarımı
Ana ekran artık tek uzun liste değil: **Kayıt, Mesai, Rapor, Ayarlar** olmak üzere dört kompakt sekme bulunur.

## v0.6 Bildirimde hızlı geçmiş kaydetme

- Geçmiş dinleme aktifken bildirim açıldığında **Son 15 dk**, **Son 1 saat**, **Son 2 saat** işlemleri görünür. Android genişletilmiş bildirimde en fazla üç eylem gösterir.
- Her seçenek **geçmişe dönüktür**; ana ekrandaki seçili süre değişmez.
- Örneğin 30 dakika birikmişken Son 1 saat seçilirse mevcut 30 dakikanın dışa aktarımı denenir.
- Hızlı kaydetme için klasör izni önceden verilmiş olmalıdır.
- İşlem sırasında dinleme devam eder; yalnızca yeni ses parçasına geçerken kısa bir boşluk oluşabilir.
- Üç eylem sınırı nedeniyle **Dinlemeyi durdur** seçeneği ana ekranda tutulmuştur. Bildirime dokunmak Günİzi'yi açar.
- **Sessiz bildirim kanalı korunmuştur.** Samsung bildirim sırasını ve "2 bildirim daha" gibi otomatik gruplamaları kendi belirler; uygulama Gecko ile aynı gruba zorla alınamaz.
- Normal Android uygulaması hücresel görüşmenin iki tarafını kaydedemez. CallVault gibi bağımsız projeler Shizuku / gömülü ADB üzerinden daha yüksek yetki ve cihaz desteği kullanabilir; bu, Günİzi'nin mevcut mikrofon izniyle aynı şey değildir.
- Görüşme başladığında Günİzi hizmetini otomatik durdurmaz; fakat Android, rakip mikrofon yakalamada sessiz ses verebilir. Ses eksikliği fark edilebildiğinde uyarı gösterilir, **telefonun kısıtlamalarını aşacağı vaat edilmez**.


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

## Güncelleme deneyimi (v0.7)

- Android WorkManager yeni sürümleri arka planda kontrol eder; mümkünse ilk açılıştan sonra erken kontrol de yapar.
- Yeni sürüm bulununca bildirimde kısa **yenilikler** gösterilir. Bildirime dokununca **Bu güncellemede neler var?** ekranı açılır.
- Kurulumdan önce mevcut ve yeni APK'nın paket adı, sürüm kodu ve imzası denetlenir; uyumsuzsa kullanıcıya uygulamayı silme önerilmez.
- **Kalıcı imza anahtarı olmadan v0.7 signed Release yayınlanmaz.** Tek seferlik [kurulum rehberi](docs/UPDATE_SETUP.md).

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

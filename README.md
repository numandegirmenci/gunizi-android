# Günİzi Android

Günİzi, çevrimdışı ses kaydı için hazırlanan deneysel Android uygulamasıdır.

## Şu anki durum

Bu depo **ilk prototiptir**. Android cihazda test edilmeden kullanılmamalıdır. Yerel yapay zeka ile gün özeti **henüz uygulanmadı**.

- **Mesai Kaydı:** Kullanıcının belirlediği hafta günleri ve saatleri içinde Opus/OGG ses kaydı tutma, seçilen Android klasörüne aktarma.
- **Sürekli Kayıt:** Gecko'dan esinlenen 15 dakikalık döngüsel RAM tamponu; son 15 dakikayı WAV olarak kaydetme.
- **Gizlilik:** Ses buluta gönderilmez. Başarıyla tamamlanmış mesai kayıtları için geçici şifreli yedek oluşturulur. Etkin kayıt dosyası henüz şifreli değildir.
- **Pil:** Mod karşılaştırması sadece açıklayıcıdır; gerçek cihazda ölçüm yapılmadı.

## Önemli Android kısıtlaması

Android 14 ve sonrasında mikrofon erişimli foreground servislerinin arka plandan otomatik başlatılması kısıtlıdır. Mesai Modu'nun seçilen saatte başlaması için servisin önceden uygulama açıkken başlatılmış ve sistem tarafından durdurulmamış olması gerekir. Kesintisiz çalışması **garanti edilmez**.

## APK derleme

GitHub Actions > Android Debug APK > Run workflow seçeneğiyle debug APK üretilebilir; varsayılan dalda yapılan kod değişikliği de iş akışını tetikler. APK sonucu Actions çalışmasının **Artifacts** bölümünde yer alır.

## Güvenlik ve izinler

Başkalarının konuşmalarını ancak gerekli bilgilendirme ve izinlerle kaydedin. Bu prototipi hukuki veya kritik kayıtların tek kopyası olarak kullanmayın.

## Lisans ve köken

Bu depo, Gecko'nun kodundan birebir kopya olmadan, ses tamponu yaklaşımından esinlenen ayrı bir prototip olarak hazırlanmıştır.

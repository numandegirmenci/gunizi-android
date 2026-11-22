# Günİzi: silmeden güncelleme ve kalıcı imza kurulumu

**Şu anki problem:** v0.1–v0.6 test APK'ları GitHub Actions'ın geçici debug
anahtarıyla imzalandı. Her yeni Actions çalışmasının anahtarı farklı olabilir.
Android aynı paket adının yeni APK'sını kabul etmek için eski ve yeni imzanın
uyumlu olmasını şart koşar. APK'dan eski özel anahtarı elde etmek mümkün değildir.
Mevcut v0.6 kurulumundan yeni imzaya **tek seferlik geçiş** gerekebilir.

## Gelecek tüm sürümler için tek seferlik çözüm

GitHub'daki `.github/workflows/release-apk.yml` dosyası artık sabit bir imza
anahtarını GitHub Secrets'tan okuyor. **Özel anahtarı hiç kimse herkese açık
repo dosyası olarak paylaşmamalı.**

Windows'ta [Java JDK 17](https://adoptium.net/) ve
[GitHub CLI](https://cli.github.com/) kurup GitHub'a `gh auth login` ile gir.
Depodaki [configure-release-signing.ps1](../scripts/configure-release-signing.ps1)
betiğini **kendi bilgisayarında** çalıştır. Betik:
- `Documents/GunIzi-Signing-PRIVATE/gunizi-release.p12` özel anahtarını
  oluşturur (dosya varsa yenisini üretmez).
- Seçtiğin güçlü şifreyle anahtarı korur.
- Dört sırrı şifreli GitHub Actions Secrets'a iletir:
  `GUNIZI_KEYSTORE_BASE64`, `GUNIZI_KEYSTORE_PASSWORD`,
  `GUNIZI_KEY_ALIAS`, `GUNIZI_KEY_PASSWORD`.

**Anahtar dosyanı ve şifreni güvenli biçimde yedekle.** Bunlardan biri
kaybolursa sonraki APK'ları aynı kimlikle imzalayamazsın.
GitHub Secrets: https://github.com/numandegirmenci/gunizi-android/settings/secrets/actions

## Sonraki yayın

GitHub'a `v0.7.0` etiketini gönderdiğinde **Signed Android Release**
iş akışı, aynı anahtarla imzalı APK'yı ve
`release-notes/v0.7.0.md` değişikliklerini yayınlar. Sürüm kodu
öncekinden büyük olmalı. Yeni sürüm bildiriminde güncellemeler özetlenir,
uygulamada tüm değişiklikler gösterilir ve Android'de kurulum onayından
önce imza eşleşmesi denetlenir.

**Mevcut debug kurulumunla ilk imzalı yayın uyumsuzsa** mevcut APK'yı
silmeden önce önemli kayıtlarını seçilen harici klasörde yedekle.
Uygulama özel alanındaki ses tamponları ve indirilmiş modeller
kaldırmada silinebilir. Sonrasında aynı anahtarla imzalanmış
**v0.8, v0.9... sürümleri uygulama kaldırılmadan** kurulabilir.

## Güncelleme deneyimi

- Otomatik GitHub Release kontrolü ve yeni sürüm bildirimi
- Bildirimde kısa yenilik özeti, ekranda ayrıntılı sürüm notları
- APK'nın Wi-Fi'de, doğrulanmış SHA-256 ile indirilmesi
- Paket adı, sürüm kodu, imza eşleşmesi kontrolü
- Uyumsuz sürümde zorla kaldırma yok; kurulum engellenir
- Kullanıcı Android yükleyicisini onayladıktan sonra sürüm güncellenir

Debug APK üretimi **test içindir**, kalıcı yayın değildir.

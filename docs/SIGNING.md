# Belge İzi: güvenli ve kalıcı APK imzası

Bu dosya ilk kurulum içindir; gizli anahtarları kod deposuna, sohbetlere
veya GitHub Issues'a YÜKLEMEYİN. Debug APK derleme başına farklı bir anahtarla
imzalanabilir ve telefon mevcut kurulumun üzerine güncellemeyi reddedebilir.

## Tek seferlik kurulum
Bilgisayarınızda güvenilir bir JDK'nın \`keytool\` aracıyla kalıcı anahtar üretin:

\`\`\`bash
keytool -genkeypair -v -keystore belgeizi-upload.jks \
  -keyalg RSA -keysize 3072 -validity 10000 \
  -alias belgeizi
\`\`\`

Anahtarı ve parolalarını güvenli, çevrimdışı bir yerde yedekleyin. Bu anahtar
kaybolursa mevcut uygulamaya veri kaybetmeden aynı paket imzasıyla güncelleme
göndermek mümkün olmayabilir.

GitHub deposunda **Settings > Secrets and variables > Actions > New repository secret**
üzerinden şu dört sırrı ekleyin:

- \`BELGEIZI_KEYSTORE_BASE64\`: JKS dosyasının tek satırlık Base64 içeriği.
- \`BELGEIZI_KEYSTORE_PASSWORD\`: keystore parolası.
- \`BELGEIZI_KEY_ALIAS\`: örnekte \`belgeizi\`.
- \`BELGEIZI_KEY_PASSWORD\`: bu alias için anahtar parolası.

Base64 oluştururken anahtar dosyasının kendisi kesinlikle Git deposuna
eklenmez. Linux'ta \`base64 -w0 belgeizi-upload.jks\`, macOS'ta
\`base64 < belgeizi-upload.jks | tr -d '\n'\` kullanabilirsiniz.

## Yayın güvenlik kuralları
Yayımlama yalnızca derleme dalındaki QA, Android birim testleri, lint ve
\`assembleRelease\` tamamlandıktan sonra başlar. Dört sır eksikse sadece
\`BelgeIzi-vX.Y.Z-Test.apk\` adlı test APK'sı yayımlanır ve sürüm
**prerelease** olarak işaretlenir.

Dört sır mevcutsa üretim APK'sı \`zipalign\` ve \`apksigner\` ile imzalanır,
imza doğrulanır, sonra \`BelgeIzi-vX.Y.Z.apk\` adıyla yayımlanır. Her iki
durumda \`--latest=false\` kullanılır; bu nedenle aynı repodaki Gün İzi
\`Latest\` sürümü değiştirilmez.

**Not:** Gün İzi ve Belge İzi teknik olarak halen tek GitHub deposunun ayrı
dallarındadır. Ayrı depo önerilir, fakat mevcut entegre yetkiler bu depoyu
otomatik oluşturamıyorsa önce kullanıcının oluşturması gerekir.

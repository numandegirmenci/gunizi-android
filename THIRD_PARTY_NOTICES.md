# Günİzi — CallVault ve üçüncü taraf lisans bildirimleri

Günİzi'nin AI Native Test bileşeni, CallVault projesinin **doğrudan sabitlenmiş**
kaynak kodunu (git submodule) kullanır:
https://github.com/madkongo/CallVault — commit `1561721d21321cf84a251093075b59b351b2e8c0`.

CallVault, ShizuCallRecorder'ın değiştirilmiş bir dalıdır:
https://github.com/kitsumed/ShizuCallRecorder.
**Günİzi, ne CallVault'un ne de ShizuCallRecorder'ın resmi sürümüdür; iki proje
tarafından desteklenmez, onaylanmaz.** Tüm isimler/markalar kendilerine aittir.
CallVault ve ShizuCallRecorder katkıcılarının telif hakları saklıdır.

CallVault kaynak bileşenleri GNU GPL v3 veya sonrası ve orijinal projenin
Section 7 ek koşullarıyla kullanılır. Bu kaynakla bağlantılı Günİzi uygulaması
da kaynak kodu sağlanarak aynı copyleft yükümlülükleri kapsamında dağıtılır.
[LICENSE](LICENSE) ve [CallVault NOTICE](third_party/callvault/NOTICE.md)
dosyaları şartların ayrıntısını içerir.

Yapay zeka kaynakları:
- `third_party/callvault/app/src/main/cpp/whispercv.cpp`, `llamacv.cpp`:
  CallVault tarafından düzenlenen JNI bağlayıcıları
- `third_party/callvault/third_party/whisper.cpp`: ggml-org whisper.cpp
- `third_party/callvault/third_party/llama.cpp`: ggml-org llama.cpp
- `third_party/callvault/app/src/main/assets/ggml-silero-v5.1.2.bin`:
  Silero VAD, MIT lisansı; dönüşüm ggml-org/whisper-vad, MIT.
  Ayrıntılı telif ve MIT izin metni CallVault NOTICE içindedir.
- Konuşma ve özet GGUF ağırlıkları kullanıcı isterse Hugging Face'den
  indirilebilir; APK'nın içine konulmaz.

# Günİzi × CallVault native AI entegrasyonu

## Kaynak güvenilirliği ve reproducibility

| Bağımlılık | Sabit sürüm |
|---|---|
| CallVault | `1561721d21321cf84a251093075b59b351b2e8c0` |
| CallVault alt modülü: whisper.cpp | `371b5a7561823ab2bb32142d2751e35e7534727b` |
| CallVault alt modülü: llama.cpp | `a298422da78eb75e440a7de0ca408af64d323d93` |
| Silero VAD | `ggml-silero-v5.1.2.bin` (CallVault asset) |
| Model bütünlüğü | Tam dosya SHA-256; CallVault katalog model hash'leri |

Clone:

```bash
git clone --recurse-submodules https://github.com/numandegirmenci/gunizi-android.git
```

Günİzi'nin `app/src/main/cpp/CMakeLists.txt` dosyası, CallVault'un
`whispercv.cpp` ve `llamacv.cpp` kaynaklarını **değiştirmeden** derler.
`GGML_BACKEND_DL` ve `GGML_CPU_ALL_VARIANTS` CallVault'un ARM
performans tercihleriyle aynı şekilde etkinleştirilir. APK yalnızca
`arm64-v8a` içerir. Native CMake Debug için `-O3` etkin tutulur;
debug native kodu `-O0` bırakılırsa test süreleri yanlış olur.

AI JNI isimlerinin, Kotlin `com.baba.callvault.*Native` bağlarıyla
birebir eşleşmesi gerekir. Bu sınıflar ve `SpeechGapSnap`, `VadModel`,
`SummaryGrammar`, `CallSummary` kaynakları GPL başlıklarıyla birlikte
Günİzi'ye alınmıştır.

## Kullanıcı işlemleri

1. **Kayıt:** Günİzi'nin mevcut özel kayıt modu/mesai modu. CallVault'un
   ADB, Shizuku, arama kaydı kontrol mekanizması taşınmaz.
2. **Konuşma çıkarma:** Günİzi AudioChunkDecoder → 30s Float PCM →
   `CallVaultWhisperEngine` → CallVault JNI + Silero VAD +
   `SpeechGapSnap` → kaynak saatli transkript.
3. **Özet:** `FullDaySummarizer` → CallVault native llama.cpp +
   `SummaryGrammar.JSON` + `CallSummary.parse` → kaynak saati
   doğrulanan Türkçe konu, karar ve görev tabloları.
4. **Depolama:** Her parçada doğrulanmış tam kontrol noktası, tam metin,
   HTML/CSV, Notlarım ve kullanıcı isteğiyle Samsung Notes paylaşımı.

## Her regresyon testinde kontrol edilecekler

- GitHub Actions `:app:assembleDebug` ve `:app:testDebugUnitTest`
  başarılı olmalı.
- APK arşivinde `lib/arm64-v8a/libwhispercv.so`,
  `lib/arm64-v8a/libllamacv.so`, GGML CPU backend .so'ları ve
  `assets/ggml-silero-v5.1.2.bin` bulunmalı.
- Yerel cihazda 30 saniyelik Türkçe kayıt: gerçek döküm, saniyeli kaynak
  aralıkları, Silero açık/kapalı farkı.
- Aynı metin yeniden özetlendiğinde Whisper yeniden yüklenmemeli.
- 3 dakika / 30 dakika / 9 saat testleri; RAM/ısınma/kaldığı yerden devam
  ayrıca ölçülmeli.
- İki büyük modelin aynı anda canlı tutulmadığı kontrol edilmeli.
- Eski uygulama kayıtları ve modelleri kesinlikle silinmemeli.
- Yan yana test app `com.gunizi.app.ai3` ile ana app arasında imza ve
  kullanıcı verisi paylaşımı olmadığı açıkça anlatılmalı.

## Lisans

Doğrudan CallVault kodunun bağlanması, Günİzi'nin GPL-3.0 uyumlu
kaynak dağıtımını gerektirir. `LICENSE` ve `THIRD_PARTY_NOTICES.md`
ile birlikte CallVault submodule'ünün kendi `NOTICE.md` dosyası da
saklanır. Markalar izinsiz kopyalanmaz; yazılımların bağımsızlığı
arayüzde açıkça belirtilir.

# Belge İzi — tekrar üretilebilir Türkçe OCR kalite testleri

Bu klasördeki belgeler **tamamen sentetiktir**. Kullanıcının gerçek kitap
sayfalarını, kişisel belgelerini veya gizli verilerini herkese açık GitHub
deposuna yüklemeyin.

## Test arşivi
\`fixtures/expected.json\` kitap metni, fatura, resmi yazı, tablo ve Türkçe
karakterler için doğru metinleri içerir. \`generate_fixtures.py\` bu metinlerden
Türkçe Unicode fontuyla PNG test belgeleri üretir:

\`\`\`bash
python3 -m pip install Pillow
python3 qa/generate_fixtures.py
\`\`\`

## Gerçek OCR doğruluğunu ölçmek
Android test APK'sında aynı görüntüleri içe aktarın; uygulamanın Kopyala
seçeneğiyle elde ettiğiniz metinleri \`results.json\` içine kaydedin:

\`\`\`json
{
  "samples": {
    "kitap": "BİNBİR GECE MASALLARI\n...",
    "fatura": "FATURA\n...",
    "resmi_evrak": "T.C. ÖRNEK BELEDİYESİ\n...",
    "tablo": "Kod | Açıklama | Tutar\n...",
    "turkce_karakter": "Çığ, İğde, Şişli..."
  }
}
\`\`\`

\`\`\`bash
python3 qa/score_ocr.py --results results.json
\`\`\`

Araç CER (karakter hata oranı) ve WER (kelime hata oranı) hesaplar.
Varsayılan sınırlar CER <= %12 ve WER <= %28'dir. Gerekirse daha sıkı
eşikler belirlenebilir.

**Sınır:** GitHub Actions şu an regresyon kararlarını, derlemeyi ve kalite
ölçüm araçlarını otomatik test eder; yalnızca gerçek Android cihazında
çalıştırılan OCR sonuçları cihaz doğruluğunu kanıtlar. Sentetik PNG üretmek
başlı başına uygulamanın OCR doğruluğunu ölçmez.

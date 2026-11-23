import json
import tempfile
import unittest
from pathlib import Path

from qa.score_ocr import distance, grade, metrics


class OCRBenchmarkTests(unittest.TestCase):
    def test_levenshtein(self):
        self.assertEqual(0, distance("Masalları", "Masalları"))
        self.assertEqual(1, distance("Masalları", "Masallari"))

    def test_turkish_dotless_i_error_is_counted(self):
        report = metrics("Masalları", "Masallari")
        self.assertGreater(report["cer"], 0)
        self.assertGreater(report["wer"], 0)

    def test_exact_match_has_zero_errors(self):
        report = metrics("Şehrazat\nIğdır", "Şehrazat\nIğdır")
        self.assertEqual(report["cer"], 0)
        self.assertEqual(report["wer"], 0)

    def test_missing_document_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            expected = Path(folder, "expected.json")
            results = Path(folder, "results.json")
            expected.write_text(json.dumps({"cases": [
                {"id": "book", "kind": "kitap", "expected": "Masalları"}
            ]}), encoding="utf-8")
            results.write_text(json.dumps({"samples": {}}), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Missing OCR result"):
                grade(expected, results)


if __name__ == "__main__":
    unittest.main()

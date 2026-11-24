"""Static regression checks for Android's external PDF/image editor integration.

These checks ensure the manifest contract is not silently removed. Actual
resolver appearance also depends on the app calling Android's intent API.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class ExternalEditContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tree = ET.parse(ROOT / "app/src/main/AndroidManifest.xml")
        cls.application = cls.tree.getroot().find("application")
        cls.activity = (ROOT /
            "app/src/main/java/com/numandegirmenci/belgeizi/MainActivity.kt"
        ).read_text(encoding="utf-8")
        cls.scan = (ROOT /
            "app/src/main/java/com/numandegirmenci/belgeizi/ui/ScanApp.kt"
        ).read_text(encoding="utf-8")

    def test_action_edit_is_exported_and_labeled(self):
        matches = [x for x in self.application.findall("activity-alias")
                   if x.attrib.get(ANDROID + "targetActivity") == ".MainActivity"]
        self.assertEqual(len(matches), 1)
        alias = matches[0]
        self.assertEqual(alias.attrib.get(ANDROID + "exported"), "true")
        self.assertEqual(alias.attrib.get(ANDROID + "label"),
                         "@string/open_with_belgeizi")
        filters = alias.findall("intent-filter")
        self.assertTrue(any(
            "android.intent.action.EDIT" in [
                action.attrib.get(ANDROID + "name")
                for action in entry.findall("action")
            ]
            for entry in filters
        ))

    def test_edit_accepts_pdf_and_images(self):
        alias = self.application.find("activity-alias")
        filters = alias.findall("intent-filter")
        mime_types = {data.attrib.get(ANDROID + "mimeType")
                      for entry in filters for data in entry.findall("data")}
        self.assertTrue({"application/pdf", "image/*"} <= mime_types)
        self.assertIn("Intent.ACTION_VIEW, Intent.ACTION_EDIT", self.activity)

    def test_readonly_input_and_scoped_sharing(self):
        provider = self.application.find("provider")
        self.assertEqual(provider.attrib.get(ANDROID + "exported"), "false")
        self.assertEqual(provider.attrib.get(ANDROID + "grantUriPermissions"), "true")
        self.assertIn("FileProvider.getUriForFile", self.scan)
        self.assertIn("Intent.FLAG_GRANT_READ_URI_PERMISSION", self.scan)
        self.assertIn("PdfWriter.export(doc, context.contentResolver, result)", self.scan)
        self.assertIn("Orijinal dosya korundu", self.scan)

    def test_chooser_label_translated(self):
        labels = (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
        self.assertIn("Belge İzi ile düzelt", labels)

    def test_ui_has_real_save_share_and_enhancement_actions(self):
        self.assertIn('Text("Kaydet"', self.scan)
        self.assertIn('Text("Belgeyi geliştir"', self.scan)
        self.assertIn("onShare = ::sharePdf", self.scan)
        self.assertIn("onEnhance: (String) -> Unit", self.scan)
        self.assertNotIn('ActionTool(Icons.Outlined.AutoFixHigh, "OCR"', self.scan)


if __name__ == "__main__":
    unittest.main()

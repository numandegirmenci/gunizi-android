"""Regression guards for Belge İzi Ultra HD capture and non-destructive import."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "app/src/main/java/com/numandegirmenci/belgeizi"
A = "{http://schemas.android.com/apk/res/android}"


class UltraHDTests(unittest.TestCase):
    def test_explicit_camera_permission(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        perms = [n.attrib.get(A + "name") for n in manifest.findall("uses-permission")]
        self.assertIn("android.permission.CAMERA", perms)

    def test_actual_camera2_resolution_reporting_not_hardcoded(self):
        source = (BASE / "camera/CameraCapabilities.kt").read_text(encoding="utf-8")
        self.assertIn("SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION", source)
        self.assertIn("REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR", source)
        self.assertIn("getOutputSizes(ImageFormat.JPEG)", source)

    def test_camera_saves_jpeg_to_file_before_decoding_dimensions(self):
        source = (BASE / "camera/UltraCameraScreen.kt").read_text(encoding="utf-8")
        self.assertIn("PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE", source)
        self.assertIn("ImageCapture.OutputFileOptions.Builder(file)", source)
        self.assertIn("inJustDecodeBounds = true", source)
        self.assertIn("onPhoto(Uri.fromFile(file)", source)

    def test_import_keeps_full_original_bytes(self):
        source = (BASE / "data/DocumentStore.kt").read_text(encoding="utf-8")
        self.assertIn("originalUri = uri", source)
        self.assertIn("input.copyTo(dest)", source)
        self.assertIn("originalWidth = fullSize.first", source)
        self.assertIn("originalHeight = fullSize.second", source)
        self.assertIn("decodeScaled(Uri.fromFile(sourceFile), 3400)", source)

    def test_full_original_can_be_exported_without_decoding(self):
        source = (BASE / "data/DocumentStore.kt").read_text(encoding="utf-8")
        self.assertIn("fun exportOriginal(page: DocumentPage, destination: Uri)", source)
        self.assertIn("source.inputStream().use { input -> input.copyTo(out) }", source)

    def test_keep_automatic_perspective_scanner_as_alternative(self):
        source = (BASE / "ui/ScanApp.kt").read_text(encoding="utf-8")
        self.assertIn("onAutoScan = ::scan", source)
        self.assertIn("onScan = ::openUltraCamera", source)
        self.assertIn("ActivityResultContracts.RequestPermission()", source)
        self.assertIn('ActionTool(Icons.Outlined.HighQuality, "Orijinal"', source)


if __name__ == "__main__":
    unittest.main()

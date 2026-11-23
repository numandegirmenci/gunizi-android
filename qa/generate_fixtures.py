#!/usr/bin/env python3
"""Generate original synthetic PNG documents; never upload real user scans."""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


def generate(manifest="qa/fixtures/expected.json", output="qa/generated"):
    cases = json.loads(Path(manifest).read_text(encoding="utf-8"))["cases"]
    folder = Path(output)
    folder.mkdir(parents=True, exist_ok=True)
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
    ]
    path = next((p for p in candidates if Path(p).is_file()), None)
    if path is None:
        raise RuntimeError("Unicode font missing; Turkish fixture rendering would be misleading.")
    font = ImageFont.truetype(path, 34)
    title = ImageFont.truetype(path, 42)
    produced = []
    for case in cases:
        image = Image.new("RGB", (1600, 2100), (248, 247, 237))
        draw = ImageDraw.Draw(image)
        draw.rectangle((90, 95, 1510, 2005), outline=(90, 90, 90), width=3)
        text_lines = case["expected"].splitlines()
        draw.text((155, 160), text_lines[0], font=title, fill=(20, 25, 30))
        y = 285
        for line in text_lines[1:]:
            draw.text((155, y), line, font=font, fill=(34, 36, 39))
            y += 110
        if case["id"] == "tablo":
            for baseline in (240, 360, 480, 600):
                draw.line((145, baseline, 1460, baseline), fill=(80, 80, 80), width=2)
            draw.line((340, 240, 340, 600), fill=(80, 80, 80), width=2)
            draw.line((980, 240, 980, 600), fill=(80, 80, 80), width=2)
        destination = folder / (case["id"] + ".png")
        image.save(destination, optimize=True)
        produced.append(str(destination))
    print("Created", len(produced), "synthetic Turkish document images.")
    return produced


if __name__ == "__main__":
    generate()

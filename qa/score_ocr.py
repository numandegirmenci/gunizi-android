#!/usr/bin/env python3
"""Deterministic CER/WER benchmark for exported Android OCR text.

Not a substitute for running the APK on-device. Inputs are OCR results exported
from the app and expected texts from a synthetic fixture manifest.
"""
import argparse
import json
import sys
import unicodedata
from pathlib import Path


def normalize(text):
    return unicodedata.normalize("NFC", text).strip()


def distance(a, b):
    # Memory-efficient Levenshtein edit distance.
    if len(b) > len(a):
        a, b = b, a
    previous = list(range(len(b) + 1))
    for i, left in enumerate(a, 1):
        current = [i]
        for j, right in enumerate(b, 1):
            current.append(min(
                current[-1] + 1, previous[j] + 1,
                previous[j - 1] + (left != right)
            ))
        previous = current
    return previous[-1]


def metrics(expected, actual):
    ref = normalize(expected)
    pred = normalize(actual)
    cer = distance(ref, pred) / max(1, len(ref))
    ref_words = ref.split()
    pred_words = pred.split()
    wer = distance(ref_words, pred_words) / max(1, len(ref_words))
    return {"cer": round(cer, 5), "wer": round(wer, 5),
            "expected_chars": len(ref), "actual_chars": len(pred)}


def grade(expected_path, results_path):
    manifest = json.loads(Path(expected_path).read_text(encoding="utf-8"))
    results = json.loads(Path(results_path).read_text(encoding="utf-8"))
    found = results.get("samples", {})
    rows = []
    for case in manifest["cases"]:
        key = case["id"]
        if key not in found or not isinstance(found[key], str):
            raise ValueError("Missing OCR result: " + key)
        rows.append({"id": key, "kind": case["kind"],
                     **metrics(case["expected"], found[key])})
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected", default="qa/fixtures/expected.json")
    parser.add_argument("--results", required=True)
    parser.add_argument("--max-cer", type=float, default=0.12)
    parser.add_argument("--max-wer", type=float, default=0.28)
    args = parser.parse_args()
    try:
        rows = grade(args.expected, args.results)
    except (ValueError, OSError, KeyError, json.JSONDecodeError) as error:
        print("Benchmark error:", error, file=sys.stderr)
        return 2
    for row in rows:
        print("{id:20s} CER={cer:.3f} WER={wer:.3f}".format(**row))
    bad = [row["id"] for row in rows
           if row["cer"] > args.max_cer or row["wer"] > args.max_wer]
    print("Result:", "PASS" if not bad else "FAIL " + ", ".join(bad))
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())

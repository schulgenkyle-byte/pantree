#!/usr/bin/env python3
"""Extract text from the three hotel-bar PDFs into raw text files."""
import sys
from pathlib import Path

import pdfplumber

ROOT = Path(r"C:\Users\12566\Downloads\pantrie-build (1)\pantrie-build\backend\ingest\cocktails\raw")

JOBS = [
    ("boothby_1908.pdf", "boothby_1908.txt"),
    ("newman_1904.pdf", "newman_1904.txt"),
    ("mahoney_1905.pdf", "mahoney_1905.txt"),
]


def extract(pdf_path: Path, txt_path: Path):
    print(f"Extracting {pdf_path.name} ...", flush=True)
    out = []
    with pdfplumber.open(str(pdf_path)) as pdf:
        for i, page in enumerate(pdf.pages):
            try:
                t = page.extract_text() or ""
            except Exception as e:
                t = ""
                print(f"  page {i+1} error: {e}", file=sys.stderr)
            out.append(f"\n===PAGE {i+1}===\n")
            out.append(t)
    txt_path.write_text("\n".join(out), encoding="utf-8")
    print(f"  wrote {txt_path} ({txt_path.stat().st_size} bytes)", flush=True)


if __name__ == "__main__":
    for pdf_name, txt_name in JOBS:
        pdf_p = ROOT / pdf_name
        txt_p = ROOT / txt_name
        if not pdf_p.exists():
            print(f"skip: {pdf_p} not found")
            continue
        if txt_p.exists() and txt_p.stat().st_size > 1000:
            print(f"skip: {txt_p} already extracted")
            continue
        extract(pdf_p, txt_p)

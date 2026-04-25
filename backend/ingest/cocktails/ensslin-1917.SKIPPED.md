# Ensslin 1917 — Harvest Skipped

**Status:** No public-domain plain-text or text-PDF transcription available.

## What was attempted
- `archive.org/details/recipesformixedd00enss` — 404 (item does not exist).
- HathiTrust catalog — Cloudflare blocked search; OCLC API forbidden.
- Project Gutenberg search — no Ensslin entry.
- Wikisource — no transcription.
- EUVS Vintage Cocktail Books bundle (`archive.org/details/vintage-cocktail-books-euvs`) — bundle does contain `1917 Recipes for Mixed Drinks by Hugo R Ensslin (second edition).pdf` (71 MB), but it is flagged as **"Image Container PDF"** with no embedded text layer and no `_djvu.txt` sidecar. `pdftotext -layout` produced 80 bytes of whitespace.
- `alscocktailclub.com/library-content/hugo-ensslin-recipes-for-mixed-drinks` — gated behind MemberSpace login.
- `aibmproject.it/hugo-ensslin/` — essay only, just an Italian-translation 3-page preview PDF.
- `cld.bz/e7NCgyt` (FlippingBook) — page text endpoints (`pages.json`, `files/text/0.html`, etc.) all 404; content is server-side only.
- Direct PDF download from EUVS (`vintage-cocktail-books-euvs/1917 Recipes for Mixed Drinks...pdf`, 71 MB) succeeded, **but no OCR tooling is available in this environment** (`tesseract`, `pdftoppm`, `pytesseract`, `easyocr` all missing).

## Recommendation
To complete the Ensslin harvest, either:
1. Install Tesseract + pdftoppm and OCR `raw/ensslin_1917.pdf` (already downloaded; ~71 MB, ~166 pages of two-column print).
2. Acquire the Wondrich-edited Mud Puddle Books 2009 facsimile reprint and OCR / type that.
3. Manually transcribe the ~350 recipes from the EUVS flipbook viewer.

The downloaded image-PDF is preserved at:
`/backend/ingest/cocktails/raw/ensslin_1917.pdf`

so a follow-up agent with OCR can pick up where this left off.

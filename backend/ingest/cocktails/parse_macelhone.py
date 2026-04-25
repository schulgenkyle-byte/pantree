#!/usr/bin/env python3
"""Parse MacElhone 'ABC of Mixing Cocktails' (1923, expanded later) into NDJSON.

Strategy: Each recipe begins with a numbered title like "1. Abyssinia Cocktail."
(sometimes the digits are space-separated, e.g. "2 0 . Black Velvet."). Body
follows until the next numbered line or a clear page-break / advertisement
block. We skip front matter/preface and trailing toasts/menus.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).parent
RAW = ROOT / "raw" / "macelhone_1923_orig.txt"
OUT = ROOT / "macelhone-1923.ndjson"

NUM_RE = re.compile(r"^\s*((?:\d\s*){1,3})\.\s*([A-Z][\w\'\-À-ſ\.\s\,()]*)$")
# Some titles like "20 . Black Velvet" — same pattern
# Unnumbered insert recipes (added later editions, like "Admiral.", "Bernice.", "Americano.")
UNNUM_TITLE_RE = re.compile(r"^([A-Z][a-zA-Z\'À-ſ\-]{2,30}(?:\s+[A-Za-z\'À-ſ\-]+){0,4})\.$")

# Skip lines that match these page-break/header patterns
PAGE_NUM_RE = re.compile(r"^\s*\d{1,3}\s*$")

GLASS_HINTS = {
    "cocktail glass": "cocktail",
    "champagne glass": "coupe",
    "long drink": "highball",
    "long glass": "highball",
    "highball": "highball",
    "tall glass": "highball",
    "fizz glass": "highball",
    "tumbler": "rocks",
    "small tumbler": "rocks",
    "wineglass": "wine",
    "wine glass": "wine",
    "wine-glass": "wine",
    "punch bowl": "punch_bowl",
    "old-fashioned": "rocks",
    "liqueur glass": "cordial",
    "champagne flute": "flute",
    "stem glass": "coupe",
    "small wineglass": "wine",
}


def detect_glass(t: str) -> str:
    s = t.lower()
    for k, v in GLASS_HINTS.items():
        if k in s:
            return v
    if "shake" in s and "strain" in s:
        return "cocktail"
    return ""


def detect_method(t: str) -> str:
    s = t.lower()
    if "shake" in s or "frappe" in s or "frapp" in s:
        return "shaken"
    if "stir" in s and "strain" in s:
        return "stirred"
    if "build" in s or re.search(r"fill\s+glass", s):
        return "built"
    if "muddle" in s:
        return "muddled"
    if "blaze" in s or "ignite" in s:
        return "flamed"
    return ""


ALC_KW = ["whisky", "whiskey", "rum", "gin", "brandy", "vermouth", "absinthe",
          "bitters", "wine", "champagne", "rye", "scotch", "bourbon", "cognac",
          "applejack", "calvados", "port", "sherry", "curacao", "curaçao",
          "creme", "crème", "chartreuse", "benedictine", "maraschino",
          "anisette", "amer picon", "byrrh", "dubonnet", "fernet", "kirsch",
          "cointreau", "kummel", "campari", "vodka", "swedish punch",
          "stout", "ale", "apple jack", "bacardi"]


def is_alcoholic(text: str) -> bool:
    t = text.lower()
    return any(k in t for k in ALC_KW)


INGRED_LINE = re.compile(
    r"^(?:\d|[¼-¾⅐-⅞]|one|two|three|four|five|six|half|juice|peel|piece|small|large|fill|dash|pony|jigger|teaspoon|tablespoon|wineglass|wine-glass|wine\s|lump|sprig|bottle|yolk|white|grate|pour|put|i\b|h\b)",
    re.I,
)
INSTR_LINE = re.compile(
    r"^(shake|stir|strain|frapp|fill|pour|mix|add|place|put|drop|squeeze|twist|float|serve|grate|sprinkle|drink|ornament|trim|dress|garnish|decorate)",
    re.I,
)


def parse_ingredients_from_body(body: str):
    out = []
    # MacElhone bodies are short; many are ratios in single sentence
    # Try to split first paragraph by commas
    text = body.split("\n", 1)[0] if body else ""
    # If body has fractions/measures, treat each ingredient as comma-segment containing fraction
    parts = re.split(r",\s*(?=(?:\d|[¼-¾⅐-⅞]|one|two|three|half|juice|peel|piece|small|large|fill|dash|pony|jigger|teaspoon|tablespoon|wineglass|lump|sprig|bottle|yolk|white))", text, flags=re.I)
    for p in parts:
        p = p.strip().rstrip(".")
        if p and re.search(r"[¼-¾⅐-⅞]|\d|teaspoon|tablespoon|dash|pony|wine|jigger|lump|bottle|piece|peel|juice|yolk|white", p, re.I):
            out.append({"text": p})
    return out


def parse_instructions(body: str):
    out = []
    for raw in body.split("\n"):
        ln = raw.strip().rstrip(".")
        if not ln:
            continue
        if INSTR_LINE.match(ln):
            out.append(ln + ".")
    return out


def main():
    src = RAW.read_text(encoding="latin-1")
    lines = src.split("\n")
    n = len(lines)

    # Find recipe section: starts at first numbered "1." Abyssinia
    # Find ending: last numbered recipe (391 / Zosmak), then maybe stop.
    # Find true start: first line containing "Absinthe Cocktail" / "Cocktail" header section
    # (it's after wine descriptions). Search for "1. Absinthe Cocktail" or "1. Abyssinia Cocktail".
    section_start = 0
    for i, ln in enumerate(lines):
        if re.match(r"^\s*1\s*\.\s*(Absinthe|Abyssinia)\s+Cocktail", ln):
            section_start = i
            break

    starts = []
    for i, ln in enumerate(lines):
        if i < section_start:
            continue
        m = NUM_RE.match(ln)
        if m:
            num = re.sub(r"\s", "", m.group(1))
            if num.isdigit():
                starts.append((i, int(num), m.group(2).strip().rstrip(".").strip()))

    if not starts:
        print("No recipes found")
        return

    records = []
    for idx, (line_i, num, title) in enumerate(starts):
        body_start = line_i + 1
        body_end = starts[idx + 1][0] if idx + 1 < len(starts) else min(line_i + 60, n)
        body_lines = []
        for j in range(body_start, body_end):
            ln = lines[j]
            if PAGE_NUM_RE.fullmatch(ln):
                continue
            # Skip ad blocks: heuristic: long blocks of all-caps or known brand names
            if re.search(r"^\s*[A-Z\s\d\.\,\-]{15,}$", ln) and len(ln.strip()) > 25:
                # Likely an ad header
                continue
            body_lines.append(ln)
        body = "\n".join(l.rstrip() for l in body_lines if l.strip()).strip()
        if not body:
            continue
        # Trim body if it spans into next recipe text accidentally (already bounded)
        # Truncate at "(Recipe by" attribution? - keep, it's part of original.
        # Strip trailing ad fragments — heuristic: remove orphan all-caps lines
        body = re.sub(r"\n\s*[A-Z][A-Z\s]{12,}\s*\n", "\n", body)

        original = f"{num}. {title}.\n{body}"

        rec = {
            "title": title,
            "content_type": "cocktail",
            "is_alcoholic": 1 if is_alcoholic(body) else 0,
            "is_historic": 1,
            "source_book": "ABC of Mixing Cocktails (Harry MacElhone, 1923)",
            "source_year": 1923,
            "source_region": "Paris, France",
            "cuisine": "cocktail",
            "description": "",
            "servings": 1,
            "prep_minutes": 3,
            "cook_minutes": 0,
            "original_text": original,
            "modernized_text": "",
            "instructions": parse_instructions(body),
            "ingredients": parse_ingredients_from_body(body),
            "glass_type": detect_glass(body),
            "method": detect_method(body),
            "garnish": "",
            "abv_percent": None,
        }
        records.append(rec)

    OUT.write_text("\n".join(json.dumps(r, ensure_ascii=False) for r in records) + "\n", encoding="utf-8")
    print(f"Wrote {len(records)} records to {OUT}")


if __name__ == "__main__":
    main()

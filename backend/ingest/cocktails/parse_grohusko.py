#!/usr/bin/env python3
"""Parse Grohusko 'Jack's Manual' (1908) djvu.txt into NDJSON cocktail records.

Strategy:
- Recipe titles are ALL-CAPS lines (often with trailing dot).
- Body lines follow until next ALL-CAPS title or blank-blank gap.
- Skip non-cocktail sections: appetizers, sandwiches, ads, vintage essays.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).parent
RAW = ROOT / "raw" / "grohusko_raw.txt"
OUT = ROOT / "grohusko-1908.ndjson"

# Skip these lines (food, ads, narrative chapters)
SKIP_TITLES = {
    "APPETIZERS", "APPETIZING SANDWICHES", "CLUB SANDWICH", "EGG SANDWICH",
    "LETTUCE MAYONNAISE SANDWICH", "NUT SANDWICH", "ROQUEFORT CHEESE SANDWICH",
    "ANCHOVY SANDWICH", "CAVIAR SANDWICH", "CHEESE STRAWS", "CANAPE, WHIMSAY",
    "SALAD, DON QUIXOTE", "SALMON ON TOAST", "INDEX", "JEREZ-QUINA",
    "EXCELLENT TONIC", "APPETIZER & FEBRIFUGE", "SHIPPED BY",
    "BEEF TEA", "BICARBONATE OF SODA",
}

# Heuristic: recipe title is ALL CAPS, optionally followed by ".", contains 1+ word chars
TITLE_RE = re.compile(r"^([A-Z][A-Z0-9'.\-&,()/ ]{2,60})[.\s]*$")

GLASS_HINTS = {
    "cocktail glass": "cocktail",
    "champagne glass": "coupe",
    "fizz glass": "highball",
    "highball": "highball",
    "high-ball": "highball",
    "high ball": "highball",
    "wine glass": "wine",
    "wine-glass": "wine",
    "punch bowl": "punch_bowl",
    "old-fashioned glass": "rocks",
    "old fashioned glass": "rocks",
    "tumbler": "rocks",
    "ale glass": "pint",
    "fizz": "highball",
    "stem glass": "coupe",
    "bar glass": "rocks",
    "thin bar glass": "rocks",
    "large glass": "highball",
    "small glass": "cocktail",
    "small bar glass": "cocktail",
    "lemonade glass": "highball",
    "champagne": "coupe",
}

METHOD_HINTS = [
    (re.compile(r"\bshake", re.I), "shaken"),
    (re.compile(r"\bfrappe", re.I), "shaken"),
    (re.compile(r"\bstir", re.I), "stirred"),
    (re.compile(r"\bbuild|fill\s+glass\b", re.I), "built"),
    (re.compile(r"\bmuddle", re.I), "muddled"),
]


def is_recipe_title(line: str) -> bool:
    s = line.strip()
    if len(s) < 3 or len(s) > 60:
        return False
    if s in SKIP_TITLES:
        return False
    # Must have at least one letter
    letters = [c for c in s if c.isalpha()]
    if not letters:
        return False
    # Must be predominantly uppercase
    upper = sum(1 for c in letters if c.isupper())
    if upper / len(letters) < 0.85:
        return False
    # Should have at least one space OR be a known short title pattern - but require min 1 word
    if not TITLE_RE.match(s):
        return False
    # Reject all-cap quotes/acknowledgments
    if any(w in s for w in ("COPYRIGHT", "INDEX", "PAGE", "MIXED DRINKS", "JACK'S MANUAL")):
        return False
    return True


def detect_glass(text: str) -> str:
    t = text.lower()
    for k, v in GLASS_HINTS.items():
        if k in t:
            return v
    return ""


def detect_method(text: str) -> str:
    for pat, m in METHOD_HINTS:
        if pat.search(text):
            return m
    return ""


# Detect non-alcoholic
def is_alcoholic(text: str) -> bool:
    t = text.lower()
    alcs = ["whisky", "whiskey", "rum", "gin", "brandy", "vermouth", "absinthe",
            "bitters", "wine", "champagne", "rye", "scotch", "bourbon",
            "applejack", "cider brandy", "port", "sherry", "curacao", "creme",
            "chartreuse", "benedictine", "maraschino", "anisette", "amer picon",
            "byrrh", "dubonnet", "fernet", "kirsch", "cointreau", "kummel",
            "bagardie", "rum,", "ale", "porter", "sloe gin"]
    return any(a in t for a in alcs)


def parse_ingredients(body: str):
    """Crude ingredient extractor: lines containing measure-ish patterns."""
    ings = []
    for raw in body.split("\n"):
        ln = raw.strip().rstrip(".")
        if not ln:
            continue
        # Lines that start with measure or %
        if re.match(r"^(\d|[%¾½⅓⅔¼¾⅛]|one|two|three|four|five|half|juice|peel|piece|small|large|fill|dash|pony|jigger|teaspoon|tablespoon|wine|lump|sprig|bottle|yolk|white|grate|i\b|h\b|to\s)\b", ln, re.I) \
                or re.match(r"^\d+\s*%", ln) \
                or re.match(r"^[%i\d]", ln):
            # Skip purely instructional lines
            if re.search(r"\b(stir|shake|strain|serve|frappe|fill\s+glass\s+with\s+(broken|cracked|fine|shaved)\s+ice\b)", ln, re.I) and len(ln) < 60:
                # Could be "Fill glass with broken ice" - that's an instruction
                if re.match(r"^fill\s+glass\b", ln, re.I) or re.match(r"^stir|^shake|^strain|^serve|^frappe", ln, re.I):
                    continue
            ings.append({"text": ln})
    return ings


def parse_instructions(body: str):
    """Lines that look like instructions (start with verb)."""
    out = []
    verbs = re.compile(r"^(shake|stir|strain|frappe|fill|pour|mix|add|place|put|drop|squeeze|twist|float|serve|grate|sprinkle|drink|ornament|trim|dress)", re.I)
    for raw in body.split("\n"):
        ln = raw.strip().rstrip(".")
        if not ln:
            continue
        if verbs.match(ln):
            out.append(ln + ".")
    return out


def main():
    text = RAW.read_text(encoding="utf-8", errors="replace")
    lines = text.split("\n")

    # Find start of recipe section: first all-caps title after the index ends.
    # Index ends around line 2263 (ABSINTHE.). We'll start scanning at line 2200.
    start = 0
    for i, ln in enumerate(lines):
        if i > 2200 and is_recipe_title(ln):
            start = i
            break

    # End: stop at "APPETIZERS" (line ~6756) but include CHAMPAGNE COCKTAIL/JULEP/COBBLER which appear after
    # Actually the structure has cocktail recipes interleaved with food after appetizers.
    # We'll just stop at line 6940 (just past the last cocktail recipe MAMIE TAYLOR).
    END_HARD = 6953

    records = []
    i = start
    while i < min(len(lines), END_HARD):
        ln = lines[i]
        if is_recipe_title(ln):
            title = ln.strip().rstrip(".").strip()
            # Collect body until next title or page break (multi blank lines)
            body_lines = []
            j = i + 1
            blank_run = 0
            while j < min(len(lines), END_HARD):
                nxt = lines[j]
                if is_recipe_title(nxt):
                    break
                if nxt.strip() == "":
                    blank_run += 1
                    if blank_run > 4:
                        break
                else:
                    blank_run = 0
                # Skip page numbers
                if re.fullmatch(r"\s*\d{1,3}\s*", nxt):
                    j += 1
                    continue
                body_lines.append(nxt.rstrip())
                j += 1
            body = "\n".join(l for l in body_lines if l.strip()).strip()
            i = j
            if not body or len(body) < 8:
                continue
            # Title cleanup
            disp_title = " ".join(w.capitalize() for w in title.split()) \
                .replace("Cocktail", "Cocktail") \
                .replace("Highball", "Highball") \
                .replace("Fizz", "Fizz")
            # Filter food/sandwich entries
            if any(t in body.upper() for t in ("MAYONNAISE", "ANCHOVIES", "ROQUEFORT", "PARMESAN")):
                continue
            if not is_alcoholic(body) and "EGG NOGG" not in title and "FIZZ" not in title and "TODDY" not in title:
                # Allow non-alcoholic if it's clearly a drink
                if not any(k in title.upper() for k in ("LEMONADE", "GINGER ALE", "GRAPE-FRUIT", "GRAPE FRUIT")):
                    continue
            original = (title + "\n" + body).strip()
            rec = {
                "title": disp_title,
                "content_type": "cocktail",
                "is_alcoholic": 1 if is_alcoholic(body) else 0,
                "is_historic": 1,
                "source_book": "Jack's Manual (J.A. Grohusko, 1908)",
                "source_year": 1908,
                "source_region": "New York, USA",
                "cuisine": "cocktail",
                "description": "",
                "servings": 1,
                "prep_minutes": 3,
                "cook_minutes": 0,
                "original_text": original,
                "modernized_text": "",
                "instructions": parse_instructions(body),
                "ingredients": parse_ingredients(body),
                "glass_type": detect_glass(body),
                "method": detect_method(body),
                "garnish": "",
                "abv_percent": None,
            }
            records.append(rec)
        else:
            i += 1

    OUT.write_text("\n".join(json.dumps(r, ensure_ascii=False) for r in records) + "\n", encoding="utf-8")
    print(f"Wrote {len(records)} records to {OUT}")


if __name__ == "__main__":
    main()

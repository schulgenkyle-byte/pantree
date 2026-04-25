#!/usr/bin/env python3
"""
Parse Jerry Thomas's 1862 "How to Mix Drinks; or, The Bon-Vivant's Companion"
into NDJSON for pan-tree's Mixology tab.

Source: Internet Archive OCR of the 1862 first edition (howtomixdrinkso00schugoog).
Project Gutenberg does not carry this title; Archive.org is the canonical source.

Output: jerry-thomas-1862.ndjson (one cocktail per line)
"""

import json
import re
import os
from pathlib import Path

HERE = Path(__file__).parent
SRC = HERE / "_tmp" / "jt_htmd.txt"
OUT = HERE / "jerry-thomas-1862.ndjson"

# ---------------- OCR cleanup ----------------

# Common OCR substitutions in this scan
OCR_FIXES = [
    (r"CocktaiL", "Cocktail"),
    (r"\bUm\b", "Use"),
    (r"\bUs\*\b", "Use"),
    (r"\bUs \*", "Use"),
    (r"\b0se\b", "Use"),
    (r"\bOso\b", "Use"),
    (r"\bUm ", "Use "),
    (r"\bsyrnp\b", "syrup"),
    (r"\bthrongh\b", "through"),
    (r"\bpopolar\b", "popular"),
    (r"\bbeholder\b", "beholder"),
    (r"\bnpon\b", "upon"),
    (r"\brepresented\b", "represented"),
    (r"\bnectareous\b", "nectareous"),
    (r"\bBHE2KV\b", "SHERRY"),
    (r"\bCura9oa\b", "Curacoa"),
    (r"Cura\$oa", "Curacoa"),
    (r"\bram\b", "rum"),  # frequent rum/ram OCR flip
    (r"fcc\b", "&c."),
    (r"<fcc", "&c."),
    (r"<fee", "&c."),
    (r"\bBogart\b", "Boker"),  # (some scans render Boker as Bogart; keep original here though)
]

# Fractional / measure OCR artifacts. We do NOT modify original_text heavily —
# only fix the obvious typographic glitches that mangle readability.
def light_clean(text: str) -> str:
    t = text
    # Replace mojibake / OCR fraction artifacts with readable markers.
    # '£' and '$' and '|' and 'i' in Jerry Thomas scans usually mean '1/2'.
    # We ONLY do this in the visible original_text for readability; the
    # ingredient parser uses its own heuristics.
    t = t.replace("�", "1/2")  # replacement char likely was ½
    # unify whitespace
    t = re.sub(r"[ \t]+", " ", t)
    # strip obvious page headers like "60 CHAMPAGNE COCKTAIL." lines.
    # (Page numbers + uppercase running head)
    t = re.sub(r"\n\s*\d{1,3}\s+[A-Z][A-Z\.\s&'\",]{4,}[\s']*\n", "\n", t)
    t = re.sub(r"\n\s*[A-Z][A-Z\.\s&'\",]{6,}\s*\d{1,3}[\s\.\',]*\n", "\n", t)
    # collapse 2+ blank lines
    t = re.sub(r"\n{3,}", "\n\n", t)
    return t.strip()

# ---------------- Recipe slicing ----------------

# Matches e.g. "107. Brandy Cocktail." at the start of a line.
# OCR sometimes replaces digits: "80." for 89, "00." for 90, etc.
# We accept any 1-3 digit leading number, then a period, then a Titled name.
RECIPE_HEAD = re.compile(
    r"^\s*(\d{1,3})\.\s+([A-Z][A-Za-z0-9\-\.\'\" &\(\)]{2,80})\s*[\.,]?\s*$",
    re.MULTILINE,
)

# Category / non-recipe heads we should skip. Keys are the *expected* number
# (which we infer by position in the book) OR fragments that unambiguously
# match a non-recipe entry.
SKIP_TITLES = {
    "PUNCH",  # generic
    "JULEPS",
    "THE SMASH",
    "THE COBBLER",
    "THE COCKTAIL & CRUSTA",
    "THE COCKTAIL & CRUSTA.",
    "MULLS AND SANGAREES",
    "MULLS AND SANGAEEES",
    "TODDIES AND SLINGS",
    "FIXES AND SOURS",
    "FLIP, NEGUS AND SHRUB",
    "FANCY DRINKS",
    "MISCELLANEOUS DRINKS",
    "TEMPERANCE DRINKS",
    "EGG NOGG",  # the plain "EGG NOGG" header at #80 is a section, but there's also a numbered egg nogg recipe; we handle by content check
}

# Known non-cocktail text noise. If a recipe has fewer than a minimum number of
# ingredient-like lines AND is very short, we skip.

# Lines that indicate "use X glass"
GLASS_LINE = re.compile(r"\(\s*use[^)]+\)|\(\s*us[e\*]\s+[^)]+\)|\(\s*Oso[^)]+\)|\(\s*0se[^)]+\)", re.IGNORECASE)

# ---------------- Ingredient parsing ----------------

INGREDIENT_UNIT_MAP = {
    # raw token -> (quantity_mult, canonical_unit)
    # caller should start with a numeric quantity they parsed, then multiply
    "wine-glass": (1.0, "wine-glass"),
    "wine glass": (1.0, "wine-glass"),
    "wineglass": (1.0, "wine-glass"),
    "pony-glass": (1.0, "pony-glass"),
    "pony glass": (1.0, "pony-glass"),
    "ponyglass": (1.0, "pony-glass"),
    "jigger": (1.0, "jigger"),
    "table-spoonful": (1.0, "tbsp"),
    "tablespoonful": (1.0, "tbsp"),
    "table-spoon": (1.0, "tbsp"),
    "tablespoon": (1.0, "tbsp"),
    "teaspoonful": (1.0, "tsp"),
    "tea-spoonful": (1.0, "tsp"),
    "teaspoon": (1.0, "tsp"),
    "tea-spoon": (1.0, "tsp"),
    "dash": (1.0, "dash"),
    "dashes": (1.0, "dash"),
    "gill": (1.0, "gill"),
    "quart": (1.0, "quart"),
    "quarts": (1.0, "quart"),
    "pint": (1.0, "pint"),
    "pints": (1.0, "pint"),
    "gallon": (1.0, "gallon"),
    "gallons": (1.0, "gallon"),
    "ounce": (1.0, "oz"),
    "ounces": (1.0, "oz"),
    "lb": (1.0, "lb"),
    "lbs": (1.0, "lb"),
    "bottle": (1.0, "bottle"),
    "bottles": (1.0, "bottle"),
    "glass": (1.0, "glass"),
    "piece": (1.0, "piece"),
    "pieces": (1.0, "pieces"),
    "slice": (1.0, "slice"),
    "slices": (1.0, "slices"),
    "lump": (1.0, "lump"),
    "lumps": (1.0, "lumps"),
    "egg": (1.0, "piece"),
    "eggs": (1.0, "pieces"),
    "drop": (1.0, "drop"),
    "drops": (1.0, "drop"),
}

# Quantity token parser — handles "1", "1$", "1£", "i", "$", "£", etc.
# OCR commonly renders "½" as "£" or "$" or "i", and "¼" as "\"
def parse_quantity(tok: str) -> float:
    t = tok.strip().replace(",", "").strip(".")
    # Unicode fraction
    frac_map = {"½": 0.5, "¼": 0.25, "¾": 0.75, "⅓": 1/3, "⅔": 2/3}
    total = 0.0
    # grab leading integer
    m = re.match(r"^(\d+)\s*([\$£i½¼¾⅓⅔])?$", t)
    if m:
        whole = int(m.group(1))
        frac_char = m.group(2)
        if frac_char in frac_map:
            return whole + frac_map[frac_char]
        if frac_char in ("$", "£", "i"):
            return whole + 0.5
        return float(whole)
    # pure fraction markers
    if t in frac_map:
        return frac_map[t]
    if t in ("$", "£"):
        return 0.5
    if t in ("f", "|", "}"):  # common ½ OCR artifacts
        return 0.5
    if t in ("i",):
        return 0.5
    # "1/2", "1/4" style
    m = re.match(r"^(\d+)\s*/\s*(\d+)$", t)
    if m:
        num = int(m.group(1))
        den = int(m.group(2))
        return num / den if den else 0.0
    # "1 1/2"
    m = re.match(r"^(\d+)\s+(\d+)\s*/\s*(\d+)$", t)
    if m:
        return int(m.group(1)) + int(m.group(2)) / int(m.group(3))
    # fallthrough: try plain number
    try:
        return float(t)
    except ValueError:
        return 0.0


def qty_to_oz(qty: float, unit: str) -> float:
    """Approximate ounce value for ABV / modernized_text reasoning. Unused for now."""
    m = {
        "wine-glass": 2.0, "pony-glass": 1.0, "jigger": 1.5,
        "tbsp": 0.5, "tsp": 1/6, "dash": 1/32, "gill": 4.0,
        "oz": 1.0, "pint": 16.0, "quart": 32.0, "gallon": 128.0,
        "bottle": 25.4, "glass": 2.0,
    }
    return qty * m.get(unit, 0.0)


AISLE_MAP = {
    "brandy": "bar", "cognac": "bar", "rum": "bar", "gin": "bar",
    "whiskey": "bar", "whisky": "bar", "bourbon": "bar", "scotch": "bar",
    "rye": "bar", "champagne": "bar", "sherry": "bar", "port": "bar",
    "wine": "bar", "claret": "bar", "sauterne": "bar", "hock": "bar",
    "chablis": "bar", "curacoa": "bar", "maraschino": "bar",
    "bitters": "bar", "absinthe": "bar", "arrack": "bar",
    "kirschwasser": "bar", "apple-jack": "bar", "apple jack": "bar",
    "madeira": "bar", "peach brandy": "bar", "noyau": "bar",
    "ale": "bar", "porter": "bar", "cider": "bar", "vermouth": "bar",
    "orgeat": "bar", "gum syrup": "bar", "raspberry syrup": "bar",
    "vanilla": "bar", "seltzer": "bar", "seltzer-water": "bar",
    "sugar": "pantry", "honey": "pantry", "molasses": "pantry",
    "salt": "pantry", "cinnamon": "pantry", "cloves": "pantry",
    "nutmeg": "pantry", "allspice": "pantry", "snake-root": "pantry",
    "pepper": "pantry", "cayenne": "pantry", "orgeat syrup": "pantry",
    "pineapple syrup": "pantry", "cream of tartar": "pantry",
    "egg": "produce", "eggs": "produce", "milk": "dairy", "cream": "dairy",
    "butter": "dairy",
    "lemon": "produce", "lime": "produce", "orange": "produce",
    "pineapple": "produce", "pine-apple": "produce", "mint": "produce",
    "berries": "produce", "peach": "produce", "apple": "produce",
    "raisin": "produce", "raisins": "produce", "cucumber": "produce",
    "strawberry": "produce", "raspberry": "produce", "currant": "produce",
    "water": "other", "ice": "other", "boiling water": "other",
}

def guess_aisle(name: str) -> str:
    n = name.lower()
    for key, aisle in AISLE_MAP.items():
        if key in n:
            return aisle
    return "bar"  # most things in this book are booze


# Map Jerry Thomas glass to pan-tree glass types
def map_glass(raw: str) -> str:
    r = raw.lower()
    if "large bar" in r or "large tumbler" in r:
        return "collins"
    if "small bar" in r or "small tumbler" in r:
        return "rocks"
    if "tumbler" in r:
        return "rocks"
    if "wine" in r or "wine-glass" in r:
        return "coupe"
    if "goblet" in r:
        return "coupe"
    if "punch-bowl" in r or "bowl" in r:
        return "punch-bowl"
    if "mug" in r:
        return "mug"
    if "champagne" in r:
        return "flute"
    if "silver" in r:
        return "mug"
    if "decanter" in r:
        return "decanter"
    if "pony" in r:
        return "shot"
    return "rocks"


# Map section heading to method/category hints
def infer_method(category: str, text: str) -> str:
    low = text.lower()
    if "blaze" in low or "blazing" in low or "ignite" in low:
        return "blazed"
    if "hot" in category.lower() or "hot " in low[:120]:
        return "built"  # hot drinks built in glass
    if "shake" in low or "shaken" in low:
        return "shaken"
    if "stir" in low:
        return "stirred"
    if "build" in low or "fill " in low[:150]:
        return "built"
    if category in ("Cobbler", "Julep", "Smash"):
        return "built"
    return "built"


# Heuristic: strip OCR glitch characters from ingredient name strings.
def clean_ingredient_name(name: str) -> str:
    n = name.strip(" .,;:\"'*")
    n = re.sub(r"\s+", " ", n)
    n = n.replace("G-in", "gin").replace("Cura9oa", "Curacoa").replace("Cura$oa", "Curacoa")
    n = n.replace("pine-apple", "pineapple").replace("Pine-Apple", "Pineapple")
    n = n.replace("tea-spoonful", "teaspoonful")
    n = n.replace("-glass", " glass")
    # Normalize case: keep lowercase unless proper noun
    return n.strip()


# Extract ingredient lines. A Jerry Thomas recipe ingredient line tends to
# match "<qty> <unit> of <thing>." e.g. "1 wine-glass of brandy."
# Note: allow extra spaces/hyphens, allow "table -spoonful".
ING_LINE = re.compile(
    r"^\s*(?P<qty>[\d½¼¾⅓⅔\$£ifr|}/\s]+?)\s*"
    r"(?P<unit>wine[\-\s]*glass(?:es)?|pony[\-\s]*glass(?:es)?|jigger|"
    r"table[\-\s]*spoon(?:ful)?|tea[\-\s]*spoon(?:ful)?|dash(?:es)?|"
    r"gill[s]?|quart[s]?|pint[s]?|gallon[s]?|ounce[s]?|lb[s]?|bottle[s]?|"
    r"glass(?:es)?|piece[s]?|slice[s]?|lump[s]?|egg[s]?|drop[s]?|do[\.,]?)"
    r"[\s\^,\.]*"  # OCR glitches: "wine-glass^", "do," "do."
    r"(?:of\s+)?(?P<thing>.+?)\s*[,\.]?\s*$",
    re.IGNORECASE,
)

# Unit-less ingredient lines: things like "5 lbs. sugar.", "12 eggs.",
# "Juice of 6 lemons." or ingredient-only lines like "2 slices of orange,"
BARE_LINE = re.compile(
    r"^\s*(?P<qty>(?:\d+(?:\s*/\s*\d+)?|\d*[½¼¾⅓⅔\$£i]|1/2|1/4|3/4)[\s½¼¾⅓⅔\$£i]*)\s+(?P<thing>[A-Za-z][A-Za-z0-9\-\s,]+?)\s*\.?\s*$"
)

# Lines to treat as ingredient with "quantity to taste"
FLEX_QTY = re.compile(r"^\s*(?P<thing>[A-Za-z][A-Za-z\s\-]+?)\s+(?:to\s+taste|as\s+desired)\.?\s*$", re.IGNORECASE)

# Also match "Juice of N lemons." / "The juice of six lemons." / "The rind of two do."
JUICE_LINE = re.compile(
    r"^\s*(?:the\s+)?(?P<kind>juice|rind|peel)\s+of\s+(?P<qty>[\d½¼¾\s]+|one|two|three|four|five|six|seven|eight|nine|ten|twelve)\s+(?P<thing>.+?)\.?\s*$",
    re.IGNORECASE,
)

NUMBER_WORDS = {
    "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6,
    "seven": 7, "eight": 8, "nine": 9, "ten": 10, "eleven": 11,
    "twelve": 12, "dozen": 12, "half": 0.5, "quarter": 0.25,
}


def parse_ingredient_line(line: str, prev_unit: str | None):
    """Return (ingredient_dict, unit_used) or (None, None)."""
    line = line.strip().rstrip(".,;")
    if not line:
        return None, None

    # Handle "Juice of N lemons" / "The rind of two do."
    m = JUICE_LINE.match(line)
    if m:
        qty_raw = m.group("qty").strip().lower()
        if qty_raw in NUMBER_WORDS:
            qty = float(NUMBER_WORDS[qty_raw])
        else:
            qty = parse_quantity(qty_raw)
        thing = clean_ingredient_name(m.group("thing"))
        if not thing:
            return None, None
        # "do." = ditto, but in "rind of two do." the 'do' means previous fruit
        if thing.lower().strip(".") == "do":
            thing = "lemons"
        kind = m.group("kind").lower()
        return {
            "name": f"{kind} of {thing.lower()}",
            "quantity": qty if qty else 1,
            "unit": "piece",
            "aisle": guess_aisle(thing),
        }, None

    # "5 lbs. sugar" / "12 eggs" / "2 slices of orange" - typical bare ingredient
    mb = BARE_LINE.match(line)
    if mb:
        qty_raw = mb.group("qty")
        thing = clean_ingredient_name(mb.group("thing"))
        qty = parse_quantity(qty_raw)
        # Require at least one known food-noun to avoid capturing narrative.
        if any(k in thing.lower() for k in ["egg", "lemon", "orange", "peach",
            "pineapple", "pine-apple", "sugar", "lime", "apple", "slice", "berries",
            "cucumber", "raisin", "cherry", "cherries", "mint", "currant", "lump",
            "piece", "quart", "pint", "gallon", "bottle", "gill", "dash"]):
            # Figure unit by common cases
            unit = "piece"
            low = thing.lower()
            if "lb" in low:
                unit = "lb"; thing = re.sub(r"lbs?\.?", "", thing, flags=re.IGNORECASE).strip()
            elif "slice" in low:
                unit = "slices"; thing = re.sub(r"slices?\s+of\s+", "", thing, flags=re.IGNORECASE).strip()
            elif "lump" in low:
                unit = "lumps"; thing = re.sub(r"lumps?\s+of\s+", "", thing, flags=re.IGNORECASE).strip()
            elif "quart" in low:
                unit = "quart"; thing = re.sub(r"quarts?\s+of\s+", "", thing, flags=re.IGNORECASE).strip()
            elif "pint" in low:
                unit = "pint"; thing = re.sub(r"pints?\s+of\s+", "", thing, flags=re.IGNORECASE).strip()
            elif "gallon" in low:
                unit = "gallon"; thing = re.sub(r"gallons?\s+of\s+", "", thing, flags=re.IGNORECASE).strip()
            elif "gill" in low:
                unit = "gill"; thing = re.sub(r"gills?\s+of\s+", "", thing, flags=re.IGNORECASE).strip()
            elif "bottle" in low:
                unit = "bottle"; thing = re.sub(r"bottles?\s+of\s+", "", thing, flags=re.IGNORECASE).strip()
            elif "dash" in low:
                unit = "dash"; thing = re.sub(r"dashes?\s+of\s+", "", thing, flags=re.IGNORECASE).strip()
            elif "piece" in low:
                unit = "piece"; thing = re.sub(r"pieces?\s+of\s+", "", thing, flags=re.IGNORECASE).strip()
            return {
                "name": thing.lower().strip(),
                "quantity": qty if qty else 1,
                "unit": unit,
                "aisle": guess_aisle(thing),
            }, unit

    m = ING_LINE.match(line)
    if m:
        qty_raw = m.group("qty")
        unit_raw = m.group("unit").lower().strip()
        thing = clean_ingredient_name(m.group("thing"))
        if not thing or len(thing) > 120:
            return None, None

        qty = parse_quantity(qty_raw)

        # "do." means "ditto" — same unit as prior line
        if unit_raw.startswith("do"):
            unit_raw = prev_unit or "tbsp"

        # normalize unit token
        unit_norm = unit_raw.replace(" ", "-")
        unit_norm = unit_norm.rstrip("s")  # plural
        unit_norm = unit_norm.rstrip(".")
        # map to INGREDIENT_UNIT_MAP
        canonical = None
        for k, v in INGREDIENT_UNIT_MAP.items():
            if unit_norm == k or unit_norm == k.rstrip("s"):
                canonical = v[1]
                break
        if not canonical:
            # fuzzier match
            if "spoon" in unit_norm and "tea" in unit_norm:
                canonical = "tsp"
            elif "spoon" in unit_norm:
                canonical = "tbsp"
            elif "wine" in unit_norm:
                canonical = "wine-glass"
            elif "pony" in unit_norm:
                canonical = "pony-glass"
            elif "dash" in unit_norm:
                canonical = "dash"
            else:
                canonical = unit_norm or "piece"

        return {
            "name": thing.lower(),
            "quantity": qty if qty else 1,
            "unit": canonical,
            "aisle": guess_aisle(thing),
        }, canonical

    return None, None


# ---------------- Category detection ----------------

CATEGORY_HEADERS = [
    (1, "Punch"),
    (87, "Julep"),
    (93, "Smash"),
    (97, "Cobbler"),
    (105, "Cocktail"),
    (119, "Mull / Sangaree"),
    (131, "Toddy / Sling"),
    (139, "Fix / Sour"),
    (144, "Flip / Negus / Shrub"),
    (161, "Fancy Drink"),
    (196, "Miscellaneous"),
]

def category_for(num: int) -> str:
    cat = "Punch"
    for n, name in CATEGORY_HEADERS:
        if num >= n:
            cat = name
    return cat


# ---------------- Main parse ----------------

def main():
    raw = SRC.read_text(encoding="utf-8", errors="replace")
    # Light OCR fixes (non-destructive to original_text capture, applied only
    # to searching — we recapture original_text from the original raw text).
    cleaned = raw

    # Find every recipe head (line-number, num, name)
    heads = []
    for m in RECIPE_HEAD.finditer(cleaned):
        num = int(m.group(1))
        name = m.group(2).strip(" .")
        heads.append((m.start(), num, name))

    # Recipe body = between one head and the next head (or until the
    # "MANUAL FOR THE MANUFACTURE" appendix which starts near line 5300 or so,
    # originally headed by a section title).
    # We cap recipes at #220 (last cocktail before temperance).
    # Temperance section starts at #221 and its recipes are mostly
    # non-alcoholic — skip per task spec.

    # Determine cutoff — end of cocktails section
    end_marker = cleaned.find("221. TEMPERANCE DRINKS")
    if end_marker < 0:
        # alt OCR
        end_marker = cleaned.find("TEMPERANCE DRINKS")
    if end_marker < 0:
        end_marker = len(cleaned)

    # Build list of recipe segments
    filtered_heads = [h for h in heads if h[0] < end_marker]
    segments = []
    for i, (pos, num, name) in enumerate(filtered_heads):
        next_pos = filtered_heads[i + 1][0] if i + 1 < len(filtered_heads) else end_marker
        body = cleaned[pos:next_pos]
        segments.append((num, name, body))

    print(f"Found {len(segments)} candidate recipe heads (pre-filter).")

    # Apply OCR fix for common title misreads. We do NOT alter original_text.
    def looks_like_category(num: int, name: str, body: str) -> bool:
        up = name.upper().strip(".")
        if up in SKIP_TITLES:
            # body confirmation: if the body has a "(Use ... glass.)" within
            # 300 chars, it's probably a real recipe labeled with a category
            # (some section headers share numbering with the first recipe).
            after_head = body.split("\n", 1)[1] if "\n" in body else ""
            if re.search(r"\(use\b|\(us[*e]\s|\(oso|\(0se", after_head[:400], re.IGNORECASE):
                return False
            return True
        return False

    recipes_out = []
    for num, name, body in segments:
        # Skip category headers
        if looks_like_category(num, name, body):
            continue

        # Skip the #87 Juleps / #105 Cocktail & Crusta text where the recipe
        # number marks an essay not a drink.
        up = name.upper().strip(".")
        if up == "EGG NOGG" and num == 80:
            # section header right before numbered Egg Nogg recipes
            continue
        if up == "PUNCH" or up == "JULEPS" or up == "THE SMASH" or \
           up == "THE COBBLER" or up.startswith("THE COCKTAIL") or \
           up.startswith("MULLS") or up.startswith("TODDIES") or \
           up.startswith("FIXES") or up.startswith("FLIP") or \
           up.startswith("FANCY DRINKS") or up.startswith("MISCELLANEOUS"):
            continue

        # Parse original text: the stuff after the head line until next head.
        body_after_head = body.split("\n", 1)[1] if "\n" in body else ""
        original_text = light_clean(body_after_head)
        if len(original_text) < 25:
            continue

        # Find glass type
        glass_m = GLASS_LINE.search(original_text)
        glass_raw = glass_m.group(0) if glass_m else ""
        glass_type = map_glass(glass_raw)

        # Ingredient extraction — walk line by line until we hit the
        # narrative instructions (usually a line beginning with a capital
        # verb: "Fill", "Shake", "Stir", "Mix", "Pour", "Place", "Put",
        # "Squeeze", "Dress", "Serve", "Sweeten", "Beat", "Strain",
        # "Ornament", "Grate", etc.)
        ingredients = []
        lines = [ln.strip() for ln in original_text.split("\n")]
        # drop the (Use ... glass.) line and blank lines
        content_lines = []
        for ln in lines:
            if not ln:
                continue
            if re.match(r"^\(.*\)\.?$", ln, re.IGNORECASE):
                continue
            content_lines.append(ln)

        instructions_text_parts = []
        prev_unit = None
        ing_section_done = False
        seen_any_ing = False
        for ln in content_lines:
            # Try to parse as ingredient first (regardless of section marker)
            parsed, unit_used = parse_ingredient_line(ln, prev_unit)
            if parsed:
                ingredients.append(parsed)
                seen_any_ing = True
                if unit_used:
                    prev_unit = unit_used
                continue
            if ing_section_done:
                instructions_text_parts.append(ln)
                continue
            # Heuristic: verb-start line means instructions began, but only
            # once we've already captured at least one ingredient (otherwise
            # preambles like "Put the following ingredients..." won't kill
            # downstream ingredient parsing).
            verb_m = re.match(
                r"^(Fill|Shake|Stir|Mix|Pour|Place|Squeeze|Dress|Serve|Sweeten|"
                r"Beat|Strain|Ornament|Grate|Ignite|Take|Cut|Slice|Add|Use|Let|"
                r"Split|Rub|Boil|Bring|To|For|Make|Dissolve|Cover|"
                r"Float|Drop|Garnish|Prepare|When|If|After|Before|Steep|"
                r"Rasp|Pare|Chop|Bottle|Cork|Wipe|Strew|Squeeze)\b",
                ln,
            )
            if verb_m and seen_any_ing:
                ing_section_done = True
                instructions_text_parts.append(ln)
            else:
                # neither parseable ingredient nor verb — treat as prose
                instructions_text_parts.append(ln)

        # Build instructions list from the narrative portion, split by "." or
        # ";" into sentence-ish steps.
        narrative = " ".join(instructions_text_parts)
        narrative = re.sub(r"\s+", " ", narrative).strip()
        # Strip leading "NN. Title." echo
        narrative = re.sub(
            rf"^\s*{num}\.\s+[A-Z][^.]*?\.\s*",
            "",
            narrative,
        )
        narrative = re.sub(rf"^\s*{num}\.\s*", "", narrative)
        # Also strip a bare title echo
        title_plain = re.escape(name.rstrip(",.").strip())
        narrative = re.sub(rf"^\s*{title_plain}[\.,]?\s*", "", narrative, count=1, flags=re.IGNORECASE)
        # Remove page-header running titles that leak in (e.g. "PINEAPPLE JULEP, 45'")
        narrative = re.sub(r"\b[A-Z][A-Z ,\.\-&']{5,}\s*\d{1,3}['\. ]*", "", narrative)
        # Split into sentences
        steps = [s.strip() for s in re.split(r"(?<=[\.;])\s+(?=[A-Z])", narrative) if len(s.strip()) > 3]
        # Drop steps that are essentially empty or pure OCR noise
        def step_ok(s):
            if len(s) < 4:
                return False
            letters = sum(c.isalpha() for c in s)
            return letters >= 4
        steps = [s for s in steps if step_ok(s)]
        # Fallback — if we failed to extract ingredients or steps, the recipe
        # is likely a one-line prose variant like "Made the same as X".
        if not steps and narrative:
            steps = [narrative]

        # Description
        category = category_for(num)
        desc = f"A 19th-century {category.lower()}-style drink from Jerry Thomas's 1862 Bartenders Guide."

        # Method + garnish heuristics
        method = infer_method(category, narrative)
        garnish = ""
        gm = re.search(r"(berries in season|lemon peel|nutmeg|mint|orange peel|sliced orange|piece of pineapple)", narrative, re.IGNORECASE)
        if gm:
            garnish = gm.group(1).lower()

        # Raw-egg safety note
        safety = ""
        if re.search(r"\begg[s]?\b|\byolk[s]?\b|\bwhite[s]? of.*egg", original_text, re.IGNORECASE):
            safety = "Original uses raw egg; use pasteurized egg whites for modern prep."

        # Crude ABV estimate — 14% for most mixed drinks in this book; will be
        # refined downstream.  Wine cups ~10%, pure spirits ~35%.
        abv = 14
        low = original_text.lower()
        if "boiling water" in low and "wine-glass" in low:
            abv = 10
        if "ale" in low or "cider" in low or "porter" in low:
            abv = 8
        if "champagne" in low and "wine" in category.lower():
            abv = 11
        if category == "Fix / Sour":
            abv = 16
        if "straight" in name.lower() or "pony" in name.lower():
            abv = 35

        # Final record
        rec = {
            "title": name.replace("CocktaiL", "Cocktail").replace("G-in", "Gin").strip(),
            "content_type": "cocktail",
            "is_alcoholic": 1,
            "is_historic": 1,
            "source_book": "The Bon-Vivant's Companion (Jerry Thomas, 1862)",
            "source_year": 1862,
            "source_region": "New York, USA",
            "cuisine": "cocktail",
            "description": desc,
            "servings": 1 if category != "Punch" else 8,
            "prep_minutes": 5,
            "cook_minutes": 0,
            "original_text": original_text,
            "modernized_text": "",
            "instructions": steps,
            "ingredients": ingredients,
            "glass_type": glass_type,
            "method": method,
            "garnish": garnish,
            "abv_percent": abv,
            "jt_recipe_number": num,
            "jt_category": category,
        }
        if safety:
            rec["safety_notes"] = safety

        recipes_out.append(rec)

    # De-dup by title+number
    seen = set()
    unique = []
    for r in recipes_out:
        key = (r["jt_recipe_number"], r["title"].lower())
        if key in seen:
            continue
        seen.add(key)
        unique.append(r)

    # Write NDJSON
    with OUT.open("w", encoding="utf-8") as f:
        for r in unique:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    print(f"Wrote {len(unique)} recipes to {OUT}")
    # Sample a few famous titles
    titles = [r["title"] for r in unique]
    for famous in ["Tom and Jerry", "Blue Blazer", "Mint Julep", "Brandy Cocktail",
                   "Whiskey Cocktail", "Champagne Cocktail", "Sherry Cobbler",
                   "Philadelphia Fish-House Punch", "Japanese Cocktail",
                   "Knickerbocker", "Rocky Mountain Punch", "Absinthe",
                   "Stone Fence", "Brandy Crusta"]:
        hit = [t for t in titles if famous.lower() in t.lower()]
        if hit:
            print(f"  + {famous}: {hit[0]}")
        else:
            print(f"  - {famous}: MISSING")


if __name__ == "__main__":
    main()

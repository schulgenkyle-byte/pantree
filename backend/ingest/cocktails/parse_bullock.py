"""
COCKTAIL-BULLOCK harvest agent for pan-tree.

Parses Tom Bullock's 1917 "The Ideal Bartender" (Project Gutenberg #13487)
into NDJSON for the TRUE VINTAGE collection.
"""

import json
import os
import re
import sys
import urllib.request

OUT_PATH = os.path.join(
    os.path.dirname(__file__), "bullock-1917.ndjson"
)
RAW_PATH = os.path.join(os.path.dirname(__file__), "_tmp", "bullock_raw.txt")
GUTENBERG_URL = "https://www.gutenberg.org/cache/epub/13487/pg13487.txt"

CONTRIBUTOR_STORY = (
    "Head bartender at the St. Louis Country Club, Tom Bullock was the first "
    "African-American author of a cocktail book. Published in 1917 with a "
    "foreword by George Herbert Walker, grandfather of President George H.W. Bush."
)


def fetch_book():
    if os.path.exists(RAW_PATH) and os.path.getsize(RAW_PATH) > 50000:
        with open(RAW_PATH, "r", encoding="utf-8-sig") as f:
            text = f.read()
    else:
        os.makedirs(os.path.dirname(RAW_PATH), exist_ok=True)
        req = urllib.request.Request(GUTENBERG_URL, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=60) as r:
            text = r.read().decode("utf-8-sig", errors="replace")
        with open(RAW_PATH, "w", encoding="utf-8") as f:
            f.write(text)
    # Strip OCR-artifact regex tokens that leaked into Project Gutenberg text
    text = re.sub(r"\\s\+\\d\\d\?", "", text)
    return text


SMALL_WORDS = {"of", "the", "and", "in", "on", "for", "to", "with", "a", "an",
               "or", "by", "at", "from", "la", "de", "del", "el"}
ROMAN_OK = {"st.", "n.y.", "u.s.", "u.s.a.", "n.y.c."}


def smart_title(s):
    """Title-case while preserving abbreviations and apostrophes."""
    parts = s.split(" ")
    out = []
    prev_token = ""
    for i, w in enumerate(parts):
        if not w:
            out.append(w)
            continue
        # Preserve all-caps short tokens (e.g., G.O.P., L.P.W.)
        letters = re.sub(r"[^A-Za-z]", "", w)
        if len(letters) <= 4 and letters.isupper() and "." in w:
            out.append(w)
            prev_token = w
            continue
        # Determine if this word starts a new clause (after ( or " or beginning)
        starts_clause = (
            i == 0 or
            prev_token.endswith("(") or
            prev_token.endswith('"') or
            prev_token.endswith('--') or
            prev_token.endswith('---') or
            (prev_token and prev_token[-1] in '("')
        )
        # Some tokens are just punctuation, skip
        if w in {'"', "(", ")", "'"}:
            out.append(w)
            prev_token = w
            continue
        # Words with leading parenthesis or quote: still capitalize their first letter
        prefix = ""
        word_core = w
        m = re.match(r'^([\("\']+)(.*)$', w)
        if m:
            prefix = m.group(1)
            word_core = m.group(2)
        # Lowercase small connector words (but not first word and not after clause start)
        if not starts_clause and word_core.lower() in SMALL_WORDS:
            out.append(prefix + word_core.lower())
            prev_token = w
            continue
        # Special tokens: 18th, 1st, 2nd
        if re.match(r"^\d+(st|nd|rd|th)\.?$", word_core.lower()):
            out.append(prefix + word_core.lower())
            prev_token = w
            continue
        # Hyphenated word: capitalize each subpart
        if "-" in word_core:
            sub = []
            for piece in word_core.split("-"):
                if piece:
                    sub.append(_cap_word(piece))
                else:
                    sub.append(piece)
            out.append(prefix + "-".join(sub))
            prev_token = w
            continue
        out.append(prefix + _cap_word(word_core))
        prev_token = w
    return " ".join(out)


def _cap_word(w):
    if not w:
        return w
    # Words like O'Hara, Bliz's, Players', Horse's
    if "'" in w:
        # Capitalize first letter; lowercase after apostrophe + s
        # e.g., Bliz's, Golfer's, Players', Horse's
        sub = w.split("'")
        sub[0] = sub[0].capitalize()
        for j in range(1, len(sub)):
            if sub[j].lower() in ("s", "t", "ll", "ve", "re", "d", "m"):
                sub[j] = sub[j].lower()
            elif sub[j]:
                sub[j] = sub[j].capitalize()
        return "'".join(sub)
    # Preserve mid-word caps if all-upper (acronyms with digits)
    return w.capitalize()


# Recipes start at "ABRICONTINE POUSSE CAFE" and end before "INDEX" line.
START_MARKER = "ABRICONTINE POUSSE CAFE"
END_MARKER = "\nINDEX"

# A recipe header line is ALL CAPS (with optional punctuation/numbers/style suffix
# like "--Country Club Style"). We identify them by being a single non-empty line
# whose alphabetic chars are all uppercase, followed by a blank line and body.
HEADER_RE = re.compile(
    r"^[A-Z0-9][A-Z0-9 \"'\.,&\-/\(\)]+$"
)


def is_header(line):
    s = line.rstrip()
    if not s:
        return False
    if len(s) < 3 or len(s) > 100:
        return False
    # Strip parenthetical clauses like "(for party of 6)" or "(2-gallon mixture)"
    # and style suffixes after "--" before checking.
    stripped = re.sub(r"\(.*?\)", "", s).strip()
    stripped = re.sub(r"--.*$", "", stripped).strip()
    if not stripped:
        return False
    # The core part (before -- or parens) must be all caps + allowed chars.
    if not HEADER_RE.match(stripped):
        return False
    letters = [c for c in stripped if c.isalpha()]
    if len(letters) < 2:
        return False
    if any(c.islower() for c in stripped):
        return False
    # Lines with only common words like "INDEX" are still headers; let caller filter.
    return True


def split_recipes(text):
    start = text.find(START_MARKER)
    end = text.find(END_MARKER, start)
    body = text[start:end]
    lines = body.splitlines()

    # Block-based: titles are lines that:
    # - are all-caps
    # - have a blank line before AND after them
    # - are followed by recipe content
    blocks = []
    current_title = None
    current_body = []
    i = 0
    while i < len(lines):
        line = lines[i]
        # candidate header
        prev_blank = (i == 0) or (lines[i - 1].strip() == "")
        next_blank = (i + 1 < len(lines)) and (lines[i + 1].strip() == "")
        if prev_blank and next_blank and is_header(line):
            # save previous
            if current_title is not None:
                blocks.append((current_title, "\n".join(current_body).strip()))
            current_title = line.strip()
            current_body = []
        else:
            if current_title is not None:
                current_body.append(line)
        i += 1
    if current_title is not None:
        blocks.append((current_title, "\n".join(current_body).strip()))
    return blocks


# --- Classification helpers ---

GLASS_KEYWORDS = [
    ("highball", "highball"),
    ("high ball", "highball"),
    ("cocktail glass", "cocktail"),
    ("champagne", "champagne"),
    ("punch glass", "punch"),
    ("punch bowl", "punch_bowl"),
    ("pousse cafe", "pousse_cafe"),
    ("sour glass", "sour"),
    ("toddy", "toddy"),
    ("whiskey glass", "rocks"),
    ("hot water glass", "mug"),
    ("mug", "mug"),
    ("stein", "stein"),
    ("ale glass", "pint"),
    ("beer glass", "pint"),
    ("fizz glass", "fizz"),
    ("shell glass", "fizz"),
    ("sherry glass", "sherry"),
    ("wineglass", "wine"),
    ("wine glass", "wine"),
    ("claret glass", "wine"),
    ("goblet", "goblet"),
    ("tumbler", "rocks"),
    ("stem glass", "coupe"),
    ("tall, thin glass", "highball"),
    ("tall thin glass", "highball"),
    ("collins", "collins"),
    ("rickey", "rickey"),
    ("brandy roller", "snifter"),
    ("silver mug", "julep"),
    ("silver mug", "julep"),
]


def detect_glass(title, body):
    t = (title + " " + body).lower()
    if "julep" in title.lower() and "silver" in t:
        return "julep"
    for kw, glass in GLASS_KEYWORDS:
        if kw in t:
            return glass
    if "julep" in title.lower():
        return "julep"
    if "fizz" in title.lower():
        return "fizz"
    if "sour" in title.lower():
        return "sour"
    if "cobbler" in title.lower():
        return "goblet"
    if "punch" in title.lower():
        return "punch"
    if "cocktail" in title.lower():
        return "cocktail"
    if "highball" in title.lower() or "high ball" in title.lower():
        return "highball"
    if "rickey" in title.lower():
        return "rickey"
    if "cooler" in title.lower():
        return "highball"
    return "bar"


def detect_method(title, body):
    b = body.lower()
    if "shake" in b or "shake well" in b or "shaker" in b:
        return "shaken"
    if "stir" in b:
        return "stirred"
    if "ignite" in b or "blaz" in b:
        return "flamed"
    if "boil" in b or "hot water" in b or "boiling water" in b:
        return "built_hot"
    if "fill up with" in b or "pour" in b:
        return "built"
    return "built"


def detect_garnish(body):
    b = body.lower()
    found = []
    if "nutmeg" in b:
        found.append("grated nutmeg")
    if "lemon peel" in b or "lemon skin" in b or "twisted lemon" in b:
        found.append("lemon peel")
    if "orange peel" in b or "orange skin" in b:
        found.append("orange peel")
    if "mint" in b and ("sprig" in b or "boquet" in b or "bouquet" in b):
        found.append("mint sprig")
    if "berries" in b or "strawberries" in b:
        found.append("berries")
    if "cherry" in b or "cherries" in b:
        found.append("cherry")
    if "pineapple" in b and ("piece" in b or "slice" in b):
        found.append("pineapple")
    if "olive" in b:
        found.append("olive")
    if "pickled onion" in b or "pickeled onion" in b or "pickled" in b:
        found.append("pickled onion")
    if not found:
        return None
    # Cap to 2 garnishes
    return ", ".join(found[:2])


def has_alcohol(body, title):
    text = (title + " " + body).lower()
    keywords = [
        "whiskey", "whisky", "rum", "gin", "brandy", "vermouth", "champagne",
        "port", "sherry", "claret", "cognac", "absinthe", "bitters",
        "liqueur", "wine", "ale", "porter", "stout", "kuemmel", "kummel",
        "chartreuse", "benedictine", "curacoa", "curacao", "maraschino",
        "anisette", "applejack", "apple jack", "calisaya", "tokay",
        "creme de menthe", "creme yvette", "abricontine", "calamus",
        "arrack", "moselle", "burgundy", "madeira", "catawba", "bevo",
        "sloe gin", "old tom", "rye", "bourbon", "scotch", "irish",
        "grenadine", "vanilla cordial", "blackberry brandy", "peach brandy",
        "apricot brandy",
    ]
    for k in keywords:
        if k in text:
            return True
    return False


def estimate_abv(body, title):
    """Rough ABV estimate based on ingredient profile."""
    t = (title + " " + body).lower()
    if not has_alcohol(body, title):
        return 0
    # Stirred spirit-forward: 25-35
    if any(k in t for k in ["old fashion", "manhattan", "stirred", "stir;"]) and \
       any(k in t for k in ["whiskey", "brandy", "gin", "rye", "bourbon"]):
        if "shaved ice" in t:
            return 28
    # Punches and bowls (heavily diluted)
    if "punch" in title.lower() and ("quart" in t or "bowl" in t or "gallon" in t):
        return 12
    # Fizz/sour
    if any(k in title.lower() for k in ["fizz", "sour", "rickey", "cooler", "lemonade"]):
        return 12
    # Highball/with seltzer/soda/ginger ale
    if any(k in t for k in ["seltzer", "carbonated water", "ginger ale", "apollinaris", "club soda", "plain soda", "fill up with"]):
        return 14
    # Champagne cocktails
    if "champagne" in t and "cocktail" in title.lower():
        return 12
    # Pousse cafe / layered cordials
    if "pousse cafe" in title.lower() or "scaffa" in title.lower():
        return 35
    # Egg drinks
    if any(k in t for k in ["egg", "eggnog", "flip"]):
        return 14
    # Hot drinks
    if "hot water" in t or "boiling water" in t:
        return 18
    # Cocktail (default)
    if "cocktail" in title.lower():
        return 24
    # Julep / smash
    if "julep" in title.lower() or "smash" in title.lower():
        return 28
    return 18


def has_raw_egg(body):
    b = body.lower()
    if "egg" not in b:
        return False
    # exclude beef/extract type with eggless ones
    if "egg" in b and not ("beat" in b and "boiling" in b):
        return True
    return False


def parse_instructions(body):
    """Split body into instruction sentences (preserving order)."""
    # Replace newlines with spaces, then split by sentences
    text = re.sub(r"\s+", " ", body).strip()
    # Sentences end with . ; or :
    parts = re.split(r"(?<=[\.;])\s+", text)
    parts = [p.strip() for p in parts if p.strip()]
    return parts


# Ingredient-line detector: lines that start with an amount or "Juice of",
# "1 Egg", "White of...", "Fill...", etc.
INGREDIENT_RE = re.compile(
    r"^("
    r"\d+[/\-\d]*\s*(jiggers?|ponys?|ponies|pints?|quarts?|"
    r"teaspoonfuls?|tablespoonfuls?|wineglass(?:es)?|wine\s*glass(?:es)?|"
    r"dashes?|drops?|lumps?|bottles?|slices?|sprigs?|pieces?|cans?|boxes?|"
    r"lbs?\.?|ounces?|oz\.?|gallons?|gills?|drachms?|cups?|quarter|halves|"
    r"glasses?|mugs?)|"
    r"juice\s+of|white\s+of|yolk\s+of|whites\s+of|yolks\s+of|"
    r"the\s+white|the\s+yolk|peel(?:ed)?|peeling|"
    r"\d+\s+\w|"
    r"one\s+|two\s+|three\s+|four\s+|five\s+|six\s+|seven\s+|eight\s+|nine\s+|ten\s+|"
    r"\d+/\d+\s+\w"
    r")",
    re.IGNORECASE,
)


def extract_ingredients(body):
    lines = [l.strip() for l in body.split("\n") if l.strip()]
    ings = []
    for ln in lines:
        # Skip pure instruction lines
        if re.match(
            r"^(stir|shake|fill|pour|serve|strain|use|drop|place|add|mix|crush|"
            r"into|in\s+a|grate|twist|ignite|set|bottle|cork|beat|cover|then|"
            r"before|when|raise|cut|dress|decorate|ornament|substitute|this\s+drink|"
            r"the\s+|it\s+|if\s+|to\s+|out\s+of|now|next|put|bruise|leave|"
            r"break|float)",
            ln, re.IGNORECASE):
            continue
        # Skip parenthetical notes
        if ln.startswith("(") and ln.endswith(")"):
            continue
        if INGREDIENT_RE.match(ln):
            # Strip trailing period
            ings.append(ln.rstrip("."))
        elif re.match(r"^[A-Z][a-z]+(\s+[A-Z]?[a-z]+)*\.?$", ln) and len(ln) < 40:
            # Single-ingredient names like "Maraschino." in a Scaffa
            ings.append(ln.rstrip("."))
    # Dedupe consecutive
    out = []
    for x in ings:
        if not out or out[-1] != x:
            out.append(x)
    return out


def make_description(title, body):
    t = title.lower()
    b = body.lower()
    if "pousse cafe" in t:
        return "Layered cordial after-dinner drink, Bullock's St. Louis style."
    if "punch" in t and ("gallon" in b or "bowl" in b or "party" in b or "quarts" in b):
        return "Large-format punch from Bullock's bar at the St. Louis Country Club."
    if "fizz" in t:
        return "Effervescent gin fizz from Bullock's 1917 bar book."
    if "sour" in t:
        return "Classic sour from Bullock's 1917 repertoire."
    if "cocktail" in t:
        return f"Pre-Prohibition cocktail from Tom Bullock's 1917 manual."
    if "julep" in t:
        return "Bullock's celebrated julep — Roosevelt's drink at the St. Louis Country Club."
    if "highball" in t or "high ball" in t:
        return "Tall, refreshing highball from Bullock's 1917 manual."
    if "cobbler" in t:
        return "Iced wine cobbler from Bullock's 1917 manual."
    if "rickey" in t:
        return "Lime rickey from Bullock's 1917 St. Louis Country Club bar."
    if "cooler" in t:
        return "Long, iced cooler from Bullock's 1917 manual."
    if "flip" in t:
        return "Egg flip — a foamy Victorian-era favorite from Bullock's bar."
    if "eggnog" in t:
        return "Bullock's eggnog — a holiday staple from St. Louis."
    if "shrub" in t:
        return "Old-fashioned vinegar/fruit shrub from Bullock's 1917 manual."
    if "scaffa" in t:
        return "Layered, unchilled cordial drink from Bullock's bar."
    if "pousse" in t:
        return "Layered after-dinner cordial from Bullock's manual."
    return "Pre-Prohibition drink from Tom Bullock's 1917 manual."


def estimate_servings(title, body):
    m = re.search(r"party of (\d+)", body + " " + title, re.IGNORECASE)
    if m:
        return int(m.group(1))
    m2 = re.search(r"for (\d+) people", body + " " + title, re.IGNORECASE)
    if m2:
        return int(m2.group(1))
    if re.search(r"gallon", body + title, re.IGNORECASE):
        return 40
    if re.search(r"\bbowl\b", title + body, re.IGNORECASE) and "punch" in title.lower():
        return 20
    return 1


def estimate_prep(title, body):
    if "pousse cafe" in title.lower() or "scaffa" in title.lower():
        return 5
    if "punch" in title.lower() and ("gallon" in body.lower() or "party" in body.lower()):
        return 30
    if "shrub" in title.lower() or "burnt brandy" in title.lower():
        return 10
    if "blue blazer" in title.lower():
        return 6
    if "hot" in title.lower() or "hot water" in body.lower():
        return 4
    return 3


def estimate_cook(title, body):
    b = (title + " " + body).lower()
    if "boil" in b or "simmer" in b or "roast" in b or "before a fire" in b or "hot oven" in b:
        return 15
    if "blue blazer" in title.lower() or "ignite" in b or "burnt brandy" in title.lower():
        return 2
    if "hot water" in b or "boiling water" in b:
        return 0
    return 0


def is_recipe_block(title, body):
    """Filter out non-recipe headers."""
    if not body or len(body) < 20:
        return False
    # Skip section headers that the index doesn't list as drinks (none in this book really)
    skip_titles = {"DEDICATED", "INTRODUCTION", "INDEX", "THE IDEAL BARTENDER"}
    if title.upper() in skip_titles:
        return False
    return True


def make_record(title, body):
    glass = detect_glass(title, body)
    method = detect_method(title, body)
    garnish = detect_garnish(body)
    is_alc = 1 if has_alcohol(body, title) else 0
    abv = estimate_abv(body, title) if is_alc else 0
    raw_egg = has_raw_egg(body)
    instructions = parse_instructions(body)
    ingredients = extract_ingredients(body)
    record = {
        "title": smart_title(title),
        "content_type": "cocktail",
        "is_alcoholic": is_alc,
        "is_historic": 1,
        "source_book": "The Ideal Bartender (Tom Bullock, 1917)",
        "source_year": 1917,
        "source_region": "St. Louis, USA",
        "contributor_name": "Tom Bullock",
        "contributor_story": CONTRIBUTOR_STORY,
        "cuisine": "cocktail",
        "description": make_description(title, body),
        "servings": estimate_servings(title, body),
        "prep_minutes": estimate_prep(title, body),
        "cook_minutes": estimate_cook(title, body),
        "original_text": f"{title}\n\n{body}",
        "modernized_text": "",
        "instructions": instructions,
        "ingredients": ingredients,
        "glass_type": glass,
        "method": method,
        "garnish": garnish,
        "abv_percent": abv,
    }
    if raw_egg:
        record["safety_notes"] = (
            "Original uses raw whole egg; use pasteurized egg for modern preparation."
        )
    else:
        record["safety_notes"] = None
    return record


def main():
    text = fetch_book()
    blocks = split_recipes(text)
    records = []
    skipped = []
    for title, body in blocks:
        if not is_recipe_block(title, body):
            skipped.append(title)
            continue
        rec = make_record(title, body)
        records.append(rec)

    with open(OUT_PATH, "w", encoding="utf-8") as f:
        for rec in records:
            f.write(json.dumps(rec, ensure_ascii=False))
            f.write("\n")

    print(f"Wrote {len(records)} recipes to {OUT_PATH}")
    if skipped:
        print(f"Skipped (non-recipe): {skipped}")
    # Sample notable ones for log
    for rec in records[:3]:
        print(f"  - {rec['title']} [{rec['glass_type']}/{rec['method']}]")


if __name__ == "__main__":
    main()

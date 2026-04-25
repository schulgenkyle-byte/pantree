#!/usr/bin/env python3
"""
Parser for George Kappeler's 1895 "Modern American Drinks"
Harvests recipes from the Internet Archive OCR text into NDJSON.
"""
import json
import re
import sys
from pathlib import Path

SRC_TXT = Path("C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/backend/ingest/cocktails/_tmp/kappeler_source.txt")
OUT_NDJSON = Path("C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/backend/ingest/cocktails/kappeler-1895.ndjson")

# --- Skip lists (non-recipe entries / overviews / syrups / page artifacts) ---
# These are overview or informational entries, not actual mixing recipes.
SKIP_TITLES = {
    "Absinthe",            # overview blurb (no recipe)
    "Arrack",              # overview blurb
    "Cherry Brandy",       # infusion/production recipe, 8-week prep — kept actually? Keep.
    "Ammonia and Seltzer",
    "Ammonia, Soda, and Seltzer",
    "Bicarbonate of Soda",
    "Barley Water",
    "Gum Syrup",
    "Lemon Syrup",
    "Raspberry Syrup",
    "Raspberry Vinegar",
    "Tom and Jerry Mixture",
}

# Titles which are explicitly NON-alcoholic
NON_ALCOHOLIC_TITLES = {
    "Barley Water",
    "Bicarbonate of Soda",
    "Hot Beef-Tea",
    "Frappe Beef-Tea",
    "Orangeade",
    "Grenadine Lemonade",
    "Plain Lemonade",
    "Phosphate Lemonade",
    "Seltzer Lemonade",
    "Soda Lemonade",
    "Tea Lemonade",
    "Limeade",
    "Hot Lemonade",
    "Lemon Squash",
    "Egg Phosphate",
    "Egg-Shake",
    "Egg Shake",
    "Raspberry Cream Frappe",
    "Clam Juice Cocktail",
    "Clam Cocktail",
    "Oyster Cocktail",
    "Cider Nectar",  # has brandy actually — keep alcoholic
    "Gum Syrup",
    "Lemon Syrup",
    "Raspberry Syrup",
    "Raspberry Vinegar",
    "Water Ice",
    "Lemon Ice",
    "Orange Ice",
    "Pineapple Water Ice",
    "Strawberry Water Ice",
    "Raspberry Water Ice",
    "Grape Sherbet",
    "Lemon Sherbet",
    "Lemon Ginger Sherbet",
    "Pomegranate Sherbet",
    "Raspberry Sherbet",
    "Shaddock, or Grape Fruit Sherbet",
    "Strawberry Sherbet",
    "Orange Sherbet No. 1",
    "Orange Sherbet No. 2",
    "Pine Apple Sherbet",
    "Blackberry Sherbet",
    "Cherry Sherbet",
    "Currant Sherbet No. 1",
    "Currant Sherbet No. 2",
    "Macedoine No. 1",
    "Macedoine No. 2",
    "Macedoine No. 3",
    "Macedoine No. 4",
    "Cafe Royal Frappe",
    "Ammonia and Seltzer",
    "Ammonia, Soda, and Seltzer",
}

# Bullet separators we strip from OCR text: stray chars, page headers, etc.
PAGE_HEADER_RE = re.compile(r"^\s*(MODERN\s+AMERICAN\s+DRINKS\.?|CONTENTS\.?|\d{1,3}|PAGE)\s*$", re.IGNORECASE)
BLANK_RE = re.compile(r"^\s*$")


def normalize(s: str) -> str:
    """Normalize OCR artifacts."""
    s = s.replace("\r", "")
    s = re.sub(r"[’‘]", "'", s)
    s = re.sub(r"\s+", " ", s).strip()
    # fix common OCR glitches
    s = s.replace("—", "-")
    return s


def clean_title(t: str) -> str:
    t = t.strip().strip(".").strip()
    t = re.sub(r"\s+", " ", t)
    # Fix OCR: curacoa/curagoa typically appears; keep as written
    return t


def is_title_line(line: str) -> bool:
    """Heuristic: Kappeler's recipe titles are short, title-cased, end with a period,
    and appear on their own line, e.g. 'Martini Cocktail.' or 'Brandy Cocktail.'.
    We detect them by: ends with period, not too long, starts with capital,
    mostly letters, not a page header, not a sentence fragment."""
    s = line.strip()
    if not s:
        return False
    if len(s) > 80:
        return False
    if not s.endswith("."):
        return False
    # Must start with uppercase letter
    if not s[0].isupper():
        return False
    # Exclude sentence-like lines (contain commas, 'the', 'a', etc. mid-sentence)
    # Titles may have parentheticals and commas, but typically no lowercase "the"/"and"
    # Heuristic: title words are mostly Capitalized or short connectors
    words = re.findall(r"[A-Za-z][A-Za-z\-']*", s)
    if not words:
        return False
    # Filter out running text: if more than half the words are lowercase function words, skip
    connectors = {"and", "of", "the", "a", "in", "on", "to", "for", "or", "de", "d'",
                  "No", "No.", "Style", "Cold", "Hot", "Fancy", "Dry", "Extra", "Plain",
                  "per", "glass", "American", "English", "French", "Italian", "Southern",
                  "Old", "Fashioned", "Fashion", "Good", "Appetizer", "Red", "White",
                  "Jersey", "Lily", "l'Anglais", "l'Anglaise", "Party", "New", "Orleans",
                  "Schiedam", "Plymouth", "Tom", "Holland", "East", "West"}
    cap_words = sum(1 for w in words if w and (w[0].isupper() or w.lower() in {"and","of","the","a","in","on","to","for","or","de","per","l'anglais","l'anglaise"}))
    if cap_words < len(words) * 0.7:
        return False
    # Block if the line ends with obvious sentence enders beyond period, or contains "serve"/"mix" etc.
    low = s.lower()
    verby = ["serve", "mix ", "shake", "strain", " add ", " pour ", "dissolve",
             "fill ", " stir ", " prepare ", "prepare same", "beat ", " put ",
             " use ", " crush ", " drink ", "is made", "is prepared", "concoct",
             " take ", " place ", " grate ", " boil ", "moisten", " cut "]
    for v in verby:
        if v in (" " + low + " "):
            return False
    return True


def parse():
    raw = SRC_TXT.read_text(encoding="utf-8", errors="replace")
    lines = raw.split("\n")

    # Find the start of the recipes proper: "MODERN AMERICAN DRINKS." appears as a
    # section header followed by "Absinthe." (overview). Skip the TOC by finding
    # the second occurrence of "MODERN AMERICAN DRINKS." at the start of a line
    # and looking for "Absinthe." shortly after.
    start_idx = None
    for i, ln in enumerate(lines):
        if "MODERN  AMERICAN  DRINKS" in ln.upper() or "MODERN AMERICAN DRINKS" in ln.upper():
            # check next non-blank lines for "Absinthe."
            for j in range(i + 1, min(i + 15, len(lines))):
                if lines[j].strip().rstrip(".").strip().lower() == "absinthe":
                    start_idx = i
                    break
            if start_idx:
                break
    if start_idx is None:
        print("Could not find start of recipes; using line 0", file=sys.stderr)
        start_idx = 0

    # End just before the advertising pages. Kappeler ends with "Widow's Kiss."
    # and then goes into "FROZEN BEVERAGES" (ices/sherbets) which we include as
    # frozen-punch alcoholic recipes. Publisher ads appear after "Cafe Royal Frappe."
    # followed by ad copy starting with "Anheuser=Busch" / "Brewing Association".
    end_idx = len(lines)
    for i in range(start_idx, len(lines)):
        u = lines[i].upper()
        if "ANHEUSER" in u or "BREWING ASSOCIATION" in u:
            end_idx = i
            break

    # Walk forward, grouping into (title, body_lines)
    body = lines[start_idx:end_idx]

    # Remove page-header/page-number only lines
    cleaned = []
    for ln in body:
        s = ln.strip()
        if PAGE_HEADER_RE.match(s):
            continue
        cleaned.append(ln.rstrip())

    # Build recipes by scanning for title lines
    recipes = []
    i = 0
    current_title = None
    current_body = []
    while i < len(cleaned):
        ln = cleaned[i]
        s = ln.strip()
        if is_title_line(s):
            # commit previous
            if current_title is not None:
                recipes.append((current_title, current_body))
            current_title = clean_title(s)
            current_body = []
        else:
            if current_title is not None:
                current_body.append(ln)
        i += 1
    if current_title is not None:
        recipes.append((current_title, current_body))

    return recipes


# --- Recipe classification & structured field extraction ---

def classify(title: str, text: str):
    """Return dict with content_type, cuisine, glass_type, method, category-ish flags."""
    t_low = title.lower()
    tx_low = text.lower()

    # Glass detection
    glass = "coupe"  # default for cocktails
    if "cocktail-glass" in tx_low or "cocktail glass" in tx_low:
        glass = "coupe"
    if "long thin glass" in tx_low or "long  thin  glass" in tx_low or "long thin punch-glass" in tx_low or "long thin punch glass" in tx_low:
        glass = "collins"
    if "collins-glass" in tx_low or "collins glass" in tx_low:
        glass = "collins"
    if "fizz-glass" in tx_low or "fizz glass" in tx_low:
        glass = "fizz"
    if "high-ball" in tx_low or "highball" in tx_low:
        glass = "highball"
    if "whiskey-glass" in tx_low and glass == "coupe":
        glass = "rocks"
    if "delmonico" in tx_low:
        glass = "rocks"
    if "hot-drink" in tx_low or "hot drink glass" in tx_low or "mug" in tx_low:
        glass = "mug"
    if "sherry-glass" in tx_low or "sherry glass" in tx_low or "wine-glass" in tx_low or "wineglass" in tx_low or "wine glass" in tx_low:
        if "pousse" in t_low or "float" in t_low or "scaffa" in t_low or "reviver" in t_low or "knickerbein" in t_low or "sam ward" in t_low or "pousse" in tx_low:
            glass = "cordial"
        else:
            glass = "coupe"
    if "cordial-glass" in tx_low or "cordial glass" in tx_low or "pony-glass" in tx_low:
        glass = "cordial"
    if "fancy bar-glass" in tx_low or "fancy glass" in tx_low:
        pass  # stays
    if "punch-bowl" in tx_low or "pitcher" in tx_low:
        glass = "punch-bowl"
    if "champagne" in t_low and ("cup" in t_low or "cocktail" in t_low):
        glass = "flute" if "cocktail" in t_low else "punch-bowl"
    if "julep" in t_low:
        glass = "julep"
    if "fix" in t_low or "fizz" in t_low:
        if "fizz" in t_low:
            glass = "fizz"
        else:
            glass = "highball"
    if "rickey" in t_low:
        glass = "highball"
    if "toddy" in t_low and "hot" in t_low:
        glass = "mug"
    if "cobbler" in t_low:
        glass = "goblet"
    if "cooler" in t_low:
        glass = "collins"
    if "sour" in t_low:
        glass = "sour"
    if "flip" in t_low and "hot" not in t_low:
        glass = "coupe"
    if "egg-nogg" in t_low or "eggnogg" in t_low or "egg nogg" in t_low:
        glass = "collins"
    if "punch" in t_low and "hot" in t_low:
        glass = "mug"
    if "blue blazer" in t_low:
        glass = "mug"
    if "pousse" in t_low or "scaffa" in t_low or "reviver" in t_low or "knickerbein" in t_low or "widow" in t_low or "brandy champerelle" in t_low or "brandy champarelle" in t_low:
        glass = "cordial"
    if "cup" in t_low and "cocktail" not in t_low:
        glass = "punch-bowl"

    # Method detection
    method = "stirred"
    if "shake well" in tx_low or "shake until" in tx_low or "shake thoroughly" in tx_low or "shake " in tx_low or "shaker" in tx_low:
        method = "shaken"
    if "mix well" in tx_low or "mix and strain" in tx_low or "mix," in tx_low or "mix." in tx_low:
        # mix implies stirred; but shaken overrides
        if method != "shaken":
            method = "stirred"
    if "boil" in tx_low or "boiling water" in tx_low or "boiling point" in tx_low or "fire" in tx_low:
        method = "hot-built"
    if "ignite" in tx_low or "burn" in tx_low:
        method = "flamed"
    if "frappe" in t_low or "frappe" in tx_low:
        method = "frappe"
    if "float" in t_low or "scaffa" in t_low or "pousse" in t_low or "reviver" in t_low or "knickerbein" in t_low:
        method = "layered"
    if "julep" in t_low or "smash" in t_low or "muddle" in tx_low or "muddler" in tx_low or "crush" in tx_low and "ice" not in tx_low[:tx_low.find("crush")+40]:
        if "smash" in t_low or "julep" in t_low:
            method = "muddled"
    if "dripped absinthe" in t_low or "dripped" in t_low:
        method = "dripped"
    if "rickey" in t_low or "fizz" in t_low or "collins" in t_low or "cooler" in t_low or "highball" in t_low or "ginger ale" in tx_low[:200] and "fizz" not in tx_low:
        if "fizz" in t_low:
            method = "shaken-built"
        if "collins" in t_low:
            method = "built"
        if "rickey" in t_low:
            method = "built"
        if "cooler" in t_low:
            method = "built"
    if "built in glass" in tx_low:
        method = "built"
    return glass, method


# ABV heuristic buckets
def guess_abv(title: str, text: str, method: str) -> int:
    t = title.lower()
    x = text.lower()
    if any(k in t for k in ["lemonade", "orangeade", "limeade", "ice", "sherbet", "water ice", "soda cocktail"]):
        return 0
    if "beef-tea" in t or "barley" in t:
        return 0
    if "cobbler" in t or "cup" in t or "punch" in t:
        return 12
    if "fizz" in t or "rickey" in t or "cooler" in t or "collins" in t:
        return 12
    if "flip" in t or "egg-nogg" in t or "eggnogg" in t:
        return 14
    if "sour" in t or "daisy" in t or "fix" in t or "smash" in t:
        return 18
    if "sling" in t and "cold" in t:
        return 24
    if "sling" in t and "hot" in t:
        return 12
    if "toddy" in t:
        return 20 if "cold" in t else 14
    if "julep" in t:
        return 20
    if "cocktail" in t:
        return 32
    if "pousse" in t or "scaffa" in t or "reviver" in t or "float" in t or "knickerbein" in t or "champerelle" in t or "champarelle" in t:
        return 40
    if "frappe" in t:
        return 25
    return 20


# ingredient extraction
JIGGER_RE = re.compile(r"(one|two|three|four|half a|one-half|half|one and a half|two-thirds|one-third|three-fourths|one-fourth|a|the|several|few)?\s*(jigger|pony|jig|tablespoonful|teaspoonful|bar-spoonful|spoonful|dash|dashes|slice|slices|lump|lumps|bottle|bottles|quart|quarts|pint|pints|gallon|ounce|ounces|pound|pounds|glass|squirt|ponies|jiggers)s?\s+(?:of\s+)?([A-Za-z][A-Za-z\-'\s]{2,40}?)(?=[,.;]|\s+(?:and|or|in|with|into|to|on|from|shake|mix|strain|fill|add|stir|serve|pour|dissolve|put|beat|take)\b)",
                       re.IGNORECASE)

AMOUNT_WORDS = {
    "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6, "eight": 8, "ten": 10,
    "a": 1, "the": 1, "half": 0.5, "half a": 0.5, "one-half": 0.5, "one and a half": 1.5,
    "two-thirds": 0.67, "one-third": 0.33, "three-fourths": 0.75, "one-fourth": 0.25,
    "several": 2, "few": 2,
}

MEASURE_OZ = {
    "jigger": 2.0, "jiggers": 2.0, "jig": 2.0,
    "pony": 1.0, "ponies": 1.0,
    "tablespoonful": 0.5, "tablespoonfuls": 0.5,
    "teaspoonful": 0.17, "teaspoonfuls": 0.17,
    "bar-spoonful": 0.17, "spoonful": 0.17, "spoonfuls": 0.17,
    "dash": 0.03, "dashes": 0.03,
    "lump": 0.0, "lumps": 0.0,
    "slice": 0.0, "slices": 0.0,
    "bottle": 12.0, "bottles": 12.0,
    "glass": 8.0,
    "squirt": 0.25,
    "ounce": 1.0, "ounces": 1.0,
    "pound": 0.0, "pounds": 0.0,
    "quart": 32.0, "quarts": 32.0,
    "pint": 16.0, "pints": 16.0,
    "gallon": 128.0,
}


def extract_ingredients(text: str):
    """Extract {name, amount, unit, oz} from recipe text. Best-effort for a
    pan-tree ingredient list."""
    ings = []
    seen_names = set()
    for m in JIGGER_RE.finditer(text):
        qty_word = (m.group(1) or "one").lower().strip()
        unit = m.group(2).lower()
        name = m.group(3).strip().strip(",.").strip()
        # Normalize name: drop leading articles and trailing noise
        name = re.sub(r"^(of\s+)", "", name, flags=re.IGNORECASE).strip()
        name = re.sub(r"\s+", " ", name)
        # stop at prepositions that slipped in
        name = re.split(r"\b(and|or|in|with|into|to|on|from)\b", name, maxsplit=1)[0].strip()
        name = name.strip(",.").strip()
        if not name or len(name) < 2:
            continue
        nl = name.lower()
        # Drop equipment / ice / mixing-glass artifacts
        equipment_noise = (
            nl in {"ice", "fine ice", "cracked ice", "shaved ice", "clear ice",
                   "fine shaven ice", "cut-loaf ice"}
            or "mixing-glass" in nl
            or "fine ice" in nl
            or "clear ice" in nl
            or "full fine ice" in nl
            or "half-full" in nl
            or "one-third full" in nl
            or "two-thirds" in nl
            or "bottom glass" in nl
            or nl.startswith("of ")
            or nl.endswith(" glass")
            or nl in {"glass", "shaker", "pitcher"}
        )
        if equipment_noise:
            continue
        qty = AMOUNT_WORDS.get(qty_word, 1)
        oz = round(qty * MEASURE_OZ.get(unit, 0.0), 2)
        key = name.lower()
        if key in seen_names:
            continue
        seen_names.add(key)
        ings.append({
            "name": name,
            "amount": qty,
            "unit": unit,
            "oz": oz,
        })
    return ings


GARNISH_RE = re.compile(
    r"(?:add|with|top(?:ped)?(?:\s+with)?|trim(?:med)?\s+with|ornament(?:ed)?\s+with|decorate(?:d)?\s+with|grate(?:d)?\s+|garnish(?:ed)?\s+with|dress(?:ed)?\s+with|(?:a\s+)?(?:small\s+)?piece\s+(?:of\s+)?(?:twisted\s+)?|slice\s+of)\s+([A-Za-z\s\-]+?)(?=[,.;]|$)",
    re.IGNORECASE)


def extract_garnish(text: str):
    tl = text.lower()
    candidates = []
    # lemon peel / orange peel / cherry / nutmeg / fruit / mint
    for kw, canon in [
        ("twisted lemon-peel", "twisted lemon peel"),
        ("twisted lemon peel", "twisted lemon peel"),
        ("lemon-peel", "lemon peel"),
        ("lemon peel", "lemon peel"),
        ("orange-peel", "orange peel"),
        ("orange peel", "orange peel"),
        ("maraschino cherry", "maraschino cherry"),
        (" cherry", "cherry"),
        ("grate nutmeg", "grated nutmeg"),
        ("grated nutmeg", "grated nutmeg"),
        ("little nutmeg", "grated nutmeg"),
        ("nutmeg on top", "grated nutmeg"),
        ("sprigs of mint", "mint sprigs"),
        ("sprigs fresh mint", "fresh mint sprigs"),
        ("sprig of mint", "mint sprig"),
        ("fruit in season", "seasonal fruit"),
        ("trim with fruit", "seasonal fruit"),
        ("ornament with fruit", "seasonal fruit"),
        ("slice of lemon", "lemon slice"),
        ("slice of orange", "orange slice"),
    ]:
        if kw in tl:
            candidates.append(canon)
            break
    if candidates:
        return candidates[0]
    return None


def make_description(title: str, text: str) -> str:
    t_low = title.lower()
    if "martini" in t_low and "bottled" not in t_low:
        return "Direct ancestor of the modern Martini: Old Tom gin, Italian vermouth, and orange bitters."
    if "manhattan" in t_low and "bottled" not in t_low and "punch" not in t_low:
        return "A pre-Prohibition Manhattan, built with whiskey, Italian vermouth, and bitters."
    if "old-fashioned" in t_low or "old fashioned" in t_low or "old-fashion" in t_low:
        return "An early, in-the-glass cocktail built on sugar, bitters, ice, and the chosen spirit."
    if "blue blazer" in t_low:
        return "Jerry Thomas-era flaming Scotch drink, theatrically poured between mugs."
    if "pousse cafe" in t_low or "pousse-cafe" in t_low:
        return "Layered cordial drink served after dinner."
    if "julep" in t_low:
        return "Classic mint julep-family drink served over crushed ice."
    if "cobbler" in t_low:
        return "Sugared, iced wine or spirit drink served with fruit and a straw."
    if "fizz" in t_low:
        return "Pre-Prohibition fizz: shaken with citrus and finished with seltzer."
    if "collins" in t_low:
        return "Tall, sparkling, sour-and-seltzer cooler."
    if "rickey" in t_low:
        return "Dry, lime-accented highball — 1890s Washington-born classic."
    if "flip" in t_low:
        return "Egg-rich, nutmeg-dusted flip in the 19th-century style."
    if "egg-nogg" in t_low or "egg nogg" in t_low:
        return "Period egg-nogg, shaken with spirit, egg, sugar, and milk."
    if "cocktail" in t_low:
        return "1895 Kappeler cocktail, preserved verbatim from Modern American Drinks."
    if "cup" in t_low:
        return "Pitcher/punch-bowl wine cup, ornamented with fruit and mint."
    if "punch" in t_low:
        return "1895-era punch, served in ice with trim of fruit."
    if "smash" in t_low:
        return "Muddled-mint smash, served strained over fruit."
    if "sour" in t_low:
        return "Classic sour: spirit, lemon, and sugar — period build."
    if "fix" in t_low:
        return "A fix: sweetened, iced, and spirit-forward — pineapple-scented."
    if "cooler" in t_low:
        return "Ginger-ale-and-spirit cooler over lemon peel."
    if "toddy" in t_low:
        return "Sugar, spirit, and water — cold or hot toddy in period style."
    if "sling" in t_low:
        return "A sling: sugar, spirit, water, and nutmeg."
    if "crusta" in t_low:
        return "A crusta — sugared-rim fancy drink, predecessor to the sidecar."
    if "daisy" in t_low:
        return "A daisy: spirit, lemon, orange cordial, topped with seltzer."
    return "Historic 1895 drink from Kappeler's Modern American Drinks."


def is_alcoholic(title: str, text: str) -> int:
    if title in NON_ALCOHOLIC_TITLES:
        return 0
    tl = title.lower()
    if "lemonade" in tl and not any(k in tl for k in ["italian wine", "rhine", "claret", "egg", "angostura"]):
        # most lemonades are non-alcoholic; Rhine-Wine/Claret/Italian Wine are alcoholic
        return 0
    return 1


def has_raw_egg(text: str) -> bool:
    tl = text.lower()
    if "fresh egg" in tl or "whites of" in tl or "yolk of" in tl or "one egg" in tl or "yolks of" in tl or "white of an egg" in tl or "white of one egg" in tl:
        if "boil" in tl or "fire" in tl or "boiling" in tl:
            return False
        return True
    return False


def build_instructions(text: str):
    """Split verbatim text into 1-5 instruction-ish sentences (without altering wording)."""
    t = re.sub(r"\s+", " ", text).strip()
    parts = re.split(r"(?<=[.;])\s+(?=[A-Z])", t)
    steps = [p.strip() for p in parts if p.strip()]
    # Keep original wording; limit length
    return steps[:6] if steps else [t]


def main():
    recipes = parse()

    # Map to NDJSON records
    out_records = []
    seen = set()
    for title, body_lines in recipes:
        # Stitch body lines
        text = " ".join(l.strip() for l in body_lines if l.strip())
        text = normalize(text)
        if not text:
            continue
        # Filter out glossary-style / overview entries (too short, or no imperative verbs)
        if title in SKIP_TITLES:
            continue
        # Skip duplicate cross-references that have near-empty text
        if len(text) < 25:
            continue
        # Skip obvious non-drinks (preface, contents artifact)
        low = title.lower()
        if any(k in low for k in ["contents", "preface", "footnote"]):
            continue
        # Skip purely production/infusion descriptions for syrups
        if title.lower() in {"gum syrup", "lemon syrup", "raspberry syrup", "raspberry vinegar"}:
            continue

        alcoholic = is_alcoholic(title, text)
        glass, method = classify(title, text)
        abv = guess_abv(title, text, method)
        if not alcoholic:
            abv = 0

        # Content type
        if title in NON_ALCOHOLIC_TITLES and "sherbet" not in title.lower() and "ice" not in title.lower():
            content_type = "beverage"
        else:
            content_type = "cocktail"

        # Prep time heuristic
        if "hot" in low or "boil" in text.lower() or method == "hot-built":
            prep = 5
            cook = 2
        elif method == "frappe":
            prep = 5
            cook = 0
        elif method == "layered":
            prep = 4
            cook = 0
        elif method == "shaken":
            prep = 3
            cook = 0
        else:
            prep = 3
            cook = 0

        ingredients = extract_ingredients(text)
        garnish = extract_garnish(text)

        desc = make_description(title, text)

        rec = {
            "title": title,
            "content_type": content_type,
            "is_alcoholic": alcoholic,
            "is_historic": 1,
            "source_book": "Modern American Drinks (George Kappeler, 1895)",
            "source_year": 1895,
            "source_region": "New York, USA",
            "contributor_name": "George J. Kappeler",
            "cuisine": "cocktail",
            "description": desc,
            "servings": 1,
            "prep_minutes": prep,
            "cook_minutes": cook,
            "original_text": text,
            "modernized_text": "",
            "instructions": build_instructions(text),
            "ingredients": ingredients,
            "glass_type": glass,
            "method": method,
            "garnish": garnish,
            "abv_percent": abv,
        }

        if has_raw_egg(text):
            rec["safety_notes"] = "Contains raw egg. Use pasteurized eggs or omit for food-safety."

        key = title.lower()
        if key in seen:
            continue
        seen.add(key)
        out_records.append(rec)

    OUT_NDJSON.parent.mkdir(parents=True, exist_ok=True)
    with OUT_NDJSON.open("w", encoding="utf-8") as f:
        for r in out_records:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    # Report
    print(f"Wrote {len(out_records)} recipes to {OUT_NDJSON}")
    notable = [r["title"] for r in out_records if r["title"] in (
        "Martini Cocktail", "Manhattan Cocktail", "Manhattan Cocktail, Dry",
        "Manhattan Cocktail, Extra Dry", "Blue Blazer", "Fish House Punch",
        "Old-Fashioned Whiskey Cocktail", "Old-Fashioned Brandy Cocktail",
        "Sherry Cobbler No. 1", "Tom Collins", "John Collins",
        "Champagne Cocktail", "Widow's Kiss", "Mint Julep, Southern Style")]
    print("Notable captures:", notable)


if __name__ == "__main__":
    main()

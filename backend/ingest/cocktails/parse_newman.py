#!/usr/bin/env python3
# Parse Newman 1904 into NDJSON.
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).parent
RAW = ROOT / "raw" / "newman_1904.txt"
OUT = ROOT / "newman-1904.ndjson"

CONTRIBUTOR_STORY = (
    "Frank P. Newman ran an American-style bar in Paris at the turn of the "
    "20th century. American-Bar (1904) was an early French-language guide "
    "to American mixed drinks."
)

PAGE_RE = re.compile(r"^===PAGE\s+\d+===\s*$")
PAGE_NUM_RE = re.compile(r"^\s*\d{1,3}\s*$")
NUM_RE = re.compile(r'^\s*N[°«»oO"][.\s]*(\d+(?:bis)?)\s*$', re.IGNORECASE)
VERRE_RE = re.compile(r"^\s*[VYrV]erre", re.IGNORECASE)

def normalize(s):
    s = s.replace(chr(13), "")
    s = s.replace(chr(0x2018), "'").replace(chr(0x2019), "'")
    s = s.replace(chr(0x201c), chr(34)).replace(chr(0x201d), chr(34))
    s = s.replace(chr(0x2014), "-").replace(chr(0x2013), "-")
    return s


def find_recipe_start(lines):
    # Find first line matching NUM_RE = 1
    for i, ln in enumerate(lines):
        m = NUM_RE.match(ln.strip())
        if m and m.group(1) == "1":
            return i
    return 0


def find_recipes(lines):
    """Find recipe blocks in newman lines."""
    start = find_recipe_start(lines)
    recipes = []
    cur_num = None
    cur_title = None
    cur_body = []
    expecting_title = False
    i = start
    n = len(lines)
    while i < n:
        raw = lines[i]
        s = raw.strip()
        if PAGE_RE.match(s):
            i += 1
            continue
        m = NUM_RE.match(s)
        if m:
            if cur_title is not None:
                recipes.append((cur_num, cur_title, cur_body))
            cur_num = m.group(1)
            cur_title = None
            cur_body = []
            expecting_title = True
            i += 1
            continue
        if expecting_title:
            if not s:
                i += 1
                continue
            cur_title = s
            expecting_title = False
            i += 1
            continue
        if cur_title is not None and s:
            if PAGE_NUM_RE.match(s):
                i += 1
                continue
            cur_body.append(raw.rstrip())
        i += 1
    if cur_title is not None:
        recipes.append((cur_num, cur_title, cur_body))
    return recipes


# Newman uses French glass terms; map to pan-tree glass types
GLASS_HINTS = [
    ("verre a bordeaux", "wine"),
    ("verre a champagne", "flute"),
    ("verre a porto", "sherry"),
    ("verre a sherry", "sherry"),
    ("verre a madere", "sherry"),
    ("verre a liqueur", "cordial"),
    ("verre a punch", "punch"),
    ("gobelet en argent", "mug"),
    ("verre a cognac", "cordial"),
    ("bol", "punch_bowl"),
    ("tasse", "mug"),
]


def detect_glass(title, body):
    t = (title + " " + body).lower()
    for kw, g in GLASS_HINTS:
        if kw in t:
            return g
    tl = title.lower()
    if "cocktail" in tl: return "cocktail"
    if "fizz" in tl: return "fizz"
    if "collins" in tl: return "collins"
    if "cobbler" in tl: return "goblet"
    if "julep" in tl: return "julep"
    if "punch" in tl and "bol" in body.lower(): return "punch_bowl"
    if "punch" in tl: return "punch"
    if "sour" in tl: return "sour"
    if "highball" in tl or "high ball" in tl: return "highball"
    if "rickey" in tl: return "highball"
    if "cooler" in tl: return "highball"
    if "toddy" in tl: return "rocks"
    if "flip" in tl or "egg nog" in tl or "eggnog" in tl: return "coupe"
    if "pousse" in tl or "scaffa" in tl: return "pousse_cafe"
    if "crusta" in tl: return "coupe"
    return ""


def detect_method(title, body):
    b = body.lower()
    tl = title.lower()
    if "flamber" in b or "enflammer" in b:
        return "flamed"
    if "pousse" in tl or "scaffa" in tl or "sans melanger" in b:
        return "layered"
    if "piler" in b:
        return "muddled"
    if "frapper" in b or "shaker" in b or "battre" in b:
        return "shaken"
    if "remuer" in b and ("passer" in b or "melanger" in b):
        return "stirred"
    if "chaud" in b or "bouillant" in b:
        return "built_hot"
    if "remplir" in b or "verser" in b:
        return "built"
    return "stirred"


def detect_garnish(body):
    b = body.lower()
    found = []
    if "zeste de citron" in b: found.append("lemon peel")
    if "zeste d" in b and "orange" in b: found.append("orange peel")
    if "cerise" in b: found.append("cherry")
    if "olive" in b: found.append("olive")
    if "muscade" in b: found.append("grated nutmeg")
    if "menthe" in b: found.append("mint sprig")
    if "fruits" in b and "saison" in b: found.append("seasonal fruit")
    if "ananas" in b: found.append("pineapple")
    if not found:
        return None
    return ", ".join(found[:2])


ALC_KW = (
    "whisky", "whiskey", "rum", "gin", "brandy", "vermouth", "absinthe",
    "angostura", "orange bitter", "vin", "champagne", "rye", "scotch",
    "bourbon", "cognac", "calvados", "porto", "sherry", "madere",
    "curacao", "chartreuse", "benedictine", "marasquin", "anisette",
    "dubonnet", "kirsch", "cointreau", "kummel", "creme", "creme de",
    "ale", "porter", "claret", "bordeaux", "bourgogne", "sloe gin",
    "old tom", "arrack", "liqueur", "cidre", "cherry brandy",
    "orgeat", "genievre", "genever", "applejack", "apple jack",
    "grenadine", "yvette", "sirop de cognac",
)


def is_alcoholic(title, body):
    t = (title + " " + body).lower()
    return 1 if any(k in t for k in ALC_KW) else 0


def has_raw_egg(body):
    b = body.lower()
    if "oeuf" not in b and "jaune" not in b and "blanc d" not in b:
        return False
    if "chaud" in b or "bouillant" in b: return False
    return True


AISLE_FOR = [
    ("absinthe", "bar"), ("anisette", "bar"), ("angostura", "bar"),
    ("bitter", "bar"), ("vermouth", "bar"),
    ("whisky", "bar"), ("whiskey", "bar"), ("rye", "bar"),
    ("scotch", "bar"), ("bourbon", "bar"), ("rum", "bar"),
    ("gin", "bar"), ("genever", "bar"), ("genievre", "bar"),
    ("brandy", "bar"), ("cognac", "bar"), ("calvados", "bar"),
    ("applejack", "bar"), ("apple jack", "bar"),
    ("champagne", "bar"), ("vin", "bar"), ("porto", "bar"),
    ("sherry", "bar"), ("madere", "bar"), ("claret", "bar"),
    ("bordeaux", "bar"), ("bourgogne", "bar"),
    ("benedictine", "bar"), ("chartreuse", "bar"), ("curacao", "bar"),
    ("marasquin", "bar"), ("kirsch", "bar"), ("kummel", "bar"),
    ("cointreau", "bar"), ("creme", "bar"), ("dubonnet", "bar"),
    ("orgeat", "bar"), ("yvette", "bar"), ("grenadine", "bar"),
    ("liqueur", "bar"), ("cidre", "bar"), ("ale", "bar"),
    ("porter", "bar"), ("sirop", "pantry"), ("sucre", "pantry"),
    ("miel", "pantry"), ("sel", "pantry"), ("muscade", "pantry"),
    ("cannelle", "pantry"), ("poivre", "pantry"),
    ("oeuf", "produce"), ("jaune", "produce"), ("blanc d", "produce"),
    ("lait", "dairy"), ("creme fraiche", "dairy"),
    ("citron", "produce"), ("limon", "produce"), ("orange", "produce"),
    ("ananas", "produce"), ("menthe", "produce"), ("cerise", "produce"),
    ("fruits", "produce"), ("fraise", "produce"), ("framboise", "produce"),
    ("pomme", "produce"), ("peche", "produce"), ("concombre", "produce"),
    ("seltz", "other"), ("soda", "other"), ("eau", "other"),
    ("glace", "other"),
]


def guess_aisle(name):
    n = strip_accents(name.lower())
    for kw, ai in AISLE_FOR:
        if kw in n:
            return ai
    return "other"


UNIT_NORM_FR = {
    "verre a liqueur": "liqueur-glass",
    "verre a porto": "sherry-glass",
    "verre a madere": "sherry-glass",
    "cuilleree a cafe": "tsp",
    "cuilleree a soupe": "tbsp",
    "cuilleree": "tsp",
    "trait": "dash",
    "traits": "dash",
    "goutte": "drop",
    "gouttes": "drop",
    "morceau": "lump",
    "morceaux": "lump",
    "tranche": "slice",
    "tranches": "slice",
    "oeuf": "piece",
    "jaune": "piece",
    "jaunes": "piece",
    "blanc": "piece",
    "blancs": "piece",
    "verre": "glass",
    "verres": "glass",
    "bouteille": "bottle",
    "bouteilles": "bottle",
    "litre": "liter",
    "litres": "liter",
    "pinte": "pint",
    "chopine": "pint",
    "once": "oz",
    "onces": "oz",
    "sprig": "sprig",
    "branche": "sprig",
    "branches": "sprig",
}


def strip_accents(s):
    import unicodedata
    return "".join(c for c in unicodedata.normalize("NFKD", s) if not unicodedata.combining(c))


FRAC_RE = re.compile(r"^(\d+)\s*/\s*(\d+)$")


def parse_qty_fr(q):
    q = q.strip().lower()
    if not q:
        return 1.0
    if q in ("un", "une", "a", "the"): return 1.0
    if q in ("deux",): return 2.0
    if q in ("trois",): return 3.0
    if q in ("quatre",): return 4.0
    if q in ("cinq",): return 5.0
    if q in ("six",): return 6.0
    if q in ("sept",): return 7.0
    if q in ("huit",): return 8.0
    if q in ("dix",): return 10.0
    if q in ("douze",): return 12.0
    if q in ("demi", "demie", "un demi", "une demie"): return 0.5
    m = FRAC_RE.match(q)
    if m:
        return int(m.group(1)) / int(m.group(2)) if int(m.group(2)) else 0.0
    try:
        return float(q)
    except ValueError:
        return 1.0


ING_LINE_RE = re.compile(
    r"""^\s*
    (?P<qty>\d+(?:\s*/\s*\d+)?|un|une|deux|trois|quatre|cinq|six|sept|huit|dix|douze|demi|demie)
    \s+
    (?P<unit>cuilleree(?:\s+a\s+(?:cafe|café|soupe))?|verre(?:\s+a\s+(?:liqueur|cognac|porto|madere|madère|sherry|champagne|bordeaux))?|verres|trait|traits|goutte|gouttes|morceau|morceaux|tranche|tranches|oeuf|jaune[s]?|blanc[s]?|bouteille|litre|pinte|chopine|once[s]?|branche[s]?)
    \s+(?:de|d['’])\s*
    (?P<thing>[A-Za-zÀ-ÿ][A-Za-z0-9À-ÿ'’\- ]+?)
    \s*[,.;]?\s*$""",
    re.IGNORECASE | re.VERBOSE,
)

BARE_LINE_RE = re.compile(
    r"^\s*(?P<qty>\d+(?:\s*/\s*\d+)?|un|une|deux|trois)\s+(?P<thing>(?:oeuf|jaune|blanc|tranche|morceau)[A-Za-z0-9À-ÿ'’\- ]*?)\s*[,.;]?\s*$",
    re.IGNORECASE,
)

JUS_LINE_RE = re.compile(
    r"^\s*(?:le\s+|la\s+)?jus\s+d['’]?\s*un\s+(?:demi[\-\s]*)?(?P<thing>[a-zÀ-ÿ'’\- ]+?)\s*[,.;]?\s*$",
    re.IGNORECASE,
)


def normalize_unit_fr(u):
    u = strip_accents(u.lower()).strip()
    u = u.replace("  ", " ")
    u = u.replace("a ", "a ")
    if u in UNIT_NORM_FR:
        return UNIT_NORM_FR[u]
    if u.startswith("verre a liqueur"): return "liqueur-glass"
    if u.startswith("verre a porto"): return "sherry-glass"
    if u.startswith("verre a madere"): return "sherry-glass"
    if u.startswith("verre a champagne"): return "flute-glass"
    if u.startswith("verre a cognac"): return "liqueur-glass"
    if u.startswith("verre"): return "glass"
    if u.startswith("cuilleree"): return "tsp"
    if u.startswith("trait"): return "dash"
    if u.startswith("goutte"): return "drop"
    if u.startswith("morceau"): return "lump"
    if u.startswith("tranche"): return "slice"
    if u.startswith("branche"): return "sprig"
    if u in ("oeuf", "jaune", "jaunes", "blanc", "blancs"): return "piece"
    return u


def extract_ingredients(body):
    out = []
    seen = set()
    for line in body.split(chr(10)):
        s = line.strip()
        if not s:
            continue
        # ING_LINE_RE
        m = ING_LINE_RE.match(s)
        if m:
            qty = parse_qty_fr(m.group("qty"))
            unit = normalize_unit_fr(m.group("unit"))
            thing = m.group("thing").strip().rstrip(",.").strip()
            if not thing or len(thing) > 80:
                continue
            key = (thing.lower(), unit)
            if key in seen: continue
            seen.add(key)
            out.append({"name": thing.lower(), "quantity": qty, "unit": unit, "aisle": guess_aisle(thing)})
            continue
        # JUS_LINE_RE
        m = JUS_LINE_RE.match(s)
        if m:
            thing = m.group("thing").strip().rstrip(",.").strip()
            if not thing: continue
            name = "juice of " + thing.lower()
            key = (name, "piece")
            if key in seen: continue
            seen.add(key)
            out.append({"name": name, "quantity": 1, "unit": "piece", "aisle": guess_aisle(thing)})
            continue
        # BARE_LINE_RE
        m = BARE_LINE_RE.match(s)
        if m:
            qty = parse_qty_fr(m.group("qty"))
            thing = m.group("thing").strip().rstrip(",.").strip()
            if not thing: continue
            unit = "piece"
            key = (thing.lower(), unit)
            if key in seen: continue
            seen.add(key)
            out.append({"name": thing.lower(), "quantity": qty, "unit": unit, "aisle": guess_aisle(thing)})
            continue
    return out


def split_instructions(body):
    text = re.sub(r"\s+", " ", body).strip()
    if not text: return []
    parts = re.split(r"(?<=[.;])\s+(?=[A-ZÀ-Þ])", text)
    parts = [p.strip() for p in parts if p.strip() and len(p) > 3]
    return parts[:8]


def make_description(title):
    t = title.lower()
    if "manhattan" in t: return "Newman 1904 Manhattan -- a Paris-bar take on the New York classic."
    if "martini" in t or "martini" in t: return "A pre-Prohibition Martini cocktail from Newman 1904 (Paris)."
    if "old-fashioned" in t or "old fashioned" in t: return "In-the-glass Old-Fashioned, Newman 1904 Paris bar style."
    if "fizz" in t: return "A shaken citrus fizz from Newman 1904 (Paris)."
    if "collins" in t: return "Tall sparkling collins from Newman 1904 American-Bar."
    if "rickey" in t: return "Lime-and-spirit rickey from Newman 1904 (Paris)."
    if "cooler" in t: return "Long iced cooler from Newman 1904 American-Bar."
    if "julep" in t: return "Mint julep from Newman 1904 (Paris)."
    if "cobbler" in t: return "Iced wine cobbler from Newman 1904 American-Bar."
    if "punch" in t: return "Pitcher- or bowl-style punch from Newman 1904 (Paris)."
    if "pousse" in t or "scaffa" in t: return "Layered cordial after-dinner drink from Newman 1904."
    if "flip" in t or "egg nog" in t or "eggnog" in t: return "Egg-rich flip from Newman 1904 American-Bar."
    if "sour" in t: return "Classic sour: spirit, lemon, sugar -- 1904 Paris build."
    if "frappe" in t: return "Frappe over crushed ice, Newman 1904."
    if "smash" in t: return "Muddled smash from Newman 1904 (Paris)."
    if "sangaree" in t or "sangar" in t: return "A sangaree -- wine or spirit dusted with nutmeg."
    if "toddy" in t: return "Hot or cold toddy from Newman 1904."
    if "sling" in t: return "A sling: sugar, water, and spirit."
    if "daisy" in t: return "A daisy: spirit, lemon, orange cordial, topped with seltzer."
    if "crusta" in t: return "Sugar-rim crusta from Newman 1904 American-Bar."
    if "highball" in t or "high ball" in t: return "A spirit-and-soda highball from Newman 1904 (Paris)."
    if "cocktail" in t: return "1904 cocktail from Newman American-Bar (Paris)."
    return "Pre-Prohibition drink from Newman 1904 American-Bar (Paris)."


def estimate_abv(title, body):
    t = title.lower()
    if not is_alcoholic(title, body): return 0
    if "pousse" in t or "scaffa" in t: return 38
    if "cocktail" in t: return 30
    if "julep" in t or "smash" in t: return 26
    if "fizz" in t or "rickey" in t or "collins" in t or "cooler" in t: return 12
    if "highball" in t: return 14
    if "punch" in t:
        if any(k in body.lower() for k in ("bol", "litre", "bouteille", "personnes")):
            return 12
        return 18
    if "sour" in t: return 16
    if "cobbler" in t: return 14
    if "flip" in t or "eggnog" in t or "egg nog" in t: return 14
    if "champagne" in t: return 12
    if "toddy" in t or "sling" in t: return 18
    if "sangaree" in t or "sangar" in t: return 12
    return 18


def estimate_servings(body):
    b = body.lower()
    if "litre" in b: return 20
    if "personnes" in b:
        m = re.search(r"(\d+)\s*personnes", b)
        if m: return int(m.group(1))
    if "bol" in b: return 12
    return 1


def title_case(t):
    small = {"and", "of", "the", "a", "an", "in", "on", "to", "for", "or", "by", "at", "with", "de", "la", "le", "du"}
    parts = t.split()
    out = []
    for i, w in enumerate(parts):
        wl = w.lower()
        if i > 0 and wl in small:
            out.append(wl)
        else:
            out.append(w[0:1].upper() + w[1:])
    return " ".join(out)


def main():
    if not RAW.exists():
        print("Missing raw file:", RAW, file=sys.stderr)
        sys.exit(1)
    text = normalize(RAW.read_text(encoding="utf-8", errors="replace"))
    lines = text.split(chr(10))
    recipes = find_recipes(lines)
    print("Found", len(recipes), "candidate recipes (pre-filter).", file=sys.stderr)

    out_records = []
    seen_keys = set()
    skipped = 0
    for num, title, body_lines in recipes:
        body_text = chr(10).join(l for l in body_lines if l.strip()).strip()
        if not body_text or len(body_text) < 20:
            skipped += 1
            continue
        if not title or len(title) < 3:
            skipped += 1
            continue
        # Filter clear non-recipe titles
        tlow = title.lower()
        if any(k in tlow for k in ("sommaire", "table", "index", "contents")):
            skipped += 1
            continue
        # Title may include trailing OCR noise; strip
        title = title.strip().rstrip(".,").strip()
        # If body has no alcohol mention and title has no mixer hint, skip
        if not is_alcoholic(title, body_text):
            if not any(k in tlow for k in ("lemonade", "lemonaclc", "punch", "cooler", "cocktail", "sour", "fizz", "highball", "chocolat", "coffee", "tea")):
                skipped += 1
                continue
        key = (num, tlow)
        if key in seen_keys:
            continue
        seen_keys.add(key)

        glass = detect_glass(title, body_text)
        method = detect_method(title, body_text)
        garnish = detect_garnish(body_text)
        alc = is_alcoholic(title, body_text)
        abv = estimate_abv(title, body_text) if alc else 0
        raw_egg = has_raw_egg(body_text)
        display_title = title_case(title)
        original = "No " + str(num) + chr(10) + title + chr(10) + body_text

        rec = {
            "title": display_title,
            "content_type": "cocktail",
            "is_alcoholic": alc,
            "is_historic": 1,
            "source_book": "American Bar (Frank P. Newman, 1904)",
            "source_year": 1904,
            "source_region": "Paris, France",
            "contributor_name": "Frank P. Newman",
            "contributor_story": CONTRIBUTOR_STORY,
            "cuisine": "cocktail",
            "description": make_description(title),
            "servings": estimate_servings(body_text),
            "prep_minutes": 5 if "punch" in tlow and "bol" in body_text.lower() else 3,
            "cook_minutes": 2 if (method == "built_hot" or method == "flamed") else 0,
            "original_text": original,
            "modernized_text": "",
            "instructions": split_instructions(body_text),
            "ingredients": extract_ingredients(body_text),
            "glass_type": glass,
            "method": method,
            "garnish": garnish,
            "abv_percent": abv,
            "newman_recipe_number": num,
        }
        if raw_egg:
            rec["safety_notes"] = "Original uses raw whole egg; use pasteurized egg for modern preparation."
        else:
            rec["safety_notes"] = None
        out_records.append(rec)

    OUT.write_text(
        chr(10).join(json.dumps(r, ensure_ascii=False) for r in out_records) + chr(10),
        encoding="utf-8",
    )
    print("Wrote", len(out_records), "recipes to", OUT, "(skipped", skipped, ")")


if __name__ == "__main__":
    main()

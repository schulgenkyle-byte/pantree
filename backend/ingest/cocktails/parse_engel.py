#!/usr/bin/env python3
"""
Parse Leo Engel's 1878 "American and Other Drinks" (Criterion Restaurant, London)
into NDJSON for pan-tree's Mixology tab.

Source: Internet Archive OCR (americanandothe00engegoog).
"""
import json
import re
from pathlib import Path

HERE = Path(__file__).parent
SRC = HERE / "_tmp" / "engel_source.txt"
OUT = HERE / "leo-engel-1878.ndjson"

CONTRIBUTOR_STORY = (
    "Leo Engel was a celebrated American bartender who emigrated to London in the 1870s "
    "and presided over the bar at the Criterion Restaurant in Piccadilly Circus, where he "
    "introduced British drinkers to the craft of the American 'cooling drink.' His 1878 "
    "compendium 'American and Other Drinks' was one of the first British-published cocktail "
    "books, drawing from his lengthy sojourn in the United States and seasoned by Criterion "
    "house specialties. He is credited with popularizing the cocktail among the bon-vivants "
    "of late-Victorian London, from the Prince of Wales downwards."
)

# Category headers that are NOT recipes (page headers / section titles)
SECTION_HEADERS = {
    "PUNCH", "PUNCHES", "JULEPS", "SMASHES", "COBBLERS", "COCKTAILS",
    "CRUSTAS", "MULLS", "SANGAREES", "TODDIES", "SLINGS", "FIXES",
    "SOURS", "FLIPS", "NEGUS", "POUSEE CAFES", "POUSSE CAFE",
    "FANCY DRINKS", "MISCELLANEOUS DRINKS", "TEMPERANCE DRINKS",
    "AMERICAN AND OTHER DRINKS", "CONTENTS", "PREFACE",
    "CUPS", "BISHOPS", "FLIP", "EGG NOGG", "EGG NOGGS",
    "LEMONADES", "AMERICAN A^I> O^H^LRF IMTMLKFK",
    "HOT DRINKS",
}

NON_ALCOHOLIC = {
    "LEMONADE", "PLAIN LEMONADE", "ORANGEADE", "ORGEAT LEMONADE",
    "DRINK FOR THE DOG DAYS", "SODA NECTAR", "SHERBET",
    "LEMONADE POWDERS", "GINGER-BEER POWDERS", "LEMON SHERBET",
    "DRINK FOR HOME", "NECTAR", "ORANGE SHERBET", "MILK LEMONADE",
    "LEMON WHEY", "LEMON SYLLABUBS", "PRAIRIE OYSTER",
}

# OCR fixes specific to this scan
def normalize(text: str) -> str:
    t = text.replace("\r", "")
    t = re.sub(r"[‘’]", "'", t)
    t = re.sub(r"[“”]", '"', t)
    t = t.replace("Cura9oa", "Curacoa").replace("Curaçoa", "Curacoa")
    t = t.replace("Cura9ao", "Curacao").replace("Curaçao", "Curacao")
    return t


# Head pattern: number (1-3 digits) followed by spaces and capital-letter title
# ending with period, comma, or end-of-line. Title contains uppercase letters
# possibly with spaces, hyphens, apostrophes, parens, ampersands.
RECIPE_HEAD = re.compile(
    r"^\s*(\d{1,3})\s+([A-Z][A-Z0-9\.\-'\" \(\)&,]{2,70})\s*$",
    re.MULTILINE,
)

PAGE_HEADER_RE = re.compile(
    r"^\s*(\d{1,3}\s+(AMERICAN AND OTHER DRINKS|PUNCH|JULEPS|COBBLERS|FANCY DRINKS|"
    r"COCKTAILS|FLIPS|NEGUS|TEMPERANCE|MISCELLANEOUS|MULLED|SANGAREES|TODDIES))\.?\s*$",
    re.IGNORECASE,
)


def parse_recipes():
    raw = SRC.read_text(encoding="utf-8", errors="replace")
    raw = normalize(raw)
    lines = raw.split("\n")

    # Find start: first line "1 BRANDT FUHCH." (which OCRs to BRANDY PUNCH)
    start_idx = None
    for i, ln in enumerate(lines):
        s = ln.strip()
        m = RECIPE_HEAD.match(s)
        if m and m.group(1) == "1" and "PUNCH" in m.group(2).upper().replace("FUHCH", "PUNCH"):
            start_idx = i
            break
    if start_idx is None:
        # fall back: find "1 BRANDY PUNCH"
        for i, ln in enumerate(lines):
            if re.match(r"^\s*1\s+[A-Z]", ln) and i > 3000:
                start_idx = i
                break
    if start_idx is None:
        start_idx = 0

    # End: stop at "MODE OF DRINKING" instructions (around line 5860) or
    # end of file. Recipes go up through ~No. 202.
    end_idx = len(lines)
    for i in range(start_idx + 100, len(lines)):
        su = lines[i].strip().upper()
        if su == "MODE OF DRINKING." or su == "MODE OF DRINKING" \
                or su == "THE END." or su == "THE END":
            end_idx = i
            break

    body = lines[start_idx:end_idx]

    # Slice into (number, title, text-lines)
    recipes = []
    cur_num = None
    cur_title = None
    cur_body = []
    for ln in body:
        s = ln.strip()
        if PAGE_HEADER_RE.match(s):
            continue
        m = RECIPE_HEAD.match(s)
        if m:
            num = int(m.group(1))
            title_raw = m.group(2).strip().rstrip(",.").strip()
            # Reject if title is a known section header
            tu = title_raw.upper().replace(",", "").replace(".", "").strip()
            if tu in SECTION_HEADERS:
                # commit previous, then skip
                if cur_title is not None:
                    recipes.append((cur_num, cur_title, cur_body))
                cur_num = None; cur_title = None; cur_body = []
                continue
            # commit prior
            if cur_title is not None:
                recipes.append((cur_num, cur_title, cur_body))
            cur_num = num
            cur_title = title_raw
            cur_body = []
        else:
            if cur_title is not None:
                cur_body.append(ln)
    if cur_title is not None:
        recipes.append((cur_num, cur_title, cur_body))

    # Filter duplicates - same number + title (OCR repeated pages)
    seen_pairs = set()
    deduped = []
    for n, t, b in recipes:
        key = (n, t.upper().strip())
        if key in seen_pairs:
            continue
        seen_pairs.add(key)
        deduped.append((n, t, b))

    return deduped


# --- Title-casing for output: convert ALL CAPS to nice Title Case ---
def title_case(s: str) -> str:
    s = s.strip().rstrip(",.").strip()
    # Lowercase common short words
    small = {"a", "an", "and", "or", "of", "the", "in", "on", "to", "for", "with",
             "by", "from", "at", "la", "le", "des", "du", "&"}
    parts = re.split(r"(\s+|-|/)", s.lower())
    out = []
    for i, p in enumerate(parts):
        if not p.strip() or p in {"-", "/", " "}:
            out.append(p)
            continue
        if i > 0 and p in small:
            out.append(p)
        else:
            out.append(p[:1].upper() + p[1:])
    res = "".join(out)
    # Common OCR mishaps in titles
    res = res.replace("Fuhcii", "Punch").replace("Fuhch", "Punch")
    res = res.replace("Puiicif", "Punch").replace("Puhcif", "Punch")
    res = res.replace("Crusta", "Crusta").replace("Pousee", "Pousse")
    res = res.replace("Brandt", "Brandy")
    res = res.replace("Norfolk", "Norfolk")
    res = res.replace("Korfolk", "Norfolk")
    res = res.replace("Horfolk", "Norfolk")
    res = res.replace("Lainss'", "Ladies'")
    res = res.replace("Sukg", "Sling")
    res = res.replace("Nogg", "Nogg")
    res = res.replace("Cura9Oa", "Curacao").replace("Curacoa", "Curacao")
    return res


def classify(title: str, text: str):
    tl = title.lower()
    xl = text.lower()
    glass = "coupe"
    if "punch" in tl and ("party" in xl or "bowl" in xl or "gallon" in xl or "for" in tl):
        glass = "punch-bowl"
    elif "punch" in tl:
        glass = "highball"
    if "cup" in tl:
        glass = "punch-bowl"
    if "cobbler" in tl:
        glass = "goblet"
    if "julep" in tl:
        glass = "julep"
    if "smash" in tl:
        glass = "rocks"
    if "fizz" in tl:
        glass = "fizz"
    if "collins" in tl:
        glass = "collins"
    if "sour" in tl:
        glass = "sour"
    if "fix" in tl:
        glass = "highball"
    if "flip" in tl:
        glass = "coupe"
    if "egg nogg" in tl or "eggnogg" in tl:
        glass = "collins"
    if "cocktail" in tl:
        glass = "coupe"
    if "crusta" in tl:
        glass = "coupe"
    if "toddy" in tl and "hot" in tl:
        glass = "mug"
    if "sling" in tl:
        glass = "highball"
    if "sangaree" in tl:
        glass = "wine"
    if "pousse" in tl or "scaffa" in tl or "champerelle" in tl:
        glass = "cordial"
    if "blue blazer" in tl:
        glass = "mug"
    if "negus" in tl:
        glass = "wine"
    if "mulled" in tl:
        glass = "mug"
    if "bishop" in tl or "archbishop" in tl or "cardinal" in tl or "pope" in tl:
        glass = "punch-bowl"
    if "lemonade" in tl or "orangeade" in tl or "sherbet" in tl:
        glass = "highball"

    method = "stirred"
    if "shake" in xl or "shaken" in xl:
        method = "shaken"
    if "boil" in xl or "fire" in xl or "hot" in tl:
        method = "hot-built"
    if "ignite" in xl or "blaze" in tl or "blazer" in tl:
        method = "flamed"
    if "pousse" in tl or "scaffa" in tl or "champerelle" in tl:
        method = "layered"
    if "smash" in tl or "julep" in tl or "muddle" in xl:
        method = "muddled"
    if "fizz" in tl:
        method = "shaken-built"
    return glass, method


def guess_abv(title: str) -> int:
    t = title.lower()
    if t.upper() in NON_ALCOHOLIC:
        return 0
    if any(k in t for k in ["lemonade", "orangeade", "sherbet", "syllabub",
                            "soda nectar", "ginger-beer powder",
                            "lemonade powder", "drink for", "lemon whey"]):
        return 0
    if "punch" in t:
        return 14
    if "cup" in t:
        return 12
    if "cobbler" in t or "fizz" in t or "collins" in t or "rickey" in t:
        return 12
    if "flip" in t or "egg nogg" in t or "eggnogg" in t:
        return 14
    if "sour" in t or "fix" in t or "smash" in t or "daisy" in t:
        return 18
    if "julep" in t:
        return 22
    if "toddy" in t or "sling" in t:
        return 18
    if "cocktail" in t:
        return 32
    if "crusta" in t:
        return 30
    if "pousse" in t or "scaffa" in t or "champerelle" in t:
        return 40
    if "negus" in t or "sangaree" in t:
        return 14
    if "mulled" in t or "bishop" in t or "archbishop" in t:
        return 12
    if "blue blazer" in t:
        return 35
    return 18


# Ingredient extraction
JIGGER_RE = re.compile(
    r"(one|two|three|four|five|six|half a|one-half|a half|half|one and a half|"
    r"two-thirds|one-third|three-quarters|one-quarter|quarter|a|the|several|few)?\s*"
    r"(table-?\s*spoonful?s?|tea-?\s*spoonful?s?|spoonful?s?|"
    r"wine\s*glass(?:es)?|wine-?glass(?:es)?|"
    r"jigger?s?|pony|ponies|"
    r"dash(?:es)?|slice?s?|lump?s?|drop?s?|gill?s?|"
    r"bottle?s?|quart?s?|pint?s?|gallon?s?|ounce?s?|pound?s?|"
    r"tumbler?s?|glass(?:es)?)\s+(?:of\s+)?"
    r"([A-Za-z][A-Za-z\-'\s]{2,40}?)(?=[,.;]|\s+(?:and|or|in|with|into|to|on|from|"
    r"shake|mix|strain|fill|add|stir|serve|pour|dissolve|put|beat|take|let|set|surround)\b)",
    re.IGNORECASE,
)

AMOUNT_WORDS = {
    "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6,
    "a": 1, "the": 1, "half": 0.5, "half a": 0.5, "one-half": 0.5,
    "a half": 0.5, "one and a half": 1.5,
    "two-thirds": 0.67, "one-third": 0.33, "three-quarters": 0.75,
    "one-quarter": 0.25, "quarter": 0.25,
    "several": 2, "few": 3,
}

MEASURE_OZ = {
    "wine glass": 2.0, "wineglass": 2.0, "wine-glass": 2.0,
    "jigger": 1.5, "jiggers": 1.5,
    "pony": 1.0, "ponies": 1.0,
    "table-spoonful": 0.5, "tablespoonful": 0.5, "tablespoonfuls": 0.5,
    "table-spoonfuls": 0.5, "tablespoon": 0.5, "table spoon": 0.5,
    "tea-spoonful": 0.17, "teaspoonful": 0.17, "teaspoonfuls": 0.17,
    "tea-spoonfuls": 0.17, "spoonful": 0.5, "spoonfuls": 0.5,
    "dash": 0.03, "dashes": 0.03,
    "drop": 0.02, "drops": 0.02,
    "gill": 4.0, "gills": 4.0,
    "lump": 0.0, "lumps": 0.0,
    "slice": 0.0, "slices": 0.0,
    "bottle": 25.0, "bottles": 25.0,
    "quart": 32.0, "quarts": 32.0,
    "pint": 16.0, "pints": 16.0,
    "gallon": 128.0, "gallons": 128.0,
    "ounce": 1.0, "ounces": 1.0,
    "tumbler": 8.0, "tumblers": 8.0,
    "glass": 4.0, "glasses": 4.0,
}


def extract_ingredients(text: str):
    ings = []
    seen = set()
    norm = re.sub(r"\s+", " ", text)
    for m in JIGGER_RE.finditer(norm):
        qty_word = (m.group(1) or "one").lower().strip()
        unit = m.group(2).lower().replace("  ", " ").replace("- ", "-")
        unit_key = unit
        # canonicalize unit form
        for k in MEASURE_OZ:
            if unit.replace("-", " ") == k.replace("-", " "):
                unit_key = k
                break
        name = m.group(3).strip().rstrip(".,;").strip()
        name = re.sub(r"^(of\s+)", "", name, flags=re.IGNORECASE).strip()
        name = re.sub(r"\s+", " ", name)
        name = re.split(r"\b(and|or|in|with|into|to|on|from|fill|shake|mix|stir|"
                        r"strain|serve|let|put|set|add|beat|take)\b",
                        name, maxsplit=1, flags=re.IGNORECASE)[0].strip()
        name = name.strip(",. ")
        if not name or len(name) < 2:
            continue
        nl = name.lower()
        if nl in {"ice", "fine ice", "shaved ice", "chipped ice", "cracked ice",
                  "fine shaved ice", "tumbler", "glass", "shaker", "bowl"}:
            continue
        if nl.startswith("the ") and len(nl) < 8:
            continue
        qty = AMOUNT_WORDS.get(qty_word, 1)
        oz = round(qty * MEASURE_OZ.get(unit_key, 0.0), 2)
        key = nl
        if key in seen:
            continue
        seen.add(key)
        ings.append({
            "name": name,
            "amount": qty,
            "unit": unit_key.replace("-", " "),
            "oz": oz,
        })
    return ings


def extract_garnish(text: str):
    tl = text.lower()
    pairs = [
        ("twist a piece of lemon peel", "lemon peel twist"),
        ("piece of lemon peel", "lemon peel"),
        ("lemon peel", "lemon peel"),
        ("orange peel", "orange peel"),
        ("slice of orange", "orange slice"),
        ("slice of lemon", "lemon slice"),
        ("grated nutmeg", "grated nutmeg"),
        ("grate a little nutmeg", "grated nutmeg"),
        ("nutmeg on top", "grated nutmeg"),
        ("berries in season", "seasonal berries"),
        ("fruits in season", "seasonal fruit"),
        ("fruit in season", "seasonal fruit"),
        ("sprig of mint", "mint sprig"),
        ("sprigs of mint", "mint sprigs"),
        ("strawberries", "strawberries"),
        ("pine-apple", "pineapple"),
    ]
    for needle, canon in pairs:
        if needle in tl:
            return canon
    return None


def has_raw_egg(text: str) -> bool:
    tl = text.lower()
    if any(k in tl for k in ["fresh egg", "white of an egg", "yolk of",
                              "yolks of", "whites of", "raw egg", "one egg",
                              "the egg", "egg (the white", "egg (the yolk"]):
        if "boil" in tl or "fire" in tl or "boiling" in tl or "hot wine" in tl:
            return False
        return True
    return False


def make_description(title: str) -> str:
    t = title.lower()
    if "criterion" in t:
        return "A house specialty of London's Criterion Restaurant, where Engel presided."
    if "alabazam" in t:
        return "A flagship Engel concoction—brandy, curacao, lemon, and bitters."
    if "blue blazer" in t:
        return "Jerry Thomas's flaming Scotch ritual, codified for British bartenders."
    if "bosom caresser" in t:
        return "Classic Victorian fancy drink: brandy, curacao, sugar, and egg yolk."
    if "manhattan" in t or "martini" in t:
        return "A pre-Prohibition cocktail in late-Victorian Criterion form."
    if "pousee" in t or "pousse" in t or "scaffa" in t or "champerelle" in t:
        return "Layered cordial drink served after dinner."
    if "julep" in t:
        return "Classic mint julep style — over crushed ice with fruit."
    if "cobbler" in t:
        return "Wine or spirit with sugar, fruit, and ice; served with a straw."
    if "fizz" in t:
        return "Shaken sour finished with seltzer."
    if "punch" in t:
        return "1878-era punch from the Criterion repertoire."
    if "cup" in t:
        return "Wine or champagne cup, fruited and iced for entertaining."
    if "cocktail" in t:
        return "1878 cocktail, preserved verbatim from Engel's Criterion manual."
    if "smash" in t:
        return "Muddled-mint smash served over crushed ice."
    if "sour" in t:
        return "Spirit, lemon, and sugar — Victorian style."
    if "flip" in t:
        return "Egg-rich flip in the late-19th-century manner."
    if "toddy" in t:
        return "Sugar, spirit, and water — cold or hot."
    if "sling" in t:
        return "Classic sling: spirit, sugar, water, and a hint of nutmeg."
    if "negus" in t:
        return "Sweetened, spiced wine — a Victorian-era warmer."
    if "sangaree" in t:
        return "Wine or spirit sangaree dusted with nutmeg."
    if "lemonade" in t:
        return "A 19th-century lemonade — refreshing and citrus-forward."
    if "knickerbein" in t or "knickerbocker" in t:
        return "Layered after-dinner drink, often with kummel and cordials."
    if "crusta" in t:
        return "Sugared-rim fancy drink, ancestor of the sidecar."
    if "bishop" in t or "archbishop" in t or "cardinal" in t or "pope" in t:
        return "Mulled, fortified-wine punch from the British ecclesiastical tradition."
    if "blazer" in t:
        return "Flaming spirit, ritualistically poured between mugs."
    return "1878 Engel drink, preserved verbatim from American and Other Drinks."


def build_instructions(text: str):
    t = re.sub(r"\s+", " ", text).strip()
    parts = re.split(r"(?<=[.;])\s+(?=[A-Z])", t)
    steps = [p.strip() for p in parts if p.strip()]
    return steps[:8] if steps else [t]


def main():
    recipes = parse_recipes()

    out_records = []
    seen_titles = set()
    for num, raw_title, body_lines in recipes:
        text = " ".join(l.strip() for l in body_lines if l.strip())
        text = re.sub(r"\s+", " ", text).strip()
        # remove page headers stuck inside text
        text = re.sub(r"\b\d{1,3}\s+AMERICAN AND OTHER DRINKS\.?\s*", " ",
                      text, flags=re.IGNORECASE)
        text = re.sub(r"\bAMERICAN AND OTHER DRINKS\.?\s*\d{1,3}\b", " ",
                      text, flags=re.IGNORECASE)
        text = re.sub(r"\s+", " ", text).strip()
        if len(text) < 25:
            continue

        # Filter sections we should skip
        title_uc = raw_title.upper().strip().rstrip(",.").strip()
        if title_uc in SECTION_HEADERS:
            continue
        title_nice = title_case(raw_title)

        # Skip non-cocktail (e.g., "Drink for the Dog Days" is non-alc; keep but mark)
        is_alc = 0 if title_uc in NON_ALCOHOLIC else 1

        # Skip syrup/powder production-only items
        if any(k in title_uc for k in ["LEMONADE POWDERS", "GINGER-BEER POWDERS",
                                       "RASPBERRY, STRAWBERRY"]):
            continue

        glass, method = classify(title_nice, text)
        abv = guess_abv(title_nice) if is_alc else 0

        if "hot" in title_nice.lower() or method == "hot-built":
            prep = 5; cook = 3
        elif method == "frappe":
            prep = 5; cook = 0
        elif method == "layered":
            prep = 4; cook = 0
        elif method == "shaken":
            prep = 3; cook = 0
        else:
            prep = 3; cook = 0

        ingredients = extract_ingredients(text)
        garnish = extract_garnish(text)

        servings = 1
        if any(k in title_nice.lower() for k in ["bowl", "cup"]) or \
           re.search(r"for a party|gallons?\b|bottles? of (champagne|brandy)", text, re.IGNORECASE):
            servings = 8

        rec = {
            "title": title_nice,
            "content_type": "cocktail" if is_alc else "beverage",
            "is_alcoholic": is_alc,
            "is_historic": 1,
            "source_book": "American and Other Drinks (Leo Engel, 1878)",
            "source_year": 1878,
            "source_region": "London, UK (Criterion Restaurant)",
            "contributor_name": "Leo Engel",
            "contributor_story": CONTRIBUTOR_STORY,
            "cuisine": "cocktail",
            "description": make_description(title_nice),
            "servings": servings,
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

        key = title_nice.lower()
        if key in seen_titles:
            # disambiguate by adding the recipe number
            key2 = f"{title_nice} (No. {num})".lower()
            if key2 in seen_titles:
                continue
            rec["title"] = f"{title_nice} (No. {num})"
            seen_titles.add(key2)
        else:
            seen_titles.add(key)
        out_records.append(rec)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8") as f:
        for r in out_records:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    print(f"Wrote {len(out_records)} recipes to {OUT}")
    notable_keys = ["alabazam", "blue blazer", "bosom caresser", "criterion",
                    "knickerbein", "tom and jerry", "rocky mountain",
                    "philadelphia fish house", "japanese cocktail",
                    "prairie oyster", "criterion reviver"]
    notables = []
    for r in out_records:
        for k in notable_keys:
            if k in r["title"].lower():
                notables.append(r["title"])
                break
    print("Notable captures:", notables[:8])


if __name__ == "__main__":
    main()

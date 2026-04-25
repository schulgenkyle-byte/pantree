"""
COCKTAIL-STRAUB harvest agent for pan-tree.

Parses Jacques Straub's 1914 "Drinks" (Internet Archive: publication_202009)
into NDJSON for the TRUE VINTAGE collection.

Straub was head steward at Chicago's Pendennis Club (later Blackstone) and
his pocket manual carried a foreword from Oscar Tschirky of the Waldorf-Astoria.
~700 recipes, the largest pre-Prohibition cocktail catalog.
"""

import json
import os
import re
import urllib.request

OUT_PATH = os.path.join(os.path.dirname(__file__), "straub-1914.ndjson")
RAW_PATH = os.path.join(os.path.dirname(__file__), "_tmp", "straub_djvu.txt")
ARCHIVE_URL = "https://archive.org/download/publication_202009/publication_djvu.txt"

CONTRIBUTOR_STORY = (
    "Jacques Straub was head steward at the Pendennis Club in Louisville and "
    "later wine steward at Chicago's Blackstone Hotel. His 1914 pocket manual, "
    "endorsed by Oscar Tschirky (the legendary Waldorf-Astoria maitre d'), "
    "compiled about seven hundred recipes from the best hotels, clubs and "
    "buffets of the pre-Prohibition era."
)

# ----------------------------------------------------------------------------
# Fetch
# ----------------------------------------------------------------------------

def fetch_book():
    if os.path.exists(RAW_PATH) and os.path.getsize(RAW_PATH) > 50000:
        with open(RAW_PATH, "r", encoding="utf-8-sig") as f:
            return f.read()
    os.makedirs(os.path.dirname(RAW_PATH), exist_ok=True)
    req = urllib.request.Request(ARCHIVE_URL, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=120) as r:
        text = r.read().decode("utf-8-sig", errors="replace")
    with open(RAW_PATH, "w", encoding="utf-8") as f:
        f.write(text)
    return text


# ----------------------------------------------------------------------------
# Smart-title (same approach as Bullock parser)
# ----------------------------------------------------------------------------

SMALL_WORDS = {"of", "the", "and", "in", "on", "for", "to", "with", "a", "an",
               "or", "by", "at", "from", "la", "de", "del", "el"}


def _cap_word(w):
    if not w:
        return w
    if "'" in w:
        sub = w.split("'")
        sub[0] = sub[0].capitalize()
        for j in range(1, len(sub)):
            if sub[j].lower() in ("s", "t", "ll", "ve", "re", "d", "m"):
                sub[j] = sub[j].lower()
            elif sub[j]:
                sub[j] = sub[j].capitalize()
        return "'".join(sub)
    return w.capitalize()


def smart_title(s):
    parts = s.split(" ")
    out = []
    for i, w in enumerate(parts):
        if not w:
            out.append(w)
            continue
        letters = re.sub(r"[^A-Za-z]", "", w)
        if len(letters) <= 4 and letters.isupper() and "." in w:
            out.append(w)
            continue
        # If the previous word ends with "(", treat this word as title-position
        # so it gets capitalized regardless of small-word status.
        prev_ends_paren = (i > 0 and out and out[-1].endswith("("))
        if i > 0 and w.lower() in SMALL_WORDS and not prev_ends_paren:
            out.append(w.lower())
            continue
        if re.match(r"^\d+(st|nd|rd|th)$", w.lower()):
            out.append(w.lower())
            continue
        if "-" in w:
            sub = []
            for piece in w.split("-"):
                sub.append(_cap_word(piece) if piece else piece)
            out.append("-".join(sub))
            continue
        if w.lower() == "no.":
            out.append("No.")
            continue
        out.append(_cap_word(w))
    return " ".join(out)


# ----------------------------------------------------------------------------
# Pre-clean OCR
# ----------------------------------------------------------------------------

PAGE_NUM_RE = re.compile(r"^\s*\d{1,3}\s*$")
RUNNING_HEADER_RE = re.compile(r"^\s*D[REI]?[INL]?[NL]?KS\s*$", re.IGNORECASE)
# Examples: "DRINKS", "DEINKS", "DRTNKS", "DKINKS", "PRINKS"
HEADER_VARIANTS_RE = re.compile(r"^\s*[DPK][REIK]?[INTL]?[NLT]?KS?\s*$", re.IGNORECASE)


def looks_like_running_header(line):
    s = line.strip()
    if not s:
        return False
    # Page number alone
    if PAGE_NUM_RE.match(s):
        return True
    # The OCR turns "DRINKS" into many variants — match short uppercase words 5-7 chars
    if 4 <= len(s) <= 7 and s.isupper() and s.isalpha():
        # Only running header variants make it here
        if s in ("DRINKS", "DEINKS", "DRTNKS", "DKINKS", "PRINKS", "DLINKS",
                 "DEINKS", "DBINKS", "DRTKS", "DRINK"):
            return True
    return False


# Top-level section headers — these reset the current_section.
TRUE_SECTION_HEADERS = {
    "COBBLERS.", "COCKTAILS.", "COLLINS.", "COOLERS.", "CUPS.", "DAISYS.",
    "EGGNOGS.", "FIZZES.", "FLIPS.", "FRAPPES.", "HIGHBALLS.", "HOT DRINKS.",
    "JULEPS.", "LEMONADES.", "MISCELLANEOUS.", "PUNCHES.", "RICKEYS.", "SOURS.",
    "SLINGS.", "SMASH.", "TODDIES.", "INDEX",
    "PUNCHES — NON-ALCOHOLIC.", "PUNCHES-NON-ALCOHOLIC.",
    "PUNCHES—NON-ALCOHOLIC.", "PUNCHES NON-ALCOHOLIC.",
}

# Subheaders that should be ATTACHED to the recipe body (not reset section).
SUB_HEADERS = {
    "SERVE IN COCKTAIL GLASS UNLESS OTHERWISE", "SPECIFIED.",
    "SEIIVE IN TALL GLASS ALWAYS.", "SERVE IN TALL GLASS ALWAYS.",
    "USE HEAVY BAR GLASSES OR METAL MUGS.", "USE A PHOSPHATE GLASS.",
    "USE WHISKEY GLASS.", "USE SHERRY GLASS.", "USE COLLINS GLASS.",
    "USE LONG THIN GLASS.", "USE A SHAKER.", "USE LARGE BOWL.",
    "USE GOBLET.", "ONE ONLY.", "1 GALLON PUNCH BOWL.", "1 GALLON.",
    "1 QUART.", "(SPECIAL BLACKSTONE.)",
}


def is_section_header(line):
    s = line.strip()
    if not s:
        return False
    if s.upper() in TRUE_SECTION_HEADERS:
        return True
    return False


def is_sub_header(line):
    s = line.strip()
    if not s:
        return False
    if s.upper() in SUB_HEADERS:
        return True
    # Generic all-caps short line that isn't a section header
    upper = s.upper()
    if 4 <= len(s) <= 60 and s == upper and any(c.isalpha() for c in s):
        if not any(c.islower() for c in s):
            return True
    return False


# ----------------------------------------------------------------------------
# Title detection
# ----------------------------------------------------------------------------
# Recipe titles in Straub:
#  - Ends with "." (sometimes ":" or no punctuation due to OCR)
#  - Title case (e.g., "Aviation Cocktail.", "Bobbie Burns Cocktail (For Two).")
#  - Often surrounded by blank lines
#  - May have parentheticals
#  - Length 4-60 characters

# Words we know appear in titles to strengthen detection
TITLE_KEYWORDS_RE = re.compile(
    r"\b(Cocktails?|Coolers?|Cups?|Daisys?|Daisies|Eggnogs?|Egg\s+Nogg|"
    r"Fizz(?:es)?|Flips?|Frappes?|Highballs?|"
    r"High\s+Balls?|Toddy|Toddies|Juleps?|Lemonades?|Phosphates?|Punch(?:es)?|"
    r"Rickeys?|Sours?|Slings?|"
    r"Smash(?:es)?|Cobblers?|Collins|Negus|Sangaree|Pousse\s+Caf[eé]|Pouss[ée]|"
    r"Crusta|Float|Bracer|Drip|Dream|Kiss|Tip|Blush|Star|Special|Skin|"
    r"Buck|Fence|Fix|Mixture|Bowl|Cordial|Rose|Bishop|Snowball|"
    r"Knickerbein|Knickerbocker|Stinger|Stonewall|Velvet|Vichy|Pop|"
    r"Sangree|Shrub|Shandy|Soother|Sunset|Reviver|Sazerac|Slipper|Touraine|"
    r"Blazer|Jerry|Suissess|Suisette|Polly|Promoter|Frazie|Floater|"
    r"Appetizer|Whirligig|Whirl|Eye-Opener|Boules|Tip-Top|"
    r"Sister|Taylor|Tobie|Ferguson|"
    r"Mike|Hawk|Days|Horse|Boulevard|Plush|Cap|Assorted|"
    r"and\s+Egg|and\s+Honey|and\s+Bitters|and\s+Seltzer|and\s+Eye|"
    r"de\s+Paree|Au\s+Kirsch|Coffee|Ward|Tea\s+Shake)\b",
    re.IGNORECASE,
)


def looks_like_title(line):
    s = line.strip().rstrip(",").rstrip()
    # Strip trailing period for matching
    if not s:
        return False
    if len(s) < 4 or len(s) > 70:
        return False
    # Must end with period or contain typical title structure
    # We accept lines that end with "." and are mostly title-cased.
    if not (s.endswith(".") or s.endswith(":")):
        return False
    # Avoid all-lowercase or all-uppercase
    if s == s.upper():
        return False
    # Drop instructional fragments that end with a period
    # Hard reject instruction-fragment starts (these can never be titles).
    HARD_BAD = (
        "crush ", "shake ", "shake;", "shake,", "shake.", "shake well",
        "stir ", "stir;", "stir,", "stir.", "stir well", "stir up",
        "fill ", "fill;", "fill,", "pour ", "pour;", "let ",
        "place ", "serve", "strain", "add ", "set ", "twist",
        "drop ", "drop;", "mix ", "mix;", "mixed", "decorate", "dress",
        "remove", "cover", "squeeze", "rub ", "make ", "to serve",
        "beat", "dip ", "have this", "heat ", "do not", "it ", "the ",
        "this ", "with ", "when ", "after", "carefully", "ice.",
        "fine ice", "no mixed", "(note", "(this", "(serve",
        "above", "drinks ", "drinks.", "lemon peel", "orange peel",
        "olive.", "olives.", "cherry.", "fruit.", "fruits.",
        "sorve in", "use a", "use the", "see that", "see ", "in old",
        "in goblet", "in fine", "in shaker",
        "into ", "ginger ale", "club soda", "imported", "sugar to taste",
        "grated nutmeg", "shake ", "stir ",
        "shake.", "shake.serve",
        # Cross-references in bodies (must NOT be promoted to titles):
        "same as ", "same as", "martini with ", "manhattan with ",
        "bronx with ", "bourbon whiskey rickey", "irish whiskey high",
        "gin fizz made", "gin rickey made", "plain lemonade",
        "whiskey sour, with",
    )
    # Soft bad starts: ok IF a strong keyword like "Cocktail" appears
    SOFT_BAD = (
        "old ", "same ", "all ", "bourbon ", "rye ", "scotch ", "irish ",
        "rum ", "gin ", "brandy ", "vermouth ", "champagne ", "moselle ",
        "rhine ", "claret ", "seltzer ", "creme", "crème", "kir",
        "absinth", "anise", "bene", "char", "ma ", "milk ", "sugar ",
        "juice ", "white ", "yolk ", "rind ", "peel ",
    )
    low = s.lower()
    for bad in HARD_BAD:
        if low.startswith(bad):
            return False
    for bad in SOFT_BAD:
        if low.startswith(bad):
            if TITLE_KEYWORDS_RE.search(s):
                break
            return False
    # Reject if numeric starts
    if re.match(r"^[0-9¼½¾%]", s):
        return False
    # Reject odd OCR fragments
    if re.match(r"^[Vv]\s*\d", s) or low.startswith(("vs ", "vi ", "y ", "v 2", "v2")):
        return False
    # Reject if first character isn't a capital letter (common OCR artifact)
    if not s[0].isalpha() or not s[0].isupper():
        return False
    # Reject lines that look like ingredients (contain typical ingredient amounts)
    if re.search(r"\b(jiggers?|ponies|ponys|barspoons?|barspoonful|"
                 r"dashes?|drops?|lumps?|pints?|quarts?|gallons?|"
                 r"teaspoonfuls?|tablespoonfuls?)\b", low):
        return False
    if re.search(r"^\d", s):
        return False
    if re.search(r"\bjuice\s+of\b", low):
        return False
    # Verify the line is title-cased: every alphabetic word must start with
    # a capital letter (allowing small connector words like "of", "and").
    words_alpha = re.findall(r"[A-Za-z][A-Za-z'\-]*", s)
    if not words_alpha:
        return False
    for j, w in enumerate(words_alpha):
        wl = w.lower()
        if j > 0 and wl in SMALL_WORDS:
            continue
        # OCR sometimes captures "No.1" or "II" — accept all-caps short words
        if w.isupper() and len(w) <= 3:
            continue
        if not w[0].isupper():
            return False
    # The vast majority of titles contain a known beverage word
    if TITLE_KEYWORDS_RE.search(s):
        return True
    # Otherwise: only very short title-cased lines ending with "."
    # Heuristic: 1-4 words, where caps-words pattern looks like a name.
    words = re.findall(r"[A-Za-z]+", s)
    # Allow lowercase "small" words (and, of, the)
    caps_ok = all(
        w[0].isupper() or w.lower() in SMALL_WORDS
        for w in words
    )
    if 1 <= len(words) <= 5 and caps_ok:
        # Avoid instruction-y bare lines.
        forbidden_words = {
            "sugar", "milk", "juice", "ice", "iced", "stir", "shake", "serve",
            "pour", "fill", "fruit", "fruits", "lemon", "orange", "lime",
            "mint", "cherry", "cherries", "decorate", "decorating", "crush",
            "with", "for", "use", "drinks", "drink",
            "cordial", "garnish", "small", "strain", "add", "ornament",
            "into", "from", "above", "below", "after", "before",
            "olive", "olives", "twist", "again", "served",
        }
        # Reject if the FIRST significant word is an instruction word
        first = words[0].lower()
        if first in forbidden_words:
            return False
        # Reject if more than 2 of the words are instruction words
        instr_count = sum(1 for w in words if w.lower() in forbidden_words)
        if instr_count >= 2:
            return False
        if len(s) <= 60:
            return True
    return False


# ----------------------------------------------------------------------------
# Extract recipes
# ----------------------------------------------------------------------------

def clean_lines(text):
    """Pre-process OCR text: drop running headers, join hyphenated linebreaks,
    keep one blank line between paragraphs."""
    lines = text.splitlines()
    out = []
    for ln in lines:
        if looks_like_running_header(ln):
            continue
        # OCR misreads "DRINKS" as DEINKS / DRTNKS / etc.
        if HEADER_VARIANTS_RE.match(ln) and len(ln.strip()) <= 7:
            continue
        out.append(ln.rstrip())
    # Collapse 3+ blank lines into 2
    cleaned = []
    blank_run = 0
    for ln in out:
        if not ln.strip():
            blank_run += 1
            if blank_run <= 2:
                cleaned.append("")
        else:
            blank_run = 0
            cleaned.append(ln)
    return cleaned


def extract_recipes(text):
    # Find body between first section header and INDEX
    cobbler_idx = text.find("COBBLERS.")
    index_idx = text.find("\nINDEX")
    if index_idx < 0:
        index_idx = text.find("\nA.BMNTHE")  # OCR'd "ABSINTHE" in index
    if cobbler_idx < 0 or index_idx < 0:
        raise RuntimeError("Could not locate recipe body markers")
    body = text[cobbler_idx:index_idx]
    lines = clean_lines(body)

    # Walk lines accumulating recipes by title detection.
    recipes = []
    current_title = None
    current_section = None
    current_body = []

    def flush():
        if current_title is not None:
            recipes.append({
                "title": current_title,
                "section": current_section,
                "body": "\n".join(current_body).strip(),
            })

    for i, line in enumerate(lines):
        s = line.strip()
        if not s:
            if current_title is not None:
                current_body.append("")
            continue
        # Top-level section header — reset section.
        if is_section_header(line):
            flush()
            current_title = None
            current_body = []
            current_section = s.rstrip(".").title()
            continue
        # Subheader — append to body of current recipe (note about glass etc.)
        if is_sub_header(line) and current_title is not None:
            # Skip — it's a clarifying instruction; we don't add OCR'd ALL-CAPS
            # to the body (often noise), but we keep the recipe alive.
            continue
        # Title?
        if looks_like_title(line):
            prev_line = lines[i - 1].strip() if i > 0 else ""
            prev_blank = (i == 0) or (prev_line == "") or \
                is_section_header(lines[i - 1]) or is_sub_header(lines[i - 1])
            if prev_blank:
                flush()
                current_title = s.rstrip(".").rstrip()
                current_body = []
                continue
        # Otherwise body line
        if current_title is not None:
            current_body.append(s)
    flush()

    # Post-pass: titles with empty bodies often share the body of an adjacent
    # recipe (typical Straub idiom for "Tom Gin Fizz / Dry Gin Fizz / Sloe Gin
    # Fizz" sequences with one shared cross-reference body). Look forward, then
    # backward, for a body in the same section.
    for i in range(len(recipes)):
        r = recipes[i]
        if r["body"].strip():
            continue
        # Forward
        nb = ""
        for j in range(i + 1, min(i + 5, len(recipes))):
            if recipes[j]["section"] != r["section"]:
                break
            cand = recipes[j]["body"].strip()
            if cand:
                low = cand.lower()
                if low.startswith(("same as", "make these same",
                                   "prepare this drink", "gin cocktail",
                                   "martini with", "manhattan with",
                                   "bronx with", "use ", "fill ",
                                   "1 jigger", "juice ", "%", "y ", "v",
                                   "2 ", "3 ", "4 ")):
                    # Always share short cross-reference style ("Same as...")
                    if low.startswith(("same as", "make these same",
                                       "prepare this drink",
                                       "martini with", "manhattan with",
                                       "bronx with")):
                        nb = cand
                    # For "Fill cocktail glass with..." (Frappes Assorted), share
                    elif low.startswith("fill cocktail glass with"):
                        nb = cand
                break
        # Backward (e.g., assorted-frappe children sharing parent body)
        if not nb:
            for j in range(i - 1, max(i - 5, -1), -1):
                if recipes[j]["section"] != r["section"]:
                    break
                cand = recipes[j]["body"].strip()
                if cand and "frappe" in r["title"].lower() and \
                        cand.lower().startswith("fill cocktail glass"):
                    nb = cand
                    break
        if nb:
            r["body"] = nb
    return recipes


# ----------------------------------------------------------------------------
# Classification
# ----------------------------------------------------------------------------

def detect_glass(title, body, section):
    t = (title + " " + body).lower()
    sec = (section or "").lower()
    title_l = title.lower()

    if "punch bowl" in t or "gallon" in t:
        return "punch_bowl"
    if "claret glass" in t:
        return "wine"
    if "old fashion" in t or "old-fashion" in t:
        return "rocks"
    if "whiskey glass" in t:
        return "rocks"
    if "sherry glass" in t:
        return "sherry"
    if "fizz glass" in t:
        return "fizz"
    if "pousse cafe glass" in t or "pousse café glass" in t:
        return "pousse_cafe"
    if "delmonico glass" in t:
        return "fizz"
    if "phosphate glass" in t:
        return "fizz"
    if "collins glass" in t or "long thin glass" in t or "long glass" in t \
            or "tall thin glass" in t or "tall glass" in t or "highball glass" in t:
        return "highball"
    if "silver cup" in t or "julep" in title_l or "julep" in sec:
        return "julep"
    if "punch glass" in t or "punch glasses" in t:
        return "punch"
    if "goblet" in t:
        return "goblet"
    if "lemonade glass" in t:
        return "highball"
    if "champagne glass" in t or "tall champagne" in t:
        return "champagne"
    if "shaker" in t and "fancy cocktail glass" in t:
        return "cocktail"
    if "fancy cocktail glass" in t:
        return "cocktail"
    if "bowl" in t and "punch" in title_l:
        return "punch_bowl"
    if "stein" in t:
        return "stein"
    if "metal mug" in t or "mug" in t:
        return "mug"

    # Section-based defaults
    section_map = {
        "Cobblers": "goblet",
        "Cocktails": "cocktail",
        "Collins": "highball",
        "Coolers": "highball",
        "Cups": "wine",
        "Daisys": "goblet",
        "Eggnogs": "goblet",
        "Fizzes": "fizz",
        "Flips": "wine",
        "Frappes": "cocktail",
        "Highballs": "highball",
        "Hot Drinks": "mug",
        "Juleps": "julep",
        "Lemonades": "highball",
        "Miscellaneous": "cocktail",
        "Punches": "punch",
        "Punches—Non-Alcoholic": "punch",
        "Punches-Non-Alcoholic": "punch",
        "Punches Non-Alcoholic": "punch",
        "Rickeys": "highball",
        "Sours": "sour",
        "Slings": "rocks",
        "Smash": "rocks",
        "Toddies": "rocks",
    }
    if section and section in section_map:
        return section_map[section]
    if "fizz" in title_l:
        return "fizz"
    if "sour" in title_l:
        return "sour"
    if "cocktail" in title_l:
        return "cocktail"
    if "punch" in title_l:
        return "punch"
    if "high ball" in title_l or "highball" in title_l:
        return "highball"
    if "cooler" in title_l:
        return "highball"
    if "rickey" in title_l:
        return "highball"
    if "toddy" in title_l:
        return "rocks"
    if "julep" in title_l:
        return "julep"
    if "smash" in title_l:
        return "rocks"
    if "sling" in title_l:
        return "rocks"
    if "flip" in title_l:
        return "wine"
    if "cobbler" in title_l:
        return "goblet"
    return "cocktail"


def detect_method(title, body, section):
    b = body.lower()
    t = title.lower()
    if "blue blazer" in t or "ignite" in b or "light with match" in b or \
            "set the liquid on fire" in b or "set on fire" in b:
        return "flamed"
    if "hot water" in b or "boiling water" in b or "fill with hot" in b or \
            (section and section.lower() == "hot drinks"):
        return "built_hot"
    if "frapp" in b or "frapp" in t:
        return "shaken"
    if "shake" in b or "shaker" in b:
        return "shaken"
    if "stir" in b:
        return "stirred"
    if "float" in b and "fill" not in b:
        return "layered"
    if "pousse caf" in t:
        return "layered"
    if "fill glass" in b or "fill up" in b:
        return "built"
    return "built"


def detect_garnish(body, title):
    b = body.lower()
    found = []
    if "nutmeg" in b:
        found.append("grated nutmeg")
    if "lemon peel" in b or "twist lemon" in b or "lemon rind" in b or "rind of lemon" in b:
        found.append("lemon peel")
    if "orange peel" in b or "rind of orange" in b or "orange rind" in b:
        found.append("orange peel")
    if "mint" in b and ("sprig" in b or "bunch" in b or "leaves" in b or "on top" in b):
        found.append("mint sprig")
    if "cherry" in b or "cherries" in b:
        found.append("cherry")
    if "olive" in b:
        found.append("olive")
    if "pineapple" in b and ("slice" in b or "piece" in b):
        found.append("pineapple")
    if "slice of orange" in b or "slice orange" in b:
        if "orange peel" not in [f for f in found]:
            found.append("orange slice")
    if "slice of lemon" in b or "slice lemon" in b:
        if "lemon peel" not in [f for f in found]:
            found.append("lemon slice")
    if "dress with fruit" in b and not found:
        found.append("seasonal fruit")
    if not found:
        return None
    return ", ".join(found[:2])


ALCOHOL_KEYWORDS = [
    "whiskey", "whisky", "rum", "gin", "brandy", "vermouth", "champagne",
    "port", "sherry", "claret", "cognac", "absinthe", "bitters", "liqueur",
    "wine", "ale", "porter", "stout", "kummel", "kuemmel", "chartreuse",
    "benedictine", "curacao", "curasao", "maraschino", "anisette", "applejack",
    "apple jack", "apple brandy", "calisaya", "byrrh", "dubonnet", "fernet",
    "amer pi", "kirsch", "kirschwasser", "creme de", "crème de",
    "creme yvette", "crème yvette", "creme de menthe", "creme de cacao",
    "sloe gin", "tom gin", "old tom", "rye", "bourbon", "scotch", "irish",
    "blackberry brandy", "peach brandy", "apricot brandy", "raspberry",
    "grenadine", "moselle", "rhine wine", "burgundy", "madeira", "tokay",
    "champagn", "calamus", "arrack", "creme de rose", "parfait amour",
    "eau de vie", "creme de vanilla", "creme de cassis", "creme decassis",
    "white mint", "green mint", "celery bitters", "angostura", "peychaud",
    "orange bitters", "boker", "amer pigon", "amer picon", "byrrh wine",
    "vermouth",
]


def has_alcohol(body, title, section):
    text = (title + " " + body).lower()
    if section and section.lower() in (
        "punches—non-alcoholic", "punches-non-alcoholic",
        "punches non-alcoholic",
    ):
        return False
    for k in ALCOHOL_KEYWORDS:
        if k in text:
            return True
    # Cross-references to other (clearly alcoholic) drinks.
    cross_refs = [
        "martini", "manhattan", "bronx", "duchess", "rossington",
        "perfect cocktail", "colonial cocktail", "old fashion",
        "same as gin cocktail", "gin fizz", "gin rickey",
        "irish whiskey high ball",
    ]
    for k in cross_refs:
        if k in text:
            return True
    return False


def is_alcoholic_recipe(body, title, section):
    if has_alcohol(body, title, section):
        return 1
    return 0


def estimate_abv(body, title, section, alcoholic):
    if not alcoholic:
        return 0
    t_low = title.lower()
    b = body.lower()
    sec = (section or "").lower()

    if "pousse caf" in t_low or "scaffa" in t_low:
        return 35
    if "cocktail" in t_low and "champagne" in b:
        return 12
    if sec == "cocktails":
        if "egg" in b or "white of" in b or "yolk" in b or "cream" in b:
            return 18
        return 26
    if sec == "fizzes":
        return 12
    if sec == "sours":
        return 14
    if sec == "highballs":
        return 14
    if sec == "rickeys":
        return 12
    if sec == "coolers":
        return 10
    if sec == "collins":
        return 10
    if sec == "lemonades":
        if "whiskey" in b or "claret" in b:
            return 8
        if "bitters" in b or "angostura" in b:
            return 2
        return 0
    if sec == "punches":
        if "gallon" in b or "bowl" in b or "quarts" in b:
            return 12
        return 14
    if sec == "punches—non-alcoholic" or sec == "punches-non-alcoholic":
        return 0
    if sec == "hot drinks":
        return 16
    if sec == "toddies":
        return 28
    if sec == "slings":
        return 28
    if sec == "smash":
        return 28
    if sec == "juleps":
        return 24
    if sec == "flips":
        return 14
    if sec == "eggnogs":
        return 12
    if sec == "frappes":
        return 30
    if sec == "cobblers":
        return 12
    if sec == "cups":
        return 12
    if sec == "daisys":
        return 16
    if "cocktail" in t_low:
        return 24
    return 18


def has_raw_egg(body):
    b = body.lower()
    if "egg" not in b:
        return False
    # exclude cooked
    if "boil" in b or "hot" in b or "cooked" in b:
        return False
    return True


def parse_instructions(body):
    text = re.sub(r"\s+", " ", body).strip()
    parts = re.split(r"(?<=[\.;:])\s+", text)
    return [p.strip() for p in parts if p.strip()]


INGREDIENT_RE = re.compile(
    r"^("
    r"\d+[/\-\d]*\s*(jiggers?|pony|ponies|ponys|pints?|quarts?|"
    r"teaspoonfuls?|tablespoonfuls?|barspoons?|barspoonfuls?|wineglass(?:es)?|"
    r"dashes?|drops?|lumps?|bottles?|slices?|sprigs?|pieces?|cans?|cubes?|"
    r"glasses?|mugs?|gallons?|small\s+|large\s+|whole\s+|fresh\s+|long\s+|"
    r"split|splits|spoons?|spoonfuls?|big\s+|cups?|"
    r"oranges?|lemons?|limes?|peaches?|eggs?|sugar|"
    r"yolks?|whites?|rinds?)|"
    r"juice\s+of|white\s+of|yolk\s+of|whites\s+of|yolks\s+of|"
    r"the\s+white|the\s+yolk|peel|peeling|"
    r"\d+\s+\w|"
    r"one\s+|two\s+|three\s+|four\s+|five\s+|six\s+|seven\s+|eight\s+|nine\s+|ten\s+|"
    r"\d+/\d+\s+\w|"
    r"½\s+|¼\s+|¾\s+|"
    r"y\s*\d|"
    r"v\s*\d|vs\s+|vi\s+|"
    r"%\s+|"
    r"piece\s+|rind\s+|crush\s+\d|fill\s+goblet|fill\s+glass|fill\s+with"
    r")",
    re.IGNORECASE,
)

INSTR_PREFIX_RE = re.compile(
    r"^(stir|shake|fill|pour|serve|strain|use|drop|place|add|mix|crush|"
    r"into|in\s+a|in\s+old|grate|twist|ignite|set|bottle|cork|beat|cover|then|"
    r"before|when|raise|cut|dress|decorate|ornament|substitute|frapp|"
    r"the\s+|it\s+|if\s+|to\s+|out\s+of|now|next|put|leave|"
    r"break|float|bunch\s+of|sweeten|let|same\s+as|use\s+|see\s+that|"
    r"do\s+not|carefully|rub\s+|dip\s+|prepare|allow\s+|then\s+|after\s+|"
    r"orange\s+peel\s+on\s+top|lemon\s+peel\s+on\s+top|lemon\s+peel\s*\.|"
    r"orange\s+peel\s*\.|olive\s*\.|cherry\s*\.|fruit\s*\.|fruits?\s*\.|"
    r"sugar\s+to\s+taste|nutmeg|grated\s+nutmeg|fine\s+ice)",
    re.IGNORECASE,
)


def extract_ingredients(body):
    lines = [l.strip() for l in body.split("\n") if l.strip()]
    ings = []
    for ln in lines:
        if INSTR_PREFIX_RE.match(ln):
            continue
        if ln.startswith("(") and ln.endswith(")"):
            continue
        # Skip bare attribution lines
        if ln.lower().startswith("same as"):
            continue
        if INGREDIENT_RE.match(ln):
            ings.append(ln.rstrip("."))
    out = []
    for x in ings:
        if not out or out[-1] != x:
            out.append(x)
    return out


def make_description(title, body, section):
    t = title.lower()
    sec = (section or "").lower()
    if "pendennis" in t:
        return "Signature drink of the Pendennis Club, where Straub served as head steward."
    if "blackstone" in t:
        return "House cocktail of Chicago's Blackstone Hotel, where Straub was wine steward."
    if "waldorf" in t:
        return "Waldorf-Astoria-style mixed drink, recorded in Straub's 1914 manual."
    if "fizz" in t:
        return "Effervescent fizz from Straub's 1914 manual of pre-Prohibition drinks."
    if "sour" in t:
        return "Classic sour from Jacques Straub's 1914 \"Drinks.\""
    if "cocktail" in t:
        return "Pre-Prohibition cocktail from Jacques Straub's 1914 \"Drinks.\""
    if "julep" in t:
        return "Iced julep from Straub's 1914 manual, a tradition of the Kentucky bar."
    if "highball" in t or "high ball" in t:
        return "Tall highball from Straub's 1914 \"Drinks.\""
    if "cobbler" in t:
        return "Iced wine cobbler from Straub's 1914 manual."
    if "rickey" in t:
        return "Lime rickey from Straub's 1914 \"Drinks.\""
    if "cooler" in t:
        return "Long cooler from Straub's 1914 manual."
    if "flip" in t:
        return "Egg flip from Straub's 1914 \"Drinks.\""
    if "eggnog" in t or "egg nogg" in t:
        return "Eggnog from Straub's 1914 manual."
    if "punch" in t:
        if "non-alcoholic" in sec or "gallon" in body.lower():
            return "Bowl-style punch from Straub's 1914 manual."
        return "Single-serving punch from Straub's 1914 \"Drinks.\""
    if "toddy" in t:
        return "Hot toddy from Straub's 1914 manual."
    if "smash" in t:
        return "Mint smash from Straub's 1914 \"Drinks.\""
    if "sling" in t:
        return "Sling from Straub's 1914 manual."
    if "pousse" in t or "scaffa" in t:
        return "Layered after-dinner cordial from Straub's 1914 manual."
    if "frapp" in t:
        return "Iced cordial frappe from Straub's 1914 manual."
    if "cup" in t:
        return "Wine cup from Straub's 1914 \"Drinks.\""
    if "lemonade" in t:
        return "Lemonade from Straub's 1914 \"Drinks.\""
    return "Pre-Prohibition drink from Jacques Straub's 1914 \"Drinks.\""


def estimate_servings(title, body):
    txt = title + " " + body
    m = re.search(r"for\s+(\d+)\s+(?:people|persons)", txt, re.IGNORECASE)
    if m:
        return int(m.group(1))
    m2 = re.search(r"\(For\s+Two\s*(?:Persons)?\)", title, re.IGNORECASE)
    if m2:
        return 2
    m3 = re.search(r"party\s+of\s+(\d+)", txt, re.IGNORECASE)
    if m3:
        return int(m3.group(1))
    if "gallon" in txt.lower():
        return 24
    if "punch bowl" in txt.lower() or "bowl" in title.lower():
        return 16
    if re.search(r"\bquarts?\b", txt, re.IGNORECASE) and "punch" in title.lower():
        return 12
    return 1


def estimate_prep(title, body, section):
    t = title.lower()
    sec = (section or "").lower()
    if "pousse caf" in t or "scaffa" in t:
        return 5
    if "blue blazer" in t:
        return 6
    if sec == "punches" and ("gallon" in body.lower() or "quart" in body.lower()):
        return 20
    if sec == "hot drinks":
        return 5
    if sec == "cups":
        return 8
    if "tom and jerry" in t:
        return 15
    return 3


def estimate_cook(title, body, section):
    t = title.lower()
    b = body.lower()
    sec = (section or "").lower()
    if "blue blazer" in t or "ignite" in b or "set on fire" in b or \
            "set the liquid on fire" in b or "light with match" in b:
        return 2
    if "boil" in b or "warm and stir" in b or "heat" in b:
        return 5
    if sec == "hot drinks":
        return 3
    return 0


# ----------------------------------------------------------------------------
# Filter unwanted recipes
# ----------------------------------------------------------------------------

# Skip these recipe titles entirely (etiquette / non-recipe blocks / placeholders)
SKIP_TITLES = set()

# Whole sections to skip (none — punches single-serving included; bowl punches
# counted but kept; non-alcoholic punches included as "highball" non-alcoholic).
SKIP_SECTIONS = set()


def should_skip(title, body, section):
    if title.upper() in SKIP_TITLES:
        return True
    # Discard partial / too-short bodies
    if not body or len(body) < 12:
        return True
    # Discard wine-list type entries (these appear in the front matter, but
    # extract_recipes already starts at COBBLERS, so this is a safety net.)
    return False


# ----------------------------------------------------------------------------
# Build NDJSON record
# ----------------------------------------------------------------------------

def make_record(title, body, section):
    alc = is_alcoholic_recipe(body, title, section)
    glass = detect_glass(title, body, section)
    method = detect_method(title, body, section)
    garnish = detect_garnish(body, title)
    abv = estimate_abv(body, title, section, alc)
    raw_egg = has_raw_egg(body)
    instructions = parse_instructions(body)
    ingredients = extract_ingredients(body)

    record = {
        "title": smart_title(title),
        "content_type": "cocktail",
        "is_alcoholic": alc,
        "is_historic": 1,
        "source_book": "Drinks (Jacques Straub, 1914)",
        "source_year": 1914,
        "source_region": "Chicago / Kentucky, USA",
        "contributor_name": "Jacques Straub",
        "contributor_story": CONTRIBUTOR_STORY,
        "cuisine": "cocktail",
        "description": make_description(title, body, section),
        "servings": estimate_servings(title, body),
        "prep_minutes": estimate_prep(title, body, section),
        "cook_minutes": estimate_cook(title, body, section),
        "original_text": f"{title}\n\n{body}",
        "modernized_text": "",
        "instructions": instructions,
        "ingredients": ingredients,
        "glass_type": glass,
        "method": method,
        "garnish": garnish,
        "abv_percent": abv,
    }
    record["safety_notes"] = (
        "Original uses raw egg; substitute pasteurized egg for modern preparation."
        if raw_egg else None
    )
    return record


# ----------------------------------------------------------------------------
# Main
# ----------------------------------------------------------------------------

def main():
    text = fetch_book()
    raw_recipes = extract_recipes(text)
    print(f"Detected {len(raw_recipes)} candidate blocks.")

    records = []
    skipped = []
    seen_titles = set()
    for rr in raw_recipes:
        title = rr["title"]
        body = rr["body"]
        section = rr["section"]
        if should_skip(title, body, section):
            skipped.append(title)
            continue
        # Dedupe by smart-titled name + first 40 chars of body
        key = (smart_title(title), body[:40])
        if key in seen_titles:
            skipped.append(f"DUP:{title}")
            continue
        seen_titles.add(key)
        records.append(make_record(title, body, section))

    with open(OUT_PATH, "w", encoding="utf-8") as f:
        for rec in records:
            f.write(json.dumps(rec, ensure_ascii=False))
            f.write("\n")

    print(f"Wrote {len(records)} recipes to {OUT_PATH}")
    if skipped:
        print(f"Skipped {len(skipped)} blocks (samples: {skipped[:10]})")
    # Print a few notable hits
    notable_keys = ["pendennis", "blackstone", "waldorf", "aviation", "bronx",
                    "manhattan", "martini", "sazerac", "brooklyn", "old fashion"]
    print("Notable captures:")
    for rec in records:
        for k in notable_keys:
            if k in rec["title"].lower():
                print(f"  - {rec['title']}  [{rec['glass_type']}/{rec['method']}, ABV {rec['abv_percent']}]")
                break


if __name__ == "__main__":
    main()

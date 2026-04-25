#!/usr/bin/env python3
"""
Wikipedia cocktail harvester for pan-tree Mixology tab.

Enumerates cocktail-related categories on en.wikipedia.org, fetches each
article's wikitext, parses {{Infobox drink}} or {{Infobox cocktail}},
normalizes ingredients/instructions/glass/garnish/abv, and writes one JSON
object per line to ./wikipedia.ndjson.

License: Wikipedia text is CC-BY-SA. Attribution: Wikipedia (CC-BY-SA).
"""

from __future__ import annotations

import json
import os
import re
import sys
import time
from pathlib import Path
from typing import Any

import requests

OUT_PATH = Path(__file__).parent / "wikipedia.ndjson" if "__file__" in globals() else Path(
    "C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/backend/ingest/cocktails/wikipedia.ndjson"
)
WIKI_API = "https://en.wikipedia.org/w/api.php"
HEADERS = {"User-Agent": "pan-tree-cocktail-harvester (schulgenkyle@gmail.com)"}

# Seed categories. The harvester recurses through subcategories.
SEED_CATEGORIES = [
    "Category:Cocktails",
    "Category:IBA official cocktails",
    "Category:Cocktails by ingredient",
    "Category:Cocktails by flavor",
    "Category:Bubbly cocktails",
    "Category:Cuban cocktails",
    "Category:New Orleans cocktails",
    "Category:Three-ingredient cocktails",
    "Category:Tiki drinks",
    "Category:Two-ingredient cocktails",
    "Category:Cocktail stubs",
    "Category:Cocktails served with a salty rim",
    "Category:Flaming drinks",
]

# Categories we should NOT recurse into (they contain non-cocktails or noise)
SKIP_CATEGORIES = {
    "Category:Cocktail garnishes",
    "Category:Lists of cocktails",
    "Category:Cocktail books",
    "Category:Drink mixers",
    "Category:Cocktail glass",  # not a real cat but defensive
}

# Page titles to skip - lists, glasses, brands, generic concepts
SKIP_TITLE_PATTERNS = [
    r"^List of ",
    r"^Lists of ",
    r"\bglass\b",  # cocktail glass, highball glass etc
    r"\bgarnish\b",
    r"^Cocktail$",
    r"^Cocktail party",
    r"^Cocktail dress",
    r"^Cocktail shaker",
    r"^Cocktail strainer",
    r"^Bartender",
    r"^Mixology",
    r"^Mixed drink",
    r"^Speakeasy",
    r"^Liqueur",
    r"^Liquor",
    r"^Spirits$",
    r"^Distilled",
    r"^Bitters$",
    r"^Vermouth$",
    r"^Bathtub Gin",  # speakeasy
    r"Brewing Company",
    r"Bar$",  # Harry's New York Bar
    r"^IBA Tiki$",
    r"^Drink ",
    r"^Cerveza preparada",  # generic style
    r"^Shrub \(drink\)",
    r"^Signature drink",
    r"^Frozen \(alcoholic",
    r"^Bomb shot",
    r"^Shot \(drink\)",
    r"^Highball$",
    r"^Lanique",  # liqueur brand
    r"^BuzzBallz",  # brand
    r"^Bushwacker",  # generic family - actually keep this one. exclude only if no infobox
]
SKIP_TITLE_RE = re.compile("|".join(SKIP_TITLE_PATTERNS), re.IGNORECASE)

# Glass normalization
GLASS_MAP = {
    "old fashioned glass": "rocks",
    "old-fashioned glass": "rocks",
    "rocks glass": "rocks",
    "double old fashioned": "rocks",
    "lowball": "rocks",
    "tumbler": "rocks",
    "highball glass": "highball",
    "collins glass": "collins",
    "cocktail glass": "martini",
    "martini glass": "martini",
    "coupe": "coupe",
    "coupe glass": "coupe",
    "champagne coupe": "coupe",
    "champagne flute": "flute",
    "flute": "flute",
    "champagne glass": "flute",
    "shot glass": "shot",
    "hurricane glass": "hurricane",
    "hurricane": "hurricane",
    "tiki mug": "tiki",
    "mug": "mug",
    "irish coffee glass": "mug",
    "wine glass": "wine",
    "white wine glass": "wine",
    "red wine glass": "wine",
    "margarita glass": "margarita",
    "snifter": "snifter",
    "brandy snifter": "snifter",
    "brandy balloon": "snifter",
    "pint glass": "pint",
    "beer glass": "pint",
    "beer mug": "pint",
    "julep cup": "julep",
    "punch bowl": "punch",
    "moscow mule mug": "mug",
    "copper mug": "mug",
    "irish coffee mug": "mug",
    "pousse cafe glass": "shot",
    "shooter": "shot",
    "sour glass": "coupe",
    "nick and nora": "nick-and-nora",
    "nick & nora": "nick-and-nora",
}

# Approximate ABVs for common spirits (% alcohol by volume)
SPIRIT_ABV = {
    "gin": 40, "vodka": 40, "rum": 40, "white rum": 40, "light rum": 40,
    "dark rum": 40, "gold rum": 40, "spiced rum": 40, "aged rum": 40,
    "demerara rum": 45, "navy rum": 54, "overproof rum": 63,
    "tequila": 40, "blanco tequila": 40, "reposado tequila": 40,
    "anejo tequila": 40, "añejo tequila": 40, "mezcal": 45,
    "whisky": 40, "whiskey": 40, "bourbon": 40, "bourbon whiskey": 40,
    "rye": 40, "rye whiskey": 40, "scotch": 40, "scotch whisky": 40,
    "irish whiskey": 40, "japanese whisky": 40,
    "brandy": 40, "cognac": 40, "armagnac": 40, "calvados": 40,
    "pisco": 40, "cachaça": 40, "cachaca": 40, "aguardiente": 40,
    "campari": 24, "aperol": 11, "fernet": 39, "fernet branca": 39,
    "amaro": 25, "amaro nonino": 35, "averna": 29, "cynar": 16.5,
    "vermouth": 17, "sweet vermouth": 17, "dry vermouth": 17,
    "red vermouth": 17, "white vermouth": 17, "rosso vermouth": 17,
    "lillet": 17, "lillet blanc": 17, "kina lillet": 17, "cocchi americano": 16.5,
    "dubonnet": 14.8, "byrrh": 18, "punt e mes": 16,
    "absinthe": 60, "pastis": 40, "ouzo": 38, "raki": 45, "sambuca": 38,
    "chartreuse": 55, "green chartreuse": 55, "yellow chartreuse": 40,
    "benedictine": 40, "bénédictine": 40, "drambuie": 40, "galliano": 30,
    "amaretto": 24, "frangelico": 20, "kahlua": 20, "kahlúa": 20,
    "tia maria": 20, "baileys": 17, "irish cream": 17, "rumchata": 13.75,
    "sambuca": 38, "ouzo": 38, "anisette": 25, "pernod": 40,
    "triple sec": 30, "cointreau": 40, "grand marnier": 40, "curaçao": 30,
    "blue curaçao": 30, "curacao": 30, "blue curacao": 30,
    "maraschino liqueur": 32, "luxardo maraschino": 32, "maraschino": 32,
    "creme de cassis": 20, "crème de cassis": 20, "creme de menthe": 25,
    "crème de menthe": 25, "creme de cacao": 25, "crème de cacao": 25,
    "creme de violette": 16, "crème de violette": 16, "creme de mure": 18,
    "crème de mure": 18, "crème de mûre": 18, "creme de mure": 18,
    "creme yvette": 16, "creme de framboise": 18, "crème de framboise": 18,
    "elderflower liqueur": 20, "st-germain": 20, "st germain": 20,
    "pimm's": 25, "pimms": 25, "pimm's no. 1": 25,
    "schnapps": 20, "peach schnapps": 20, "apple schnapps": 20,
    "peppermint schnapps": 30, "peach liqueur": 18, "peachtree": 20,
    "amaretto": 24, "midori": 20, "melon liqueur": 20,
    "champagne": 12, "prosecco": 11, "cava": 11.5, "sparkling wine": 12,
    "sparkling": 12, "white wine": 12, "red wine": 13, "rose wine": 12,
    "rosé wine": 12, "port": 19, "sherry": 17, "fino sherry": 15,
    "manzanilla sherry": 15, "amontillado": 18, "oloroso": 18,
    "pedro ximenez": 17, "pedro ximénez": 17, "madeira": 19, "marsala": 18,
    "ginger wine": 13.5, "sake": 16, "soju": 17,
    "beer": 5, "stout": 6, "lager": 5, "ale": 5, "guinness": 4.2,
    "ginger beer": 0, "ginger ale": 0, "tonic water": 0, "soda water": 0,
    "club soda": 0, "cola": 0, "lemon-lime soda": 0, "sprite": 0,
    "orange juice": 0, "lemon juice": 0, "lime juice": 0, "grapefruit juice": 0,
    "pineapple juice": 0, "cranberry juice": 0, "tomato juice": 0,
    "apple juice": 0, "grape juice": 0, "passion fruit juice": 0,
    "simple syrup": 0, "sugar": 0, "honey": 0, "agave": 0, "agave syrup": 0,
    "grenadine": 0, "orgeat": 0, "falernum": 6, "velvet falernum": 11,
    "bitters": 40, "angostura bitters": 44.7, "peychaud's bitters": 35,
    "orange bitters": 25, "aromatic bitters": 40,
    "cream": 0, "milk": 0, "egg white": 0, "egg yolk": 0, "egg": 0,
    "coconut cream": 0, "cream of coconut": 0, "coconut milk": 0,
    "water": 0, "soda": 0,
}

# ----------------------- HTTP -----------------------
session = requests.Session()
session.headers.update(HEADERS)


def api_get(params: dict) -> dict:
    p = dict(params)
    p["format"] = "json"
    p["formatversion"] = "2"
    for attempt in range(4):
        try:
            r = session.get(WIKI_API, params=p, timeout=30)
            if r.status_code == 200:
                return r.json()
            time.sleep(1.5 * (attempt + 1))
        except requests.RequestException:
            time.sleep(2 * (attempt + 1))
    return {}


# ----------------------- Category enumeration -----------------------

def category_pages(cat: str) -> list[str]:
    titles: list[str] = []
    cont = None
    while True:
        params = {
            "action": "query", "list": "categorymembers",
            "cmtitle": cat, "cmlimit": 500, "cmtype": "page",
        }
        if cont:
            params.update(cont)
        d = api_get(params)
        if not d:
            break
        for m in d.get("query", {}).get("categorymembers", []):
            titles.append(m["title"])
        if "continue" in d:
            cont = d["continue"]
        else:
            break
    return titles


def category_subcats(cat: str) -> list[str]:
    subs: list[str] = []
    cont = None
    while True:
        params = {
            "action": "query", "list": "categorymembers",
            "cmtitle": cat, "cmlimit": 500, "cmtype": "subcat",
        }
        if cont:
            params.update(cont)
        d = api_get(params)
        if not d:
            break
        for m in d.get("query", {}).get("categorymembers", []):
            subs.append(m["title"])
        if "continue" in d:
            cont = d["continue"]
        else:
            break
    return subs


def page_links(title: str) -> list[str]:
    """Get all wiki page (ns=0) links from a page."""
    out: list[str] = []
    cont = None
    while True:
        params = {
            "action": "query", "prop": "links",
            "titles": title, "pllimit": 500, "plnamespace": 0,
        }
        if cont:
            params.update(cont)
        d = api_get(params)
        if not d:
            break
        pages = d.get("query", {}).get("pages", [])
        for p in pages:
            for ln in p.get("links", []):
                out.append(ln["title"])
        if "continue" in d:
            cont = d["continue"]
        else:
            break
    return out


# Pages whose internal wikilinks we should harvest as candidate cocktails
LIST_PAGES = [
    "List of cocktails",
    "List of cocktails (alphabetical)",
    "List of IBA official cocktails",
    "List of cocktails with wine, sparkling wine, or port",
    "Beer cocktail",
    "Wine cocktail",
]

# Keyword filter to keep page titles from list pages that look like cocktails
COCKTAIL_KEYWORDS = re.compile(
    r"\(cocktail\)|"
    r"\b(?:martini|sour|sling|fizz|collins|daisy|flip|toddy|julep|smash|rickey|"
    r"swizzle|punch|spritz|negroni|margarita|mojito|colada|colado|caipirinha|"
    r"mai\s*tai|bloody|gimlet|sidecar|sazerac|paloma|bramble|aviation|tequila|"
    r"manhattan|daiquiri|highball|cooler)\b",
    re.IGNORECASE,
)


def all_categories_with_prefix(prefix: str) -> list[str]:
    """Enumerate every category whose name starts with prefix."""
    out = []
    cont = None
    while True:
        params = {
            "action": "query", "list": "allcategories",
            "acprefix": prefix, "aclimit": 500,
        }
        if cont:
            params.update(cont)
        d = api_get(params)
        if not d:
            break
        for c in d.get("query", {}).get("allcategories", []):
            out.append("Category:" + c["category"])
        if "continue" in d:
            cont = d["continue"]
        else:
            break
    return out


def gather_all_titles() -> list[str]:
    seen_cats: set[str] = set()
    queue = list(SEED_CATEGORIES)
    # Add every "Cocktails*" category as a seed
    for c in all_categories_with_prefix("Cocktails"):
        if c not in queue and c not in SKIP_CATEGORIES:
            queue.append(c)
    # Also add Tiki-related categories
    for prefix in ["Tiki "]:
        for c in all_categories_with_prefix(prefix):
            if c not in queue and c not in SKIP_CATEGORIES:
                queue.append(c)
    titles: set[str] = set()
    while queue:
        cat = queue.pop(0)
        if cat in seen_cats or cat in SKIP_CATEGORIES:
            continue
        seen_cats.add(cat)
        # pages
        for t in category_pages(cat):
            if t.startswith(("Category:", "Template:", "File:", "User:")):
                continue
            titles.add(t)
        # one level of subcats
        for sub in category_subcats(cat):
            if sub not in seen_cats and sub not in SKIP_CATEGORIES:
                queue.append(sub)

    # Also harvest from curated list pages
    for lp in LIST_PAGES:
        for t in page_links(lp):
            if t.startswith(("Category:", "Template:", "File:", "User:", "Wikipedia:", "Help:")):
                continue
            # only keep titles that look cocktail-ish, to avoid pulling in unrelated links
            if "(cocktail)" in t or COCKTAIL_KEYWORDS.search(t):
                titles.add(t)

    return sorted(titles)


# ----------------------- Wikitext fetch -----------------------

def fetch_wikitext(title: str) -> str | None:
    d = api_get({"action": "parse", "page": title, "prop": "wikitext", "redirects": 1})
    try:
        return d["parse"]["wikitext"]
    except (KeyError, TypeError):
        return None


def fetch_wikitext_batch(titles: list[str]) -> dict[str, str]:
    """Fetch wikitext for up to 50 titles via revisions API."""
    if not titles:
        return {}
    out: dict[str, str] = {}
    chunk_size = 30
    for i in range(0, len(titles), chunk_size):
        chunk = titles[i:i + chunk_size]
        d = api_get({
            "action": "query", "prop": "revisions",
            "rvprop": "content", "rvslots": "main",
            "titles": "|".join(chunk),
            "redirects": 1,
        })
        pages = d.get("query", {}).get("pages", [])
        # build redirect map back to requested titles
        norm = {n["from"]: n["to"] for n in d.get("query", {}).get("normalized", [])}
        redir = {r["from"]: r["to"] for r in d.get("query", {}).get("redirects", [])}
        for p in pages:
            t = p.get("title", "")
            if "missing" in p:
                continue
            try:
                wt = p["revisions"][0]["slots"]["main"]["content"]
            except (KeyError, IndexError):
                continue
            out[t] = wt
            # also store under any original requested title that mapped here
        # map original titles -> resolved
        for orig in chunk:
            t = norm.get(orig, orig)
            t = redir.get(t, t)
            if t in out and orig not in out:
                out[orig] = out[t]
    return out


# ----------------------- Wikitext parsing -----------------------

WIKILINK_RE = re.compile(r"\[\[([^\[\]|]+)(?:\|([^\[\]]+))?\]\]")
HTML_TAG_RE = re.compile(r"<[^>]+>")
HTML_ENTITY_MAP_BASIC = {
    "&nbsp;": " ", "&amp;": "&", "&quot;": '"', "&apos;": "'",
    "&lt;": "<", "&gt;": ">", "&ndash;": "-", "&mdash;": "-",
    "&frac12;": "1/2", "&frac14;": "1/4", "&frac34;": "3/4",
    "&deg;": "°", "&times;": "x",
}
REF_RE = re.compile(r"<ref[^>]*?(/>|>.*?</ref>)", re.IGNORECASE | re.DOTALL)
COMMENT_RE = re.compile(r"<!--.*?-->", re.DOTALL)
NOWIKI_RE = re.compile(r"<nowiki>.*?</nowiki>", re.IGNORECASE | re.DOTALL)


def strip_wikitext(s: str) -> str:
    if not s:
        return ""
    s = COMMENT_RE.sub("", s)
    s = REF_RE.sub("", s)
    s = NOWIKI_RE.sub("", s)
    # convert [[A|B]] -> B, [[A]] -> A
    s = WIKILINK_RE.sub(lambda m: (m.group(2) or m.group(1)), s)
    # strip remaining {{...}} templates conservatively (after we extracted what we need)
    # Replace {{convert|X|unit|...}} -> "X unit" before generic strip
    s = re.sub(r"\{\{[Cc]onvert\|([^|}]+)\|([^|}]+)(?:\|[^}]*)?\}\}",
               lambda m: f"{m.group(1).strip()} {m.group(2).strip()}", s)
    s = re.sub(r"\{\{[Ff]rac\|(\d+)\|(\d+)\|(\d+)\}\}",
               lambda m: f"{m.group(1)} {m.group(2)}/{m.group(3)}", s)
    s = re.sub(r"\{\{[Ff]rac\|(\d+)\|(\d+)\}\}",
               lambda m: f"{m.group(1)}/{m.group(2)}", s)
    s = re.sub(r"\{\{nbsp\}\}", " ", s, flags=re.I)
    s = re.sub(r"\{\{[^{}]*\}\}", "", s)
    s = re.sub(r"\{\{[^{}]*\}\}", "", s)  # second pass for nested
    s = HTML_TAG_RE.sub("", s)
    s = s.replace("'''", "").replace("''", "")
    # decode common html entities
    for k, v in HTML_ENTITY_MAP_BASIC.items():
        s = s.replace(k, v)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def find_infobox(wikitext: str) -> str | None:
    """Return the substring of the {{Infobox drink|cocktail}} template, or None."""
    m = re.search(r"\{\{\s*[Ii]nfobox\s+(?:drink|cocktail)\b", wikitext)
    if not m:
        return None
    start = m.start()
    # walk to find matching closing braces
    depth = 0
    i = start
    while i < len(wikitext):
        if wikitext[i:i + 2] == "{{":
            depth += 1
            i += 2
        elif wikitext[i:i + 2] == "}}":
            depth -= 1
            i += 2
            if depth == 0:
                return wikitext[start:i]
        else:
            i += 1
    return None


def parse_infobox_fields(infobox: str) -> dict[str, str]:
    """Parse Infobox into field -> raw value (wikitext, not stripped)."""
    # Strip outer {{ }}
    body = infobox.strip()
    if body.startswith("{{"):
        body = body[2:]
    if body.endswith("}}"):
        body = body[:-2]
    # Split by top-level pipes (depth-aware)
    parts = []
    depth = 0
    cur = []
    for i, ch in enumerate(body):
        if ch == "{" and i + 1 < len(body) and body[i + 1] == "{":
            depth += 1
        elif ch == "}" and i + 1 < len(body) and body[i + 1] == "}":
            depth -= 1
        if ch == "[" and i + 1 < len(body) and body[i + 1] == "[":
            depth += 1
        elif ch == "]" and i + 1 < len(body) and body[i + 1] == "]":
            depth -= 1
        if ch == "|" and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    parts.append("".join(cur))
    # First part is template name
    fields: dict[str, str] = {}
    for p in parts[1:]:
        if "=" not in p:
            continue
        k, _, v = p.partition("=")
        fields[k.strip().lower()] = v.strip()
    return fields


# ----------------------- Ingredient parsing -----------------------

UNIT_PATTERNS = [
    (r"(?:milliliters?|millilitres?|ml|mL)\b", "ml"),
    (r"(?:centiliters?|centilitres?|cl|cL)\b", "cl"),
    (r"(?:fluid\s*ounces?|fl\.?\s*oz\.?|oz\.?|ounces?)\b", "oz"),
    (r"(?:tablespoons?|tbsp\.?|tbs\.?)\b", "tbsp"),
    (r"(?:teaspoons?|tsp\.?)\b", "tsp"),
    (r"dash(?:es)?\b", "dash"),
    (r"drops?\b", "drop"),
    (r"splash(?:es)?\b", "splash"),
    (r"(?:barspoons?|bar\s*spoons?)\b", "barspoon"),
    (r"cups?\b", "cup"),
    (r"(?:liters?|litres?|L)\b", "l"),
    (r"parts?\b", "part"),
    (r"(?:pieces?|pcs?)\b", "piece"),
    (r"slices?\b", "slice"),
    (r"wedges?\b", "wedge"),
    (r"sprigs?\b", "sprig"),
    (r"(?:leaves|leaf)\b", "leaf"),
    (r"cubes?\b", "cube"),
]

# fraction unicode
FRAC_MAP = {
    "½": "1/2", "¼": "1/4", "¾": "3/4", "⅓": "1/3", "⅔": "2/3",
    "⅛": "1/8", "⅜": "3/8", "⅝": "5/8", "⅞": "7/8",
}


def parse_quantity(s: str) -> tuple[float | None, str]:
    """Return (qty, remaining string)."""
    s = s.strip()
    for k, v in FRAC_MAP.items():
        s = s.replace(k, " " + v)
    s = s.strip()
    # mixed number "1 1/2"
    m = re.match(r"^(\d+)\s+(\d+)\s*/\s*(\d+)\b\s*(.*)", s)
    if m:
        whole, num, den, rest = m.groups()
        try:
            return float(whole) + float(num) / float(den), rest
        except ZeroDivisionError:
            return None, s
    # plain fraction "1/2"
    m = re.match(r"^(\d+)\s*/\s*(\d+)\b\s*(.*)", s)
    if m:
        num, den, rest = m.groups()
        try:
            return float(num) / float(den), rest
        except ZeroDivisionError:
            return None, s
    # decimal or integer
    m = re.match(r"^(\d+(?:\.\d+)?)\s*(.*)", s)
    if m:
        return float(m.group(1)), m.group(2)
    return None, s


def parse_unit(s: str) -> tuple[str | None, str]:
    s = s.strip()
    for pat, unit in UNIT_PATTERNS:
        m = re.match(pat, s, re.IGNORECASE)
        if m:
            return unit, s[m.end():].strip()
    return None, s


def ml_to_oz(ml: float) -> float:
    return round(ml / 29.5735, 2)


def cl_to_oz(cl: float) -> float:
    return round((cl * 10) / 29.5735, 2)


# Aisle hints
def aisle_for(name: str) -> str:
    n = name.lower()
    if any(w in n for w in [
        "gin", "vodka", "rum", "tequila", "whisky", "whiskey", "bourbon", "rye",
        "scotch", "brandy", "cognac", "pisco", "cachaça", "cachaca", "mezcal",
        "campari", "aperol", "fernet", "vermouth", "lillet", "amaro", "amari",
        "absinthe", "pastis", "chartreuse", "benedictine", "bénédictine", "drambuie",
        "galliano", "kahlua", "kahlúa", "baileys", "amaretto", "frangelico",
        "triple sec", "cointreau", "grand marnier", "curaçao", "curacao",
        "maraschino", "creme de", "crème de", "schnapps", "midori", "pimm",
        "champagne", "prosecco", "cava", "sparkling wine", "sherry", "port",
        "madeira", "marsala", "sake", "soju", "wine", "beer", "stout",
        "lager", "ale", "ouzo", "sambuca", "anisette", "pernod", "raki",
        "cynar", "averna", "bitters", "punsch", "falernum", "velvet falernum",
        "byrrh", "dubonnet", "punt e mes", "cocchi", "kina", "elderflower liqueur",
        "st-germain", "st germain", "rumchata", "tia maria", "irish cream",
        "liqueur", "liquor", "spirit", "aguardiente",
    ]):
        return "bar"
    if any(w in n for w in ["juice", "soda", "water", "tonic", "ginger beer",
                            "ginger ale", "cola", "sprite", "lemonade",
                            "syrup", "grenadine", "orgeat", "honey", "agave",
                            "sugar", "milk", "cream", "egg", "coconut",
                            "coffee", "tea", "salt", "pepper", "nutmeg",
                            "cinnamon", "mint", "basil", "rosemary", "thyme",
                            "lime", "lemon", "orange", "grapefruit",
                            "pineapple", "cherry", "berry", "fruit", "ice"]):
        if any(w in n for w in ["milk", "cream", "egg", "yogurt"]):
            return "dairy"
        if "ice" == n or n == "crushed ice":
            return "frozen"
        if any(w in n for w in ["lime", "lemon", "orange", "grapefruit",
                                "pineapple", "cherry", "berry", "mint", "basil"]):
            return "produce"
        if any(w in n for w in ["sugar", "honey", "syrup", "salt", "pepper",
                                "nutmeg", "cinnamon", "grenadine", "orgeat"]):
            return "condiment"
        return "beverage"
    return "bar"


def decode_entities(s: str) -> str:
    if not s:
        return s
    for k, v in HTML_ENTITY_MAP_BASIC.items():
        s = s.replace(k, v)
    return s


def parse_ingredient_line(line: str) -> dict | None:
    raw = line.strip().lstrip("*•·-").strip()
    if not raw:
        return None
    raw = strip_wikitext(raw)
    raw = decode_entities(raw)
    if not raw:
        return None
    if len(raw) > 200:
        # likely a paragraph, not an ingredient
        return None
    # Drop trailing "(garnish)" notes etc captured as note
    note = ""
    m = re.search(r"\(([^()]+)\)\s*$", raw)
    if m:
        note = m.group(1).strip()
        raw = raw[:m.start()].strip()
    # parse qty
    qty, rest = parse_quantity(raw)
    unit, rest = parse_unit(rest)
    name = rest.strip(" ,;.-").lower()
    # name cleanup: strip trailing "of"
    name = re.sub(r"^of\s+", "", name)
    if not name:
        return None
    # convert ml/cl -> oz
    if unit == "ml" and qty:
        qty = ml_to_oz(qty)
        unit = "oz"
    elif unit == "cl" and qty:
        qty = cl_to_oz(qty)
        unit = "oz"
    elif unit == "l" and qty:
        qty = ml_to_oz(qty * 1000)
        unit = "oz"
    out = {"name": name, "quantity": qty, "unit": unit, "aisle": aisle_for(name)}
    if note:
        out["note"] = note
    return out


def parse_plainlist(text: str) -> list[str]:
    """Pull line items from a {{Plainlist|...}} or bulleted list."""
    # If it contains a Plainlist template, extract its content
    m = re.search(r"\{\{\s*[Pp]lainlist\s*\|(.*?)\}\}", text, re.DOTALL)
    body = m.group(1) if m else text
    # split lines
    items = []
    for ln in body.splitlines():
        ln = ln.strip()
        if not ln:
            continue
        if ln.startswith("*") or ln.startswith("•"):
            items.append(ln.lstrip("*•").strip())
        elif ln and not ln.startswith("|") and not ln.startswith("{{"):
            items.append(ln)
    return items


def parse_ingredients_field(text: str) -> list[dict]:
    if not text:
        return []
    # If there's a {{Plainlist|...}} pull lines
    items = parse_plainlist(text)
    if not items:
        # Maybe simply a comma-separated list
        stripped = strip_wikitext(text)
        if stripped:
            items = [p.strip() for p in re.split(r",|;", stripped) if p.strip()]
    out: list[dict] = []
    for ln in items:
        ing = parse_ingredient_line(ln)
        if ing:
            out.append(ing)
    return out


# ----------------------- Glass / method / garnish / ABV -----------------------

def normalize_glass(text: str) -> str | None:
    if not text:
        return None
    t = strip_wikitext(text).lower().strip().rstrip(".")
    if not t:
        return None
    for k, v in GLASS_MAP.items():
        if k in t:
            return v
    # fallback: first word match
    if "rocks" in t or "old fashion" in t or "tumbler" in t:
        return "rocks"
    if "highball" in t:
        return "highball"
    if "collins" in t:
        return "collins"
    if "martini" in t or "cocktail" in t:
        return "martini"
    if "coupe" in t:
        return "coupe"
    if "flute" in t:
        return "flute"
    if "hurricane" in t:
        return "hurricane"
    if "tiki" in t:
        return "tiki"
    if "mug" in t:
        return "mug"
    if "wine" in t:
        return "wine"
    return None


def detect_method(prep: str) -> str | None:
    if not prep:
        return None
    t = prep.lower()
    if "shake" in t or "shaken" in t:
        return "shaken"
    if "stir" in t:
        return "stirred"
    if "blend" in t:
        return "blended"
    if "build" in t or "pour" in t and "ice" in t:
        return "built"
    if "muddle" in t:
        return "muddled"
    if "layer" in t:
        return "layered"
    return None


def split_instructions(prep: str) -> list[str]:
    if not prep:
        return []
    t = strip_wikitext(prep)
    if not t:
        return []
    # split on sentence boundaries / semicolons
    parts = re.split(r"(?<=[.;])\s+|\.\s*$", t)
    out = []
    for p in parts:
        p = p.strip(" .,;")
        if not p:
            continue
        if len(p) < 3:
            continue
        # capitalize first letter
        out.append(p[0].upper() + p[1:])
    return out


def estimate_abv(ingredients: list[dict]) -> int | None:
    """Sum (qty_oz * abv%) / total_qty_oz with a small dilution factor."""
    if not ingredients:
        return None
    total_alc = 0.0
    total_vol = 0.0
    for ing in ingredients:
        qty = ing.get("quantity")
        unit = ing.get("unit")
        if qty is None or unit not in {"oz", "part", "barspoon", "dash", "tsp", "tbsp", "splash"}:
            continue
        # standardize to oz-ish for ratio
        if unit == "oz":
            vol = float(qty)
        elif unit == "part":
            vol = float(qty)
        elif unit == "barspoon":
            vol = float(qty) * 0.17
        elif unit == "dash":
            vol = float(qty) * 0.03
        elif unit == "splash":
            vol = float(qty) * 0.25
        elif unit == "tsp":
            vol = float(qty) * 0.17
        elif unit == "tbsp":
            vol = float(qty) * 0.5
        else:
            continue
        name = (ing.get("name") or "").lower()
        # find best matching spirit
        abv = None
        # try longest-substring match
        candidates = [k for k in SPIRIT_ABV if k in name]
        if candidates:
            candidates.sort(key=len, reverse=True)
            abv = SPIRIT_ABV[candidates[0]]
        else:
            # default: if we don't know, assume 0 (mixer)
            abv = 0
        total_vol += vol
        total_alc += vol * abv / 100.0
    if total_vol <= 0:
        return None
    raw = total_alc / total_vol  # fractional
    # dilution from ice/shaking ~ 25% extra volume (so alc drops ~20%)
    diluted = raw * 0.78
    pct = round(diluted * 100)
    if pct < 0 or pct > 80:
        return None
    return pct


# ----------------------- Description / region -----------------------

def _skip_leading_templates_and_blanks(txt: str) -> str:
    """Skip leading {{...}} templates (depth-aware) and blank lines."""
    i = 0
    n = len(txt)
    while i < n:
        # skip whitespace / blank lines
        while i < n and txt[i] in " \t\r\n":
            i += 1
        if i >= n:
            break
        if txt[i:i + 2] == "{{":
            depth = 1
            j = i + 2
            while j < n and depth > 0:
                if txt[j:j + 2] == "{{":
                    depth += 1
                    j += 2
                elif txt[j:j + 2] == "}}":
                    depth -= 1
                    j += 2
                else:
                    j += 1
            if depth == 0:
                i = j
                continue
            else:
                break
        # also skip image/file lines like [[File:...]]
        if txt[i:i + 7].lower() == "[[file:" or txt[i:i + 7].lower() == "[[image:":
            depth = 1
            j = i + 2
            while j < n and depth > 0:
                if txt[j:j + 2] == "[[":
                    depth += 1
                    j += 2
                elif txt[j:j + 2] == "]]":
                    depth -= 1
                    j += 2
                else:
                    j += 1
            i = j
            continue
        break
    return txt[i:]


def extract_lead_paragraph(wikitext: str) -> str | None:
    txt = wikitext
    # strip first infobox
    ib = find_infobox(txt)
    if ib:
        txt = txt.replace(ib, "", 1)
    txt = COMMENT_RE.sub("", txt)
    txt = _skip_leading_templates_and_blanks(txt)
    # take up through first H2 (==)
    m = re.search(r"^==", txt, re.MULTILINE)
    if m:
        txt = txt[:m.start()]
    # take first non-empty paragraph
    paras = [p.strip() for p in txt.split("\n\n") if p.strip()]
    if not paras:
        return None
    lead = paras[0]
    lead = strip_wikitext(lead)
    lead = decode_entities(lead)
    if not lead:
        return None
    if len(lead) > 400:
        cut = lead.find(". ")
        if 60 <= cut <= 400:
            lead = lead[:cut + 1]
        else:
            lead = lead[:400].rsplit(" ", 1)[0] + "..."
    return lead.strip() or None


REGION_HINTS = [
    ("Italy", ["italian", "italy", "milan", "milano", "florence", "venice", "rome", "turin"]),
    ("Cuba", ["cuban", "cuba", "havana"]),
    ("United States", ["new york", "new orleans", "san francisco", "louisiana", "american", "united states", "u.s.", "kentucky", "manhattan"]),
    ("United Kingdom", ["british", "england", "english", "london", "uk ", "united kingdom"]),
    ("France", ["french", "france", "paris"]),
    ("Mexico", ["mexican", "mexico", "tijuana", "guadalajara"]),
    ("Brazil", ["brazilian", "brazil", "rio de janeiro"]),
    ("Spain", ["spanish", "spain", "madrid", "barcelona"]),
    ("Japan", ["japanese", "japan", "tokyo"]),
    ("Germany", ["german", "germany", "berlin"]),
    ("Ireland", ["irish", "ireland", "dublin"]),
    ("Russia", ["russian", "russia", "moscow"]),
    ("Peru", ["peruvian", "peru", "lima"]),
    ("Greece", ["greek", "greece", "athens"]),
    ("Caribbean", ["caribbean", "trinidad", "jamaica", "barbados", "puerto rico", "bahamas"]),
    ("Polynesia", ["tiki", "polynesian", "polynesia", "hawaii", "tahiti"]),
]


def guess_region(lead: str | None) -> str | None:
    if not lead:
        return None
    t = lead.lower()
    for region, kws in REGION_HINTS:
        for kw in kws:
            if kw in t:
                return region
    return None


# ----------------------- Skip checks -----------------------

NON_COCKTAIL_TITLES = {
    "Cocktail", "Cocktail garnish", "Cocktail glass", "Cocktail shaker",
    "Cocktail strainer", "Cocktail party", "Cocktail dress", "Cocktail stick",
    "Bartender", "Mixologist", "Mixology", "Mixed drink", "Speakeasy",
    "Bathtub Gin (speakeasy)", "Harry's New York Bar", "New Holland Brewing Company",
    "BuzzBallz", "Lanique", "Liquid nitrogen cocktail", "Bomb shot",
    "Shot (drink)", "Highball", "Frozen (alcoholic drink)", "Flaming drink",
    "Signature drink", "Shrub (drink)", "Cerveza preparada", "IBA Tiki",
    "Pousse café", "Pousse cafe", "Layered drink", "Lowball", "Long drink",
    "Apéritif and digestif", "Apéritif", "Digestif", "Punch (drink)",
    "Wine cocktail", "Beer cocktail", "Sour (cocktail)", "Sling (drink)",
    "Smash (cocktail)", "Toddy", "Hot toddy", "Buck (cocktail)",
    "Fizz (cocktail)", "Cobbler (cocktail)", "Daisy (cocktail)",
    "Flip (cocktail)", "Julep", "Mojito", # mojito has its own page, keep
}
# remove Mojito from skip
NON_COCKTAIL_TITLES.discard("Mojito")


def should_skip_title(title: str) -> bool:
    if title in NON_COCKTAIL_TITLES:
        return True
    if SKIP_TITLE_RE.search(title):
        return True
    return False


# ----------------------- Build cocktail record -----------------------

SECTION_RE = re.compile(r"^==+\s*(.+?)\s*==+\s*$", re.MULTILINE)


def find_section(wikitext: str, names: list[str]) -> str | None:
    """Return the body of the first matching section (case-insensitive)."""
    matches = list(SECTION_RE.finditer(wikitext))
    for i, m in enumerate(matches):
        heading = m.group(1).strip().lower()
        for name in names:
            if heading == name.lower():
                start = m.end()
                end = matches[i + 1].start() if i + 1 < len(matches) else len(wikitext)
                return wikitext[start:end]
    return None


def parse_bullet_lines(body: str) -> list[str]:
    items = []
    for ln in body.splitlines():
        ln = ln.strip()
        if ln.startswith("*"):
            items.append(ln.lstrip("*").strip())
        elif ln.startswith("#"):
            items.append(ln.lstrip("#").strip())
    return items


def build_record(title: str, wikitext: str) -> dict | None:
    ib_text = find_infobox(wikitext)
    fields: dict[str, str] = {}
    if ib_text:
        fields = parse_infobox_fields(ib_text)

    ingredients_text = fields.get("ingredients") or fields.get("ingredient") or ""
    ingredients = parse_ingredients_field(ingredients_text)

    if not ingredients:
        # Fallback 1: try Recipe / Ingredients section
        for sec_name in ("Ingredients", "Recipe", "Recipes", "Preparation"):
            body = find_section(wikitext, [sec_name])
            if body:
                ingredients = [parse_ingredient_line(l) for l in parse_bullet_lines(body)]
                ingredients = [i for i in ingredients if i]
                if ingredients:
                    break

    if not ingredients:
        # Fallback 2: any bulleted list in the article, where lines contain
        # {{convert|N|<unit>}} or "N ml" / "N oz" — common stub recipe pattern.
        bullets = parse_bullet_lines(wikitext)
        if bullets:
            candidate = []
            for ln in bullets:
                # only keep lines that look ingredient-y
                if re.search(r"\{\{[Cc]onvert\|", ln) or re.search(
                        r"\b\d+(?:\.\d+|/\d+)?\s*(?:ml|cl|oz|fluid|ounce|tsp|tbsp|dash|drop|splash|bar)",
                        ln, re.IGNORECASE):
                    candidate.append(ln)
            if 2 <= len(candidate) <= 12:
                ingredients = [parse_ingredient_line(l) for l in candidate]
                ingredients = [i for i in ingredients if i]

    if not ingredients:
        # Fallback 3: extract from prose. Look for "made (with|of|from) X, Y, and Z"
        # or "consists of X, Y, and Z" in the lead.
        lead = wikitext[:3000]
        lead_clean = strip_wikitext(lead)
        m = re.search(
            r"(?:made|mixed|consists?|composed|comprised|prepared)\s+(?:with|of|from)\s+([^.]+?)(?:\.|$)",
            lead_clean, re.IGNORECASE)
        if m:
            blob = m.group(1)
            # Trim trailing modifiers
            blob = re.split(
                r",?\s+(?:and\s+)?(?:served|garnish|in\s+a|over|on\s+the\s+rocks|"
                r"shaken|stirred|blended|usually|generally|typically|sometimes)\b",
                blob, maxsplit=1, flags=re.IGNORECASE)[0]
            parts = re.split(r",|\band\b|;", blob)
            cands = []
            for p in parts:
                name = p.strip(" .,;").lower()
                # remove articles
                name = re.sub(r"^(?:a|an|the|some|fresh|freshly\s+squeezed)\s+", "", name)
                if not name or len(name) > 60:
                    continue
                # require it to be a known-ish drink ingredient
                if any(k in name for k in [
                    "gin", "vodka", "rum", "tequila", "whisky", "whiskey", "bourbon",
                    "rye", "scotch", "brandy", "cognac", "pisco", "mezcal", "cachaça",
                    "campari", "aperol", "vermouth", "lillet", "amaro", "absinthe",
                    "chartreuse", "benedictine", "drambuie", "kahlua", "amaretto",
                    "triple sec", "cointreau", "grand marnier", "curaçao", "curacao",
                    "maraschino", "schnapps", "champagne", "prosecco", "wine",
                    "sherry", "port", "sake", "ouzo", "sambuca", "liqueur",
                    "juice", "soda", "tonic", "ginger beer", "ginger ale", "cola",
                    "syrup", "honey", "agave", "sugar", "cream", "milk", "egg",
                    "coconut", "coffee", "tea", "lime", "lemon", "orange",
                    "grapefruit", "pineapple", "cranberry", "tomato", "apple",
                    "grenadine", "orgeat", "bitters", "falernum", "elderflower",
                    "st-germain", "st germain", "blue curacao", "blue curaçao",
                ]):
                    cands.append(name)
            if 2 <= len(cands) <= 8:
                ingredients = []
                for n in cands:
                    ingredients.append({
                        "name": n, "quantity": None, "unit": None,
                        "aisle": aisle_for(n),
                    })

    if not ingredients:
        return None

    prep_text = fields.get("prep") or fields.get("preparation") or ""
    if not prep_text:
        body = find_section(wikitext, ["Preparation", "Method", "Instructions", "How to make"])
        if body:
            prep_text = body[:1500]
    instructions = split_instructions(prep_text)
    if not instructions:
        # synthesize a default for build/stir/shake based on glass
        glass_raw = fields.get("drinkware") or fields.get("served") or ""
        if "rocks" in glass_raw.lower() or "old fashion" in glass_raw.lower():
            instructions = ["Add all ingredients to a rocks glass over ice", "Stir briefly", "Garnish and serve"]
        else:
            instructions = ["Combine ingredients with ice", "Shake or stir", "Strain into a chilled glass", "Garnish and serve"]

    glass = normalize_glass(fields.get("drinkware") or fields.get("served") or fields.get("glass") or "")
    method = detect_method(prep_text) or ("stirred" if glass == "rocks" else "shaken")
    garnish = strip_wikitext(fields.get("garnish") or "") or None
    if garnish:
        garnish = garnish.strip(". ")
        if not garnish:
            garnish = None

    description = extract_lead_paragraph(wikitext)
    region = guess_region(description)

    abv = estimate_abv(ingredients)
    is_alcoholic = 1
    # crude: if no ingredient is alcoholic, mark non-alcoholic
    spirits_present = any(
        any(k in (i.get("name") or "") for k in [
            "gin", "vodka", "rum", "tequila", "whisky", "whiskey", "bourbon",
            "rye", "scotch", "brandy", "cognac", "pisco", "cachaça", "cachaca",
            "mezcal", "campari", "aperol", "fernet", "vermouth", "lillet", "amaro",
            "absinthe", "pastis", "chartreuse", "benedictine", "bénédictine",
            "drambuie", "galliano", "kahlua", "baileys", "amaretto",
            "triple sec", "cointreau", "grand marnier", "curaçao", "curacao",
            "maraschino", "schnapps", "champagne", "prosecco", "wine", "beer",
            "sherry", "port", "sake", "ouzo", "sambuca", "liqueur",
        ])
        for i in ingredients
    )
    if not spirits_present:
        is_alcoholic = 0
        abv = 0

    record = {
        "title": title,
        "content_type": "cocktail",
        "is_alcoholic": is_alcoholic,
        "is_historic": 0,
        "source_book": "Wikipedia (CC-BY-SA)",
        "source_year": None,
        "source_region": region,
        "cuisine": "cocktail",
        "description": description,
        "servings": 1,
        "prep_minutes": 3,
        "cook_minutes": 0,
        "original_text": None,
        "modernized_text": None,
        "instructions": instructions,
        "ingredients": ingredients,
        "glass_type": glass,
        "method": method,
        "garnish": garnish,
        "abv_percent": abv,
        "image_url": None,
    }
    return record


# ----------------------- Main -----------------------

def main():
    print("[*] Gathering cocktail page titles from Wikipedia categories...", flush=True)
    titles = gather_all_titles()
    print(f"[*] Raw titles: {len(titles)}", flush=True)

    titles = [t for t in titles if not should_skip_title(t)]
    print(f"[*] After filter: {len(titles)}", flush=True)

    written = 0
    skipped_no_infobox = 0
    skipped_no_ingredients = 0
    skipped_other = 0
    seen_titles: set[str] = set()

    out_f = OUT_PATH.open("w", encoding="utf-8")
    try:
        # Batch fetch
        batch_size = 30
        for i in range(0, len(titles), batch_size):
            batch = titles[i:i + batch_size]
            wt_map = fetch_wikitext_batch(batch)
            for title in batch:
                if title in seen_titles:
                    continue
                seen_titles.add(title)
                wt = wt_map.get(title)
                if not wt:
                    skipped_other += 1
                    continue
                try:
                    rec = build_record(title, wt)
                except Exception as e:  # noqa
                    skipped_other += 1
                    continue
                if rec is None:
                    if find_infobox(wt) is None:
                        skipped_no_infobox += 1
                    else:
                        skipped_no_ingredients += 1
                    continue
                out_f.write(json.dumps(rec, ensure_ascii=False) + "\n")
                written += 1
            print(f"[*] Progress: {i + len(batch)}/{len(titles)} titles processed, "
                  f"{written} written", flush=True)
            time.sleep(0.4)
    finally:
        out_f.close()

    print(f"[OK] Wrote {written} cocktails to {OUT_PATH}")
    print(f"[..] Skipped: no_infobox={skipped_no_infobox}, "
          f"no_ingredients={skipped_no_ingredients}, other={skipped_other}")


if __name__ == "__main__":
    main()

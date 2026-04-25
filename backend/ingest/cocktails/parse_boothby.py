#!/usr/bin/env python3
"""Parse William T. Cocktail Bill Boothby's The World's Drinks and How to
Mix Them (1908) into NDJSON.

The OCR text uses ===PAGE N=== markers between pages. After the index,
recipes appear with a recipe number plus an ALL-CAPS title in 3 patterns:

  A) TITLE. <num>     e.g. ABSINTHE BRACER. 2
  B) <num> TITLE.     e.g. 30 BALD HEAD.
  C) <num> on its own line, then TITLE. on next line.

We treat any of these as recipe boundaries.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).parent
RAW = ROOT / 'raw' / 'boothby_1908.txt'
OUT = ROOT / 'boothby-1908.ndjson'

CONTRIBUTOR_STORY = (
    "William 'Cocktail Bill' Boothby (1862-1930) was San Francisco's most "
    "famous pre-Prohibition bartender. His World's Drinks traveled far "
    "beyond the city and helped define West Coast cocktail culture."
)

PAGE_RE = re.compile(r'^===PAGE\s+\d+===\s*$')
PAGE_NUM_RE = re.compile(r'^\s*\d{1,3}\s*$')

SECTION_HEADERS = {
    'ABSINTHE MIXTURES', 'ABSINTHE MIXTUUES', 'COCKTAILS', 'COBBLERS',
    'CUPS', 'FIZZES', 'FLIPS', 'FRAPPES', 'HOT DRINKS', 'JULEPS',
    'LEMONADES', 'PUNCHES', 'PUNCHES, COLD', 'PUNCHES, HOT', 'RICKEYS',
    'SCAFFAS', 'SHAKES', 'SHRUBS', 'SLINGS', 'SMASHES', 'SOURS',
    'TODDIES', 'TODDIES, COLD', 'TODDIES, HOT', 'TODDIES, COR.D',
    'POUSSE CAFES', 'POUSSE-CAFES', 'POUSSE CAFE', 'INDEX', 'I N D E X',
    'PREFACE', 'INTRODUCTORY', 'DEDICATION', 'WARNING',
    'EGG NOGGS', 'EGG NOGS', 'DAISIES', 'CRUSTAS', 'SANGAREES',
    'COOLERS', 'BEERS', 'WINES', 'CIDERS', 'LIQUEURS',
}

RUN_HEAD_RE = re.compile(
    r"^\s*(?:THE\s+WORLD'?S\s+DRINKS|WORLD'?S\s+DRINKS|HOW\s+TO\s+MIX\s+THEM|COCKTAIL\s+BILL'?S?)\s*\.?\s*$",
    re.IGNORECASE,
)

RECIPE_A = re.compile(r"^([A-Z][A-Z0-9'\-,&/().\s]{1,55}?)\.\s+(\d{1,3})\s*$")
RECIPE_B = re.compile(r"^(\d{1,3})\s+([A-Z][A-Z0-9'\-,&/().\s]{1,55}?)\.\s*$")
BARE_NUM_RE = re.compile(r'^\s*(\d{1,3})\s*$')
TITLE_LINE_RE = re.compile(r"^([A-Z][A-Z0-9'\-,&/().\s]{1,55}?)\.\s*$")


def normalize(s):
    s = s.replace('\r', '')
    s = re.sub(r"[‘’]", "'", s)
    s = re.sub(r'[“”]', '"', s)
    s = s.replace('—', '-').replace('–', '-')
    return s


def clean_title(t):
    t = re.sub(r'\s+', ' ', t.strip().rstrip('.').strip())
    t = t.replace('FRA.PPE', 'FRAPPE').replace('ROY AL', 'ROYAL')
    t = t.replace('OLD-FASHION', 'OLD-FASHIONED').replace('WHIT~', 'WHITE')
    t = t.replace('CHAMPERE!JE', 'CHAMPERELLE').replace('CHAMPERE!IE', 'CHAMPERELLE')
    return t


def is_section_header(t):
    base = t.strip().rstrip('.').strip().upper()
    return base in SECTION_HEADERS



def find_recipes(lines):
    recipes = []
    cur_num = None
    cur_title = None
    cur_body = []

    def commit():
        nonlocal cur_num, cur_title, cur_body
        if cur_title is not None:
            recipes.append((cur_num, cur_title, cur_body))
        cur_num = None
        cur_title = None
        cur_body = []

    i = 0
    n = len(lines)
    while i < n:
        raw_line = lines[i]
        s = raw_line.strip()
        if PAGE_RE.match(s) or RUN_HEAD_RE.match(s):
            i += 1
            continue

        m = RECIPE_A.match(s)
        if m:
            title_raw = m.group(1).strip()
            num = int(m.group(2))
            if not is_section_header(title_raw):
                commit()
                cur_num = num
                cur_title = clean_title(title_raw)
                cur_body = []
                i += 1
                continue

        m = RECIPE_B.match(s)
        if m:
            num = int(m.group(1))
            title_raw = m.group(2).strip()
            if not is_section_header(title_raw):
                commit()
                cur_num = num
                cur_title = clean_title(title_raw)
                cur_body = []
                i += 1
                continue

        m = BARE_NUM_RE.match(s)
        if m:
            num = int(m.group(1))
            j = i + 1
            while j < n:
                ns = lines[j].strip()
                if not ns or PAGE_RE.match(ns) or RUN_HEAD_RE.match(ns):
                    j += 1
                    continue
                break
            if j < n:
                tline = lines[j].strip()
                tm = TITLE_LINE_RE.match(tline)
                if tm and not is_section_header(tm.group(1)):
                    commit()
                    cur_num = num
                    cur_title = clean_title(tm.group(1))
                    cur_body = []
                    i = j + 1
                    continue
            i += 1
            continue

        if cur_title is not None:
            if PAGE_NUM_RE.match(s):
                i += 1
                continue
            if s:
                cur_body.append(raw_line.rstrip())
        i += 1

    commit()
    return recipes



GLASS_HINTS = [
    ('cocktail-glass', 'cocktail'), ('cocktail glass', 'cocktail'),
    ('punch-glass', 'punch'), ('punch glass', 'punch'),
    ('champagne-glass', 'flute'), ('champagne glass', 'flute'),
    ('highball-glass', 'highball'), ('highball glass', 'highball'),
    ('highball', 'highball'), ('high-ball', 'highball'),
    ('collins glass', 'collins'), ('collins-glass', 'collins'),
    ('fizz-glass', 'fizz'), ('fizz glass', 'fizz'),
    ('hot-drink', 'mug'), ('hot drink', 'mug'),
    ('toddy-glass', 'rocks'), ('toddy glass', 'rocks'),
    ('whiskey-glass', 'rocks'), ('whiskey glass', 'rocks'),
    ('sour-glass', 'sour'), ('sour glass', 'sour'),
    ('goblet', 'goblet'), ('mug', 'mug'), ('stein', 'stein'),
    ('wine-glass', 'wine'), ('wineglass', 'wine'), ('wine glass', 'wine'),
    ('sherry-glass', 'sherry'), ('sherry glass', 'sherry'),
    ('cordial-glass', 'cordial'), ('cordial glass', 'cordial'),
    ('pony-glass', 'cordial'), ('pony glass', 'cordial'),
    ('liqueur-glass', 'cordial'),
    ('punch-bowl', 'punch_bowl'), ('punch bowl', 'punch_bowl'),
    ('ale glass', 'pint'), ('tumbler', 'rocks'),
    ('bar-glass', 'rocks'), ('bar glass', 'rocks'),
]


def detect_glass(title, body):
    t = (title + ' ' + body).lower()
    for kw, g in GLASS_HINTS:
        if kw in t:
            return g
    tl = title.lower()
    if 'fizz' in tl: return 'fizz'
    if 'collins' in tl: return 'collins'
    if 'cobbler' in tl: return 'goblet'
    if 'julep' in tl: return 'julep'
    if 'punch' in tl and 'bowl' in body.lower(): return 'punch_bowl'
    if 'punch' in tl: return 'punch'
    if 'sour' in tl: return 'sour'
    if 'highball' in tl or 'rickey' in tl or 'cooler' in tl: return 'highball'
    if 'pousse' in tl or 'scaffa' in tl: return 'pousse_cafe'
    if 'cocktail' in tl: return 'cocktail'
    if 'toddy' in tl: return 'rocks'
    if 'flip' in tl or 'egg-nog' in tl or 'eggnog' in tl: return 'coupe'
    return ''


def detect_method(title, body):
    b = body.lower()
    tl = title.lower()
    if 'ignite' in b or 'blaze' in b or 'burn' in b:
        return 'flamed'
    if 'pousse' in tl or 'scaffa' in tl or 'float' in b:
        return 'layered'
    if 'muddle' in b or 'muddler' in b:
        return 'muddled'
    if 'shake' in b or 'frappe' in b or 'shaker' in b:
        return 'shaken'
    if 'stir' in b and ('strain' in b or 'mix' in b):
        return 'stirred'
    if 'boil' in b or 'boiling' in b or 'hot water' in b:
        return 'built_hot'
    if 'fill' in b or 'pour' in b or 'build' in b:
        return 'built'
    return 'stirred'


def detect_garnish(body):
    b = body.lower()
    found = []
    if ('twist' in b and 'lemon' in b) or 'lemon peel' in b or 'lemon-peel' in b:
        found.append('lemon peel')
    if ('twist' in b and 'orange' in b) or 'orange peel' in b or 'orange-peel' in b:
        found.append('orange peel')
    if 'cherry' in b or 'cherries' in b: found.append('cherry')
    if 'olive' in b: found.append('olive')
    if 'nutmeg' in b: found.append('grated nutmeg')
    if 'mint' in b and ('sprig' in b or 'bouquet' in b or 'boquet' in b):
        found.append('mint sprig')
    if 'berries' in b or 'strawberries' in b or 'raspberries' in b:
        found.append('berries')
    if 'pineapple' in b and ('piece' in b or 'slice' in b or 'stick' in b):
        found.append('pineapple')
    if not found:
        return None
    seen = set()
    uniq = []
    for g in found:
        if g not in seen:
            seen.add(g)
            uniq.append(g)
    return ', '.join(uniq[:2])



ALC_KW = (
    'whiskey', 'whisky', 'rum', 'gin', 'brandy', 'vermouth', 'absinthe',
    'bitters', 'wine', 'champagne', 'rye', 'scotch', 'bourbon', 'cognac',
    'applejack', 'apple jack', 'calvados', 'port', 'sherry', 'curacao',
    'curacoa', 'creme de', 'chartreuse', 'benedictine', 'maraschino',
    'anisette', 'amer picon', 'byrrh', 'dubonnet', 'fernet', 'kirsch',
    'cointreau', 'kummel', 'kuemmel', 'campari', 'ale', 'porter', 'stout',
    'claret', 'burgundy', 'moselle', 'tokay', 'sloe gin', 'old tom',
    'calisaya', 'swedish punch', 'arrack', 'liqueur', 'cordial',
    'creme', 'noyau', 'orgeat', 'madeira',
)


def is_alcoholic(title, body):
    t = (title + ' ' + body).lower()
    return 1 if any(k in t for k in ALC_KW) else 0


def has_raw_egg(body):
    b = body.lower()
    if 'egg' not in b: return False
    if 'boil' in b or 'boiling' in b or 'fire' in b: return False
    return True


AISLE_FOR = [
    ('absinthe', 'bar'), ('anisette', 'bar'), ('bitters', 'bar'),
    ('vermouth', 'bar'), ('whiskey', 'bar'), ('whisky', 'bar'),
    ('rye', 'bar'), ('scotch', 'bar'), ('bourbon', 'bar'), ('rum', 'bar'),
    ('gin', 'bar'), ('brandy', 'bar'), ('cognac', 'bar'), ('calvados', 'bar'),
    ('apple jack', 'bar'), ('applejack', 'bar'),
    ('champagne', 'bar'), ('wine', 'bar'), ('port', 'bar'), ('sherry', 'bar'),
    ('claret', 'bar'), ('burgundy', 'bar'), ('benedictine', 'bar'),
    ('chartreuse', 'bar'), ('curacao', 'bar'), ('curacoa', 'bar'),
    ('maraschino', 'bar'), ('kirsch', 'bar'), ('kummel', 'bar'),
    ('cointreau', 'bar'), ('creme', 'bar'), ('dubonnet', 'bar'),
    ('byrrh', 'bar'), ('fernet', 'bar'), ('amer picon', 'bar'),
    ('orgeat', 'bar'), ('ale', 'bar'), ('porter', 'bar'), ('cider', 'bar'),
    ('stout', 'bar'), ('noyau', 'bar'), ('liqueur', 'bar'), ('cordial', 'bar'),
    ('syrup', 'pantry'), ('sugar', 'pantry'), ('honey', 'pantry'),
    ('salt', 'pantry'), ('nutmeg', 'pantry'), ('cinnamon', 'pantry'),
    ('clove', 'pantry'), ('allspice', 'pantry'), ('pepper', 'pantry'),
    ('egg', 'produce'), ('milk', 'dairy'), ('cream', 'dairy'),
    ('butter', 'dairy'),
    ('lemon', 'produce'), ('lime', 'produce'), ('orange', 'produce'),
    ('pineapple', 'produce'), ('mint', 'produce'), ('cherry', 'produce'),
    ('berries', 'produce'), ('strawberr', 'produce'), ('raspberr', 'produce'),
    ('apple', 'produce'), ('peach', 'produce'), ('cucumber', 'produce'),
    ('seltzer', 'other'), ('soda', 'other'), ('water', 'other'),
    ('ice', 'other'),
]


def guess_aisle(name):
    n = name.lower()
    for kw, ai in AISLE_FOR:
        if kw in n:
            return ai
    return 'other'


WORD_QTY = {
    'a': 1, 'an': 1, 'one': 1, 'two': 2, 'three': 3, 'four': 4, 'five': 5,
    'six': 6, 'seven': 7, 'eight': 8, 'ten': 10, 'twelve': 12,
    'half': 0.5, 'one-half': 0.5, 'half a': 0.5, 'several': 2, 'a few': 2,
}

UNIT_NORM = {
    'pony': 'pony', 'jigger': 'jigger',
    'wine-glass': 'wine-glass', 'wineglass': 'wine-glass',
    'wineglassful': 'wine-glass', 'wine-glassful': 'wine-glass',
    'teaspoon': 'tsp', 'teaspoonful': 'tsp',
    'tablespoon': 'tbsp', 'tablespoonful': 'tbsp',
    'barspoon': 'tsp', 'barspoonful': 'tsp', 'spoon': 'tsp', 'spoonful': 'tsp',
    'dash': 'dash', 'drop': 'drop',
    'lump': 'lump', 'slice': 'slice', 'piece': 'piece',
    'bottle': 'bottle', 'quart': 'quart', 'pint': 'pint',
    'gallon': 'gallon', 'gill': 'gill', 'sprig': 'sprig',
    'cube': 'lump', 'squirt': 'squirt',
    'yolk': 'piece', 'white': 'piece',
}



ING_FULL_RE = re.compile(
    r"""(?:about\s+|nearly\s+|just\s+)?
    \b(?P<qty>half\s+a|one-half|one|two|three|four|five|six|seven|eight|
    ten|twelve|several|a\s+few|a|an|the|\d+(?:\s*-\s*\d+)?)\s+
    (?P<unit>pony|jigger|wine[\s-]?glass(?:ful)?|teaspoon(?:ful)?|
    tablespoon(?:ful)?|barspoon(?:ful)?|spoonful?|dash(?:es)?|
    drop(?:s)?|lump(?:s)?|slice(?:s)?|piece(?:s)?|bottle(?:s)?|
    quart(?:s)?|pint(?:s)?|gallon(?:s)?|gill(?:s)?|sprig(?:s)?|
    squirt(?:s)?|cube(?:s)?|yolk|white)
    (?:\s+of)?\s+
    (?P<rest>[A-Za-z][A-Za-z' \-,/]+?)
    (?=[,.;]|$|\s+(?:and|or|with|in|on|over|into|to|then|until|stir|
    shake|strain|fill|pour|add|place|drop|squeeze|twist|ignite|serve|
    grate|sprinkle|drink|ornament|trim|dress|garnish|decorate|float|
    use|put|set|cover|beat|crush|now|next|rub|top))""",
    re.IGNORECASE | re.VERBOSE,
)


def parse_qty(q):
    q = q.strip().lower().replace('  ', ' ')
    if q in WORD_QTY:
        return WORD_QTY[q]
    m = re.match(r'^(\d+)\s*-\s*(\d+)$', q)
    if m:
        return (int(m.group(1)) + int(m.group(2))) / 2.0
    try:
        return float(q)
    except ValueError:
        return 1.0


def normalize_unit(u):
    u = u.lower().replace(' ', '-')
    if u in UNIT_NORM:
        return UNIT_NORM[u]
    # Try dropping 'es', 's', or 'ful' suffixes
    for suf in ('es', 's', 'ful'):
        if u.endswith(suf) and len(u) > len(suf):
            cand = u[:-len(suf)]
            if cand in UNIT_NORM:
                return UNIT_NORM[cand]
    return UNIT_NORM.get(u, u)


def split_instructions(body):
    text = re.sub(r'\s+', ' ', body).strip()
    if not text: return []
    parts = re.split(r'(?<=[.;])\s+(?=[A-Z])', text)
    parts = [p.strip() for p in parts if p.strip() and len(p) > 3]
    return parts[:8]


BAD_INGRED_PREFIX = (
    'the ', 'this ', 'shaker', 'mixing', 'glass', 'ice ', 'lump of',
    'patron', 'bartender', 'barkeeper', 'customer', 'guest',
)


def extract_ingredients(body):
    out = []
    seen = set()
    text = re.sub(r'\s+', ' ', body)
    for m in ING_FULL_RE.finditer(text):
        qty = parse_qty(m.group('qty'))
        unit = normalize_unit(m.group('unit'))
        rest = m.group('rest').strip().rstrip('.,;:').strip()
        if not rest or len(rest) > 60: continue
        rl = rest.lower()
        if any(rl.startswith(b) for b in BAD_INGRED_PREFIX): continue
        if rl in {'ice', 'cracked ice', 'shaved ice', 'fine ice', 'broken ice'}:
            continue
        rest = re.sub(r'\s+(and|or|with|in|on|over|into|to|of)$', '',
                      rest, flags=re.I).strip()
        if not rest or len(rest) < 3: continue
        key = (rest.lower(), unit)
        if key in seen: continue
        seen.add(key)
        out.append({
            'name': rest.lower(),
            'quantity': qty,
            'unit': unit,
            'aisle': guess_aisle(rest),
        })
    return out



def make_description(title):
    t = title.lower()
    if 'manhattan' in t:
        return "Boothby's San Francisco take on the Manhattan, the city's signature stirred whiskey cocktail."
    if 'martini' in t:
        return "A pre-Prohibition Martini, recorded by SF's star bartender."
    if 'old-fashion' in t or 'old fashion' in t:
        return 'An in-the-glass cocktail of sugar, bitters, ice, and the chosen spirit.'
    if 'fizz' in t:
        return "A shaken citrus fizz from Boothby's 1908 World's Drinks."
    if 'collins' in t:
        return "Tall, sparkling lemon-and-spirit cooler, Boothby's San Francisco style."
    if 'rickey' in t:
        return "Lime-and-spirit highball from Boothby's 1908 World's Drinks."
    if 'cooler' in t:
        return "Long iced cooler from Boothby's 1908 manual."
    if 'julep' in t:
        return "Mint julep -- Boothby's pre-Prohibition rendition."
    if 'cobbler' in t:
        return "Iced wine cobbler from Boothby's 1908 World's Drinks."
    if 'punch' in t:
        return "Pitcher- or bowl-style punch from Boothby's San Francisco bar."
    if 'pousse' in t or 'scaffa' in t:
        return "Layered cordial after-dinner drink from Boothby's manual."
    if 'flip' in t or 'egg-nog' in t or 'eggnog' in t:
        return "Egg-rich flip/eggnog from Boothby's 1908 manual."
    if 'sour' in t:
        return "Classic sour: spirit, lemon, sugar -- Boothby's 1908 build."
    if 'frappe' in t:
        return 'Pre-Prohibition frappe over crushed ice.'
    if 'smash' in t:
        return "Muddled smash from Boothby's San Francisco bar."
    if 'sangaree' in t:
        return 'A sangaree -- wine or spirit sweetened and dusted with nutmeg.'
    if 'toddy' in t:
        return "Hot or cold toddy from Boothby's 1908 manual."
    if 'sling' in t:
        return 'A sling -- sugar, water, and spirit, served plain.'
    if 'cocktail' in t:
        return "1908 cocktail from Boothby's World's Drinks."
    return "Pre-Prohibition drink from Boothby's 1908 World's Drinks."


def estimate_abv(title, body):
    t = title.lower()
    if not is_alcoholic(title, body): return 0
    if 'pousse' in t or 'scaffa' in t: return 38
    if 'cocktail' in t: return 30
    if 'julep' in t or 'smash' in t: return 26
    if 'fizz' in t or 'rickey' in t or 'collins' in t or 'cooler' in t: return 12
    if 'highball' in t: return 14
    if 'punch' in t:
        if any(k in body.lower() for k in ('bowl', 'gallon', 'quart', 'party')):
            return 12
        return 18
    if 'sour' in t: return 16
    if 'cobbler' in t: return 14
    if 'flip' in t or 'eggnog' in t or 'egg-nog' in t: return 14
    if 'champagne' in t: return 12
    if 'toddy' in t or 'sling' in t: return 18
    if 'sangaree' in t: return 12
    return 18


def estimate_servings(body):
    b = body.lower()
    if 'gallon' in b: return 40
    if 'for a party' in b or 'for party' in b: return 12
    if 'bowl' in b and ('quart' in b or 'bottle' in b): return 20
    if 'for six' in b: return 6
    return 1


def title_case(t):
    small = {'and', 'of', 'the', 'a', 'an', 'in', 'on', 'to', 'for', 'or',
             'by', 'at', 'with', 'de', 'la', 'le', 'du'}
    parts = t.split()
    out = []
    for i, w in enumerate(parts):
        wl = w.lower()
        if i > 0 and wl in small:
            out.append(wl)
        elif w.isupper() and len(w) <= 3 and '.' not in w:
            out.append(w)
        else:
            out.append(w.capitalize())
    return ' '.join(out)



def main():
    if not RAW.exists():
        print('Missing raw file:', RAW, file=sys.stderr)
        sys.exit(1)
    text = normalize(RAW.read_text(encoding='utf-8', errors='replace'))
    lines = text.split('\n')
    recipes = find_recipes(lines)
    print('Found', len(recipes), 'candidate recipes (pre-filter).', file=sys.stderr)

    out_records = []
    seen_keys = set()
    skipped = 0
    for num, title, body_lines in recipes:
        body_text = '\n'.join(l for l in body_lines if l.strip()).strip()
        if not body_text or len(body_text) < 25:
            skipped += 1
            continue

        tlow = title.lower()
        if any(k in tlow for k in ('contents', 'preface', 'foreword',
                                    'introduction', 'index', 'publisher',
                                    'copyright', 'frontispiece',
                                    'advertisement', 'anheuser',
                                    'witty', 'true story', 'find of bacchus',
                                    'about ', 'how to make', 'how to mix',
                                    'mr.', 'mrs.', 'manufacture', 'note',
                                    'recipe of', 'fragments')):
            skipped += 1
            continue

        if 'see recipe' in body_text.lower() and len(body_text) < 80:
            skipped += 1
            continue

        if not is_alcoholic(title, body_text):
            if not any(k in tlow for k in ('lemonade', 'punch', 'cooler',
                                            'sherbet', 'tea', 'ade', 'sour',
                                            'fizz', 'syrup')):
                skipped += 1
                continue

        # Filter short single-word titles which are usually page-header artifacts
        if len(title.split()) == 1 and len(title) < 6 and title.upper() == title:
            skipped += 1
            continue
        key = (num, tlow)
        if key in seen_keys: continue
        seen_keys.add(key)

        glass = detect_glass(title, body_text)
        method = detect_method(title, body_text)
        garnish = detect_garnish(body_text)
        alc = is_alcoholic(title, body_text)
        abv = estimate_abv(title, body_text) if alc else 0
        raw_egg = has_raw_egg(body_text)
        display_title = title_case(title)
        original = title + '.\n' + body_text

        rec = {
            'title': display_title,
            'content_type': 'cocktail',
            'is_alcoholic': alc,
            'is_historic': 1,
            'source_book': "The World's Drinks and How to Mix Them (William Boothby, 1908)",
            'source_year': 1908,
            'source_region': 'San Francisco, USA',
            'contributor_name': 'William T. "Cocktail Bill" Boothby',
            'contributor_story': CONTRIBUTOR_STORY,
            'cuisine': 'cocktail',
            'description': make_description(title),
            'servings': estimate_servings(body_text),
            'prep_minutes': 5 if 'punch' in tlow and 'bowl' in body_text.lower() else 3,
            'cook_minutes': 2 if (method == 'built_hot' or method == 'flamed') else 0,
            'original_text': original,
            'modernized_text': '',
            'instructions': split_instructions(body_text),
            'ingredients': extract_ingredients(body_text),
            'glass_type': glass,
            'method': method,
            'garnish': garnish,
            'abv_percent': abv,
            'boothby_recipe_number': num,
        }
        if raw_egg:
            rec['safety_notes'] = ('Original uses raw whole egg; use pasteurized egg '
                                   'for modern preparation.')
        else:
            rec['safety_notes'] = None
        out_records.append(rec)

    OUT.write_text(
        '\n'.join(json.dumps(r, ensure_ascii=False) for r in out_records) + '\n',
        encoding='utf-8',
    )
    print('Wrote', len(out_records), 'recipes to', OUT, '(skipped', skipped, ')')


if __name__ == '__main__':
    main()

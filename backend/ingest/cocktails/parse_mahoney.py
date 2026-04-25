#!/usr/bin/env python3
"""Parse Charles Mahoney's Hoffman House Bartender's Guide (1905) into NDJSON.

The OCR for mahoney_1905.txt has the recipes section starting near
HOFFMAN HOUSE RECIPES (page header) followed by Title-Case recipe titles
ending with a period (e.g., Brandy Cocktail., Martini Cocktail.). The line
after the title is typically a use-glass note (Use large bar glass.). Recipe
ends at the next title or section header.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).parent
RAW = ROOT / 'raw' / 'mahoney_1905.txt'
OUT = ROOT / 'mahoney-1905.ndjson'

CONTRIBUTOR_STORY = (
    'Charles S. Mahoney was head bartender at the Hoffman House, the '
    "landmark New York hotel that rivaled Delmonico's. His 1905 guide "
    "captures the city's golden-age cocktail repertoire and the working "
    'practices of one of the most prestigious pre-Prohibition bars.'
)

PAGE_RE = re.compile(r'^===PAGE\s+\d+===\s*$')
PAGE_NUM_RE = re.compile(r'^\s*\d{1,3}\s*$')



SECTION_LIKE = {
    'special notice', 'preface', 'opening a cafe', 'arrangement of a bar',
    'buying an old place', 'hints for beginners', 'rules for bartenders',
    'tips for bartenders', 'when the bar man wants', 'a position',
    'relations of employer and', 'employed', 'buying supplies',
    'how to keep books', 'opening in the morning', 'concerning glassware',
    'how to treat patrons', 'tending bar', 'handling money in a rush',
    'the system of checks', 'serving at tables', 'the sale of cigars',
    'serving free lunch', 'care of cellar and store', 'porter',
    'concerning case goods', 'handling mineral waters',
    'handling claret wines', 'how to serve champagne', 'the ice box',
    'to keep ants and insects out', 'keeping silver and brass-',
    'ware clean', 'brief hints to bartenders',
    'utensils and tools for a bar', 'glassware list', 'wine list',
    'list of liquors and cordials', 'list of syrups', 'list of bitters',
    'list of incidentals', 'index', 'contents', 'hoffman house recipes',
    'pouring the cocktail', 'putting in the bitters', 'mixing',
    'using the shaker', 'opening wine', 'the scraper',
    'mix', 'cocktail', 'absinthe', 'for party of six or more', 'old french style',
}


def normalize(s):
    s = s.replace('\r', '')
    s = s.replace('‘', "'").replace('’', "'")
    s = s.replace('“', '"').replace('”', '"')
    s = s.replace('—', '-').replace('–', '-')
    return s



def looks_like_recipe_title(s):
    s = s.strip()
    if len(s) < 5 or len(s) > 60:
        return False
    words_check = s.rstrip('.').split()
    if len(words_check) < 2:
        return False
    if not s.endswith('.'):
        return False
    base = s.rstrip('.').strip()
    if not base or not base[0].isupper():
        return False
    bl = base.lower()
    if bl in SECTION_LIKE:
        return False
    # Reject titles starting with quantity words or digits (ingredient lines)
    if re.match(r'^(one|two|three|four|five|six|seven|eight|nine|ten|twelve|half|several|few|dash|drop|lump|cube|slice|piece|enough|about)\b', bl):
        return False
    if re.match(r'^\d', bl):
        return False
    verbs = ('one ', 'two ', 'three ', 'four ', 'five ', 'six ', 'seven ', 'eight ', 'ten ', 'twelve ', 'half ', 'about ', 'enough ', 'few ', 'use ', 'fill ', 'shake ', 'stir ', 'strain ', 'pour ', 'add ',
             'place ', 'mix ', 'put ', 'serve ', 'take ', 'drop ', 'twist ',
             'squeeze ', 'beat ', 'crush ', 'muddle ', 'grate ', 'ornament ',
             'dress ', 'garnish ', 'this ', 'these ', 'they ', 'the ',
             'about ', 'after ', 'before ', 'when ', 'always ', 'never ',
             'do not ', 'ice ', 'fine ', 'cracked ', 'shaved ',
             'enough ', 'pepper ', 'a piece ', 'a slice ', 'a small ',
             'a large ', 'small ', 'large ', 'medium ')
    if any(bl.startswith(v) for v in verbs):
        return False
    words = base.split()
    if len(words) > 8:
        return False
    cap = sum(1 for w in words if w and w[0].isupper())
    if cap < max(1, len(words) // 2):
        return False
    if all(re.match(r'^\d+$', w.rstrip('.,')) for w in words):
        return False
    return True



def find_recipe_section_start(lines):
    for i, ln in enumerate(lines):
        if 'HOFFMAN HOUSE RECIPES' in ln.upper():
            return i + 1
    for i, ln in enumerate(lines):
        if ln.strip() == 'Brandy Cocktail.':
            return i
    return 0


def find_recipe_section_end(lines, start):
    for i in range(start, len(lines)):
        u = lines[i].upper()
        if ("SUBSCRIPTION RATES" in u
                or "ATHLETIC LIBRARY" in u
                or "PHYSICAL CULTURE" in u):
            return i
    return len(lines)


def find_recipes(lines):
    start = find_recipe_section_start(lines)
    end = find_recipe_section_end(lines, start)
    lines = lines[:end]
    recipes = []
    cur_title = None
    cur_body = []

    def commit():
        nonlocal cur_title, cur_body
        if cur_title is not None:
            recipes.append((cur_title, cur_body))
        cur_title = None
        cur_body = []

    for raw_line in lines[start:]:
        s = raw_line.strip()
        if PAGE_RE.match(s):
            continue
        if PAGE_NUM_RE.match(s):
            continue
        if not s:
            if cur_title is not None:
                cur_body.append('')
            continue
        if looks_like_recipe_title(s):
            commit()
            cur_title = s.rstrip('.').strip()
            cur_body = []
            continue
        if cur_title is not None:
            cur_body.append(raw_line.rstrip())

    commit()
    return recipes



from parse_boothby import (
    detect_glass, detect_method, detect_garnish, is_alcoholic, has_raw_egg,
    extract_ingredients, split_instructions, estimate_servings, title_case,
)



def make_description(title):
    t = title.lower()
    if 'manhattan' in t:
        return "Hoffman House Manhattan -- a New York classic from Mahoney's 1905 guide."
    if 'martini' in t:
        return 'A pre-Prohibition Martini in the Hoffman House style.'
    if 'old-fashion' in t:
        return 'Old-Fashioned Whiskey cocktail in the Hoffman House style.'
    if 'fizz' in t:
        return 'A Hoffman House fizz, shaken and topped with seltzer.'
    if 'collins' in t:
        return "Tall sparkling collins from Mahoney's Hoffman House Bar."
    if 'rickey' in t:
        return 'Lime-and-spirit rickey from the Hoffman House bar.'
    if 'cooler' in t:
        return 'Long iced cooler from the Hoffman House.'
    if 'julep' in t:
        return 'Mint julep in the Hoffman House style.'
    if 'cobbler' in t:
        return "Iced wine cobbler from Mahoney's 1905 guide."
    if 'punch' in t:
        return 'Pitcher- or bowl-style punch from the Hoffman House Bar.'
    if 'pousse' in t or 'scaffa' in t:
        return 'Layered cordial after-dinner drink, Hoffman House style.'
    if 'flip' in t or 'egg-nog' in t or 'eggnog' in t or 'egg nog' in t:
        return 'Egg-rich flip from the Hoffman House.'
    if 'sour' in t:
        return 'Classic sour: spirit, lemon, sugar -- 1905 Hoffman House build.'
    if 'frappe' in t:
        return 'Frappe over crushed ice, Hoffman House style.'
    if 'smash' in t:
        return 'Muddled smash from the Hoffman House.'
    if 'sangaree' in t:
        return 'A sangaree -- wine or spirit sweetened and dusted with nutmeg.'
    if 'toddy' in t:
        return 'Hot or cold toddy from the Hoffman House.'
    if 'sling' in t:
        return 'A sling: sugar, water, and spirit, served plain.'
    if 'cocktail' in t:
        return "1905 Hoffman House cocktail from Mahoney's guide."
    if 'highball' in t or 'high ball' in t:
        return 'A spirit-and-soda highball from the Hoffman House Bar.'
    if 'crusta' in t:
        return 'Sugared-rim crusta in the Hoffman House style.'
    if 'daisy' in t:
        return 'A daisy: spirit, lemon, orange cordial, topped with seltzer.'
    return "Pre-Prohibition drink from Mahoney's 1905 Hoffman House Bartender's Guide."



def estimate_abv(title, body):
    t = title.lower()
    if not is_alcoholic(title, body):
        return 0
    if 'pousse' in t or 'scaffa' in t: return 38
    if 'cocktail' in t: return 30
    if 'julep' in t or 'smash' in t: return 26
    if 'fizz' in t or 'rickey' in t or 'collins' in t or 'cooler' in t: return 12
    if 'highball' in t or 'high ball' in t: return 14
    if 'punch' in t:
        if any(k in body.lower() for k in ('bowl', 'gallon', 'quart', 'party')):
            return 12
        return 18
    if 'sour' in t: return 16
    if 'cobbler' in t: return 14
    if 'flip' in t or 'eggnog' in t or 'egg-nog' in t or 'egg nog' in t: return 14
    if 'champagne' in t: return 12
    if 'toddy' in t or 'sling' in t: return 18
    if 'sangaree' in t: return 12
    return 18



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
    for title, body_lines in recipes:
        body_text = '\n'.join(l for l in body_lines if l.strip()).strip()
        if not body_text or len(body_text) < 25:
            skipped += 1
            continue
        tlow = title.lower()
        if any(k in tlow for k in ('contents', 'preface', 'foreword',
                                    'introduction', 'index', 'publisher',
                                    'copyright', 'frontispiece',
                                    'advertisement')):
            skipped += 1
            continue
        if not is_alcoholic(title, body_text):
            if not any(k in tlow for k in ('lemonade', 'punch', 'cooler',
                                            'sherbet', 'tea', 'ade', 'sour',
                                            'fizz', 'syrup', 'oyster',
                                            'clam', 'cocktail')):
                skipped += 1
                continue
        key = tlow
        if key in seen_keys:
            continue
        seen_keys.add(key)

        glass = detect_glass(title, body_text)
        method = detect_method(title, body_text)
        garnish = detect_garnish(body_text)
        alc = is_alcoholic(title, body_text)
        abv = estimate_abv(title, body_text) if alc else 0
        raw_egg = has_raw_egg(body_text)
        original = title + '.\n' + body_text

        rec = {
            'title': title,
            'content_type': 'cocktail',
            'is_alcoholic': alc,
            'is_historic': 1,
            'source_book': "The Hoffman House Bartender's Guide (Charles Mahoney, 1905)",
            'source_year': 1905,
            'source_region': 'New York, USA',
            'contributor_name': 'Charles S. Mahoney',
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

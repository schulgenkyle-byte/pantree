"""Analyze modern-craft.ndjson output for category breakdown."""
import json
import os
from collections import Counter

OUT = os.path.join(os.path.dirname(__file__), 'modern-craft.ndjson')

recipes = []
with open(OUT, 'r', encoding='utf-8') as f:
    for line in f:
        try:
            recipes.append(json.loads(line))
        except Exception:
            pass

print(f'Total recipes: {len(recipes)}')

def categorize(r):
    title = r.get('title', '').lower()
    desc = r.get('description', '').lower()
    ings = ' '.join(i.get('name', '').lower() for i in r.get('ingredients', []))
    blob = title + ' ' + desc + ' ' + ings
    cats = []
    if 'mezcal' in blob:
        cats.append('mezcal')
    if 'tiki' in blob or 'falernum' in blob or 'orgeat' in blob:
        cats.append('tiki')
    if 'tequila' in blob or 'agave' in blob:
        cats.append('agave/tequila')
    if 'spritz' in title or 'aperol' in blob or 'campari' in blob or 'cynar' in blob or 'amaro' in blob or 'fernet' in blob or 'negroni' in blob:
        cats.append('italian/spritz/amaro')
    if 'whisk' in blob or 'rye' in blob or 'bourbon' in blob:
        cats.append('whiskey/bourbon')
    if 'rum' in blob and 'tiki' not in cats:
        cats.append('rum')
    if 'gin' in blob:
        cats.append('gin')
    if 'cognac' in blob or 'brandy' in blob:
        cats.append('brandy/cognac')
    if 'espresso' in blob or 'coffee' in blob or 'kahlúa' in blob:
        cats.append('coffee')
    if 'sherry' in blob:
        cats.append('sherry')
    if 'sake' in blob or 'shochu' in blob or 'japanese' in blob or 'yuzu' in blob or 'shiso' in blob:
        cats.append('japanese')
    if 'pisco' in blob or 'cachaça' in blob:
        cats.append('south american')
    if 'frozen' in title or 'frosé' in title or 'frose' in title or r.get('method') == 'blended':
        cats.append('frozen/blended')
    return cats

c = Counter()
for r in recipes:
    for cat in set(categorize(r)):
        c[cat] += 1

print('\nCategory breakdown:')
for cat, n in sorted(c.items(), key=lambda x: -x[1]):
    print(f'  {cat:25s} {n}')

creators = Counter()
for r in recipes:
    cn = r.get('contributor_name')
    if cn:
        creators[cn] += 1

print('\nTop contributors:')
for cr, n in sorted(creators.items(), key=lambda x: -x[1])[:20]:
    print(f'  {cr:35s} {n}')

years = [r.get('source_year') for r in recipes if r.get('source_year')]
if years:
    print(f'\nYear range: {min(years)} - {max(years)}, {len(years)} dated of {len(recipes)}')

"""Build modern-craft.ndjson — contemporary craft cocktails (2000s-2020s).
Names + creator attributions are public/factual. Recipe specs use standard amounts.
Method/instructions written in our own words.
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), 'modern-craft.ndjson')

# ---------------------------------------------------------------------------
# Helper to build a record with sensible defaults
# ---------------------------------------------------------------------------

def cocktail(title, *, year=None, region=None, creator=None, bar=None, story,
             desc, instructions, ingredients, glass='coupe', method='shaken',
             garnish=None, abv=22, prep=4, source=None, category=None):
    if not source:
        if creator and bar and year:
            source = f"{bar} / {creator} ({year})"
        elif creator and year:
            source = f"{creator} ({year})"
        elif bar and year:
            source = f"{bar} ({year})"
        elif creator:
            source = creator
        elif bar:
            source = bar
        else:
            source = "Modern craft canon"
    return {
        "title": title,
        "content_type": "cocktail",
        "is_alcoholic": 1,
        "is_historic": 0,
        "source_book": source,
        "source_year": year,
        "source_region": region,
        "contributor_name": creator or bar,
        "contributor_story": story,
        "cuisine": "cocktail",
        "description": desc,
        "servings": 1,
        "prep_minutes": prep,
        "cook_minutes": 0,
        "original_text": None,
        "modernized_text": None,
        "instructions": instructions,
        "ingredients": ingredients,
        "glass_type": glass,
        "method": method,
        "garnish": garnish,
        "abv_percent": abv,
    }


def ing(name, qty, unit, aisle='bar'):
    return {"name": name, "quantity": qty, "unit": unit, "aisle": aisle}


RECIPES = []

# ============================================================================
# MODERN CRAFT CLASSICS (Sam Ross / Sasha Petraske / Milk & Honey / Attaboy era)
# ============================================================================

RECIPES.append(cocktail(
    "Gold Rush",
    year=2001, region="New York, USA", creator="T. J. Siegal", bar="Milk & Honey",
    story="Created by T. J. Siegal at Sasha Petraske's Milk & Honey on the Lower East Side around 2001. A three-ingredient bourbon riff on the Bee's Knees, it became the template for Sam Ross's later Penicillin and helped define the Milk & Honey simplicity ethos.",
    desc="Three-ingredient bourbon, lemon and honey sour — the modern Milk & Honey template.",
    instructions=[
        "Shake bourbon, lemon juice, and honey syrup hard with ice for 10-12 seconds",
        "Strain over a single large cube in a rocks glass",
        "No garnish required",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("honey syrup (3:1 honey to water)", 0.75, "oz", "pantry"),
    ],
    glass="rocks", garnish=None, abv=24, category="modern",
))

RECIPES.append(cocktail(
    "Business",
    year=2003, region="New York, USA", creator="Sasha Petraske", bar="Milk & Honey",
    story="Sasha Petraske's gin variant on the Gold Rush template — gin, lemon and honey shaken short and served up. Petraske preferred this format as a daytime drink for industry friends, hence the name.",
    desc="Petraske's gin-honey-lemon sour, served straight up.",
    instructions=[
        "Shake all ingredients hard with ice",
        "Double strain into a chilled coupe",
        "No garnish",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("honey syrup", 0.75, "oz", "pantry"),
    ],
    glass="coupe", abv=22, category="modern",
))

RECIPES.append(cocktail(
    "Whiskey Smash",
    year=2004, region="New York, USA", creator="Dale DeGroff",
    story="Dale DeGroff revived and refined the 19th-century smash format at the Rainbow Room in the early 2000s, giving the genre a modern bourbon-mint-lemon spec that became standard at craft bars worldwide.",
    desc="Dale DeGroff's modern bourbon, mint, and lemon smash.",
    instructions=[
        "Muddle lemon wedges with mint leaves and simple syrup in a shaker",
        "Add bourbon and ice; shake hard",
        "Strain over crushed ice in a rocks glass",
        "Garnish with a generous mint sprig",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("lemon wedges", 4, "each", "produce"),
        ing("mint leaves", 8, "each", "produce"),
        ing("simple syrup", 0.75, "oz", "pantry"),
    ],
    glass="rocks", garnish="mint sprig", abv=22,
))

RECIPES.append(cocktail(
    "Eastside",
    year=2003, region="New York, USA", bar="Milk & Honey",
    creator="Sasha Petraske",
    story="A Milk & Honey staple — gin, cucumber, mint, lime, and simple. The Eastside became one of the era's defining gin sours and a benchmark for vegetal freshness.",
    desc="Cucumber-mint gin sour from the early Milk & Honey era.",
    instructions=[
        "Muddle cucumber slices and mint gently in a shaker",
        "Add gin, lime juice, simple syrup and ice; shake hard",
        "Double strain into a chilled coupe",
        "Garnish with a cucumber wheel",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.75, "oz", "pantry"),
        ing("cucumber slices", 3, "each", "produce"),
        ing("mint leaves", 6, "each", "produce"),
    ],
    glass="coupe", garnish="cucumber wheel", abv=22,
))

RECIPES.append(cocktail(
    "Greenpoint",
    year=2005, region="Brooklyn, USA", creator="Michael McIlroy", bar="Milk & Honey",
    story="Michael McIlroy created the Greenpoint at Milk & Honey in 2005 as part of the 'borough Manhattans' series — a Brooklyn-Manhattan riff using Yellow Chartreuse and rye that has since become a modern standard.",
    desc="Rye Manhattan riff with Yellow Chartreuse and sweet vermouth.",
    instructions=[
        "Stir rye, vermouth, Yellow Chartreuse and bitters with ice",
        "Strain into a chilled coupe",
        "Express a lemon peel and discard",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("sweet vermouth", 0.5, "oz"),
        ing("Yellow Chartreuse", 0.5, "oz"),
        ing("Angostura bitters", 1, "dash"),
        ing("orange bitters", 1, "dash"),
    ],
    glass="coupe", method="stirred", garnish="lemon peel", abv=32,
))

RECIPES.append(cocktail(
    "Red Hook",
    year=2003, region="Brooklyn, USA", creator="Vincenzo Errico", bar="Milk & Honey",
    story="Vincenzo Errico's contribution to the Milk & Honey 'borough Manhattans' canon — a Brooklyn variation using Punt e Mes and maraschino, named for the Red Hook neighborhood.",
    desc="Brooklyn riff with Punt e Mes and maraschino.",
    instructions=[
        "Stir rye, Punt e Mes, and maraschino with ice",
        "Strain into a chilled coupe",
        "Garnish with a brandied cherry",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("Punt e Mes", 0.5, "oz"),
        ing("maraschino liqueur", 0.5, "oz"),
    ],
    glass="coupe", method="stirred", garnish="brandied cherry", abv=32,
))

RECIPES.append(cocktail(
    "Little Italy",
    year=2005, region="New York, USA", creator="Audrey Saunders", bar="Pegu Club",
    story="Audrey Saunders created the Little Italy at Pegu Club, drawing on the Manhattan template with the bittersweet richness of Cynar to bridge classic and modern Italian aperitivo culture.",
    desc="Manhattan-style stir with rye, sweet vermouth and Cynar.",
    instructions=[
        "Stir rye, sweet vermouth, and Cynar with ice",
        "Strain into a chilled coupe",
        "Garnish with two brandied cherries",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("sweet vermouth", 0.75, "oz"),
        ing("Cynar", 0.5, "oz"),
    ],
    glass="coupe", method="stirred", garnish="brandied cherries", abv=27,
))

RECIPES.append(cocktail(
    "Bensonhurst",
    year=2006, region="Brooklyn, USA", creator="Chad Solomon", bar="Milk & Honey",
    story="Chad Solomon's contribution to the borough Manhattan series — a savory rye, dry vermouth, Cynar and maraschino combination named for the Brooklyn neighborhood.",
    desc="Dry, herbal rye Manhattan with Cynar and maraschino.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain into a chilled coupe",
        "Express a lemon peel over the surface",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("dry vermouth", 1, "oz"),
        ing("maraschino liqueur", 0.25, "oz"),
        ing("Cynar", 0.25, "oz"),
    ],
    glass="coupe", method="stirred", garnish="lemon peel", abv=27,
))

RECIPES.append(cocktail(
    "Bushwick",
    year=2007, region="Brooklyn, USA", creator="Phil Ward", bar="Death & Co",
    story="Phil Ward's Bushwick layered Punt e Mes and Amaro Maraschino-style notes with rye for a darker, bittersweet borough Manhattan.",
    desc="Rye Manhattan with Punt e Mes, maraschino, and Amaro Abano.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain into a chilled coupe",
        "Garnish with a brandied cherry",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("Punt e Mes", 1, "oz"),
        ing("maraschino liqueur", 1, "tsp"),
        ("Amaro Abano" if False else None) or ing("Amaro Abano", 1, "tsp"),
    ],
    glass="coupe", method="stirred", garnish="brandied cherry", abv=28,
))

RECIPES.append(cocktail(
    "Slope",
    year=2008, region="Brooklyn, USA", creator="Julie Reiner", bar="Clover Club",
    story="Julie Reiner's Slope combined rye, Punt e Mes, apricot liqueur, and bitters for one of the Brooklyn borough series' most balanced entries — a signature drink at Clover Club.",
    desc="Rye, Punt e Mes and apricot Manhattan riff.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain into a chilled coupe",
        "Garnish with a brandied cherry",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("Punt e Mes", 1, "oz"),
        ing("apricot liqueur", 0.25, "oz"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="coupe", method="stirred", garnish="brandied cherry", abv=27,
))

RECIPES.append(cocktail(
    "Cobble Hill",
    year=2007, region="Brooklyn, USA", creator="Sam Ross", bar="Milk & Honey",
    story="Sam Ross's Cobble Hill brought a vegetal twist to the borough Manhattans by muddling fresh cucumber into a rye, Amaro Montenegro and dry vermouth stir.",
    desc="Cucumber-rye Manhattan with Amaro Montenegro.",
    instructions=[
        "Muddle cucumber slices in mixing glass",
        "Add rye, Amaro Montenegro, dry vermouth and ice",
        "Stir, double strain into a coupe",
        "Garnish with a cucumber slice",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("Amaro Montenegro", 0.5, "oz"),
        ing("dry vermouth", 0.5, "oz"),
        ing("cucumber slices", 3, "each", "produce"),
    ],
    glass="coupe", method="stirred", garnish="cucumber slice", abv=26,
))

RECIPES.append(cocktail(
    "Right Hand",
    year=2007, region="New York, USA", creator="Michael McIlroy", bar="Milk & Honey",
    story="Michael McIlroy's Right Hand swapped rum into a Negroni format with Campari and chocolate bitters, producing a darker, richer aperitivo for cooler weather.",
    desc="Rum Negroni variant with Campari and chocolate bitters.",
    instructions=[
        "Stir rum, sweet vermouth, Campari and bitters with ice",
        "Strain into a chilled coupe",
        "Garnish with a brandied cherry",
    ],
    ingredients=[
        ing("aged rum", 2, "oz"),
        ing("sweet vermouth", 1, "oz"),
        ing("Campari", 1, "oz"),
        ing("chocolate bitters", 2, "dash"),
    ],
    glass="coupe", method="stirred", garnish="brandied cherry", abv=27,
))

RECIPES.append(cocktail(
    "Final Ward",
    year=2006, region="New York, USA", creator="Phil Ward", bar="Death & Co",
    story="Phil Ward's rye-based reinterpretation of the Last Word — equal parts rye, Green Chartreuse, maraschino, and lemon. A modern signature of the Death & Co repertoire.",
    desc="Equal-parts rye Last Word riff with Green Chartreuse and maraschino.",
    instructions=[
        "Shake all ingredients with ice",
        "Double strain into a chilled coupe",
        "Garnish with a brandied cherry",
    ],
    ingredients=[
        ing("rye whiskey", 0.75, "oz"),
        ing("Green Chartreuse", 0.75, "oz"),
        ing("maraschino liqueur", 0.75, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
    ],
    glass="coupe", garnish="brandied cherry", abv=26,
))

RECIPES.append(cocktail(
    "Industry Sour",
    year=2005, region="New York, USA", creator="Ted Kilgore",
    story="Ted Kilgore's Industry Sour pairs Green Chartreuse and Fernet-Branca over ice — beloved by bartenders as an end-of-shift palate cleanser, and a foundational craft-era spec.",
    desc="Bartender-favorite Fernet and Chartreuse sour.",
    instructions=[
        "Shake all ingredients with ice",
        "Strain over a large cube in a rocks glass",
    ],
    ingredients=[
        ing("Green Chartreuse", 1, "oz"),
        ing("Fernet-Branca", 1, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
    ],
    glass="rocks", abv=27,
))

RECIPES.append(cocktail(
    "Toronto",
    year=None, region="Toronto, Canada",
    story="A Fernet-laced Old Fashioned popularized in the 1920s but elevated to canon status by 2000s craft bartenders. The bittersweet Italian amaro lifts rye into something darker and more contemplative.",
    desc="Fernet-laced rye Old Fashioned, modern craft staple.",
    instructions=[
        "Stir rye, Fernet, sugar and bitters with ice",
        "Strain over a large cube in a rocks glass",
        "Express orange peel and discard",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("Fernet-Branca", 0.25, "oz"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 1, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=33,
))

RECIPES.append(cocktail(
    "Black Manhattan",
    year=2005, region="San Francisco, USA", creator="Todd Smith", bar="Bourbon & Branch",
    story="Todd Smith created the Black Manhattan at Bourbon & Branch in San Francisco around 2005, swapping sweet vermouth for Averna amaro. It is widely cited as one of the most influential modern Manhattan variants.",
    desc="Manhattan with Averna amaro in place of sweet vermouth.",
    instructions=[
        "Stir rye, Averna and bitters with ice",
        "Strain into a chilled coupe",
        "Garnish with a brandied cherry",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("Averna amaro", 1, "oz"),
        ing("Angostura bitters", 1, "dash"),
        ing("orange bitters", 1, "dash"),
    ],
    glass="coupe", method="stirred", garnish="brandied cherry", abv=28,
))

# Strip Nones we hacked in earlier
def _clean(r):
    # Drop any None or non-dict items (artifact of inline 'or' hacks during authoring)
    r['ingredients'] = [i for i in r['ingredients'] if isinstance(i, dict)]
    return r

RECIPES = [_clean(r) for r in RECIPES]

# ============================================================================
# DEATH & CO / PHIL WARD / THOMAS WAUGH / BRIAN MILLER signatures
# ============================================================================

RECIPES.append(cocktail(
    "Oaxaca Old Fashioned",
    year=2007, region="New York, USA", creator="Phil Ward", bar="Death & Co",
    story="Phil Ward created the Oaxaca Old Fashioned at Death & Co in 2007 by combining reposado tequila with mezcal and agave, igniting the modern smoky-agave revolution. Almost every contemporary mezcal cocktail descends from this template.",
    desc="Phil Ward's tequila-mezcal Old Fashioned — the modern mezcal benchmark.",
    instructions=[
        "Stir tequila, mezcal, agave and bitters with ice",
        "Strain over a large cube in a rocks glass",
        "Flame an orange peel over the drink",
    ],
    ingredients=[
        ing("reposado tequila", 1.5, "oz"),
        ing("mezcal", 0.5, "oz"),
        ing("agave syrup", 1, "tsp", "pantry"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="rocks", method="stirred", garnish="flamed orange peel", abv=32,
))

RECIPES.append(cocktail(
    "Naked and Famous",
    year=2011, region="New York, USA", creator="Joaquín Simó", bar="Death & Co",
    story="Joaquín Simó devised the Naked and Famous at Death & Co as the love child of a Last Word and a Paper Plane — equal parts mezcal, Yellow Chartreuse, Aperol, and lime. Now a global modern standard.",
    desc="Equal-parts mezcal, Yellow Chartreuse, Aperol and lime.",
    instructions=[
        "Shake all ingredients with ice",
        "Double strain into a chilled coupe",
    ],
    ingredients=[
        ing("mezcal", 0.75, "oz"),
        ing("Yellow Chartreuse", 0.75, "oz"),
        ing("Aperol", 0.75, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
    ],
    glass="coupe", abv=22,
))

RECIPES.append(cocktail(
    "Division Bell",
    year=2010, region="New York, USA", creator="Phil Ward", bar="Mayahuel",
    story="Phil Ward created the Division Bell at his agave-focused bar Mayahuel — mezcal, Aperol, maraschino and lime. A stirred-up Last Word reinterpreted with smoke.",
    desc="Mezcal Last Word riff with Aperol and maraschino.",
    instructions=[
        "Shake all ingredients with ice",
        "Double strain into a chilled coupe",
        "Express a grapefruit peel over the drink",
    ],
    ingredients=[
        ing("mezcal", 1, "oz"),
        ing("Aperol", 0.75, "oz"),
        ing("maraschino liqueur", 0.5, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
    ],
    glass="coupe", garnish="grapefruit peel", abv=22,
))

RECIPES.append(cocktail(
    "Maximilian Affair",
    year=2008, region="New York, USA", creator="Misty Kalkofen",
    story="Boston bartender Misty Kalkofen designed the Maximilian Affair to bridge mezcal with St-Germain and Punt e Mes, named after the doomed emperor of Mexico.",
    desc="Mezcal, St-Germain, Punt e Mes and lemon.",
    instructions=[
        "Shake all ingredients with ice",
        "Double strain into a chilled coupe",
        "Express a lemon peel and discard",
    ],
    ingredients=[
        ing("mezcal", 1.5, "oz"),
        ("St-Germain elderflower liqueur"),
        ing("St-Germain elderflower liqueur", 0.75, "oz"),
        ing("Punt e Mes", 0.5, "oz"),
        ing("fresh lemon juice", 0.25, "oz", "produce"),
    ],
    glass="coupe", garnish="lemon peel", abv=24,
))

RECIPES.append(cocktail(
    "Conference",
    year=2010, region="New York, USA", creator="Brian Miller", bar="Death & Co",
    story="Brian Miller's Conference unites four brown spirits — bourbon, rye, cognac and apple brandy — over Angostura, in a multi-base Old Fashioned that became one of Death & Co's most-ordered drinks.",
    desc="Brian Miller's four-spirit Old Fashioned.",
    instructions=[
        "Stir all spirits with bitters and sugar over ice",
        "Strain over a large cube in a rocks glass",
        "Express orange peel over the drink",
    ],
    ingredients=[
        ing("bourbon", 0.5, "oz"),
        ing("rye whiskey", 0.5, "oz"),
        ing("cognac", 0.5, "oz"),
        ing("apple brandy", 0.5, "oz"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
        ing("orange bitters", 1, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=33,
))

RECIPES.append(cocktail(
    "Stone Raft",
    year=2010, region="New York, USA", creator="Phil Ward", bar="Mayahuel",
    story="Phil Ward's Stone Raft combined mezcal with Lillet Blanc and yellow Chartreuse for a softer, more aromatic agave aperitif.",
    desc="Mezcal, Lillet Blanc and Yellow Chartreuse aperitif.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain into a chilled coupe",
        "Express grapefruit peel over the drink",
    ],
    ingredients=[
        ing("mezcal", 1.5, "oz"),
        ing("Lillet Blanc", 0.75, "oz"),
        ing("Yellow Chartreuse", 0.5, "oz"),
        ing("orange bitters", 1, "dash"),
    ],
    glass="coupe", method="stirred", garnish="grapefruit peel", abv=24,
))

RECIPES.append(cocktail(
    "Mezcal Mule",
    year=None, region="USA",
    story="A modern smoky reimagining of the Moscow Mule, swapping vodka for mezcal. Popularized across craft bars during the 2010s mezcal boom.",
    desc="Smoky mezcal mule with ginger beer and lime.",
    instructions=[
        "Build mezcal, lime juice and agave in a copper mug",
        "Add ice and top with ginger beer",
        "Garnish with lime wedge and mint",
    ],
    ingredients=[
        ing("mezcal", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("agave syrup", 0.5, "oz", "pantry"),
        ing("ginger beer", 4, "oz"),
    ],
    glass="copper mug", method="built", garnish="lime wedge and mint", abv=11,
))

RECIPES.append(cocktail(
    "Mezcal Negroni",
    year=None, region="USA",
    story="A modern Negroni variation popularized by craft bartenders in the late 2000s, swapping gin for smoky mezcal. Now an off-menu standard at agave-friendly bars.",
    desc="Mezcal in place of gin in a classic Negroni build.",
    instructions=[
        "Stir mezcal, Campari, and sweet vermouth with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with an orange peel",
    ],
    ingredients=[
        ing("mezcal", 1, "oz"),
        ing("Campari", 1, "oz"),
        ing("sweet vermouth", 1, "oz"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=24,
))

RECIPES.append(cocktail(
    "Mezcal Last Word",
    year=None, region="USA",
    story="A late-2000s mezcal swap on the 1930s Last Word, replacing gin with smoky agave for an entirely new aromatic dimension.",
    desc="Smoky mezcal version of the Last Word.",
    instructions=[
        "Shake all ingredients with ice",
        "Double strain into a chilled coupe",
    ],
    ingredients=[
        ing("mezcal", 0.75, "oz"),
        ing("Green Chartreuse", 0.75, "oz"),
        ing("maraschino liqueur", 0.75, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
    ],
    glass="coupe", abv=22,
))

RECIPES.append(cocktail(
    "Bone Dry Margarita",
    year=2008, region="USA", creator="Junior Merino",
    story="A bone-dry, savory take on the Margarita popularized in the late 2000s — no orange liqueur, just tequila, lime and salt.",
    desc="Tequila, lime, salt — no orange liqueur.",
    instructions=[
        "Shake tequila and lime with ice",
        "Strain into a salted-rim rocks glass over fresh ice",
        "Garnish with a lime wheel",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("fresh lime juice", 1, "oz", "produce"),
        ing("salt", 1, "pinch", "pantry"),
    ],
    glass="rocks", garnish="lime wheel", abv=27,
))

RECIPES.append(cocktail(
    "Smoky Margarita",
    year=None, region="USA",
    story="The smoky Margarita rose to ubiquity in the 2010s, replacing some or all of the tequila with mezcal. A defining drink of the modern agave craft era.",
    desc="Margarita made with mezcal in place of tequila.",
    instructions=[
        "Shake mezcal, lime, agave and orange liqueur with ice",
        "Strain over fresh ice in a salt-rimmed rocks glass",
        "Garnish with a lime wheel",
    ],
    ingredients=[
        ing("mezcal", 2, "oz"),
        ing("fresh lime juice", 1, "oz", "produce"),
        ing("orange liqueur", 0.5, "oz"),
        ing("agave syrup", 0.25, "oz", "pantry"),
    ],
    glass="rocks", garnish="lime wheel and salt rim", abv=24,
))

RECIPES.append(cocktail(
    "Pepino",
    year=2007, region="New York, USA", creator="Julie Reiner",
    story="Julie Reiner's cucumber tequila cooler became a hot-weather standard in NYC craft bars, balancing fresh cucumber, agave and lime.",
    desc="Cucumber-jalapeño tequila cooler.",
    instructions=[
        "Muddle cucumber slices and jalapeño rings in shaker",
        "Add tequila, lime, agave, ice; shake hard",
        "Double strain over fresh ice in a rocks glass",
        "Garnish with a cucumber wheel",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("agave syrup", 0.5, "oz", "pantry"),
        ing("cucumber slices", 4, "each", "produce"),
        ing("jalapeño rings", 2, "each", "produce"),
    ],
    glass="rocks", garnish="cucumber wheel", abv=22,
))

# ============================================================================
# PDT (Jim Meehan) signatures
# ============================================================================

RECIPES.append(cocktail(
    "Benton's Old Fashioned",
    year=2007, region="New York, USA", creator="Don Lee", bar="PDT",
    story="Don Lee's bacon-fat-washed bourbon Old Fashioned at PDT pioneered the modern fat-washing technique. The smoky-savory bourbon paired with maple syrup became a touchstone for culinary cocktails.",
    desc="Bacon-fat-washed bourbon Old Fashioned with maple syrup.",
    instructions=[
        "Stir bacon-fat-washed bourbon, maple syrup, and bitters with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with an orange twist",
    ],
    ingredients=[
        ing("bacon-fat-washed bourbon", 2, "oz"),
        ing("maple syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange twist", abv=32,
))

RECIPES.append(cocktail(
    "Mezcal Mule (PDT)",
    year=2008, region="New York, USA", creator="Jim Meehan", bar="PDT",
    story="Jim Meehan's PDT version of the Mezcal Mule blended mezcal with lime, agave and ginger beer for a smoky, refreshing summer drink.",
    desc="PDT-spec smoky mule with mezcal and ginger beer.",
    instructions=[
        "Build in a copper mug with ice",
        "Top with ginger beer",
        "Garnish with candied ginger",
    ],
    ingredients=[
        ing("mezcal", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("agave syrup", 0.5, "oz", "pantry"),
        ing("ginger beer", 4, "oz"),
    ],
    glass="copper mug", method="built", garnish="candied ginger", abv=11,
))

RECIPES.append(cocktail(
    "Mata Hari",
    year=2007, region="New York, USA", creator="Audrey Saunders", bar="Pegu Club",
    story="Audrey Saunders' Mata Hari paired cognac with chai-infused vermouth and pomegranate, evoking Eastern spice-route mystery.",
    desc="Cognac with chai-vermouth and pomegranate.",
    instructions=[
        "Shake cognac, chai-spiced vermouth, lemon and pomegranate with ice",
        "Double strain into a coupe",
    ],
    ingredients=[
        ing("cognac", 1.5, "oz"),
        ing("chai-spiced sweet vermouth", 0.75, "oz"),
        ing("pomegranate juice", 0.75, "oz", "produce"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
    ],
    glass="coupe", abv=18,
))

RECIPES.append(cocktail(
    "Earl Grey MarTEAni",
    year=2003, region="New York, USA", creator="Audrey Saunders", bar="Bemelmans Bar",
    story="Audrey Saunders developed the Earl Grey MarTEAni at Bemelmans Bar by infusing gin with Earl Grey tea, then shaking with lemon, sugar and egg white. It became one of the most influential modern tea cocktails.",
    desc="Earl-Grey-infused gin sour with egg white.",
    instructions=[
        "Dry-shake Earl Grey gin, lemon, syrup and egg white",
        "Add ice and shake again",
        "Double strain into a sugar-rimmed coupe",
        "No garnish",
    ],
    ingredients=[
        ing("Earl Grey-infused gin", 1.5, "oz"),
        ing("fresh lemon juice", 1, "oz", "produce"),
        ing("simple syrup", 1, "oz", "pantry"),
        ing("egg white", 1, "each", "dairy"),
    ],
    glass="coupe", garnish="sugar rim", abv=14,
))

RECIPES.append(cocktail(
    "Old Cuban",
    year=2001, region="New York, USA", creator="Audrey Saunders", bar="Bemelmans Bar",
    story="Audrey Saunders' Old Cuban marries the mojito with the French 75 — aged rum, mint, lime and sugar topped with champagne. A crossover classic of the modern era.",
    desc="Aged rum mojito topped with champagne.",
    instructions=[
        "Muddle mint with simple syrup in a shaker",
        "Add rum, lime, bitters and ice; shake hard",
        "Double strain into a coupe",
        "Top with champagne and garnish with a mint sprig",
    ],
    ingredients=[
        ing("aged rum", 1.5, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 1, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
        ing("mint leaves", 6, "each", "produce"),
        ing("champagne", 2, "oz"),
    ],
    glass="coupe", garnish="mint sprig", abv=18,
))

RECIPES.append(cocktail(
    "Gin Gin Mule",
    year=2000, region="New York, USA", creator="Audrey Saunders",
    story="Audrey Saunders created the Gin Gin Mule as her signature drink, splicing the Mojito and Moscow Mule with London dry gin. A foundational drink of the modern craft era.",
    desc="Gin, mint, lime, ginger beer — Mojito-Mule hybrid.",
    instructions=[
        "Muddle mint with simple syrup in shaker",
        "Add gin, lime, ginger beer and ice; shake briefly",
        "Strain into a Collins glass over fresh ice",
        "Top with more ginger beer; garnish with mint and lime",
    ],
    ingredients=[
        ing("London dry gin", 1.75, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 1, "oz", "pantry"),
        ing("ginger beer", 1, "oz"),
        ing("mint leaves", 6, "each", "produce"),
    ],
    glass="collins", garnish="mint sprig and lime wedge", abv=14,
))

RECIPES.append(cocktail(
    "Tantris Sidecar",
    year=2007, region="New York, USA", creator="Audrey Saunders", bar="Pegu Club",
    story="Audrey Saunders' Tantris Sidecar layered apple brandy and gin into the Sidecar template with Yellow Chartreuse and absinthe — among the more aromatically complex Pegu Club creations.",
    desc="Cognac sidecar layered with apple brandy, gin, Chartreuse and absinthe.",
    instructions=[
        "Shake all ingredients with ice",
        "Double strain into a chilled coupe",
        "Express a lemon peel over the drink",
    ],
    ingredients=[
        ing("cognac", 0.75, "oz"),
        ing("apple brandy", 0.75, "oz"),
        ing("gin", 0.75, "oz"),
        ("Yellow Chartreuse"),
        ing("Yellow Chartreuse", 0.5, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("orange liqueur", 0.25, "oz"),
        ing("absinthe", 1, "dash"),
    ],
    glass="coupe", garnish="lemon peel", abv=28,
))

RECIPES.append(cocktail(
    "Intro to Aperol",
    year=2008, region="New York, USA", creator="Phil Ward", bar="Death & Co",
    story="Phil Ward's gateway Aperol cocktail — designed to introduce skeptical drinkers to bittersweet aperitivo with reposado tequila and lime cordial.",
    desc="Reposado tequila with Aperol and lime cordial.",
    instructions=[
        "Shake tequila, Aperol and lime cordial with ice",
        "Strain into a chilled coupe",
    ],
    ingredients=[
        ing("reposado tequila", 1.5, "oz"),
        ing("Aperol", 0.75, "oz"),
        ing("lime cordial", 0.5, "oz", "pantry"),
    ],
    glass="coupe", abv=21,
))

RECIPES = [_clean(r) for r in RECIPES]

# ============================================================================
# TIKI REVIVAL — Beachbum Berry / Smuggler's Cove / Latitude 29 / modern tiki
# ============================================================================

RECIPES.append(cocktail(
    "Three Dots and a Dash",
    year=2013, region="USA", creator="Don the Beachcomber (revived)",
    story="A 1940s Don the Beachcomber recipe rediscovered and popularized by Beachbum Berry in the 2000s, named after the Morse code for 'V' for victory. The Three Dots and a Dash bar in Chicago made it a modern tiki standard.",
    desc="Aged rhum, lime, falernum, honey and orange — wartime tiki classic.",
    instructions=[
        "Shake all ingredients with crushed ice",
        "Pour unstrained into a footed pilsner",
        "Garnish with three cherries and a pineapple-leaf 'dash'",
    ],
    ingredients=[
        ing("aged rhum agricole", 1.5, "oz"),
        ing("aged Demerara rum", 0.5, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("fresh orange juice", 0.5, "oz", "produce"),
        ing("falernum", 0.5, "oz"),
        ing("honey syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 1, "dash"),
    ],
    glass="footed pilsner", garnish="3 cherries and pineapple leaf", abv=18,
))

RECIPES.append(cocktail(
    "Saturn",
    year=1967, region="USA", creator="J. Popo Galsini",
    story="J. Popo Galsini won the IBA's 1967 World Cocktail Championship with the Saturn — gin, lemon, falernum, passion fruit and orgeat. Largely forgotten, it was rediscovered by Beachbum Berry in the 2000s and now ranks among the most important tropical gin cocktails.",
    desc="Gin tiki with passion fruit, falernum and orgeat.",
    instructions=[
        "Blend all ingredients with crushed ice for 5 seconds",
        "Pour into a hurricane glass",
        "Garnish with a lemon-peel 'Saturn ring'",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("passion fruit syrup", 0.5, "oz", "pantry"),
        ing("falernum", 0.25, "oz"),
        ing("orgeat", 0.25, "oz", "pantry"),
    ],
    glass="hurricane", method="blended", garnish="lemon peel ring", abv=14,
))

RECIPES.append(cocktail(
    "Hotel Nacional Special",
    year=None, region="Havana, Cuba",
    story="A late-1930s Cuban classic from the Hotel Nacional in Havana — white rum, apricot, pineapple and lime. Rediscovered by Charles H. Baker and revived by modern tiki bartenders.",
    desc="Cuban rum, apricot, pineapple and lime.",
    instructions=[
        "Shake all ingredients with ice",
        "Double strain into a chilled coupe",
        "Garnish with a pineapple wedge",
    ],
    ingredients=[
        ing("white rum", 2, "oz"),
        ing("apricot liqueur", 0.5, "oz"),
        ing("fresh pineapple juice", 0.75, "oz", "produce"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
    ],
    glass="coupe", garnish="pineapple wedge", abv=20,
))

RECIPES.append(cocktail(
    "Test Pilot",
    year=1941, region="USA", creator="Don the Beachcomber",
    story="Don the Beachcomber's 1941 Test Pilot fuses three rums with falernum, lime, Cointreau and bitters. A foundational tiki recipe rebuilt to spec by Beachbum Berry's research.",
    desc="Three-rum tiki with falernum, lime and Cointreau.",
    instructions=[
        "Shake all ingredients with crushed ice",
        "Pour unstrained into a double rocks glass",
        "Garnish with a mint sprig",
    ],
    ingredients=[
        ing("aged Jamaican rum", 1.5, "oz"),
        ing("light Puerto Rican rum", 0.75, "oz"),
        ing("Cointreau", 0.5, "oz"),
        ing("falernum", 0.5, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("Angostura bitters", 1, "dash"),
        ing("absinthe", 1, "dash"),
    ],
    glass="double rocks", garnish="mint sprig", abv=22,
))

RECIPES.append(cocktail(
    "Nui Nui",
    year=1937, region="USA", creator="Don the Beachcomber",
    story="A 1937 Don the Beachcomber creation — aged rum with allspice dram, vanilla syrup, cinnamon syrup, lime and orange. The Nui Nui became a holy grail recipe before Beachbum Berry decoded it.",
    desc="Spiced aged-rum tiki with allspice, vanilla and cinnamon.",
    instructions=[
        "Shake all ingredients with crushed ice",
        "Pour into a tiki mug filled with crushed ice",
        "Garnish with a cinnamon stick and orange peel",
    ],
    ingredients=[
        ing("aged Demerara rum", 2, "oz"),
        ing("fresh orange juice", 0.75, "oz", "produce"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("vanilla syrup", 0.5, "oz", "pantry"),
        ing("cinnamon syrup", 0.5, "oz", "pantry"),
        ing("allspice dram", 0.25, "oz"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="tiki mug", garnish="cinnamon stick and orange peel", abv=18,
))

RECIPES.append(cocktail(
    "Cobra's Fang",
    year=1937, region="USA", creator="Don the Beachcomber",
    story="A wartime Don the Beachcomber Tiki classic resurrected by Beachbum Berry — Demerara rum, passion fruit, lime, falernum, absinthe and bitters. The 'fang' is the absinthe sting.",
    desc="Demerara rum tiki with passion fruit and absinthe sting.",
    instructions=[
        "Shake all ingredients with crushed ice",
        "Pour into a footed pilsner with extra crushed ice",
        "Garnish with mint and a paper umbrella",
    ],
    ingredients=[
        ing("Demerara rum", 1.5, "oz"),
        ing("fresh orange juice", 1, "oz", "produce"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("passion fruit syrup", 0.75, "oz", "pantry"),
        ing("falernum", 0.25, "oz"),
        ing("absinthe", 4, "dash"),
        ing("Angostura bitters", 1, "dash"),
    ],
    glass="footed pilsner", garnish="mint sprig", abv=18,
))

RECIPES.append(cocktail(
    "Pearl Diver",
    year=1941, region="USA", creator="Don the Beachcomber",
    story="A Don the Beachcomber original combining gold rum with the namesake Pearl Diver's Mix — honey, butter, vanilla, cinnamon and allspice — for a uniquely warm, spiced sour.",
    desc="Tiki classic with honey-butter Pearl Diver's mix.",
    instructions=[
        "Shake all ingredients with crushed ice",
        "Pour unstrained into a tiki mug",
        "Garnish with mint and a paper orchid",
    ],
    ingredients=[
        ing("gold Puerto Rican rum", 1.25, "oz"),
        ing("aged Jamaican rum", 1.25, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("fresh orange juice", 0.75, "oz", "produce"),
        ing("Pearl Diver's mix (honey-butter-vanilla-spice)", 1, "oz", "pantry"),
    ],
    glass="tiki mug", garnish="mint sprig", abv=22,
))

RECIPES.append(cocktail(
    "Donn's Mix",
    year=None, region="USA", creator="Don the Beachcomber",
    story="Donn's Mix — equal parts grapefruit juice and cinnamon syrup — is the building block of multiple Don the Beachcomber tiki originals including the Navy Grog and Three Dots and a Dash.",
    desc="Grapefruit-cinnamon syrup base used in Don the Beachcomber tiki specs.",
    instructions=[
        "Combine 2 parts fresh grapefruit juice with 1 part cinnamon syrup",
        "Shake to combine and refrigerate up to 1 week",
    ],
    ingredients=[
        ing("fresh grapefruit juice", 4, "oz", "produce"),
        ing("cinnamon syrup", 2, "oz", "pantry"),
    ],
    glass="bottle", method="stirred", garnish=None, abv=0,
))

RECIPES.append(cocktail(
    "Demerara Dry Float",
    year=2007, region="USA", creator="Beachbum Berry",
    story="Beachbum Berry's Demerara Dry Float reverse-engineered from a 1953 menu — gold rum, lime, grapefruit, falernum, with a high-proof Demerara rum float on top.",
    desc="Tiki sour with high-proof Demerara rum float.",
    instructions=[
        "Shake gold rum, lime, grapefruit and falernum with crushed ice",
        "Pour into a double rocks glass",
        "Float overproof Demerara rum on top",
    ],
    ingredients=[
        ing("gold rum", 1.5, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("fresh grapefruit juice", 0.5, "oz", "produce"),
        ing("falernum", 0.5, "oz"),
        ing("overproof Demerara rum", 0.5, "oz"),
    ],
    glass="double rocks", garnish=None, abv=24,
))

RECIPES.append(cocktail(
    "151 Swizzle",
    year=None, region="Caribbean",
    story="A high-octane swizzle built around 151-proof Demerara rum, lime, falernum and Angostura. Often credited to Trader Vic, refined in modern tiki revival bars.",
    desc="Overproof rum swizzle with falernum and Angostura.",
    instructions=[
        "Build all ingredients in a Collins glass with crushed ice",
        "Swizzle with a swizzle stick until heavily frosted",
        "Top with more crushed ice and dust with bitters",
    ],
    ingredients=[
        ing("overproof Demerara rum", 1.5, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("falernum", 0.75, "oz"),
        ing("Angostura bitters", 6, "dash"),
    ],
    glass="collins", method="swizzled", garnish="Angostura dust", abv=30,
))

RECIPES.append(cocktail(
    "Queen's Park Swizzle",
    year=None, region="Trinidad",
    story="A swizzle from the Queen's Park Hotel in Port of Spain, Trinidad — aged rum, mint, lime, sugar and Angostura. Brought back into US craft canon during the 2000s tiki revival.",
    desc="Trinidadian mint-rum swizzle with Angostura crown.",
    instructions=[
        "Muddle mint with sugar and lime in a Collins glass",
        "Add aged rum and crushed ice; swizzle briskly",
        "Top with more crushed ice and crown with Angostura bitters",
    ],
    ingredients=[
        ing("aged Demerara rum", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("mint leaves", 8, "each", "produce"),
        ing("Angostura bitters", 6, "dash"),
    ],
    glass="collins", method="swizzled", garnish="mint sprig", abv=22,
))

RECIPES.append(cocktail(
    "Painkiller",
    year=1971, region="British Virgin Islands", creator="Daphne Henderson", bar="Soggy Dollar Bar",
    story="Daphne Henderson invented the Painkiller at the Soggy Dollar Bar on Jost Van Dyke. The recipe was later trademarked and standardized by Pusser's Rum, who built the modern brand around it.",
    desc="Pusser's rum, pineapple, orange, coconut cream tropical.",
    instructions=[
        "Shake all ingredients with ice",
        "Pour into a snifter or rocks glass over fresh ice",
        "Dust with fresh nutmeg",
    ],
    ingredients=[
        ing("Pusser's navy rum", 2, "oz"),
        ing("pineapple juice", 4, "oz", "produce"),
        ing("fresh orange juice", 1, "oz", "produce"),
        ing("cream of coconut", 1, "oz", "pantry"),
    ],
    glass="snifter", garnish="grated nutmeg", abv=10,
))

RECIPES.append(cocktail(
    "Hotel Nacional",
    year=None, region="Havana, Cuba",
    story="A second variation on the Hotel Nacional Special with a heavier pineapple and apricot character. Standard at modern Cuban-leaning bars.",
    desc="Apricot-pineapple Cuban rum sour.",
    instructions=[
        "Shake all with ice",
        "Strain into a coupe",
        "Garnish with a pineapple leaf",
    ],
    ingredients=[
        ing("white rum", 1.5, "oz"),
        ing("apricot brandy", 0.5, "oz"),
        ing("pineapple juice", 1, "oz", "produce"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
    ],
    glass="coupe", garnish="pineapple leaf", abv=18,
))

RECIPES.append(cocktail(
    "Missionary's Downfall",
    year=1940, region="USA", creator="Don the Beachcomber",
    story="A frozen mint-pineapple-peach tiki cooler from Don the Beachcomber, beloved for its garden-fresh aroma. Rediscovered by Beachbum Berry's 2007 'Sippin' Safari'.",
    desc="Frozen tiki cooler with mint, peach and pineapple.",
    instructions=[
        "Blend all ingredients with crushed ice for 8 seconds",
        "Pour into a chilled cocktail glass",
        "Garnish with a mint sprig",
    ],
    ingredients=[
        ing("light rum", 1.5, "oz"),
        ing("peach brandy", 0.5, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("pineapple chunks", 4, "each", "produce"),
        ing("mint leaves", 12, "each", "produce"),
        ing("honey syrup", 0.5, "oz", "pantry"),
    ],
    glass="coupe", method="blended", garnish="mint sprig", abv=14,
))

RECIPES.append(cocktail(
    "Sidewinder's Fang",
    year=1968, region="USA", creator="Trader Vic",
    story="Trader Vic's tropical sour combining aged rum with passion fruit, lime, orange and a club-soda lift. A late-tiki-era Vic creation.",
    desc="Tropical aged-rum cooler with passion fruit and soda.",
    instructions=[
        "Shake all but soda with ice",
        "Strain into a Collins glass over fresh ice",
        "Top with club soda and a pineapple wedge",
    ],
    ingredients=[
        ing("aged rum", 2, "oz"),
        ing("fresh orange juice", 1, "oz", "produce"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("passion fruit syrup", 0.75, "oz", "pantry"),
        ing("club soda", 2, "oz"),
    ],
    glass="collins", garnish="pineapple wedge", abv=14,
))

RECIPES.append(cocktail(
    "Hurricane",
    year=1940, region="New Orleans, USA", bar="Pat O'Brien's",
    story="Created at Pat O'Brien's in New Orleans in the 1940s when bar owner Pat had to take dark rum off bootleggers' hands; the lurid red passion-fruit cooler became a Bourbon Street staple.",
    desc="New Orleans dark rum classic with passion fruit.",
    instructions=[
        "Shake all ingredients with ice",
        "Pour into a hurricane glass over fresh ice",
        "Garnish with an orange wheel and cherry",
    ],
    ingredients=[
        ing("dark rum", 2, "oz"),
        ing("light rum", 2, "oz"),
        ing("passion fruit syrup", 1, "oz", "pantry"),
        ing("fresh lime juice", 1, "oz", "produce"),
        ing("fresh orange juice", 1, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
    ],
    glass="hurricane", garnish="orange wheel and cherry", abv=18,
))

RECIPES.append(cocktail(
    "Dr. Funk",
    year=None, region="Samoa",
    story="A century-old tropical drink attributed to Dr. Bernhard Funk who lived in Samoa in the early 1900s. Adapted into tiki canon by Don the Beachcomber and reproduced in modern tiki bars.",
    desc="Absinthe-laced rum tropical with grenadine and lime.",
    instructions=[
        "Shake all but soda with ice",
        "Strain into a Collins glass over fresh ice",
        "Top with club soda",
    ],
    ingredients=[
        ing("dark Jamaican rum", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("grenadine", 0.5, "oz", "pantry"),
        ("absinthe"),
        ing("absinthe", 1, "dash"),
        ing("club soda", 2, "oz"),
    ],
    glass="collins", garnish="lime wheel", abv=18,
))

RECIPES.append(cocktail(
    "Shrunken Skull",
    year=None, region="USA",
    story="A two-rum modern tiki classic served in a shrunken-skull mug — overproof Demerara, dark Jamaican, lime and grenadine. Popular at modern tiki revival bars.",
    desc="Strong two-rum tiki with grenadine in a skull mug.",
    instructions=[
        "Shake all ingredients with crushed ice",
        "Pour unstrained into a skull tiki mug",
    ],
    ingredients=[
        ing("overproof Demerara rum", 1, "oz"),
        ing("dark Jamaican rum", 1, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("grenadine", 0.5, "oz", "pantry"),
    ],
    glass="tiki mug", garnish=None, abv=24,
))

RECIPES.append(cocktail(
    "Suffering Bastard",
    year=1942, region="Cairo, Egypt", creator="Joe Scialom", bar="Shepheard's Hotel",
    story="Joe Scialom invented the Suffering Bastard at Shepheard's Hotel in Cairo as a hangover cure for British Eighth Army officers. Revived by Beachbum Berry and modern tiki bars worldwide.",
    desc="Cognac-gin hangover cure with ginger beer and lime.",
    instructions=[
        "Build cognac, gin, lime and bitters in a Collins glass with ice",
        "Top with ginger beer",
        "Garnish with mint and an orange wheel",
    ],
    ingredients=[
        ing("cognac", 1, "oz"),
        ing("gin", 1, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("Angostura bitters", 2, "dash"),
        ing("ginger beer", 4, "oz"),
    ],
    glass="collins", method="built", garnish="mint sprig and orange wheel", abv=14,
))

RECIPES.append(cocktail(
    "Ancient Mariner",
    year=1980, region="USA", creator="Jeff Berry",
    story="Beachbum Jeff Berry's Ancient Mariner — a Mai Tai variant using dark Jamaican rum, gold Demerara rum, allspice dram and grapefruit instead of orange-curaçao. A foundational modern-tiki original.",
    desc="Mai Tai variant with allspice and grapefruit.",
    instructions=[
        "Shake all ingredients with crushed ice",
        "Pour unstrained into a double rocks glass",
        "Garnish with a mint sprig and lime shell",
    ],
    ingredients=[
        ing("aged Demerara rum", 1, "oz"),
        ing("dark Jamaican rum", 1, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("fresh grapefruit juice", 0.5, "oz", "produce"),
        ing("allspice dram", 0.25, "oz"),
        ing("simple syrup", 0.5, "oz", "pantry"),
    ],
    glass="double rocks", garnish="mint sprig and lime shell", abv=22,
))

RECIPES.append(cocktail(
    "Latitude 29 Old Fashioned",
    year=2014, region="New Orleans, USA", creator="Beachbum Berry", bar="Latitude 29",
    story="When Beachbum Berry opened Latitude 29 in New Orleans in 2014, this rum-based Old Fashioned with allspice and orange anchored the menu — a tropical reimagining of the original.",
    desc="Aged-rum Old Fashioned with allspice dram.",
    instructions=[
        "Stir aged rum, allspice dram, simple, and bitters with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with an orange peel",
    ],
    ingredients=[
        ing("aged Jamaican rum", 2, "oz"),
        ing("allspice dram", 0.25, "oz"),
        ing("Demerara syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=32,
))

RECIPES.append(cocktail(
    "Smuggler's Cove Mai Tai",
    year=2009, region="San Francisco, USA", creator="Martin Cate", bar="Smuggler's Cove",
    story="Martin Cate's flagship Mai Tai at Smuggler's Cove uses a blend of aged Jamaican and aged Martinique rhum to recover what he believes Trader Vic was approximating in 1944.",
    desc="Modern blended-rum Mai Tai per Martin Cate's spec.",
    instructions=[
        "Shake all ingredients with crushed ice",
        "Pour unstrained into a double rocks glass",
        "Garnish with a mint sprig and spent lime shell",
    ],
    ingredients=[
        ing("aged Jamaican rum", 1, "oz"),
        ing("aged Martinique rhum", 1, "oz"),
        ing("orange curaçao", 0.5, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("orgeat", 0.25, "oz", "pantry"),
        ing("Demerara syrup", 0.25, "oz", "pantry"),
    ],
    glass="double rocks", garnish="mint sprig and lime shell", abv=22,
))

RECIPES.append(cocktail(
    "Hot Buttered Rum (Smuggler's Cove)",
    year=2009, region="San Francisco, USA", creator="Martin Cate", bar="Smuggler's Cove",
    story="Martin Cate's modernized Hot Buttered Rum at Smuggler's Cove combines aged rum with a spiced butter batter for a definitive winter tiki classic.",
    desc="Spiced-butter batter aged rum hot drink.",
    instructions=[
        "Stir batter and rum in a heated mug",
        "Top with boiling water",
        "Garnish with cinnamon stick and grated nutmeg",
    ],
    ingredients=[
        ing("aged Demerara rum", 2, "oz"),
        ing("hot buttered rum batter", 1, "tbsp", "pantry"),
        ing("boiling water", 5, "oz", "pantry"),
    ],
    glass="mug", method="hot", garnish="cinnamon stick and nutmeg", abv=12,
))

RECIPES.append(cocktail(
    "Donn Beach",
    year=2014, region="New Orleans, USA", creator="Beachbum Berry", bar="Latitude 29",
    story="Beachbum Berry's tribute drink to his mentor Donn Beach (Don the Beachcomber) — a balanced spec drawing on Don's signature ingredient palette.",
    desc="Tribute tiki to Don the Beachcomber with grapefruit and falernum.",
    instructions=[
        "Shake all with crushed ice",
        "Pour into a tiki mug filled with crushed ice",
    ],
    ingredients=[
        ing("aged Jamaican rum", 1.5, "oz"),
        ing("fresh grapefruit juice", 0.5, "oz", "produce"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("falernum", 0.5, "oz"),
        ing("cinnamon syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 1, "dash"),
    ],
    glass="tiki mug", garnish="mint sprig", abv=18,
))

RECIPES.append(cocktail(
    "Hawaiian Sunset",
    year=None, region="Hawaii, USA",
    story="A modern Hawaiian-style tiki refresher built on light rum, pineapple and grenadine for a literal sunset gradient in the glass.",
    desc="Pineapple-rum tropical with grenadine sunset.",
    instructions=[
        "Shake light rum, pineapple, and lime with ice",
        "Strain over crushed ice in a hurricane glass",
        "Drizzle grenadine for sunset effect",
    ],
    ingredients=[
        ing("light rum", 2, "oz"),
        ing("pineapple juice", 3, "oz", "produce"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("grenadine", 0.5, "oz", "pantry"),
    ],
    glass="hurricane", garnish="pineapple wedge", abv=10,
))

RECIPES.append(cocktail(
    "Jet Pilot",
    year=1958, region="USA", creator="Stephen Crane",
    story="Stephen Crane's Jet Pilot expanded the Test Pilot template with multiple rums, falernum, grapefruit and absinthe. Reverse-engineered by Beachbum Berry and a frequent modern tiki menu choice.",
    desc="Three-rum tiki with grapefruit and absinthe.",
    instructions=[
        "Shake all ingredients with crushed ice",
        "Pour unstrained into a double rocks glass",
        "Garnish with a mint sprig",
    ],
    ingredients=[
        ing("dark Jamaican rum", 0.75, "oz"),
        ing("gold Puerto Rican rum", 0.75, "oz"),
        ing("overproof Demerara rum", 0.75, "oz"),
        ing("fresh grapefruit juice", 0.75, "oz", "produce"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("cinnamon syrup", 0.5, "oz", "pantry"),
        ing("falernum", 0.5, "oz"),
        ing("Angostura bitters", 1, "dash"),
        ing("absinthe", 1, "dash"),
    ],
    glass="double rocks", garnish="mint sprig", abv=24,
))

RECIPES.append(cocktail(
    "Mr. Bali Hai",
    year=None, region="USA", creator="Don the Beachcomber",
    story="A late Don the Beachcomber tropical attributed to bartender Bob Esmino — gin, rum, pineapple, lime and crème de cassis served in the namesake ceramic mug.",
    desc="Gin-rum-pineapple tiki with cassis.",
    instructions=[
        "Blend all ingredients briefly with crushed ice",
        "Pour into a Mr. Bali Hai mug",
        "Garnish extravagantly",
    ],
    ingredients=[
        ing("gold rum", 1, "oz"),
        ing("gin", 1, "oz"),
        ing("pineapple juice", 1, "oz", "produce"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("crème de cassis", 0.5, "oz"),
    ],
    glass="tiki mug", method="blended", garnish="paper umbrella", abv=18,
))

RECIPES.append(cocktail(
    "Tortuga",
    year=2014, region="USA", creator="Martin Cate", bar="Smuggler's Cove",
    story="Martin Cate's two-rum tropical with cherry brandy and coffee liqueur — a richer, dessert-leaning tiki drink for cooler nights.",
    desc="Two-rum tropical with cherry brandy and coffee.",
    instructions=[
        "Shake all ingredients with crushed ice",
        "Pour into a footed pilsner",
        "Garnish with mint and cherries",
    ],
    ingredients=[
        ing("aged rum", 1.5, "oz"),
        ing("dark Jamaican rum", 0.5, "oz"),
        ing("cherry brandy", 0.5, "oz"),
        ing("coffee liqueur", 0.25, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
    ],
    glass="footed pilsner", garnish="mint and cherries", abv=20,
))

RECIPES.append(cocktail(
    "Kingston Negroni",
    year=2010, region="New York, USA", creator="Joaquín Simó", bar="Death & Co",
    story="Joaquín Simó's Kingston Negroni swaps gin for funky Smith & Cross Jamaican rum, doubling down on the bittersweet structure with hogo. A modern world standard.",
    desc="Negroni made with Smith & Cross Jamaican rum.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with an orange peel",
    ],
    ingredients=[
        ing("Smith & Cross Jamaican rum", 1, "oz"),
        ing("Campari", 1, "oz"),
        ing("sweet vermouth", 1, "oz"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=29,
))

RECIPES.append(cocktail(
    "Beachbum's Own",
    year=2010, region="USA", creator="Beachbum Berry",
    story="Beachbum Berry's signature tropical built on aged rum with falernum, lime, orange and Demerara — designed as a balanced Mai-Tai-adjacent house drink.",
    desc="Beachbum's house tropical balanced sour.",
    instructions=[
        "Shake all ingredients with crushed ice",
        "Pour unstrained into a double rocks glass",
        "Garnish with a mint sprig",
    ],
    ingredients=[
        ing("aged Jamaican rum", 2, "oz"),
        ing("falernum", 0.75, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("fresh orange juice", 0.5, "oz", "produce"),
        ing("Demerara syrup", 0.25, "oz", "pantry"),
    ],
    glass="double rocks", garnish="mint sprig", abv=20,
))

RECIPES = [_clean(r) for r in RECIPES]


def main():
    print(f"Built {len(RECIPES)} recipes so far (Part 1).")
    import importlib.util, sys, pathlib
    def load_part(path):
        spec = importlib.util.spec_from_file_location("p_" + pathlib.Path(path).stem, path)
        mod = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = mod
        spec.loader.exec_module(mod)
        return mod.RECIPES

    here = pathlib.Path(__file__).parent
    all_recipes = list(RECIPES)
    for part in ["build_modern_part2.py", "build_modern_part3.py", "build_modern_part4.py", "build_modern_part5.py", "build_modern_part6.py"]:
        p = here / part
        if p.exists():
            try:
                extra = load_part(str(p))
                all_recipes.extend(extra)
                print(f"+ {part}: {len(extra)} recipes")
            except Exception as e:
                print(f"!! {part}: {e}")

    # Load existing DB titles (lowercased) so we don't duplicate
    existing_path = os.path.expanduser('~/existing_titles.txt')
    existing = set()
    if os.path.exists(existing_path):
        with open(existing_path, 'r', encoding='utf-8') as f:
            existing = {line.strip().lower() for line in f if line.strip()}

    seen = set()
    unique = []
    skipped_existing = 0
    for r in all_recipes:
        key = r['title'].lower().strip()
        if key in seen:
            continue
        if key in existing:
            skipped_existing += 1
            continue
        # Also skip if a (cocktail) variant exists already
        if (key + ' (cocktail)') in existing or (key + ' cocktail') in existing:
            skipped_existing += 1
            continue
        seen.add(key)
        unique.append(_clean(r))
    print(f"Skipped {skipped_existing} duplicates with existing DB")

    with open(OUT, 'w', encoding='utf-8') as f:
        for r in unique:
            f.write(json.dumps(r, ensure_ascii=False) + '\n')

    print(f"Wrote {len(unique)} unique recipes to {OUT}")


if __name__ == '__main__':
    main()

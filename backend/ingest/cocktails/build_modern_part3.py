"""Part 3: More signature modern craft from named bartenders/bars."""
from build_modern_craft import cocktail, ing, _clean

RECIPES = []

# ============================================================================
# EMPLOYEES ONLY (NYC) signatures
# ============================================================================

RECIPES.append(cocktail(
    "Ginger Smash",
    year=2004, region="New York, USA", creator="Dushan Zaric", bar="Employees Only",
    story="Dushan Zaric's Ginger Smash at Employees Only — gin, fresh ginger, lime and pomegranate — became one of the bar's signature drinks and helped popularize fresh ginger root muddling.",
    desc="Gin smash with fresh ginger, lime and pomegranate.",
    instructions=[
        "Muddle fresh ginger and pomegranate in shaker",
        "Add gin, lime, simple syrup and ice; shake hard",
        "Double strain over fresh ice in a rocks glass",
        "Garnish with candied ginger",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("fresh ginger", 3, "slice", "produce"),
        ing("pomegranate seeds", 1, "tbsp", "produce"),
    ],
    glass="rocks", garnish="candied ginger", abv=22,
))

RECIPES.append(cocktail(
    "Provençal",
    year=2007, region="New York, USA", creator="Dushan Zaric", bar="Employees Only",
    story="Dushan Zaric's Provençal evokes Mediterranean coastal aromatics — gin, lavender, lemon, and Cocchi Americano — a defining herbal craft drink of the late 2000s.",
    desc="Gin, lavender and Cocchi Americano with lemon.",
    instructions=[
        "Shake all ingredients with ice",
        "Double strain into a chilled coupe",
        "Garnish with a lavender sprig",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("Cocchi Americano", 0.75, "oz"),
        ing("lavender syrup", 0.5, "oz", "pantry"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
    ],
    glass="coupe", garnish="lavender sprig", abv=18,
))

RECIPES.append(cocktail(
    "Mata Hari",
    year=2007, region="New York, USA", creator="Dushan Zaric", bar="Employees Only",
    story="Employees Only's Mata Hari was a runaway hit of the late 2000s — cognac, chai-infused vermouth, pomegranate and lemon. The deep red color and exotic spice palate made it a Manhattan signature drink.",
    desc="Cognac, chai-vermouth, pomegranate and lemon.",
    instructions=[
        "Shake all ingredients with ice",
        "Double strain into a chilled coupe",
        "Garnish with dried rose petals",
    ],
    ingredients=[
        ing("cognac", 1.5, "oz"),
        ing("chai-spiced sweet vermouth", 0.75, "oz"),
        ing("pomegranate juice", 0.75, "oz", "produce"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("simple syrup", 0.25, "oz", "pantry"),
    ],
    glass="coupe", garnish="dried rose petals", abv=18,
))

RECIPES.append(cocktail(
    "Billionaire",
    year=2006, region="New York, USA", creator="Jim Meehan", bar="Employees Only",
    story="Jim Meehan's Billionaire — a riff on the Millionaire — uses overproof bourbon, lemon, grenadine and absinthe bitters. Originally devised when he tended bar at Employees Only.",
    desc="Overproof bourbon sour with grenadine and absinthe bitters.",
    instructions=[
        "Shake all ingredients with ice",
        "Double strain into a chilled coupe",
        "Garnish with a lemon wheel",
    ],
    ingredients=[
        ing("overproof bourbon", 2, "oz"),
        ing("fresh lemon juice", 1, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("grenadine", 0.5, "oz", "pantry"),
        ing("absinthe", 1, "dash"),
    ],
    glass="coupe", garnish="lemon wheel", abv=24,
))

RECIPES.append(cocktail(
    "Ginger Lime Rickey",
    year=2008, region="New York, USA", creator="Igor Hadzismajlovic", bar="Employees Only",
    story="A modern revival of the 19th-century rickey format featuring fresh ginger and Aperol — a low-ABV daytime cooler from Employees Only.",
    desc="Fresh ginger gin rickey with Aperol.",
    instructions=[
        "Muddle ginger in shaker",
        "Add gin, Aperol, lime; shake with ice",
        "Strain into a Collins glass over fresh ice",
        "Top with soda",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("Aperol", 0.5, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("fresh ginger", 3, "slice", "produce"),
        ing("club soda", 3, "oz"),
    ],
    glass="collins", garnish="lime wedge", abv=12,
))

# ============================================================================
# NOMAD / DEAD RABBIT / TRICK DOG signatures
# ============================================================================

RECIPES.append(cocktail(
    "Dead Rabbit Irish Coffee",
    year=2013, region="New York, USA", bar="The Dead Rabbit",
    creator="Sean Muldoon and Jack McGarry",
    story="The Dead Rabbit's Irish Coffee, designed by Sean Muldoon and Jack McGarry to be the world's best, uses house-made demerara, single-origin coffee and lightly whipped cream — widely cited as the gold standard.",
    desc="The Dead Rabbit's gold-standard Irish Coffee.",
    instructions=[
        "Preheat a 6 oz goblet with hot water",
        "Empty water; add demerara syrup, brewed coffee and Irish whiskey",
        "Stir to combine",
        "Float lightly whipped cream over the back of a spoon",
    ],
    ingredients=[
        ing("Irish whiskey", 1.5, "oz"),
        ing("hot brewed coffee", 4, "oz", "pantry"),
        ing("demerara syrup", 0.5, "oz", "pantry"),
        ing("lightly whipped heavy cream", 1, "oz", "dairy"),
    ],
    glass="footed mug", method="hot", garnish=None, abv=10,
))

RECIPES.append(cocktail(
    "Tipperary",
    year=None, region="Ireland",
    story="A 1916 Hugo Ensslin original revived by craft Irish whiskey bars — equal parts Irish whiskey, sweet vermouth and Green Chartreuse.",
    desc="Equal-parts Irish whiskey, sweet vermouth and Green Chartreuse.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
        "No garnish",
    ],
    ingredients=[
        ing("Irish whiskey", 1, "oz"),
        ing("sweet vermouth", 1, "oz"),
        ing("Green Chartreuse", 1, "oz"),
    ],
    glass="coupe", method="stirred", garnish=None, abv=27,
))

RECIPES.append(cocktail(
    "NoMad Cocktail",
    year=2014, region="New York, USA", creator="Leo Robitschek", bar="NoMad Bar",
    story="Leo Robitschek's signature gin sour at the NoMad Hotel bar features house-made cinnamon syrup and lemon — a defining gin drink of NYC's mid-2010s craft scene.",
    desc="Gin sour with cinnamon syrup and grenadine.",
    instructions=[
        "Shake all ingredients with ice",
        "Double strain into a chilled coupe",
        "No garnish",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("Aperol", 0.75, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("cinnamon syrup", 0.5, "oz", "pantry"),
    ],
    glass="coupe", garnish=None, abv=18,
))

RECIPES.append(cocktail(
    "Bobby Burns",
    year=None, region="USA",
    story="A pre-Prohibition Scotch Manhattan revived in 21st-century craft bars — Scotch, sweet vermouth, and Bénédictine. The Bobby Burns has long competed with the Rob Roy as the canonical Scotch Manhattan.",
    desc="Scotch Manhattan with Bénédictine.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain into a chilled coupe",
        "Express a lemon peel and discard",
    ],
    ingredients=[
        ing("blended Scotch", 2, "oz"),
        ing("sweet vermouth", 0.75, "oz"),
        ing("Bénédictine", 0.25, "oz"),
    ],
    glass="coupe", method="stirred", garnish="lemon peel", abv=27,
))

RECIPES.append(cocktail(
    "Trick Dog Mai Tai",
    year=2013, region="San Francisco, USA", bar="Trick Dog",
    story="Trick Dog's house Mai Tai uses a complex blended-rum base and house-made orgeat to compete with Smuggler's Cove for the SF crown.",
    desc="Trick Dog's blended-rum Mai Tai with house orgeat.",
    instructions=[
        "Shake all ingredients with crushed ice",
        "Pour into a double rocks glass",
        "Garnish with mint and a lime shell",
    ],
    ingredients=[
        ing("aged Jamaican rum", 1, "oz"),
        ing("aged agricole rhum", 1, "oz"),
        ing("orange curaçao", 0.5, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("orgeat", 0.5, "oz", "pantry"),
    ],
    glass="double rocks", garnish="mint and lime shell", abv=22,
))

RECIPES.append(cocktail(
    "Pisco Inferno",
    year=2014, region="USA",
    story="A modern craft Pisco Sour variant adding muddled ají amarillo chile for a slow, building heat. Standard at Peruvian-leaning bars.",
    desc="Spicy ají amarillo Pisco Sour.",
    instructions=[
        "Muddle ají amarillo in shaker",
        "Add pisco, lime, syrup, egg white; dry-shake",
        "Add ice and shake again; double strain into a coupe",
        "Garnish with Angostura dots",
    ],
    ingredients=[
        ing("pisco", 2, "oz"),
        ing("fresh lime juice", 1, "oz", "produce"),
        ing("simple syrup", 0.75, "oz", "pantry"),
        ing("egg white", 1, "each", "dairy"),
        ing("ají amarillo paste", 1, "tsp", "pantry"),
    ],
    glass="coupe", garnish="Angostura dots", abv=20,
))

# ============================================================================
# SAM ROSS / ATTABOY / DUTCH KILLS additional
# ============================================================================

RECIPES.append(cocktail(
    "Floridita Daiquiri",
    year=None, region="Havana, Cuba",
    story="The El Floridita's no.4 Daiquiri (Hemingway Daiquiri) was reborn in 2000s craft bars; this rebuilt-spec version honors the Constantino Ribalaigua frozen original.",
    desc="Floridita-style daiquiri with fresh grapefruit and maraschino.",
    instructions=[
        "Blend all ingredients with crushed ice for 6 seconds",
        "Pour into a chilled coupe",
    ],
    ingredients=[
        ing("white rum", 2, "oz"),
        ing("fresh grapefruit juice", 0.5, "oz", "produce"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("maraschino liqueur", 0.25, "oz"),
        ing("simple syrup", 0.25, "oz", "pantry"),
    ],
    glass="coupe", method="blended", garnish=None, abv=20,
))

RECIPES.append(cocktail(
    "Dunaway",
    year=2012, region="New York, USA", creator="Sam Ross", bar="Attaboy",
    story="Sam Ross's Dunaway pairs reposado tequila with grapefruit and Aperol for a bittersweet tropical sour. A modern Attaboy menu staple.",
    desc="Reposado tequila, grapefruit and Aperol sour.",
    instructions=[
        "Shake all ingredients with ice",
        "Double strain into a chilled coupe",
        "Garnish with a grapefruit peel",
    ],
    ingredients=[
        ing("reposado tequila", 2, "oz"),
        ing("Aperol", 0.5, "oz"),
        ing("fresh grapefruit juice", 0.75, "oz", "produce"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
    ],
    glass="coupe", garnish="grapefruit peel", abv=22,
))

RECIPES.append(cocktail(
    "Wibble",
    year=1999, region="London, UK", creator="Dick Bradsell",
    story="Dick Bradsell's Wibble — gin, sloe gin, grapefruit, lemon and crème de mure — is one of the late 90s craft revival's foundational gin sours.",
    desc="Gin and sloe gin grapefruit sour with crème de mure.",
    instructions=[
        "Shake all ingredients with ice",
        "Double strain into a chilled coupe",
    ],
    ingredients=[
        ing("London dry gin", 1, "oz"),
        ing("sloe gin", 1, "oz"),
        ing("fresh pink grapefruit juice", 1, "oz", "produce"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("crème de mure", 0.25, "oz"),
        ing("simple syrup", 0.25, "oz", "pantry"),
    ],
    glass="coupe", garnish=None, abv=18,
))

RECIPES.append(cocktail(
    "Bramble",
    year=1984, region="London, UK", creator="Dick Bradsell",
    story="Dick Bradsell created the Bramble in London's Fred's Club around 1984 — gin, lemon, simple, and a drizzle of crème de mure over crushed ice — and helped launch the modern British craft cocktail movement.",
    desc="Gin sour over crushed ice with a crème de mure bleed.",
    instructions=[
        "Shake gin, lemon and syrup with ice",
        "Strain over crushed ice in a rocks glass",
        "Drizzle crème de mure over the ice",
        "Garnish with blackberries and a lemon wheel",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("crème de mure", 0.5, "oz"),
    ],
    glass="rocks", garnish="blackberries and lemon wheel", abv=20,
))

RECIPES.append(cocktail(
    "Treacle",
    year=1991, region="London, UK", creator="Dick Bradsell",
    story="Bradsell's Treacle is a dark Jamaican rum Old Fashioned with apple juice float — a modern London riff that brought the Old Fashioned format into 1990s craft bars.",
    desc="Dark rum Old Fashioned with apple juice float.",
    instructions=[
        "Stir rum, sugar and bitters with ice",
        "Strain over a large cube in a rocks glass",
        "Float apple juice on top",
        "Garnish with an apple slice",
    ],
    ingredients=[
        ing("dark Jamaican rum", 2, "oz"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
        ing("fresh apple juice", 0.5, "oz", "produce"),
    ],
    glass="rocks", method="stirred", garnish="apple slice", abv=28,
))

RECIPES.append(cocktail(
    "Russian Spring Punch",
    year=1986, region="London, UK", creator="Dick Bradsell",
    story="Dick Bradsell's Russian Spring Punch — vodka, lemon, crème de cassis and champagne — became Britain's best-selling cocktail of the 1990s.",
    desc="Vodka, cassis and champagne sour.",
    instructions=[
        "Shake vodka, lemon, syrup and cassis with ice",
        "Strain into a flute or wine glass over ice",
        "Top with champagne",
    ],
    ingredients=[
        ing("vodka", 1.5, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("crème de cassis", 0.5, "oz"),
        ing("champagne", 2, "oz"),
    ],
    glass="flute", garnish="berries", abv=14,
))

RECIPES.append(cocktail(
    "Hedgerow Sling",
    year=2009, region="London, UK", creator="Tony Conigliaro",
    story="Tony Conigliaro's Hedgerow Sling brought British wild-fruit aromatics — sloe, blackcurrant, and hedgerow herbs — into a sling-format craft cocktail.",
    desc="Sloe gin sling with British hedgerow flavors.",
    instructions=[
        "Shake all but soda with ice",
        "Strain into a Collins glass over ice",
        "Top with soda",
        "Garnish with berries and lemon",
    ],
    ingredients=[
        ing("sloe gin", 2, "oz"),
        ing("crème de cassis", 0.25, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("club soda", 3, "oz"),
    ],
    glass="collins", garnish="berries", abv=14,
))

# ============================================================================
# CRAFT MARGARITAS, PALOMAS, ETC.
# ============================================================================

RECIPES.append(cocktail(
    "Tommy's Margarita (Modern Spec)",
    year=1990, region="San Francisco, USA", creator="Julio Bermejo", bar="Tommy's",
    story="Julio Bermejo created Tommy's Margarita at his family restaurant in San Francisco around 1990 — 100% blue agave tequila, fresh lime, and agave nectar (no orange liqueur). The new template defined modern margarita craftsmanship.",
    desc="Tequila-lime-agave Margarita without orange liqueur.",
    instructions=[
        "Shake all with ice",
        "Strain over fresh ice in a rocks glass",
        "No salt rim required",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("fresh lime juice", 1, "oz", "produce"),
        ing("agave syrup", 0.5, "oz", "pantry"),
    ],
    glass="rocks", garnish="lime wheel", abv=24,
))

RECIPES.append(cocktail(
    "Mezcal Paloma",
    year=None, region="Mexico",
    story="Modern craft bartenders' smoky Paloma — mezcal in place of tequila with fresh grapefruit, lime and a salt rim. Standard at agave-forward bars.",
    desc="Smoky Paloma with mezcal and fresh grapefruit.",
    instructions=[
        "Build mezcal, lime, and grapefruit in a Collins glass over ice",
        "Top with sparkling grapefruit soda",
        "Garnish with a salt rim and grapefruit wedge",
    ],
    ingredients=[
        ing("mezcal", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("fresh grapefruit juice", 1, "oz", "produce"),
        ("grapefruit soda"),
        ing("grapefruit soda", 3, "oz"),
    ],
    glass="collins", method="built", garnish="salt rim and grapefruit wedge", abv=14,
))

RECIPES.append(cocktail(
    "Spicy Margarita",
    year=None, region="USA",
    story="The 2010s craft spicy Margarita pairs muddled jalapeño with Tommy's Margarita base — became one of the most-ordered modern variations.",
    desc="Tommy's Margarita with muddled jalapeño.",
    instructions=[
        "Muddle jalapeño in shaker",
        "Add tequila, lime, agave and ice; shake hard",
        "Strain over fresh ice in a rocks glass with salt rim",
        "Garnish with a jalapeño slice",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("fresh lime juice", 1, "oz", "produce"),
        ing("agave syrup", 0.5, "oz", "pantry"),
        ing("jalapeño slices", 3, "each", "produce"),
    ],
    glass="rocks", garnish="salt rim and jalapeño", abv=22,
))

RECIPES.append(cocktail(
    "Cadillac Margarita",
    year=None, region="USA",
    story="The luxury 1990s Margarita upgrade — añejo tequila and Grand Marnier in place of standard tequila and triple sec. Standard at high-end Mexican restaurants.",
    desc="Añejo tequila Margarita with Grand Marnier.",
    instructions=[
        "Shake añejo tequila, Grand Marnier and lime with ice",
        "Strain over fresh ice in a salt-rimmed rocks glass",
        "Garnish with a lime wheel",
    ],
    ingredients=[
        ing("añejo tequila", 2, "oz"),
        ing("Grand Marnier", 1, "oz"),
        ing("fresh lime juice", 1, "oz", "produce"),
    ],
    glass="rocks", garnish="lime wheel and salt rim", abv=27,
))

RECIPES.append(cocktail(
    "Tequila Smash",
    year=None, region="USA",
    story="A modern smash variant built on blanco tequila — fresh mint, cucumber, lime and agave. Late-2000s craft bar standard.",
    desc="Tequila, mint, cucumber and lime smash.",
    instructions=[
        "Muddle mint and cucumber in shaker",
        "Add tequila, lime and agave; shake with ice",
        "Strain over crushed ice in a rocks glass",
        "Garnish with a mint sprig",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("agave syrup", 0.5, "oz", "pantry"),
        ing("mint leaves", 8, "each", "produce"),
        ing("cucumber slices", 3, "each", "produce"),
    ],
    glass="rocks", garnish="mint sprig", abv=22,
))

RECIPES.append(cocktail(
    "El Diablo",
    year=None, region="USA",
    story="A 1940s tequila-cassis-ginger highball revived by craft bartenders during the modern tequila renaissance.",
    desc="Tequila, ginger beer, lime and cassis.",
    instructions=[
        "Build tequila and lime in a Collins glass over ice",
        "Top with ginger beer",
        "Drizzle crème de cassis through the top",
        "Garnish with a lime wedge",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ("crème de cassis"),
        ing("crème de cassis", 0.5, "oz"),
        ing("ginger beer", 4, "oz"),
    ],
    glass="collins", method="built", garnish="lime wedge", abv=12,
))

RECIPES.append(cocktail(
    "Mexican Firing Squad",
    year=None, region="Mexico City, Mexico",
    story="A 1937 Charles H. Baker classic from Mexico City — tequila, lime, grenadine and Angostura — restored to modern craft canon.",
    desc="Tequila, lime, grenadine and Angostura.",
    instructions=[
        "Shake all ingredients with ice",
        "Strain over fresh ice in a rocks glass",
        "Garnish with a lime wheel",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("fresh lime juice", 1, "oz", "produce"),
        ing("grenadine", 0.5, "oz", "pantry"),
        ing("Angostura bitters", 4, "dash"),
    ],
    glass="rocks", garnish="lime wheel", abv=20,
))

RECIPES.append(cocktail(
    "Bandera",
    year=None, region="Mexico",
    story="The Bandera ('flag') is a Mexican shot trio — sangrita, blanco tequila, and lime juice — for the green, white, red of the Mexican flag. Restored to modern bar menus during the agave craft era.",
    desc="Mexican tequila shot trio: sangrita, tequila, lime.",
    instructions=[
        "Pour each component into its own shot glass",
        "Sip alternately to taste the flag",
    ],
    ingredients=[
        ing("blanco tequila", 1, "oz"),
        ing("sangrita", 1, "oz", "pantry"),
        ing("fresh lime juice", 1, "oz", "produce"),
    ],
    glass="shot", method="built", garnish=None, abv=14,
))

RECIPES.append(cocktail(
    "Ranch Water",
    year=None, region="West Texas, USA",
    story="Ranch Water — blanco tequila, lime and Topo Chico mineral water — emerged from West Texas oil-country bars in the 1980s but became a national craft phenomenon in the late 2010s as the canned cocktail era's defining drink.",
    desc="Tequila and Topo Chico West Texas highball.",
    instructions=[
        "Pour blanco tequila and lime into a tall glass over ice",
        "Top with Topo Chico mineral water",
        "Garnish with a lime wedge",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("Topo Chico mineral water", 5, "oz"),
    ],
    glass="highball", method="built", garnish="lime wedge", abv=11,
))

# ============================================================================
# MORE MODERN GIN
# ============================================================================

RECIPES.append(cocktail(
    "Basil Smash",
    year=2008, region="Hamburg, Germany", creator="Jörg Meyer", bar="Le Lion",
    story="Jörg Meyer created the Gin Basil Smash at Le Lion in Hamburg in 2008 — gin, basil, lemon and sugar. It rapidly became a global craft standard and is the most influential modern German cocktail.",
    desc="Jörg Meyer's gin, basil and lemon smash.",
    instructions=[
        "Muddle basil leaves with lemon in shaker",
        "Add gin, simple syrup and ice; shake hard",
        "Double strain over fresh ice in a rocks glass",
        "Garnish with a basil sprig",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("basil leaves", 12, "each", "produce"),
    ],
    glass="rocks", garnish="basil sprig", abv=22,
))

RECIPES.append(cocktail(
    "Cucumber Gimlet",
    year=None, region="USA",
    story="A 2000s craft Gimlet variation built on muddled cucumber, gin, lime and simple — the vegetal freshness made it a summer standard.",
    desc="Cucumber gin Gimlet.",
    instructions=[
        "Muddle cucumber slices in shaker",
        "Add gin, lime, syrup and ice; shake hard",
        "Double strain into a chilled coupe",
        "Garnish with a cucumber slice",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("cucumber slices", 4, "each", "produce"),
    ],
    glass="coupe", garnish="cucumber slice", abv=22,
))

RECIPES.append(cocktail(
    "Last Word (Modern Spec)",
    year=None, region="USA",
    story="The 1916 Last Word vanished by mid-century and was rediscovered by Murray Stenson at Seattle's Zig Zag Cafe around 2004. His revival made it the modern era's most-imitated equal-parts template.",
    desc="Stenson-revived equal-parts gin, Chartreuse, maraschino and lime.",
    instructions=[
        "Shake all ingredients hard with ice",
        "Double strain into a chilled coupe",
    ],
    ingredients=[
        ing("London dry gin", 0.75, "oz"),
        ing("Green Chartreuse", 0.75, "oz"),
        ing("maraschino liqueur", 0.75, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
    ],
    glass="coupe", garnish=None, abv=22,
))

RECIPES.append(cocktail(
    "Aviation (Modern Spec)",
    year=None, region="USA",
    story="The Aviation, with proper crème de violette restored, returned to bar menus in the late 2000s as US Aviation gin and Rothman & Winter Crème de Violette became commercially available.",
    desc="Modern Aviation with crème de violette restored.",
    instructions=[
        "Shake gin, lemon, maraschino and crème de violette with ice",
        "Double strain into a chilled coupe",
        "Garnish with a brandied cherry",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("maraschino liqueur", 0.5, "oz"),
        ing("crème de violette", 0.25, "oz"),
    ],
    glass="coupe", garnish="brandied cherry", abv=22,
))

RECIPES.append(cocktail(
    "Aviation No. 2",
    year=None, region="USA",
    story="A modern omitted-violette Aviation variant — gin, lemon and maraschino only. Some bartenders prefer this as 'truer' to interwar bar guides.",
    desc="Aviation without crème de violette.",
    instructions=[
        "Shake gin, lemon and maraschino with ice",
        "Double strain into a chilled coupe",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("maraschino liqueur", 0.5, "oz"),
    ],
    glass="coupe", garnish="brandied cherry", abv=22,
))

RECIPES.append(cocktail(
    "Corpse Reviver No. 2 (Modern Spec)",
    year=None, region="USA",
    story="Harry Craddock's 1930 absinthe-rinsed gin sour became a modern signature drink wherever Lillet Blanc and Cocchi Americano were available — restored to canon by the 2000s craft revival.",
    desc="Equal-parts gin, Lillet, Cointreau, lemon with absinthe rinse.",
    instructions=[
        "Rinse a chilled coupe with absinthe; discard",
        "Shake gin, Lillet, Cointreau and lemon with ice",
        "Double strain into the rinsed coupe",
        "Garnish with a brandied cherry",
    ],
    ingredients=[
        ing("London dry gin", 0.75, "oz"),
        ing("Lillet Blanc", 0.75, "oz"),
        ing("Cointreau", 0.75, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("absinthe", 1, "rinse"),
    ],
    glass="coupe", garnish="brandied cherry", abv=22,
))

RECIPES.append(cocktail(
    "Jasmine",
    year=1990, region="Berkeley, USA", creator="Paul Harrington",
    story="Paul Harrington created the Jasmine in Berkeley around 1990 — a Margarita-template gin sour with Campari that splits the difference between Cosmopolitan and Negroni. A defining 1990s American craft drink.",
    desc="Gin Margarita with Campari blush.",
    instructions=[
        "Shake all ingredients with ice",
        "Strain into a chilled coupe",
        "Garnish with a lemon peel",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("Cointreau", 0.25, "oz"),
        ing("Campari", 0.25, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
    ],
    glass="coupe", garnish="lemon peel", abv=22,
))

RECIPES.append(cocktail(
    "Cucumber Collins",
    year=None, region="USA",
    story="A modern craft John Collins variant with muddled cucumber for vegetal freshness — became a summer staple at gin-focused bars in the 2010s.",
    desc="John Collins with muddled cucumber.",
    instructions=[
        "Muddle cucumber in shaker",
        "Add gin, lemon and syrup; shake with ice",
        "Strain over fresh ice in a Collins glass",
        "Top with soda; garnish with cucumber",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("cucumber slices", 4, "each", "produce"),
        ing("club soda", 3, "oz"),
    ],
    glass="collins", garnish="cucumber wheel", abv=14,
))

RECIPES.append(cocktail(
    "Floradora",
    year=None, region="USA",
    story="A 1900-era gin-raspberry-ginger highball revived by 21st-century craft bartenders as a cousin of the Gin Gin Mule.",
    desc="Gin, raspberry, lime, ginger ale highball.",
    instructions=[
        "Build gin, lime and raspberry syrup in a Collins glass over ice",
        "Top with ginger ale",
        "Garnish with raspberries and lime",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ("raspberry syrup"),
        ing("raspberry syrup", 0.5, "oz", "pantry"),
        ing("ginger ale", 4, "oz"),
    ],
    glass="collins", method="built", garnish="raspberries and lime", abv=14,
))

RECIPES.append(cocktail(
    "Southside (Modern)",
    year=None, region="New York, USA",
    story="The Southside — gin, mint, lime, sugar — came back into fashion in 2000s NY craft bars as a Mojito-like daytime drink. The 21 Club kept the tradition alive throughout the dark decades.",
    desc="Gin Mojito-style mint sour, modern craft spec.",
    instructions=[
        "Muddle mint with simple in shaker",
        "Add gin, lime and ice; shake hard",
        "Double strain into a chilled coupe",
        "Garnish with a mint sprig",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.75, "oz", "pantry"),
        ing("mint leaves", 8, "each", "produce"),
    ],
    glass="coupe", garnish="mint sprig", abv=22,
))

RECIPES.append(cocktail(
    "Bee's Knees (Modern Spec)",
    year=None, region="USA",
    story="The Prohibition-era Bee's Knees — gin, honey, lemon — was restored to canon by 2000s craft bartenders using fresh-pressed lemon and proper honey syrup. The template inspired Sam Ross's Penicillin.",
    desc="Modern gin, lemon, honey-syrup sour.",
    instructions=[
        "Shake all ingredients with ice",
        "Strain into a chilled coupe",
        "Garnish with a lemon peel",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("honey syrup", 0.75, "oz", "pantry"),
    ],
    glass="coupe", garnish="lemon peel", abv=22,
))

RECIPES.append(cocktail(
    "Maid in Cuba",
    year=None, region="USA",
    story="A modern Mojito variant adding muddled cucumber and basil for an herbaceous twist — popular at modern Cuban-revival bars.",
    desc="Mojito with cucumber and basil.",
    instructions=[
        "Muddle cucumber, basil and mint with simple in shaker",
        "Add rum, lime and ice; shake briefly",
        "Strain over crushed ice in a Collins glass",
        "Top with soda",
    ],
    ingredients=[
        ing("white rum", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("cucumber slices", 3, "each", "produce"),
        ("basil leaves"),
        ing("basil leaves", 4, "each", "produce"),
        ing("mint leaves", 6, "each", "produce"),
        ing("club soda", 2, "oz"),
    ],
    glass="collins", garnish="cucumber wheel", abv=14,
))

RECIPES = [_clean(r) for r in RECIPES]

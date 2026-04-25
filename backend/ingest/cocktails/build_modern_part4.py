"""Part 4: More tiki, brandy, sherry, vermouth-forward, fernet, amaro, vodka modern."""
from build_modern_craft import cocktail, ing, _clean

RECIPES = []

# ============================================================================
# AMARO / FERNET / VERMOUTH-FORWARD MODERN CRAFT
# ============================================================================

RECIPES.append(cocktail(
    "Amaro Sour",
    year=None, region="USA",
    story="A 2010s craft template — any amaro shaken with lemon, simple syrup and egg white. Showcases the increasing range of Italian and craft American amari.",
    desc="Generic amaro sour template with egg white.",
    instructions=[
        "Dry-shake amaro, lemon, simple and egg white",
        "Add ice and shake again",
        "Double strain into a chilled coupe",
        "Garnish with Angostura dots",
    ],
    ingredients=[
        ing("Amaro Nonino", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("egg white", 1, "each", "dairy"),
    ],
    glass="coupe", garnish="Angostura dots", abv=14,
))

RECIPES.append(cocktail(
    "Hard Start",
    year=2014, region="New York, USA", creator="Damon Boelte",
    story="Damon Boelte's Hard Start — equal parts Fernet-Branca and Branca Menta — became a bartender shift drink that crossed over into menus across America in the mid-2010s.",
    desc="Equal-parts Fernet-Branca and Branca Menta shot.",
    instructions=[
        "Pour both ingredients into a shot glass over a single ice cube",
        "Sip slowly to taste both bitterness profiles",
    ],
    ingredients=[
        ing("Fernet-Branca", 1, "oz"),
        ing("Branca Menta", 1, "oz"),
    ],
    glass="rocks", method="built", garnish=None, abv=39,
))

RECIPES.append(cocktail(
    "Toronto (Modern Spec)",
    year=None, region="Toronto, Canada",
    story="The 1948 Robert Vermeire Fernet-laced Old Fashioned has become a bartender-favorite modern stir, with rye in place of the original Canadian whisky.",
    desc="Rye Old Fashioned with a Fernet kiss.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain over a large cube in a rocks glass",
        "Express orange peel and discard",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("Fernet-Branca", 0.25, "oz"),
        ing("Demerara syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 1, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=33,
))

RECIPES.append(cocktail(
    "Stinger (Modern)",
    year=None, region="USA",
    story="The 19th-century Stinger — cognac and white crème de menthe — has been restored by modern craft bartenders as an after-dinner palate cleanser.",
    desc="Cognac and white crème de menthe digestif.",
    instructions=[
        "Stir cognac and crème de menthe with ice",
        "Strain over crushed ice in a rocks glass",
        "Garnish with a mint sprig",
    ],
    ingredients=[
        ing("cognac", 2, "oz"),
        ing("white crème de menthe", 0.5, "oz"),
    ],
    glass="rocks", method="stirred", garnish="mint sprig", abv=30,
))

RECIPES.append(cocktail(
    "Chrysanthemum",
    year=None, region="Europe",
    story="A pre-Prohibition aromatic cocktail of Bénédictine, dry vermouth and absinthe rediscovered by modern craft bartenders as a low-ABV aperitif.",
    desc="Dry vermouth, Bénédictine and absinthe aperitif.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain into a chilled coupe",
        "Express an orange peel and discard",
    ],
    ingredients=[
        ing("dry vermouth", 2, "oz"),
        ing("Bénédictine", 1, "oz"),
        ing("absinthe", 3, "dash"),
    ],
    glass="coupe", method="stirred", garnish="orange peel", abv=18,
))

RECIPES.append(cocktail(
    "Fernet Old Fashioned",
    year=None, region="USA",
    story="A modern bittersweet variant of the Old Fashioned built on Fernet-Branca with rye and demerara — a signature drink for amaro converts.",
    desc="Rye Old Fashioned with Fernet base.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain over a large cube in a rocks glass",
        "Express an orange peel",
    ],
    ingredients=[
        ing("rye whiskey", 1.5, "oz"),
        ("Fernet-Branca"),
        ing("Fernet-Branca", 0.5, "oz"),
        ing("Demerara syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=30,
))

RECIPES.append(cocktail(
    "Vieux Carré (Modern Spec)",
    year=1937, region="New Orleans, USA", creator="Walter Bergeron", bar="Hotel Monteleone",
    story="Walter Bergeron created the Vieux Carré at the Hotel Monteleone's Carousel Bar in 1937. Largely forgotten until 2000s craft bartenders restored it to canon — now a New Orleans pilgrimage drink.",
    desc="New Orleans rye, cognac, sweet vermouth and Bénédictine.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain into a rocks glass over a large cube",
        "Garnish with a brandied cherry and lemon peel",
    ],
    ingredients=[
        ing("rye whiskey", 1, "oz"),
        ing("cognac", 1, "oz"),
        ing("sweet vermouth", 1, "oz"),
        ing("Bénédictine", 0.25, "oz"),
        ing("Peychaud's bitters", 2, "dash"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="rocks", method="stirred", garnish="brandied cherry and lemon peel", abv=27,
))

RECIPES.append(cocktail(
    "Sazerac (Modern Spec)",
    year=None, region="New Orleans, USA",
    story="The Sazerac — New Orleans' official cocktail — has been restored to spec at modern craft bars: rye, sugar, Peychaud's, and an absinthe-rinsed glass. Dating to the mid-1800s, the modern revival made it a national standard again.",
    desc="Rye Old Fashioned variant with absinthe rinse.",
    instructions=[
        "Rinse a chilled rocks glass with absinthe; discard",
        "Stir rye, sugar, and Peychaud's with ice",
        "Strain into the rinsed glass (no ice)",
        "Express a lemon peel over the surface and discard",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("Peychaud's bitters", 4, "dash"),
        ing("absinthe", 1, "rinse"),
    ],
    glass="rocks", method="stirred", garnish="lemon peel", abv=33,
))

RECIPES.append(cocktail(
    "Sherry Cobbler (Modern Spec)",
    year=None, region="USA",
    story="The 19th-century Sherry Cobbler — once America's most popular drink — was reborn in 2000s craft bars as a low-ABV summer staple.",
    desc="Modern Sherry Cobbler with citrus and crushed ice.",
    instructions=[
        "Build sherry, simple, and citrus slices in a wine glass with crushed ice",
        "Stir to chill",
        "Top with more crushed ice",
        "Garnish with mint and seasonal berries",
    ],
    ingredients=[
        ing("amontillado sherry", 4, "oz"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("orange slices", 2, "each", "produce"),
        ing("lemon slices", 2, "each", "produce"),
    ],
    glass="wine", method="stirred", garnish="mint and berries", abv=10,
))

RECIPES.append(cocktail(
    "Bamboo Cocktail",
    year=None, region="Japan/USA",
    story="The 1890s Bamboo, born in Japan and brought to America, was rediscovered as the prototype low-ABV stirred drink — equal parts dry sherry and dry vermouth.",
    desc="Equal-parts dry sherry and dry vermouth low-ABV stir.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain into a chilled coupe",
        "Express a lemon peel",
    ],
    ingredients=[
        ing("amontillado sherry", 1.5, "oz"),
        ing("dry vermouth", 1.5, "oz"),
        ing("orange bitters", 1, "dash"),
        ing("Angostura bitters", 1, "dash"),
    ],
    glass="coupe", method="stirred", garnish="lemon peel", abv=14,
))

RECIPES.append(cocktail(
    "Adonis (Modern Spec)",
    year=None, region="New York, USA",
    story="The 1890s Adonis returned to modern craft menus as the low-ABV sherry-vermouth-orange-bitters stir, alongside the Bamboo, in the 2010s low-ABV revival.",
    desc="Sherry, sweet vermouth and orange bitters.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
        "Express orange peel and discard",
    ],
    ingredients=[
        ing("amontillado sherry", 1.5, "oz"),
        ing("sweet vermouth", 1.5, "oz"),
        ing("orange bitters", 2, "dash"),
    ],
    glass="coupe", method="stirred", garnish="orange peel", abv=14,
))

RECIPES.append(cocktail(
    "Tipsy Palmer",
    year=None, region="USA",
    story="A modern bourbon-and-tea Arnold Palmer riff — bourbon, lemonade, iced tea, often with mint. A 2010s craft summer porch drink.",
    desc="Bourbon Arnold Palmer with mint.",
    instructions=[
        "Build all ingredients in a Collins glass over ice",
        "Garnish with a mint sprig",
    ],
    ingredients=[
        ing("bourbon", 1.5, "oz"),
        ing("lemonade", 3, "oz", "pantry"),
        ing("iced tea", 3, "oz", "pantry"),
        ing("mint leaves", 4, "each", "produce"),
    ],
    glass="collins", method="built", garnish="mint sprig", abv=8,
))

# ============================================================================
# MORE PHIL WARD / DEATH & CO / BACK BAR catalog
# ============================================================================

RECIPES.append(cocktail(
    "Black Flip",
    year=2008, region="New York, USA", creator="Phil Ward", bar="Death & Co",
    story="Phil Ward's Black Flip combined Guinness reduction with rum, demerara, and a whole egg for one of the more unexpected modern flips.",
    desc="Guinness-rum flip with demerara and whole egg.",
    instructions=[
        "Combine all ingredients in shaker with no ice; dry-shake hard",
        "Add ice and shake again",
        "Double strain into a chilled coupe",
        "Garnish with grated nutmeg",
    ],
    ingredients=[
        ing("dark rum", 1.5, "oz"),
        ing("Guinness reduction syrup", 0.75, "oz", "pantry"),
        ing("Demerara syrup", 0.5, "oz", "pantry"),
        ing("whole egg", 1, "each", "dairy"),
    ],
    glass="coupe", garnish="grated nutmeg", abv=14,
))

RECIPES.append(cocktail(
    "Bitter Giuseppe",
    year=2007, region="Chicago, USA", creator="Stephen Cole", bar="The Violet Hour",
    story="Stephen Cole's Bitter Giuseppe at Chicago's Violet Hour — Cynar, sweet vermouth, lemon, orange bitters — became a defining low-ABV craft drink of the late 2000s.",
    desc="Cynar, sweet vermouth and lemon low-ABV stir.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Express orange peel",
    ],
    ingredients=[
        ing("Cynar", 2, "oz"),
        ing("sweet vermouth", 1, "oz"),
        ing("fresh lemon juice", 0.25, "oz", "produce"),
        ing("orange bitters", 6, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=15,
))

RECIPES.append(cocktail(
    "The Slope",
    year=None, region="Brooklyn, USA",
    story="Another borough Manhattan variant — rye, Punt e Mes, apricot liqueur, bitters — named after Park Slope, Brooklyn.",
    desc="Park Slope rye Manhattan with apricot.",
    instructions=[
        "Stir all with ice",
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
    "Carroll Gardens",
    year=2006, region="Brooklyn, USA", creator="Michael McIlroy", bar="Milk & Honey",
    story="Michael McIlroy's contribution to the borough Manhattans — rye, Punt e Mes, Amaro Nardini, maraschino, and bitters. Named for the Brooklyn neighborhood.",
    desc="Brooklyn-style rye Manhattan with Amaro Nardini.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
        "Express orange peel",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("Punt e Mes", 0.75, "oz"),
        ("Amaro Nardini"),
        ing("Amaro Nardini", 0.25, "oz"),
        ing("maraschino liqueur", 0.25, "oz"),
    ],
    glass="coupe", method="stirred", garnish="orange peel", abv=28,
))

RECIPES.append(cocktail(
    "Newark",
    year=2007, region="New York, USA", creator="Michael McIlroy", bar="Milk & Honey",
    story="Michael McIlroy's Newark applies the borough Manhattan template to apple brandy — a darker, more autumnal companion to the Greenpoint and Red Hook.",
    desc="Apple brandy Manhattan with Fernet and maraschino.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
        "Garnish with brandied cherry",
    ],
    ingredients=[
        ing("apple brandy", 2, "oz"),
        ing("sweet vermouth", 1, "oz"),
        ("Fernet-Branca"),
        ing("Fernet-Branca", 0.25, "oz"),
        ing("maraschino liqueur", 0.25, "oz"),
    ],
    glass="coupe", method="stirred", garnish="brandied cherry", abv=27,
))

RECIPES.append(cocktail(
    "Brooklyn (Modern Spec)",
    year=None, region="Brooklyn, USA",
    story="The 1908 Brooklyn — rye, dry vermouth, maraschino, Amer Picon — was reborn after Bigallet China-China and other Picon substitutes appeared in the early 2010s.",
    desc="Rye Manhattan with Amer Picon and maraschino.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
        "Express lemon peel",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("dry vermouth", 0.75, "oz"),
        ing("maraschino liqueur", 0.25, "oz"),
        ing("Amer Picon (or Bigallet China-China)", 0.25, "oz"),
    ],
    glass="coupe", method="stirred", garnish="lemon peel", abv=27,
))

RECIPES.append(cocktail(
    "Dr. Cocktail",
    year=2009, region="USA", creator="Ted Haigh",
    story="Ted Haigh ('Dr. Cocktail') created this drink to honor the bar history mission of his Vintage Spirits and Forgotten Cocktails — a balanced rum-citrus-grenadine sour.",
    desc="Aged rum, lemon and grenadine sour.",
    instructions=[
        "Shake all with ice",
        "Double strain into a chilled coupe",
        "Garnish with a brandied cherry",
    ],
    ingredients=[
        ing("aged rum", 2, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("grenadine", 0.5, "oz", "pantry"),
        ing("maraschino liqueur", 0.25, "oz"),
    ],
    glass="coupe", garnish="brandied cherry", abv=22,
))

RECIPES.append(cocktail(
    "Gunshop Fizz",
    year=2010, region="New York, USA", creator="Maks Pazuniak",
    story="Maks Pazuniak's Gunshop Fizz is a heavy-bittered Peychaud's-driven highball — Peychaud's, Aperol, lime, grapefruit, soda — over crushed ice.",
    desc="Peychaud's-led bitter highball with grapefruit and soda.",
    instructions=[
        "Build all but soda in a Collins glass with crushed ice",
        "Top with soda",
        "Garnish with a cucumber slice",
    ],
    ingredients=[
        ing("Peychaud's bitters", 1, "oz"),
        ing("Aperol", 1, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("fresh grapefruit juice", 0.5, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("club soda", 3, "oz"),
    ],
    glass="collins", method="built", garnish="cucumber slice", abv=12,
))

# ============================================================================
# MORE TIKI DEEP CUTS
# ============================================================================

RECIPES.append(cocktail(
    "Demerara Cocktail",
    year=2008, region="USA", creator="Audrey Saunders",
    story="Audrey Saunders' Demerara Cocktail is a stirred Old Fashioned showcasing aged Demerara rum, demerara syrup and bitters — an example of the modern stirred-rum movement.",
    desc="Aged Demerara rum Old Fashioned.",
    instructions=[
        "Stir rum, syrup and bitters with ice",
        "Strain over a large cube in a rocks glass",
        "Express orange peel",
    ],
    ingredients=[
        ing("aged Demerara rum", 2, "oz"),
        ing("Demerara syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
        ing("orange bitters", 1, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=33,
))

RECIPES.append(cocktail(
    "Mai Tai (Modern Spec)",
    year=1944, region="Oakland, USA", creator="Trader Vic",
    story="Trader Vic's 1944 Mai Tai — aged Jamaican rum, lime, orange curaçao, orgeat, simple — has been restored to spec by modern craft bartenders working with proper aged rum.",
    desc="Trader Vic original spec restored.",
    instructions=[
        "Shake all ingredients with crushed ice",
        "Pour unstrained into a double rocks glass",
        "Garnish with a mint sprig and spent lime shell",
    ],
    ingredients=[
        ing("aged Jamaican rum", 2, "oz"),
        ing("orange curaçao", 0.5, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("orgeat", 0.25, "oz", "pantry"),
        ing("Demerara syrup", 0.25, "oz", "pantry"),
    ],
    glass="double rocks", garnish="mint sprig and lime shell", abv=22,
))

RECIPES.append(cocktail(
    "Zombie (Modern Spec)",
    year=1934, region="USA", creator="Don the Beachcomber",
    story="Don the Beachcomber's 1934 Zombie — three rums, lime, grapefruit, falernum, absinthe, grenadine, Angostura — rebuilt to spec by Beachbum Berry's research from the original recipe cards.",
    desc="Beachbum Berry-restored 1934 Zombie spec.",
    instructions=[
        "Blend all ingredients with crushed ice for 5 seconds",
        "Pour into a tall pilsner",
        "Garnish with a mint sprig",
    ],
    ingredients=[
        ing("light Puerto Rican rum", 1.5, "oz"),
        ing("aged Jamaican rum", 1.5, "oz"),
        ing("overproof Demerara rum", 1, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("Donn's Mix (grapefruit-cinnamon)", 0.5, "oz", "pantry"),
        ing("falernum", 0.5, "oz"),
        ing("grenadine", 1, "tsp", "pantry"),
        ing("absinthe", 1, "dash"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="pilsner", method="blended", garnish="mint sprig", abv=30,
))

RECIPES.append(cocktail(
    "Navy Grog (Modern Spec)",
    year=None, region="USA", creator="Don the Beachcomber",
    story="Don the Beachcomber's mid-century Navy Grog — three rums, citrus, honey, soda — has been restored to canon at modern tiki bars, often served with an iconic ice cone.",
    desc="Three-rum tiki classic with citrus, honey and ice cone.",
    instructions=[
        "Shake all ingredients with ice",
        "Strain over crushed ice in a double rocks glass",
        "Insert a hand-shaped ice cone",
        "Garnish with a mint sprig",
    ],
    ingredients=[
        ing("light Puerto Rican rum", 1, "oz"),
        ing("aged Jamaican rum", 1, "oz"),
        ing("Demerara rum", 1, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("fresh grapefruit juice", 0.75, "oz", "produce"),
        ing("Donn's Mix", 0.5, "oz", "pantry"),
        ing("honey syrup", 0.5, "oz", "pantry"),
        ing("club soda", 1, "oz"),
    ],
    glass="double rocks", garnish="mint sprig", abv=22,
))

RECIPES.append(cocktail(
    "Trader Vic Mai Tai 1944",
    year=1944, region="Oakland, USA", creator="Trader Vic",
    story="Victor Bergeron's original 1944 Mai Tai at Trader Vic's in Oakland was created for visiting Tahitian friends. The aged Wray & Nephew Jamaican rum it called for is no longer available, but modern bartenders blend rums to approximate.",
    desc="Original 1944 spec with blended Jamaican rums.",
    instructions=[
        "Shake all ingredients with crushed ice",
        "Pour unstrained into a double rocks glass",
        "Garnish with a mint sprig and spent lime shell",
    ],
    ingredients=[
        ing("17-year Jamaican rum blend", 2, "oz"),
        ing("orange curaçao", 0.5, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("orgeat", 0.25, "oz", "pantry"),
        ing("rock candy syrup", 0.25, "oz", "pantry"),
    ],
    glass="double rocks", garnish="mint sprig", abv=22,
))

RECIPES.append(cocktail(
    "Pineapple Daiquiri",
    year=None, region="Cuba",
    story="A Cuban pineapple Daiquiri variant — fresh pineapple, white rum, lime and a small pour of simple. Modern craft bars elevate it with high-quality rum and fresh juice.",
    desc="White rum pineapple Daiquiri.",
    instructions=[
        "Shake all with ice",
        "Strain into a chilled coupe",
        "Garnish with a pineapple wedge",
    ],
    ingredients=[
        ing("white rum", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("fresh pineapple juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.25, "oz", "pantry"),
    ],
    glass="coupe", garnish="pineapple wedge", abv=22,
))

RECIPES.append(cocktail(
    "Banana Daiquiri (Modern)",
    year=None, region="Caribbean",
    story="A modern craft Daiquiri variant adding banana liqueur and a splash of orange — the proper craft version is shaken sharp, not blended.",
    desc="Shaken banana Daiquiri with white rum.",
    instructions=[
        "Shake all with ice",
        "Strain into a coupe",
        "Garnish with a banana slice",
    ],
    ingredients=[
        ing("white rum", 2, "oz"),
        ing("banana liqueur", 0.5, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.25, "oz", "pantry"),
    ],
    glass="coupe", garnish="banana slice", abv=22,
))

RECIPES.append(cocktail(
    "Coconut Daiquiri",
    year=None, region="Caribbean",
    story="A modern bartender variant — coconut cream stirred into a Daiquiri spec for tropical creaminess without crossing into the Pina Colada zone.",
    desc="White rum Daiquiri with coconut cream.",
    instructions=[
        "Shake all with ice",
        "Strain into a coupe",
        "Garnish with a toasted coconut chip",
    ],
    ingredients=[
        ing("white rum", 2, "oz"),
        ing("coconut cream", 0.5, "oz", "pantry"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.25, "oz", "pantry"),
    ],
    glass="coupe", garnish="toasted coconut chip", abv=22,
))

RECIPES.append(cocktail(
    "Hemingway Daiquiri (Modern Spec)",
    year=None, region="Havana, Cuba",
    story="Constantino Ribalaigua's no-sugar Daiquiri at El Floridita, made for diabetic Hemingway, became the modern Hemingway Daiquiri spec — rum, lime, grapefruit, maraschino, no added sugar.",
    desc="Constantino's no-sugar Hemingway Daiquiri.",
    instructions=[
        "Shake all with ice",
        "Strain into a chilled coupe",
        "Garnish with a lime wheel",
    ],
    ingredients=[
        ing("white rum", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("fresh grapefruit juice", 0.5, "oz", "produce"),
        ing("maraschino liqueur", 0.25, "oz"),
    ],
    glass="coupe", garnish="lime wheel", abv=22,
))

RECIPES.append(cocktail(
    "Spiced Pear Daiquiri",
    year=None, region="USA",
    story="A modern craft autumn Daiquiri — aged rum with pear purée and warm spice. A seasonal craft cocktail standard.",
    desc="Aged-rum Daiquiri with pear and warm spice.",
    instructions=[
        "Shake all with ice",
        "Double strain into a coupe",
        "Garnish with a pear slice",
    ],
    ingredients=[
        ing("aged rum", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ("pear purée"),
        ing("pear purée", 0.75, "oz", "produce"),
        ing("cinnamon syrup", 0.25, "oz", "pantry"),
    ],
    glass="coupe", garnish="pear slice", abv=22,
))

RECIPES.append(cocktail(
    "Airmail",
    year=1930, region="Cuba/USA",
    story="A 1930s Cuban-American invention combining rum, lime, honey and champagne — restored to canon by 2000s craft bartenders as a celebration drink.",
    desc="Rum-honey-lime topped with champagne.",
    instructions=[
        "Shake rum, lime and honey syrup with ice",
        "Strain into a flute",
        "Top with champagne",
    ],
    ingredients=[
        ing("aged rum", 1.5, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("honey syrup", 0.5, "oz", "pantry"),
        ing("champagne", 2, "oz"),
    ],
    glass="flute", garnish="lime peel", abv=14,
))

RECIPES.append(cocktail(
    "Trinidad Sour (Modern Spec)",
    year=2008, region="New York, USA", creator="Giuseppe González",
    story="Giuseppe González's Trinidad Sour stunned the cocktail world by using 1.5 oz of Angostura bitters as the base spirit — orgeat, lemon and rye balance the bracing bitterness. The most famous bitters-as-base cocktail.",
    desc="Angostura-as-base sour with orgeat and rye.",
    instructions=[
        "Shake all with ice hard",
        "Double strain into a chilled coupe",
    ],
    ingredients=[
        ing("Angostura bitters", 1.5, "oz"),
        ing("orgeat", 1, "oz", "pantry"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("rye whiskey", 0.5, "oz"),
    ],
    glass="coupe", garnish=None, abv=18,
))

RECIPES.append(cocktail(
    "Improved Whiskey Cocktail",
    year=None, region="USA",
    story="Jerry Thomas's 1862 Improved Whiskey Cocktail — rye, simple, Peychaud's, maraschino and absinthe — was restored to canon by Dale DeGroff and David Wondrich in the 2000s revival.",
    desc="Rye Old Fashioned with maraschino and absinthe.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Express lemon peel",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("maraschino liqueur", 1, "tsp"),
        ing("absinthe", 1, "dash"),
        ing("Peychaud's bitters", 2, "dash"),
    ],
    glass="rocks", method="stirred", garnish="lemon peel", abv=33,
))

RECIPES.append(cocktail(
    "Fancy Free",
    year=None, region="USA",
    story="The pre-Prohibition Fancy Free — bourbon, maraschino, two bitters — was restored by 2000s craft bartenders as a sweeter cousin of the Old Fashioned.",
    desc="Bourbon Old Fashioned with maraschino.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Express orange peel",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("maraschino liqueur", 0.5, "oz"),
        ing("Angostura bitters", 2, "dash"),
        ing("orange bitters", 2, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=30,
))

# ============================================================================
# CRAFT VODKA + LOWER-ABV
# ============================================================================

RECIPES.append(cocktail(
    "Moscow Mule (Modern Spec)",
    year=None, region="USA",
    story="The Moscow Mule, born in 1941, was reborn at 2010s craft bars with house-made ginger beer and serious copper mugs. The fresh-ginger spec sets craft versions apart from the original syrup-based drink.",
    desc="Vodka, fresh-ginger beer and lime in copper mug.",
    instructions=[
        "Build vodka and lime in a copper mug with ice",
        "Top with fresh ginger beer",
        "Garnish with a lime wedge and mint",
    ],
    ingredients=[
        ing("vodka", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("ginger beer", 4, "oz"),
    ],
    glass="copper mug", method="built", garnish="lime wedge and mint", abv=11,
))

RECIPES.append(cocktail(
    "Vesper (Modern Spec)",
    year=None, region="London, UK",
    story="Ian Fleming's 1953 Bond cocktail returned to craft bar menus when Lillet Blanc and Kina Lillet equivalents became widely available. The modern spec adjusts proportions to balance current Lillet's reduced quinine.",
    desc="Bond's gin-vodka-Lillet martini, modern spec.",
    instructions=[
        "Shake (per Bond's specification) with ice",
        "Double strain into a chilled coupe",
        "Garnish with a lemon peel",
    ],
    ingredients=[
        ing("London dry gin", 3, "oz"),
        ing("vodka", 1, "oz"),
        ing("Lillet Blanc", 0.5, "oz"),
    ],
    glass="coupe", garnish="lemon peel", abv=33,
))

RECIPES.append(cocktail(
    "Cosmopolitan (Modern Spec)",
    year=None, region="USA",
    story="The Cosmopolitan, made famous by Sex and the City, has been restored to its 1980s Cheryl Cook spec at modern craft bars — citron vodka, Cointreau, lime and a splash of cranberry.",
    desc="Modern proper-spec Cosmopolitan with citron vodka.",
    instructions=[
        "Shake all with ice",
        "Double strain into a chilled coupe",
        "Garnish with a flamed orange peel",
    ],
    ingredients=[
        ing("citron vodka", 1.5, "oz"),
        ing("Cointreau", 0.5, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("cranberry juice", 0.25, "oz", "produce"),
    ],
    glass="coupe", garnish="flamed orange peel", abv=22,
))

RECIPES.append(cocktail(
    "Lemon Drop (Modern Spec)",
    year=None, region="San Francisco, USA",
    story="Norman Jay Hobday created the Lemon Drop at Henry Africa's in San Francisco in the 1970s. Restored to craft bars with fresh lemon and a sugar rim.",
    desc="Vodka lemon sour with sugar rim.",
    instructions=[
        "Shake all with ice",
        "Double strain into a sugar-rimmed coupe",
        "Garnish with a lemon twist",
    ],
    ingredients=[
        ing("citron vodka", 2, "oz"),
        ing("triple sec", 0.5, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
    ],
    glass="coupe", garnish="sugar rim and lemon twist", abv=22,
))

RECIPES.append(cocktail(
    "Sage Sour",
    year=None, region="USA",
    story="A modern craft sour built on bourbon, fresh sage, lemon, simple and an egg white. Earthy and aromatic — a 2010s farm-to-glass standard.",
    desc="Bourbon sour with muddled fresh sage.",
    instructions=[
        "Muddle sage in shaker",
        "Add bourbon, lemon, simple, egg white; dry-shake",
        "Add ice and shake again",
        "Double strain into a chilled coupe",
        "Garnish with a sage leaf",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("egg white", 1, "each", "dairy"),
        ("sage leaves"),
        ing("sage leaves", 4, "each", "produce"),
    ],
    glass="coupe", garnish="sage leaf", abv=20,
))

RECIPES.append(cocktail(
    "Rosemary Greyhound",
    year=None, region="USA",
    story="A modern craft Greyhound — vodka, grapefruit juice — with muddled rosemary for an herbal lift. Standard at brunch-leaning craft bars.",
    desc="Vodka greyhound with muddled rosemary.",
    instructions=[
        "Muddle rosemary in shaker",
        "Add vodka, grapefruit and ice; shake briefly",
        "Strain over fresh ice in a Collins glass",
        "Garnish with a rosemary sprig",
    ],
    ingredients=[
        ing("vodka", 2, "oz"),
        ing("fresh grapefruit juice", 4, "oz", "produce"),
        ("rosemary"),
        ing("rosemary sprig", 1, "each", "produce"),
    ],
    glass="collins", garnish="rosemary sprig", abv=10,
))

RECIPES.append(cocktail(
    "Salty Dog",
    year=None, region="USA",
    story="The Salty Dog — a Greyhound with a salt rim — has roots in the 1950s and returned to bartender attention in the 2010s as a precursor to the modern Paloma.",
    desc="Salted Greyhound: vodka, grapefruit, salt rim.",
    instructions=[
        "Pour vodka and grapefruit into a salt-rimmed Collins glass over ice",
        "Stir to combine",
        "Garnish with a grapefruit wedge",
    ],
    ingredients=[
        ing("vodka", 2, "oz"),
        ing("fresh grapefruit juice", 4, "oz", "produce"),
    ],
    glass="collins", method="built", garnish="grapefruit wedge and salt rim", abv=10,
))

RECIPES.append(cocktail(
    "Bloody Maria",
    year=None, region="USA",
    story="The Bloody Maria swaps tequila for vodka in a Bloody Mary — a brunch staple of Mexican-leaning craft restaurants.",
    desc="Bloody Mary made with tequila.",
    instructions=[
        "Build all in a Collins glass with ice",
        "Roll between glasses to mix",
        "Garnish with celery, olive, lime, and pickled vegetable",
    ],
    ingredients=[
        ing("blanco tequila", 1.5, "oz"),
        ing("tomato juice", 4, "oz", "produce"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ("Worcestershire sauce"),
        ing("Worcestershire sauce", 4, "dash", "pantry"),
        ing("hot sauce", 4, "dash", "pantry"),
        ing("celery salt", 1, "pinch", "pantry"),
        ing("black pepper", 1, "pinch", "pantry"),
    ],
    glass="collins", method="built", garnish="celery and pickled vegetables", abv=8,
))

RECIPES.append(cocktail(
    "Bloody Mary (Modern Spec)",
    year=None, region="USA",
    story="The Bloody Mary's modern craft form upgrades to fresh-pressed tomato juice and house-made spice mix. Standard at brunch service nationwide.",
    desc="Modern fresh-tomato Bloody Mary.",
    instructions=[
        "Build all in a Collins glass over ice",
        "Roll between glasses to mix",
        "Garnish ostentatiously",
    ],
    ingredients=[
        ing("vodka", 1.5, "oz"),
        ing("tomato juice", 4, "oz", "produce"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("Worcestershire sauce", 4, "dash", "pantry"),
        ing("hot sauce", 4, "dash", "pantry"),
        ing("horseradish", 0.5, "tsp", "pantry"),
        ing("celery salt", 1, "pinch", "pantry"),
        ing("black pepper", 1, "pinch", "pantry"),
    ],
    glass="collins", method="built", garnish="celery, olive, pickle", abv=8,
))

RECIPES.append(cocktail(
    "Red Snapper",
    year=None, region="USA",
    story="The 1934 St. Regis Bloody Mary variant using gin instead of vodka. Restored to craft brunch menus in the 2010s as the gin-forward alternative.",
    desc="Gin Bloody Mary, the original 1934 spec.",
    instructions=[
        "Build all in a Collins glass with ice",
        "Roll between glasses",
        "Garnish with celery and lemon",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("tomato juice", 4, "oz", "produce"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("Worcestershire sauce", 3, "dash", "pantry"),
        ing("hot sauce", 3, "dash", "pantry"),
        ing("celery salt", 1, "pinch", "pantry"),
    ],
    glass="collins", method="built", garnish="celery and lemon", abv=8,
))

RECIPES.append(cocktail(
    "Michelada",
    year=None, region="Mexico",
    story="The Mexican Michelada — beer, lime, hot sauce, Worcestershire over a salt rim — became a US craft brunch staple in the 2010s after lager-and-tomato 'cubana' versions spread.",
    desc="Mexican beer cocktail with lime, salt and hot sauce.",
    instructions=[
        "Combine lime, hot sauce and Worcestershire in a salt-rimmed pint glass",
        "Add ice",
        "Top with cold Mexican lager",
        "Garnish with a lime wheel",
    ],
    ingredients=[
        ing("Mexican lager", 12, "oz"),
        ing("fresh lime juice", 1, "oz", "produce"),
        ing("hot sauce", 6, "dash", "pantry"),
        ing("Worcestershire sauce", 4, "dash", "pantry"),
    ],
    glass="pint", method="built", garnish="lime wheel and salt rim", abv=4,
))

RECIPES.append(cocktail(
    "Chelada",
    year=None, region="Mexico",
    story="The simpler Mexican beer cocktail — beer, lime, salt rim, no hot sauce. The Cubana cousin to the Michelada.",
    desc="Mexican beer cocktail with lime and salt.",
    instructions=[
        "Pour cold beer into a salt-rimmed pint glass over ice",
        "Add lime juice; stir gently",
    ],
    ingredients=[
        ing("Mexican lager", 12, "oz"),
        ing("fresh lime juice", 1, "oz", "produce"),
    ],
    glass="pint", method="built", garnish="lime wheel and salt rim", abv=4,
))

RECIPES = [_clean(r) for r in RECIPES]

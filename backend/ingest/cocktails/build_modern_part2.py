"""Part 2: Italian / Spritz family + modern named craft + sours + Japanese."""
from build_modern_craft import cocktail, ing, _clean

RECIPES = []

# ============================================================================
# ITALIAN / SPRITZ FAMILY
# ============================================================================

RECIPES.append(cocktail(
    "Hugo Spritz",
    year=2005, region="South Tyrol, Italy", creator="Roland Gruber",
    story="Roland Gruber, a bartender in Naturno, South Tyrol, created the Hugo Spritz in 2005 as a less-bitter alternative to the Aperol Spritz. Within a decade it spread across Alpine Italy and Austria, and by the late 2010s had become a global summer staple.",
    desc="Elderflower-mint prosecco spritz from South Tyrol.",
    instructions=[
        "Build St-Germain in a wine glass with mint and ice",
        "Add prosecco and a splash of soda",
        "Garnish with a mint sprig and lime wedge",
    ],
    ingredients=[
        ing("St-Germain elderflower liqueur", 1, "oz"),
        ing("prosecco", 4, "oz"),
        ing("club soda", 1, "oz"),
        ing("mint leaves", 5, "each", "produce"),
        ing("fresh lime juice", 0.25, "oz", "produce"),
    ],
    glass="wine", method="built", garnish="mint sprig", abv=8,
))

RECIPES.append(cocktail(
    "Negroni Sbagliato",
    year=1972, region="Milan, Italy", creator="Mirko Stocchetto", bar="Bar Basso",
    story="Mirko Stocchetto invented the Negroni Sbagliato ('mistaken Negroni') at Bar Basso in Milan in 1972 when he reached for prosecco instead of gin. Suddenly viral globally in 2022 after Emma D'Arcy's House of the Dragon interview.",
    desc="Negroni made with prosecco instead of gin — Bar Basso's happy accident.",
    instructions=[
        "Build Campari and sweet vermouth in a rocks glass with ice",
        "Top with prosecco",
        "Garnish with a half-orange wheel",
    ],
    ingredients=[
        ing("Campari", 1, "oz"),
        ing("sweet vermouth", 1, "oz"),
        ing("prosecco", 1, "oz"),
    ],
    glass="rocks", method="built", garnish="orange wheel", abv=14,
))

RECIPES.append(cocktail(
    "White Negroni",
    year=2002, region="Bordeaux, France", creator="Wayne Collins",
    story="British bartender Wayne Collins created the White Negroni at Vinexpo in Bordeaux in 2002, swapping Campari for Suze and sweet vermouth for Lillet Blanc. It became a French aperitivo classic and a modern bartender favorite.",
    desc="Suze and Lillet Blanc Negroni — Wayne Collins' Bordeaux invention.",
    instructions=[
        "Stir gin, Lillet Blanc and Suze with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with a grapefruit peel",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("Lillet Blanc", 1, "oz"),
        ing("Suze", 1, "oz"),
    ],
    glass="rocks", method="stirred", garnish="grapefruit peel", abv=24,
))

RECIPES.append(cocktail(
    "Cynar Negroni",
    year=None, region="USA",
    story="A 2010s craft swap on the Negroni replacing Campari with Cynar — gives a darker, vegetal, less candied bittersweet character. Standard at modern Italian-leaning bars.",
    desc="Negroni with Cynar in place of Campari.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with an orange peel",
    ],
    ingredients=[
        ing("London dry gin", 1, "oz"),
        ing("Cynar", 1, "oz"),
        ing("sweet vermouth", 1, "oz"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=24,
))

RECIPES.append(cocktail(
    "Bicicletta",
    year=None, region="Northern Italy",
    story="The Bicicletta — 'little bicycle' — was named for the unsteady cyclists riding home after one too many. A simple Northern Italian aperitif of Campari, white wine and soda.",
    desc="Northern Italian aperitif: Campari, white wine, soda.",
    instructions=[
        "Build all ingredients in a wine glass over ice",
        "Garnish with a lemon wheel",
    ],
    ingredients=[
        ing("Campari", 1.5, "oz"),
        ing("dry white wine", 3, "oz"),
        ing("club soda", 1, "oz"),
    ],
    glass="wine", method="built", garnish="lemon wheel", abv=10,
))

RECIPES.append(cocktail(
    "Garibaldi (Modern)",
    year=2011, region="New York, USA", creator="Naren Young", bar="Dante",
    story="Naren Young popularized the modern Garibaldi at Dante in NYC by 'fluffing' fresh orange juice through a high-speed juicer to give the simple two-ingredient drink an aerated body. It became Dante's signature.",
    desc="Campari and 'fluffy' orange juice — Naren Young's Dante spec.",
    instructions=[
        "Pour Campari into a tall glass over ice",
        "Top with freshly aerated orange juice",
        "Garnish with an orange wedge",
    ],
    ingredients=[
        ing("Campari", 1.5, "oz"),
        ing("fluffy fresh orange juice", 4, "oz", "produce"),
    ],
    glass="highball", method="built", garnish="orange wedge", abv=10,
))

RECIPES.append(cocktail(
    "Spagliato Spritz",
    year=None, region="Italy",
    story="A spritz-format Sbagliato — Campari, sweet vermouth, prosecco and soda over plenty of ice. Standard at modern Italian aperitivo bars.",
    desc="Spritz-format Negroni Sbagliato.",
    instructions=[
        "Build Campari and sweet vermouth in a wine glass with ice",
        "Top with prosecco and a splash of soda",
        "Garnish with an orange wheel",
    ],
    ingredients=[
        ing("Campari", 1, "oz"),
        ing("sweet vermouth", 1, "oz"),
        ing("prosecco", 2, "oz"),
        ing("club soda", 1, "oz"),
    ],
    glass="wine", method="built", garnish="orange wheel", abv=10,
))

RECIPES.append(cocktail(
    "Cynar Spritz",
    year=None, region="Italy",
    story="A modern artichoke-amaro spritz that swaps Aperol or Campari for Cynar. Drier, more vegetal, beloved by amaro aficionados.",
    desc="Spritz with Cynar in place of Aperol.",
    instructions=[
        "Build Cynar in a wine glass over ice",
        "Top with prosecco and a splash of soda",
        "Garnish with an orange wheel",
    ],
    ingredients=[
        ing("Cynar", 2, "oz"),
        ing("prosecco", 3, "oz"),
        ing("club soda", 1, "oz"),
    ],
    glass="wine", method="built", garnish="orange wheel", abv=9,
))

RECIPES.append(cocktail(
    "Select Spritz",
    year=None, region="Venice, Italy",
    story="The Venetian original spritz uses Select aperitivo, the bittersweet liqueur created in Venice in 1920. The Select Spritz predates and arguably defines the spritz format.",
    desc="Original Venetian spritz with Select aperitivo.",
    instructions=[
        "Build Select in a wine glass over ice",
        "Top with prosecco and soda",
        "Garnish with a green olive",
    ],
    ingredients=[
        ing("Select aperitivo", 2, "oz"),
        ing("prosecco", 3, "oz"),
        ing("club soda", 1, "oz"),
    ],
    glass="wine", method="built", garnish="green olive", abv=9,
))

RECIPES.append(cocktail(
    "Limoncello Spritz",
    year=None, region="Amalfi Coast, Italy",
    story="Italy's southern coast answered the spritz craze with a lemon-forward variant built on limoncello and prosecco. A summer Capri staple.",
    desc="Lemon-prosecco Amalfi spritz.",
    instructions=[
        "Build limoncello in a wine glass over ice",
        "Top with prosecco and soda",
        "Garnish with a lemon wheel and mint",
    ],
    ingredients=[
        ing("limoncello", 2, "oz"),
        ing("prosecco", 3, "oz"),
        ing("club soda", 1, "oz"),
    ],
    glass="wine", method="built", garnish="lemon wheel and mint", abv=9,
))

RECIPES.append(cocktail(
    "St-Germain Cocktail",
    year=2007, region="USA", creator="Robert J. Cooper",
    story="The St-Germain Cocktail launched alongside the elderflower liqueur in 2007 and instantly became one of the most ordered drinks of the late-2000s craft era — sometimes credited with bringing the spritz format to America.",
    desc="The drink that introduced America to elderflower spritzes.",
    instructions=[
        "Build all ingredients in a Collins glass over ice",
        "Top with a splash of soda",
        "Garnish with a lemon twist",
    ],
    ingredients=[
        ing("St-Germain elderflower liqueur", 1.5, "oz"),
        ing("dry sparkling wine", 2, "oz"),
        ing("club soda", 2, "oz"),
    ],
    glass="collins", method="built", garnish="lemon twist", abv=9,
))

RECIPES.append(cocktail(
    "Old Pal",
    year=1922, region="Paris, France", creator="Harry MacElhone",
    story="Harry MacElhone's Old Pal first appeared in Harry's New York Bar Paris in 1922 — rye, dry vermouth, and Campari. Long overshadowed by the Boulevardier, it was rediscovered by 2000s craft bartenders as an aperitivo bookend.",
    desc="Rye whiskey Negroni with dry vermouth and Campari.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain into a chilled coupe",
        "Garnish with a lemon peel",
    ],
    ingredients=[
        ing("rye whiskey", 1, "oz"),
        ing("dry vermouth", 1, "oz"),
        ing("Campari", 1, "oz"),
    ],
    glass="coupe", method="stirred", garnish="lemon peel", abv=24,
))

RECIPES.append(cocktail(
    "Boulevardier (Modern Spec)",
    year=None, region="Paris, France",
    story="The 1927 bourbon Negroni rediscovered and standardized at modern American bars in the 2000s — typically with bourbon at 1.5 oz to 1 each Campari and sweet vermouth.",
    desc="Bourbon Negroni — modern higher-spirit spec.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with an orange peel",
    ],
    ingredients=[
        ing("bourbon", 1.5, "oz"),
        ing("Campari", 1, "oz"),
        ing("sweet vermouth", 1, "oz"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=27,
))

RECIPES.append(cocktail(
    "Americano (Modern Spec)",
    year=None, region="Italy",
    story="The 19th-century Americano, restored to its modern aperitivo glory by Italian-leaning craft bars. Equal Campari and sweet vermouth lengthened with soda over plenty of ice.",
    desc="Bittersweet Italian highball: Campari, sweet vermouth, soda.",
    instructions=[
        "Build Campari and sweet vermouth in a Collins glass over ice",
        "Top with soda",
        "Garnish with a lemon peel and orange wheel",
    ],
    ingredients=[
        ing("Campari", 1.5, "oz"),
        ing("sweet vermouth", 1.5, "oz"),
        ing("club soda", 3, "oz"),
    ],
    glass="collins", method="built", garnish="lemon peel and orange wheel", abv=11,
))

RECIPES.append(cocktail(
    "Milano-Torino",
    year=None, region="Italy",
    story="The pre-Americano Italian classic — Campari from Milan and Cinzano sweet vermouth from Turin in equal parts on the rocks. Restored to modern menus by craft Italian bars.",
    desc="Campari + sweet vermouth on the rocks — pre-Americano original.",
    instructions=[
        "Build over ice in a rocks glass",
        "Garnish with an orange peel",
    ],
    ingredients=[
        ing("Campari", 1.5, "oz"),
        ing("sweet vermouth", 1.5, "oz"),
    ],
    glass="rocks", method="built", garnish="orange peel", abv=18,
))

RECIPES.append(cocktail(
    "Sgroppino",
    year=None, region="Venice, Italy",
    story="The Venetian Sgroppino combines lemon sorbet, vodka and prosecco for a digestif-style frozen palate cleanser. A standard end-of-meal drink across Veneto.",
    desc="Venetian lemon-sorbet vodka prosecco digestif.",
    instructions=[
        "Beat lemon sorbet, vodka and a splash of prosecco in a chilled bowl",
        "Pour into a coupe and top with more prosecco",
        "Garnish with mint",
    ],
    ingredients=[
        ing("lemon sorbet", 2, "scoop", "freezer"),
        ing("vodka", 1, "oz"),
        ing("prosecco", 3, "oz"),
    ],
    glass="coupe", method="beaten", garnish="mint sprig", abv=8,
))

RECIPES.append(cocktail(
    "Aperol Sour",
    year=None, region="Italy",
    story="A modern bartender riff blending Aperol with the sour template — bourbon or whiskey, lemon, sugar and Aperol. Bridges aperitivo and the whiskey sour family.",
    desc="Whiskey sour with Aperol modifier.",
    instructions=[
        "Shake all ingredients with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with an orange wheel",
    ],
    ingredients=[
        ing("bourbon", 1.5, "oz"),
        ing("Aperol", 0.75, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
    ],
    glass="rocks", garnish="orange wheel", abv=20,
))

RECIPES.append(cocktail(
    "Spritz Veneziano",
    year=None, region="Veneto, Italy",
    story="The original Veneto spritz format, predating the modern Aperol marketing era — built on local bittersweet aperitivo, white wine and soda over ice.",
    desc="Original Veneto spritz with bitter aperitivo and white wine.",
    instructions=[
        "Build aperitivo in a wine glass over ice",
        "Top with white wine and soda",
        "Garnish with an orange slice",
    ],
    ingredients=[
        ing("Italian bitter aperitivo", 2, "oz"),
        ing("dry white wine", 3, "oz"),
        ing("club soda", 1, "oz"),
    ],
    glass="wine", method="built", garnish="orange slice", abv=9,
))

RECIPES.append(cocktail(
    "Rosita",
    year=None, region="USA", creator="Gary Regan",
    story="Gary Regan's Rosita — a tequila Negroni — appeared in his Joy of Mixology in 2003 and helped popularize the Negroni-template variations of the 2000s.",
    desc="Tequila Negroni: reposado, Campari, sweet and dry vermouths.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with a lemon peel",
    ],
    ingredients=[
        ing("reposado tequila", 1.5, "oz"),
        ing("Campari", 0.5, "oz"),
        ing("sweet vermouth", 0.5, "oz"),
        ing("dry vermouth", 0.5, "oz"),
        ing("Angostura bitters", 1, "dash"),
    ],
    glass="rocks", method="stirred", garnish="lemon peel", abv=27,
))

RECIPES.append(cocktail(
    "Agave Negroni",
    year=None, region="USA",
    story="A 2010s craft favorite swapping gin for blanco tequila in the Negroni — the agave brings a softer, peppery character.",
    desc="Negroni with blanco tequila in place of gin.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with an orange peel",
    ],
    ingredients=[
        ing("blanco tequila", 1, "oz"),
        ing("Campari", 1, "oz"),
        ing("sweet vermouth", 1, "oz"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=27,
))

RECIPES.append(cocktail(
    "Hanky Panky (Modern)",
    year=None, region="London, UK",
    story="Ada Coleman's 1903 Hanky Panky — gin, sweet vermouth and Fernet-Branca — has become a modern bartender's secret handshake, often used to test a new bar's range.",
    desc="Gin and sweet vermouth with a Fernet kiss.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
        "Express orange peel and discard",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("sweet vermouth", 1.5, "oz"),
        ing("Fernet-Branca", 2, "dash"),
    ],
    glass="coupe", method="stirred", garnish="orange peel", abv=24,
))

# ============================================================================
# JAPANESE CRAFT
# ============================================================================

RECIPES.append(cocktail(
    "Japanese Highball",
    year=None, region="Japan",
    story="The Japanese Highball — typically Suntory whisky topped with chilled, hard-carbonated soda over a single hand-cut ice spear — is treated as a precision ritual at Tokyo bars and Suntory's marketing has made it Japan's national drink.",
    desc="Precision Japanese whisky highball with hand-cut ice spear.",
    instructions=[
        "Place a single hand-cut ice spear in a chilled highball glass",
        "Pour Japanese whisky over the ice and stir 13 times",
        "Add chilled soda from a fresh bottle and stir once more",
        "Garnish with a lemon peel (optional)",
    ],
    ingredients=[
        ing("Japanese whisky", 1.5, "oz"),
        ing("chilled soda water", 4, "oz"),
    ],
    glass="highball", method="built", garnish="lemon peel", abv=10,
))

RECIPES.append(cocktail(
    "Yuzu Sour",
    year=None, region="Japan",
    story="The yuzu sour applies the Western whiskey-sour template to Japan's signature citrus, often with shochu or Japanese whisky as base. A modern Tokyo bar staple.",
    desc="Japanese citrus sour with shochu or whisky.",
    instructions=[
        "Shake all with ice",
        "Strain into a chilled coupe",
        "Garnish with a yuzu peel",
    ],
    ingredients=[
        ing("Japanese whisky", 2, "oz"),
        ing("yuzu juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("egg white", 1, "each", "dairy"),
    ],
    glass="coupe", garnish="yuzu peel", abv=22,
))

RECIPES.append(cocktail(
    "Toki Highball",
    year=2014, region="Japan", creator="Suntory",
    story="Suntory launched the Toki blended whisky in 2014 specifically for the highball — its lighter profile is engineered to remain crisp under heavy carbonation.",
    desc="Lighter Toki blended-whisky highball.",
    instructions=[
        "Fill a chilled highball with ice",
        "Pour Toki and stir to chill",
        "Top with fresh chilled soda",
        "Garnish with a lemon peel",
    ],
    ingredients=[
        ing("Suntory Toki whisky", 1.5, "oz"),
        ing("chilled soda water", 4.5, "oz"),
    ],
    glass="highball", method="built", garnish="lemon peel", abv=10,
))

RECIPES.append(cocktail(
    "Saketini",
    year=None, region="USA",
    story="A 1960s American invention rediscovered by craft bartenders — gin or vodka with sake and a cucumber garnish for a clean, vegetal martini variation.",
    desc="Gin (or vodka) and sake martini with cucumber.",
    instructions=[
        "Stir gin and sake with ice",
        "Strain into a chilled cocktail glass",
        "Garnish with a cucumber slice",
    ],
    ingredients=[
        ing("gin", 2, "oz"),
        ing("dry sake", 1, "oz"),
    ],
    glass="cocktail", method="stirred", garnish="cucumber slice", abv=28,
))

RECIPES.append(cocktail(
    "Plum Sake Sour",
    year=None, region="Japan",
    story="A modern Japanese bar standard built on umeshu (plum sake) and lemon — a softer, fruitier cousin of the whiskey sour.",
    desc="Umeshu plum sake sour.",
    instructions=[
        "Shake umeshu, lemon and syrup with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with a plum slice",
    ],
    ingredients=[
        ing("umeshu (plum sake)", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.25, "oz", "pantry"),
    ],
    glass="rocks", garnish="plum slice", abv=12,
))

RECIPES.append(cocktail(
    "Bamboo (Modern)",
    year=None, region="Yokohama, Japan",
    story="The 19th-century Bamboo, born at the Grand Hotel Yokohama, has been reclaimed by modern low-ABV craft bartenders as the prototype for the dry sherry-vermouth aperitif.",
    desc="Dry sherry and dry vermouth low-ABV aperitif.",
    instructions=[
        "Stir sherry, vermouth and bitters with ice",
        "Strain into a chilled coupe",
        "Express lemon peel and discard",
    ],
    ingredients=[
        ing("dry sherry", 1.5, "oz"),
        ing("dry vermouth", 1.5, "oz"),
        ing("orange bitters", 1, "dash"),
        ing("Angostura bitters", 1, "dash"),
    ],
    glass="coupe", method="stirred", garnish="lemon peel", abv=14,
))

RECIPES.append(cocktail(
    "Tokyo Drift",
    year=2015, region="USA",
    story="A modern craft Japanese-inflected drink combining shochu, yuzu, ginger and shiso — a reflection of growing US interest in Japanese ingredients.",
    desc="Shochu sour with yuzu, ginger and shiso.",
    instructions=[
        "Muddle shiso leaves in shaker",
        "Add shochu, yuzu, ginger syrup and ice; shake",
        "Double strain into a chilled coupe",
        "Garnish with a shiso leaf",
    ],
    ingredients=[
        ing("shochu", 2, "oz"),
        ing("yuzu juice", 0.5, "oz", "produce"),
        ing("ginger syrup", 0.5, "oz", "pantry"),
        ing("shiso leaves", 4, "each", "produce"),
    ],
    glass="coupe", garnish="shiso leaf", abv=14,
))

RECIPES.append(cocktail(
    "Midori Sour (Modern)",
    year=None, region="Japan",
    story="The Midori melon liqueur sour — once a 1980s embarrassment, reclaimed by craft bartenders in the 2010s with proper fresh lemon and balanced sugar.",
    desc="Modernized Midori sour with fresh lemon.",
    instructions=[
        "Shake all with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with a lemon wheel",
    ],
    ingredients=[
        ing("Midori melon liqueur", 1.5, "oz"),
        ing("vodka", 0.75, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.25, "oz", "pantry"),
    ],
    glass="rocks", garnish="lemon wheel", abv=14,
))

# ============================================================================
# COFFEE / DESSERT MODERN
# ============================================================================

RECIPES.append(cocktail(
    "Espresso Martini (Modern Spec)",
    year=1983, region="London, UK", creator="Dick Bradsell",
    story="Dick Bradsell created the Espresso Martini at Fred's Club in London in 1983 reportedly for a model who asked for something to 'wake me up and then **** me up.' By the 2010s it had become one of the world's most-ordered cocktails.",
    desc="Bradsell's vodka-espresso classic — proper modern spec.",
    instructions=[
        "Shake vodka, espresso, coffee liqueur and syrup very hard with ice",
        "Double strain into a chilled coupe",
        "Garnish with three coffee beans",
    ],
    ingredients=[
        ing("vodka", 2, "oz"),
        ing("fresh espresso", 1, "oz", "pantry"),
        ing("coffee liqueur", 0.5, "oz"),
        ing("simple syrup", 0.25, "oz", "pantry"),
    ],
    glass="coupe", garnish="3 coffee beans", abv=22,
))

RECIPES.append(cocktail(
    "Mr. Black Espresso Martini",
    year=2016, region="Sydney, Australia", creator="Mr. Black",
    story="When Australian cold-brew coffee liqueur Mr. Black launched in the mid-2010s, it became the bartender-preferred base for the Espresso Martini, replacing Kahlúa as the standard.",
    desc="Cold-brew Espresso Martini using Mr. Black.",
    instructions=[
        "Shake all with ice very hard",
        "Double strain into a chilled coupe",
        "Garnish with three beans",
    ],
    ingredients=[
        ing("vodka", 1.5, "oz"),
        ing("Mr. Black cold-brew coffee liqueur", 1, "oz"),
        ing("fresh espresso", 1, "oz", "pantry"),
    ],
    glass="coupe", garnish="3 coffee beans", abv=22,
))

RECIPES.append(cocktail(
    "White Russian (Modern Spec)",
    year=None, region="USA",
    story="The 1949-era White Russian was reborn in 1998 with The Big Lebowski. Modern craft bars now spec it with cold brew and a heavy cream float.",
    desc="Modern White Russian with cold brew and heavy cream float.",
    instructions=[
        "Build vodka and cold-brew coffee liqueur in a rocks glass over ice",
        "Float heavy cream over the back of a spoon",
    ],
    ingredients=[
        ing("vodka", 2, "oz"),
        ing("cold-brew coffee liqueur", 1, "oz"),
        ing("heavy cream", 1, "oz", "dairy"),
    ],
    glass="rocks", method="built", garnish=None, abv=20,
))

RECIPES.append(cocktail(
    "Black Russian (Modern)",
    year=None, region="USA",
    story="The cream-free White Russian, restored to dignity by craft bartenders using fresh-pressed coffee liqueur and overproof vodka over a single large cube.",
    desc="Vodka and coffee liqueur on a single cube.",
    instructions=[
        "Build over a large cube in a rocks glass",
        "Stir to chill",
        "No garnish",
    ],
    ingredients=[
        ing("vodka", 2, "oz"),
        ing("coffee liqueur", 1, "oz"),
    ],
    glass="rocks", method="built", garnish=None, abv=24,
))

RECIPES.append(cocktail(
    "Irish Coffee (Modern Buena Vista Spec)",
    year=1952, region="San Francisco, USA", creator="Stanton Delaplane / Jack Koeppler", bar="Buena Vista Cafe",
    story="Stanton Delaplane and bartender Jack Koeppler perfected the Irish Coffee at the Buena Vista Cafe in 1952, after Koeppler tested for weeks to get the unwhipped cream to float properly. The Buena Vista has served millions of them since.",
    desc="Buena Vista's exact Irish coffee spec — coffee, sugar, whiskey, cream float.",
    instructions=[
        "Preheat a 6 oz heatproof goblet with hot water",
        "Empty water; add sugar and hot coffee, stir to dissolve",
        "Add Irish whiskey",
        "Float lightly whipped cream over the back of a heated spoon",
    ],
    ingredients=[
        ing("Irish whiskey", 1.5, "oz"),
        ing("hot brewed coffee", 4, "oz", "pantry"),
        ing("sugar cubes", 2, "each", "pantry"),
        ing("lightly whipped heavy cream", 1, "oz", "dairy"),
    ],
    glass="footed mug", method="hot", garnish=None, abv=10,
))

RECIPES.append(cocktail(
    "Spanish Coffee",
    year=1973, region="Portland, USA", creator="Carl Schumacher", bar="Huber's",
    story="Bartender Carl Schumacher's flaming Spanish Coffee at Huber's in Portland — 151 rum, triple sec, Kahlúa, coffee and whipped cream — has been performed tableside since 1973 and remains the bar's signature.",
    desc="Huber's flaming 151 rum, triple sec and Kahlúa coffee.",
    instructions=[
        "Pour 151 rum and triple sec into a heated mug; ignite",
        "Carefully rotate the mug to caramelize a sugared rim",
        "Add Kahlúa, hot coffee, and float whipped cream",
        "Dust with nutmeg",
    ],
    ingredients=[
        ing("151 rum", 0.5, "oz"),
        ing("triple sec", 0.5, "oz"),
        ing("Kahlúa", 0.75, "oz"),
        ing("hot brewed coffee", 4, "oz", "pantry"),
        ing("whipped cream", 1, "oz", "dairy"),
    ],
    glass="mug", method="hot", garnish="nutmeg", abv=12,
))

RECIPES.append(cocktail(
    "Mexican Coffee",
    year=None, region="USA",
    story="A modern variation on Irish Coffee replacing whiskey with tequila and Kahlúa, often topped with cinnamon-spiced cream.",
    desc="Tequila-Kahlúa coffee with cinnamon cream.",
    instructions=[
        "Combine tequila, Kahlúa and hot coffee in a heated mug",
        "Float lightly whipped cinnamon cream",
    ],
    ingredients=[
        ing("añejo tequila", 1, "oz"),
        ing("Kahlúa", 0.5, "oz"),
        ing("hot brewed coffee", 4, "oz", "pantry"),
        ing("cinnamon whipped cream", 1, "oz", "dairy"),
    ],
    glass="mug", method="hot", garnish="cinnamon", abv=8,
))

RECIPES.append(cocktail(
    "Brandy Alexander (Modern Spec)",
    year=None, region="USA",
    story="The 1922 Brandy Alexander has returned as a modern craft favorite — properly made with cognac, dark crème de cacao and heavy cream, freshly grated nutmeg.",
    desc="Modern proper-spec Brandy Alexander.",
    instructions=[
        "Shake all ingredients hard with ice",
        "Double strain into a chilled coupe",
        "Dust with freshly grated nutmeg",
    ],
    ingredients=[
        ing("cognac", 1.5, "oz"),
        ing("dark crème de cacao", 1, "oz"),
        ing("heavy cream", 1, "oz", "dairy"),
    ],
    glass="coupe", garnish="grated nutmeg", abv=18,
))

RECIPES.append(cocktail(
    "Mexican Hot Chocolate",
    year=None, region="USA",
    story="A modern winter cocktail combining mezcal or añejo tequila with rich hot chocolate, cinnamon and a dash of chile.",
    desc="Spiced mezcal hot chocolate.",
    instructions=[
        "Heat milk, chocolate and spices in a saucepan; stir until melted",
        "Pour into a mug and add mezcal",
        "Top with whipped cream and a chile dusting",
    ],
    ingredients=[
        ing("mezcal", 1.5, "oz"),
        ing("Mexican chocolate", 1, "oz", "pantry"),
        ing("whole milk", 5, "oz", "dairy"),
        ing("cinnamon", 1, "pinch", "pantry"),
        ing("ancho chile powder", 1, "pinch", "pantry"),
    ],
    glass="mug", method="hot", garnish="whipped cream and chile", abv=8,
))

RECIPES = [_clean(r) for r in RECIPES]

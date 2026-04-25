"""Part 6: Additional modern signatures, more named bartender creations, more spritz, more tiki."""
from build_modern_craft import cocktail, ing, _clean

RECIPES = []

# ============================================================================
# More named modern signatures
# ============================================================================

RECIPES.append(cocktail(
    "Yellow Submarine",
    year=2010, region="USA", creator="Misty Kalkofen",
    story="Misty Kalkofen's Yellow Submarine — yellow Chartreuse, lemon, mint, simple — became a Boston craft signature drink.",
    desc="Yellow Chartreuse and mint sour.",
    instructions=[
        "Muddle mint in shaker",
        "Add Chartreuse, lemon, simple, ice; shake",
        "Double strain into a coupe",
    ],
    ingredients=[
        ing("Yellow Chartreuse", 1.5, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("mint leaves", 8, "each", "produce"),
    ],
    glass="coupe", garnish="mint sprig", abv=18,
))

RECIPES.append(cocktail(
    "Improved Tequila Cocktail",
    year=2008, region="New York, USA", creator="Phil Ward", bar="Death & Co",
    story="Phil Ward's Improved Tequila Cocktail brings Jerry Thomas's 1862 'Improved' template to tequila — reposado, agave, maraschino, absinthe.",
    desc="Reposado tequila Old Fashioned with maraschino and absinthe.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Express orange peel",
    ],
    ingredients=[
        ing("reposado tequila", 2, "oz"),
        ing("agave syrup", 0.25, "oz", "pantry"),
        ing("maraschino liqueur", 1, "tsp"),
        ing("absinthe", 1, "dash"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=32,
))

RECIPES.append(cocktail(
    "Final Cut",
    year=2010, region="USA", creator="Joaquín Simó",
    story="Joaquín Simó's Final Cut applies the Last Word template to mezcal and Yellow Chartreuse — a smoky-floral equal-parts variation.",
    desc="Mezcal Last Word with Yellow Chartreuse.",
    instructions=[
        "Shake all with ice",
        "Double strain into a chilled coupe",
    ],
    ingredients=[
        ing("mezcal", 0.75, "oz"),
        ing("Yellow Chartreuse", 0.75, "oz"),
        ing("maraschino liqueur", 0.75, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
    ],
    glass="coupe", garnish=None, abv=22,
))

RECIPES.append(cocktail(
    "Mister Howell",
    year=2014, region="USA", creator="Sam Ross",
    story="Sam Ross's Mister Howell at Attaboy — gin, dry vermouth, yellow Chartreuse, basil. A herbaceous gin Martini variant.",
    desc="Gin Martini with Yellow Chartreuse and basil.",
    instructions=[
        "Muddle basil in mixing glass",
        "Add gin, vermouth, Chartreuse and ice; stir",
        "Double strain into a chilled coupe",
        "Garnish with a basil leaf",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("dry vermouth", 0.75, "oz"),
        ing("Yellow Chartreuse", 0.5, "oz"),
        ing("basil leaves", 6, "each", "produce"),
    ],
    glass="coupe", method="stirred", garnish="basil leaf", abv=24,
))

RECIPES.append(cocktail(
    "Heirloom",
    year=None, region="USA",
    story="A modern craft farm-to-glass cocktail — gin, heirloom tomato shrub, basil and lemon. A 2010s farm-table standard.",
    desc="Gin with heirloom tomato shrub and basil.",
    instructions=[
        "Muddle basil in shaker",
        "Add gin, tomato shrub, lemon and ice; shake",
        "Double strain into a coupe",
        "Garnish with a cherry tomato",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("heirloom tomato shrub", 0.75, "oz", "pantry"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("basil leaves", 4, "each", "produce"),
    ],
    glass="coupe", garnish="cherry tomato", abv=22,
))

RECIPES.append(cocktail(
    "Smoke and Mirrors",
    year=None, region="USA",
    story="A modern smoky craft cocktail — mezcal, lapsang souchong tea, lemon, honey. A signature drink at speakeasy bars.",
    desc="Mezcal with lapsang souchong tea and honey.",
    instructions=[
        "Shake all with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with a lemon peel",
    ],
    ingredients=[
        ing("mezcal", 2, "oz"),
        ("lapsang souchong tea, brewed strong"),
        ing("strong lapsang souchong tea", 0.5, "oz", "pantry"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("honey syrup", 0.75, "oz", "pantry"),
    ],
    glass="rocks", garnish="lemon peel", abv=22,
))

RECIPES.append(cocktail(
    "Aviation 75",
    year=None, region="USA",
    story="A modern craft hybrid combining the Aviation with the French 75 — gin, lemon, crème de violette topped with champagne.",
    desc="Aviation French 75 hybrid.",
    instructions=[
        "Shake gin, lemon, crème de violette with ice",
        "Strain into a flute",
        "Top with champagne",
    ],
    ingredients=[
        ing("London dry gin", 1, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("crème de violette", 0.25, "oz"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("champagne", 2, "oz"),
    ],
    glass="flute", garnish="brandied cherry", abv=14,
))

RECIPES.append(cocktail(
    "French 76",
    year=None, region="USA",
    story="A modern craft variant on the French 75 substituting vodka for gin. Standard at brunch service.",
    desc="French 75 with vodka in place of gin.",
    instructions=[
        "Shake vodka, lemon and simple with ice",
        "Strain into a flute",
        "Top with champagne",
    ],
    ingredients=[
        ing("vodka", 1, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("champagne", 3, "oz"),
    ],
    glass="flute", garnish="lemon peel", abv=12,
))

RECIPES.append(cocktail(
    "French 77",
    year=None, region="USA",
    story="A modern craft variant on the French 75 — gin, St-Germain elderflower, lemon, champagne. The elderflower version became a 2010s wedding standard.",
    desc="French 75 with St-Germain elderflower liqueur.",
    instructions=[
        "Shake gin, St-Germain, lemon with ice",
        "Strain into a flute",
        "Top with champagne",
    ],
    ingredients=[
        ing("London dry gin", 1, "oz"),
        ing("St-Germain elderflower liqueur", 0.5, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("champagne", 3, "oz"),
    ],
    glass="flute", garnish="lemon peel", abv=12,
))

RECIPES.append(cocktail(
    "Elderflower Spritz",
    year=None, region="USA",
    story="A modern aperitivo bar standard built on St-Germain, prosecco and soda — broader cousin to the Hugo Spritz.",
    desc="St-Germain prosecco spritz.",
    instructions=[
        "Build St-Germain in a wine glass over ice",
        "Top with prosecco and soda",
        "Garnish with a lemon peel",
    ],
    ingredients=[
        ing("St-Germain elderflower liqueur", 1.5, "oz"),
        ing("prosecco", 3, "oz"),
        ing("club soda", 1, "oz"),
    ],
    glass="wine", method="built", garnish="lemon peel", abv=8,
))

RECIPES.append(cocktail(
    "Suze Spritz",
    year=None, region="France",
    story="A modern French-inflected spritz built on Suze gentian liqueur, prosecco and soda. The bartender-favorite among bitter-aperitivo spritzes.",
    desc="Suze gentian spritz.",
    instructions=[
        "Build Suze in a wine glass over ice",
        "Top with prosecco and soda",
        "Garnish with a grapefruit peel",
    ],
    ingredients=[
        ing("Suze", 2, "oz"),
        ing("prosecco", 3, "oz"),
        ing("club soda", 1, "oz"),
    ],
    glass="wine", method="built", garnish="grapefruit peel", abv=9,
))

RECIPES.append(cocktail(
    "Pimm's Cup (Modern Spec)",
    year=None, region="London, UK",
    story="The 1850s London Pimm's Cup — Pimm's, lemonade, ginger ale, fruit, mint, cucumber — was rebuilt at modern craft bars with proper ginger beer and fresh produce.",
    desc="Modern Pimm's Cup with cucumber and mint.",
    instructions=[
        "Build Pimm's in a Collins glass over ice with cucumber, orange and mint",
        "Top with lemonade and ginger ale (or ginger beer)",
        "Stir gently",
    ],
    ingredients=[
        ing("Pimm's No. 1", 2, "oz"),
        ing("lemonade", 2, "oz", "pantry"),
        ing("ginger ale", 2, "oz"),
        ("cucumber slices"),
        ing("cucumber slices", 3, "each", "produce"),
        ing("orange slices", 2, "each", "produce"),
        ing("mint leaves", 4, "each", "produce"),
    ],
    glass="collins", method="built", garnish="cucumber and mint", abv=6,
))

RECIPES.append(cocktail(
    "Englishman Abroad",
    year=2011, region="London, UK", creator="Tony Conigliaro",
    story="Tony Conigliaro's Englishman Abroad applies the Pimm's template with mezcal and a craft sensibility.",
    desc="Mezcal Pimm's-style cooler.",
    instructions=[
        "Build mezcal, Pimm's and lemon in a Collins glass over ice",
        "Top with ginger beer",
        "Garnish with mint and cucumber",
    ],
    ingredients=[
        ing("mezcal", 1, "oz"),
        ing("Pimm's No. 1", 1, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("ginger beer", 4, "oz"),
    ],
    glass="collins", method="built", garnish="mint and cucumber", abv=10,
))

RECIPES.append(cocktail(
    "Brown Derby (Modern Spec)",
    year=None, region="Los Angeles, USA",
    story="The 1930s Brown Derby — bourbon, grapefruit, honey — was restored to canon by 2000s craft bartenders as a balanced bourbon sour with character.",
    desc="Bourbon, grapefruit and honey sour.",
    instructions=[
        "Shake all with ice",
        "Double strain into a chilled coupe",
        "Garnish with a grapefruit peel",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("fresh grapefruit juice", 1, "oz", "produce"),
        ing("honey syrup", 0.5, "oz", "pantry"),
    ],
    glass="coupe", garnish="grapefruit peel", abv=22,
))

RECIPES.append(cocktail(
    "Honey Bee",
    year=None, region="USA",
    story="The Honey Bee — rum, honey, lemon — predates the Bee's Knees but was rediscovered by craft bartenders as the rum cousin to the gin classic.",
    desc="Rum-honey-lemon sour.",
    instructions=[
        "Shake all with ice",
        "Strain into a chilled coupe",
    ],
    ingredients=[
        ing("white rum", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("honey syrup", 0.75, "oz", "pantry"),
    ],
    glass="coupe", garnish="lemon peel", abv=22,
))

RECIPES.append(cocktail(
    "Improved Cuba Libre",
    year=2007, region="New York, USA", creator="Audrey Saunders", bar="Pegu Club",
    story="Audrey Saunders' Improved Cuba Libre at Pegu Club uses fresh lime, real cane sugar cola, and bitters to elevate the rum and Coke template.",
    desc="Aged-rum Cuba Libre with bitters and craft cola.",
    instructions=[
        "Build aged rum, lime and bitters in a Collins glass over ice",
        "Top with craft cane-sugar cola",
        "Garnish with a lime wedge",
    ],
    ingredients=[
        ing("aged rum", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("Angostura bitters", 2, "dash"),
        ing("craft cane-sugar cola", 4, "oz"),
    ],
    glass="collins", method="built", garnish="lime wedge", abv=11,
))

RECIPES.append(cocktail(
    "Floridita",
    year=None, region="Havana, Cuba",
    story="The El Floridita Daiquiri's eponymous house spec — white rum, grapefruit, lime, maraschino, sugar — frozen-blended in Constantino Ribalaigua style.",
    desc="Floridita's frozen Daiquiri spec.",
    instructions=[
        "Blend all with crushed ice for 6 seconds",
        "Pour into a chilled coupe",
    ],
    ingredients=[
        ing("white rum", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("fresh grapefruit juice", 0.5, "oz", "produce"),
        ing("maraschino liqueur", 0.25, "oz"),
        ing("simple syrup", 0.25, "oz", "pantry"),
    ],
    glass="coupe", method="blended", garnish=None, abv=22,
))

RECIPES.append(cocktail(
    "Mojito (Modern Spec)",
    year=None, region="Havana, Cuba",
    story="The Cuban Mojito — white rum, lime, mint, sugar, soda — was restored to its proper minimalist proportions by modern craft bars after 2000s tourist-trap excess.",
    desc="Modern minimalist Cuban Mojito.",
    instructions=[
        "Muddle mint with simple in shaker; do not bruise",
        "Add rum, lime, ice; shake briefly",
        "Strain into a Collins glass over fresh ice",
        "Top with soda; garnish with mint",
    ],
    ingredients=[
        ing("white rum", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("mint leaves", 8, "each", "produce"),
        ing("club soda", 2, "oz"),
    ],
    glass="collins", garnish="mint sprig", abv=14,
))

RECIPES.append(cocktail(
    "Smoky Mojito",
    year=None, region="USA",
    story="A modern craft Mojito variant replacing some rum with mezcal — adds smoky depth to the herbaceous freshness.",
    desc="Mojito with mezcal in place of some rum.",
    instructions=[
        "Muddle mint with simple in shaker",
        "Add rum, mezcal, lime, ice; shake briefly",
        "Strain into a Collins glass over fresh ice",
        "Top with soda; garnish with mint",
    ],
    ingredients=[
        ing("white rum", 1.5, "oz"),
        ing("mezcal", 0.5, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("mint leaves", 8, "each", "produce"),
        ing("club soda", 2, "oz"),
    ],
    glass="collins", garnish="mint sprig", abv=14,
))

RECIPES.append(cocktail(
    "Pineapple Mojito",
    year=None, region="USA",
    story="A modern craft Mojito variation incorporating fresh pineapple — became a tropical brunch staple at the 2010s craft bars.",
    desc="Pineapple-rum Mojito.",
    instructions=[
        "Muddle mint and pineapple in shaker",
        "Add rum, lime, simple, ice; shake",
        "Strain into a Collins glass over fresh ice",
        "Top with soda",
    ],
    ingredients=[
        ing("white rum", 2, "oz"),
        ("pineapple chunks"),
        ing("pineapple chunks", 4, "each", "produce"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("mint leaves", 6, "each", "produce"),
        ing("club soda", 2, "oz"),
    ],
    glass="collins", garnish="mint sprig and pineapple", abv=14,
))

RECIPES.append(cocktail(
    "Strawberry Daiquiri (Shaken)",
    year=None, region="USA",
    story="The proper craft strawberry Daiquiri — shaken with fresh strawberries, never blended frozen. The 2010s craft revival of the much-abused 1980s drink.",
    desc="Shaken (not frozen) strawberry rum Daiquiri.",
    instructions=[
        "Muddle strawberries in shaker",
        "Add rum, lime, simple, ice; shake",
        "Double strain into a chilled coupe",
        "Garnish with a strawberry",
    ],
    ingredients=[
        ing("white rum", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ("strawberries"),
        ing("strawberries", 3, "each", "produce"),
    ],
    glass="coupe", garnish="strawberry", abv=22,
))

RECIPES.append(cocktail(
    "Bee's Sting",
    year=None, region="USA",
    story="A modern craft cocktail blending tequila, honey, lemon and a habanero kick — sweet-tart-spicy.",
    desc="Tequila-honey sour with habanero heat.",
    instructions=[
        "Shake all with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with a habanero slice",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("habanero-honey syrup", 0.75, "oz", "pantry"),
    ],
    glass="rocks", garnish="habanero slice", abv=22,
))

RECIPES.append(cocktail(
    "Whiskey Ginger",
    year=None, region="USA",
    story="The casual bourbon-ginger ale highball, restored to craft attention in the 2010s with proper craft ginger beer and a lime squeeze.",
    desc="Bourbon and craft ginger beer highball.",
    instructions=[
        "Build bourbon over ice in a highball glass",
        "Top with ginger beer; squeeze lime",
        "Garnish with a lime wedge",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("ginger beer", 5, "oz"),
        ing("fresh lime juice", 0.25, "oz", "produce"),
    ],
    glass="highball", method="built", garnish="lime wedge", abv=10,
))

RECIPES.append(cocktail(
    "Bourbon Buck",
    year=None, region="USA",
    story="A modern craft variation on the buck format — bourbon, lemon, ginger beer. Lighter and more lemon-forward than a Whiskey Ginger.",
    desc="Bourbon buck with lemon and ginger beer.",
    instructions=[
        "Shake bourbon and lemon with ice",
        "Strain over fresh ice in a Collins glass",
        "Top with ginger beer",
        "Garnish with lemon wheel",
    ],
    ingredients=[
        ing("bourbon", 1.5, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("ginger beer", 4, "oz"),
    ],
    glass="collins", garnish="lemon wheel", abv=11,
))

RECIPES.append(cocktail(
    "Gin Buck",
    year=None, region="USA",
    story="The 19th-century gin buck — gin, lemon, ginger ale — restored to canon by craft bars seeking simpler highball formats.",
    desc="Gin and ginger ale highball.",
    instructions=[
        "Build gin and lemon in a highball glass over ice",
        "Top with ginger ale",
        "Garnish with a lemon wedge",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("ginger ale", 4, "oz"),
    ],
    glass="highball", method="built", garnish="lemon wedge", abv=11,
))

RECIPES.append(cocktail(
    "Mexican Mule",
    year=None, region="USA",
    story="A modern Moscow Mule variation built on tequila in place of vodka. Standard at modern Mexican bars.",
    desc="Tequila Moscow Mule.",
    instructions=[
        "Build tequila and lime in a copper mug over ice",
        "Top with ginger beer",
        "Garnish with lime wedge",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("ginger beer", 4, "oz"),
    ],
    glass="copper mug", method="built", garnish="lime wedge", abv=11,
))

RECIPES.append(cocktail(
    "Kentucky Mule",
    year=None, region="USA",
    story="A bourbon variation on the Moscow Mule — bourbon, lime, ginger beer. A craft bar mule menu standard.",
    desc="Bourbon Moscow Mule.",
    instructions=[
        "Build bourbon and lime in a copper mug over ice",
        "Top with ginger beer",
        "Garnish with mint and lime",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("ginger beer", 4, "oz"),
    ],
    glass="copper mug", method="built", garnish="mint and lime", abv=11,
))

RECIPES.append(cocktail(
    "Irish Mule",
    year=None, region="USA",
    story="An Irish whiskey variant on the Moscow Mule — Irish whiskey, lime, ginger beer.",
    desc="Irish whiskey Moscow Mule.",
    instructions=[
        "Build Irish whiskey and lime in a copper mug over ice",
        "Top with ginger beer",
        "Garnish with mint",
    ],
    ingredients=[
        ing("Irish whiskey", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("ginger beer", 4, "oz"),
    ],
    glass="copper mug", method="built", garnish="mint sprig", abv=11,
))

RECIPES.append(cocktail(
    "London Mule",
    year=None, region="USA",
    story="A gin variation on the Moscow Mule — gin, lime, ginger beer. The London Mule sat alongside the Moscow Mule on 2010s craft bar menus.",
    desc="Gin Moscow Mule.",
    instructions=[
        "Build gin and lime in a copper mug over ice",
        "Top with ginger beer",
        "Garnish with cucumber and mint",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("ginger beer", 4, "oz"),
    ],
    glass="copper mug", method="built", garnish="cucumber and mint", abv=11,
))

RECIPES.append(cocktail(
    "Dark and Stormy (Modern Spec)",
    year=None, region="Bermuda",
    story="The Bermudian classic — Gosling's Black Seal rum and ginger beer over ice. The trademarked spec is closely guarded by Gosling's family but has become a global craft standard.",
    desc="Gosling's rum and ginger beer Bermudian classic.",
    instructions=[
        "Build ginger beer in a highball glass over ice",
        "Float Gosling's Black Seal rum on top",
        "Garnish with a lime wedge",
    ],
    ingredients=[
        ing("Gosling's Black Seal rum", 2, "oz"),
        ing("ginger beer", 5, "oz"),
        ing("fresh lime juice", 0.25, "oz", "produce"),
    ],
    glass="highball", method="built", garnish="lime wedge", abv=10,
))

RECIPES.append(cocktail(
    "Pomegranate Sour",
    year=None, region="USA",
    story="A modern craft fall sour — bourbon, fresh pomegranate juice, lemon, simple, egg white.",
    desc="Bourbon sour with fresh pomegranate.",
    instructions=[
        "Dry-shake all without ice",
        "Add ice and shake again",
        "Double strain into a coupe",
        "Garnish with pomegranate seeds",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("fresh pomegranate juice", 0.75, "oz", "produce"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("egg white", 1, "each", "dairy"),
    ],
    glass="coupe", garnish="pomegranate seeds", abv=20,
))

RECIPES.append(cocktail(
    "Pear and Sage Smash",
    year=None, region="USA",
    story="A modern craft fall smash — bourbon, pear purée, fresh sage, lemon. Standard on autumn craft menus.",
    desc="Bourbon smash with pear and sage.",
    instructions=[
        "Muddle sage with simple in shaker",
        "Add bourbon, pear purée, lemon, ice; shake",
        "Strain over crushed ice in a rocks glass",
        "Garnish with sage and pear slice",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ("pear purée"),
        ing("pear purée", 0.75, "oz", "produce"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("sage leaves", 4, "each", "produce"),
    ],
    glass="rocks", garnish="sage and pear", abv=20,
))

RECIPES.append(cocktail(
    "Cucumber Mint Cooler",
    year=None, region="USA",
    story="A modern craft summer non-alcoholic-leaning cooler — gin (or zero-proof spirit), cucumber, mint, lime, soda. Light, refreshing.",
    desc="Cucumber-mint gin cooler.",
    instructions=[
        "Muddle cucumber and mint in shaker",
        "Add gin, lime, simple, ice; shake briefly",
        "Strain over fresh ice in a Collins glass",
        "Top with soda; garnish with cucumber",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("cucumber slices", 4, "each", "produce"),
        ing("mint leaves", 6, "each", "produce"),
        ing("club soda", 4, "oz"),
    ],
    glass="collins", garnish="cucumber wheel and mint", abv=10,
))

RECIPES.append(cocktail(
    "Strawberry Basil Smash",
    year=None, region="USA",
    story="A modern craft summer smash — vodka or gin, fresh strawberries, basil, lemon, simple. Standard at 2010s farm-to-glass bars.",
    desc="Strawberry-basil gin smash.",
    instructions=[
        "Muddle strawberries and basil in shaker",
        "Add gin, lemon, simple, ice; shake",
        "Double strain over fresh ice in a rocks glass",
        "Garnish with basil and strawberry",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ("strawberries"),
        ing("strawberries", 3, "each", "produce"),
        ing("basil leaves", 4, "each", "produce"),
    ],
    glass="rocks", garnish="basil and strawberry", abv=22,
))

RECIPES.append(cocktail(
    "Watermelon Mint Cooler",
    year=None, region="USA",
    story="A craft summer cooler featuring fresh watermelon — vodka, watermelon, mint, lime, soda. Standard at brunch craft bars.",
    desc="Watermelon-mint vodka cooler.",
    instructions=[
        "Muddle mint in shaker",
        "Add vodka, watermelon, lime, ice; shake",
        "Strain over fresh ice in a Collins glass",
        "Top with soda; garnish with mint and watermelon",
    ],
    ingredients=[
        ing("vodka", 1.5, "oz"),
        ing("fresh watermelon juice", 2, "oz", "produce"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("mint leaves", 6, "each", "produce"),
        ing("club soda", 2, "oz"),
    ],
    glass="collins", garnish="mint and watermelon", abv=10,
))

RECIPES.append(cocktail(
    "Spiced Apple Toddy",
    year=None, region="USA",
    story="A modern craft hot toddy variation built on apple brandy and warm spice. Standard on cold-weather menus.",
    desc="Apple brandy hot toddy with warm spice.",
    instructions=[
        "Heat apple brandy, cider, syrup and bitters in a saucepan",
        "Pour into a heated mug",
        "Garnish with cinnamon stick and lemon wheel",
    ],
    ingredients=[
        ing("apple brandy", 1.5, "oz"),
        ing("hot apple cider", 4, "oz", "produce"),
        ing("maple syrup", 0.5, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="mug", method="hot", garnish="cinnamon and lemon", abv=10,
))

RECIPES.append(cocktail(
    "Hot Toddy (Modern Spec)",
    year=None, region="USA",
    story="The 18th-century hot toddy — whiskey, hot water, honey, lemon — restored to craft bar menus as a winter standard.",
    desc="Modern craft hot toddy.",
    instructions=[
        "Combine whiskey, honey and lemon juice in a heated mug",
        "Top with hot water; stir",
        "Garnish with a lemon wheel and cinnamon stick",
    ],
    ingredients=[
        ing("Irish whiskey", 1.5, "oz"),
        ing("honey syrup", 0.75, "oz", "pantry"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("hot water", 4, "oz", "pantry"),
    ],
    glass="mug", method="hot", garnish="lemon wheel and cinnamon", abv=10,
))

RECIPES.append(cocktail(
    "Hot Buttered Rum (Modern Spec)",
    year=None, region="USA",
    story="The colonial-American hot buttered rum, restored at modern craft bars with proper aged Demerara rum and homemade spice butter.",
    desc="Aged Demerara rum hot buttered drink.",
    instructions=[
        "Stir butter batter and rum in a heated mug",
        "Top with boiling water",
        "Dust with nutmeg",
    ],
    ingredients=[
        ing("aged Demerara rum", 2, "oz"),
        ing("hot buttered rum batter", 1, "tbsp", "pantry"),
        ing("boiling water", 5, "oz", "pantry"),
    ],
    glass="mug", method="hot", garnish="grated nutmeg", abv=12,
))

RECIPES.append(cocktail(
    "Tom and Jerry (Modern Spec)",
    year=None, region="USA",
    story="Jerry Thomas's 1862 hot egg punch, restored to canon by craft bartenders for winter holiday menus.",
    desc="Jerry Thomas's 1862 hot egg punch.",
    instructions=[
        "Make Tom & Jerry batter (whipped egg, sugar, spice) in advance",
        "Spoon batter into a heated mug",
        "Add brandy and rum; top with hot milk or water",
        "Dust with nutmeg",
    ],
    ingredients=[
        ing("brandy", 1, "oz"),
        ing("aged rum", 1, "oz"),
        ("Tom & Jerry batter"),
        ing("Tom & Jerry batter (egg-sugar-spice)", 2, "tbsp", "pantry"),
        ing("hot whole milk", 4, "oz", "dairy"),
    ],
    glass="mug", method="hot", garnish="grated nutmeg", abv=10,
))

RECIPES.append(cocktail(
    "Coquito",
    year=None, region="Puerto Rico",
    story="The Puerto Rican Christmas Coquito — rum, coconut milk, sweetened condensed milk, cinnamon — has long been a household tradition and joined craft bar menus in the 2010s as a holiday option.",
    desc="Puerto Rican coconut Christmas drink.",
    instructions=[
        "Blend all ingredients until smooth",
        "Refrigerate at least 1 hour",
        "Serve in small chilled glasses with cinnamon dust",
    ],
    ingredients=[
        ing("white rum", 4, "oz"),
        ing("coconut milk", 6, "oz", "pantry"),
        ing("cream of coconut", 4, "oz", "pantry"),
        ing("sweetened condensed milk", 4, "oz", "pantry"),
        ing("ground cinnamon", 1, "tsp", "pantry"),
        ing("nutmeg", 1, "pinch", "pantry"),
    ],
    glass="cordial", method="blended", garnish="cinnamon", abv=10, prep=10,
))

RECIPES.append(cocktail(
    "Eggnog (Modern Spec)",
    year=None, region="USA",
    story="The colonial-American eggnog restored to craft bar menus with proper aged-bourbon-and-aged-rum base, fresh nutmeg, and (often) batched and aged for weeks.",
    desc="Modern craft eggnog with aged spirits.",
    instructions=[
        "Whisk eggs and sugar until pale",
        "Slowly add bourbon, rum, milk, cream",
        "Refrigerate at least 24 hours; ideally weeks",
        "Serve cold with grated nutmeg",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("aged rum", 1, "oz"),
        ing("whole milk", 3, "oz", "dairy"),
        ing("heavy cream", 1, "oz", "dairy"),
        ing("whole egg", 1, "each", "dairy"),
        ing("sugar", 1, "tbsp", "pantry"),
    ],
    glass="punch cup", method="batched", garnish="grated nutmeg", abv=14, prep=30,
))

RECIPES.append(cocktail(
    "Mulled Wine",
    year=None, region="Europe",
    story="The European mulled wine tradition was given craft restaurant attention in the 2010s with quality red wine, fresh citrus, and proper spice.",
    desc="Spiced mulled red wine.",
    instructions=[
        "Combine wine, sugar, citrus and spices in saucepan",
        "Heat gently 30 minutes; do not boil",
        "Strain into mugs",
        "Garnish with orange wheel",
    ],
    ingredients=[
        ing("dry red wine", 6, "oz"),
        ("brandy"),
        ing("brandy", 0.5, "oz"),
        ing("sugar", 1, "tbsp", "pantry"),
        ing("orange slices", 2, "each", "produce"),
        ing("cinnamon stick", 1, "each", "pantry"),
        ing("cloves", 4, "each", "pantry"),
    ],
    glass="mug", method="hot", garnish="orange wheel", abv=10, prep=30,
))

RECIPES.append(cocktail(
    "Glühwein",
    year=None, region="Germany",
    story="The German Glühwein — heated red wine with citrus and warm spice — is the European original of mulled wine, served from market stalls at Christmas markets.",
    desc="German Christmas-market mulled wine.",
    instructions=[
        "Heat wine, sugar, orange, lemon, and spices gently",
        "Do not boil; let infuse 30 minutes",
        "Strain into mugs",
    ],
    ingredients=[
        ing("dry red wine", 6, "oz"),
        ing("sugar", 1, "tbsp", "pantry"),
        ing("orange slices", 2, "each", "produce"),
        ing("lemon slices", 1, "each", "produce"),
        ing("cinnamon stick", 1, "each", "pantry"),
        ing("cloves", 3, "each", "pantry"),
        ing("star anise", 1, "each", "pantry"),
    ],
    glass="mug", method="hot", garnish="orange wheel", abv=10, prep=30,
))

RECIPES.append(cocktail(
    "Sidecar With a Pop",
    year=2009, region="New York, USA", creator="Audrey Saunders",
    story="Audrey Saunders' Sidecar With a Pop adds champagne float to the classic Sidecar — celebratory and effervescent.",
    desc="Sidecar topped with a champagne float.",
    instructions=[
        "Shake cognac, Cointreau, lemon with ice",
        "Strain into a coupe",
        "Top with a small champagne float",
    ],
    ingredients=[
        ing("cognac", 1.5, "oz"),
        ing("Cointreau", 0.5, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("champagne", 1, "oz"),
    ],
    glass="coupe", garnish="lemon peel", abv=22,
))

RECIPES.append(cocktail(
    "Black Velvet (Modern)",
    year=None, region="London, UK",
    story="The 1861 Brooks's Club Black Velvet — Guinness and champagne — was restored to attention by 2010s craft bartenders and beer-cocktail enthusiasts.",
    desc="Guinness and champagne layered cocktail.",
    instructions=[
        "Pour Guinness halfway up a flute",
        "Float champagne on top with the back of a spoon",
    ],
    ingredients=[
        ing("Guinness stout", 4, "oz"),
        ing("champagne", 4, "oz"),
    ],
    glass="flute", method="built", garnish=None, abv=8,
))

RECIPES.append(cocktail(
    "Spaghett",
    year=None, region="Baltimore, USA",
    story="A regional Baltimore drink that went craft-viral in the 2010s — Miller High Life with Aperol and lemon. The unpretentious craft palate cleanser.",
    desc="Miller High Life with Aperol shot — Baltimore craft viral hit.",
    instructions=[
        "Open a chilled Miller High Life and take a sip",
        "Pour Aperol and lemon into the bottle",
        "Drink directly from the bottle",
    ],
    ingredients=[
        ing("Miller High Life", 12, "oz"),
        ing("Aperol", 1, "oz"),
        ing("fresh lemon juice", 0.25, "oz", "produce"),
    ],
    glass="bottle", method="built", garnish=None, abv=5,
))

RECIPES.append(cocktail(
    "Beer Margarita",
    year=None, region="USA",
    story="A modern craft frozen blender drink — tequila, lime, beer, frozen limeade. Standard at Tex-Mex restaurants.",
    desc="Frozen tequila-beer-lime margarita.",
    instructions=[
        "Blend tequila, frozen limeade, lime juice and crushed ice",
        "Pour into a salt-rimmed pitcher",
        "Top with cold Mexican lager",
    ],
    ingredients=[
        ing("blanco tequila", 4, "oz"),
        ing("frozen limeade concentrate", 4, "oz", "freezer"),
        ing("Mexican lager", 12, "oz"),
        ing("fresh lime juice", 1, "oz", "produce"),
    ],
    glass="pitcher", method="blended", garnish="salt rim", abv=8,
))

RECIPES.append(cocktail(
    "Aperol Sour (Modern)",
    year=None, region="Italy",
    story="A modern craft Aperol Sour — Aperol shaken with lemon, simple and egg white for body. The bittersweet aperitivo as a foamy sour.",
    desc="Aperol sour with egg white.",
    instructions=[
        "Dry-shake all without ice",
        "Add ice and shake again",
        "Double strain into a coupe",
        "Garnish with orange peel",
    ],
    ingredients=[
        ing("Aperol", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("egg white", 1, "each", "dairy"),
    ],
    glass="coupe", garnish="orange peel", abv=10,
))

RECIPES.append(cocktail(
    "Black Pearl",
    year=None, region="USA",
    story="A modern craft cocktail combining espresso, dark rum, vanilla and chocolate bitters — a coffee tiki crossover.",
    desc="Dark rum, espresso and vanilla.",
    instructions=[
        "Shake all hard with ice",
        "Double strain into a chilled coupe",
        "Garnish with three coffee beans",
    ],
    ingredients=[
        ing("dark rum", 2, "oz"),
        ing("fresh espresso", 1, "oz", "pantry"),
        ing("vanilla syrup", 0.5, "oz", "pantry"),
        ing("chocolate bitters", 2, "dash"),
    ],
    glass="coupe", garnish="3 coffee beans", abv=20,
))

RECIPES.append(cocktail(
    "Pumpkin Spice Old Fashioned",
    year=None, region="USA",
    story="A modern craft autumn variation on the Old Fashioned built on bourbon and pumpkin-spice syrup — peak fall menu standard.",
    desc="Bourbon Old Fashioned with pumpkin spice.",
    instructions=[
        "Stir bourbon, pumpkin syrup and bitters with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with cinnamon stick and orange peel",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ("pumpkin spice syrup"),
        ing("pumpkin spice syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="rocks", method="stirred", garnish="cinnamon and orange", abv=32,
))

RECIPES.append(cocktail(
    "Gingerbread Old Fashioned",
    year=None, region="USA",
    story="A modern craft holiday-season Old Fashioned built on bourbon and gingerbread syrup. Standard on December craft bar menus.",
    desc="Bourbon Old Fashioned with gingerbread syrup.",
    instructions=[
        "Stir bourbon, gingerbread syrup and bitters with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with orange peel and a candied ginger piece",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("gingerbread syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
        ing("orange bitters", 1, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel and candied ginger", abv=32,
))

RECIPES.append(cocktail(
    "Smoked Maple Old Fashioned",
    year=None, region="USA",
    story="A modern craft cocktail combining maple syrup with mezcal-rinsed bourbon — autumnal but smoky.",
    desc="Bourbon-maple Old Fashioned with mezcal rinse.",
    instructions=[
        "Rinse a chilled rocks glass with mezcal; discard",
        "Stir bourbon, maple, bitters with ice",
        "Strain over a large cube in the rinsed glass",
        "Express orange peel",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("maple syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
        ing("mezcal", 1, "rinse"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=32,
))

RECIPES.append(cocktail(
    "Beet Negroni",
    year=None, region="USA",
    story="A modern craft Negroni variation incorporating fresh beet juice — earthy, bittersweet and vivid.",
    desc="Negroni with fresh beet juice.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with orange peel",
    ],
    ingredients=[
        ing("London dry gin", 1, "oz"),
        ing("Campari", 1, "oz"),
        ing("sweet vermouth", 1, "oz"),
        ("fresh beet juice"),
        ing("fresh beet juice", 0.5, "oz", "produce"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=22,
))

RECIPES.append(cocktail(
    "Carrot Negroni",
    year=None, region="USA",
    story="A modern craft farm-to-glass Negroni variation built with fresh carrot juice — sweet vegetal balance to the bitter Campari.",
    desc="Negroni with fresh carrot juice.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with carrot ribbon",
    ],
    ingredients=[
        ing("London dry gin", 1, "oz"),
        ing("Campari", 1, "oz"),
        ing("sweet vermouth", 1, "oz"),
        ("fresh carrot juice"),
        ing("fresh carrot juice", 0.5, "oz", "produce"),
    ],
    glass="rocks", method="stirred", garnish="carrot ribbon", abv=22,
))

RECIPES.append(cocktail(
    "Aperol Highball",
    year=None, region="Italy",
    story="A modern craft spritz alternative — Aperol, soda and lime over ice. Lighter and brighter than the traditional spritz.",
    desc="Aperol and soda highball.",
    instructions=[
        "Build Aperol and lime in a Collins glass over ice",
        "Top with soda",
        "Garnish with orange wheel",
    ],
    ingredients=[
        ing("Aperol", 2, "oz"),
        ing("fresh lime juice", 0.25, "oz", "produce"),
        ing("club soda", 5, "oz"),
    ],
    glass="collins", method="built", garnish="orange wheel", abv=4,
))

RECIPES.append(cocktail(
    "Negroni Royale",
    year=None, region="USA",
    story="A modern craft variation on the Negroni topped with champagne — celebratory and dry.",
    desc="Negroni topped with champagne.",
    instructions=[
        "Stir gin, Campari, sweet vermouth with ice",
        "Strain into a coupe",
        "Top with champagne",
    ],
    ingredients=[
        ing("London dry gin", 0.75, "oz"),
        ing("Campari", 0.75, "oz"),
        ing("sweet vermouth", 0.75, "oz"),
        ing("champagne", 2, "oz"),
    ],
    glass="coupe", method="built", garnish="orange peel", abv=18,
))

RECIPES.append(cocktail(
    "Cynar Toronto",
    year=None, region="Canada",
    story="A modern Toronto variant subbing Cynar for Fernet — slightly less aggressive but just as bittersweet.",
    desc="Toronto with Cynar in place of Fernet.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Express orange peel",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("Cynar", 0.25, "oz"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 1, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=33,
))

RECIPES.append(cocktail(
    "Honey Bee Ti'Punch",
    year=None, region="Martinique",
    story="A modern variation on the traditional Ti' Punch using honey instead of cane syrup. Standard at French Caribbean-leaning craft bars.",
    desc="Ti' Punch with honey instead of cane syrup.",
    instructions=[
        "Squeeze lime peel into a small rocks glass",
        "Add honey syrup and rhum",
        "Stir gently",
    ],
    ingredients=[
        ing("rhum agricole blanc", 2, "oz"),
        ing("honey syrup", 0.25, "oz", "pantry"),
        ing("lime peel disc", 1, "each", "produce"),
    ],
    glass="rocks", method="built", garnish=None, abv=33,
))

RECIPES.append(cocktail(
    "Kalimotxo",
    year=None, region="Basque Country, Spain",
    story="The Basque Kalimotxo — equal parts red wine and Coca-Cola over ice — is a popular Spanish festival drink given craft attention in the 2010s.",
    desc="Basque red wine and Coca-Cola.",
    instructions=[
        "Build red wine and Coca-Cola over ice in a tall glass",
        "Stir gently",
        "Garnish with a lemon wheel",
    ],
    ingredients=[
        ing("dry red wine", 4, "oz"),
        ("Coca-Cola"),
        ing("Coca-Cola", 4, "oz"),
    ],
    glass="highball", method="built", garnish="lemon wheel", abv=6,
))

RECIPES.append(cocktail(
    "Tinto de Verano",
    year=None, region="Spain",
    story="The Spanish Tinto de Verano — red wine and lemon-lime soda over ice with citrus — is the working-class predecessor to Sangria, restored to attention by Iberian-leaning bars.",
    desc="Spanish red wine and lemon-lime soda summer drink.",
    instructions=[
        "Build red wine and lemon-lime soda over ice",
        "Add citrus slices",
        "Stir gently",
    ],
    ingredients=[
        ing("dry red wine", 4, "oz"),
        ("lemon-lime soda"),
        ing("lemon-lime soda", 4, "oz"),
        ing("orange slices", 2, "each", "produce"),
    ],
    glass="wine", method="built", garnish="orange and lemon", abv=6,
))

RECIPES.append(cocktail(
    "Sangria (Modern Spec)",
    year=None, region="Spain",
    story="The Spanish Sangria, restored to attention with proper red wine, fresh citrus, brandy and seasonal fruit — overhauled at modern craft Spanish restaurants.",
    desc="Modern craft Sangria.",
    instructions=[
        "Combine wine, brandy, sugar, citrus and fruit in a pitcher",
        "Refrigerate at least 2 hours",
        "Top with sparkling water before serving",
        "Pour into wine glasses with ice",
    ],
    ingredients=[
        ing("dry red wine", 24, "oz"),
        ("brandy"),
        ing("brandy", 2, "oz"),
        ing("sugar", 2, "tbsp", "pantry"),
        ing("orange slices", 4, "each", "produce"),
        ing("apple slices", 4, "each", "produce"),
        ing("club soda", 6, "oz"),
    ],
    glass="wine", method="batched", garnish="fresh fruit", abv=10, prep=120,
))

RECIPES.append(cocktail(
    "White Sangria",
    year=None, region="Spain",
    story="A craft white-wine variation on Sangria — Albariño, peach brandy, peach slices, citrus. Brighter and lighter than the red version.",
    desc="Modern white-wine Sangria.",
    instructions=[
        "Combine all in a pitcher and refrigerate 2 hours",
        "Top with soda before serving",
        "Pour over ice",
    ],
    ingredients=[
        ing("Albariño white wine", 24, "oz"),
        ("peach brandy"),
        ing("peach brandy", 2, "oz"),
        ing("sugar", 2, "tbsp", "pantry"),
        ("peach slices"),
        ing("peach slices", 4, "each", "produce"),
        ing("orange slices", 4, "each", "produce"),
        ing("club soda", 6, "oz"),
    ],
    glass="wine", method="batched", garnish="fruit slices", abv=10, prep=120,
))

RECIPES.append(cocktail(
    "Rosé Spritz",
    year=None, region="USA",
    story="A modern craft summer spritz built on rosé wine, Aperol and soda — brightly pink and dry.",
    desc="Rosé wine, Aperol and soda spritz.",
    instructions=[
        "Build Aperol in a wine glass over ice",
        "Top with rosé and a splash of soda",
        "Garnish with strawberry",
    ],
    ingredients=[
        ing("Aperol", 1, "oz"),
        ing("rosé wine", 4, "oz"),
        ing("club soda", 1, "oz"),
    ],
    glass="wine", method="built", garnish="strawberry", abv=8,
))

RECIPES.append(cocktail(
    "Frozen Rosé (Frosé)",
    year=2014, region="New York, USA", creator="Justin Sievers",
    story="Justin Sievers's Frosé at Bar Primi in NYC kicked off the 2014 frozen-rosé craze. Frozen rosé wine with strawberry purée and lemon — went viral and became the summer drink of the 2010s.",
    desc="Frozen rosé wine with strawberry — Bar Primi viral hit.",
    instructions=[
        "Freeze rosé in a shallow pan until slushy",
        "Blend with strawberry, lemon, simple and ice",
        "Pour into chilled coupes",
        "Garnish with mint",
    ],
    ingredients=[
        ing("rosé wine", 6, "oz"),
        ing("strawberries", 4, "each", "produce"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
    ],
    glass="coupe", method="blended", garnish="mint sprig", abv=8, prep=180,
))

RECIPES.append(cocktail(
    "Aperol Frozen Spritz",
    year=None, region="USA",
    story="A modern craft frozen variation on the Aperol Spritz — blended with prosecco for a slushy summer drink.",
    desc="Frozen blender Aperol Spritz.",
    instructions=[
        "Blend Aperol, frozen prosecco cubes and ice until slushy",
        "Pour into a wine glass",
        "Garnish with orange",
    ],
    ingredients=[
        ing("Aperol", 2, "oz"),
        ("frozen prosecco cubes"),
        ing("frozen prosecco", 4, "oz", "freezer"),
        ing("fresh lemon juice", 0.25, "oz", "produce"),
    ],
    glass="wine", method="blended", garnish="orange wheel", abv=8,
))

RECIPES.append(cocktail(
    "Frozen Negroni",
    year=None, region="USA",
    story="A modern craft frozen Negroni — blended for slushy summer service. Standard at hot-weather Italian craft bars.",
    desc="Frozen Negroni slush.",
    instructions=[
        "Blend gin, Campari, sweet vermouth and ice until slushy",
        "Pour into a chilled wine glass",
        "Garnish with orange wheel",
    ],
    ingredients=[
        ing("London dry gin", 1, "oz"),
        ing("Campari", 1, "oz"),
        ing("sweet vermouth", 1, "oz"),
    ],
    glass="wine", method="blended", garnish="orange wheel", abv=20,
))

RECIPES.append(cocktail(
    "Frozen Margarita",
    year=1971, region="Dallas, USA", creator="Mariano Martinez",
    story="Mariano Martinez invented the frozen Margarita machine in 1971 in Dallas, modifying a soft-serve ice cream machine. The Smithsonian holds the original. Frozen Margaritas became Tex-Mex restaurant fixtures.",
    desc="Mariano Martinez's blender Margarita classic.",
    instructions=[
        "Blend tequila, Cointreau, lime, simple and crushed ice until slushy",
        "Pour into a salt-rimmed Margarita glass",
        "Garnish with a lime wheel",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("Cointreau", 0.5, "oz"),
        ing("fresh lime juice", 1, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
    ],
    glass="margarita", method="blended", garnish="lime wheel and salt rim", abv=18,
))

RECIPES.append(cocktail(
    "Frozen Daiquiri (Modern Spec)",
    year=None, region="USA",
    story="The Floridita-style frozen Daiquiri restored to craft attention by Audrey Saunders, Dale DeGroff, and modern Cuban-leaning bars.",
    desc="Modern proper-spec frozen Daiquiri.",
    instructions=[
        "Blend rum, lime, simple and crushed ice for 6 seconds",
        "Pour into a chilled coupe",
    ],
    ingredients=[
        ing("white rum", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
    ],
    glass="coupe", method="blended", garnish=None, abv=22,
))

RECIPES.append(cocktail(
    "Hemingway Daiquiri (Frozen)",
    year=None, region="USA",
    story="The frozen variation on Constantino's no-sugar Hemingway Daiquiri — rum, lime, grapefruit, maraschino, blender ice.",
    desc="Frozen Hemingway Daiquiri.",
    instructions=[
        "Blend all with crushed ice for 6 seconds",
        "Pour into a chilled coupe",
    ],
    ingredients=[
        ing("white rum", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("fresh grapefruit juice", 0.5, "oz", "produce"),
        ing("maraschino liqueur", 0.25, "oz"),
    ],
    glass="coupe", method="blended", garnish=None, abv=22,
))

RECIPES.append(cocktail(
    "Espresso Old Fashioned",
    year=None, region="USA",
    story="A modern craft cross of the Old Fashioned and the Espresso Martini — bourbon, fresh espresso, demerara, chocolate bitters.",
    desc="Bourbon Old Fashioned with fresh espresso.",
    instructions=[
        "Stir bourbon, espresso, syrup and bitters with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with orange peel",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("fresh espresso", 0.5, "oz", "pantry"),
        ing("Demerara syrup", 0.25, "oz", "pantry"),
        ing("chocolate bitters", 2, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=32,
))

RECIPES.append(cocktail(
    "Manhattan Project",
    year=None, region="USA",
    story="A modern craft Manhattan variation incorporating Amaro Nonino — softer and more aromatic than the standard.",
    desc="Manhattan with Amaro Nonino.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
        "Garnish with brandied cherry",
    ],
    ingredients=[
        ing("rye whiskey", 1.5, "oz"),
        ing("sweet vermouth", 0.75, "oz"),
        ing("Amaro Nonino", 0.5, "oz"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="coupe", method="stirred", garnish="brandied cherry", abv=27,
))

RECIPES.append(cocktail(
    "Jewel of the Nile",
    year=2005, region="New York, USA", creator="Audrey Saunders", bar="Pegu Club",
    story="Audrey Saunders's Jewel of the Nile — gin with Yellow and Green Chartreuse and a mint leaf — became a Pegu Club signature.",
    desc="Gin with double Chartreuse and mint.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
        "Garnish with a mint leaf",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("Yellow Chartreuse", 0.25, "oz"),
        ing("Green Chartreuse", 0.25, "oz"),
        ing("orange bitters", 2, "dash"),
    ],
    glass="coupe", method="stirred", garnish="mint leaf", abv=27,
))

RECIPES.append(cocktail(
    "Fitzgerald",
    year=2002, region="New York, USA", creator="Dale DeGroff",
    story="Dale DeGroff's Fitzgerald — gin, lemon, simple, Angostura — is essentially a perfectly balanced Gin Sour, named for F. Scott Fitzgerald and the literary 1920s heritage of the cocktail.",
    desc="Gin sour with Angostura.",
    instructions=[
        "Shake all with ice",
        "Double strain into a coupe",
        "Garnish with lemon peel",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.75, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="coupe", garnish="lemon peel", abv=22,
))

RECIPES.append(cocktail(
    "Whiskey Daisy",
    year=None, region="USA",
    story="The 19th-century Daisy template — spirit, citrus, sugar, sometimes a liqueur — restored by craft bartenders as the precursor to the Margarita.",
    desc="Whiskey daisy with grenadine and lemon.",
    instructions=[
        "Shake all with ice",
        "Strain over crushed ice in a rocks glass",
        "Garnish with seasonal berry",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("orange liqueur", 0.5, "oz"),
        ing("grenadine", 0.5, "oz", "pantry"),
    ],
    glass="rocks", garnish="berry", abv=22,
))

RECIPES.append(cocktail(
    "Brandy Daisy",
    year=None, region="USA",
    story="The brandy version of the Daisy template — cognac, lemon, orange liqueur, grenadine. A 19th-century template reborn at modern craft bars.",
    desc="Cognac daisy with grenadine and lemon.",
    instructions=[
        "Shake all with ice",
        "Strain over crushed ice in a rocks glass",
        "Garnish with seasonal berry",
    ],
    ingredients=[
        ing("cognac", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("orange liqueur", 0.5, "oz"),
        ing("grenadine", 0.5, "oz", "pantry"),
    ],
    glass="rocks", garnish="berry", abv=22,
))

RECIPES.append(cocktail(
    "Gin Daisy",
    year=None, region="USA",
    story="The gin daisy was a 19th-century daisy template, restored by craft bartenders — gin, lemon, orange liqueur, grenadine.",
    desc="Gin daisy.",
    instructions=[
        "Shake all with ice",
        "Strain over crushed ice in a rocks glass",
        "Garnish with seasonal berry",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("orange liqueur", 0.5, "oz"),
        ing("grenadine", 0.5, "oz", "pantry"),
    ],
    glass="rocks", garnish="berry", abv=22,
))

RECIPES.append(cocktail(
    "Yacht Club",
    year=None, region="USA",
    story="A modern craft Manhattan-template variation — rye, Madeira, maraschino — gentle and oxidative.",
    desc="Rye Manhattan with Madeira.",
    instructions=[
        "Stir all with ice",
        "Strain into a coupe",
        "Express orange peel",
    ],
    ingredients=[
        ing("rye whiskey", 1.5, "oz"),
        ("Madeira"),
        ing("Madeira", 1, "oz"),
        ing("maraschino liqueur", 0.25, "oz"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="coupe", method="stirred", garnish="orange peel", abv=24,
))

RECIPES.append(cocktail(
    "Scofflaw",
    year=None, region="Paris, France",
    story="The 1924 Scofflaw was created at Harry's New York Bar Paris in response to American Prohibition's 'scofflaw' label. Restored to canon by 2000s craft bartenders.",
    desc="Rye Prohibition-era sour with grenadine.",
    instructions=[
        "Shake all with ice",
        "Double strain into a coupe",
        "Garnish with a lemon peel",
    ],
    ingredients=[
        ing("rye whiskey", 1.5, "oz"),
        ing("dry vermouth", 1, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("grenadine", 0.5, "oz", "pantry"),
        ing("orange bitters", 1, "dash"),
    ],
    glass="coupe", garnish="lemon peel", abv=22,
))

RECIPES.append(cocktail(
    "Income Tax",
    year=None, region="USA",
    story="A modern restoration of the 1930s Bronx-style Income Tax — gin, dry and sweet vermouth, orange juice, Angostura.",
    desc="Gin Bronx-style with orange juice.",
    instructions=[
        "Shake all with ice",
        "Double strain into a coupe",
        "Garnish with orange peel",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("dry vermouth", 0.5, "oz"),
        ing("sweet vermouth", 0.5, "oz"),
        ing("fresh orange juice", 0.75, "oz", "produce"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="coupe", garnish="orange peel", abv=20,
))

RECIPES.append(cocktail(
    "Bronx (Modern Spec)",
    year=None, region="New York, USA",
    story="The 1900-era Bronx — gin, sweet and dry vermouth, orange juice — was restored at craft bars in the 2010s with fresh-pressed orange.",
    desc="Bronx with fresh orange juice.",
    instructions=[
        "Shake all with ice",
        "Double strain into a coupe",
        "Garnish with orange peel",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("dry vermouth", 0.5, "oz"),
        ing("sweet vermouth", 0.5, "oz"),
        ing("fresh orange juice", 0.75, "oz", "produce"),
    ],
    glass="coupe", garnish="orange peel", abv=20,
))

RECIPES.append(cocktail(
    "Satan's Whiskers",
    year=None, region="USA",
    story="A 1930 Savoy Cocktail Book classic — gin, dry and sweet vermouth, orange juice, Grand Marnier, orange bitters. Restored to craft bar attention by the 2010s.",
    desc="Gin and Grand Marnier sour with orange.",
    instructions=[
        "Shake all with ice",
        "Double strain into a coupe",
        "Garnish with orange peel",
    ],
    ingredients=[
        ing("London dry gin", 1, "oz"),
        ing("dry vermouth", 0.5, "oz"),
        ing("sweet vermouth", 0.5, "oz"),
        ing("Grand Marnier", 0.5, "oz"),
        ing("fresh orange juice", 0.5, "oz", "produce"),
        ing("orange bitters", 2, "dash"),
    ],
    glass="coupe", garnish="orange peel", abv=22,
))

RECIPES.append(cocktail(
    "Twentieth Century",
    year=None, region="USA",
    story="The 1937 Cafe Royal Cocktail Book Twentieth Century — gin, Lillet Blanc, lemon, white crème de cacao — was restored by 2000s craft bartenders.",
    desc="Gin sour with Lillet and white crème de cacao.",
    instructions=[
        "Shake all with ice",
        "Double strain into a coupe",
        "Garnish with lemon peel",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("Lillet Blanc", 0.75, "oz"),
        ("white crème de cacao"),
        ing("white crème de cacao", 0.5, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
    ],
    glass="coupe", garnish="lemon peel", abv=22,
))

RECIPES.append(cocktail(
    "Savoy Hotel",
    year=None, region="London, UK",
    story="A 1930 Savoy classic restored to canon by 2000s craft bartenders — equal parts brandy, Bénédictine, dark crème de cacao layered.",
    desc="Layered brandy, Bénédictine, crème de cacao.",
    instructions=[
        "Layer crème de cacao, Bénédictine, brandy in a cordial glass",
    ],
    ingredients=[
        ing("brandy", 0.5, "oz"),
        ing("Bénédictine", 0.5, "oz"),
        ("dark crème de cacao"),
        ing("dark crème de cacao", 0.5, "oz"),
    ],
    glass="cordial", method="layered", garnish=None, abv=24,
))

RECIPES.append(cocktail(
    "20th Century (Modern Spec)",
    year=None, region="USA",
    story="The 20th Century, restored by 2000s craft bartenders to its proper proportions, balances gin, Lillet, lemon and white crème de cacao for a deceptively complex sour.",
    desc="Modern proper-spec 20th Century cocktail.",
    instructions=[
        "Shake all with ice",
        "Double strain into a coupe",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("Lillet Blanc", 0.75, "oz"),
        ing("white crème de cacao", 0.5, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
    ],
    glass="coupe", garnish="lemon peel", abv=22,
))

RECIPES.append(cocktail(
    "Lavender Lemonade",
    year=None, region="USA",
    story="A modern craft summer cooler — gin or vodka, lavender syrup, lemonade. Standard at brunch craft bars.",
    desc="Lavender-gin lemonade.",
    instructions=[
        "Build gin and lavender syrup over ice in a Collins glass",
        "Top with lemonade",
        "Garnish with lavender sprig",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ("lavender syrup"),
        ing("lavender syrup", 0.5, "oz", "pantry"),
        ing("fresh lemonade", 5, "oz", "pantry"),
    ],
    glass="collins", method="built", garnish="lavender sprig", abv=10,
))

RECIPES.append(cocktail(
    "Hibiscus Margarita",
    year=None, region="USA",
    story="A modern craft Margarita variation built on hibiscus tea syrup — vivid magenta and floral. Standard at modern Mexican bars.",
    desc="Margarita with hibiscus syrup.",
    instructions=[
        "Shake all with ice",
        "Strain over fresh ice in a salt-rimmed rocks glass",
        "Garnish with hibiscus flower",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("Cointreau", 0.5, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ("hibiscus syrup"),
        ing("hibiscus syrup", 0.5, "oz", "pantry"),
    ],
    glass="rocks", garnish="hibiscus flower and salt rim", abv=22,
))

RECIPES.append(cocktail(
    "Avocado Margarita",
    year=None, region="USA",
    story="A modern craft Margarita variation — tequila, avocado, lime, agave, blended for a creamy savory version. Standard at modern California Mexican bars.",
    desc="Blended Margarita with fresh avocado.",
    instructions=[
        "Blend all with crushed ice until smooth",
        "Pour into a salt-rimmed rocks glass",
        "Garnish with avocado slice",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ("avocado"),
        ing("ripe avocado", 0.25, "each", "produce"),
        ing("agave syrup", 0.5, "oz", "pantry"),
    ],
    glass="rocks", method="blended", garnish="avocado slice and salt rim", abv=20,
))

RECIPES.append(cocktail(
    "Cucumber Lavender Spritz",
    year=None, region="USA",
    story="A modern craft summer spritz built on gin, cucumber, lavender, lemon and prosecco — standard at modern garden parties.",
    desc="Cucumber-lavender prosecco spritz.",
    instructions=[
        "Muddle cucumber in shaker",
        "Add gin, lavender syrup, lemon, ice; shake briefly",
        "Strain into a wine glass over ice",
        "Top with prosecco; garnish with lavender",
    ],
    ingredients=[
        ing("London dry gin", 1, "oz"),
        ("cucumber slices"),
        ing("cucumber slices", 3, "each", "produce"),
        ing("lavender syrup", 0.5, "oz", "pantry"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("prosecco", 3, "oz"),
    ],
    glass="wine", garnish="lavender sprig", abv=10,
))

RECIPES.append(cocktail(
    "Mezcal Spritz",
    year=None, region="USA",
    story="A modern craft Italian-Mexican hybrid spritz — mezcal, Aperol, prosecco, soda over ice.",
    desc="Mezcal-Aperol prosecco spritz.",
    instructions=[
        "Build mezcal and Aperol in a wine glass over ice",
        "Top with prosecco and soda",
        "Garnish with grapefruit peel",
    ],
    ingredients=[
        ing("mezcal", 1, "oz"),
        ing("Aperol", 1.5, "oz"),
        ing("prosecco", 2, "oz"),
        ing("club soda", 1, "oz"),
    ],
    glass="wine", method="built", garnish="grapefruit peel", abv=10,
))

RECIPES.append(cocktail(
    "Tequila Spritz",
    year=None, region="USA",
    story="A modern craft tequila spritz built on blanco tequila, Aperol, grapefruit and soda over ice — Mexican-Italian crossover.",
    desc="Blanco tequila-Aperol grapefruit spritz.",
    instructions=[
        "Build all in a wine glass over ice",
        "Stir gently",
        "Garnish with grapefruit wheel",
    ],
    ingredients=[
        ing("blanco tequila", 1, "oz"),
        ing("Aperol", 1, "oz"),
        ing("fresh grapefruit juice", 1, "oz", "produce"),
        ing("club soda", 3, "oz"),
    ],
    glass="wine", method="built", garnish="grapefruit wheel", abv=10,
))

RECIPES.append(cocktail(
    "Spaghetti Bender",
    year=None, region="USA",
    story="A modern craft cocktail combining Fernet, Campari and grapefruit — a bartender-favorite bittersweet sour.",
    desc="Fernet-Campari grapefruit sour.",
    instructions=[
        "Shake all with ice",
        "Strain over crushed ice in a rocks glass",
        "Garnish with grapefruit peel",
    ],
    ingredients=[
        ing("Fernet-Branca", 1, "oz"),
        ing("Campari", 1, "oz"),
        ing("fresh grapefruit juice", 1, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
    ],
    glass="rocks", garnish="grapefruit peel", abv=22,
))

RECIPES.append(cocktail(
    "Sherry Spritz",
    year=None, region="Spain",
    story="A modern Iberian spritz built on fino sherry, soda and citrus — the Spanish answer to the Italian spritz.",
    desc="Fino sherry highball spritz.",
    instructions=[
        "Build fino sherry over ice in a wine glass",
        "Top with soda; squeeze lemon",
        "Garnish with olive and lemon peel",
    ],
    ingredients=[
        ing("fino sherry", 3, "oz"),
        ing("club soda", 3, "oz"),
        ing("fresh lemon juice", 0.25, "oz", "produce"),
    ],
    glass="wine", method="built", garnish="olive and lemon peel", abv=8,
))

RECIPES.append(cocktail(
    "Rebujito",
    year=None, region="Andalusia, Spain",
    story="The Andalusian Rebujito — fino sherry and lemon-lime soda over ice with mint — is the signature drink of the Feria de Sevilla. A craft Spanish bar staple.",
    desc="Andalusian sherry and soda festival drink.",
    instructions=[
        "Build sherry in a tall glass over ice",
        "Top with lemon-lime soda; add mint",
    ],
    ingredients=[
        ing("fino sherry", 3, "oz"),
        ("lemon-lime soda"),
        ing("lemon-lime soda", 5, "oz"),
        ing("mint sprig", 1, "each", "produce"),
    ],
    glass="highball", method="built", garnish="mint sprig", abv=6,
))

RECIPES.append(cocktail(
    "Kalimotxo (Modern)",
    year=None, region="Basque Country, Spain",
    story="The traditional Basque Kalimotxo restored to international craft attention — equal parts inexpensive red wine and Coca-Cola served over ice. The original 'low-brow craft' drink.",
    desc="Basque red wine and Coca-Cola over ice.",
    instructions=[
        "Build red wine and Coca-Cola in a tall glass over ice",
        "Garnish with lemon wedge",
    ],
    ingredients=[
        ing("dry red wine", 4, "oz"),
        ing("Coca-Cola", 4, "oz"),
    ],
    glass="highball", method="built", garnish="lemon wedge", abv=6,
))

RECIPES.append(cocktail(
    "Bourbon Milk Punch",
    year=None, region="New Orleans, USA",
    story="The New Orleans bourbon milk punch — bourbon, whole milk, vanilla, sugar, nutmeg — restored to brunch menus by craft bars in the 2010s.",
    desc="New Orleans brunch bourbon milk punch.",
    instructions=[
        "Shake all with ice",
        "Strain into a chilled rocks glass",
        "Dust with grated nutmeg",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("whole milk", 4, "oz", "dairy"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("vanilla extract", 0.25, "tsp", "pantry"),
    ],
    glass="rocks", garnish="grated nutmeg", abv=10,
))

RECIPES.append(cocktail(
    "Brandy Milk Punch",
    year=None, region="New Orleans, USA",
    story="The brandy variation on the New Orleans milk punch — brandy, milk, vanilla, sugar, nutmeg — a brunch institution at the Brennan family restaurants.",
    desc="New Orleans brandy milk punch.",
    instructions=[
        "Shake all with ice",
        "Strain into a chilled rocks glass",
        "Dust with grated nutmeg",
    ],
    ingredients=[
        ing("brandy", 2, "oz"),
        ing("whole milk", 4, "oz", "dairy"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("vanilla extract", 0.25, "tsp", "pantry"),
    ],
    glass="rocks", garnish="grated nutmeg", abv=10,
))

RECIPES.append(cocktail(
    "Ramos Gin Fizz (Modern Spec)",
    year=None, region="New Orleans, USA",
    story="Henry Ramos's 1888 Gin Fizz — gin, lemon, lime, sugar, cream, egg white, orange flower water, soda, shaken for 12 minutes — restored at craft bars where bartenders still hand-shake the original endurance drink.",
    desc="Henry Ramos's 12-minute shaken Gin Fizz.",
    instructions=[
        "Dry-shake all but soda for 2 minutes",
        "Add ice and shake hard for 10 minutes (or use a sous-vide trick)",
        "Strain into a chilled fizz glass",
        "Top with chilled soda from a fresh bottle",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("simple syrup", 1, "oz", "pantry"),
        ing("heavy cream", 1, "oz", "dairy"),
        ing("egg white", 1, "each", "dairy"),
        ("orange flower water"),
        ing("orange flower water", 3, "drop", "pantry"),
        ing("club soda", 1.5, "oz"),
    ],
    glass="fizz", garnish=None, abv=14, prep=12,
))

RECIPES.append(cocktail(
    "Silver Fizz",
    year=None, region="USA",
    story="The silver fizz adds an egg white to the classic Gin Fizz — restored at craft bars in the 2010s as part of the egg-cocktail revival.",
    desc="Gin fizz with egg white.",
    instructions=[
        "Dry-shake all but soda",
        "Add ice and shake again",
        "Strain into a fizz glass",
        "Top with soda",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.75, "oz", "pantry"),
        ing("egg white", 1, "each", "dairy"),
        ing("club soda", 2, "oz"),
    ],
    glass="fizz", garnish=None, abv=12,
))

RECIPES.append(cocktail(
    "Golden Fizz",
    year=None, region="USA",
    story="The golden fizz adds an egg yolk to the gin fizz template — restored to canon as part of the egg-cocktail revival.",
    desc="Gin fizz with egg yolk.",
    instructions=[
        "Dry-shake all but soda",
        "Add ice and shake",
        "Strain into a fizz glass",
        "Top with soda",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.75, "oz", "pantry"),
        ing("egg yolk", 1, "each", "dairy"),
        ing("club soda", 2, "oz"),
    ],
    glass="fizz", garnish=None, abv=12,
))

RECIPES.append(cocktail(
    "Royal Fizz",
    year=None, region="USA",
    story="The royal fizz uses a whole egg in the gin fizz template — restored to craft bar attention by the egg-cocktail revival.",
    desc="Gin fizz with whole egg.",
    instructions=[
        "Dry-shake all but soda",
        "Add ice and shake hard",
        "Strain into a fizz glass",
        "Top with soda",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.75, "oz", "pantry"),
        ing("whole egg", 1, "each", "dairy"),
        ing("club soda", 2, "oz"),
    ],
    glass="fizz", garnish=None, abv=12,
))

RECIPES = [_clean(r) for r in RECIPES]

"""Part 5: Additional signature modern craft drinks — broader catalog."""
from build_modern_craft import cocktail, ing, _clean

RECIPES = []

# ============================================================================
# MODERN BAR SIGNATURES (continued, broad)
# ============================================================================

RECIPES.append(cocktail(
    "Revolver",
    year=2003, region="San Francisco, USA", creator="Jon Santer", bar="Bourbon & Branch",
    story="Jon Santer's Revolver — bourbon, coffee liqueur, orange bitters with a flamed orange peel — was created at Bruno's in San Francisco around 2003 and became a defining modern bourbon Manhattan variant.",
    desc="Bourbon-coffee Manhattan with flamed orange.",
    instructions=[
        "Stir bourbon, coffee liqueur and bitters with ice",
        "Strain into a chilled coupe",
        "Flame an orange peel over the surface",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("coffee liqueur", 0.5, "oz"),
        ing("orange bitters", 2, "dash"),
    ],
    glass="coupe", method="stirred", garnish="flamed orange peel", abv=27,
))

RECIPES.append(cocktail(
    "Trinity",
    year=2008, region="USA", creator="Sam Ross",
    story="Sam Ross's Trinity is an equal-parts split of mezcal, fino sherry and Lillet Rosé — designed as a modern aperitif marrying Mexican smoke with Spanish-French aromatics.",
    desc="Mezcal, fino sherry and Lillet Rosé low-ABV stir.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
        "Garnish with grapefruit peel",
    ],
    ingredients=[
        ing("mezcal", 1, "oz"),
        ing("fino sherry", 1, "oz"),
        ing("Lillet Rosé", 1, "oz"),
    ],
    glass="coupe", method="stirred", garnish="grapefruit peel", abv=18,
))

RECIPES.append(cocktail(
    "Bee Sting",
    year=None, region="USA",
    story="A modern craft tequila sour pairing honey, lemon, jalapeño and reposado tequila — sweet, tart, spicy.",
    desc="Reposado tequila sour with honey, jalapeño and lemon.",
    instructions=[
        "Muddle jalapeño in shaker",
        "Add tequila, lemon, honey syrup; shake with ice",
        "Double strain over fresh ice in a rocks glass",
        "Garnish with a jalapeño slice",
    ],
    ingredients=[
        ing("reposado tequila", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("honey syrup", 0.5, "oz", "pantry"),
        ing("jalapeño slices", 3, "each", "produce"),
    ],
    glass="rocks", garnish="jalapeño slice", abv=22,
))

RECIPES.append(cocktail(
    "Smoke Show",
    year=None, region="USA",
    story="A modern smoky-bourbon riff using mezcal-rinsed glassware and bourbon Old Fashioned base. Standard at speakeasy bars in the 2010s.",
    desc="Bourbon Old Fashioned with mezcal rinse.",
    instructions=[
        "Rinse a chilled rocks glass with mezcal; discard",
        "Stir bourbon, syrup, bitters with ice",
        "Strain into rinsed glass over a large cube",
        "Garnish with orange peel",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("Demerara syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
        ing("mezcal", 1, "rinse"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=32,
))

RECIPES.append(cocktail(
    "Pendennis Cocktail",
    year=None, region="Louisville, USA", bar="Pendennis Club",
    story="The Pendennis Cocktail — gin, apricot liqueur, lime, Peychaud's — comes from Louisville's Pendennis Club, said also to be the original Old Fashioned's birthplace. Restored by 2000s craft bartenders.",
    desc="Gin-apricot-lime sour with Peychaud's.",
    instructions=[
        "Shake all with ice",
        "Double strain into a chilled coupe",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("apricot liqueur", 0.75, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("Peychaud's bitters", 2, "dash"),
    ],
    glass="coupe", garnish=None, abv=22,
))

RECIPES.append(cocktail(
    "Champs Élysées",
    year=None, region="Paris, France",
    story="The 1930 Savoy Cocktail Book Champs Élysées — cognac, Green Chartreuse, lemon, sugar, Angostura — was rediscovered by 21st-century craft bartenders as a sophisticated cognac sour.",
    desc="Cognac and Green Chartreuse sour.",
    instructions=[
        "Shake all ingredients with ice",
        "Double strain into a chilled coupe",
        "Garnish with a lemon peel",
    ],
    ingredients=[
        ing("cognac", 1.5, "oz"),
        ing("Green Chartreuse", 0.5, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 1, "dash"),
    ],
    glass="coupe", garnish="lemon peel", abv=22,
))

RECIPES.append(cocktail(
    "Diamondback",
    year=None, region="USA",
    story="A pre-Prohibition rye-applejack-Yellow Chartreuse stir restored to canon by 2000s craft bartenders — bracing, dry, and quietly powerful.",
    desc="Rye, applejack and Yellow Chartreuse stir.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
    ],
    ingredients=[
        ing("rye whiskey", 1.5, "oz"),
        ("applejack"),
        ing("applejack", 0.75, "oz"),
        ing("Yellow Chartreuse", 0.75, "oz"),
    ],
    glass="coupe", method="stirred", garnish=None, abv=33,
))

RECIPES.append(cocktail(
    "Widow's Kiss",
    year=1895, region="USA", creator="George Kappeler",
    story="George Kappeler's 1895 apple-brandy-Chartreuse-Bénédictine stir lay forgotten for nearly a century before being revived by craft bartenders. Now an essential autumn pour.",
    desc="Apple brandy with Chartreuse and Bénédictine.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
        "Garnish with a brandied cherry",
    ],
    ingredients=[
        ing("apple brandy", 1.5, "oz"),
        ing("Yellow Chartreuse", 0.75, "oz"),
        ing("Bénédictine", 0.75, "oz"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="coupe", method="stirred", garnish="brandied cherry", abv=27,
))

RECIPES.append(cocktail(
    "Fairbank",
    year=None, region="USA",
    story="A pre-Prohibition gin-vermouth stir with crème de noyaux and orange bitters, restored to the modern stirred-drink revival of the 2000s.",
    desc="Gin Martini variant with crème de noyaux.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain into a chilled coupe",
        "Garnish with a brandied cherry",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("dry vermouth", 0.75, "oz"),
        ing("crème de noyaux", 1, "tsp"),
        ing("orange bitters", 2, "dash"),
    ],
    glass="coupe", method="stirred", garnish="brandied cherry", abv=27,
))

RECIPES.append(cocktail(
    "Martinez (Modern Spec)",
    year=None, region="USA",
    story="The proto-Martini — Old Tom gin, sweet vermouth, maraschino, Angostura — was rediscovered by craft bartenders as Old Tom gins returned to market in the 2000s.",
    desc="Old Tom gin proto-Martini with maraschino.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
        "Express orange peel",
    ],
    ingredients=[
        ing("Old Tom gin", 2, "oz"),
        ing("sweet vermouth", 1, "oz"),
        ing("maraschino liqueur", 1, "tsp"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="coupe", method="stirred", garnish="orange peel", abv=27,
))

RECIPES.append(cocktail(
    "Improved Holland Gin Cocktail",
    year=None, region="USA",
    story="Jerry Thomas's 1862 genever-based Old Fashioned restored to canon by craft bartenders as Dutch genever returned to American markets.",
    desc="Genever Old Fashioned with maraschino.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Express lemon peel",
    ],
    ingredients=[
        ing("genever", 2, "oz"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("maraschino liqueur", 1, "tsp"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="rocks", method="stirred", garnish="lemon peel", abv=30,
))

RECIPES.append(cocktail(
    "Sherry Flip (Modern)",
    year=None, region="USA",
    story="The Sherry Flip — sherry, sugar, whole egg, nutmeg — has come back into modern craft bars as part of the 2010s sherry revival.",
    desc="Sherry flip with whole egg and nutmeg.",
    instructions=[
        "Dry-shake all ingredients without ice",
        "Add ice and shake again",
        "Double strain into a chilled coupe",
        "Dust with nutmeg",
    ],
    ingredients=[
        ing("oloroso sherry", 2, "oz"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("whole egg", 1, "each", "dairy"),
    ],
    glass="coupe", garnish="grated nutmeg", abv=12,
))

RECIPES.append(cocktail(
    "Whiskey Flip",
    year=None, region="USA",
    story="The 19th-century Whiskey Flip — bourbon or rye, sugar, whole egg — returned to craft bar menus alongside the egg-cocktail revival of the 2010s.",
    desc="Whiskey flip with whole egg and nutmeg.",
    instructions=[
        "Dry-shake all without ice",
        "Add ice and shake again",
        "Double strain into a coupe",
        "Dust with nutmeg",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("whole egg", 1, "each", "dairy"),
    ],
    glass="coupe", garnish="grated nutmeg", abv=20,
))

RECIPES.append(cocktail(
    "Gin Flip",
    year=None, region="USA",
    story="The classic gin flip — gin, sugar, whole egg — has returned to craft menus as bartenders rediscovered egg-cocktail technique.",
    desc="Gin flip with whole egg and nutmeg.",
    instructions=[
        "Dry-shake all without ice",
        "Add ice and shake again",
        "Double strain into a coupe",
        "Dust with nutmeg",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("whole egg", 1, "each", "dairy"),
    ],
    glass="coupe", garnish="grated nutmeg", abv=20,
))

RECIPES.append(cocktail(
    "Bourbon Renewal",
    year=2007, region="New York, USA", creator="Jeffrey Morgenthaler",
    story="Jeffrey Morgenthaler's Bourbon Renewal — bourbon, lemon, simple, crème de cassis, Angostura — became a quietly influential modern bourbon sour.",
    desc="Bourbon sour with crème de cassis bleed.",
    instructions=[
        "Shake all with ice",
        "Strain over crushed ice in a rocks glass",
        "Garnish with a lemon wheel",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("fresh lemon juice", 1, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("crème de cassis", 0.5, "oz"),
        ing("Angostura bitters", 1, "dash"),
    ],
    glass="rocks", garnish="lemon wheel", abv=20,
))

RECIPES.append(cocktail(
    "Richmond Gimlet",
    year=2011, region="Portland, USA", creator="Jeffrey Morgenthaler",
    story="Jeffrey Morgenthaler's Richmond Gimlet at Clyde Common — gin, mint, lime, elderflower — earned a national following for its herbal balance.",
    desc="Gin Gimlet with mint and elderflower.",
    instructions=[
        "Muddle mint in shaker",
        "Add gin, lime, elderflower; shake with ice",
        "Double strain into a chilled coupe",
        "Garnish with a mint sprig",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("St-Germain elderflower liqueur", 0.5, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.25, "oz", "pantry"),
        ing("mint leaves", 6, "each", "produce"),
    ],
    glass="coupe", garnish="mint sprig", abv=20,
))

RECIPES.append(cocktail(
    "Amaretto Sour (Morgenthaler)",
    year=2012, region="Portland, USA", creator="Jeffrey Morgenthaler", bar="Clyde Common",
    story="Jeffrey Morgenthaler's reformulation of the Amaretto Sour at Clyde Common added overproof bourbon and egg white. The result rehabilitated a long-mocked drink and went viral.",
    desc="Morgenthaler's reformulated Amaretto Sour with overproof bourbon and egg white.",
    instructions=[
        "Dry-shake all ingredients without ice",
        "Add ice and shake again",
        "Strain over fresh ice in a rocks glass",
        "Garnish with a brandied cherry and lemon peel",
    ],
    ingredients=[
        ing("amaretto", 1.5, "oz"),
        ing("overproof bourbon", 0.75, "oz"),
        ing("fresh lemon juice", 1, "oz", "produce"),
        ing("simple syrup", 1, "tsp", "pantry"),
        ing("egg white", 0.5, "each", "dairy"),
    ],
    glass="rocks", garnish="brandied cherry and lemon peel", abv=18,
))

RECIPES.append(cocktail(
    "Whisky Macarthur",
    year=None, region="Scotland",
    story="A modern Scotch craft cocktail combining blended Scotch with Drambuie, lemon and a touch of marmalade — a sweetened Rusty Nail variant that emerged in 2010s craft bars.",
    desc="Scotch sour with Drambuie and marmalade.",
    instructions=[
        "Shake all with ice",
        "Double strain into a chilled coupe",
        "Garnish with an orange peel",
    ],
    ingredients=[
        ing("blended Scotch", 1.5, "oz"),
        ing("Drambuie", 0.5, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ("orange marmalade"),
        ing("orange marmalade", 1, "tsp", "pantry"),
    ],
    glass="coupe", garnish="orange peel", abv=22,
))

RECIPES.append(cocktail(
    "Highball Mizuwari",
    year=None, region="Japan",
    story="The Mizuwari — Japanese whisky with cold mineral water and ice — is a precision Japanese ritual treated as a serving format in its own right at Tokyo high-end bars.",
    desc="Japanese whisky and water precision pour.",
    instructions=[
        "Place a single hand-cut ice cube in a chilled glass",
        "Pour Japanese whisky over the ice and stir 13 times",
        "Add cold mineral water in a 2:1 water-to-whisky ratio",
        "Stir once gently",
    ],
    ingredients=[
        ing("Japanese whisky", 1.5, "oz"),
        ing("cold mineral water", 3, "oz", "pantry"),
    ],
    glass="rocks", method="built", garnish=None, abv=14,
))

RECIPES.append(cocktail(
    "Fog Cutter (Modern Spec)",
    year=1942, region="USA", creator="Trader Vic",
    story="Trader Vic's 1942 Fog Cutter combines rum, gin, brandy with citrus, orgeat and a sherry float — restored to modern tiki menus as one of the most impressive multi-base tiki creations.",
    desc="Trader Vic's three-spirit tiki with sherry float.",
    instructions=[
        "Shake rum, gin, brandy, lemon, orange, orgeat with ice",
        "Strain over crushed ice in a Collins glass",
        "Float oloroso sherry on top",
        "Garnish with a mint sprig",
    ],
    ingredients=[
        ing("light rum", 2, "oz"),
        ("brandy"),
        ing("brandy", 1, "oz"),
        ("gin"),
        ing("gin", 0.5, "oz"),
        ing("fresh lemon juice", 1, "oz", "produce"),
        ing("fresh orange juice", 2, "oz", "produce"),
        ing("orgeat", 0.5, "oz", "pantry"),
        ing("oloroso sherry", 0.5, "oz"),
    ],
    glass="collins", garnish="mint sprig", abv=18,
))

RECIPES.append(cocktail(
    "Scorpion (Modern Spec)",
    year=None, region="USA", creator="Trader Vic",
    story="Trader Vic's Scorpion — rum, brandy, orange, lemon, orgeat — is a tiki classic for sharing, often served in a punch bowl with a gardenia float.",
    desc="Trader Vic's rum-brandy tiki with orgeat.",
    instructions=[
        "Blend all ingredients with crushed ice for 5 seconds",
        "Pour into a tall pilsner",
        "Garnish with a gardenia float and mint",
    ],
    ingredients=[
        ing("light rum", 2, "oz"),
        ing("brandy", 1, "oz"),
        ing("fresh orange juice", 2, "oz", "produce"),
        ing("fresh lemon juice", 1.5, "oz", "produce"),
        ing("orgeat", 0.5, "oz", "pantry"),
    ],
    glass="pilsner", method="blended", garnish="gardenia and mint", abv=14,
))

RECIPES.append(cocktail(
    "Trader Vic's Grog",
    year=None, region="USA", creator="Trader Vic",
    story="Trader Vic's Grog — aged rum, falernum, lime, grapefruit, honey — was the simpler Vic answer to Don the Beachcomber's Navy Grog. Often served over a cone of ice.",
    desc="Vic's aged-rum grog with falernum and citrus.",
    instructions=[
        "Shake all with ice",
        "Strain over crushed ice in a double rocks glass",
        "Garnish with mint",
    ],
    ingredients=[
        ing("aged rum", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("fresh grapefruit juice", 0.5, "oz", "produce"),
        ing("falernum", 0.5, "oz"),
        ing("honey syrup", 0.25, "oz", "pantry"),
    ],
    glass="double rocks", garnish="mint sprig", abv=22,
))

RECIPES.append(cocktail(
    "Suffering Bastard (Vic Variation)",
    year=None, region="USA",
    story="Trader Vic adopted Joe Scialom's Suffering Bastard for the American tiki canon, occasionally substituting bourbon for cognac.",
    desc="Vic's bourbon variation on the Suffering Bastard.",
    instructions=[
        "Build all in a Collins glass over ice",
        "Top with ginger beer",
        "Garnish with mint and orange",
    ],
    ingredients=[
        ing("bourbon", 1, "oz"),
        ing("gin", 1, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("Angostura bitters", 2, "dash"),
        ing("ginger beer", 4, "oz"),
    ],
    glass="collins", method="built", garnish="mint sprig and orange wheel", abv=14,
))

RECIPES.append(cocktail(
    "Whisky Smash (Modern Spec)",
    year=None, region="USA",
    story="The 19th-century smash, restored by Dale DeGroff and standardized at modern craft bars — bourbon, lemon, mint and simple over crushed ice.",
    desc="DeGroff-standardized bourbon smash.",
    instructions=[
        "Muddle mint and lemon wedges with simple in shaker",
        "Add bourbon and ice; shake hard",
        "Strain over crushed ice in a rocks glass",
        "Garnish with a mint sprig",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("simple syrup", 0.75, "oz", "pantry"),
        ("mint"),
        ing("mint leaves", 8, "each", "produce"),
        ing("lemon wedges", 4, "each", "produce"),
    ],
    glass="rocks", garnish="mint sprig", abv=22,
))

RECIPES.append(cocktail(
    "Mint Julep (Modern Spec)",
    year=None, region="Kentucky, USA",
    story="The 19th-century Mint Julep, restored to canon at modern craft bars — bourbon, sugar, mint and crushed ice in a chilled silver cup.",
    desc="Bourbon, sugar, mint, crushed ice in silver cup.",
    instructions=[
        "Muddle mint and simple syrup gently in a julep cup",
        "Add bourbon and pack with crushed ice",
        "Stir with bar spoon until cup frosts",
        "Top with more crushed ice and a generous mint bouquet",
    ],
    ingredients=[
        ing("bourbon", 2.5, "oz"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("mint leaves", 10, "each", "produce"),
    ],
    glass="julep cup", method="built", garnish="mint bouquet", abv=24,
))

RECIPES.append(cocktail(
    "Old Fashioned (Modern Spec)",
    year=None, region="USA",
    story="The 19th-century Old Fashioned was restored to its proper minimalist form by 2000s craft bartenders — whiskey, sugar, bitters, large cube. No fruit-muddling.",
    desc="Modern craft Old Fashioned with no fruit muddling.",
    instructions=[
        "Stir whiskey, syrup and bitters with ice",
        "Strain over a large cube in a rocks glass",
        "Express orange peel and discard",
    ],
    ingredients=[
        ing("bourbon or rye", 2, "oz"),
        ing("Demerara syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=33,
))

RECIPES.append(cocktail(
    "Manhattan (Modern Spec)",
    year=None, region="New York, USA",
    story="The 1880s Manhattan returned to glory in the 2000s craft revival — rye, sweet vermouth and Angostura, served up in a chilled coupe.",
    desc="Modern craft Manhattan: rye, sweet vermouth, Angostura.",
    instructions=[
        "Stir all ingredients with ice",
        "Strain into a chilled coupe",
        "Garnish with a brandied cherry",
    ],
    ingredients=[
        ing("rye whiskey", 2, "oz"),
        ing("sweet vermouth", 1, "oz"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="coupe", method="stirred", garnish="brandied cherry", abv=27,
))

RECIPES.append(cocktail(
    "Negroni (Modern Spec)",
    year=None, region="Florence, Italy",
    story="The 1919 Florentine classic returned as a global craft signature in the 2010s — equal parts gin, Campari, sweet vermouth on a large cube with an orange peel.",
    desc="Equal-parts gin, Campari, sweet vermouth on a large cube.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with an orange peel",
    ],
    ingredients=[
        ing("London dry gin", 1, "oz"),
        ing("Campari", 1, "oz"),
        ing("sweet vermouth", 1, "oz"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=24,
))

RECIPES.append(cocktail(
    "Sidecar (Modern Spec)",
    year=None, region="Paris, France",
    story="The 1920s Sidecar — cognac, Cointreau, lemon — was restored to proper proportions by craft bartenders, with optional sugar rim and aged cognac.",
    desc="Modern craft Sidecar with proper proportions.",
    instructions=[
        "Shake all with ice",
        "Double strain into a sugar-rimmed coupe",
        "Garnish with a lemon peel",
    ],
    ingredients=[
        ing("cognac", 2, "oz"),
        ing("Cointreau", 0.75, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
    ],
    glass="coupe", garnish="lemon peel and sugar rim", abv=24,
))

RECIPES.append(cocktail(
    "Daiquiri (Modern Spec)",
    year=None, region="Cuba",
    story="The 1898 Daiquiri returned to canon at craft bars as the gold-standard test — only three ingredients (rum, lime, simple) but the proportions and shake separate craft from the rest.",
    desc="Three-ingredient white rum, lime, simple Daiquiri.",
    instructions=[
        "Shake hard with ice for 12 seconds",
        "Double strain into a chilled coupe",
    ],
    ingredients=[
        ing("white rum", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
    ],
    glass="coupe", garnish=None, abv=22,
))

RECIPES.append(cocktail(
    "Margarita (Modern Spec)",
    year=None, region="Mexico",
    story="The 1940s Margarita — tequila, Cointreau, lime — restored to canonical 2:1:1 proportions at craft bars, with optional salt rim.",
    desc="Modern proper-spec Margarita with Cointreau.",
    instructions=[
        "Shake all with ice",
        "Strain over fresh ice in a salt-rimmed rocks glass",
        "Garnish with a lime wheel",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("Cointreau", 1, "oz"),
        ing("fresh lime juice", 1, "oz", "produce"),
    ],
    glass="rocks", garnish="lime wheel and salt rim", abv=24,
))

# ============================================================================
# COFFEE / DESSERT / CREAM MODERN
# ============================================================================

RECIPES.append(cocktail(
    "Bushwacker",
    year=None, region="Caribbean/USA",
    story="A Caribbean frozen chocolate-coconut cocktail from the late 1970s — rum, Kahlúa, crème de cacao, coconut cream, blended with ice. A Florida and Gulf Coast staple.",
    desc="Frozen chocolate-coconut tiki dessert.",
    instructions=[
        "Blend all ingredients with crushed ice until smooth",
        "Pour into a hurricane glass",
        "Garnish with shaved chocolate",
    ],
    ingredients=[
        ing("dark rum", 1.5, "oz"),
        ing("Kahlúa", 0.5, "oz"),
        ing("dark crème de cacao", 0.5, "oz"),
        ("coconut cream"),
        ing("coconut cream", 1, "oz", "pantry"),
        ing("heavy cream", 1, "oz", "dairy"),
    ],
    glass="hurricane", method="blended", garnish="shaved chocolate", abv=12,
))

RECIPES.append(cocktail(
    "Frozen Painkiller",
    year=None, region="USA",
    story="A modern frozen variation on Daphne Henderson's Painkiller — blended for hot summer service. The Soggy Dollar Bar's drink translated for the blender age.",
    desc="Frozen Pusser's rum painkiller.",
    instructions=[
        "Blend all with ice until smooth",
        "Pour into a hurricane glass",
        "Dust with nutmeg",
    ],
    ingredients=[
        ing("Pusser's rum", 2, "oz"),
        ing("pineapple juice", 4, "oz", "produce"),
        ing("fresh orange juice", 1, "oz", "produce"),
        ing("cream of coconut", 1, "oz", "pantry"),
    ],
    glass="hurricane", method="blended", garnish="grated nutmeg", abv=10,
))

RECIPES.append(cocktail(
    "Coconut Margarita",
    year=None, region="USA",
    story="A modern Margarita variant adding coconut cream — became a craft beach-bar standard in the 2010s.",
    desc="Margarita with coconut cream.",
    instructions=[
        "Shake all with ice",
        "Strain over fresh ice in a coconut-rimmed rocks glass",
        "Garnish with a lime wheel",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ("Cointreau"),
        ing("Cointreau", 0.5, "oz"),
        ing("fresh lime juice", 1, "oz", "produce"),
        ing("coconut cream", 0.5, "oz", "pantry"),
    ],
    glass="rocks", garnish="toasted coconut rim", abv=22,
))

# ============================================================================
# AGAVE DEEP CUTS
# ============================================================================

RECIPES.append(cocktail(
    "El Pepino",
    year=2010, region="USA",
    story="A modern craft cucumber tequila cooler — blanco tequila, fresh cucumber, lime, agave, and a salt rim. Standard at modern Mexican-leaning bars.",
    desc="Cucumber-tequila craft cooler.",
    instructions=[
        "Muddle cucumber in shaker",
        "Add tequila, lime, agave, ice; shake",
        "Double strain over fresh ice in a salt-rimmed rocks glass",
        "Garnish with cucumber wheel",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("agave syrup", 0.5, "oz", "pantry"),
        ing("cucumber slices", 4, "each", "produce"),
    ],
    glass="rocks", garnish="cucumber wheel and salt rim", abv=22,
))

RECIPES.append(cocktail(
    "Nopalita",
    year=None, region="Mexico City, Mexico",
    story="A Mexican craft Margarita variation made with prickly-pear (nopal) syrup. Vivid magenta and floral — standard at modern Mexico City craft bars.",
    desc="Prickly-pear Margarita variation.",
    instructions=[
        "Shake all with ice",
        "Strain over fresh ice in a salted rocks glass",
        "Garnish with a lime wheel",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("fresh lime juice", 1, "oz", "produce"),
        ing("prickly pear syrup", 0.75, "oz", "pantry"),
    ],
    glass="rocks", garnish="lime wheel and salt rim", abv=22,
))

RECIPES.append(cocktail(
    "Mezcal Mule",
    year=None, region="USA",
    story="A 2010s craft Moscow Mule swap with mezcal — see also Phil Ward's separate Mezcal Mule entry. Standard at agave bars.",
    desc="Mezcal mule with lime and ginger beer.",
    instructions=[
        "Build mezcal and lime in a copper mug over ice",
        "Top with ginger beer",
        "Garnish with mint",
    ],
    ingredients=[
        ing("mezcal", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("ginger beer", 4, "oz"),
    ],
    glass="copper mug", method="built", garnish="mint sprig", abv=11,
))

RECIPES.append(cocktail(
    "Tequila Old Fashioned",
    year=None, region="USA",
    story="A modern craft Old Fashioned built on añejo tequila — became a standard variation as agave spirits gained respect in the 2010s.",
    desc="Añejo tequila Old Fashioned.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Express orange peel",
    ],
    ingredients=[
        ing("añejo tequila", 2, "oz"),
        ing("agave syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
        ing("orange bitters", 1, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=32,
))

RECIPES.append(cocktail(
    "Mezcal Old Fashioned",
    year=None, region="USA",
    story="The smoky cousin to the Tequila Old Fashioned — pure mezcal as the base spirit with agave, bitters and an orange peel.",
    desc="Mezcal Old Fashioned with smoky depth.",
    instructions=[
        "Stir mezcal, agave and bitters with ice",
        "Strain over a large cube in a rocks glass",
        "Express orange peel",
    ],
    ingredients=[
        ing("mezcal", 2, "oz"),
        ing("agave syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=32,
))

RECIPES.append(cocktail(
    "Cacao Old Fashioned",
    year=None, region="USA",
    story="A modern craft Old Fashioned variant with cacao nib-infused bourbon and chocolate bitters. Standard at chocolate-pairing menus.",
    desc="Cacao-infused bourbon Old Fashioned.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with a coffee bean and orange peel",
    ],
    ingredients=[
        ing("cacao-nib-infused bourbon", 2, "oz"),
        ing("Demerara syrup", 0.25, "oz", "pantry"),
        ing("chocolate bitters", 2, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel and coffee bean", abv=32,
))

RECIPES.append(cocktail(
    "Smoked Old Fashioned",
    year=None, region="USA",
    story="The Smoked Old Fashioned — bourbon Old Fashioned poured over a glass smoked with hickory or cherry wood — became a 2010s craft theatrical signature.",
    desc="Bourbon Old Fashioned in a wood-smoked glass.",
    instructions=[
        "Smoke a chilled rocks glass with hickory chips for 30 seconds; cap with coaster",
        "Stir bourbon, syrup and bitters with ice",
        "Uncap glass and strain over a large cube",
        "Express orange peel",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("Demerara syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel and smoke", abv=32,
))

RECIPES.append(cocktail(
    "Maple Old Fashioned",
    year=None, region="USA",
    story="A New England craft variant on the Old Fashioned using grade-B maple syrup. Standard at autumn craft cocktail menus.",
    desc="Bourbon Old Fashioned with maple syrup.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with orange peel",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("maple syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
        ing("orange bitters", 1, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=32,
))

RECIPES.append(cocktail(
    "Mole Old Fashioned",
    year=None, region="USA",
    story="A modern craft Old Fashioned with mole bitters — bourbon, agave, mole bitters — bringing chocolate-and-chile complexity to the format.",
    desc="Bourbon Old Fashioned with mole bitters.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Express orange peel",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("agave syrup", 0.25, "oz", "pantry"),
        ing("mole bitters", 3, "dash"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=32,
))

# ============================================================================
# MORE NAMED MODERN
# ============================================================================

RECIPES.append(cocktail(
    "Ti' Punch",
    year=None, region="Martinique",
    story="The traditional Martinique rhum agricole drink — rhum, lime peel, cane syrup — usually self-mixed by the drinker. Restored to global craft canon by 2010s rhum agricole exposure.",
    desc="Martinique rhum agricole, lime, cane syrup self-mix.",
    instructions=[
        "Squeeze lime peel into a small rocks glass",
        "Add cane syrup and rhum agricole",
        "Stir gently; serve at room temperature or with one cube",
    ],
    ingredients=[
        ing("rhum agricole blanc", 2, "oz"),
        ing("cane syrup", 0.25, "oz", "pantry"),
        ing("lime peel disc", 1, "each", "produce"),
    ],
    glass="rocks", method="built", garnish=None, abv=33,
))

RECIPES.append(cocktail(
    "El Presidente",
    year=None, region="Havana, Cuba",
    story="The 1920s Cuban presidential cocktail — white rum, dry vermouth, orange curaçao, grenadine — rediscovered by Cuban-revival craft bars in the 2000s.",
    desc="Cuban white rum, vermouth and curaçao classic.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
        "Garnish with an orange peel",
    ],
    ingredients=[
        ing("white rum", 2, "oz"),
        ing("dry vermouth", 0.75, "oz"),
        ing("orange curaçao", 0.25, "oz"),
        ing("grenadine", 1, "tsp", "pantry"),
    ],
    glass="coupe", method="stirred", garnish="orange peel", abv=22,
))

RECIPES.append(cocktail(
    "Jasmine (Modern)",
    year=None, region="USA",
    story="See also our Paul Harrington Jasmine — this entry covers the off-menu modern variant using mezcal in place of gin.",
    desc="Mezcal Margarita-template with Campari blush.",
    instructions=[
        "Shake all with ice",
        "Strain into a chilled coupe",
        "Garnish with a lemon peel",
    ],
    ingredients=[
        ing("mezcal", 1.5, "oz"),
        ing("Cointreau", 0.25, "oz"),
        ing("Campari", 0.25, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
    ],
    glass="coupe", garnish="lemon peel", abv=22,
))

RECIPES.append(cocktail(
    "Mezcal Negroni Sbagliato",
    year=None, region="USA",
    story="A modern craft hybrid combining the smoky Mezcal Negroni with the prosecco-finished Sbagliato format. A late-2010s aperitivo bar standard.",
    desc="Sbagliato made with mezcal and prosecco finish.",
    instructions=[
        "Build mezcal, Campari, sweet vermouth in a rocks glass over ice",
        "Top with prosecco",
        "Garnish with an orange peel",
    ],
    ingredients=[
        ing("mezcal", 0.75, "oz"),
        ing("Campari", 1, "oz"),
        ing("sweet vermouth", 1, "oz"),
        ing("prosecco", 1, "oz"),
    ],
    glass="rocks", method="built", garnish="orange peel", abv=15,
))

RECIPES.append(cocktail(
    "Carajillo",
    year=None, region="Spain/Mexico",
    story="The Carajillo — espresso with brandy or Licor 43 — has Spanish colonial roots but became a modern Mexican restaurant standard in the 2010s.",
    desc="Espresso with Licor 43 over ice.",
    instructions=[
        "Pour Licor 43 over ice in a rocks glass",
        "Pour fresh hot espresso slowly over the back of a spoon",
    ],
    ingredients=[
        ing("Licor 43", 1.5, "oz"),
        ing("fresh hot espresso", 1.5, "oz", "pantry"),
    ],
    glass="rocks", method="built", garnish=None, abv=14,
))

RECIPES.append(cocktail(
    "Penicillin (Reverse)",
    year=None, region="USA",
    story="A modern craft variant on Sam Ross's Penicillin reversing the float — Islay Scotch as base with a blended Scotch lift.",
    desc="Penicillin variant with Islay base.",
    instructions=[
        "Shake Islay, lemon, honey-ginger with ice",
        "Strain over a large cube in a rocks glass",
        "Float blended Scotch on top",
    ],
    ingredients=[
        ing("Islay Scotch", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("honey-ginger syrup", 0.75, "oz", "pantry"),
        ("blended Scotch"),
        ing("blended Scotch", 0.25, "oz"),
    ],
    glass="rocks", garnish=None, abv=24,
))

RECIPES.append(cocktail(
    "Whisky Sour (Modern Spec)",
    year=None, region="USA",
    story="The 19th-century Whisky Sour was restored by 2000s craft bartenders using fresh lemon, proper sugar ratio, and an egg white for the modern 'silver' style.",
    desc="Modern bourbon sour with egg white.",
    instructions=[
        "Dry-shake all without ice",
        "Add ice and shake again",
        "Double strain into a chilled coupe",
        "Garnish with Angostura dots and a brandied cherry",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.75, "oz", "pantry"),
        ing("egg white", 1, "each", "dairy"),
    ],
    glass="coupe", garnish="Angostura dots and brandied cherry", abv=20,
))

RECIPES.append(cocktail(
    "New York Sour (Modern)",
    year=None, region="USA",
    story="The New York Sour — whisky sour with a red wine float — was restored to modern craft bars in the 2010s as a sophisticated visual signature.",
    desc="Whisky sour with red wine float.",
    instructions=[
        "Shake bourbon, lemon, simple, egg white (dry then with ice)",
        "Strain into a chilled coupe",
        "Float a robust red wine over the back of a spoon",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("egg white", 1, "each", "dairy"),
        ing("red wine (Malbec or Shiraz)", 0.5, "oz"),
    ],
    glass="coupe", garnish=None, abv=20,
))

RECIPES.append(cocktail(
    "Pisco Sour (Modern Spec)",
    year=None, region="Lima, Peru",
    story="The 1920s Pisco Sour from Morris's Bar in Lima was restored by Peruvian and US craft bartenders with proper Peruvian pisco, fresh lime, and Angostura dots.",
    desc="Peruvian pisco sour with egg white and Angostura.",
    instructions=[
        "Dry-shake all without ice",
        "Add ice and shake again hard",
        "Double strain into a coupe",
        "Garnish with three Angostura dots",
    ],
    ingredients=[
        ing("Peruvian pisco", 2, "oz"),
        ing("fresh lime juice", 1, "oz", "produce"),
        ing("simple syrup", 0.75, "oz", "pantry"),
        ing("egg white", 1, "each", "dairy"),
    ],
    glass="coupe", garnish="Angostura dots", abv=20,
))

RECIPES.append(cocktail(
    "Chilcano",
    year=None, region="Lima, Peru",
    story="The Peruvian Chilcano — pisco, lime, ginger ale, Angostura — is a long-form sibling to the Pisco Sour, restored to modern bar menus alongside the Peruvian pisco renaissance.",
    desc="Peruvian pisco highball with ginger ale.",
    instructions=[
        "Build pisco and lime in a Collins glass over ice",
        "Top with ginger ale and dash bitters",
        "Garnish with a lime wheel",
    ],
    ingredients=[
        ing("Peruvian pisco", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("ginger ale", 4, "oz"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="collins", method="built", garnish="lime wheel", abv=11,
))

RECIPES.append(cocktail(
    "Caipirinha (Modern Spec)",
    year=None, region="Brazil",
    story="The Brazilian national cocktail — cachaça muddled with lime and sugar over ice — restored to global craft canon as quality cachaças appeared in the 2010s.",
    desc="Brazilian muddled cachaça-lime classic.",
    instructions=[
        "Cut lime into 8 wedges; muddle with sugar in a rocks glass",
        "Add cachaça and crushed ice",
        "Stir vigorously to integrate",
    ],
    ingredients=[
        ing("cachaça", 2, "oz"),
        ing("fresh lime", 1, "each", "produce"),
        ing("granulated sugar", 2, "tsp", "pantry"),
    ],
    glass="rocks", method="built", garnish=None, abv=22,
))

RECIPES.append(cocktail(
    "Caipiroska",
    year=None, region="Brazil",
    story="A Brazilian Caipirinha variation using vodka in place of cachaça — became standard at international bars seeking a milder Caipirinha equivalent.",
    desc="Vodka Caipirinha variant.",
    instructions=[
        "Muddle lime wedges with sugar in a rocks glass",
        "Add vodka and crushed ice",
        "Stir to combine",
    ],
    ingredients=[
        ing("vodka", 2, "oz"),
        ing("fresh lime", 1, "each", "produce"),
        ing("granulated sugar", 2, "tsp", "pantry"),
    ],
    glass="rocks", method="built", garnish=None, abv=22,
))

RECIPES.append(cocktail(
    "Batida",
    year=None, region="Brazil",
    story="A Brazilian blended cocktail of cachaça, fruit and sweetened condensed milk — many variations exist; this coconut version is the most popular.",
    desc="Brazilian blended cachaça with coconut milk.",
    instructions=[
        "Blend all with crushed ice until smooth",
        "Pour into a coupe or rocks glass",
    ],
    ingredients=[
        ing("cachaça", 2, "oz"),
        ing("coconut milk", 2, "oz", "pantry"),
        ing("sweetened condensed milk", 1, "oz", "pantry"),
        ("fresh lime juice"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
    ],
    glass="rocks", method="blended", garnish=None, abv=14,
))

# ============================================================================
# CRAFT REVIVALS — odd & beautiful
# ============================================================================

RECIPES.append(cocktail(
    "Velvet Falernum",
    year=None, region="Barbados",
    story="A Barbadian rum drink showcasing John D. Taylor's Velvet Falernum — rum, lime, falernum, soda. The base recipe for many tiki spec sheets.",
    desc="Rum, lime, falernum highball.",
    instructions=[
        "Build all but soda in a Collins glass over ice",
        "Top with soda; garnish with lime",
    ],
    ingredients=[
        ing("white rum", 1.5, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("velvet falernum", 0.5, "oz"),
        ing("club soda", 4, "oz"),
    ],
    glass="collins", method="built", garnish="lime wedge", abv=10,
))

RECIPES.append(cocktail(
    "Caribbean Milk Punch",
    year=None, region="Caribbean",
    story="A modern revival of the 19th-century clarified milk punch with rum, citrus, spice and sugar — clarified through milk for a clear, silky finished punch.",
    desc="Clarified milk punch with rum and tropical spice.",
    instructions=[
        "Combine rum, citrus, sugar and spice; let macerate 1 hour",
        "Pour into hot whole milk; stir and rest 1 hour to curdle",
        "Strain repeatedly through cheesecloth until clear",
        "Bottle and refrigerate",
    ],
    ingredients=[
        ing("aged rum", 8, "oz"),
        ing("fresh lemon juice", 4, "oz", "produce"),
        ing("simple syrup", 4, "oz", "pantry"),
        ing("whole milk", 8, "oz", "dairy"),
        ing("nutmeg", 1, "tsp", "pantry"),
    ],
    glass="rocks", method="punch", garnish="grated nutmeg", abv=15, prep=120,
))

RECIPES.append(cocktail(
    "Clarified Milk Punch (Modern)",
    year=None, region="USA",
    story="The clarified milk punch technique was rescued from history by Dave Arnold and Eben Freeman in the 2010s and rapidly adopted as a craft signature for batch service.",
    desc="Modern clarified milk punch base recipe.",
    instructions=[
        "Combine spirit, citrus and sugar",
        "Slowly add hot milk to curdle",
        "Strain repeatedly through cheesecloth",
        "Refrigerate finished punch",
    ],
    ingredients=[
        ing("bourbon", 8, "oz"),
        ing("fresh lemon juice", 4, "oz", "produce"),
        ing("simple syrup", 4, "oz", "pantry"),
        ing("whole milk", 8, "oz", "dairy"),
    ],
    glass="rocks", method="punch", garnish="grated nutmeg", abv=15, prep=120,
))

RECIPES.append(cocktail(
    "Brandy Crusta",
    year=None, region="New Orleans, USA",
    story="Joseph Santini's 1850s New Orleans Brandy Crusta — cognac, Cointreau, lemon, maraschino, sugar rim, lemon peel — was rediscovered as the precursor to the Sidecar by 2000s craft bartenders.",
    desc="Cognac sour with sugar rim and full lemon peel.",
    instructions=[
        "Sugar-rim a chilled coupe; line with a long lemon peel",
        "Shake cognac, Cointreau, lemon, maraschino with ice",
        "Strain into the prepared coupe",
    ],
    ingredients=[
        ing("cognac", 2, "oz"),
        ing("Cointreau", 0.5, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("maraschino liqueur", 0.25, "oz"),
    ],
    glass="coupe", garnish="sugar rim and lemon peel", abv=24,
))

RECIPES.append(cocktail(
    "Saturn (Modern Tiki)",
    year=None, region="USA",
    story="Modern tiki bartenders' rebuilt spec for the 1967 Saturn cocktail — gin tiki with passion fruit, falernum, orgeat. See also the Galsini original entry.",
    desc="Tiki gin sour with passion fruit and orgeat.",
    instructions=[
        "Blend all with crushed ice for 5 seconds",
        "Pour into a hurricane glass",
        "Garnish with a Saturn-ring lemon peel",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("passion fruit syrup", 0.5, "oz", "pantry"),
        ing("falernum", 0.25, "oz"),
        ing("orgeat", 0.25, "oz", "pantry"),
    ],
    glass="hurricane", method="blended", garnish="lemon-peel ring", abv=14,
))

RECIPES.append(cocktail(
    "French Pearl",
    year=None, region="USA", creator="Audrey Saunders",
    story="Audrey Saunders' French Pearl combined gin, mint, lime, simple and absinthe — a herbaceous Mojito variant with absinthe's anise lift.",
    desc="Gin Mojito with absinthe.",
    instructions=[
        "Muddle mint with simple in shaker",
        "Add gin, lime, absinthe; shake with ice",
        "Double strain into a coupe",
        "Garnish with a mint sprig",
    ],
    ingredients=[
        ing("London dry gin", 1.75, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.75, "oz", "pantry"),
        ing("absinthe", 0.25, "oz"),
        ing("mint leaves", 8, "each", "produce"),
    ],
    glass="coupe", garnish="mint sprig", abv=22,
))

RECIPES.append(cocktail(
    "Tradewinds",
    year=2010, region="USA", creator="Brian Miller", bar="Death & Co",
    story="Brian Miller's Tradewinds — three rums, lime, falernum and amaro — became one of Death & Co's most-imitated tiki originals.",
    desc="Three-rum tiki with falernum and amaro.",
    instructions=[
        "Shake all with crushed ice",
        "Pour unstrained into a tiki mug",
        "Garnish with mint",
    ],
    ingredients=[
        ing("aged Jamaican rum", 1, "oz"),
        ing("aged Demerara rum", 1, "oz"),
        ing("light Puerto Rican rum", 0.5, "oz"),
        ing("Amaro Meletti", 0.5, "oz"),
        ing("falernum", 0.5, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
    ],
    glass="tiki mug", garnish="mint sprig", abv=22,
))

RECIPES.append(cocktail(
    "Atta Boy",
    year=2008, region="New York, USA", creator="Sam Ross", bar="Attaboy",
    story="Sam Ross's namesake drink at Attaboy — gin, sweet vermouth, dry vermouth, grenadine — a sweet-and-dry Negroni-adjacent stir.",
    desc="Gin with sweet and dry vermouth and grenadine.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
        "Express lemon peel",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("sweet vermouth", 0.5, "oz"),
        ing("dry vermouth", 0.5, "oz"),
        ing("grenadine", 1, "tsp", "pantry"),
    ],
    glass="coupe", method="stirred", garnish="lemon peel", abv=27,
))

RECIPES.append(cocktail(
    "Whiskey Peach Smash",
    year=None, region="USA",
    story="A modern craft seasonal smash — bourbon, fresh peach, mint, lemon, simple — peak summer at craft cocktail bars.",
    desc="Summer bourbon smash with fresh peach.",
    instructions=[
        "Muddle peach slices with mint in shaker",
        "Add bourbon, lemon, simple, ice; shake",
        "Strain over crushed ice in a rocks glass",
        "Garnish with mint and peach slice",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("fresh peach slices", 4, "each", "produce"),
        ing("mint leaves", 6, "each", "produce"),
    ],
    glass="rocks", garnish="mint and peach", abv=22,
))

RECIPES.append(cocktail(
    "Strawberry Mule",
    year=None, region="USA",
    story="A modern craft Moscow Mule variation muddling fresh strawberries — became a summer farm-to-glass standard at the 2010s craft bars.",
    desc="Strawberry-muddled Moscow Mule.",
    instructions=[
        "Muddle strawberries in shaker",
        "Add vodka, lime; shake briefly with ice",
        "Strain into a copper mug over fresh ice",
        "Top with ginger beer",
    ],
    ingredients=[
        ing("vodka", 2, "oz"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ("strawberries"),
        ing("strawberries", 3, "each", "produce"),
        ing("ginger beer", 4, "oz"),
    ],
    glass="copper mug", garnish="strawberry slice", abv=11,
))

RECIPES.append(cocktail(
    "Watermelon Margarita",
    year=None, region="USA",
    story="A craft summer Margarita variation built on fresh watermelon juice — became a brunch staple at modern Mexican-leaning bars.",
    desc="Margarita with fresh watermelon juice.",
    instructions=[
        "Shake all with ice",
        "Strain over fresh ice in a salt-rimmed rocks glass",
        "Garnish with a watermelon wedge",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ing("Cointreau", 0.5, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("fresh watermelon juice", 1.5, "oz", "produce"),
    ],
    glass="rocks", garnish="watermelon wedge and salt rim", abv=20,
))

RECIPES.append(cocktail(
    "Blackberry Bramble",
    year=None, region="USA",
    story="A craft variation on Dick Bradsell's Bramble — adds muddled fresh blackberries to the gin-lemon-simple base. Standard at summer craft bar menus.",
    desc="Bradsell Bramble with muddled fresh blackberries.",
    instructions=[
        "Muddle blackberries in shaker",
        "Add gin, lemon, simple and ice; shake",
        "Strain over crushed ice in a rocks glass",
        "Drizzle crème de mure on top",
        "Garnish with blackberries",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lemon juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ("fresh blackberries"),
        ing("blackberries", 4, "each", "produce"),
        ing("crème de mure", 0.5, "oz"),
    ],
    glass="rocks", garnish="blackberries and lemon wheel", abv=22,
))

RECIPES.append(cocktail(
    "Beet Box",
    year=None, region="USA",
    story="A modern craft farm-to-glass cocktail using fresh beet juice — gin, beet, lime, simple, basil. The vivid magenta and earthy depth made it a 2010s craft signature.",
    desc="Gin sour with fresh beet juice and basil.",
    instructions=[
        "Muddle basil in shaker",
        "Add gin, beet juice, lime, simple, ice; shake",
        "Double strain into a coupe",
        "Garnish with a basil leaf",
    ],
    ingredients=[
        ing("London dry gin", 1.5, "oz"),
        ing("fresh beet juice", 1, "oz", "produce"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("simple syrup", 0.5, "oz", "pantry"),
        ing("basil leaves", 4, "each", "produce"),
    ],
    glass="coupe", garnish="basil leaf", abv=18,
))

RECIPES.append(cocktail(
    "Blood Orange Margarita",
    year=None, region="USA",
    story="A craft seasonal Margarita variation using fresh-pressed blood orange — peak winter at modern Mexican bars.",
    desc="Margarita with fresh blood orange juice.",
    instructions=[
        "Shake all with ice",
        "Strain over fresh ice in a salt-rimmed rocks glass",
        "Garnish with a blood orange wheel",
    ],
    ingredients=[
        ing("blanco tequila", 2, "oz"),
        ("Cointreau"),
        ing("Cointreau", 0.5, "oz"),
        ing("fresh blood orange juice", 1, "oz", "produce"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
    ],
    glass="rocks", garnish="blood orange wheel and salt rim", abv=22,
))

RECIPES.append(cocktail(
    "Bonal Negroni",
    year=None, region="USA",
    story="A 2010s craft Negroni variation swapping sweet vermouth for Bonal Gentiane-Quina — gentian-driven and floral.",
    desc="Negroni with Bonal Gentiane-Quina.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with orange peel",
    ],
    ingredients=[
        ing("London dry gin", 1, "oz"),
        ing("Campari", 1, "oz"),
        ing("Bonal Gentiane-Quina", 1, "oz"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=24,
))

RECIPES.append(cocktail(
    "Sherry Negroni",
    year=None, region="USA",
    story="A modern craft Negroni variant replacing gin with oloroso sherry — much lower ABV, nutty, oxidative.",
    desc="Low-ABV Negroni with oloroso sherry.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Express orange peel",
    ],
    ingredients=[
        ing("oloroso sherry", 1.5, "oz"),
        ing("Campari", 1, "oz"),
        ing("sweet vermouth", 1, "oz"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=18,
))

RECIPES.append(cocktail(
    "Improved Negroni",
    year=2010, region="New York, USA", creator="Phil Ward",
    story="Phil Ward's Improved Negroni adds a quarter ounce of mezcal to the classic Negroni base — a smoky bridge between the original and the Mezcal Negroni.",
    desc="Negroni with a mezcal lift.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Express orange peel",
    ],
    ingredients=[
        ing("London dry gin", 1, "oz"),
        ing("mezcal", 0.25, "oz"),
        ing("Campari", 1, "oz"),
        ing("sweet vermouth", 1, "oz"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=25,
))

RECIPES.append(cocktail(
    "Negroni Bianco",
    year=None, region="USA",
    story="A modern craft variation on the Negroni using Suze and bianco vermouth in place of Campari and sweet vermouth — pale, dry, gentian-driven.",
    desc="Pale gin Negroni with Suze and bianco vermouth.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with grapefruit peel",
    ],
    ingredients=[
        ing("London dry gin", 1, "oz"),
        ing("Suze", 1, "oz"),
        ing("bianco vermouth", 1, "oz"),
    ],
    glass="rocks", method="stirred", garnish="grapefruit peel", abv=24,
))

RECIPES.append(cocktail(
    "Old Pal (Modern Spec)",
    year=None, region="Paris, France",
    story="The 1922 MacElhone classic returned to canon as a craft Negroni-template alternative — equal-parts rye, dry vermouth, Campari.",
    desc="Equal-parts rye, dry vermouth, Campari.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
        "Express lemon peel",
    ],
    ingredients=[
        ing("rye whiskey", 1, "oz"),
        ing("dry vermouth", 1, "oz"),
        ing("Campari", 1, "oz"),
    ],
    glass="coupe", method="stirred", garnish="lemon peel", abv=24,
))

RECIPES.append(cocktail(
    "Spiced Pear Manhattan",
    year=None, region="USA",
    story="A modern craft autumn Manhattan with pear-infused rye and warm spice. Standard on seasonal menus.",
    desc="Pear-rye Manhattan with warm spice.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
        "Garnish with a pear slice",
    ],
    ingredients=[
        ing("pear-infused rye", 2, "oz"),
        ing("sweet vermouth", 1, "oz"),
        ing("cinnamon syrup", 1, "tsp", "pantry"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="coupe", method="stirred", garnish="pear slice", abv=27,
))

RECIPES.append(cocktail(
    "Apple Cider Old Fashioned",
    year=None, region="USA",
    story="A modern craft autumn Old Fashioned using fresh apple cider and warm spice. Peak fall.",
    desc="Bourbon Old Fashioned with apple cider.",
    instructions=[
        "Stir bourbon, cider, syrup and bitters with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with an apple slice and cinnamon stick",
    ],
    ingredients=[
        ing("bourbon", 1.5, "oz"),
        ing("fresh apple cider", 1, "oz", "produce"),
        ing("maple syrup", 0.25, "oz", "pantry"),
        ing("Angostura bitters", 2, "dash"),
    ],
    glass="rocks", method="stirred", garnish="apple slice and cinnamon", abv=22,
))

RECIPES.append(cocktail(
    "Cranberry Bourbon Sour",
    year=None, region="USA",
    story="A modern Thanksgiving-table craft sour built on bourbon, cranberry shrub, lemon and rosemary. Holiday menu standard.",
    desc="Bourbon sour with cranberry shrub and rosemary.",
    instructions=[
        "Shake all with ice",
        "Double strain into a coupe",
        "Garnish with a rosemary sprig",
    ],
    ingredients=[
        ing("bourbon", 2, "oz"),
        ("cranberry shrub"),
        ing("cranberry shrub", 0.75, "oz", "pantry"),
        ing("fresh lemon juice", 0.5, "oz", "produce"),
        ing("simple syrup", 0.25, "oz", "pantry"),
    ],
    glass="coupe", garnish="rosemary sprig", abv=20,
))

RECIPES.append(cocktail(
    "Fall Negroni",
    year=None, region="USA",
    story="A modern craft autumn Negroni variant using rye and Amaro Averna. Hearty, dark, bittersweet.",
    desc="Rye Negroni with Averna.",
    instructions=[
        "Stir all with ice",
        "Strain over a large cube in a rocks glass",
        "Garnish with orange peel",
    ],
    ingredients=[
        ing("rye whiskey", 1, "oz"),
        ing("Campari", 1, "oz"),
        ing("Amaro Averna", 1, "oz"),
    ],
    glass="rocks", method="stirred", garnish="orange peel", abv=27,
))

RECIPES.append(cocktail(
    "Gin Punch (Modern)",
    year=None, region="UK",
    story="The 19th-century British gin punch — gin, citrus, sugar, tea — returned to craft bar punch service in the 2010s as the punch revival took hold.",
    desc="Modern gin punch with tea and citrus.",
    instructions=[
        "Combine gin, lemon oleo-saccharum, juice, tea in a punch bowl",
        "Add a large block of ice",
        "Stir and ladle into cups",
    ],
    ingredients=[
        ing("London dry gin", 16, "oz"),
        ing("fresh lemon juice", 4, "oz", "produce"),
        ing("strong brewed black tea", 8, "oz", "pantry"),
        ing("simple syrup", 4, "oz", "pantry"),
    ],
    glass="punch cup", method="punch", garnish="lemon wheels and nutmeg", abv=15, prep=30,
))

RECIPES.append(cocktail(
    "Champagne Punch (Modern)",
    year=None, region="USA",
    story="A modern craft revival of the 19th-century champagne punch — brandy, citrus, sugar, champagne over a single block of ice.",
    desc="Brandy and champagne punch.",
    instructions=[
        "Combine brandy, lemon, simple and citrus slices in a punch bowl",
        "Add ice block",
        "Top with champagne just before service",
    ],
    ingredients=[
        ing("cognac", 8, "oz"),
        ing("fresh lemon juice", 3, "oz", "produce"),
        ing("simple syrup", 3, "oz", "pantry"),
        ing("champagne", 24, "oz"),
    ],
    glass="punch cup", method="punch", garnish="lemon wheels", abv=14, prep=20,
))

RECIPES.append(cocktail(
    "Spritz Sbagliato",
    year=None, region="Italy",
    story="A modern craft variant of the Sbagliato lengthened with soda and served in a wine glass. Standard at modern Italian aperitivo bars.",
    desc="Sbagliato lengthened with soda.",
    instructions=[
        "Build Campari and sweet vermouth in a wine glass over ice",
        "Top with prosecco and soda",
        "Garnish with orange wheel",
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
    "Italian Greyhound",
    year=None, region="USA",
    story="A modern craft Greyhound variation adding Aperol — vodka, grapefruit, Aperol over ice.",
    desc="Greyhound with Aperol modifier.",
    instructions=[
        "Build vodka, Aperol and grapefruit in a Collins glass over ice",
        "Stir to combine",
        "Garnish with a grapefruit wedge",
    ],
    ingredients=[
        ing("vodka", 1.5, "oz"),
        ing("Aperol", 0.75, "oz"),
        ing("fresh grapefruit juice", 4, "oz", "produce"),
    ],
    glass="collins", method="built", garnish="grapefruit wedge", abv=11,
))

RECIPES.append(cocktail(
    "Chartreuse Swizzle",
    year=2003, region="San Francisco, USA", creator="Marcovaldo Dionysos",
    story="Marco Dionysos created the Chartreuse Swizzle at Enrico's in San Francisco — Green Chartreuse, pineapple, lime, falernum, swizzled over crushed ice. A modern San Francisco original that put Chartreuse on craft menus.",
    desc="Green Chartreuse swizzle with pineapple and falernum.",
    instructions=[
        "Build all in a Collins glass with crushed ice",
        "Swizzle until heavily frosted",
        "Top with more crushed ice",
        "Garnish with mint",
    ],
    ingredients=[
        ing("Green Chartreuse", 1.25, "oz"),
        ing("pineapple juice", 1, "oz", "produce"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
        ing("falernum", 0.5, "oz"),
    ],
    glass="collins", method="swizzled", garnish="mint sprig", abv=22,
))

RECIPES.append(cocktail(
    "Bywater",
    year=2008, region="New York, USA", creator="Chris Hannah",
    story="Chris Hannah's Bywater at French 75 in New Orleans — rum, Falernum, Chartreuse — became a New Orleans craft signature drink.",
    desc="Aged rum, Chartreuse and Velvet Falernum stir.",
    instructions=[
        "Stir all with ice",
        "Strain into a chilled coupe",
        "Express lime peel",
    ],
    ingredients=[
        ing("aged Jamaican rum", 1.5, "oz"),
        ing("sweet vermouth", 0.5, "oz"),
        ing("Green Chartreuse", 0.5, "oz"),
        ing("Velvet Falernum", 0.5, "oz"),
    ],
    glass="coupe", method="stirred", garnish="lime peel", abv=24,
))

RECIPES.append(cocktail(
    "Mr. Howell",
    year=2013, region="USA", creator="Don Lee",
    story="Don Lee's Mr. Howell at Existing Conditions blends gin with curry leaf, lime and palm sugar for a savory tropical sour.",
    desc="Gin sour with curry leaf and palm sugar.",
    instructions=[
        "Muddle curry leaves in shaker",
        "Add gin, lime, palm sugar syrup; shake with ice",
        "Double strain into a chilled coupe",
        "Garnish with a curry leaf",
    ],
    ingredients=[
        ing("London dry gin", 2, "oz"),
        ing("fresh lime juice", 0.75, "oz", "produce"),
        ing("palm sugar syrup", 0.5, "oz", "pantry"),
        ("curry leaves"),
        ing("curry leaves", 6, "each", "produce"),
    ],
    glass="coupe", garnish="curry leaf", abv=22,
))

RECIPES.append(cocktail(
    "Vacation",
    year=2010, region="New York, USA", creator="Jane Danger",
    story="Jane Danger's Vacation — rum, pineapple, lime, falernum, Aperol — became a tiki-meets-aperitivo crossover signature in NYC.",
    desc="Rum tiki with pineapple, falernum and Aperol.",
    instructions=[
        "Shake all with crushed ice",
        "Pour into a tiki mug",
        "Garnish with a pineapple frond",
    ],
    ingredients=[
        ing("aged rum", 1.5, "oz"),
        ing("Aperol", 0.5, "oz"),
        ing("falernum", 0.5, "oz"),
        ing("pineapple juice", 1, "oz", "produce"),
        ing("fresh lime juice", 0.5, "oz", "produce"),
    ],
    glass="tiki mug", garnish="pineapple frond", abv=18,
))

RECIPES = [_clean(r) for r in RECIPES]

package app.pantrie.feature.parties

/**
 * Curate-a-Party menu data. Five menus hardcoded for the Kickstarter pre-launch
 * surface. Content sourced from .planning/kickstarter/04-SAMPLE-MENUS/0[1-5]-*.md
 * which are the brand-clean cocktail_history_nerd voice versions.
 *
 * Hero images live at res/drawable-nodpi/menu_<slug>_hero.jpg, generated via
 * fal.ai Flux Pro Ultra (see Desktop/AI_Auto_vid/generate_menu_heroes.py). If a
 * drawable is missing at runtime (image-gen race condition or pristine clone),
 * MenuDetailScreen renders a typographic title card fallback. No SVG cocktail
 * glasses substituting for photography. Acceptance bar from the build spec.
 */

data class PartyMenu(
  val id: String,           // slug, matches drawable name "menu_<id>_hero"
  val title: String,        // "The Bee's Knees Garden Party"
  val eraYear: Int,         // 1928
  val eraCity: String,      // "Long Island, USA"
  val eraSourceLine: String, // "Frank Meier, Ritz Paris, 1929"
  val guestCount: String,   // "8 to 10"
  val duration: String,     // "Three hours · 2pm to 5pm"
  val description: String,  // 2-3 sentence narrative intro (the "above-the-fold" lede)
  val drinks: List<MenuDrink>,
  val food: List<MenuPlate>,
  val timeline: List<TimelineStep>,
  val musicNote: String,
  val playlistName: String, // "Long Island, 1928"
  val shoppingList: List<ShoppingItem>,
  val shoppingTotal: String, // "Roughly $115 for the perishables. The gin, Pimm's, and Lillet are pantry investments..."
  val faithfulNote: String, // The "Faithful and modern-flexible" section
  val citation: String,     // The closing italic *sourced from...* footnote
  // ---- Mystery Night extensions (null on standard party menus) ----
  val isMystery: Boolean = false,
  val mysteryHook: String? = null,         // 2-3 sentence story setup. The reason guests are gathered.
  val cast: List<MysteryCharacter>? = null, // Character cards guests draw at the start
  val mysteryReveal: String? = null,        // The author's note for the host: who, why, when
  val hostPlaybook: String? = null,         // Beat-by-beat for when to surface clues
)

/** A single guest role in a Mystery Night. Drawn from a hat at the start of the evening. */
data class MysteryCharacter(
  val name: String,           // "The Critic's Editor"
  val role: String,           // "Theatre critic for The Standard. Sharp tongue, sharper enemies."
  val secret: String,         // What only this character knows. Visible to the host, given privately to the guest.
)

data class MenuDrink(
  val name: String,
  val sourceLine: String, // "Frank Meier, Ritz Paris, 1929"
  val historyNote: String, // The 2-3 sentence period lede before the recipe
  val recipe: String,     // The fragment-rhythm recipe in period prose
)

data class MenuPlate(
  val name: String,
  val description: String,
)

data class TimelineStep(
  val timeLabel: String, // "Three hours before guests arrive" / "Zero" / "The day before"
  val actions: List<String>, // Each action is its own line
)

enum class ShoppingAisle { Bar, Produce, Dairy, MeatFish, DryGoods, Misc }

data class ShoppingItem(
  val name: String,
  val qty: String, // "1 × 750ml" or "(8)" or "(small)"
  val aisle: ShoppingAisle,
)

// ---------------------------------------------------------------------------
//  Menu 01 — The Bee's Knees Garden Party
// ---------------------------------------------------------------------------

val MENU_BEES_KNEES = PartyMenu(
  id = "bees_knees",
  title = "The Bee's Knees Garden Party",
  eraYear = 1928,
  eraCity = "Long Island, USA",
  eraSourceLine = "Frank Meier, Ritz Paris, 1929",
  guestCount = "8 to 10",
  duration = "Three hours · 2pm to 5pm",
  description = "A Long Island estate, a summer afternoon, a table that does not move. The Bee's Knees was the workaround for the rough gin that came over from England in glass-lined teapots. Honey syrup hid the cut. The good stuff returned in 1933 and the recipe stayed.",
  drinks = listOf(
    MenuDrink(
      name = "Bee's Knees",
      sourceLine = "Frank Meier, Ritz Paris, 1929",
      historyNote = "Honey syrup was the workaround for the rough gin that came over from England in glass-lined teapots. Real gin returned in 1933 and the recipe never got revised. The honey works on the good stuff too.",
      recipe = "Two ounces London Dry. Three-quarter ounce honey syrup, one-to-one with hot water and cooled. Three-quarter ounce lemon. Shake hard. Strain into a coupe. No garnish.",
    ),
    MenuDrink(
      name = "Southside",
      sourceLine = "The 21 Club, New York, 1922",
      historyNote = "Opened in 1922 as a speakeasy on West 52nd Street. Mint and lime over gin, the bar's house build. The drink that signaled what kind of room you were in.",
      recipe = "Two ounces gin. Three-quarter ounce lime. Three-quarter ounce simple. Six mint leaves. Muddle. Shake. Strain into a coupe. One mint sprig on top.",
    ),
    MenuDrink(
      name = "Pimm's Cup Number One",
      sourceLine = "London 1840s, New York hotel bars by 1924",
      historyNote = "The lowest-proof drink on this menu and the one your guests will keep ordering.",
      recipe = "Two ounces Pimm's Number One. Three ounces ginger ale. Cucumber wheel, orange slice, mint sprig. Build in a highball over ice.",
    ),
    MenuDrink(
      name = "Clover Club",
      sourceLine = "Philadelphia, 1911",
      historyNote = "Pre-Prohibition holdover that stayed in print through the dry years because the recipe is in The Savoy Cocktail Book (1930). Egg white over raspberry over gin.",
      recipe = "One and a half ounces gin. Half ounce raspberry syrup. Half ounce lemon. One egg white. Dry shake without ice. Wet shake with. Strain into a coupe. Three raspberries.",
    ),
    MenuDrink(
      name = "Lillet Spritz",
      sourceLine = "Faithful 1920s aperitif",
      historyNote = "The drink for guests who arrive cautious and want something they can sip for an hour.",
      recipe = "Two ounces Lillet Blanc. Three ounces soda. Large ice. Orange twist.",
    ),
  ),
  food = listOf(
    MenuPlate("Deviled eggs", "Hellmann's mayonnaise reached supermarket shelves in 1922. The era's deviled egg is heavier on mustard, lighter on the modern paprika dust. Twelve halves for eight guests."),
    MenuPlate("Cucumber sandwiches", "White bread, salted butter, sliced cucumber, crusts off. The English drawing-room hit that crossed the Atlantic with the same social wave that built the Long Island estates."),
    MenuPlate("Smoked salmon on rye toasts", "Toast points cut into triangles. Cream cheese, smoked salmon, capers, dill. Plate cold."),
    MenuPlate("Strawberries with crème fraîche", "One bowl in the center of the table. Whole strawberries, hulled. A ramekin of crème fraîche dusted with brown sugar. Spoons."),
    MenuPlate("Salted radishes with butter", "The French side dish that 1920s American hostesses copied from European travels. French breakfast radishes halved, European cultured butter, flaky sea salt."),
    MenuPlate("Lemon shortbread", "One-bite squares. Bake the morning of. Leave on the counter. The dessert that does not fight the cocktails."),
  ),
  timeline = listOf(
    TimelineStep("Three hours before guests arrive", listOf(
      "Bake the shortbread. Cool.",
      "Make honey syrup. Cool.",
    )),
    TimelineStep("Ninety minutes before", listOf(
      "Mince mint for the Southsides. Keep cold under a damp paper towel.",
      "Slice cucumber thin.",
      "Toast and cut rye triangles.",
      "Whip egg yolks for the deviled eggs.",
    )),
    TimelineStep("Sixty minutes before", listOf(
      "Assemble deviled eggs. Cover and refrigerate.",
      "Halve and salt radishes.",
      "Slice strawberries.",
      "Build sandwiches. Refrigerate under a damp towel.",
    )),
    TimelineStep("Thirty minutes before", listOf(
      "Pour a Lillet for yourself.",
      "Cue music.",
      "Bring food platters out.",
    )),
    TimelineStep("Zero", listOf(
      "Guests arrive. First formal cocktail is the Bee's Knees, shaken to order. The rest follow.",
    )),
  ),
  musicNote = "Solo piano. Eubie Blake. Earl Hines's earliest stride records. Charleston Chasers when you want a touch more brass. 90 to 120 BPM, the same tempo the bartender keeps with the shaker.",
  playlistName = "Long Island, 1928",
  shoppingList = listOf(
    ShoppingItem("London Dry gin", "1 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Pimm's Number One", "1 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Lillet Blanc", "1 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Honey", "4oz", ShoppingAisle.DryGoods),
    ShoppingItem("Lemons", "8", ShoppingAisle.Produce),
    ShoppingItem("Limes", "4", ShoppingAisle.Produce),
    ShoppingItem("Mint", "1 bunch", ShoppingAisle.Produce),
    ShoppingItem("Raspberries", "1 pint", ShoppingAisle.Produce),
    ShoppingItem("Cucumber", "2", ShoppingAisle.Produce),
    ShoppingItem("Eggs", "13", ShoppingAisle.Dairy),
    ShoppingItem("White bread", "1 loaf", ShoppingAisle.DryGoods),
    ShoppingItem("Rye bread", "1 loaf", ShoppingAisle.DryGoods),
    ShoppingItem("Salted butter", "8oz", ShoppingAisle.Dairy),
    ShoppingItem("European cultured butter", "4oz", ShoppingAisle.Dairy),
    ShoppingItem("Cream cheese", "4oz", ShoppingAisle.Dairy),
    ShoppingItem("Smoked salmon", "8oz", ShoppingAisle.MeatFish),
    ShoppingItem("Capers", "2 tbsp", ShoppingAisle.DryGoods),
    ShoppingItem("Dill", "1 bunch", ShoppingAisle.Produce),
    ShoppingItem("Strawberries", "1 pint", ShoppingAisle.Produce),
    ShoppingItem("Crème fraîche", "8oz", ShoppingAisle.Dairy),
    ShoppingItem("Brown sugar", "", ShoppingAisle.DryGoods),
    ShoppingItem("French breakfast radishes", "1 bunch", ShoppingAisle.Produce),
    ShoppingItem("Flaky sea salt", "", ShoppingAisle.DryGoods),
    ShoppingItem("Ginger ale", "1L", ShoppingAisle.Bar),
    ShoppingItem("Flour, butter, sugar (shortbread)", "", ShoppingAisle.DryGoods),
  ),
  shoppingTotal = "Roughly $115 for the perishables. The gin, Pimm's, and Lillet are pantry investments that survive a few parties.",
  faithfulNote = "The Bee's Knees and Southside are non-negotiable. Both still mix in serious cocktail bars worldwide. The Clover Club substitutes pasteurized egg white from a carton for hosts whose guests are raw-egg-shy. The Lillet Spritz becomes a Verjus Spritz (verjus, soda, orange twist) for alcohol-free guests, drawn from the temperance-era mocktail set.",
  citation = "Sourced from Frank Meier (1929), the 21 Club archival menu (1922), Craddock's Savoy Cocktail Book (1930) for the Clover Club, and contemporary American Hotel Bar reports of the period.",
)

// ---------------------------------------------------------------------------
//  Menu 02 — Speakeasy Opening Night
// ---------------------------------------------------------------------------

val MENU_SPEAKEASY_OPENING = PartyMenu(
  id = "speakeasy_opening",
  title = "Speakeasy Opening Night",
  eraYear = 1924,
  eraCity = "Manhattan, USA",
  eraSourceLine = "Old Waldorf Bar Days, 1931",
  guestCount = "6",
  duration = "Four hours · 9pm to 1am",
  description = "Below-street. The bar that opens at nine. The food is anchored, salty, served cold or at room temperature, and built to outlast a four-hour night. Plates eaten with one hand while a coupe is in the other.",
  drinks = listOf(
    MenuDrink(
      name = "Hanky Panky",
      sourceLine = "Ada Coleman, American Bar at the Savoy, London, 1903",
      historyNote = "The first widely-credited cocktail attributed to a woman bartender. By 1923 the recipe had crossed the Atlantic and was running in every Manhattan speakeasy that kept Fernet on the back shelf.",
      recipe = "One and a half ounces London Dry. One and a half ounces sweet vermouth. Two dashes Fernet-Branca. Stir with ice. Strain into a coupe. Orange peel expressed and dropped.",
    ),
    MenuDrink(
      name = "Sidecar",
      sourceLine = "Harry MacElhone, Harry's New York Bar, Paris, 1921",
      historyNote = "Cognac was a hard import in the dry American market and stayed dear after Repeal. The sugar-rimmed coupe is optional and era-correct.",
      recipe = "One and a half ounces cognac. Three-quarter ounce Cointreau or orange curaçao. Three-quarter ounce lemon. Shake hard with ice. Strain into a coupe. Lemon peel.",
    ),
    MenuDrink(
      name = "Mary Pickford",
      sourceLine = "Fred Kaufman, Hotel Nacional, Havana, 1922",
      historyNote = "Named for the silent-film actress who wintered in Cuba during the dry years. Havana was three days by steamer from New York. The rum was legal there. The 1920s American passport solved the rest.",
      recipe = "One and a half ounces white rum. One and a half ounces pineapple juice. Six dashes grenadine. Two dashes maraschino. Shake hard with ice. Strain into a coupe. Maraschino cherry.",
    ),
    MenuDrink(
      name = "Aviation",
      sourceLine = "Hugo Ensslin, Hotel Wallick, New York, 1916",
      historyNote = "The drink with crème de violette in it. By 1924 the violette had become hard to find in Manhattan, and it stayed hard to find in America until 2007. If you have the bottle, the drink is itself.",
      recipe = "Two ounces gin. Half ounce maraschino. Quarter ounce crème de violette. Half ounce lemon. Shake with ice. Strain into a coupe. No garnish.",
    ),
    MenuDrink(
      name = "Manhattan",
      sourceLine = "The Manhattan Club, New York, 1874",
      historyNote = "The drink that named itself for the city. Originally one-to-one whiskey-to-vermouth. The two-to-one ratio came later, after taste shifted and the rye got better.",
      recipe = "Two ounces rye. One ounce sweet vermouth. Two dashes Angostura. Stir with ice. Strain into a coupe. Brandied cherry.",
    ),
  ),
  food = listOf(
    MenuPlate("Oyster crackers in smoked paprika butter", "Soft butter, smoked paprika, sea salt, lemon zest. Smear thinly on oyster crackers. Bake at 300°F for eight minutes."),
    MenuPlate("Country ham on warm biscuits", "Smithfield-style salt-cured ham, paper-thin. Split warm buttermilk biscuits. Grain mustard. Two per guest."),
    MenuPlate("Pickled deviled eggs", "Hard-boil. Peel. Steep the whites in beet brine for twenty-four hours. They turn ruby red and visually carry the speakeasy table."),
    MenuPlate("Roasted nuts", "Walnuts and almonds tossed in melted butter with chopped rosemary and flaky salt. Roast twelve minutes at 350°F. The bowl that disappears first."),
    MenuPlate("Cheese", "Aged cheddar, Stilton, Camembert. Quince paste. Walnut bread, sliced."),
    MenuPlate("Chocolate truffles", "Plain dark-chocolate ganache rolled in cocoa powder. Two per guest. Plate at the end of the night with espresso."),
  ),
  timeline = listOf(
    TimelineStep("The day before", listOf(
      "Brine the egg whites in beet brine. Cover. Refrigerate.",
      "Roll the chocolate truffles. Cocoa-dust. Refrigerate.",
    )),
    TimelineStep("Three hours before guests arrive", listOf(
      "Bake the biscuits. Cool covered on the counter.",
      "Slice the ham paper-thin or unwrap the bought slices.",
    )),
    TimelineStep("Ninety minutes before", listOf(
      "Roast the nuts. Cool.",
      "Mix the paprika butter. Smear the crackers. Bake.",
      "Assemble the cheese plate. Slice the walnut bread.",
    )),
    TimelineStep("Sixty minutes before", listOf(
      "Finish the deviled-egg filling. Stuff the whites. Cover and refrigerate.",
      "Set up the bar: ice, glasses, jigger, strainer, mixing glass, bar spoon. Pre-chill three coupes.",
    )),
    TimelineStep("Thirty minutes before", listOf(
      "Stir one Hanky Panky for yourself. Taste it. Adjust the room.",
      "Light the candles. Cue the music.",
    )),
    TimelineStep("Zero", listOf(
      "Three knocks at the door. The first round is Hanky Panky for the bold, Mary Pickford for the curious, Manhattan for the regulars.",
    )),
  ),
  musicNote = "Stride piano and early hot jazz. Fletcher Henderson's New York sides. The Coon-Sanders Original Nighthawks. Bessie Smith on Columbia. 90 to 110 BPM, slow enough to hold a conversation over.",
  playlistName = "Manhattan, 1924",
  shoppingList = listOf(
    ShoppingItem("London Dry gin", "1 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Rye whiskey", "1 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("White rum", "1 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Cognac", "1 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Sweet vermouth", "1 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Cointreau or orange curaçao", "small", ShoppingAisle.Bar),
    ShoppingItem("Maraschino liqueur", "small", ShoppingAisle.Bar),
    ShoppingItem("Crème de violette", "small (if findable)", ShoppingAisle.Bar),
    ShoppingItem("Fernet-Branca", "small", ShoppingAisle.Bar),
    ShoppingItem("Pineapple juice", "small bottle", ShoppingAisle.Bar),
    ShoppingItem("Grenadine", "small", ShoppingAisle.Bar),
    ShoppingItem("Angostura bitters", "", ShoppingAisle.Bar),
    ShoppingItem("Brandied cherries", "", ShoppingAisle.Misc),
    ShoppingItem("Lemons", "6", ShoppingAisle.Produce),
    ShoppingItem("Oranges", "4", ShoppingAisle.Produce),
    ShoppingItem("Beets", "3 medium (for brine)", ShoppingAisle.Produce),
    ShoppingItem("Fresh rosemary", "", ShoppingAisle.Produce),
    ShoppingItem("Oyster crackers", "1 box", ShoppingAisle.DryGoods),
    ShoppingItem("Unsalted butter", "8oz", ShoppingAisle.Dairy),
    ShoppingItem("Smoked paprika", "", ShoppingAisle.DryGoods),
    ShoppingItem("Sea salt", "", ShoppingAisle.DryGoods),
    ShoppingItem("Smithfield ham", "4oz", ShoppingAisle.MeatFish),
    ShoppingItem("Biscuits or biscuit ingredients", "12", ShoppingAisle.DryGoods),
    ShoppingItem("Grain mustard", "", ShoppingAisle.DryGoods),
    ShoppingItem("Eggs", "8", ShoppingAisle.Dairy),
    ShoppingItem("Walnuts", "4oz", ShoppingAisle.DryGoods),
    ShoppingItem("Almonds", "4oz", ShoppingAisle.DryGoods),
    ShoppingItem("Aged cheddar", "4oz", ShoppingAisle.Dairy),
    ShoppingItem("Stilton", "4oz", ShoppingAisle.Dairy),
    ShoppingItem("Camembert", "4oz", ShoppingAisle.Dairy),
    ShoppingItem("Quince paste", "", ShoppingAisle.DryGoods),
    ShoppingItem("Walnut bread", "", ShoppingAisle.DryGoods),
    ShoppingItem("Dark chocolate", "8oz", ShoppingAisle.DryGoods),
    ShoppingItem("Heavy cream", "4oz", ShoppingAisle.Dairy),
    ShoppingItem("Cocoa powder", "", ShoppingAisle.DryGoods),
  ),
  shoppingTotal = "Roughly $145 for the perishables. The spirits, vermouth, and bitters are the pantry investments that survive across nights.",
  faithfulNote = "The Aviation may omit the crème de violette and become the Aviation No. 2. The pickled deviled eggs simplify to standard deviled eggs when twenty-four hours of beet brine is not on the table. The Mary Pickford has a faithful Prohibition-era mocktail in the temperance set: the Florida Cooler, pineapple over grenadine over lime, finished with soda.",
  citation = "Sourced from Ada Coleman's Savoy bartending notes (1903), Harry MacElhone's ABC of Mixing Cocktails (1922), Hugo Ensslin's Recipes for Mixed Drinks (1916), and the Hotel Nacional Havana opening-bar register (1922).",
)

// ---------------------------------------------------------------------------
//  Menu 03 — The Roaring Rooftop
// ---------------------------------------------------------------------------

val MENU_ROARING_ROOFTOP = PartyMenu(
  id = "roaring_rooftop",
  title = "The Roaring Rooftop",
  eraYear = 1926,
  eraCity = "Manhattan, USA",
  eraSourceLine = "Harry MacElhone, Barflies and Cocktails, 1927",
  guestCount = "12",
  duration = "Three hours · 7pm to 10pm",
  description = "The hotel rooftop that operates \"for residents only\" and admits anyone in evening dress. A rooftop in August takes a different menu than a basement bar in February. Most plates are cold and small, built to be eaten standing. The wind is the third guest.",
  drinks = listOf(
    MenuDrink(
      name = "French 75",
      sourceLine = "Harry MacElhone, Harry's New York Bar, Paris, 1915",
      historyNote = "Named for the French 75-millimeter field gun. The kick is the same.",
      recipe = "One ounce gin. Half ounce lemon. Half ounce simple syrup. Three ounces champagne. Shake the gin, lemon, syrup with ice. Strain into a flute. Top with cold champagne. Long lemon peel.",
    ),
    MenuDrink(
      name = "White Lady",
      sourceLine = "Harry MacElhone, Harry's New York Bar, 1929",
      historyNote = "Earlier versions used crème de menthe. The 1929 build is the one that lived.",
      recipe = "One and a half ounces gin. Three-quarter ounce Cointreau. Three-quarter ounce lemon. One egg white. Dry shake. Wet shake with ice. Strain into a coupe.",
    ),
    MenuDrink(
      name = "Boulevardier",
      sourceLine = "Erskine Gwynne, Harry's New York Bar, 1927",
      historyNote = "Gwynne edited an expatriate Paris monthly called Boulevardier. The drink is a Negroni with bourbon in place of gin, and the bourbon makes it American.",
      recipe = "One and a half ounces bourbon. One ounce Campari. One ounce sweet vermouth. Stir with ice. Strain into a coupe. Orange peel.",
    ),
    MenuDrink(
      name = "Last Word",
      sourceLine = "Detroit Athletic Club, circa 1916",
      historyNote = "Forgotten for fifty years. Rediscovered in 2003 by Murray Stenson at the Zig Zag Café in Seattle, who pulled it out of Ted Saucier's 1951 Bottoms Up. The recipe is four equal parts, which is the kind of recipe a tired bartender writes down before service.",
      recipe = "Three-quarter ounce gin. Three-quarter ounce green Chartreuse. Three-quarter ounce maraschino. Three-quarter ounce lime. Shake with ice. Strain into a coupe.",
    ),
    MenuDrink(
      name = "Champagne Cocktail",
      sourceLine = "Standardized by Jerry Thomas, 1862",
      historyNote = "Pre-Prohibition holdover. The drink for guests who don't want to commit.",
      recipe = "One sugar cube saturated with Angostura. Drop it into a flute. Fill with cold champagne. Lemon peel.",
    ),
  ),
  food = listOf(
    MenuPlate("Gougères", "Cheese choux puffs. Aged Gruyère, black pepper, baked the morning of. Twelve per twelve guests, plus the inevitable second pass."),
    MenuPlate("Smoked trout on pumpernickel", "Pumpernickel rounds. Horseradish cream. Smoked trout. Dill. Lemon zest."),
    MenuPlate("Watermelon and feta on toothpicks", "An American hotel-bar invention. Watermelon cubed. Feta crumbled. Mint leaf. Toothpick. Era-correct to within a decade."),
    MenuPlate("Olives and almonds", "Marcona almonds with sea salt. Castelvetrano olives. Manzanilla olives. A small bowl of each. Refill twice across the three hours."),
    MenuPlate("Beef carpaccio crostini", "Thin-sliced raw eye-of-round. Capers. Shaved Parmesan. Lemon. Olive oil. On toasted baguette. Slice the beef the morning of, plate just before guests arrive."),
    MenuPlate("Macarons", "Store-bought is fine. The era did not have macarons in America yet; they hit American shores in the 1930s. The rooftop sensibility tolerates the slight anachronism for the visual."),
  ),
  timeline = listOf(
    TimelineStep("Three hours before guests arrive", listOf(
      "Bake the gougères. Cool covered.",
      "Toast the baguette slices for the carpaccio. Slice the trout.",
    )),
    TimelineStep("Two hours before", listOf(
      "Slice the carpaccio, or have the butcher do it. Plate. Cover and refrigerate.",
    )),
    TimelineStep("Ninety minutes before", listOf(
      "Cube the watermelon. Crumble the feta. Pluck the mint. Assemble the picks. Refrigerate.",
    )),
    TimelineStep("Sixty minutes before", listOf(
      "Chill the champagne, the gin, the glassware. Set the rooftop bar against the wind. Cut the lemon peels.",
    )),
    TimelineStep("Thirty minutes before", listOf(
      "Pour yourself a Champagne Cocktail. Walk the perimeter. Light the candles in their hurricanes against the wind.",
    )),
    TimelineStep("Zero", listOf(
      "Guests arrive in evening dress. The first French 75 is the photograph everyone takes.",
    )),
  ),
  musicNote = "Hot jazz with a romantic edge. Bix Beiderbecke and the Wolverines. The Original Memphis Five. Annette Hanshaw's vocals on the slow numbers. 100 to 130 BPM, fast enough to lift the room.",
  playlistName = "Manhattan Rooftop, 1926",
  shoppingList = listOf(
    ShoppingItem("London Dry gin", "1.5 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Bourbon", "1 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Sweet vermouth", "1 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Cointreau", "small", ShoppingAisle.Bar),
    ShoppingItem("Campari", "1 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Green Chartreuse", "small", ShoppingAisle.Bar),
    ShoppingItem("Maraschino liqueur", "small", ShoppingAisle.Bar),
    ShoppingItem("Angostura bitters", "", ShoppingAisle.Bar),
    ShoppingItem("Sugar cubes", "", ShoppingAisle.DryGoods),
    ShoppingItem("Brut champagne", "4 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Lemons", "10", ShoppingAisle.Produce),
    ShoppingItem("Limes", "4", ShoppingAisle.Produce),
    ShoppingItem("Oranges", "3", ShoppingAisle.Produce),
    ShoppingItem("Eggs", "4", ShoppingAisle.Dairy),
    ShoppingItem("Pumpernickel bread", "", ShoppingAisle.DryGoods),
    ShoppingItem("Prepared horseradish", "", ShoppingAisle.DryGoods),
    ShoppingItem("Crème fraîche", "", ShoppingAisle.Dairy),
    ShoppingItem("Smoked trout", "8oz", ShoppingAisle.MeatFish),
    ShoppingItem("Fresh dill", "", ShoppingAisle.Produce),
    ShoppingItem("Watermelon", "1 small", ShoppingAisle.Produce),
    ShoppingItem("Feta", "8oz", ShoppingAisle.Dairy),
    ShoppingItem("Fresh mint", "", ShoppingAisle.Produce),
    ShoppingItem("Marcona almonds", "8oz", ShoppingAisle.DryGoods),
    ShoppingItem("Castelvetrano olives", "", ShoppingAisle.DryGoods),
    ShoppingItem("Manzanilla olives", "", ShoppingAisle.DryGoods),
    ShoppingItem("Eye-of-round", "8oz", ShoppingAisle.MeatFish),
    ShoppingItem("Capers", "", ShoppingAisle.DryGoods),
    ShoppingItem("Parmigiano-Reggiano", "", ShoppingAisle.Dairy),
    ShoppingItem("Baguette", "", ShoppingAisle.DryGoods),
    ShoppingItem("Extra-virgin olive oil", "", ShoppingAisle.DryGoods),
    ShoppingItem("Macarons", "24, assorted", ShoppingAisle.DryGoods),
    ShoppingItem("Gougère ingredients", "flour/eggs/milk/butter/Gruyère", ShoppingAisle.DryGoods),
  ),
  shoppingTotal = "Roughly $245 for twelve guests, champagne included. The bourbon, Campari, vermouth, and Chartreuse are the pantry investments.",
  faithfulNote = "The Last Word is the drink with no easy non-alcoholic substitute. For alcohol-free guests, the temperance database offers the Cinderella, orange over lemon over pineapple over grenadine, finished with ginger ale in a flute. The beef carpaccio becomes a roasted-beet carpaccio for vegetarian hosts.",
  citation = "Sourced from Harry MacElhone's Barflies and Cocktails (1927), Ted Saucier's Bottoms Up (1951) for the Last Word's documentary trail, Jerry Thomas's 1862 Champagne Cocktail entry, and Vanity Fair hotel-bar reporting of the period.",
)

// ---------------------------------------------------------------------------
//  Menu 04 — A Gatsby Affair
// ---------------------------------------------------------------------------

val MENU_GATSBY_AFFAIR = PartyMenu(
  id = "gatsby_affair",
  title = "A Gatsby Affair",
  eraYear = 1925,
  eraCity = "West Egg, Long Island",
  eraSourceLine = "Fitzgerald, The Great Gatsby, 1925",
  guestCount = "16 to 20",
  duration = "Five hours · 8pm onward",
  description = "Black tie. The party that lasts until sunrise. The host has been seen exactly once. Not a meal, a five-hour grazing arc. Guests serve themselves and drift back. The ice on the caviar service has to be reset twice across the night.",
  drinks = listOf(
    MenuDrink(
      name = "Gin Rickey",
      sourceLine = "Joe Rickey, Shoomaker's Bar, Washington D.C., 1880s",
      historyNote = "The bourbon version came first. Gin took over by the 1900s. The drink Gatsby himself orders in chapter seven: \"we drank gin rickeys with the boys.\" The 1925 reader knew exactly what was in his hand.",
      recipe = "Two ounces gin. Half ounce lime. Soda water. Lime wedge. Build in a highball over ice.",
    ),
    MenuDrink(
      name = "Mint Julep",
      sourceLine = "Kentucky bourbon tradition, 1830s",
      historyNote = "Era-appropriate for the South-fascinated wealthy of West Egg, who collected Confederate cavalry sabers and quoted Walt Whitman without reading him.",
      recipe = "Twelve mint leaves. Half ounce simple syrup. Two and a half ounces bourbon. Muddle the mint with the syrup in a julep cup or rocks glass. Pack with crushed ice. Pour the bourbon over. Stir lightly. Top with more crushed ice. Mint bouquet.",
    ),
    MenuDrink(
      name = "Pink Lady",
      sourceLine = "Jack Dunn, New York, c. 1912",
      historyNote = "The drink that signaled what kind of party it was.",
      recipe = "Two ounces gin. Half ounce applejack. Half ounce grenadine. Half ounce lemon. One egg white. Dry shake. Wet shake with ice. Strain into a coupe.",
    ),
    MenuDrink(
      name = "Brandy Alexander",
      sourceLine = "Troy Alexander, Rector's, New York, 1922",
      historyNote = "Built for a wedding party celebrating Phoebe Snow, the fictional clean-clothes mascot of the Lackawanna Railroad. The recipe outlived the railroad.",
      recipe = "One ounce cognac. One ounce crème de cacao. One ounce heavy cream. Shake hard with ice. Strain into a coupe. Grated nutmeg.",
    ),
    MenuDrink(
      name = "Champagne, simply",
      sourceLine = "Veuve Clicquot or Pol Roger if budget allows",
      historyNote = "Iced. Open on the half-hour. The drink Gatsby's parties actually ran on.",
      recipe = "Pop the cork audibly. Pour into a flute. No garnish.",
    ),
  ),
  food = listOf(
    MenuPlate("Caviar service", "One ounce of American osetra or paddlefish caviar per four guests. Buckwheat blinis, warm. Crème fraîche. Minced shallot. Chopped chive. Lemon wedges. Mother-of-pearl spoons. The single most period-correct expense."),
    MenuPlate("Oysters on the half-shell", "Two per guest. Lemon. Mignonette. Horseradish. Tabasco. The American oyster bar of 1925 in miniature."),
    MenuPlate("Cold poached lobster medallions", "Remoulade alongside. Half a lobster tail per guest, pre-poached, chilled, sliced into rounds."),
    MenuPlate("Beef tenderloin canapés", "Rare-roasted tenderloin, sliced thin. Toast points. Horseradish cream. Watercress. Three per guest."),
    MenuPlate("Stuffed mushrooms", "White button mushrooms stuffed with crab and breadcrumbs, baked. The hot bite that keeps people from getting too drunk too fast."),
    MenuPlate("Chocolate-dipped strawberries", "Plated at midnight. Twenty-four for sixteen guests. The dessert that ends up in someone's coat pocket on the way out."),
  ),
  timeline = listOf(
    TimelineStep("The day before", listOf(
      "Order the oysters and caviar for next-day pickup.",
      "Pre-poach the lobster. Chill on a rack.",
      "Roast the beef tenderloin rare. Chill.",
    )),
    TimelineStep("Four hours before guests arrive", listOf(
      "Slice the lobster medallions. Make the remoulade.",
      "Slice the beef paper-thin. Cover and refrigerate everything.",
    )),
    TimelineStep("Three hours before", listOf(
      "Make the mignonette. Arrange for the oysters to be shucked at home.",
      "Par-bake the stuffed mushrooms.",
    )),
    TimelineStep("Two hours before", listOf(
      "Cut and toast the toast points. Warm the blinis. Assemble the beef canapés.",
      "Plate the caviar service on a bed of crushed ice in a chilled silver dish.",
    )),
    TimelineStep("Ninety minutes before", listOf(
      "Set up two bar stations. Sixteen guests on a five-hour party needs two bars.",
      "Iced champagne in standing buckets. Chill every coupe.",
    )),
    TimelineStep("Sixty minutes before", listOf(
      "Plate the chocolate-dipped strawberries on a silver tray. Cover. Refrigerate.",
      "Final check. Walk every room. Stand at the front door for a moment.",
    )),
    TimelineStep("Thirty minutes before", listOf(
      "Pour a Gin Rickey for yourself on the front porch.",
      "Reheat the stuffed mushrooms in a low oven.",
      "Bring up the music.",
    )),
    TimelineStep("Zero", listOf(
      "The first guests arrive. Open the first champagne audibly. The party becomes itself.",
    )),
  ),
  musicNote = "Hot jazz, dance jazz. Paul Whiteman's orchestra. The Original Dixieland Jass Band on Victor reissues. Florence Mills sides. 100 to 130 BPM. A live pianist if the budget allows.",
  playlistName = "Long Island Manor, 1925",
  shoppingList = listOf(
    ShoppingItem("London Dry gin", "3 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Bourbon", "2 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Cognac", "1 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Applejack", "small", ShoppingAisle.Bar),
    ShoppingItem("Crème de cacao", "small", ShoppingAisle.Bar),
    ShoppingItem("Grenadine", "small", ShoppingAisle.Bar),
    ShoppingItem("Soda water", "4 × 1L", ShoppingAisle.Bar),
    ShoppingItem("Lemons", "12", ShoppingAisle.Produce),
    ShoppingItem("Limes", "12", ShoppingAisle.Produce),
    ShoppingItem("Fresh mint", "3 bunches", ShoppingAisle.Produce),
    ShoppingItem("Eggs", "6", ShoppingAisle.Dairy),
    ShoppingItem("Heavy cream", "1 pint", ShoppingAisle.Dairy),
    ShoppingItem("Whole nutmeg", "", ShoppingAisle.DryGoods),
    ShoppingItem("Brut champagne", "8 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Caviar", "4 to 6 oz", ShoppingAisle.MeatFish),
    ShoppingItem("Buckwheat blinis", "24", ShoppingAisle.DryGoods),
    ShoppingItem("Crème fraîche", "", ShoppingAisle.Dairy),
    ShoppingItem("Shallots", "", ShoppingAisle.Produce),
    ShoppingItem("Chives", "", ShoppingAisle.Produce),
    ShoppingItem("Oysters", "32 to 40", ShoppingAisle.MeatFish),
    ShoppingItem("Tabasco", "", ShoppingAisle.DryGoods),
    ShoppingItem("Lobster tails", "8", ShoppingAisle.MeatFish),
    ShoppingItem("Beef tenderloin", "1.5 lbs", ShoppingAisle.MeatFish),
    ShoppingItem("Watercress", "", ShoppingAisle.Produce),
    ShoppingItem("Horseradish", "", ShoppingAisle.DryGoods),
    ShoppingItem("White button mushrooms", "24", ShoppingAisle.Produce),
    ShoppingItem("Crabmeat", "8oz", ShoppingAisle.MeatFish),
    ShoppingItem("Breadcrumbs", "", ShoppingAisle.DryGoods),
    ShoppingItem("Strawberries", "24 large", ShoppingAisle.Produce),
    ShoppingItem("Dark chocolate", "8oz", ShoppingAisle.DryGoods),
    ShoppingItem("Baguettes", "3 loaves", ShoppingAisle.DryGoods),
  ),
  shoppingTotal = "Roughly $625 for sixteen guests, excluding the spirits. The most expensive menu in the set. The era's parties were not cheap.",
  faithfulNote = "The caviar may scale to paddlefish without anyone of taste objecting. Oysters can be replaced with pickled shrimp for guests with shellfish reservations. Lobster medallions become shrimp cocktail for tighter budgets. The Brandy Alexander has a faithful non-alcoholic substitute in the temperance database, the Cocoa Cream Float, circa 1928.",
  citation = "Sourced from Fitzgerald's The Great Gatsby (1925) for the named drinks, Albert Stevens Crockett's Old Waldorf Bar Days (1931), Rector's New York menu archive for the Brandy Alexander origin, and Long Island estate hire-bar inventories of the 1924-1928 seasons.",
)

// ---------------------------------------------------------------------------
//  Menu 05 — Bootlegger's Den
// ---------------------------------------------------------------------------

val MENU_BOOTLEGGERS_DEN = PartyMenu(
  id = "bootleggers_den",
  title = "Bootlegger's Den",
  eraYear = 1929,
  eraCity = "Chicago, USA",
  eraSourceLine = "Jack Grohusko, Jack's Manual, 1908",
  guestCount = "8",
  duration = "Four hours · 10pm to 2am",
  description = "A converted basement. Wood plank floor. Two musicians in the corner. Eight chairs and a coal-oil lamp. The bar food is anchored, salted, and slow. The pork shoulder went into the oven at noon. Nothing here is plated.",
  drinks = listOf(
    MenuDrink(
      name = "Old Fashioned",
      sourceLine = "Pendennis Club, Louisville, 1880s",
      historyNote = "The drink that defined the word \"cocktail.\" Thomas had it in 1862 in essence. The Pendennis put a name on it.",
      recipe = "One sugar cube. Three dashes Angostura. One orange peel. One teaspoon water. Muddle in a rocks glass. Add two ounces rye. Add one large ice cube. Stir.",
    ),
    MenuDrink(
      name = "Brooklyn",
      sourceLine = "Jack Grohusko, Jack's Manual, 1908",
      historyNote = "A Manhattan that went east across the river and dried out. The Amer Picon original is the version on the page; Bigallet China-China is the closest substitute now that Amer Picon's American formulation is gone.",
      recipe = "Two ounces rye. One ounce dry vermouth. Quarter ounce maraschino. Quarter ounce Amer Picon or Bigallet China-China. Stir with ice. Strain into a coupe.",
    ),
    MenuDrink(
      name = "Vieux Carré",
      sourceLine = "Walter Bergeron, Hotel Monteleone, New Orleans, 1937",
      historyNote = "Technically outside Prohibition. Worth the inclusion. The drink's lineage runs through every basement bar in Chicago that took a New Orleans bartender on after Repeal.",
      recipe = "One ounce rye. One ounce cognac. One ounce sweet vermouth. Half teaspoon Bénédictine. Two dashes Peychaud's. Two dashes Angostura. Stir with ice. Strain over fresh ice in a rocks glass. Lemon peel.",
    ),
    MenuDrink(
      name = "Bramble",
      sourceLine = "Dick Bradsell, Fred's Club, London, 1984",
      historyNote = "Modern, and we say so. Based on the lost American basement-bar template of pre-Prohibition shrubs over crushed ice. The faithful filter in the app flags it.",
      recipe = "One and a half ounces gin. Three-quarter ounce lemon. Half ounce simple syrup. Shake with ice. Pour into a rocks glass over crushed ice. Drizzle a quarter ounce of crème de mûre over the top. Lemon wedge. One fresh blackberry.",
    ),
    MenuDrink(
      name = "Sazerac",
      sourceLine = "Antoine Peychaud, New Orleans, 1850s",
      historyNote = "Pre-Prohibition holdover, kept alive in Chicago basements by way of New Orleans expatriates. The cognac original. The rye substitution came after phylloxera killed European vineyards in the 1870s.",
      recipe = "Rinse a rocks glass with absinthe or Herbsaint. Discard the rinse. In a mixing glass: one sugar cube, three dashes Peychaud's, two ounces rye, ice. Stir. Strain into the rinsed glass. Lemon peel expressed and discarded.",
    ),
  ),
  food = listOf(
    MenuPlate("Country pâté on grilled bread", "Coarse pork pâté with pistachios and brandy. Served at room temperature on grilled country bread. The basement-bar standard."),
    MenuPlate("Pickled eggs", "Whole hard-boiled eggs in beet brine. Sliced into quarters. The bar food of every illicit American basement room in 1929."),
    MenuPlate("Cured sausage and cheese", "A board. Thinly sliced salami. Soppressata. Dry cheddar. Whole-grain mustard. Cornichons. Walnut bread."),
    MenuPlate("Slow-cooked pulled pork shoulder", "Six hours at 275°F, cooled overnight, pulled the morning of, reheated covered. Served warm on small biscuits with mustard. The hot food at midnight."),
    MenuPlate("Hot peanuts", "Boiled and salted, served warm in a paper-lined bowl. The bowl that keeps people drinking through the slow hour."),
    MenuPlate("Apple pie", "One pie. Eight slices. Plated with a small wedge of sharp Wisconsin cheddar on the side. The dessert that ends the night."),
  ),
  timeline = listOf(
    TimelineStep("The day before", listOf(
      "Brine the pickled eggs.",
      "Slow-cook the pork shoulder. Six hours at 275°F. Cool overnight.",
      "Bake the apple pie.",
      "Boil and salt the peanuts.",
    )),
    TimelineStep("Three hours before guests arrive", listOf(
      "Pull the pork. Reheat gently in the oven covered.",
      "Cut the country bread. Grill the slices.",
    )),
    TimelineStep("Two hours before", listOf(
      "Slice the salami and soppressata. Assemble the cheese board.",
      "Make the biscuits, or warm the pre-made ones.",
    )),
    TimelineStep("Ninety minutes before", listOf(
      "Spoon the pâté into a serving dish. Slice the pickled eggs into quarters. Plate.",
    )),
    TimelineStep("Sixty minutes before", listOf(
      "Set up the bar in the basement corner. One rocks glass per guest, one coupe per guest, polished.",
      "Ice. Crushed ice separately. Lemon peels cut. Sugar cubes within reach.",
    )),
    TimelineStep("Thirty minutes before", listOf(
      "Pour yourself an Old Fashioned. Light the lamps. Cue the musicians or the playlist.",
    )),
    TimelineStep("Zero", listOf(
      "The guests come down the back stair. The first round is Old Fashioneds and Brooklyns. The Sazerac is the second round, made theatrically, one at a time.",
    )),
  ),
  musicNote = "Chicago jazz and early blues. King Oliver's Creole Jazz Band. Louis Armstrong's Hot Five sides. Ma Rainey on Paramount. A live piano if a friend will play it. 80 to 110 BPM.",
  playlistName = "Chicago Basement, 1929",
  shoppingList = listOf(
    ShoppingItem("Rye whiskey", "2 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Cognac", "small", ShoppingAisle.Bar),
    ShoppingItem("London Dry gin", "small", ShoppingAisle.Bar),
    ShoppingItem("Sweet vermouth", "1 × 750ml", ShoppingAisle.Bar),
    ShoppingItem("Dry vermouth", "small", ShoppingAisle.Bar),
    ShoppingItem("Maraschino liqueur", "small", ShoppingAisle.Bar),
    ShoppingItem("Bénédictine", "small", ShoppingAisle.Bar),
    ShoppingItem("Amer Picon or Bigallet China-China", "small", ShoppingAisle.Bar),
    ShoppingItem("Crème de mûre", "small", ShoppingAisle.Bar),
    ShoppingItem("Absinthe or Herbsaint", "small", ShoppingAisle.Bar),
    ShoppingItem("Angostura bitters", "", ShoppingAisle.Bar),
    ShoppingItem("Peychaud's bitters", "", ShoppingAisle.Bar),
    ShoppingItem("Sugar cubes", "", ShoppingAisle.DryGoods),
    ShoppingItem("Lemons", "8", ShoppingAisle.Produce),
    ShoppingItem("Oranges", "4", ShoppingAisle.Produce),
    ShoppingItem("Blackberries", "1 pint", ShoppingAisle.Produce),
    ShoppingItem("Country pâté or pork shoulder", "8oz pâté", ShoppingAisle.MeatFish),
    ShoppingItem("Country bread", "1 loaf", ShoppingAisle.DryGoods),
    ShoppingItem("Eggs", "16", ShoppingAisle.Dairy),
    ShoppingItem("Beets", "4 medium", ShoppingAisle.Produce),
    ShoppingItem("Salami", "4oz", ShoppingAisle.MeatFish),
    ShoppingItem("Soppressata", "4oz", ShoppingAisle.MeatFish),
    ShoppingItem("Dry cheddar", "8oz", ShoppingAisle.Dairy),
    ShoppingItem("Walnut bread", "", ShoppingAisle.DryGoods),
    ShoppingItem("Whole-grain mustard", "", ShoppingAisle.DryGoods),
    ShoppingItem("Cornichons", "", ShoppingAisle.DryGoods),
    ShoppingItem("Pork shoulder", "3 lbs", ShoppingAisle.MeatFish),
    ShoppingItem("Biscuits", "16", ShoppingAisle.DryGoods),
    ShoppingItem("Yellow mustard", "", ShoppingAisle.DryGoods),
    ShoppingItem("Raw peanuts in shell", "1 lb", ShoppingAisle.DryGoods),
    ShoppingItem("Coarse salt", "", ShoppingAisle.DryGoods),
    ShoppingItem("Baking apples", "6", ShoppingAisle.Produce),
    ShoppingItem("Pie crust ingredients", "", ShoppingAisle.DryGoods),
    ShoppingItem("Sharp Wisconsin cheddar", "", ShoppingAisle.Dairy),
  ),
  shoppingTotal = "Roughly $135 for the perishables. The rye, vermouth, and bitters are the pantry investments.",
  faithfulNote = "The Sazerac is the drink that historically requires the most explanation. Hosts who want simplicity may swap it for a second round of Old Fashioneds. The Vieux Carré is technically 1937, four years past Repeal. Vegetarian guests get mushroom pâté and roasted-vegetable biscuits in place of the pork.",
  citation = "Sourced from Jack Grohusko's Jack's Manual (1908) for the Brooklyn, the Pendennis Club historical record for the Old Fashioned, Walter Bergeron's New Orleans bar register (1937) for the Vieux Carré, and Antoine Peychaud's pharmacy notebooks (1850s) for the Sazerac lineage.",
)

/** The five party-menu seeds (non-mystery), ordered for the grid landing. */
val PARTY_MENUS: List<PartyMenu> = listOf(
  MENU_BEES_KNEES,
  MENU_SPEAKEASY_OPENING,
  MENU_ROARING_ROOFTOP,
  MENU_GATSBY_AFFAIR,
  MENU_BOOTLEGGERS_DEN,
)

/** All ten ready-now menus (party + mystery). Used by findMenu + entitlement checks. */
val ALL_MENUS: List<PartyMenu> get() = PARTY_MENUS + MYSTERY_MENUS

fun findMenu(id: String): PartyMenu? = ALL_MENUS.firstOrNull { it.id == id }

// ---------------------------------------------------------------------------
//  Placeholder menus — the other 45, to round out the "fifty menus" promise.
//  Each ships post-launch on the ten-per-month cadence stated in the campaign.
//  Titles and eras are real period-anchored entries, not lorem-ipsum. They
//  exist in the grid as compact preview cards (no hero image, no detail page
//  wired) so the campaign screenshot can show all 50 slots accounted for.
// ---------------------------------------------------------------------------

enum class Cohort(val label: String, val isStretch: Boolean = false) {
  HOTEL_BARS("Hotel Bars"),
  HOLIDAY_NIGHTS("Holiday Nights"),
  WORLD_TOUR("Speakeasy World Tour", isStretch = true),
  OCCASIONS("Occasions"),
  MYSTERY_STRETCH("More Mystery Nights", isStretch = true),
}

data class PartyMenuPlaceholder(
  val title: String,
  val eraYear: Int,
  val eraCity: String,
  val cohort: Cohort,
  /** Approximate ship cohort label, e.g. "AUG 2026" — drives the grid badge. */
  val shipBadge: String,
)

val PLACEHOLDER_MENUS: List<PartyMenuPlaceholder> = listOf(
  // ---- Hotel Bars (10) · ships July-Aug 2026 ----
  PartyMenuPlaceholder("The Waldorf-Astoria Bar", 1893, "New York", Cohort.HOTEL_BARS, "JUL 2026"),
  PartyMenuPlaceholder("The Knickerbocker Hotel", 1906, "New York", Cohort.HOTEL_BARS, "JUL 2026"),
  PartyMenuPlaceholder("The Plaza, Oak Bar", 1907, "New York", Cohort.HOTEL_BARS, "JUL 2026"),
  PartyMenuPlaceholder("The Algonquin Round Table", 1919, "New York", Cohort.HOTEL_BARS, "JUL 2026"),
  PartyMenuPlaceholder("The Ritz-Carlton Bar", 1910, "New York", Cohort.HOTEL_BARS, "JUL 2026"),
  PartyMenuPlaceholder("The Pendennis Club", 1894, "Louisville", Cohort.HOTEL_BARS, "AUG 2026"),
  PartyMenuPlaceholder("The Brown Hotel", 1923, "Louisville", Cohort.HOTEL_BARS, "AUG 2026"),
  PartyMenuPlaceholder("The Drake Hotel Bar", 1920, "Chicago", Cohort.HOTEL_BARS, "AUG 2026"),
  PartyMenuPlaceholder("The Palmer House", 1873, "Chicago", Cohort.HOTEL_BARS, "AUG 2026"),
  PartyMenuPlaceholder("The Bellevue-Stratford", 1904, "Philadelphia", Cohort.HOTEL_BARS, "AUG 2026"),

  // ---- Holiday Nights (10) · ships Sept-Oct 2026 ----
  PartyMenuPlaceholder("New Year's Eve, 1925", 1925, "Anywhere", Cohort.HOLIDAY_NIGHTS, "SEP 2026"),
  PartyMenuPlaceholder("Christmas Eve at the Knickerbocker", 1920, "New York", Cohort.HOLIDAY_NIGHTS, "SEP 2026"),
  PartyMenuPlaceholder("Independence Day Garden Party", 1922, "Long Island", Cohort.HOLIDAY_NIGHTS, "SEP 2026"),
  PartyMenuPlaceholder("Halloween at the Algonquin", 1924, "New York", Cohort.HOLIDAY_NIGHTS, "SEP 2026"),
  PartyMenuPlaceholder("Thanksgiving in New England", 1923, "Boston", Cohort.HOLIDAY_NIGHTS, "SEP 2026"),
  PartyMenuPlaceholder("Valentine's at Delmonico's", 1899, "New York", Cohort.HOLIDAY_NIGHTS, "OCT 2026"),
  PartyMenuPlaceholder("Kentucky Derby Day", 1921, "Louisville", Cohort.HOLIDAY_NIGHTS, "OCT 2026"),
  PartyMenuPlaceholder("Bastille Day at Harry's Bar", 1922, "Paris", Cohort.HOLIDAY_NIGHTS, "OCT 2026"),
  PartyMenuPlaceholder("St. Patrick's Eve", 1923, "New York", Cohort.HOLIDAY_NIGHTS, "OCT 2026"),
  PartyMenuPlaceholder("Mardi Gras at Antoine's", 1920, "New Orleans", Cohort.HOLIDAY_NIGHTS, "OCT 2026"),

  // ---- The Speakeasy World Tour (10) · ships Nov-Dec 2026, stretch-goal cohort ----
  PartyMenuPlaceholder("Havana, Hotel Nacional", 1928, "Havana", Cohort.WORLD_TOUR, "STRETCH"),
  PartyMenuPlaceholder("Paris, Harry's New York Bar", 1925, "Paris", Cohort.WORLD_TOUR, "STRETCH"),
  PartyMenuPlaceholder("Shanghai, Cathay Hotel", 1927, "Shanghai", Cohort.WORLD_TOUR, "STRETCH"),
  PartyMenuPlaceholder("London, American Bar", 1922, "London", Cohort.WORLD_TOUR, "STRETCH"),
  PartyMenuPlaceholder("Berlin, Weimar Salon", 1930, "Berlin", Cohort.WORLD_TOUR, "STRETCH"),
  PartyMenuPlaceholder("Rome, the Negroni Bar", 1919, "Rome", Cohort.WORLD_TOUR, "STRETCH"),
  PartyMenuPlaceholder("Sydney, American Hotel", 1924, "Sydney", Cohort.WORLD_TOUR, "STRETCH"),
  PartyMenuPlaceholder("Tokyo, Ginza Salon", 1926, "Tokyo", Cohort.WORLD_TOUR, "STRETCH"),
  PartyMenuPlaceholder("Buenos Aires, Sur", 1929, "Buenos Aires", Cohort.WORLD_TOUR, "STRETCH"),
  PartyMenuPlaceholder("Cairo, Shepheard's Bar", 1925, "Cairo", Cohort.WORLD_TOUR, "STRETCH"),

  // ---- Occasions (15) · ships Nov 2026 ----
  PartyMenuPlaceholder("Wedding Reception", 1926, "Long Island", Cohort.OCCASIONS, "NOV 2026"),
  PartyMenuPlaceholder("Election Night", 1924, "Anywhere", Cohort.OCCASIONS, "NOV 2026"),
  PartyMenuPlaceholder("After the Theatre", 1922, "Broadway", Cohort.OCCASIONS, "NOV 2026"),
  PartyMenuPlaceholder("Card Night", 1928, "Chicago", Cohort.OCCASIONS, "NOV 2026"),
  PartyMenuPlaceholder("Boxing Match Watch Party", 1927, "New York", Cohort.OCCASIONS, "NOV 2026"),
  PartyMenuPlaceholder("Book Club, Algonquin Set", 1924, "New York", Cohort.OCCASIONS, "NOV 2026"),
  PartyMenuPlaceholder("Jazz Salon in Harlem", 1925, "New York", Cohort.OCCASIONS, "NOV 2026"),
  PartyMenuPlaceholder("Boudoir Cocktail Hour", 1921, "Anywhere", Cohort.OCCASIONS, "NOV 2026"),
  PartyMenuPlaceholder("The Stag Night", 1920, "Anywhere", Cohort.OCCASIONS, "NOV 2026"),
  PartyMenuPlaceholder("Engagement Brunch", 1925, "Long Island", Cohort.OCCASIONS, "NOV 2026"),
  PartyMenuPlaceholder("Anniversary Dinner", 1923, "Anywhere", Cohort.OCCASIONS, "DEC 2026"),
  PartyMenuPlaceholder("Newspaper Press Room", 1922, "Chicago", Cohort.OCCASIONS, "DEC 2026"),
  PartyMenuPlaceholder("Movie Premiere Night", 1927, "Los Angeles", Cohort.OCCASIONS, "DEC 2026"),
  PartyMenuPlaceholder("Yacht Club Cocktails", 1925, "Newport", Cohort.OCCASIONS, "DEC 2026"),
  PartyMenuPlaceholder("Sunday Supper", 1923, "Anywhere", Cohort.OCCASIONS, "DEC 2026"),

  // ---- More Mystery Nights (25) · STRETCH unlock at $50,000 ----
  // Five live now in MYSTERY_MENUS. These twenty-five additional mysteries
  // ship if the campaign hits the $50,000 stretch goal. Sent free to every
  // backer at the $49 Founding Set tier and above.
  PartyMenuPlaceholder("The Rooftop Affair", 1925, "Manhattan", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("Death at the Theatre Guild", 1923, "Broadway", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("The Knickerbocker Suite", 1922, "New York", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("Murder on the IRT", 1924, "Manhattan", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("The Cotton Club's Lost Heir", 1928, "Harlem", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("The Capone Banquet", 1928, "Chicago", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("Detroit Riverside", 1930, "Detroit", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("The Stockyard Investor", 1927, "Chicago", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("Lake Geneva Estate", 1925, "Wisconsin", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("Mardi Gras at the St. Charles", 1927, "New Orleans", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("The Distiller's Wife", 1924, "Louisville", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("Charleston at Sundown", 1926, "Charleston", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("The Cocoanut Grove", 1928, "Los Angeles", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("The San Francisco Tea Room", 1923, "San Francisco", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("Hollywood Premiere Night", 1929, "Los Angeles", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("The Cairo Embassy", 1925, "Cairo", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("Berlin Cabaret After Dark", 1928, "Berlin", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("The Singapore Sling", 1924, "Singapore", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("Havana Casino", 1927, "Havana", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("Buenos Aires Tango Hall", 1926, "Buenos Aires", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("The Suffragette's Last Dinner", 1920, "Washington", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("Black Tuesday Banquet", 1929, "New York", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("The Veterans' Hall", 1925, "Boston", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("Christmas Eve Manor", 1924, "Connecticut", Cohort.MYSTERY_STRETCH, "STRETCH"),
  PartyMenuPlaceholder("Spring Equinox Salon", 1923, "Boston", Cohort.MYSTERY_STRETCH, "STRETCH"),
)

/** Grouped placeholders, ordered for the grid render. */
val PLACEHOLDERS_BY_COHORT: Map<Cohort, List<PartyMenuPlaceholder>> =
  PLACEHOLDER_MENUS.groupBy { it.cohort }

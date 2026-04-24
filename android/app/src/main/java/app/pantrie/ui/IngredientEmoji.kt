package app.pantrie.ui

/** Map ingredient/pantry item names to emoji. Returns null if no good match (caller can skip). */
object IngredientEmoji {

  private val EMOJI_MAP: List<Pair<Regex, String>> = listOf(
    // Produce
    Regex("tomato") to "🍅",
    Regex("onion|shallot|scallion|leek") to "🧅",
    Regex("garlic") to "🧄",
    Regex("potato") to "🥔",
    Regex("sweet potato|yam") to "🍠",
    Regex("carrot") to "🥕",
    Regex("cucumber|pickle") to "🥒",
    Regex("pepper|chili|chilli|capsicum|jalapeno|habanero|poblano") to "🌶️",
    Regex("bell pepper|sweet pepper") to "🫑",
    Regex("broccoli") to "🥦",
    Regex("lettuce|cabbage|kale|spinach|chard|arugula|rocket|greens") to "🥬",
    Regex("eggplant|aubergine") to "🍆",
    Regex("corn|maize") to "🌽",
    Regex("avocado") to "🥑",
    Regex("mushroom") to "🍄",
    Regex("lemon") to "🍋",
    Regex("lime") to "🫛",
    Regex("orange|mandarin|clementine") to "🍊",
    Regex("apple") to "🍎",
    Regex("banana") to "🍌",
    Regex("grape") to "🍇",
    Regex("strawberr") to "🍓",
    Regex("blueberr|raspberr|blackberr|berr") to "🫐",
    Regex("peach|nectarine") to "🍑",
    Regex("pear") to "🍐",
    Regex("cherry|cherries") to "🍒",
    Regex("pineapple") to "🍍",
    Regex("watermelon") to "🍉",
    Regex("melon|cantaloupe|honeydew") to "🍈",
    Regex("mango") to "🥭",
    Regex("kiwi") to "🥝",
    Regex("coconut") to "🥥",
    Regex("olive") to "🫒",
    Regex("pea|edamame") to "🫛",
    Regex("ginger|galangal") to "🫚",
    Regex("beet|beetroot|radish|turnip|parsnip") to "🥕",
    Regex("herb|basil|parsley|cilantro|mint|thyme|oregano|rosemary|sage|dill|tarragon|chive|bay") to "🌿",

    // Protein
    Regex("chicken") to "🍗",
    Regex("turkey") to "🦃",
    Regex("beef|steak|veal") to "🥩",
    Regex("pork|ham|bacon|prosciutto") to "🥓",
    Regex("lamb|mutton") to "🍖",
    Regex("sausage|chorizo|hot dog|frankfurter") to "🌭",
    Regex("fish|salmon|tuna|cod|tilapia|halibut|snapper|trout|sardine|anchov|herring") to "🐟",
    Regex("shrimp|prawn") to "🍤",
    Regex("lobster|crab|crayfish") to "🦞",
    Regex("scallop|mussel|clam|oyster") to "🦪",
    Regex("squid|octopus|calamari") to "🐙",
    Regex("egg") to "🥚",
    Regex("tofu|tempeh|seitan|bean curd") to "🧆",

    // Dairy
    Regex("milk|cream|half.?and.?half|buttermilk") to "🥛",
    Regex("cheese|cheddar|mozzarella|parmesan|feta|brie|gouda|ricotta|cottage") to "🧀",
    Regex("butter") to "🧈",
    Regex("yogurt|yoghurt") to "🍶",

    // Grains / starches
    Regex("bread|bun|roll|baguette|sourdough|toast") to "🍞",
    Regex("bagel|pretzel") to "🥨",
    Regex("croissant") to "🥐",
    Regex("pancake|waffle") to "🥞",
    Regex("rice|grain|quinoa|barley|couscous|farro") to "🍚",
    Regex("pasta|spaghetti|noodle|ramen|udon|soba|linguine|fettuccine|penne") to "🍝",
    Regex("tortilla|wrap|pita|flatbread") to "🫓",
    Regex("taco") to "🌮",
    Regex("burrito|quesadilla") to "🌯",
    Regex("pizza|flatbread") to "🍕",
    Regex("dumpling|gyoza|ravioli|wonton") to "🥟",
    Regex("sushi|nigiri|sashimi") to "🍣",
    Regex("oat|oatmeal|muesli|cereal") to "🥣",
    Regex("flour|cornmeal|polenta") to "🌾",

    // Legumes / nuts / seeds
    Regex("bean|chickpea|lentil|pulse") to "🫘",
    Regex("peanut|almond|walnut|pecan|cashew|pistachio|pine nut|hazelnut|nut") to "🥜",
    Regex("sesame|chia|flax|sunflower|pumpkin seed|seed") to "🌱",

    // Condiments / sweeteners / oils
    Regex("oil|vinegar|dressing|vinaigrette") to "🫗",
    Regex("soy sauce|fish sauce|hoisin|teriyaki|worcestershire|sauce") to "🥫",
    Regex("honey|nectar") to "🍯",
    Regex("jam|jelly|preserve|marmalade") to "🍯",
    Regex("mustard|ketchup|mayo|mayonnaise|sriracha|hot sauce|bbq|barbecue") to "🍶",
    Regex("salt|sugar|spice|seasoning|powder|paprika|cinnamon|pepper|cumin|coriander seed|turmeric|curry|saffron|nutmeg|clove|cardamom|star anise") to "🧂",
    Regex("vanilla|extract") to "🫙",
    Regex("chocolate|cocoa|cacao") to "🍫",
    Regex("cookie|biscuit") to "🍪",
    Regex("cake|cupcake|muffin") to "🧁",
    Regex("pie|tart") to "🥧",
    Regex("ice cream|gelato|sorbet") to "🍨",

    // Beverages
    Regex("water|sparkling") to "💧",
    Regex("coffee|espresso|latte") to "☕",
    Regex("tea|matcha") to "🍵",
    Regex("wine|prosecco|champagne") to "🍷",
    Regex("beer|ale|lager|stout") to "🍺",
    Regex("cocktail|martini|spirit|vodka|gin|rum|whiskey|tequila") to "🍸",
    Regex("juice|soda|cola") to "🧃",

    // Canned / frozen / pantry staples
    Regex("frozen") to "🧊",
    Regex("soup|broth|stock") to "🥣",
    Regex("salad") to "🥗",
    Regex("popcorn") to "🍿",
    Regex("candy|sweet") to "🍬",
  )

  fun forName(name: String): String? {
    if (name.isBlank()) return null
    val n = name.lowercase()
    for ((re, emoji) in EMOJI_MAP) {
      if (re.containsMatchIn(n)) return emoji
    }
    return null
  }

  /** Defaults to a plate emoji for generic food items when no specific match. */
  fun forNameOrDefault(name: String): String = forName(name) ?: "🍽️"
}

package app.pantrie.ui

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Map an ingredient/cuisine/glass/aisle name to a `brimm_*` drawable resource id, or 0 if no match.
 *
 * Resolution is done at runtime via [Context.getResources] + `getIdentifier` so this file can
 * list the full canonical slug set even when only a subset of the 124 PNGs has actually landed
 * in `res/drawable-xxhdpi/`. A missing PNG just yields 0 and the caller falls back to a brass
 * placeholder dot — nothing crashes.
 *
 * The slug list mirrors the TARGETS array in `backend/ingest/generate_brimm_images.cjs`. Keep
 * the two in sync when new images are added or renamed.
 *
 * TODO(naming): NAMING.md Phase G renames the on-disk drawables from `brimm_*` to content
 * prefixes (`food_*`, `cuisine_*`, `glass_*`, `aisle_*`). Update [BRIMM_PREFIX] when that lands.
 */
object IngredientImage {

  /** Drawable name prefix. Single source of truth for the eventual Phase G rename. */
  private const val BRIMM_PREFIX = "brimm_"

  // ---------------------------------------------------------------------------------------------
  // Slug -> regex synonym matching for ingredient names that arrive from the backend.
  // Same "first match wins" ordering. Keep more specific patterns ABOVE more generic ones
  // (sweet potato before potato, etc.).
  // ---------------------------------------------------------------------------------------------
  private val INGREDIENT_RULES: List<Pair<Regex, String>> = listOf(
    // Produce — specific before generic
    Regex("sweet potato|yam") to "sweet_potato",
    Regex("bell pepper|sweet pepper") to "bell_pepper",
    Regex("pepper|chili|chilli|capsicum|jalapeno|habanero|poblano") to "chili",
    Regex("tomato") to "tomato",
    Regex("onion|shallot|scallion|leek") to "onion",
    Regex("garlic") to "garlic",
    Regex("potato") to "potato",
    Regex("carrot|beet|beetroot|radish|turnip|parsnip") to "carrot",
    Regex("cucumber|pickle") to "cucumber",
    Regex("broccoli") to "broccoli",
    Regex("lettuce|cabbage|kale|spinach|chard|arugula|rocket|greens") to "leafy_greens",
    Regex("eggplant|aubergine") to "eggplant",
    Regex("corn|maize") to "corn",
    Regex("avocado") to "avocado",
    Regex("mushroom") to "mushroom",
    Regex("lemon") to "lemon",
    Regex("lime") to "lime",
    Regex("orange|mandarin|clementine") to "orange",
    Regex("apple") to "apple",
    Regex("banana") to "banana",
    Regex("grape") to "grape",
    Regex("strawberr") to "strawberry",
    Regex("blueberr|raspberr|blackberr|berr") to "berries",
    Regex("peach|nectarine") to "peach",
    Regex("pear") to "pear",
    Regex("cherry|cherries") to "cherry",
    Regex("pineapple") to "pineapple",
    Regex("watermelon") to "watermelon",
    Regex("melon|cantaloupe|honeydew") to "melon",
    Regex("mango") to "mango",
    Regex("kiwi") to "kiwi",
    Regex("coconut") to "coconut",
    Regex("olive") to "olive",
    Regex("pea|edamame") to "pea",
    Regex("ginger|galangal") to "ginger",
    Regex("herb|basil|parsley|cilantro|mint|thyme|oregano|rosemary|sage|dill|tarragon|chive|bay") to "herbs",

    // Protein
    Regex("chicken") to "chicken",
    Regex("turkey") to "turkey",
    Regex("beef|steak|veal") to "beef",
    Regex("pork|ham|bacon|prosciutto") to "pork",
    Regex("lamb|mutton") to "lamb",
    Regex("sausage|chorizo|hot dog|frankfurter") to "sausage",
    Regex("fish|salmon|tuna|cod|tilapia|halibut|snapper|trout|sardine|anchov|herring") to "fish",
    Regex("shrimp|prawn") to "shrimp",
    Regex("lobster|crab|crayfish") to "lobster",
    Regex("scallop|mussel|clam|oyster") to "shellfish",
    Regex("squid|octopus|calamari") to "octopus",
    Regex("egg") to "egg",
    Regex("tofu|tempeh|seitan|bean curd") to "tofu",

    // Dairy
    Regex("milk|cream|half.?and.?half|buttermilk") to "milk",
    Regex("cheese|cheddar|mozzarella|parmesan|feta|brie|gouda|ricotta|cottage") to "cheese",
    Regex("butter") to "butter",
    Regex("yogurt|yoghurt") to "yogurt",

    // Grains / starches — specific compound carbs before bread
    Regex("bagel|pretzel") to "bagel",
    Regex("croissant") to "croissant",
    Regex("pancake|waffle") to "pancake",
    Regex("tortilla|wrap|pita|flatbread") to "tortilla",
    Regex("taco") to "taco",
    Regex("burrito|quesadilla") to "burrito",
    Regex("pizza") to "pizza",
    Regex("dumpling|gyoza|ravioli|wonton") to "dumpling",
    Regex("sushi|nigiri|sashimi") to "sushi",
    Regex("oat|oatmeal|muesli|cereal") to "oatmeal",
    Regex("pasta|spaghetti|noodle|ramen|udon|soba|linguine|fettuccine|penne") to "pasta",
    Regex("rice|grain|quinoa|barley|couscous|farro") to "rice",
    Regex("bread|bun|roll|baguette|sourdough|toast") to "bread",
    Regex("flour|cornmeal|polenta") to "flour",

    // Legumes / nuts / seeds
    Regex("bean|chickpea|lentil|pulse") to "beans",
    Regex("peanut|almond|walnut|pecan|cashew|pistachio|pine nut|hazelnut|nut") to "nuts",
    Regex("sesame|chia|flax|sunflower|pumpkin seed|seed") to "seeds",

    // Condiments / sweeteners / oils — honey before "sauce" so honey wins over generic sauces
    Regex("honey|nectar|jam|jelly|preserve|marmalade") to "honey",
    Regex("oil|vinegar|dressing|vinaigrette") to "oil",
    Regex("soy sauce|fish sauce|hoisin|teriyaki|worcestershire|sauce") to "sauce",
    Regex("mustard|ketchup|mayo|mayonnaise|sriracha|hot sauce|bbq|barbecue") to "condiment",
    Regex("salt|sugar|spice|seasoning|powder|paprika|cinnamon|cumin|coriander seed|turmeric|curry|saffron|nutmeg|clove|cardamom|star anise") to "salt",
    Regex("vanilla|extract") to "vanilla",
    Regex("chocolate|cocoa|cacao") to "chocolate",
    Regex("cookie|biscuit") to "cookie",
    Regex("cake|cupcake|muffin") to "cake",
    Regex("pie|tart") to "pie",
    Regex("ice cream|gelato|sorbet") to "ice_cream",

    // Beverages
    Regex("water|sparkling") to "water",
    Regex("coffee|espresso|latte") to "coffee",
    Regex("tea|matcha") to "tea",
    Regex("wine|prosecco|champagne") to "wine",
    Regex("beer|ale|lager|stout") to "beer",
    Regex("cocktail|martini|spirit|vodka|gin|rum|whiskey|tequila") to "cocktail_generic",
    Regex("juice|soda|cola") to "juice",

    // Pantry catch-alls
    Regex("frozen") to "frozen",
    Regex("soup|broth|stock") to "soup",
    Regex("salad") to "salad",
    Regex("popcorn") to "popcorn",
    Regex("candy|sweet") to "candy",
  )

  /** Cuisine name -> slug. Keys must be lowercased. */
  private val CUISINE_SLUGS: Map<String, String> = mapOf(
    "italian" to "cuisine_italian",
    "mexican" to "cuisine_mexican",
    "japanese" to "cuisine_japanese",
    "chinese" to "cuisine_chinese",
    "indian" to "cuisine_indian",
    "thai" to "cuisine_thai",
    "french" to "cuisine_french",
    "american" to "cuisine_american",
    "mediterranean" to "cuisine_mediterranean",
    "greek" to "cuisine_mediterranean",
    "korean" to "cuisine_korean",
  )

  /** Glass type -> slug. Keys must be lowercased. */
  private val GLASS_SLUGS: Map<String, String> = mapOf(
    "rocks" to "glass_rocks",
    "old fashioned" to "glass_rocks",
    "lowball" to "glass_rocks",
    "coupe" to "glass_coupe",
    "highball" to "glass_highball",
    "collins" to "glass_highball",
    "flute" to "glass_flute",
    "champagne" to "glass_flute",
    "martini" to "glass_martini",
    "shot" to "glass_shot",
  )

  /** Aisle name -> slug. Keys must be lowercased. */
  private val AISLE_SLUGS: Map<String, String> = mapOf(
    "produce" to "aisle_produce",
    "bakery" to "aisle_bakery",
    "deli" to "aisle_deli",
    "protein" to "aisle_protein",
    "dairy" to "aisle_dairy",
    "frozen" to "aisle_frozen",
    "grain" to "aisle_grain",
    "pantry" to "aisle_pantry",
    "spice" to "aisle_spice",
    "condiment" to "aisle_condiment",
    "bar" to "aisle_bar",
    "beverage" to "aisle_beverage",
    "other" to "aisle_other",
  )

  /** Tiny memo cache so repeated lookups for "tomato" don't re-walk the rules + getIdentifier. */
  private val cache = ConcurrentHashMap<String, Int>()

  /**
   * Resolve an ingredient name (free-form, e.g. "Roma Tomatoes", "free-range chicken") to a
   * `brimm_*` drawable id. Returns 0 if no rule matched OR the resource doesn't exist on disk.
   */
  fun forName(context: Context, name: String): Int {
    if (name.isBlank()) return 0
    val n = name.lowercase().trim()
    cache[n]?.let { return it }
    for ((re, slug) in INGREDIENT_RULES) {
      if (re.containsMatchIn(n)) {
        val id = resolve(context, slug)
        cache[n] = id
        return id
      }
    }
    cache[n] = 0
    return 0
  }

  fun cuisineImage(context: Context, cuisine: String?): Int {
    val key = cuisine?.trim()?.lowercase().orEmpty()
    if (key.isEmpty()) return 0
    return CUISINE_SLUGS[key]?.let { resolve(context, it) } ?: 0
  }

  fun glassImage(context: Context, glassType: String?): Int {
    val key = glassType?.trim()?.lowercase().orEmpty()
    if (key.isEmpty()) return 0
    return GLASS_SLUGS[key]?.let { resolve(context, it) } ?: 0
  }

  fun aisleImage(context: Context, aisleName: String?): Int {
    val key = aisleName?.trim()?.lowercase().orEmpty()
    if (key.isEmpty()) return 0
    return AISLE_SLUGS[key]?.let { resolve(context, it) } ?: 0
  }

  /** `getIdentifier` returns 0 when the resource is missing — exactly what we want as a sentinel. */
  private fun resolve(context: Context, slug: String): Int {
    @Suppress("DiscouragedApi") // Intentional — gen pipeline ships PNGs in waves.
    return context.resources.getIdentifier(
      "$BRIMM_PREFIX$slug", "drawable", context.packageName,
    )
  }
}

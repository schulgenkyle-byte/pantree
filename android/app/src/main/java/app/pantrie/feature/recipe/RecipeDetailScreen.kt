@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantrie.feature.recipe

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.Recipe
import app.pantrie.network.dto.RecipeNutrition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecipeDetailUi(
  val recipe: Recipe? = null,
  val nutrition: RecipeNutrition? = null,
  val loading: Boolean = true,
  val error: String? = null,
  /** User's allergen list, used to flag matching ingredients with a banner. */
  val userAllergens: List<String> = emptyList(),
  /** allergen-name → has-real-sub. Populated after the recipe + allergens
   *  load by querying the substitutions endpoint per matched allergen.
   *  Drives the banner copy: "Substitute available" vs "No sub on file." */
  val allergenSubs: Map<String, Boolean> = emptyMap(),
  /** True while the per-allergen substitutions queries are in flight. */
  val checkingSubs: Boolean = false,
)

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
  private val api: PantrieApi,
) : ViewModel() {
  private val _ui = MutableStateFlow(RecipeDetailUi())
  val ui = _ui.asStateFlow()

  fun load(id: String) {
    viewModelScope.launch {
      runCatching { api.recipe(id) }
        .onSuccess { _ui.value = _ui.value.copy(recipe = it, loading = false) }
        .onFailure { _ui.value = _ui.value.copy(error = it.message, loading = false) }
      runCatching { api.recipeNutrition(id) }
        .onSuccess { _ui.value = _ui.value.copy(nutrition = it.nutrition) }
      runCatching { api.getPreferences() }
        .onSuccess { _ui.value = _ui.value.copy(userAllergens = it.allergens) }
      // Once recipe + allergens are loaded, check substitutions for each
      // allergen that actually appears in this recipe. Banner copy depends
      // on whether the substitutions endpoint returned a non-empty list.
      checkSubstitutionsForAllergens()
    }
  }

  /** Fire one substitutions query per allergen that matches an ingredient.
   *  Stores results in allergenSubs map so the banner can render variants.
   *  For substitutions lookup we use the FIRST matched ingredient name, not
   *  the allergen label, so "dairy" → "cheddar" actually queries the subs
   *  for "cheddar" not for the unspecific word "dairy." */
  private fun checkSubstitutionsForAllergens() {
    viewModelScope.launch {
      val state = _ui.value
      val recipe = state.recipe ?: return@launch
      if (state.userAllergens.isEmpty()) return@launch
      val matches = matchAllergens(recipe.ingredients.map { it.name }, state.userAllergens)
      if (matches.isEmpty()) return@launch
      _ui.value = _ui.value.copy(checkingSubs = true)
      val results = mutableMapOf<String, Boolean>()
      for ((allergen, hitIngredient) in matches) {
        val resp = runCatching { api.substitutions(hitIngredient) }.getOrNull()
        results[allergen] = resp?.subs?.isNotEmpty() == true
      }
      _ui.value = _ui.value.copy(allergenSubs = results, checkingSubs = false)
    }
  }
}

/** Allergen → keywords commonly seen in ingredient names. Substring match,
 *  case-insensitive. FDA Top 9 plus mustard / sulfites / corn / celery /
 *  gluten / molluscs (~14 total). Order doesn't matter for matching. */
internal val ALLERGEN_KEYWORDS: Map<String, List<String>> = mapOf(
  "peanuts" to listOf("peanut", "groundnut"),
  "tree nuts" to listOf(
    "almond", "walnut", "pecan", "cashew", "hazelnut", "pistachio",
    "macadamia", "brazil nut", "pine nut", "chestnut",
  ),
  "shellfish" to listOf(
    "shrimp", "prawn", "lobster", "crab", "crayfish", "langoustine",
  ),
  "molluscs" to listOf(
    "oyster", "mussel", "clam", "scallop", "squid", "calamari", "octopus", "snail",
  ),
  "fish" to listOf(
    "fish", "salmon", "tuna", "cod", "tilapia", "haddock", "trout",
    "anchovy", "sardine", "bass", "mackerel", "halibut", "snapper", "swordfish",
  ),
  "eggs" to listOf("egg", "yolk", "albumen", "mayonnaise", "mayo"),
  "dairy" to listOf(
    "milk", "butter", "cheese", "cream", "yogurt", "yoghurt", "ricotta",
    "parmesan", "mozzarella", "ghee", "buttermilk", "kefir", "whey",
    "casein", "lactose", "cheddar", "feta", "gouda", "brie", "burrata",
  ),
  "soy" to listOf(
    "soy", "soybean", "tofu", "tempeh", "edamame", "miso", "tamari",
  ),
  "wheat" to listOf(
    "wheat", "flour", "bread", "pasta", "couscous", "bulgur", "semolina",
    "spelt", "farro", "noodle",
  ),
  "gluten" to listOf(
    "wheat", "barley", "rye", "flour", "bread", "pasta", "couscous",
    "bulgur", "semolina", "spelt", "malt", "farro", "seitan", "noodle",
  ),
  "sesame" to listOf("sesame", "tahini"),
  "mustard" to listOf("mustard"),
  "sulfites" to listOf("sulfite", "wine", "dried apricot", "molasses", "champagne"),
  "corn" to listOf(
    "corn", "cornstarch", "polenta", "hominy", "masa", "tortilla", "popcorn",
  ),
  "celery" to listOf("celery"),
)

/** Returns map of allergen → first matching ingredient name. Allergen labels
 *  not in the keyword table fall back to a literal substring match against
 *  the allergen label itself, so user-typed allergens still work. */
internal fun matchAllergens(
  ingredientNames: List<String>,
  userAllergens: List<String>,
): Map<String, String> {
  val lowercased = ingredientNames.map { it.lowercase() }
  val matched = mutableMapOf<String, String>()
  for (rawAllergen in userAllergens) {
    val allergen = rawAllergen.trim().lowercase()
    if (allergen.isEmpty()) continue
    val keywords = ALLERGEN_KEYWORDS[allergen] ?: listOf(allergen)
    for ((idx, ingLower) in lowercased.withIndex()) {
      val keyword = keywords.firstOrNull { ingLower.contains(it) }
      if (keyword != null) {
        matched[allergen] = ingredientNames[idx]
        break
      }
    }
  }
  return matched
}

@Composable
fun RecipeDetailScreen(
  recipeId: String,
  onBack: () -> Unit,
  vm: RecipeDetailViewModel = hiltViewModel(),
) {
  LaunchedEffect(recipeId) { vm.load(recipeId) }
  val ui by vm.ui.collectAsState()
  var subIngredient by remember { mutableStateOf<String?>(null) }
  var showAddToBook by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(ui.recipe?.title ?: "Recipe") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.Close, contentDescription = "Back") } },
        actions = {
          if (ui.recipe != null) {
            IconButton(onClick = { showAddToBook = true }) {
              Icon(Icons.Outlined.Bookmark, contentDescription = "Save to a book")
            }
          }
        },
      )
    },
  ) { padding ->
    val recipe = ui.recipe
    if (ui.loading || recipe == null) {
      Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        if (ui.error != null) Text(ui.error ?: "") else CircularProgressIndicator()
      }
      return@Scaffold
    }

    // Allergen detection map: keyword-based, not literal substring on the
    // allergen label. So "dairy" matches "cheddar," "wheat" matches "flour."
    val matchedAllergens = remember(recipe.ingredients, ui.userAllergens) {
      matchAllergens(recipe.ingredients.map { it.name }, ui.userAllergens).keys.toList()
    }

    Column(modifier = Modifier.padding(padding).fillMaxSize()) {

      // Debug-only diagnostic strip. Stamped with versionCode so we can verify the
      // install is fresh, plus the loaded-vs-matched allergen counts. Bright red
      // background so it cannot be missed on the device and we can rule out
      // "stale APK" before debugging the keyword matcher itself.
      if (app.pantrie.BuildConfig.DEBUG) {
        androidx.compose.material3.Surface(
          modifier = Modifier.fillMaxWidth(),
          color = androidx.compose.ui.graphics.Color(0xFFC54B3C),
        ) {
          Text(
            "DEBUG vc${app.pantrie.BuildConfig.VERSION_CODE} · allergens=${ui.userAllergens.size} " +
              "[${ui.userAllergens.joinToString()}] · matched=${matchedAllergens.size} " +
              "[${matchedAllergens.joinToString()}]",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
          )
        }
      }

      // Banner is OUTSIDE the LazyColumn so it spans the full screen width
      // edge-to-edge — no contentPadding inset. Sticks at the top of the
      // recipe view, never scrolls out of sight while the user reads steps.
      if (matchedAllergens.isNotEmpty()) {
        AllergenBanner(
          matched = matchedAllergens,
          subStatus = ui.allergenSubs,
          checking = ui.checkingSubs,
        )
      }

      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        item {
          Text(recipe.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
          val meta = listOfNotNull(
            recipe.cuisine,
            "${recipe.totalMin} min",
            "${recipe.serves} servings",
          ).joinToString(" · ")
          if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyMedium)
        }

        ui.nutrition?.let { item { NutritionRow(it) } }

        item {
          Spacer(Modifier.height(6.dp))
          Text("Ingredients", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Text("Long-press for substitutes.", style = MaterialTheme.typography.labelSmall)
        }

      itemsIndexed(recipe.ingredients, key = { idx, ing -> "${idx}-${ing.name}" }) { _, ing ->
        Row(
          Modifier
            .fillMaxWidth()
            .combinedClickable(
              onClick = {},
              onLongClick = { subIngredient = ing.name },
            )
            .padding(vertical = 8.dp),
        ) {
          Text(
            listOfNotNull(
              ing.quantity?.takeIf { it > 0.0 }?.let { q -> if (q == q.toInt().toDouble()) q.toInt().toString() else "%.1f".format(q) },
              ing.unit?.takeIf { it.isNotBlank() },
              ing.name,
              // Show "to taste" hint for seasonings without a quantity
              if ((ing.quantity ?: 0.0) <= 0.0 && ing.unit.isNullOrBlank()) "· to taste" else null,
            ).joinToString(" "),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
          )
          if (ing.have) Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(18.dp))
        }
        HorizontalDivider()
      }

      item {
        Spacer(Modifier.height(8.dp))
        Text("Steps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      }

      items(recipe.steps, key = { it.order }) { step ->
        Column(Modifier.padding(vertical = 8.dp)) {
          Text("Step ${step.order + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
          Text(step.text, style = MaterialTheme.typography.bodyLarge)
          step.timerSeconds?.let { Text("Timer: ${it / 60}:${"%02d".format(it % 60)}", style = MaterialTheme.typography.labelSmall) }
        }
        HorizontalDivider()
      }

      // Attribution footer — required for CC-BY-SA photos (Wikimedia) and OGL-Canada recipes.
      val hasPhotoCredit = !recipe.photoCredit.isNullOrBlank() && !recipe.photoLicense.isNullOrBlank()
      val hasAttribution = !recipe.attribution.isNullOrBlank()
      if (hasPhotoCredit || hasAttribution) {
        item {
          Column(
            Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
          ) {
            if (hasPhotoCredit) {
              val line = buildString {
                append("Photo: ")
                append(recipe.photoCredit)
                append(" (")
                append(recipe.photoLicense)
                append(")")
                recipe.photoSourceUrl?.takeIf { it.isNotBlank() }?.let { append(" · Wikimedia Commons") }
              }
              Text(
                line,
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (hasAttribution) {
              if (hasPhotoCredit) Spacer(Modifier.height(4.dp))
              Text(
                recipe.attribution ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

      SubstitutionSheet(ingredient = subIngredient, onDismiss = { subIngredient = null })
    }
  }

  if (showAddToBook) {
    app.pantrie.feature.library.AddToBookSheet(
      recipeId = recipeId,
      onDismiss = { showAddToBook = false },
    )
  }
}

/**
 * Allergen banner with three render states:
 *   checking  → "Allergen detected. Checking substitutes..."
 *   has-subs  → terracotta border + brass icon + "Substitute available."
 *   no-subs   → red-leaning border + warning icon + "No substitute on file."
 *   mixed     → per-allergen lozenges showing each status individually
 *
 * Hex literals used here instead of theme tokens because RecipeDetail uses the
 * Material 3 cream theme; dropping in raw values keeps the banner palette
 * locked regardless of upstream theme changes.
 */
@Composable
private fun AllergenBanner(
  matched: List<String>,
  subStatus: Map<String, Boolean>,
  checking: Boolean,
) {
  val terracotta = androidx.compose.ui.graphics.Color(0xFFB5634A)
  val red = androidx.compose.ui.graphics.Color(0xFFC54B3C)
  val ink = androidx.compose.ui.graphics.Color(0xFF2B2621)
  val inkSoft = androidx.compose.ui.graphics.Color(0xFF5A5148)
  val tintLight = androidx.compose.ui.graphics.Color(0xFFFCEAE3)

  // Decide overall tone. If we don't know yet, lean terracotta (informational).
  // If every matched allergen has zero subs, lean red (warning, no path forward).
  // Otherwise terracotta (action available).
  val resolved = subStatus.isNotEmpty() && !checking
  val noneHaveSubs = resolved && matched.all { subStatus[it] == false }
  val borderColor = if (noneHaveSubs) red else terracotta

  androidx.compose.material3.Surface(
    shape = RoundedCornerShape(8.dp),
    color = tintLight,
    border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Outlined.Warning,
        contentDescription = null,
        tint = borderColor,
        modifier = Modifier.size(22.dp),
      )
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        val label = matched.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
        Text(
          "Allergen${if (matched.size == 1) "" else "s"} detected: $label",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = ink,
        )
        Spacer(Modifier.height(4.dp))
        when {
          checking || subStatus.isEmpty() ->
            Text(
              "Checking substitute options...",
              style = MaterialTheme.typography.bodySmall,
              color = inkSoft,
            )
          // Single-allergen messaging is cleaner than the multi-allergen breakdown.
          matched.size == 1 -> {
            val a = matched[0]
            val hasSub = subStatus[a] == true
            Text(
              if (hasSub) "Substitute available. Long-press the ingredient to see options."
              else "No substitute on file. Consider skipping this recipe.",
              style = MaterialTheme.typography.bodySmall,
              color = inkSoft,
            )
          }
          // Multi-allergen: per-allergen status, comma-joined.
          else -> {
            val parts = matched.map { a ->
              val name = a.replaceFirstChar { c -> c.uppercase() }
              when (subStatus[a]) {
                true -> "$name: sub avail"
                false -> "$name: no sub"
                else -> "$name: checking"
              }
            }
            Text(
              parts.joinToString(" · "),
              style = MaterialTheme.typography.bodySmall,
              color = inkSoft,
            )
          }
        }
      }
    }
  }
}

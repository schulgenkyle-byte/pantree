@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantrie.feature.recipe

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
    }
  }
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

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(ui.recipe?.title ?: "Recipe") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.Close, contentDescription = "Back") } },
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

    LazyColumn(
      modifier = Modifier.padding(padding).fillMaxSize(),
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

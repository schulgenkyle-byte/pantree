package app.pantrie.feature.mealprep

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.pantrie.network.dto.MealPrepRecipe
import app.pantrie.network.dto.MealPrepShoppingItem
import app.pantrie.network.dto.MealPrepWeek
import app.pantrie.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPrepScreen(
  onBack: () -> Unit,
  vm: MealPrepViewModel = hiltViewModel(),
) {
  val input by vm.input.collectAsState()
  val state by vm.state.collectAsState()
  val banner by vm.bannerMessage.collectAsState()

  val snackbarHost = remember { SnackbarHostState() }
  LaunchedEffect(banner) {
    banner?.let {
      snackbarHost.showSnackbar(it)
      vm.clearBanner()
    }
  }

  Scaffold(
    containerColor = Cream,
    topBar = {
      TopAppBar(
        title = { Text("Meal Prep", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream, titleContentColor = Ink),
      )
    },
    snackbarHost = { SnackbarHost(snackbarHost) },
  ) { padding ->
    LazyColumn(
      Modifier.padding(padding).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Text(
          "Plan a week (or four) to hit your macro targets, with batch-cookable recipes and one consolidated shop.",
          style = MaterialTheme.typography.bodyMedium, color = InkSoft,
        )
      }

      item {
        InputCard(
          calories = input.dailyCalories,
          protein = input.dailyProtein,
          servingsPerDay = input.servingsPerDay,
          recipesPerWeek = input.recipesPerWeek,
          weeks = input.weeks,
          onCalories = { v -> vm.update { it.copy(dailyCalories = v) } },
          onProtein  = { v -> vm.update { it.copy(dailyProtein = v) } },
          onServings = { v -> vm.update { it.copy(servingsPerDay = v) } },
          onRecipes  = { v -> vm.update { it.copy(recipesPerWeek = v) } },
          onWeeks    = { v -> vm.update { it.copy(weeks = v) } },
        )
      }

      item {
        Button(
          onClick = vm::generate,
          modifier = Modifier.fillMaxWidth().height(56.dp),
          shape = RoundedCornerShape(4.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
          enabled = state !is MealPrepUiState.Loading,
        ) {
          Icon(Icons.Outlined.Bolt, null, tint = Paper, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          Text("Generate plan", color = Paper, fontWeight = FontWeight.SemiBold)
        }
      }

      when (val s = state) {
        MealPrepUiState.Idle -> Unit

        MealPrepUiState.Loading -> item {
          Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
          }
        }

        is MealPrepUiState.Ready -> {
          items(s.weeks, key = { "w-${it.index}" }) { w -> WeekCard(w) }
          if (s.shoppingList.isNotEmpty()) {
            item { ShoppingListHeader(count = s.shoppingList.size, onAddAll = vm::addAllToShop) }
            items(s.shoppingList.take(80), key = { "s-${it.canonical}" }) { item -> ShoppingRow(item) }
          }
          item {
            TextButton(onClick = vm::reset) { Text("Start over", color = InkSoft) }
          }
        }

        is MealPrepUiState.PaywallPreview -> {
          item { PaywallCard(message = s.upgrade) }
          s.preview?.let { w -> item { WeekCard(w, previewOnly = true) } }
          if (s.shoppingList.isNotEmpty()) {
            item { ShoppingListHeader(count = s.shoppingList.size, onAddAll = null) }
            items(s.shoppingList.take(20), key = { "p-${it.canonical}" }) { item -> ShoppingRow(item) }
          }
        }

        is MealPrepUiState.Error -> item {
          Column {
            Text("Something went wrong: ${s.message}", color = Terracotta, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(
              onClick = vm::reset,
              colors = ButtonDefaults.buttonColors(containerColor = Ink),
              shape = RoundedCornerShape(4.dp),
            ) { Text("Try again", color = Paper) }
          }
        }
      }
    }
  }
}

@Composable
private fun InputCard(
  calories: Int, protein: Int, servingsPerDay: Int, recipesPerWeek: Int, weeks: Int,
  onCalories: (Int) -> Unit, onProtein: (Int) -> Unit, onServings: (Int) -> Unit,
  onRecipes: (Int) -> Unit, onWeeks: (Int) -> Unit,
) {
  Surface(shape = RoundedCornerShape(12.dp), color = Paper, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Your targets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      NumberField("Daily calories (kcal)", calories, 1200, 4500, onCalories)
      NumberField("Daily protein (g)", protein, 50, 300, onProtein)
      NumberField("Servings per day", servingsPerDay, 1, 6, onServings)
      NumberField("Recipes per week", recipesPerWeek, 3, 10, onRecipes)
      NumberField("Number of weeks", weeks, 1, 4, onWeeks)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberField(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
  var text by remember(value) { mutableStateOf(value.toString()) }
  OutlinedTextField(
    value = text,
    onValueChange = { s ->
      text = s.filter { it.isDigit() }.take(5)
      text.toIntOrNull()?.coerceIn(min, max)?.let(onChange)
    },
    label = { Text(label) },
    modifier = Modifier.fillMaxWidth(),
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    supportingText = { Text("Range: $min-$max", color = InkMuted) },
    colors = OutlinedTextFieldDefaults.colors(
      focusedBorderColor = Ink,
      unfocusedBorderColor = InkFaint,
      cursorColor = Terracotta,
    ),
  )
}

@Composable
private fun WeekCard(w: MealPrepWeek, previewOnly: Boolean = false) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = Paper,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          if (previewOnly) "Preview · Week ${w.index}" else "Week ${w.index}",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
          color = Ink,
        )
        Spacer(Modifier.weight(1f))
        val okTolerance = w.totals?.withinTolerance == true
        Icon(
          Icons.Outlined.CheckCircle, null,
          tint = if (okTolerance) Olive else InkFaint,
          modifier = Modifier.size(18.dp),
        )
      }
      w.totals?.let { t ->
        Spacer(Modifier.height(4.dp))
        Text(
          "~${t.projectedDailyCalories} kcal/day · ~${t.projectedDailyProtein} g protein/day " +
            "(Δ ${t.calorieDeviation}% cal, ${t.proteinDeviation}% pro)",
          style = MaterialTheme.typography.bodySmall, color = InkMuted,
        )
      }
      Spacer(Modifier.height(12.dp))
      for (r in w.recipes) {
        RecipeRow(r)
        Spacer(Modifier.height(6.dp))
      }
    }
  }
}

@Composable
private fun RecipeRow(r: MealPrepRecipe) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = CreamAlt,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Outlined.Restaurant, null, tint = Terracotta, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(r.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Ink)
        val meta = listOfNotNull(
          r.cuisine,
          "${(r.prepMinutes ?: 0) + (r.cookMinutes ?: 0)} min",
          r.caloriesPerServing?.let { "$it kcal" },
          r.proteinPerServing?.let { "${it.toInt()} g pro" },
        ).joinToString(" · ")
        Text(meta, style = MaterialTheme.typography.bodySmall, color = InkMuted)
      }
      if (r.batchCookable == true) {
        Text(
          "BATCH",
          style = MaterialTheme.typography.labelSmall,
          color = Olive,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

@Composable
private fun ShoppingListHeader(count: Int, onAddAll: (() -> Unit)?) {
  Row(
    Modifier.fillMaxWidth().padding(top = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      "Shopping list · $count item${if (count == 1) "" else "s"}",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.weight(1f))
    if (onAddAll != null) {
      Button(
        onClick = onAddAll,
        colors = ButtonDefaults.buttonColors(containerColor = Ink),
        shape = RoundedCornerShape(4.dp),
      ) {
        Icon(Icons.Outlined.AddShoppingCart, null, tint = Paper, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Add all to shop", color = Paper, style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

@Composable
private fun ShoppingRow(item: MealPrepShoppingItem) {
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = Paper,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(item.name, style = MaterialTheme.typography.bodyMedium, color = Ink)
        val meta = buildString {
          if (item.quantity != null) {
            append(item.quantity)
            if (item.unit != null) append(' ').append(item.unit)
          }
          if (item.aisle != null) {
            if (isNotEmpty()) append(" · ")
            append(item.aisle)
          }
          if (item.recipeCount > 1) {
            if (isNotEmpty()) append(" · ")
            append("×${item.recipeCount}")
          }
        }
        if (meta.isNotEmpty()) {
          Text(meta, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
      }
    }
  }
}

@Composable
private fun PaywallCard(message: String) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = Terracotta.copy(alpha = 0.08f),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Outlined.Lock, null, tint = Terracotta, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(
          "pan-tree Pro",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold, color = Terracotta,
        )
        Text(
          message,
          style = MaterialTheme.typography.bodySmall, color = InkSoft,
        )
      }
    }
  }
}

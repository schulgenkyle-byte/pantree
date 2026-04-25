package app.pantrie.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.pantrie.network.dto.TasteProfile
import app.pantrie.ui.theme.*
import kotlinx.coroutines.delay

private val CUISINE_CHOICES = listOf(
  "italian", "mexican", "chinese", "japanese", "thai", "indian",
  "mediterranean", "american", "french", "korean", "vietnamese", "middle-eastern",
)
private val DIET_CHOICES = listOf(
  "none" to "No restriction",
  "vegetarian" to "Vegetarian",
  "vegan" to "Vegan",
  "pescatarian" to "Pescatarian",
  "keto" to "Keto",
  "paleo" to "Paleo",
  "gluten-free" to "Gluten-free",
  "dairy-free" to "Dairy-free",
)
private val ALLERGEN_CHOICES = listOf(
  "peanuts", "tree nuts", "shellfish", "fish", "eggs", "dairy", "soy", "wheat", "sesame",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  onRedoOnboarding: () -> Unit,
  onOpenSubmissions: () -> Unit = {},
  vm: SettingsViewModel = hiltViewModel(),
) {
  val s by vm.state.collectAsState()
  LaunchedEffect(s.savedFlash) {
    if (s.savedFlash) { delay(1400); vm.clearSavedFlash() }
  }

  Scaffold(
    containerColor = Cream,
    topBar = {
      TopAppBar(
        title = { Text("Settings", fontWeight = FontWeight.Medium) },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
      )
    },
  ) { padding ->
    Column(
      Modifier.padding(padding).fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
      if (s.loading) {
        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
        }
        return@Column
      }

      // ---------- Taste profile card ----------
      TasteProfileCard(s.taste)

      Spacer(Modifier.height(24.dp))
      SectionHeader("Favorite cuisines")
      Spacer(Modifier.height(8.dp))
      ChipGrid(
        options = CUISINE_CHOICES,
        selected = s.cuisines,
        onToggle = vm::toggleCuisine,
        labelFor = { it.replaceFirstChar { c -> c.uppercase() }.replace("-", " ") },
      )

      Spacer(Modifier.height(24.dp))
      SectionHeader("Diet")
      Spacer(Modifier.height(8.dp))
      ChipGrid(
        options = DIET_CHOICES.map { it.first },
        selected = setOf(s.diet),
        onToggle = { vm.setDiet(it) },
        labelFor = { key -> DIET_CHOICES.firstOrNull { it.first == key }?.second ?: key },
      )

      Spacer(Modifier.height(24.dp))
      SectionHeader("Allergens")
      Spacer(Modifier.height(8.dp))
      ChipGrid(
        options = ALLERGEN_CHOICES,
        selected = s.allergens,
        onToggle = vm::toggleAllergen,
        labelFor = { it.replaceFirstChar { c -> c.uppercase() } },
      )

      Spacer(Modifier.height(24.dp))
      SectionHeader("Ingredients to avoid")
      Text("Separate with commas — e.g. cilantro, olives",
        style = MaterialTheme.typography.bodySmall, color = InkMuted)
      Spacer(Modifier.height(6.dp))
      OutlinedTextField(
        value = s.avoidText,
        onValueChange = vm::setAvoidText,
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        placeholder = { Text("cilantro, olives") },
        shape = RoundedCornerShape(4.dp),
      )

      Spacer(Modifier.height(24.dp))
      SectionHeader("Heat tolerance")
      Text(
        when (s.heat) { 0 -> "None"; 1 -> "Mild"; 2 -> "Hot"; else -> "Fiery" },
        style = MaterialTheme.typography.bodyMedium, color = Ink,
      )
      Slider(
        value = s.heat.toFloat(),
        onValueChange = { vm.setHeat(it.toInt()) },
        valueRange = 0f..3f,
        steps = 2,
        colors = SliderDefaults.colors(thumbColor = Terracotta, activeTrackColor = Terracotta),
      )

      Spacer(Modifier.height(16.dp))
      SectionHeader("Adventure")
      Text(
        when (s.adventure) { 0 -> "Stick to my faves"; 1 -> "A little variety"; 2 -> "Show me new things"; else -> "Surprise me" },
        style = MaterialTheme.typography.bodyMedium, color = Ink,
      )
      Slider(
        value = s.adventure.toFloat(),
        onValueChange = { vm.setAdventure(it.toInt()) },
        valueRange = 0f..3f,
        steps = 2,
        colors = SliderDefaults.colors(thumbColor = Olive, activeTrackColor = Olive),
      )

      if (s.errorMessage != null) {
        Spacer(Modifier.height(12.dp))
        Text(s.errorMessage ?: "", color = Terracotta, style = MaterialTheme.typography.bodySmall)
      }

      // ---------- Mixology toggle ----------
      // Hidden by default; 21+ disclaimer in the subtitle. Flips a device-local
      // SharedPreferences flag, which MainActivity reads to decide whether to
      // render the Mixology tab in the bottom nav.
      Spacer(Modifier.height(24.dp))
      val mixOn by vm.showMixology.collectAsState()
      Surface(shape = RoundedCornerShape(8.dp), color = Paper, modifier = Modifier.fillMaxWidth()) {
        Row(
          Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(
              "Show Mixology (cocktails)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = Ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
              "Enables the cocktail deck with historic drinks and modernized recipes. Requires 21+.",
              style = MaterialTheme.typography.bodySmall,
              color = InkMuted,
            )
          }
          Switch(
            checked = mixOn,
            onCheckedChange = vm::setShowMixology,
            colors = SwitchDefaults.colors(
              checkedThumbColor = Terracotta,
              checkedTrackColor = Terracotta.copy(alpha = 0.4f),
            ),
          )
        }
      }

      Spacer(Modifier.height(24.dp))
      Button(
        onClick = vm::save,
        enabled = !s.saving,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
        shape = RoundedCornerShape(4.dp),
      ) {
        when {
          s.saving -> CircularProgressIndicator(Modifier.size(18.dp), color = Paper, strokeWidth = 2.dp)
          s.savedFlash -> Text("Saved", color = Paper)
          else -> Text("Save preferences", color = Paper)
        }
      }

      Spacer(Modifier.height(12.dp))
      OutlinedButton(
        onClick = onOpenSubmissions,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(4.dp),
      ) { Text("My submissions", color = Ink) }

      Spacer(Modifier.height(12.dp))
      OutlinedButton(
        onClick = { vm.redoOnboarding(onRedoOnboarding) },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(4.dp),
      ) { Text("Redo onboarding", color = Ink) }

      Spacer(Modifier.height(40.dp))
    }
  }
}

@Composable
private fun SectionHeader(title: String) {
  Text(title, style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.SemiBold, color = Ink)
}

@Composable
private fun TasteProfileCard(t: TasteProfile?) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = Paper,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(16.dp)) {
      Text("Your taste profile", style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold, color = Ink)
      if (t == null || t.totalSignals == 0) {
        Spacer(Modifier.height(4.dp))
        Text(
          "Cook or save a few recipes and we'll learn what you like.",
          style = MaterialTheme.typography.bodyMedium, color = InkMuted,
        )
        return@Column
      }
      Spacer(Modifier.height(4.dp))
      val summary = buildList {
        add("${t.savedCount} saved")
        add("${t.cookedCount} cooked")
        if (t.dismissedCount > 0) add("${t.dismissedCount} passed")
        if (t.complexityAvgMinutes > 0) add("~${t.complexityAvgMinutes} min avg")
      }.joinToString(" · ")
      Text(summary, style = MaterialTheme.typography.bodySmall, color = InkMuted)

      if (t.topCuisines.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Top cuisines", style = MaterialTheme.typography.labelLarge, color = InkSoft)
        Spacer(Modifier.height(4.dp))
        for (c in t.topCuisines.take(5)) TasteRow(c.name.ifBlank { "Unknown" }, c.score)
      }
      if (t.topIngredients.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Top ingredients", style = MaterialTheme.typography.labelLarge, color = InkSoft)
        Spacer(Modifier.height(4.dp))
        for (i in t.topIngredients.take(5)) TasteRow(i.name, i.score)
      }
    }
  }
}

@Composable
private fun TasteRow(name: String, score: Double) {
  Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(name.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = Ink,
      modifier = Modifier.weight(1f))
    Text("+${"%.1f".format(score)}", style = MaterialTheme.typography.bodySmall, color = Olive,
      fontWeight = FontWeight.Medium)
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChipGrid(
  options: List<String>,
  selected: Set<String>,
  onToggle: (String) -> Unit,
  labelFor: (String) -> String,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    for (opt in options) {
      val isOn = selected.contains(opt)
      FilterChip(
        selected = isOn,
        onClick = { onToggle(opt) },
        label = { Text(labelFor(opt)) },
        colors = FilterChipDefaults.filterChipColors(
          containerColor = Paper,
          selectedContainerColor = Ink,
          labelColor = Ink,
          selectedLabelColor = Paper,
        ),
      )
    }
  }
}


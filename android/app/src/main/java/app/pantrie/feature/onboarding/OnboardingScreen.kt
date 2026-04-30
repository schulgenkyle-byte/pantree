package app.pantrie.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.pantrie.ui.theme.*

/** Canonical pick lists. Kept short — users can always edit later in Settings. */
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
  onDone: () -> Unit,
  vm: OnboardingViewModel = hiltViewModel(),
) {
  val s by vm.state.collectAsState()
  LaunchedEffect(s.done) { if (s.done) onDone() }

  Scaffold(
    containerColor = Paper,
    topBar = {
      TopAppBar(
        title = { Text("Welcome to ${app.pantrie.Brand.APP_NAME}", fontWeight = FontWeight.Medium) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
      )
    },
  ) { padding ->
    Column(
      Modifier.padding(padding).fillMaxSize().padding(horizontal = 24.dp)
        .verticalScroll(rememberScrollState()),
    ) {
      StepIndicator(current = s.step, total = 3)

      when (s.step) {
        0 -> CuisineStep(s, vm)
        1 -> DietAllergenStep(s, vm)
        else -> HeatAdventureStep(s, vm)
      }

      if (s.errorMessage != null) {
        Spacer(Modifier.height(12.dp))
        Text(s.errorMessage ?: "", color = Terracotta, style = MaterialTheme.typography.bodySmall)
      }

      Spacer(Modifier.height(24.dp))
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (s.step > 0) {
          OutlinedButton(
            onClick = vm::back,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, InkFaint),
          ) { Text("Back", color = Ink) }
          Spacer(Modifier.width(12.dp))
        }
        Spacer(Modifier.weight(1f))
        if (s.step < 2) {
          Button(
            onClick = vm::next,
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
            shape = RoundedCornerShape(4.dp),
          ) { Text("Next", color = Paper) }
        } else {
          Button(
            onClick = vm::submit,
            enabled = !s.saving,
            colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
            shape = RoundedCornerShape(4.dp),
          ) {
            if (s.saving) CircularProgressIndicator(Modifier.size(18.dp), color = Paper, strokeWidth = 2.dp)
            else Text("Save & start cooking", color = Paper)
          }
        }
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun StepIndicator(current: Int, total: Int) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = 16.dp),
    horizontalArrangement = Arrangement.Start,
  ) {
    for (i in 0 until total) {
      val active = i <= current
      Surface(
        color = if (active) Ink else InkFaint.copy(alpha = 0.4f),
        shape = RoundedCornerShape(2.dp),
        modifier = Modifier.height(4.dp).weight(1f),
      ) {}
      if (i < total - 1) Spacer(Modifier.width(6.dp))
    }
  }
}

@Composable
private fun CuisineStep(s: OnboardingState, vm: OnboardingViewModel) {
  Text("Which cuisines do you love?", style = MaterialTheme.typography.headlineMedium,
    fontWeight = FontWeight.Medium, color = Ink)
  Spacer(Modifier.height(4.dp))
  Text("Pick as many as you like — we'll prioritize these in your deck.",
    style = MaterialTheme.typography.bodyMedium, color = InkMuted)
  Spacer(Modifier.height(16.dp))
  ChipGrid(
    options = CUISINE_CHOICES,
    selected = s.cuisines,
    onToggle = vm::toggleCuisine,
    labelFor = { it.replaceFirstChar { c -> c.uppercase() }.replace("-", " ") },
  )
}

@Composable
private fun DietAllergenStep(s: OnboardingState, vm: OnboardingViewModel) {
  Text("Any dietary needs?", style = MaterialTheme.typography.headlineMedium,
    fontWeight = FontWeight.Medium, color = Ink)
  Spacer(Modifier.height(4.dp))
  Text("Pick one diet and any allergens. We'll hide recipes that clash.",
    style = MaterialTheme.typography.bodyMedium, color = InkMuted)
  Spacer(Modifier.height(16.dp))

  Text("Diet", style = MaterialTheme.typography.labelLarge, color = InkSoft)
  Spacer(Modifier.height(6.dp))
  ChipGrid(
    options = DIET_CHOICES.map { it.first },
    selected = setOf(s.diet),
    onToggle = { vm.setDiet(it) },
    labelFor = { key -> DIET_CHOICES.firstOrNull { it.first == key }?.second ?: key },
  )

  Spacer(Modifier.height(20.dp))
  Text("Allergens", style = MaterialTheme.typography.labelLarge, color = InkSoft)
  Spacer(Modifier.height(6.dp))
  ChipGrid(
    options = ALLERGEN_CHOICES,
    selected = s.allergens,
    onToggle = vm::toggleAllergen,
    labelFor = { it.replaceFirstChar { c -> c.uppercase() } },
  )

  Spacer(Modifier.height(20.dp))
  Text("Ingredients to avoid", style = MaterialTheme.typography.labelLarge, color = InkSoft)
  Text("Separate with commas — e.g. cilantro, olives, blue cheese",
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
}

@Composable
private fun HeatAdventureStep(s: OnboardingState, vm: OnboardingViewModel) {
  Text("Last thing — your palate", style = MaterialTheme.typography.headlineMedium,
    fontWeight = FontWeight.Medium, color = Ink)
  Spacer(Modifier.height(4.dp))
  Text("You can change these anytime in Settings.",
    style = MaterialTheme.typography.bodyMedium, color = InkMuted)
  Spacer(Modifier.height(20.dp))

  Text("Heat tolerance", style = MaterialTheme.typography.labelLarge, color = InkSoft)
  Text(
    when (s.heat) { 0 -> "None — keep it mild"; 1 -> "A little warmth"; 2 -> "Bring the heat"; else -> "Fiery" },
    style = MaterialTheme.typography.bodyMedium, color = Ink,
  )
  Slider(
    value = s.heat.toFloat(),
    onValueChange = { vm.setHeat(it.toInt()) },
    valueRange = 0f..3f,
    steps = 2,
    colors = SliderDefaults.colors(thumbColor = Terracotta, activeTrackColor = Terracotta),
  )

  Spacer(Modifier.height(20.dp))
  Text("Adventure level", style = MaterialTheme.typography.labelLarge, color = InkSoft)
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
          containerColor = Paper2,
          selectedContainerColor = Ink,
          labelColor = Ink,
          selectedLabelColor = Paper,
        ),
      )
    }
  }
}

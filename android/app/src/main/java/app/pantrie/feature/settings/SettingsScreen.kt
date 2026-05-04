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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.pantrie.R
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
// FDA Top 9 plus mustard, sulfites, corn, celery, gluten, molluscs.
// Mirrors ALLERGEN_KEYWORDS in RecipeDetailScreen — every entry here has a
// keyword expansion in that table so the recipe-detail banner can detect it.
private val ALLERGEN_CHOICES = listOf(
  "peanuts", "tree nuts", "shellfish", "molluscs", "fish",
  "eggs", "dairy", "soy", "wheat", "gluten",
  "sesame", "mustard", "sulfites", "corn", "celery",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  onRedoOnboarding: () -> Unit,
  onOpenSubmissions: () -> Unit = {},
  onOpenImportLinks: () -> Unit = {},
  onOpenPriceDemo: () -> Unit = {},
  vm: SettingsViewModel = hiltViewModel(),
) {
  val s by vm.state.collectAsState()
  // Walkthrough VM — used by the cocktails / add-recipe mini-tours so they can spotlight
  // the Mixology toggle row + the My submissions button respectively.
  val tourVm: app.pantrie.feature.walkthrough.WalkthroughViewModel = hiltViewModel()
  LaunchedEffect(s.savedFlash) {
    if (s.savedFlash) { delay(1400); vm.clearSavedFlash() }
  }

  Scaffold(
    containerColor = Paper,
    topBar = {
      TopAppBar(
        title = { Text("Settings", fontWeight = FontWeight.Medium) },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
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

      // ============= YOU — taste profile card =============
      SectionHeader(eyebrow = "01", title = "You")
      Spacer(Modifier.height(14.dp))
      TasteProfileCard(s.taste)

      Spacer(Modifier.height(36.dp))

      // ============= PALATE — what you like =============
      SectionHeader(eyebrow = "02", title = "Palate")
      Spacer(Modifier.height(18.dp))

      Text("Favorite cuisines", style = MaterialTheme.typography.labelLarge, color = InkSoft, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      ChipGrid(
        options = CUISINE_CHOICES,
        selected = s.cuisines,
        onToggle = vm::toggleCuisine,
        labelFor = { it.replaceFirstChar { c -> c.uppercase() }.replace("-", " ") },
      )

      Spacer(Modifier.height(20.dp))
      Text("Diet", style = MaterialTheme.typography.labelLarge, color = InkSoft, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      ChipGrid(
        options = DIET_CHOICES.map { it.first },
        selected = setOf(s.diet),
        onToggle = { vm.setDiet(it) },
        labelFor = { key -> DIET_CHOICES.firstOrNull { it.first == key }?.second ?: key },
      )

      Spacer(Modifier.height(20.dp))
      Text("Allergens", style = MaterialTheme.typography.labelLarge, color = InkSoft, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      ChipGrid(
        options = ALLERGEN_CHOICES,
        selected = s.allergens,
        onToggle = vm::toggleAllergen,
        labelFor = { it.replaceFirstChar { c -> c.uppercase() } },
      )

      Spacer(Modifier.height(20.dp))
      Text("Ingredients to avoid", style = MaterialTheme.typography.labelLarge, color = InkSoft, fontWeight = FontWeight.SemiBold)
      Text("Separate with commas, e.g. cilantro, olives",
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

      Spacer(Modifier.height(36.dp))

      // ============= SENSITIVITY — sliders =============
      SectionHeader(eyebrow = "03", title = "Sensitivity")
      Spacer(Modifier.height(18.dp))

      Text("Heat tolerance", style = MaterialTheme.typography.labelLarge, color = InkSoft, fontWeight = FontWeight.SemiBold)
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
      Text("Adventure", style = MaterialTheme.typography.labelLarge, color = InkSoft, fontWeight = FontWeight.SemiBold)
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

      Spacer(Modifier.height(36.dp))

      // ============= FEATURES — toggles =============
      SectionHeader(eyebrow = "04", title = "Features")
      Spacer(Modifier.height(18.dp))

      val mixOn by vm.showMixology.collectAsState()
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Paper2,
        modifier = Modifier
          .fillMaxWidth()
          .onGloballyPositioned { coords ->
            tourVm.reportAnchor(
              app.pantrie.feature.walkthrough.TourAnchors.SETTINGS_MIXOLOGY_TOGGLE,
              coords.boundsInWindow(),
            )
          },
      ) {
        Row(
          Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(
              "Mixology (cocktails)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = Ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
              "Reveals the cocktail deck. 21+ only.",
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

      Spacer(Modifier.height(8.dp))
      val libraryAnim by vm.libraryAnimEnabled.collectAsState()
      FeatureToggleRow(
        title = "Save → Library animation",
        subtitle = "Brass capsule sweeps to the Library icon when you save.",
        on = libraryAnim,
        onChange = vm::setLibraryAnimEnabled,
      )

      Spacer(Modifier.height(8.dp))
      val fallAnim by vm.ingredientFallEnabled.collectAsState()
      FeatureToggleRow(
        title = "Missing ingredients fall to cart",
        subtitle = "Each missing ingredient cascades down to Shop after a save.",
        on = fallAnim,
        onChange = vm::setIngredientFallEnabled,
      )

      Spacer(Modifier.height(36.dp))

      // ============= ACCOUNT — submissions, imports, walkthroughs =============
      SectionHeader(eyebrow = "05", title = "Account")
      Spacer(Modifier.height(18.dp))

      OutlinedButton(
        onClick = onOpenSubmissions,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          // Mini-tour: add-recipe step 2 spotlights this button.
          .onGloballyPositioned { coords ->
            tourVm.reportAnchor(
              app.pantrie.feature.walkthrough.TourAnchors.SETTINGS_SUBMISSIONS_BUTTON,
              coords.boundsInWindow(),
            )
          },
        shape = RoundedCornerShape(4.dp),
      ) { Text("My submissions", color = Ink) }

      Spacer(Modifier.height(12.dp))
      // Pro-gated: paste TikTok / YouTube cooking links → AI extracts the recipe.
      // Backend rejects with 402 if the user isn't Pro; the screen surfaces the
      // upsell. Lives in Settings (not the deck) so it's discoverable without
      // cluttering the home screen for free users.
      OutlinedButton(
        onClick = onOpenImportLinks,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(4.dp),
      ) { Text("Import recipes from links · Pro", color = Ink) }

      Spacer(Modifier.height(12.dp))
      // Re-runs the first-launch guided walkthrough. Wipes the persisted tour_completed
      // flag — the WalkthroughViewModel observes that change and re-opens the overlay
      // immediately on top of whatever screen the user is currently on.
      OutlinedButton(
        onClick = vm::replayTour,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(4.dp),
      ) { Text("Show app tour again", color = Ink) }

      Spacer(Modifier.height(12.dp))
      OutlinedButton(
        onClick = { vm.redoOnboarding(onRedoOnboarding) },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(4.dp),
      ) { Text("Redo onboarding", color = Ink) }

      // ============= PRIVACY & LEGAL =============
      // Play Store policy (effective 2024) requires an in-app deletion entry point
      // for any account-creating app, plus an accessible privacy policy. These
      // links open the canonical pages on speakeater.com via the system browser.
      // Intentionally below "Account" so the audit trail is obvious.
      Spacer(Modifier.height(36.dp))
      SectionHeader(eyebrow = "06", title = "Privacy & legal")
      Spacer(Modifier.height(18.dp))

      val ctx = androidx.compose.ui.platform.LocalContext.current
      val openUrl: (String) -> Unit = { url ->
        runCatching {
          ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
              .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
          )
        }
      }

      OutlinedButton(
        onClick = { openUrl("https://speakeater.com/privacy.html") },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(4.dp),
      ) { Text(stringResource(R.string.settings_privacy_policy), color = Ink) }

      Spacer(Modifier.height(12.dp))
      OutlinedButton(
        onClick = { openUrl("https://speakeater.com/terms.html") },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(4.dp),
      ) { Text(stringResource(R.string.settings_terms_of_service), color = Ink) }

      Spacer(Modifier.height(12.dp))
      // Play Store-mandated in-app account-deletion entry point.
      // Opens the web deletion flow at /delete-account.html so the user gets the same
      // 30-day-purge guarantee the privacy policy promises.
      OutlinedButton(
        onClick = { openUrl("https://speakeater.com/delete-account.html") },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(4.dp),
      ) { Text(stringResource(R.string.settings_delete_account), color = Terracotta) }

      Text(
        stringResource(R.string.settings_delete_account_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = InkMuted,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, end = 4.dp),
      )

      // Debug-only block — never ships in release builds.
      if (app.pantrie.BuildConfig.DEBUG) {
        Spacer(Modifier.height(36.dp))
        SectionHeader(eyebrow = "07", title = "Internal")
        Spacer(Modifier.height(18.dp))
        OutlinedButton(
          onClick = onOpenPriceDemo,
          modifier = Modifier.fillMaxWidth().height(48.dp),
          shape = RoundedCornerShape(4.dp),
        ) { Text("Price comparison pitch demo", color = Terracotta) }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
          onClick = { vm.resetSwipeQuotaForTesting() },
          modifier = Modifier.fillMaxWidth().height(48.dp),
          shape = RoundedCornerShape(4.dp),
        ) { Text("[DEBUG] Reset today's swipe count", color = Terracotta) }

        // Debug-only Pro tier toggle — flips the user's entitlement via the dev-gated
        // server endpoint so we can test Pro-vs-free flows without going through real
        // Play Billing. Server endpoint returns 404 in production builds.
        Spacer(Modifier.height(12.dp))
        val isPro by vm.isPro.collectAsState()
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Paper2,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(Modifier.weight(1f)) {
              Text(
                "[DEBUG] Speakeater Pro tier",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Terracotta,
              )
              Text(
                "Toggle to test Pro-gated flows. Server-backed.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
              )
            }
            Switch(
              checked = isPro,
              onCheckedChange = vm::debugTogglePro,
              colors = SwitchDefaults.colors(
                checkedThumbColor = Terracotta,
                checkedTrackColor = Terracotta.copy(alpha = 0.4f),
              ),
            )
          }
        }
      }

      Spacer(Modifier.height(40.dp))
    }
  }
}

/** Brand-on header treatment: terracotta eyebrow caps + serif display title +
 *  thin terracotta rule. Reads as an editorial divider, not a Material card. */
@Composable
private fun SectionHeader(eyebrow: String, title: String) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      eyebrow.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = Terracotta,
      letterSpacing = 2.sp,
    )
    Spacer(Modifier.height(4.dp))
    Text(
      title,
      style = MaterialTheme.typography.titleLarge,
      fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
      fontWeight = FontWeight.Medium,
      color = Ink,
    )
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(
      modifier = Modifier.width(40.dp),
      thickness = 2.dp,
      color = Terracotta,
    )
  }
}

/** Backwards-compat shim for existing one-arg callers; eyebrow inferred from title. */
@Composable
private fun SectionHeader(title: String) {
  Text(title, style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.SemiBold, color = Ink)
}

@Composable
private fun TasteProfileCard(t: TasteProfile?) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = Paper2,
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
          containerColor = Paper2,
          selectedContainerColor = Ink,
          labelColor = Ink,
          selectedLabelColor = Paper,
        ),
      )
    }
  }
}


/** Reusable on/off row for the Features section. Same shape as the Mixology
 *  row above; encapsulated here so future feature toggles drop in cleanly. */
@Composable
private fun FeatureToggleRow(
  title: String,
  subtitle: String,
  on: Boolean,
  onChange: (Boolean) -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = Paper2,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(
          title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = Ink,
        )
        Spacer(Modifier.height(2.dp))
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = InkMuted,
        )
      }
      Switch(
        checked = on,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
          checkedThumbColor = Terracotta,
          checkedTrackColor = Terracotta.copy(alpha = 0.4f),
        ),
      )
    }
  }
}

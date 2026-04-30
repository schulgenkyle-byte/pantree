@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantrie.feature.pricedemo

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantrie.ui.theme.*
import kotlin.math.abs

/**
 * INTERNAL DEMO SCREEN for the price-comparison + place-order pitch.
 *
 * NOT shipped to users. Wired only in debug builds via a Settings entry. Generates
 * deterministic fake prices per ingredient so screenshots are stable across runs.
 *
 * Use case: take screenshots, show partners (Amazon Associates, Walmart Marketplace,
 * Instacart Connect) what the integrated experience will look like once contracts
 * are signed. Vision: ingredient row with fuzzy-matched image on the left, three
 * vendor prices on the right, sticky "Place order" footer that batch-deploys the
 * full list to whichever vendor the user picks.
 */

private data class MockIngredient(
  val name: String,
  val unit: String,
  val initial: Char,
  val tint: Color,
  val amazonCents: Int,
  val walmartCents: Int,
  val instacartCents: Int,
)

private val DEMO_TINTS = listOf(
  Color(0xFFB5634A),  // terracotta
  Color(0xFF7A8664),  // olive
  Color(0xFFC9A554),  // brass
  Color(0xFFD4B068),  // brass-bright
  Color(0xFFA1745F),  // copper
  Color(0xFF6E7E5C),  // sage
)

/** Deterministic per-name fake prices. Screenshots stay stable across launches. */
private fun mockFromName(name: String): MockIngredient {
  val seed = abs(name.hashCode())
  val basePrice = 200 + (seed % 800)  // $2.00 to $9.99
  val amazon = basePrice
  val walmart = basePrice - (seed % 80) - 20  // walmart slightly cheaper
  val instacart = basePrice + (seed % 130) + 30  // instacart with markup + service
  val unitOptions = listOf("each", "lb", "12 oz", "16 oz", "1 dozen", "1 bunch")
  val unit = unitOptions[seed % unitOptions.size]
  val tint = DEMO_TINTS[seed % DEMO_TINTS.size]
  val initial = name.firstOrNull { it.isLetter() }?.uppercaseChar() ?: '?'
  return MockIngredient(name, unit, initial, tint, amazon, walmart, instacart)
}

private val DEMO_INGREDIENTS: List<MockIngredient> = listOf(
  "Boneless chicken thighs", "Yellow onion", "Fresh garlic", "Olive oil",
  "Heavy cream", "Parmesan cheese", "Linguine pasta", "Roma tomatoes",
  "Fresh basil", "Crushed red pepper", "Kosher salt", "Lemons",
).map(::mockFromName)

private fun centsToDollars(cents: Int): String =
  "$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

@Composable
fun PriceComparisonMockScreen(onBack: () -> Unit) {
  val totalAmazon = DEMO_INGREDIENTS.sumOf { it.amazonCents }
  val totalWalmart = DEMO_INGREDIENTS.sumOf { it.walmartCents }
  val totalInstacart = DEMO_INGREDIENTS.sumOf { it.instacartCents }

  Scaffold(
    containerColor = Paper,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              "Compare and order",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
              color = Ink,
            )
            Text(
              "Mockup for partner pitch · fake prices",
              style = MaterialTheme.typography.bodySmall,
              color = InkMuted,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
      )
    },
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {

      // Vendor header strip — three columns labeled with the vendor name. Real
      // build replaces these with logo SVGs (Amazon smile, Walmart spark,
      // Instacart carrot). Text-only for the mock keeps it license-clean.
      Row(
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 12.dp)
          .background(Paper2, RoundedCornerShape(8.dp))
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Spacer(Modifier.width(56.dp))            // image gutter
        Spacer(Modifier.weight(1f))              // name column
        VendorHeader("amazon", Modifier.weight(0.7f))
        VendorHeader("walmart", Modifier.weight(0.7f))
        VendorHeader("instacart", Modifier.weight(0.7f))
      }

      LazyColumn(modifier = Modifier.weight(1f)) {
        items(DEMO_INGREDIENTS) { ing -> IngredientRow(ing) }
      }

      // Sticky footer: three vendor totals + Place order CTAs.
      Surface(
        color = Paper2,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
          Text(
            "TOTAL FOR ALL ${DEMO_INGREDIENTS.size} ITEMS",
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold,
          )
          Spacer(Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrderButton("amazon", totalAmazon, Modifier.weight(1f))
            OrderButton("walmart", totalWalmart, Modifier.weight(1f))
            OrderButton("instacart", totalInstacart, Modifier.weight(1f))
          }
          Spacer(Modifier.height(10.dp))
          Text(
            "One tap deploys the full list to the chosen vendor.",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}

@Composable
private fun VendorHeader(name: String, modifier: Modifier = Modifier) {
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Text(
      name,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = BrassBright,
      letterSpacing = 1.2.sp,
    )
  }
}

@Composable
private fun IngredientRow(ing: MockIngredient) {
  // Find the cheapest vendor for this row to highlight the price.
  val cheapest = listOf(ing.amazonCents, ing.walmartCents, ing.instacartCents).min()

  Surface(
    color = Paper,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp),
  ) {
    Row(
      Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Ingredient image stand-in — a brass-tinted square with the first letter.
      // Real build will pull a fuzzy-matched product image via vendor API.
      Box(
        Modifier
          .size(48.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(ing.tint.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          ing.initial.toString(),
          style = MaterialTheme.typography.titleLarge,
          color = ing.tint,
          fontWeight = FontWeight.Bold,
        )
      }

      Spacer(Modifier.width(12.dp))

      Column(Modifier.weight(1f)) {
        Text(
          ing.name,
          style = MaterialTheme.typography.titleSmall,
          color = Ink,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
        )
        Text(
          ing.unit,
          style = MaterialTheme.typography.bodySmall,
          color = InkMuted,
        )
      }

      PriceChip(ing.amazonCents, isCheapest = ing.amazonCents == cheapest, modifier = Modifier.weight(0.7f))
      PriceChip(ing.walmartCents, isCheapest = ing.walmartCents == cheapest, modifier = Modifier.weight(0.7f))
      PriceChip(ing.instacartCents, isCheapest = ing.instacartCents == cheapest, modifier = Modifier.weight(0.7f))
    }
    HorizontalDivider(color = Rule)
  }
}

@Composable
private fun PriceChip(cents: Int, isCheapest: Boolean, modifier: Modifier = Modifier) {
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    if (isCheapest) {
      Surface(
        shape = RoundedCornerShape(6.dp),
        color = BrassBright,
      ) {
        Text(
          centsToDollars(cents),
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = Ink,
        )
      }
    } else {
      Text(
        centsToDollars(cents),
        style = MaterialTheme.typography.labelLarge,
        color = InkSoft,
      )
    }
  }
}

@Composable
private fun OrderButton(vendor: String, totalCents: Int, modifier: Modifier = Modifier) {
  Button(
    onClick = { /* mockup — no-op */ },
    modifier = modifier.height(54.dp),
    colors = ButtonDefaults.buttonColors(containerColor = Ink),
    shape = RoundedCornerShape(6.dp),
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        vendor,
        style = MaterialTheme.typography.labelSmall,
        color = BrassBright,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
      )
      Text(
        centsToDollars(totalCents),
        style = MaterialTheme.typography.titleMedium,
        color = Paper,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

package app.pantrie.feature.recipe

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pantrie.network.dto.RecipeNutrition

@Composable
fun NutritionRow(n: RecipeNutrition, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    NutritionPill(value = "${n.calories}", label = "kcal")
    NutritionPill(value = "${"%.0f".format(n.proteinG)}g", label = "protein")
    NutritionPill(value = "${"%.0f".format(n.carbsG)}g", label = "carbs")
    NutritionPill(value = "${"%.0f".format(n.fatG)}g", label = "fat")
  }
}

@Composable
private fun NutritionPill(value: String, label: String) {
  Column {
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(label, style = MaterialTheme.typography.labelSmall)
  }
}

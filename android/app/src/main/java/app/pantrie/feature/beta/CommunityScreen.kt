package app.pantrie.feature.beta

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.CommunityReview
import app.pantrie.network.dto.TrendingItem
import app.pantrie.ui.theme.*
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommunityViewModel @Inject constructor(
  private val api: PantrieApi,
) : ViewModel() {
  private val _reviews = MutableStateFlow<List<CommunityReview>>(emptyList())
  val reviews = _reviews.asStateFlow()
  private val _trending = MutableStateFlow<List<TrendingItem>>(emptyList())
  val trending = _trending.asStateFlow()
  private val _loading = MutableStateFlow(true)
  val loading = _loading.asStateFlow()

  init { refresh() }

  fun refresh() {
    viewModelScope.launch {
      _loading.value = true
      runCatching { api.communityReviews() }.onSuccess { _reviews.value = it.reviews }
      runCatching { api.betaActivity() }.onSuccess { _trending.value = it.trending }
      _loading.value = false
    }
  }
}

@Composable
fun CommunityScreen(
  onOpenRecipe: (String) -> Unit,
  vm: CommunityViewModel = hiltViewModel(),
) {
  val reviews by vm.reviews.collectAsState()
  val trending by vm.trending.collectAsState()
  val loading by vm.loading.collectAsState()

  Scaffold(containerColor = Cream) { padding ->
    LazyColumn(
      Modifier.padding(padding).fillMaxSize(),
      contentPadding = PaddingValues(bottom = 24.dp),
    ) {
      item {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
          Text("Community", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Normal)
          Text(
            "What other pan-tree cooks are making — real reviews, real photos.",
            style = MaterialTheme.typography.bodyMedium, color = InkMuted,
          )
        }
      }

      if (trending.isNotEmpty()) {
        item {
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Outlined.Whatshot, null, tint = Terracotta, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Trending this week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          }
        }
        items(trending, key = { "t-${it.recipeId}" }) { t ->
          TrendingRow(t = t, onOpen = { onOpenRecipe(t.recipeId) })
        }
        item { Spacer(Modifier.height(8.dp)) }
      }

      item {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Outlined.Star, null, tint = Color(0xFFD4A017), modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Reviews", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
      }

      if (loading && reviews.isEmpty()) {
        item {
          Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
          }
        }
      } else if (reviews.isEmpty()) {
        item {
          Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
              "No reviews yet — cook something tonight and be the first.",
              style = MaterialTheme.typography.bodyMedium, color = InkMuted,
            )
          }
        }
      } else {
        items(reviews, key = { it.id }) { rv ->
          ReviewCard(rv = rv, onOpen = { onOpenRecipe(rv.recipeId) })
        }
      }
    }
  }
}

@Composable
private fun TrendingRow(t: TrendingItem, onOpen: () -> Unit) {
  Surface(
    onClick = onOpen,
    shape = RoundedCornerShape(8.dp),
    color = Terracotta.copy(alpha = 0.06f),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
  ) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(t.title, style = MaterialTheme.typography.titleMedium, color = Ink)
        Text(
          "${t.cooks} cook${if (t.cooks == 1) "" else "s"} this week" +
            (t.cuisine?.let { " · $it" } ?: ""),
          style = MaterialTheme.typography.bodySmall, color = Terracotta, fontWeight = FontWeight.Medium,
        )
      }
    }
  }
}

@Composable
private fun ReviewCard(rv: CommunityReview, onOpen: () -> Unit) {
  Card(
    onClick = onOpen,
    colors = CardDefaults.cardColors(containerColor = Paper),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
  ) {
    Column(Modifier.fillMaxWidth()) {
      // Header: reviewer + rating
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = androidx.compose.foundation.shape.CircleShape,
          color = Olive.copy(alpha = 0.2f),
          modifier = Modifier.size(30.dp),
        ) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
              rv.author.firstOrNull()?.uppercase() ?: "?",
              fontWeight = FontWeight.SemiBold, color = Olive,
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
          Text(
            rv.author + if (rv.isOwn) " · you" else "",
            style = MaterialTheme.typography.labelLarge, color = Ink, fontWeight = FontWeight.Medium,
          )
          Text(timeAgo(rv.createdAt), style = MaterialTheme.typography.labelSmall, color = InkMuted)
        }
        Row {
          repeat(5) { i ->
            Icon(
              Icons.Outlined.Star, null,
              tint = if (i < rv.ratingPots) Color(0xFFD4A017) else InkFaint,
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }

      // Review photo (if any)
      if (!rv.photoUrl.isNullOrBlank()) {
        AsyncImage(
          model = rv.photoUrl, contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxWidth().height(200.dp),
        )
      }

      // Body
      if (!rv.notes.isNullOrBlank()) {
        Text(
          rv.notes,
          style = MaterialTheme.typography.bodyMedium,
          color = Ink,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          maxLines = 6,
        )
      }

      // Recipe ref
      Surface(
        onClick = onOpen,
        color = Cream,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (!rv.recipeImage.isNullOrBlank()) {
            AsyncImage(
              model = rv.recipeImage, contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(10.dp))
          }
          Column(Modifier.weight(1f)) {
            Text(
              rv.recipeTitle,
              style = MaterialTheme.typography.labelLarge, color = Ink, maxLines = 1, fontWeight = FontWeight.Medium,
            )
            rv.recipeCuisine?.takeIf { it.isNotBlank() }?.let {
              Text(it, style = MaterialTheme.typography.labelSmall, color = InkMuted)
            }
          }
          Text("→", color = InkMuted)
        }
      }
    }
  }
}

private fun timeAgo(at: Long): String {
  val now = System.currentTimeMillis()
  val mins = ((now - at) / 60_000).coerceAtLeast(0)
  return when {
    mins < 1 -> "just now"
    mins < 60 -> "${mins}m ago"
    mins < 60 * 24 -> "${mins / 60}h ago"
    else -> "${mins / (60 * 24)}d ago"
  }
}

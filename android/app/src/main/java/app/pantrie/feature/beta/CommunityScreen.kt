package app.pantrie.feature.beta

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MoreVert
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
  private val _toast = MutableStateFlow<String?>(null)
  val toast = _toast.asStateFlow()

  init { refresh() }

  fun refresh() {
    viewModelScope.launch {
      _loading.value = true
      runCatching { api.communityReviews() }.onSuccess { _reviews.value = it.reviews }
      runCatching { api.betaActivity() }.onSuccess { _trending.value = it.trending }
      _loading.value = false
    }
  }

  /** Submit a moderation report. Server-side handler hides the review for the reporter and
   * flags it for admin review. We optimistically remove it from this user's feed too. */
  fun reportReview(reviewId: String, reason: String) {
    viewModelScope.launch {
      runCatching { api.reportReview(reviewId, mapOf("reason" to reason)) }
        .onSuccess {
          _reviews.value = _reviews.value.filter { it.id != reviewId }
          _toast.value = "Thanks — review reported. Our team will check it."
        }
        .onFailure { _toast.value = "Couldn't send report — try again later." }
    }
  }

  fun clearToast() { _toast.value = null }
}

@Composable
fun CommunityScreen(
  onOpenRecipe: (String) -> Unit,
  vm: CommunityViewModel = hiltViewModel(),
) {
  val reviews by vm.reviews.collectAsState()
  val trending by vm.trending.collectAsState()
  val loading by vm.loading.collectAsState()
  val toast by vm.toast.collectAsState()

  Scaffold(containerColor = Paper) { padding ->
    LazyColumn(
      Modifier.padding(padding).fillMaxSize(),
      contentPadding = PaddingValues(bottom = 24.dp),
    ) {
      item {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
          Text("Community", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Normal)
          Text(
            "What other ${app.pantrie.Brand.APP_NAME} cooks are making — real reviews, real photos.",
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
          Icon(Icons.Outlined.Star, null, tint = BrassBright, modifier = Modifier.size(18.dp))
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
          ReviewCard(
            rv = rv,
            onOpen = { onOpenRecipe(rv.recipeId) },
            onReport = { reason -> vm.reportReview(rv.id, reason) },
          )
        }
      }
    }

    // Lightweight toast — same pattern as Mixology / Deck. Auto-dismisses after 2.5s.
    toast?.let { msg ->
      LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); vm.clearToast() }
      Box(Modifier.padding(padding).fillMaxSize().padding(bottom = 90.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Ink,
          modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        ) {
          Text(msg, color = Paper, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
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
private fun ReviewCard(
  rv: CommunityReview,
  onOpen: () -> Unit,
  onReport: (String) -> Unit,
) {
  // Kebab-menu state for the per-card moderation overflow.
  var menuOpen by remember { mutableStateOf(false) }
  var reportSheetOpen by remember { mutableStateOf(false) }

  Card(
    onClick = onOpen,
    colors = CardDefaults.cardColors(containerColor = Paper2),
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
              tint = if (i < rv.ratingPots) BrassBright else InkFaint,
              modifier = Modifier.size(14.dp),
            )
          }
        }
        // Per-card overflow — only on other people's reviews. Hide on your own.
        if (!rv.isOwn) {
          Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
              Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = InkMuted, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
              DropdownMenuItem(
                text = { Text("Report content") },
                onClick = { menuOpen = false; reportSheetOpen = true },
              )
            }
          }
        }
      }

      if (reportSheetOpen) {
        ReportReasonSheet(
          onPick = { reason ->
            reportSheetOpen = false
            onReport(reason)
          },
          onDismiss = { reportSheetOpen = false },
        )
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
        color = Paper2,
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
          Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = InkMuted,
            modifier = Modifier.size(18.dp),
          )
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

/** Reasons map to backend `reason` field. Server stores them verbatim for admin triage;
 * the names below are also what surfaces on the moderator dashboard. */
private val REPORT_REASONS = listOf(
  "spam" to "Spam or scam",
  "inappropriate" to "Inappropriate / offensive",
  "off_topic" to "Off-topic for this recipe",
  "wrong_info" to "Dangerous or wrong info",
  "other" to "Something else",
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReportReasonSheet(
  onPick: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = Paper,
    dragHandle = { BottomSheetDefaults.DragHandle(color = InkMuted) },
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
      Text(
        "Report this review",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = Ink,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        "Tell us what's wrong. We'll review and take it down if it breaks our rules.",
        style = MaterialTheme.typography.bodySmall,
        color = InkMuted,
      )
      Spacer(Modifier.height(16.dp))
      REPORT_REASONS.forEach { (key, label) ->
        Surface(
          onClick = { onPick(key) },
          shape = RoundedCornerShape(10.dp),
          color = Paper2,
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
          Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
          )
        }
      }
      Spacer(Modifier.height(8.dp))
      TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text("Cancel", color = InkMuted)
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

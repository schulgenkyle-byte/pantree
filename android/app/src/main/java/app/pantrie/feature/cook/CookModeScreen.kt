@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantrie.feature.cook

import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.InteractRequest
import app.pantrie.network.dto.Recipe
import app.pantrie.network.dto.Step
import app.pantrie.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CookUiState {
  data object Loading : CookUiState
  data class Running(
    val recipe: Recipe,
    val stepIndex: Int,
    val remainingSec: Int,
    val paused: Boolean,
    val servingScale: Float = 1f,  // 0.5x, 1x, 1.5x, 2x
  ) : CookUiState
  data class Finished(val cookUndoId: String?) : CookUiState
  data class Error(val message: String) : CookUiState
}

@HiltViewModel
class CookViewModel @Inject constructor(
  private val api: PantrieApi,
) : ViewModel() {
  private val _state = MutableStateFlow<CookUiState>(CookUiState.Loading)
  val state = _state.asStateFlow()
  private var recipe: Recipe? = null
  private var timerJob: Job? = null

  fun load(recipeId: String) {
    viewModelScope.launch {
      runCatching { api.recipe(recipeId) }
        .onSuccess { r ->
          recipe = r
          if (r.steps.isEmpty()) {
            _state.value = CookUiState.Error("This recipe has no steps yet.")
          } else {
            _state.value = CookUiState.Running(r, 0, stepTimer(r.steps[0].timerSeconds), paused = false)
            startTimer()
          }
        }
        .onFailure { _state.value = CookUiState.Error(it.message ?: "Couldn't load") }
    }
  }

  /** Give every step a minimum 60s timer so no step gets stuck waiting for a tap. */
  private fun stepTimer(explicit: Int?): Int = explicit?.takeIf { it > 0 } ?: 60

  private fun startTimer() {
    timerJob?.cancel()
    timerJob = viewModelScope.launch {
      while (true) {
        val s = _state.value as? CookUiState.Running ?: return@launch
        if (s.paused) { delay(250); continue }
        if (s.remainingSec <= 0) {
          // Auto-advance
          next()
          return@launch
        }
        delay(1000)
        val cur = _state.value as? CookUiState.Running ?: return@launch
        if (cur.paused) continue
        _state.value = cur.copy(remainingSec = (cur.remainingSec - 1).coerceAtLeast(0))
      }
    }
  }

  fun togglePause() {
    val s = _state.value as? CookUiState.Running ?: return
    _state.value = s.copy(paused = !s.paused)
  }

  fun next() {
    val s = _state.value as? CookUiState.Running ?: return
    val r = s.recipe
    val nextIdx = s.stepIndex + 1
    if (nextIdx >= r.steps.size) {
      finish(r)
      return
    }
    val nextStep = r.steps[nextIdx]
    _state.value = s.copy(stepIndex = nextIdx, remainingSec = stepTimer(nextStep.timerSeconds), paused = false)
    startTimer()
  }

  fun prev() {
    val s = _state.value as? CookUiState.Running ?: return
    val r = s.recipe
    val prevIdx = (s.stepIndex - 1).coerceAtLeast(0)
    val prevStep = r.steps[prevIdx]
    _state.value = s.copy(stepIndex = prevIdx, remainingSec = stepTimer(prevStep.timerSeconds), paused = true)
    timerJob?.cancel()
  }

  private fun finish(r: Recipe) {
    timerJob?.cancel()
    viewModelScope.launch {
      val resp = runCatching { api.interact(InteractRequest(recipeId = r.id, status = "cooked")) }.getOrNull()
      _state.value = CookUiState.Finished(resp?.cookUndoId)
    }
  }

  fun endEarly() {
    val s = _state.value as? CookUiState.Running ?: return
    finish(s.recipe)
  }

  fun setServingScale(scale: Float) {
    val s = _state.value as? CookUiState.Running ?: return
    _state.value = s.copy(servingScale = scale.coerceIn(0.5f, 4f))
  }

  override fun onCleared() {
    timerJob?.cancel()
    super.onCleared()
  }
}

@Composable
fun CookModeScreen(
  recipeId: String,
  onExit: () -> Unit,
  vm: CookViewModel = hiltViewModel(),
) {
  val state by vm.state.collectAsState()

  // Keep screen on while cooking
  val view = LocalView.current
  DisposableEffect(Unit) {
    val win = (view.context as? android.app.Activity)?.window
    win?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    onDispose { win?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
  }

  LaunchedEffect(recipeId) { vm.load(recipeId) }

  Scaffold(containerColor = Cream) { padding ->
    Box(Modifier.padding(padding).fillMaxSize()) {
      when (val s = state) {
        CookUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = Ink)
        }
        is CookUiState.Error -> Column(
          Modifier.fillMaxSize().padding(24.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(s.message, style = MaterialTheme.typography.titleLarge, color = Terracotta)
          Spacer(Modifier.height(16.dp))
          Button(onClick = onExit, colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
            Text("Exit", color = Paper)
          }
        }
        is CookUiState.Finished -> FinishedView(onExit = onExit)
        is CookUiState.Running -> RunningView(
          state = s,
          onExit = onExit,
          onPauseToggle = vm::togglePause,
          onNext = vm::next,
          onPrev = vm::prev,
          onEndEarly = vm::endEarly,
          onServingScale = vm::setServingScale,
        )
      }
    }
  }
}

@Composable
private fun RunningView(
  state: CookUiState.Running,
  onExit: () -> Unit,
  onPauseToggle: () -> Unit,
  onNext: () -> Unit,
  onPrev: () -> Unit,
  onEndEarly: () -> Unit,
  onServingScale: (Float) -> Unit,
) {
  val step = state.recipe.steps[state.stepIndex]
  val total = state.recipe.steps.size
  val progress by animateFloatAsState(
    targetValue = (state.stepIndex + 1f) / total,
    animationSpec = tween(durationMillis = 400),
    label = "progress",
  )

  Column(Modifier.fillMaxSize()) {
    // Top bar — minimal, touchable
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onExit, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Outlined.Close, "Exit", tint = Ink, modifier = Modifier.size(28.dp))
      }
      Spacer(Modifier.weight(1f))
      Text(
        state.recipe.title,
        style = MaterialTheme.typography.titleMedium,
        color = InkMuted, fontWeight = FontWeight.Medium,
        maxLines = 1,
      )
      Spacer(Modifier.weight(1f))
      TextButton(onClick = onEndEarly) { Text("Done", color = Ink, fontWeight = FontWeight.Medium) }
    }

    // Serving-scale selector: tap to scale quantities in step text on the fly.
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Outlined.People, null, tint = InkMuted, modifier = Modifier.size(14.dp))
      Spacer(Modifier.width(6.dp))
      listOf(0.5f, 1f, 1.5f, 2f).forEach { scale ->
        val selected = kotlin.math.abs(state.servingScale - scale) < 0.01f
        TextButton(
          onClick = { onServingScale(scale) },
          colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) Ink else InkMuted,
          ),
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Text(
            "${if (scale == scale.toInt().toFloat()) "${scale.toInt()}x" else "${scale}x"}",
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
          )
        }
      }
    }

    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier.fillMaxWidth().height(4.dp),
      color = Olive,
      trackColor = InkFaint.copy(alpha = 0.3f),
    )

    // Main area — tappable to pause, swipeable to navigate
    Box(
      Modifier
        .weight(1f)
        .fillMaxWidth()
        .pointerInput(state.stepIndex) {
          detectTapGestures(onTap = { onPauseToggle() })
        }
        .pointerInput(state.stepIndex) {
          detectHorizontalDragGestures { _, drag ->
            // Large drag threshold to avoid accidental swipes
            if (drag < -40) { onNext(); return@detectHorizontalDragGestures }
            if (drag > 40) { onPrev(); return@detectHorizontalDragGestures }
          }
        }
        .padding(horizontal = 24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          "Step ${state.stepIndex + 1} of $total",
          style = MaterialTheme.typography.labelLarge,
          color = InkMuted, fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
          text = highlightMeasurements(step.text, state.servingScale),
          style = MaterialTheme.typography.headlineSmall,
          color = Ink,
          fontWeight = FontWeight.Normal,
          textAlign = TextAlign.Center,
          lineHeight = 34.sp,
        )
        if (state.remainingSec > 0 || (step.timerSeconds ?: 0) > 0) {
          Spacer(Modifier.height(32.dp))
          Text(
            formatTimer(state.remainingSec),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Normal,
            color = if (state.paused) InkFaint else Ink,
            fontSize = 72.sp,
          )
          Text(
            if (state.paused) "paused" else "remaining",
            style = MaterialTheme.typography.labelMedium,
            color = InkMuted,
          )
        }
        Spacer(Modifier.height(20.dp))
        Text(
          "Tap to ${if (state.paused) "resume" else "pause"} · swipe to move",
          style = MaterialTheme.typography.labelSmall, color = InkFaint,
        )
      }
    }

    // Big bottom action bar — greasy-hand-friendly
    Row(
      Modifier.fillMaxWidth().padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FloatingActionButton(
        onClick = onPrev,
        containerColor = Paper,
        contentColor = Ink,
        modifier = Modifier.size(64.dp),
      ) { Icon(Icons.Outlined.SkipPrevious, "Previous", modifier = Modifier.size(32.dp)) }

      FloatingActionButton(
        onClick = onPauseToggle,
        containerColor = Ink,
        contentColor = Paper,
        modifier = Modifier.weight(1f).height(80.dp),
      ) {
        Icon(
          if (state.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
          if (state.paused) "Resume" else "Pause",
          modifier = Modifier.size(40.dp),
        )
      }

      FloatingActionButton(
        onClick = onNext,
        containerColor = Paper,
        contentColor = Ink,
        modifier = Modifier.size(64.dp),
      ) { Icon(Icons.Outlined.SkipNext, "Next", modifier = Modifier.size(32.dp)) }
    }
  }
}

@Composable
private fun FinishedView(onExit: () -> Unit) {
  Column(
    Modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text("Enjoy your meal.", style = MaterialTheme.typography.displayMedium, color = Ink, fontWeight = FontWeight.Normal)
    Spacer(Modifier.height(8.dp))
    Text(
      "Pantry updated. Ingredients used were auto-deducted.",
      style = MaterialTheme.typography.bodyLarge, color = InkSoft,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
    Button(
      onClick = onExit,
      modifier = Modifier.fillMaxWidth().height(56.dp),
      colors = ButtonDefaults.buttonColors(containerColor = Ink),
      shape = RoundedCornerShape(4.dp),
    ) { Text("Done", color = Paper, fontWeight = FontWeight.SemiBold) }
  }
}

private fun formatTimer(seconds: Int): String {
  val m = seconds / 60
  val s = seconds % 60
  return "%d:%02d".format(m, s)
}

// Bolds measurements (quantities + units + time) in step text. If servingScale != 1f,
// scales the numeric quantity portion so "2 tbsp oil" at 0.5x shows as "1 tbsp oil".
private val MEASUREMENT_RE = Regex(
  "(\\b\\d+(?:[./]\\d+)?\\s*(?:cups?|tbsp|tablespoons?|tsp|teaspoons?|oz|ounces?|lbs?|pounds?|g|grams?|kg|ml|l|liters?|cloves?|heads?|bunches?|pinch|dash|slices?)\\b" +
    "|\\b\\d+(?:[./]\\d+)?\\s*(?:minute|min|hour|hr|second|sec)s?\\b" +
    "|\\b\\d+(?:[./]\\d+)?\\s*(?:°F|°C|degrees?)" +
    ")",
  RegexOption.IGNORE_CASE,
)
private val NUMBER_RE = Regex("\\d+(?:[./]\\d+)?")

private fun scaleNumber(raw: String, scale: Float, isTempOrTime: Boolean): String {
  if (isTempOrTime) return raw // never scale temperatures or times
  val parsed = when {
    "/" in raw -> {
      val (a, b) = raw.split("/")
      val n = a.toDoubleOrNull() ?: return raw
      val d = b.toDoubleOrNull() ?: return raw
      if (d == 0.0) return raw else n / d
    }
    else -> raw.toDoubleOrNull() ?: return raw
  }
  val scaled = parsed * scale
  // Format: integer if whole, else up to 2 decimals
  return when {
    scaled == scaled.toInt().toDouble() -> scaled.toInt().toString()
    (scaled * 2) == (scaled * 2).toInt().toDouble() -> (scaled.toInt().toString() + when ((scaled * 2).toInt() % 2) { 1 -> ".5"; else -> "" })
    else -> "%.2f".format(scaled).trimEnd('0').trimEnd('.')
  }
}

fun highlightMeasurements(text: String, scale: Float = 1f): AnnotatedString {
  if (text.isBlank()) return AnnotatedString("")
  return buildAnnotatedString {
    var cursor = 0
    for (match in MEASUREMENT_RE.findAll(text)) {
      if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
      val raw = match.value
      val isTempOrTime = Regex("°|degree|minute|min|hour|hr|second|sec", RegexOption.IGNORE_CASE).containsMatchIn(raw)
      val rendered = if (scale != 1f && !isTempOrTime) {
        NUMBER_RE.replace(raw) { scaleNumber(it.value, scale, false) }
      } else raw
      withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(rendered) }
      cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
  }
}

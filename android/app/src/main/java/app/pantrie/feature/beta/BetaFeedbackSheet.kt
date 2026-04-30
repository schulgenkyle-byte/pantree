package app.pantrie.feature.beta

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.BuildConfig
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.BetaFeedbackRequest
import app.pantrie.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BetaFeedbackViewModel @Inject constructor(
  private val api: PantrieApi,
  private val analytics: Analytics,
) : ViewModel() {
  private val _sending = MutableStateFlow(false)
  val sending = _sending.asStateFlow()
  private val _result = MutableStateFlow<String?>(null)
  val result = _result.asStateFlow()

  fun send(kind: String, title: String, body: String, severity: String?, route: String?) {
    if (title.isBlank()) { _result.value = "Add a title first"; return }
    viewModelScope.launch {
      _sending.value = true
      runCatching {
        api.sendFeedback(BetaFeedbackRequest(
          kind = kind,
          title = title.trim().take(120),
          body = body.trim().take(4000).ifBlank { null },
          route = route,
          appVersion = "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}",
          device = analytics.deviceLabel(),
          severity = severity,
        ))
      }.onSuccess {
        analytics.track("beta_feedback_sent", mapOf("kind" to kind))
        _result.value = "Thanks — logged. You can close this."
      }.onFailure {
        _result.value = "Could not send (${it.message?.take(60) ?: "unknown"}). Try again."
      }
      _sending.value = false
    }
  }

  fun clear() { _result.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetaFeedbackSheet(
  currentRoute: String?,
  onDismiss: () -> Unit,
  vm: BetaFeedbackViewModel = hiltViewModel(),
) {
  var kind by remember { mutableStateOf("bug") }
  var title by remember { mutableStateOf("") }
  var body by remember { mutableStateOf("") }
  var severity by remember { mutableStateOf<String?>("med") }
  val sending by vm.sending.collectAsState()
  val result by vm.result.collectAsState()

  // Success swap — once the server confirms, we replace the entire form with a
  // wax-seal stamp animation. Failure messages stay inline with the form so the
  // user can correct + retry without losing what they typed.
  val isSuccess = result?.startsWith("Thanks") == true

  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper) {
    AnimatedContent(
      targetState = isSuccess,
      transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(160)) },
      label = "feedback-confirm",
    ) { success ->
      if (success) {
        WaxSealConfirmation(
          onComplete = {
            vm.clear()
            onDismiss()
          },
        )
      } else {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp).fillMaxWidth()) {
          Text("Beta feedback", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
          Spacer(Modifier.height(4.dp))
          Text(
            "Bug, idea, or praise — all lands instantly with the dev.",
            style = MaterialTheme.typography.bodyMedium, color = InkMuted,
          )
          Spacer(Modifier.height(16.dp))

          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindChip("bug", "Bug", Icons.Outlined.BugReport, Terracotta, kind == "bug") { kind = "bug"; severity = "med" }
            KindChip("idea", "Idea", Icons.Outlined.Lightbulb, Olive, kind == "idea") { kind = "idea"; severity = null }
            KindChip("praise", "Praise", Icons.Outlined.Favorite, Olive, kind == "praise") { kind = "praise"; severity = null }
            KindChip("other", "Other", Icons.Outlined.MoreHoriz, Ink, kind == "other") { kind = "other"; severity = null }
          }

          Spacer(Modifier.height(12.dp))
          OutlinedTextField(
            value = title, onValueChange = { title = it.take(120) },
            placeholder = { Text("Short title") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp),
          )
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(
            value = body, onValueChange = { body = it.take(4000) },
            placeholder = {
              Text(when (kind) {
                "bug" -> "What did you do? What did you expect? What happened?"
                "idea" -> "What would make ${app.pantrie.Brand.APP_NAME} better?"
                "praise" -> "What worked well?"
                else -> "Tell us more…"
              })
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            shape = RoundedCornerShape(4.dp),
          )

          if (kind == "bug") {
            Spacer(Modifier.height(12.dp))
            Text("Severity", style = MaterialTheme.typography.labelMedium, color = InkMuted)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("low" to "Low", "med" to "Medium", "high" to "High", "crash" to "Crash").forEach { (k, lab) ->
                SeverityChip(lab, selected = severity == k) { severity = k }
              }
            }
          }

          Spacer(Modifier.height(12.dp))
          Text(
            "Attached: ${currentRoute ?: "–"} · v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall, color = InkFaint,
          )

          Spacer(Modifier.height(16.dp))
          Button(
            onClick = { vm.send(kind, title, body, severity, currentRoute) },
            enabled = !sending && title.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper),
            shape = RoundedCornerShape(4.dp),
          ) { Text(if (sending) "Sending…" else "Send feedback") }

          // Only error / non-success messages render here — success path is replaced
          // by the wax-seal animation above.
          result?.takeIf { !it.startsWith("Thanks") }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Terracotta)
          }
          Spacer(Modifier.height(12.dp))
        }
      }
    }
  }
}

/**
 * Wax-seal stamp confirmation — brass circle slams down with a slight rotation
 * overshoot, "LOGGED" mono caps fades in below, sheet auto-dismisses after ~1.7s.
 *
 * On-brand for the speakeasy aesthetic: feels like an old-world post stamp pressed
 * into wax, not a confetti popup.
 */
@Composable
private fun WaxSealConfirmation(onComplete: () -> Unit) {
  // 0 = hidden, 1 = seal lands, 2 = text appears, 3 = dismiss
  var phase by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    phase = 1
    delay(450)
    phase = 2
    delay(1300)
    onComplete()
  }

  val sealScale by animateFloatAsState(
    targetValue = if (phase >= 1) 1f else 0.4f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessLow,
    ),
    label = "seal-scale",
  )
  val sealAlpha by animateFloatAsState(
    targetValue = if (phase >= 1) 1f else 0f,
    animationSpec = tween(220, easing = FastOutSlowInEasing),
    label = "seal-alpha",
  )
  val sealRotation by animateFloatAsState(
    targetValue = if (phase >= 1) 0f else -14f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioLowBouncy,
      stiffness = Spring.StiffnessMediumLow,
    ),
    label = "seal-rotation",
  )
  val textAlpha by animateFloatAsState(
    targetValue = if (phase >= 2) 1f else 0f,
    animationSpec = tween(360, delayMillis = 80, easing = FastOutSlowInEasing),
    label = "text-alpha",
  )

  Column(
    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 56.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .size(96.dp)
        .scale(sealScale)
        .rotate(sealRotation)
        .alpha(sealAlpha)
        .background(BrassBright, CircleShape)
        .border(2.dp, BrassBright.copy(alpha = 0.35f), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Outlined.Check,
        contentDescription = null,
        tint = Paper,
        modifier = Modifier.size(44.dp),
      )
    }
    Spacer(Modifier.height(28.dp))
    Text(
      "LOGGED",
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.SemiBold,
      letterSpacing = 4.sp,
      fontSize = 13.sp,
      color = BrassBright,
      modifier = Modifier.alpha(textAlpha),
    )
    Spacer(Modifier.height(10.dp))
    Text(
      "you'll hear back",
      style = MaterialTheme.typography.bodyMedium,
      color = InkSoft,
      textAlign = TextAlign.Center,
      modifier = Modifier.alpha(textAlpha),
    )
    Spacer(Modifier.height(20.dp))
  }
}

@Composable
private fun RowScope.KindChip(
  key: String, label: String, icon: ImageVector, tint: Color,
  selected: Boolean, onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(8.dp),
    color = if (selected) tint.copy(alpha = 0.12f) else Color.Transparent,
    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) tint else InkFaint),
    modifier = Modifier.weight(1f).height(56.dp),
  ) {
    Column(
      Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(icon, null, tint = if (selected) tint else InkMuted, modifier = Modifier.size(18.dp))
      Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Ink else InkMuted)
    }
  }
}

@Composable
private fun SeverityChip(label: String, selected: Boolean, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(20.dp),
    color = if (selected) Terracotta.copy(alpha = 0.12f) else Color.Transparent,
    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Terracotta else InkFaint),
  ) {
    Text(
      label, style = MaterialTheme.typography.labelMedium,
      color = if (selected) Terracotta else InkMuted,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
  }
}

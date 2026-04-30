package app.pantrie.feature.walkthrough

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.max

/**
 * Full-screen guided tour overlay.
 *
 * Three flavors:
 *   - FullSheet (welcome / done cards): centered card, blocks ALL touches behind it,
 *     advances when the user taps the primary button.
 *   - Spotlight: dim scrim with a circular cutout punched out around a real measured UI
 *     element. Click-through everywhere; the user advances by tapping the highlighted
 *     element and the resulting state change trips the step's TourTrigger.
 *   - Hub: branching menu rendered after the main tour. Lists the mini-tours plus a
 *     "Self explore" exit. Mini-tours that have already been completed render with a
 *     subtle brass checkmark next to their button.
 *
 * Backward-compat: if a Spotlight step's anchor key has not been reported (target not
 * mounted), the overlay falls back to a centered FullSheet for that step so the user
 * still sees the copy instead of a misaligned spotlight.
 */
@Composable
internal fun WalkthroughOverlay(
  state: WalkthroughUiState,
  anchors: Map<String, Rect>,
  onNext: () -> Unit,
  onSkip: () -> Unit,
  onEnterMiniTour: (String) -> Unit,
  onSelfExplore: () -> Unit,
) {
  if (!state.visible) return
  val seg = state.segment
  val step = seg.steps.getOrNull(state.stepIndex) ?: return
  val isLastStepInSegment = state.stepIndex == seg.steps.lastIndex
  val isMainTour = seg is MainTour
  val isMiniTour = seg is MiniTour

  // Resolve the measured anchor (if any). If a Spotlight target hasn't reported yet
  // we degrade to a FullSheet so the user still sees the copy.
  val resolvedAnchor: Rect? = step.anchorKey?.let { key ->
    anchors[key]?.takeIf { !it.isEmpty }
  }
  val effectiveKind = when {
    step.kind == TourStepKind.Spotlight && resolvedAnchor == null -> TourStepKind.FullSheet
    else -> step.kind
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .zIndex(100f),
  ) {
    when (effectiveKind) {
      TourStepKind.FullSheet -> {
        // Primary button label varies by context. Welcome step (main tour, idx 0) =
        // "Start tour". Last step of a mini-tour = "Back to menu". Last step of main
        // tour that isn't a Hub (shouldn't happen but safe) = "Done".
        val primaryLabel = when {
          isMainTour && state.stepIndex == 0 -> "Start tour"
          isMiniTour && isLastStepInSegment -> "Back to menu"
          isLastStepInSegment -> "Done"
          else -> "Continue"
        }
        FullSheet(
          title = step.title,
          body = step.body,
          primaryLabel = primaryLabel,
          onPrimary = onNext,
          // Skip is hidden on the very last sheet of the main tour (the Hub IS the main
          // tour's last step now, so we never actually hit this for main). For mini-tour
          // done cards, hide Skip — primary is "Back to menu" which already exits cleanly.
          showSkip = !(isMiniTour && isLastStepInSegment),
          onSkip = onSkip,
        )
      }
      TourStepKind.Spotlight -> SpotlightSheet(
        anchor = resolvedAnchor!!,
        title = step.title,
        body = step.body,
        allowTooltipTap = step.allowTooltipTap,
        onTooltipTap = onNext,
        onSkip = onSkip,
      )
      TourStepKind.Hub -> HubSheet(
        title = step.title,
        body = step.body,
        completedMiniTours = state.completedMiniTours,
        onPickMiniTour = onEnterMiniTour,
        onSelfExplore = onSelfExplore,
      )
    }
  }
}

@Composable
private fun FullSheet(
  title: String,
  body: String,
  primaryLabel: String,
  onPrimary: () -> Unit,
  showSkip: Boolean,
  onSkip: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = 0.85f))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = {},
      ),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp),
    ) {
      Column(Modifier.padding(24.dp)) {
        Text(
          title,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Text(
          body,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(20.dp))
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (showSkip) {
            TextButton(onClick = onSkip) {
              Text(
                "Skip",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
              )
            }
            Spacer(Modifier.width(8.dp))
          }
          Button(
            onClick = onPrimary,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
          ) { Text(primaryLabel) }
        }
      }
    }
  }
}

/**
 * Branching menu sheet. Lists each mini-tour as its own button (with description sub-line
 * and a brass checkmark when completed) plus a Self explore exit. Tapping any mini-tour
 * button delegates to the VM which swaps the active segment and navigates as needed.
 *
 * If every mini-tour has been completed, replaces the menu with a "you've explored
 * everything" wrap card whose Done button finishes the tour.
 */
@Composable
private fun HubSheet(
  title: String,
  body: String,
  completedMiniTours: Set<String>,
  onPickMiniTour: (String) -> Unit,
  onSelfExplore: () -> Unit,
) {
  val all = TourSteps.miniTours
  val allDone = all.isNotEmpty() && all.all { it.id in completedMiniTours }
  val brass = app.pantrie.ui.theme.BrassBright

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = 0.85f))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = {},
      ),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp),
    ) {
      Column(
        Modifier
          .padding(24.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        if (allDone) {
          Text(
            "You've explored everything.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(Modifier.height(12.dp))
          Text(
            "Cook something tonight. You can replay any of this from Settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(Modifier.height(20.dp))
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
              onClick = onSelfExplore,
              shape = RoundedCornerShape(6.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
              ),
            ) { Text("Done") }
          }
          return@Column
        }

        Text(
          title,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
          body,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(16.dp))

        for (mini in all) {
          val done = mini.id in completedMiniTours
          OutlinedButton(
            onClick = { onPickMiniTour(mini.id) },
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            // Default OutlinedButton content padding squashes our two-line content; widen it.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
              horizontal = 16.dp,
              vertical = 12.dp,
            ),
          ) {
            Column(Modifier.weight(1f)) {
              Text(
                mini.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                mini.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
              )
            }
            if (done) {
              Icon(
                Icons.Outlined.Check,
                contentDescription = "Completed",
                tint = brass.copy(alpha = 0.85f),
              )
            }
          }
        }

        Spacer(Modifier.height(12.dp))
        // Self explore — tertiary action, low-emphasis text button. Exits the tour.
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onSelfExplore) {
            Text(
              "Self explore",
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
              fontWeight = FontWeight.Medium,
            )
          }
        }
      }
    }
  }
}

/**
 * Spotlight cutout with a tooltip card on the opposite half of the screen.
 *
 * Click semantics: the dim layer is purely visual. Pointer events flow through the
 * underlying layout tree, so the user can tap the highlighted element and the trigger
 * picks up the resulting state change. The tooltip card itself can be tapped to advance
 * manually when [allowTooltipTap] is true.
 */
@Composable
private fun SpotlightSheet(
  anchor: Rect,
  title: String,
  body: String,
  allowTooltipTap: Boolean,
  onTooltipTap: () -> Unit,
  onSkip: () -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val heightPx = with(density) { maxHeight.toPx() }
    val paddingPx = with(density) { 16.dp.toPx() }

    val centerX = anchor.center.x
    val centerY = anchor.center.y
    val radiusPx = max(anchor.width, anchor.height) / 2f + paddingPx

    val infinite = rememberInfiniteTransition(label = "spotlight-pulse")
    val pulseScale by infinite.animateFloat(
      initialValue = 1.0f,
      targetValue = 1.15f,
      animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
      label = "pulse",
    )

    val brass = app.pantrie.ui.theme.BrassBright

    // Visual dim + spotlight cutout. NO pointerInput — must remain click-transparent
    // so the underlying highlighted element receives taps.
    Box(
      modifier = Modifier
        .matchParentSize()
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
          drawContent()
          drawRect(Color.Black.copy(alpha = 0.85f))
          drawCircle(
            color = Color.Transparent,
            radius = radiusPx,
            center = Offset(centerX, centerY),
            blendMode = BlendMode.Clear,
          )
        },
    )

    // Pulsing brass affordance ring.
    Box(
      modifier = Modifier
        .fillMaxSize()
        .drawWithContent {
          drawContent()
          drawCircle(
            color = brass.copy(alpha = 0.55f),
            radius = radiusPx * pulseScale,
            center = Offset(centerX, centerY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
          )
        },
    )

    val tooltipAlignment =
      if (centerY < heightPx / 2f) Alignment.BottomCenter else Alignment.TopCenter

    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 64.dp),
      contentAlignment = tooltipAlignment,
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = Modifier
          .fillMaxWidth()
          .let { m ->
            if (allowTooltipTap) m.clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = onTooltipTap,
            ) else m
          },
      ) {
        Column(Modifier.padding(20.dp)) {
          Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(Modifier.height(8.dp))
          Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (allowTooltipTap) {
            Spacer(Modifier.height(10.dp))
            Text(
              "or tap here to continue",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
          }
          Spacer(Modifier.height(12.dp))
          Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TextButton(onClick = onSkip) {
              Text(
                "Skip tour",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
              )
            }
          }
        }
      }
    }
  }
}

package app.pantrie.feature.parties

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantrie.ui.theme.BrassBright
import app.pantrie.ui.theme.BrassDeep
import app.pantrie.ui.theme.Ink
import app.pantrie.ui.theme.InkFaint
import app.pantrie.ui.theme.InkSoft
import app.pantrie.ui.theme.Mono
import app.pantrie.ui.theme.Paper
import app.pantrie.ui.theme.Paper2
import app.pantrie.ui.theme.Paper3
import app.pantrie.ui.theme.Rule
import app.pantrie.ui.theme.SerifBody
import app.pantrie.ui.theme.SerifDisplay
import app.pantrie.ui.theme.Terracotta
import kotlinx.coroutines.launch

/**
 * Bottom sheet shown when a locked menu is tapped. Two paths:
 *
 *   Left column   — "Kickstarter backer?" — a code entry field (SPEAK-XXXX-YYYY).
 *   Right column  — "Don't have a code?" — copy explaining the post-launch price,
 *                   and a "Notify me at launch" button.
 *
 * The sheet's voice rules are locked: no banned phrases (powerful, seamless,
 * elevate, unlock the magic, etc.). Editorial register only. Mono caps eyebrow
 * lines + Playfair Display italic headings + Source Serif 4 body copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallSheet(
  menuTitle: String,
  onDismiss: () -> Unit,
  onRedeemed: (grantedMenuIds: Set<String>) -> Unit,
  vm: PartiesViewModel,
  isMystery: Boolean = false,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val feedback by vm.redemptionFeedback.collectAsState()
  val keyboard = LocalSoftwareKeyboardController.current

  LaunchedEffect(feedback) {
    val fb = feedback ?: return@LaunchedEffect
    if (fb is RedemptionResult.Success) {
      onRedeemed(fb.grantedMenuIds)
      vm.dismissFeedback()
      scope.launch {
        sheetState.hide()
        onDismiss()
      }
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Paper2,
    dragHandle = { BrassDragHandle() },
    contentWindowInsets = { WindowInsets.systemBars },
  ) {
    PaywallContent(
      menuTitle = menuTitle,
      feedback = feedback,
      isMystery = isMystery,
      onSubmitCode = { code ->
        keyboard?.hide()
        vm.redeemCode(code)
      },
      onDismissFeedback = { vm.dismissFeedback() },
      onClose = {
        scope.launch {
          sheetState.hide()
          onDismiss()
        }
      },
    )
  }
}

@Composable
private fun BrassDragHandle() {
  Box(
    modifier = Modifier
      .padding(top = 12.dp, bottom = 8.dp)
      .size(width = 38.dp, height = 4.dp)
      .clip(RoundedCornerShape(2.dp))
      .background(BrassBright.copy(alpha = 0.6f)),
  )
}

@Composable
private fun PaywallContent(
  menuTitle: String,
  feedback: RedemptionResult?,
  isMystery: Boolean,
  onSubmitCode: (String) -> Unit,
  onDismissFeedback: () -> Unit,
  onClose: () -> Unit,
) {
  var code by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 16.dp)
      .padding(bottom = 32.dp),
  ) {
    // Eyebrow + heading
    Text(
      text = "SPEAKEATER · CURATE A PARTY",
      style = MaterialTheme.typography.labelMedium.copy(
        color = BrassBright,
        fontFamily = Mono,
        letterSpacing = 3.2.sp,
      ),
    )
    Spacer(Modifier.height(10.dp))
    Text(
      text = "Unlock this menu",
      style = MaterialTheme.typography.displaySmall.copy(
        color = Ink,
        fontFamily = SerifDisplay,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 36.sp,
      ),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = menuTitle,
      style = MaterialTheme.typography.bodyMedium.copy(
        color = InkSoft,
        fontFamily = SerifBody,
        fontStyle = FontStyle.Italic,
      ),
    )

    // Host clarification — only for Mystery Nights (multiplayer game). Party
    // menus don't have multiplayer mode so this would be confusing there.
    if (isMystery) {
      Spacer(Modifier.height(14.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, BrassBright.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
          .padding(horizontal = 14.dp, vertical = 12.dp),
      ) {
        Column {
          Text(
            text = "1 HOST · UP TO 8 GUESTS · FREE TO JOIN",
            style = MaterialTheme.typography.labelMedium.copy(
              color = BrassBright,
              fontFamily = Mono,
              fontSize = 11.sp,
              letterSpacing = 2.4.sp,
              fontWeight = FontWeight.SemiBold,
            ),
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text = "You buy once. Up to eight guests join with a 4-letter code on the free app.",
            style = MaterialTheme.typography.bodySmall.copy(
              color = InkSoft,
              fontFamily = SerifBody,
              fontSize = 13.sp,
              lineHeight = 19.sp,
              fontStyle = FontStyle.Italic,
            ),
          )
        }
      }
    }

    Spacer(Modifier.height(20.dp))
    DottedGoldRule(modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(20.dp))

    // Left: Kickstarter backer? Code entry.
    Text(
      text = "KICKSTARTER BACKER?",
      style = MaterialTheme.typography.labelMedium.copy(
        color = BrassBright,
        fontFamily = Mono,
        letterSpacing = 2.4.sp,
      ),
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = "Your code came with your reward delivery email. Enter it below to unlock this menu, or your full Founding Set.",
      style = MaterialTheme.typography.bodyMedium.copy(
        color = InkSoft,
        fontFamily = SerifBody,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
      value = code,
      onValueChange = { raw ->
        // Force uppercase and trim spaces. Allow only the format chars.
        val cleaned = raw.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(15)
        code = cleaned
        if (feedback is RedemptionResult.InvalidFormat || feedback is RedemptionResult.AlreadyUsed) {
          onDismissFeedback()
        }
      },
      placeholder = {
        Text(
          "SPEAK-XXXX-YYYY",
          style = MaterialTheme.typography.bodyMedium.copy(
            color = InkFaint,
            fontFamily = Mono,
            letterSpacing = 1.5.sp,
          ),
        )
      },
      singleLine = true,
      keyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Characters,
        imeAction = ImeAction.Done,
      ),
      keyboardActions = KeyboardActions(onDone = { onSubmitCode(code) }),
      colors = OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = Rule,
        focusedBorderColor = BrassBright,
        focusedTextColor = Ink,
        unfocusedTextColor = Ink,
        cursorColor = BrassBright,
        focusedContainerColor = Paper3,
        unfocusedContainerColor = Paper3,
      ),
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))

    // Submit button — brass-filled with paper text
    Button(
      onClick = { onSubmitCode(code) },
      enabled = code.length >= 14, // SPEAK-XXXX-YYYY = 15 chars
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(3.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = BrassBright,
        contentColor = Paper,
        disabledContainerColor = BrassDeep.copy(alpha = 0.4f),
        disabledContentColor = InkFaint,
      ),
    ) {
      Text(
        "REDEEM CODE",
        style = MaterialTheme.typography.labelLarge.copy(
          fontFamily = Mono,
          letterSpacing = 2.8.sp,
        ),
      )
    }

    // Feedback line (errors)
    when (val fb = feedback) {
      is RedemptionResult.InvalidFormat -> FeedbackLine(
        text = "Code format is SPEAK followed by two groups of four. Letters and numbers only.",
        color = Terracotta,
      )
      is RedemptionResult.AlreadyUsed -> FeedbackLine(
        text = "This code has already been redeemed on this device.",
        color = Terracotta,
      )
      is RedemptionResult.Failure -> FeedbackLine(
        text = fb.message,
        color = Terracotta,
      )
      else -> Unit
    }

    Spacer(Modifier.height(28.dp))
    DottedGoldRule(modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(20.dp))

    // Right: single-menu purchase. Two buttons total on this sheet:
    //  1. REDEEM CODE (above, brass-filled) — Kickstarter backers
    //  2. BUY THIS MENU · $5 (below, brass-filled) — single Play purchase
    // The "or both" framing is intentional: every guest at the paywall has at
    // least one valid path forward.
    Text(
      text = "NOT A KICKSTARTER BACKER?",
      style = MaterialTheme.typography.labelMedium.copy(
        color = BrassBright,
        fontFamily = Mono,
        letterSpacing = 2.4.sp,
      ),
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = "Subscribe to Speakeater Pro and every menu unlocks — all five Mystery Nights, fifty party menus, the cocktail archive, and no ads.",
      style = MaterialTheme.typography.bodyMedium.copy(
        color = InkSoft,
        fontFamily = SerifBody,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
    )
    Spacer(Modifier.height(16.dp))

    // PRIMARY: real Pro subscription card (tier selector + Play purchase flow).
    app.pantrie.billing.ProUpgradeCard(vintageMode = false)

    Spacer(Modifier.height(10.dp))

    // Secondary: notify-me link for users who want to wait.
    OutlinedButton(
      onClick = onClose,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(3.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
    ) {
      Text(
        "MAYBE LATER",
        style = MaterialTheme.typography.labelLarge.copy(
          color = InkSoft,
          fontFamily = Mono,
          letterSpacing = 2.8.sp,
        ),
      )
    }

    Spacer(Modifier.height(24.dp))
    Text(
      text = "We honor every Kickstarter code for as long as the app keeps running. If we shut down, all menus unlock for everyone.",
      style = MaterialTheme.typography.bodySmall.copy(
        color = InkFaint,
        fontFamily = SerifBody,
        fontStyle = FontStyle.Italic,
        fontSize = 12.sp,
        lineHeight = 18.sp,
      ),
    )
  }
}

@Composable
private fun BuyMenuButton() {
  // Single-menu Play Billing unlock. Wired to a toast today (Play SKU
  // `menu_unlock_single` ships at Play Store launch, June 10). Post-launch:
  // launch billing flow via BillingClient.launchBillingFlow with the SKU.
  val context = LocalContext.current
  Button(
    onClick = {
      android.widget.Toast.makeText(
        context,
        "Single-menu purchase opens at the Play Store launch on June 10. Backers can redeem above.",
        android.widget.Toast.LENGTH_LONG,
      ).show()
    },
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(3.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = BrassBright,
      contentColor = Paper,
    ),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "BUY THIS MENU",
        style = MaterialTheme.typography.labelLarge.copy(
          fontFamily = Mono,
          letterSpacing = 2.8.sp,
        ),
      )
      Spacer(Modifier.width(10.dp))
      Box(
        modifier = Modifier
          .background(Paper.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
          .padding(horizontal = 8.dp, vertical = 2.dp),
      ) {
        Text(
          "$5",
          style = MaterialTheme.typography.labelLarge.copy(
            fontFamily = Mono,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.0.sp,
          ),
        )
      }
    }
  }
}

@Composable
private fun FeedbackLine(text: String, color: androidx.compose.ui.graphics.Color) {
  Spacer(Modifier.height(10.dp))
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
      .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "·",
      style = MaterialTheme.typography.bodyMedium.copy(color = color, fontWeight = FontWeight.Bold),
    )
    Spacer(Modifier.width(8.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall.copy(
        color = color,
        fontFamily = SerifBody,
        fontStyle = FontStyle.Italic,
        fontSize = 12.sp,
        lineHeight = 16.sp,
      ),
    )
  }
}

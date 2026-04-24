package app.pantrie.feature.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * One-shot Composable that asks for POST_NOTIFICATIONS on Android 13+.
 *
 * Call once from a top-level screen (e.g. Pantry). Uses a flag in
 * [NotificationScheduler.PREFS] so we only ever prompt the user once per install;
 * if they decline the system shows nothing further on subsequent calls.
 */
@Composable
fun NotificationPermissionPrompt() {
  val ctx = LocalContext.current

  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { /* no-op: worker handles missing permission gracefully */ }

  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
    if (hasNotificationPermission(ctx)) return@LaunchedEffect
    val prefs = ctx.getSharedPreferences(NotificationScheduler.PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(KEY_ASKED, false)) return@LaunchedEffect
    prefs.edit().putBoolean(KEY_ASKED, true).apply()
    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
  }
}

private fun hasNotificationPermission(ctx: Context): Boolean {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
  return ContextCompat.checkSelfPermission(
    ctx, Manifest.permission.POST_NOTIFICATIONS,
  ) == PackageManager.PERMISSION_GRANTED
}

private const val KEY_ASKED = "notif_permission_asked"

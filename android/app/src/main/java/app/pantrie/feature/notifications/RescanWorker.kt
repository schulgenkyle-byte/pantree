package app.pantrie.feature.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.pantrie.MainActivity
import app.pantrie.R
import app.pantrie.feature.beta.Analytics
import app.pantrie.network.PantrieApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

/**
 * Periodic worker that fetches home stats and posts local nudge notifications:
 *  - Rescan nudge when `daysSinceScan >= RESCAN_THRESHOLD_DAYS`
 *  - Expiring nudge when `expiringSoon + expired > 0`
 *
 * Idempotent per UTC calendar day: last-notified timestamps stored in
 * [NotificationScheduler.PREFS] so we never double-fire in the same day.
 */
@HiltWorker
class RescanWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val api: PantrieApi,
  private val analytics: Analytics,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val stats = runCatching { api.homeStats() }.getOrElse {
      return Result.retry()
    }

    val prefs = applicationContext.getSharedPreferences(
      NotificationScheduler.PREFS, Context.MODE_PRIVATE,
    )
    val today = todayKey()

    // Rescan nudge
    val daysSince = stats.daysSinceScan ?: Int.MAX_VALUE
    if (daysSince >= RESCAN_THRESHOLD_DAYS) {
      val lastRescan = prefs.getString(KEY_LAST_RESCAN, null)
      if (lastRescan != today) {
        postRescanNotification(daysSince)
        prefs.edit().putString(KEY_LAST_RESCAN, today).apply()
        analytics.track("notification_posted", mapOf("kind" to "rescan"))
      }
    }

    // Expiring nudge
    val expiringTotal = stats.expiringSoon + stats.expired
    if (expiringTotal > 0) {
      val lastExpiring = prefs.getString(KEY_LAST_EXPIRING, null)
      if (lastExpiring != today) {
        postExpiringNotification(stats.expiringSoon, stats.expired)
        prefs.edit().putString(KEY_LAST_EXPIRING, today).apply()
        analytics.track("notification_posted", mapOf("kind" to "expiring"))
      }
    }

    return Result.success()
  }

  private fun postRescanNotification(daysSince: Int) {
    val text = when {
      daysSince >= 14 -> "It's been $daysSince days since you scanned your pantry."
      daysSince >= 7 -> "Pantry looks stale — give it a quick rescan."
      else -> "Haven't checked your pantry in $daysSince days?"
    }
    val intent = tapIntent(NAV_TARGET_PANTRY)
    val pending = PendingIntent.getActivity(
      applicationContext, REQ_RESCAN, intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val builder = NotificationCompat.Builder(applicationContext, CHANNEL_RESCAN)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Rescan your pantry")
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .setAutoCancel(true)
      .setContentIntent(pending)
    safeNotify(NOTIF_ID_RESCAN, builder)
  }

  private fun postExpiringNotification(expiringSoon: Int, expired: Int) {
    val text = buildString {
      if (expired > 0) append("$expired expired")
      if (expired > 0 && expiringSoon > 0) append(" and ")
      if (expiringSoon > 0) append("$expiringSoon expiring soon")
      append(" — cook them before they're wasted.")
    }
    val intent = tapIntent(NAV_TARGET_EXPIRING)
    val pending = PendingIntent.getActivity(
      applicationContext, REQ_EXPIRING, intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val builder = NotificationCompat.Builder(applicationContext, CHANNEL_EXPIRING)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Items expiring in your pantry")
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .setAutoCancel(true)
      .setContentIntent(pending)
    safeNotify(NOTIF_ID_EXPIRING, builder)
  }

  private fun tapIntent(navTarget: String): Intent =
    Intent(applicationContext, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_NAV_TARGET, navTarget)
    }

  private fun safeNotify(id: Int, builder: NotificationCompat.Builder) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ActivityCompat.checkSelfPermission(
        applicationContext, Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) return
    }
    NotificationManagerCompat.from(applicationContext).notify(id, builder.build())
  }

  private fun todayKey(): String {
    val c = Calendar.getInstance()
    return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
  }

  companion object {
    const val WORK_NAME = "pantrie-rescan-nudge"
    const val CHANNEL_RESCAN = "pantrie_rescan"
    const val CHANNEL_EXPIRING = "pantrie_expiring"

    const val EXTRA_NAV_TARGET = "pantrie.nav_target"
    const val NAV_TARGET_PANTRY = "pantry"
    const val NAV_TARGET_EXPIRING = "shopping"

    const val KEY_LAST_RESCAN = "last_rescan_notified"
    const val KEY_LAST_EXPIRING = "last_expiring_notified"

    private const val NOTIF_ID_RESCAN = 1001
    private const val NOTIF_ID_EXPIRING = 1002
    private const val REQ_RESCAN = 2001
    private const val REQ_EXPIRING = 2002

    private const val RESCAN_THRESHOLD_DAYS = 5
  }
}

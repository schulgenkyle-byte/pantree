package app.pantrie.feature.notifications

import android.Manifest
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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Fires a "your swipes refilled" notification at the next local midnight after
 * a free user has hit the 40-swipe daily cap.
 *
 * Inactivity gate: if the user opened the app within the last 12 hours, skip
 * the notification entirely. This is a re-engagement nudge, not an annoyance —
 * we don't ping people who are already actively cooking.
 *
 * Scheduled by [SwipeQuotaRepository] when increment() pushes the count to the
 * cap. WorkManager handles the delay even if the device reboots.
 */
@HiltWorker
class SwipeRefillWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val analytics: Analytics,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val prefs = applicationContext.getSharedPreferences(
      NotificationScheduler.PREFS, Context.MODE_PRIVATE,
    )
    val now = System.currentTimeMillis()
    // Look up the last app-open timestamp. Set by MainActivity.onCreate via
    // the same prefs file; if missing (fresh install or pre-instrumented user),
    // assume long-inactive and fire the notification.
    val lastOpen = prefs.getLong(KEY_LAST_APP_OPEN_MS, 0L)
    val inactiveMs = now - lastOpen
    if (lastOpen != 0L && inactiveMs < INACTIVITY_THRESHOLD_MS) {
      // User is still active — don't ping. Return success so WorkManager
      // doesn't retry; the next cap-hit will reschedule a new worker anyway.
      analytics.track("swipe_refill_skipped", mapOf("reason" to "active", "inactiveMs" to inactiveMs))
      return Result.success()
    }

    postNotification()
    analytics.track("swipe_refill_notification_posted")
    return Result.success()
  }

  private fun postNotification() {
    val intent = Intent(applicationContext, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(RescanWorker.EXTRA_NAV_TARGET, NAV_TARGET_DECK)
    }
    val pending = PendingIntent.getActivity(
      applicationContext, REQ_REFILL, intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val builder = NotificationCompat.Builder(applicationContext, CHANNEL_REFILL)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Your swipes are back")
      .setContentText("40 fresh recipes waiting — what's for dinner tonight?")
      .setStyle(NotificationCompat.BigTextStyle().bigText("40 fresh recipes waiting — what's for dinner tonight?"))
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .setAutoCancel(true)
      .setContentIntent(pending)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ActivityCompat.checkSelfPermission(
        applicationContext, Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) return
    }
    NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID_REFILL, builder.build())
  }

  companion object {
    const val WORK_NAME_PREFIX = "speakeater-swipe-refill-"
    const val CHANNEL_REFILL = "pantrie_swipe_refill"
    const val NAV_TARGET_DECK = "deck"

    /** Persisted in NotificationScheduler.PREFS — written by MainActivity.onCreate. */
    const val KEY_LAST_APP_OPEN_MS = "last_app_open_ms"

    private const val NOTIF_ID_REFILL = 1003
    private const val REQ_REFILL = 2003

    /** Skip the notification if the user opened the app within this window.
     *  Reasoning: actively-cooking users don't need a "come back" ping. */
    private const val INACTIVITY_THRESHOLD_MS = 12L * 60L * 60L * 1000L  // 12 hours
  }
}

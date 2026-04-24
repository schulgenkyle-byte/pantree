package app.pantrie.feature.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules the [RescanWorker] to run approximately once a day, aligned to 9am local.
 * Safe to call repeatedly — uses [ExistingPeriodicWorkPolicy.KEEP] so the existing
 * schedule is preserved across app launches.
 */
object NotificationScheduler {
  const val PREFS = "pantrie_notifications"

  fun schedule(context: Context) {
    ensureChannels(context)

    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()

    val initialDelayMinutes = minutesUntilNext9am()

    val request = PeriodicWorkRequestBuilder<RescanWorker>(1, TimeUnit.DAYS)
      .setConstraints(constraints)
      .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
      .addTag(RescanWorker.WORK_NAME)
      .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      RescanWorker.WORK_NAME,
      ExistingPeriodicWorkPolicy.KEEP,
      request,
    )
  }

  fun cancel(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(RescanWorker.WORK_NAME)
  }

  private fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return

    if (nm.getNotificationChannel(RescanWorker.CHANNEL_RESCAN) == null) {
      nm.createNotificationChannel(
        NotificationChannel(
          RescanWorker.CHANNEL_RESCAN,
          "Pantry rescan reminders",
          NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
          description = "Nudges to rescan your pantry when items may be out of date."
        },
      )
    }
    if (nm.getNotificationChannel(RescanWorker.CHANNEL_EXPIRING) == null) {
      nm.createNotificationChannel(
        NotificationChannel(
          RescanWorker.CHANNEL_EXPIRING,
          "Expiring food alerts",
          NotificationManager.IMPORTANCE_HIGH,
        ).apply {
          description = "Alerts when items in your pantry are about to expire."
        },
      )
    }
  }

  private fun minutesUntilNext9am(): Long {
    val now = Calendar.getInstance()
    val target = (now.clone() as Calendar).apply {
      set(Calendar.HOUR_OF_DAY, 9)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
    }
    return ((target.timeInMillis - now.timeInMillis) / 60_000L).coerceAtLeast(1L)
  }
}

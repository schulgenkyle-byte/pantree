package app.pantrie.feature.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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

  /** Schedules a one-shot SwipeRefillWorker for the next local midnight (+1 min buffer
   *  so the date-rollover has fully landed). REPLACE policy means re-hitting the cap
   *  on a subsequent day just retargets the same unique work, no duplicates. */
  fun scheduleSwipeRefill(context: Context) {
    ensureChannels(context)
    val delayMs = msUntilNextLocalMidnight() + TimeUnit.MINUTES.toMillis(1)
    val request = OneTimeWorkRequestBuilder<SwipeRefillWorker>()
      .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
      .addTag("swipe-refill")
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      SwipeRefillWorker.WORK_NAME_PREFIX + "v1",
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }

  private fun msUntilNextLocalMidnight(): Long {
    val now = Calendar.getInstance()
    val target = (now.clone() as Calendar).apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      add(Calendar.DAY_OF_YEAR, 1)
    }
    return (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
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
    if (nm.getNotificationChannel(SwipeRefillWorker.CHANNEL_REFILL) == null) {
      nm.createNotificationChannel(
        NotificationChannel(
          SwipeRefillWorker.CHANNEL_REFILL,
          "Daily swipes refilled",
          NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
          description = "Lets you know when your free daily recipe swipes reset."
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

package app.pantrie.feature.parties.game

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.pantrie.MainActivity
import app.pantrie.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notification surface for Mystery Nights multiplayer games.
 *
 * Doctrine (memory/project_mystery_nights_design_doctrine.md): every phone
 * vibrates at the SAME instant on every beat — that is how suspicion is
 * managed at the table. Players glance, read the whispered text in 5-8
 * seconds, put the phone face-down. The vibration pattern is uniform so
 * observers cannot pattern-match whose phone got the actionable info.
 *
 * Two channels:
 *   • [CHANNEL_BEATS]   — high-priority, vibrates with [UNIFORM_VIBRATION],
 *                          fired every time a beat arrives at this phone.
 *   • [CHANNEL_SESSION] — low-priority ongoing notification used by the
 *                          [GameSessionService] to keep the process alive
 *                          while the WebSocket holds the game open.
 */
@Singleton
class GameNotifications @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  init { ensureChannels() }

  fun postBeat(body: String, roomCode: String) {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    val contentIntent = PendingIntent.getActivity(context, 0, intent, flags)
    val notif = NotificationCompat.Builder(context, CHANNEL_BEATS)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Mystery Night · $roomCode")
      .setContentText(body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setVibrate(UNIFORM_VIBRATION)
      .setAutoCancel(true)
      .setContentIntent(contentIntent)
      .build()
    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BEAT, notif)
  }

  fun buildSessionNotification(roomCode: String, players: Int): Notification {
    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    val contentIntent = PendingIntent.getActivity(context, 0, intent, flags)
    val playerLabel = if (players == 1) "1 player" else "$players players"
    return NotificationCompat.Builder(context, CHANNEL_SESSION)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Mystery Night in progress")
      .setContentText("Room $roomCode · $playerLabel")
      .setOngoing(true)
      .setSilent(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setShowWhen(false)
      .setContentIntent(contentIntent)
      .build()
  }

  fun dismissBeat() {
    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_BEAT)
  }

  private fun ensureChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = context.getSystemService(NotificationManager::class.java) ?: return

    if (nm.getNotificationChannel(CHANNEL_BEATS) == null) {
      val beats = NotificationChannel(
        CHANNEL_BEATS,
        "Mystery Night beats",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Whispers from a Mystery Night in progress."
        enableVibration(true)
        vibrationPattern = UNIFORM_VIBRATION
        setShowBadge(false)
      }
      nm.createNotificationChannel(beats)
    }

    if (nm.getNotificationChannel(CHANNEL_SESSION) == null) {
      val session = NotificationChannel(
        CHANNEL_SESSION,
        "Mystery Night in session",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Keeps the game running while your phone is face-down."
        setShowBadge(false)
      }
      nm.createNotificationChannel(session)
    }
  }

  companion object {
    const val CHANNEL_BEATS = "mystery_night_beats"
    const val CHANNEL_SESSION = "mystery_night_session"
    const val NOTIFICATION_ID_BEAT = 4201
    const val NOTIFICATION_ID_SESSION = 4200
    /** Uniform pattern. Same on every phone. Every beat. */
    val UNIFORM_VIBRATION = longArrayOf(0L, 250L)
  }
}

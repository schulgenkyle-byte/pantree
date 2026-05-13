package app.pantrie.feature.parties.game

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that keeps the app process alive while a Mystery Night
 * is in progress. The single-tagged WebSocket in [GameSocket] lives in the
 * Hilt singleton scope, so as long as the process is alive the socket stays
 * open and beats keep flowing — even while the phone is face-down on the
 * table and the Activity has been backgrounded.
 *
 * The service does not own the WebSocket itself; the existing
 * @Singleton GameSocket already owns it. The service exists solely to make
 * the process foreground-resident so Doze and aggressive OEM battery savers
 * (Samsung especially) do not kill it mid-game.
 *
 * Started from [GameViewModel] on `host_begin` / `character_assigned` and
 * stopped on close() or reveal.
 */
@AndroidEntryPoint
class GameSessionService : Service() {
  @Inject lateinit var notifications: GameNotifications

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val code = intent?.getStringExtra(EXTRA_CODE) ?: "----"
    val players = intent?.getIntExtra(EXTRA_PLAYERS, 0) ?: 0
    val notif = notifications.buildSessionNotification(code, players)
    startForeground(GameNotifications.NOTIFICATION_ID_SESSION, notif)
    return START_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    notifications.dismissBeat()
  }

  companion object {
    const val EXTRA_CODE = "code"
    const val EXTRA_PLAYERS = "players"

    fun start(context: Context, code: String, players: Int) {
      val intent = Intent(context, GameSessionService::class.java).apply {
        putExtra(EXTRA_CODE, code)
        putExtra(EXTRA_PLAYERS, players)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, GameSessionService::class.java))
    }
  }
}

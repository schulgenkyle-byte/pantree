package app.pantrie.feature.parties.game

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists a single in-progress Mystery Night so players or hosts can rejoin
 * after the app is force-killed, the WebSocket drops, or the phone goes to
 * sleep long enough that the foreground service is reaped.
 *
 * On the DO side: a player who reconnects with the same code + name keeps
 * their original character. A host who reconnects with role=host on the
 * same code re-attaches to the in-progress room without minting a new
 * 4-letter code.
 */
@Singleton
class GameSessionStore @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun save(active: ActiveGame) {
    prefs.edit()
      .putBoolean(KEY_ACTIVE, true)
      .putString(KEY_CODE, active.code)
      .putString(KEY_NAME, active.name)
      .putString(KEY_ROLE, active.role)
      .putString(KEY_MENU_ID, active.menuId)
      .putLong(KEY_SAVED_AT, System.currentTimeMillis())
      .apply()
  }

  fun load(): ActiveGame? {
    if (!prefs.getBoolean(KEY_ACTIVE, false)) return null
    val code = prefs.getString(KEY_CODE, null)?.takeIf { it.isNotBlank() } ?: return null
    val role = prefs.getString(KEY_ROLE, null)?.takeIf { it.isNotBlank() } ?: return null
    val savedAt = prefs.getLong(KEY_SAVED_AT, 0L)
    // The backend GCs rooms after 12 hours. After that the stored record is
    // dead weight, so we expire local records on the same window.
    if (System.currentTimeMillis() - savedAt > ROOM_TTL_MS) {
      clear(); return null
    }
    return ActiveGame(
      code = code,
      name = prefs.getString(KEY_NAME, "") ?: "",
      role = role,
      menuId = prefs.getString(KEY_MENU_ID, "") ?: "",
    )
  }

  fun clear() {
    prefs.edit().clear().apply()
  }

  companion object {
    private const val PREFS = "mystery_nights_session"
    private const val KEY_ACTIVE = "active"
    private const val KEY_CODE = "code"
    private const val KEY_NAME = "name"
    private const val KEY_ROLE = "role"
    private const val KEY_MENU_ID = "menu_id"
    private const val KEY_SAVED_AT = "saved_at"
    private const val ROOM_TTL_MS = 12L * 60L * 60L * 1000L
  }
}

data class ActiveGame(
  val code: String,
  val name: String,
  val role: String,    // "host" or "player"
  val menuId: String,  // host needs this to re-send the script on rejoin
)

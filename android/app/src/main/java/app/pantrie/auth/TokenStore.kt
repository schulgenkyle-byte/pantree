package app.pantrie.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Refresh + user-id persistence in EncryptedSharedPreferences; access token kept in memory only.
 * If EncryptedSharedPreferences fails to open (known edge cases after Keystore invalidation),
 * we delete the file and retry once — the cost is a forced logout, better than a crash loop.
 */
@Singleton
class TokenStore @Inject constructor(@ApplicationContext private val ctx: Context) {

  private val prefs: SharedPreferences = openPrefs()

  @Volatile private var accessToken: String? = null
  @Volatile private var accessExpiresAt: Long = 0

  private fun openPrefs(): SharedPreferences = try {
    createEncryptedPrefs()
  } catch (e: Throwable) {
    // Keystore key invalidated, Tink state broken, biometric re-enroll etc.
    // Scorch + rebuild. User will be signed out on next launch, which is acceptable.
    File(ctx.filesDir.parent, "shared_prefs/$PREFS_FILE.xml").delete()
    runCatching { createEncryptedPrefs() }.getOrElse {
      // Final fallback: in-memory only. Treat the user as logged out.
      InMemoryPrefs()
    }
  }

  private fun createEncryptedPrefs(): SharedPreferences {
    val masterKey = MasterKey.Builder(ctx, MASTER_KEY_ALIAS)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()
    return EncryptedSharedPreferences.create(
      ctx,
      PREFS_FILE,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }

  fun saveRefreshToken(token: String) { prefs.edit().putString(KEY_REFRESH, token).apply() }
  fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)

  fun setAccess(token: String, expiresAt: Long) {
    accessToken = token
    accessExpiresAt = expiresAt
  }

  fun getAccess(): String? {
    val t = accessToken ?: return null
    if (System.currentTimeMillis() / 1000 >= accessExpiresAt - 30) return null
    return t
  }

  fun saveUserId(id: String) { prefs.edit().putString(KEY_USER_ID, id).apply() }
  fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

  fun clear() {
    accessToken = null
    accessExpiresAt = 0
    prefs.edit().clear().apply()
  }

  companion object {
    private const val MASTER_KEY_ALIAS = "pantrie_master_key"
    private const val PREFS_FILE = "pantrie_prefs"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_USER_ID = "user_id"
  }
}

/** Last-resort in-memory prefs. Keeps the app usable but forces re-login every process start. */
private class InMemoryPrefs : SharedPreferences {
  private val data = mutableMapOf<String, Any?>()
  override fun getAll(): Map<String, *> = data.toMap()
  override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
  override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = @Suppress("UNCHECKED_CAST") (data[key] as? Set<String>) ?: defValues
  override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
  override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
  override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
  override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
  override fun contains(key: String?): Boolean = data.containsKey(key)
  override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
    override fun putString(k: String?, v: String?) = apply { k?.let { data[it] = v } }
    override fun putStringSet(k: String?, v: Set<String>?) = apply { k?.let { data[it] = v } }
    override fun putInt(k: String?, v: Int) = apply { k?.let { data[it] = v } }
    override fun putLong(k: String?, v: Long) = apply { k?.let { data[it] = v } }
    override fun putFloat(k: String?, v: Float) = apply { k?.let { data[it] = v } }
    override fun putBoolean(k: String?, v: Boolean) = apply { k?.let { data[it] = v } }
    override fun remove(k: String?) = apply { k?.let { data.remove(it) } }
    override fun clear() = apply { data.clear() }
    override fun commit(): Boolean = true
    override fun apply() { /* noop */ }
  }
  override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
  override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}

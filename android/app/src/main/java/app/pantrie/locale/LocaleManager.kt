package app.pantrie.locale

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Five supported locales. Order is the order they appear in the picker:
 * English first as the default, then Spanish + Portuguese (LatAm reach),
 * then Hindi + Indonesian (largest growing Android markets we target).
 *
 * Each tag follows BCP-47 — Android's `LocaleListCompat.forLanguageTags`
 * accepts these directly. `pt-BR` (not `pt`) so the regional Brazilian
 * forms in values-pt-rBR/strings.xml are picked up.
 */
enum class AppLocale(val tag: String, val displayKey: String) {
  ENGLISH("en",     "lang_name_english"),
  SPANISH("es",     "lang_name_spanish"),
  PORTUGUESE("pt-BR", "lang_name_portuguese"),
  HINDI("hi",       "lang_name_hindi"),
  INDONESIAN("id",  "lang_name_indonesian");

  companion object {
    fun fromTag(tag: String?): AppLocale = entries.firstOrNull { it.tag == tag } ?: ENGLISH
  }
}

/**
 * Persists the user's chosen locale and applies it to the running process.
 *
 * Storage is plain SharedPreferences (NOT DataStore) because the picker gate
 * needs to read this on the main thread during the very first composition —
 * before splash dismisses. DataStore.data is a never-completing hot Flow;
 * `runBlocking { collect { } }` on the main thread hangs the app forever.
 * SharedPreferences.getString returns synchronously and is the right tool
 * for "did the user pick a locale yet" boot-time questions.
 *
 * Apply path uses `AppCompatDelegate.setApplicationLocales` which Android 13+
 * stores in per-app preferences and recreates the activity. On Android 12
 * and below, AppCompat falls back to runtime configuration override.
 */
@Singleton
class LocaleManager @Inject constructor(
  @ApplicationContext private val ctx: Context,
) {
  private val prefs = ctx.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)

  /** Synchronous snapshot. Safe to call from main thread. */
  fun currentTag(): String? = prefs.getString(KEY, null)

  /** True once the user has explicitly picked a locale. */
  fun hasPicked(): Boolean = currentTag() != null

  /** Persist + apply. Activity recreates after AppCompatDelegate call so
   *  the new locale takes effect on the next composition. */
  fun set(locale: AppLocale) {
    prefs.edit().putString(KEY, locale.tag).apply()
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale.tag))
  }

  companion object {
    private const val KEY = "app_locale_tag"
  }
}

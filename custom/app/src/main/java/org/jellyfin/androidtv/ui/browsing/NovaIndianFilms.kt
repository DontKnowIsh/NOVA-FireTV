package org.jellyfin.androidtv.ui.browsing

import android.content.Context

/**
 * Settings → "Indian films" (off by default): adds Hindi and Punjabi to the
 * Download page, and sends Indian-language films to a Radarr root folder with
 * "Indian" in its name (if there is one) instead of the usual movies folder.
 */
object NovaIndianFilms {
	private const val PREFS = "nova_settings"
	private const val KEY = "indian_films"

	@Volatile
	private var enabled = false

	/** Read once when NOVA starts. */
	fun init(context: Context) {
		enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
	}

	fun isEnabled(): Boolean = enabled

	fun setEnabled(context: Context, value: Boolean) {
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
		enabled = value
	}
}

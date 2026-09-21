package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

enum class AppTheme(
	override val nameRes: Int,
) : PreferenceEnum {
	/** The default dark theme. */
	DARK(R.string.pref_theme_dark),

	/** The classic emerald theme. */
	EMERALD(R.string.pref_theme_emerald),

	/** Muted purple Jellyfin theme. */
	MUTED_PURPLE(R.string.pref_theme_muted_purple),

	/** Cinema-style black and red theme. */
	CINEMA_RED(R.string.pref_theme_cinema_red),

	/** Near-black theme with a cool blue accent. */
	MIDNIGHT(R.string.pref_theme_midnight),

	/** Near-black theme with a warm amber accent. */
	AMBER(R.string.pref_theme_amber),
}

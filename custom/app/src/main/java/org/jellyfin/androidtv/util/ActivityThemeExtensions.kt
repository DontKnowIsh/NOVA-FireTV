package org.jellyfin.androidtv.util

import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.AppTheme
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Getter to get the style resource for a given theme.
 */
private val AppTheme.style
	get() = when (this) {
		AppTheme.DARK -> R.style.Theme_Jellyfin
		AppTheme.EMERALD -> R.style.Theme_Jellyfin_Emerald
		AppTheme.MUTED_PURPLE -> R.style.Theme_Jellyfin_MutedPurple
		AppTheme.CINEMA_RED -> R.style.Theme_Jellyfin_CinemaRed
		AppTheme.MIDNIGHT -> R.style.Theme_Jellyfin_Midnight
		AppTheme.AMBER -> R.style.Theme_Jellyfin_Amber
	}

/**
 * Private view model for applyTheme to store the currently set theme.
 */
class ThemeViewModel : ViewModel() {
	var theme: AppTheme? = null
}

/**
 * Apply the selected app theme and recreate the activity when needed.
 */
fun FragmentActivity.applyTheme() {
	val viewModel by viewModels<ThemeViewModel>()
	val userPreferences by inject<UserPreferences>()
	val theme = userPreferences[UserPreferences.appTheme]

	if (viewModel.theme != theme) {
		if (viewModel.theme != null) {
			Timber.i("Recreating activity to apply theme")
			viewModel.theme = null
			recreate()
		} else {
			Timber.i("Applying theme $theme")
			viewModel.theme = theme
			setTheme(theme.style)
		}
	}
}

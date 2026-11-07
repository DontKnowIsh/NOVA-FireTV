package org.jellyfin.androidtv.ui.browsing

import android.app.Activity
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Opens NOVA's ☰ menu (More info, favourite, Delete) from screens that
 * aren't Compose, e.g. a season's episode list or an episode's page.
 */
object NovaItemMenuHost {
	private var open: ComposeView? = null

	@JvmStatic
	fun isMenuKey(keyCode: Int) = keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BUTTON_Y

	@JvmStatic
	@JvmOverloads
	fun show(activity: Activity, item: BaseItemDto?, onRemoved: Runnable? = null) {
		item ?: return
		if (open != null) return
		val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
		val view = ComposeView(activity)
		fun close() {
			runCatching { root.removeView(view) }
			if (open === view) open = null
		}
		view.setContent {
			JellyfinTheme {
				NovaItemMenu(
					item = item,
					onDismiss = { view.post { close() } },
					onRemoved = { onRemoved?.run() },
				)
			}
		}
		open = view
		root.addView(view, ViewGroup.LayoutParams(0, 0))
	}
}

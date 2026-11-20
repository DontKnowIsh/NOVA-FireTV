package org.jellyfin.androidtv.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import org.jellyfin.androidtv.ui.base.JellyfinTheme

/**
 * A full-screen NOVA page drawn over the current one, in the same window (not a
 * pop-up window), so trailers can play inside it. The page underneath can't
 * take the remote while it's open; closing puts the cursor back where it was.
 */
object NovaOverlay {
	fun show(context: Context, content: @Composable (close: () -> Unit) -> Unit) {
		val activity = context.findActivity() ?: return
		val root = activity.window?.decorView as? ViewGroup ?: return
		val page = activity.findViewById<ViewGroup>(android.R.id.content)
		val previousFocus = activity.currentFocus
		val previousBlocking = page?.descendantFocusability

		val view = ComposeView(activity)
		var closed = false
		val close: () -> Unit = {
			if (!closed) {
				closed = true
				root.removeView(view)
				if (page != null && previousBlocking != null) page.descendantFocusability = previousBlocking
				previousFocus?.requestFocus()
			}
		}
		view.setContent { JellyfinTheme { content(close) } }
		page?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
		root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
		view.isFocusable = true
		view.requestFocus(View.FOCUS_DOWN)
	}
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
	is Activity -> this
	is ContextWrapper -> baseContext.findActivity()
	else -> null
}

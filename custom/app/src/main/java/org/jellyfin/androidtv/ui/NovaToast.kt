package org.jellyfin.androidtv.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * NOVA's own pop-up message (instead of the Fire TV's plain grey one): a card
 * in the theme's colours near the bottom of the screen that fades away by itself.
 */
object NovaToast {
	private const val TAG = "nova_toast"
	private val main = Handler(Looper.getMainLooper())

	private class Shown(val view: ComposeView) {
		var message by mutableStateOf("")
		var error by mutableStateOf(false)
		var visible by mutableStateOf(false)
	}

	/** Shows [message]; [error] uses a warning mark instead of a tick. */
	fun show(activity: Activity, message: String, error: Boolean = false, long: Boolean = false) {
		main.post {
			if (activity.isFinishing) return@post
			val root = activity.window?.decorView as? ViewGroup ?: return@post
			val shown = (root.findViewWithTag<ComposeView>(TAG)?.getTag(TAG.hashCode()) as? Shown)
				?: create(activity, root)
			shown.message = message
			shown.error = error
			shown.visible = true
			main.removeCallbacksAndMessages(shown)
			main.postAtTime({ shown.visible = false }, shown, android.os.SystemClock.uptimeMillis() + if (long) 4_500L else 2_800L)
		}
	}

	private fun create(activity: Activity, root: ViewGroup): Shown {
		val view = ComposeView(activity)
		val shown = Shown(view)
		view.tag = TAG
		view.setTag(TAG.hashCode(), shown)
		// Only shows a message; never takes the remote.
		view.isFocusable = false
		view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
		view.setContent {
			val palette = nova
			Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
				AnimatedVisibility(
					visible = shown.visible,
					enter = fadeIn() + slideInVertically { it / 2 },
					exit = fadeOut() + slideOutVertically { it / 2 },
					modifier = Modifier.padding(bottom = 48.dp),
				) {
					val mark = if (shown.error) palette.statusMissing else palette.accent
					Row(
						modifier = Modifier
							.widthIn(max = 640.dp)
							.background(palette.surfaceRaised.withAlpha(0xF2), RoundedCornerShape(16.dp))
							.border(1.dp, mark.withAlpha(0x88), RoundedCornerShape(16.dp))
							.padding(horizontal = 18.dp, vertical = 13.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp),
					) {
						Box(
							modifier = Modifier.size(26.dp).background(mark, CircleShape),
							contentAlignment = Alignment.Center,
						) {
							BasicText(
								if (shown.error) "!" else "✓",
								style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
							)
						}
						BasicText(
							shown.message,
							style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
						)
					}
				}
			}
		}
		root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
		return shown
	}
}

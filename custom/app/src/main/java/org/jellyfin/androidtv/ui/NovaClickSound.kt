package org.jellyfin.androidtv.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.Window
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

/**
 * The Fire TV's own navigation sounds as you move around NOVA with the remote
 * (on by default; Settings → "Navigation sounds"). Quiet while a video is
 * playing. They follow the Fire TV's own "Navigation sounds" setting too.
 */
object NovaClickSound {
	private const val PREFS = "nova_settings"
	private const val KEY_ENABLED = "navigation_sounds"
	private const val MIN_GAP_MS = 45L

	// Holding a direction: at most one tick per card that actually moves, and no faster than this.
	private const val HELD_GAP_MS = 170L

	private var audioManager: AudioManager? = null
	private var lastPlayed = 0L

	fun isEnabled(context: Context): Boolean =
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

	fun setEnabled(context: Context, enabled: Boolean) {
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
	}

	/** Called once from the Application: every NOVA screen gets the click. */
	fun install(application: Application) {
		audioManager = application.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

		application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
			override fun onActivityResumed(activity: Activity) = wrap(activity)
			override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
			override fun onActivityStarted(activity: Activity) = Unit
			override fun onActivityPaused(activity: Activity) = Unit
			override fun onActivityStopped(activity: Activity) = Unit
			override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
			override fun onActivityDestroyed(activity: Activity) = Unit
		})
	}

	private fun wrap(activity: Activity) {
		val window = activity.window ?: return
		val original = window.callback ?: return
		if (original is ClickCallback) return
		window.callback = ClickCallback(activity, original)
		// Older screens make their own click sound on OK as well as ours, which sounded like a double press.
		// NOVA plays every sound itself, so switch theirs off.
		window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
			newFocus?.isSoundEffectsEnabled = false
		}
	}

	private class ClickCallback(
		private val activity: Activity,
		private val original: Window.Callback,
	) : Window.Callback by original {
		override fun dispatchKeyEvent(event: KeyEvent): Boolean {
			val effect = if (event.action == KeyEvent.ACTION_DOWN) soundFor(event.keyCode) else null
			if (effect == null) return original.dispatchKeyEvent(event)

			// First press: tick straight away, like the Fire TV does.
			if (event.repeatCount == 0) {
				click(activity, effect)
				return original.dispatchKeyEvent(event)
			}

			// OK held down: no machine-gun clicking.
			if (effect == AudioManager.FX_KEY_CLICK) return original.dispatchKeyEvent(event)

			// Direction held down: only tick when the highlight really moves to another card.
			val before = activity.currentFocus
			val handled = original.dispatchKeyEvent(event)
			activity.window?.decorView?.post {
				val after = activity.currentFocus
				val moved = after !== before || (handled && after?.isCompose() == true)
				if (moved) heldClick(activity, effect)
			}
			return handled
		}
	}

	private fun android.view.View.isCompose(): Boolean = javaClass.name.contains("ComposeView")

	private var lastHeld = 0L

	private fun heldClick(activity: Activity, effect: Int) {
		val now = SystemClock.uptimeMillis()
		if (now - lastHeld < HELD_GAP_MS) return
		lastHeld = now
		click(activity, effect)
	}

	private fun soundFor(keyCode: Int): Int? = when (keyCode) {
		KeyEvent.KEYCODE_DPAD_UP -> AudioManager.FX_FOCUS_NAVIGATION_UP
		KeyEvent.KEYCODE_DPAD_DOWN -> AudioManager.FX_FOCUS_NAVIGATION_DOWN
		KeyEvent.KEYCODE_DPAD_LEFT -> AudioManager.FX_FOCUS_NAVIGATION_LEFT
		KeyEvent.KEYCODE_DPAD_RIGHT -> AudioManager.FX_FOCUS_NAVIGATION_RIGHT
		KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> AudioManager.FX_KEY_CLICK
		else -> null
	}

	private fun click(activity: Activity, effect: Int) {
		val manager = audioManager ?: return
		val now = SystemClock.uptimeMillis()
		if (now - lastPlayed < MIN_GAP_MS) return
		if (!isEnabled(activity)) return
		if (isWatching(activity)) return
		lastPlayed = now
		manager.playSoundEffect(effect)
	}

	/** No clicks over a film, episode, photo or live TV channel. */
	private fun isWatching(activity: Activity): Boolean {
		if (activity is org.jellyfin.androidtv.ui.iptv.NovaIptvActivity && activity.isWatching) return true
		val name = activity.javaClass.simpleName
		if (name.contains("Player", ignoreCase = true)) return true
		if (activity is FragmentActivity) {
			return activity.supportFragmentManager.fragments.any { it.isWatchingFragment() }
		}
		return false
	}

	private fun Fragment.isWatchingFragment(): Boolean {
		if (!isVisible) return false
		val name = javaClass.simpleName
		if (name.contains("PlaybackOverlay") || name.contains("VideoPlayer") || name.contains("PhotoPlayer") ||
			name.contains("NextUp") || name.contains("StillWatching")
		) return true
		return childFragmentManager.fragments.any { it.isWatchingFragment() }
	}
}

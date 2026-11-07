package org.jellyfin.androidtv.ui.navigation

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** One repeat clock for every app activity; native repeat rates never stack. */
class NovaDpadDispatcher(
    private val activity: FragmentActivity,
    private val deliver: (KeyEvent) -> Boolean,
) : DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private var held: KeyEvent? = null
    // When the system last confirmed the button is still down (its own repeat
    // events). If the release goes to another window, e.g. the on-screen keyboard
    // that just opened, these stop arriving and the auto-repeat ends by itself
    // instead of moving on forever.
    private var lastConfirmed = 0L
    private var repeating = false
    // Some remotes (and TV remotes passed through HDMI-CEC) send the same press
    // twice a few milliseconds apart, which moved two titles per press.
    private var lastPressKey = 0
    private var lastPressAt = 0L
    private val confirmTimeout = ViewConfiguration.getKeyRepeatTimeout().toLong() + 250L
    private val repeatStep = object : Runnable {
        override fun run() {
            val event = held ?: return
            if (!activity.hasWindowFocus()) { cancel(); return }
            val now = SystemClock.uptimeMillis()
            if (now - lastConfirmed > confirmTimeout) { cancel(); return }
            // Each scheduled step is a single press for older native key listeners.
            deliver(KeyEvent(now, now, KeyEvent.ACTION_DOWN, event.keyCode, 0,
                event.metaState, event.deviceId, event.scanCode, event.flags, event.source))
            activity.onUserInteraction()
            if (held != null) handler.postDelayed(this, 180L)
        }
    }

    init { activity.lifecycle.addObserver(this) }

    fun dispatch(event: KeyEvent): Boolean {
        if (!isDirection(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_DOWN) cancel()
            return deliver(event)
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount != 0) {
                    if (held?.keyCode == event.keyCode) {
                        lastConfirmed = SystemClock.uptimeMillis()
                        // Only start NOVA's steady repeat once the remote itself says the
                        // button is being held. A normal tap never repeats, so one press
                        // always moves exactly one title.
                        if (!repeating) {
                            repeating = true
                            handler.removeCallbacks(repeatStep)
                            handler.post(repeatStep)
                        }
                    }
                    return true
                }
                if (held?.keyCode == event.keyCode && held?.downTime == event.downTime) return true
                cancel()
                val now = SystemClock.uptimeMillis()
                if (event.keyCode == lastPressKey && now - lastPressAt < DUPLICATE_WINDOW_MS) return true
                lastPressKey = event.keyCode
                lastPressAt = now
                held = KeyEvent(event)
                lastConfirmed = now
                repeating = false
                return deliver(event)
            }
            KeyEvent.ACTION_UP -> {
                if (held?.keyCode == event.keyCode) cancel()
                // Preserve release semantics for native player/settings controls.
                return deliver(event)
            }
        }
        return true
    }

    fun cancel() { held = null; repeating = false; handler.removeCallbacks(repeatStep) }
    override fun onPause(owner: LifecycleOwner) = cancel()
    override fun onDestroy(owner: LifecycleOwner) {
        cancel()
        owner.lifecycle.removeObserver(this)
    }

    companion object {
        private const val DUPLICATE_WINDOW_MS = 90L

        fun isDirection(code: Int) = code == KeyEvent.KEYCODE_DPAD_LEFT ||
            code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_UP ||
            code == KeyEvent.KEYCODE_DPAD_DOWN
    }
}

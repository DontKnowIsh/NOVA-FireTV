package org.jellyfin.androidtv.ui.navigation

import android.content.Context
import android.view.KeyEvent
import android.widget.FrameLayout

/** Routes mixed Compose/Leanback pages before recycled cards can lose input. */
class NovaRemoteLayout(context: Context) : FrameLayout(context) {
    var directionHandler: ((KeyEvent) -> Boolean)? = null

    /** The remote's ☰ button: NOVA's poster menu. Return true when handled. */
    var longPressHandler: (() -> Boolean)? = null

    private var menuFired = false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // The remote's ☰ button opens NOVA's poster menu too (never Jellyfin's).
        if (event.keyCode == KeyEvent.KEYCODE_MENU && longPressHandler != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) menuFired = longPressHandler?.invoke() == true
                if (menuFired) return true
            } else if (event.action == KeyEvent.ACTION_UP && menuFired) {
                menuFired = false
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        if (!NovaDpadDispatcher.isDirection(event.keyCode)) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && directionHandler?.invoke(event) != true) {
            super.dispatchKeyEvent(event)
        }
        return true
    }

    fun focusCompose(action: () -> Unit) {
        post {
            getChildAt(0)?.requestFocus()
            action()
        }
    }
}

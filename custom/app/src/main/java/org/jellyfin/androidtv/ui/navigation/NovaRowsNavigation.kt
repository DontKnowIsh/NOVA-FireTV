package org.jellyfin.androidtv.ui.navigation

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.Presenter
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter

/** Logical positions survive animations, adapter refreshes and focus handoffs. */
class NovaRowsNavigation(
    private val fragment: RowsSupportFragment,
    private val onLeft: () -> Unit,
    private val onUp: () -> Unit,
    private val onMenu: (() -> Unit)? = null,
) {
    private var rowPosition = 0
    private val cards = mutableMapOf<Int, Int>()
    private var generation = 0
    private var selecting = false
    private var enabled = false
    private var lastEventDown = -1L
    private var lastEventTime = -1L
    private var lastEventKey = 0

    fun onSelection(item: Any?, row: Any?) {
        if (selecting || !enabled || item == null || row !is ListRow) return
        val index = (0 until row.adapter.size()).firstOrNull { row.adapter.get(it) === item } ?: return
        rowPosition = fragment.selectedPosition.coerceAtLeast(0)
        cards[rowPosition] = index
    }

    private fun select(row: Int, card: Int) {
        val list = fragment.adapter.get(row) as? ListRow ?: return
        if (list.adapter.size() == 0) return
        val position = card.coerceIn(0, list.adapter.size() - 1)
        rowPosition = row
        cards[row] = position
        selecting = true
        val token = ++generation
        val task = ListRowPresenter.SelectItemViewHolderTask(position).apply {
            isSmoothScroll = true
            itemTask = object : Presenter.ViewHolderTask() {
                override fun run(holder: Presenter.ViewHolder) {
                    if (!enabled || token != generation) return
                    selecting = false
                    holder.view.requestFocus()
                }
            }
        }
        fragment.setSelectedPosition(row, true, task)
    }

    private fun loadMoreThenMove(row: Int, card: Int) {
        val list = fragment.adapter?.get(row) as? ListRow ?: return
        val rowAdapter = list.adapter as? ItemRowAdapter ?: return
        rowAdapter.loadMoreItemsIfNeeded(card)
        val token = generation
        val view = fragment.view ?: return
        var tries = 0
        val check = object : Runnable {
            override fun run() {
                if (!enabled || token != generation || rowPosition != row) return
                if (list.adapter.size() > card + 1) {
                    select(row, card + 1)
                } else if (++tries < 20) {
                    view.postDelayed(this, 250L)
                }
            }
        }
        view.postDelayed(check, 250L)
    }

    /**
     * Moves straight to [index] in [row] (the A–Z bar), loading more titles first
     * if that one hasn't been fetched yet.
     */
    fun jumpTo(row: Int, index: Int) {
        val view = fragment.view ?: return
        val adapter = fragment.adapter ?: return
        if (row !in 0 until adapter.size() || index < 0) return
        val list = adapter.get(row) as? ListRow ?: return
        enabled = true
        val grid = fragment.verticalGridView ?: return
        grid.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        grid.isFocusable = true
        grid.requestFocus()
        rowPosition = row
        if (list.adapter.size() > index) {
            select(row, index)
            return
        }
        // Show the end of what's loaded while the rest arrives, then step on.
        if (list.adapter.size() > 0) select(row, list.adapter.size() - 1)
        val token = generation
        var tries = 0
        val step = object : Runnable {
            override fun run() {
                if (!enabled || token != generation) return
                val size = list.adapter.size()
                if (size > index) {
                    select(row, index)
                    return
                }
                if (++tries > 150) return
                (list.adapter as? ItemRowAdapter)?.loadMoreItemsIfNeeded((size - 1).coerceAtLeast(0))
                view.postDelayed(this, 200L)
            }
        }
        view.post(step)
    }

    /** The row and card the remote is on (for long-press menus), or null. */
    fun currentRowItem(): Pair<ListRow, Any>? {
        if (!enabled) {
            // Leanback put the focus there itself: use what's really selected.
            if (fragment.view == null || fragment.verticalGridView?.hasFocus() != true) return null
            val adapter = fragment.adapter ?: return null
            val row = fragment.selectedPosition.takeIf { it in 0 until adapter.size() } ?: return null
            val list = adapter.get(row) as? ListRow ?: return null
            val card = (fragment.getRowViewHolder(row) as? ListRowPresenter.ViewHolder)?.gridView?.selectedPosition ?: return null
            if (card !in 0 until list.adapter.size()) return null
            return list to (list.adapter.get(card) ?: return null)
        }
        val adapter = fragment.adapter ?: return null
        if (adapter.size() == 0) return null
        val row = rowPosition.coerceIn(0, adapter.size() - 1)
        val list = adapter.get(row) as? ListRow ?: return null
        if (list.adapter.size() == 0) return null
        val card = (cards[row] ?: 0).coerceIn(0, list.adapter.size() - 1)
        return list to (list.adapter.get(card) ?: return null)
    }

    fun requestCardFocus() {
        if (fragment.view == null) return
        val adapter = fragment.adapter ?: return
        if (adapter.size() == 0) return
        val savedRow = rowPosition.coerceIn(0, adapter.size() - 1)
        fun hasCards(index: Int): Boolean {
            val list = adapter.get(index) as? ListRow
            return list != null && list.adapter.size() > 0
        }
        val row = if (hasCards(savedRow)) savedRow else
            (0 until adapter.size()).firstOrNull(::hasCards) ?: return
        val card = cards[row] ?: 0
        enabled = true
        val grid = fragment.verticalGridView ?: return
        grid.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        grid.isFocusable = true
        grid.requestFocus()
        select(row, card)
    }

    private fun leave(action: () -> Unit) {
        enabled = false
        selecting = false
        generation++
        fragment.verticalGridView?.apply {
            stopScroll()
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            isFocusable = false
        }
        action()
    }

    fun handle(event: KeyEvent): Boolean {
        // The page can outlive its rows (e.g. while closing): ignore keys then.
        if (fragment.view == null || fragment.verticalGridView == null) return false
        if (!enabled) {
            // Leanback can put focus on a poster by itself when the page loads (before
            // NOVA took over). Adopt that position instead of letting the stock row
            // move by itself, which skipped titles and stopped short of the end.
            if (fragment.view == null || !fragment.verticalGridView.hasFocus()) return false
            enabled = true
            selecting = false
            rowPosition = fragment.selectedPosition.coerceAtLeast(0)
        }
        if (!NovaDpadDispatcher.isDirection(event.keyCode) && event.keyCode != KeyEvent.KEYCODE_MENU) return false
        if (event.action != KeyEvent.ACTION_DOWN) return true
        // The same press can reach us twice (page handler and the grid's own key
        // listener); only act on it once so one tap is always one step.
        if (event.downTime == lastEventDown && event.eventTime == lastEventTime && event.keyCode == lastEventKey) return true
        lastEventDown = event.downTime
        lastEventTime = event.eventTime
        lastEventKey = event.keyCode
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            val action = onMenu ?: return false
            leave(action)
            return true
        }
        val adapter = fragment.adapter ?: return true
        if (adapter.size() == 0) { leave(onUp); return true }
        var row = rowPosition.coerceIn(0, adapter.size() - 1)
        var list = adapter.get(row) as? ListRow
        if (list == null || list.adapter.size() == 0) {
            row = (0 until adapter.size()).firstOrNull {
                val candidate = adapter.get(it) as? ListRow
                candidate != null && candidate.adapter.size() > 0
            } ?: run { leave(onUp); return true }
            list = adapter.get(row) as ListRow
        }
        // Trust the poster that is really selected on screen when no move is in
        // flight, so a stale remembered position can never make Left skip a title.
        val onScreen = if (selecting) null else
            (fragment.getRowViewHolder(row) as? ListRowPresenter.ViewHolder)?.gridView?.selectedPosition?.takeIf { it >= 0 }
        val card = (onScreen ?: cards[row] ?: 0).coerceIn(0, list.adapter.size() - 1)
        rowPosition = row
        cards[row] = card
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (card == 0) leave(onLeft) else select(row, card - 1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (card < list.adapter.size() - 1) {
                select(row, card + 1)
            } else {
                // At the last title loaded so far: fetch the next batch and step on
                // as soon as it arrives, instead of stopping before the real end.
                loadMoreThenMove(row, card)
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                val step = if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                var next = row + step
                while (next in 0 until adapter.size()) {
                    val candidate = adapter.get(next) as? ListRow
                    if (candidate != null && candidate.adapter.size() > 0) break
                    next += step
                }
                if (next in 0 until adapter.size()) select(next, cards[next] ?: card)
                else if (step < 0) leave(onUp)
            }
        }
        return true
    }
}

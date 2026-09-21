package org.jellyfin.androidtv.ui.search

import android.content.Context
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.CustomListRowPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter

class SearchFragmentDelegate(
	private val context: Context,
	private val backgroundService: BackgroundService,
	private val itemLauncher: ItemLauncher,
) {
	val rowsAdapter = MutableObjectAdapter<Row>(CustomListRowPresenter())

	fun showResults(searchResultGroups: Collection<SearchResultGroup>) {
		rowsAdapter.clear()
		val adapters = mutableListOf<ItemRowAdapter>()
		for ((labelRes, baseItems) in searchResultGroups) {
			if (baseItems.isEmpty()) continue
			val adapter = ItemRowAdapter(
				context,
				baseItems.toList(),
				CardPresenter(false, ImageType.POSTER, 150, false),
				rowsAdapter,
				QueryType.Search,
			).apply {
				setRow(ListRow(HeaderItem(context.getString(labelRes)), this))
			}
			adapters.add(adapter)
		}
		for (adapter in adapters) adapter.Retrieve()
	}

	val onItemViewClickedListener = OnItemViewClickedListener { _, item, _, row ->
		if (item !is BaseRowItem || row !is ListRow) return@OnItemViewClickedListener
		val adapter = row.adapter as? ItemRowAdapter ?: return@OnItemViewClickedListener
		itemLauncher.launch(item, adapter, context)
	}

	val onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
		val baseItem = (item as? BaseRowItem)?.baseItem
		if (baseItem != null) backgroundService.setBackground(baseItem)
		else backgroundService.clearBackgrounds()
	}
}

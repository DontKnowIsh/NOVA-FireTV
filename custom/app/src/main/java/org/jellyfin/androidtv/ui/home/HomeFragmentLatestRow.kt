package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest

class HomeFragmentLatestRow(
	private val userRepository: UserRepository,
	private val userViews: Collection<BaseItemDto>,
) : HomeFragmentRow {
	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		val configuration = userRepository.currentUser.value?.configuration
		val latestItemsExcludes = configuration?.latestItemsExcludes.orEmpty()

		userViews
			.filterNot { item -> item.collectionType in EXCLUDED_COLLECTION_TYPES || item.id in latestItemsExcludes }
			.map { item ->
				val title = context.getString(R.string.lbl_latest_in, item.name)

				if (item.collectionType == CollectionType.TVSHOWS) {
					// Home should show each TV series once, not one card per newly-added episode.
					val request = GetItemsRequest(
						fields = ItemRepository.browseFields,
						imageTypeLimit = 1,
						parentId = item.id,
						includeItemTypes = setOf(BaseItemKind.SERIES),
						recursive = true,
						sortBy = setOf(ItemSortBy.DATE_CREATED),
						sortOrder = setOf(SortOrder.DESCENDING),
						limit = ITEM_LIMIT,
						enableTotalRecordCount = false,
					)

					HomeFragmentBrowseRowDefRow(
						BrowseRowDef(
							title,
							request,
							ITEM_LIMIT,
							arrayOf(ChangeTriggerType.LibraryUpdated),
						)
					)
				} else {
					val request = GetLatestMediaRequest(
						fields = ItemRepository.browseFields,
						imageTypeLimit = 1,
						parentId = item.id,
						groupItems = true,
						limit = ITEM_LIMIT,
					)

					HomeFragmentBrowseRowDefRow(
						BrowseRowDef(
							title,
							request,
							arrayOf(ChangeTriggerType.LibraryUpdated),
						)
					)
				}
			}.forEach { row ->
				row.addToRowsAdapter(context, cardPresenter, rowsAdapter)
			}
	}

	companion object {
		private val EXCLUDED_COLLECTION_TYPES = arrayOf(
			CollectionType.PLAYLISTS,
			CollectionType.LIVETV,
			CollectionType.BOXSETS,
			CollectionType.BOOKS,
		)

		private const val ITEM_LIMIT = 50
	}
}

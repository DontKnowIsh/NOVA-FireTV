package org.jellyfin.androidtv.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

data class HomeHeroState(
	val item: BaseItemDto? = null,
	val title: String = "Welcome back",
	val overview: String = "Browse your library and pick up where you left off.",
	val year: String = "",
	val rating: String = "",
)

object HomeHeroStateStore {
	private val _state = MutableStateFlow(HomeHeroState())
	val state: StateFlow<HomeHeroState> = _state.asStateFlow()

	fun update(item: BaseItemDto?) {
		if (item == null) return
		if (_state.value.item?.id == item.id) return

		// Library shortcut tiles are useful in rows, but they do not make a good
		// cinematic hero. Keep the previous media hero when one is selected.
		if (item.type == BaseItemKind.USER_VIEW || item.type == BaseItemKind.COLLECTION_FOLDER) return

		_state.value = HomeHeroState(
			item = item,
			title = item.name.orEmpty().ifBlank { "NOVA" },
			overview = item.overview.orEmpty().ifBlank { "Press OK to open this title." },
			year = item.productionYear?.toString().orEmpty(),
			rating = item.officialRating.orEmpty(),
		)
	}

	fun clear() {
		// Keep the current hero when focus briefly leaves a card.
	}

	fun resetToPreview() {
		_state.value = HomeHeroState()
	}
}

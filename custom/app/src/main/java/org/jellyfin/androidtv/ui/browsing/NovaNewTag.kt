package org.jellyfin.androidtv.ui.browsing

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.time.LocalDateTime
import java.time.ZoneOffset

/** "NEW" on posters: titles that reached your library in the last 7 days. */
object NovaNewTag {
	private const val DAYS = 7L

	@JvmStatic
	fun isNew(item: BaseItemDto?): Boolean {
		item ?: return false
		if (item.type !in setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE, BaseItemKind.SEASON, BaseItemKind.VIDEO)) return false
		// Discover titles are placeholders, not downloads.
		val path = item.path?.replace('\\', '/')?.lowercase().orEmpty()
		if ("jellybridge" in path || "/discover/" in path) return false
		val config = NovaSeerr.loadedConfig()
		if (config != null && NovaSeerr.isDiscoverPlaceholder(config, item)) return false
		// Shows count as new when an episode was just added.
		val added: LocalDateTime = (if (item.type == BaseItemKind.SERIES) item.dateLastMediaAdded else null)
			?: item.dateCreated
			?: return false
		val cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(DAYS)
		return added.isAfter(cutoff)
	}
}

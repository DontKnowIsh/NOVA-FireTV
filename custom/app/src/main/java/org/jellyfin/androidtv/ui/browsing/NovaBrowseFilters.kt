package org.jellyfin.androidtv.ui.browsing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

object NovaBrowseFilters {
	const val FILTER_FAVOURITES = "favourites"
	const val FILTER_GENRE = "genre"
	/** "Recently added": [RECENT_MOVIES] or [RECENT_SHOWS] as the value. */
	const val FILTER_RECENT = "recent"
	const val RECENT_MOVIES = "movies"
	const val RECENT_SHOWS = "shows"
	private const val RECENT_LIMIT = 100

	private const val GENRE_CACHE_TTL_MS = 5 * 60 * 1000L

	private data class GenreCacheEntry(
		val genres: List<String>,
		val loadedAtMs: Long,
	)

	private val genreCache = mutableMapOf<String, GenreCacheEntry>()

	@Synchronized
	private fun getCachedGenres(key: String): List<String>? {
		val entry = genreCache[key] ?: return null
		if (System.currentTimeMillis() - entry.loadedAtMs > GENRE_CACHE_TTL_MS) {
			genreCache.remove(key)
			return null
		}
		return entry.genres
	}

	@Synchronized
	private fun cacheGenres(key: String, genres: List<String>) {
		if (genres.isNotEmpty()) {
			genreCache[key] = GenreCacheEntry(genres, System.currentTimeMillis())
		}
	}

	/**
	 * Load the movie categories reliably.
	 *
	 * 1) Use Jellyfin's dedicated Genres endpoint.
	 * 2) If that intermittently returns nothing, derive the genres directly
	 *    from the Movies library metadata as a fallback.
	 * 3) Retry transient failures and cache only successful non-empty results.
	 *
	 * All network work stays on Dispatchers.IO so cancelling a menu load cannot
	 * reproduce the NetworkOnMainThreadException fixed in NOVA v1.
	 */
	suspend fun loadMovieGenres(
		api: ApiClient,
		moviesView: BaseItemDto,
	): List<String> = withContext(Dispatchers.IO) {
		val cacheKey = moviesView.id.toString()
		getCachedGenres(cacheKey)?.let { return@withContext it }

		repeat(3) { attempt ->
			val apiGenres = try {
				api.genresApi.getGenres(
					parentId = moviesView.id,
					includeItemTypes = listOf(BaseItemKind.MOVIE),
					sortBy = setOf(ItemSortBy.SORT_NAME),
					sortOrder = setOf(SortOrder.ASCENDING),
					enableImages = false,
					enableTotalRecordCount = false,
				).content.items
					.mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
					.distinctBy { it.lowercase() }
					.sortedBy { it.lowercase() }
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (_: Exception) {
				emptyList()
			}

			if (apiGenres.isNotEmpty()) {
				cacheGenres(cacheKey, apiGenres)
				return@withContext apiGenres
			}

			val scannedGenres = try {
				scanMovieGenres(api, moviesView)
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (_: Exception) {
				emptyList()
			}

			if (scannedGenres.isNotEmpty()) {
				cacheGenres(cacheKey, scannedGenres)
				return@withContext scannedGenres
			}

			if (attempt < 2) delay(300L * (attempt + 1))
		}

		emptyList()
	}

	private suspend fun scanMovieGenres(
		api: ApiClient,
		moviesView: BaseItemDto,
	): List<String> {
		val genres = linkedSetOf<String>()
		val pageSize = 200
		var startIndex = 0

		while (true) {
			val page = api.itemsApi.getItems(
				parentId = moviesView.id,
				includeItemTypes = listOf(BaseItemKind.MOVIE),
				recursive = true,
				fields = setOf(ItemFields.GENRES),
				startIndex = startIndex,
				limit = pageSize,
				enableUserData = false,
				enableImages = false,
				enableTotalRecordCount = true,
			).content

			page.items.forEach { item ->
				item.genres
					.orEmpty()
					.map(String::trim)
					.filter(String::isNotEmpty)
					.forEach(genres::add)
			}

			startIndex += page.items.size
			if (page.items.isEmpty() || startIndex >= page.totalRecordCount) break
		}

		return genres
			.distinctBy { it.lowercase() }
			.sortedBy { it.lowercase() }
	}

	/**
	 * The newest films or shows with new episodes, newest first, across your film
	 * (or TV) libraries. Libraries ticked under "Exclude from Latest" in your
	 * Jellyfin profile's Home settings are left out.
	 */
	suspend fun loadRecent(api: ApiClient, views: Collection<BaseItemDto>, shows: Boolean): List<BaseItemDto> =
		withContext(Dispatchers.IO) {
			val excluded = runCatching {
				api.userApi.getCurrentUser().content.configuration?.latestItemsExcludes.orEmpty().toSet()
			}.getOrDefault(emptySet())
			val libraries = views.filter { view ->
				val type = if (shows) org.jellyfin.sdk.model.api.CollectionType.TVSHOWS else org.jellyfin.sdk.model.api.CollectionType.MOVIES
				view.collectionType == type && view.id !in excluded
			}
			libraries.flatMap { library ->
				runCatching {
					api.itemsApi.getItems(
						org.jellyfin.sdk.model.api.request.GetItemsRequest(
							parentId = library.id,
							includeItemTypes = setOf(if (shows) BaseItemKind.SERIES else BaseItemKind.MOVIE),
							recursive = true,
							sortBy = setOf(if (shows) ItemSortBy.DATE_LAST_CONTENT_ADDED else ItemSortBy.DATE_CREATED, ItemSortBy.SORT_NAME),
							sortOrder = setOf(SortOrder.DESCENDING),
							limit = RECENT_LIMIT,
							fields = org.jellyfin.androidtv.data.repository.ItemRepository.itemFields,
						)
					).content.items
				}.getOrDefault(emptyList())
			}
				.distinctBy { it.id }
				.sortedByDescending { (if (shows) it.dateLastMediaAdded else null) ?: it.dateCreated ?: java.time.LocalDateTime.MIN }
				.take(RECENT_LIMIT)
		}
}

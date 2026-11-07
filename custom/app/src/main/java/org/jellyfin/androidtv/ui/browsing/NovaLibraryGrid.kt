package org.jellyfin.androidtv.ui.browsing

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.home.HomeHeroStateStore
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.artistsApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.request.GetAlbumArtistsRequest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber

/**
 * A Movies / TV Shows library as a grid of posters (like the Fire TV's own
 * pages), under the big preview at the top. Loads as you scroll down.
 */
internal class NovaLibraryGridState(
	/** One page of the list; [namesOnly] = just names, for the A–Z bar. */
	private val load: suspend (startIndex: Int?, limit: Int?, namesOnly: Boolean) -> BaseItemDtoQueryResult,
	/** Width ÷ height of a card: 2:3 posters, or square for artists. */
	val cardRatio: Float = 2f / 3f,
	/** "Surprise me": one random title from the whole list (null = pick from what's loaded). */
	private val random: (suspend () -> BaseItemDto?)? = null,
) {
	var items by mutableStateOf<List<BaseItemDto>>(emptyList())
		private set
	var total by mutableIntStateOf(-1)
		private set
	/** The poster focus goes back to when you come into the grid. */
	var focusedIndex by mutableIntStateOf(0)
	var focusedItem by mutableStateOf<BaseItemDto?>(null)
		private set
	var hasFocus by mutableStateOf(false)
		internal set

	val gridState = LazyGridState()
	val itemFocus = FocusRequester()

	private val loading = Mutex()
	private var sortNames: List<String>? = null

	val isEmpty get() = total == 0

	// How far through the server's list we are, and how many it has in all.
	private var fetched = 0
	private var serverTotal = -1

	/** Makes sure everything up to [index] (plus a page) is loaded. */
	suspend fun ensureLoaded(index: Int) = loading.withLock {
		while ((serverTotal < 0 || fetched < serverTotal) && items.size <= index) {
			val start = fetched
			val result = runCatching {
				withContext(Dispatchers.IO) {
					load(start, maxOf(PAGE, index - items.size + PAGE), false)
				}
			}.onFailure { Timber.w(it, "NOVA: library grid page failed") }.getOrNull() ?: return@withLock
			serverTotal = result.totalRecordCount
			if (result.items.isEmpty()) {
				serverTotal = fetched
				total = items.size
				return@withLock
			}
			fetched += result.items.size
			items = items + result.items
			total = serverTotal
		}
	}

	suspend fun reload() {
		loading.withLock {
			items = emptyList()
			total = -1
			serverTotal = -1
			fetched = 0
			sortNames = null
		}
		ensureLoaded(maxOf(focusedIndex, 0))
		focusedIndex = focusedIndex.coerceIn(0, maxOf(items.lastIndex, 0))
	}

	fun onFocused(index: Int, item: BaseItemDto) {
		focusedIndex = index
		focusedItem = item
	}

	fun remove(item: BaseItemDto) {
		items = items.filterNot { it.id == item.id }
		if (total > 0) total--
		sortNames = null
		focusedIndex = focusedIndex.coerceIn(0, maxOf(items.lastIndex, 0))
	}

	/** "Surprise me": a random title from this page. */
	suspend fun randomItem(): BaseItemDto? =
		random?.let { pick -> runCatching { withContext(Dispatchers.IO) { pick() } }.getOrNull() }
			?: items.randomOrNull()

	/** Moves focus back onto the grid, on the last poster you were on. */
	suspend fun focus(): Boolean {
		// The remembered poster may be further down than what's loaded so far.
		if (focusedIndex >= items.size) ensureLoaded(focusedIndex)
		if (items.isEmpty()) return false
		val index = focusedIndex.coerceIn(0, items.lastIndex)
		// The focus target sits on this poster, so it must be one that exists.
		focusedIndex = index
		val visible = gridState.layoutInfo.visibleItemsInfo.any { it.index == index }
		if (!visible) gridState.scrollToItem(index)
		// The poster may take a few frames to appear (more on a slow Fire Stick), and
		// asking too early doesn't always fail loudly, so check focus really arrived.
		repeat(60) {
			withFrameNanos { }
			runCatching { itemFocus.requestFocus() }
			withFrameNanos { }
			if (hasFocus) return true
		}
		return false
	}

	/** The A–Z bar: goes to the first title starting with [letter] ('#' = numbers and symbols). */
	suspend fun jumpTo(letter: Char) {
		val names = sortNames ?: runCatching {
			withContext(Dispatchers.IO) {
				load(null, null, true).items.map { (it.sortName ?: it.name).orEmpty() }
			}
		}.getOrNull()?.also { sortNames = it } ?: return
		if (names.isEmpty()) return
		val buckets = names.map(::letterOf)
		val index = buckets.indexOfFirst { it == letter }.takeIf { it >= 0 }
			?: buckets.indexOfFirst { it != '#' && letter != '#' && it > letter }.takeIf { it >= 0 }
			?: if (letter == '#') 0 else names.lastIndex
		ensureLoaded(index)
		focusedIndex = index.coerceIn(0, maxOf(items.lastIndex, 0))
		gridState.scrollToItem(focusedIndex)
		focus()
	}

	private fun letterOf(name: String): Char {
		val first = name.trim().firstOrNull()?.uppercaseChar() ?: return '#'
		return if (first in 'A'..'Z') first else '#'
	}

	companion object {
		private const val PAGE = 100

		/** Films / shows (A–Z). */
		fun items(api: ApiClient, request: GetItemsRequest) = NovaLibraryGridState(
			load = { start, limit, namesOnly ->
				val page = if (namesOnly) request.copy(
					startIndex = null,
					limit = null,
					fields = setOf(ItemFields.SORT_NAME),
					enableImages = false,
					enableUserData = false,
				) else request.copy(startIndex = start, limit = limit)
				api.itemsApi.getItems(page).content
			},
			random = {
				api.itemsApi.getItems(
					request.copy(startIndex = null, limit = 1, sortBy = setOf(ItemSortBy.RANDOM), sortOrder = null)
				).content.items.firstOrNull()
			},
		)

		/** A fixed list loaded in one go (e.g. Recently Added). */
		fun list(load: suspend () -> List<BaseItemDto>) = NovaLibraryGridState({ start, _, _ ->
			val all = if ((start ?: 0) == 0) load() else emptyList()
			BaseItemDtoQueryResult(items = all, totalRecordCount = all.size, startIndex = 0)
		})

		/** A music library's artists (A–Z), as square cards. */
		fun artists(api: ApiClient, request: GetAlbumArtistsRequest) = NovaLibraryGridState(
			load = { start, limit, namesOnly ->
				val page = if (namesOnly) request.copy(
					startIndex = null,
					limit = null,
					fields = setOf(ItemFields.SORT_NAME),
					enableImages = false,
					enableUserData = false,
				) else request.copy(startIndex = start, limit = limit)
				api.artistsApi.getAlbumArtists(page).content
			},
			cardRatio = 1f,
			random = {
				api.artistsApi.getAlbumArtists(
					request.copy(startIndex = null, limit = 1, sortBy = setOf(ItemSortBy.RANDOM), sortOrder = null)
				).content.items.firstOrNull()
			},
		)
	}
}

@Composable
internal fun NovaLibraryGrid(
	state: NovaLibraryGridState,
	api: ApiClient,
	onUp: () -> Unit,
	onLeft: () -> Unit,
	onClick: (BaseItemDto) -> Unit,
	modifier: Modifier = Modifier,
) {
	LaunchedEffect(state) { state.ensureLoaded(0) }

	// More as you get near the bottom.
	LaunchedEffect(state) {
		snapshotFlow { state.focusedIndex to state.items.size }.collect { (index, size) ->
			if (size > 0 && index >= size - 30) state.ensureLoaded(size)
		}
	}

	// Update the big preview once focus settles on a poster, so flicking stays quick.
	LaunchedEffect(state) {
		var pending: Job? = null
		snapshotFlow { state.focusedItem }.collect { item ->
			pending?.cancel()
			if (item == null) return@collect
			pending = launch {
				delay(180L)
				HomeHeroStateStore.update(item)
			}
		}
	}

	when {
		state.isEmpty -> Text(
			"Nothing here yet.",
			color = nova.textSecondary,
			fontSize = 15.sp,
			modifier = modifier.padding(start = 28.dp, top = 12.dp),
		)
		else -> LazyVerticalGrid(
			state = state.gridState,
			// Square artist cards a bit bigger than film posters.
			columns = GridCells.Adaptive(minSize = if (state.cardRatio >= 1f) 112.dp else 88.dp),
			horizontalArrangement = Arrangement.spacedBy(14.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
			contentPadding = PaddingValues(start = 24.dp, end = 30.dp, top = 14.dp, bottom = 40.dp),
			modifier = modifier.onFocusChanged { state.hasFocus = it.hasFocus },
		) {
			itemsIndexed(state.items, key = { _, item -> item.id.toString() }) { index, item ->
				NovaGridPoster(
					item = item,
					api = api,
					ratio = state.cardRatio,
					onClick = { onClick(item) },
					onFocused = { state.onFocused(index, item) },
					onKey = { key ->
						val info = state.gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
						when {
							key == Key.DirectionUp && info?.row == 0 -> { onUp(); true }
							key == Key.DirectionLeft && info?.column == 0 -> { onLeft(); true }
							else -> false
						}
					},
					modifier = if (index == state.focusedIndex) Modifier.focusRequester(state.itemFocus) else Modifier,
				)
			}
		}
	}
}

@Composable
private fun NovaGridPoster(
	item: BaseItemDto,
	api: ApiClient,
	ratio: Float,
	onClick: () -> Unit,
	onFocused: () -> Unit,
	onKey: (Key) -> Boolean,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(nova.corner(8))
	val poster = remember(item.id) {
		item.itemImages[ImageType.PRIMARY]?.getUrl(api, maxWidth = 300, maxHeight = 390)
	}

	val isNew = remember(item.id, item.dateCreated, item.dateLastMediaAdded) { NovaNewTag.isNew(item) }

	LaunchedEffect(focused) { if (focused) onFocused() }

	Box(
		modifier = modifier
			.fillMaxWidth()
			.aspectRatio(ratio)
			.scale(if (focused) 1.06f else 1f)
			.clip(shape)
			.background(nova.surfaceRaised)
			.then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
			.onPreviewKeyEvent { event ->
				when {
					event.type != KeyEventType.KeyDown -> false
					event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter -> {
						if (event.nativeKeyEvent.repeatCount == 0) onClick()
						true
					}
					else -> onKey(event.key)
				}
			}
			.focusable(interactionSource = interactionSource),
		contentAlignment = Alignment.Center,
	) {
		// The title shows until the poster arrives (or if there isn't one).
		Text(
			item.name.orEmpty(),
			color = nova.textMuted,
			fontSize = 12.sp,
			fontWeight = FontWeight.Bold,
			textAlign = TextAlign.Center,
			maxLines = 3,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(8.dp),
		)
		if (poster != null) {
			AsyncImage(
				url = poster,
				aspectRatio = ratio,
				scaleType = ImageView.ScaleType.CENTER_CROP,
				modifier = Modifier.fillMaxSize(),
			)
		}
		// Added in the last 7 days (shows: a new episode), same as on the Home rows.
		if (isNew) {
			Box(
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(5.dp)
					.background(nova.accent, RoundedCornerShape(nova.corner(3)))
					.padding(horizontal = 6.dp, vertical = 2.dp),
			) {
				Text("NEW", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
			}
		}
	}
}

package org.jellyfin.androidtv.ui.browsing

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import org.jellyfin.androidtv.ui.withAlpha
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.androidtv.ui.home.NovaSideRail
import org.jellyfin.androidtv.ui.home.NovaRailPage
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.navigation.fragmentDestination
import org.jellyfin.androidtv.ui.search.composable.SearchTextInput
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.compose.koinInject

object NovaDownloadDestinations {
	val download = fragmentDestination<NovaDownloadFragment>()
}

/**
 * Find any film or show (via Seerr/TMDB) and download it.
 * Results are split into Movies and TV Shows; each card shows its status in
 * Radarr's colours with a progress bar while downloading.
 */
class NovaDownloadFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		JellyfinTheme {
			val api = koinInject<ApiClient>()
			val scope = rememberCoroutineScope()

			var config by remember { mutableStateOf<NovaSeerr.Config?>(null) }
			var configChecked by remember { mutableStateOf(false) }
			var query by remember { mutableStateOf("") }
			var submittedQuery by remember { mutableStateOf("") }
			var searching by remember { mutableStateOf(false) }
			var results by remember { mutableStateOf<List<NovaSeerr.SearchResult>>(emptyList()) }
			val statuses = remember { mutableStateMapOf<String, NovaSeerr.Status>() }
			var seasonPickerFor by remember { mutableStateOf<NovaSeerr.SearchResult?>(null) }
			// "Choose a copy": the title and the (TV) seasons still to go; [null] for a film.
			var copyFor by remember { mutableStateOf<NovaSeerr.SearchResult?>(null) }
			var copyQueue by remember { mutableStateOf<List<Int?>>(emptyList()) }
			var copyTotal by remember { mutableStateOf(0) }
			val busy = remember { mutableStateMapOf<String, Boolean>() }
			// Get YouTube's player ready so preview trailers start quickly.
			org.jellyfin.androidtv.ui.home.NovaTrailerWarmUp()

			val inputFocus = remember { FocusRequester() }
			val railFocus = remember { FocusRequester() }
			val firstMovieFocus = remember { FocusRequester() }
			val firstShowFocus = remember { FocusRequester() }
			// Remember each row's scroll so Down from the search box can bring the
			// first card back on screen before focusing it.
			val movieRowState = rememberLazyListState()
			val showRowState = rememberLazyListState()

			// Category chips under the search box: Discover, English (+ Hindi, Punjabi with "Indian films" on).
			var category by remember { mutableStateOf(DownloadCategory.DISCOVER) }
			val categoryFocus = remember { FocusRequester() }
			// English / Hindi / Punjabi: a grid of Movies or TV Shows in that language.
			var languageShows by remember { mutableStateOf(false) }
			val mediaFocus = remember { FocusRequester() }

			// Movie Genres, Series Genres, Studios and Networks (like Seerr's Discover page).
			var tileRows by remember { mutableStateOf<List<Pair<String, List<NovaBrowseTile>>>>(emptyList()) }
			val tileRowStates = List(4) { rememberLazyListState() }
			val tileFocus = remember { List(4) { FocusRequester() } }
			// The genre / studio / network picked, and what's in it.
			var openTile by remember { mutableStateOf<NovaBrowseTile?>(null) }
			var lastTile by remember { mutableStateOf<NovaBrowseTile?>(null) }
			// Its titles as a grid, like Seerr's page for a genre / studio / network; more load as you scroll down.
			var tileItems by remember { mutableStateOf<List<NovaSeerr.SearchResult>>(emptyList()) }
			var tileNextPage by remember { mutableStateOf(1) }
			var tileHasMore by remember { mutableStateOf(false) }
			var loadingTile by remember { mutableStateOf(false) }
			var gridColumns by remember { mutableStateOf(6) }
			val gridFocus = remember { FocusRequester() }
			val languageTile = remember(category, languageShows) { category.languageTile(languageShows) }
			// What the grid shows: a picked genre / studio / network, or the chosen language.
			val gridTile = openTile ?: languageTile
			val columnState = rememberLazyListState()
			val density = LocalDensity.current
			val returnFocus = remember { FocusRequester() }

			val movies = results.filter { it.type == NovaSeerr.MediaType.MOVIE }
			val shows = results.filter { it.type == NovaSeerr.MediaType.TV }
			val browsing = query.isBlank()
			val allShown = when {
				!browsing -> results
				gridTile != null -> tileItems
				else -> emptyList()
			}
			fun keyOf(result: NovaSeerr.SearchResult) = "${result.type.apiName}-${result.tmdbId}"
			fun statusOf(result: NovaSeerr.SearchResult) = statuses[keyOf(result)] ?: result.status

			fun focusFirstResult() {
				if (browsing && gridTile != null) {
					if (tileItems.isEmpty()) return
					scope.launch {
						// Only scroll when the first row is off screen, so the page doesn't jump.
						if (columnState.layoutInfo.visibleItemsInfo.none { it.index == 1 }) columnState.scrollToItem(1)
						repeat(5) {
							withFrameNanos { }
							if (runCatching { gridFocus.requestFocus() }.isSuccess) return@launch
						}
					}
					return
				}
				val (rowState, focus) = when {
					browsing && openTile == null && category == DownloadCategory.DISCOVER && tileRows.isNotEmpty() -> tileRowStates[0] to tileFocus[0]
					browsing -> return
					movies.isNotEmpty() -> movieRowState to firstMovieFocus
					shows.isNotEmpty() -> showRowState to firstShowFocus
					else -> return
				}
				scope.launch {
					// The first card may have scrolled away (and been unloaded) after
					// browsing along the row, so scroll back to it first.
					// Discover's tiles: bring the first tile row (Movie Genres) on screen too.
					// Only when it's off screen, so the page doesn't jump about.
					if (rowState === tileRowStates[0] && columnState.layoutInfo.visibleItemsInfo.none { it.index == 1 }) {
						columnState.scrollToItem(1)
					}
					if (rowState.firstVisibleItemIndex != 0 || rowState.firstVisibleItemScrollOffset != 0) rowState.scrollToItem(0)
					repeat(5) {
						withFrameNanos { }
						if (runCatching { focus.requestFocus() }.isSuccess) return@launch
					}
				}
			}

			LaunchedEffect(Unit) {
				config = NovaSeerr.config(api)
				NovaAgeRatings.config = config
				configChecked = true
				runCatching { inputFocus.requestFocus() }
			}

			LaunchedEffect(config) {
				val cfg = config ?: return@LaunchedEffect
				tileRows = NovaDownloadBrowse.tileRows(cfg)
			}

			// A genre / studio / network was picked (or a language chip): load it as a grid.
			LaunchedEffect(gridTile, config) {
				val cfg = config
				val tile = gridTile
				if (cfg == null || tile == null) {
					tileItems = emptyList()
					tileHasMore = false
					// Back from a genre: return to the tile you picked.
					val back = lastTile ?: return@LaunchedEffect
					val row = tileRows.indexOfFirst { (_, tiles) -> back in tiles }
					if (row >= 0) {
						// Bring the row, then the tile, back on screen before focusing it.
						withFrameNanos { }
						columnState.scrollToItem(1 + row)
						tileRowStates[row].scrollToItem(tileRows[row].second.indexOf(back).coerceAtLeast(0))
						repeat(5) {
							withFrameNanos { }
							if (runCatching { returnFocus.requestFocus() }.isSuccess) {
								lastTile = null
								return@LaunchedEffect
							}
						}
					}
					lastTile = null
					return@LaunchedEffect
				}
				// A picked tile: Back returns to it. A language chip: focus stays on the chips.
				val picked = tile == openTile
				if (picked) lastTile = tile
				tileItems = emptyList()
				tileNextPage = 1
				tileHasMore = true
				columnState.scrollToItem(0)
				// Enough for a few rows of the grid, then go to the first title.
				while (tileHasMore && tileItems.size < gridColumns * 3) {
					loadingTile = true
					val page = NovaDownloadBrowse.tilePage(cfg, tile, tileNextPage)
					loadingTile = false
					if (page == null) tileHasMore = false else {
						tileNextPage++
						tileItems = (tileItems + page).distinctBy { keyOf(it) }
					}
				}
				if (picked) focusFirstResult()
				// More pages as you get near the bottom.
				snapshotFlow {
					val info = columnState.layoutInfo
					(info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
				}.collect { nearEnd ->
					if (!nearEnd) return@collect
					var added = 0
					// A page can be all films you already have, so keep going until something shows.
					while (tileHasMore && added == 0) {
						loadingTile = true
						val page = NovaDownloadBrowse.tilePage(cfg, tile, tileNextPage)
						loadingTile = false
						if (page == null) tileHasMore = false else {
							tileNextPage++
							val before = tileItems.size
							tileItems = (tileItems + page).distinctBy { keyOf(it) }
							added = tileItems.size - before
						}
					}
				}
			}

			// Back closes the genre / studio / network instead of leaving the page.
			BackHandler(enabled = openTile != null) { openTile = null }

			// Search a moment after typing stops, or straight away on submit.
			LaunchedEffect(query, config) {
				val cfg = config ?: return@LaunchedEffect
				if (query.isBlank()) {
					results = emptyList()
					submittedQuery = ""
					return@LaunchedEffect
				}
				if (query != submittedQuery) delay(700L)
				searching = true
				results = NovaSeerr.search(cfg, query)
				submittedQuery = query
				statuses.clear()
				searching = false
			}

			// Keep in-progress items fresh.
			LaunchedEffect(allShown, config) {
				val cfg = config ?: return@LaunchedEffect
				while (true) {
					delay(5_000L)
					allShown
						.filter {
							when (statusOf(it).state) {
								NovaSeerr.State.DOWNLOADING,
								NovaSeerr.State.SEARCHING,
								NovaSeerr.State.PENDING_APPROVAL -> true
								else -> false
							}
						}
						.take(12)
						.forEach { result ->
							NovaSeerr.status(cfg, result.type, result.tmdbId)
								// A TV season picked by hand isn't known to Seerr straight away;
								// keep showing it as downloading rather than "not requested".
								?.takeIf { it.state != NovaSeerr.State.NOT_REQUESTED }
								?.let { statuses[keyOf(result)] = it }
						}
				}
			}

			// OK on a card: the preview (trailer, description, Download button) first.
			var previewFor by remember { mutableStateOf<NovaSeerr.SearchResult?>(null) }
			// The card the preview was opened from, so closing it puts you back there.
			var returnKey by remember { mutableStateOf<String?>(null) }
			val previewReturnFocus = remember { FocusRequester() }
			var returnPending by remember { mutableStateOf(false) }
			fun onCardClick(result: NovaSeerr.SearchResult) {
				if (config == null) return
				returnKey = keyOf(result)
				previewFor = result
			}
			fun closePreview() {
				previewFor = null
				returnPending = true
			}
			LaunchedEffect(returnPending) {
				if (!returnPending) return@LaunchedEffect
				repeat(20) {
					withFrameNanos { }
					if (!returnPending) return@LaunchedEffect
					runCatching { previewReturnFocus.requestFocus() }
				}
				returnPending = false
			}

			fun startDownload(result: NovaSeerr.SearchResult) {
				val cfg = config ?: return
				val key = keyOf(result)
				if (busy[key] == true) return
				// TV shows: ask which seasons first.
				if (result.type == NovaSeerr.MediaType.TV && statusOf(result).state != NovaSeerr.State.DOWNLOADED) {
					seasonPickerFor = result
					return
				}
				// Films: list every copy so the best one can be picked.
				if (statusOf(result).state.canChooseCopy()) {
					copyFor = result
					copyQueue = listOf(null)
					copyTotal = 1
					return
				}
				scope.launch {
					busy[key] = true
					try {
						if (statusOf(result).state == NovaSeerr.State.NOT_REQUESTED) {
							NovaSeerr.request(cfg, result.type, result.tmdbId)
						}
						NovaSeerr.status(cfg, result.type, result.tmdbId)?.let { statuses[key] = it }
					} finally {
						busy[key] = false
					}
				}
			}

			val picker = seasonPickerFor
			val pickerConfig = config
			if (picker != null && pickerConfig != null) {
				NovaSeasonPicker(
					config = pickerConfig,
					tmdbId = picker.tmdbId,
					title = picker.title,
					onDismiss = { seasonPickerFor = null },
					onRequested = {
						seasonPickerFor = null
						scope.launch {
							NovaSeerr.status(pickerConfig, picker.type, picker.tmdbId)?.let { statuses[keyOf(picker)] = it }
						}
					},
					onSeasonChosen = { season ->
						seasonPickerFor = null
						copyFor = picker
						copyQueue = listOf(season)
						copyTotal = 1
					},
					onAllMissingChosen = { seasons ->
						seasonPickerFor = null
						copyFor = picker
						copyQueue = seasons
						copyTotal = seasons.size
					},
				)
			}

			val result = copyFor
			if (result != null && copyQueue.isNotEmpty() && pickerConfig != null) {
				val season = copyQueue.first()
				val key = keyOf(result)
				fun next() {
					copyQueue = copyQueue.drop(1)
					if (copyQueue.isEmpty()) copyFor = null
				}
				NovaReleasePicker(
					config = pickerConfig,
					type = result.type,
					tmdbId = result.tmdbId,
					title = result.title,
					season = season,
					progressLabel = if (copyTotal > 1) "${copyTotal - copyQueue.size + 1} of $copyTotal" else null,
					onDismiss = {
						copyQueue = emptyList()
						copyFor = null
					},
					onAutomatic = {
						next()
						scope.launch {
							busy[key] = true
							try {
								NovaSeerr.request(pickerConfig, result.type, result.tmdbId, season?.let { listOf(it) })
								NovaSeerr.status(pickerConfig, result.type, result.tmdbId)?.let { statuses[key] = it }
							} finally {
								busy[key] = false
							}
						}
					},
					onGrabbed = {
						next()
						statuses[key] = NovaSeerr.Status(NovaSeerr.State.DOWNLOADING, 0f)
						scope.launch {
							delay(3_000L)
							NovaSeerr.status(pickerConfig, result.type, result.tmdbId)
								?.takeIf { it.state != NovaSeerr.State.NOT_REQUESTED }
								?.let { statuses[key] = it }
						}
					},
				)
			}

			androidx.compose.runtime.CompositionLocalProvider(LocalPreviewReturn provides returnKey?.let { PreviewReturn(it, previewReturnFocus) { returnPending = false } }) {
			Box(modifier = Modifier.fillMaxSize()) {
			Row(modifier = Modifier.fillMaxSize().background(nova.bgDeep)) {
				// Left rail
				NovaSideRail(
					current = NovaRailPage.Download,
					focusRequester = railFocus,
					// Right from the side bar goes to the first title, not the search box.
					onRight = {
						val hasTiles = browsing && openTile == null && category == DownloadCategory.DISCOVER && tileRows.isNotEmpty()
						if (allShown.isNotEmpty() || hasTiles) focusFirstResult() else runCatching { inputFocus.requestFocus() }
					},
				)

				// Content
				LazyColumn(
					state = columnState,
					modifier = Modifier
						.fillMaxSize()
						.background(nova.bg)
						// As many 134dp posters (12dp apart) as fit across.
						.onSizeChanged { size ->
							val width = with(density) { size.width.toDp() } - 66.dp + 12.dp
							gridColumns = (width / 146.dp).toInt().coerceIn(3, 9)
						},
					contentPadding = PaddingValues(start = 30.dp, end = 0.dp, top = 22.dp, bottom = 24.dp),
				) {
					item {
						Text("DOWNLOAD", color = nova.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
						Spacer(Modifier.height(5.dp))
						Text("Find any film or show", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
						Spacer(Modifier.height(5.dp))
						Text(
							"Press OK on a title to download it. Colours show what's happening.",
							color = nova.textMuted,
							fontSize = 13.sp,
						)
						Spacer(Modifier.height(14.dp))
						Box(modifier = Modifier.padding(end = 36.dp)) {
							SearchTextInput(
								query = query,
								onQueryChange = { query = it },
								onQuerySubmit = { focusFirstResult() },
								modifier = Modifier
									.fillMaxWidth()
									.focusRequester(inputFocus)
									.onPreviewKeyEvent { event ->
										if (event.key == Key.DirectionDown) {
											if (event.type == KeyEventType.KeyDown) {
												if (browsing) runCatching { categoryFocus.requestFocus() } else focusFirstResult()
											}
											true
										} else false
									}
									.focusProperties { left = railFocus },
							)
						}
						Spacer(Modifier.height(10.dp))
						StatusLegend()
						Spacer(Modifier.height(12.dp))
						if (browsing) {
							CategoryChips(
								selected = category,
								firstFocus = categoryFocus,
								onSelect = {
									openTile = null
									lastTile = null
									category = it
								},
								onUp = { runCatching { inputFocus.requestFocus() } },
								onDown = {
									if (openTile == null && languageTile != null) runCatching { mediaFocus.requestFocus() }
									else focusFirstResult()
								},
							)
							Spacer(Modifier.height(12.dp))
							if (openTile == null && languageTile != null) {
								MediaChips(
									shows = languageShows,
									firstFocus = mediaFocus,
									onSelect = { languageShows = it },
									onUp = { runCatching { categoryFocus.requestFocus() } },
									onDown = { focusFirstResult() },
								)
								Spacer(Modifier.height(12.dp))
							}
							val tile = gridTile
							if (tile != null) {
								if (tile == openTile) {
									NovaTileHeader(tile)
									Spacer(Modifier.height(12.dp))
								}
								if (tileItems.isEmpty() && loadingTile) InfoText("Loading ${tile.name}…")
								else if (tileItems.isEmpty() && !tileHasMore) InfoText("Nothing new to download in ${tile.name}.")
							}
						}

						when {
							configChecked && config == null -> InfoText(
								"Downloading from NOVA needs an admin account and the JellyBridge plugin set up on the server.",
							)
							searching -> InfoText("Searching…")
							submittedQuery.isNotBlank() && results.isEmpty() -> InfoText("Nothing found for \"$submittedQuery\".")
						}
					}

					if (browsing) {
						// Only on "Discover".
						if (openTile == null && category == DownloadCategory.DISCOVER) {
							tileRows.take(4).forEachIndexed { index, (title, tiles) ->
								item(key = "tiles-$title") {
									NovaTileRow(
										title = title,
										tiles = tiles,
										rowState = tileRowStates[index],
										firstFocus = tileFocus[index],
										railFocus = railFocus,
										returnTile = lastTile,
										returnFocus = returnFocus,
										onClick = { openTile = it },
									)
								}
							}
						}
					}
					// A genre / studio / network, or a language: its titles as a grid.
					val tile = gridTile
					if (browsing && tile != null) {
						val gridRows = tileItems.chunked(gridColumns)
						itemsIndexed(gridRows, key = { index, _ -> "grid-${tile.paths.first()}-$index" }) { rowIndex, row ->
							Row(
								horizontalArrangement = Arrangement.spacedBy(12.dp),
								modifier = Modifier.padding(bottom = 14.dp),
							) {
								row.forEachIndexed { column, result ->
									ResultCard(
										result = result,
										status = statusOf(result),
										busy = busy[keyOf(result)] == true,
										onClick = { onCardClick(result) },
										modifier = when {
											rowIndex == 0 && column == 0 -> Modifier.focusRequester(gridFocus).focusProperties { left = railFocus }
											column == 0 -> Modifier.focusProperties { left = railFocus }
											else -> Modifier
										},
									)
								}
							}
						}
						if (loadingTile && tileItems.isNotEmpty()) item { InfoText("Loading more…") }
					}
					if (!browsing && movies.isNotEmpty()) item {
						ResultRow(
							title = "Movies",
							results = movies,
							rowState = movieRowState,
							firstFocus = firstMovieFocus,
							railFocus = railFocus,
							statusOf = ::statusOf,
							isBusy = { busy[keyOf(it)] == true },
							onClick = ::onCardClick,
						)
					}
					if (!browsing && shows.isNotEmpty()) item {
						ResultRow(
							title = "TV Shows",
							results = shows,
							rowState = showRowState,
							firstFocus = firstShowFocus,
							railFocus = railFocus,
							statusOf = ::statusOf,
							isBusy = { busy[keyOf(it)] == true },
							onClick = ::onCardClick,
						)
					}
				}
			}

			// The preview sits on top of the page (not in a pop-up window), so the
			// trailer is drawn in the right place.
			val preview = previewFor
			val previewConfig = config
			if (preview != null && previewConfig != null) {
				NovaDownloadPreview(
					config = previewConfig,
					result = preview,
					status = statusOf(preview),
					onDownload = {
						closePreview()
						startDownload(preview)
					},
					onDismiss = { closePreview() },
				)
			}
			}
			}
		}
	}
}

@Composable
private fun InfoText(text: String) {
	Text(text, color = nova.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(end = 36.dp, bottom = 8.dp))
}

@Composable
private fun StatusLegend() {
	Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
		listOf(
			NovaStatusColors.Downloaded to "Downloaded",
			NovaStatusColors.Queued to "Downloading",
			NovaStatusColors.Missing to "Searching",
			NovaStatusColors.Unreleased to "Unreleased",
			NovaStatusColors.Waiting to "Awaiting approval",
		).forEach { (colour, label) ->
			Row(verticalAlignment = Alignment.CenterVertically) {
				Box(Modifier.size(width = 16.dp, height = 10.dp).background(colour, RoundedCornerShape(nova.corner(2))))
				Spacer(Modifier.width(5.dp))
				Text(label, color = nova.textSecondary, fontSize = 11.sp)
			}
		}
	}
}

@Composable
private fun ResultRow(
	title: String,
	results: List<NovaSeerr.SearchResult>,
	rowState: LazyListState,
	firstFocus: FocusRequester,
	railFocus: FocusRequester,
	statusOf: (NovaSeerr.SearchResult) -> NovaSeerr.Status,
	isBusy: (NovaSeerr.SearchResult) -> Boolean,
	onClick: (NovaSeerr.SearchResult) -> Unit,
) {
	Column {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
			Spacer(Modifier.width(8.dp))
			Text("${results.size}", color = nova.textFaint, fontSize = 13.sp)
		}
		Spacer(Modifier.height(8.dp))
		LazyRow(
			state = rowState,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			contentPadding = PaddingValues(end = 36.dp),
		) {
			itemsIndexed(results, key = { _, r -> "${r.type.apiName}-${r.tmdbId}" }) { index, result ->
				ResultCard(
					result = result,
					status = statusOf(result),
					busy = isBusy(result),
					onClick = { onClick(result) },
					modifier = if (index == 0) {
						Modifier.focusRequester(firstFocus).focusProperties { left = railFocus }
					} else Modifier,
				)
			}
		}
		Spacer(Modifier.height(16.dp))
	}
}

@Composable
private fun ResultCard(
	result: NovaSeerr.SearchResult,
	status: NovaSeerr.Status,
	busy: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(nova.corner(8))
	val previewReturn = LocalPreviewReturn.current

	Button(
		onClick = onClick,
		modifier = modifier
			.then(
				if (previewReturn != null && previewReturn.key == "${result.type.apiName}-${result.tmdbId}") {
					Modifier
						.focusRequester(previewReturn.focus)
						.onFocusChanged { if (it.isFocused) previewReturn.onReturned() }
				} else {
					Modifier
				},
			)
			.width(134.dp)
			.then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier),
		interactionSource = interactionSource,
		shape = shape,
		colors = ButtonDefaults.colors(
			containerColor = nova.surface,
			contentColor = Color.White,
			focusedContainerColor = nova.accent.withAlpha(0x40),
			focusedContentColor = Color.White,
		),
		contentPadding = PaddingValues(6.dp),
	) {
		Column(modifier = Modifier.fillMaxWidth()) {
			Box {
				AsyncImage(
					url = result.posterUrl,
					aspectRatio = 2f / 3f,
					scaleType = ImageView.ScaleType.CENTER_CROP,
					modifier = Modifier.fillMaxWidth().height(183.dp).background(nova.surfaceRaised),
				)
				// MOVIE / TV SHOW tag in the corner of the poster
				Box(
					modifier = Modifier
						.padding(4.dp)
						.background(
							if (result.type == NovaSeerr.MediaType.MOVIE) nova.movieTag else nova.tvTag,
							RoundedCornerShape(nova.corner(3)),
						)
						.padding(horizontal = 5.dp, vertical = 1.dp)
				) {
					Text(
						if (result.type == NovaSeerr.MediaType.MOVIE) "MOVIE" else "TV SHOW",
						color = Color.White,
						fontSize = 9.sp,
						fontWeight = FontWeight.Black,
					)
				}
			}
			Spacer(Modifier.height(5.dp))
			Text(result.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
			// Year, and the age rating (U / PG / 12A / 15 / 18) once it's looked up.
			val ageRating by androidx.compose.runtime.produceState(NovaAgeRatings.cached(result)?.ifEmpty { null }, result.tmdbId) {
				value = NovaAgeRatings.get(result)
			}
			Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(20.dp)) {
				Text(result.year.ifBlank { " " }, color = nova.textMuted, fontSize = 11.sp, maxLines = 1)
				ageRating?.let {
					Spacer(Modifier.width(6.dp))
					NovaRatingBadge(it, small = true)
				}
			}
			Spacer(Modifier.height(5.dp))
			// Status chip
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.background(status.colour(), RoundedCornerShape(nova.corner(4)))
					.padding(horizontal = 6.dp, vertical = 4.dp)
			) {
				Text(
					if (busy) "Requesting…" else status.label(),
					color = Color.White,
					fontSize = 11.sp,
					fontWeight = FontWeight.Bold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				val progress = status.progress
				if (status.state == NovaSeerr.State.DOWNLOADING && progress != null) {
					NovaProgressBar(progress, Modifier.padding(top = 3.dp).fillMaxWidth())
				}
			}
		}
	}
}

/** Download page categories: what the browse rows show before you search. */
internal enum class DownloadCategory(val label: String, val language: String?) {
	/** Movie Genres, Series Genres, Studios and Networks. */
	DISCOVER("Discover", null),
	ENGLISH("English", "en"),
	HINDI("Hindi", "hi"),
	PUNJABI("Punjabi", "pa"),
	;

	/** The language's films or shows as a grid (Seerr's "by language" list; the filter form as a fallback). */
	fun languageTile(shows: Boolean): NovaBrowseTile? {
		val lang = language ?: return null
		val kind = if (shows) "tv" else "movies"
		return NovaBrowseTile(
			name = "$label ${if (shows) "TV Shows" else "Movies"}",
			imageUrl = null,
			isLogo = false,
			paths = listOf("discover/$kind/language/$lang", "discover/$kind?originalLanguage=$lang"),
			type = if (shows) NovaSeerr.MediaType.TV else NovaSeerr.MediaType.MOVIE,
		)
	}
}

/** Movies / TV Shows under a language chip. */
@Composable
private fun MediaChips(
	shows: Boolean,
	firstFocus: FocusRequester,
	onSelect: (Boolean) -> Unit,
	onUp: () -> Unit,
	onDown: () -> Unit,
) {
	Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
		listOf(false to "Movies", true to "TV Shows").forEachIndexed { index, (value, label) ->
			val active = value == shows
			Button(
				onClick = { onSelect(value) },
				modifier = (if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
					.onPreviewKeyEvent { event ->
						when (event.key) {
							Key.DirectionUp -> {
								if (event.type == KeyEventType.KeyDown) onUp()
								true
							}
							Key.DirectionDown -> {
								if (event.type == KeyEventType.KeyDown) onDown()
								true
							}
							else -> false
						}
					},
				shape = RoundedCornerShape(nova.corner(18)),
				colors = ButtonDefaults.colors(
					containerColor = if (active) nova.accent.withAlpha(0x88) else nova.surfaceHigh.withAlpha(0x88),
					contentColor = Color.White,
					focusedContainerColor = nova.textPrimary,
					focusedContentColor = nova.surface,
				),
				contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
			) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
		}
	}
}

@Composable
private fun CategoryChips(
	selected: DownloadCategory,
	firstFocus: FocusRequester,
	onSelect: (DownloadCategory) -> Unit,
	onUp: () -> Unit,
	onDown: () -> Unit,
) {
	Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
		DownloadCategory.entries
			// Hindi and Punjabi only with Settings → "Indian films" on.
			.filter { it == DownloadCategory.DISCOVER || it == DownloadCategory.ENGLISH || NovaIndianFilms.isEnabled() }
			.forEachIndexed { index, category ->
			val active = category == selected
			Button(
				onClick = { onSelect(category) },
				modifier = (if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
					.onPreviewKeyEvent { event ->
						when (event.key) {
							Key.DirectionUp -> {
								if (event.type == KeyEventType.KeyDown) onUp()
								true
							}
							Key.DirectionDown -> {
								if (event.type == KeyEventType.KeyDown) onDown()
								true
							}
							else -> false
						}
					},
				shape = RoundedCornerShape(nova.corner(18)),
				colors = ButtonDefaults.colors(
					containerColor = if (active) nova.accent else nova.surfaceHigh.withAlpha(0xAA),
					contentColor = if (active) nova.onAccent else Color.White,
					focusedContainerColor = nova.textPrimary,
					focusedContentColor = nova.surface,
				),
				contentPadding = PaddingValues(horizontal = 18.dp, vertical = 7.dp),
			) { Text(category.label, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
		}
	}
}

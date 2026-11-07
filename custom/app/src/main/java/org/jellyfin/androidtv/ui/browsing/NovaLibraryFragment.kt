package org.jellyfin.androidtv.ui.browsing

import org.jellyfin.androidtv.ui.withAlpha
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.androidtv.ui.home.NovaSideRail
import org.jellyfin.androidtv.ui.home.NovaRailPage
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
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
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.content
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Row as LeanbackRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.extensions.itemsApi
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.constant.ImageType as CardImageType
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.home.HomeFragmentBrowseRowDefRow
import org.jellyfin.androidtv.ui.home.HomeHeroStateStore
import org.jellyfin.androidtv.ui.home.HomeHeroArtwork
import org.jellyfin.androidtv.ui.home.novaHeroImage
import org.jellyfin.androidtv.ui.home.HomePreviewPlayer
import org.jellyfin.androidtv.ui.home.rememberSelectedHomePreview
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.navigation.NovaRemoteLayout
import org.jellyfin.androidtv.ui.navigation.NovaRowsNavigation
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType as SdkImageType
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

/** The A–Z list of everything in a library (or its favourites / one genre). */
internal fun novaLibraryRequest(folder: BaseItemDto, filterKind: String?, filterValue: String?): org.jellyfin.sdk.model.api.request.GetItemsRequest {
	val baseRequest = when {
		folder.name.equals("Collections", ignoreCase = true) ||
			folder.collectionType == CollectionType.BOXSETS ->
			BrowsingUtils.createCollectionsRequest(folder.id)
		else -> BrowsingUtils.createBrowseGridItemsRequest(folder)
	}
	val filteredRequest = when (filterKind) {
		NovaBrowseFilters.FILTER_FAVOURITES -> baseRequest.copy(
			filters = (baseRequest.filters.orEmpty() + ItemFilter.IS_FAVORITE).toSet(),
		)
		NovaBrowseFilters.FILTER_GENRE -> baseRequest.copy(
			genres = filterValue?.let(::setOf),
		)
		else -> baseRequest
	}
	val isCollection = folder.type == BaseItemKind.BOX_SET
	return filteredRequest.copy(
		// A collection (e.g. The Addams Family Collection) in release order.
		sortBy = if (isCollection) setOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME) else setOf(ItemSortBy.SORT_NAME),
		sortOrder = setOf(SortOrder.ASCENDING),
	)
}

class NovaLibraryFragment : Fragment() {
	internal lateinit var currentFolder: BaseItemDto
		private set
	internal var filterKind: String? = null
		private set
	internal var filterValue: String? = null
		private set
	internal var filterTitle: String? = null
		private set

	companion object {
		const val EXTRA_FILTER_KIND = "NovaFilterKind"
		const val EXTRA_FILTER_VALUE = "NovaFilterValue"
		const val EXTRA_FILTER_TITLE = "NovaFilterTitle"
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val folderJson = requireArguments().getString(Extras.Folder)
			?: error("NOVA library screen requires Extras.Folder")
		currentFolder = Json.Default.decodeFromString(BaseItemDto.serializer(), folderJson)
		filterKind = arguments?.getString(EXTRA_FILTER_KIND)
		filterValue = arguments?.getString(EXTRA_FILTER_VALUE)
		filterTitle = arguments?.getString(EXTRA_FILTER_TITLE)
		HomeHeroStateStore.resetToPreview()
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = NovaRemoteLayout(requireContext()).apply {
		val remoteLayout = this
		addView(content {
		val railFocusRequester = remember { FocusRequester() }
		val focusScope = rememberCoroutineScope()
		val heroInfoFocusRequester = remember { FocusRequester() }
		var rowsFragment by remember { mutableStateOf<NovaLibraryRowsFragment?>(null) }
		var menuItem by remember { mutableStateOf<BaseItemDto?>(null) }
		val hero by HomeHeroStateStore.state.collectAsState()
		val selectedPreview = rememberSelectedHomePreview(hero.item)
		var selectedPreviewFinished by remember(hero.item?.id) { mutableStateOf(false) }
		var heroVideoPlaying by remember { mutableStateOf(false) }
		val heroOverlayAlpha by animateFloatAsState(
			targetValue = if (heroVideoPlaying) 0.8f else 1f,
			animationSpec = tween(durationMillis = 450),
			label = "libraryHeroOverlayAlpha",
		)

		val api = koinInject<ApiClient>()
		val navigationRepository = koinInject<NavigationRepository>()
		val userViewsRepository = koinInject<org.jellyfin.androidtv.data.repository.UserViewsRepository>()
		val displayItem = hero.item
		// The JellyBridge "Discover" library (a mixed library) only holds
		// placeholders: no Play / Download in its header, just More Info,
		// where the Download button lives.
		val isCollection = currentFolder.type == BaseItemKind.BOX_SET
		val isDiscoverLibrary = currentFolder.collectionType == null && !isCollection &&
			!currentFolder.name.equals("Collections", ignoreCase = true)
		if (isDiscoverLibrary) LaunchedEffect(Unit) { NovaDiscoverRefresh.maybeSync(api) }
		val heroPrimaryFocusRequester = heroInfoFocusRequester
		val currentDisplayItem by rememberUpdatedState(displayItem)
		val currentHeroPrimaryFocusRequester by rememberUpdatedState(heroPrimaryFocusRequester)
		val browseTitle = filterTitle.orEmpty().ifBlank { currentFolder.name.orEmpty() }
		val displayTitle = displayItem?.name.orEmpty().ifBlank { browseTitle }
		val displayOverview = displayItem?.overview.orEmpty().ifBlank {
			"Browse $browseTitle."
		}
		val displayYear = displayItem?.productionYear?.toString().orEmpty()
		val displayRating = displayItem?.officialRating.orEmpty()
		val selectedBackdrop = remember(displayItem?.id) {
			displayItem?.novaHeroImage
		}
		val displayLogo = remember(displayItem?.id) {
			displayItem?.itemImages?.get(SdkImageType.LOGO)
				?: displayItem?.parentImages?.get(SdkImageType.LOGO)
		}

		// Movies / TV Shows (and their Favourites / genre pages), Music's artists,
		// Collections and each collection's films: a grid under the preview, like
		// the Fire TV. Only Discover keeps its rows.
		val isMusicArtists = currentFolder.collectionType == CollectionType.MUSIC && filterKind == null
		val useGrid = !isDiscoverLibrary
		val isRecent = filterKind == NovaBrowseFilters.FILTER_RECENT
		// Sort (A–Z / Newest / Year / Rating) on libraries, Favourites and categories.
		val sortable = useGrid && !isRecent && !isCollection && !isMusicArtists
		val prefs = remember { requireContext().getSharedPreferences("nova_settings", android.content.Context.MODE_PRIVATE) }
		val pageKey = remember { "${currentFolder.id}|${filterKind.orEmpty()}|${filterValue.orEmpty()}" }
		var sortMode by remember {
			androidx.compose.runtime.mutableIntStateOf(if (sortable) prefs.getInt("sort|$pageKey", 0).coerceIn(0, NovaSort.entries.lastIndex) else 0)
		}
		val sort = NovaSort.entries[sortMode]
		// Age filter: All, or just U / PG / 12 / 15 / 18.
		var ageMode by remember {
			androidx.compose.runtime.mutableIntStateOf(if (sortable) prefs.getInt("age|$pageKey", 0).coerceIn(0, NovaAge.entries.lastIndex) else 0)
		}
		val age = NovaAge.entries[ageMode]
		val grid = remember(sortMode, ageMode) {
			when {
				!useGrid -> null
				isRecent -> NovaLibraryGridState.list {
					NovaBrowseFilters.loadRecent(api, userViewsRepository.views.first(), filterValue == NovaBrowseFilters.RECENT_SHOWS)
				}
				isMusicArtists -> NovaLibraryGridState.artists(api, BrowsingUtils.createAlbumArtistsRequest(currentFolder.id))
				else -> NovaLibraryGridState.items(
					api,
					novaLibraryRequest(currentFolder, filterKind, filterValue).let { if (sortable) age.apply(sort.apply(it)) else it },
				)
			}?.also { state ->
				// Back on the poster you were on last time (even after closing NOVA).
				if (!isRecent) state.focusedIndex = prefs.getInt("pos|$pageKey|$sortMode|$ageMode", 0)
			}
		}
		val headerFocus = remember { FocusRequester() }
		fun openGridItem(item: BaseItemDto) {
			navigationRepository.navigate(
				when (item.type) {
					BaseItemKind.MOVIE, BaseItemKind.SERIES -> Destinations.novaInfo(item)
					BaseItemKind.BOX_SET -> Destinations.collectionBrowser(item)
					else -> Destinations.novaItem(item)
				}
			)
		}
		var menuOpenedFromGrid by remember { mutableStateOf(false) }
		fun focusGrid() {
			val state = grid ?: return
			focusScope.launch { if (!state.focus()) runCatching { railFocusRequester.requestFocus() } }
		}

		LaunchedEffect(rowsFragment) { rowsFragment?.view?.post { rowsFragment?.requestCardFocus() } }
		if (grid != null) {
			// Start on the remembered poster once they've loaded (only when the page
			// opens, not when you change the sort with the Sort button).
			var openedOnce by remember { mutableStateOf(false) }
			LaunchedEffect(grid) {
				snapshotFlow { grid.items.isNotEmpty() }.first { it }
				if (!openedOnce) {
					openedOnce = true
					grid.focus()
				}
			}
			// Remember where you are.
			if (!isRecent) LaunchedEffect(grid) {
				snapshotFlow { grid.focusedIndex }.collectLatest { index ->
					delay(600L)
					prefs.edit().putInt("pos|$pageKey|$sortMode|$ageMode", index).apply()
				}
			}
			// New titles: reload, but not while you're looking through the posters.
			LaunchedEffect(grid) {
				NovaDiscoverRefresh.updates.collect { if (!grid.hasFocus) grid.reload() }
			}
			LaunchedEffect(grid) {
				remoteLayout.directionHandler = null
				remoteLayout.longPressHandler = {
					grid.focusedItem?.takeIf { grid.hasFocus }?.let {
						menuItem = it
						menuOpenedFromGrid = true
						true
					} ?: false
				}
			}
		}

		// A–Z bar between the hero buttons and the row (not on Discover).
		val showLetters = !isDiscoverLibrary && !isCollection && !isRecent && (!sortable || sort == NovaSort.AZ)
		val hasHeaderButtons = grid != null
		val lettersFocusRequester = remember { FocusRequester() }
		// Back while browsing the posters: up to the A–Z bar (or Sort) instead of
		// leaving the page, so you can jump to another letter. Back again leaves.
		androidx.activity.compose.BackHandler(enabled = grid != null && grid.hasFocus) {
			val moved = (showLetters && runCatching { lettersFocusRequester.requestFocus() }.isSuccess) ||
				runCatching { headerFocus.requestFocus() }.isSuccess
			if (!moved) runCatching { railFocusRequester.requestFocus() }
		}
		val heroDown: () -> Unit = {
			if (showLetters) runCatching { lettersFocusRequester.requestFocus() }
			else if (grid != null) focusGrid()
			else rowsFragment?.requestCardFocus()
		}

		JellyfinTheme {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(nova.bg)
			) {
				Box(
					// The preview spreads across most of the screen on the right; it keeps
					// its 16:9 shape and just has its top and bottom trimmed, so more of the
					// video shows. The left side fades into the page behind the text.
					modifier = Modifier
						.align(Alignment.TopEnd)
						.fillMaxWidth(0.66f)
						// Over a grid it ends (fully faded) just above the posters, so no edge shows behind them.
						.height(if (grid != null) 236.dp else 300.dp)
						.clipToBounds()
				) {
					androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
						Box(
							modifier = Modifier
								.fillMaxWidth()
								.requiredHeight(maxWidth * 9f / 16f)
								.align(Alignment.Center),
						) {
							if (selectedBackdrop != null) {
								HomeHeroArtwork(
									url = selectedBackdrop.getUrl(api, maxWidth = 1280, maxHeight = 720),
									modifier = Modifier.fillMaxSize(),
								)
							}

							HomePreviewPlayer(
								preview = selectedPreview.takeIf {
									displayItem != null && !selectedPreviewFinished
								},
								onPlayingChanged = { heroVideoPlaying = it },
								onPreviewFinished = { selectedPreviewFinished = true },
								modifier = Modifier.fillMaxSize(),
							)
						}
					}

					Box(
						modifier = Modifier
							.fillMaxSize()
							.alpha(heroOverlayAlpha)
							.background(
								// Starts fully solid, so the picture's left edge never shows as a line.
								Brush.horizontalGradient(
									0.0f to nova.bg,
									0.10f to nova.bg.withAlpha(0xE8),
									0.34f to nova.bg.withAlpha(0xA8),
									0.64f to nova.bg.withAlpha(0x44),
									1.0f to Color.Transparent,
								)
							)
					)
					Box(
						modifier = Modifier
							.fillMaxSize()
							.alpha(heroOverlayAlpha)
							.background(
								Brush.verticalGradient(
									0.0f to nova.bg.withAlpha(0x12),
									0.72f to nova.bg.withAlpha(0x18),
									0.92f to nova.bg.withAlpha(0xC8),
									1.0f to nova.bg,
								)
							)
					)
					Box(
						modifier = Modifier
							.fillMaxSize()
							.background(
								// Always full strength (even while a preview plays and the
								// other shading lightens), so the left edge never shows.
								Brush.horizontalGradient(
									0.0f to nova.bg,
									0.06f to nova.bg.withAlpha(0xE0),
									0.13f to nova.bg.withAlpha(0x88),
									0.20f to nova.bg.withAlpha(0x30),
									0.28f to Color.Transparent,
								)
							)
					)
					Box(
						modifier = Modifier
							.fillMaxSize()
							.background(
								Brush.verticalGradient(
									0.0f to Color.Transparent,
									0.67f to Color.Transparent,
									0.76f to nova.bg.withAlpha(0x22),
									0.86f to nova.bg.withAlpha(0x55),
									0.94f to nova.bg.withAlpha(0xB0),
									1.0f to nova.bg,
								)
							)
					)
				}

				Box(
					modifier = Modifier
						.padding(start = 150.dp)
						.width(54.dp)
						.height(if (grid != null) 236.dp else 258.dp)
						.background(
							Brush.horizontalGradient(
								0.0f to nova.bgDeep.withAlpha(0xFC),
								0.30f to nova.bgDeep.withAlpha(0xE8),
								0.58f to nova.bgDeep.withAlpha(0xA8),
								0.80f to nova.bgDeep.withAlpha(0x55),
								1.0f to Color.Transparent,
							)
						)
				)

				Row(modifier = Modifier.fillMaxSize()) {
					NovaSideRail(
						current = when (filterKind) {
							NovaBrowseFilters.FILTER_FAVOURITES -> NovaRailPage.Favourites
							NovaBrowseFilters.FILTER_GENRE -> NovaRailPage.Genre(filterValue.orEmpty())
							NovaBrowseFilters.FILTER_RECENT -> NovaRailPage.Recent(filterValue.orEmpty())
							else -> NovaRailPage.Library(currentFolder.id)
						},
						focusRequester = railFocusRequester,
						onRight = { if (grid != null) focusGrid() else rowsFragment?.requestCardFocus() },
					)

					Column(modifier = Modifier.fillMaxSize().clipToBounds()) {
						// A little shorter over the grid, so two rows of posters fit underneath.
						Box(modifier = Modifier.fillMaxWidth().height(if (grid != null) 200.dp else 250.dp)) {
							if (heroOverlayAlpha > 0.01f) {
								Column(
									modifier = Modifier
										// Pinned to the top with fixed-height parts, so the buttons stay
										// in exactly the same place whatever title you move to.
										.align(Alignment.TopStart)
										.fillMaxWidth(0.72f)
										.alpha(heroOverlayAlpha)
										.padding(start = 28.dp, end = 26.dp, top = 22.dp, bottom = 12.dp)
								) {
									if (displayItem == null) {
										Text(browseTitle, color = nova.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
										Spacer(Modifier.height(8.dp))
									}
									Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.BottomStart) {
										if (displayLogo != null) {
											AsyncImage(
												url = displayLogo.getUrl(api, maxWidth = 620, maxHeight = 160),
												blurHash = displayLogo.blurHash,
												scaleType = ImageView.ScaleType.FIT_START,
												modifier = Modifier.fillMaxWidth(0.8f).height(60.dp),
											)
										} else {
											Text(displayTitle, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
										}
									}
									Spacer(Modifier.height(9.dp))
									Row(modifier = Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
										if (displayItem != null) {
											NovaTypeTag(displayItem)
											Spacer(Modifier.width(9.dp))
										}
										if (displayYear.isNotBlank()) Text(displayYear, color = nova.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
										if (displayYear.isNotBlank() && displayRating.isNotBlank()) {
											Spacer(Modifier.width(9.dp)); Text("•", color = nova.textMuted, fontSize = 13.sp); Spacer(Modifier.width(9.dp))
										}
										if (displayRating.isNotBlank()) {
											NovaRatingBadge(displayRating)
										}
										org.jellyfin.androidtv.ui.browsing.NovaLengthInfo(displayItem)
									}
									Spacer(Modifier.height(10.dp))
									Box(modifier = Modifier.height(40.dp)) {
										Text(displayOverview, color = nova.textPrimary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
									}

									// Film, TV and other libraries: no buttons here; OK on a
									// poster opens More Info (Play and ♥ live there).
									if (displayItem != null && isDiscoverLibrary) {
										Spacer(Modifier.height(14.dp))
										Row(verticalAlignment = Alignment.CenterVertically) {
											Button(
											modifier = Modifier.focusRequester(heroInfoFocusRequester).onPreviewKeyEvent { event ->
												if (event.key == Key.DirectionDown) {
													if (event.type == KeyEventType.KeyDown) heroDown()
													true
												} else false
											}.focusProperties {
												left = railFocusRequester
												down = FocusRequester.Cancel
												up = FocusRequester.Cancel
											},
												onClick = { navigationRepository.navigate(Destinations.novaInfo(displayItem)) },
												shape = RoundedCornerShape(nova.corner(5)),
												colors = ButtonDefaults.colors(
													containerColor = nova.surfaceHigh.withAlpha(0xAA),
													contentColor = Color.White,
													focusedContainerColor = nova.accent,
													focusedContentColor = nova.onAccent,
												),
												contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
											) { Text("More Info", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
										}
									}
								}
							}
							// Bottom left of the header: how many (e.g. "1,597 MOVIES"), Sort and Surprise me.
							if (grid != null) Row(
								modifier = Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 3.dp),
								horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								val count = grid.total
								if (count >= 0 && !isRecent) {
									val noun = when {
										isMusicArtists -> "artist"
										isCollection -> "film"
										currentFolder.collectionType == CollectionType.BOXSETS || currentFolder.name.equals("Collections", ignoreCase = true) -> "collection"
										currentFolder.collectionType == CollectionType.TVSHOWS -> "TV show"
										currentFolder.collectionType == CollectionType.MOVIES -> "movie"
										else -> "title"
									}
									Text(
										"%,d %s".format(count, if (count == 1) noun else noun + "s").uppercase(),
										color = nova.textMuted,
										fontSize = 11.sp,
										fontWeight = FontWeight.Bold,
										letterSpacing = 1.2.sp,
									)
								}
								val headerDown: () -> Unit = {
									if (showLetters) runCatching { lettersFocusRequester.requestFocus() } else focusGrid()
								}
								if (sortable) {
									NovaHeaderDropdown(
										label = "Sort: ${sort.label}",
										options = NovaSort.entries.map { it.label },
										selected = sortMode,
										focusRequester = headerFocus,
										onLeft = { runCatching { railFocusRequester.requestFocus() } },
										onDown = headerDown,
										onSelect = {
											sortMode = it
											prefs.edit().putInt("sort|$pageKey", it).apply()
										},
									)
									NovaHeaderDropdown(
										label = "Age: ${age.label}",
										options = NovaAge.entries.map { it.label },
										selected = ageMode,
										focusRequester = null,
										onLeft = null,
										onDown = headerDown,
										onSelect = {
											ageMode = it
											prefs.edit().putInt("age|$pageKey", it).apply()
										},
									)
								}
								NovaHeaderButton(
									label = "🎲  Surprise me",
									focusRequester = if (sortable) null else headerFocus,
									onLeft = if (sortable) null else ({ runCatching { railFocusRequester.requestFocus() } }),
									onDown = headerDown,
									onClick = {
										focusScope.launch { grid.randomItem()?.let(::openGridItem) }
									},
								)
							}
						}

						if (showLetters) {
							NovaLetterBar(
								focusRequester = lettersFocusRequester,
								onLetter = { letter ->
									if (grid != null) focusScope.launch { grid.jumpTo(letter) } else rowsFragment?.jumpToLetter(letter)
								},
								onUp = {
									if (hasHeaderButtons) runCatching { headerFocus.requestFocus() }
									else if (currentDisplayItem != null) runCatching { currentHeroPrimaryFocusRequester.requestFocus() }
								},
								onDown = { if (grid != null) focusGrid() else rowsFragment?.requestCardFocus() },
								onLeftEdge = { runCatching { railFocusRequester.requestFocus() } },
								modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
							)
						}

						if (grid != null) NovaLibraryGrid(
							state = grid,
							api = api,
							onUp = {
								val moved = (showLetters && runCatching { lettersFocusRequester.requestFocus() }.isSuccess) ||
									runCatching { headerFocus.requestFocus() }.isSuccess
								if (!moved) runCatching { railFocusRequester.requestFocus() }
							},
							onLeft = { runCatching { railFocusRequester.requestFocus() } },
							onClick = ::openGridItem,
							// Solid behind the posters: the preview's edges stay up in the header.
						modifier = Modifier.fillMaxSize().background(nova.bg),
						) else AndroidFragment<NovaLibraryRowsFragment>(
							modifier = Modifier

								.fillMaxSize()
								.clipToBounds(),
							onUpdate = { fragment ->
								rowsFragment = fragment
								remoteLayout.directionHandler = fragment::handleNavigation
								remoteLayout.longPressHandler = {
									fragment.focusedItem()?.let { menuItem = it; true } ?: false
								}
								fragment.onNavigateToSidebar = {
									focusScope.launch {
										remoteLayout.focusCompose { railFocusRequester.requestFocus() }
									}
								}
								fragment.onNavigateToHero = {
									remoteLayout.focusCompose {
										// A focus target that isn't on screen yet throws; fall back instead.
										val moved = when {
											showLetters -> runCatching { lettersFocusRequester.requestFocus() }.isSuccess
											currentDisplayItem != null -> runCatching { currentHeroPrimaryFocusRequester.requestFocus() }.isSuccess
											else -> false
										}
										if (!moved) runCatching { railFocusRequester.requestFocus() }
									}
								}
								fragment.onNavigateToInfo = { remoteLayout.focusCompose { if (currentDisplayItem != null) heroInfoFocusRequester.requestFocus() else railFocusRequester.requestFocus() } }
							},
						)
					}
				}
			}
		}
		menuItem?.let { item ->
			NovaItemMenu(
				item = item,
				onDismiss = {
					menuItem = null
					menuOpenedFromGrid = false
				},
				onRemoved = {
					if (grid != null && menuOpenedFromGrid) {
						grid.remove(item)
						focusGrid()
					} else {
						rowsFragment?.removeFocusedItem()
						rowsFragment?.view?.post { rowsFragment?.requestCardFocus() }
					}
				},
			)
		}
		})
	}

	override fun onDestroyView() {
		HomeHeroStateStore.resetToPreview()
		super.onDestroyView()
	}
}

class NovaLibraryRowsFragment : RowsSupportFragment() {
	private val itemLauncher by inject<ItemLauncher>()
	private val navigationRepository by inject<NavigationRepository>()
	private lateinit var folder: BaseItemDto
	private var filterKind: String? = null
	private var filterValue: String? = null
	private var filterTitle: String? = null
	var onNavigateToSidebar: (() -> Unit)? = null
	var onNavigateToHero: (() -> Unit)? = null
	var onNavigateToInfo: (() -> Unit)? = null

	private val remoteNavigation by lazy {
		NovaRowsNavigation(this, { onNavigateToSidebar?.invoke() },
			{ onNavigateToHero?.invoke() }, { onNavigateToInfo?.invoke() })
	}
	fun requestCardFocus() = remoteNavigation.requestCardFocus()
	fun handleNavigation(event: android.view.KeyEvent) = remoteNavigation.handle(event)

	private val api by inject<ApiClient>()
	// The A–Z bar: the row's request, and the sort name of every title in row order.
	private var letterRequest: org.jellyfin.sdk.model.api.request.GetItemsRequest? = null
	private var sortNames: List<String>? = null
	private var isDiscoverRows = false
	private val heroHandler = android.os.Handler(android.os.Looper.getMainLooper())
	private var heroUpdate = Runnable {}

	override fun onDestroyView() {
		heroHandler.removeCallbacks(heroUpdate)
		super.onDestroyView()
	}

	/** Jumps the row to the first title starting with [letter] ('#' = numbers and symbols). */
	fun jumpToLetter(letter: Char) {
		val request = letterRequest ?: return
		viewLifecycleOwner.lifecycleScope.launch {
			val names = sortNames ?: runCatching {
				withContext(Dispatchers.IO) {
					api.itemsApi.getItems(
						request.copy(
							startIndex = null,
							limit = null,
							fields = setOf(org.jellyfin.sdk.model.api.ItemFields.SORT_NAME),
							enableImages = false,
							enableUserData = false,
						)
					).content.items.map { (it.sortName ?: it.name).orEmpty() }
				}
			}.getOrNull()?.also { sortNames = it } ?: return@launch
			if (names.isEmpty()) return@launch
			val buckets = names.map(::letterOf)
			val index = buckets.indexOfFirst { it == letter }.takeIf { it >= 0 }
				?: buckets.indexOfFirst { it != '#' && letter != '#' && it > letter }.takeIf { it >= 0 }
				?: if (letter == '#') 0 else names.lastIndex
			remoteNavigation.jumpTo(0, index)
		}
	}

	private fun letterOf(name: String): Char {
		val first = name.trim().firstOrNull()?.uppercaseChar() ?: return '#'
		return if (first in 'A'..'Z') first else '#'
	}

	/** Title on the focused poster (long-press menu). */
	fun focusedItem(): org.jellyfin.sdk.model.api.BaseItemDto? =
		if (view?.hasFocus() != true) null else (remoteNavigation.currentRowItem()?.second as? org.jellyfin.androidtv.ui.itemhandling.BaseRowItem)?.baseItem

	/** Drops the focused poster (after it was deleted). */
	fun removeFocusedItem() {
		sortNames = null
		val (row, item) = remoteNavigation.currentRowItem() ?: return
		@Suppress("UNCHECKED_CAST")
		(row.adapter as? org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter<Any>)?.remove(item)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val libraryFragment = parentFragment as? NovaLibraryFragment
		folder = libraryFragment?.currentFolder
			?: run {
				val raw = parentFragment?.arguments?.getString(Extras.Folder)
					?: error("NOVA library rows require a folder")
				Json.Default.decodeFromString(BaseItemDto.serializer(), raw)
			}
		filterKind = libraryFragment?.filterKind ?: parentFragment?.arguments?.getString(NovaLibraryFragment.EXTRA_FILTER_KIND)
		filterValue = libraryFragment?.filterValue ?: parentFragment?.arguments?.getString(NovaLibraryFragment.EXTRA_FILTER_VALUE)
		filterTitle = libraryFragment?.filterTitle ?: parentFragment?.arguments?.getString(NovaLibraryFragment.EXTRA_FILTER_TITLE)

		val rowsAdapter = MutableObjectAdapter<LeanbackRow>(PositionableListRowPresenter())
		adapter = rowsAdapter

		val isCollection = folder.type == BaseItemKind.BOX_SET
		val request = novaLibraryRequest(folder, filterKind, filterValue)

		val header = filterTitle ?: when {
			isCollection -> folder.name.orEmpty()
			folder.name.equals("Movies", ignoreCase = true) -> "All Movies"
			folder.name.equals("TV Shows", ignoreCase = true) -> "All TV Shows"
			folder.name.equals("Music", ignoreCase = true) -> "All Music"
			folder.name.equals("Collections", ignoreCase = true) -> "All Collections"
			else -> "All ${folder.name.orEmpty()}"
		}

		val cardPresenter = CardPresenter(false, CardImageType.POSTER, 190)
		// Mixed libraries (no collection type, e.g. the JellyBridge "Discover"
		// library) hold films and shows together. Show them as separate rows so
		// it is obvious which is which.
		val isMixedLibrary = folder.collectionType == null && !isCollection &&
			!folder.name.equals("Collections", ignoreCase = true)
		isDiscoverRows = isMixedLibrary
		if (isMixedLibrary) {
			val prefix = filterTitle?.let { "$it · " }.orEmpty()
			// Discover changes as JellyBridge syncs new titles, so show the newest
			// first instead of A–Z (where new titles were buried along the row).
			val newestFirst = request.copy(
				sortBy = setOf(ItemSortBy.DATE_CREATED, ItemSortBy.SORT_NAME),
				sortOrder = setOf(SortOrder.DESCENDING),
				recursive = true,
			)
			HomeFragmentBrowseRowDefRow(
				BrowseRowDef(
					"${prefix}Newest Movies",
					newestFirst.copy(includeItemTypes = setOf(BaseItemKind.MOVIE)),
					60,
				)
			).addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
			HomeFragmentBrowseRowDefRow(
				BrowseRowDef(
					"${prefix}Newest TV Shows",
					newestFirst.copy(includeItemTypes = setOf(BaseItemKind.SERIES)),
					60,
				)
			).addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
		} else {
			if (!isCollection) letterRequest = request
			HomeFragmentBrowseRowDefRow(BrowseRowDef(header, request, 60))
				.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
		}

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, row ->
			if (item !is BaseRowItem || row !is ListRow) return@OnItemViewClickedListener
			val clickedItem = item.baseItem
			if (clickedItem != null &&
				(clickedItem.type == BaseItemKind.MOVIE || clickedItem.type == BaseItemKind.SERIES)
			) {
				// Films and shows open More Info (Play / ♥ there, or Download
				// for Discover titles) instead of playing straight away.
				navigationRepository.navigate(Destinations.novaInfo(clickedItem))
				return@OnItemViewClickedListener
			}
			@Suppress("UNCHECKED_CAST")
			val rowAdapter = row.adapter as? MutableObjectAdapter<Any>
				?: return@OnItemViewClickedListener
			itemLauncher.launch(item, rowAdapter, requireContext())
		}

		onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, row ->
			if (item !is BaseRowItem || row !is ListRow) {
				return@OnItemViewSelectedListener
			}

			remoteNavigation.onSelection(item, row)
			val rowAdapter = row.adapter as? ItemRowAdapter
			val itemIndex = rowAdapter?.indexOf(item) ?: -1
			rowAdapter?.loadMoreItemsIfNeeded(itemIndex)

			// Update the big header only once the focus stops on a card, not for
			// every card it passes, so flicking along a row stays quick.
			val selected = item.baseItem
			heroHandler.removeCallbacks(heroUpdate)
			heroUpdate = Runnable { HomeHeroStateStore.update(selected) }
			heroHandler.postDelayed(heroUpdate, 180L)
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		verticalGridView.setOnKeyInterceptListener(::handleNavigation)
		// Discover: reload when NOVA's sync has brought in new titles.
		// Reload when new titles have arrived (Discover sync, or a finished download).
		viewLifecycleOwner.lifecycleScope.launch {
			run {
				NovaDiscoverRefresh.updates.collect {
					// Not while you're scrolling through the posters: that would jump
					// your place back to the start.
					if (view?.hasFocus() == true && !isDiscoverRows) return@collect
					val rows = adapter ?: return@collect
					for (i in 0 until rows.size()) {
						((rows.get(i) as? ListRow)?.adapter as? ItemRowAdapter)?.Retrieve()
					}
				}
			}
		}
	}

}

/** Library sort orders (the Sort button cycles through them). */
internal enum class NovaSort(val label: String) {
	AZ("A–Z"),
	NEWEST("Newest"),
	YEAR("Year"),
	RATING("Rating");

	fun apply(request: org.jellyfin.sdk.model.api.request.GetItemsRequest) = when (this) {
		AZ -> request.copy(sortBy = setOf(ItemSortBy.SORT_NAME), sortOrder = setOf(SortOrder.ASCENDING))
		NEWEST -> request.copy(sortBy = setOf(ItemSortBy.DATE_CREATED, ItemSortBy.SORT_NAME), sortOrder = setOf(SortOrder.DESCENDING))
		YEAR -> request.copy(
			sortBy = setOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME),
			sortOrder = setOf(SortOrder.DESCENDING),
		)
		RATING -> request.copy(sortBy = setOf(ItemSortBy.COMMUNITY_RATING, ItemSortBy.SORT_NAME), sortOrder = setOf(SortOrder.DESCENDING))
	}
}

/** Age filter: everything, or one age rating (UK ratings with their US / TV equivalents). */
internal enum class NovaAge(val label: String, private val ratings: Set<String>) {
	ALL("All", emptySet()),
	U("U", setOf("U", "GB-U", "G", "TV-G", "TV-Y", "TV-Y7", "TV-Y7-FV")),
	PG("PG", setOf("PG", "GB-PG", "TV-PG")),
	TWELVE("12", setOf("12", "12A", "GB-12", "GB-12A", "PG-13", "TV-14")),
	FIFTEEN("15", setOf("15", "GB-15", "R")),
	EIGHTEEN("18", setOf("18", "R18", "GB-18", "GB-R18", "NC-17", "TV-MA"));

	fun apply(request: org.jellyfin.sdk.model.api.request.GetItemsRequest) =
		if (ratings.isEmpty()) request else request.copy(officialRatings = ratings)
}

/** Header button that opens a little list to pick from (Sort / Age). */
@androidx.compose.runtime.Composable
private fun NovaHeaderDropdown(
	label: String,
	options: List<String>,
	selected: Int,
	focusRequester: FocusRequester?,
	onLeft: (() -> Unit)?,
	onDown: () -> Unit,
	onSelect: (Int) -> Unit,
) {
	var open by remember { mutableStateOf(false) }
	val buttonFocus = remember { FocusRequester() }
	val density = androidx.compose.ui.platform.LocalDensity.current
	Box {
		NovaHeaderButton(
			label = "$label  ▾",
			focusRequester = focusRequester,
			extraFocus = buttonFocus,
			onLeft = onLeft,
			onDown = onDown,
			onClick = { open = true },
		)
		if (open) {
			fun close() {
				open = false
				runCatching { buttonFocus.requestFocus() }
			}
			androidx.compose.ui.window.Popup(
				alignment = Alignment.TopStart,
				offset = androidx.compose.ui.unit.IntOffset(0, with(density) { 32.dp.roundToPx() }),
				onDismissRequest = ::close,
				properties = androidx.compose.ui.window.PopupProperties(focusable = true),
			) {
				val first = remember { FocusRequester() }
				Column(
					modifier = Modifier
						.width(150.dp)
						.background(nova.surfaceRaised, RoundedCornerShape(nova.corner(8)))
						.padding(6.dp),
				) {
					options.forEachIndexed { index, option ->
						Button(
							onClick = {
								close()
								if (index != selected) onSelect(index)
							},
							modifier = (if (index == selected) Modifier.focusRequester(first) else Modifier)
								.fillMaxWidth()
								.height(32.dp),
							shape = RoundedCornerShape(nova.corner(5)),
							colors = ButtonDefaults.colors(
								containerColor = Color.Transparent,
								contentColor = Color.White,
								focusedContainerColor = nova.accent,
								focusedContentColor = nova.onAccent,
							),
							contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
						) {
							Text(
								(if (index == selected) "✓  " else "     ") + option,
								fontSize = 13.sp,
								fontWeight = FontWeight.Bold,
							)
						}
					}
				}
				LaunchedEffect(Unit) {
					delay(60L)
					runCatching { first.requestFocus() }
				}
			}
		}
	}
}

/** Small header button (Sort / Surprise me). */
@androidx.compose.runtime.Composable
private fun NovaHeaderButton(
	label: String,
	focusRequester: FocusRequester?,
	extraFocus: FocusRequester? = null,
	onLeft: (() -> Unit)?,
	onDown: () -> Unit,
	onClick: () -> Unit,
) {
	Button(
		onClick = onClick,
		modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
			.then(if (extraFocus != null) Modifier.focusRequester(extraFocus) else Modifier)
			.height(28.dp)
			.onPreviewKeyEvent { event ->
				when (event.key) {
					Key.DirectionDown -> {
						if (event.type == KeyEventType.KeyDown) onDown()
						true
					}
					Key.DirectionUp -> true
					Key.DirectionLeft -> if (onLeft != null) {
						if (event.type == KeyEventType.KeyDown) onLeft()
						true
					} else false
					else -> false
				}
			},
		shape = RoundedCornerShape(nova.corner(14)),
		colors = ButtonDefaults.colors(
			containerColor = nova.surfaceHigh.withAlpha(0xAA),
			contentColor = Color.White,
			focusedContainerColor = nova.accent,
			focusedContentColor = nova.onAccent,
		),
		contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
	) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

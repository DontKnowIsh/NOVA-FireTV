package org.jellyfin.androidtv.ui.browsing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.constant.ImageType as CardImageType
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.home.HomeFragmentBrowseRowDefRow
import org.jellyfin.androidtv.ui.home.HomeHeroStateStore
import org.jellyfin.androidtv.ui.home.HomePreviewPlayer
import org.jellyfin.androidtv.ui.home.rememberSelectedHomePreview
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItemSelectAction
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType as SdkImageType
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

private fun novaLibraryRailIcon(name: String, type: CollectionType?): Int = when {
	name.equals("Collections", ignoreCase = true) -> R.drawable.ic_grid
	name.equals("Indian Movies", ignoreCase = true) -> R.drawable.ic_movie
	name.contains("Prayer", ignoreCase = true) -> R.drawable.ic_star
	type == CollectionType.MOVIES -> R.drawable.ic_movie
	type == CollectionType.TVSHOWS -> R.drawable.ic_tv
	type == CollectionType.MUSIC -> R.drawable.ic_music_album
	else -> R.drawable.ic_folder
}

@Composable
private fun NovaLibraryRailButton(
	label: String,
	iconRes: Int,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	active: Boolean = false,
	onRight: (() -> Unit)? = null,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(8.dp)
	val keyModifier = modifier.onPreviewKeyEvent { event ->
		if (
			onRight != null &&
			event.type == KeyEventType.KeyDown &&
			event.key == Key.DirectionRight
		) {
			onRight()
			true
		} else {
			false
		}
	}
	val decoratedModifier = if (focused) {
		keyModifier.border(1.dp, Color(0x99FF5C66), shape)
	} else {
		keyModifier
	}

	Button(
		onClick = onClick,
		modifier = decoratedModifier,
		interactionSource = interactionSource,
		shape = shape,
		colors = ButtonDefaults.colors(
			containerColor = if (active) Color(0x332B0A0D) else Color.Transparent,
			contentColor = if (active) Color.White else Color(0xFFB8B8B8),
			focusedContainerColor = Color(0xFFE50914),
			focusedContentColor = Color.White,
		),
		contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
		) {
			if (active && !focused) {
				Box(
					modifier = Modifier
						.width(3.dp)
						.height(18.dp)
						.background(Color(0xFFE50914), RoundedCornerShape(3.dp))
				)
				Spacer(Modifier.width(7.dp))
			}
			Icon(
				painter = painterResource(iconRes),
				contentDescription = null,
				modifier = Modifier.size(19.dp),
			)
			Spacer(Modifier.width(11.dp))
			Text(
				text = label,
				fontSize = 13.sp,
				fontWeight = if (focused || active) FontWeight.Bold else FontWeight.Medium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
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
	) = content {
		val rowsFocusRequester = remember { FocusRequester() }
		val railFocusRequester = remember { FocusRequester() }
		var rowsFragment by remember { mutableStateOf<NovaLibraryRowsFragment?>(null) }
		val hero by HomeHeroStateStore.state.collectAsState()
		val selectedPreview = rememberSelectedHomePreview(hero.item)
		var selectedPreviewFinished by remember(hero.item?.id) { mutableStateOf(false) }
		var heroVideoPlaying by remember { mutableStateOf(false) }
		val heroOverlayAlpha by animateFloatAsState(
			targetValue = if (heroVideoPlaying) 0.35f else 1f,
			animationSpec = tween(durationMillis = 450),
			label = "libraryHeroOverlayAlpha",
		)

		val api = koinInject<ApiClient>()
		val userViewsRepository = koinInject<UserViewsRepository>()
		val itemMutationRepository = koinInject<ItemMutationRepository>()
		val itemLauncher = koinInject<ItemLauncher>()
		val navigationRepository = koinInject<NavigationRepository>()
		val context = LocalContext.current
		val actionScope = rememberCoroutineScope()
		val userViews by userViewsRepository.views.collectAsState(emptyList())
		var showGenres by remember { mutableStateOf(false) }
		val moviesView = remember(userViews) {
			userViews.firstOrNull { it.name.equals("Movies", ignoreCase = true) }
				?: userViews.firstOrNull {
					it.collectionType == CollectionType.MOVIES &&
						!it.name.equals("Indian Movies", ignoreCase = true)
				}
		}
		var availableGenres by remember(moviesView?.id) { mutableStateOf<List<String>>(emptyList()) }
		var categoriesLoaded by remember(moviesView?.id) { mutableStateOf(false) }
		var categoriesLoading by remember(moviesView?.id) { mutableStateOf(false) }
		var categoriesLoadFailed by remember(moviesView?.id) { mutableStateOf(false) }

		LaunchedEffect(showGenres, moviesView?.id) {
			val movies = moviesView ?: return@LaunchedEffect
			if (showGenres && !categoriesLoaded && !categoriesLoading) {
				categoriesLoading = true
				categoriesLoadFailed = false
				try {
					val genres = NovaBrowseFilters.loadMovieGenres(api, movies)
					if (genres.isNotEmpty()) {
						availableGenres = genres
						categoriesLoaded = true
					} else {
						categoriesLoadFailed = true
					}
				} finally {
					categoriesLoading = false
				}
			}
		}

		val orderedViews = remember(userViews) {
			val remaining = userViews.toMutableList()
			fun takeByName(name: String) = remaining
				.firstOrNull { it.name.equals(name, ignoreCase = true) }
				?.also { remaining.remove(it) }
			fun takeByType(type: CollectionType, excludingName: String? = null) = remaining
				.firstOrNull { view ->
					view.collectionType == type &&
						(excludingName == null || !view.name.equals(excludingName, ignoreCase = true))
				}
				?.also { remaining.remove(it) }

			buildList {
				takeByName("Collections")?.let(::add)
				takeByName("Indian Movies")?.let(::add)
				takeByName("Movies")?.let(::add)
					?: takeByType(CollectionType.MOVIES, "Indian Movies")?.let(::add)
				takeByName("TV Shows")?.let(::add)
					?: takeByType(CollectionType.TVSHOWS)?.let(::add)
				takeByName("Music")?.let(::add)
					?: takeByType(CollectionType.MUSIC)?.let(::add)
				addAll(remaining)
			}
		}

		val favoriteOverrides = remember { mutableStateMapOf<String, Boolean>() }
		val watchedOverrides = remember { mutableStateMapOf<String, Boolean>() }
		val displayItem = hero.item
		val displayItemKey = displayItem?.id?.toString()
		var isFavorite by remember(displayItemKey) {
			mutableStateOf(
				displayItemKey?.let(favoriteOverrides::get)
					?: (displayItem?.userData?.isFavorite == true)
			)
		}
		var isWatched by remember(displayItemKey) {
			mutableStateOf(
				displayItemKey?.let(watchedOverrides::get)
					?: (displayItem?.userData?.played == true)
			)
		}
		var favoriteUpdating by remember(displayItem?.id) { mutableStateOf(false) }
		var watchedUpdating by remember(displayItem?.id) { mutableStateOf(false) }

		LaunchedEffect(displayItem?.id) {
			val item = displayItem ?: return@LaunchedEffect
			val key = item.id.toString()

			// Fast D-pad navigation replaces this effect repeatedly. Give focus a
			// moment to settle, then run Jellyfin I/O off Android's UI dispatcher.
			// This prevents SDK response cancellation from doing socket work on the
			// main thread (the NetworkOnMainThreadException seen in the crash log).
			delay(300L)
			val latestUserData = try {
				withContext(Dispatchers.IO) {
					api.userLibraryApi.getItem(itemId = item.id).content.userData
				}
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (_: Exception) {
				null
			}

			isFavorite = favoriteOverrides[key]
				?: (latestUserData?.isFavorite == true)
			isWatched = watchedOverrides[key]
				?: (latestUserData?.played == true)
		}
		val showLibraryActions =
			currentFolder.collectionType == CollectionType.MOVIES ||
				currentFolder.collectionType == CollectionType.TVSHOWS ||
				currentFolder.name.equals("Indian Movies", ignoreCase = true)
		val browseTitle = filterTitle.orEmpty().ifBlank { currentFolder.name.orEmpty() }
		val displayTitle = displayItem?.name.orEmpty().ifBlank { browseTitle }
		val displayOverview = displayItem?.overview.orEmpty().ifBlank {
			"Browse $browseTitle."
		}
		val displayYear = displayItem?.productionYear?.toString().orEmpty()
		val displayRating = displayItem?.officialRating.orEmpty()
		val selectedBackdrop = remember(displayItem?.id) {
			displayItem?.itemBackdropImages?.firstOrNull()
				?: displayItem?.parentBackdropImages?.firstOrNull()
		}
		val displayLogo = remember(displayItem?.id) {
			displayItem?.itemImages?.get(SdkImageType.LOGO)
				?: displayItem?.parentImages?.get(SdkImageType.LOGO)
		}

		LaunchedEffect(rowsFocusRequester) { rowsFocusRequester.requestFocus() }

		JellyfinTheme {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(Color(0xFF070707))
			) {
				Box(
					modifier = Modifier
						.padding(start = 188.dp)
						.fillMaxWidth()
						.height(312.dp)
						.clipToBounds()
				) {
					if (selectedBackdrop != null) {
						AsyncImage(
							url = selectedBackdrop.getUrl(api, maxWidth = 1280, maxHeight = 720),
							blurHash = selectedBackdrop.blurHash,
							aspectRatio = 16f / 9f,
							scaleType = ImageView.ScaleType.CENTER_CROP,
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

					Box(
						modifier = Modifier
							.fillMaxSize()
							.alpha(heroOverlayAlpha)
							.background(
								Brush.horizontalGradient(
									0.0f to Color(0xE8070707),
									0.30f to Color(0xA8070707),
									0.62f to Color(0x44070707),
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
									0.0f to Color(0x12070707),
									0.72f to Color(0x18070707),
									0.92f to Color(0xC8070707),
									1.0f to Color(0xFF070707),
								)
							)
					)
					Box(
						modifier = Modifier
							.fillMaxSize()
							.background(
								Brush.horizontalGradient(
									0.0f to Color(0xC8070707),
									0.05f to Color(0xA80F0F0F),
									0.11f to Color(0x66747474),
									0.18f to Color(0x267F7F7F),
									0.25f to Color.Transparent,
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
									0.76f to Color(0x22787878),
									0.86f to Color(0x557A7A7A),
									0.94f to Color(0x99707070),
									1.0f to Color(0xE8070707),
								)
							)
					)
				}

				Box(
					modifier = Modifier
						.padding(start = 174.dp)
						.width(54.dp)
						.height(312.dp)
						.background(
							Brush.horizontalGradient(
								0.0f to Color(0xFC050505),
								0.30f to Color(0xE8050505),
								0.58f to Color(0xA8050505),
								0.80f to Color(0x55050505),
								1.0f to Color.Transparent,
							)
						)
				)

				Row(modifier = Modifier.fillMaxSize()) {
					Column(
						modifier = Modifier
							.width(188.dp)
							.fillMaxHeight()
							.background(Color(0xFC050505))
					) {
						LazyColumn(
							modifier = Modifier
								.fillMaxWidth()
								.weight(1f),
							contentPadding = PaddingValues(start = 16.dp, end = 14.dp, top = 20.dp, bottom = 12.dp),
						) {
							item {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(start = 7.dp, end = 4.dp),
									verticalAlignment = Alignment.CenterVertically,
								) {
									Icon(
										painter = painterResource(R.drawable.nova_mark),
										contentDescription = "NOVA",
										modifier = Modifier.size(34.dp),
										tint = Color.Unspecified,
									)
									Spacer(Modifier.width(8.dp))
									Column {
										Text("NOVA", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.2.sp)
										Text("YOUR MEDIA", color = Color(0xFF777777), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
									}
								}
								Spacer(Modifier.height(22.dp))
								NovaLibraryRailButton(
									label = "Home",
									iconRes = R.drawable.ic_house,
									onClick = { navigationRepository.navigate(Destinations.home) },
									onRight = { rowsFragment?.requestCardFocus() },
									modifier = Modifier
										.fillMaxWidth()
										.focusRequester(railFocusRequester)
										.focusProperties { right = rowsFocusRequester },
								)
								if (moviesView != null) {
									Spacer(Modifier.height(6.dp))
									NovaLibraryRailButton(
										label = "Favourites",
										iconRes = R.drawable.ic_star,
										onClick = {
											navigationRepository.navigate(
												Destinations.novaFilteredLibrary(moviesView, NovaBrowseFilters.FILTER_FAVOURITES, null, "Favourites")
											)
										},
										active = filterKind == NovaBrowseFilters.FILTER_FAVOURITES,
										onRight = { rowsFragment?.requestCardFocus() },
										modifier = Modifier.fillMaxWidth().focusProperties { right = rowsFocusRequester },
									)
									Spacer(Modifier.height(6.dp))
									NovaLibraryRailButton(
										label = if (showGenres) "Categories ▾" else "Categories ▸",
										iconRes = R.drawable.ic_grid,
										onClick = { showGenres = !showGenres },
										onRight = { rowsFragment?.requestCardFocus() },
										modifier = Modifier.fillMaxWidth().focusProperties { right = rowsFocusRequester },
									)
									if (showGenres) {
										if (categoriesLoading) {
											Text(
												text = "Loading categories…",
												color = Color(0xFF888888),
												fontSize = 11.sp,
												modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 4.dp),
											)
										} else if (categoriesLoadFailed) {
											Text(
												text = "Could not load — reopen to retry",
												color = Color(0xFF888888),
												fontSize = 10.sp,
												modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 4.dp),
											)
										} else if (categoriesLoaded) {
											availableGenres.forEach { genre ->
												Spacer(Modifier.height(3.dp))
												NovaLibraryRailButton(
													label = genre,
													iconRes = R.drawable.ic_movie,
													onClick = {
														navigationRepository.navigate(
															Destinations.novaFilteredLibrary(moviesView, NovaBrowseFilters.FILTER_GENRE, genre, genre)
														)
													},
													active = filterKind == NovaBrowseFilters.FILTER_GENRE && filterValue == genre,
													onRight = { rowsFragment?.requestCardFocus() },
													modifier = Modifier.fillMaxWidth().padding(start = 10.dp).focusProperties { right = rowsFocusRequester },
												)
											}
										}
									}
								}

								Spacer(Modifier.height(6.dp))
								NovaLibraryRailButton(
									label = "Search",
									iconRes = R.drawable.ic_search,
									onClick = { navigationRepository.navigate(Destinations.search()) },
									onRight = { rowsFragment?.requestCardFocus() },
									modifier = Modifier.fillMaxWidth().focusProperties { right = rowsFocusRequester },
								)
								Spacer(Modifier.height(20.dp))
								Row(
									modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
									verticalAlignment = Alignment.CenterVertically,
								) {
									Box(
										modifier = Modifier.width(18.dp).height(1.dp).background(Color(0x88E50914))
									)
									Spacer(Modifier.width(8.dp))
									Text("MY LIBRARIES", color = Color(0xFF6F6F6F), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.15.sp)
								}
								Spacer(Modifier.height(9.dp))
							}

							items(orderedViews.toList(), key = { it.id }) { view ->
								NovaLibraryRailButton(
									label = view.name.orEmpty(),
									iconRes = novaLibraryRailIcon(view.name.orEmpty(), view.collectionType),
									onClick = { itemLauncher.launchUserView(view) },
									active = filterKind == null && view.id == currentFolder.id,
									onRight = { rowsFragment?.requestCardFocus() },
									modifier = Modifier
										.fillMaxWidth()
										.focusProperties { right = rowsFocusRequester },
								)
								Spacer(Modifier.height(5.dp))
							}
						}
					}

					Column(modifier = Modifier.fillMaxSize().clipToBounds()) {
						Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
							if (heroOverlayAlpha > 0.01f) {
								Column(
									modifier = Modifier
										.align(Alignment.CenterStart)
										.fillMaxWidth(0.62f)
										.alpha(heroOverlayAlpha)
										.padding(start = 28.dp, end = 26.dp, top = 22.dp, bottom = 12.dp)
								) {
									if (displayItem == null) {
										Text(browseTitle, color = Color(0xFFE50914), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
										Spacer(Modifier.height(8.dp))
									}
									if (displayLogo != null) {
										AsyncImage(
											url = displayLogo.getUrl(api, maxWidth = 620, maxHeight = 160),
											blurHash = displayLogo.blurHash,
											scaleType = ImageView.ScaleType.FIT_START,
											modifier = Modifier.fillMaxWidth(0.8f).height(78.dp),
										)
									} else {
										Text(displayTitle, color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
									}
									Spacer(Modifier.height(9.dp))
									Row(verticalAlignment = Alignment.CenterVertically) {
										if (displayYear.isNotBlank()) Text(displayYear, color = Color(0xFFE4E4E4), fontSize = 14.sp, fontWeight = FontWeight.Bold)
										if (displayYear.isNotBlank() && displayRating.isNotBlank()) {
											Spacer(Modifier.width(9.dp)); Text("•", color = Color(0xFF9A9A9A), fontSize = 13.sp); Spacer(Modifier.width(9.dp))
										}
										if (displayRating.isNotBlank()) {
											Box(modifier = Modifier.background(Color(0x55333333), RoundedCornerShape(3.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
												Text(displayRating, color = Color(0xFFEAEAEA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
											}
										}
									}
									Spacer(Modifier.height(10.dp))
									Text(displayOverview, color = Color(0xFFE1E1E1), fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)

									if (displayItem != null) {
										Spacer(Modifier.height(14.dp))
										Row(verticalAlignment = Alignment.CenterVertically) {
											val playable = displayItem.type == BaseItemKind.MOVIE ||
												displayItem.type == BaseItemKind.SERIES ||
												displayItem.type == BaseItemKind.EPISODE ||
												displayItem.type == BaseItemKind.VIDEO ||
												displayItem.type == BaseItemKind.AUDIO ||
												displayItem.type == BaseItemKind.MUSIC_ALBUM
											if (playable) {
												Button(
													onClick = {
														itemLauncher.launch(
															BaseItemDtoBaseRowItem(item = displayItem, selectAction = BaseRowItemSelectAction.Play),
															null,
															context,
														)
													},
													shape = RoundedCornerShape(5.dp),
													colors = ButtonDefaults.colors(
														containerColor = Color(0xFFF2F2F2),
														contentColor = Color(0xFF111111),
														focusedContainerColor = Color(0xFFE50914),
														focusedContentColor = Color.White,
													),
													contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
												) { Text("▶  Play", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
												Spacer(Modifier.width(10.dp))
											}
											Button(
												onClick = { navigationRepository.navigate(Destinations.novaInfo(displayItem)) },
												shape = RoundedCornerShape(5.dp),
												colors = ButtonDefaults.colors(
													containerColor = Color(0xAA333333),
													contentColor = Color.White,
													focusedContainerColor = Color(0xFFE50914),
													focusedContentColor = Color.White,
												),
												contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
											) { Text("More Info", fontSize = 14.sp, fontWeight = FontWeight.Bold) }

											if (showLibraryActions) {
												Spacer(Modifier.width(10.dp))
												Button(
													onClick = {
														actionScope.launch {
															favoriteUpdating = true
															try {
																runCatching {
																	itemMutationRepository.setFavorite(displayItem.id, !isFavorite)
																}.onSuccess { data ->
																	isFavorite = data.isFavorite
																	favoriteOverrides[displayItem.id.toString()] = data.isFavorite
																}
															} finally {
																favoriteUpdating = false
															}
														}
													},
													enabled = !favoriteUpdating,
													shape = RoundedCornerShape(5.dp),
													colors = ButtonDefaults.colors(
														containerColor = if (isFavorite) Color(0xFFE50914) else Color(0xAA333333),
														contentColor = Color.White,
														focusedContainerColor = Color(0xFFE50914),
														focusedContentColor = Color.White,
													),
													contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
												) {
													Text(
														"Favourite",
														fontSize = 14.sp,
														fontWeight = FontWeight.Bold,
													)
												}

												Spacer(Modifier.width(10.dp))
												Button(
													onClick = {
														actionScope.launch {
															watchedUpdating = true
															try {
																runCatching {
																	itemMutationRepository.setPlayed(displayItem.id, !isWatched)
																}.onSuccess { data ->
																	isWatched = data.played
																	watchedOverrides[displayItem.id.toString()] = data.played
																}
															} finally {
																watchedUpdating = false
															}
														}
													},
													enabled = !watchedUpdating,
													shape = RoundedCornerShape(5.dp),
													colors = ButtonDefaults.colors(
														containerColor = if (isWatched) Color(0xFFE50914) else Color(0xAA333333),
														contentColor = Color.White,
														focusedContainerColor = Color(0xFFE50914),
														focusedContentColor = Color.White,
													),
													contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
												) {
													Text(
														"Watched",
														fontSize = 14.sp,
														fontWeight = FontWeight.Bold,
													)
												}
											}
										}
									}
								}
							}
						}

						AndroidFragment<NovaLibraryRowsFragment>(
							modifier = Modifier
								.focusGroup()
								.focusRequester(rowsFocusRequester)
								.focusProperties {
									onExit = {
										val firstRow = rowsFragment?.selectedPosition?.let { it <= 0 } ?: false
										if (requestedFocusDirection != FocusDirection.Up || !firstRow) {
											cancelFocusChange()
										} else {
											rowsFragment?.selectedPosition = 0
											rowsFragment?.verticalGridView?.clearFocus()
										}
									}
								}
								.fillMaxSize()
								.clipToBounds(),
							onUpdate = { fragment ->
								rowsFragment = fragment
								fragment.onNavigateToSidebar = { railFocusRequester.requestFocus() }
							},
						)
					}
				}
			}
		}
	}

	override fun onDestroyView() {
		HomeHeroStateStore.resetToPreview()
		super.onDestroyView()
	}
}

class NovaLibraryRowsFragment : RowsSupportFragment() {
	private val itemLauncher by inject<ItemLauncher>()
	private lateinit var folder: BaseItemDto
	private var filterKind: String? = null
	private var filterValue: String? = null
	private var filterTitle: String? = null
	private var currentItem: BaseRowItem? = null
	private var currentRow: ListRow? = null
	private var currentCardView: View? = null
	var onNavigateToSidebar: (() -> Unit)? = null

	fun requestCardFocus() {
		if (currentCardView?.requestFocus() == true) return
		selectedPosition = 0
		verticalGridView.requestFocus()
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
		val request = filteredRequest.copy(
			sortBy = setOf(ItemSortBy.SORT_NAME),
			sortOrder = setOf(SortOrder.ASCENDING),
		)

		val header = filterTitle ?: when {
			folder.name.equals("Movies", ignoreCase = true) -> "All Movies"
			folder.name.equals("TV Shows", ignoreCase = true) -> "All TV Shows"
			folder.name.equals("Music", ignoreCase = true) -> "All Music"
			folder.name.equals("Collections", ignoreCase = true) -> "All Collections"
			else -> "All ${folder.name.orEmpty()}"
		}

		val cardPresenter = CardPresenter(false, CardImageType.POSTER, 190)
		HomeFragmentBrowseRowDefRow(BrowseRowDef(header, request, 60))
			.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, row ->
			if (item !is BaseRowItem || row !is ListRow) return@OnItemViewClickedListener
			@Suppress("UNCHECKED_CAST")
			val rowAdapter = row.adapter as? MutableObjectAdapter<Any>
				?: return@OnItemViewClickedListener
			itemLauncher.launch(item, rowAdapter, requireContext())
		}

		onItemViewSelectedListener = OnItemViewSelectedListener { itemViewHolder, item, _, row ->
			if (item !is BaseRowItem || row !is ListRow) {
				currentItem = null
				currentRow = null
				currentCardView = null
				HomeHeroStateStore.clear()
				return@OnItemViewSelectedListener
			}

			currentItem = item
			currentRow = row
			currentCardView = itemViewHolder?.view
			val rowAdapter = row.adapter as? ItemRowAdapter
			val itemIndex = rowAdapter?.indexOf(item) ?: -1
			rowAdapter?.loadMoreItemsIfNeeded(itemIndex)

			HomeHeroStateStore.update(item.baseItem)
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		verticalGridView.setOnKeyInterceptListener { event ->
			if (
				event.action == android.view.KeyEvent.ACTION_DOWN &&
				event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT &&
				event.repeatCount == 0
			) {
				val item = currentItem
				val index = if (item != null) (currentRow?.adapter as? ItemRowAdapter)?.indexOf(item) ?: -1 else -1
				if (index == 0) {
					onNavigateToSidebar?.invoke()
					true
				} else {
					false
				}
			} else false
		}
	}
}

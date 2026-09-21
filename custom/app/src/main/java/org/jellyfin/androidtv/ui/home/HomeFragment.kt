package org.jellyfin.androidtv.ui.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.browsing.NovaBrowseFilters
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItemSelectAction
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

private fun novaRailIcon(name: String, type: CollectionType?): Int = when {
	name.equals("Collections", ignoreCase = true) -> R.drawable.ic_grid
	name.equals("Indian Movies", ignoreCase = true) -> R.drawable.ic_movie
	name.contains("Prayer", ignoreCase = true) -> R.drawable.ic_star
	type == CollectionType.MOVIES -> R.drawable.ic_movie
	type == CollectionType.TVSHOWS -> R.drawable.ic_tv
	type == CollectionType.MUSIC -> R.drawable.ic_music_album
	else -> R.drawable.ic_folder
}

@Composable
private fun NovaRailButton(
	label: String,
	iconRes: Int,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	active: Boolean = false,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(8.dp)
	val decoratedModifier = if (focused) {
		modifier.border(1.dp, Color(0x99FF5C66), shape)
	} else {
		modifier
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

class HomeFragment : Fragment() {
	private val sessionRepository by inject<SessionRepository>()
	private val serverRepository by inject<ServerRepository>()
	private val notificationRepository by inject<NotificationsRepository>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		val rowsFocusRequester = remember { FocusRequester() }
		val railFocusRequester = remember { FocusRequester() }
		val hero by HomeHeroStateStore.state.collectAsState()
		val preview = rememberRandomHomePreview(enabled = hero.item == null)
		val selectedPreview = rememberSelectedHomePreview(hero.item)
		var selectedPreviewFinished by remember(hero.item?.id) { mutableStateOf(false) }
		var heroVideoPlaying by remember { mutableStateOf(false) }
		val heroOverlayAlpha by animateFloatAsState(
			targetValue = if (heroVideoPlaying) 0.35f else 1f,
			animationSpec = tween(durationMillis = 450),
			label = "heroOverlayAlpha",
		)
		val api = koinInject<ApiClient>()
		val userViewsRepository = koinInject<UserViewsRepository>()
		val itemLauncher = koinInject<ItemLauncher>()
		val navigationRepository = koinInject<NavigationRepository>()
		val context = LocalContext.current
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

		val displayItem = hero.item ?: preview?.item
		val displayTitle = when {
			hero.item != null -> hero.title
			displayItem != null -> displayItem.name.orEmpty()
			else -> "Welcome back"
		}
		val displayOverview = when {
			hero.item != null -> hero.overview
			displayItem != null -> displayItem.overview.orEmpty().ifBlank { "A random preview from your library." }
			else -> "Browse your library and pick up where you left off."
		}
		val displayYear = when {
			hero.item != null -> hero.year
			else -> displayItem?.productionYear?.toString().orEmpty()
		}
		val displayRating = when {
			hero.item != null -> hero.rating
			else -> displayItem?.officialRating.orEmpty()
		}

		val selectedBackdrop = remember(hero.item?.id) {
			hero.item?.itemBackdropImages?.firstOrNull()
				?: hero.item?.parentBackdropImages?.firstOrNull()
		}
		val displayLogo = remember(displayItem?.id) {
			displayItem?.itemImages?.get(ImageType.LOGO)
				?: displayItem?.parentImages?.get(ImageType.LOGO)
		}
		val previewFallbackBackdrop = remember(preview?.item?.id) {
			preview?.item?.itemBackdropImages?.firstOrNull()
				?: preview?.item?.parentBackdropImages?.firstOrNull()
		}

		LaunchedEffect(rowsFocusRequester) { rowsFocusRequester.requestFocus() }

		JellyfinTheme {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(Color(0xFF070707))
			) {
				// Fixed NOVA hero stage: it starts after the left rail and ends just
				// below the top edge of the first card row. Both still artwork and
				// hover video use exactly this same area.
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
							modifier = Modifier.fillMaxSize()
						)
					} else if (previewFallbackBackdrop != null) {
						AsyncImage(
							url = previewFallbackBackdrop.getUrl(api, maxWidth = 1280, maxHeight = 720),
							blurHash = previewFallbackBackdrop.blurHash,
							aspectRatio = 16f / 9f,
							scaleType = ImageView.ScaleType.CENTER_CROP,
							modifier = Modifier.fillMaxSize()
						)
					}

					HomePreviewPlayer(
						preview = when {
							hero.item != null && !selectedPreviewFinished -> selectedPreview
							hero.item == null -> preview
							else -> null
						},
						onPlayingChanged = { heroVideoPlaying = it },
						onPreviewFinished = {
							if (hero.item != null) selectedPreviewFinished = true
						},
						modifier = Modifier.fillMaxSize(),
					)

					// Keep the artwork readable while browsing, then fade all hero
					// shading away when the preview video actually starts.
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

					// Frost only the left and bottom edges of the hero. The top and
					// right stay crisp so the preview keeps a clean cinematic frame.
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

				// Soften only the seam where the sidebar touches the hero preview.
				// Below the 312dp hero area the rail stays crisp/solid.
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
					// NOVA navigation rail: soft fade into the hero instead of a hard divider.
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
										Text(
											text = "NOVA",
											color = Color.White,
											fontSize = 20.sp,
											fontWeight = FontWeight.Black,
											letterSpacing = 2.2.sp,
										)
										Text(
											text = "YOUR MEDIA",
											color = Color(0xFF777777),
											fontSize = 8.sp,
											fontWeight = FontWeight.Bold,
											letterSpacing = 1.2.sp,
										)
									}
								}

								Spacer(Modifier.height(22.dp))

								NovaRailButton(
									label = "Home",
									iconRes = R.drawable.ic_house,
									onClick = { HomeHeroStateStore.resetToPreview() },
									active = true,
									modifier = Modifier
										.fillMaxWidth()
										.focusRequester(railFocusRequester)
										.focusProperties { right = rowsFocusRequester },
								)

								if (moviesView != null) {
									Spacer(Modifier.height(6.dp))
									NovaRailButton(
										label = "Favourites",
										iconRes = R.drawable.ic_star,
										onClick = {
											navigationRepository.navigate(
												Destinations.novaFilteredLibrary(moviesView, NovaBrowseFilters.FILTER_FAVOURITES, null, "Favourites")
											)
										},
										modifier = Modifier.fillMaxWidth().focusProperties { right = rowsFocusRequester },
									)

									Spacer(Modifier.height(6.dp))
									NovaRailButton(
										label = if (showGenres) "Categories ▾" else "Categories ▸",
										iconRes = R.drawable.ic_grid,
										onClick = { showGenres = !showGenres },
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
												NovaRailButton(
													label = genre,
													iconRes = R.drawable.ic_movie,
													onClick = {
														navigationRepository.navigate(
															Destinations.novaFilteredLibrary(moviesView, NovaBrowseFilters.FILTER_GENRE, genre, genre)
														)
													},
													modifier = Modifier
														.fillMaxWidth()
														.padding(start = 10.dp)
														.focusProperties { right = rowsFocusRequester },
												)
											}
										}
									}

								}


								Spacer(Modifier.height(6.dp))
								NovaRailButton(
									label = "Search",
									iconRes = R.drawable.ic_search,
									onClick = { navigationRepository.navigate(Destinations.search()) },
									modifier = Modifier
										.fillMaxWidth()
										.focusProperties { right = rowsFocusRequester },
								)

								Spacer(Modifier.height(20.dp))

								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(horizontal = 8.dp),
									verticalAlignment = Alignment.CenterVertically,
								) {
									Box(
										modifier = Modifier
											.width(18.dp)
											.height(1.dp)
											.background(Color(0x88E50914))
									)
									Spacer(Modifier.width(8.dp))
									Text(
										text = "MY LIBRARIES",
										color = Color(0xFF6F6F6F),
										fontSize = 9.sp,
										fontWeight = FontWeight.Bold,
										letterSpacing = 1.15.sp,
									)
								}
								Spacer(Modifier.height(9.dp))
							}

							items(orderedViews.toList(), key = { it.id }) { view ->
								NovaRailButton(
									label = view.name.orEmpty(),
									iconRes = novaRailIcon(view.name.orEmpty(), view.collectionType),
									onClick = { itemLauncher.launchUserView(view) },
									modifier = Modifier
										.fillMaxWidth()
										.focusProperties { right = rowsFocusRequester },
								)
								Spacer(Modifier.height(5.dp))
							}
						}

					}

					Column(
						modifier = Modifier
							.fillMaxSize()
							.clipToBounds()
					) {
						Box(
							modifier = Modifier
								.fillMaxWidth()
								.height(300.dp)
						) {
							if (heroOverlayAlpha > 0.01f) {
								Column(
									modifier = Modifier
										.align(Alignment.CenterStart)
										.fillMaxWidth(0.62f)
										.alpha(heroOverlayAlpha)
										.padding(start = 28.dp, end = 26.dp, top = 22.dp, bottom = 12.dp)
								) {
								if (hero.item == null && preview != null) {
									Box(
										modifier = Modifier
											.background(Color(0xCC111111), RoundedCornerShape(4.dp))
											.padding(horizontal = 8.dp, vertical = 3.dp)
									) {
										Text(
											text = "LIBRARY PREVIEW",
											color = Color(0xFFE5E5E5),
											fontSize = 10.sp,
											fontWeight = FontWeight.Bold,
											letterSpacing = 0.9.sp,
										)
									}
									Spacer(Modifier.height(10.dp))
								}

								if (displayLogo != null) {
									AsyncImage(
										url = displayLogo.getUrl(api, maxWidth = 620, maxHeight = 160),
										blurHash = displayLogo.blurHash,
										scaleType = ImageView.ScaleType.FIT_START,
										modifier = Modifier
											.fillMaxWidth(0.8f)
											.height(78.dp)
									)
								} else {
									Text(
										text = displayTitle,
										color = Color.White,
										fontSize = 42.sp,
										fontWeight = FontWeight.Bold,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
								}

								Spacer(Modifier.height(9.dp))

								Row(verticalAlignment = Alignment.CenterVertically) {
									if (displayYear.isNotBlank()) {
										Text(displayYear, color = Color(0xFFE4E4E4), fontSize = 14.sp, fontWeight = FontWeight.Bold)
									}
									if (displayYear.isNotBlank() && displayRating.isNotBlank()) {
										Spacer(Modifier.width(9.dp))
										Text("•", color = Color(0xFF9A9A9A), fontSize = 13.sp)
										Spacer(Modifier.width(9.dp))
									}
									if (displayRating.isNotBlank()) {
										Box(
											modifier = Modifier
												.background(Color(0x55333333), RoundedCornerShape(3.dp))
												.padding(horizontal = 7.dp, vertical = 2.dp)
										) {
											Text(displayRating, color = Color(0xFFEAEAEA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
										}
									}
								}

								Spacer(Modifier.height(10.dp))

								Text(
									text = displayOverview,
									color = Color(0xFFE1E1E1),
									fontSize = 15.sp,
									maxLines = 3,
									overflow = TextOverflow.Ellipsis,
									lineHeight = 20.sp,
								)

								if (displayItem != null) {
									Spacer(Modifier.height(14.dp))
									Row(verticalAlignment = Alignment.CenterVertically) {
										Button(
											onClick = {
												itemLauncher.launch(
													BaseItemDtoBaseRowItem(
														item = displayItem,
														selectAction = BaseRowItemSelectAction.Play,
													),
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
										) {
											Text("▶  Play", fontSize = 14.sp, fontWeight = FontWeight.Bold)
										}

										Spacer(Modifier.width(10.dp))

										Button(
											onClick = {
												navigationRepository.navigate(Destinations.novaInfo(displayItem))
											},
											shape = RoundedCornerShape(5.dp),
											colors = ButtonDefaults.colors(
												containerColor = Color(0xAA333333),
												contentColor = Color.White,
												focusedContainerColor = Color(0xFFE50914),
												focusedContentColor = Color.White,
											),
											contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
										) {
											Text("More Info", fontSize = 14.sp, fontWeight = FontWeight.Bold)
										}
									}
								}
								}
							}
						}

						var rowsSupportFragment by remember { mutableStateOf<HomeRowsFragment?>(null) }
						AndroidFragment<HomeRowsFragment>(
							modifier = Modifier
								.focusGroup()
								.focusRequester(rowsFocusRequester)
								.focusProperties {
									onExit = {
										val isFirstRowSelected = rowsSupportFragment?.selectedPosition?.let { it <= 0 } ?: false
										if (requestedFocusDirection != FocusDirection.Up || !isFirstRowSelected) {
											cancelFocusChange()
										} else {
											rowsSupportFragment?.selectedPosition = 0
											rowsSupportFragment?.verticalGridView?.clearFocus()
										}
									}
								}
								.fillMaxSize()
								.clipToBounds(),
							onUpdate = { fragment ->
								rowsSupportFragment = fragment
								fragment.onNavigateToSidebar = {
									railFocusRequester.requestFocus()
								}
							}
						)
					}
				}
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		sessionRepository.currentSession
			.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
			.map { session ->
				if (session == null) null
				else serverRepository.getServer(session.serverId)
			}
			.onEach { server ->
				notificationRepository.updateServerNotifications(server)
			}
			.launchIn(viewLifecycleOwner.lifecycleScope)
	}
}

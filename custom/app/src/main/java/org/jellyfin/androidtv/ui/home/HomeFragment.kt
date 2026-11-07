package org.jellyfin.androidtv.ui.home

import org.jellyfin.androidtv.ui.browsing.NovaDownloadNotifier
import org.jellyfin.androidtv.ui.withAlpha
import org.jellyfin.androidtv.ui.nova
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage as CoilAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.navigation.NovaRemoteLayout
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.ImageType
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

// Preserve the complete still image on the right of the hero, leaving room
// for metadata on the left. Both still layers use identical sizing.
@Composable
internal fun HomeHeroArtwork(url: String, modifier: Modifier = Modifier) {
	val context = LocalContext.current
	val loader = koinInject<coil3.ImageLoader>()
	val request = remember(url, context) {
		ImageRequest.Builder(context).data(url).crossfade(false).build()
	}
	CoilAsyncImage(
		model = request,
		imageLoader = loader,
		contentDescription = null,
		contentScale = ContentScale.Fit,
		alignment = Alignment.CenterEnd,
		modifier = modifier,
	)
}


class HomeFragment : Fragment() {
	private val sessionRepository by inject<SessionRepository>()
	private val serverRepository by inject<ServerRepository>()
	private val notificationRepository by inject<NotificationsRepository>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View = NovaRemoteLayout(requireContext()).apply {
		val remoteLayout = this
		addView(content {
		var rowsFragment by remember { mutableStateOf<HomeRowsFragment?>(null) }
		var menuItem by remember { mutableStateOf<org.jellyfin.sdk.model.api.BaseItemDto?>(null) }
		val railFocusRequester = remember { FocusRequester() }
		val focusScope = rememberCoroutineScope()
		val hero by HomeHeroStateStore.state.collectAsState()
		val preview = rememberRandomHomePreview(enabled = hero.item == null)
		val selectedPreview = rememberSelectedHomePreview(hero.item)
		var selectedPreviewFinished by remember(hero.item?.id) { mutableStateOf(false) }
		var heroVideoPlaying by remember { mutableStateOf(false) }
		val heroOverlayAlpha by animateFloatAsState(
			targetValue = if (heroVideoPlaying) 0.8f else 1f,
			animationSpec = tween(durationMillis = 450),
			label = "heroOverlayAlpha",
		)
		val api = koinInject<ApiClient>()
		val context = LocalContext.current
		// Pop-up + chime when a download finishes, on any NOVA screen.
		LaunchedEffect(Unit) { NovaDownloadNotifier.ensureStarted(context, api) }
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
			hero.item?.novaHeroImage
		}
		val displayLogo = remember(displayItem?.id) {
			displayItem?.itemImages?.get(ImageType.LOGO)
				?: displayItem?.parentImages?.get(ImageType.LOGO)
		}
		val previewFallbackBackdrop = remember(preview?.item?.id) {
			preview?.item?.novaHeroImage
		}

		LaunchedEffect(rowsFragment) {
			val fragment = rowsFragment ?: return@LaunchedEffect
			fragment.view?.post { fragment.requestCardFocus() }
		}

		JellyfinTheme {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(nova.bg)
			) {
				// Fixed NOVA hero stage: it starts after the left rail and ends just
				// below the top edge of the first card row. Both still artwork and
				// hover video use exactly this same area.
				Box(
					// The preview keeps its real 16:9 shape on the right (like Netflix) instead
					// of being cropped into a thin strip; the left side fades into the page.
					modifier = Modifier
						.align(Alignment.TopEnd)
						.height(284.dp)
						.aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
						.clipToBounds()
				) {
					if (selectedBackdrop != null) {
						HomeHeroArtwork(
							url = selectedBackdrop.getUrl(api, maxWidth = 1280, maxHeight = 720),
							modifier = Modifier.fillMaxSize()
						)
					} else if (previewFallbackBackdrop != null) {
						HomeHeroArtwork(
							url = previewFallbackBackdrop.getUrl(api, maxWidth = 1280, maxHeight = 720),
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
									0.0f to nova.bg.withAlpha(0xE8),
									0.30f to nova.bg.withAlpha(0xA8),
									0.62f to nova.bg.withAlpha(0x44),
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

					// Frost only the left and bottom edges of the hero. The top and
					// right stay crisp so the preview keeps a clean cinematic frame.
					Box(
						modifier = Modifier
							.fillMaxSize()
							.background(
								// Fully solid at the very edge, even while a preview plays,
								// so the picture's left edge never shows as a line.
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

				// Soften only the seam where the sidebar touches the hero preview.
				// Below the 312dp hero area the rail stays crisp/solid.
				Box(
					modifier = Modifier
						.padding(start = 150.dp)
						.width(54.dp)
						.height(258.dp)
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
					// NOVA navigation rail: soft fade into the hero instead of a hard divider.
					NovaSideRail(
						current = NovaRailPage.Home,
						focusRequester = railFocusRequester,
						onRight = { rowsFragment?.requestCardFocus() },
						onHome = { HomeHeroStateStore.resetToPreview() },
					)

					Column(
						modifier = Modifier
							.fillMaxSize()
							.clipToBounds()
					) {
						Box(
							modifier = Modifier
								.fillMaxWidth()
								.height(250.dp)
						) {
							if (heroOverlayAlpha > 0.01f) {
								Column(
									modifier = Modifier
										.align(Alignment.CenterStart)
										.fillMaxWidth(0.72f)
										.alpha(heroOverlayAlpha)
										.padding(start = 28.dp, end = 26.dp, top = 22.dp, bottom = 12.dp)
								) {
								if (hero.item == null && preview != null) {
									Box(
										modifier = Modifier
											.background(nova.surface.withAlpha(0xCC), RoundedCornerShape(nova.corner(4)))
											.padding(horizontal = 8.dp, vertical = 3.dp)
									) {
										Text(
											text = "LIBRARY PREVIEW",
											color = nova.textPrimary,
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
											.height(60.dp)
									)
								} else {
									Text(
										text = displayTitle,
										color = Color.White,
										fontSize = 34.sp,
										fontWeight = FontWeight.Bold,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
								}

								Spacer(Modifier.height(9.dp))

								Row(verticalAlignment = Alignment.CenterVertically) {
									if (displayYear.isNotBlank()) {
										Text(displayYear, color = nova.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
									}
									if (displayYear.isNotBlank() && displayRating.isNotBlank()) {
										Spacer(Modifier.width(9.dp))
										Text("•", color = nova.textMuted, fontSize = 13.sp)
										Spacer(Modifier.width(9.dp))
									}
									if (displayRating.isNotBlank()) {
										org.jellyfin.androidtv.ui.browsing.NovaRatingBadge(displayRating)
									}
									org.jellyfin.androidtv.ui.browsing.NovaLengthInfo(displayItem)
								}

								Spacer(Modifier.height(10.dp))

								Text(
									text = displayOverview,
									color = nova.textPrimary,
									fontSize = 15.sp,
									maxLines = 2,
									overflow = TextOverflow.Ellipsis,
									lineHeight = 20.sp,
								)

								}
							}
						}

						AndroidFragment<HomeRowsFragment>(
							modifier = Modifier
								.fillMaxSize()
								.clipToBounds(),
							onUpdate = { fragment ->
								rowsFragment = fragment
								remoteLayout.directionHandler = fragment::handleNavigation
								remoteLayout.longPressHandler = {
									fragment.novaFocusedItem()?.let { menuItem = it; true } ?: false
								}
								fragment.onNavigateToSidebar = {
									focusScope.launch {
										remoteLayout.post {
											remoteLayout.getChildAt(0)?.requestFocus()
											runCatching { railFocusRequester.requestFocus() }
										}
									}
								}
							}
						)
					}
				}
			}
		}
		menuItem?.let { item ->
			org.jellyfin.androidtv.ui.browsing.NovaItemMenu(
				item = item,
				onDismiss = { menuItem = null },
				onRemoved = {
					rowsFragment?.novaRemoveFocusedItem()
					rowsFragment?.view?.post { rowsFragment?.requestCardFocus() }
				},
			)
		}
		})
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


package org.jellyfin.androidtv.ui.browsing

import org.jellyfin.androidtv.ui.withAlpha
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.androidtv.ui.home.NovaSideRail
import org.jellyfin.androidtv.ui.home.NovaRailPage
import android.os.Bundle
import android.view.LayoutInflater
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.home.HomePreviewPlayer
import org.jellyfin.androidtv.ui.home.rememberSelectedHomePreview
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.androidtv.util.apiclient.Response
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType as SdkImageType
import org.koin.compose.koinInject

class NovaItemFragment : Fragment(), android.view.View.OnKeyListener {
	/** ☰ on the remote: NOVA's menu for this episode / title (with Delete). */
	override fun onKey(v: android.view.View?, keyCode: Int, event: android.view.KeyEvent?): Boolean {
		if (!NovaItemMenuHost.isMenuKey(keyCode)) return false
		if (event?.action == android.view.KeyEvent.ACTION_UP && ::item.isInitialized) {
			val episode = focusedEpisode
			if (item.type == BaseItemKind.SEASON && episode != null) {
				// Season page: the menu is for the episode in focus; reload after a delete.
				NovaItemMenuHost.show(requireActivity(), episode) { reloadTick.intValue++ }
			} else {
				NovaItemMenuHost.show(requireActivity(), item) {
					// Deleted: go back to the page it was opened from.
					activity?.onBackPressedDispatcher?.onBackPressed()
				}
			}
		}
		return true
	}

	private lateinit var item: BaseItemDto
	private var focusedEpisode: BaseItemDto? = null
	private val reloadTick = androidx.compose.runtime.mutableIntStateOf(0)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val raw = requireArguments().getString(Extras.Folder)
			?: error("NOVA item screen requires Extras.Folder")
		item = Json.Default.decodeFromString(BaseItemDto.serializer(), raw)
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		// Albums and playlists get a track list instead of the film-style page.
		if (item.type == BaseItemKind.MUSIC_ALBUM || item.type == BaseItemKind.PLAYLIST) {
			NovaAlbumPage(item)
			return@content
		}
		if (item.type == BaseItemKind.MUSIC_ARTIST) {
			NovaArtistPage(item)
			return@content
		}
		if (item.type == BaseItemKind.SEASON) {
			NovaSeasonPage(item, reload = reloadTick.intValue, onFocused = { focusedEpisode = it })
			return@content
		}
		val api = koinInject<ApiClient>()
		val playbackHelper = koinInject<PlaybackHelper>()
		val playbackLauncher = koinInject<PlaybackLauncher>()
		val context = LocalContext.current

		val preview = rememberSelectedHomePreview(item)
		var previewFinished by remember(item.id) { mutableStateOf(false) }
		var videoPlaying by remember { mutableStateOf(false) }
		val overlayAlpha by animateFloatAsState(
			targetValue = if (videoPlaying) 0.8f else 1f,
			animationSpec = tween(450),
			label = "itemHeroOverlayAlpha",
		)
		val playFocusRequester = remember { FocusRequester() }
		val railFocusRequester = remember { FocusRequester() }

		val backdrop = remember(item.id) {
			item.itemBackdropImages.firstOrNull() ?: item.parentBackdropImages.firstOrNull()
		}
		val logo = remember(item.id) {
			item.itemImages[SdkImageType.LOGO] ?: item.parentImages[SdkImageType.LOGO]
		}
		val activeViewId = item.parentId

		LaunchedEffect(item.id) {
			runCatching { playFocusRequester.requestFocus() }
		}

		JellyfinTheme {
			Box(modifier = Modifier.fillMaxSize().background(nova.bg)) {
				Box(
					modifier = Modifier
						.padding(start = 164.dp)
						.fillMaxWidth()
						.height(430.dp)
						.clipToBounds()
				) {
					if (preview != null && !previewFinished) {
						HomePreviewPlayer(
							preview = preview,
							onPlayingChanged = { videoPlaying = it },
							onPreviewFinished = { previewFinished = true },
							modifier = Modifier.fillMaxSize(),
						)
					} else if (backdrop != null) {
						AsyncImage(
							url = backdrop.getUrl(api, maxWidth = 1280, maxHeight = 720),
							blurHash = backdrop.blurHash,
							aspectRatio = 16f / 9f,
							scaleType = ImageView.ScaleType.CENTER_CROP,
							modifier = Modifier.fillMaxSize(),
						)
					}

					Box(
						modifier = Modifier.fillMaxSize().alpha(overlayAlpha).background(
							Brush.horizontalGradient(
								0f to nova.bg.withAlpha(0xE8),
								0.30f to nova.bg.withAlpha(0xA8),
								0.62f to nova.bg.withAlpha(0x44),
								1f to Color.Transparent,
							)
						)
					)
					Box(
						modifier = Modifier.fillMaxSize().alpha(overlayAlpha).background(
							Brush.verticalGradient(
								0f to nova.bg.withAlpha(0x12),
								0.72f to nova.bg.withAlpha(0x18),
								0.92f to nova.bg.withAlpha(0xC8),
								1f to nova.bg,
							)
						)
					)
					Box(
						modifier = Modifier.fillMaxSize().background(
							Brush.horizontalGradient(
								0f to nova.bg.withAlpha(0xC8),
								0.05f to nova.bg.withAlpha(0xA8),
								0.11f to nova.bg.withAlpha(0x66),
								0.18f to nova.bg.withAlpha(0x26),
								0.25f to Color.Transparent,
							)
						)
					)
					Box(
						modifier = Modifier.fillMaxSize().background(
							Brush.verticalGradient(
								0f to Color.Transparent,
								0.67f to Color.Transparent,
								0.76f to nova.bg.withAlpha(0x22),
								0.86f to nova.bg.withAlpha(0x55),
								0.94f to nova.bg.withAlpha(0x99),
								1f to nova.bg.withAlpha(0xE8),
							)
						)
					)
				}

				Box(
					modifier = Modifier
						.padding(start = 150.dp)
						.width(54.dp)
						.height(430.dp)
						.background(
							Brush.horizontalGradient(
								0f to nova.bgDeep.withAlpha(0xFC),
								0.30f to nova.bgDeep.withAlpha(0xE8),
								0.58f to nova.bgDeep.withAlpha(0xA8),
								0.80f to nova.bgDeep.withAlpha(0x55),
								1f to Color.Transparent,
							)
						)
				)

				Row(modifier = Modifier.fillMaxSize()) {
					NovaSideRail(
						current = activeViewId?.let { NovaRailPage.Library(it) } ?: NovaRailPage.None,
						focusRequester = railFocusRequester,
					)

					Box(modifier = Modifier.fillMaxSize()) {
						Column(
							modifier = Modifier
								.align(Alignment.TopStart)
								.fillMaxWidth(0.66f)
								.alpha(overlayAlpha)
								.padding(start = 28.dp, end = 26.dp, top = 52.dp, bottom = 12.dp)
						) {
							if (logo != null) {
								AsyncImage(
									url = logo.getUrl(api, maxWidth = 620, maxHeight = 160),
									blurHash = logo.blurHash,
									scaleType = ImageView.ScaleType.FIT_START,
									modifier = Modifier.fillMaxWidth(0.8f).height(72.dp),
								)
							} else {
								Text(
									item.name.orEmpty(),
									color = Color.White,
									fontSize = 34.sp,
									fontWeight = FontWeight.Bold,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
							}

							Spacer(Modifier.height(10.dp))
							Row(verticalAlignment = Alignment.CenterVertically) {
								item.productionYear?.let {
									Text(it.toString(), color = nova.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
								}
								if (!item.officialRating.isNullOrBlank()) {
									Spacer(Modifier.width(9.dp))
									Text("•", color = nova.textMuted, fontSize = 13.sp)
									Spacer(Modifier.width(9.dp))
									org.jellyfin.androidtv.ui.browsing.NovaRatingBadge(item.officialRating.orEmpty())
								}
								org.jellyfin.androidtv.ui.browsing.NovaLengthInfo(item)
							}
							Spacer(Modifier.height(12.dp))
							Text(
								item.overview.orEmpty(),
								color = nova.textPrimary,
								fontSize = 15.sp,
								maxLines = 4,
								overflow = TextOverflow.Ellipsis,
								lineHeight = 20.sp,
							)
							Spacer(Modifier.height(18.dp))
							Row(verticalAlignment = Alignment.CenterVertically) {
							Button(
								onClick = {
									playbackHelper.getItemsToPlay(
										context,
										item,
										item.type == BaseItemKind.MOVIE,
										false,
										object : Response<List<BaseItemDto>>() {
											override fun onResponse(response: List<BaseItemDto>) {
												if (!isActive || response.isEmpty()) return
												playbackLauncher.launch(context, response)
											}
										},
									)
								},
								modifier = Modifier.focusRequester(playFocusRequester),
								shape = RoundedCornerShape(nova.corner(5)),
								colors = ButtonDefaults.colors(
									containerColor = nova.textPrimary,
									contentColor = nova.surface,
									focusedContainerColor = nova.accent,
									focusedContentColor = nova.onAccent,
								),
								contentPadding = PaddingValues(horizontal = 24.dp, vertical = 9.dp),
							) {
								Text("▶  Play", fontSize = 15.sp, fontWeight = FontWeight.Bold)
							}
							Spacer(Modifier.width(10.dp))
							NovaActionButton(item = item)
							}
						}
					}
				}
			}
		}
	}
}

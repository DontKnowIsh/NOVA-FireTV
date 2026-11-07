package org.jellyfin.androidtv.ui.browsing

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.home.HomePreviewPlaybackController
import org.jellyfin.androidtv.ui.home.NovaRailPage
import org.jellyfin.androidtv.ui.home.NovaSideRail
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.androidtv.ui.withAlpha
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.compose.koinInject
import timber.log.Timber
import org.jellyfin.sdk.model.api.ImageType as SdkImageType

/**
 * NOVA page for one season: the show's artwork behind, then every episode with
 * its picture, length, description and how far you got. OK plays an episode;
 * ☰ opens NOVA's menu for the episode in focus ([onFocused] reports it).
 */
@Composable
fun NovaSeasonPage(season: BaseItemDto, reload: Int, onFocused: (BaseItemDto?) -> Unit) {
	val api = koinInject<ApiClient>()
	val playbackHelper = koinInject<PlaybackHelper>()
	val context = LocalContext.current
	val railFocusRequester = remember { FocusRequester() }
	val firstFocus = remember { FocusRequester() }
	val listState = rememberLazyListState()
	var episodes by remember(season.id) { mutableStateOf<List<BaseItemDto>?>(null) }
	var focusIndex by remember(season.id) { mutableStateOf(0) }

	LaunchedEffect(season.id, reload) {
		val loaded = withContext(Dispatchers.IO) {
			runCatching {
				api.itemsApi.getItems(
					parentId = season.id,
					includeItemTypes = listOf(BaseItemKind.EPISODE),
					isMissing = false,
					sortBy = listOf(ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER, ItemSortBy.SORT_NAME),
					recursive = true,
					fields = ItemRepository.itemFields,
				).content.items
			}.onFailure { Timber.w(it, "NOVA: couldn't load episodes for %s", season.name) }.getOrDefault(emptyList())
		}
		val firstLoad = episodes == null
		episodes = loaded
		if (loaded.isEmpty()) return@LaunchedEffect
		// Start on the first episode you haven't finished.
		if (firstLoad) focusIndex = loaded.indexOfFirst { it.userData?.played != true }.takeIf { it >= 0 } ?: 0
		focusIndex = focusIndex.coerceAtMost(loaded.lastIndex)
		runCatching { listState.scrollToItem(focusIndex) }
		delay(80L)
		runCatching { firstFocus.requestFocus() }
	}

	val backdrop = remember(season.id) {
		season.itemBackdropImages.firstOrNull() ?: season.parentBackdropImages.firstOrNull()
	}
	val logo = remember(season.id) {
		season.itemImages[SdkImageType.LOGO] ?: season.parentImages[SdkImageType.LOGO]
	}
	val list = episodes

	JellyfinTheme {
		Box(modifier = Modifier.fillMaxSize().background(nova.bg)) {
			if (backdrop != null) {
				AsyncImage(
					url = backdrop.getUrl(api, maxWidth = 1280, maxHeight = 720),
					blurHash = backdrop.blurHash,
					scaleType = ImageView.ScaleType.CENTER_CROP,
					modifier = Modifier.fillMaxSize().alpha(0.35f),
				)
				Box(
					Modifier.fillMaxSize().background(
						Brush.verticalGradient(
							0f to nova.bg.withAlpha(0x55),
							0.45f to nova.bg.withAlpha(0xAA),
							1f to nova.bg.withAlpha(0xE0),
						)
					)
				)
			}
			Row(modifier = Modifier.fillMaxSize()) {
				NovaSideRail(current = NovaRailPage.None, focusRequester = railFocusRequester)
				Column(modifier = Modifier.fillMaxSize().padding(start = 28.dp, end = 40.dp, top = 34.dp)) {
					if (logo != null) {
						AsyncImage(
							url = logo.getUrl(api, maxWidth = 620, maxHeight = 160),
							blurHash = logo.blurHash,
							scaleType = ImageView.ScaleType.FIT_START,
							modifier = Modifier.fillMaxWidth(0.5f).height(60.dp),
						)
					} else {
						Text(
							season.seriesName.orEmpty(),
							color = Color.White,
							fontSize = 30.sp,
							fontWeight = FontWeight.Bold,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
					Spacer(Modifier.height(6.dp))
					Row(verticalAlignment = Alignment.CenterVertically) {
						Text(season.name.orEmpty(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
						list?.size?.let { count ->
							Spacer(Modifier.width(10.dp))
							Text(if (count == 1) "1 episode" else "$count episodes", color = nova.textMuted, fontSize = 14.sp)
						}
					}
					Spacer(Modifier.height(14.dp))
					when {
						list == null -> Text("Loading episodes…", color = nova.textMuted, fontSize = 14.sp)
						list.isEmpty() -> Text("No episodes yet.", color = nova.textMuted, fontSize = 14.sp)
						else -> LazyColumn(
							state = listState,
							contentPadding = PaddingValues(bottom = 40.dp),
							verticalArrangement = Arrangement.spacedBy(6.dp),
							modifier = Modifier.fillMaxSize(),
						) {
							itemsIndexed(list, key = { _, ep -> ep.id }) { index, episode ->
								EpisodeRow(
									episode = episode,
									modifier = if (index == focusIndex) Modifier.focusRequester(firstFocus) else Modifier,
									onFocused = {
										focusIndex = index
										onFocused(episode)
									},
									onClick = {
										HomePreviewPlaybackController.stopActivePreview()
										playbackHelper.retrieveAndPlay(episode.id, false, null, context)
									},
								)
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun EpisodeRow(
	episode: BaseItemDto,
	modifier: Modifier,
	onFocused: () -> Unit,
	onClick: () -> Unit,
) {
	val api = koinInject<ApiClient>()
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(nova.corner(8))
	val thumb = episode.itemImages[SdkImageType.PRIMARY]
	val played = episode.userData?.played == true
	val percent = (episode.userData?.playedPercentage ?: 0.0).toFloat() / 100f
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = modifier
			.fillMaxWidth()
			.clip(shape)
			.background(if (focused) nova.surfaceHigh.withAlpha(0xDD) else nova.surface.withAlpha(0x88))
			.then(if (focused) Modifier.border(2.dp, nova.accent, shape) else Modifier)
			.onFocusChanged { if (it.isFocused) onFocused() }
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
			.padding(8.dp),
	) {
		Box(
			modifier = Modifier.width(176.dp).height(99.dp).clip(RoundedCornerShape(nova.corner(6))).background(nova.surfaceRaised),
			contentAlignment = Alignment.Center,
		) {
			if (thumb != null) {
				AsyncImage(
					url = thumb.getUrl(api, maxWidth = 352, maxHeight = 198),
					blurHash = thumb.blurHash,
					scaleType = ImageView.ScaleType.CENTER_CROP,
					modifier = Modifier.fillMaxSize(),
				)
			}
			if (focused) {
				Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
					Text("▶", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
				}
			}
			if (played) {
				Box(
					modifier = Modifier
						.align(Alignment.TopEnd)
						.padding(4.dp)
						.background(NovaStatusColors.Downloaded, RoundedCornerShape(nova.corner(3)))
						.padding(horizontal = 5.dp, vertical = 1.dp),
				) { Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
			} else if (percent > 0.01f) {
				Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.5f))) {
					Box(Modifier.fillMaxWidth(percent.coerceIn(0f, 1f)).fillMaxHeight().background(nova.accent))
				}
			}
		}
		Spacer(Modifier.width(16.dp))
		Column(modifier = Modifier.weight(1f)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(
					listOfNotNull(episode.indexNumber?.let { "$it." }, episode.name).joinToString(" "),
					color = Color.White,
					fontSize = 16.sp,
					fontWeight = FontWeight.Bold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f, fill = false),
				)
				episode.runTimeTicks?.takeIf { it > 0 }?.let { ticks ->
					Spacer(Modifier.width(10.dp))
					val minutes = ticks / 600_000_000L
					Text(
						if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m",
						color = nova.textMuted,
						fontSize = 13.sp,
					)
				}
			}
			if (!episode.overview.isNullOrBlank()) {
				Spacer(Modifier.height(4.dp))
				Text(
					episode.overview.orEmpty(),
					color = nova.textSecondary,
					fontSize = 13.sp,
					lineHeight = 17.sp,
					maxLines = if (focused) 4 else 2,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}

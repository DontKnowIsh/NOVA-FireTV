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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.home.NovaRailPage
import org.jellyfin.androidtv.ui.home.NovaSideRail
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.androidtv.ui.withAlpha
import org.jellyfin.androidtv.util.apiclient.albumPrimaryImage
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.koin.compose.koinInject
import timber.log.Timber
import org.jellyfin.sdk.model.api.ImageType as SdkImageType

/** NOVA page for a music artist: photo, Play all / Shuffle, and their albums. */
@Composable
fun NovaArtistPage(artist: BaseItemDto) {
	val api = koinInject<ApiClient>()
	val navigationRepository = koinInject<NavigationRepository>()
	val playFocusRequester = remember { FocusRequester() }
	val railFocusRequester = remember { FocusRequester() }
	var albums by remember(artist.id) { mutableStateOf<List<BaseItemDto>?>(null) }
	var songs by remember(artist.id) { mutableStateOf<List<BaseItemDto>>(emptyList()) }

	LaunchedEffect(artist.id) {
		albums = withContext(Dispatchers.IO) {
			runCatching {
				api.itemsApi.getItems(
					albumArtistIds = listOf(artist.id),
					includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
					recursive = true,
					sortBy = listOf(ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME),
					sortOrder = listOf(SortOrder.DESCENDING),
					fields = ItemRepository.itemFields,
				).content.items
			}.onFailure { Timber.w(it, "NOVA: couldn't load albums for %s", artist.name) }.getOrDefault(emptyList())
		}
		delay(80L)
		runCatching { playFocusRequester.requestFocus() }
		songs = withContext(Dispatchers.IO) {
			runCatching {
				api.itemsApi.getItems(
					artistIds = listOf(artist.id),
					isMissing = false,
					mediaTypes = listOf(MediaType.AUDIO),
					filters = listOf(ItemFilter.IS_NOT_FOLDER),
					sortBy = listOf(ItemSortBy.ALBUM, ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER, ItemSortBy.SORT_NAME),
					recursive = true,
					fields = ItemRepository.itemFields,
				).content.items
			}.getOrDefault(emptyList())
		}
	}

	// Same controls as an album: Play all → Pause / Resume, ⏮ ⏭, the remote's
	// ⏪ ⏯ ⏩, and what's playing in the top-right corner.
	val music = rememberNovaMusicController()
	val playingHere = music.isPlayingFrom(songs)

	val photo = artist.itemImages[SdkImageType.PRIMARY]
	val backdrop = artist.itemBackdropImages.firstOrNull()
	val list = albums

	JellyfinTheme {
		Box(modifier = Modifier.fillMaxSize().background(nova.bg).novaMusicKeys(music, playingHere)) {
			val back = backdrop ?: photo
			if (back != null) {
				AsyncImage(
					url = back.getUrl(api, maxWidth = 1280, maxHeight = 720),
					blurHash = back.blurHash,
					scaleType = ImageView.ScaleType.CENTER_CROP,
					modifier = Modifier.fillMaxSize().alpha(0.2f),
				)
				Box(
					Modifier.fillMaxSize().background(
						Brush.verticalGradient(0f to nova.bg.withAlpha(0x66), 1f to nova.bg.withAlpha(0xEE))
					)
				)
			}
			Row(modifier = Modifier.fillMaxSize()) {
				NovaSideRail(
					current = artist.parentId?.let { NovaRailPage.Library(it) } ?: NovaRailPage.None,
					focusRequester = railFocusRequester,
				)
				Column(modifier = Modifier.fillMaxSize().padding(start = 28.dp, end = 40.dp, top = 34.dp)) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						Box(
							modifier = Modifier.size(140.dp).clip(CircleShape).background(nova.surfaceRaised),
							contentAlignment = Alignment.Center,
						) {
							if (photo != null) {
								AsyncImage(
									url = photo.getUrl(api, maxWidth = 320, maxHeight = 320),
									blurHash = photo.blurHash,
									scaleType = ImageView.ScaleType.CENTER_CROP,
									modifier = Modifier.fillMaxSize(),
								)
							} else {
								Text("♪", color = nova.textMuted, fontSize = 44.sp)
							}
						}
						Spacer(Modifier.width(24.dp))
						Column {
							Text("ARTIST", color = nova.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
							Text(
								artist.name.orEmpty(),
								color = Color.White,
								fontSize = 30.sp,
								fontWeight = FontWeight.Bold,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							list?.let {
								Text(
									listOfNotNull(
										if (it.size == 1) "1 album" else "${it.size} albums",
										songs.size.takeIf { n -> n > 0 }?.let { n -> if (n == 1) "1 song" else "$n songs" },
									).joinToString("  •  "),
									color = nova.textMuted,
									fontSize = 13.sp,
								)
							}
							Spacer(Modifier.height(14.dp))
							Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
								Button(
									onClick = { if (playingHere) music.togglePause() else music.play(songs) },
									// Fixed width so Play all / Pause / Resume don't shift the others.
									modifier = Modifier.focusRequester(playFocusRequester).width(132.dp),
									shape = RoundedCornerShape(nova.corner(5)),
									colors = ButtonDefaults.colors(
										containerColor = nova.textPrimary,
										contentColor = nova.surface,
										focusedContainerColor = nova.accent,
										focusedContentColor = nova.onAccent,
									),
									contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
								) {
									Text(
										when {
											!playingHere -> "▶  Play all"
											music.isPlaying -> "❚❚  Pause"
											else -> "▶  Resume"
										},
										fontSize = 14.sp,
										fontWeight = FontWeight.Bold,
										textAlign = TextAlign.Center,
										modifier = Modifier.fillMaxWidth(),
									)
								}
								Button(
									onClick = { music.play(songs, shuffle = true) },
									shape = RoundedCornerShape(nova.corner(5)),
									colors = ButtonDefaults.colors(
										containerColor = if (playingHere && music.shuffleOn) nova.accent.copy(alpha = 0.6f) else nova.surfaceHigh.withAlpha(0xAA),
										contentColor = Color.White,
										focusedContainerColor = nova.accent,
										focusedContentColor = nova.onAccent,
									),
									contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
								) { Text("⤮  Shuffle", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
								if (playingHere) {
									NovaMusicSmallButton("⏮") { music.skip(-1) }
									NovaMusicSmallButton("⏭") { music.skip(1) }
								}
							}
						}
					}
					Spacer(Modifier.height(18.dp))
					when {
						list == null -> Text("Loading albums…", color = nova.textMuted, fontSize = 14.sp)
						list.isEmpty() -> Text("No albums found.", color = nova.textMuted, fontSize = 14.sp)
						else -> {
							Text("Albums", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
							Spacer(Modifier.height(8.dp))
							LazyVerticalGrid(
								columns = GridCells.Adaptive(132.dp),
								horizontalArrangement = Arrangement.spacedBy(14.dp),
								verticalArrangement = Arrangement.spacedBy(14.dp),
								contentPadding = PaddingValues(bottom = 32.dp),
								modifier = Modifier.fillMaxSize(),
							) {
								itemsIndexed(list, key = { _, a -> a.id }) { _, album ->
									AlbumCard(album) { navigationRepository.navigate(Destinations.novaItem(album)) }
								}
							}
						}
					}
				}
			}
			// What's playing, in the far top-right corner of the screen.
			if (playingHere) {
				Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 16.dp)) {
					NowPlayingCard(music)
				}
			}
		}
	}
}

@Composable
private fun AlbumCard(album: BaseItemDto, onClick: () -> Unit) {
	val api = koinInject<ApiClient>()
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(nova.corner(8))
	val cover = album.itemImages[SdkImageType.PRIMARY]
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
	) {
		Box(
			modifier = Modifier
				.size(128.dp)
				.clip(shape)
				.background(nova.surfaceRaised)
				.then(if (focused) Modifier.border(3.dp, nova.accent, shape) else Modifier),
			contentAlignment = Alignment.Center,
		) {
			if (cover != null) {
				AsyncImage(
					url = cover.getUrl(api, maxWidth = 300, maxHeight = 300),
					blurHash = cover.blurHash,
					scaleType = ImageView.ScaleType.CENTER_CROP,
					modifier = Modifier.fillMaxSize(),
				)
			} else {
				Text("♪", color = nova.textMuted, fontSize = 32.sp)
			}
		}
		Spacer(Modifier.height(5.dp))
		Text(
			album.name.orEmpty(),
			color = if (focused) Color.White else nova.textPrimary,
			fontSize = 12.sp,
			fontWeight = FontWeight.Bold,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			textAlign = TextAlign.Center,
			modifier = Modifier.fillMaxWidth(),
		)
		album.productionYear?.let { Text(it.toString(), color = nova.textMuted, fontSize = 11.sp) }
	}
}

@Composable
private fun NowPlayingCard(music: NovaMusicController) {
	val api = koinInject<ApiClient>()
	val current = music.nowPlaying ?: return
	val art = current.itemImages[SdkImageType.PRIMARY] ?: current.albumPrimaryImage
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.width(300.dp)
			.clip(RoundedCornerShape(nova.corner(10)))
			.background(nova.surface.withAlpha(0xCC))
			.padding(10.dp),
	) {
		Box(
			modifier = Modifier.size(64.dp).clip(RoundedCornerShape(nova.corner(6))).background(nova.surfaceRaised),
			contentAlignment = Alignment.Center,
		) {
			if (art != null) {
				AsyncImage(
					url = art.getUrl(api, maxWidth = 160, maxHeight = 160),
					blurHash = art.blurHash,
					scaleType = ImageView.ScaleType.CENTER_CROP,
					modifier = Modifier.fillMaxSize(),
				)
			} else {
				Text("♪", color = nova.textMuted, fontSize = 24.sp)
			}
		}
		Spacer(Modifier.width(12.dp))
		Column(modifier = Modifier.weight(1f)) {
			Text(
				if (music.isPlaying) "NOW PLAYING" else "PAUSED",
				color = nova.accent,
				fontSize = 11.sp,
				fontWeight = FontWeight.Bold,
				letterSpacing = 1.5.sp,
			)
			Text(
				current.name.orEmpty(),
				color = Color.White,
				fontSize = 15.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			if (!current.album.isNullOrBlank()) {
				Text(
					current.album.orEmpty(),
					color = nova.textMuted,
					fontSize = 12.sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
			Spacer(Modifier.height(6.dp))
			Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(nova.surfaceHigh)) {
				Box(Modifier.fillMaxWidth(music.progress).height(3.dp).background(nova.accent))
			}
		}
	}
}

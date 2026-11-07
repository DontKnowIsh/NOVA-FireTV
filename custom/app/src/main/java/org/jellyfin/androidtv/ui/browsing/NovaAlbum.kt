package org.jellyfin.androidtv.ui.browsing

import android.widget.ImageView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.home.NovaRailPage
import org.jellyfin.androidtv.ui.home.NovaSideRail
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.androidtv.ui.playback.AudioEventListener
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.rewrite.RewriteMediaManager
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.playback.core.model.PlaybackOrder
import org.jellyfin.playback.core.queue.queue
import org.jellyfin.androidtv.ui.home.HomePreviewPlaybackController
import org.jellyfin.androidtv.ui.withAlpha
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.koin.compose.koinInject
import timber.log.Timber
import org.jellyfin.sdk.model.api.ImageType as SdkImageType

/** NOVA page for a music album or playlist: cover, Play / Shuffle / ♥ and the track list. */
@Composable
fun NovaAlbumPage(album: BaseItemDto) {
	val api = koinInject<ApiClient>()
	val mediaManager = koinInject<MediaManager>()
	val playbackManager = koinInject<PlaybackManager>()
	val itemMutationRepository = koinInject<ItemMutationRepository>()
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val playFocusRequester = remember { FocusRequester() }
	val railFocusRequester = remember { FocusRequester() }
	var tracks by remember(album.id) { mutableStateOf<List<BaseItemDto>?>(null) }
	var favourite by remember(album.id) { mutableStateOf(album.userData?.isFavorite == true) }
	val isPlaylist = album.type == BaseItemKind.PLAYLIST

	// What's playing, so the page can show it and offer pause / skip.
	var nowPlaying by remember { mutableStateOf(mediaManager.currentAudioItem) }
	var isPlaying by remember { mutableStateOf(mediaManager.isPlayingAudio) }
	var shuffleOn by remember { mutableStateOf(mediaManager.isShuffleMode) }
	var progress by remember { mutableStateOf(0f) }
	DisposableEffect(mediaManager) {
		// Changing song briefly reports "stopped / nothing playing"; only believe
		// a pause or an empty queue if it lasts, so the buttons and the
		// "Now playing" line don't flicker on every skip.
		var settle: kotlinx.coroutines.Job? = null
		fun later(block: () -> Unit) {
			settle?.cancel()
			settle = scope.launch {
				delay(800L)
				block()
			}
		}
		val listener = object : AudioEventListener {
			override fun onPlaybackStateChange(newState: PlaybackController.PlaybackState, currentItem: BaseItemDto?) {
				if (currentItem != null) nowPlaying = currentItem
				shuffleOn = mediaManager.isShuffleMode
				when (newState) {
					PlaybackController.PlaybackState.PLAYING,
					PlaybackController.PlaybackState.BUFFERING,
					PlaybackController.PlaybackState.SEEKING -> {
						settle?.cancel()
						isPlaying = true
					}
					PlaybackController.PlaybackState.PAUSED -> later { isPlaying = false }
					PlaybackController.PlaybackState.ERROR -> later { isPlaying = false }
					else -> Unit
				}
			}

			override fun onProgress(pos: Long, duration: Long) {
				progress = if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f
			}

			override fun onQueueStatusChanged(hasQueue: Boolean) {
				if (!hasQueue) {
					later {
						if (!mediaManager.hasAudioQueueItems()) {
							nowPlaying = null
							isPlaying = false
							progress = 0f
						}
					}
				}
			}
		}
		mediaManager.addAudioEventListener(listener)
		onDispose { mediaManager.removeAudioEventListener(listener) }
	}

	LaunchedEffect(album.id) {
		tracks = withContext(Dispatchers.IO) {
			runCatching {
				val response = if (isPlaylist) {
					api.itemsApi.getItems(
						parentId = album.id,
						isMissing = false,
						recursive = true,
						fields = ItemRepository.itemFields,
					)
				} else {
					api.itemsApi.getItems(
						albumIds = listOf(album.id),
						isMissing = false,
						mediaTypes = listOf(MediaType.AUDIO),
						filters = listOf(ItemFilter.IS_NOT_FOLDER),
						sortBy = listOf(ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER, ItemSortBy.SORT_NAME),
						recursive = true,
						fields = ItemRepository.itemFields,
					)
				}
				response.content.items
			}.onFailure { Timber.w(it, "NOVA: couldn't load tracks for %s", album.name) }.getOrDefault(emptyList())
		}
		delay(80L)
		runCatching { playFocusRequester.requestFocus() }
	}

	fun play(startAt: Int = 0, shuffle: Boolean = false) {
		val list = tracks.orEmpty()
		if (list.isEmpty()) return
		// Plays right here (no separate player screen); the page shows what's on.
		HomePreviewPlaybackController.stopActivePreview()
		if (shuffle) {
			mediaManager.playNow(context, list, 0, true)
		} else {
			// The whole album goes in the queue, starting at the chosen song, so
			// ⏮ can still go back to the songs before it.
			scope.launch {
				runCatching {
					playbackManager.state.setPlaybackOrder(PlaybackOrder.DEFAULT)
					playbackManager.queue.clear()
					playbackManager.queue.addSupplier(RewriteMediaManager.BaseItemQueueSupplier(api, list, true))
					if (startAt > 0) playbackManager.queue.setIndex(startAt, true)
					playbackManager.state.play()
				}.onFailure {
					Timber.w(it, "NOVA: couldn't start album at %d", startAt)
					mediaManager.playNow(context, list, startAt, false)
				}
			}
		}
		nowPlaying = list.getOrNull(if (shuffle) 0 else startAt)
		shuffleOn = shuffle
		isPlaying = true
	}

	// Previous / next song by position in the album (Jellyfin's own "previous"
	// only goes back through songs already played).
	val hold = remember { HoldState() }

	// Pause / resume: the button changes straight away rather than waiting for the player.
	fun togglePause() {
		mediaManager.togglePlayPause()
		isPlaying = !isPlaying
	}

	// The list follows the music: when the song changes it scrolls to it, and if
	// you're in the list the highlight moves with it.
	val trackListState = rememberLazyListState()
	val playingFocus = remember { FocusRequester() }
	var listHasFocus by remember { mutableStateOf(false) }
	LaunchedEffect(nowPlaying?.id, tracks) {
		val index = tracks.orEmpty().indexOfFirst { it.id == nowPlaying?.id }
		if (index < 0) return@LaunchedEffect
		// Jump straight there (a smooth scroll on every skip was slow on the
		// Fire Stick and left two rows highlighted while it caught up).
		runCatching { trackListState.scrollToItem((index - 1).coerceAtLeast(0)) }
		if (listHasFocus) {
			androidx.compose.runtime.withFrameNanos { }
			runCatching { playingFocus.requestFocus() }
		}
	}

	fun skip(by: Int) {
		if (mediaManager.isShuffleMode) {
			if (by > 0) {
				if (mediaManager.hasNextAudioItem()) mediaManager.nextAudioItem()
			} else if (mediaManager.hasPrevAudioItem()) {
				mediaManager.prevAudioItem()
			}
			return
		}
		scope.launch {
			val queue = playbackManager.queue
			val target = queue.entryIndex.value + by
			if (target >= 0 && target < queue.estimatedSize) queue.setIndex(target, true)
		}
	}
	val playingHere = nowPlaying != null && tracks.orEmpty().any { it.id == nowPlaying?.id }

	val cover = album.itemImages[SdkImageType.PRIMARY]
	val artist = album.albumArtist ?: album.albumArtists?.joinToString(", ") { it.name.orEmpty() }
	val list = tracks
	val totalTicks = list?.sumOf { it.runTimeTicks ?: 0L } ?: album.runTimeTicks ?: 0L
	val facts = listOfNotNull(
		album.productionYear?.toString(),
		list?.size?.let { if (it == 1) "1 track" else "$it tracks" },
		totalTicks.takeIf { it > 0 }?.let { minutesText(it) },
	).joinToString("  •  ")

	JellyfinTheme {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(nova.bg)
				// The remote's ⏪ ⏯ ⏩ buttons work wherever you are in the list:
				// tap ⏪ / ⏩ for the previous / next song, hold to go back /
				// forward 10 seconds at a time; ⏯ pauses or plays.
				.onPreviewKeyEvent { event ->
					if (!playingHere) return@onPreviewKeyEvent false
					val direction = when (event.key) {
						Key.MediaFastForward, Key.MediaNext -> 1
						Key.MediaRewind, Key.MediaPrevious -> -1
						else -> 0
					}
					if (direction != 0) {
						when (event.type) {
							KeyEventType.KeyDown -> if (event.nativeKeyEvent.repeatCount == 0) {
								hold.seeks = 0
								hold.lastSeek = 0L
							} else {
								val now = android.os.SystemClock.uptimeMillis()
								if (now - hold.lastSeek >= 400L) {
									hold.lastSeek = now
									hold.seeks++
									if (direction > 0) playbackManager.state.fastForward(10.seconds)
									else playbackManager.state.rewind(10.seconds)
								}
							}
							KeyEventType.KeyUp -> {
								if (hold.seeks == 0) skip(direction)
								hold.seeks = 0
							}
						}
						return@onPreviewKeyEvent true
					}
					when (event.key) {
						Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
							if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) togglePause()
							true
						}
						else -> false
					}
				},
		) {
			// The cover faintly behind the page.
			if (cover != null) {
				AsyncImage(
					url = cover.getUrl(api, maxWidth = 640, maxHeight = 640),
					blurHash = cover.blurHash,
					scaleType = ImageView.ScaleType.CENTER_CROP,
					modifier = Modifier.fillMaxSize().alpha(0.18f),
				)
				Box(
					Modifier.fillMaxSize().background(
						Brush.verticalGradient(
							0f to nova.bg.withAlpha(0x66),
							0.5f to nova.bg.withAlpha(0xBB),
							1f to nova.bg.withAlpha(0xEE),
						)
					)
				)
			}
			Row(modifier = Modifier.fillMaxSize()) {
				NovaSideRail(
					current = album.parentId?.let { NovaRailPage.Library(it) } ?: NovaRailPage.None,
					focusRequester = railFocusRequester,
				)
				Column(modifier = Modifier.fillMaxSize().padding(start = 28.dp, end = 40.dp, top = 36.dp)) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						Box(
							modifier = Modifier.size(170.dp).clip(RoundedCornerShape(nova.corner(8))).background(nova.surfaceRaised),
							contentAlignment = Alignment.Center,
						) {
							if (cover != null) {
								AsyncImage(
									url = cover.getUrl(api, maxWidth = 400, maxHeight = 400),
									blurHash = cover.blurHash,
									scaleType = ImageView.ScaleType.CENTER_CROP,
									modifier = Modifier.fillMaxSize(),
								)
							} else {
								Text("♪", color = nova.textMuted, fontSize = 48.sp)
							}
						}
						Spacer(Modifier.width(24.dp))
						Column {
							Text(
								if (isPlaylist) "PLAYLIST" else "ALBUM",
								color = nova.accent,
								fontSize = 12.sp,
								fontWeight = FontWeight.Bold,
								letterSpacing = 2.sp,
							)
							Text(
								album.name.orEmpty(),
								color = Color.White,
								fontSize = 30.sp,
								fontWeight = FontWeight.Bold,
								maxLines = 2,
								overflow = TextOverflow.Ellipsis,
							)
							if (!artist.isNullOrBlank()) Text(artist, color = nova.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
							if (facts.isNotBlank()) {
								Spacer(Modifier.height(4.dp))
								Text(facts, color = nova.textMuted, fontSize = 13.sp)
							}
							Spacer(Modifier.height(14.dp))
							Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
								Button(
									onClick = { if (playingHere) togglePause() else play() },
									// Fixed width so Play / Pause / Resume don't shift the other buttons.
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
											!playingHere -> "▶  Play"
											isPlaying -> "❚❚  Pause"
											else -> "▶  Resume"
										},
										fontSize = 14.sp,
										fontWeight = FontWeight.Bold,
										textAlign = TextAlign.Center,
										modifier = Modifier.fillMaxWidth(),
									)
								}
								Button(
									onClick = { play(shuffle = true) },
									shape = RoundedCornerShape(nova.corner(5)),
									// Lit up while this album is playing shuffled.
									colors = ButtonDefaults.colors(
										containerColor = if (shuffleOn && playingHere) nova.accent.copy(alpha = 0.6f) else nova.surfaceHigh.withAlpha(0xAA),
										contentColor = Color.White,
										focusedContainerColor = nova.accent,
										focusedContentColor = nova.onAccent,
									),
									contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
								) { Text("⤮  Shuffle", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
								Button(
									onClick = {
										scope.launch {
											runCatching { itemMutationRepository.setFavorite(album.id, !favourite) }
												.onSuccess { favourite = it.isFavorite }
										}
									},
									modifier = Modifier.size(width = 52.dp, height = 38.dp),
									shape = RoundedCornerShape(nova.corner(5)),
									colors = ButtonDefaults.colors(
										containerColor = nova.surfaceHigh.withAlpha(0xAA),
										contentColor = Color.White,
										focusedContainerColor = nova.accent,
										focusedContentColor = nova.onAccent,
									),
									contentPadding = PaddingValues(0.dp),
								) {
									Box(Modifier.size(width = 52.dp, height = 38.dp), contentAlignment = Alignment.Center) {
										Text(
											if (favourite) "♥" else "♡",
											fontSize = 20.sp,
											fontWeight = FontWeight.Bold,
											color = if (favourite) Color(0xFFFF4D5E) else Color.White,
										)
									}
								}
								// Song controls go last so nothing before them moves when they appear.
								if (playingHere) {
									SmallButton("⏮") { skip(-1) }
									SmallButton("⏭") { skip(1) }
								}
							}
						}
					}
					val current = nowPlaying
					if (playingHere && current != null) {
						Spacer(Modifier.height(12.dp))
						Text(
							"${if (isPlaying) "Now playing" else "Paused"}:  ${current.name.orEmpty()}",
							color = nova.accent,
							fontSize = 14.sp,
							fontWeight = FontWeight.Bold,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
						Spacer(Modifier.height(5.dp))
						Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(nova.surfaceHigh)) {
							Box(Modifier.fillMaxWidth(progress).height(3.dp).background(nova.accent))
						}
					}
					Spacer(Modifier.height(14.dp))
					when {
						list == null -> Text("Loading tracks…", color = nova.textMuted, fontSize = 14.sp)
						list.isEmpty() -> Text("No tracks found.", color = nova.textMuted, fontSize = 14.sp)
						else -> LazyColumn(
							state = trackListState,
							contentPadding = PaddingValues(bottom = 32.dp),
							verticalArrangement = Arrangement.spacedBy(2.dp),
							modifier = Modifier.fillMaxSize().onFocusChanged { listHasFocus = it.hasFocus },
						) {
							itemsIndexed(list, key = { _, t -> t.id }) { index, track ->
								TrackRow(
									number = track.indexNumber ?: (index + 1),
									track = track,
									albumArtist = artist,
									playing = track.id == nowPlaying?.id,
									modifier = if (track.id == nowPlaying?.id) Modifier.focusRequester(playingFocus) else Modifier,
									onClick = {
										// The song that's on: pause / resume it rather than restarting.
										if (track.id == nowPlaying?.id) togglePause() else play(startAt = index)
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
private fun TrackRow(
	number: Int,
	track: BaseItemDto,
	albumArtist: String?,
	playing: Boolean,
	modifier: Modifier,
	onClick: () -> Unit,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val trackArtist = track.artists?.joinToString(", ")?.takeIf { it.isNotBlank() && it != albumArtist }
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(nova.corner(6)))
			// Only one coloured bar: the highlight. The song that's on is shown
			// by its ♪ and coloured title, not a second background.
			.background(if (focused) nova.accent else Color.Transparent)
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
			.padding(horizontal = 14.dp, vertical = 9.dp),
	) {
		Text(
			when {
				playing -> "♪"
				focused -> "▶"
				else -> number.toString()
			},
			color = when {
				focused -> nova.onAccent
				playing -> nova.accent
				else -> nova.textMuted
			},
			fontSize = 14.sp,
			fontWeight = FontWeight.Bold,
			modifier = Modifier.width(34.dp),
		)
		Column(modifier = Modifier.weight(1f)) {
			Text(
				track.name.orEmpty(),
				color = when {
					focused -> nova.onAccent
					playing -> nova.accent
					else -> Color.White
				},
				fontSize = 15.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			if (trackArtist != null) {
				Text(trackArtist, color = if (focused) nova.onAccent else nova.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
		}
		track.runTimeTicks?.takeIf { it > 0 }?.let {
			Text(clockText(it), color = if (focused) nova.onAccent else nova.textMuted, fontSize = 13.sp)
		}
	}
}

private fun clockText(ticks: Long): String {
	val seconds = ticks / 10_000_000L
	return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun minutesText(ticks: Long): String {
	val minutes = ticks / 600_000_000L
	return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes min"
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
	Button(
		onClick = onClick,
		modifier = Modifier.size(width = 48.dp, height = 38.dp),
		shape = RoundedCornerShape(nova.corner(5)),
		colors = ButtonDefaults.colors(
			containerColor = nova.surfaceHigh.withAlpha(0xAA),
			contentColor = Color.White,
			focusedContainerColor = nova.accent,
			focusedContentColor = nova.onAccent,
		),
		contentPadding = PaddingValues(0.dp),
	) {
		Box(Modifier.size(width = 48.dp, height = 38.dp), contentAlignment = Alignment.Center) {
			Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
		}
	}
}

/** How long ⏪ / ⏩ has been held: a tap skips songs, holding seeks. */
private class HoldState {
	var seeks = 0
	var lastSeek = 0L
}

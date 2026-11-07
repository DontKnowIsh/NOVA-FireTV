package org.jellyfin.androidtv.ui.browsing

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.home.HomePreviewPlaybackController
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.androidtv.ui.playback.AudioEventListener
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.rewrite.RewriteMediaManager
import org.jellyfin.androidtv.ui.withAlpha
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.playback.core.model.PlaybackOrder
import org.jellyfin.playback.core.queue.queue
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.compose.koinInject
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * Music controls shared by NOVA's music pages (artist page): what's playing,
 * play / pause, previous / next song, shuffle and the remote's ⏪ ⏯ ⏩ buttons.
 */
class NovaMusicController internal constructor(
	private val context: Context,
	private val api: ApiClient,
	private val mediaManager: MediaManager,
	private val playbackManager: PlaybackManager,
	private val scope: CoroutineScope,
) {
	var nowPlaying by mutableStateOf(mediaManager.currentAudioItem)
		internal set
	var isPlaying by mutableStateOf(mediaManager.isPlayingAudio)
		internal set
	var shuffleOn by mutableStateOf(mediaManager.isShuffleMode)
		internal set
	var progress by mutableStateOf(0f)
		internal set

	private var settle: Job? = null
	private var holdSeeks = 0
	private var lastSeek = 0L

	/** True when the song that's on is one of [songs]. */
	fun isPlayingFrom(songs: List<BaseItemDto>): Boolean {
		val id = nowPlaying?.id ?: return false
		return songs.any { it.id == id }
	}

	/** Plays [songs] here: in order from [startAt], or shuffled. */
	fun play(songs: List<BaseItemDto>, startAt: Int = 0, shuffle: Boolean = false) {
		if (songs.isEmpty()) return
		HomePreviewPlaybackController.stopActivePreview()
		if (shuffle) {
			mediaManager.playNow(context, songs, 0, true)
		} else {
			// Everything goes in the queue, so ⏮ can go back past where you started.
			scope.launch {
				runCatching {
					playbackManager.state.setPlaybackOrder(PlaybackOrder.DEFAULT)
					playbackManager.queue.clear()
					playbackManager.queue.addSupplier(RewriteMediaManager.BaseItemQueueSupplier(api, songs, true))
					if (startAt > 0) playbackManager.queue.setIndex(startAt, true)
					playbackManager.state.play()
				}.onFailure {
					Timber.w(it, "NOVA: couldn't start music at %d", startAt)
					mediaManager.playNow(context, songs, startAt, false)
				}
			}
		}
		nowPlaying = songs.getOrNull(if (shuffle) 0 else startAt)
		shuffleOn = shuffle
		isPlaying = true
	}

	fun togglePause() {
		mediaManager.togglePlayPause()
		isPlaying = !isPlaying
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

	/** The remote's media keys: tap ⏪ / ⏩ = previous / next song, hold = 10 s jumps, ⏯ = pause. */
	fun handleKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
		val direction = when (event.key) {
			Key.MediaFastForward, Key.MediaNext -> 1
			Key.MediaRewind, Key.MediaPrevious -> -1
			else -> 0
		}
		if (direction != 0) {
			when (event.type) {
				KeyEventType.KeyDown -> if (event.nativeKeyEvent.repeatCount == 0) {
					holdSeeks = 0
					lastSeek = 0L
				} else {
					val now = android.os.SystemClock.uptimeMillis()
					if (now - lastSeek >= 400L) {
						lastSeek = now
						holdSeeks++
						if (direction > 0) playbackManager.state.fastForward(10.seconds)
						else playbackManager.state.rewind(10.seconds)
					}
				}
				KeyEventType.KeyUp -> {
					if (holdSeeks == 0) skip(direction)
					holdSeeks = 0
				}
			}
			return true
		}
		return when (event.key) {
			Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
				if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) togglePause()
				true
			}
			else -> false
		}
	}

	// Changing song briefly reports "stopped"; only believe a pause / empty queue that lasts.
	private fun later(block: () -> Unit) {
		settle?.cancel()
		settle = scope.launch {
			delay(800L)
			block()
		}
	}

	internal val listener = object : AudioEventListener {
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
				PlaybackController.PlaybackState.PAUSED,
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
}

@Composable
fun rememberNovaMusicController(): NovaMusicController {
	val context = LocalContext.current
	val api = koinInject<ApiClient>()
	val mediaManager = koinInject<MediaManager>()
	val playbackManager = koinInject<PlaybackManager>()
	val scope = rememberCoroutineScope()
	val controller = remember { NovaMusicController(context, api, mediaManager, playbackManager, scope) }
	DisposableEffect(controller) {
		mediaManager.addAudioEventListener(controller.listener)
		onDispose { mediaManager.removeAudioEventListener(controller.listener) }
	}
	return controller
}

/** Lets the remote's ⏪ ⏯ ⏩ control the music while [enabled]. */
fun Modifier.novaMusicKeys(controller: NovaMusicController, enabled: Boolean): Modifier =
	onPreviewKeyEvent { event -> enabled && controller.handleKey(event) }

/** Small square ⏮ / ⏭ button. */
@Composable
fun NovaMusicSmallButton(label: String, onClick: () -> Unit) {
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

package org.jellyfin.androidtv.ui.home

import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.playback.core.model.PlayState
import org.jellyfin.playback.media3.exoplayer.ExoPlayerBackend
import org.koin.java.KoinJavaComponent
import timber.log.Timber

/**
 * Hero previews and trailers go silent while music is playing (the music keeps
 * going), and get their sound back once the music is paused or stopped.
 */
object NovaPreviewMute {
	private var installed = false

	fun install() {
		if (installed) return
		installed = true
		runCatching {
			val music = KoinJavaComponent.get<PlaybackManager>(PlaybackManager::class.java)
			ProcessLifecycleOwner.get().lifecycleScope.launch {
				music.state.playState
					.map { it == PlayState.PLAYING }
					.distinctUntilChanged()
					.collectLatest { playing ->
						// Changing song briefly reports "stopped": wait before
						// turning the preview sound back on.
						if (!playing) delay(1_500L)
						ExoPlayerBackend.setPreviewsMuted(playing)
						novaSetTrailerMuted(playing)
					}
			}
		}.onFailure { Timber.w(it, "NOVA: preview mute unavailable") }
	}
}

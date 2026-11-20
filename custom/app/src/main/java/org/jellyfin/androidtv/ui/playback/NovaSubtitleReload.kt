package org.jellyfin.androidtv.ui.playback

/**
 * NOVA: restart the video at the same spot so Jellyfin includes a subtitle that
 * was just downloaded, and switch it on.
 */
fun PlaybackController.novaReloadWithSubtitle(index: Int) {
	val position = currentPosition
	stop()
	burningSubs = false
	mCurrentOptions.subtitleStreamIndex = index
	play(position, index)
}

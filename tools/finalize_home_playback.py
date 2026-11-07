#!/usr/bin/env python3
"""Make full playback take ownership after NOVA preview browsing."""
from pathlib import Path

root = Path("jellyfin-src/app/src/main/java/org/jellyfin/androidtv")


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise RuntimeError(f"Expected playback hook missing in {path}: {old[:80]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


launcher = root / "ui/playback/PlaybackLauncher.kt"
replace_once(
    launcher,
    "import org.jellyfin.androidtv.preference.UserPreferences",
    "import org.jellyfin.androidtv.preference.UserPreferences\nimport org.jellyfin.androidtv.ui.home.HomePreviewPlaybackController",
)
replace_once(
    launcher,
    "\t\tval isAudio = items.any { it.mediaType == MediaType.AUDIO }",
    """\t\tif (items.isEmpty()) return
\t\tHomePreviewPlaybackController.stopActivePreview()
\t\tval isAudio = items.any { it.mediaType == MediaType.AUDIO }""",
)
helper = root / "util/sdk/SdkPlaybackHelper.kt"
replace_once(
    helper,
    "import org.jellyfin.androidtv.preference.UserPreferences",
    "import org.jellyfin.androidtv.preference.UserPreferences\nimport org.jellyfin.androidtv.ui.home.HomePreviewPlaybackController",
)
replace_once(
    helper,
    "\toverride fun retrieveAndPlay(id: UUID, shuffle: Boolean, position: Long?, context: Context) {",
    """\toverride fun retrieveAndPlay(id: UUID, shuffle: Boolean, position: Long?, context: Context) {
\t\tHomePreviewPlaybackController.stopActivePreview()""",
)
# The legacy player used to start before acquiring audio focus in onResume.
# A resolving preview could then take focus and pause the newly opened movie.
# Start exactly once, after focus is granted, without overriding later pauses.
overlay = root / "ui/playback/CustomPlaybackOverlayFragment.java"
replace_once(
    overlay,
    "    private AudioManager mAudioManager;",
    "    private AudioManager mAudioManager;\n    private boolean novaPlaybackStarted = false;",
)
replace_once(
    overlay,
    '''        int startPos = getArguments().getInt("Position", 0);

        // start playing
        playbackControllerContainer.getValue().getPlaybackController().play(startPos);
        leanbackOverlayFragment.updatePlayState();''',
    "        // Initial playback starts in onResume, after acquiring audio focus.",
)
replace_once(
    overlay,
    '''        if (mAudioManager.requestAudioFocus(mAudioFocusChanged, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Timber.e("Unable to get audio focus");
            Utils.showToast(requireContext(), R.string.msg_cannot_play_time);
            return;
        }''',
    '''        org.jellyfin.androidtv.ui.home.HomePreviewPlaybackController.INSTANCE.stopActivePreview();
        if (mAudioManager.requestAudioFocus(mAudioFocusChanged, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Timber.e("Unable to get audio focus");
            Utils.showToast(requireContext(), R.string.msg_cannot_play_time);
            return;
        }
        if (!novaPlaybackStarted) {
            novaPlaybackStarted = true;
            int startPos = getArguments().getInt("Position", 0);
            playbackControllerContainer.getValue().getPlaybackController().play(startPos);
            leanbackOverlayFragment.updatePlayState();
        }''',
)

# Resolution can outlive queue.clear(). Ignore its result once that entry has
# been removed, so a late preview cannot start after the full player has opened.
stream_service = Path("jellyfin-src/playback/core/src/main/kotlin/mediastream/MediaStreamService.kt")
replace_once(
    stream_service,
    "\t\t\t\tval hasMediaStream = entry.ensureMediaStream()",
    """\t\t\t\tval hasMediaStream = entry.ensureMediaStream()
\t\t\t\tif (manager.queue.entry.value !== entry) return@onEach""",
)

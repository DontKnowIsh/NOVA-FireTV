#!/usr/bin/env python3
"""Add audio leveling to NOVA's preview-only ExoPlayer instance."""

from pathlib import Path

root = Path("jellyfin-src")
module = root / "playback/media3/exoplayer/src/main/kotlin"


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise RuntimeError(f"Expected preview audio hook missing in {path}: {old[:80]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


options = module / "ExoPlayerOptions.kt"
replace_once(
    options,
    "val enableDebugLogging: Boolean = false,",
    "val enableDebugLogging: Boolean = false,\n\tval enablePreviewLeveling: Boolean = false,",
)

backend = module / "ExoPlayerBackend.kt"
replace_once(
    backend,
    "private var audioPipeline = ExoPlayerAudioPipeline()",
    "private var audioPipeline = ExoPlayerAudioPipeline(exoPlayerOptions.enablePreviewLeveling)",
)

pipeline = module / "ExoPlayerAudioPipeline.kt"
replace_once(
    pipeline,
    "import android.media.audiofx.LoudnessEnhancer",
    "import android.media.audiofx.LoudnessEnhancer\nimport android.media.audiofx.DynamicsProcessing\nimport android.os.Build",
)
replace_once(
    pipeline,
    "class ExoPlayerAudioPipeline {",
    "class ExoPlayerAudioPipeline(private val enablePreviewLeveling: Boolean = false) {\n\tprivate var previewLeveler: DynamicsProcessing? = null",
)
replace_once(
    pipeline,
    "\t\t// Re-apply current normalization gain\n\t\tapplyGain()",
    """\t\t// Preview playback varies across files. A gentle compressor lifts quiet
\t\t// dialogue and limits loud peaks on this preview-only audio session.
\t\tpreviewLeveler?.release()
\t\tpreviewLeveler = null
\t\tif (enablePreviewLeveling && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && audioSessionId > 0) {
\t\t\tpreviewLeveler = runCatching {
\t\t\t\tDynamicsProcessing(audioSessionId).apply {
\t\t\t\t\tsetInputGainAllChannelsTo(0f)
\t\t\t\t\tsetLimiterAllChannelsTo(
\t\t\t\t\t\tDynamicsProcessing.Limiter(true, true, 0, 10f, 120f, 4f, -14f, 2f)
\t\t\t\t\t)
\t\t\t\t\tsetEnabled(true)
\t\t\t\t}
\t\t\t}.onFailure { Timber.w(it, "Preview audio leveling is unavailable") }.getOrNull()
\t\t}

\t\t// Re-apply current normalization gain
\t\tapplyGain()""",
)


# Even out preview loudness on every Fire TV (any Android version): an automatic
# level control runs on the decoded sound of the preview player only. Some films
# are mixed quiet (5.1 dialogue folded down to stereo) and some loud; this brings
# them all to one level. Passthrough / offload would skip it, so they are turned
# off for previews only (full playback is untouched).
(module / "PreviewLevelerAudioProcessor.kt").write_text("""package org.jellyfin.playback.media3.exoplayer

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tanh

/** Automatic level control for NOVA's previews: every title plays at about the same loudness. */
@OptIn(UnstableApi::class)
class PreviewLevelerAudioProcessor : BaseAudioProcessor() {
	private var level = TARGET
	private var gain = 1f

	override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
		if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
			throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
		}
		return inputAudioFormat
	}

	override fun queueInput(inputBuffer: ByteBuffer) {
		val remaining = inputBuffer.remaining()
		if (remaining == 0) return
		val start = inputBuffer.position()
		val count = remaining / 2
		val output = replaceOutputBuffer(count * 2)

		var sum = 0.0
		for (i in 0 until count) {
			val s = inputBuffer.getShort(start + i * 2) / 32768f
			sum += s * s
		}
		val rms = sqrt(sum / count.coerceAtLeast(1)).toFloat()
		// Ignore silence so quiet gaps don't pump the volume up.
		if (rms > SILENCE) {
			val speed = if (rms > level) ATTACK else RELEASE
			level += (rms - level) * speed
		}
		val wanted = (TARGET / level).coerceIn(MIN_GAIN, MAX_GAIN)
		val from = gain
		val to = gain + (wanted - gain) * GAIN_SMOOTHING
		for (i in 0 until count) {
			val g = from + (to - from) * i / count
			val x = softLimit(inputBuffer.getShort(start + i * 2) / 32768f * g)
			output.putShort((x * 32767f).toInt().coerceIn(-32768, 32767).toShort())
		}
		gain = to
		inputBuffer.position(start + count * 2)
		output.flip()
	}

	private fun softLimit(x: Float): Float {
		val a = abs(x)
		if (a <= KNEE) return x
		val over = KNEE + (1f - KNEE) * tanh(((a - KNEE) / (1f - KNEE)).toDouble()).toFloat()
		return if (x < 0) -over else over
	}

	override fun onReset() {
		level = TARGET
		gain = 1f
	}

	private companion object {
		/** About -20 dBFS: normal TV loudness. */
		const val TARGET = 0.1f
		const val SILENCE = 0.003f
		/** Reacts quickly when it gets louder, slowly when it gets quieter. */
		const val ATTACK = 0.35f
		const val RELEASE = 0.015f
		const val GAIN_SMOOTHING = 0.5f
		const val MIN_GAIN = 0.2f
		const val MAX_GAIN = 4f
		const val KNEE = 0.8f
	}
}
""", encoding="utf-8")

replace_once(
    backend,
    """		val renderersFactory = DefaultRenderersFactory(context).apply {""",
    """		val renderersFactory = object : DefaultRenderersFactory(context) {
			override fun buildAudioSink(
				context: Context,
				enableFloatOutput: Boolean,
				enableAudioTrackPlaybackParams: Boolean,
			): androidx.media3.exoplayer.audio.AudioSink? {
				if (!exoPlayerOptions.enablePreviewLeveling) {
					return super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
				}
				@Suppress("DEPRECATION")
				// No context on purpose: then the capabilities below are used, i.e. plain
				// PCM (no Dolby passthrough), so the leveller hears every preview.
				return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder()
					.setAudioCapabilities(androidx.media3.exoplayer.audio.AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
					.setAudioProcessors(arrayOf<androidx.media3.common.audio.AudioProcessor>(PreviewLevelerAudioProcessor()))
					.setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
					.build()
			}
		}.apply {""",
)
replace_once(
    backend,
    "setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)",
    """setAudioOffloadMode(
								if (exoPlayerOptions.enablePreviewLeveling) TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
								else TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
							)""",
)

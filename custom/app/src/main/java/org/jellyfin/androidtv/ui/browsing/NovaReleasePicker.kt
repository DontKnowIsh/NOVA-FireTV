package org.jellyfin.androidtv.ui.browsing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.androidtv.ui.withAlpha

/**
 * "Choose a copy" pop-up: every copy your indexers have for a film (or one TV
 * season), with seeders, peers, size, quality, language and site, so you can
 * pick the best one yourself. The first option leaves the choice to Radarr/Sonarr.
 *
 * @param season for TV, the season to search; null for films.
 * @param onAutomatic "Let Radarr/Sonarr choose": the normal automatic request.
 * @param onGrabbed called after a copy was sent to qBittorrent.
 */
@Composable
fun NovaReleasePicker(
	config: NovaSeerr.Config,
	type: NovaSeerr.MediaType,
	tmdbId: Int,
	title: String,
	season: Int?,
	/** e.g. "1 of 3" when going through several seasons. */
	progressLabel: String? = null,
	onDismiss: () -> Unit,
	onAutomatic: () -> Unit,
	onGrabbed: () -> Unit,
) {
	val scope = rememberCoroutineScope()
	var phase by remember(tmdbId, season) { mutableStateOf("Getting ready…") }
	var target by remember(tmdbId, season) { mutableStateOf<NovaReleases.Target?>(null) }
	var releases by remember(tmdbId, season) { mutableStateOf<List<NovaReleases.Release>?>(null) }
	var failed by remember(tmdbId, season) { mutableStateOf(false) }
	var grabbing by remember(tmdbId, season) { mutableStateOf<String?>(null) }
	var message by remember(tmdbId, season) { mutableStateOf<String?>(null) }
	val firstFocus = remember { FocusRequester() }
	val appName = if (type == NovaSeerr.MediaType.MOVIE) "Radarr" else "Sonarr"

	LaunchedEffect(tmdbId, season) {
		delay(50L)
		runCatching { firstFocus.requestFocus() }
		phase = "Adding to $appName…"
		val prepared = if (type == NovaSeerr.MediaType.MOVIE) {
			NovaReleases.prepareMovie(config, tmdbId)
		} else {
			NovaReleases.prepareSeason(config, tmdbId, season ?: 1)
		}
		if (prepared == null) {
			failed = true
			phase = "Couldn't add this to $appName. You can still let it choose automatically."
			return@LaunchedEffect
		}
		target = prepared
		phase = "Searching your indexers… this can take up to a minute."
		val found = NovaReleases.search(config, prepared)
		if (found == null) {
			failed = true
			phase = "The search didn't work. Try again, or let $appName choose."
		} else {
			releases = found
			phase = if (found.isEmpty()) "No copies found on your indexers right now." else "${found.size} copies found"
		}
	}

	fun grab(release: NovaReleases.Release) {
		val t = target ?: return
		if (grabbing != null) return
		scope.launch {
			grabbing = release.guid
			val ok = NovaReleases.grab(config, t, release)
			grabbing = null
			if (ok) {
				onGrabbed()
			} else {
				message = "$appName couldn't download that copy. Try another one."
			}
		}
	}

	Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
		Column(
			modifier = Modifier
				.width(760.dp)
				.background(nova.surface, RoundedCornerShape(nova.corner(12)))
				.border(1.dp, nova.surfaceHigh, RoundedCornerShape(nova.corner(12)))
				.padding(20.dp)
		) {
			Text("CHOOSE A COPY", color = nova.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
			Spacer(Modifier.height(4.dp))
			Text(
				buildString {
					append(title)
					if (season != null) append(" · Season $season")
					if (progressLabel != null) append(" ($progressLabel)")
				},
				color = Color.White,
				fontSize = 22.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Spacer(Modifier.height(4.dp))
			Text(message ?: phase, color = if (message != null || failed) Color(0xFFFFC266) else nova.textMuted, fontSize = 13.sp)
			Spacer(Modifier.height(12.dp))

			LazyColumn(
				modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
				verticalArrangement = Arrangement.spacedBy(4.dp),
			) {
				item {
					PickerRow(
						modifier = Modifier.focusRequester(firstFocus),
						onClick = onAutomatic,
						enabled = grabbing == null,
					) {
						Column {
							Text("Let $appName choose the best copy", fontSize = 15.sp, fontWeight = FontWeight.Bold)
							Text("The normal automatic download", fontSize = 11.sp, color = nova.textMuted)
						}
					}
				}
				items(releases.orEmpty(), key = { "${it.indexerId}-${it.guid}" }) { release ->
					PickerRow(onClick = { grab(release) }, enabled = grabbing == null) {
						ReleaseContent(release, busy = grabbing == release.guid)
					}
				}
				item {
					Spacer(Modifier.height(4.dp))
					PickerRow(onClick = onDismiss, enabled = true) {
						Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Bold)
					}
				}
			}
		}
	}
}

@Composable
private fun ReleaseContent(release: NovaReleases.Release, busy: Boolean) {
	Column(modifier = Modifier.fillMaxWidth()) {
		Text(
			if (busy) "Sending to qBittorrent…" else release.title,
			fontSize = 13.sp,
			fontWeight = FontWeight.Bold,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
		Spacer(Modifier.height(4.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
			val seeders = release.seeders
			if (seeders != null) {
				val colour = when {
					seeders >= 20 -> nova.statusDownloaded
					seeders >= 1 -> nova.statusWaiting
					else -> nova.statusMissing
				}
				Chip("▲ $seeders seeders", colour)
				Chip("▼ ${release.peers ?: 0} peers", nova.surfaceHigh)
			}
			// Matches a preferred Custom Format in Radarr / Sonarr (e.g. a favourite release group).
			if (release.formatScore > 0) Chip("★ " + release.formats.ifEmpty { listOf("Preferred") }.joinToString(" / "), nova.accent)
			if (release.quality.isNotBlank()) Chip(release.quality, nova.statusUnreleased)
			if (release.sizeBytes > 0) Chip(NovaReleases.friendlySize(release.sizeBytes), nova.surfaceHigh)
			release.coverage?.let { Chip(it, nova.statusDownloading) }
			if (release.languages.isNotEmpty()) Chip(release.languages.take(3).joinToString(" / "), nova.surfaceHigh)
			if (release.indexer.isNotBlank()) Chip(release.indexer, nova.surfaceHigh)
			NovaReleases.friendlyAge(release.ageHours)?.let { Chip(it, nova.surfaceHigh) }
		}
		if (release.rejected) {
			Spacer(Modifier.height(3.dp))
			Text(
				"⚠ " + release.rejections.first(),
				fontSize = 11.sp,
				color = Color(0xFFFFC266),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
private fun Chip(text: String, colour: Color) {
	Box(
		modifier = Modifier
			.background(colour, RoundedCornerShape(nova.corner(3)))
			.padding(horizontal = 6.dp, vertical = 2.dp)
	) {
		Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
	}
}

@Composable
private fun PickerRow(
	onClick: () -> Unit,
	enabled: Boolean,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(nova.corner(6))
	Button(
		onClick = { if (enabled) onClick() },
		modifier = modifier
			.fillMaxWidth()
			.then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier),
		interactionSource = interactionSource,
		shape = shape,
		colors = ButtonDefaults.colors(
			containerColor = nova.surfaceRaised,
			contentColor = nova.textPrimary,
			focusedContainerColor = nova.accent.withAlpha(0x55),
			focusedContentColor = Color.White,
		),
		contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
	) {
		content()
	}
}

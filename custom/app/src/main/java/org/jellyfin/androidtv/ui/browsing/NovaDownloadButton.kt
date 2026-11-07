package org.jellyfin.androidtv.ui.browsing

import org.jellyfin.androidtv.ui.withAlpha
import org.jellyfin.androidtv.ui.nova
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.compose.koinInject

/** Download status colours: Radarr's on the NOVA themes, Seerr's on the Seerr theme. */
object NovaStatusColors {
	val Downloaded get() = nova.statusDownloaded
	val Missing get() = nova.statusMissing
	val Queued get() = nova.statusDownloading
	val Unreleased get() = nova.statusUnreleased
	val Waiting get() = nova.statusWaiting
	val Neutral get() = nova.surfaceHigh.withAlpha(0xAA)
}

fun NovaSeerr.Status.label(): String = when (state) {
	NovaSeerr.State.NOT_REQUESTED -> "⬇ Download"
	NovaSeerr.State.PENDING_APPROVAL -> "Waiting for approval"
	NovaSeerr.State.UNRELEASED -> "Unreleased"
	NovaSeerr.State.SEARCHING -> "Searching…"
	NovaSeerr.State.DOWNLOADING -> "Downloading ${((progress ?: 0f) * 100).toInt()}%"
	NovaSeerr.State.PARTLY_DOWNLOADED -> "Partly downloaded"
	NovaSeerr.State.DOWNLOADED -> "Downloaded ✓"
}

fun NovaSeerr.Status.colour(): Color = when (state) {
	NovaSeerr.State.NOT_REQUESTED -> NovaStatusColors.Neutral
	NovaSeerr.State.PENDING_APPROVAL -> NovaStatusColors.Waiting
	NovaSeerr.State.UNRELEASED -> NovaStatusColors.Unreleased
	NovaSeerr.State.SEARCHING -> NovaStatusColors.Missing
	NovaSeerr.State.DOWNLOADING -> NovaStatusColors.Queued
	NovaSeerr.State.PARTLY_DOWNLOADED,
	NovaSeerr.State.DOWNLOADED -> NovaStatusColors.Downloaded
}

/** Thin progress bar shown under a downloading label. */
@Composable
fun NovaProgressBar(progress: Float, modifier: Modifier = Modifier) {
	Box(
		modifier = modifier
			.height(4.dp)
			.background(Color(0x55FFFFFF), RoundedCornerShape(nova.corner(2)))
	) {
		Box(
			modifier = Modifier
				.fillMaxHeight()
				.fillMaxWidth(progress.coerceIn(0f, 1f))
				.background(Color.White, RoundedCornerShape(nova.corner(2)))
		)
	}
}

private enum class ActionMode { LOADING, FAVOURITE, DOWNLOAD }

/** "Choose a copy" makes sense when nothing is downloading yet, or a search got stuck. */
internal fun NovaSeerr.State?.canChooseCopy(): Boolean = when (this) {
	null,
	NovaSeerr.State.NOT_REQUESTED,
	NovaSeerr.State.SEARCHING,
	NovaSeerr.State.PARTLY_DOWNLOADED -> true
	else -> false
}

/**
 * The hero/detail action button.
 *
 * - JellyBridge "Discover" titles: "⬇ Download", which requests the title from
 *   Seerr directly and then shows live status and download progress.
 * - Everything else (real library items), or when Seerr is unavailable:
 *   the normal "♥ Favourite" toggle, which JellyBridge also turns into a request.
 *
 * It is a single Button whose content changes, so remote focus is never lost
 * while it switches mode.
 */
@Composable
fun NovaActionButton(
	item: BaseItemDto,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()
	val itemMutationRepository = koinInject<ItemMutationRepository>()
	val scope = rememberCoroutineScope()

	// Start as Favourite (right for everything you own) so the button never shows
	// "…" while scrolling; it switches to Download only for Discover titles.
	var mode by remember(item.id) { mutableStateOf(ActionMode.FAVOURITE) }
	var isFavorite by remember(item.id) { mutableStateOf(item.userData?.isFavorite == true) }
	var status by remember(item.id) { mutableStateOf<NovaSeerr.Status?>(null) }
	var busy by remember(item.id) { mutableStateOf(false) }
	var showSeasonPicker by remember(item.id) { mutableStateOf(false) }
	// "Choose a copy" queue: [0] for a film, otherwise the TV seasons still to go.
	var copyQueue by remember(item.id) { mutableStateOf<List<Int>>(emptyList()) }
	var copyTotal by remember(item.id) { mutableStateOf(0) }
	var seerrTarget by remember(item.id) {
		mutableStateOf<Triple<NovaSeerr.Config, NovaSeerr.MediaType, Int>?>(null)
	}

	// Work out which mode this title needs. A short delay avoids network work while
	// the remote is scrolling quickly through a row and the hero item keeps changing.
	LaunchedEffect(item.id) {
		delay(250L)
		try {
			val full = NovaSeerr.fullItem(api, item)
			full.userData?.let { isFavorite = it.isFavorite }
			val config = NovaSeerr.config(api)
			val type = NovaSeerr.mediaType(full)
			val tmdb = NovaSeerr.tmdbId(full)
			if (config != null && type != null && tmdb != null && NovaSeerr.isDiscoverPlaceholder(config, full)) {
				seerrTarget = Triple(config, type, tmdb)
				status = NovaSeerr.status(config, type, tmdb)
				mode = ActionMode.DOWNLOAD
			} else {
				mode = ActionMode.FAVOURITE
			}
		} catch (cancelled: CancellationException) {
			throw cancelled
		} catch (_: Exception) {
			mode = ActionMode.FAVOURITE
		}
	}

	// Keep the status fresh: quickly while downloading, slowly otherwise.
	LaunchedEffect(item.id, mode, status?.state) {
		val target = seerrTarget ?: return@LaunchedEffect
		if (mode != ActionMode.DOWNLOAD) return@LaunchedEffect
		while (true) {
			val waitMs = when (status?.state) {
				NovaSeerr.State.DOWNLOADING -> 4_000L
				NovaSeerr.State.SEARCHING, NovaSeerr.State.PENDING_APPROVAL -> 15_000L
				NovaSeerr.State.DOWNLOADED, NovaSeerr.State.UNRELEASED -> 60_000L
				else -> 30_000L
			}
			delay(waitMs)
			NovaSeerr.status(target.first, target.second, target.third)?.let { status = it }
		}
	}

	val currentStatus = status
	val containerColour = when (mode) {
		ActionMode.DOWNLOAD -> currentStatus?.colour() ?: NovaStatusColors.Neutral
		ActionMode.FAVOURITE -> if (isFavorite) nova.accent else NovaStatusColors.Neutral
		ActionMode.LOADING -> NovaStatusColors.Neutral
	}
	val label = when (mode) {
		ActionMode.DOWNLOAD -> if (busy) "Requesting…" else currentStatus?.label() ?: "⬇ Download"
		ActionMode.FAVOURITE -> if (isFavorite) "♥ Favourited" else "♥ Favourite"
		ActionMode.LOADING -> "…"
	}

	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(nova.corner(5))

	Button(
		onClick = {
			if (busy || mode == ActionMode.LOADING) return@Button
			// TV shows: ask which seasons (unless everything is already downloaded).
			val tvTarget = seerrTarget?.takeIf { it.second == NovaSeerr.MediaType.TV }
			if (mode == ActionMode.DOWNLOAD && tvTarget != null && status?.state != NovaSeerr.State.DOWNLOADED) {
				showSeasonPicker = true
				return@Button
			}
			// Films: show every copy available so you can pick the best one.
			if (mode == ActionMode.DOWNLOAD && status?.state.canChooseCopy()) {
				copyQueue = listOf(0)
				copyTotal = 1
				return@Button
			}
			scope.launch {
				busy = true
				try {
					when (mode) {
						ActionMode.DOWNLOAD -> {
							val target = seerrTarget ?: return@launch
							if (status?.state == NovaSeerr.State.NOT_REQUESTED || status == null) {
								NovaSeerr.request(target.first, target.second, target.third)
							}
							NovaSeerr.status(target.first, target.second, target.third)?.let { status = it }
						}
						ActionMode.FAVOURITE -> {
							runCatching { itemMutationRepository.setFavorite(item.id, !isFavorite) }
								.onSuccess { isFavorite = it.isFavorite }
						}
						ActionMode.LOADING -> Unit
					}
				} finally {
					busy = false
				}
			}
		},
		modifier = modifier
			.widthIn(min = 132.dp)
			.then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier),
		interactionSource = interactionSource,
		shape = shape,
		colors = ButtonDefaults.colors(
			containerColor = containerColour,
			contentColor = Color.White,
			// Keep the real colour when highlighted (the white border shows focus),
			// so a highlighted Favourite button never looks "favourited" (red).
			focusedContainerColor = when {
				mode == ActionMode.DOWNLOAD -> containerColour
				mode == ActionMode.FAVOURITE && isFavorite -> containerColour
				else -> nova.surfaceFocus
			},
			focusedContentColor = Color.White,
		),
		contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
	) {
		Column {
			Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
			val progress = currentStatus?.progress
			if (mode == ActionMode.DOWNLOAD && currentStatus?.state == NovaSeerr.State.DOWNLOADING && progress != null) {
				NovaProgressBar(progress, Modifier.padding(top = 4.dp).width(120.dp))
			}
		}
	}

	val pickerTarget = seerrTarget
	if (showSeasonPicker && pickerTarget != null) {
		NovaSeasonPicker(
			config = pickerTarget.first,
			tmdbId = pickerTarget.third,
			title = item.name.orEmpty(),
			onDismiss = { showSeasonPicker = false },
			onRequested = {
				showSeasonPicker = false
				scope.launch {
					NovaSeerr.status(pickerTarget.first, pickerTarget.second, pickerTarget.third)?.let { status = it }
				}
			},
			onSeasonChosen = { season ->
				showSeasonPicker = false
				copyQueue = listOf(season)
				copyTotal = 1
			},
			onAllMissingChosen = { seasons ->
				showSeasonPicker = false
				copyQueue = seasons
				copyTotal = seasons.size
			},
		)
	}

	val chosenSeason = copyQueue.firstOrNull()
	if (chosenSeason != null && pickerTarget != null) {
		// Move on to the next season (if any) once this one is decided.
		fun next() {
			copyQueue = copyQueue.drop(1)
		}
		fun refresh() {
			scope.launch {
				NovaSeerr.status(pickerTarget.first, pickerTarget.second, pickerTarget.third)?.let { status = it }
			}
		}
		NovaReleasePicker(
			config = pickerTarget.first,
			type = pickerTarget.second,
			tmdbId = pickerTarget.third,
			title = item.name.orEmpty(),
			season = chosenSeason.takeIf { pickerTarget.second == NovaSeerr.MediaType.TV },
			progressLabel = if (copyTotal > 1) "${copyTotal - copyQueue.size + 1} of $copyTotal" else null,
			onDismiss = { copyQueue = emptyList() },
			onAutomatic = {
				next()
				scope.launch {
					busy = true
					try {
						val seasons = if (pickerTarget.second == NovaSeerr.MediaType.TV) listOf(chosenSeason) else null
						NovaSeerr.request(pickerTarget.first, pickerTarget.second, pickerTarget.third, seasons)
						NovaSeerr.status(pickerTarget.first, pickerTarget.second, pickerTarget.third)?.let { status = it }
					} finally {
						busy = false
					}
				}
			},
			onGrabbed = {
				next()
				status = NovaSeerr.Status(NovaSeerr.State.DOWNLOADING, 0f)
				refresh()
			},
		)
	}
}

/** Small "MOVIE" / "TV SHOW" tag for hero areas. */
@Composable
fun NovaTypeTag(item: BaseItemDto?) {
	val text = when (item?.type) {
		BaseItemKind.MOVIE -> "MOVIE"
		BaseItemKind.SERIES,
		BaseItemKind.SEASON,
		BaseItemKind.EPISODE -> "TV SHOW"
		else -> return
	}
	val colour = if (text == "MOVIE") nova.movieTag else nova.tvTag
	Box(
		modifier = Modifier
			.background(colour, RoundedCornerShape(nova.corner(3)))
			.padding(horizontal = 7.dp, vertical = 2.dp)
	) {
		Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
	}
}

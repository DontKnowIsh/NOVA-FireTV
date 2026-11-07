package org.jellyfin.androidtv.ui.browsing

import org.jellyfin.androidtv.ui.nova
import org.jellyfin.androidtv.ui.home.NovaSideRail
import org.jellyfin.androidtv.ui.home.NovaRailPage
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.navigation.fragmentDestination
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.compose.koinInject

object NovaDownloadsDestinations {
	val downloads = fragmentDestination<NovaDownloadsFragment>()
}

/**
 * Everything Radarr/Sonarr are downloading, with progress, pause/resume
 * (via qBittorrent) and delete (removes the download, stops it being fetched
 * again and removes the request from Seerr).
 */
class NovaDownloadsFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		JellyfinTheme {
			val api = koinInject<ApiClient>()
			val scope = rememberCoroutineScope()

			var seerr by remember { mutableStateOf<NovaSeerr.Config?>(null) }
			var services by remember { mutableStateOf<NovaArr.Services?>(null) }
			var loaded by remember { mutableStateOf(false) }
			var downloads by remember { mutableStateOf<List<NovaArr.Download>>(emptyList()) }
			// Optimistic paused state while Radarr/Sonarr catch up with qBittorrent.
			val pausedOverride = remember { mutableStateMapOf<String, Boolean>() }
			val busy = remember { mutableStateMapOf<String, Boolean>() }
			val confirmDelete = remember { mutableStateMapOf<String, Boolean>() }
			val confirmRetry = remember { mutableStateMapOf<String, Boolean>() }
			var notice by remember { mutableStateOf<String?>(null) }

			val railFocus = remember { FocusRequester() }
			val firstRowFocus = remember { FocusRequester() }
			// One focus target per row, so the highlight can land on a neighbouring
			// row when a download disappears from the list.
			val rowFocus = remember { mutableMapOf<String, FocusRequester>() }
			// Which row the remote is on, so we can keep the highlight if it vanishes.
			var focusedKey by remember { mutableStateOf<String?>(null) }

			suspend fun refocusNear(oldIndex: Int) {
				// Let the list re-lay itself out first, otherwise the target isn't attached yet.
				repeat(4) { attempt ->
					delay(if (attempt == 0) 150L else 250L)
					val target = downloads.getOrNull(oldIndex.coerceAtMost(downloads.size - 1))
					val requester = target?.let { rowFocus[it.key] }
					if (requester == null) {
						if (downloads.isEmpty() && runCatching { railFocus.requestFocus() }.isSuccess) return
					} else if (runCatching { requester.requestFocus() }.isSuccess) {
						return
					}
				}
				runCatching { railFocus.requestFocus() }
			}

			suspend fun refresh() {
				val cfg = seerr ?: return
				val svc = services ?: NovaArr.services(cfg).also { services = it }
				val before = downloads
				downloads = NovaArr.downloads(svc)
				// The row the remote was on has gone (finished, swapped or deleted):
				// move the highlight to its neighbour instead of losing it.
				val lostKey = focusedKey
				if (lostKey != null && downloads.none { it.key == lostKey }) {
					val oldIndex = before.indexOfFirst { it.key == lostKey }
					if (oldIndex >= 0) {
						focusedKey = null
						scope.launch { refocusNear(oldIndex) }
					}
				}
				// Drop overrides once Radarr/Sonarr report the same state.
				downloads.forEach { d ->
					val override = pausedOverride[d.key]
					if (override != null && override == d.isPaused) pausedOverride.remove(d.key)
				}
				loaded = true
			}

			LaunchedEffect(Unit) {
				seerr = NovaSeerr.config(api)
				if (seerr == null) {
					loaded = true
					return@LaunchedEffect
				}
				refresh()
				runCatching { if (downloads.isNotEmpty()) firstRowFocus.requestFocus() else railFocus.requestFocus() }
				while (true) {
					delay(3_000L)
					refresh()
				}
			}

			LaunchedEffect(notice) {
				if (notice != null) {
					delay(6_000L)
					notice = null
				}
			}

			fun togglePause(download: NovaArr.Download) {
				val svc = services ?: return
				if (busy[download.key] == true) return
				val currentlyPaused = pausedOverride[download.key] ?: download.isPaused
				scope.launch {
					busy[download.key] = true
					try {
						val error = NovaArr.setPaused(svc, download, !currentlyPaused)
						if (error == null) pausedOverride[download.key] = !currentlyPaused else notice = error
					} finally {
						busy[download.key] = false
					}
				}
			}

			fun retry(download: NovaArr.Download) {
				val svc = services ?: return
				if (busy[download.key] == true) return
				if (confirmRetry[download.key] != true) {
					// First press arms it; a second press within a few seconds swaps the copy.
					confirmRetry[download.key] = true
					scope.launch {
						delay(4_000L)
						confirmRetry.remove(download.key)
					}
					return
				}
				scope.launch {
					busy[download.key] = true
					try {
						val oldIndex = downloads.indexOfFirst { it.key == download.key }.coerceAtLeast(0)
					val ok = NovaArr.retry(svc, download)
						notice = if (ok) {
							"Dropped that copy of ${download.title} and searching for another. It will reappear here when one is found."
						} else {
							"Couldn't swap ${download.title} for another copy. Try Radarr/Sonarr's Queue instead."
						}
						confirmRetry.remove(download.key)
						focusedKey = null
						refresh()
						refocusNear(oldIndex)
					} finally {
						busy[download.key] = false
					}
				}
			}

			fun delete(download: NovaArr.Download) {
				val cfg = seerr ?: return
				val svc = services ?: return
				if (busy[download.key] == true) return
				if (confirmDelete[download.key] != true) {
					// First press arms it; a second press within a few seconds deletes.
					confirmDelete[download.key] = true
					scope.launch {
						delay(4_000L)
						confirmDelete.remove(download.key)
					}
					return
				}
				scope.launch {
					busy[download.key] = true
					try {
						val oldIndex = downloads.indexOfFirst { it.key == download.key }.coerceAtLeast(0)
						val ok = NovaArr.delete(cfg, svc, download)
						notice = if (ok) "Deleted ${download.title}." else "Deleted the download, but couldn't tidy everything up."
						confirmDelete.remove(download.key)
						focusedKey = null
						refresh()
						refocusNear(oldIndex)
					} finally {
						busy[download.key] = false
					}
				}
			}

			Row(modifier = Modifier.fillMaxSize().background(nova.bgDeep)) {
				// Left rail
				NovaSideRail(
					current = NovaRailPage.Downloads,
					focusRequester = railFocus,
					rightFocus = firstRowFocus,
				)

				// Content
				LazyColumn(
					modifier = Modifier.fillMaxSize().background(nova.bg),
					contentPadding = PaddingValues(start = 30.dp, end = 30.dp, top = 22.dp, bottom = 24.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					item {
						Text("DOWNLOADS", color = nova.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
						Spacer(Modifier.height(5.dp))
						Text("What's downloading", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
						Spacer(Modifier.height(5.dp))
						val downloading = downloads.count { !(pausedOverride[it.key] ?: it.isPaused) }
						val paused = downloads.size - downloading
						Text(
							when {
								!loaded -> "Loading…"
								seerr == null -> "Downloads need an admin account and the JellyBridge plugin set up on the server."
								downloads.isEmpty() -> "Nothing is downloading right now."
								else -> buildString {
									append("$downloading downloading")
									if (paused > 0) append(" · $paused paused")
									append(" · press Other copy or Delete twice to confirm")
								}
							},
							color = nova.textMuted,
							fontSize = 13.sp,
						)
						notice?.let {
							Spacer(Modifier.height(8.dp))
							Text(it, color = Color(0xFFFFC266), fontSize = 13.sp)
						}
					}

					itemsIndexed(downloads, key = { _, d -> d.key }) { index, download ->
						DownloadRow(
							download = download,
							paused = pausedOverride[download.key] ?: download.isPaused,
							busy = busy[download.key] == true,
							confirmingDelete = confirmDelete[download.key] == true,
							confirmingRetry = confirmRetry[download.key] == true,
							canPause = services?.qbittorrentUrl != null && download.downloadId != null,
							onPause = { togglePause(download) },
							onRetry = { retry(download) },
							onFocused = { focusedKey = download.key },
							onDelete = { delete(download) },
							firstButtonModifier = Modifier
								.focusRequester(rowFocus.getOrPut(download.key) { FocusRequester() })
								.then(if (index == 0) Modifier.focusRequester(firstRowFocus) else Modifier)
								.focusProperties { left = railFocus },
						)
					}
				}
			}
		}
	}
}

@Composable
private fun DownloadRow(
	download: NovaArr.Download,
	paused: Boolean,
	busy: Boolean,
	confirmingDelete: Boolean,
	confirmingRetry: Boolean,
	canPause: Boolean,
	onPause: () -> Unit,
	onRetry: () -> Unit,
	onDelete: () -> Unit,
	onFocused: () -> Unit,
	firstButtonModifier: Modifier,
) {
	val importing = download.trackedState?.startsWith("import", ignoreCase = true) == true
	val problem = download.status.equals("warning", true) || download.status.equals("failed", true) ||
		download.trackedState.equals("failedPending", true)
	// qBittorrent's own state is the most accurate, so use it when we have it.
	val qbitState = download.stats?.state.orEmpty()
	val (stateLabel, stateColour) = when {
		importing -> "Moving to library" to NovaStatusColors.Downloaded
		problem -> "Problem" to NovaStatusColors.Missing
		paused -> "Paused" to NovaStatusColors.Waiting
		qbitState == "metaDL" || qbitState == "forcedMetaDL" -> "Downloading metadata" to NovaStatusColors.Waiting
		qbitState == "stalledDL" -> "Stalled (no one sharing)" to NovaStatusColors.Missing
		qbitState == "queuedDL" -> "Queued in qBittorrent" to NovaStatusColors.Unreleased
		qbitState.startsWith("checking") -> "Checking files" to NovaStatusColors.Unreleased
		qbitState == "error" || qbitState == "missingFiles" -> "qBittorrent error" to NovaStatusColors.Missing
		download.status.equals("queued", true) || download.status.equals("delay", true) -> "Queued" to NovaStatusColors.Unreleased
		download.status.equals("completed", true) -> "Finishing" to NovaStatusColors.Downloaded
		else -> "Downloading" to NovaStatusColors.Queued
	}
	val percent = (download.progress * 100).toInt()
	val stats = download.stats
	val timeLeft = if (!paused && !importing) {
		stats?.etaSeconds?.let { NovaArr.friendlyEta(it) } ?: NovaArr.friendlyTimeLeft(download.timeLeft)
	} else null
	val sizeText = if (download.size > 0) {
		"${NovaArr.friendlySize(download.size - download.sizeLeft)} of ${NovaArr.friendlySize(download.size)}"
	} else null
	val speedText = if (stats != null && !paused && !importing) NovaArr.friendlySpeed(stats.downloadSpeed) else null
	val peersText = stats?.takeIf { !importing }?.let {
		"${it.seeds} (${it.seedsTotal}) seeds · ${it.peers} (${it.peersTotal}) peers"
	}
	// Stuck with nobody sending data: suggest swapping it for another copy.
	val stuck = !paused && !importing && stats != null && stats.seeds == 0 &&
		qbitState in setOf("metaDL", "forcedMetaDL", "stalledDL")

	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.onFocusChanged { if (it.hasFocus) onFocused() }
			.fillMaxWidth()
			.background(nova.surface, RoundedCornerShape(nova.corner(8)))
			.padding(10.dp),
	) {
		Box(modifier = Modifier.width(56.dp).height(84.dp).background(nova.surfaceRaised, RoundedCornerShape(nova.corner(4)))) {
			AsyncImage(
				url = download.posterUrl,
				aspectRatio = 2f / 3f,
				scaleType = ImageView.ScaleType.CENTER_CROP,
				modifier = Modifier.fillMaxSize(),
			)
		}
		Spacer(Modifier.width(14.dp))
		Column(modifier = Modifier.weight(1f)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Box(
					modifier = Modifier
						.background(
							if (download.kind == NovaArr.Kind.MOVIE) nova.movieTag else nova.tvTag,
							RoundedCornerShape(nova.corner(3)),
						)
						.padding(horizontal = 6.dp, vertical = 1.dp)
				) {
					Text(
						if (download.kind == NovaArr.Kind.MOVIE) "MOVIE" else "TV SHOW",
						color = Color.White,
						fontSize = 9.sp,
						fontWeight = FontWeight.Black,
					)
				}
				Spacer(Modifier.width(8.dp))
				Text(
					download.title,
					color = Color.White,
					fontSize = 16.sp,
					fontWeight = FontWeight.Bold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
			if (download.subtitle.isNotBlank()) {
				Spacer(Modifier.height(2.dp))
				Text(download.subtitle, color = nova.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
			Spacer(Modifier.height(8.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				Box(Modifier.size(width = 10.dp, height = 10.dp).background(stateColour, RoundedCornerShape(nova.corner(2))))
				Spacer(Modifier.width(6.dp))
				Text(
					buildString {
						append(stateLabel)
						if (!importing) append(" · $percent%")
						timeLeft?.let { append(" · $it") }
					},
					color = nova.textPrimary,
					fontSize = 12.sp,
					fontWeight = FontWeight.Bold,
				)
			}
			// Size, speed and sharing info (like qBittorrent's columns).
			val details = listOfNotNull(sizeText, speedText, peersText)
			if (details.isNotEmpty()) {
				Spacer(Modifier.height(2.dp))
				Text(
					details.joinToString("  ·  "),
					color = nova.textMuted,
					fontSize = 12.sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
			if (stuck) {
				Text(
					"Nobody is sharing this copy right now. Press Other copy to try a different one.",
					color = Color(0xFFFFC266),
					fontSize = 11.sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
			if (problem && !download.message.isNullOrBlank()) {
				Text(download.message, color = Color(0xFFFF8A80), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
			Spacer(Modifier.height(6.dp))
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(6.dp)
					.background(Color(0x33FFFFFF), RoundedCornerShape(nova.corner(3)))
			) {
				Box(
					modifier = Modifier
						.fillMaxHeight()
						.fillMaxWidth(download.progress)
						.background(stateColour, RoundedCornerShape(nova.corner(3)))
				)
			}
		}
		Spacer(Modifier.width(14.dp))
		// The first button shown takes the row's focus wiring (left goes to the rail).
		var firstUsed = false
		fun first(): Modifier = if (firstUsed) Modifier else firstButtonModifier.also { firstUsed = true }
		if (canPause && !importing) {
			RowActionButton(
				label = if (paused) "Resume" else "Pause",
				iconRes = if (paused) R.drawable.ic_play else R.drawable.ic_pause,
				enabled = !busy,
				onClick = onPause,
				modifier = first(),
			)
			Spacer(Modifier.width(8.dp))
		}
		if (!importing) {
			RowActionButton(
				label = if (confirmingRetry) "Press again" else "Other copy",
				iconRes = R.drawable.ic_shuffle,
				enabled = !busy,
				armed = confirmingRetry,
				onClick = onRetry,
				modifier = first(),
			)
			Spacer(Modifier.width(8.dp))
		}
		RowActionButton(
			label = if (confirmingDelete) "Press again" else "Delete",
			iconRes = R.drawable.ic_trash,
			enabled = !busy,
			danger = true,
			armed = confirmingDelete,
			onClick = onDelete,
			modifier = first(),
		)
	}
}

@Composable
private fun RowActionButton(
	label: String,
	iconRes: Int,
	enabled: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	danger: Boolean = false,
	armed: Boolean = false,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(nova.corner(5))
	Button(
		onClick = { if (enabled) onClick() },
		modifier = modifier
			.widthIn(min = 112.dp)
			.then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier),
		interactionSource = interactionSource,
		shape = shape,
		colors = ButtonDefaults.colors(
			containerColor = when {
				armed -> Color(0xFFB71C1C)
				else -> nova.surfaceRaised
			},
			contentColor = Color.White,
			focusedContainerColor = when {
				armed -> Color(0xFFE53935)
				danger -> Color(0xFF7A1F1F)
				else -> nova.surfaceFocus
			},
			focusedContentColor = Color.White,
		),
		contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
	) {
		Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(16.dp))
		Spacer(Modifier.width(6.dp))
		Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
	}
}

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults

/**
 * "Which seasons?" pop-up for TV downloads.
 * Choose all missing seasons or a single season; seasons you already have or
 * have already asked for are shown but can't be picked again.
 */
@Composable
fun NovaSeasonPicker(
	config: NovaSeerr.Config,
	tmdbId: Int,
	title: String,
	onDismiss: () -> Unit,
	/** Called after a request was sent (true) or failed (false). */
	onRequested: (Boolean) -> Unit,
	/** When set, picking one season hands over to "Choose a copy" instead of requesting it. */
	onSeasonChosen: ((Int) -> Unit)? = null,
	/** When set, "All missing seasons" hands every missing season to "Choose a copy", one at a time. */
	onAllMissingChosen: ((List<Int>) -> Unit)? = null,
) {
	val scope = rememberCoroutineScope()
	var seasons by remember(tmdbId) { mutableStateOf<List<NovaSeerr.SeasonInfo>?>(null) }
	var sending by remember { mutableStateOf(false) }
	val firstFocus = remember { FocusRequester() }

	LaunchedEffect(tmdbId) {
		seasons = NovaSeerr.seasons(config, tmdbId)
		delay(50L)
		runCatching { firstFocus.requestFocus() }
	}

	fun send(numbers: List<Int>?) {
		if (sending) return
		scope.launch {
			sending = true
			val ok = NovaSeerr.request(config, NovaSeerr.MediaType.TV, tmdbId, numbers)
			sending = false
			onRequested(ok)
		}
	}

	Dialog(onDismissRequest = onDismiss) {
		Column(
			modifier = Modifier
				.width(460.dp)
				.background(nova.surface, RoundedCornerShape(nova.corner(12)))
				.border(1.dp, nova.surfaceHigh, RoundedCornerShape(nova.corner(12)))
				.padding(20.dp)
		) {
			Text("DOWNLOAD", color = nova.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
			Spacer(Modifier.height(4.dp))
			Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
			Spacer(Modifier.height(4.dp))
			Text(
				if (sending) "Requesting…" else "Which seasons do you want?",
				color = nova.textMuted,
				fontSize = 13.sp,
			)
			Spacer(Modifier.height(14.dp))

			val list = seasons
			when {
				list == null -> Text("Loading seasons…", color = nova.textSecondary, fontSize = 14.sp)
				else -> {
					val missing = list.filter { it.status == NovaSeerr.State.NOT_REQUESTED }
					LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
						item {
							SeasonOption(
								label = if (missing.isEmpty()) "All seasons" else "All missing seasons",
								detail = when {
									list.isEmpty() -> "Whole show"
									missing.isEmpty() -> "Nothing left to download"
									else -> "${missing.size} season${if (missing.size == 1) "" else "s"}"
								},
								status = null,
								enabled = !sending && (missing.isNotEmpty() || list.isEmpty()),
								onClick = {
									if (onAllMissingChosen != null && missing.isNotEmpty()) {
										onAllMissingChosen(missing.map { it.number })
									} else {
										send(null)
									}
								},
								modifier = Modifier.focusRequester(firstFocus),
							)
						}
						items(list, key = { it.number }) { season ->
							SeasonOption(
								label = season.name,
								detail = if (season.episodeCount > 0) "${season.episodeCount} episodes" else "",
								status = season.status.takeIf { it != NovaSeerr.State.NOT_REQUESTED },
								enabled = !sending && season.status == NovaSeerr.State.NOT_REQUESTED,
								onClick = {
									if (onSeasonChosen != null) onSeasonChosen(season.number) else send(listOf(season.number))
								},
							)
						}
						item {
							Spacer(Modifier.height(6.dp))
							SeasonOption(
								label = "Cancel",
								detail = "",
								status = null,
								enabled = true,
								onClick = onDismiss,
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun SeasonOption(
	label: String,
	detail: String,
	status: NovaSeerr.State?,
	enabled: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(nova.corner(6))
	Button(
		onClick = { if (enabled) onClick() },
		modifier = modifier
			.fillMaxWidth()
			.padding(vertical = 3.dp)
			.then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier),
		interactionSource = interactionSource,
		shape = shape,
		colors = ButtonDefaults.colors(
			containerColor = nova.surfaceRaised,
			contentColor = if (enabled) Color.White else nova.textFaint,
			focusedContainerColor = if (enabled) nova.accent.withAlpha(0x80) else nova.surfaceRaised,
			focusedContentColor = if (enabled) Color.White else nova.textMuted,
		),
		contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
	) {
		Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
			Column(modifier = Modifier.weight(1f)) {
				Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
				if (detail.isNotBlank()) Text(detail, fontSize = 11.sp, color = nova.textMuted, maxLines = 1)
			}
			if (status != null) {
				val chip = NovaSeerr.Status(status)
				Box(
					modifier = Modifier
						.background(chip.colour(), RoundedCornerShape(nova.corner(4)))
						.padding(horizontal = 8.dp, vertical = 3.dp)
				) {
					Text(
						when (status) {
							NovaSeerr.State.DOWNLOADED -> "Downloaded"
							NovaSeerr.State.PARTLY_DOWNLOADED -> "Partly downloaded"
							NovaSeerr.State.PENDING_APPROVAL -> "Waiting for approval"
							else -> "Requested"
						},
						color = Color.White,
						fontSize = 11.sp,
						fontWeight = FontWeight.Bold,
					)
				}
			} else {
				Spacer(Modifier.size(1.dp))
			}
		}
	}
}

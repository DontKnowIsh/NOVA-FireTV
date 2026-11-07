package org.jellyfin.androidtv.ui.browsing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItemSelectAction
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.androidtv.ui.withAlpha
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.compose.koinInject

/**
 * The buttons on More Info: ▶ Play, ♥ (favourite) (and Seasons for a
 * show) for titles you have; the Download button for Discover titles.
 */
@Composable
fun NovaInfoActions(item: BaseItemDto, firstFocus: FocusRequester) {
	val api = koinInject<ApiClient>()
	val itemLauncher = koinInject<ItemLauncher>()
	val playbackHelper = koinInject<org.jellyfin.androidtv.util.PlaybackHelper>()
	val navigationRepository = koinInject<NavigationRepository>()
	val itemMutationRepository = koinInject<ItemMutationRepository>()
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	var isDiscover by remember(item.id) { mutableStateOf<Boolean?>(null) }
	var favourite by remember(item.id) { mutableStateOf(item.userData?.isFavorite == true) }

	LaunchedEffect(item.id) {
		isDiscover = runCatching { NovaSeerr.isDiscoverItem(api, item) }.getOrDefault(false)
		delay(80L)
		runCatching { firstFocus.requestFocus() }
	}
	// Shows: the episode to watch next (where you're up to, or S1 E1).
	var nextEpisode by remember(item.id) { mutableStateOf<BaseItemDto?>(null) }
	LaunchedEffect(item.id, item.userData?.playedPercentage, item.userData?.unplayedItemCount) {
		if (item.type != BaseItemKind.SERIES) return@LaunchedEffect
		nextEpisode = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
			runCatching {
				api.tvShowsApi.getNextUp(
					org.jellyfin.sdk.model.api.request.GetNextUpRequest(
						seriesId = item.id,
						limit = 1,
						enableResumable = true,
						disableFirstEpisode = false,
						enableRewatching = false,
						fields = org.jellyfin.androidtv.data.repository.ItemRepository.itemFields,
					)
				).content.items.firstOrNull()
					?: api.tvShowsApi.getEpisodes(
						org.jellyfin.sdk.model.api.request.GetEpisodesRequest(
							seriesId = item.id,
							season = 1,
							limit = 1,
							fields = org.jellyfin.androidtv.data.repository.ItemRepository.itemFields,
						)
					).content.items.firstOrNull()
			}.getOrNull()
		} ?: nextEpisode
	}

	LaunchedEffect(item.userData) {
		item.userData?.let {
			favourite = it.isFavorite
		}
	}

	when (isDiscover) {
		null -> Box(Modifier.height(40.dp))
		true -> NovaActionButton(item = item, modifier = Modifier.focusRequester(firstFocus))
		false -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
			// Shows: Continue, Seasons and ♥.
			val isShow = item.type == BaseItemKind.SERIES
			// Part-watched: "Resume" (from where you stopped) and "Play from start".
			val resumeTicks = item.userData?.playbackPositionTicks ?: 0L
			val canResume = !isShow && resumeTicks > 0L
			if (!isShow) Button(
				onClick = {
					if (canResume) {
						playbackHelper.retrieveAndPlay(item.id, false, null, context)
					} else {
						itemLauncher.launch(
							BaseItemDtoBaseRowItem(item = item, selectAction = BaseRowItemSelectAction.Play),
							null,
							context,
						)
					}
				},
				modifier = Modifier.focusRequester(firstFocus),
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
					if (canResume) "▶  Resume from ${resumeTime(resumeTicks)}" else "▶  Play",
					fontSize = 14.sp,
					fontWeight = FontWeight.Bold,
				)
			}
			if (canResume) Button(
				onClick = { playbackHelper.retrieveAndPlay(item.id, false, 0L, context) },
				shape = RoundedCornerShape(nova.corner(5)),
				colors = ButtonDefaults.colors(
					containerColor = nova.surfaceHigh.withAlpha(0xAA),
					contentColor = Color.White,
					focusedContainerColor = nova.accent,
					focusedContentColor = nova.onAccent,
				),
				contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
			) { Text("↺  Play from start", fontSize = 14.sp, fontWeight = FontWeight.Bold) }

			// Shows: ▶ Continue S2 E5 (or Play S1 E1), straight into the right episode.
			if (isShow) Button(
				onClick = {
					val episode = nextEpisode
					if (episode != null) playbackHelper.retrieveAndPlay(episode.id, false, null, context)
					else navigationRepository.navigate(Destinations.novaSeries(item))
				},
				modifier = Modifier.focusRequester(firstFocus),
				shape = RoundedCornerShape(nova.corner(5)),
				colors = ButtonDefaults.colors(
					containerColor = nova.textPrimary,
					contentColor = nova.surface,
					focusedContainerColor = nova.accent,
					focusedContentColor = nova.onAccent,
				),
				contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
			) {
				Text(episodeLabel(item, nextEpisode), fontSize = 14.sp, fontWeight = FontWeight.Bold)
			}

			// Shows: Seasons next to Continue, the heart last.
			if (isShow) {
				Button(
					onClick = { navigationRepository.navigate(Destinations.novaSeries(item)) },
					shape = RoundedCornerShape(nova.corner(5)),
					colors = ButtonDefaults.colors(
						containerColor = nova.surfaceHigh.withAlpha(0xAA),
						contentColor = Color.White,
						focusedContainerColor = nova.accent,
						focusedContentColor = nova.onAccent,
					),
					contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
				) { Text("Seasons", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
			}

			// Favourite: just a heart (filled when it's a favourite).
			Button(
				onClick = {
					scope.launch {
						runCatching { itemMutationRepository.setFavorite(item.id, !favourite) }
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

		}
	}
}

/** "1:23:45" or "45:12" from Jellyfin ticks (10,000,000 per second). */
private fun resumeTime(ticks: Long): String {
	val total = ticks / 10_000_000L
	val h = total / 3600
	val m = (total % 3600) / 60
	val sec = total % 60
	return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** "▶  Continue S2 E5", "▶  Resume S2 E5" or "▶  Play S1 E1". */
private fun episodeLabel(series: BaseItemDto, episode: BaseItemDto?): String {
	episode ?: return "▶  Play"
	val code = "S${episode.parentIndexNumber ?: 1} E${episode.indexNumber ?: 1}"
	val partWatched = (episode.userData?.playbackPositionTicks ?: 0L) > 0L
	val started = (series.userData?.playedPercentage ?: 0.0) > 0.0 ||
		(episode.parentIndexNumber ?: 1) > 1 || (episode.indexNumber ?: 1) > 1
	return when {
		partWatched -> "▶  Resume $code"
		started -> "▶  Continue $code"
		else -> "▶  Play $code"
	}
}

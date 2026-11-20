package org.jellyfin.androidtv.ui

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.browsing.NovaIndianFilms
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.subtitleApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.RemoteSubtitleInfo
import org.koin.java.KoinJavaComponent
import java.util.UUID

/**
 * "Find subtitles": searches Jellyfin's subtitle plugin (OpenSubtitles) for the
 * title in English (plus Hindi and Punjabi with "Indian films" on), and downloads
 * the one you pick. Jellyfin saves it next to the video, so it is there every time after.
 */
object NovaSubtitles {
	private const val TAG = "nova_subtitles"

	/** English always; Hindi and Punjabi only with Settings → "Indian films" on. */
	private val languages: List<Pair<String, String>>
		get() = listOfNotNull(
			"English" to "eng",
			("Punjabi" to "pan").takeIf { NovaIndianFilms.isEnabled() },
			("Hindi" to "hin").takeIf { NovaIndianFilms.isEnabled() },
		)

	/**
	 * Shows the search over the current screen. [onAdded] gets the new subtitle's
	 * stream index once Jellyfin has it (null if it could not be found in time);
	 * [onClosed] runs when the search closes without a download.
	 */
	fun show(
		activity: Activity,
		item: BaseItemDto,
		onClosed: () -> Unit = {},
		onAdded: (Int?) -> Unit = {},
	) {
		val root = activity.window?.decorView as? ViewGroup ?: return
		root.findViewWithTag<View>(TAG)?.let { root.removeView(it) }
		val previousFocus = activity.currentFocus

		val view = ComposeView(activity)
		view.tag = TAG
		fun close() {
			root.removeView(view)
			previousFocus?.requestFocus()
		}
		view.setContent {
			JellyfinTheme {
				SubtitleSearch(
					item = item,
					onClose = { close(); onClosed() },
					onAdded = { index ->
						close()
						NovaToast.show(activity, "Subtitles added")
						onAdded(index)
					},
					onFailed = { message -> NovaToast.show(activity, message, error = true, long = true) },
				)
			}
		}
		root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
	}

	private fun userId(): UUID? = runCatching {
		KoinJavaComponent.get<UserRepository>(UserRepository::class.java).currentUser.value?.id
	}.getOrNull()

	/** True when the title already has subtitles (inside the file or next to it); null if unknown. */
	suspend fun hasSubtitles(item: BaseItemDto): Boolean? = withContext(Dispatchers.IO) {
		runCatching {
			val api = KoinJavaComponent.get<ApiClient>(ApiClient::class.java)
			api.userLibraryApi.getItem(itemId = item.id, userId = userId()).content
				.mediaStreams.orEmpty()
				.any { it.type == MediaStreamType.SUBTITLE }
		}.getOrNull()
	}

	private suspend fun subtitleIndexes(api: ApiClient, itemId: UUID): Set<Int> = runCatching {
		api.userLibraryApi.getItem(itemId = itemId, userId = userId()).content
			.mediaStreams.orEmpty()
			.filter { it.type == MediaStreamType.SUBTITLE }
			.map { it.index }
			.toSet()
	}.getOrDefault(emptySet())

	@Composable
	private fun SubtitleSearch(
		item: BaseItemDto,
		onClose: () -> Unit,
		onAdded: (Int?) -> Unit,
		onFailed: (String) -> Unit,
	) {
		val api = remember { KoinJavaComponent.get<ApiClient>(ApiClient::class.java) }
		val scope = rememberCoroutineScope()
		var language by remember { mutableStateOf(languages.first()) }
		var results by remember { mutableStateOf<List<RemoteSubtitleInfo>?>(null) }
		var error by remember { mutableStateOf<String?>(null) }
		var downloading by remember { mutableStateOf<String?>(null) }
		val firstFocus = remember { FocusRequester() }

		LaunchedEffect(language) {
			results = null
			error = null
			val found = withContext(Dispatchers.IO) {
				runCatching {
					api.subtitleApi.searchRemoteSubtitles(itemId = item.id, language = language.second).content
				}
			}
			found.onSuccess { list ->
				// Exact matches for this file first, then the most downloaded.
				results = list.sortedWith(
					compareByDescending<RemoteSubtitleInfo> { it.isHashMatch == true }
						.thenByDescending { it.downloadCount ?: 0 }
				).take(40)
			}.onFailure {
				error = "Couldn't search for subtitles. Is the Open Subtitles plugin set up in Jellyfin?"
			}
		}
		LaunchedEffect(Unit) {
			delay(60)
			runCatching { firstFocus.requestFocus() }
		}

		fun download(sub: RemoteSubtitleInfo) {
			val id = sub.id ?: return
			if (downloading != null) return
			downloading = id
			scope.launch {
				val before = withContext(Dispatchers.IO) { subtitleIndexes(api, item.id) }
				val ok = withContext(Dispatchers.IO) {
					runCatching { api.subtitleApi.downloadRemoteSubtitles(itemId = item.id, subtitleId = id) }.isSuccess
				}
				if (!ok) {
					downloading = null
					onFailed("Couldn't download that one (daily limit reached?). Try another.")
					return@launch
				}
				// Jellyfin adds the new subtitle to the title a moment after saving it.
				var newIndex: Int? = null
				repeat(12) {
					if (newIndex != null) return@repeat
					delay(1_250)
					val after = withContext(Dispatchers.IO) { subtitleIndexes(api, item.id) }
					newIndex = (after - before).maxOrNull()
				}
				if (newIndex == null) {
					// Jellyfin found it but couldn't save it next to the video
					// (usually folder permissions on the server).
					downloading = null
					onFailed("Jellyfin couldn't save that subtitle. Check it can write to the movie folder.")
					return@launch
				}
				onAdded(newIndex)
			}
		}

		val buttonColors = ButtonDefaults.colors(
			containerColor = nova.surfaceHigh.withAlpha(0xAA),
			contentColor = Color.White,
			focusedContainerColor = nova.accent,
			focusedContentColor = nova.onAccent,
		)

		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.Black.withAlpha(0xB8))
				.onPreviewKeyEvent { event ->
					if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
						if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) onClose()
						true
					} else false
				},
			contentAlignment = Alignment.Center,
		) {
			Column(
				modifier = Modifier
					.width(640.dp)
					.background(nova.surface, RoundedCornerShape(nova.corner(14)))
					.border(1.dp, nova.accent.withAlpha(0x55), RoundedCornerShape(nova.corner(14)))
					.padding(22.dp),
			) {
				Text("Find subtitles", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
				Text(
					item.name.orEmpty(),
					color = nova.textMuted,
					fontSize = 14.sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Spacer(Modifier.height(14.dp))
				Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
					languages.forEachIndexed { i, lang ->
						val selected = lang == language
						Button(
							onClick = { language = lang },
							modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
							shape = RoundedCornerShape(nova.corner(18)),
							colors = ButtonDefaults.colors(
								containerColor = if (selected) nova.textPrimary else nova.surfaceHigh.withAlpha(0xAA),
								contentColor = if (selected) nova.surface else Color.White,
								focusedContainerColor = nova.accent,
								focusedContentColor = nova.onAccent,
							),
							contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp),
						) { Text(lang.first, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
					}
				}
				Spacer(Modifier.height(14.dp))

				val list = results
				when {
					error != null -> Text(error.orEmpty(), color = nova.statusMissing, fontSize = 15.sp)
					list == null -> Text("Searching…", color = nova.textMuted, fontSize = 15.sp)
					list.isEmpty() -> Text("No ${language.first} subtitles found for this one.", color = nova.textMuted, fontSize = 15.sp)
					else -> LazyColumn(
						modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
						verticalArrangement = Arrangement.spacedBy(6.dp),
					) {
						itemsIndexed(list) { _, sub ->
							Button(
								onClick = { download(sub) },
								modifier = Modifier.fillMaxWidth(),
								shape = RoundedCornerShape(nova.corner(8)),
								colors = buttonColors,
								contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
							) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(10.dp),
								) {
									Text(
										sub.name ?: sub.comment ?: "Subtitle",
										fontSize = 14.sp,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
										modifier = Modifier.weight(1f),
									)
									if (sub.isHashMatch == true) Text("✓ Exact match", fontSize = 12.sp, fontWeight = FontWeight.Bold)
									if (sub.hearingImpaired == true) Text("CC", fontSize = 12.sp, fontWeight = FontWeight.Bold)
									sub.downloadCount?.let { Text("⬇ ${compact(it)}", fontSize = 12.sp) }
									if (downloading == sub.id) Text("Adding…", fontSize = 12.sp, fontWeight = FontWeight.Bold)
								}
							}
						}
					}
				}
				Spacer(Modifier.height(12.dp))
				Text("Press Back to close", color = nova.textMuted, fontSize = 12.sp)
			}
		}
	}

	private fun compact(n: Int): String = when {
		n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
		n >= 1_000 -> "%.1fk".format(n / 1_000f)
		else -> n.toString()
	}
}

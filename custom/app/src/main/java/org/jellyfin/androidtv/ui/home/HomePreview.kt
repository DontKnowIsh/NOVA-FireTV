package org.jellyfin.androidtv.ui.home

import android.content.Context
import android.os.SystemClock
import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.media3.datasource.HttpDataSource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.playback.rewrite.RewriteMediaManager
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.androidtv.util.apiclient.Response
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.androidtv.util.profile.createDeviceProfile
import org.jellyfin.androidtv.ui.player.base.PlayerSurface
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.playback.core.playbackManager
import org.jellyfin.playback.core.plugin.playbackPlugin
import org.jellyfin.playback.core.model.PlayState
import org.jellyfin.playback.core.queue.queue
import org.jellyfin.playback.jellyfin.mediastream.JellyfinMediaStreamResolver
import org.jellyfin.playback.media3.exoplayer.ExoPlayerOptions
import org.jellyfin.playback.media3.exoplayer.exoPlayerPlugin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.compose.koinInject
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

data class HomePreview(
	val item: BaseItemDto,
	val playableItem: BaseItemDto,
	val startPositionMs: Long,
)

object HomePreviewPlaybackController {
	@Volatile
	private var activeManager: PlaybackManager? = null

	fun attach(manager: PlaybackManager) {
		activeManager = manager
	}

	fun detach(manager: PlaybackManager) {
		if (activeManager === manager) activeManager = null
	}

	fun stopActivePreview() {
		activeManager?.let { manager ->
			runCatching { manager.state.stop() }
			runCatching { manager.queue.clear() }
		}
	}
}

// Keep recently resolved preview items in memory. Moving away from a card and
// back to it should not repeat Jellyfin API lookups or Series -> Episode
// resolution. The small bounded cache is cleared naturally when the app exits.
private object HomePreviewCache {
	private const val MAX_ENTRIES = 80
	private val previews = object : LinkedHashMap<String, HomePreview>(MAX_ENTRIES, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HomePreview>?): Boolean =
			size > MAX_ENTRIES
	}

	@Synchronized
	fun get(id: String): HomePreview? = previews[id]

	@Synchronized
	fun put(id: String, preview: HomePreview) {
		previews[id] = preview
	}
}

// Older/legacy containers are normally handled by Jellyfin's HLS transcoder.
// Starting those previews with a second seek can make them appear slow or fail
// on Fire TV, so let them begin at 0 while modern seek-friendly files still
// jump into the title.
private val legacyPreviewContainers = setOf(
	"avi", "asf", "wmv", "vob", "ogm", "ogv", "xvid", "mpg", "mpeg",
)

private fun calculatePreviewStartMs(item: BaseItemDto): Long {
	val container = item.mediaSources?.firstOrNull()?.container?.lowercase()
	if (container in legacyPreviewContainers) return 0L

	val durationMs = (item.runTimeTicks ?: 0L) / 10_000L
	val safeEnd = (durationMs - 45_000L).coerceAtLeast(0L)
	return if (safeEnd > 60_000L) {
		Random.nextLong(
			30_000L,
			safeEnd.coerceAtMost((durationMs * 2L) / 3L),
		)
	} else {
		0L
	}
}

private suspend fun refreshPlayableItem(
	api: ApiClient,
	item: BaseItemDto,
): BaseItemDto = runCatching {
	api.userLibraryApi.getItem(itemId = item.id).content
}.getOrDefault(item)

private suspend fun resolveWithJellyfinPlaybackHelper(
	context: Context,
	playbackHelper: PlaybackHelper,
	api: ApiClient,
	selectedItem: BaseItemDto,
): BaseItemDto? = suspendCancellableCoroutine { continuation ->
	playbackHelper.getItemsToPlay(
		context = context,
		mainItem = selectedItem,
		allowIntros = false,
		shuffle = true,
		outerResponse = object : Response<List<BaseItemDto>>() {
			override fun onResponse(response: List<BaseItemDto>) {
				val candidate = response.firstOrNull { item ->
					item.type == BaseItemKind.EPISODE ||
						item.type == BaseItemKind.MOVIE ||
						item.type == BaseItemKind.VIDEO
				}

				if (!continuation.isActive) return
				if (candidate == null) {
					continuation.resume(null)
					return
				}

				if (continuation.isActive) continuation.resume(candidate)
			}

			override fun onError(exception: Exception) {
				if (continuation.isActive) continuation.resume(null)
			}
		},
	)
}

private suspend fun getPlayablePreviewItem(
	api: ApiClient,
	selectedItem: BaseItemDto,
): BaseItemDto? = when (selectedItem.type) {
	BaseItemKind.MOVIE,
	BaseItemKind.EPISODE,
	BaseItemKind.VIDEO -> selectedItem

	BaseItemKind.SERIES -> {
		// A Series card is not directly playable. Resolve it to a real episode
		// using several Jellyfin paths so different TV library layouts still work.
		//
		// 1) Prefer Next Up when Jellyfin has one for this series.
		val nextUp = runCatching {
			api.tvShowsApi.getNextUp(
				seriesId = selectedItem.id,
				fields = ItemRepository.itemFields,
				limit = 1,
			).content.items.firstOrNull()
		}.getOrNull()

		if (nextUp?.type == BaseItemKind.EPISODE) {
			nextUp
		} else {
			// 2) Ask Jellyfin's dedicated TV Shows API for episodes in the series.
			val seriesEpisode = runCatching {
				api.tvShowsApi.getEpisodes(
					seriesId = selectedItem.id,
					isMissing = false,
					sortBy = ItemSortBy.RANDOM,
					limit = 50,
					fields = ItemRepository.itemFields,
				).content.items.firstOrNull { episode ->
					episode.type == BaseItemKind.EPISODE
				}
			}.getOrNull()

			if (seriesEpisode != null) {
				seriesEpisode
			} else {
				// 3) Final fallback: recursively search below the Series item.
				// This covers libraries where episodes are nested under Season folders.
				runCatching {
					api.itemsApi.getItems(
						parentId = selectedItem.id,
						isMissing = false,
						includeItemTypes = listOf(BaseItemKind.EPISODE),
						recursive = true,
						sortBy = listOf(ItemSortBy.RANDOM),
						limit = 50,
						fields = ItemRepository.itemFields,
						imageTypes = listOf(ImageType.BACKDROP, ImageType.LOGO),
					).content.items.firstOrNull { episode ->
						episode.type == BaseItemKind.EPISODE
					}
				}.getOrNull()
			}
		}
	}

	else -> null
}

private suspend fun createHomePreviewForItem(
	context: Context,
	playbackHelper: PlaybackHelper,
	api: ApiClient,
	selectedItem: BaseItemDto,
): HomePreview? {
	val cacheKey = selectedItem.id.toString()
	HomePreviewCache.get(cacheKey)?.let { cached ->
		return cached.copy(item = selectedItem)
	}

	val resolvedItem = when (selectedItem.type) {
		BaseItemKind.SERIES -> {
			// The TV API is considerably faster than going through the legacy
			// launcher helper. Keep that helper as a fallback for unusual
			// Season/Series layouts.
			getPlayablePreviewItem(api, selectedItem)
				?: resolveWithJellyfinPlaybackHelper(context, playbackHelper, api, selectedItem)
		}

		BaseItemKind.SEASON ->
			resolveWithJellyfinPlaybackHelper(context, playbackHelper, api, selectedItem)

		else -> getPlayablePreviewItem(api, selectedItem)
	} ?: return null

	// Home/library cards intentionally contain lightweight metadata. Always
	// refresh the actual playable movie/episode before preview playback so the
	// media source, container and stream information is present. This is also
	// what fixed direct Home playback for mixed MKV/MP4/AVI libraries.
	val playableItem = refreshPlayableItem(api, resolvedItem)

	val preview = HomePreview(
		item = selectedItem,
		playableItem = playableItem,
		startPositionMs = calculatePreviewStartMs(playableItem),
	)
	HomePreviewCache.put(cacheKey, preview)
	return preview
}

@Composable
fun rememberSelectedHomePreview(item: BaseItemDto?): HomePreview? {
	val context = LocalContext.current
	val api = koinInject<ApiClient>()
	val playbackHelper = koinInject<PlaybackHelper>()
	var preview by remember(item?.id) { mutableStateOf<HomePreview?>(null) }

	LaunchedEffect(api, item?.id) {
		preview = null
		if (item == null) return@LaunchedEffect
		if (item.type !in setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.SEASON, BaseItemKind.EPISODE, BaseItemKind.VIDEO)) {
			return@LaunchedEffect
		}

		// Avoid starting a Jellyfin request for every poster the focus passes.
		// Normal browsing now stays light, while a settled card still previews
		// after roughly the same two-second hover window.
		delay(650L)
		val resolveStartedAt = SystemClock.elapsedRealtime()
		val resolvedPreview = try {
			withContext(Dispatchers.IO) {
				createHomePreviewForItem(context, playbackHelper, api, item)
			}
		} catch (cancelled: CancellationException) {
			throw cancelled
		} catch (error: Exception) {
			Timber.w(error, "NOVA preview resolve failed: %s", item.name)
			null
		}
		val remainingDelay = 1_350L - (SystemClock.elapsedRealtime() - resolveStartedAt)
		if (remainingDelay > 0L) delay(remainingDelay)
		preview = resolvedPreview
	}

	return preview
}

@Composable
fun rememberRandomHomePreview(enabled: Boolean = true): HomePreview? {
	val context = LocalContext.current
	val api = koinInject<ApiClient>()
	val playbackHelper = koinInject<PlaybackHelper>()
	var preview by remember { mutableStateOf<HomePreview?>(null) }

	LaunchedEffect(api, enabled) {
		if (!enabled) {
			preview = null
			return@LaunchedEffect
		}

		while (isActive) {
			val randomPreview = try {
				withContext(Dispatchers.IO) {
					val response by api.itemsApi.getItems(
						includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
						recursive = true,
						sortBy = listOf(ItemSortBy.RANDOM),
						limit = 12,
						fields = setOf(
							ItemFields.OVERVIEW,
							ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
						),
						imageTypes = listOf(ImageType.BACKDROP, ImageType.LOGO),
					)

					response.items.firstNotNullOfOrNull { candidate ->
						createHomePreviewForItem(context, playbackHelper, api, candidate)
					}
				}
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (error: Exception) {
				Timber.w(error, "NOVA random preview resolve failed")
				null
			}
			if (randomPreview != null) preview = randomPreview

			delay(30_000L)
		}
	}

	return preview
}

@Composable
fun HomePreviewPlayer(
	preview: HomePreview?,
	modifier: Modifier = Modifier,
	onPlayingChanged: (Boolean) -> Unit = {},
	onPreviewFinished: () -> Unit = {},
) {
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val api = koinInject<ApiClient>()
	val userPreferences = koinInject<UserPreferences>()
	val serverVersion = koinInject<ServerVersion>()
	val dataSourceFactory = koinInject<HttpDataSource.Factory>()
	val currentOnPlayingChanged by rememberUpdatedState(onPlayingChanged)
	val currentOnPreviewFinished by rememberUpdatedState(onPreviewFinished)

	// Keep one private preview player alive while the browse page is on-screen.
	// Recreating ExoPlayer every time focus moves to a new poster was one of the
	// largest sources of sluggish preview startup on Fire TV.
	val playbackManager = remember(context, api, userPreferences, serverVersion, dataSourceFactory) {
		playbackManager(context) {
			install(
				exoPlayerPlugin(
					context,
					ExoPlayerOptions(
						preferFfmpeg = userPreferences[UserPreferences.preferExoPlayerFfmpeg],
						enableDebugLogging = false,
						baseDataSourceFactory = dataSourceFactory,
					)
				)
			)

			val deviceProfileBuilder = {
				createDeviceProfile(context, userPreferences, serverVersion)
			}
			install(
				playbackPlugin {
					provide(JellyfinMediaStreamResolver(api, deviceProfileBuilder))
				}
			)
		}
	}

	val previewId = preview?.playableItem?.id
	var videoVisible by remember(previewId) { mutableStateOf(false) }
	val backdropAlpha by animateFloatAsState(
		targetValue = if (videoVisible) 0f else 1f,
		animationSpec = tween(durationMillis = 2000),
		label = "novaPreviewBackdropAlpha",
	)
	val previewBackdrop = remember(preview?.item?.id) {
		preview?.item?.itemBackdropImages?.firstOrNull()
			?: preview?.item?.parentBackdropImages?.firstOrNull()
	}

	LaunchedEffect(previewId) {
		videoVisible = false
		currentOnPlayingChanged(false)

		val activePreview = preview
		if (activePreview == null) {
			runCatching { playbackManager.state.stop() }
			runCatching { playbackManager.queue.clear() }
			return@LaunchedEffect
		}

		var stateBeforePlay = playbackManager.state.playState.value
		val startResult = runCatching {
			playbackManager.state.stop()
			playbackManager.queue.clear()
			stateBeforePlay = playbackManager.state.playState.value
			playbackManager.queue.addSupplier(
				RewriteMediaManager.BaseItemQueueSupplier(
					api = api,
					items = listOf(activePreview.playableItem),
					visibleInScreensaver = false,
				)
			)
			playbackManager.state.play()
		}
		if (startResult.isFailure) {
			Timber.e(startResult.exceptionOrNull(), "NOVA preview failed to initialize: %s", activePreview.playableItem.name)
			currentOnPlayingChanged(false)
			currentOnPreviewFinished()
			return@LaunchedEffect
		}

		// Give Jellyfin enough time to direct-play or start an HLS transcode.
		// Waiting for ERROR as well as PLAYING prevents a bad file from leaving
		// the preview coroutine stuck forever.
		val startState = withTimeoutOrNull(9_000L) {
			playbackManager.state.playState
				.dropWhile { it == stateBeforePlay }
				.first { it == PlayState.PLAYING || it == PlayState.ERROR }
		}

		if (startState != PlayState.PLAYING) {
			val container = activePreview.playableItem.mediaSources
				?.firstOrNull()?.container.orEmpty()
			Timber.w(
				"NOVA preview could not start: %s [%s]",
				activePreview.playableItem.name,
				container,
			)
			runCatching { playbackManager.state.stop() }
			runCatching { playbackManager.queue.clear() }
			currentOnPreviewFinished()
			return@LaunchedEffect
		}

		if (!isActive) return@LaunchedEffect
		videoVisible = true
		currentOnPlayingChanged(true)

		if (activePreview.startPositionMs > 0L) {
			// A short settle keeps modern MKV/MP4 seeking smooth. Legacy
			// containers use startPositionMs=0 and avoid this second buffer.
			delay(100L)
			runCatching {
				playbackManager.state.seek(activePreview.startPositionMs.milliseconds)
			}.onFailure { error ->
				Timber.w(error, "NOVA preview seek failed: %s", activePreview.playableItem.name)
			}
		}

		// 30-second preview: 28 seconds visible + a slow 2-second fade back.
		delay(28_000L)
		videoVisible = false
		currentOnPlayingChanged(false)
		delay(2_000L)
		runCatching { playbackManager.state.stop() }
		runCatching { playbackManager.queue.clear() }
		currentOnPreviewFinished()
	}

	DisposableEffect(playbackManager, lifecycleOwner) {
		HomePreviewPlaybackController.attach(playbackManager)

		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
				videoVisible = false
				currentOnPlayingChanged(false)
				runCatching { playbackManager.state.stop() }
				runCatching { playbackManager.queue.clear() }
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)

		onDispose {
			lifecycleOwner.lifecycle.removeObserver(observer)
			HomePreviewPlaybackController.detach(playbackManager)
			videoVisible = false
			currentOnPlayingChanged(false)
			runCatching { playbackManager.state.stop() }
			runCatching { playbackManager.queue.clear() }
		}
	}

	// The host stays composed even when preview == null, preserving the player
	// for the next focused card. Only the video surface is conditional.
	if (preview != null) {
		Box(modifier = modifier) {
			PlayerSurface(
				modifier = Modifier.fillMaxSize(),
				playbackManager = playbackManager,
			)

			if (previewBackdrop != null) {
				AsyncImage(
					url = previewBackdrop.getUrl(api, maxWidth = 1280, maxHeight = 720),
					blurHash = previewBackdrop.blurHash,
					aspectRatio = 16f / 9f,
					scaleType = ImageView.ScaleType.CENTER_CROP,
					modifier = Modifier
						.fillMaxSize()
						.alpha(backdropAlpha),
				)
			}
		}
	}
}

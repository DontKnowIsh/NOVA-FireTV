package org.jellyfin.androidtv.ui.browsing

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.browsing.NovaSeerr.double
import org.jellyfin.androidtv.ui.browsing.NovaSeerr.int
import org.jellyfin.androidtv.ui.browsing.NovaSeerr.string
import org.jellyfin.androidtv.ui.home.NovaTrailerPlayer
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.androidtv.ui.withAlpha
import timber.log.Timber

/** The card a preview was opened from (focus goes back to it on close). */
internal class PreviewReturn(val key: String, val focus: FocusRequester, val onReturned: () -> Unit)

internal val LocalPreviewReturn = compositionLocalOf<PreviewReturn?> { null }

/** What the preview shows about a film or show (from Seerr). */
internal class PreviewDetails(
	val overview: String?,
	val backdropUrl: String?,
	val runtime: String?,
	val genres: String?,
	val score: String?,
)

/** Previews already fetched (filled while the cards' age ratings load). */
internal object NovaPreviewCache {
	private val cache = java.util.concurrent.ConcurrentHashMap<String, PreviewDetails>()

	private fun key(result: NovaSeerr.SearchResult) = "${result.type.apiName}-${result.tmdbId}"

	fun get(result: NovaSeerr.SearchResult): PreviewDetails? = cache[key(result)]

	fun put(result: NovaSeerr.SearchResult, details: PreviewDetails) {
		if (cache.size > 400) cache.clear()
		cache[key(result)] = details
	}
}

internal fun previewFrom(obj: JsonObject, isMovie: Boolean): PreviewDetails {
	val runtime = if (isMovie) {
		obj.int("runtime")?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" }
	} else {
		obj.int("numberOfSeasons")?.takeIf { it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" }
	}
	return PreviewDetails(
		overview = obj.string("overview")?.takeIf { it.isNotBlank() },
		backdropUrl = obj.string("backdropPath")?.let { "https://image.tmdb.org/t/p/w1280$it" },
		runtime = runtime,
		genres = (obj["genres"] as? JsonArray).orEmpty()
			.mapNotNull { (it as? JsonObject)?.string("name") }
			.take(3)
			.joinToString(" · ")
			.ifBlank { null },
		score = obj.double("voteAverage")?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
	)
}

private suspend fun loadPreviewDetails(cfg: NovaSeerr.Config, result: NovaSeerr.SearchResult): PreviewDetails? {
	NovaPreviewCache.get(result)?.let { return it }
	return withContext(Dispatchers.IO) {
		runCatching {
			val body = NovaSeerr.http(
				url = "${cfg.baseUrl}/api/v1/${result.type.apiName}/${result.tmdbId}",
				headers = mapOf("X-Api-Key" to cfg.apiKey),
			)
			val obj = NovaSeerr.json.parseToJsonElement(body).jsonObject
			// One request for both the description and the trailer.
			NovaSeerr.rememberTrailer(result.type, result.tmdbId, NovaSeerr.trailerFrom(obj))
			previewFrom(obj, result.type == NovaSeerr.MediaType.MOVIE).also { NovaPreviewCache.put(result, it) }
		}.onFailure { Timber.w(it, "NOVA: preview details failed") }.getOrNull()
	}
}

/**
 * Pressing OK on a Download page card opens this: the trailer (or picture),
 * the description, and a Download button. Back closes it.
 */
@Composable
internal fun NovaDownloadPreview(
	config: NovaSeerr.Config,
	result: NovaSeerr.SearchResult,
	status: NovaSeerr.Status,
	onDownload: () -> Unit,
	onDismiss: () -> Unit,
) {
	var details by remember(result.tmdbId) { mutableStateOf(NovaPreviewCache.get(result)) }
	var detailsLoaded by remember(result.tmdbId) { mutableStateOf(details != null) }
	var trailer by remember(result.tmdbId) { mutableStateOf<String?>(null) }
	LaunchedEffect(result.tmdbId) {
		// Usually already fetched with the card's age rating, so this is instant.
		if (details == null) details = loadPreviewDetails(config, result)
		detailsLoaded = true
		trailer = NovaSeerr.trailerKey(config, result.type, result.tmdbId)
	}
	val ageRating by produceState(NovaAgeRatings.cached(result)?.ifEmpty { null }, result.tmdbId) {
		value = NovaAgeRatings.get(result)
	}
	val downloadFocus = remember { FocusRequester() }
	var trailerPlaying by remember { mutableStateOf(false) }
	var hasFocus by remember { mutableStateOf(false) }

	BackHandler(onBack = onDismiss)

	// Drawn over the page (a pop-up window put the trailer behind it). Back closes it;
	// nothing behind can be reached.
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black.withAlpha(0xB3))
			.onPreviewKeyEvent { event ->
				when (event.key) {
					// Only one button, so the arrows do nothing (and never move the page behind).
					Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> true
					else -> false
				}
			},
		contentAlignment = Alignment.Center,
	) {
		Column(
			modifier = Modifier
				.onFocusChanged { hasFocus = it.hasFocus }
				.width(720.dp)
				.clip(RoundedCornerShape(nova.corner(14)))
				.background(nova.surface)
				.border(1.dp, nova.surfaceHigh, RoundedCornerShape(nova.corner(14))),
		) {
			// Trailer across the whole top; the picture fills it until the trailer plays.
			Box(modifier = Modifier.fillMaxWidth().height(250.dp).clipToBounds().background(Color.Black)) {
				// 16:9 and full width (the top and bottom are trimmed), so there are no black bars.
				NovaTrailerPlayer(
					youTubeKey = trailer,
					backdropUrl = null,
					modifier = Modifier.fillMaxWidth().requiredHeight(405.dp).align(Alignment.Center),
					onPlayingChanged = { trailerPlaying = it },
				)
				val pictureAlpha by androidx.compose.animation.core.animateFloatAsState(
					targetValue = if (trailerPlaying) 0f else 1f,
					animationSpec = androidx.compose.animation.core.tween(if (trailerPlaying) 700 else 120),
					label = "novaPreviewPicture",
				)
				val picture = details?.backdropUrl ?: result.posterUrl
				if (picture != null && pictureAlpha > 0.01f) {
					org.jellyfin.androidtv.ui.composable.AsyncImage(
						url = picture,
						aspectRatio = 16f / 9f,
						scaleType = android.widget.ImageView.ScaleType.CENTER_CROP,
						modifier = Modifier.fillMaxSize().alpha(pictureAlpha),
					)
				}
				Box(
					Modifier.fillMaxSize().background(
						Brush.verticalGradient(0.6f to Color.Transparent, 1f to nova.surface),
					),
				)
				if (trailer != null && !trailerPlaying) {
					Text(
						"Trailer loading…",
						color = Color.White.withAlpha(0xAA),
						fontSize = 12.sp,
						modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
					)
				}
			}

			Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 18.dp)) {
				Text(
					result.title,
					color = Color.White,
					fontSize = 24.sp,
					fontWeight = FontWeight.Bold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Spacer(Modifier.height(6.dp))
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					Box(
						modifier = Modifier
							.background(
								if (result.type == NovaSeerr.MediaType.MOVIE) nova.movieTag else nova.tvTag,
								RoundedCornerShape(nova.corner(3)),
							)
							.padding(horizontal = 6.dp, vertical = 2.dp),
					) {
						Text(
							if (result.type == NovaSeerr.MediaType.MOVIE) "MOVIE" else "TV SHOW",
							color = Color.White,
							fontSize = 10.sp,
							fontWeight = FontWeight.Black,
						)
					}
					val meta = listOfNotNull(result.year.ifBlank { null }, details?.runtime, details?.score)
					if (meta.isNotEmpty()) Text(meta.joinToString("  ·  "), color = nova.textSecondary, fontSize = 13.sp)
					ageRating?.let { NovaRatingBadge(it, small = true) }
				}
				details?.genres?.let {
					Spacer(Modifier.height(4.dp))
					Text(it, color = nova.textMuted, fontSize = 12.sp)
				}
				Spacer(Modifier.height(10.dp))
				Text(
					details?.overview ?: if (!detailsLoaded) "Loading…" else "No description available.",
					color = nova.textPrimary,
					fontSize = 14.sp,
					maxLines = 3,
					overflow = TextOverflow.Ellipsis,
				)
				Spacer(Modifier.height(12.dp))
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					val label = when (status.state) {
						NovaSeerr.State.NOT_REQUESTED -> "⬇  Download"
						NovaSeerr.State.DOWNLOADED ->
							if (result.type == NovaSeerr.MediaType.TV) "⬇  Get more episodes" else "✓  In your library"
						else -> status.label()
					}
					PreviewButton(label, primary = true, modifier = Modifier.focusRequester(downloadFocus), onClick = onDownload)
				}
			}
		}
	}

	LaunchedEffect(Unit) {
		// Straight onto Download, so the remote never moves the page behind.
		var tries = 0
		while (!hasFocus && tries++ < 20) {
			withFrameNanos { }
			runCatching { downloadFocus.requestFocus() }
			delay(30L)
		}
		// If anything else ever takes the remote while this is open, take it back.
		while (true) {
			delay(400L)
			if (!hasFocus) runCatching { downloadFocus.requestFocus() }
		}
	}
}

@Composable
private fun PreviewButton(label: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	Button(
		onClick = onClick,
		modifier = modifier.then(
			if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(nova.corner(6))) else Modifier,
		),
		interactionSource = interactionSource,
		shape = RoundedCornerShape(nova.corner(6)),
		colors = ButtonDefaults.colors(
			containerColor = if (primary) nova.accent else nova.surfaceHigh,
			contentColor = Color.White,
			focusedContainerColor = if (primary) nova.accent else nova.surfaceRaised,
			focusedContentColor = Color.White,
		),
		contentPadding = PaddingValues(horizontal = 22.dp, vertical = 9.dp),
	) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

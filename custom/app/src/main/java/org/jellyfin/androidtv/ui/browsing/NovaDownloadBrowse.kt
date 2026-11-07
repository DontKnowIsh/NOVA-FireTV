package org.jellyfin.androidtv.ui.browsing

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.androidtv.ui.browsing.NovaSeerr.int
import org.jellyfin.androidtv.ui.browsing.NovaSeerr.string
import org.jellyfin.androidtv.ui.withAlpha
import timber.log.Timber

/**
 * Seerr's Discover extras on the Download page: Movie Genres, Series Genres,
 * Studios and Networks. Picking a tile opens a list of everything in it.
 */
internal data class NovaBrowseTile(
	val name: String,
	/** A backdrop for genres, a white logo for studios / networks. */
	val imageUrl: String?,
	val isLogo: Boolean,
	/** Seerr lists for this tile; the first that answers is used. */
	val paths: List<String>,
	val type: NovaSeerr.MediaType,
)

internal object NovaDownloadBrowse {
	private val json = Json { ignoreUnknownKeys = true }
	private var cached: List<Pair<String, List<NovaBrowseTile>>>? = null

	// The same studios and networks Seerr shows (TMDB ids), plus the main UK channels.
	private val studios = listOf(2, 127928, 34, 174, 33, 4, 3, 521, 420, 9993, 41077, 1632)
	private val networks = listOf(213, 2739, 1024, 2552, 49, 3353, 4330, 453, 4, 332, 9, 26, 1063, 56, 13)

	suspend fun tileRows(cfg: NovaSeerr.Config): List<Pair<String, List<NovaBrowseTile>>> {
		cached?.let { return it }
		val rows = coroutineScope {
			val movieGenres = async { genres(cfg, NovaSeerr.MediaType.MOVIE) }
			val tvGenres = async { genres(cfg, NovaSeerr.MediaType.TV) }
			val studioTiles = async { companies(cfg, "studio", studios, NovaSeerr.MediaType.MOVIE) }
			val networkTiles = async { companies(cfg, "network", networks, NovaSeerr.MediaType.TV) }
			listOf(
				"Movie Genres" to movieGenres.await(),
				"Series Genres" to tvGenres.await(),
				"Studios" to studioTiles.await(),
				"Networks" to networkTiles.await(),
			).filter { it.second.isNotEmpty() }
		}
		if (rows.size == 4) cached = rows
		return rows
	}

	private suspend fun genres(cfg: NovaSeerr.Config, type: NovaSeerr.MediaType): List<NovaBrowseTile> = withContext(Dispatchers.IO) {
		runCatching {
			val body = NovaSeerr.http(
				url = "${cfg.baseUrl}/api/v1/discover/genreslider/${type.apiName}?language=en",
				headers = mapOf("X-Api-Key" to cfg.apiKey),
			)
			val kind = if (type == NovaSeerr.MediaType.MOVIE) "movies" else "tv"
			(json.parseToJsonElement(body) as? JsonArray).orEmpty().mapNotNull { element ->
				val obj = element as? JsonObject ?: return@mapNotNull null
				val id = obj.int("id") ?: return@mapNotNull null
				val name = obj.string("name") ?: return@mapNotNull null
				val backdrop = (obj["backdrops"] as? JsonArray)?.firstOrNull()
					?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
				NovaBrowseTile(
					name = name,
					imageUrl = backdrop?.let { "https://image.tmdb.org/t/p/w500$it" },
					isLogo = false,
					paths = listOf("discover/$kind/genre/$id", "discover/$kind?genre=$id"),
					type = type,
				)
			}
		}.onFailure { Timber.w(it, "NOVA: genres failed") }.getOrDefault(emptyList())
	}

	private suspend fun companies(
		cfg: NovaSeerr.Config,
		kind: String,
		ids: List<Int>,
		type: NovaSeerr.MediaType,
	): List<NovaBrowseTile> = coroutineScope {
		ids.map { id ->
			async(Dispatchers.IO) {
				runCatching {
					val body = NovaSeerr.http(
						url = "${cfg.baseUrl}/api/v1/$kind/$id",
						headers = mapOf("X-Api-Key" to cfg.apiKey),
					)
					val obj = json.parseToJsonElement(body).jsonObject
					val list = if (kind == "studio") "discover/movies/studio/$id" else "discover/tv/network/$id"
					NovaBrowseTile(
						name = obj.string("name") ?: return@runCatching null,
						imageUrl = obj.string("logoPath")?.let { "https://image.tmdb.org/t/p/w500_filter(duotone,ffffff,bababa)$it" },
						isLogo = true,
						paths = listOf(list),
						type = type,
					)
				}.onFailure { Timber.w(it, "NOVA: %s %d failed", kind, id) }.getOrNull()
			}
		}.awaitAll().filterNotNull()
	}

	// Which of a tile's lists Seerr answered for, so later pages go straight there.
	private val workingPath = HashMap<NovaBrowseTile, String>()

	/**
	 * One page of a tile's titles, without films you already have;
	 * null once the list has run out.
	 */
	suspend fun tilePage(cfg: NovaSeerr.Config, tile: NovaBrowseTile, page: Int): List<NovaSeerr.SearchResult>? {
		val paths = workingPath[tile]?.let { listOf(it) } ?: tile.paths
		for (path in paths) {
			val list = NovaSeerr.browse(cfg, path, tile.type, page)
			if (list.isNotEmpty()) {
				workingPath[tile] = path
				return list.filterNot { it.type == NovaSeerr.MediaType.MOVIE && it.status.state == NovaSeerr.State.DOWNLOADED }
			}
		}
		return null
	}
}

@Composable
internal fun NovaTileRow(
	title: String,
	tiles: List<NovaBrowseTile>,
	rowState: LazyListState,
	firstFocus: FocusRequester,
	railFocus: FocusRequester,
	/** The tile you came back from, so focus can return to it. */
	returnTile: NovaBrowseTile?,
	returnFocus: FocusRequester,
	onClick: (NovaBrowseTile) -> Unit,
) {
	Column {
		Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
		Spacer(Modifier.height(8.dp))
		LazyRow(
			state = rowState,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			contentPadding = PaddingValues(end = 36.dp),
		) {
			itemsIndexed(tiles, key = { _, t -> t.paths.first() }) { index, tile ->
				NovaTile(
					tile = tile,
					onClick = { onClick(tile) },
					modifier = (
						if (index == 0) Modifier.focusRequester(firstFocus).focusProperties { left = railFocus } else Modifier
						).then(if (tile == returnTile) Modifier.focusRequester(returnFocus) else Modifier),
				)
			}
		}
		Spacer(Modifier.height(16.dp))
	}
}

@Composable
private fun NovaTile(
	tile: NovaBrowseTile,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(nova.corner(10))

	Button(
		onClick = onClick,
		modifier = modifier
			.size(width = 220.dp, height = 112.dp)
			.then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier),
		interactionSource = interactionSource,
		shape = shape,
		colors = ButtonDefaults.colors(
			containerColor = nova.surfaceHigh,
			contentColor = Color.White,
			focusedContainerColor = nova.surfaceRaised,
			focusedContentColor = Color.White,
		),
		contentPadding = PaddingValues(0.dp),
	) {
		Box(Modifier.size(width = 220.dp, height = 112.dp), contentAlignment = Alignment.Center) {
			if (tile.isLogo) {
				if (tile.imageUrl != null) {
					AsyncImage(
						url = tile.imageUrl,
						aspectRatio = 220f / 112f,
						scaleType = ImageView.ScaleType.FIT_CENTER,
						modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
					)
				} else {
					TileName(tile.name)
				}
			} else {
				if (tile.imageUrl != null) {
					AsyncImage(
						url = tile.imageUrl,
						aspectRatio = 220f / 112f,
						scaleType = ImageView.ScaleType.CENTER_CROP,
						modifier = Modifier.fillMaxSize(),
					)
				}
				// Darken the picture so the genre name stands out.
				Box(
					Modifier.fillMaxSize().background(
						Brush.verticalGradient(listOf(Color.Black.withAlpha(0x66), Color.Black.withAlpha(0xBB))),
					),
				)
				TileName(tile.name)
			}
		}
	}
}

@Composable
private fun TileName(name: String) {
	Text(
		name,
		color = Color.White,
		fontSize = 19.sp,
		fontWeight = FontWeight.Black,
		textAlign = TextAlign.Center,
		maxLines = 2,
		overflow = TextOverflow.Ellipsis,
		modifier = Modifier.padding(horizontal = 10.dp),
	)
}

/** Top of a genre / studio / network grid: its logo (or name) and how to get back. */
@Composable
internal fun NovaTileHeader(tile: NovaBrowseTile) {
	Column {
		if (tile.isLogo && tile.imageUrl != null) {
			AsyncImage(
				url = tile.imageUrl,
				aspectRatio = 4f,
				scaleType = ImageView.ScaleType.FIT_START,
				modifier = Modifier.size(width = 240.dp, height = 60.dp),
			)
		} else {
			Text(tile.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
		}
		Spacer(Modifier.height(4.dp))
		Text(
			if (tile.type == NovaSeerr.MediaType.MOVIE) "Movies · press Back to return" else "TV Shows · press Back to return",
			color = nova.textMuted,
			fontSize = 12.sp,
		)
	}
}

package org.jellyfin.androidtv.ui.browsing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.compose.koinInject
import timber.log.Timber
import java.util.UUID

/**
 * Films in the same series (TMDB collection) as a movie, e.g. The Addams Family →
 * The Addams Family 2, via Seerr. Shown on the More Info page.
 */
object NovaSequels {
	data class Part(
		val tmdbId: Int,
		val title: String,
		val year: String,
		val overview: String?,
		val posterUrl: String?,
		val status: NovaSeerr.Status?,
	)

	data class Collection(val name: String, val parts: List<Part>)

	private val cache = HashMap<Int, Collection?>()

	suspend fun forMovie(config: NovaSeerr.Config, tmdbId: Int): Collection? = withContext(Dispatchers.IO) {
		synchronized(cache) { if (cache.containsKey(tmdbId)) return@withContext cache[tmdbId] }
		val result = runCatching {
			with(NovaSeerr) {
				val headers = mapOf("X-Api-Key" to config.apiKey)
				val movie = json.parseToJsonElement(http("${config.baseUrl}/api/v1/movie/$tmdbId", headers = headers)).jsonObject
				val collectionId = (movie["collection"] as? JsonObject)?.int("id") ?: return@runCatching null
				val collection = json.parseToJsonElement(
					http("${config.baseUrl}/api/v1/collection/$collectionId", headers = headers)
				).jsonObject
				val parts = (collection["parts"] as? JsonArray).orEmpty()
					.mapNotNull { it as? JsonObject }
					.mapNotNull { part ->
						val id = part.int("id") ?: return@mapNotNull null
						val title = part.string("title") ?: return@mapNotNull null
						val release = part.string("releaseDate").orEmpty()
						Part(
							tmdbId = id,
							title = title,
							year = release.take(4),
							overview = part.string("overview"),
							posterUrl = part.string("posterPath")?.let { "https://image.tmdb.org/t/p/w342$it" },
							status = runCatching { statusFrom(part["mediaInfo"] as? JsonObject, release) }.getOrNull(),
						)
					}
					// In release order; titles with no date yet go last.
					.sortedWith(compareBy({ it.year.isBlank() }, { it.year }))
				if (parts.size < 2) null
				else Collection(collection.string("name") ?: "More in this series", parts)
			}
		}.onFailure { Timber.w(it, "NOVA: collection lookup failed for %d", tmdbId) }.getOrNull()
		synchronized(cache) { cache[tmdbId] = result }
		result
	}

	/**
	 * The page to open for [part]: the title you have (or its Discover entry) when
	 * Jellyfin knows it, otherwise a Discover-style entry so Download works.
	 */
	suspend fun itemFor(api: ApiClient, config: NovaSeerr.Config, part: Part): BaseItemDto {
		val matches = withContext(Dispatchers.IO) {
			runCatching {
				api.itemsApi.getItems(
					GetItemsRequest(
						searchTerm = part.title,
						includeItemTypes = setOf(BaseItemKind.MOVIE),
						recursive = true,
						fields = setOf(ItemFields.PROVIDER_IDS, ItemFields.PATH, ItemFields.OVERVIEW),
						limit = 30,
					)
				).content.items.filter { NovaSeerr.tmdbId(it) == part.tmdbId }
			}.getOrDefault(emptyList())
		}
		matches.firstOrNull { !NovaSeerr.isDiscoverPlaceholder(config, it) }?.let { return it }
		matches.firstOrNull()?.let { return it }
		// Not on the server at all: a stand-in that lives "in Discover", so the info
		// page offers Download (cast and trailer come from TMDB as usual).
		return BaseItemDto(
			id = UUID.nameUUIDFromBytes("nova-tmdb-movie-${part.tmdbId}".toByteArray()),
			type = BaseItemKind.MOVIE,
			name = part.title,
			overview = part.overview,
			productionYear = part.year.toIntOrNull(),
			providerIds = mapOf("Tmdb" to part.tmdbId.toString()),
			path = "${config.discoverDirectory.trimEnd('/', '\\')}/nova/${part.tmdbId}",
		)
	}
}

/** "More in The Addams Family Collection" row on the More Info page (films only). */
@Composable
fun NovaSequelsRow(item: BaseItemDto, modifier: Modifier = Modifier) {
	val api = koinInject<ApiClient>()
	val navigationRepository = koinInject<NavigationRepository>()
	val scope = rememberCoroutineScope()
	var config by remember(item.id) { mutableStateOf<NovaSeerr.Config?>(null) }
	var collection by remember(item.id) { mutableStateOf<NovaSequels.Collection?>(null) }
	val currentTmdb = NovaSeerr.tmdbId(item)

	LaunchedEffect(item.id, currentTmdb) {
		if (item.type != BaseItemKind.MOVIE) return@LaunchedEffect
		val full = NovaSeerr.fullItem(api, item)
		val tmdb = NovaSeerr.tmdbId(full) ?: NovaSeerr.tmdbId(item) ?: return@LaunchedEffect
		val seerr = NovaSeerr.config(api) ?: return@LaunchedEffect
		config = seerr
		collection = NovaSequels.forMovie(seerr, tmdb)
	}

	val shown = collection ?: return
	val others = shown.parts.filter { it.tmdbId != currentTmdb }
	if (others.isEmpty()) return

	Column(modifier = modifier) {
		Text(shown.name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
		Spacer(Modifier.height(8.dp))
		LazyRow(
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			contentPadding = PaddingValues(end = 24.dp),
			modifier = Modifier.fillMaxWidth(),
		) {
			items(others, key = { it.tmdbId }) { part ->
				SequelCard(part) {
					val seerr = config ?: return@SequelCard
					scope.launch {
						val target = NovaSequels.itemFor(api, seerr, part)
						navigationRepository.navigate(Destinations.novaInfo(target))
					}
				}
			}
		}
	}
}

@Composable
private fun SequelCard(part: NovaSequels.Part, onClick: () -> Unit) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(nova.corner(8))
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = Modifier
			.width(112.dp)
			.height(222.dp)
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
			.then(if (focused) Modifier.background(nova.accentSoft, shape) else Modifier)
			.padding(4.dp),
	) {
		Box(
			modifier = Modifier
				.width(104.dp)
				.height(156.dp)
				.clip(shape)
				.background(nova.surfaceRaised)
				.then(if (focused) Modifier.border(3.dp, nova.accent, shape) else Modifier),
			contentAlignment = Alignment.Center,
		) {
			Text(
				part.title,
				color = nova.textSecondary,
				fontSize = 12.sp,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(6.dp),
			)
			if (part.posterUrl != null) {
				Image(
					painter = rememberAsyncImagePainter(part.posterUrl),
					contentDescription = part.title,
					contentScale = ContentScale.Crop,
					modifier = Modifier.fillMaxSize(),
				)
			}
			val owned = part.status?.state == NovaSeerr.State.DOWNLOADED
			if (owned) {
				Box(
					modifier = Modifier
						.align(Alignment.TopEnd)
						.padding(4.dp)
						.background(NovaStatusColors.Downloaded, RoundedCornerShape(nova.corner(3)))
						.padding(horizontal = 5.dp, vertical = 1.dp),
				) {
					Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
				}
			}
		}
		Spacer(Modifier.height(5.dp))
		Text(
			part.title,
			color = if (focused) Color.White else nova.textPrimary,
			fontSize = 12.sp,
			fontWeight = FontWeight.Bold,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			textAlign = TextAlign.Center,
		)
		if (part.year.isNotBlank()) Text(part.year, color = nova.textMuted, fontSize = 11.sp)
	}
}

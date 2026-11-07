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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.compose.koinInject
import timber.log.Timber
import java.util.UUID

/** A cast member's films and shows (from TMDB, via Seerr), split into owned / not yet. */
object NovaPeople {
	data class Credit(
		val tmdbId: Int,
		val type: NovaSeerr.MediaType,
		val title: String,
		val year: String,
		val overview: String?,
		val posterUrl: String?,
		val status: NovaSeerr.Status,
		val popularity: Double,
	) {
		val owned get() = status.state == NovaSeerr.State.DOWNLOADED || status.state == NovaSeerr.State.PARTLY_DOWNLOADED
	}

	/** The person's TMDB id: given, from Jellyfin's person entry, or by name. */
	suspend fun tmdbIdOf(api: ApiClient, config: NovaSeerr.Config, member: NovaSeerr.CastMember): Int? = withContext(Dispatchers.IO) {
		member.tmdbId?.let { return@withContext it }
		member.jellyfinId?.let { id ->
			runCatching { api.userLibraryApi.getItem(itemId = id).content }.getOrNull()
				?.let { NovaSeerr.tmdbId(it) }
				?.let { return@withContext it }
		}
		runCatching {
			with(NovaSeerr) {
				val q = java.net.URLEncoder.encode(member.name, "UTF-8").replace("+", "%20")
				val body = http("${config.baseUrl}/api/v1/search?query=$q&page=1", headers = mapOf("X-Api-Key" to config.apiKey))
				(json.parseToJsonElement(body).jsonObject["results"] as? JsonArray).orEmpty()
					.mapNotNull { it as? JsonObject }
					.firstOrNull { it.string("mediaType") == "person" && it.string("name").equals(member.name, ignoreCase = true) }
					?.int("id")
			}
		}.getOrNull()
	}

	suspend fun credits(config: NovaSeerr.Config, personId: Int): List<Credit> = withContext(Dispatchers.IO) {
		runCatching {
			with(NovaSeerr) {
				val body = http(
					"${config.baseUrl}/api/v1/person/$personId/combined_credits?language=en",
					headers = mapOf("X-Api-Key" to config.apiKey),
				)
				(json.parseToJsonElement(body).jsonObject["cast"] as? JsonArray).orEmpty()
					.mapNotNull { it as? JsonObject }
					.mapNotNull { obj ->
						val type = when (obj.string("mediaType")) {
							"movie" -> NovaSeerr.MediaType.MOVIE
							"tv" -> NovaSeerr.MediaType.TV
							else -> return@mapNotNull null
						}
						val id = obj.int("id") ?: return@mapNotNull null
						val date = obj.string("releaseDate") ?: obj.string("firstAirDate")
						Credit(
							tmdbId = id,
							type = type,
							title = (obj.string("title") ?: obj.string("name")).orEmpty(),
							year = date?.take(4).orEmpty(),
							overview = obj.string("overview"),
							posterUrl = obj.string("posterPath")?.let { "https://image.tmdb.org/t/p/w342$it" },
							status = statusFrom(obj["mediaInfo"] as? JsonObject, date),
							popularity = (obj["popularity"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
						)
					}
					// Talk shows and award nights clutter the list; skip titles with no poster.
					.filter { it.posterUrl != null && it.title.isNotBlank() }
					.distinctBy { "${it.type}-${it.tmdbId}" }
					.sortedByDescending { it.popularity }
			}
		}.onFailure { Timber.w(it, "NOVA: credits failed for person %d", personId) }.getOrDefault(emptyList())
	}

	/** The page to open: your copy when Jellyfin has it, otherwise a Download page. */
	suspend fun itemFor(api: ApiClient, config: NovaSeerr.Config, credit: Credit): BaseItemDto {
		val kind = if (credit.type == NovaSeerr.MediaType.MOVIE) BaseItemKind.MOVIE else BaseItemKind.SERIES
		val matches = withContext(Dispatchers.IO) {
			runCatching {
				api.itemsApi.getItems(
					GetItemsRequest(
						searchTerm = credit.title,
						includeItemTypes = setOf(kind),
						recursive = true,
						fields = setOf(ItemFields.PROVIDER_IDS, ItemFields.PATH, ItemFields.OVERVIEW),
						limit = 30,
					)
				).content.items.filter { item ->
					NovaSeerr.tmdbId(item) == credit.tmdbId ||
						// TV shows may only carry a TVDB id: fall back to name + year.
						(kind == BaseItemKind.SERIES && item.name.equals(credit.title, ignoreCase = true) &&
							(credit.year.isBlank() || item.productionYear?.toString() == credit.year))
				}
			}.getOrDefault(emptyList())
		}
		matches.firstOrNull { !NovaSeerr.isDiscoverPlaceholder(config, it) }?.let { return it }
		matches.firstOrNull()?.let { return it }
		return BaseItemDto(
			id = UUID.nameUUIDFromBytes("nova-tmdb-${credit.type.apiName}-${credit.tmdbId}".toByteArray()),
			type = kind,
			name = credit.title,
			overview = credit.overview,
			productionYear = credit.year.toIntOrNull(),
			providerIds = mapOf("Tmdb" to credit.tmdbId.toString()),
			path = "${config.discoverDirectory.trimEnd('/', '\\')}/nova/${credit.type.apiName}-${credit.tmdbId}",
		)
	}
}

/** Full-screen NOVA page for a cast member: what you have, and what you can download. */
@Composable
fun NovaPersonDialog(member: NovaSeerr.CastMember, onDismiss: () -> Unit) {
	val api = koinInject<ApiClient>()
	val navigationRepository = koinInject<NavigationRepository>()
	val scope = rememberCoroutineScope()
	var config by remember { mutableStateOf<NovaSeerr.Config?>(null) }
	var credits by remember { mutableStateOf<List<NovaPeople.Credit>?>(null) }
	val firstFocus = remember { FocusRequester() }

	LaunchedEffect(member) {
		val cfg = NovaSeerr.config(api)
		config = cfg
		credits = if (cfg == null) emptyList() else {
			NovaPeople.tmdbIdOf(api, cfg, member)?.let { NovaPeople.credits(cfg, it) }.orEmpty()
		}
		delay(80L)
		runCatching { firstFocus.requestFocus() }
	}

	fun open(credit: NovaPeople.Credit) {
		val cfg = config ?: return
		scope.launch {
			val target = NovaPeople.itemFor(api, cfg, credit)
			onDismiss()
			navigationRepository.navigate(Destinations.novaInfo(target))
		}
	}

	Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
		Box(modifier = Modifier.fillMaxSize().background(nova.bg)) {
			LazyColumn(contentPadding = PaddingValues(start = 48.dp, top = 32.dp, bottom = 32.dp)) {
				item {
					Row(verticalAlignment = Alignment.CenterVertically) {
						Box(
							contentAlignment = Alignment.Center,
							modifier = Modifier.size(84.dp).clip(CircleShape).background(nova.surfaceRaised),
						) {
							if (member.imageUrl != null) {
								Image(
									painter = rememberAsyncImagePainter(member.imageUrl),
									contentDescription = member.name,
									contentScale = ContentScale.Crop,
									modifier = Modifier.size(84.dp).clip(CircleShape),
								)
							}
						}
						Spacer(Modifier.width(18.dp))
						Column {
							Text(member.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
							if (member.role.isNotBlank()) Text("as ${member.role}", color = nova.textMuted, fontSize = 14.sp)
						}
					}
					Spacer(Modifier.height(20.dp))
				}
				val list = credits
				when {
					list == null -> item { Text("Loading their films and shows…", color = nova.textMuted, fontSize = 14.sp) }
					list.isEmpty() -> item { Text("Couldn't find other films or shows for ${member.name}.", color = nova.textMuted, fontSize = 14.sp) }
					else -> {
						val owned = list.filter { it.owned }
						val rest = list.filterNot { it.owned }
						var focusGiven = false
						fun focusFor(index: Int): FocusRequester? =
							if (!focusGiven && index == 0) { focusGiven = true; firstFocus } else null
						if (owned.isNotEmpty()) item {
							CreditRow("In your library", owned, focusFor(0), ::open)
						}
						val films = rest.filter { it.type == NovaSeerr.MediaType.MOVIE }
						val shows = rest.filter { it.type == NovaSeerr.MediaType.TV }
						if (films.isNotEmpty()) item { CreditRow("Films you can download", films, focusFor(0), ::open) }
						if (shows.isNotEmpty()) item { CreditRow("Shows you can download", shows, focusFor(0), ::open) }
					}
				}
			}
		}
	}
}

@Composable
private fun CreditRow(
	title: String,
	credits: List<NovaPeople.Credit>,
	firstFocus: FocusRequester?,
	onClick: (NovaPeople.Credit) -> Unit,
) {
	Column {
		Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
		Spacer(Modifier.height(8.dp))
		LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 48.dp)) {
			itemsIndexed(credits, key = { _, c -> "${c.type}-${c.tmdbId}" }) { index, credit ->
				CreditCard(
					credit,
					modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
					onClick = { onClick(credit) },
				)
			}
		}
		Spacer(Modifier.height(18.dp))
	}
}

@Composable
private fun CreditCard(credit: NovaPeople.Credit, modifier: Modifier, onClick: () -> Unit) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(nova.corner(8))
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = modifier
			.width(116.dp)
			.height(226.dp)
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
	) {
		Box(
			modifier = Modifier
				.width(112.dp)
				.height(168.dp)
				.clip(shape)
				.background(nova.surfaceRaised)
				.then(if (focused) Modifier.border(3.dp, nova.accent, shape) else Modifier),
			contentAlignment = Alignment.Center,
		) {
			Text(credit.title, color = nova.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(6.dp))
			if (credit.posterUrl != null) {
				Image(
					painter = rememberAsyncImagePainter(credit.posterUrl),
					contentDescription = credit.title,
					contentScale = ContentScale.Crop,
					modifier = Modifier.fillMaxSize(),
				)
			}
			Box(
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(5.dp)
					.background(if (credit.type == NovaSeerr.MediaType.MOVIE) nova.movieTag else nova.tvTag, RoundedCornerShape(nova.corner(3)))
					.padding(horizontal = 5.dp, vertical = 1.dp),
			) {
				Text(if (credit.type == NovaSeerr.MediaType.MOVIE) "MOVIE" else "TV", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
			}
		}
		Spacer(Modifier.height(5.dp))
		Text(
			credit.title,
			color = if (focused) Color.White else nova.textPrimary,
			fontSize = 12.sp,
			fontWeight = FontWeight.Bold,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			textAlign = TextAlign.Center,
			modifier = Modifier.fillMaxWidth(),
		)
		if (credit.year.isNotBlank()) Text(credit.year, color = nova.textMuted, fontSize = 11.sp)
	}
}

package org.jellyfin.androidtv.ui.browsing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Talks to Seerr (Jellyseerr/Overseerr) so NOVA can request downloads and show
 * their progress. The Seerr address and API key are read from the JellyBridge
 * plugin configuration on the Jellyfin server, which only admin accounts can
 * read. When that fails, callers fall back to the plain Favourite button and
 * JellyBridge's own favourite-to-request sync.
 */
object NovaSeerr {
	private const val JELLYBRIDGE_PLUGIN_ID = "8ecc808c-d6e9-432f-9219-b638fbfb37e6"
	private const val RETRY_FAILED_CONFIG_MS = 60_000L

	data class Config(
		val baseUrl: String,
		val apiKey: String,
		val discoverDirectory: String,
	)

	enum class State {
		NOT_REQUESTED,
		PENDING_APPROVAL,
		UNRELEASED,
		SEARCHING,
		DOWNLOADING,
		PARTLY_DOWNLOADED,
		DOWNLOADED,
	}

	data class Status(
		val state: State,
		/** 0f..1f while downloading, otherwise null. */
		val progress: Float? = null,
	)

	enum class MediaType(val apiName: String) { MOVIE("movie"), TV("tv") }

	data class SearchResult(
		val tmdbId: Int,
		val type: MediaType,
		val title: String,
		val year: String,
		val posterUrl: String?,
		val status: Status,
	)

	internal val json = Json { ignoreUnknownKeys = true }
	private val configLock = Any()
	@Volatile private var cachedConfig: Config? = null

	/** The Seerr settings if they've been loaded already (never waits on the network). */
	fun loadedConfig(): Config? = cachedConfig
	@Volatile private var cachedForServer: String? = null
	@Volatile private var lastConfigFailure = 0L
	private val fullItemCache = object : LinkedHashMap<String, BaseItemDto>(64, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BaseItemDto>?) = size > 200
	}

	/** Seerr connection details, or null when unavailable (not admin, no JellyBridge, offline). */
	suspend fun config(api: ApiClient): Config? {
		val server = api.baseUrl?.trimEnd('/') ?: return null
		val token = api.accessToken ?: return null
		synchronized(configLock) {
			if (cachedForServer == server) cachedConfig?.let { return it }
			if (cachedForServer == server && System.currentTimeMillis() - lastConfigFailure < RETRY_FAILED_CONFIG_MS) return null
		}

		val loaded = withContext(Dispatchers.IO) {
			runCatching {
				val body = http(
					url = "$server/Plugins/$JELLYBRIDGE_PLUGIN_ID/Configuration",
					headers = mapOf("Authorization" to "MediaBrowser Token=\"$token\""),
				)
				val obj = json.parseToJsonElement(body).jsonObject
				val url = obj.stringIgnoreCase("JellyseerrUrl")?.trim()?.trimEnd('/')
				val key = obj.stringIgnoreCase("ApiKey")?.trim()
				val dir = obj.stringIgnoreCase("LibraryDirectory")?.trim().orEmpty()
				if (url.isNullOrBlank() || key.isNullOrBlank()) null
				else Config(reachableFromTv(url, server), key, dir.trimEnd('/', '\\'))
			}.onFailure { Timber.w(it, "NOVA: could not read JellyBridge configuration") }.getOrNull()
		}

		synchronized(configLock) {
			cachedForServer = server
			cachedConfig = loaded
			if (loaded == null) lastConfigFailure = System.currentTimeMillis()
		}
		return loaded
	}

	/**
	 * JellyBridge may be configured with "localhost" because it runs on the server.
	 * From the Fire TV that would point at the TV itself, so swap in the server's host.
	 */
	private fun reachableFromTv(seerrUrl: String, jellyfinUrl: String): String = runCatching {
		val seerr = URI(seerrUrl)
		val host = seerr.host?.lowercase()
		if (host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0") {
			val jellyfinHost = URI(jellyfinUrl).host
			URI(seerr.scheme, seerr.userInfo, jellyfinHost, seerr.port, seerr.path, null, null).toString().trimEnd('/')
		} else seerrUrl
	}.getOrDefault(seerrUrl)

	/** Full item (provider ids + path), cached; browse rows omit provider ids. */
	suspend fun fullItem(api: ApiClient, item: BaseItemDto): BaseItemDto {
		val key = item.id.toString()
		synchronized(fullItemCache) { fullItemCache[key]?.let { return it } }
		val loaded = withContext(Dispatchers.IO) {
			runCatching {
				api.userLibraryApi.getItem(itemId = item.id).content
			}.getOrNull()
		} ?: return item
		synchronized(fullItemCache) { fullItemCache[key] = loaded }
		return loaded
	}

	fun tmdbId(item: BaseItemDto): Int? = item.providerIds
		?.entries
		?.firstOrNull { it.key.equals("Tmdb", ignoreCase = true) }
		?.value
		?.toIntOrNull()

	fun mediaType(item: BaseItemDto): MediaType? = when (item.type) {
		BaseItemKind.MOVIE -> MediaType.MOVIE
		BaseItemKind.SERIES -> MediaType.TV
		else -> null
	}

	/** True when the item is a JellyBridge "Discover" placeholder rather than real media. */
	fun isDiscoverPlaceholder(config: Config, item: BaseItemDto): Boolean {
		val dir = config.discoverDirectory
		val path = item.path ?: return false
		if (dir.isBlank()) return false
		val normalisedPath = path.replace('\\', '/')
		val normalisedDir = dir.replace('\\', '/')
		return normalisedPath.startsWith("$normalisedDir/", ignoreCase = true)
	}

	suspend fun status(config: Config, type: MediaType, tmdbId: Int): Status? = withContext(Dispatchers.IO) {
		runCatching {
			val body = http(
				url = "${config.baseUrl}/api/v1/${type.apiName}/$tmdbId",
				headers = mapOf("X-Api-Key" to config.apiKey),
			)
			val obj = json.parseToJsonElement(body).jsonObject
			val releaseDate = obj.string("releaseDate") ?: obj.string("firstAirDate")
			val fromSeerr = statusFrom(obj["mediaInfo"] as? JsonObject, releaseDate)
			// A copy picked by hand ("Choose a copy") goes straight to Radarr, so Seerr
			// may not know about it yet; ask Radarr in that case.
			if (fromSeerr.state == State.NOT_REQUESTED && type == MediaType.MOVIE) {
				val radarr = NovaArr.services(config).radarr
				radarr?.let { NovaArr.radarrMovieStatus(it, tmdbId) } ?: fromSeerr
			} else fromSeerr
		}.onFailure { Timber.w(it, "NOVA: Seerr status failed for %s %d", type, tmdbId) }.getOrNull()
	}

	data class CastMember(
		val name: String,
		val role: String,
		val imageUrl: String?,
		/** TMDB person id when known (cast from TMDB). */
		val tmdbId: Int? = null,
		/** Jellyfin person id (cast from Jellyfin), used to find the TMDB id. */
		val jellyfinId: java.util.UUID? = null,
	)

	/** Cast from TMDB via Seerr, used when Jellyfin has no people for a title. */
	suspend fun cast(config: Config, type: MediaType, tmdbId: Int): List<CastMember> = withContext(Dispatchers.IO) {
		runCatching {
			val body = http(
				url = "${config.baseUrl}/api/v1/${type.apiName}/$tmdbId",
				headers = mapOf("X-Api-Key" to config.apiKey),
			)
			val credits = json.parseToJsonElement(body).jsonObject["credits"] as? JsonObject
			(credits?.get("cast") as? JsonArray).orEmpty()
				.mapNotNull { it as? JsonObject }
				.mapNotNull { person ->
					val name = person.string("name") ?: return@mapNotNull null
					CastMember(
						name = name,
						role = person.string("character").orEmpty(),
						imageUrl = person.string("profilePath")?.let { "https://image.tmdb.org/t/p/w185$it" },
						tmdbId = person.int("id"),
					)
				}
				.take(20)
		}.onFailure { Timber.w(it, "NOVA: Seerr cast failed for %s %d", type, tmdbId) }.getOrDefault(emptyList())
	}

	private val trailerCache = HashMap<String, String?>()

	/** YouTube video id of the title's trailer (from TMDB via Seerr), if it has one. */
	suspend fun trailerKey(config: Config, type: MediaType, tmdbId: Int): String? {
		val cacheKey = "${type.apiName}/$tmdbId"
		synchronized(trailerCache) { if (trailerCache.containsKey(cacheKey)) return trailerCache[cacheKey] }
		val key = withContext(Dispatchers.IO) {
			runCatching {
				val body = http(
					url = "${config.baseUrl}/api/v1/${type.apiName}/$tmdbId",
					headers = mapOf("X-Api-Key" to config.apiKey),
				)
				trailerFrom(json.parseToJsonElement(body).jsonObject)
			}.onFailure { Timber.w(it, "NOVA: Seerr trailer failed for %s %d", type, tmdbId) }.getOrNull()
		}
		rememberTrailer(type, tmdbId, key)
		return key
	}

	/** The trailer from a title's details (so one request gives both). */
	fun trailerFrom(details: JsonObject): String? {
		val videos = (details["relatedVideos"] as? JsonArray).orEmpty()
			.mapNotNull { it as? JsonObject }
			.filter { it.string("site").equals("YouTube", ignoreCase = true) && !it.string("key").isNullOrBlank() }
		return (videos.firstOrNull { it.string("type") == "Trailer" }
			?: videos.firstOrNull { it.string("type") == "Teaser" }
			?: videos.firstOrNull())?.string("key")
	}

	/** Saves a trailer found elsewhere, so [trailerKey] doesn't ask Seerr again. */
	fun rememberTrailer(type: MediaType, tmdbId: Int, key: String?) {
		synchronized(trailerCache) { trailerCache["${type.apiName}/$tmdbId"] = key }
	}

	private val trailerListCache = HashMap<String, List<String>>()

	/**
	 * Every YouTube video for a title, best first (trailers, then teasers, then
	 * the rest). Some uploaders block playing inside apps, so the preview tries
	 * the next one when a video won't play.
	 */
	fun trailersFrom(details: JsonObject): List<String> {
		val videos = (details["relatedVideos"] as? JsonArray).orEmpty()
			.mapNotNull { it as? JsonObject }
			.filter { it.string("site").equals("YouTube", ignoreCase = true) && !it.string("key").isNullOrBlank() }
		val rank = { video: JsonObject ->
			when (video.string("type")) {
				"Trailer" -> 0
				"Teaser" -> 1
				"Clip" -> 2
				else -> 3
			}
		}
		return videos.sortedBy(rank).mapNotNull { it.string("key") }.distinct().take(6)
	}

	fun rememberTrailers(type: MediaType, tmdbId: Int, keys: List<String>) {
		synchronized(trailerListCache) { trailerListCache["${type.apiName}/$tmdbId"] = keys }
	}

	/** All of a title's YouTube videos, best first (from the cache, or asked of Seerr). */
	suspend fun trailerKeys(config: Config, type: MediaType, tmdbId: Int): List<String> {
		val cacheKey = "${type.apiName}/$tmdbId"
		synchronized(trailerListCache) { trailerListCache[cacheKey]?.let { return it } }
		val keys = withContext(Dispatchers.IO) {
			runCatching {
				val body = http(
					url = "${config.baseUrl}/api/v1/${type.apiName}/$tmdbId",
					headers = mapOf("X-Api-Key" to config.apiKey),
				)
				trailersFrom(json.parseToJsonElement(body).jsonObject)
			}.onFailure { Timber.w(it, "NOVA: Seerr trailers failed for %s %d", type, tmdbId) }.getOrNull()
		} ?: return emptyList()
		rememberTrailers(type, tmdbId, keys)
		return keys
	}

	/** YouTube id from a Jellyfin remote trailer URL (watch?v=, youtu.be/, embed/). */
	fun youTubeId(url: String?): String? {
		if (url.isNullOrBlank()) return null
		val match = Regex("""(?:v=|youtu\.be/|embed/|shorts/)([A-Za-z0-9_-]{11})""").find(url)
		return match?.groupValues?.get(1)
	}

	data class SeasonInfo(
		val number: Int,
		val name: String,
		val episodeCount: Int,
		val status: State,
	)

	/** Seasons of a show (specials excluded) with what you already have or have asked for. */
	suspend fun seasons(config: Config, tmdbId: Int): List<SeasonInfo> = withContext(Dispatchers.IO) {
		runCatching {
			val body = http(
				url = "${config.baseUrl}/api/v1/tv/$tmdbId",
				headers = mapOf("X-Api-Key" to config.apiKey),
			)
			val obj = json.parseToJsonElement(body).jsonObject
			val mediaInfo = obj["mediaInfo"] as? JsonObject
			val seasonStatus = (mediaInfo?.get("seasons") as? JsonArray).orEmpty()
				.mapNotNull { it as? JsonObject }
				.mapNotNull { s -> s.int("seasonNumber")?.let { it to (s.int("status") ?: 1) } }
				.toMap()
			// Seasons already requested but not yet available count as "Searching".
			val requested = (mediaInfo?.get("requests") as? JsonArray).orEmpty()
				.mapNotNull { it as? JsonObject }
				.flatMap { r -> (r["seasons"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.int("seasonNumber") } }
				.toSet()
			(obj["seasons"] as? JsonArray).orEmpty()
				.mapNotNull { it as? JsonObject }
				.mapNotNull { season ->
					val number = season.int("seasonNumber") ?: return@mapNotNull null
					if (number <= 0) return@mapNotNull null
					val state = when (seasonStatus[number]) {
						5 -> State.DOWNLOADED
						4 -> State.PARTLY_DOWNLOADED
						3 -> State.SEARCHING
						2 -> State.PENDING_APPROVAL
						else -> if (number in requested) State.SEARCHING else State.NOT_REQUESTED
					}
					SeasonInfo(
						number = number,
						name = season.string("name")?.takeIf { it.isNotBlank() } ?: "Season $number",
						episodeCount = season.int("episodeCount") ?: 0,
						status = state,
					)
				}
				.sortedBy { it.number }
		}.onFailure { Timber.w(it, "NOVA: Seerr seasons failed for %d", tmdbId) }.getOrDefault(emptyList())
	}

	/** Asks Seerr for the normal (non-4K) version; whole show for TV. */
	/**
	 * Requests a title. For TV, [seasons] limits it to those season numbers;
	 * null means every season (Seerr skips seasons you already have).
	 */
	suspend fun request(
		config: Config,
		type: MediaType,
		tmdbId: Int,
		seasons: List<Int>? = null,
	): Boolean = withContext(Dispatchers.IO) {
		runCatching {
			// Settings → "Indian films": Indian-language films go to the Indian Movies folder.
			val indianFolder = if (type == MediaType.MOVIE) indianMoviesFolderFor(config, tmdbId) else null
			val payload = buildJsonObject {
				put("mediaType", type.apiName)
				put("mediaId", tmdbId)
				put("is4k", false)
				if (indianFolder != null) {
					put("serverId", indianFolder.first)
					put("rootFolder", indianFolder.second)
				}
				if (type == MediaType.TV) {
					if (seasons.isNullOrEmpty()) put("seasons", "all")
					else putJsonArray("seasons") { seasons.forEach { add(it) } }
				}
			}
			http(
				url = "${config.baseUrl}/api/v1/request",
				method = "POST",
				headers = mapOf("X-Api-Key" to config.apiKey),
				body = payload.toString(),
			)
			true
		}.onFailure { Timber.w(it, "NOVA: Seerr request failed for %s %d", type, tmdbId) }.getOrDefault(false)
	}

	/** True when [item] is a Discover (JellyBridge) placeholder rather than a title you have. */
	suspend fun isDiscoverItem(api: ApiClient, item: BaseItemDto): Boolean {
		val seerr = runCatching { config(api) }.getOrNull() ?: return false
		val full = runCatching { fullItem(api, item) }.getOrNull() ?: return false
		return isDiscoverPlaceholder(seerr, full)
	}

	/**
	 * Drops Discover (JellyBridge) placeholders, keeping only titles you really
	 * have. The items must be loaded with the PATH field.
	 */
	suspend fun withoutDiscover(api: ApiClient, items: List<BaseItemDto>): List<BaseItemDto> {
		val seerr = runCatching { config(api) }.getOrNull()
		return items.filter { item ->
			if (seerr != null && isDiscoverPlaceholder(seerr, item)) return@filter false
			val path = item.path?.lowercase() ?: return@filter true
			"jellybridge" !in path && "/discover/" !in path
		}
	}

	/** TMDB original-language codes that count as Indian films. */
	private val indianLanguages = setOf("hi", "pa", "ta", "te", "ml", "kn", "mr", "gu", "bn", "or")

	@Volatile private var indianFolderCache: Pair<Int, String>? = null
	@Volatile private var indianFolderCheckedAt = 0L

	/**
	 * Radarr server id and "Indian Movies" root folder for this film, or null when
	 * it isn't an Indian-language film (or there is no such folder in Radarr).
	 * The language comes from TMDB via Seerr, e.g. "hi" for Hindi. Only with Settings → "Indian films" on.
	 */
	internal fun indianMoviesFolderFor(config: Config, tmdbId: Int): Pair<Int, String>? = runCatching {
		// Only when switched on in Settings → "Indian films".
		if (!NovaIndianFilms.isEnabled()) return@runCatching null
		val headers = mapOf("X-Api-Key" to config.apiKey)
		val movie = json.parseToJsonElement(http("${config.baseUrl}/api/v1/movie/$tmdbId", headers = headers)).jsonObject
		val language = movie.string("originalLanguage")?.lowercase()
		if (language == null || language !in indianLanguages) return@runCatching null

		val now = System.currentTimeMillis()
		indianFolderCache?.takeIf { now - indianFolderCheckedAt < 10 * 60_000L }?.let { return@runCatching it }

		val servers = (json.parseToJsonElement(http("${config.baseUrl}/api/v1/service/radarr", headers = headers)) as? JsonArray)
			.orEmpty().mapNotNull { it as? JsonObject }
			.filter { (it["is4k"] as? JsonPrimitive)?.booleanOrNull != true }
		val server = servers.firstOrNull { (it["isDefault"] as? JsonPrimitive)?.booleanOrNull == true } ?: servers.firstOrNull()
		val serverId = server?.int("id") ?: return@runCatching null
		val details = json.parseToJsonElement(http("${config.baseUrl}/api/v1/service/radarr/$serverId", headers = headers)).jsonObject
		val folder = (details["rootFolders"] as? JsonArray).orEmpty()
			.mapNotNull { (it as? JsonObject)?.string("path") }
			.firstOrNull { it.contains("indian", ignoreCase = true) }
			?: return@runCatching null
		(serverId to folder).also {
			indianFolderCache = it
			indianFolderCheckedAt = now
		}
	}.onFailure { Timber.w(it, "NOVA: couldn't work out the Indian Movies folder for %d", tmdbId) }.getOrNull()

	/**
	 * Movies and shows matching [query]. Seerr only returns 20 results per page
	 * (movies, shows and people mixed), so the first [pages] pages are fetched at
	 * the same time and merged.
	 */
	suspend fun search(config: Config, query: String, pages: Int = 5): List<SearchResult> = withContext(Dispatchers.IO) {
		val trimmed = query.trim()
		if (trimmed.isEmpty()) return@withContext emptyList()
		// Seerr rejects '+' for spaces, so encode spaces as %20.
		val encoded = URLEncoder.encode(trimmed, "UTF-8").replace("+", "%20")

		fun page(number: Int): Pair<List<SearchResult>, Int> = runCatching {
			val body = http(
				url = "${config.baseUrl}/api/v1/search?query=$encoded&page=$number&language=en",
				headers = mapOf("X-Api-Key" to config.apiKey),
			)
			val root = json.parseToJsonElement(body).jsonObject
			val totalPages = root.int("totalPages") ?: 1
			val results = (root["results"] as? JsonArray).orEmpty().mapNotNull { element ->
				val obj = element as? JsonObject ?: return@mapNotNull null
				val type = when (obj.string("mediaType")) {
					"movie" -> MediaType.MOVIE
					"tv" -> MediaType.TV
					else -> return@mapNotNull null
				}
				val id = obj.int("id") ?: return@mapNotNull null
				val title = (obj.string("title") ?: obj.string("name")).orEmpty()
				val date = obj.string("releaseDate") ?: obj.string("firstAirDate")
				SearchResult(
					tmdbId = id,
					type = type,
					title = title,
					year = date?.take(4).orEmpty(),
					posterUrl = obj.string("posterPath")?.let { "https://image.tmdb.org/t/p/w342$it" },
					status = statusFrom(obj["mediaInfo"] as? JsonObject, date),
				)
			}
			results to totalPages
		}.onFailure { Timber.w(it, "NOVA: Seerr search page %d failed", number) }.getOrDefault(emptyList<SearchResult>() to 0)

		val (first, totalPages) = page(1)
		val more = (2..minOf(pages, totalPages)).map { number -> async { page(number).first } }.awaitAll()
		(first + more.flatten()).distinctBy { "${it.type}/${it.tmdbId}" }
	}

	/**
	 * A Seerr browse list (e.g. "discover/trending", "discover/movies") for the
	 * Download page; [fallback] is the type when the list doesn't say.
	 */
	suspend fun browse(config: Config, path: String, fallback: MediaType?, page: Int = 1): List<SearchResult> = withContext(Dispatchers.IO) {
		runCatching {
			val sep = if (path.contains('?')) '&' else '?'
			val body = http(
				url = "${config.baseUrl}/api/v1/$path${sep}page=$page&language=en",
				headers = mapOf("X-Api-Key" to config.apiKey),
			)
			(json.parseToJsonElement(body).jsonObject["results"] as? JsonArray).orEmpty().mapNotNull { element ->
				val obj = element as? JsonObject ?: return@mapNotNull null
				val type = when (obj.string("mediaType")) {
					"movie" -> MediaType.MOVIE
					"tv" -> MediaType.TV
					null -> fallback ?: return@mapNotNull null
					else -> return@mapNotNull null
				}
				val id = obj.int("id") ?: return@mapNotNull null
				val date = obj.string("releaseDate") ?: obj.string("firstAirDate")
				SearchResult(
					tmdbId = id,
					type = type,
					title = (obj.string("title") ?: obj.string("name")).orEmpty(),
					year = date?.take(4).orEmpty(),
					posterUrl = obj.string("posterPath")?.let { "https://image.tmdb.org/t/p/w342$it" } ?: return@mapNotNull null,
					status = statusFrom(obj["mediaInfo"] as? JsonObject, date),
				)
			}
		}.onFailure { Timber.w(it, "NOVA: Seerr browse %s failed", path) }.getOrDefault(emptyList())
	}

	internal fun statusFrom(mediaInfo: JsonObject?, releaseDate: String?): Status {
		val unreleased = releaseDate
			?.takeIf { it.length >= 10 }
			?.let { runCatching { LocalDate.parse(it.take(10)).isAfter(LocalDate.now()) }.getOrNull() }
			?: false

		if (mediaInfo == null) return Status(State.NOT_REQUESTED)

		// Seerr download tracker: one entry per active Radarr/Sonarr queue item.
		val downloads = (mediaInfo["downloadStatus"] as? JsonArray).orEmpty()
			.mapNotNull { it as? JsonObject }
		if (downloads.isNotEmpty()) {
			val size = downloads.sumOf { it.double("size") ?: 0.0 }
			val left = downloads.sumOf { it.double("sizeLeft") ?: 0.0 }
			val progress = if (size > 0) (1.0 - left / size).toFloat().coerceIn(0f, 1f) else 0f
			return Status(State.DOWNLOADING, progress)
		}

		// MediaStatus: 1 unknown, 2 pending, 3 processing, 4 partially available,
		// 5 available, 6 blocklisted, 7 deleted.
		return when (mediaInfo.int("status")) {
			2 -> Status(State.PENDING_APPROVAL)
			3 -> Status(if (unreleased) State.UNRELEASED else State.SEARCHING)
			4 -> Status(State.PARTLY_DOWNLOADED)
			5 -> Status(State.DOWNLOADED)
			else -> Status(State.NOT_REQUESTED)
		}
	}

	internal fun http(
		url: String,
		method: String = "GET",
		headers: Map<String, String> = emptyMap(),
		body: String? = null,
		contentType: String = "application/json",
		/** Indexer searches through Radarr/Sonarr can take a minute. */
		readTimeoutMs: Int = 15_000,
	): String {
		val connection = URL(url).openConnection() as HttpURLConnection
		try {
			connection.requestMethod = method
			connection.connectTimeout = 8_000
			connection.readTimeout = readTimeoutMs
			connection.setRequestProperty("Accept", "application/json")
			headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
			if (body != null) {
				connection.doOutput = true
				connection.setRequestProperty("Content-Type", contentType)
				connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
			}
			val code = connection.responseCode
			val stream = if (code in 200..299) connection.inputStream else connection.errorStream
			val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
			if (code !in 200..299) error("HTTP $code from $method $url: ${text.take(2000)}")
			return text
		} finally {
			connection.disconnect()
		}
	}

	internal fun JsonObject.string(key: String): String? =
		(this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

	internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

	internal fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

	private fun JsonObject.stringIgnoreCase(key: String): String? =
		entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
			?.value
			?.let { it as? JsonPrimitive }
			?.takeUnless { it is JsonNull }
			?.contentOrNull
}

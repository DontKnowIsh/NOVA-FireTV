package org.jellyfin.androidtv.ui.browsing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import timber.log.Timber
import java.net.URI

/**
 * Radarr, Sonarr and qBittorrent access for the Downloads page.
 *
 * Their addresses and API keys come from Seerr's own settings (Seerr already
 * knows them). Docker-internal host names such as "radarr" are not reachable
 * from the TV, so those are swapped for the server's address.
 */
object NovaArr {
	enum class Kind { MOVIE, TV }

	data class Service(
		val baseUrl: String,
		val apiKey: String,
		/** Quality profile, root folder (and Sonarr v3 language profile) Seerr uses for requests. */
		val profileId: Int? = null,
		val rootFolder: String? = null,
		val languageProfileId: Int? = null,
	)

	data class Services(
		val radarr: Service?,
		val sonarr: Service?,
		val qbittorrentUrl: String?,
	)

	/** Live numbers from qBittorrent. */
	data class TorrentStats(
		val downloadSpeed: Long,
		val seeds: Int,
		val peers: Int,
		/** Seconds, or null when unknown / infinite. */
		val etaSeconds: Long?,
		/** qBittorrent's own state, e.g. "metaDL", "stalledDL", "queuedDL", "downloading". */
		val state: String? = null,
		/** Seeds / peers in the whole swarm (qBittorrent's number in brackets). */
		val seedsTotal: Int = 0,
		val peersTotal: Int = 0,
	)

	data class Download(
		/** Stable key: the torrent hash, or the queue id when there is none. */
		val key: String,
		val kind: Kind,
		val queueIds: List<Int>,
		val episodeIds: List<Int>,
		/** Radarr movie id or Sonarr series id. */
		val libraryId: Int,
		val tmdbId: Int?,
		val title: String,
		val subtitle: String,
		val posterUrl: String?,
		val size: Double,
		val sizeLeft: Double,
		val timeLeft: String?,
		val status: String,
		val trackedState: String?,
		val message: String?,
		val downloadId: String?,
		val stats: TorrentStats? = null,
	) {
		val progress: Float get() = if (size > 0) (1.0 - sizeLeft / size).toFloat().coerceIn(0f, 1f) else 0f
		val isPaused: Boolean get() = status.equals("paused", ignoreCase = true)
	}

	@Volatile private var cached: Pair<String, Services>? = null
	@Volatile private var cachedAt = 0L

	suspend fun services(seerr: NovaSeerr.Config): Services = withContext(Dispatchers.IO) {
		val existing = cached
		if (existing != null && existing.first == seerr.baseUrl && System.currentTimeMillis() - cachedAt < 5 * 60_000L) {
			return@withContext existing.second
		}
		val seerrHost = runCatching { URI(seerr.baseUrl).host }.getOrNull()
		val radarr = arrFromSeerr(seerr, "radarr", seerrHost)
		val sonarr = arrFromSeerr(seerr, "sonarr", seerrHost)
		val qbit = radarr?.let { qbittorrentFrom(it, seerrHost) } ?: sonarr?.let { qbittorrentFrom(it, seerrHost) }
		val services = Services(radarr, sonarr, qbit)
		cached = seerr.baseUrl to services
		cachedAt = System.currentTimeMillis()
		services
	}

	private fun arrFromSeerr(seerr: NovaSeerr.Config, name: String, seerrHost: String?): Service? = runCatching {
		val body = NovaSeerr.http(
			url = "${seerr.baseUrl}/api/v1/settings/$name",
			headers = mapOf("X-Api-Key" to seerr.apiKey),
		)
		val servers = (NovaSeerr.json.parseToJsonElement(body) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
		val server = servers.firstOrNull { it.bool("is4k") != true && it.bool("isDefault") == true }
			?: servers.firstOrNull { it.bool("is4k") != true }
			?: return@runCatching null
		val host = reachableHost(server.str("hostname"), seerrHost) ?: return@runCatching null
		val port = server.integer("port") ?: return@runCatching null
		val ssl = server.bool("useSsl") == true
		val base = server.str("baseUrl").orEmpty().trim().trimEnd('/').let { if (it.isNotEmpty() && !it.startsWith("/")) "/$it" else it }
		val key = server.str("apiKey") ?: return@runCatching null
		Service(
			baseUrl = "${if (ssl) "https" else "http"}://$host:$port$base",
			apiKey = key,
			profileId = server.integer("activeProfileId"),
			rootFolder = server.str("activeDirectory"),
			languageProfileId = server.integer("activeLanguageProfileId"),
		)
	}.onFailure { Timber.w(it, "NOVA: could not read %s settings from Seerr", name) }.getOrNull()

	private fun qbittorrentFrom(service: Service, seerrHost: String?): String? = runCatching {
		val body = NovaSeerr.http("${service.baseUrl}/api/v3/downloadclient", headers = mapOf("X-Api-Key" to service.apiKey))
		val clients = (NovaSeerr.json.parseToJsonElement(body) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
		val client = clients.firstOrNull {
			it.str("implementation").equals("QBittorrent", ignoreCase = true) && it.bool("enable") != false
		} ?: return@runCatching null
		val fields = (client["fields"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
		fun field(name: String) = fields.firstOrNull { it.str("name").equals(name, ignoreCase = true) }?.get("value") as? JsonPrimitive
		val host = reachableHost(field("host")?.contentOrNull, seerrHost) ?: return@runCatching null
		val port = field("port")?.intOrNull ?: 8080
		val ssl = field("useSsl")?.booleanOrNull == true
		"${if (ssl) "https" else "http"}://$host:$port"
	}.getOrNull()

	/** "radarr", "localhost" etc. only exist inside the server, so use the server's address. */
	private fun reachableHost(host: String?, seerrHost: String?): String? {
		val h = host?.trim()?.takeIf { it.isNotEmpty() } ?: return seerrHost
		val isIp = h.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))
		val internal = h.equals("localhost", true) || h == "127.0.0.1" || h == "0.0.0.0" || (!isIp && !h.contains('.'))
		return if (internal) seerrHost ?: h else h
	}

	suspend fun downloads(services: Services): List<Download> = withContext(Dispatchers.IO) {
		val movies = services.radarr?.let { radarrQueue(it) }.orEmpty()
		val shows = services.sonarr?.let { sonarrQueue(it) }.orEmpty()
		val all = movies + shows
		val stats = services.qbittorrentUrl?.let { torrentStats(it, all.mapNotNull { d -> d.downloadId }) }.orEmpty()
		all.map { d -> d.downloadId?.lowercase()?.let { stats[it] }?.let { d.copy(stats = it) } ?: d }
	}

	/** Speed, seeds, peers and ETA per torrent hash (lower-case). Empty if qBittorrent can't be reached. */
	private fun torrentStats(qbit: String, hashes: List<String>): Map<String, TorrentStats> {
		if (hashes.isEmpty()) return emptyMap()
		return runCatching {
			val body = NovaSeerr.http("$qbit/api/v2/torrents/info?hashes=${hashes.joinToString("|") { it.lowercase() }}")
			(NovaSeerr.json.parseToJsonElement(body) as? JsonArray).orEmpty()
				.mapNotNull { it as? JsonObject }
				.mapNotNull { t ->
					val hash = t.str("hash")?.lowercase() ?: return@mapNotNull null
					// qBittorrent uses 8640000 (100 days) to mean "unknown".
					val eta = t.number("eta")?.toLong()?.takeIf { it in 0L until 8_640_000L }
					hash to TorrentStats(
						downloadSpeed = t.number("dlspeed")?.toLong() ?: 0L,
						seeds = t.integer("num_seeds") ?: 0,
						peers = t.integer("num_leechs") ?: 0,
						etaSeconds = eta,
						state = t.str("state"),
						seedsTotal = t.integer("num_complete")?.coerceAtLeast(0) ?: 0,
						peersTotal = t.integer("num_incomplete")?.coerceAtLeast(0) ?: 0,
					)
				}
				.toMap()
		}.onFailure { Timber.w(it, "NOVA: qBittorrent stats failed") }.getOrDefault(emptyMap())
	}

	/** 1536 → "1.5 KB", 2_900_000_000 → "2.7 GB". */
	fun friendlySize(bytes: Double): String {
		val units = listOf("B", "KB", "MB", "GB", "TB")
		var value = bytes.coerceAtLeast(0.0)
		var unit = 0
		while (value >= 1024 && unit < units.lastIndex) {
			value /= 1024
			unit++
		}
		// Terabytes get two decimals so the drive's free space matches what most NAS dashboards show
		// (e.g. 1.98 TB rather than a rounded 2.0 TB).
		return when {
			unit <= 1 -> "${value.toInt()} ${units[unit]}"
			unit == units.lastIndex -> "%.2f %s".format(value, units[unit])
			else -> "%.1f %s".format(value, units[unit])
		}
	}

	fun friendlySpeed(bytesPerSecond: Long): String = friendlySize(bytesPerSecond.toDouble()) + "/s"

	fun friendlyEta(seconds: Long): String {
		val days = seconds / 86_400
		val hours = (seconds % 86_400) / 3_600
		val minutes = (seconds % 3_600) / 60
		return when {
			days > 0 -> "${days}d ${hours}h left"
			hours > 0 -> "${hours}h ${minutes}m left"
			minutes > 0 -> "${minutes}m left"
			else -> "under a minute left"
		}
	}

	private fun radarrQueue(service: Service): List<Download> = runCatching {
		val body = NovaSeerr.http(
			"${service.baseUrl}/api/v3/queue?page=1&pageSize=200&includeMovie=true",
			headers = mapOf("X-Api-Key" to service.apiKey),
		)
		val records = (NovaSeerr.json.parseToJsonElement(body).jsonObject["records"] as? JsonArray).orEmpty()
			.mapNotNull { it as? JsonObject }
		records.groupBy { it.str("downloadId") ?: "q${it.integer("id")}" }.mapNotNull { (key, group) ->
			val first = group.first()
			val movie = first["movie"] as? JsonObject
			Download(
				key = key,
				kind = Kind.MOVIE,
				queueIds = group.mapNotNull { it.integer("id") },
				episodeIds = emptyList(),
				libraryId = first.integer("movieId") ?: return@mapNotNull null,
				tmdbId = movie?.integer("tmdbId"),
				title = movie?.str("title") ?: first.str("title").orEmpty(),
				subtitle = movie?.integer("year")?.toString().orEmpty(),
				posterUrl = poster(movie),
				size = first.number("size") ?: 0.0,
				sizeLeft = first.number("sizeleft") ?: 0.0,
				timeLeft = first.str("timeleft"),
				status = first.str("status").orEmpty(),
				trackedState = first.str("trackedDownloadState"),
				message = statusMessage(first),
				downloadId = first.str("downloadId"),
			)
		}
	}.onFailure { Timber.w(it, "NOVA: Radarr queue failed") }.getOrDefault(emptyList())

	private fun sonarrQueue(service: Service): List<Download> = runCatching {
		val body = NovaSeerr.http(
			"${service.baseUrl}/api/v3/queue?page=1&pageSize=500&includeSeries=true&includeEpisode=true",
			headers = mapOf("X-Api-Key" to service.apiKey),
		)
		val records = (NovaSeerr.json.parseToJsonElement(body).jsonObject["records"] as? JsonArray).orEmpty()
			.mapNotNull { it as? JsonObject }
		// A season pack is one torrent but one queue entry per episode: show it once.
		records.groupBy { it.str("downloadId") ?: "q${it.integer("id")}" }.mapNotNull { (key, group) ->
			val first = group.first()
			val series = first["series"] as? JsonObject
			val episodes = group.mapNotNull { it["episode"] as? JsonObject }
			val seasons = episodes.mapNotNull { it.integer("seasonNumber") }.distinct().sorted()
			val subtitle = when {
				episodes.size == 1 -> {
					val e = episodes.first()
					val s = e.integer("seasonNumber") ?: 0
					val n = e.integer("episodeNumber") ?: 0
					"S%02dE%02d".format(s, n) + (e.str("title")?.let { " · $it" } ?: "")
				}
				seasons.size == 1 -> "Season ${seasons.first()} · ${episodes.size} episodes"
				seasons.isNotEmpty() -> "Seasons ${seasons.joinToString(", ")} · ${episodes.size} episodes"
				else -> ""
			}
			Download(
				key = key,
				kind = Kind.TV,
				queueIds = group.mapNotNull { it.integer("id") },
				episodeIds = group.mapNotNull { it.integer("episodeId") },
				libraryId = first.integer("seriesId") ?: return@mapNotNull null,
				tmdbId = series?.integer("tmdbId"),
				title = series?.str("title") ?: first.str("title").orEmpty(),
				subtitle = subtitle,
				posterUrl = poster(series),
				size = first.number("size") ?: 0.0,
				sizeLeft = first.number("sizeleft") ?: 0.0,
				timeLeft = first.str("timeleft"),
				status = first.str("status").orEmpty(),
				trackedState = first.str("trackedDownloadState"),
				message = statusMessage(first),
				downloadId = first.str("downloadId"),
			)
		}
	}.onFailure { Timber.w(it, "NOVA: Sonarr queue failed") }.getOrDefault(emptyList())

	private fun poster(media: JsonObject?): String? {
		val images = (media?.get("images") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
		val image = images.firstOrNull { it.str("coverType").equals("poster", true) } ?: return null
		return image.str("remoteUrl") ?: image.str("url")?.takeIf { it.startsWith("http") }
	}

	private fun statusMessage(record: JsonObject): String? {
		record.str("errorMessage")?.takeIf { it.isNotBlank() }?.let { return it }
		val messages = (record["statusMessages"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
		return messages.firstNotNullOfOrNull { m ->
			(m["messages"] as? JsonArray).orEmpty().firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull }
				?: m.str("title")
		}
	}

	/** Deletes torrents (and their downloaded files) from qBittorrent by hash. */
	fun removeTorrents(services: Services, hashes: List<String>) {
		val qbit = services.qbittorrentUrl ?: return
		val list = hashes.map { it.lowercase() }.filter { it.isNotBlank() }.distinct()
		if (list.isEmpty()) return
		runCatching {
			NovaSeerr.http(
				url = "$qbit/api/v2/torrents/delete",
				method = "POST",
				body = "hashes=${list.joinToString("|")}&deleteFiles=true",
				contentType = "application/x-www-form-urlencoded",
			)
		}.onFailure { Timber.w(it, "NOVA: qBittorrent delete failed") }
	}

	/** Pause (true) or resume (false) in qBittorrent. Returns an error message, or null when it worked. */
	suspend fun setPaused(services: Services, download: Download, paused: Boolean): String? = withContext(Dispatchers.IO) {
		val qbit = services.qbittorrentUrl ?: return@withContext "qBittorrent wasn't found in Radarr/Sonarr."
		val hash = download.downloadId?.lowercase() ?: return@withContext "This download can't be paused."
		// qBittorrent 5 renamed pause/resume to stop/start; try the new names first.
		val endpoints = if (paused) listOf("stop", "pause") else listOf("start", "resume")
		var lastError: String? = null
		for (endpoint in endpoints) {
			try {
				NovaSeerr.http(
					url = "$qbit/api/v2/torrents/$endpoint",
					method = "POST",
					body = "hashes=$hash",
					contentType = "application/x-www-form-urlencoded",
				)
				return@withContext null
			} catch (e: Exception) {
				val text = e.message.orEmpty()
				lastError = text
				if (!text.contains("HTTP 404")) break
			}
		}
		Timber.w("NOVA: qBittorrent pause/resume failed: %s", lastError)
		if (lastError?.contains("HTTP 403") == true) {
			"qBittorrent blocked NOVA. In qBittorrent → Options → WebUI, add your home network (for example 192.168.1.0/24) to the bypass list."
		} else "Couldn't reach qBittorrent."
	}

	/**
	 * Cancels a download everywhere: removes it (and its data) from qBittorrent,
	 * stops Radarr/Sonarr fetching it again, and removes it from Seerr so it shows
	 * as not requested.
	 */
	suspend fun delete(seerr: NovaSeerr.Config, services: Services, download: Download): Boolean = withContext(Dispatchers.IO) {
		val service = (if (download.kind == Kind.MOVIE) services.radarr else services.sonarr) ?: return@withContext false
		val headers = mapOf("X-Api-Key" to service.apiKey)
		var ok = true

		// 1. Remove from the queue and from qBittorrent (with its partial files).
		download.queueIds.forEach { id ->
			runCatching {
				NovaSeerr.http(
					"${service.baseUrl}/api/v3/queue/$id?removeFromClient=true&blocklist=false",
					method = "DELETE",
					headers = headers,
				)
			}.onFailure { Timber.w(it, "NOVA: queue delete failed for %d", id) }
		}

		// 1b. Also remove it in qBittorrent directly (with its files). Radarr/Sonarr's
		// "remove from client" can miss it (e.g. once it's finished or was re-added),
		// which left the torrent in qBittorrent.
		download.downloadId?.let { hash -> removeTorrents(services, listOf(hash)) }

		// 2. Stop Radarr/Sonarr grabbing it again. Never deletes files you already have.
		if (download.kind == Kind.MOVIE) {
			runCatching {
				val movie = NovaSeerr.json.parseToJsonElement(
					NovaSeerr.http("${service.baseUrl}/api/v3/movie/${download.libraryId}", headers = headers)
				).jsonObject
				if (movie.bool("hasFile") == true) {
					// Already have a copy (this was an upgrade): just stop monitoring.
					NovaSeerr.http(
						"${service.baseUrl}/api/v3/movie/editor",
						method = "PUT",
						headers = headers,
						body = buildJsonObject {
							putJsonArray("movieIds") { add(download.libraryId) }
							put("monitored", false)
						}.toString(),
					)
				} else {
					NovaSeerr.http(
						"${service.baseUrl}/api/v3/movie/${download.libraryId}?deleteFiles=false&addImportExclusion=false",
						method = "DELETE",
						headers = headers,
					)
				}
			}.onFailure { ok = false; Timber.w(it, "NOVA: Radarr movie removal failed") }
		} else {
			runCatching {
				val series = NovaSeerr.json.parseToJsonElement(
					NovaSeerr.http("${service.baseUrl}/api/v3/series/${download.libraryId}", headers = headers)
				).jsonObject
				val fileCount = (series["statistics"] as? JsonObject)?.integer("episodeFileCount") ?: 0
				if (fileCount == 0) {
					NovaSeerr.http(
						"${service.baseUrl}/api/v3/series/${download.libraryId}?deleteFiles=false&addImportListExclusion=false",
						method = "DELETE",
						headers = headers,
					)
				} else if (download.episodeIds.isNotEmpty()) {
					// Some episodes are already in the library: keep them, just stop these ones.
					NovaSeerr.http(
						"${service.baseUrl}/api/v3/episode/monitor",
						method = "PUT",
						headers = headers,
						body = buildJsonObject {
							putJsonArray("episodeIds") { download.episodeIds.forEach { add(it) } }
							put("monitored", false)
						}.toString(),
					)
				}
			}.onFailure { ok = false; Timber.w(it, "NOVA: Sonarr series removal failed") }
		}

		// 3. Remove it from Seerr (request + media entry), so it can be requested again later.
		download.tmdbId?.let { tmdb ->
			runCatching {
				val type = if (download.kind == Kind.MOVIE) "movie" else "tv"
				val details = NovaSeerr.json.parseToJsonElement(
					NovaSeerr.http("${seerr.baseUrl}/api/v1/$type/$tmdb", headers = mapOf("X-Api-Key" to seerr.apiKey))
				).jsonObject
				val mediaId = (details["mediaInfo"] as? JsonObject)?.integer("id")
				if (mediaId != null) {
					NovaSeerr.http(
						"${seerr.baseUrl}/api/v1/media/$mediaId",
						method = "DELETE",
						headers = mapOf("X-Api-Key" to seerr.apiKey),
					)
				}
			}.onFailure { ok = false; Timber.w(it, "NOVA: Seerr media removal failed") }
		}

		ok
	}

	/**
	 * "Try another copy": drops this release from qBittorrent, blocklists it so it
	 * is never picked again, and asks Radarr/Sonarr to search for a different one.
	 * The title stays requested in Seerr.
	 */
	suspend fun retry(services: Services, download: Download): Boolean = withContext(Dispatchers.IO) {
		val service = (if (download.kind == Kind.MOVIE) services.radarr else services.sonarr) ?: return@withContext false
		val headers = mapOf("X-Api-Key" to service.apiKey)
		var ok = true

		download.queueIds.forEach { id ->
			runCatching {
				NovaSeerr.http(
					"${service.baseUrl}/api/v3/queue/$id?removeFromClient=true&blocklist=true&skipRedownload=true",
					method = "DELETE",
					headers = headers,
				)
			}.onFailure { ok = false; Timber.w(it, "NOVA: queue blocklist failed for %d", id) }
		}
		download.downloadId?.let { hash -> removeTorrents(services, listOf(hash)) }

		runCatching {
			val command = if (download.kind == Kind.MOVIE) {
				buildJsonObject {
					put("name", "MoviesSearch")
					putJsonArray("movieIds") { add(download.libraryId) }
				}
			} else if (download.episodeIds.isNotEmpty()) {
				buildJsonObject {
					put("name", "EpisodeSearch")
					putJsonArray("episodeIds") { download.episodeIds.forEach { add(it) } }
				}
			} else {
				buildJsonObject {
					put("name", "SeriesSearch")
					put("seriesId", download.libraryId)
				}
			}
			NovaSeerr.http(
				"${service.baseUrl}/api/v3/command",
				method = "POST",
				headers = headers,
				body = command.toString(),
			)
		}.onFailure { ok = false; Timber.w(it, "NOVA: new search failed for %s", download.title) }

		ok
	}

	/**
	 * What Radarr itself knows about a film (for copies picked by hand, which go
	 * straight to Radarr): downloaded, downloading (with progress) or searching.
	 */
	fun radarrMovieStatus(service: Service, tmdbId: Int): NovaSeerr.Status? = runCatching {
		val headers = mapOf("X-Api-Key" to service.apiKey)
		val movie = (NovaSeerr.json.parseToJsonElement(
			NovaSeerr.http("${service.baseUrl}/api/v3/movie?tmdbId=$tmdbId", headers = headers)
		) as? JsonArray).orEmpty().firstOrNull() as? JsonObject ?: return@runCatching null
		if (movie.bool("hasFile") == true) return@runCatching NovaSeerr.Status(NovaSeerr.State.DOWNLOADED)
		val id = movie.integer("id") ?: return@runCatching null
		val queue = (NovaSeerr.json.parseToJsonElement(
			NovaSeerr.http("${service.baseUrl}/api/v3/queue/details?movieId=$id", headers = headers)
		) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
		if (queue.isNotEmpty()) {
			val size = queue.sumOf { it.number("size") ?: 0.0 }
			val left = queue.sumOf { it.number("sizeleft") ?: 0.0 }
			val progress = if (size > 0) (1.0 - left / size).toFloat().coerceIn(0f, 1f) else 0f
			NovaSeerr.Status(NovaSeerr.State.DOWNLOADING, progress)
		} else if (movie.bool("monitored") == true) {
			NovaSeerr.Status(NovaSeerr.State.SEARCHING)
		} else null
	}.onFailure { Timber.w(it, "NOVA: Radarr status failed for %d", tmdbId) }.getOrNull()

	/** "00:12:34" or "1.02:03:04" → "12m left" / "1d 2h left". */
	fun friendlyTimeLeft(raw: String?): String? {
		if (raw.isNullOrBlank()) return null
		val parts = raw.split(':')
		if (parts.size != 3) return null
		val (dayHours, minutes, _) = parts
		val days = dayHours.substringBefore('.', "0").takeIf { dayHours.contains('.') }?.toIntOrNull() ?: 0
		val hours = (if (dayHours.contains('.')) dayHours.substringAfter('.') else dayHours).toIntOrNull() ?: 0
		val mins = minutes.toIntOrNull() ?: 0
		return when {
			days > 0 -> "${days}d ${hours}h left"
			hours > 0 -> "${hours}h ${mins}m left"
			mins > 0 -> "${mins}m left"
			else -> "under a minute left"
		}
	}

	internal fun JsonObject.str(key: String): String? =
		(this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

	internal fun JsonObject.integer(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

	internal fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

	internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
}

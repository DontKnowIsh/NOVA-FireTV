package org.jellyfin.androidtv.ui.browsing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import timber.log.Timber

/**
 * "Choose a copy": lists every release Radarr/Sonarr can find for a title on your
 * indexers (with seeders, peers, size, quality and language) and downloads the
 * one you pick, like Radarr's Interactive Search.
 *
 * The title is added to Radarr/Sonarr first (without an automatic search, so
 * nothing is grabbed before you choose), using the same quality profile and
 * folder Seerr would use (Indian-language films go to Indian Movies).
 */
object NovaReleases {
	data class Release(
		val guid: String,
		val indexerId: Int,
		val indexer: String,
		val title: String,
		val sizeBytes: Long,
		val quality: String,
		/** Torrent seeders / peers; null when the indexer doesn't say. */
		val seeders: Int?,
		val peers: Int?,
		val languages: List<String>,
		val ageHours: Double?,
		val rejections: List<String>,
		/** TV: "Season pack" or "Episode 3" etc. */
		val coverage: String?,
		/** Radarr / Sonarr Custom Format score (e.g. +100 for a preferred release group). */
		val formatScore: Int = 0,
		/** The Custom Formats it matched (e.g. a group name). */
		val formats: List<String> = emptyList(),
	) {
		val rejected: Boolean get() = rejections.isNotEmpty()
	}

	/** Where a search runs: a Radarr movie, or one season of a Sonarr series. */
	data class Target(val kind: NovaArr.Kind, val arrId: Int, val season: Int?)

	/** Search can take a while on many indexers. */
	private const val SEARCH_TIMEOUT_MS = 120_000

	/** Adds the film to Radarr if needed (no automatic search) and returns where to search. */
	suspend fun prepareMovie(seerr: NovaSeerr.Config, tmdbId: Int): Target? = withContext(Dispatchers.IO) {
		runCatching {
			val radarr = NovaArr.services(seerr).radarr ?: error("Radarr isn't set up in Seerr")
			val headers = mapOf("X-Api-Key" to radarr.apiKey)
			val existing = (parse(NovaSeerr.http("${radarr.baseUrl}/api/v3/movie?tmdbId=$tmdbId", headers = headers)) as? JsonArray)
				.orEmpty().firstOrNull() as? JsonObject
			val id = existing?.int("id") ?: run {
				val lookup = parse(NovaSeerr.http("${radarr.baseUrl}/api/v3/movie/lookup/tmdb?tmdbId=$tmdbId", headers = headers)) as? JsonObject
					?: error("Radarr couldn't find this film")
				val folder = NovaSeerr.indianMoviesFolderFor(seerr, tmdbId)?.second
					?: radarr.rootFolder
					?: firstRootFolder(radarr)
					?: error("Radarr has no root folder")
				val profile = radarr.profileId ?: firstProfile(radarr) ?: error("Radarr has no quality profile")
				val body = JsonObject(
					lookup + mapOf(
						"qualityProfileId" to JsonPrimitive(profile),
						"rootFolderPath" to JsonPrimitive(folder),
						"monitored" to JsonPrimitive(true),
						"minimumAvailability" to JsonPrimitive("released"),
						"addOptions" to buildJsonObject {
							put("searchForMovie", false)
							put("monitor", "movieOnly")
						},
					)
				)
				val added = parse(
					NovaSeerr.http("${radarr.baseUrl}/api/v3/movie", method = "POST", headers = headers, body = body.toString())
				) as? JsonObject
				added?.int("id") ?: error("Radarr didn't add the film")
			}
			Target(NovaArr.Kind.MOVIE, id, null)
		}.onFailure { Timber.w(it, "NOVA: couldn't prepare film %d for choosing a copy", tmdbId) }.getOrNull()
	}

	/** Adds the show to Sonarr if needed (no automatic search), monitors [season], returns where to search. */
	suspend fun prepareSeason(seerr: NovaSeerr.Config, tmdbId: Int, season: Int): Target? = withContext(Dispatchers.IO) {
		runCatching {
			val sonarr = NovaArr.services(seerr).sonarr ?: error("Sonarr isn't set up in Seerr")
			val headers = mapOf("X-Api-Key" to sonarr.apiKey)
			val details = parse(
				NovaSeerr.http("${seerr.baseUrl}/api/v1/tv/$tmdbId", headers = mapOf("X-Api-Key" to seerr.apiKey))
			) as? JsonObject
			val tvdbId = (details?.get("externalIds") as? JsonObject)?.int("tvdbId") ?: error("No TVDB id for this show")

			val existing = (parse(NovaSeerr.http("${sonarr.baseUrl}/api/v3/series?tvdbId=$tvdbId", headers = headers)) as? JsonArray)
				.orEmpty().firstOrNull() as? JsonObject
			val id = if (existing != null) {
				val seriesId = existing.int("id") ?: error("Sonarr series without id")
				// Make sure the chosen season is monitored so it imports and upgrades.
				runCatching {
					val seasons = (existing["seasons"] as? JsonArray).orEmpty().map { s ->
						val obj = s as? JsonObject ?: return@map s
						if (obj.int("seasonNumber") == season) JsonObject(obj + ("monitored" to JsonPrimitive(true))) else obj
					}
					val updated = JsonObject(existing + ("seasons" to JsonArray(seasons)))
					NovaSeerr.http("${sonarr.baseUrl}/api/v3/series/$seriesId", method = "PUT", headers = headers, body = updated.toString())
				}.onFailure { Timber.w(it, "NOVA: couldn't monitor season %d", season) }
				seriesId
			} else {
				val lookup = (parse(NovaSeerr.http("${sonarr.baseUrl}/api/v3/series/lookup?term=tvdb%3A$tvdbId", headers = headers)) as? JsonArray)
					.orEmpty().firstOrNull() as? JsonObject ?: error("Sonarr couldn't find this show")
				val folder = sonarr.rootFolder ?: firstRootFolder(sonarr) ?: error("Sonarr has no root folder")
				val profile = sonarr.profileId ?: firstProfile(sonarr) ?: error("Sonarr has no quality profile")
				val seasons = (lookup["seasons"] as? JsonArray).orEmpty().map { s ->
					val obj = s as? JsonObject ?: return@map s
					JsonObject(obj + ("monitored" to JsonPrimitive(obj.int("seasonNumber") == season)))
				}
				val extra = mutableMapOf<String, JsonElement>(
					"qualityProfileId" to JsonPrimitive(profile),
					"rootFolderPath" to JsonPrimitive(folder),
					"monitored" to JsonPrimitive(true),
					"seasonFolder" to JsonPrimitive(true),
					"seasons" to JsonArray(seasons),
					"addOptions" to buildJsonObject {
						put("searchForMissingEpisodes", false)
						put("searchForCutoffUnmetEpisodes", false)
					},
				)
				sonarr.languageProfileId?.let { extra["languageProfileId"] = JsonPrimitive(it) }
				val added = parse(
					NovaSeerr.http("${sonarr.baseUrl}/api/v3/series", method = "POST", headers = headers, body = JsonObject(lookup + extra).toString())
				) as? JsonObject
				added?.int("id") ?: error("Sonarr didn't add the show")
			}
			Target(NovaArr.Kind.TV, id, season)
		}.onFailure { Timber.w(it, "NOVA: couldn't prepare %d season %d for choosing a copy", tmdbId, season) }.getOrNull()
	}

	/** Every copy the indexers have, best first (allowed, preferred groups, then most seeders). Null on failure. */
	suspend fun search(seerr: NovaSeerr.Config, target: Target): List<Release>? = withContext(Dispatchers.IO) {
		runCatching {
			val service = service(seerr, target.kind) ?: error("No Radarr/Sonarr")
			val query = when (target.kind) {
				NovaArr.Kind.MOVIE -> "movieId=${target.arrId}"
				NovaArr.Kind.TV -> "seriesId=${target.arrId}&seasonNumber=${target.season ?: 1}"
			}
			val body = NovaSeerr.http(
				"${service.baseUrl}/api/v3/release?$query",
				headers = mapOf("X-Api-Key" to service.apiKey),
				readTimeoutMs = SEARCH_TIMEOUT_MS,
			)
			(parse(body) as? JsonArray).orEmpty()
				.mapNotNull { (it as? JsonObject)?.let(::toRelease) }
				.sortedWith(
					// Allowed first, then your preferred groups (Custom Format score), then most seeders.
					compareBy<Release> { it.rejected }
						.thenByDescending { it.formatScore }
						.thenByDescending { it.seeders ?: -1 }
						.thenByDescending { it.sizeBytes }
				)
		}.onFailure { Timber.w(it, "NOVA: release search failed") }.getOrNull()
	}

	/** Sends the chosen copy to qBittorrent through Radarr/Sonarr. */
	suspend fun grab(seerr: NovaSeerr.Config, target: Target, release: Release): Boolean = withContext(Dispatchers.IO) {
		runCatching {
			val service = service(seerr, target.kind) ?: error("No Radarr/Sonarr")
			val body = buildJsonObject {
				put("guid", release.guid)
				put("indexerId", release.indexerId)
			}
			NovaSeerr.http(
				"${service.baseUrl}/api/v3/release",
				method = "POST",
				headers = mapOf("X-Api-Key" to service.apiKey),
				body = body.toString(),
				readTimeoutMs = 60_000,
			)
			true
		}.onFailure { Timber.w(it, "NOVA: grabbing %s failed", release.title) }.getOrDefault(false)
	}

	private suspend fun service(seerr: NovaSeerr.Config, kind: NovaArr.Kind): NovaArr.Service? {
		val services = NovaArr.services(seerr)
		return if (kind == NovaArr.Kind.MOVIE) services.radarr else services.sonarr
	}

	private fun toRelease(obj: JsonObject): Release? {
		val guid = obj.str("guid") ?: return null
		val indexerId = obj.int("indexerId") ?: return null
		val quality = ((obj["quality"] as? JsonObject)?.get("quality") as? JsonObject)?.str("name").orEmpty()
		val languages = (obj["languages"] as? JsonArray).orEmpty()
			.mapNotNull { (it as? JsonObject)?.str("name") }
			.filterNot { it.equals("Unknown", ignoreCase = true) }
		val rejections = (obj["rejections"] as? JsonArray).orEmpty().mapNotNull { r ->
			(r as? JsonPrimitive)?.contentOrNull ?: (r as? JsonObject)?.str("reason")
		}
		val isTorrent = obj.str("protocol").equals("torrent", ignoreCase = true)
		val episodes = (obj["episodeNumbers"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.intOrNull }
		val coverage = when {
			obj["fullSeason"]?.let { (it as? JsonPrimitive)?.booleanOrNull } == true -> "Season pack"
			episodes.size == 1 -> "Episode ${episodes.first()}"
			episodes.size > 1 -> "Episodes ${episodes.first()}–${episodes.last()}"
			else -> null
		}
		return Release(
			guid = guid,
			indexerId = indexerId,
			indexer = obj.str("indexer").orEmpty(),
			title = obj.str("title").orEmpty(),
			sizeBytes = (obj["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
			quality = quality,
			seeders = if (isTorrent) obj.int("seeders") else null,
			peers = if (isTorrent) obj.int("leechers") else null,
			languages = languages,
			ageHours = (obj["ageHours"] as? JsonPrimitive)?.doubleOrNull,
			rejections = rejections,
			coverage = coverage,
			formatScore = obj.int("customFormatScore") ?: 0,
			formats = (obj["customFormats"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("name") },
		)
	}

	private fun firstRootFolder(service: NovaArr.Service): String? = runCatching {
		(parse(NovaSeerr.http("${service.baseUrl}/api/v3/rootfolder", headers = mapOf("X-Api-Key" to service.apiKey))) as? JsonArray)
			.orEmpty().firstNotNullOfOrNull { (it as? JsonObject)?.str("path") }
	}.getOrNull()

	private fun firstProfile(service: NovaArr.Service): Int? = runCatching {
		(parse(NovaSeerr.http("${service.baseUrl}/api/v3/qualityprofile", headers = mapOf("X-Api-Key" to service.apiKey))) as? JsonArray)
			.orEmpty().firstNotNullOfOrNull { (it as? JsonObject)?.int("id") }
	}.getOrNull()

	private fun parse(body: String): JsonElement = NovaSeerr.json.parseToJsonElement(body)

	private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
	private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

	/** "2.4 GB" */
	fun friendlySize(bytes: Long): String = NovaArr.friendlySize(bytes.toDouble())

	/** "3h", "5d", "2y" */
	fun friendlyAge(hours: Double?): String? {
		val h = hours ?: return null
		return when {
			h < 1 -> "new"
			h < 48 -> "${h.toInt()}h"
			h < 24 * 60 -> "${(h / 24).toInt()}d"
			h < 24 * 730 -> "${(h / (24 * 30)).toInt()}mo"
			else -> "${(h / (24 * 365)).toInt()}y"
		}
	}
}

package org.jellyfin.androidtv.ui.browsing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jellyfin.androidtv.ui.browsing.NovaSeerr.string
import java.util.concurrent.ConcurrentHashMap

/**
 * Age ratings (U / PG / 12A / 15 / 18…) for titles on the Download page, from
 * Seerr's details: the UK rating where there is one, otherwise the US one.
 * Looked up as cards appear and remembered while NOVA is open.
 */
object NovaAgeRatings {
	/** Set by the Download page once it has Seerr's details. */
	@Volatile var config: NovaSeerr.Config? = null

	// "" = looked up, no rating.
	private val cache = ConcurrentHashMap<String, String>()
	private val gate = Semaphore(4)

	private fun key(result: NovaSeerr.SearchResult) = "${result.type.apiName}-${result.tmdbId}"

	/** Already known: the rating, "" for none, or null if not looked up yet. */
	fun cached(result: NovaSeerr.SearchResult): String? = cache[key(result)]

	suspend fun get(result: NovaSeerr.SearchResult): String? {
		cache[key(result)]?.let { return it.ifEmpty { null } }
		val cfg = config ?: return null
		val rating = gate.withPermit {
			cache[key(result)] ?: withContext(Dispatchers.IO) {
				runCatching { lookUp(cfg, result) }.getOrNull()
			}.orEmpty().also { cache[key(result)] = it }
		}
		return rating.ifEmpty { null }
	}

	private fun lookUp(cfg: NovaSeerr.Config, result: NovaSeerr.SearchResult): String? {
		val isMovie = result.type == NovaSeerr.MediaType.MOVIE
		val body = NovaSeerr.http(
			url = "${cfg.baseUrl}/api/v1/${if (isMovie) "movie" else "tv"}/${result.tmdbId}",
			headers = mapOf("X-Api-Key" to cfg.apiKey),
		)
		val obj = NovaSeerr.json.parseToJsonElement(body).jsonObject
		// Same answer has the trailer and description: keep them so the preview opens straight away.
		NovaSeerr.rememberTrailer(result.type, result.tmdbId, NovaSeerr.trailerFrom(obj))
		NovaSeerr.rememberTrailers(result.type, result.tmdbId, NovaSeerr.trailersFrom(obj))
		NovaPreviewCache.put(result, previewFrom(obj, isMovie))
		fun byCountry(results: JsonArray?, country: String) =
			results.orEmpty().mapNotNull { it as? JsonObject }.firstOrNull { it.string("iso_3166_1") == country }
		return if (isMovie) {
			val results = (obj["releases"] as? JsonObject)?.get("results") as? JsonArray
			listOf("GB", "US").firstNotNullOfOrNull { country ->
				(byCountry(results, country)?.get("release_dates") as? JsonArray).orEmpty()
					.mapNotNull { (it as? JsonObject)?.string("certification")?.trim() }
					.firstOrNull { it.isNotEmpty() }
			}
		} else {
			val results = (obj["contentRatings"] as? JsonObject)?.get("results") as? JsonArray
			listOf("GB", "US").firstNotNullOfOrNull { country ->
				byCountry(results, country)?.string("rating")?.trim()?.takeIf { it.isNotEmpty() }
			}
		}
	}
}

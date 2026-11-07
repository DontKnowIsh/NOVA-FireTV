package org.jellyfin.androidtv.ui.browsing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber

/** Free and total space on the server's media drive, for Settings. */
object NovaStorage {
	data class Space(val free: Long, val total: Long) {
		val used: Long get() = (total - free).coerceAtLeast(0L)
		val usedPercent: Int get() = if (total > 0) ((used * 100) / total).toInt() else 0
	}

	/** Last value read, shown straight away while a fresh one loads. */
	@Volatile
	var last: Space? = null
		private set

	/** e.g. "1.2 TB free of 3.6 TB · 67% used" */
	fun describe(space: Space?): String = when (space) {
		null -> "Checking…"
		else -> "${NovaArr.friendlySize(space.free.toDouble())} free of " +
			"${NovaArr.friendlySize(space.total.toDouble())} · ${space.usedPercent}% used"
	}

	/**
	 * Reads the media drive's space from Radarr (or Sonarr), which report the
	 * exact size of the drive your films are on; falls back to Jellyfin.
	 */
	suspend fun load(api: ApiClient): Space? = withContext(Dispatchers.IO) {
		val fromArr = runCatching {
			val seerr = NovaSeerr.config(api) ?: return@runCatching null
			val services = NovaArr.services(seerr)
			listOfNotNull(services.radarr, services.sonarr).firstNotNullOfOrNull { arrSpace(it) }
		}.getOrNull()
		val space = fromArr ?: runCatching { jellyfinSpace(api) }.getOrNull()
		if (space != null) last = space
		space
	}

	private fun arrSpace(service: NovaArr.Service): Space? = runCatching {
		val drives = (NovaSeerr.json.parseToJsonElement(
			NovaSeerr.http("${service.baseUrl}/api/v3/diskspace", headers = mapOf("X-Api-Key" to service.apiKey))
		) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
		fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull
		fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
		// The media drive is mounted at /data in the containers; otherwise take the biggest.
		val drive = drives.firstOrNull { it.str("path")?.trimEnd('/') == "/data" }
			?: drives.maxByOrNull { it.long("totalSpace") ?: 0L }
			?: return@runCatching null
		val free = drive.long("freeSpace") ?: return@runCatching null
		val total = drive.long("totalSpace")?.takeIf { it > 0 } ?: return@runCatching null
		Space(free, total)
	}.onFailure { Timber.w(it, "NOVA: disk space from %s failed", service.baseUrl) }.getOrNull()

	/** Jellyfin's storage report: the library folders' drive. */
	private fun jellyfinSpace(api: ApiClient): Space? {
		val server = api.baseUrl?.trimEnd('/') ?: return null
		val token = api.accessToken ?: return null
		val root = NovaSeerr.json.parseToJsonElement(
			NovaSeerr.http("$server/System/Info/Storage", headers = mapOf("Authorization" to "MediaBrowser Token=\"$token\""))
		) as? JsonObject ?: return null
		fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull
		val folders = (root["Libraries"] as? JsonArray).orEmpty()
			.flatMap { ((it as? JsonObject)?.get("Folders") as? JsonArray).orEmpty() }
			.mapNotNull { it as? JsonObject }
		val folder = folders.maxByOrNull { (it.long("FreeSpace") ?: 0L) + (it.long("UsedSpace") ?: 0L) } ?: return null
		val free = folder.long("FreeSpace") ?: return null
		val used = folder.long("UsedSpace") ?: return null
		return Space(free, free + used)
	}
}

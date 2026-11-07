package org.jellyfin.androidtv.ui.browsing

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber

/**
 * Keeps Discover fresh: when NOVA shows Discover and JellyBridge hasn't synced
 * with Seerr for a while, start JellyBridge's sync task on the server (the
 * same as pressing ▶ on it under Dashboard → Scheduled Tasks).
 */
object NovaDiscoverRefresh {
	/** Each time NOVA is opened (at most every 10 minutes) Discover is synced. */
	private const val RETRY_MS = 10 * 60 * 1000L
	private var lastAttempt = 0L

	/** Fires when fresh Discover titles should be on the server: pages reload then. */
	val updates = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

	/** Scans just the Discover library (or everything if it can't be found). */
	private fun scanDiscover(server: String, auth: Map<String, String>) {
		runCatching {
			val folders = Json.parseToJsonElement(NovaSeerr.http("$server/Library/VirtualFolders", headers = auth)) as? JsonArray
			val discoverId = folders?.mapNotNull { it as? JsonObject }
				?.firstOrNull { (it["Name"] as? JsonPrimitive)?.contentOrNull.equals("Discover", ignoreCase = true) }
				?.let { (it["ItemId"] as? JsonPrimitive)?.contentOrNull }
			val url = if (discoverId != null) {
				"$server/Items/$discoverId/Refresh?Recursive=true&MetadataRefreshMode=Default&ImageRefreshMode=Default&ReplaceAllMetadata=false&ReplaceAllImages=false"
			} else {
				"$server/Library/Refresh"
			}
			Timber.i("NOVA: scanning Discover after JellyBridge sync")
			NovaSeerr.http(url, method = "POST", headers = auth, body = "")
		}.onFailure { Timber.w(it, "NOVA: Discover scan failed") }
	}

	// Runs on its own, so leaving the page doesn't cancel a sync half way.
	private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

	fun maybeSync(api: ApiClient) {
		val now = SystemClock.elapsedRealtime()
		if (lastAttempt != 0L && now - lastAttempt < RETRY_MS) return
		lastAttempt = now
		scope.launch {
			runCatching {
				val server = api.baseUrl?.trimEnd('/') ?: return@runCatching
				val token = api.accessToken ?: return@runCatching
				val auth = mapOf("Authorization" to "MediaBrowser Token=\"$token\"")
				val tasks = Json.parseToJsonElement(NovaSeerr.http("$server/ScheduledTasks", headers = auth)) as? JsonArray
					?: return@runCatching
				fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
				val bridge = tasks.mapNotNull { it as? JsonObject }.filter { task ->
					val text = "${task.str("Name")} ${task.str("Category")} ${task.str("Key")}".lowercase()
					text.contains("jellybridge") || text.contains("jelly bridge")
				}
				val syncs = bridge.filter { it.str("Name").orEmpty().contains("sync", ignoreCase = true) }
				if (syncs.isEmpty()) {
					Timber.i("NOVA: no JellyBridge sync task found (%d JellyBridge tasks)", bridge.size)
					return@runCatching
				}
				for (task in syncs) {
					val id = task.str("Id") ?: continue
					if (!task.str("State").equals("Idle", ignoreCase = true)) continue
					val ended = (task["LastExecutionResult"] as? JsonObject)?.str("EndTime")
					Timber.i("NOVA: starting JellyBridge task %s (last run %s)", task.str("Name"), ended)
					NovaSeerr.http("$server/ScheduledTasks/Running/$id", method = "POST", headers = auth, body = "")
					// JellyBridge only writes the new titles to disk; Jellyfin has to scan
					// the Discover library before they show. Wait for the sync to finish.
					delay(5_000L)
					var polls = 0
					while (polls++ < 60) {
						val state = runCatching {
							(Json.parseToJsonElement(NovaSeerr.http("$server/ScheduledTasks/$id", headers = auth)) as? JsonObject)?.str("State")
						}.getOrNull()
						if (state == null || state.equals("Idle", ignoreCase = true)) break
						delay(5_000L)
					}
					scanDiscover(server, auth)
					// Give Jellyfin a moment to add the new titles, then reload the pages.
					delay(20_000L)
					updates.tryEmit(Unit)
					delay(40_000L)
					updates.tryEmit(Unit)
				}
			}.onFailure { Timber.w(it, "NOVA: couldn't refresh Discover") }
		}
	}
}

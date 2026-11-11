package org.jellyfin.androidtv.ui.browsing

import android.content.Context
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

/** The PIN that protects deleting from the server (Settings → Customization → Change delete PIN). */
object NovaDeletePin {
	private const val PREFS = "nova_settings"
	private const val KEY = "delete_pin"
	const val DEFAULT = "0000"
	const val LENGTH = 4

	fun get(context: Context): String =
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, DEFAULT) ?: DEFAULT

	fun set(context: Context, pin: String) {
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, pin).apply()
	}
}

/** The PIN for the kids lock (Settings → Customization → Change kids lock PIN). */
object NovaKidsPin {
	private const val PREFS = "nova_settings"
	private const val KEY = "kids_pin"
	private const val ENABLED = "kids_lock"
	const val DEFAULT = "0000"

	fun get(context: Context): String =
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, DEFAULT) ?: DEFAULT

	fun set(context: Context, pin: String) {
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, pin).apply()
	}

	/** Kids lock is on unless switched off in Settings. */
	fun isEnabled(context: Context): Boolean =
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, true)

	fun setEnabled(context: Context, enabled: Boolean) {
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).apply()
	}
}

/**
 * The 8-digit master code: works at every NOVA PIN prompt ("Forgot PIN?"), in
 * case a PIN is forgotten. Each Fire TV picks its own (the first time a PIN is
 * changed, or Settings → Master code); only a salted fingerprint is kept.
 */
object NovaMasterCode {
	const val LENGTH = 8
	private const val PREFS = "nova_settings"
	private const val KEY_HASH = "master_code_hash"
	private const val KEY_SALT = "master_code_salt"

	private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	private fun fingerprint(salt: String, code: String): String =
		java.security.MessageDigest.getInstance("SHA-256")
			.digest("$salt:$code".toByteArray(Charsets.UTF_8))
			.joinToString("") { "%02x".format(it) }

	/** Has this Fire TV got a master code yet? */
	fun isSet(context: Context): Boolean = !prefs(context).getString(KEY_HASH, null).isNullOrEmpty()

	fun set(context: Context, code: String) {
		val salt = java.util.UUID.randomUUID().toString()
		prefs(context).edit().putString(KEY_SALT, salt).putString(KEY_HASH, fingerprint(salt, code)).apply()
	}

	fun matches(context: Context, code: String): Boolean {
		val hash = prefs(context).getString(KEY_HASH, null) ?: return false
		val salt = prefs(context).getString(KEY_SALT, null) ?: return false
		return fingerprint(salt, code) == hash
	}
}

/**
 * Deletes a film or show from the server, files included.
 *
 * Goes through Radarr/Sonarr when they manage the title (otherwise they would
 * see the file as missing and download it again), removes it from Seerr so it
 * can be requested again, and removes it from Jellyfin.
 */
object NovaLibraryDelete {
	sealed interface Outcome {
		data object Deleted : Outcome
		data class Failed(val reason: String) : Outcome
	}

	/** qBittorrent hashes of everything Radarr/Sonarr ever downloaded for a title. */
	private fun downloadIds(baseUrl: String, headers: Map<String, String>, path: String): List<String> = runCatching {
		(NovaSeerr.json.parseToJsonElement(NovaSeerr.http("$baseUrl/api/v3/$path", headers = headers)) as? JsonArray)
			.orEmpty()
			.mapNotNull { ((it as? JsonObject)?.get("downloadId") as? JsonPrimitive)?.content }
			.filter { it.isNotBlank() }
			.distinct()
	}.getOrDefault(emptyList())

	/**
	 * Stops a film or show that is still "Searching" (nothing downloaded yet): takes
	 * it out of Radarr / Sonarr and removes its request from Seerr, so nothing keeps
	 * looking for it. Files already on the server are left alone.
	 */
	suspend fun removeRequest(seerr: NovaSeerr.Config, result: NovaSeerr.SearchResult): Outcome = withContext(Dispatchers.IO) {
		try {
			val services = runCatching { NovaArr.services(seerr) }.getOrNull()
			val seerrHeaders = mapOf("X-Api-Key" to seerr.apiKey)
			val details = runCatching {
				NovaSeerr.json.parseToJsonElement(
					NovaSeerr.http("${seerr.baseUrl}/api/v1/${result.type.apiName}/${result.tmdbId}", headers = seerrHeaders)
				) as? JsonObject
			}.getOrNull()

			when (result.type) {
				NovaSeerr.MediaType.MOVIE -> services?.radarr?.let { radarr ->
					val headers = mapOf("X-Api-Key" to radarr.apiKey)
					val movie = (NovaSeerr.json.parseToJsonElement(
						NovaSeerr.http("${radarr.baseUrl}/api/v3/movie?tmdbId=${result.tmdbId}", headers = headers)
					) as? JsonArray).orEmpty().firstOrNull() as? JsonObject
					val id = (movie?.get("id") as? JsonPrimitive)?.intOrNull ?: return@let
					NovaArr.removeTorrents(services, downloadIds(radarr.baseUrl, headers, "history/movie?movieId=$id"))
					NovaSeerr.http(
						"${radarr.baseUrl}/api/v3/movie/$id?deleteFiles=false&addImportExclusion=false",
						method = "DELETE",
						headers = headers,
					)
				}
				NovaSeerr.MediaType.TV -> services?.sonarr?.let { sonarr ->
					val tvdb = ((details?.get("externalIds") as? JsonObject)?.get("tvdbId") as? JsonPrimitive)?.intOrNull
						?: return@let
					val headers = mapOf("X-Api-Key" to sonarr.apiKey)
					val series = (NovaSeerr.json.parseToJsonElement(
						NovaSeerr.http("${sonarr.baseUrl}/api/v3/series?tvdbId=$tvdb", headers = headers)
					) as? JsonArray).orEmpty().firstOrNull() as? JsonObject
					val id = (series?.get("id") as? JsonPrimitive)?.intOrNull ?: return@let
					NovaSeerr.http(
						"${sonarr.baseUrl}/api/v3/series/$id?deleteFiles=false&addImportListExclusion=false",
						method = "DELETE",
						headers = headers,
					)
				}
			}

			// Its request in Seerr goes too, so it can be asked for again later.
			val mediaId = ((details?.get("mediaInfo") as? JsonObject)?.get("id") as? JsonPrimitive)?.intOrNull
			if (mediaId != null) {
				NovaSeerr.http("${seerr.baseUrl}/api/v1/media/$mediaId", method = "DELETE", headers = seerrHeaders)
			}
			Outcome.Deleted
		} catch (cancelled: CancellationException) {
			throw cancelled
		} catch (error: Exception) {
			Timber.w(error, "NOVA: remove request failed for %s", result.title)
			Outcome.Failed("Couldn't remove it. Check the server is on and try again.")
		}
	}

	suspend fun delete(api: ApiClient, item: BaseItemDto): Outcome = withContext(Dispatchers.IO) {
		try {
			val full = NovaSeerr.fullItem(api, item)
			val seerr = runCatching { NovaSeerr.config(api) }.getOrNull()
			val services = seerr?.let { runCatching { NovaArr.services(it) }.getOrNull() }
			val tmdb = NovaSeerr.tmdbId(full)
			var deletedByArr = false

			when (full.type) {
				BaseItemKind.MOVIE -> services?.radarr?.let { radarr ->
					if (tmdb == null) return@let
					val headers = mapOf("X-Api-Key" to radarr.apiKey)
					val movie = (NovaSeerr.json.parseToJsonElement(
						NovaSeerr.http("${radarr.baseUrl}/api/v3/movie?tmdbId=$tmdb", headers = headers)
					) as? JsonArray).orEmpty().firstOrNull() as? JsonObject
					val id = (movie?.get("id") as? JsonPrimitive)?.intOrNull ?: return@let
					// Its torrents in qBittorrent (still seeding or downloading) go too.
					NovaArr.removeTorrents(services ?: return@let, downloadIds(radarr.baseUrl, headers, "history/movie?movieId=$id"))
					NovaSeerr.http(
						"${radarr.baseUrl}/api/v3/movie/$id?deleteFiles=true&addImportExclusion=false",
						method = "DELETE",
						headers = headers,
					)
					deletedByArr = true
				}
				BaseItemKind.SERIES -> services?.sonarr?.let { sonarr ->
					val tvdb = full.providerIds?.entries?.firstOrNull { it.key.equals("Tvdb", ignoreCase = true) }?.value
						?.toIntOrNull() ?: return@let
					val headers = mapOf("X-Api-Key" to sonarr.apiKey)
					val series = (NovaSeerr.json.parseToJsonElement(
						NovaSeerr.http("${sonarr.baseUrl}/api/v3/series?tvdbId=$tvdb", headers = headers)
					) as? JsonArray).orEmpty().firstOrNull() as? JsonObject
					val id = (series?.get("id") as? JsonPrimitive)?.intOrNull ?: return@let
					NovaArr.removeTorrents(services ?: return@let, downloadIds(sonarr.baseUrl, headers, "history/series?seriesId=$id"))
					NovaSeerr.http(
						"${sonarr.baseUrl}/api/v3/series/$id?deleteFiles=true&addImportListExclusion=false",
						method = "DELETE",
						headers = headers,
					)
					deletedByArr = true
				}
				// One episode: delete just its file in Sonarr and stop Sonarr fetching it again.
				BaseItemKind.EPISODE -> services?.sonarr?.let { sonarr ->
					val show = full.seriesId?.let { id -> runCatching { api.userLibraryApi.getItem(itemId = id).content }.getOrNull() }
						?: return@let
					val tvdb = show.providerIds?.entries?.firstOrNull { it.key.equals("Tvdb", ignoreCase = true) }?.value
						?.toIntOrNull() ?: return@let
					val season = full.parentIndexNumber ?: return@let
					val number = full.indexNumber ?: return@let
					val headers = mapOf("X-Api-Key" to sonarr.apiKey)
					val series = (NovaSeerr.json.parseToJsonElement(
						NovaSeerr.http("${sonarr.baseUrl}/api/v3/series?tvdbId=$tvdb", headers = headers)
					) as? JsonArray).orEmpty().firstOrNull() as? JsonObject
					val seriesId = (series?.get("id") as? JsonPrimitive)?.intOrNull ?: return@let
					val episode = (NovaSeerr.json.parseToJsonElement(
						NovaSeerr.http("${sonarr.baseUrl}/api/v3/episode?seriesId=$seriesId", headers = headers)
					) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.firstOrNull {
						(it["seasonNumber"] as? JsonPrimitive)?.intOrNull == season &&
							(it["episodeNumber"] as? JsonPrimitive)?.intOrNull == number
					} ?: return@let
					val episodeId = (episode["id"] as? JsonPrimitive)?.intOrNull ?: return@let
					val fileId = (episode["episodeFileId"] as? JsonPrimitive)?.intOrNull ?: 0
					// Its torrent in qBittorrent goes too (unless it was a whole-season pack).
					runCatching {
						val history = NovaSeerr.json.parseToJsonElement(
							NovaSeerr.http("${sonarr.baseUrl}/api/v3/history?episodeId=$episodeId&pageSize=50", headers = headers)
						) as? JsonObject
						val records = (history?.get("records") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
						val hashes = records.mapNotNull { (it["downloadId"] as? JsonPrimitive)?.content }.filter { it.isNotBlank() }.distinct()
						// Only remove torrents that were just this episode, so a season pack keeps the rest.
						val single = hashes.filter { hash ->
							val all = NovaSeerr.json.parseToJsonElement(
								NovaSeerr.http("${sonarr.baseUrl}/api/v3/history?downloadId=$hash&pageSize=200", headers = headers)
							) as? JsonObject
							(all?.get("records") as? JsonArray).orEmpty()
								.mapNotNull { ((it as? JsonObject)?.get("episodeId") as? JsonPrimitive)?.intOrNull }
								.distinct().size <= 1
						}
						services?.let { NovaArr.removeTorrents(it, single) }
					}
					NovaSeerr.http(
						"${sonarr.baseUrl}/api/v3/episode/monitor",
						method = "PUT",
						headers = headers,
						body = "{\"episodeIds\":[$episodeId],\"monitored\":false}",
					)
					if (fileId > 0) {
						NovaSeerr.http("${sonarr.baseUrl}/api/v3/episodefile/$fileId", method = "DELETE", headers = headers)
					}
					deletedByArr = true
				}
				else -> Unit
			}

			// Remove it from Jellyfin too. If Radarr/Sonarr already deleted the files this
			// may fail harmlessly; Jellyfin then drops it on its next library update.
			val jellyfin = runCatching { api.libraryApi.deleteItem(full.id) }
			if (!deletedByArr && jellyfin.isFailure) {
				Timber.w(jellyfin.exceptionOrNull(), "NOVA: Jellyfin delete failed for %s", full.name)
				return@withContext Outcome.Failed(
					"The server didn't allow it. In Jellyfin: Dashboard → Users → your user → tick \"Allow media deletion\"."
				)
			}

			// Let it be requested again in Seerr later.
			// Seerr only tracks films and whole shows (not single episodes).
			if (seerr != null && tmdb != null && (full.type == BaseItemKind.MOVIE || full.type == BaseItemKind.SERIES)) {
				runCatching {
					val type = if (full.type == BaseItemKind.MOVIE) "movie" else "tv"
					val details = NovaSeerr.json.parseToJsonElement(
						NovaSeerr.http("${seerr.baseUrl}/api/v1/$type/$tmdb", headers = mapOf("X-Api-Key" to seerr.apiKey))
					) as? JsonObject
					val mediaId = ((details?.get("mediaInfo") as? JsonObject)?.get("id") as? JsonPrimitive)?.intOrNull
					if (mediaId != null) {
						NovaSeerr.http("${seerr.baseUrl}/api/v1/media/$mediaId", method = "DELETE", headers = mapOf("X-Api-Key" to seerr.apiKey))
					}
				}
			}
			Outcome.Deleted
		} catch (cancelled: CancellationException) {
			throw cancelled
		} catch (error: Exception) {
			Timber.w(error, "NOVA: delete failed for %s", item.name)
			Outcome.Failed("Couldn't delete it. Check the server is on and try again.")
		}
	}
}


/** PIN entry pop-up (on-screen number pad, works with the remote). */
@Composable
fun NovaPinDialog(
	title: String,
	subtitle: String,
	onDismiss: () -> Unit,
	onCorrect: () -> Unit,
	pin: (Context) -> String = NovaDeletePin::get,
) {
	val context = LocalContext.current
	Dialog(onDismissRequest = onDismiss) {
		NovaPinPad(
			title = title,
			subtitle = subtitle,
			onEntered = { entered -> if (entered == pin(context)) { onCorrect(); null } else "Wrong PIN, try again" },
			onCancel = onDismiss,
			onMaster = onCorrect,
		)
	}
}

/**
 * Four-digit number pad. [onEntered] gets the PIN once four digits are typed and
 * returns an error to show (the pad then clears), or null when accepted.
 */
@Composable
fun NovaPinPad(
	title: String,
	subtitle: String,
	onEntered: (String) -> String?,
	onCancel: () -> Unit,
	/** When set, a "Forgot PIN?" key lets the master code through instead (once one is set). */
	onMaster: (() -> Unit)? = null,
	/** How many digits to type (8 when choosing the master code). */
	digits: Int = NovaDeletePin.LENGTH,
) {
	val context = LocalContext.current
	var entered by remember(title) { mutableStateOf("") }
	var error by remember(title) { mutableStateOf<String?>(null) }
	var masterMode by remember(title) { mutableStateOf(false) }
	val length = if (masterMode) NovaMasterCode.LENGTH else digits
	val firstKey = remember { FocusRequester() }

	LaunchedEffect(title) {
		delay(50L)
		runCatching { firstKey.requestFocus() }
	}

	fun press(key: String) {
		when (key) {
			"⌫" -> entered = entered.dropLast(1)
			"✕" -> if (masterMode) {
				masterMode = false
				entered = ""
				error = null
			} else onCancel()
			else -> if (entered.length < length) {
				entered += key
				error = null
				if (entered.length == length) {
					val pin = entered
					entered = ""
					error = if (masterMode) {
						if (NovaMasterCode.matches(context, pin)) {
							onMaster?.invoke()
							null
						} else "Wrong master code"
					} else onEntered(pin)
				}
			}
		}
	}

	Column(
		modifier = Modifier
			.width(380.dp)
			.background(nova.surface, RoundedCornerShape(nova.corner(12)))
			.border(1.dp, nova.surfaceHigh, RoundedCornerShape(nova.corner(12)))
			.padding(22.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Text(if (masterMode) "Master code" else title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
		Spacer(Modifier.height(6.dp))
		Text(if (masterMode) "Enter the ${NovaMasterCode.LENGTH}-digit master code." else subtitle, color = nova.textMuted, fontSize = 13.sp)
		Spacer(Modifier.height(16.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
			repeat(length) { index ->
				Box(
					modifier = Modifier
						.size(16.dp)
						.background(if (index < entered.length) nova.accent else nova.surfaceHigh, CircleShape)
				)
			}
		}
		Spacer(Modifier.height(8.dp))
		Text(error ?: " ", color = Color(0xFFFF8A80), fontSize = 12.sp)
		Spacer(Modifier.height(8.dp))
		listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("✕", "0", "⌫")).forEachIndexed { r, rowKeys ->
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				rowKeys.forEachIndexed { c, key ->
					PinKey(
						label = key,
						onClick = { press(key) },
						modifier = if (r == 0 && c == 0) Modifier.focusRequester(firstKey) else Modifier,
					)
				}
			}
			Spacer(Modifier.height(10.dp))
		}
		if (onMaster != null && !masterMode && NovaMasterCode.isSet(context)) {
			ForgotKey {
				masterMode = true
				entered = ""
				error = null
			}
		}
	}
}

@Composable
private fun ForgotKey(onClick: () -> Unit) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	Button(
		onClick = onClick,
		interactionSource = interactionSource,
		shape = RoundedCornerShape(nova.corner(8)),
		colors = ButtonDefaults.colors(
			containerColor = Color.Transparent,
			contentColor = nova.textMuted,
			focusedContainerColor = nova.surfaceRaised,
			focusedContentColor = nova.textPrimary,
		),
		contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
	) {
		Text("Forgot PIN? Use master code", fontSize = 13.sp, color = if (focused) nova.textPrimary else nova.textMuted)
	}
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(nova.corner(8))
	Button(
		onClick = onClick,
		modifier = modifier
			.size(width = 84.dp, height = 52.dp)
			.then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier),
		interactionSource = interactionSource,
		shape = shape,
		colors = ButtonDefaults.colors(
			containerColor = nova.surfaceRaised,
			contentColor = nova.textPrimary,
			focusedContainerColor = nova.accent,
			focusedContentColor = nova.onAccent,
		),
		contentPadding = PaddingValues(0.dp),
	) {
		Box(modifier = Modifier.size(width = 84.dp, height = 52.dp), contentAlignment = Alignment.Center) {
			Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
		}
	}
}

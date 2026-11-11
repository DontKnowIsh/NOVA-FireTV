package org.jellyfin.androidtv.ui.browsing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import org.jellyfin.androidtv.ui.NovaPalette
import org.jellyfin.androidtv.ui.NovaTheme
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.lang.ref.WeakReference
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * "Download finished" pop-up: while NOVA is open, watches Radarr/Sonarr's queue
 * and, when something finishes, shows a small card in the top-right corner with
 * its name and plays a soft chime (silent while you're watching something).
 */
object NovaDownloadNotifier : Application.ActivityLifecycleCallbacks {
	/** A pop-up: a finished download (green ✓) or a download that needs attention (amber !). */
	private data class Finished(
		val title: String,
		val detail: String,
		val problem: Boolean = false,
		/** The film or show's poster, shown next to the tick. */
		val posterUrl: String? = null,
	)

	/** Problems already shown, so each is only announced once. */
	private val warned = HashSet<String>()

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	private var started = false
	private var resumed: WeakReference<Activity>? = null
	private val pending = ArrayDeque<Finished>()
	private val notified = HashSet<String>()
	private val visibleCards = ArrayList<View>()
	private var showing = false
	private var api: ApiClient? = null
	private val main = Handler(Looper.getMainLooper())

	/** Starts watching (once per app run). */
	fun ensureStarted(context: Context, api: ApiClient) {
		if (started) return
		val application = context.applicationContext as? Application ?: return
		started = true
		this.api = api
		application.registerActivityLifecycleCallbacks(this)
		context.findActivity()?.let { resumed = WeakReference(it) }
		scope.launch { watch(api) }
		scope.launch { watchLibrary(api) }
	}

	/**
	 * Watches Jellyfin for newly added films and episodes (a tiny check every 30
	 * seconds while NOVA is open). As soon as one appears, Home and the library
	 * pages reload, so new downloads show without reopening NOVA.
	 */
	private suspend fun watchLibrary(api: ApiClient) {
		var newest: String? = null
		while (true) {
			delay(30_000L)
			if (resumed?.get() == null) continue
			val latest = withContext(Dispatchers.IO) {
				runCatching {
					api.itemsApi.getItems(
						sortBy = listOf(ItemSortBy.DATE_CREATED),
						sortOrder = listOf(SortOrder.DESCENDING),
						includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
						recursive = true,
						limit = 1,
						enableImages = false,
						enableUserData = false,
					).content.let { result ->
						// The count as well as the newest: a film whose file carries an old
						// date (e.g. its release date) isn't "newest" but still adds one.
						"${result.totalRecordCount}|${result.items.firstOrNull()?.id}"
					}
				}.getOrNull()
			} ?: continue
			if (newest != null && latest != newest) {
				Timber.i("NOVA: new title in Jellyfin, reloading pages")
				NovaDiscoverRefresh.updates.tryEmit(Unit)
			}
			newest = latest
		}
	}

	private suspend fun watch(api: ApiClient) {
		// Radarr/Sonarr's history says exactly when a download was imported into the
		// library, so nothing is missed (the queue could finish between two checks).
		var since: Instant? = null
		while (true) {
			if (resumed?.get() == null) {
				delay(10_000L)
				continue
			}
			val seerr = runCatching { NovaSeerr.config(api) }.getOrNull()
			if (seerr == null) {
				delay(60_000L)
				continue
			}
			val services = runCatching { NovaArr.services(seerr) }.getOrNull()
			if (services == null) {
				delay(30_000L)
				continue
			}
			val checkedAt = Instant.now()
			// Overlap a little (and allow for clock differences); repeats are skipped by id.
			val from = (since ?: checkedAt).minus(Duration.ofMinutes(15))
			val imported = withContext(Dispatchers.IO) {
				services.radarr?.let { movieImports(it, from) }.orEmpty() +
					services.sonarr?.let { episodeImports(it, from) }.orEmpty()
			}
			// Downloads that are stuck or need a hand (fake copy, failed, can't import…).
			val queue = runCatching { NovaArr.downloads(services) }.getOrNull().orEmpty()
			for (download in queue) {
				val problem = problemOf(download) ?: continue
				val signature = "${download.key}|$problem"
				if (!warned.add(signature)) continue
				if (since == null) continue // already there when NOVA opened
				announce(Finished(download.title, problem, problem = true))
			}
			if (since == null) {
				// Already finished before NOVA opened: don't announce those.
				imported.forEach { notified.addAll(it.ids) }
			} else {
				val fresh = imported.filter { batch -> batch.ids.none { it in notified } }
				fresh.forEach { batch ->
					notified.addAll(batch.ids)
					announce(batch.finished)
				}
				// Radarr / Sonarr tell Jellyfin about the new file themselves (Settings →
				// Connect), and NOVA notices it in Jellyfin within 30 s (watchLibrary).
			}
			since = checkedAt
			delay(15_000L)
		}
	}

	/** One finished download (a film, an episode, or a season pack's episodes together). */
	private data class Imported(val ids: List<String>, val finished: Finished)

	private fun history(service: NovaArr.Service, from: Instant, extra: String): List<JsonObject> = runCatching {
		val body = NovaSeerr.http(
			"${service.baseUrl}/api/v3/history/since?date=$from&eventType=downloadFolderImported$extra",
			headers = mapOf("X-Api-Key" to service.apiKey),
		)
		(NovaSeerr.json.parseToJsonElement(body) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
	}.onFailure { Timber.w(it, "NOVA: import history failed for %s", service.baseUrl) }.getOrDefault(emptyList())

	private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
	private fun JsonObject.number(key: String) = (this[key] as? JsonPrimitive)?.intOrNull

	/** The poster from a Radarr movie / Sonarr series (its online copy, so it loads anywhere). */
	private fun JsonObject.poster(): String? = (this["images"] as? JsonArray).orEmpty()
		.mapNotNull { it as? JsonObject }
		.firstOrNull { it.text("coverType") == "poster" }
		?.let { it.text("remoteUrl") ?: it.text("url") }
		?.takeIf { it.startsWith("http") }

	private fun movieImports(service: NovaArr.Service, from: Instant): List<Imported> =
		history(service, from, "&includeMovie=true").map { record ->
			val movie = record["movie"] as? JsonObject
			val title = movie?.text("title") ?: record.text("sourceTitle").orEmpty()
			val year = movie?.number("year")?.takeIf { it > 0 }
			Imported(
				ids = listOf("radarr-${record.number("id")}"),
				finished = Finished(title, listOfNotNull("Film", year?.toString()).joinToString(" · "), posterUrl = movie?.poster()),
			)
		}

	private fun episodeImports(service: NovaArr.Service, from: Instant): List<Imported> =
		history(service, from, "&includeSeries=true&includeEpisode=true")
			// A season pack imports many episodes at once: one pop-up for the lot.
			.groupBy { it.text("downloadId") ?: "id-${it.number("id")}" }
			.values
			.map { records ->
				val first = records.first()
				val show = (first["series"] as? JsonObject)?.text("title") ?: first.text("sourceTitle").orEmpty()
				val detail = if (records.size == 1) {
					val episode = first["episode"] as? JsonObject
					val season = episode?.number("seasonNumber")
					val number = episode?.number("episodeNumber")
					listOfNotNull(
						if (season != null && number != null) "S%02dE%02d".format(season, number) else "TV episode",
						episode?.text("title"),
					).joinToString(" · ")
				} else {
					"${records.size} episodes"
				}
				val poster = (first["series"] as? JsonObject)?.poster()
				Imported(ids = records.map { "sonarr-${it.number("id")}" }, finished = Finished(show, detail, posterUrl = poster))
			}

	private val SUCCESS = 0xFF2E9E4F.toInt()
	private val WARNING = 0xFFF0A020.toInt()

	/** A short reason when a download needs attention, or null when it's fine. */
	private fun problemOf(download: NovaArr.Download): String? {
		val state = download.trackedState.orEmpty().lowercase()
		val status = download.status.lowercase()
		val message = download.message?.trim()?.takeIf { it.isNotEmpty() }
		return when {
			message != null && message.contains(".exe", ignoreCase = true) ->
				"Fake copy (has a program in it). Use Other copy on Downloads."
			state == "importblocked" -> message ?: "Couldn't be moved to the library. Check Radarr/Sonarr."
			state == "failedpending" || state == "failed" || status == "failed" -> message ?: "The download failed. NOVA will try another copy."
			status == "downloadclientunavailable" -> "qBittorrent isn't reachable."
			else -> null
		}
	}

	private fun announce(finished: Finished) {
		pending.addLast(finished)
		showNext()
	}

	/** Shows the queued pop-ups one after another, on whatever screen is open. */
	private fun showNext() {
		if (showing || pending.isEmpty()) return
		val activity = resumed?.get() ?: return
		showing = true
		show(activity, pending.removeFirst())
	}

	private fun show(activity: Activity, finished: Finished) {
		val root = activity.window?.decorView as? ViewGroup ?: return
		val p = NovaTheme.palette
		val density = activity.resources.displayMetrics.density
		fun dp(v: Int) = (v * density).toInt()

		val card = buildCard(activity, finished, p, density)
		val params = FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT,
			ViewGroup.LayoutParams.WRAP_CONTENT,
			Gravity.TOP or Gravity.END,
		).apply {
			topMargin = dp(24)
			rightMargin = dp(24)
		}
		runCatching { root.addView(card, params) }.onFailure {
			showing = false
			return
		}
		visibleCards.add(card)
		card.alpha = 0f
		card.translationX = dp(40).toFloat()
		card.animate().alpha(1f).translationX(0f).setDuration(300L).start()

		val watching = activity.javaClass.simpleName.let { it.contains("Playback") || it.contains("Player") }
		if (!watching) playChime(finished.problem)

		card.postDelayed({
			card.animate().alpha(0f).translationX(dp(40).toFloat()).setDuration(300L).withEndAction {
				runCatching { (card.parent as? ViewGroup)?.removeView(card) }
				visibleCards.remove(card)
				showing = false
				// Small gap so two pop-ups read as separate, then the next one.
				main.postDelayed({ showNext() }, 400L)
			}.start()
		}, 5_000L)
	}

	private fun buildCard(context: Context, finished: Finished, p: NovaPalette, density: Float): View {
		fun dp(v: Int) = (v * density).toInt()
		return LinearLayout(context).apply {
			orientation = LinearLayout.HORIZONTAL
			gravity = Gravity.CENTER_VERTICAL
			isFocusable = false
			descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
			setPadding(dp(14), dp(10), dp(18), dp(10))
			elevation = dp(12).toFloat()
			background = GradientDrawable().apply {
				setColor(p.surface.copy(alpha = 0.96f).toArgb())
				cornerRadius = p.corner(10).value * density
				setStroke(dp(1), p.surfaceHigh.toArgb())
			}

			addView(TextView(context).apply {
				text = if (finished.problem) "!" else "✓"
				setTextColor(p.onAccent.toArgb())
				setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
				typeface = Typeface.DEFAULT_BOLD
				gravity = Gravity.CENTER
				background = GradientDrawable().apply {
					shape = GradientDrawable.OVAL
					setColor(if (finished.problem) WARNING else SUCCESS)
				}
			}, LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(12) })

			// A small copy of the card's picture, just after the tick.
			finished.posterUrl?.let { url ->
				addView(org.jellyfin.androidtv.ui.AsyncImageView(context).apply {
					scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
					clipToOutline = true
					background = GradientDrawable().apply {
						setColor(p.surfaceHigh.toArgb())
						cornerRadius = dp(6).toFloat()
					}
					load(url = url)
				}, LinearLayout.LayoutParams(dp(40), dp(60)).apply { rightMargin = dp(12) })
			}

			addView(LinearLayout(context).apply {
				orientation = LinearLayout.VERTICAL
				addView(TextView(context).apply {
					text = if (finished.problem) "DOWNLOAD PROBLEM" else "DOWNLOAD FINISHED"
					setTextColor(if (finished.problem) WARNING else SUCCESS)
					setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
					typeface = Typeface.DEFAULT_BOLD
					letterSpacing = 0.12f
				})
				addView(TextView(context).apply {
					text = finished.title
					setTextColor(p.textPrimary.toArgb())
					setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
					typeface = Typeface.DEFAULT_BOLD
					maxLines = 1
					ellipsize = TextUtils.TruncateAt.END
					maxWidth = dp(320)
				})
				addView(TextView(context).apply {
					text = finished.detail
					setTextColor(p.textMuted.toArgb())
					setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
					maxLines = if (finished.problem) 2 else 1
					ellipsize = TextUtils.TruncateAt.END
					maxWidth = dp(320)
				})
			})
		}
	}

	/** A soft two-note chime, generated so it works on every Fire TV. */
	private fun playChime(problem: Boolean = false) {
		scope.launch(Dispatchers.Default) {
			runCatching {
				val rate = 44_100
				// Rising for done, falling for a problem.
				val notes = if (problem) listOf(659.3, 440.0) else listOf(880.0, 1318.5)
				val noteSamples = (rate * 0.16).toInt()
				val pcm = ShortArray(noteSamples * notes.size)
				notes.forEachIndexed { n, freq ->
					for (i in 0 until noteSamples) {
						val t = i.toDouble() / rate
						val envelope = exp(-t * 14.0) * minOf(1.0, i / 200.0)
						pcm[n * noteSamples + i] = (sin(2 * PI * freq * t) * envelope * 0.18 * Short.MAX_VALUE).toInt().toShort()
					}
				}
				@Suppress("DEPRECATION")
				val track = AudioTrack(
					AudioManager.STREAM_MUSIC,
					rate,
					AudioFormat.CHANNEL_OUT_MONO,
					AudioFormat.ENCODING_PCM_16BIT,
					pcm.size * 2,
					AudioTrack.MODE_STATIC,
				)
				track.write(pcm, 0, pcm.size)
				track.play()
				delay(800L)
				track.release()
			}.onFailure { Timber.w(it, "NOVA: chime failed") }
		}
	}

	private fun Context.findActivity(): Activity? {
		var current: Context? = this
		while (current is ContextWrapper) {
			if (current is Activity) return current
			current = current.baseContext
		}
		return null
	}

	override fun onActivityResumed(activity: Activity) {
		resumed = WeakReference(activity)
		showNext()
	}

	override fun onActivityPaused(activity: Activity) {
		if (resumed?.get() === activity) resumed = null
		// A card on the screen being left goes with it; carry on with the next one
		// on the new screen.
		val leaving = visibleCards.filter { it.context.findActivity() === activity }
		if (leaving.isNotEmpty()) {
			visibleCards.removeAll(leaving)
			leaving.forEach { card -> runCatching { (card.parent as? ViewGroup)?.removeView(card) } }
			showing = false
		}
	}

	override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
	override fun onActivityStarted(activity: Activity) = Unit
	override fun onActivityStopped(activity: Activity) = Unit
	override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
	override fun onActivityDestroyed(activity: Activity) = Unit
}

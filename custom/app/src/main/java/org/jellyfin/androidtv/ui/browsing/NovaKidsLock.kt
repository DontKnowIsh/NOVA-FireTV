package org.jellyfin.androidtv.ui.browsing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.java.KoinJavaComponent
import timber.log.Timber

/**
 * Kids lock: playing anything rated 15 / R or above asks for the kids lock PIN
 * (or the master code) first. Switch it off or change the PIN in Settings.
 */
object NovaKidsLock {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	private var pending: (() -> Unit)? = null

	/** Runs [proceed] straight away, or after the PIN when [items] need it. */
	fun guard(context: Context, items: List<BaseItemDto>, proceed: () -> Unit) {
		if (!NovaKidsPin.isEnabled(context) || items.isEmpty()) {
			proceed()
			return
		}
		scope.launch {
			val locked = withContext(Dispatchers.IO) {
				runCatching { items.take(3).any { needsPin(it) } }.getOrDefault(false)
			}
			if (!locked) {
				proceed()
				return@launch
			}
			pending = proceed
			context.startActivity(
				Intent(context, NovaUnlockActivity::class.java)
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK.takeIf { context !is android.app.Activity } ?: 0)
			)
		}
	}

	/** The rating that applies: the title's own, else the show's (for episodes / seasons). */
	suspend fun ratingOf(item: BaseItemDto): String? {
		item.officialRating?.takeIf { it.isNotBlank() }?.let { return it }
		val api = KoinJavaComponent.get<ApiClient>(ApiClient::class.java)
		val full = runCatching { api.userLibraryApi.getItem(itemId = item.id).content }.getOrNull()
		full?.officialRating?.takeIf { it.isNotBlank() }?.let { return it }
		val seriesId = full?.seriesId ?: item.seriesId ?: return null
		return runCatching { api.userLibraryApi.getItem(itemId = seriesId).content.officialRating }.getOrNull()
	}

	suspend fun needsPin(item: BaseItemDto): Boolean = isAdultRating(ratingOf(item))

	internal fun unlocked() {
		val action = pending
		pending = null
		runCatching { action?.invoke() }.onFailure { Timber.w(it, "NOVA: playback after kids lock failed") }
	}

	internal fun cancelled() {
		pending = null
	}
}

/** The kids lock PIN screen shown before an adult-rated title plays. */
class NovaUnlockActivity : FragmentActivity() {
	private var done = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ComposeView(this).apply {
			setContent {
				JellyfinTheme {
					Box(modifier = Modifier.fillMaxSize().background(nova.bg), contentAlignment = Alignment.Center) {
						NovaPinPad(
							title = "Kids lock",
							subtitle = "This is rated for adults. Enter the kids lock PIN to play it.",
							onEntered = { pin ->
								if (pin == NovaKidsPin.get(this@NovaUnlockActivity)) {
									unlock()
									null
								} else "Wrong PIN, try again"
							},
							onCancel = { finish() },
							onMaster = { unlock() },
						)
					}
				}
			}
		})
	}

	private fun unlock() {
		if (done) return
		done = true
		finish()
		NovaKidsLock.unlocked()
	}

	override fun onDestroy() {
		if (!done) NovaKidsLock.cancelled()
		super.onDestroy()
	}
}

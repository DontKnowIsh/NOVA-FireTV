package org.jellyfin.androidtv.ui.home

import org.koin.compose.koinInject
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.androidtv.ui.browsing.NovaSeerr
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * One YouTube player kept alive while a browse screen is open. Building the player
 * (WebView + YouTube's player script) is the slow part, so it is done once and
 * each trailer just swaps the video.
 */
private object NovaTrailerWebView {
	interface Listener {
		fun onPlaying(key: String)
		fun onEnded(key: String)
		fun onError(key: String, code: String)
		/** Paused or buffering: YouTube draws its play/pause icon, so cover it. */
		fun onStalled(key: String)
	}

	// Held strongly so the pre-loaded player survives between trailers; replaced
	// when a different screen (Activity) asks for it.
	private var webView: WebView? = null
	private val main = Handler(Looper.getMainLooper())

	@Volatile
	var listener: Listener? = null

	/** The shared player for this screen, created (and pre-loaded) on first use. */
	@SuppressLint("SetJavaScriptEnabled")
	fun get(context: Context): WebView {
		// Pop-ups (like the Download preview) have their own context wrapping the
		// same screen, so compare screens, not contexts: the pre-loaded player is reused
		// instead of loading YouTube all over again.
		val screen = context.screen()
		webView?.let { existing ->
			if (existing.context.screen() === screen) return existing
			runCatching { existing.destroy() }
		}
		val created = NovaNoFocusWebView(screen).apply {
			layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT,
			)
			isFocusable = false
			isFocusableInTouchMode = false
			descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
			setBackgroundColor(AndroidColor.BLACK)
			isVerticalScrollBarEnabled = false
			isHorizontalScrollBarEnabled = false
			settings.javaScriptEnabled = true
			settings.domStorageEnabled = true
			settings.mediaPlaybackRequiresUserGesture = false
			// With the Fire TV's own (mobile) browser name YouTube uses its touch-screen
			// player, which keeps a pause button in the middle of the video. The desktop
			// player has no on-screen buttons when controls are off.
			settings.userAgentString = DESKTOP_USER_AGENT
			webViewClient = object : WebViewClient() {
				// YouTube's page can crash WebView's renderer (low memory on Fire TV).
				// Unhandled, Android then kills all of NOVA; handled, only the trailer
				// stops and a fresh player is made next time.
				@android.annotation.TargetApi(26)
				override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
					Timber.w("NOVA trailer: YouTube player crashed (crash=%s)", detail.didCrash())
					(view.parent as? ViewGroup)?.removeView(view)
					if (webView === view) webView = null
					runCatching { view.destroy() }
					val key = currentKey
					if (key != null) main.post { listener?.onError(key, "renderer") }
					return true
				}
			}
			webChromeClient = WebChromeClient()
			addJavascriptInterface(
				object {
					@JavascriptInterface
					fun onPlaying(key: String) {
						main.post { listener?.onPlaying(key) }
					}

					@JavascriptInterface
					fun onEnded(key: String) {
						main.post { listener?.onEnded(key) }
					}

					@JavascriptInterface
					fun onStalled(key: String) {
						main.post { listener?.onStalled(key) }
					}

					@JavascriptInterface
					fun onError(key: String, code: String) {
						main.post { listener?.onError(key, code) }
					}
				},
				"NovaTrailer",
			)
			// Re-fit the video whenever the hero area changes size (or screen).
			// The page's own idea of its size can lag behind on Fire TV (which left a
			// black strip down the right), so NOVA passes the real size in.
			addOnLayoutChangeListener { view, l, t, r, b, ol, ot, or, ob ->
				if (r - l != or - ol || b - t != ob - ot) sendSize(view as WebView)
			}
			// Inside YouTube's own player (a frame NOVA's page can't reach): hide its
			// pause / play flash and spinner, and make the video fill the frame. Without
			// this a pause icon could stay on screen and some videos left a black strip.
			if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
				runCatching {
					WebViewCompat.addDocumentStartJavaScript(
						this,
						YOUTUBE_FRAME_SCRIPT,
						setOf("https://www.youtube.com", "https://www.youtube-nocookie.com"),
					)
				}.onFailure { Timber.w(it, "NOVA trailer: couldn't add the YouTube tidy-up") }
			}
			loadDataWithBaseURL(TRAILER_ORIGIN, PLAYER_HTML, "text/html", "utf-8", null)
		}
		webView = created
		return created
	}

	/** The trailer last asked for (reported if the player crashes). */
	private var currentKey: String? = null

	fun play(webView: WebView, key: String) {
		if (webView !== this.webView) return
		currentKey = key
		sendSize(webView)
		runCatching { webView.evaluateJavascript("novaMute($muted); novaPlay('$key');", null) }
	}

	/** Tells the page its real size (in CSS pixels). */
	private fun sendSize(webView: WebView) {
		val density = webView.resources.displayMetrics.density
		val width = webView.width / density
		val height = webView.height / density
		if (width <= 0f || height <= 0f) return
		runCatching { webView.evaluateJavascript("if (window.novaSize) novaSize($width, $height);", null) }
	}

	fun stop() {
		currentKey = null
		runCatching { webView?.evaluateJavascript("novaStop();", null) }
	}

	/** Silent while music is playing (see NovaPreviewMute). */
	var muted = false
		private set

	fun setMuted(value: Boolean) {
		muted = value
		runCatching { webView?.evaluateJavascript("novaMute($value);", null) }
	}

	/** Called when the screen's player area goes away: keep the WebView, just stop it. */
	fun detach(webView: WebView) {
		stop()
		(webView.parent as? ViewGroup)?.removeView(webView)
	}
}

/**
 * YouTube's page can ask for focus (for example when a trailer ends). If the
 * player took it, the remote would control YouTube (pausing it, showing its
 * pause button) instead of NOVA, so it is never allowed to have focus.
 */
private class NovaNoFocusWebView(context: Context) : WebView(context) {
	override fun requestFocus(direction: Int, previouslyFocusedRect: android.graphics.Rect?): Boolean = false

	override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean = false
}

/** The Activity (screen) behind a context, or the context itself. */
private fun Context.screen(): Context {
	var current: Context = this
	while (current !is android.app.Activity && current is android.content.ContextWrapper) {
		current = current.baseContext ?: break
	}
	return current
}

/** Start loading YouTube's player in the background so the first trailer starts quickly. */
@Composable
fun NovaTrailerWarmUp() {
	val context = LocalContext.current
	val api = koinInject<ApiClient>()
	LaunchedEffect(context) {
		delay(1_500L)
		// Only worth it when Discover (Seerr + JellyBridge) is set up.
		if (NovaSeerr.config(api) == null) return@LaunchedEffect
		runCatching { NovaTrailerWebView.get(context) }
	}
}

/**
 * Plays a title's YouTube trailer as the hero preview (used for Discover titles,
 * which only have JellyBridge's "favourite this show" placeholder video).
 *
 * The player stays in place while you move between titles and only the video is
 * swapped, because tearing the YouTube player down and rebuilding it froze the
 * remote for a couple of seconds on Fire TV. Pass a null [youTubeKey] to stop it
 * and hide it. It never takes remote focus, and shows [backdropUrl] until the
 * trailer is really playing; if it can't play, the artwork just stays.
 */
@Composable
fun NovaTrailerPlayer(
	youTubeKey: String?,
	backdropUrl: String?,
	modifier: Modifier = Modifier,
	onPlayingChanged: (Boolean) -> Unit = {},
	onFinished: () -> Unit = {},
	/** The video couldn't be played (blocked in apps, removed, or never started). */
	onFailed: () -> Unit = {},
) {
	val currentOnPlayingChanged by rememberUpdatedState(onPlayingChanged)
	val currentOnFinished by rememberUpdatedState(onFinished)
	val currentOnFailed by rememberUpdatedState(onFailed)
	var playing by remember(youTubeKey) { mutableStateOf(false) }
	var done by remember(youTubeKey) { mutableStateOf(false) }
	val active = youTubeKey != null && !done
	val backdropAlpha by animateFloatAsState(
		targetValue = if (playing) 0f else 1f,
		// Fade the trailer in gently, but cover it instantly when YouTube pauses or
		// buffers so its play/pause icon is never seen.
		animationSpec = tween(durationMillis = if (playing) 700 else 120),
		label = "novaTrailerBackdropAlpha",
	)

	fun finish() {
		if (done) return
		done = true
		playing = false
		NovaTrailerWebView.stop()
		currentOnPlayingChanged(false)
		currentOnFinished()
	}

	DisposableEffect(youTubeKey) {
		val key = youTubeKey
		if (key == null) {
			NovaTrailerWebView.stop()
			currentOnPlayingChanged(false)
			return@DisposableEffect onDispose { }
		}
		val listener = object : NovaTrailerWebView.Listener {
			override fun onPlaying(key: String) {
				if (key != youTubeKey || done) return
				playing = true
				currentOnPlayingChanged(true)
			}

			override fun onEnded(key: String) {
				if (key == youTubeKey) finish()
			}

			override fun onStalled(key: String) {
				if (key != youTubeKey || done) return
				playing = false
				currentOnPlayingChanged(false)
			}

			override fun onError(key: String, code: String) {
				if (key != youTubeKey) return
				Timber.w("NOVA trailer %s could not play (YouTube error %s)", key, code)
				finish()
				currentOnFailed()
			}
		}
		NovaTrailerWebView.listener = listener
		onDispose {
			if (NovaTrailerWebView.listener === listener) NovaTrailerWebView.listener = null
			currentOnPlayingChanged(false)
		}
	}

	// Give up if YouTube hasn't started within 15 s; stop after 2.5 minutes.
	LaunchedEffect(youTubeKey, playing) {
		if (youTubeKey == null) return@LaunchedEffect
		if (!playing) {
			delay(15_000L)
			if (!playing) {
				finish()
				currentOnFailed()
			}
		} else {
			delay(150_000L)
			finish()
		}
	}

	DisposableEffect(Unit) {
		onDispose { NovaTrailerWebView.stop() }
	}

	Box(modifier = modifier.clipToBounds()) {
		AndroidView(
			modifier = Modifier
				.fillMaxSize()
				.focusProperties { canFocus = false },
			factory = { context ->
				NovaTrailerWebView.get(context).also { webView ->
					(webView.parent as? ViewGroup)?.removeView(webView)
				}
			},
			update = { webView ->
				val key = youTubeKey
				if (key != null && !done) {
					webView.visibility = View.VISIBLE
					NovaTrailerWebView.play(webView, key)
				} else {
					webView.visibility = View.INVISIBLE
				}
			},
			onRelease = { webView -> NovaTrailerWebView.detach(webView) },
		)

		if (active && backdropUrl != null) {
			HomeHeroArtwork(
				url = backdropUrl,
				modifier = Modifier
					.fillMaxSize()
					.alpha(backdropAlpha),
			)
		}
	}
}

/** Runs inside YouTube's player frame: hides its play/pause flash, spinner and big play button. */
private const val YOUTUBE_FRAME_SCRIPT = """
(function () {
	if (window.__novaTidy) return;
	window.__novaTidy = true;
	var css =
		'.ytp-bezel, .ytp-bezel-text-wrapper, .ytp-pause-overlay, .ytp-large-play-button, ' +
		'.ytp-spinner, .ytp-cued-thumbnail-overlay { display: none !important; opacity: 0 !important; }';
	function add() {
		var style = document.createElement('style');
		style.textContent = css;
		(document.head || document.documentElement).appendChild(style);
	}
	if (document.documentElement) add(); else document.addEventListener('DOMContentLoaded', add);
})();
"""

private const val DESKTOP_USER_AGENT =
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

// YouTube refuses embeds that have no referring site, so the page is given one.
private const val TRAILER_ORIGIN = "https://nova.firetv.app"

private val PLAYER_HTML = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="strict-origin-when-cross-origin">
<link rel="preconnect" href="https://www.youtube.com">
<link rel="preconnect" href="https://i.ytimg.com">
<link rel="preconnect" href="https://www.google.com">
<style>
	html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #000; }
	/* Cover the hero like the normal preview: keep 16:9 and crop the excess,
	   which also hides YouTube's title bar and logo. */
	#player {
		position: absolute; top: 50%; left: 50%;
		width: 100%; height: 100%;
		transform: translate(-50%, -50%) scale(1.06);
		pointer-events: none; border: 0;
	}
</style>
</head>
<body>
<div id="player"></div>
<script>
	var player = null, ready = false, current = null, pending = null, fitW = 0, fitH = 0, realW = 0, realH = 0;
	// The real size of NOVA's trailer area, sent in from Android.
	function novaSize(w, h) { realW = w; realH = h; novaFit(); }
	var tag = document.createElement('script');
	tag.src = 'https://www.youtube.com/iframe_api';
	document.head.appendChild(tag);

	// Size the player from the real page size every time. The same player moves
	// between screens with different hero sizes, and CSS vh / YouTube's own size
	// could stay stuck at the old one, which drew the video too high.
	function novaFit() {
		var W = realW || window.innerWidth || document.documentElement.clientWidth;
		var H = realH || window.innerHeight || document.documentElement.clientHeight;
		if (!W || !H) return;
		var w = Math.ceil(Math.max(W, H * 16 / 9)), h = Math.ceil(Math.max(W * 9 / 16, H));
		// Only touch the player when the size really changed: resizing a playing
		// YouTube video makes it stutter (it looked like pause / play by itself).
		if (w === fitW && h === fitH && ready) return;
		fitW = w; fitH = h;
		var el = document.getElementById('player');
		if (el) { el.style.width = w + 'px'; el.style.height = h + 'px'; }
		if (ready) { try { player.setSize(w, h); } catch (e) {} }
	}
	window.addEventListener('resize', novaFit);
	// The page size can settle a moment after the player moves; check now and then
	// (novaFit does nothing when the size hasn't changed).
	setInterval(novaFit, 1000);
	function novaPlay(key) {
		novaFit();
		if (current === key && ready) return;
		current = key;
		shown = null;
		if (ready) {
			// Silent until the picture is showing, so sound and picture start together.
			try { player.mute(); } catch (e) {}
			player.loadVideoById({ videoId: key, suggestedQuality: 'hd720' });
		}
		else pending = key;
	}
	var muted = false, shown = null;
	function novaMute(m) {
		muted = m;
		if (!ready) return;
		try {
			if (m || shown !== current) player.mute(); else player.unMute();
		} catch (e) {}
	}
	function novaStop() {
		current = null; pending = null;
		if (ready) { try { player.stopVideo(); } catch (e) {} }
	}
	function onYouTubeIframeAPIReady() {
		player = new YT.Player('player', {
			host: 'https://www.youtube.com',
			playerVars: {
				autoplay: 1, controls: 0, disablekb: 1, fs: 0, rel: 0,
				iv_load_policy: 3, modestbranding: 1, playsinline: 1,
				cc_load_policy: 0, origin: '$TRAILER_ORIGIN'
			},
			events: {
				onReady: function () {
					ready = true;
					novaFit();
					if (pending) { var k = pending; pending = null; current = null; novaPlay(k); }
				},
				onStateChange: function (e) {
					if (!current) return;
					var key = current;
					if (e.data === 1) {
						// YouTube flashes its play icon as a video starts; reveal it a moment
						// later, and turn the sound on at the same time.
						setTimeout(function () {
							if (current === key && player.getPlayerState() === 1 && shown !== key) {
								shown = key;
								if (!muted) { try { player.unMute(); } catch (e) {} }
								NovaTrailer.onPlaying(key);
							}
						}, 450);
					}
					// Paused by YouTube itself: just carry on. Short buffering is left
					// alone too; covering the video each time made it look like it kept
					// pausing and playing.
					if (e.data === 2) player.playVideo();
					if (e.data === 0) NovaTrailer.onEnded(key);
				},
				onError: function (e) { if (current) NovaTrailer.onError(current, String(e.data)); }
			}
		});
	}
</script>
</body>
</html>
""".trimIndent()


/** Lets [NovaPreviewMute] silence the YouTube trailer player. */
internal fun novaSetTrailerMuted(muted: Boolean) = NovaTrailerWebView.setMuted(muted)

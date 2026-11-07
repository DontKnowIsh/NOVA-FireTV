package org.jellyfin.androidtv.ui.iptv

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.TextUtils
import android.util.Xml
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.AsyncImageView
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * NOVA Live TV (IPTV) screen.
 *
 * No provider details are hard-coded. The playlist URL is entered on-device
 * and stored only in this app's private SharedPreferences.
 */
class NovaIptvActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("nova_iptv", MODE_PRIVATE) }

    private lateinit var root: FrameLayout
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    /** A channel is playing full screen (NOVA's navigation click stays quiet). */
    val isWatching: Boolean get() = playerView != null
    private var previewPlayer: ExoPlayer? = null
    private var previewPlayerView: PlayerView? = null
    private var previewJob: Job? = null
    private var currentPlayingChannel: IptvChannel? = null
    private var playbackEpgOverlay: View? = null
    private var playbackEpgJob: Job? = null
    private var playbackRetryJob: Job? = null
    private var iptvVolume: Float = 1f
    private var volumeOverlay: View? = null
    private var volumeOverlayJob: Job? = null
    private var searchOpen: Boolean = false

    // Keep the parsed playlist in memory so returning from a channel is instant.
    private var loadedChannels: List<IptvChannel> = emptyList()
    private var loadedPlaylistUrl: String? = null
    private var loadedEpgUrl: String? = null
    private var epgByChannelId: Map<String, NowNextProgramme> = emptyMap()
    private var selectedGroup: String = ALL_CHANNELS
    private var selectedChannelPosition: Int = 0
    private var selectedChannelUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        // IPTV behaves like a set-top box: keep the TV awake while this
        // dedicated Live TV activity is open.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this)
        setContentView(root)

        selectedGroup = prefs.getString(KEY_SELECTED_GROUP, ALL_CHANNELS) ?: ALL_CHANNELS
        selectedChannelPosition = prefs.getInt(KEY_SELECTED_POSITION, 0)
        selectedChannelUrl = prefs.getString(KEY_SELECTED_CHANNEL_URL, null)
        val savedIptvVolume = prefs.getFloat(KEY_IPTV_VOLUME, 1f).coerceIn(0f, 1f)
        iptvVolume = (Math.round(savedIptvVolume * 10f) / 10f).coerceIn(0f, 1f)
        prefs.edit().putFloat(KEY_IPTV_VOLUME, iptvVolume).apply()
        restoreCachedPlaylistOrSetup()
    }

    override fun onDestroy() {
        releasePlayer()
        releasePreviewPlayer()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (player != null) {
            releasePlayer()
            showGuide()
        } else if (searchOpen) {
            searchOpen = false
            showGuide()
        } else {
            super.onBackPressed()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (
            player != null &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0
        ) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    zapChannel(-1)
                    return true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    zapChannel(1)
                    return true
                }

                KeyEvent.KEYCODE_DPAD_UP -> {
                    adjustIptvVolume(0.10f)
                    return true
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    adjustIptvVolume(-0.10f)
                    return true
                }

                KeyEvent.KEYCODE_VOLUME_UP -> {
                    adjustIptvVolume(0.10f)
                    return true
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    adjustIptvVolume(-0.10f)
                    return true
                }

                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    currentPlayingChannel?.let { showPlaybackEpg(it) }
                    return true
                }

                KeyEvent.KEYCODE_MENU -> {
                    currentPlayingChannel?.let { channel ->
                        val added = toggleFavourite(channel)
                        showFavouriteStatus(added)
                    }
                    return true
                }
            }
        }

        if (super.dispatchKeyEvent(event)) return true

        // Nothing on screen used the arrow, so Android would move the highlight itself
        // and play its own tick on top of NOVA's (two sounds per press). Move it here
        // quietly instead; NOVA's single tick has already played.
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            else -> return false
        }
        val focused = currentFocus
        if (player != null || focused == null) return false
        if (event.action == KeyEvent.ACTION_DOWN) focused.focusSearch(direction)?.requestFocus(direction)
        return true
    }

    private fun favouriteUrls(): MutableSet<String> =
        prefs.getStringSet(KEY_FAVOURITE_URLS, emptySet())
            ?.toMutableSet()
            ?: mutableSetOf()

    private fun isFavourite(channel: IptvChannel): Boolean =
        favouriteUrls().contains(channel.url)

    private fun toggleFavourite(channel: IptvChannel): Boolean {
        val favourites = favouriteUrls()
        val added = if (channel.url in favourites) {
            favourites.remove(channel.url)
            false
        } else {
            favourites.add(channel.url)
            true
        }
        prefs.edit().putStringSet(KEY_FAVOURITE_URLS, favourites).apply()
        return added
    }

    private fun showFavouriteStatus(added: Boolean) {
        val label = TextView(this).apply {
            text = if (added) "★  Added to Favourites" else "☆  Removed from Favourites"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = roundedBackground(
                Color.argb(235, 12, 12, 12),
                themeAccent(),
                1
            )
        }

        root.addView(
            label,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = dp(24) }
        )

        lifecycleScope.launch {
            delay(1400)
            label.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction { (label.parent as? ViewGroup)?.removeView(label) }
                .start()
        }
    }

    private fun playlistCacheFile() = File(filesDir, PLAYLIST_CACHE_FILE)
    private fun epgCacheFile() = File(filesDir, EPG_CACHE_FILE)
    private fun channelIndexCacheFile() = File(filesDir, CHANNEL_INDEX_CACHE_FILE)
    private fun epgSnapshotFile() = File(filesDir, EPG_SNAPSHOT_FILE)

    private fun saveChannelIndexCache(channels: List<IptvChannel>) {
        runCatching {
            val array = JSONArray()
            channels.forEach { channel ->
                array.put(
                    JSONObject()
                        .put("name", channel.name)
                        .put("url", channel.url)
                        .put("group", channel.group)
                        .put("logoUrl", channel.logoUrl ?: JSONObject.NULL)
                        .put("tvgId", channel.tvgId ?: JSONObject.NULL)
                )
            }
            channelIndexCacheFile().writeText(array.toString())
        }
    }

    private fun loadChannelIndexCache(): List<IptvChannel> =
        runCatching {
            val array = JSONArray(channelIndexCacheFile().readText())
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        IptvChannel(
                            name = item.optString("name", "Channel"),
                            url = item.optString("url"),
                            group = item.optString("group", "Other"),
                            logoUrl = item.optString("logoUrl").takeIf { it.isNotBlank() && it != "null" },
                            tvgId = item.optString("tvgId").takeIf { it.isNotBlank() && it != "null" },
                        )
                    )
                }
            }.filter { it.url.isNotBlank() }
        }.getOrDefault(emptyList())

    private fun saveEpgSnapshot(epg: Map<String, NowNextProgramme>) {
        runCatching {
            val root = JSONObject()
            epg.forEach { (id, schedule) ->
                fun programmeJson(programme: ProgrammeInfo?): Any {
                    if (programme == null) return JSONObject.NULL
                    return JSONObject()
                        .put("title", programme.title)
                        .put("start", programme.start)
                        .put("stop", programme.stop)
                        .put("description", programme.description ?: JSONObject.NULL)
                }

                root.put(
                    id,
                    JSONObject()
                        .put("now", programmeJson(schedule.now))
                        .put("next", programmeJson(schedule.next))
                )
            }
            epgSnapshotFile().writeText(root.toString())
        }
    }

    private fun loadEpgSnapshot(): Map<String, NowNextProgramme> =
        runCatching {
            val root = JSONObject(epgSnapshotFile().readText())

            fun programmeFromJson(value: Any?): ProgrammeInfo? {
                val item = value as? JSONObject ?: return null
                return ProgrammeInfo(
                    title = item.optString("title", "Programme"),
                    start = item.optLong("start", 0L),
                    stop = item.optLong("stop", 0L),
                    description = item.optString("description").takeIf { it.isNotBlank() && it != "null" },
                )
            }

            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val item = root.optJSONObject(id) ?: continue
                    put(
                        id,
                        NowNextProgramme(
                            now = programmeFromJson(item.opt("now")),
                            next = programmeFromJson(item.opt("next")),
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())

    private fun restoreCachedPlaylistOrSetup() {
        val savedUrl = prefs.getString(KEY_PLAYLIST_URL, "").orEmpty()
        val playlistCache = playlistCacheFile()

        if (savedUrl.isBlank() || !playlistCache.exists()) {
            showSetup()
            return
        }

        showLoadingState()

        lifecycleScope.launch {
            val cachedChannels = withContext(Dispatchers.IO) {
                if (channelIndexCacheFile().exists()) loadChannelIndexCache() else emptyList()
            }

            val channels = if (cachedChannels.isNotEmpty()) {
                cachedChannels
            } else {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val text = playlistCache.readText()
                        channelsFromPlaylistText(text, savedUrl).also(::saveChannelIndexCache)
                    }.getOrDefault(emptyList())
                }
            }

            if (channels.isEmpty()) {
                showSetup()
                return@launch
            }

            loadedPlaylistUrl = savedUrl
            loadedChannels = channels
            loadedEpgUrl = prefs.getString(KEY_EPG_URL, "").orEmpty().ifBlank { null }

            // Load only the tiny Now/Next snapshot before drawing the guide.
            // The full XMLTV file can be huge, so reparsing it no longer blocks entry.
            epgByChannelId = withContext(Dispatchers.IO) {
                if (epgSnapshotFile().exists()) loadEpgSnapshot() else emptyMap()
            }

            val groups = channels.map { it.group }.toSet()
            if (
                selectedGroup != ALL_CHANNELS &&
                selectedGroup != FAVOURITES &&
                selectedGroup !in groups
            ) {
                selectedGroup = ALL_CHANNELS
            }

            showGuide()

            // Refresh cached XMLTV in the background after the guide is already usable.
            if (epgCacheFile().exists()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val refreshed = runCatching {
                        parseXmltv(epgCacheFile(), channels)
                    }.getOrDefault(emptyMap())

                    if (refreshed.isNotEmpty()) {
                        epgByChannelId = refreshed
                        saveEpgSnapshot(refreshed)
                    }
                }
            }
        }
    }

    private fun addIptvSetupBrand(page: LinearLayout) {
        page.addView(ImageView(this).apply {
            setImageResource(R.drawable.nova_n_mark)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "NOVA"
        }, LinearLayout.LayoutParams(dp(105), dp(66)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        page.addView(TextView(this).apply {
            text = "NOVA"
            gravity = Gravity.CENTER
            textSize = 26f
            letterSpacing = 0.40f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        page.addView(TextView(this).apply {
            text = "YOUR MEDIA UNIVERSE"
            gravity = Gravity.CENTER
            textSize = 9f
            letterSpacing = 0.34f
            setTextColor(Color.rgb(233, 231, 234))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        })
    }

    private fun showIptvSetupPage(page: LinearLayout) {
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.nova_setup_space)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = null
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Keep the full form reachable on shorter Fire TV displays and when
        // large text is enabled, without changing focus or input behaviour.
        val viewport = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            setPadding(dp(8), dp(8), dp(8), dp(16))
            addView(page, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        val contentWidth = minOf((resources.displayMetrics.widthPixels * 0.64f).toInt(), dp(700))
        root.addView(viewport, FrameLayout.LayoutParams(
            contentWidth,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER_HORIZONTAL
        ))
    }

    private fun setupButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(Color.WHITE)
        background = getDrawable(R.drawable.nova_concept_card)
        setOnClickListener { onClick() }
    }

    private fun showLoadingState() {
        releasePreviewPlayer()
        root.removeAllViews()

        val loading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(6, 6, 6))
        }

        loading.addView(TextView(this).apply {
            text = "NOVA LIVE TV"
            setTextColor(Color.WHITE)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
        })

        loading.addView(TextView(this).apply {
            text = "Opening your TV guide…"
            setTextColor(Color.rgb(145, 145, 145))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(18))
        })

        loading.addView(ProgressBar(this))

        root.addView(
            loading,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun showSetup() {
        releasePlayer()
        releasePreviewPlayer()
        root.removeAllViews()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(14))
        }
        addIptvSetupBrand(page)

        page.addView(TextView(this).apply {
            text = "NOVA IPTV"
            setTextColor(Color.WHITE)
            textSize = 31f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })

        page.addView(TextView(this).apply {
            text = "Choose how you connect to Live TV"
            setTextColor(Color.rgb(208, 205, 210))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(18))
        })

        val source = prefs.getString(KEY_SOURCE_TYPE, "").orEmpty()
        if (loadedChannels.isNotEmpty()) {
            val current = when (source) {
                SOURCE_XTREAM -> "Xtream Login"
                SOURCE_M3U -> "M3U / XMLTV"
                else -> "Live TV"
            }

            val currentCard = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(12), dp(18), dp(12))
                background = getDrawable(R.drawable.nova_concept_card)
            }
            currentCard.addView(TextView(this).apply {
                text = "Current connection:  $current"
                setTextColor(Color.rgb(205, 205, 205))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val guideButton = setupButton("Back to Guide") { showGuide() }
            currentCard.addView(guideButton, LinearLayout.LayoutParams(dp(150), dp(42)))
            page.addView(
                currentCard,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(18)
                }
            )
        }

        page.addView(TextView(this).apply {
            text = "CONNECTION TYPES"
            setTextColor(Color.rgb(205, 201, 209))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            setPadding(dp(2), 0, 0, dp(9))
        })

        fun connectionCard(
            title: String,
            description: String,
            onClick: () -> Unit,
        ): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true
                isClickable = true
                setPadding(dp(20), dp(16), dp(20), dp(16))
                background = getDrawable(R.drawable.nova_concept_card)
                setOnClickListener { onClick() }
                setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                        onClick()
                        true
                    } else false
                }
            }

            card.addView(TextView(this).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
            })
            card.addView(TextView(this).apply {
                text = description
                setTextColor(Color.rgb(188, 184, 194))
                textSize = 12f
                setPadding(0, dp(5), 0, 0)
            })
            return card
        }

        val m3uCard = connectionCard(
            "M3U / XMLTV",
            "Use a playlist URL. Add an XMLTV programme-guide URL if your playlist does not supply one.",
        ) { showM3uSetup() }
        page.addView(
            m3uCard,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        )

        val xtreamCard = connectionCard(
            "Xtream Login",
            "Use the server URL, username and password supplied by your TV service. Channels, categories and EPG are loaded automatically.",
        ) { showXtreamSetup() }
        page.addView(
            xtreamCard,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        page.addView(TextView(this).apply {
            text = "Login details are entered on this Fire TV only and are not stored in GitHub."
            setTextColor(Color.rgb(190, 185, 194))
            textSize = 11f
            setPadding(0, dp(22), 0, 0)
        })

        showIptvSetupPage(page)

        m3uCard.post { m3uCard.requestFocus() }
    }

    private fun showM3uSetup() {
        releasePlayer()
        releasePreviewPlayer()
        root.removeAllViews()

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(14))
        }
        addIptvSetupBrand(outer)

        val title = TextView(this).apply {
            text = "M3U / XMLTV"
            setTextColor(Color.WHITE)
            textSize = 31f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        outer.addView(title)

        val subtitle = TextView(this).apply {
            text = "Playlist URL and optional XMLTV programme guide."
            setTextColor(Color.rgb(208, 205, 210))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(18))
        }
        outer.addView(subtitle)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val playlistInput = EditText(this).apply {
            hint = "M3U / M3U8 playlist URL"
            setText(loadedPlaylistUrl ?: prefs.getString(KEY_PLAYLIST_URL, "").orEmpty())
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(182, 178, 188))
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = getDrawable(R.drawable.nova_concept_input)
        }

        inputRow.addView(
            playlistInput,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(12)
            }
        )

        val loadButton = Button(this).apply {
            text = if (loadedChannels.isEmpty()) "Load Playlist" else "Reload Playlist"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            background = getDrawable(R.drawable.nova_concept_connect)
        }
        inputRow.addView(loadButton, LinearLayout.LayoutParams(dp(180), dp(54)))
        outer.addView(inputRow)

        outer.addView(TextView(this).apply {
            text = "PROGRAMME GUIDE (XMLTV)"
            setTextColor(Color.rgb(205, 201, 209))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.11f
            setPadding(0, dp(18), 0, dp(7))
        })

        val epgInput = EditText(this).apply {
            hint = "EPG / XMLTV URL (.xml or .xml.gz) — optional if playlist supplies one"
            setText(loadedEpgUrl ?: prefs.getString(KEY_EPG_URL, "").orEmpty())
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(182, 178, 188))
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = getDrawable(R.drawable.nova_concept_input)
        }
        outer.addView(
            epgInput,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
        )

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(12))
        }

        val progress = ProgressBar(this).apply { visibility = View.GONE }
        statusRow.addView(progress, LinearLayout.LayoutParams(dp(28), dp(28)).apply {
            marginEnd = dp(10)
        })

        val status = TextView(this).apply {
            setTextColor(Color.rgb(224, 220, 227))
            textSize = 13f
            text = when {
                loadedChannels.isNotEmpty() -> "${loadedChannels.size} channels already loaded."
                playlistInput.text.isNullOrBlank() -> "Enter a playlist URL to begin."
                else -> "Saved playlist ready to load."
            }
        }
        statusRow.addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (loadedChannels.isNotEmpty()) {
            val guideButton = Button(this).apply {
                text = "Back to Guide"
                isAllCaps = false
                textSize = 12f
                setTextColor(Color.WHITE)
                background = getDrawable(R.drawable.nova_concept_card)
                setOnClickListener { showGuide() }
            }
            statusRow.addView(guideButton, LinearLayout.LayoutParams(dp(150), dp(42)).apply {
                marginEnd = dp(10)
            })
        }

        val clearButton = Button(this).apply {
            text = "Clear"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.WHITE)
            background = getDrawable(R.drawable.nova_concept_card)
            setOnClickListener {
                prefs.edit()
                    .remove(KEY_PLAYLIST_URL)
                    .remove(KEY_SELECTED_GROUP)
                    .remove(KEY_SELECTED_POSITION)
                    .remove(KEY_SELECTED_CHANNEL_URL)
                    .remove(KEY_EPG_URL)
                    .remove(KEY_SOURCE_TYPE)
                    .apply()
                runCatching { playlistCacheFile().delete() }
                runCatching { epgCacheFile().delete() }
                loadedPlaylistUrl = null
                loadedEpgUrl = null
                loadedChannels = emptyList()
                epgByChannelId = emptyMap()
                selectedGroup = ALL_CHANNELS
                selectedChannelPosition = 0
                selectedChannelUrl = null
                playlistInput.setText("")
                epgInput.setText("")
                status.text = "Saved playlist and programme guide cleared."
            }
        }
        statusRow.addView(clearButton, LinearLayout.LayoutParams(dp(100), dp(42)))
        outer.addView(statusRow)

        val info = TextView(this).apply {
            text = "Playlist details stay on this Fire TV only. NOVA does not store IPTV provider details in GitHub."
            setTextColor(Color.rgb(190, 185, 194))
            textSize = 12f
            setPadding(0, dp(6), 0, 0)
        }
        outer.addView(info)

        val connectionTypesButton = setupButton("Connection Types") { showSetup() }
        outer.addView(
            connectionTypesButton,
            LinearLayout.LayoutParams(dp(170), dp(42)).apply {
                topMargin = dp(16)
            }
        )

        loadButton.setOnClickListener {
            val url = playlistInput.text.toString().trim()
            if (url.isBlank()) {
                status.text = "Enter a playlist URL first."
                playlistInput.requestFocus()
                return@setOnClickListener
            }

            val requestedEpgUrl = epgInput.text.toString().trim()
            prefs.edit()
                .putString(KEY_SOURCE_TYPE, SOURCE_M3U)
                .putString(KEY_PLAYLIST_URL, url)
                .putString(KEY_EPG_URL, requestedEpgUrl)
                .apply()
            progress.visibility = View.VISIBLE
            status.text = "Loading playlist…"
            loadButton.isEnabled = false

            lifecycleScope.launch {
                val result = runCatching { loadPlaylist(url) }
                progress.visibility = View.GONE
                loadButton.isEnabled = true

                result.onSuccess { channels ->
                    if (channels.isEmpty()) {
                        status.text = "No playable channels were found in this playlist."
                    } else {
                        loadedChannels = channels
                        loadedPlaylistUrl = url
                        selectedGroup = ALL_CHANNELS
                        selectedChannelPosition = 0

                        val autoEpg = prefs.getString(KEY_EPG_URL, "").orEmpty()
                        val epgUrl = requestedEpgUrl.ifBlank { autoEpg }
                        loadedEpgUrl = epgUrl.ifBlank { null }

                        if (epgUrl.isNotBlank()) {
                            progress.visibility = View.VISIBLE
                            status.text = "Loading programme guide…"
                            val guide = withContext(Dispatchers.IO) {
                                runCatching { downloadAndParseEpg(epgUrl, channels) }.getOrDefault(emptyMap())
                            }
                            progress.visibility = View.GONE
                            epgByChannelId = guide
                        } else {
                            epgByChannelId = emptyMap()
                        }

                        showGuide()
                    }
                }.onFailure { error ->
                    status.text = "Could not load playlist: ${error.message ?: "Unknown error"}"
                }
            }
        }

        showIptvSetupPage(outer)

        playlistInput.post {
            if (playlistInput.text.isNullOrBlank()) playlistInput.requestFocus()
            else loadButton.requestFocus()
        }
    }

    private fun showXtreamSetup() {
        releasePlayer()
        releasePreviewPlayer()
        root.removeAllViews()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(14))
        }
        addIptvSetupBrand(page)

        page.addView(TextView(this).apply {
            text = "Xtream Login"
            setTextColor(Color.WHITE)
            textSize = 31f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })

        page.addView(TextView(this).apply {
            text = "Enter the three details supplied by your TV service. A port is not required if your server URL does not have one."
            setTextColor(Color.rgb(208, 205, 210))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(14))
        })

        fun field(
            hintText: String,
            value: String,
            password: Boolean = false,
        ) = EditText(this).apply {
            hint = hintText
            setText(value)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(182, 178, 188))
            setSingleLine(true)
            inputType = if (password) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT
            }
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = getDrawable(R.drawable.nova_concept_input)
        }

        page.addView(TextView(this).apply {
            text = "SERVER URL"
            setTextColor(Color.rgb(205, 201, 209))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.11f
            setPadding(0, 0, 0, dp(6))
        })
        val serverInput = field(
            "https://example.com",
            prefs.getString(KEY_XTREAM_SERVER, "").orEmpty(),
        )
        page.addView(serverInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))

        page.addView(TextView(this).apply {
            text = "USERNAME"
            setTextColor(Color.rgb(205, 201, 209))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.11f
            setPadding(0, dp(14), 0, dp(6))
        })
        val usernameInput = field(
            "Username",
            prefs.getString(KEY_XTREAM_USERNAME, "").orEmpty(),
        )
        page.addView(usernameInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))

        page.addView(TextView(this).apply {
            text = "PASSWORD"
            setTextColor(Color.rgb(205, 201, 209))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.11f
            setPadding(0, dp(14), 0, dp(6))
        })
        val passwordInput = field(
            "Password",
            prefs.getString(KEY_XTREAM_PASSWORD, "").orEmpty(),
            password = true,
        )
        page.addView(passwordInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(18), 0, 0)
        }
        val progress = ProgressBar(this).apply { visibility = View.GONE }
        statusRow.addView(progress, LinearLayout.LayoutParams(dp(28), dp(28)).apply {
            marginEnd = dp(10)
        })

        val status = TextView(this).apply {
            text = if (prefs.getString(KEY_SOURCE_TYPE, "") == SOURCE_XTREAM && loadedChannels.isNotEmpty()) {
                "${loadedChannels.size} channels loaded from Xtream."
            } else {
                "Ready to connect."
            }
            setTextColor(Color.rgb(224, 220, 227))
            textSize = 12f
        }
        statusRow.addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val connectButton = Button(this).apply {
            text = "Connect"
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            background = getDrawable(R.drawable.nova_concept_connect)
        }
        statusRow.addView(connectButton, LinearLayout.LayoutParams(dp(130), dp(44)).apply {
            marginStart = dp(12)
        })
        page.addView(statusRow)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }

        val connectionTypesButton = setupButton("Connection Types") { showSetup() }
        actionRow.addView(connectionTypesButton, LinearLayout.LayoutParams(dp(170), dp(42)))

        if (loadedChannels.isNotEmpty()) {
            val guideButton = setupButton("Back to Guide") { showGuide() }
            actionRow.addView(guideButton, LinearLayout.LayoutParams(dp(150), dp(42)).apply {
                marginStart = dp(10)
            })
        }

        val clearButton = setupButton("Clear Xtream Login") {
            prefs.edit()
                .remove(KEY_XTREAM_SERVER)
                .remove(KEY_XTREAM_USERNAME)
                .remove(KEY_XTREAM_PASSWORD)
                .apply()
            serverInput.setText("")
            usernameInput.setText("")
            passwordInput.setText("")
            status.text = "Saved Xtream login cleared."
        }
        actionRow.addView(clearButton, LinearLayout.LayoutParams(dp(175), dp(42)).apply {
            marginStart = dp(10)
        })
        page.addView(actionRow)

        page.addView(TextView(this).apply {
            text = "NOVA will load the service's own Live TV categories, channel logos and XMLTV programme guide automatically."
            setTextColor(Color.rgb(190, 185, 194))
            textSize = 11f
            setPadding(0, dp(18), 0, 0)
        })

        connectButton.setOnClickListener {
            val server = normaliseXtreamServer(serverInput.text.toString())
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString()

            when {
                server.isBlank() -> {
                    status.text = "Enter the server URL."
                    serverInput.requestFocus()
                }
                username.isBlank() -> {
                    status.text = "Enter the username."
                    usernameInput.requestFocus()
                }
                password.isBlank() -> {
                    status.text = "Enter the password."
                    passwordInput.requestFocus()
                }
                else -> {
                    progress.visibility = View.VISIBLE
                    connectButton.isEnabled = false
                    status.text = "Connecting and loading Live TV…"

                    lifecycleScope.launch {
                        val result = runCatching {
                            loadXtream(server, username, password)
                        }

                        progress.visibility = View.GONE
                        connectButton.isEnabled = true

                        result.onSuccess { xtream ->
                            loadedChannels = xtream.channels
                            loadedPlaylistUrl = server
                            loadedEpgUrl = xtream.epgUrl
                            epgByChannelId = xtream.epg
                            selectedGroup = ALL_CHANNELS
                            selectedChannelPosition = 0
                            selectedChannelUrl = null

                            prefs.edit()
                                .putString(KEY_SOURCE_TYPE, SOURCE_XTREAM)
                                .putString(KEY_XTREAM_SERVER, server)
                                .putString(KEY_XTREAM_USERNAME, username)
                                .putString(KEY_XTREAM_PASSWORD, password)
                                .putString(KEY_PLAYLIST_URL, server)
                                .putString(KEY_EPG_URL, xtream.epgUrl)
                                .remove(KEY_SELECTED_CHANNEL_URL)
                                .putString(KEY_SELECTED_GROUP, ALL_CHANNELS)
                                .putInt(KEY_SELECTED_POSITION, 0)
                                .apply()

                            withContext(Dispatchers.IO) {
                                playlistCacheFile().writeText(xtream.generatedM3u)
                                saveChannelIndexCache(xtream.channels)
                                saveEpgSnapshot(xtream.epg)
                            }

                            showGuide()
                        }.onFailure { error ->
                            status.text = "Could not connect: ${error.message ?: "Unknown error"}"
                        }
                    }
                }
            }
        }

        showIptvSetupPage(page)

        serverInput.post {
            if (serverInput.text.isNullOrBlank()) serverInput.requestFocus()
            else connectButton.requestFocus()
        }
    }

    private fun showGuide() {
        searchOpen = false
        if (loadedChannels.isEmpty()) {
            showSetup()
            return
        }

        releasePreviewPlayer()
        root.removeAllViews()

        // Everything takes its colours from NOVA's theme (Settings → Theme).
        val accent = themeAccent()
        val bg = novaColours.bg.toArgb()

        // Page: the theme's background with a soft glow of its accent colour.
        val page = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = resources.displayMetrics.widthPixels * 0.75f
                setGradientCenter(0.78f, 0.3f)
                colors = intArrayOf(blend(accent, bg, 0.22f), blend(accent, bg, 0.08f), bg)
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(34), dp(18), dp(34), dp(16))
        }
        page.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Top bar: title, channel count, search, settings and the time.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "Live TV"
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        })
        val countText = TextView(this).apply {
            setTextColor(accent)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(3), dp(10), dp(3))
            background = pillBackground(Color.TRANSPARENT, withAlpha(accent, 0x90), 1)
        }
        header.addView(
            countText,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(12)
            },
        )
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val searchButton = iconButton(R.drawable.ic_search, "Search") { showChannelSearch() }
        header.addView(searchButton, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(8) })
        val settingsButton = iconButton(R.drawable.ic_settings, "Playlist settings") { showSetup() }
        header.addView(settingsButton, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
        val clock = TextView(this).apply {
            setTextColor(Color.rgb(235, 235, 240))
            textSize = 17f
        }
        header.addView(clock)
        content.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        // Category pills.
        val groups = buildList {
            add(ALL_CHANNELS)
            add(FAVOURITES)
            loadedChannels.map { it.group }
                .filter { it.isNotBlank() }
                .distinct()
                .forEach(::add)
        }
        val categoryList = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@NovaIptvActivity, LinearLayoutManager.HORIZONTAL, false)
            clipToPadding = false
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }
        content.addView(
            categoryList,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            },
        )

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        content.addView(
            body,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(12) },
        )

        // LEFT: the channel cards.
        val channelList = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@NovaIptvActivity)
            clipToPadding = false
            setPadding(dp(3), dp(3), dp(3), dp(3))
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        body.addView(channelList, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.44f))

        // RIGHT: the live preview window and what's on.
        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        body.addView(
            side,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.56f).apply { marginStart = dp(26) },
        )

        val previewFrame = FrameLayout(this).apply {
            background = cardBackground(Color.BLACK, Color.argb(0x55, 255, 255, 255), 1, 12)
            clipToOutline = true
        }
        side.addView(previewFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { height = 0 })

        // While the picture loads: the channel's logo in the middle.
        val previewPlaceholder = FrameLayout(this).apply {
            setBackgroundColor(blend(accent, Color.BLACK, 0.18f))
        }
        val placeholderLogo = AsyncImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        previewPlaceholder.addView(
            placeholderLogo,
            FrameLayout.LayoutParams(dp(150), dp(90), Gravity.CENTER),
        )
        val placeholderInitials = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
        }
        previewPlaceholder.addView(
            placeholderInitials,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        previewFrame.addView(
            previewPlaceholder,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        previewFrame.addView(
            TextView(this).apply {
                text = "●  LIVE"
                setTextColor(Color.WHITE)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = cardBackground(Color.rgb(226, 54, 54), Color.TRANSPARENT, 0, 5)
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START,
            ).apply { setMargins(dp(10), dp(10), 0, 0) },
        )
        previewFrame.addView(
            TextView(this).apply {
                text = "OK to watch full screen"
                setTextColor(Color.WHITE)
                textSize = 10f
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = cardBackground(Color.argb(0x99, 0, 0, 0), Color.TRANSPARENT, 0, 5)
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END,
            ).apply { setMargins(0, 0, dp(10), dp(10)) },
        )
        // Keep the window 16:9 whatever the screen size.
        side.addOnLayoutChangeListener { view, l, _, r, _, ol, _, or, _ ->
            if (r - l != or - ol) {
                val width = r - l
                previewFrame.layoutParams = previewFrame.layoutParams.apply { height = width * 9 / 16 }
                view.post { previewFrame.requestLayout() }
            }
        }

        val showTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(12), 0, 0)
        }
        side.addView(showTitle)
        val showMeta = TextView(this).apply {
            setTextColor(Color.rgb(210, 210, 220))
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(3), 0, 0)
        }
        side.addView(showMeta)
        val showProgress = ProgressStrip(dp(4), Color.argb(0x40, 255, 255, 255))
        side.addView(
            showProgress.view,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).apply { topMargin = dp(9) },
        )
        val showNext = TextView(this).apply {
            setTextColor(Color.rgb(160, 160, 172))
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(8), 0, 0)
        }
        side.addView(showNext)
        side.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        side.addView(TextView(this).apply {
            text = "OK  watch     ·     MENU  favourite     ·     ◀  categories     ·     BACK  exit"
            setTextColor(Color.rgb(125, 125, 135))
            textSize = 10f
        })

        root.addView(
            page,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )


        fun channelsFor(group: String) =
            when (group) {
                ALL_CHANNELS -> loadedChannels
                FAVOURITES -> loadedChannels.filter { isFavourite(it) }
                else -> loadedChannels.filter { it.group == group }
            }

        fun updatePreviewDetails(channel: IptvChannel, number: Int) {
            val schedule = channel.tvgId?.let { epgByChannelId[it] }
            val nowProgramme = schedule?.now
            val nextProgramme = schedule?.next

            if (nowProgramme != null) {
                showTitle.text = nowProgramme.title
                showMeta.text = "${channel.name}  ·  channel $number  ·  ${programmeTime(nowProgramme)}"
                showProgress.view.visibility = View.VISIBLE
                showProgress.set(programmeFraction(nowProgramme), accent)
            } else {
                showTitle.text = channel.name
                showMeta.text = listOf("Channel $number", channel.group).filter { it.isNotBlank() }.joinToString("  ·  ")
                showProgress.view.visibility = View.GONE
            }
            if (nextProgramme != null) {
                showNext.visibility = View.VISIBLE
                showNext.text = "Next at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(nextProgramme.start)}  ·  ${nextProgramme.title}"
            } else {
                showNext.visibility = View.GONE
            }

            if (!channel.logoUrl.isNullOrBlank()) {
                placeholderLogo.visibility = View.VISIBLE
                placeholderInitials.visibility = View.GONE
                placeholderLogo.load(url = channel.logoUrl)
            } else {
                placeholderLogo.visibility = View.GONE
                placeholderInitials.visibility = View.VISIBLE
                placeholderInitials.text = channelInitials(channel.name)
            }
        }

        fun schedulePreview(channel: IptvChannel, number: Int) {
            updatePreviewDetails(channel, number)
            previewJob?.cancel()
            releasePreviewPlayer()
            previewPlaceholder.visibility = View.VISIBLE
            previewJob = lifecycleScope.launch {
                delay(700)
                if (player != null) return@launch

                val view = PlayerView(this@NovaIptvActivity).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setBackgroundColor(Color.BLACK)
                    isFocusable = false
                    isClickable = false
                }

                val exo = buildIptvPlayer().apply {
                    volume = 1f
                    setMediaItem(iptvMediaItem(channel.url))
                    prepare()
                    playWhenReady = true
                }
                // Keep the logo showing until the picture actually starts.
                exo.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        previewPlaceholder.visibility = View.GONE
                    }
                })

                view.player = exo
                previewPlayer = exo
                previewPlayerView = view
                previewFrame.addView(
                    view,
                    0,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
        }

        fun focusSelectedPill() {
            focusListItem(categoryList, groups.indexOf(selectedGroup).coerceAtLeast(0))
        }

        // Top bar: Left/Right between the two buttons, Down to the categories.
        fun headerKeys(view: View, left: View?, right: View?) {
            view.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> focusSelectedPill()
                    KeyEvent.KEYCODE_DPAD_LEFT -> left?.requestFocus()
                    KeyEvent.KEYCODE_DPAD_RIGHT -> right?.requestFocus()
                    KeyEvent.KEYCODE_DPAD_UP -> Unit
                    else -> return@setOnKeyListener false
                }
                true
            }
        }
        headerKeys(searchButton, null, settingsButton)
        headerKeys(settingsButton, searchButton, null)

        fun focusRememberedChannel(channels: List<IptvChannel>, group: String) {
            if (channels.isEmpty()) return
            val rememberedIndex = selectedChannelUrl?.let { url -> channels.indexOfFirst { it.url == url } } ?: -1
            val position = when {
                rememberedIndex >= 0 -> rememberedIndex
                group == ALL_CHANNELS -> selectedChannelPosition.coerceIn(0, channels.lastIndex)
                else -> 0
            }
            channelList.scrollToPosition(position)
            channelList.post {
                channelList.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
                    ?: categoryList.requestFocus()
            }
        }

        fun updateChannelList(group: String, focusList: Boolean) {
            selectedGroup = group
            prefs.edit().putString(KEY_SELECTED_GROUP, group).apply()
            val channels = channelsFor(group)
            countText.text = if (channels.size == 1) "1 channel" else "${channels.size} channels"
            previewJob?.cancel()
            releasePreviewPlayer()
            previewPlaceholder.visibility = View.VISIBLE

            channelList.adapter = ChannelAdapter(
                channels = channels,
                onFocus = { channel, number -> schedulePreview(channel, number) },
                onClick = { channel ->
                    val index = loadedChannels.indexOfFirst { it.url == channel.url }
                    if (index >= 0) selectedChannelPosition = index
                    selectedChannelUrl = channel.url
                    prefs.edit()
                        .putInt(KEY_SELECTED_POSITION, selectedChannelPosition)
                        .putString(KEY_SELECTED_CHANNEL_URL, channel.url)
                        .apply()
                    playChannel(channel)
                },
                onFavouriteChanged = { _, added ->
                    categoryList.adapter?.notifyDataSetChanged()
                    if (selectedGroup == FAVOURITES && !added) {
                        updateChannelList(FAVOURITES, true)
                    } else {
                        channelList.adapter?.notifyDataSetChanged()
                    }
                },
                onUpFromTop = { focusSelectedPill() },
            )
            categoryList.adapter?.notifyDataSetChanged()

            if (focusList) channelList.post { focusRememberedChannel(channels, group) }
        }

        // The time, kept up to date while the guide is open.
        lifecycleScope.launch {
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            while (true) {
                clock.text = formatter.format(System.currentTimeMillis())
                delay(15_000L)
                if (!page.isAttachedToWindow) break
            }
        }

        categoryList.adapter = CategoryAdapter(
            groups = groups,
            onClick = { group -> updateChannelList(group, true) },
            onDown = { focusRememberedChannel(channelsFor(selectedGroup), selectedGroup) },
            onUp = { searchButton.requestFocus() },
        )
        updateChannelList(selectedGroup, false)

        // Back to the exact channel after full-screen playback.
        channelList.post { focusRememberedChannel(channelsFor(selectedGroup), selectedGroup) }
    }

    private fun showChannelSearch() {
        if (loadedChannels.isEmpty()) {
            showGuide()
            return
        }

        searchOpen = true
        releasePreviewPlayer()
        root.removeAllViews()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(24), dp(32), dp(24))
            setBackgroundColor(Color.rgb(8, 8, 8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        title.addView(TextView(this).apply {
            text = "NOVA LIVE TV"
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.07f
        })
        title.addView(TextView(this).apply {
            text = "CHANNEL SEARCH"
            setTextColor(themeAccent())
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.16f
        })
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val backButton = tvButton("Back to Guide") {
            searchOpen = false
            showGuide()
        }
        header.addView(backButton, LinearLayout.LayoutParams(dp(150), dp(44)))
        page.addView(header)

        val searchInput = EditText(this).apply {
            hint = "Search channel name or category…"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(110, 110, 110))
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 16f
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = roundedBackground(Color.rgb(22, 22, 22), Color.rgb(70, 70, 70), 1)
        }
        page.addView(
            searchInput,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply {
                topMargin = dp(20)
            }
        )

        val resultInfo = TextView(this).apply {
            text = "Start typing to search ${loadedChannels.size} channels."
            setTextColor(Color.rgb(145, 145, 145))
            textSize = 11f
            setPadding(dp(4), dp(10), 0, dp(8))
        }
        page.addView(resultInfo)

        val resultList = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@NovaIptvActivity)
            setBackgroundColor(Color.TRANSPARENT)
        }
        page.addView(
            resultList,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        page.addView(TextView(this).apply {
            text = "OK  Watch channel     •     MENU  Add/remove favourite     •     BACK  Return to guide"
            setTextColor(Color.rgb(115, 115, 115))
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        })

        fun showResults(query: String) {
            val term = query.trim()
            val results = if (term.isBlank()) {
                emptyList()
            } else {
                loadedChannels.filter { channel ->
                    channel.name.contains(term, ignoreCase = true) ||
                        channel.group.contains(term, ignoreCase = true)
                }
            }

            resultInfo.text = when {
                term.isBlank() -> "Start typing to search ${loadedChannels.size} channels."
                results.isEmpty() -> "No channels found for “$term”."
                else -> "${results.size} channel${if (results.size == 1) "" else "s"} found."
            }

            resultList.adapter = ChannelAdapter(
                channels = results,
                onFocus = { _, _ -> },
                onClick = { channel ->
                    val index = loadedChannels.indexOfFirst { it.url == channel.url }
                    if (index >= 0) selectedChannelPosition = index
                    selectedChannelUrl = channel.url
                    prefs.edit()
                        .putInt(KEY_SELECTED_POSITION, selectedChannelPosition)
                        .putString(KEY_SELECTED_CHANNEL_URL, channel.url)
                        .apply()
                    playChannel(channel)
                },
                onFavouriteChanged = { _, _ ->
                    resultList.adapter?.notifyDataSetChanged()
                },
                onUpFromTop = { searchInput.requestFocus() },
            )

        }

        searchInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                resultList.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    ?: resultList.post {
                        resultList.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    }
                true
            } else {
                false
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                showResults(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        root.addView(
            page,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        searchInput.post { searchInput.requestFocus() }
    }

    private suspend fun loadPlaylist(url: String): List<IptvChannel> = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NOVA-IPTV/1.0")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            val text = connection.inputStream.bufferedReader().use { it.readText() }

            // Cache the raw playlist in app-private storage. This means leaving
            // IPTV for Movies/TV Shows and coming back opens the guide instantly,
            // without asking the user to load the playlist again.
            playlistCacheFile().writeText(text)

            val detectedEpg = extractEpgUrl(text)
            if (!detectedEpg.isNullOrBlank() && prefs.getString(KEY_EPG_URL, "").isNullOrBlank()) {
                prefs.edit().putString(KEY_EPG_URL, detectedEpg).apply()
            }

            channelsFromPlaylistText(text, url).also(::saveChannelIndexCache)
        } finally {
            connection.disconnect()
        }
    }

    private fun channelsFromPlaylistText(text: String, sourceUrl: String): List<IptvChannel> {
        if (text.contains("#EXT-X-STREAM-INF") && !text.contains("#EXTINF:")) {
            return listOf(
                IptvChannel(
                    name = "Live Stream",
                    url = sourceUrl,
                    group = "Live",
                    logoUrl = null,
                    tvgId = null,
                )
            )
        }

        return parseM3u(text)
    }

    private fun parseM3u(text: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        var pendingName: String? = null
        var pendingGroup: String? = null
        var pendingLogo: String? = null
        var pendingTvgId: String? = null

        val groupRegex = Regex("""group-title\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
        val logoRegex = Regex("""tvg-logo\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
        val tvgIdRegex = Regex("""tvg-id\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingName = line.substringAfterLast(',', "Channel").trim().ifBlank { "Channel" }
                    pendingGroup = groupRegex.find(line)?.groupValues?.getOrNull(1)?.trim()?.ifBlank { null }
                    pendingLogo = logoRegex.find(line)?.groupValues?.getOrNull(1)?.trim()?.ifBlank { null }
                    pendingTvgId = tvgIdRegex.find(line)?.groupValues?.getOrNull(1)?.trim()?.ifBlank { null }
                }

                line.isNotBlank() && !line.startsWith("#") -> {
                    val name = pendingName
                    if (name != null && (line.startsWith("http://", true) || line.startsWith("https://", true))) {
                        channels += IptvChannel(
                            name = name,
                            url = line,
                            group = pendingGroup ?: "Other",
                            logoUrl = pendingLogo,
                            tvgId = pendingTvgId,
                        )
                    }
                    pendingName = null
                    pendingGroup = null
                    pendingLogo = null
                    pendingTvgId = null
                }
            }
        }

        return channels
    }

    private fun normaliseXtreamServer(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        if (trimmed.isBlank()) return ""
        return if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    private fun queryValue(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun httpGetText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NOVA-IPTV/1.0")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun loadXtream(
        server: String,
        username: String,
        password: String,
    ): XtreamLoadResult = withContext(Dispatchers.IO) {
        val user = queryValue(username)
        val pass = queryValue(password)
        val userPath = Uri.encode(username)
        val passPath = Uri.encode(password)
        val api = "$server/player_api.php?username=$user&password=$pass"

        var apiFailure: String? = null

        // First try the normal Xtream player_api endpoint. Some services disable
        // this endpoint even though the same login works through get.php, so a
        // standards-compatible M3U fallback is handled below.
        val apiChannels = runCatching {
            val accountText = httpGetText(api)
            val account = JSONObject(accountText)
            val userInfo = account.optJSONObject("user_info")
                ?: error("Server did not return Xtream account information")

            val authenticated =
                userInfo.optInt("auth", 0) == 1 ||
                userInfo.optString("auth").equals("1", ignoreCase = true)

            if (!authenticated) error("Login was rejected")

            val categoriesJson = JSONArray(httpGetText("$api&action=get_live_categories"))
            val categories = mutableMapOf<String, String>()
            for (i in 0 until categoriesJson.length()) {
                val item = categoriesJson.optJSONObject(i) ?: continue
                val id = item.optString("category_id")
                val name = item.optString("category_name").ifBlank { "Other" }
                if (id.isNotBlank()) categories[id] = name
            }

            val streamsJson = JSONArray(httpGetText("$api&action=get_live_streams"))
            buildList {
                for (i in 0 until streamsJson.length()) {
                    val item = streamsJson.optJSONObject(i) ?: continue
                    val streamId = item.optInt("stream_id", -1)
                    if (streamId <= 0) continue

                    val name = item.optString("name").ifBlank { "Channel" }
                    val categoryId = item.optString("category_id")
                    val group = categories[categoryId] ?: "Other"
                    val logo = item.optString("stream_icon").ifBlank { null }
                    val epgId = item.optString("epg_channel_id").ifBlank { null }

                    add(
                        IptvChannel(
                            name = name,
                            url = "$server/live/$userPath/$passPath/$streamId.ts",
                            group = group,
                            logoUrl = logo,
                            tvgId = epgId,
                        )
                    )
                }
            }.also {
                if (it.isEmpty()) error("Xtream API returned no Live TV channels")
            }
        }.onFailure {
            apiFailure = it.message ?: it.javaClass.simpleName
        }.getOrNull()

        val channels: List<IptvChannel>
        val generatedM3u: String

        // Prefer the provider's own generated M3U because those entries contain
        // the exact playback URLs expected by that panel. This avoids assuming
        // every Xtream-compatible server uses /live/user/pass/id.ts.
        val providerM3uUrl = "$server/get.php?username=$user&password=$pass&type=m3u_plus&output=ts"
        val providerM3uText = runCatching { httpGetText(providerM3uUrl) }.getOrNull()
        val providerChannels = providerM3uText?.let { parseM3u(it) }.orEmpty()

        if (providerChannels.isNotEmpty()) {
            channels = providerChannels
            generatedM3u = providerM3uText!!
        } else if (!apiChannels.isNullOrEmpty()) {
            channels = apiChannels
            generatedM3u = buildString {
                appendLine("#EXTM3U")
                channels.forEach { channel ->
                    val safeName = channel.name.replace('"', '\'')
                    val safeGroup = channel.group.replace('"', '\'')
                    val safeLogo = channel.logoUrl.orEmpty().replace('"', '\'')
                    val safeId = channel.tvgId.orEmpty().replace('"', '\'')
                    appendLine(
                        "#EXTINF:-1 tvg-id=\"$safeId\" tvg-logo=\"$safeLogo\" group-title=\"$safeGroup\",$safeName"
                    )
                    appendLine(channel.url)
                }
            }
        } else {
            error(
                "The login worked, but no playable Live TV channels were returned. " +
                    "Xtream API: ${apiFailure ?: "unavailable"}"
            )
        }

        val epgUrl = "$server/xmltv.php?username=$user&password=$pass"
        val epg = runCatching {
            downloadAndParseEpg(epgUrl, channels)
        }.getOrDefault(emptyMap())

        XtreamLoadResult(
            channels = channels,
            generatedM3u = generatedM3u,
            epgUrl = epgUrl,
            epg = epg,
        )
    }

    private fun extractEpgUrl(text: String): String? {
        val header = text.lineSequence().firstOrNull()?.trim().orEmpty()
        val quoted = Regex("""(?:x-tvg-url|url-tvg)\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)
        val unquoted = Regex("""(?:x-tvg-url|url-tvg)\s*=\s*([^\s]+)""", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)
        return (quoted ?: unquoted)
            ?.split(',', ';')
            ?.firstOrNull()
            ?.trim()
            ?.ifBlank { null }
    }

    private fun downloadAndParseEpg(
        url: String,
        channels: List<IptvChannel>,
    ): Map<String, NowNextProgramme> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 35_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NOVA-IPTV/1.0")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) error("EPG HTTP $code")

            val raw = connection.inputStream
            val input = if (
                url.endsWith(".gz", ignoreCase = true) ||
                connection.contentEncoding.equals("gzip", ignoreCase = true)
            ) {
                GZIPInputStream(raw)
            } else {
                raw
            }

            input.use { source ->
                epgCacheFile().outputStream().buffered().use { target ->
                    source.copyTo(target)
                }
            }

            return parseXmltv(epgCacheFile(), channels).also(::saveEpgSnapshot)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseXmltv(
        file: File,
        channels: List<IptvChannel>,
    ): Map<String, NowNextProgramme> {
        val wantedIds = channels.mapNotNull { it.tvgId }.toHashSet()
        if (wantedIds.isEmpty()) return emptyMap()

        val current = mutableMapOf<String, ProgrammeInfo>()
        val next = mutableMapOf<String, ProgrammeInfo>()
        val now = System.currentTimeMillis()

        file.inputStream().buffered().use { input ->
            val parser = Xml.newPullParser().apply {
                setInput(input, "UTF-8")
            }

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                    val channelId = parser.getAttributeValue(null, "channel").orEmpty()
                    val start = parseXmltvTime(parser.getAttributeValue(null, "start"))
                    val stop = parseXmltvTime(parser.getAttributeValue(null, "stop"))

                    if (channelId in wantedIds && stop > now) {
                        var title = "Programme"
                        var description: String? = null
                        val programmeDepth = parser.depth

                        while (true) {
                            val inner = parser.next()
                            if (inner == XmlPullParser.START_TAG) {
                                when (parser.name) {
                                    "title" -> title = parser.nextText().trim().ifBlank { "Programme" }
                                    "desc" -> description = parser.nextText().trim().ifBlank { null }
                                }
                            }
                            if (
                                inner == XmlPullParser.END_TAG &&
                                parser.depth == programmeDepth &&
                                parser.name == "programme"
                            ) break
                        }

                        val programme = ProgrammeInfo(title, start, stop, description)
                        if (start <= now && stop > now) {
                            current[channelId] = programme
                        } else if (start > now) {
                            val existing = next[channelId]
                            if (existing == null || start < existing.start) next[channelId] = programme
                        }
                    }
                }
                event = parser.next()
            }
        }

        return wantedIds.associateWith { id ->
            NowNextProgramme(current[id], next[id])
        }.filterValues { it.now != null || it.next != null }
    }

    private fun parseXmltvTime(value: String?): Long {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return 0L

        val candidates = listOf(
            "yyyyMMddHHmmss Z",
            "yyyyMMddHHmmssZ",
            "yyyyMMddHHmm Z",
            "yyyyMMddHHmmZ",
            "yyyyMMddHHmmss",
            "yyyyMMddHHmm",
        )

        for (pattern in candidates) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = true }.parse(raw)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }

        return 0L
    }

    private fun programmeTime(programme: ProgrammeInfo?): String {
        if (programme == null) return ""
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return "${formatter.format(programme.start)} – ${formatter.format(programme.stop)}"
    }

    private fun playbackChannelList(): List<IptvChannel> {
        val groupChannels = when (selectedGroup) {
            ALL_CHANNELS -> loadedChannels
            FAVOURITES -> loadedChannels.filter { isFavourite(it) }
            else -> loadedChannels.filter { it.group == selectedGroup }
        }
        return if (groupChannels.isNotEmpty()) groupChannels else loadedChannels
    }

    private fun zapChannel(direction: Int) {
        val current = currentPlayingChannel ?: return
        val channels = playbackChannelList()
        if (channels.isEmpty()) return

        var index = channels.indexOfFirst { it.url == current.url }
        if (index < 0) index = 0

        val nextIndex = (index + direction + channels.size) % channels.size
        playChannel(channels[nextIndex])
    }

    private fun adjustIptvVolume(change: Float) {
        // Fire TV does not reliably expose the physical TV/receiver volume to
        // third-party apps. Keep IPTV volume app-local so D-pad Up/Down always
        // works consistently while watching Live TV.
        val currentStep = Math.round(iptvVolume * 10f)
        val stepDirection = if (change >= 0f) 1 else -1
        val nextStep = (currentStep + stepDirection).coerceIn(0, 10)
        iptvVolume = nextStep / 10f
        player?.volume = iptvVolume
        prefs.edit().putFloat(KEY_IPTV_VOLUME, iptvVolume).apply()
        showVolumeOverlay()
    }

    private fun showVolumeOverlay() {
        volumeOverlayJob?.cancel()
        volumeOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        volumeOverlay = null

        val percent = Math.round(iptvVolume * 100f)
        val label = TextView(this).apply {
            text = if (percent == 0) "MUTED" else "VOLUME  $percent%"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = roundedBackground(
                Color.argb(235, 12, 12, 12),
                themeAccent(),
                1
            )
        }

        root.addView(
            label,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply {
                topMargin = dp(24)
                rightMargin = dp(24)
            }
        )

        volumeOverlay = label
        volumeOverlayJob = lifecycleScope.launch {
            delay(1200)
            volumeOverlay?.animate()
                ?.alpha(0f)
                ?.setDuration(200)
                ?.withEndAction {
                    volumeOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
                    volumeOverlay = null
                }
                ?.start()
        }
    }

    private fun showPlaybackEpg(channel: IptvChannel) {
        if (player == null) return

        playbackEpgJob?.cancel()
        playbackEpgOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        playbackEpgOverlay = null

        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(26), dp(16), dp(26), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.argb(245, 9, 9, 9),
                    Color.argb(245, 24, 10, 12),
                )
            ).apply {
                setStroke(dp(1), blend(themeAccent(), Color.BLACK, 0.4f))
            }
        }

        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logoBox = FrameLayout(this).apply {
            background = roundedBackground(Color.rgb(30, 30, 30), Color.rgb(70, 70, 70), 1)
        }

        if (!channel.logoUrl.isNullOrBlank()) {
            logoBox.addView(
                AsyncImageView(this).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                    load(url = channel.logoUrl)
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            )
        } else {
            logoBox.addView(
                TextView(this).apply {
                    text = channelInitials(channel.name)
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            )
        }

        identity.addView(logoBox, LinearLayout.LayoutParams(dp(86), dp(56)).apply {
            marginEnd = dp(13)
        })

        val channelText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        channelText.addView(TextView(this).apply {
            text = channel.name
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        channelText.addView(TextView(this).apply {
            text = channel.group
            setTextColor(Color.rgb(155, 155, 155))
            textSize = 11f
            maxLines = 1
        })
        identity.addView(channelText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        overlay.addView(
            identity,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.38f).apply {
                marginEnd = dp(22)
            }
        )

        val schedule = channel.tvgId?.let { epgByChannelId[it] }
        val nowProgramme = schedule?.now
        val nextProgramme = schedule?.next

        val programmes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        programmes.addView(TextView(this).apply {
            text = if (nowProgramme != null) "NOW  ${programmeTime(nowProgramme)}" else "NOW"
            setTextColor(themeAccent())
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        })
        programmes.addView(TextView(this).apply {
            text = nowProgramme?.title ?: "No programme information"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(3), 0, dp(5))
        })

        val progress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal,
        ).apply {
            max = 100
            this.progress = if (nowProgramme != null) {
                val duration = (nowProgramme.stop - nowProgramme.start).coerceAtLeast(1L)
                val elapsed = (System.currentTimeMillis() - nowProgramme.start).coerceIn(0L, duration)
                ((elapsed * 100L) / duration).toInt()
            } else {
                0
            }
        }
        programmes.addView(
            progress,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5))
        )

        programmes.addView(TextView(this).apply {
            text = if (nextProgramme != null) {
                "NEXT  ${programmeTime(nextProgramme)}  •  ${nextProgramme.title}"
            } else if (loadedEpgUrl.isNullOrBlank()) {
                "NEXT  Programme guide not available"
            } else {
                "NEXT  No upcoming programme information"
            }
            setTextColor(Color.rgb(190, 190, 190))
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(8), 0, 0)
        })

        if (!nowProgramme?.description.isNullOrBlank()) {
            programmes.addView(TextView(this).apply {
                text = nowProgramme?.description
                setTextColor(Color.rgb(145, 145, 145))
                textSize = 10f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(5), 0, 0)
            })
        }

        overlay.addView(
            programmes,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.62f)
        )

        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(150),
                Gravity.BOTTOM,
            ).apply {
                leftMargin = dp(24)
                rightMargin = dp(24)
                bottomMargin = dp(24)
            }
        )

        playbackEpgOverlay = overlay
        playbackEpgJob = lifecycleScope.launch {
            delay(7000)
            playbackEpgOverlay?.animate()
                ?.alpha(0f)
                ?.setDuration(300)
                ?.withEndAction {
                    playbackEpgOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
                    playbackEpgOverlay = null
                }
                ?.start()
        }
    }

    private fun buildIptvPlayer(): ExoPlayer {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("NOVA-IPTV/1.0")
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(5))

        // The app already bundles FFmpeg for audio formats missing from some
        // Fire TV devices. Use it when the platform cannot decode the track.
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

        return ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply { volume = iptvVolume }
    }

    private fun iptvMediaItem(url: String): MediaItem {
        val uri = Uri.parse(url)
        return MediaItem.Builder().setUri(uri).apply {
            // Provider URLs frequently have query strings or extensionless paths.
            if (uri.path?.endsWith(".m3u8", ignoreCase = true) == true) {
                setMimeType(MimeTypes.APPLICATION_M3U8)
            }
        }.build()
    }

    private fun playChannel(channel: IptvChannel) {
        releasePreviewPlayer()
        releasePlayer()
        root.removeAllViews()

        currentPlayingChannel = channel
        selectedChannelUrl = channel.url
        loadedChannels.indexOfFirst { it.url == channel.url }.takeIf { it >= 0 }?.let {
            selectedChannelPosition = it
        }
        prefs.edit()
            .putString(KEY_SELECTED_CHANNEL_URL, channel.url)
            .putInt(KEY_SELECTED_POSITION, selectedChannelPosition)
            .apply()

        val view = PlayerView(this).apply {
            useController = false
            controllerAutoShow = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val exo = buildIptvPlayer().apply {
            setMediaItem(iptvMediaItem(channel.url))
            prepare()
            playWhenReady = true
        }

        view.player = exo
        player = exo
        playerView = view

        root.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Brief channel identification, then fade it completely away.
        val badge = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(14), dp(7))
            background = roundedBackground(
                Color.argb(215, 12, 12, 12),
                Color.argb(180, 229, 9, 20),
                1
            )
        }

        if (!channel.logoUrl.isNullOrBlank()) {
            val logo = AsyncImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                load(url = channel.logoUrl)
            }
            badge.addView(logo, LinearLayout.LayoutParams(dp(56), dp(32)).apply {
                marginEnd = dp(10)
            })
        }

        badge.addView(TextView(this).apply {
            text = channel.name
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        })

        root.addView(
            badge,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                leftMargin = dp(24)
                topMargin = dp(22)
            }
        )

        badge.animate()
            .alpha(0f)
            .setStartDelay(2500)
            .setDuration(450)
            .withEndAction { badge.visibility = View.GONE }
            .start()

        val playbackError = TextView(this).apply {
            visibility = View.GONE
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = roundedBackground(
                Color.argb(230, 28, 10, 12),
                themeAccent(),
                1
            )
        }
        root.addView(
            playbackError,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dp(36)
                leftMargin = dp(24)
                rightMargin = dp(24)
            }
        )

        var retryCount = 0
        exo.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                if (audioGroups.isNotEmpty() && audioGroups.none { it.isSelected }) {
                    playbackError.text = "This channel's audio format is not supported on this device."
                    playbackError.visibility = View.VISIBLE
                } else if (playbackError.text.startsWith("This channel's audio format")) {
                    playbackError.visibility = View.GONE
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val httpStatus = generateSequence(error.cause) { it.cause }
                    .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                    .firstOrNull()?.responseCode
                val temporaryHttpError = httpStatus == 408 || httpStatus == 429 ||
                    (httpStatus != null && httpStatus in 500..599)
                val temporaryNetworkError = error.errorCode in setOf(
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                )
                if ((temporaryHttpError || temporaryNetworkError) && retryCount < 2) {
                    val attempt = ++retryCount
                    playbackError.text = "Stream interrupted. Reconnecting ($attempt/2)…"
                    playbackError.visibility = View.VISIBLE
                    playbackRetryJob?.cancel()
                    playbackRetryJob = lifecycleScope.launch {
                        delay(if (attempt == 1) 1500 else 3500)
                        if (player === exo) {
                            exo.prepare()
                            exo.playWhenReady = true
                        }
                    }
                    return
                }
                playbackError.text = when {
                    httpStatus == 401 || httpStatus == 403 ->
                        "This channel's provider denied access (HTTP $httpStatus). Check the playlist or login."
                    httpStatus != null -> "This channel's provider returned HTTP $httpStatus."
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                        "This channel uses a video or audio format this device cannot decode."
                    else -> "Playback error: ${error.errorCodeName}"
                }
                playbackError.visibility = View.VISIBLE
            }
        })

        view.requestFocus()
    }

    private fun releasePlayer() {
        playbackRetryJob?.cancel()
        playbackRetryJob = null
        playbackEpgJob?.cancel()
        playbackEpgJob = null
        playbackEpgOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        playbackEpgOverlay = null
        volumeOverlayJob?.cancel()
        volumeOverlayJob = null
        volumeOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        volumeOverlay = null
        currentPlayingChannel = null
        playerView?.player = null
        playerView = null
        player?.release()
        player = null
    }

    private fun releasePreviewPlayer() {
        previewJob?.cancel()
        previewJob = null
        previewPlayerView?.player = null
        previewPlayerView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        previewPlayerView = null
        previewPlayer?.release()
        previewPlayer = null
    }

    private fun tvButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(Color.WHITE)
        background = roundedBackground(Color.rgb(30, 30, 30), Color.rgb(90, 90, 90), 1)
        setOnClickListener { onClick() }
        setOnFocusChangeListener { view, focused ->
            view.background = if (focused) {
                roundedBackground(blend(themeAccent(), Color.BLACK, 0.25f), themeAccent(), 2)
            } else {
                roundedBackground(Color.rgb(30, 30, 30), Color.rgb(90, 90, 90), 1)
            }
        }
    }

    private fun roundedBackground(fill: Int, stroke: Int, strokeDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(7).toFloat()
            setColor(fill)
            if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private data class IptvChannel(
        val name: String,
        val url: String,
        val group: String,
        val logoUrl: String?,
        val tvgId: String?,
    )

    private data class XtreamLoadResult(
        val channels: List<IptvChannel>,
        val generatedM3u: String,
        val epgUrl: String,
        val epg: Map<String, NowNextProgramme>,
    )

    private data class ProgrammeInfo(
        val title: String,
        val start: Long,
        val stop: Long,
        val description: String?,
    )

    private data class NowNextProgramme(
        val now: ProgrammeInfo?,
        val next: ProgrammeInfo?,
    )

    /** Category pills along the top of the guide. */
    private inner class CategoryAdapter(
        private val groups: List<String>,
        private val onClick: (String) -> Unit,
        private val onDown: () -> Unit,
        private val onUp: () -> Unit,
    ) : RecyclerView.Adapter<CategoryViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
            val pill = TextView(parent.context).apply {
                isFocusable = true
                isClickable = true
                textSize = 13f
                maxLines = 1
                setPadding(dp(15), dp(6), dp(15), dp(6))
            }
            pill.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(8) }
            return CategoryViewHolder(pill)
        }

        override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
            val group = groups[position]
            val active = group == selectedGroup
            val count = when (group) {
                ALL_CHANNELS -> loadedChannels.size
                FAVOURITES -> loadedChannels.count { isFavourite(it) }
                else -> loadedChannels.count { it.group == group }
            }
            val label = when (group) {
                ALL_CHANNELS -> "All"
                FAVOURITES -> "★  Favourites"
                else -> group
            }
            holder.pill.text = "$label   $count"

            fun applyStyle(focused: Boolean) {
                val accent = themeAccent()
                when {
                    focused -> {
                        holder.pill.background = pillBackground(accent, Color.WHITE, 2)
                        holder.pill.setTextColor(themeOnAccent())
                    }
                    active -> {
                        holder.pill.background = pillBackground(Color.WHITE, Color.TRANSPARENT, 0)
                        holder.pill.setTextColor(Color.rgb(17, 17, 17))
                    }
                    else -> {
                        holder.pill.background = pillBackground(Color.argb(0x24, 255, 255, 255), Color.TRANSPARENT, 0)
                        holder.pill.setTextColor(Color.rgb(225, 225, 232))
                    }
                }
                holder.pill.typeface = if (focused || active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }

            applyStyle(holder.pill.isFocused)
            holder.pill.setOnFocusChangeListener { _, focused -> applyStyle(focused) }
            holder.pill.setOnClickListener { onClick(group) }
            // The remote only ever moves along the pills, down to the channels or up
            // to the search button, never somewhere unexpected.
            holder.pill.setOnKeyListener { view, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val list = view.parent as? RecyclerView
                val at = holder.bindingAdapterPosition
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> onDown()
                    KeyEvent.KEYCODE_DPAD_UP -> onUp()
                    KeyEvent.KEYCODE_DPAD_LEFT -> if (list != null && at > 0) focusListItem(list, at - 1)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> if (list != null && at < groups.lastIndex) focusListItem(list, at + 1)
                    else -> return@setOnKeyListener false
                }
                true
            }
        }

        override fun getItemCount() = groups.size
    }

    /** The floating channel cards. */
    private inner class ChannelAdapter(
        private val channels: List<IptvChannel>,
        private val onFocus: (IptvChannel, Int) -> Unit,
        private val onClick: (IptvChannel) -> Unit,
        private val onFavouriteChanged: (IptvChannel, Boolean) -> Unit,
        /** Up from the first channel, or Left from any channel. */
        private val onUpFromTop: () -> Unit,
    ) : RecyclerView.Adapter<ChannelViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                minimumHeight = dp(66)
                setPadding(dp(10), dp(8), dp(12), dp(8))
            }
            row.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(6) }

            val logoFrame = FrameLayout(parent.context).apply {
                background = cardBackground(Color.argb(0x26, 255, 255, 255), Color.TRANSPARENT, 0, 8)
            }
            val logo = AsyncImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(5), dp(5), dp(5), dp(5))
            }
            logoFrame.addView(
                logo,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            val initials = TextView(parent.context).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                visibility = View.GONE
            }
            logoFrame.addView(
                initials,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            row.addView(logoFrame, LinearLayout.LayoutParams(dp(74), dp(46)).apply { marginEnd = dp(13) })

            val textColumn = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val nameRow = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val number = TextView(parent.context).apply {
                setTextColor(Color.rgb(140, 140, 150))
                textSize = 12f
                setPadding(0, 0, dp(7), 0)
            }
            nameRow.addView(number)
            val name = TextView(parent.context).apply {
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            nameRow.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val favourite = TextView(parent.context).apply {
                text = "★"
                textSize = 15f
                setPadding(dp(6), 0, 0, 0)
            }
            nameRow.addView(favourite)
            textColumn.addView(nameRow)

            val subtitle = TextView(parent.context).apply {
                setTextColor(Color.rgb(165, 165, 176))
                textSize = 12f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(1), 0, 0)
            }
            textColumn.addView(subtitle)
            val progress = ProgressStrip(dp(3), Color.argb(0x30, 255, 255, 255))
            textColumn.addView(
                progress.view,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)).apply { topMargin = dp(5) },
            )
            row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            return ChannelViewHolder(row, number, logo, initials, name, subtitle, favourite, progress)
        }

        override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
            val channel = channels[position]
            val number = position + 1
            holder.number.text = number.toString()
            holder.name.text = channel.name

            val nowProgramme = channel.tvgId?.let { epgByChannelId[it] }?.now
            holder.subtitle.text = if (nowProgramme != null) {
                "${nowProgramme.title}  ·  ends ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(nowProgramme.stop)}"
            } else {
                channel.group
            }
            holder.progress.view.visibility = if (nowProgramme != null) View.VISIBLE else View.GONE

            holder.favourite.visibility = if (isFavourite(channel)) View.VISIBLE else View.GONE
            holder.favourite.setTextColor(themeAccent())

            if (!channel.logoUrl.isNullOrBlank()) {
                holder.logo.visibility = View.VISIBLE
                holder.initials.visibility = View.GONE
                holder.logo.load(url = channel.logoUrl)
            } else {
                holder.logo.visibility = View.GONE
                holder.initials.visibility = View.VISIBLE
                holder.initials.text = channelInitials(channel.name)
            }

            fun applyStyle(focused: Boolean) {
                holder.row.background = if (focused) {
                    cardBackground(Color.argb(0x2E, 255, 255, 255), Color.WHITE, 2, 12)
                } else {
                    cardBackground(Color.argb(0x10, 255, 255, 255), Color.TRANSPARENT, 0, 12)
                }
                holder.subtitle.setTextColor(if (focused) Color.rgb(225, 225, 232) else Color.rgb(165, 165, 176))
                if (nowProgramme != null) {
                    holder.progress.set(
                        programmeFraction(nowProgramme),
                        if (focused) themeAccent() else Color.argb(0x99, 255, 255, 255),
                    )
                }
            }

            applyStyle(holder.row.isFocused)
            holder.row.setOnFocusChangeListener { _, focused ->
                applyStyle(focused)
                if (focused) onFocus(channel, number)
            }

            holder.row.setOnClickListener { onClick(channel) }
            holder.row.setOnKeyListener { view, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) {
                    false
                } else {
                    val list = view.parent as? RecyclerView
                    val at = holder.bindingAdapterPosition
                    when (keyCode) {
                        // Up and down only ever move between channels (Android's own
                        // search could jump to the settings button mid-scroll).
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (at <= 0) onUpFromTop() else if (list != null) focusListItem(list, at - 1)
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (list != null && at < channels.lastIndex) focusListItem(list, at + 1)
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            onClick(channel)
                            true
                        }

                        KeyEvent.KEYCODE_MENU -> {
                            val added = toggleFavourite(channel)
                            notifyItemChanged(position)
                            onFavouriteChanged(channel, added)
                            true
                        }

                        // Left from any channel jumps straight back to the categories
                        // (no scrolling all the way up). The preview isn't something to move to.
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onUpFromTop()
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_RIGHT -> true

                        else -> false
                    }
                }
            }
        }

        override fun getItemCount() = channels.size
    }

    private fun channelInitials(name: String): String {
        val words = name
            .replace(Regex("""[^A-Za-z0-9 ]"""), " ")
            .trim()
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }

        return when {
            words.size >= 2 -> (words[0].take(1) + words[1].take(1)).uppercase()
            words.size == 1 -> words[0].take(2).uppercase()
            else -> "TV"
        }
    }

    /** Moves focus to [position] in [list], scrolling it into view first if needed. */
    private fun focusListItem(list: RecyclerView, position: Int) {
        val count = list.adapter?.itemCount ?: return
        if (position !in 0 until count) return
        val ready = list.findViewHolderForAdapterPosition(position)?.itemView
        if (ready != null && ready.isAttachedToWindow) {
            ready.requestFocus()
            return
        }
        list.scrollToPosition(position)
        list.post { list.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus() }
    }

    /** A thin progress bar (how far through the programme). */
    private inner class ProgressStrip(height: Int, track: Int) {
        private val fill = View(this@NovaIptvActivity)
        private val rest = View(this@NovaIptvActivity)
        val view = LinearLayout(this@NovaIptvActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = cardBackground(track, Color.TRANSPARENT, 0, 4)
            clipToOutline = true
            addView(fill, LinearLayout.LayoutParams(0, height, 0f))
            addView(rest, LinearLayout.LayoutParams(0, height, 1f))
        }

        fun set(fraction: Float, colour: Int) {
            val done = fraction.coerceIn(0f, 1f)
            fill.background = cardBackground(colour, Color.TRANSPARENT, 0, 4)
            (fill.layoutParams as LinearLayout.LayoutParams).weight = done
            (rest.layoutParams as LinearLayout.LayoutParams).weight = 1f - done
            view.requestLayout()
        }
    }

    private fun programmeFraction(programme: ProgrammeInfo): Float {
        val duration = (programme.stop - programme.start).coerceAtLeast(1L)
        val elapsed = (System.currentTimeMillis() - programme.start).coerceIn(0L, duration)
        return elapsed.toFloat() / duration
    }

    // NOVA's chosen colour theme (Settings → Theme).
    private val novaColours get() = org.jellyfin.androidtv.ui.nova

    private fun themeAccent(): Int = novaColours.accent.toArgb()

    private fun themeOnAccent(): Int = novaColours.onAccent.toArgb()

    private fun withAlpha(colour: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(colour), Color.green(colour), Color.blue(colour))

    /** [amount] of [colour] mixed into [base]. */
    private fun blend(colour: Int, base: Int, amount: Float): Int {
        fun mix(a: Int, b: Int) = (a * amount + b * (1f - amount)).toInt()
        return Color.rgb(
            mix(Color.red(colour), Color.red(base)),
            mix(Color.green(colour), Color.green(base)),
            mix(Color.blue(colour), Color.blue(base)),
        )
    }

    private fun pillBackground(fill: Int, stroke: Int, strokeDp: Int): GradientDrawable =
        cardBackground(fill, stroke, strokeDp, 100)

    private fun cardBackground(fill: Int, stroke: Int, strokeDp: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
        }

    /** A round icon button for the top bar. */
    private fun iconButton(icon: Int, label: String, onClick: () -> Unit) = ImageView(this).apply {
        setImageResource(icon)
        contentDescription = label
        isFocusable = true
        isClickable = true
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(9), dp(9), dp(9), dp(9))
        setColorFilter(Color.WHITE)
        background = pillBackground(Color.argb(0x1F, 255, 255, 255), Color.TRANSPARENT, 0)
        setOnClickListener { onClick() }
        setOnFocusChangeListener { view, focused ->
            view.background = if (focused) {
                pillBackground(themeAccent(), Color.WHITE, 2)
            } else {
                pillBackground(Color.argb(0x1F, 255, 255, 255), Color.TRANSPARENT, 0)
            }
            setColorFilter(if (focused) themeOnAccent() else Color.WHITE)
        }
    }

    private class CategoryViewHolder(
        val pill: TextView,
    ) : RecyclerView.ViewHolder(pill)

    private class ChannelViewHolder(
        val row: LinearLayout,
        val number: TextView,
        val logo: AsyncImageView,
        val initials: TextView,
        val name: TextView,
        val subtitle: TextView,
        val favourite: TextView,
        val progress: ProgressStrip,
    ) : RecyclerView.ViewHolder(row)

    companion object {
        private const val KEY_PLAYLIST_URL = "playlist_url"
        private const val KEY_SELECTED_GROUP = "selected_group"
        private const val KEY_SELECTED_POSITION = "selected_position"
        private const val KEY_SELECTED_CHANNEL_URL = "selected_channel_url"
        private const val KEY_IPTV_VOLUME = "iptv_volume"
        private const val KEY_FAVOURITE_URLS = "favourite_urls"
        private const val PLAYLIST_CACHE_FILE = "nova_iptv_playlist_cache.m3u"
        private const val EPG_CACHE_FILE = "nova_iptv_epg_cache.xml"
        private const val CHANNEL_INDEX_CACHE_FILE = "nova_iptv_channels.json"
        private const val EPG_SNAPSHOT_FILE = "nova_iptv_epg_now_next.json"
        private const val KEY_EPG_URL = "epg_url"
        private const val KEY_SOURCE_TYPE = "source_type"
        private const val KEY_XTREAM_SERVER = "xtream_server"
        private const val KEY_XTREAM_USERNAME = "xtream_username"
        private const val KEY_XTREAM_PASSWORD = "xtream_password"
        private const val SOURCE_M3U = "m3u"
        private const val SOURCE_XTREAM = "xtream"
        private const val ALL_CHANNELS = "All Channels"
        private const val FAVOURITES = "Favourites"
    }
}

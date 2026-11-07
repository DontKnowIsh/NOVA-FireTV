#!/usr/bin/env python3
from pathlib import Path

root = Path("jellyfin-src")

# Use a native vector for in-app NOVA branding.  This is intentionally
# independent of the PNG artwork because older Fire OS builds can fail to
# decode that PNG through Compose painterResource at runtime.
nova_mark = root / "app/src/main/res/drawable/nova_mark.xml"
nova_mark.write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="512dp"
    android:height="512dp"
    android:viewportWidth="512"
    android:viewportHeight="512">

    <!-- Clean Concept A NOVA mark.
         One solid path only: no overlays, highlights or intersecting shapes,
         so the N stays crisp on Fire TV / Android TV at every size. -->
    <path
        android:fillColor="#FFE50914"
        android:pathData="
            M155,105
            C130,105 110,126 110,151
            L110,360
            L185,405
            L185,250
            L324,365
            C347,384 376,375 387,353
            C390,347 392,339 392,329
            L392,145
            L315,95
            L315,256
            L197,159
            C182,147 168,138 155,135
            Z" />
</vector>
""", encoding="utf-8")

# Point all in-app logo surfaces at the safe vector.
for logo_user in [
    root / "app/src/main/java/org/jellyfin/androidtv/ui/startup/fragment/SplashFragment.kt",
    root / "app/src/main/java/org/jellyfin/androidtv/ui/shared/toolbar/Toolbar.kt",
    root / "app/src/main/java/org/jellyfin/androidtv/integration/dream/composable/DreamContentLogo.kt",
]:
    if logo_user.exists():
        q = logo_user.read_text(encoding="utf-8")
        q = q.replace("R.drawable.app_logo", "R.drawable.nova_mark")
        logo_user.write_text(q, encoding="utf-8")

# Build a proper NOVA splash using the vector mark plus the wordmark/tagline.
splash_file = root / "app/src/main/java/org/jellyfin/androidtv/ui/startup/fragment/SplashFragment.kt"
if splash_file.exists():
    q = splash_file.read_text(encoding="utf-8")
    q = q.replace(
        "import androidx.compose.foundation.layout.Column\n",
        "import androidx.compose.foundation.layout.Column\nimport androidx.compose.foundation.layout.Spacer\n"
    )
    q = q.replace(
        "import androidx.compose.foundation.layout.fillMaxSize\n",
        "import androidx.compose.foundation.layout.fillMaxSize\nimport androidx.compose.foundation.layout.height\n"
    )
    q = q.replace(
        "import androidx.compose.ui.Alignment\n",
        "import androidx.compose.ui.Alignment\nimport androidx.compose.ui.graphics.Color\n"
    )
    q = q.replace(
        "import androidx.compose.ui.unit.dp\n",
        "import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp\n"
    )
    q = q.replace(
        "import org.jellyfin.androidtv.ui.base.JellyfinTheme\n",
        "import org.jellyfin.androidtv.ui.base.JellyfinTheme\nimport org.jellyfin.androidtv.ui.base.Text\n"
    )
    old_splash_image = """\t\t\tImage(
\t\t\t\tpainter = painterResource(R.drawable.nova_mark),
\t\t\t\tcontentDescription = stringResource(R.string.app_name),
\t\t\t\tmodifier = Modifier
\t\t\t\t\t.width(400.dp)
\t\t\t\t\t.fillMaxHeight()
\t\t\t)
"""
    new_splash_image = """\t\t\tImage(
\t\t\t\tpainter = painterResource(R.drawable.nova_mark),
\t\t\t\tcontentDescription = stringResource(R.string.app_name),
\t\t\t\tmodifier = Modifier.width(240.dp)
\t\t\t)
\t\t\tSpacer(modifier = Modifier.height(14.dp))
\t\t\tText(
\t\t\t\ttext = "NOVA",
\t\t\t\tcolor = Color.White,
\t\t\t\tfontSize = 56.sp,
\t\t\t\tfontWeight = FontWeight.Bold,
\t\t\t\tletterSpacing = 6.sp,
\t\t\t)
\t\t\tText(
\t\t\t\ttext = "YOUR MEDIA",
\t\t\t\tcolor = Color(0xFFBDBDBD),
\t\t\t\tfontSize = 16.sp,
\t\t\t\tletterSpacing = 4.sp,
\t\t\t)
"""
    q = q.replace(old_splash_image, new_splash_image)
    splash_file.write_text(q, encoding="utf-8")

# Fire TV launcher banner: NOVA / YOUR MEDIA (assets/nova_tv_banner.png).
manifest = root / "app/src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
m = m.replace('android:banner="@mipmap/app_banner"', 'android:banner="@drawable/nova_tv_banner"')

# Private IPTV build: register the in-app playlist browser/player. Provider
# credentials or playlist URLs are never stored in source; they are entered on
# the Fire TV and kept in app-private SharedPreferences.
iptv_activity = """        <activity
            android:name=".ui.iptv.NovaIptvActivity"
            android:configChanges="density|screenSize|smallestScreenSize|screenLayout|orientation|keyboard|keyboardHidden|navigation|uiMode"
            android:launchMode="singleTop"
            android:screenOrientation="landscape"
            android:windowSoftInputMode="adjustNothing" />

"""
main_activity_anchor = """        <!-- Main application -->
        <activity
            android:name=".ui.browsing.MainActivity"""
if iptv_activity.strip() not in m:
    if main_activity_anchor not in m:
        raise RuntimeError("Could not find MainActivity manifest anchor for NOVA")
    m = m.replace(
        """        <!-- Main application -->
""",
        """        <!-- Main application -->
""" + iptv_activity,
        1,
    )

manifest.write_text(m, encoding="utf-8")

searchable = root / "app/src/main/res/xml/searchable.xml"
if searchable.exists():
    q = searchable.read_text(encoding="utf-8")
    searchable.write_text(q, encoding="utf-8")

# Remove remaining hard-coded Jellyfin branding from startup/about UI.

about_screen = root / "app/src/main/java/org/jellyfin/androidtv/ui/settings/screen/about/SettingsAboutScreen.kt"
if about_screen.exists():
    q = about_screen.read_text(encoding="utf-8")
    q = q.replace('val heading = "Jellyfin app version"', 'val heading = "NOVA app version"')
    q = q.replace(
        'val caption = "jellyfin-androidtv ${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_TYPE}"',
        'val caption = "NOVA ${BuildConfig.VERSION_NAME}"'
    )
    about_screen.write_text(q, encoding="utf-8")


drawable_dir = root / "app/src/main/res/drawable"
drawable_dir.mkdir(parents=True, exist_ok=True)


# NOVA wording on first-run strings.
strings = root / "app/src/main/res/values/strings.xml"
sv = strings.read_text(encoding="utf-8")
sv = sv.replace(
    '1. Open the Jellyfin app on your phone or webbrowser, and sign in with your account',
    '1. Open your media server in a phone or web browser and sign in'
)
sv = sv.replace(
    '2. Open the user menu and go to the Quick Connect page',
    '2. Open the user menu and choose Quick Connect'
)
strings.write_text(sv, encoding="utf-8")

# Make server cards match the red/black NOVA setup language.
server_button = root / "app/src/main/java/org/jellyfin/androidtv/ui/ServerButtonView.kt"
sb = server_button.read_text(encoding="utf-8")
if "import androidx.compose.ui.graphics.Color" not in sb:
    sb = sb.replace(
        "import androidx.compose.ui.graphics.Shape\n",
        "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.Shape\n"
    )
# Add explicit NOVA colors at the callsite through the composable function.
if "colors: ButtonColors = ButtonDefaults.colors()" not in sb:
    sb = sb.replace(
        "shape: Shape = ButtonDefaults.Shape,\n) {",
        """shape: Shape = ButtonDefaults.Shape,
	colors: org.jellyfin.androidtv.ui.base.button.ButtonColors = ButtonDefaults.colors(
		containerColor = Color(0xFF242424),
		contentColor = Color(0xFFE6E6E6),
		focusedContainerColor = Color(0xFFE50914),
		focusedContentColor = Color.White,
	),
) {"""
    )
    sb = sb.replace(
        "shape = shape,\n\t) {",
        "shape = shape,\n\t\tcolors = colors,\n\t) {",
        1
    )
server_button.write_text(sb, encoding="utf-8")

# Startup toolbar: NOVA identity on the left, just Help on the right.
startup_toolbar = root / "app/src/main/java/org/jellyfin/androidtv/ui/shared/toolbar/StartupToolbar.kt"
startup_toolbar.write_text("""package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.IconButton

@Composable
fun StartupToolbar(
    openHelp: () -> Unit,
    openSettings: () -> Unit,
) {
    Toolbar(
        start = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.nova_mark),
                    contentDescription = "NOVA",
                    modifier = Modifier.size(34.dp),
                    tint = Color.Unspecified,
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "NOVA",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.2.sp,
                    )
                    Text(
                        text = "YOUR MEDIA",
                        color = Color(0xFF777777),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.15.sp,
                    )
                }
            }
        },
        end = {
            ToolbarButtons {
                IconButton(onClick = openHelp) {
                    Icon(
                        painter = painterResource(R.drawable.ic_help),
                        contentDescription = stringResource(R.string.help),
                    )
                }
                Spacer(Modifier.width(8.dp))
                ToolbarClock()
            }
        }
    )
}
""", encoding="utf-8")

# Connection help: keep useful behavior, replace old Jellyfin-doc visual with NOVA.
connect_help = root / "app/src/main/java/org/jellyfin/androidtv/ui/startup/fragment/ConnectHelpAlertFragment.kt"
ch = connect_help.read_text(encoding="utf-8")
ch = ch.replace("painterResource(R.drawable.qr_jellyfin_docs)", "painterResource(R.drawable.nova_mark)")
ch = ch.replace("colorResource(R.color.not_quite_black)", "Color(0xFF070707)")
if "import androidx.compose.ui.graphics.Color" not in ch:
    ch = ch.replace(
        "import androidx.compose.ui.focus.focusRequester\n",
        "import androidx.compose.ui.focus.focusRequester\nimport androidx.compose.ui.graphics.Color\n"
    )
ch = ch.replace("modifier = Modifier\n\t\t\t\t\t\t.width(200.dp)", "modifier = Modifier\n\t\t\t\t\t\t.width(180.dp)")
connect_help.write_text(ch, encoding="utf-8")


gradle = root / "app/build.gradle.kts"
text = gradle.read_text(encoding="utf-8")

text = text.replace(
    'applicationId = namespace',
    'applicationId = "app.nova.firetv.iptv"'
)

text = text.replace(
    'resValue("string", "app_id", namespace!!)',
    'resValue("string", "app_id", "app.nova.firetv.iptv")'
)
text = text.replace(
    'resValue("string", "app_search_suggest_authority", "${namespace}.content")',
    'resValue("string", "app_search_suggest_authority", "app.nova.firetv.iptv.content")'
)
text = text.replace(
    'resValue("string", "app_search_suggest_intent_data", "content://${namespace}.content/intent")',
    'resValue("string", "app_search_suggest_intent_data", "content://app.nova.firetv.iptv.content/intent")'
)

text = text.replace(
    'resValue("string", "app_id", namespace + applicationIdSuffix)',
    'resValue("string", "app_id", "app.nova.firetv.iptv.debug")'
)
text = text.replace(
    'resValue("string", "app_search_suggest_authority", "${namespace + applicationIdSuffix}.content")',
    'resValue("string", "app_search_suggest_authority", "app.nova.firetv.iptv.debug.content")'
)
text = text.replace(
    'resValue("string", "app_search_suggest_intent_data", "content://${namespace + applicationIdSuffix}.content/intent")',
    'resValue("string", "app_search_suggest_intent_data", "content://app.nova.firetv.iptv.debug.content/intent")'
)

text = text.replace(
    'base.archivesName.set("jellyfin-androidtv-v${project.getVersionName()}")',
    'base.archivesName.set("nova-tv-v${project.getVersionName()}")'
)

# Build a real release variant for NOVA v1. If no private release keystore is
# configured in CI, fall back to Android's debug signing key so the APK remains
# installable while BuildConfig.DEBUG stays false.
text = text.replace(
    'signingConfig = signingConfigs.findByName("release")',
    'signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")'
)

# AboutLibraries otherwise tries GitHub/SPDX lookups during every release build.
# NOVA does not need live license metadata at build time; offline mode avoids
# network/rate-limit stalls and makes CI deterministic and faster.
if "aboutLibraries {" not in text:
    text += """

aboutLibraries {
    offlineMode = true
}
"""

gradle.write_text(text, encoding="utf-8")

strings = root / "app/src/main/res/values/strings.xml"
s = strings.read_text(encoding="utf-8")
s = s.replace(
    '<string name="app_name_release" translatable="false" tools:ignore="UnusedResources">Jellyfin</string>',
    '<string name="app_name_release" translatable="false" tools:ignore="UnusedResources">NOVA</string>'
)
s = s.replace(
    '<string name="app_name_debug" translatable="false" tools:ignore="UnusedResources">Jellyfin Debug</string>',
    '<string name="app_name_debug" translatable="false" tools:ignore="UnusedResources">NOVA Debug</string>'
)
strings.write_text(s, encoding="utf-8")

# Replace remaining visible Jellyfin branding in the English resources used on
# this Fire TV build. Lower-case URLs/package names are intentionally left alone.
for localized_strings in [
    root / "app/src/main/res/values/strings.xml",
    root / "app/src/main/res/values-en-rGB/strings.xml",
]:
    if localized_strings.exists():
        localized_text = localized_strings.read_text(encoding="utf-8")
        localized_text = localized_text.replace("Jellyfin", "NOVA")
        localized_strings.write_text(localized_text, encoding="utf-8")

# The custom Home/category rails still contain the previous NOVA wordmark.


# Home-screen cinematic layout hooks.
home_rows = root / "app/src/main/java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt"
h = home_rows.read_text(encoding="utf-8")

# NOVA uses a fixed Home order, with random movies first.
# A new random set is requested each time the Home screen is created.
old_sections = "val homesections = userSettingPreferences.activeHomesections"
new_sections = """val homesections = emptyList<HomeSectionType>()"""
if old_sections not in h:
    raise RuntimeError("Could not find home section ordering hook")
h = h.replace(old_sections, new_sections, 1)

# Imports needed by the custom Random Movies row.
import_anchor = "import org.jellyfin.androidtv.data.model.DataRefreshService"
if import_anchor not in h:
    raise RuntimeError("Could not find HomeRowsFragment import anchor")
h = h.replace(
    import_anchor,
    """import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef""",
    1
)

sdk_import_anchor = "import org.jellyfin.sdk.api.client.ApiClient"
if sdk_import_anchor not in h:
    raise RuntimeError("Could not find HomeRowsFragment SDK import anchor")
h = h.replace(
    sdk_import_anchor,
    """import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest""",
    1
)

# Add the NOVA Home content rows after Jellyfin has finished processing its
# (now empty) configurable Home-section list.
rows_anchor = """			// Add sections to layout
			withContext(Dispatchers.Main) {"""
random_row = """			val randomMovies = GetItemsRequest(
				fields = ItemRepository.browseFields,
				imageTypeLimit = 1,
				enableTotalRecordCount = false,
				includeItemTypes = setOf(BaseItemKind.MOVIE),
				recursive = true,
				sortBy = setOf(ItemSortBy.RANDOM),
				limit = 20,
			)
			rows.add(
				HomeFragmentBrowseRowDefRow(
					BrowseRowDef("Random Movies", randomMovies, 20)
				)
			)
			val views = userViewsRepository.views.first()
			val remainingViews = views.toMutableList()
			fun takeView(name: String, type: org.jellyfin.sdk.model.api.CollectionType? = null) =
				remainingViews.firstOrNull { it.name?.filter(Char::isLetterOrDigit).equals(name.filter(Char::isLetterOrDigit), ignoreCase = true) }
					?.also { remainingViews.remove(it) }
					?: type?.let { collectionType ->
						remainingViews.firstOrNull {
							it.collectionType == collectionType
						}?.also { remainingViews.remove(it) }
					}
			val movies = takeView("Movies", org.jellyfin.sdk.model.api.CollectionType.MOVIES)
			val indianMovies = takeView("Indian Movies")
			val shows = takeView("TV Shows", org.jellyfin.sdk.model.api.CollectionType.TVSHOWS)
			val prayers = takeView("Prayers")
			val music = takeView("Music", org.jellyfin.sdk.model.api.CollectionType.MUSIC)
			movies?.let { rows.add(helper.loadRecentlyAdded(listOf(it))) }
			indianMovies?.let { rows.add(helper.loadRecentlyAdded(listOf(it))) }
			shows?.let { rows.add(helper.loadRecentlyAdded(listOf(it))) }
			rows.add(helper.loadResumeVideo())
			prayers?.let { rows.add(helper.loadRecentlyAdded(listOf(it))) }
			music?.let { rows.add(helper.loadRecentlyAdded(listOf(it))) }
			// Preserve other libraries after the requested Home sections.
			rows.add(helper.loadRecentlyAdded(remainingViews))

			// Add sections to layout
			withContext(Dispatchers.Main) {"""
if rows_anchor not in h:
    raise RuntimeError("Could not find HomeRowsFragment rows layout anchor")
h = h.replace(rows_anchor, random_row, 1)

# Do not add Jellyfin notification/audio rows above NOVA's Random Movies row.
old_special_rows = """				notificationsRow.addToRowsAdapter(requireContext(), cardPresenter, adapter as MutableObjectAdapter<Row>)
				nowPlaying.addToRowsAdapter(requireContext(), cardPresenter, adapter as MutableObjectAdapter<Row>)
				for (row in rows) row.addToRowsAdapter(requireContext(), cardPresenter, adapter as MutableObjectAdapter<Row>)"""
new_special_rows = """				for (row in rows) row.addToRowsAdapter(requireContext(), cardPresenter, adapter as MutableObjectAdapter<Row>)"""
if old_special_rows not in h:
    raise RuntimeError("Could not find HomeRowsFragment special-row hook")
h = h.replace(old_special_rows, new_special_rows, 1)


# Keep NOVA's 20 random Home movies stable while the user enters/exits playback.
# Playback progress emits UserDataChanged messages; refreshing the row there would
# execute ItemSortBy.RANDOM again and replace the whole set.
user_data_refresh = """				api.webSocket.subscribe<UserDataChangedMessage>()
					.onEach { refreshRows(force = true, delayed = false) }
					.launchIn(this)"""
user_data_stable = """				api.webSocket.subscribe<UserDataChangedMessage>()
					.onEach { refreshCurrentItem() }
					.launchIn(this)"""
if user_data_refresh not in h:
    raise RuntimeError("Could not find Home UserData refresh hook")
h = h.replace(user_data_refresh, user_data_stable, 1)

# Likewise, returning from the player should update the selected card only,
# not re-run the random query.
resume_refresh = """		if (!justLoaded) {
			//Re-retrieve anything that needs it but delay slightly so we don't take away gui landing
			refreshCurrentItem()
			refreshRows()
		} else {
			justLoaded = false
		}"""
resume_stable = """		if (!justLoaded) {
			// Returning from playback: keep the same random movie set and only
			// refresh metadata/progress for the currently selected card.
			refreshCurrentItem()
		} else {
			justLoaded = false
		}"""
if resume_refresh not in h:
    raise RuntimeError("Could not find Home onResume row refresh hook")
h = h.replace(resume_refresh, resume_stable, 1)


# Fix D-pad navigation between horizontal rows and the Compose sidebar.
# Left should move within the row normally; only the first card hands focus to the rail.
field_anchor = """	private var justLoaded = true"""
field_replacement = """	private var justLoaded = true

	private var focusedRowPosition = 0
	private val focusedCardPositions = mutableMapOf<Int, Int>()
	private var selectionGeneration = 0
	private var selectingCard = false
	private var cardsNavigationEnabled = true

	private fun leaveCards(destination: (() -> Unit)?) {
		cardsNavigationEnabled = false
		selectingCard = false
		selectionGeneration++
		verticalGridView?.let { grid ->
			grid.stopScroll()
			grid.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
			grid.isFocusable = false
		}
		destination?.invoke()
	}

	private fun selectCard(rowPosition: Int, cardPosition: Int) {
		val row = adapter.get(rowPosition) as? ListRow ?: return
		if (row.adapter.size() == 0) return
		val position = cardPosition.coerceIn(0, row.adapter.size() - 1)
		focusedRowPosition = rowPosition
		focusedCardPositions[rowPosition] = position
		selectingCard = true
		val generation = ++selectionGeneration
		val task = androidx.leanback.widget.ListRowPresenter.SelectItemViewHolderTask(position).apply {
			isSmoothScroll = true
			itemTask = object : Presenter.ViewHolderTask() {
				override fun run(holder: Presenter.ViewHolder) {
					if (!cardsNavigationEnabled || generation != selectionGeneration) return
					selectingCard = false
					holder.view.requestFocus()
				}
			}
		}
		setSelectedPosition(rowPosition, true, task)
	}

	fun requestCardFocus() {
		if (view == null || verticalGridView == null || adapter.size() == 0) return
		val row = focusedRowPosition.coerceIn(0, adapter.size() - 1)
		val card = focusedCardPositions[row] ?: 0
		cardsNavigationEnabled = true
		verticalGridView.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
		verticalGridView.isFocusable = true
		verticalGridView.requestFocus()
		selectCard(row, card)
	}

	var onNavigateToSidebar: (() -> Unit)? = null"""
if field_anchor not in h:
    raise RuntimeError("Could not find HomeRowsFragment callback anchor")
h = h.replace(field_anchor, field_replacement, 1)

key_anchor = """	override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {"""
key_replacement = """	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		verticalGridView.setOnKeyInterceptListener(::handleNavigation)
	}

	fun handleNavigation(event: KeyEvent): Boolean {
		// Keys can still arrive while the page closes; the rows are gone by then.
		if (view == null || verticalGridView == null) return false
		if (!cardsNavigationEnabled) return false
		val direction = event.keyCode in setOf(
			KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
			KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
		)
		if (!direction) return false
		if (event.action != KeyEvent.ACTION_DOWN) return true
		if (adapter.size() == 0) return true
		var rowPosition = focusedRowPosition.coerceIn(0, adapter.size() - 1)
		var row = adapter.get(rowPosition) as? ListRow
		if (row == null || row.adapter.size() == 0) {
			rowPosition = (0 until adapter.size()).firstOrNull {
				val candidate = adapter.get(it) as? ListRow
				candidate != null && candidate.adapter.size() > 0
			} ?: return true
			row = adapter.get(rowPosition) as ListRow
		}
		val cardPosition = (focusedCardPositions[rowPosition] ?: 0).coerceIn(0, row.adapter.size() - 1)
		focusedRowPosition = rowPosition
		focusedCardPositions[rowPosition] = cardPosition
		when (event.keyCode) {
			KeyEvent.KEYCODE_DPAD_LEFT -> {
				if (cardPosition == 0) {
					leaveCards(onNavigateToSidebar)
				}
				else selectCard(rowPosition, cardPosition - 1)
			}
			KeyEvent.KEYCODE_DPAD_RIGHT -> {
				if (cardPosition < row.adapter.size() - 1) selectCard(rowPosition, cardPosition + 1)
			}
			KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
				val step = if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
				var target = rowPosition + step
				while (target in 0 until adapter.size()) {
					val next = adapter.get(target) as? ListRow
					if (next != null && next.adapter.size() > 0) break
					target += step
				}
				if (target in 0 until adapter.size()) selectCard(target, focusedCardPositions[target] ?: cardPosition)

			}
		}
		return true
	}

	override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {"""
if key_anchor not in h:
    raise RuntimeError("Could not find HomeRowsFragment key navigation anchor")
h = h.replace(key_anchor, key_replacement, 1)


# Make home media rows uniform poster cards, larger, and image-only.
image_import_anchor = "import org.jellyfin.androidtv.constant.HomeSectionType"
if image_import_anchor not in h:
    raise RuntimeError("Could not find ImageType import anchor")
h = h.replace(
    image_import_anchor,
    image_import_anchor + "\nimport org.jellyfin.androidtv.constant.ImageType",
    1
)

old_presenter = "val cardPresenter = CardPresenter()"
if old_presenter not in h:
    raise RuntimeError("Could not find home CardPresenter() hook")
h = h.replace(old_presenter, "val cardPresenter = CardPresenter(false, ImageType.POSTER, 158)", 1)

old_background = """				backgroundService.setBackground(item.baseItem)"""
new_background = """				if (!selectingCard && cardsNavigationEnabled) {
					focusedRowPosition = selectedPosition.coerceAtLeast(0)
					focusedCardPositions[focusedRowPosition] = itemRowAdapter?.indexOf(item)?.coerceAtLeast(0) ?: 0
				}
				HomeHeroStateStore.update(item.baseItem)
				backgroundService.setBackground(item.baseItem)"""
if old_background not in h:
    raise RuntimeError("Could not find hero update hook")
h = h.replace(old_background, new_background, 1)

# Compose/Leanback focus changes can briefly report a non-ListRow selection.
# Avoid the old unchecked row cast so normal D-pad browsing cannot crash Home.
unsafe_home_selection = """			if (item !is BaseRowItem) {
				currentItem = null
				//fill in default background
				backgroundService.clearBackgrounds()
			} else {
				currentItem = item
				currentRow = row as ListRow"""
safe_home_selection = """			if (item !is BaseRowItem || row !is ListRow) {
				currentItem = null
				currentRow = null
				//fill in default background
				backgroundService.clearBackgrounds()
			} else {
				currentItem = item
				currentRow = row"""
if unsafe_home_selection not in h:
    raise RuntimeError("Could not find Home selection safety hook")
h = h.replace(unsafe_home_selection, safe_home_selection, 1)

home_rows.write_text(h, encoding="utf-8")

# v0.19.10 legacy media cards are customised below. Do not install a
# second focus listener here: Leanback already handles the normal focus scale,
# and LegacyImageCardView owns the NOVA red focus frame.
card_presenter = root / "app/src/main/java/org/jellyfin/androidtv/ui/presentation/CardPresenter.java"
c = card_presenter.read_text(encoding="utf-8")

# Force image-only POSTER presenters to keep video cards at a uniform 2:3
# aspect. For episodes, use the parent series poster instead of a 16:9 still.
old_episode_block = """                        case EPISODE:
                            if (m instanceof BaseItemDtoBaseRowItem && ((BaseItemDtoBaseRowItem) m).getPreferSeriesPoster()) {
                                mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_port_tv);
                                aspect = ImageHelper.ASPECT_RATIO_2_3;
                            } else {
                                mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_land_tv);
                                aspect = ImageHelper.ASPECT_RATIO_16_9;
                                if (itemDto.getLocationType() != null) {
                                    switch (itemDto.getLocationType()) {
                                        case FILE_SYSTEM:
                                            break;
                                        case REMOTE:
                                            break;
                                        case VIRTUAL:
                                            mCardView.setBanner(itemDto.getPremiereDate() == null || itemDto.getPremiereDate().isAfter(LocalDateTime.now()) ? R.drawable.banner_edge_future : R.drawable.banner_edge_missing);
                                            break;
                                    }
                                }
                                showProgress = true;
                                //Always show info for episodes
                                mCardView.setCardType(BaseCardView.CARD_TYPE_INFO_UNDER);
                            }
                            break;"""

new_episode_block = """                        case EPISODE:
                            if (
                                imageType.equals(ImageType.POSTER) ||
                                (m instanceof BaseItemDtoBaseRowItem && ((BaseItemDtoBaseRowItem) m).getPreferSeriesPoster())
                            ) {
                                mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_port_tv);
                                aspect = ImageHelper.ASPECT_RATIO_2_3;
                                showProgress = true;
                            } else {
                                mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_land_tv);
                                aspect = ImageHelper.ASPECT_RATIO_16_9;
                                if (itemDto.getLocationType() != null) {
                                    switch (itemDto.getLocationType()) {
                                        case FILE_SYSTEM:
                                            break;
                                        case REMOTE:
                                            break;
                                        case VIRTUAL:
                                            mCardView.setBanner(itemDto.getPremiereDate() == null || itemDto.getPremiereDate().isAfter(LocalDateTime.now()) ? R.drawable.banner_edge_future : R.drawable.banner_edge_missing);
                                            break;
                                    }
                                }
                                showProgress = true;
                                mCardView.setCardType(BaseCardView.CARD_TYPE_INFO_UNDER);
                            }
                            break;"""

if old_episode_block not in c:
    raise RuntimeError("Could not find CardPresenter episode block")
c = c.replace(old_episode_block, new_episode_block, 1)

# Keep Nova home cards image-only and make artwork fill the full poster tile.
# Some episode cards force INFO_UNDER and recycled holders can then show title/
# rating text under movies too, so reset the card mode on every bind when this
# presenter was created with showInfo=false.
old_bind_block = """        holder.setItem(rowItem, mImageType, 130, 150, mStaticHeight);

        holder.mCardView.setTitleText(rowItem.getCardName(holder.mCardView.getContext()));
        holder.mCardView.setContentText(rowItem.getSubText(holder.mCardView.getContext()));"""
new_bind_block = """        holder.setItem(rowItem, mImageType, 130, 150, mStaticHeight);

        if (!mShowInfo) {
            holder.mCardView.setCardType(BaseCardView.CARD_TYPE_MAIN_ONLY);
            holder.mCardView.setTitleText(null);
            holder.mCardView.setContentText(null);
            holder.mCardView.getMainImageView().setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            holder.mCardView.setTitleText(rowItem.getCardName(holder.mCardView.getContext()));
            holder.mCardView.setContentText(rowItem.getSubText(holder.mCardView.getContext()));
        }"""
if old_bind_block not in c:
    raise RuntimeError("Could not find CardPresenter bind block")
c = c.replace(old_bind_block, new_bind_block, 1)

# For 16:9 Nova cards, never use a portrait poster as the first choice.
# Prefer a true landscape Thumb, then Backdrop, then parent landscape art.
# Episodes can use their own PRIMARY image because Jellyfin episode primary art
# is normally a 16:9 still frame.
old_image_selection = """        JellyfinImage image = null;
        if (rowItem.getBaseItem() != null) {
            if (aspect == ImageHelper.ASPECT_RATIO_BANNER) {
                image = JellyfinImageKt.getItemImages(rowItem.getBaseItem()).get(org.jellyfin.sdk.model.api.ImageType.BANNER);
            } else if (aspect == ImageHelper.ASPECT_RATIO_2_3 && rowItem.getBaseItem().getType() == BaseItemKind.EPISODE && rowItem instanceof BaseItemDtoBaseRowItem && ((BaseItemDtoBaseRowItem) rowItem).getPreferSeriesPoster()) {
                image = JellyfinImageKt.getSeriesPrimaryImage(rowItem.getBaseItem());
            } else if (aspect == ImageHelper.ASPECT_RATIO_16_9 && !isUserView && (rowItem.getBaseItem().getType() != BaseItemKind.EPISODE || !rowItem.getBaseItem().getImageTags().containsKey(org.jellyfin.sdk.model.api.ImageType.PRIMARY) || (rowItem.getPreferParentThumb() && rowItem.getBaseItem().getParentThumbImageTag() != null))) {
                if (rowItem.getPreferParentThumb() || !rowItem.getBaseItem().getImageTags().containsKey(org.jellyfin.sdk.model.api.ImageType.PRIMARY)) {
                    image = JellyfinImageKt.getParentImages(rowItem.getBaseItem()).get(org.jellyfin.sdk.model.api.ImageType.THUMB);
                } else {
                    image = JellyfinImageKt.getItemImages(rowItem.getBaseItem()).get(org.jellyfin.sdk.model.api.ImageType.THUMB);
                }
            } else {
                image = JellyfinImageKt.getItemImages(rowItem.getBaseItem()).get(org.jellyfin.sdk.model.api.ImageType.PRIMARY);
            }
        }"""

new_image_selection = """        JellyfinImage image = null;
        if (rowItem.getBaseItem() != null) {
            BaseItemDto baseItem = rowItem.getBaseItem();

            if (aspect == ImageHelper.ASPECT_RATIO_BANNER) {
                image = JellyfinImageKt.getItemImages(baseItem).get(org.jellyfin.sdk.model.api.ImageType.BANNER);
            } else if (
                aspect == ImageHelper.ASPECT_RATIO_2_3 &&
                baseItem.getType() == BaseItemKind.EPISODE
            ) {
                image = JellyfinImageKt.getSeriesPrimaryImage(baseItem);
            } else if (aspect == ImageHelper.ASPECT_RATIO_16_9 && !isUserView) {
                if (baseItem.getType() == BaseItemKind.EPISODE) {
                    image = JellyfinImageKt.getItemImages(baseItem).get(org.jellyfin.sdk.model.api.ImageType.PRIMARY);
                }

                if (image == null) {
                    image = JellyfinImageKt.getItemImages(baseItem).get(org.jellyfin.sdk.model.api.ImageType.THUMB);
                }

                if (image == null && !JellyfinImageKt.getItemBackdropImages(baseItem).isEmpty()) {
                    image = JellyfinImageKt.getItemBackdropImages(baseItem).get(0);
                }

                if (image == null) {
                    image = JellyfinImageKt.getParentImages(baseItem).get(org.jellyfin.sdk.model.api.ImageType.THUMB);
                }

                if (image == null && !JellyfinImageKt.getParentBackdropImages(baseItem).isEmpty()) {
                    image = JellyfinImageKt.getParentBackdropImages(baseItem).get(0);
                }
            } else {
                image = JellyfinImageKt.getItemImages(baseItem).get(org.jellyfin.sdk.model.api.ImageType.PRIMARY);
            }
        }"""

if old_image_selection not in c:
    raise RuntimeError("Could not find CardPresenter image selection block")
c = c.replace(old_image_selection, new_image_selection, 1)

card_presenter.write_text(c, encoding="utf-8")


# Style all library/category pages to match the Nova home screen.
browse_grid = root / "app/src/main/java/org/jellyfin/androidtv/ui/browsing/BrowseGridFragment.java"
b = browse_grid.read_text(encoding="utf-8")

# The sidebar occupies 188dp, so size the content grid to the remaining width.
old_grid_width = """        mGridWidth = Math.round(display.widthPixels / getResources().getDisplayMetrics().density);"""
new_grid_width = """        mGridWidth = Math.round(display.widthPixels / getResources().getDisplayMetrics().density - 188f);"""
if old_grid_width not in b:
    raise RuntimeError("Could not find BrowseGridFragment width hook")
b = b.replace(old_grid_width, new_grid_width, 1)

# Force a consistent poster-grid view on Nova category pages.
old_initial_prefs = """        mPosterSizeSetting = libraryPreferences.get(LibraryPreferences.Companion.getPosterSize());
        mImageType = libraryPreferences.get(LibraryPreferences.Companion.getImageType());
        mGridDirection = libraryPreferences.get(LibraryPreferences.Companion.getGridDirection());"""
new_initial_prefs = """        mPosterSizeSetting = PosterSize.MED;
        mImageType = ImageType.POSTER;
        mGridDirection = GridDirection.VERTICAL;"""
if old_initial_prefs not in b:
    raise RuntimeError("Could not find BrowseGridFragment initial preferences hook")
b = b.replace(old_initial_prefs, new_initial_prefs, 1)

old_resume_prefs = """        PosterSize posterSizeSetting = libraryPreferences.get(LibraryPreferences.Companion.getPosterSize());
        ImageType imageType = libraryPreferences.get(LibraryPreferences.Companion.getImageType());
        GridDirection gridDirection = libraryPreferences.get(LibraryPreferences.Companion.getGridDirection());"""
new_resume_prefs = """        PosterSize posterSizeSetting = PosterSize.MED;
        ImageType imageType = ImageType.POSTER;
        GridDirection gridDirection = GridDirection.VERTICAL;"""
if old_resume_prefs not in b:
    raise RuntimeError("Could not find BrowseGridFragment resume preferences hook")
b = b.replace(old_resume_prefs, new_resume_prefs, 1)

# Populate the same Home/Search/library/settings rail used on the Nova home page.
old_view_created = """        createGrid();
        loadGrid();
        addTools();"""
new_view_created = """        createGrid();
        loadGrid();
        addTools();
        NovaSidebarHelperKt.setupNovaSidebar(
            this,
            binding.novaSidebarItems,
            mFolder,
            userViewsRepository.getValue(),
            itemLauncher.getValue(),
            navigationRepository.getValue()
        );"""
if old_view_created not in b:
    raise RuntimeError("Could not find BrowseGridFragment onViewCreated hook")
b = b.replace(old_view_created, new_view_created, 1)

# Let Left move within the grid normally. Only when the selected card is in the
# first column should focus move into the currently active library button.
vertical_import = "import androidx.leanback.widget.VerticalGridPresenter;"
if vertical_import not in b:
    raise RuntimeError("Could not find VerticalGridPresenter import")

grid_focus_anchor = """        mGridView.setHorizontalSpacing(mGridItemSpacingHorizontal);
        mGridView.setVerticalSpacing(mGridItemSpacingVertical);
        mGridView.setFocusable(true);"""
grid_focus_replacement = """        mGridView.setHorizontalSpacing(mGridItemSpacingHorizontal);
        mGridView.setVerticalSpacing(mGridItemSpacingVertical);
        mGridView.setFocusable(true);

        mGridView.setOnKeyInterceptListener(event -> {
            if (
                event.getAction() == KeyEvent.ACTION_DOWN &&
                event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT &&
                mGridPresenter instanceof VerticalGridPresenter
            ) {
                int columns = Math.max(
                    1,
                    ((VerticalGridPresenter) mGridPresenter).getNumberOfColumns()
                );
                int position = mGridView.getSelectedPosition();

                if (position >= 0 && position % columns == 0) {
                    Object sidebarTarget = binding.novaSidebarItems.getTag();
                    if (sidebarTarget instanceof View) {
                        ((View) sidebarTarget).requestFocus();
                        return true;
                    }
                }
            }

            return false;
        });"""
if grid_focus_anchor not in b:
    raise RuntimeError("Could not find BrowseGridFragment focus hook")
b = b.replace(grid_focus_anchor, grid_focus_replacement, 1)

browse_grid.write_text(b, encoding="utf-8")

# Route Movies, TV Shows and Music through the same Nova library browser so all
# left-rail categories share one visual system instead of mixing Jellyfin screens.
item_launcher = root / "app/src/main/java/org/jellyfin/androidtv/ui/itemhandling/ItemLauncher.java"
i = item_launcher.read_text(encoding="utf-8")

old_user_view_switch = """        switch (collectionType) {
            case MOVIES:
            case TVSHOWS:
                LibraryPreferences displayPreferences = preferencesRepository.getValue().getLibraryPreferences(baseItem.getDisplayPreferencesId());
                boolean enableSmartScreen = displayPreferences.get(LibraryPreferences.Companion.getEnableSmartScreen());

                if (!enableSmartScreen) return Destinations.INSTANCE.libraryBrowser(baseItem);
                else return Destinations.INSTANCE.librarySmartScreen(baseItem);
            case MUSIC:
            case LIVETV:
                return Destinations.INSTANCE.librarySmartScreen(baseItem);
            default:
                return Destinations.INSTANCE.libraryBrowser(baseItem);
        }"""
new_user_view_switch = """        switch (collectionType) {
            case MOVIES:
            case TVSHOWS:
            case MUSIC:
                return Destinations.INSTANCE.libraryBrowser(baseItem);
            case LIVETV:
                return Destinations.INSTANCE.librarySmartScreen(baseItem);
            default:
                return Destinations.INSTANCE.libraryBrowser(baseItem);
        }"""
if old_user_view_switch not in i:
    raise RuntimeError("Could not find ItemLauncher user-view routing hook")
i = i.replace(old_user_view_switch, new_user_view_switch, 1)

item_launcher.write_text(i, encoding="utf-8")



# Convert category pages from dense poster grids into Home-style horizontal rows
# with selected-title info and the same Nova left navigation rail.
enhanced_browse = root / "app/src/main/java/org/jellyfin/androidtv/ui/browsing/EnhancedBrowseFragment.java"
e = enhanced_browse.read_text(encoding="utf-8")

user_views_import = "import org.jellyfin.androidtv.data.repository.CustomMessageRepository;"
if user_views_import not in e:
    raise RuntimeError("Could not find EnhancedBrowseFragment repository import anchor")
e = e.replace(
    user_views_import,
    """import org.jellyfin.androidtv.data.repository.CustomMessageRepository;
import org.jellyfin.androidtv.data.repository.UserViewsRepository;""",
    1
)

binding_fields = """    protected TextView mTitle;
    private LinearLayout mInfoRow;
    private TextView mSummary;"""
binding_fields_new = """    protected TextView mTitle;
    private LinearLayout mInfoRow;
    private TextView mSummary;
    private EnhancedDetailBrowseBinding binding;"""
if binding_fields not in e:
    raise RuntimeError("Could not find EnhancedBrowseFragment binding fields")
e = e.replace(binding_fields, binding_fields_new, 1)

inject_anchor = """    private final Lazy<CustomMessageRepository> customMessageRepository = inject(CustomMessageRepository.class);
    private final Lazy<NavigationRepository> navigationRepository = inject(NavigationRepository.class);"""
inject_new = """    private final Lazy<CustomMessageRepository> customMessageRepository = inject(CustomMessageRepository.class);
    private final Lazy<UserViewsRepository> userViewsRepository = inject(UserViewsRepository.class);
    private final Lazy<NavigationRepository> navigationRepository = inject(NavigationRepository.class);"""
if inject_anchor not in e:
    raise RuntimeError("Could not find EnhancedBrowseFragment injection anchor")
e = e.replace(inject_anchor, inject_new, 1)

local_binding = """        EnhancedDetailBrowseBinding binding = EnhancedDetailBrowseBinding.inflate(inflater, container, false);"""
if local_binding not in e:
    raise RuntimeError("Could not find EnhancedBrowseFragment local binding")
e = e.replace(
    local_binding,
    """        binding = EnhancedDetailBrowseBinding.inflate(inflater, container, false);""",
    1
)

view_created = """        setupEventListeners();"""
view_created_new = """        setupEventListeners();

        NovaSidebarHelperKt.setupNovaSidebar(
            this,
            binding.novaSidebarItems,
            mFolder,
            userViewsRepository.getValue(),
            itemLauncher.getValue(),
            navigationRepository.getValue()
        );

        androidx.leanback.widget.VerticalGridView novaRowsGrid = mRowsFragment.getVerticalGridView();\n        if (novaRowsGrid != null) novaRowsGrid.setOnKeyInterceptListener(event -> {
            if (
                event.getAction() == KeyEvent.ACTION_DOWN &&
                event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT &&
                mCurrentRow != null &&
                mCurrentItem != null &&
                mCurrentRow.getAdapter() instanceof ItemRowAdapter
            ) {
                ItemRowAdapter rowAdapter = (ItemRowAdapter) mCurrentRow.getAdapter();
                if (rowAdapter.indexOf(mCurrentItem) == 0) {
                    Object sidebarTarget = binding.novaSidebarItems.getTag();
                    if (sidebarTarget instanceof View) {
                        ((View) sidebarTarget).requestFocus();
                        return true;
                    }
                }
            }

            return false;
        });"""
if view_created not in e:
    raise RuntimeError("Could not find EnhancedBrowseFragment onViewCreated hook")
e = e.replace(view_created, view_created_new, 1)

destroy_view = """        mClickedListener.removeListeners();
        mSelectedListener.removeListeners();"""
destroy_view_new = """        mClickedListener.removeListeners();
        mSelectedListener.removeListeners();
        binding = null;"""
if destroy_view not in e:
    raise RuntimeError("Could not find EnhancedBrowseFragment destroy hook")
e = e.replace(destroy_view, destroy_view_new, 1)

card_presenter = """        mCardPresenter = new CardPresenter(false, 140);"""
card_presenter_new = """        mCardPresenter = new CardPresenter(false, ImageType.POSTER, 158);
        mCardPresenter.setUniformAspect(true);"""
if card_presenter not in e:
    raise RuntimeError("Could not find EnhancedBrowseFragment card presenter")
e = e.replace(card_presenter, card_presenter_new, 1)

enhanced_browse.write_text(e, encoding="utf-8")

browse_view = root / "app/src/main/java/org/jellyfin/androidtv/ui/browsing/BrowseViewFragment.java"
v = browse_view.read_text(encoding="utf-8")

movies_anchor = """            case MOVIES:
                itemType = BaseItemKind.MOVIE;"""
movies_new = """            case MOVIES:
                itemType = BaseItemKind.MOVIE;
                showViews = false;"""
if movies_anchor not in v:
    raise RuntimeError("Could not find MOVIES BrowseView hook")
v = v.replace(movies_anchor, movies_new, 1)

tv_anchor = """            case TVSHOWS:
                itemType = BaseItemKind.SERIES;"""
tv_new = """            case TVSHOWS:
                itemType = BaseItemKind.SERIES;
                showViews = false;"""
if tv_anchor not in v:
    raise RuntimeError("Could not find TVSHOWS BrowseView hook")
v = v.replace(tv_anchor, tv_new, 1)

music_anchor = """            case MUSIC:
                //Latest"""
music_new = """            case MUSIC:
                showViews = false;
                //Latest"""
if music_anchor not in v:
    raise RuntimeError("Could not find MUSIC BrowseView hook")
v = v.replace(music_anchor, music_new, 1)

movies_load = """                //Collections
                mRows.add(new BrowseRowDef(getString(R.string.lbl_collections), BrowsingUtils.createCollectionsRequest(mFolder.getId()), 60, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated}));

                rowLoader.loadRows(mRows);"""
movies_load_new = """                //Collections
                mRows.add(new BrowseRowDef(getString(R.string.lbl_collections), BrowsingUtils.createCollectionsRequest(mFolder.getId()), 60, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated}));

                //All movies as a horizontally scrolling poster row
                mRows.add(new BrowseRowDef(getString(R.string.lbl_all_items), BrowsingUtils.createBrowseGridItemsRequest(mFolder), 60, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated}));

                rowLoader.loadRows(mRows);"""
if movies_load not in v:
    raise RuntimeError("Could not find MOVIES loadRows hook")
v = v.replace(movies_load, movies_load_new, 1)

tv_load = """                //Favorites
                mRows.add(new BrowseRowDef(getString(R.string.lbl_favorites), BrowsingUtils.createFavoriteItemsRequest(mFolder.getId(), BaseItemKind.SERIES), 60, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated, ChangeTriggerType.FavoriteUpdate}));

                rowLoader.loadRows(mRows);"""
tv_load_new = """                //Favorites
                mRows.add(new BrowseRowDef(getString(R.string.lbl_favorites), BrowsingUtils.createFavoriteItemsRequest(mFolder.getId(), BaseItemKind.SERIES), 60, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated, ChangeTriggerType.FavoriteUpdate}));

                //All shows as a horizontally scrolling poster row
                mRows.add(new BrowseRowDef(getString(R.string.lbl_all_items), BrowsingUtils.createBrowseGridItemsRequest(mFolder), 60, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated}));

                rowLoader.loadRows(mRows);"""
if tv_load not in v:
    raise RuntimeError("Could not find TVSHOWS loadRows hook")
v = v.replace(tv_load, tv_load_new, 1)

music_load = """                //AudioPlaylists
                mRows.add(new BrowseRowDef(getString(R.string.lbl_playlists), BrowsingUtils.createPlaylistsRequest(), 60, false, true, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated}, QueryType.AudioPlaylists));

                rowLoader.loadRows(mRows);"""
music_load_new = """                //AudioPlaylists
                mRows.add(new BrowseRowDef(getString(R.string.lbl_playlists), BrowsingUtils.createPlaylistsRequest(), 60, false, true, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated}, QueryType.AudioPlaylists));

                //All music items in the same horizontal browsing style
                mRows.add(new BrowseRowDef(getString(R.string.lbl_all_items), BrowsingUtils.createBrowseGridItemsRequest(mFolder), 60, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated}));

                rowLoader.loadRows(mRows);"""
if music_load not in v:
    raise RuntimeError("Could not find MUSIC loadRows hook")
v = v.replace(music_load, music_load_new, 1)

default_anchor = """            default:
                boolean isRecordingsView = getArguments().getBoolean(Extras.IsLiveTvSeriesRecordings, false);"""
default_new = """            default:
                if (mFolder != null) {
                    showViews = false;
                    mRows.add(new BrowseRowDef(
                        getString(R.string.lbl_all_items),
                        BrowsingUtils.createBrowseGridItemsRequest(mFolder),
                        60,
                        new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated}
                    ));
                    rowLoader.loadRows(mRows);
                    break;
                }

                boolean isRecordingsView = getArguments().getBoolean(Extras.IsLiveTvSeriesRecordings, false);"""
if default_anchor not in v:
    raise RuntimeError("Could not find BrowseView default hook")
v = v.replace(default_anchor, default_new, 1)

browse_view.write_text(v, encoding="utf-8")

# All top-level libraries from the Nova rail now open the Home-style row screen.
# Live TV already uses that screen; other library types use its generic All Items row.
new_user_view_switch_rows = """        switch (collectionType) {
            case LIVETV:
                return Destinations.INSTANCE.librarySmartScreen(baseItem);
            default:
                return Destinations.INSTANCE.librarySmartScreen(baseItem);
        }"""
if new_user_view_switch not in i:
    raise RuntimeError("Could not find Nova ItemLauncher routing replacement")
i = i.replace(new_user_view_switch, new_user_view_switch_rows, 1)
item_launcher.write_text(i, encoding="utf-8")

# Route normal library/user-view screens into the NOVA cinematic library page.
destinations = root / "app/src/main/java/org/jellyfin/androidtv/ui/navigation/Destinations.kt"
d = destinations.read_text(encoding="utf-8")

dest_import = "import org.jellyfin.androidtv.ui.browsing.BrowseViewFragment"
if dest_import not in d:
    raise RuntimeError("Could not find Destinations BrowseViewFragment import")
d = d.replace(
    dest_import,
    dest_import + "\nimport org.jellyfin.androidtv.ui.browsing.NovaLibraryFragment",
    1
)

old_library_smart = """\tfun librarySmartScreen(item: BaseItemDto) = fragmentDestination<BrowseViewFragment>(
\t\tExtras.Folder to Json.Default.encodeToString(item),
\t)"""
new_library_smart = """\tfun librarySmartScreen(item: BaseItemDto) = fragmentDestination<NovaLibraryFragment>(
\t\tExtras.Folder to Json.Default.encodeToString(item),
\t)"""
if old_library_smart not in d:
    raise RuntimeError("Could not find librarySmartScreen destination")
d = d.replace(old_library_smart, new_library_smart, 1)

# Add the NOVA TV-series detail destination.
series_import = "import org.jellyfin.androidtv.ui.browsing.NovaLibraryFragment"
if series_import not in d:
    raise RuntimeError("Could not find NovaLibraryFragment import for series destination")
d = d.replace(
    series_import,
    series_import + "\nimport org.jellyfin.androidtv.ui.browsing.NovaSeriesFragment",
    1
)

item_details_anchor = """\tfun itemDetails(item: UUID) = fragmentDestination<FullDetailsFragment>(
\t\t"ItemId" to item.toString(),
\t)"""
series_destination = """\tfun novaSeries(item: BaseItemDto) = fragmentDestination<NovaSeriesFragment>(
\t\tExtras.Folder to Json.Default.encodeToString(item),
\t)

""" + item_details_anchor
if item_details_anchor not in d:
    raise RuntimeError("Could not find itemDetails destination hook")
d = d.replace(item_details_anchor, series_destination, 1)

destinations.write_text(d, encoding="utf-8")

# Add a NOVA detail page for playable non-series items.
item_import = "import org.jellyfin.androidtv.ui.browsing.NovaSeriesFragment"
if item_import not in d:
    raise RuntimeError("Could not find NovaSeriesFragment import for item detail destination")
d = d.replace(
    item_import,
    item_import + "\nimport org.jellyfin.androidtv.ui.browsing.NovaItemFragment\nimport org.jellyfin.androidtv.ui.browsing.NovaInfoFragment",
    1
)

series_destination_anchor = """\tfun novaSeries(item: BaseItemDto) = fragmentDestination<NovaSeriesFragment>(
\t\tExtras.Folder to Json.Default.encodeToString(item),
\t)
"""
if series_destination_anchor not in d:
    raise RuntimeError("Could not find novaSeries destination hook")
d = d.replace(
    series_destination_anchor,
    series_destination_anchor + """
\tfun novaItem(item: BaseItemDto) = fragmentDestination<NovaItemFragment>(
\t\tExtras.Folder to Json.Default.encodeToString(item),
\t)

\tfun novaInfo(item: BaseItemDto) = fragmentDestination<NovaInfoFragment>(
\t\tExtras.Folder to Json.Default.encodeToString(item),
\t)

\tfun novaFilteredLibrary(
\t\titem: BaseItemDto,
\t\tfilterKind: String,
\t\tfilterValue: String?,
\t\ttitle: String,
\t) = fragmentDestination<NovaLibraryFragment>(
\t\tExtras.Folder to Json.Default.encodeToString(item),
\t\tNovaLibraryFragment.EXTRA_FILTER_KIND to filterKind,
\t\tNovaLibraryFragment.EXTRA_FILTER_VALUE to filterValue,
\t\tNovaLibraryFragment.EXTRA_FILTER_TITLE to title,
\t)

""",
    1
)

# Re-write Destinations after adding the generic item destination.
destinations.write_text(d, encoding="utf-8")

# Series cards now open the NOVA seasons/detail page instead of Jellyfin's legacy detail screen.
item_launcher = root / "app/src/main/java/org/jellyfin/androidtv/ui/itemhandling/ItemLauncher.java"
i = item_launcher.read_text(encoding="utf-8")

preview_controller_import = "import org.jellyfin.androidtv.ui.home.HomePreviewPlaybackController;"
playback_import_anchor = "import org.jellyfin.androidtv.ui.playback.MediaManager;"
if preview_controller_import not in i:
    if playback_import_anchor not in i:
        raise RuntimeError("Could not find ItemLauncher playback import anchor")
    i = i.replace(
        playback_import_anchor,
        preview_controller_import + "\n" + playback_import_anchor,
        1
    )

old_series_route = """                    case SERIES:
                    case MUSIC_ARTIST:
                        navigationRepository.getValue().navigate(Destinations.INSTANCE.itemDetails(baseItem.getId()));
                        return;"""
new_series_route = """                    case SERIES:
                        if (rowItem.getSelectAction() == BaseRowItemSelectAction.Play) {
                            playbackHelper.getValue().retrieveAndPlay(baseItem.getId(), false, null, context);
                        } else {
                            navigationRepository.getValue().navigate(Destinations.INSTANCE.novaSeries(baseItem));
                        }
                        return;
                    case MOVIE:
                    case VIDEO:
                        HomePreviewPlaybackController.INSTANCE.stopActivePreview();
                        // Home uses lightweight browse items that intentionally omit
                        // MediaSources/MediaStreams. retrieveAndPlay() refreshes the
                        // selected item from Jellyfin before launching playback, so
                        // PlaybackController always receives a complete media source.
                        playbackHelper.getValue().retrieveAndPlay(
                            baseItem.getId(),
                            false,
                            null,
                            context
                        );
                        return;
                    case MUSIC_ALBUM:
                    case PLAYLIST:
                        if (rowItem.getSelectAction() == BaseRowItemSelectAction.Play) {
                            playbackHelper.getValue().retrieveAndPlay(baseItem.getId(), false, null, context);
                        } else {
                            navigationRepository.getValue().navigate(Destinations.INSTANCE.novaItem(baseItem));
                        }
                        return;
                    case MUSIC_ARTIST:
                        navigationRepository.getValue().navigate(Destinations.INSTANCE.itemDetails(baseItem.getId()));
                        return;"""
if old_series_route not in i:
    raise RuntimeError("Could not find Series ItemLauncher route")
i = i.replace(old_series_route, new_series_route, 1)

old_music_route = """                    case MUSIC_ALBUM:
                    case PLAYLIST:
                        navigationRepository.getValue().navigate(Destinations.INSTANCE.itemList(baseItem.getId()));
                        return;

"""
if old_music_route not in i:
    raise RuntimeError("Could not find old music album/playlist route")
i = i.replace(old_music_route, "", 1)

# Episode cards should play immediately. Do not send the user through Jellyfin's
# legacy episode details submenu when selecting an episode from a season.
episode_anchor = """                    case SEASON:
                        navigationRepository.getValue().navigate(Destinations.INSTANCE.folderBrowser(baseItem));
                        return;"""
episode_direct_play = """                    case EPISODE:
                        HomePreviewPlaybackController.INSTANCE.stopActivePreview();
                        playbackHelper.getValue().retrieveAndPlay(
                            baseItem.getId(),
                            false,
                            null,
                            context
                        );
                        return;

                    case SEASON:
                        // NOVA's own season page (episode list), not Jellyfin's folder screen.
                        navigationRepository.getValue().navigate(Destinations.INSTANCE.novaItem(baseItem));
                        return;"""
if episode_anchor not in i:
    raise RuntimeError("Could not find Season ItemLauncher route for direct episode playback")
i = i.replace(episode_anchor, episode_direct_play, 1)

audio_play_anchor = """                    case AUDIO:
                        if (rowItem.getBaseItem() == null)"""
audio_play_nova = """                    case AUDIO:
                        if (rowItem.getSelectAction() == BaseRowItemSelectAction.Play) {
                            playbackHelper.getValue().retrieveAndPlay(baseItem.getId(), false, null, context);
                            return;
                        }
                        if (rowItem.getBaseItem() == null)"""
if i.count(audio_play_anchor) != 1:
    raise RuntimeError("Could not find ItemLauncher audio play route")
i = i.replace(audio_play_anchor, audio_play_nova, 1)

item_launcher.write_text(i, encoding="utf-8")


# ---------------------------------------------------------------------------
# NOVA global remote focus / cursor
# ---------------------------------------------------------------------------
# Make focused controls red everywhere so the Fire TV remote position is always
# obvious, and remove Android's default white focus highlight from media cards.
# Force Android/Leanback focus and ripple highlights to NOVA red too.
theme_jellyfin = root / "app/src/main/res/values/theme_jellyfin.xml"
if theme_jellyfin.exists():
    tj = theme_jellyfin.read_text(encoding="utf-8")
    highlight_item = '<item name="android:colorControlHighlight">#FFE50914</item>'
    if 'android:colorControlHighlight' not in tj:
        theme_anchor = '<style name="Theme.Jellyfin" parent="@style/Theme.AppCompat.Leanback">'
        if theme_anchor in tj:
            tj = tj.replace(theme_anchor, theme_anchor + "\n        " + highlight_item, 1)
    else:
        import re
        tj = re.sub(
            r'<item name="android:colorControlHighlight">.*?</item>',
            highlight_item,
            tj,
            count=1,
        )
    theme_jellyfin.write_text(tj, encoding="utf-8")

color_scheme = root / "app/src/main/java/org/jellyfin/androidtv/ui/base/colorScheme.kt"
if color_scheme.exists():
    cs = color_scheme.read_text(encoding="utf-8")
    cs = cs.replace("buttonFocused = Color(0xE6CCCCCC)", "buttonFocused = Color(0xFFE50914)")
    cs = cs.replace("onButtonFocused = Color(0xFF444444)", "onButtonFocused = Color(0xFFFFFFFF)")
    cs = cs.replace("inputFocused = Color(0xE6CCCCCC)", "inputFocused = Color(0xFFE50914)")
    cs = cs.replace("onInputFocused = Color(0xFFDDDDDD)", "onInputFocused = Color(0xFFFFFFFF)")
    color_scheme.write_text(cs, encoding="utf-8")

# Jellyfin Android TV v0.19.10 uses CardPresenter.java. Patch that exact
# presenter so NOVA Search can use clean poster-only cards and every legacy
# media card gets the NOVA red focus outline.
legacy_card_presenter = root / "app/src/main/java/org/jellyfin/androidtv/ui/presentation/CardPresenter.java"
if legacy_card_presenter.exists():
    cpj = legacy_card_presenter.read_text(encoding="utf-8")

    # Add a per-presenter switch used only by Search. Existing constructor calls
    # retain the normal Jellyfin overlays.
    field_anchor = "    private boolean mShowInfo = true;\n"
    if field_anchor not in cpj:
        raise RuntimeError("Could not find v0.19.10 CardPresenter show-info field")
    if "private boolean mShowOverlays" not in cpj:
        cpj = cpj.replace(
            field_anchor,
            field_anchor + "    private boolean mShowOverlays = true;\n",
            1,
        )

    constructor_anchor = """    public CardPresenter(boolean showInfo, ImageType imageType, int staticHeight) {
        this(showInfo, staticHeight);
        mImageType = imageType;
    }
"""
    constructor_nova = constructor_anchor + """
    public CardPresenter(boolean showInfo, ImageType imageType, int staticHeight, boolean showOverlays) {
        this(showInfo, imageType, staticHeight);
        mShowOverlays = showOverlays;
    }
"""
    if constructor_anchor not in cpj:
        raise RuntimeError("Could not find v0.19.10 CardPresenter image constructor")
    if "boolean showOverlays)" not in cpj:
        cpj = cpj.replace(constructor_anchor, constructor_nova, 1)

    # Search poster-only cards should not draw watched/progress badges.
    user_data_anchor = """                    UserItemDataDto userData = itemDto.getUserData();
                    if (showWatched && userData != null) {"""
    user_data_nova = """                    if (!mShowOverlays) {
                        showWatched = false;
                        showProgress = false;
                    }
                    UserItemDataDto userData = itemDto.getUserData();
                    if (showWatched && userData != null) {"""
    if user_data_anchor not in cpj:
        raise RuntimeError("Could not find v0.19.10 CardPresenter user-data overlay hook")
    cpj = cpj.replace(user_data_anchor, user_data_nova, 1)

    # Search should keep the poster image but omit title/footer metadata, rating
    # splats/stars, favourite marks and poster info overlays.
    overlay_anchor = """        if (ImageType.POSTER.equals(mImageType)) {
            holder.mCardView.setOverlayInfo(rowItem);
        }
        holder.mCardView.showFavIcon(rowItem.isFavorite());"""
    overlay_nova = """        holder.mCardView.clearOverlayInfo();
        if (mShowInfo && mShowOverlays && ImageType.POSTER.equals(mImageType)) {
            holder.mCardView.setOverlayInfo(rowItem);
        }
        holder.mCardView.showFavIcon(mShowOverlays && rowItem.isFavorite());"""
    if overlay_anchor not in cpj:
        raise RuntimeError("Could not find v0.19.10 CardPresenter poster overlay hook")
    cpj = cpj.replace(overlay_anchor, overlay_nova, 1)

    rating_anchor = """            if (rowItem.getBaseItem() != null && rowItem.getBaseItem().getType() != BaseItemKind.USER_VIEW) {"""
    rating_nova = """            if (mShowOverlays && rowItem.getBaseItem() != null && rowItem.getBaseItem().getType() != BaseItemKind.USER_VIEW) {"""
    if rating_anchor not in cpj:
        raise RuntimeError("Could not find v0.19.10 CardPresenter rating hook")
    cpj = cpj.replace(rating_anchor, rating_nova, 1)

    # Disable Android/Leanback's default white focus highlight. The card view
    # itself draws the NOVA red frame so there is only one focus treatment.
    focus_anchor = """        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
"""
    focus_nova = """        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            cardView.setDefaultFocusHighlightEnabled(false);
        }
"""
    if focus_anchor not in cpj:
        raise RuntimeError("Could not find v0.19.10 CardPresenter focus hook")
    cpj = cpj.replace(focus_anchor, focus_nova, 1)

    reset_anchor = """            mCardView.clearBanner();"""
    if cpj.count(reset_anchor) != 1:
        raise RuntimeError("Could not find CardPresenter recycled-card reset")
    cpj = cpj.replace(reset_anchor, """            mCardView.clearOverlayInfo();
            mCardView.clearBanner();""", 1)

    legacy_card_presenter.write_text(cpj, encoding="utf-8")


# v0.19.10 media cards are LegacyImageCardView instances. Leanback can replace
# a normal OnFocusChangeListener after the presenter creates the card, so draw
# the NOVA focus frame inside the view's own onFocusChanged() callback instead.
legacy_image_card = root / "app/src/main/java/org/jellyfin/androidtv/ui/card/LegacyImageCardView.java"
card_text = legacy_image_card.read_text(encoding="utf-8")
clear_overlay_anchor = """    public void setOverlayInfo(BaseRowItem item) {
        if (binding.overlayText == null) return;"""
clear_overlay_nova = """    public void clearOverlayInfo() {
        binding.nameOverlay.setVisibility(GONE);
        binding.overlayText.setText(null);
        binding.overlayCount.setText(null);
        binding.icon.setVisibility(GONE);
        binding.iconImage.setImageDrawable(null);
    }

    public void setOverlayInfo(BaseRowItem item) {
        clearOverlayInfo();
        if (binding.overlayText == null) return;"""
if card_text.count(clear_overlay_anchor) != 1:
    raise RuntimeError("Could not find LegacyImageCardView name-overlay hook")
card_text = card_text.replace(clear_overlay_anchor, clear_overlay_nova, 1)
legacy_image_card.write_text(card_text, encoding="utf-8")

if legacy_image_card.exists():
    lic = legacy_image_card.read_text(encoding="utf-8")

    constructor_focus_anchor = """        setForeground(null);
    }
"""
    constructor_focus_nova = """        setForeground(null);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            setDefaultFocusHighlightEnabled(false);
        }
    }
"""
    if constructor_focus_anchor not in lic:
        raise RuntimeError("Could not find v0.19.10 LegacyImageCardView constructor focus hook")
    lic = lic.replace(constructor_focus_anchor, constructor_focus_nova, 1)

    focus_method_anchor = """    public void setBanner(int bannerResource) {"""
    focus_method_nova = """    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);

        if (gainFocus) {
            android.graphics.drawable.GradientDrawable focusFrame =
                    new android.graphics.drawable.GradientDrawable();
            focusFrame.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            focusFrame.setColor(android.graphics.Color.TRANSPARENT);
            final float density = getResources().getDisplayMetrics().density;
            int stroke = Math.max(2, Math.round(3f * density));
            focusFrame.setStroke(stroke, android.graphics.Color.rgb(229, 9, 20));

            // Match the foreground frame to the exact radius used by the poster.
            // This prevents poster-coloured corners showing outside the red frame.
            android.util.TypedValue rounding = new android.util.TypedValue();
            float cornerRadius = 6f * density;
            if (getContext().getTheme().resolveAttribute(
                    org.jellyfin.androidtv.R.attr.cardRounding,
                    rounding,
                    true
            )) {
                if (rounding.type == android.util.TypedValue.TYPE_DIMENSION) {
                    cornerRadius = android.util.TypedValue.complexToDimension(
                            rounding.data,
                            getResources().getDisplayMetrics()
                    );
                } else if (rounding.resourceId != 0) {
                    cornerRadius = getResources().getDimension(rounding.resourceId);
                }
            }
            focusFrame.setCornerRadius(cornerRadius);
            setForeground(focusFrame);
        } else {
            setForeground(null);
        }

        invalidate();
    }

    public void setBanner(int bannerResource) {"""
    if focus_method_anchor not in lic:
        raise RuntimeError("Could not find v0.19.10 LegacyImageCardView method anchor")
    if "protected void onFocusChanged(boolean gainFocus" not in lic:
        lic = lic.replace(focus_method_anchor, focus_method_nova, 1)

    legacy_image_card.write_text(lic, encoding="utf-8")


card_presenter = root / "app/src/main/java/org/jellyfin/androidtv/ui/presentation/CardPresenter.kt"
if card_presenter.exists():
    cp = card_presenter.read_text(encoding="utf-8")

    # v0.19.10 keeps the full CardPresenter constructor private. NOVA Search
    # needs one extra flag, so expose the primary constructor and add the flag
    # using a tail match that works across the supported source variants.
    cp = cp.replace("class CardPresenter private constructor(", "class CardPresenter(", 1)
    presenter_tail = """	val uniformAspect: Boolean,
) : Presenter() {"""
    presenter_tail_nova = """	val uniformAspect: Boolean,
	val showOverlays: Boolean = true,
) : Presenter() {"""
    if presenter_tail in cp and "val showOverlays: Boolean = true" not in cp:
        cp = cp.replace(presenter_tail, presenter_tail_nova, 1)

    # Search can request pure poster cards with no title/subtitle/rating/provider
    # overlays, while every other existing CardPresenter call keeps the upstream
    # behavior through the default showOverlays=True parameter.
    presenter_constructor = """class CardPresenter(
	val showInfo: Boolean,
	val imageType: ImageType,
	val staticHeight: Int,
	val uniformAspect: Boolean,
) : Presenter() {"""
    presenter_constructor_nova = """class CardPresenter(
	val showInfo: Boolean,
	val imageType: ImageType,
	val staticHeight: Int,
	val uniformAspect: Boolean,
	val showOverlays: Boolean = true,
) : Presenter() {"""
    if presenter_constructor in cp:
        cp = cp.replace(presenter_constructor, presenter_constructor_nova, 1)

    content_call_anchor = """					uniformAspect = uniformAspect,
				)"""
    content_call_nova = """					uniformAspect = uniformAspect,
					showOverlays = showOverlays,
				)"""
    if content_call_anchor in cp and "showOverlays = showOverlays" not in cp:
        cp = cp.replace(content_call_anchor, content_call_nova, 1)

    content_signature = """	staticHeight: Int,
	uniformAspect: Boolean,
) {"""
    content_signature_nova = """	staticHeight: Int,
	uniformAspect: Boolean,
	showOverlays: Boolean,
) {"""
    if content_signature in cp:
        cp = cp.replace(content_signature, content_signature_nova, 1)

    preview_anchor = """	val usePreview = displayConfig.overrideShowInfo ?: showInfo"""
    preview_nova = """	val usePreview = if (showOverlays) (displayConfig.overrideShowInfo ?: showInfo) else false"""
    if preview_anchor in cp:
        cp = cp.replace(preview_anchor, preview_nova, 1)

    overlay_anchor = """			overlay = {
				val showInfo = !usePreview && item.showCardInfoOverlay"""
    overlay_nova = """			overlay = {
				if (!showOverlays) return@ItemCard
				val showInfo = !usePreview && item.showCardInfoOverlay"""
    if overlay_anchor in cp:
        cp = cp.replace(overlay_anchor, overlay_nova, 1)

    if "import android.os.Build" not in cp:
        cp = cp.replace(
            "import android.view.KeyEvent\n",
            "import android.os.Build\nimport android.view.KeyEvent\n",
            1
        )

    if "import androidx.compose.foundation.border" not in cp:
        # CardPresenter versions differ slightly between Jellyfin releases;
        # insert beside another foundation import when available.
        if "import androidx.compose.foundation.Image\n" in cp:
            cp = cp.replace(
                "import androidx.compose.foundation.Image\n",
                "import androidx.compose.foundation.Image\nimport androidx.compose.foundation.border\n",
                1
            )
        elif "import androidx.compose.foundation.background\n" in cp:
            cp = cp.replace(
                "import androidx.compose.foundation.background\n",
                "import androidx.compose.foundation.background\nimport androidx.compose.foundation.border\n",
                1
            )

    focusable_anchor = """\t\t\tisFocusable = true
\t\t\tisFocusableInTouchMode = true"""
    focusable_replacement = """\t\t\tisFocusable = true
\t\t\tisFocusableInTouchMode = true
\t\t\tif (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
\t\t\t\tdefaultFocusHighlightEnabled = false
\t\t\t}"""
    if focusable_anchor in cp and "defaultFocusHighlightEnabled = false" not in cp:
        cp = cp.replace(focusable_anchor, focusable_replacement, 1)

    # Draw a strong red outline around whichever poster/card currently owns
    # focus. This is the visible Fire TV "cursor" while moving around.
    card_modifier_anchor = """\t\t\tmodifier = Modifier
\t\t\t\t.size(size)"""
    card_modifier_replacement = """\t\t\tmodifier = Modifier
\t\t\t\t.size(size)
\t\t\t\t.border(
\t\t\t\t\twidth = if (focused) 4.dp else 0.dp,
\t\t\t\t\tcolor = JellyfinTheme.colorScheme.buttonFocused,
\t\t\t\t\tshape = JellyfinTheme.shapes.medium,
\t\t\t\t)"""
    if card_modifier_anchor in cp and "width = if (focused) 4.dp else 0.dp" not in cp:
        cp = cp.replace(card_modifier_anchor, card_modifier_replacement, 1)

    focus_listener_anchor = """			_focused.value = view.isFocused
			composeView.onFocusChangeListener = { _, focused -> _focused.value = focused }"""
    focus_listener_nova = """			_focused.value = view.isFocused
			composeView.onFocusChangeListener = { focusedView, focused ->
				_focused.value = focused
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
					focusedView.foreground = if (focused) {
						android.graphics.drawable.GradientDrawable().apply {
							setColor(android.graphics.Color.TRANSPARENT)
							setStroke(
								(3f * focusedView.resources.displayMetrics.density).toInt().coerceAtLeast(2),
								android.graphics.Color.rgb(229, 9, 20),
							)
							val rounding = android.util.TypedValue()
							cornerRadius = if (focusedView.context.theme.resolveAttribute(
								org.jellyfin.androidtv.R.attr.cardRounding,
								rounding,
								true,
							)) {
								when {
									rounding.type == android.util.TypedValue.TYPE_DIMENSION ->
										android.util.TypedValue.complexToDimension(
											rounding.data,
											focusedView.resources.displayMetrics,
										)
									rounding.resourceId != 0 ->
										focusedView.resources.getDimension(rounding.resourceId)
									else -> 6f * focusedView.resources.displayMetrics.density
								}
							} else {
								6f * focusedView.resources.displayMetrics.density
							}
						}
					} else {
						null
					}
				}
			}"""
    if focus_listener_anchor in cp:
        cp = cp.replace(focus_listener_anchor, focus_listener_nova, 1)

    card_presenter.write_text(cp, encoding="utf-8")


# ---------------------------------------------------------------------------
# NOVA preferred audio language
# ---------------------------------------------------------------------------
# Prefer English audio for both normal playback and hover previews. If a title
# has no English track, Jellyfin's normal/default audio selection is preserved.
playback_controller = root / "app/src/main/java/org/jellyfin/androidtv/ui/playback/PlaybackController.java"
if playback_controller.exists():
    pc = playback_controller.read_text(encoding="utf-8")

    # Each new movie/episode should start by preferring English rather than
    # inheriting a dub language chosen by track order.
    old_forced_audio = 'String forcedAudioLanguage = videoQueueManager.getValue().getLastPlayedAudioLanguageIsoCode();'
    if old_forced_audio not in pc:
        raise RuntimeError("Could not find PlaybackController forced audio language")
    pc = pc.replace(old_forced_audio, 'String forcedAudioLanguage = "eng";', 1)

    # Find the best English audio stream, preferring the normal dialogue track
    # over commentary/audio-description tracks where labels allow us to tell.
    best_guess_anchor = """    private Integer bestGuessAudioTrack(MediaSourceInfo info) {"""
    english_helper = """    private boolean isEnglishAudioTrack(MediaStream track) {
        if (track == null || track.getType() != MediaStreamType.AUDIO)
            return false;

        String language = track.getLanguage();
        if (language != null) {
            String normalized = language.trim().toLowerCase(java.util.Locale.ROOT);
            if (normalized.equals("eng")
                    || normalized.equals("en")
                    || normalized.startsWith("en-")
                    || normalized.equals("english")) {
                return true;
            }
        }

        String title = track.getTitle();
        String displayTitle = track.getDisplayTitle();
        String label = ((title == null ? "" : title) + " " + (displayTitle == null ? "" : displayTitle))
                .toLowerCase(java.util.Locale.ROOT);
        return label.contains("english");
    }

    private boolean isCommentaryOrDescriptionTrack(MediaStream track) {
        String title = track.getTitle();
        String displayTitle = track.getDisplayTitle();
        String label = ((title == null ? "" : title) + " " + (displayTitle == null ? "" : displayTitle))
                .toLowerCase(java.util.Locale.ROOT);
        return label.contains("commentary")
                || label.contains("audio description")
                || label.contains("descriptive")
                || label.contains("director commentary")
                || label.contains("visually impaired");
    }

    private Integer preferredEnglishAudioTrack(MediaSourceInfo info) {
        if (info == null || info.getMediaStreams() == null)
            return null;

        Integer fallbackEnglish = null;
        for (MediaStream track : info.getMediaStreams()) {
            if (!isEnglishAudioTrack(track))
                continue;

            if (fallbackEnglish == null)
                fallbackEnglish = track.getIndex();

            if (!isCommentaryOrDescriptionTrack(track))
                return track.getIndex();
        }

        return fallbackEnglish;
    }

    private Integer bestGuessAudioTrack(MediaSourceInfo info) {"""
    if best_guess_anchor not in pc:
        raise RuntimeError("Could not find PlaybackController audio helper anchor")
    if "private Integer preferredEnglishAudioTrack" not in pc:
        pc = pc.replace(best_guess_anchor, english_helper, 1)

    # Tell Jellyfin which audio stream to use before it decides whether to
    # direct-play, remux or transcode.
    source_anchor = """        MediaSourceInfo currentMediaSource = getCurrentMediaSource();
        if (forcedAudioLanguage != null) {"""
    source_nova = """        MediaSourceInfo currentMediaSource = getCurrentMediaSource();
        if (internalOptions.getAudioStreamIndex() == null) {
            Integer preferredEnglishAudio = preferredEnglishAudioTrack(currentMediaSource);
            if (preferredEnglishAudio != null) {
                internalOptions.setAudioStreamIndex(preferredEnglishAudio);
            }
        }
        if (forcedAudioLanguage != null) {"""
    if source_anchor not in pc:
        raise RuntimeError("Could not find PlaybackController media source audio hook")
    pc = pc.replace(source_anchor, source_nova, 1)

    # Also make the post-playback-info default choose English before the server
    # default. Explicit in-player track changes still remain respected.
    default_audio_anchor = """        Integer lastChosenLanguage = lastChosenLanguageAudioTrack(info.getMediaSource());
        Integer remoteDefault = info.getMediaSource().getDefaultAudioStreamIndex();
        Integer bestGuess = bestGuessAudioTrack(info.getMediaSource());

        if (lastChosenLanguage != null)
            mDefaultAudioIndex = lastChosenLanguage;
        else if (remoteDefault != null)"""
    default_audio_nova = """        Integer preferredEnglish = preferredEnglishAudioTrack(info.getMediaSource());
        Integer lastChosenLanguage = lastChosenLanguageAudioTrack(info.getMediaSource());
        Integer remoteDefault = info.getMediaSource().getDefaultAudioStreamIndex();
        Integer bestGuess = bestGuessAudioTrack(info.getMediaSource());

        if (preferredEnglish != null)
            mDefaultAudioIndex = preferredEnglish;
        else if (lastChosenLanguage != null)
            mDefaultAudioIndex = lastChosenLanguage;
        else if (remoteDefault != null)"""
    if default_audio_anchor not in pc:
        raise RuntimeError("Could not find PlaybackController default audio selection")
    pc = pc.replace(default_audio_anchor, default_audio_nova, 1)

    playback_controller.write_text(pc, encoding="utf-8")


# Legacy/full-screen ExoPlayer should select an English muxed track before
# audible playback starts when direct-playing a file.
video_manager = root / "app/src/main/java/org/jellyfin/androidtv/ui/playback/VideoManager.java"
if video_manager.exists():
    vm = video_manager.read_text(encoding="utf-8")
    vm_audio_anchor = """.setAudioOffloadPreferences(new TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                        .build()
                )
                .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)"""
    vm_audio_nova = """.setAudioOffloadPreferences(new TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                        .build()
                )
                .setPreferredAudioLanguages("eng", "en", "en-GB", "en-US")
                .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)"""
    if vm_audio_anchor not in vm:
        raise RuntimeError("Could not find VideoManager track selector audio hook")
    vm = vm.replace(vm_audio_anchor, vm_audio_nova, 1)
    video_manager.write_text(vm, encoding="utf-8")


# Hover preview: request the English stream from Jellyfin so remux/transcode
# previews also use English, not merely direct-play files.
stream_resolver = root / "playback/jellyfin/src/main/kotlin/mediastream/JellyfinMediaStreamResolver.kt"
if stream_resolver.exists():
    sr = stream_resolver.read_text(encoding="utf-8")

    if "import org.jellyfin.sdk.model.api.MediaStream\n" not in sr:
        sr = sr.replace(
            "import org.jellyfin.sdk.model.api.MediaProtocol\n",
            "import org.jellyfin.sdk.model.api.MediaProtocol\nimport org.jellyfin.sdk.model.api.MediaStream\nimport org.jellyfin.sdk.model.api.MediaStreamType\n",
            1,
        )

    resolver_method_anchor = """	private suspend fun getPlaybackInfo(
		item: BaseItemDto,
		mediaSourceId: String? = null,
	): MediaInfo {"""
    resolver_helper = """	private fun MediaStream.isNovaEnglishAudio(): Boolean {
		if (type != MediaStreamType.AUDIO) return false
		val languageCode = language?.trim()?.lowercase().orEmpty()
		if (languageCode == "eng" || languageCode == "en" || languageCode.startsWith("en-") || languageCode == "english") {
			return true
		}
		val label = listOfNotNull(title, displayTitle).joinToString(" ").lowercase()
		return "english" in label
	}

	private fun MediaStream.isNovaCommentaryAudio(): Boolean {
		val label = listOfNotNull(title, displayTitle).joinToString(" ").lowercase()
		return "commentary" in label ||
			"audio description" in label ||
			"descriptive" in label ||
			"visually impaired" in label
	}

	private fun preferredEnglishAudioStreamIndex(
		item: BaseItemDto,
		mediaSourceId: String?,
	): Int? {
		val source = item.mediaSources.orEmpty()
			.firstOrNull { mediaSourceId == null || it.id == mediaSourceId }
			?: return null
		val english = source.mediaStreams.orEmpty().filter { it.isNovaEnglishAudio() }
		return english.firstOrNull { !it.isNovaCommentaryAudio() }?.index
			?: english.firstOrNull()?.index
	}

	private suspend fun getPlaybackInfo(
		item: BaseItemDto,
		mediaSourceId: String? = null,
	): MediaInfo {"""
    if resolver_method_anchor not in sr:
        raise RuntimeError("Could not find Jellyfin preview resolver hook")
    if "preferredEnglishAudioStreamIndex" not in sr:
        sr = sr.replace(resolver_method_anchor, resolver_helper, 1)

    playback_dto_anchor = """			data = PlaybackInfoDto(
				mediaSourceId = mediaSourceId,
				deviceProfile = profile,"""
    playback_dto_nova = """			data = PlaybackInfoDto(
				mediaSourceId = mediaSourceId,
				audioStreamIndex = preferredEnglishAudioStreamIndex(item, mediaSourceId),
				deviceProfile = profile,"""
    if playback_dto_anchor not in sr:
        raise RuntimeError("Could not find preview PlaybackInfoDto hook")
    sr = sr.replace(playback_dto_anchor, playback_dto_nova, 1)

    stream_resolver.write_text(sr, encoding="utf-8")


# Hover preview direct-play uses the rewrite ExoPlayer backend, so set the same
# language preference at track-selection time too.
exo_backend = root / "playback/media3/exoplayer/src/main/kotlin/ExoPlayerBackend.kt"
if exo_backend.exists():
    eb = exo_backend.read_text(encoding="utf-8")
    exo_audio_anchor = """					setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)"""
    exo_audio_nova = """					setPreferredAudioLanguages("eng", "en", "en-GB", "en-US")
					setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)"""
    if exo_audio_anchor not in eb:
        raise RuntimeError("Could not find preview ExoPlayer audio preference hook")
    eb = eb.replace(exo_audio_anchor, exo_audio_nova, 1)
    exo_backend.write_text(eb, encoding="utf-8")


# Disable verbose Timber logging in production builds. Debug builds keep the
# original diagnostics for development, while NOVA v1 release avoids log spam.
log_initializer = root / "app/src/main/java/org/jellyfin/androidtv/LogInitializer.kt"
if log_initializer.exists():
    li = log_initializer.read_text(encoding="utf-8")
    old_log_block = """		// Initialize the logging library
		Timber.plant(Timber.DebugTree())
		Timber.i("Debug tree planted")"""
    new_log_block = """		// Keep verbose logcat output for development only.
		if (BuildConfig.DEBUG) {
			Timber.plant(Timber.DebugTree())
			Timber.i("Debug tree planted")
		}"""
    if old_log_block in li:
        li = li.replace(old_log_block, new_log_block, 1)
    log_initializer.write_text(li, encoding="utf-8")



# ---------------------------------------------------------------------------

# Galaxy-N branding for launcher, round icon, search integration and splash.
manifest = root / "app/src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
m = m.replace('android:icon="@mipmap/app_icon"', 'android:icon="@drawable/nova_galaxy_icon"')
manifest.write_text(m, encoding="utf-8")

searchable = root / "app/src/main/res/xml/searchable.xml"
if searchable.exists():
    q = searchable.read_text(encoding="utf-8")
    q = q.replace('android:icon="@mipmap/app_icon"', 'android:icon="@drawable/nova_galaxy_icon"')
    searchable.write_text(q, encoding="utf-8")

splash_file = root / "app/src/main/java/org/jellyfin/androidtv/ui/startup/fragment/SplashFragment.kt"
if splash_file.exists():
    q = splash_file.read_text(encoding="utf-8")
    q = q.replace("R.drawable.nova_mark", "R.drawable.nova_galaxy_icon")
    splash_file.write_text(q, encoding="utf-8")




# ---------------------------------------------------------------------------
# NOVA rebuilt full-screen setup flow
# ---------------------------------------------------------------------------
# The earlier concept PNG is now treated as design reference only. The setup
# pages themselves are real Android controls/layouts so there is no stock
# Jellyfin dialog sitting over baked UI artwork.

(drawable_dir / "nova_setup_page_background.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient
        android:angle="0"
        android:startColor="#050507"
        android:centerColor="#0A0A0F"
        android:endColor="#16070B" />
</shape>
""", encoding="utf-8")

(drawable_dir / "nova_setup_panel_strong.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#E615151B" />
    <corners android:radius="18dp" />
    <stroke android:width="1dp" android:color="#554A4A55" />
    <padding android:left="26dp" android:top="24dp" android:right="26dp" android:bottom="24dp" />
</shape>
""", encoding="utf-8")

(drawable_dir / "nova_setup_field.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_focused="true">
        <shape android:shape="rectangle">
            <solid android:color="#FF252532" />
            <corners android:radius="12dp" />
            <stroke android:width="3dp" android:color="#FFFFFFFF" />
            <padding android:left="18dp" android:top="11dp" android:right="18dp" android:bottom="11dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#F0181820" />
            <corners android:radius="12dp" />
            <stroke android:width="1dp" android:color="#FF52525F" />
            <padding android:left="18dp" android:top="11dp" android:right="18dp" android:bottom="11dp" />
        </shape>
    </item>
</selector>
""", encoding="utf-8")


(drawable_dir / "nova_setup_action_red.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_enabled="false">
        <shape android:shape="rectangle">
            <solid android:color="#55380A0D" />
            <corners android:radius="12dp" />
        </shape>
    </item>
    <item android:state_focused="true">
        <shape android:shape="rectangle">
            <solid android:color="#FFFF1621" />
            <corners android:radius="12dp" />
            <stroke android:width="3dp" android:color="#FFFFFFFF" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <gradient android:angle="0" android:startColor="#FFB20710" android:endColor="#FFE50914" />
            <corners android:radius="12dp" />
            <stroke android:width="1dp" android:color="#FFFF5B64" />
        </shape>
    </item>
</selector>
""", encoding="utf-8")

# MAIN SERVER SELECTION -------------------------------------------------------

# MANUAL SERVER ADDRESS -------------------------------------------------------
(root / "app/src/main/res/layout/fragment_server_add.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/nova_setup_page_background">

    <ImageView
        android:layout_width="500dp"
        android:layout_height="500dp"
        android:layout_gravity="end|center_vertical"
        android:layout_marginEnd="-70dp"
        android:alpha="0.42"
        android:contentDescription="@null"
        android:src="@drawable/nova_galaxy_icon" />

    <LinearLayout
        android:layout_width="680dp"
        android:layout_height="wrap_content"
        android:layout_gravity="start|center_vertical"
        android:layout_marginStart="72dp"
        android:background="@drawable/nova_setup_panel_strong"
        android:orientation="vertical">

        <TextView
            android:id="@+id/title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:fontFamily="sans-serif-medium"
            android:text="Connect your media server"
            android:textColor="#FFFFFFFF"
            android:textSize="38sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/address_label"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:fontFamily="sans-serif"
            android:text="Enter the Jellyfin server address, including http:// or https://"
            android:textColor="#FFB6B6BE"
            android:textSize="15sp" />

        <EditText
            android:id="@+id/address"
            android:layout_width="match_parent"
            android:layout_height="64dp"
            android:layout_marginTop="22dp"
            android:background="@drawable/nova_setup_field"
            android:fontFamily="sans-serif"
            android:hint="http://192.168.1.100:8096"
            android:imeOptions="actionDone"
            android:inputType="textUri"
            android:nextFocusDown="@id/confirm"
            android:nextFocusForward="@id/confirm"
            android:singleLine="true"
            android:textColor="#FFFFFFFF"
            android:textColorHint="#FF80808A"
            android:textSize="17sp" />

        <Button
            android:id="@+id/confirm"
            android:layout_width="260dp"
            android:layout_height="60dp"
            android:layout_marginTop="20dp"
            android:background="@drawable/nova_setup_action_red"
            android:fontFamily="sans-serif-medium"
            android:text="Connect"
            android:textAllCaps="false"
            android:textColor="#FFFFFFFF"
            android:textSize="18sp" />

        <TextView
            android:id="@+id/error"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="14dp"
            android:fontFamily="sans-serif"
            android:textColor="#FFFF747C"
            android:textSize="13sp"
            tools:text="Connecting..." />
    </LinearLayout>
</FrameLayout>
""", encoding="utf-8")

# WHO'S WATCHING --------------------------------------------------------------

# Remove the legacy startup toolbar from first-run pages so setup truly owns
# the whole screen.
startup_activity = root / "app/src/main/java/org/jellyfin/androidtv/ui/startup/StartupActivity.kt"
if startup_activity.exists():
    q = startup_activity.read_text(encoding="utf-8")
    q = q.replace(
        """\tprivate fun showServer(id: UUID) = supportFragmentManager.commit {
\t\treplace<StartupToolbarFragment>(R.id.content_view)
\t\tadd<ServerFragment>(
\t\t\tR.id.content_view, null, bundleOf(
\t\t\t\tServerFragment.ARG_SERVER_ID to id.toString()
\t\t\t)
\t\t)
\t}

\tprivate fun showServerSelection() = supportFragmentManager.commit {
\t\treplace<StartupToolbarFragment>(R.id.content_view)
\t\tadd<SelectServerFragment>(R.id.content_view)
\t}
""",
        """\tprivate fun showServer(id: UUID) = supportFragmentManager.commit {
\t\treplace<ServerFragment>(
\t\t\tR.id.content_view, null, bundleOf(
\t\t\t\tServerFragment.ARG_SERVER_ID to id.toString()
\t\t\t)
\t\t)
\t}

\tprivate fun showServerSelection() = supportFragmentManager.commit {
\t\treplace<SelectServerFragment>(R.id.content_view)
\t}
"""
    )
    startup_activity.write_text(q, encoding="utf-8")

# Remove toolbar insertion when moving from server discovery to a server page.

# All setup navigation is now full-screen; keepToolbar remains in the function
# signature for compatibility but no longer inserts the old Jellyfin toolbar.
server_fragment = root / "app/src/main/java/org/jellyfin/androidtv/ui/startup/fragment/ServerFragment.kt"
if server_fragment.exists():
    q = server_fragment.read_text(encoding="utf-8")
    old_nav = """\t\trequireActivity()
\t\t\t.supportFragmentManager
\t\t\t.commit {
\t\t\t\tif (keepToolbar) {
\t\t\t\t\treplace<StartupToolbarFragment>(R.id.content_view)
\t\t\t\t\tadd<F>(R.id.content_view, null, args)
\t\t\t\t} else {
\t\t\t\t\treplace<F>(R.id.content_view, null, args)
\t\t\t\t}

\t\t\t\tif (keepHistory) addToBackStack(null)
\t\t\t}
"""
    new_nav = """\t\trequireActivity()
\t\t\t.supportFragmentManager
\t\t\t.commit {
\t\t\t\treplace<F>(R.id.content_view, null, args)
\t\t\t\tif (keepHistory) addToBackStack(null)
\t\t\t}
"""
    if old_nav in q:
        q = q.replace(old_nav, new_nav, 1)
    server_fragment.write_text(q, encoding="utf-8")



# ---------------------------------------------------------------------------
# NOVA main setup screen — true from-scratch rebuild
# ---------------------------------------------------------------------------
# This final override deliberately does not use the generated setup mockup as a
# background. It recreates the screen using real Android controls, with the
# uploaded galaxy-N used only as branding/decorative artwork.



# ---------------------------------------------------------------------------
# NOVA setup polish pass — fix profile/login visual overlap
# ---------------------------------------------------------------------------

# WHO'S WATCHING: no giant cropped logo behind the title; use a clean branded
# header, centered profile area, and properly sized action row.


# ---------------------------------------------------------------------------
# NOVA connect screen — code the concept as real UI, not a screenshot background
# ---------------------------------------------------------------------------

# Purpose-built vectors for the three live controls.
(drawable_dir / "nova_ic_server.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="28dp" android:height="28dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M4,3h16a2,2 0,0 1,2 2v3a2,2 0,0 1,-2 2H4A2,2 0,0 1,2 8V5a2,2 0,0 1,2 -2M5,5v3h14V5H5m-1,7h16a2,2 0,0 1,2 2v3a2,2 0,0 1,-2 2H4a2,2 0,0 1,-2 -2v-3a2,2 0,0 1,2 -2m1,2v3h14v-3H5M7,6.5a1,1 0,1 0,0.01 0M7,15.5a1,1 0,1 0,0.01 0"/>
</vector>
""", encoding="utf-8")


(drawable_dir / "nova_ic_gear.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="30dp" android:height="30dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M19.14,12.94a7.3,7.3 0,0 0,0.05 -0.94,7.3 7.3,0 0,0 -0.07,-1l2.03,-1.58 -2,-3.46 -2.47,1a7.1,7.1 0,0 0,-1.63 -0.95L14.7,3.36h-4L10.32,6a7,7 0,0 0,-1.65 0.96l-2.45,-1 -2,3.46L6.27,11a7,7 0,0 0,-0.07 1,7 7,0 0,0 0.07,1l-2.05,1.58 2,3.46 2.45,-1a7,7 0,0 0,1.65 0.96l0.38,2.64h4l0.37,-2.64a7.1,7.1 0,0 0,1.63 -0.95l2.47,1 2,-3.46 -2.03,-1.65M12.7,15.5A3.5,3.5 0,1 1,12.7 8a3.5,3.5 0,0 1,0 7.5"/>
</vector>
""", encoding="utf-8")

(drawable_dir / "nova_ic_link.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="30dp" android:height="30dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M10.59,13.41a2,2 0,0 1,0 -2.82l2.83,-2.83a2,2 0,0 1,2.82 2.83l-1.41,1.41 1.41,1.41 1.42,-1.41a4,4 0,0 0,-5.66 -5.66l-2.83,2.83a4,4 0,0 0,0 5.66l1.42,-1.42m2.82,-2.82 -1.42,1.42a2,2 0,0 1,0 2.82l-2.83,2.83a2,2 0,1 1,-2.82 -2.83L8.3,12.4 6.88,11l-1.41,1.41a4,4 0,1 0,5.66 5.66l2.83,-2.83a4,4 0,0 0,0 -5.66l-1.42,1.01z"/>
</vector>
""", encoding="utf-8")

(drawable_dir / "nova_concept_input.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_focused="true">
        <shape android:shape="rectangle">
            <solid android:color="#E418181D" />
            <corners android:radius="13dp" />
            <stroke android:width="3dp" android:color="#FFFF4E5B" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#E418181D" />
            <corners android:radius="13dp" />
            <stroke android:width="2dp" android:color="#FFE44752" />
        </shape>
    </item>
</selector>
""", encoding="utf-8")

(drawable_dir / "nova_concept_card.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_focused="true">
        <shape android:shape="rectangle">
            <solid android:color="#F126262D" />
            <corners android:radius="13dp" />
            <stroke android:width="3dp" android:color="#FFFFFFFF" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#E818181D" />
            <corners android:radius="13dp" />
            <stroke android:width="1dp" android:color="#FF4B4B53" />
        </shape>
    </item>
</selector>
""", encoding="utf-8")

(drawable_dir / "nova_concept_connect.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_focused="true">
        <shape android:shape="rectangle">
            <gradient android:angle="0" android:startColor="#FFFF2433" android:endColor="#FFB80712" />
            <corners android:radius="13dp" />
            <stroke android:width="3dp" android:color="#FFFFFFFF" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <gradient android:angle="0" android:startColor="#FFE51A27" android:endColor="#FF9F0610" />
            <corners android:radius="13dp" />
            <stroke android:width="1dp" android:color="#FFFF4C58" />
        </shape>
    </item>
</selector>
""", encoding="utf-8")

# Real Android UI matching the supplied concept. The concept image itself is
# NOT displayed as a background.

# Matching real behavior for the three concept controls.
select_server = root / "app/src/main/java/org/jellyfin/androidtv/ui/startup/fragment/SelectServerFragment.kt"
select_server.write_text("""package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ConnectedState
import org.jellyfin.androidtv.auth.model.ConnectingState
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.UnableToConnectState
import org.jellyfin.androidtv.databinding.FragmentSelectServerBinding
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import org.jellyfin.androidtv.util.getSummary
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class SelectServerFragment : Fragment() {
    private var _binding: FragmentSelectServerBinding? = null
    private val binding get() = _binding!!
    private val startupViewModel: StartupViewModel by activityViewModel()

    private var storedServers: List<Server> = emptyList()
    private var discoveredServers: List<Server> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSelectServerBinding.inflate(inflater, container, false)

        binding.serverAddress.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                connectEnteredAddress(quickConnect = false)
                true
            } else false
        }

        binding.connect.setOnClickListener {
            connectEnteredAddress(quickConnect = false)
        }

        binding.quickConnect.setOnClickListener {
            connectEnteredAddress(quickConnect = true)
        }

        binding.manualSetup.setOnClickListener {
            parentFragmentManager.commit {
                replace<ServerAddFragment>(R.id.content_view)
                addToBackStack(null)
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                startupViewModel.storedServers.onEach { servers ->
                    storedServers = servers
                    suggestServerAddress()
                }.launchIn(this)

                startupViewModel.discoveredServers.onEach { servers ->
                    discoveredServers = servers
                    suggestServerAddress()
                }.launchIn(this)
            }
        }

        startupViewModel.reloadStoredServers()
        startupViewModel.loadDiscoveryServers()

        binding.serverAddress.requestFocus()
    }

    private fun suggestServerAddress() {
        if (binding.serverAddress.text?.isNotBlank() == true) return

        val address = storedServers.firstOrNull()?.address
            ?: discoveredServers.firstOrNull()?.address
            ?: return

        binding.serverAddress.setText(address)
        binding.serverAddress.setSelection(address.length)
    }

    private fun connectEnteredAddress(quickConnect: Boolean) {
        val address = binding.serverAddress.text?.toString()?.trim().orEmpty()
        if (address.isBlank()) {
            showStatus("Enter your server address first.")
            binding.serverAddress.requestFocus()
            return
        }

        binding.connect.isEnabled = false
        binding.quickConnect.isEnabled = false
        showStatus("Connecting…")

        startupViewModel.addServer(address)
            .onEach { state ->
                when (state) {
                    is ConnectingState -> showStatus("Connecting to __DOLLAR__{state.address}…")

                    is ConnectedState -> {
                        binding.connect.isEnabled = true
                        binding.quickConnect.isEnabled = true
                        binding.connectionStatus.isVisible = false

                        if (quickConnect) {
                            parentFragmentManager.commit {
                                replace<UserLoginFragment>(
                                    R.id.content_view,
                                    null,
                                    bundleOf(
                                        UserLoginFragment.ARG_SERVER_ID to state.id.toString(),
                                        UserLoginFragment.ARG_USERNAME to null,
                                        UserLoginFragment.ARG_SKIP_QUICKCONNECT to false,
                                    )
                                )
                                addToBackStack(null)
                            }
                        } else {
                            parentFragmentManager.commit {
                                replace<ServerFragment>(
                                    R.id.content_view,
                                    null,
                                    bundleOf(ServerFragment.ARG_SERVER_ID to state.id.toString())
                                )
                            }
                        }
                    }

                    is UnableToConnectState -> {
                        binding.connect.isEnabled = true
                        binding.quickConnect.isEnabled = true

                        val details = state.addressCandidates
                            .map { "__DOLLAR__{it.key} - __DOLLAR__{it.value.getSummary(requireContext())}" }
                            .joinToString(separator = "\\n")

                        showStatus(
                            if (details.isBlank()) "Could not connect to the server."
                            else "Could not connect.\\n__DOLLAR__details"
                        )
                    }
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun showStatus(message: String) {
        binding.connectionStatus.text = message
        binding.connectionStatus.isVisible = true
    }

    override fun onResume() {
        super.onResume()
        startupViewModel.reloadStoredServers()
        startupViewModel.loadDiscoveryServers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
""".replace("__DOLLAR__", "$"), encoding="utf-8")


print("Applied NOVA branding, Home UI, cinematic library pages, direct playback and global red focus cursor.")

# Consistent remote cadence across browsing, setup, preferences and IPTV.
for relative in [
    "ui/browsing/MainActivity.kt",
    "ui/startup/StartupActivity.kt",
    "ui/preference/PreferencesActivity.kt",
    "ui/playback/ExternalPlayerActivity.kt",
    "ui/iptv/NovaIptvActivity.kt",
]:
    activity_path = root / "app/src/main/java/org/jellyfin/androidtv" / relative
    activity_source = activity_path.read_text(encoding="utf-8")
    import re
    class_match = re.search(r"^class \w+[^\n]*\{", activity_source, re.MULTILINE)
    if not class_match:
        raise RuntimeError(f"Could not find activity declaration: {relative}")
    original_dispatch = "override fun dispatchKeyEvent(event: KeyEvent): Boolean"
    has_dispatch = original_dispatch in activity_source
    if has_dispatch:
        activity_source = activity_source.replace(original_dispatch, "private fun dispatchNovaKeyEvent(event: KeyEvent): Boolean", 1)
    methods = """
    private val novaDpad by lazy {
        org.jellyfin.androidtv.ui.navigation.NovaDpadDispatcher(this, ::dispatchNovaKeyEvent)
    }
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean = novaDpad.dispatch(event)
"""
    if not has_dispatch:
        methods += """    private fun dispatchNovaKeyEvent(event: android.view.KeyEvent): Boolean = super.dispatchKeyEvent(event)
"""
    activity_source = activity_source[:class_match.end()] + methods + activity_source[class_match.end():]
    activity_path.write_text(activity_source, encoding="utf-8")

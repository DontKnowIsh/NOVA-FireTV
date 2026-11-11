#!/usr/bin/env python3
"""Small NOVA fixes applied after the main customisation scripts."""

from pathlib import Path

root = Path("jellyfin-src/app/src/main")

# The screensaver's logo screen still used the old NOVA mark; use the current
# "N" logo that every other NOVA screen shows.
dream_logo = root / "java/org/jellyfin/androidtv/integration/dream/composable/DreamContentLogo.kt"
content = dream_logo.read_text(encoding="utf-8")
content = content.replace("R.drawable.nova_mark", "R.drawable.nova_n_mark")
if "R.drawable.nova_n_mark" not in content:
    raise RuntimeError("Could not point the screensaver logo at nova_n_mark")
dream_logo.write_text(content, encoding="utf-8")


# The screensaver should only show titles you really have, never the Discover
# (JellyBridge) placeholders for things that haven't been downloaded.
dream_vm = root / "java/org/jellyfin/androidtv/integration/dream/DreamViewModel.kt"
content = dream_vm.read_text(encoding="utf-8")
old_query = """						limit = batchSize,
						imageTypes = listOf(ImageType.BACKDROP),"""
new_query = """						// Ask for extra, as Discover placeholders are removed below.
						limit = batchSize * 4,
						fields = setOf(org.jellyfin.sdk.model.api.ItemFields.PATH),
						imageTypes = listOf(ImageType.BACKDROP),"""
old_items = """					)
					response.items
				}"""
new_items = """					)
					org.jellyfin.androidtv.ui.browsing.NovaSeerr.withoutDiscover(api, response.items).take(batchSize)
				}"""
for old, new in ((old_query, new_query), (old_items, new_items)):
    if content.count(old) != 1:
        raise RuntimeError(f"Screensaver hook missing in {dream_vm}: {old[:60]!r}")
    content = content.replace(old, new, 1)
dream_vm.write_text(content, encoding="utf-8")


# Themes: NOVA's base buttons, input boxes and poster focus frames follow the
# chosen theme (Settings → Theme) instead of always being NOVA red.
color_scheme = root / "java/org/jellyfin/androidtv/ui/base/colorScheme.kt"
content = color_scheme.read_text(encoding="utf-8")
if "Color(0xFFE50914)" not in content:
    raise RuntimeError("NOVA accent missing from colorScheme.kt")
content = content.replace("Color(0xFFE50914)", "org.jellyfin.androidtv.ui.NovaTheme.palette.accent")
for name in ("onButtonFocused", "onInputFocused"):
    content = content.replace(f"{name} = Color(0xFFFFFFFF)", f"{name} = org.jellyfin.androidtv.ui.NovaTheme.palette.onAccent")
color_scheme.write_text(content, encoding="utf-8")

jellyfin_theme = root / "java/org/jellyfin/androidtv/ui/base/JellyfinTheme.kt"
content = jellyfin_theme.read_text(encoding="utf-8")
old = "colorScheme: ColorScheme = JellyfinTheme.colorScheme,"
if content.count(old) != 1:
    raise RuntimeError("JellyfinTheme colorScheme parameter not found")
# Build the scheme fresh so a theme change applies without restarting the app.
content = content.replace(old, "colorScheme: ColorScheme = org.jellyfin.androidtv.ui.base.colorScheme(),")
jellyfin_theme.write_text(content, encoding="utf-8")

card_view = root / "java/org/jellyfin/androidtv/ui/card/LegacyImageCardView.java"
content = card_view.read_text(encoding="utf-8")
old = "focusFrame.setStroke(stroke, android.graphics.Color.rgb(229, 9, 20));"
if content.count(old) != 1:
    raise RuntimeError("Poster focus frame colour not found")
content = content.replace(old, "focusFrame.setStroke(stroke, org.jellyfin.androidtv.ui.NovaTheme.accentArgb());")
card_view.write_text(content, encoding="utf-8")

for theme_file, accent in (("theme_emerald.xml", "@color/theme_emerald_light"), ("theme_mutedpurple.xml", "@color/theme_muted_purple_accent")):
    path = root / "res/values" / theme_file
    content = path.read_text(encoding="utf-8")
    if "colorControlHighlight" not in content:
        content = content.replace(
            '<item name="android:colorAccent">',
            f'<item name="android:colorControlHighlight">{accent}</item>\n        <item name="android:colorAccent">',
            1,
        )
        path.write_text(content, encoding="utf-8")


# Settings screens: NOVA panel, theme-coloured highlights, re-styled live when the
# theme is changed.
prefs_activity = root / "java/org/jellyfin/androidtv/ui/preference/PreferencesActivity.kt"
content = prefs_activity.read_text(encoding="utf-8")
old = "\t\tval screenArgs = intent.extras?.getBundle(EXTRA_SCREEN_ARGS) ?: bundleOf()\n"
if content.count(old) != 1:
    raise RuntimeError("PreferencesActivity onCreate hook not found")
content = content.replace(old, old + """
\t\t// NOVA draws Settings itself (same settings, NOVA look); Jellyfin's own
\t\t// screens are only used as a fallback for the rare items NOVA can't show.
\t\tif (!intent.getBooleanExtra(org.jellyfin.androidtv.ui.NovaSettings.EXTRA_LEGACY, false)) {
\t\t\torg.jellyfin.androidtv.ui.NovaSettings.show(this, screen ?: return, screenArgs)
\t\t\treturn
\t\t}
\t\torg.jellyfin.androidtv.ui.NovaSettingsStyle.attach(this)
""", 1)
prefs_activity.write_text(content, encoding="utf-8")


# Home: "Recently added in Discover" goes to the top instead of the bottom, and
# "Random Movies" only picks from the Movies library (never Discover placeholders).
home_rows = root / "java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt"
content = home_rows.read_text(encoding="utf-8")
old = "\t\t\tmovies?.let { rows.add(helper.loadRecentlyAdded(listOf(it))) }\n"
new = """\t\t\tmovies?.let { moviesView ->
\t\t\t\trows[0] = HomeFragmentBrowseRowDefRow(
\t\t\t\t\tBrowseRowDef("Random Movies", randomMovies.copy(parentId = moviesView.id), 20)
\t\t\t\t)
\t\t\t}
\t\t\tval discover = takeView("Discover")
\t\t\t\t?: remainingViews.firstOrNull {
\t\t\t\t\tit.collectionType == null && !it.name.equals("Collections", ignoreCase = true)
\t\t\t\t}?.also { remainingViews.remove(it) }
\t\t\t// Discover is no longer shown on Home (new titles are on the Download page).
""" + old
if content.count(old) != 1:
    raise RuntimeError("Home rows hook for Discover not found")
content = content.replace(old, new, 1)
home_rows.write_text(content, encoding="utf-8")


# Home: clicking a Discover title opens its More Info page (with the Download
# button), like on the Discover page, instead of playing the placeholder video.
home_rows = root / "java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt"
content = home_rows.read_text(encoding="utf-8")
old = """			@Suppress("UNCHECKED_CAST")
			itemLauncher.launch(item, row.adapter as MutableObjectAdapter<Any>, requireContext())"""
new = """			@Suppress("UNCHECKED_CAST")
			val rowAdapter = row.adapter as MutableObjectAdapter<Any>
			val dto = item.baseItem
			val context = requireContext()
			if (dto == null) {
				itemLauncher.launch(item, rowAdapter, context)
				return
			}
			viewLifecycleOwner.lifecycleScope.launch {
				// Films and shows open More Info (Play / ♥ / Watched there); episodes
				// in Continue Watching / Next Up still play straight away.
				if (dto.type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE ||
					dto.type == org.jellyfin.sdk.model.api.BaseItemKind.SERIES ||
					org.jellyfin.androidtv.ui.browsing.NovaSeerr.isDiscoverItem(api, dto)
				) {
					org.koin.java.KoinJavaComponent.get<org.jellyfin.androidtv.ui.navigation.NavigationRepository>(
						org.jellyfin.androidtv.ui.navigation.NavigationRepository::class.java
					).navigate(org.jellyfin.androidtv.ui.navigation.Destinations.novaInfo(dto))
				} else {
					itemLauncher.launch(item, rowAdapter, context)
				}
			}"""
if content.count(old) != 1:
    raise RuntimeError("Home row click hook not found")
content = content.replace(old, new, 1)
if "import kotlinx.coroutines.launch\n" not in content:
    content = content.replace("import kotlinx.coroutines.delay\n", "import kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch\n", 1)
home_rows.write_text(content, encoding="utf-8")


# Delete PIN: a "Change delete PIN" option in Settings → Customization, and the
# small screen it opens.
manifest = root / "AndroidManifest.xml"
content = manifest.read_text(encoding="utf-8")
if "NovaPinActivity" not in content:
    anchor = '<activity android:name=".ui.playback.ExternalPlayerActivity" />'
    if content.count(anchor) != 1:
        raise RuntimeError("Manifest anchor for NovaPinActivity not found")
    content = content.replace(
        anchor,
        anchor + """

        <activity
            android:name=".ui.browsing.NovaPinActivity"
            android:exported="false"
            android:screenOrientation="landscape"
            android:theme="@style/Theme.Jellyfin" />""",
        1,
    )
    manifest.write_text(content, encoding="utf-8")

customization = root / "java/org/jellyfin/androidtv/ui/preference/screen/CustomizationPreferencesScreen.kt"
content = customization.read_text(encoding="utf-8")
old = """		category {
			setTitle(R.string.pref_browsing)"""
new = """		category {
			title = "NOVA"

			action {
				title = "Server storage"
				content = org.jellyfin.androidtv.ui.browsing.NovaStorage.describe(org.jellyfin.androidtv.ui.browsing.NovaStorage.last)
				novaStorageItem = this
				onActivate = { refreshNovaStorage() }
			}

			action {
				title = "Change delete PIN"
				content = "PIN needed to delete films and shows from the server (starts as 0000)"
				onActivate = {
					startActivity(android.content.Intent(requireContext(), org.jellyfin.androidtv.ui.browsing.NovaPinActivity::class.java))
				}
			}

			action {
				title = "Master code"
				content = "8-digit code that unlocks any PIN if you forget it"
				onActivate = {
					startActivity(
						android.content.Intent(requireContext(), org.jellyfin.androidtv.ui.browsing.NovaPinActivity::class.java)
							.putExtra("nova_pin_kind", "master")
					)
				}
			}

			checkbox {
				title = "Kids lock"
				contentOn = "PIN needed to play anything rated 15 / R or above"
				contentOff = "Off: everything plays without a PIN"
				bind {
					get { org.jellyfin.androidtv.ui.browsing.NovaKidsPin.isEnabled(requireContext()) }
					set { value -> org.jellyfin.androidtv.ui.browsing.NovaKidsPin.setEnabled(requireContext(), value) }
					default { true }
				}
			}

			action {
				title = "Change kids lock PIN"
				content = "PIN for the kids lock (starts as 0000)"
				onActivate = {
					startActivity(
						android.content.Intent(requireContext(), org.jellyfin.androidtv.ui.browsing.NovaPinActivity::class.java)
							.putExtra("nova_pin_kind", "kids")
					)
				}
			}

			checkbox {
				title = "Indian films"
				contentOn = "Hindi and Punjabi on Download; Indian films go to Radarr's Indian folder"
				contentOff = "Off: English only, and all films go to the usual movies folder"
				bind {
					get { org.jellyfin.androidtv.ui.browsing.NovaIndianFilms.isEnabled() }
					set { value -> org.jellyfin.androidtv.ui.browsing.NovaIndianFilms.setEnabled(requireContext(), value) }
					default { false }
				}
			}

			checkbox {
				title = "Navigation sounds"
				contentOn = "Fire TV click sounds as you move around NOVA"
				contentOff = "Off: no click sounds"
				bind {
					get { org.jellyfin.androidtv.ui.NovaClickSound.isEnabled(requireContext()) }
					set { value -> org.jellyfin.androidtv.ui.NovaClickSound.setEnabled(requireContext(), value) }
					default { true }
				}
			}
		}

""" + old
if content.count(old) != 1:
    raise RuntimeError("Customization settings anchor not found")
content = content.replace(old, new, 1)
old_field = "\tprivate val userPreferences: UserPreferences by inject()\n"
if content.count(old_field) != 1:
    raise RuntimeError("Customization settings field anchor not found")
content = content.replace(old_field, old_field + """
\t// "Server storage": free and total space on the NAS, refreshed when shown.
\tprivate var novaStorageItem: org.jellyfin.androidtv.ui.preference.dsl.OptionsAction? = null

\tprivate fun refreshNovaStorage() {
\t\tviewLifecycleOwner.lifecycleScope.launch {
\t\t\tval api = org.koin.java.KoinJavaComponent.get<org.jellyfin.sdk.api.client.ApiClient>(org.jellyfin.sdk.api.client.ApiClient::class.java)
\t\t\tval space = org.jellyfin.androidtv.ui.browsing.NovaStorage.load(api)
\t\t\tnovaStorageItem?.content = org.jellyfin.androidtv.ui.browsing.NovaStorage.describe(space)
\t\t\trebuild()
\t\t}
\t}

\toverride fun onResume() {
\t\tsuper.onResume()
\t\trefreshNovaStorage()
\t}
""", 1)
for imp in ("import androidx.lifecycle.lifecycleScope\n", "import kotlinx.coroutines.launch\n"):
    if imp not in content:
        content = content.replace("import org.jellyfin.androidtv.R\n", "import org.jellyfin.androidtv.R\n" + imp, 1)
if "import org.jellyfin.androidtv.ui.preference.dsl.action" not in content:
    content = content.replace(
        "import org.jellyfin.androidtv.ui.preference.dsl.checkbox\n",
        "import org.jellyfin.androidtv.ui.preference.dsl.action\nimport org.jellyfin.androidtv.ui.preference.dsl.checkbox\n",
        1,
    )
customization.write_text(content, encoding="utf-8")


# Home: expose the focused title so a long press of OK opens NOVA's own menu
# (More info, favourite, watched, Delete from server) instead of Jellyfin's.
home_rows = root / "java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt"
content = home_rows.read_text(encoding="utf-8")
old = "\tprivate var currentRow: ListRow? = null\n"
new = old + """
\t/** NOVA long-press menu: the focused title (only while the rows have focus). */
\tfun novaFocusedItem(): org.jellyfin.sdk.model.api.BaseItemDto? =
\t\tif (view?.hasFocus() == true) currentItem?.baseItem else null

\t/** Drops the focused poster after it was deleted from the server. */
\tfun novaRemoveFocusedItem() {
\t\tval row = currentRow ?: return
\t\tval item = currentItem ?: return
\t\t(row.adapter as? ItemRowAdapter)?.remove(item)
\t\tcurrentItem = null
\t}
"""
if content.count(old) != 1:
    raise RuntimeError("Home rows currentRow anchor not found")
home_rows.write_text(content.replace(old, new, 1), encoding="utf-8")


# Posters: Jellyfin's long-click opened its own pop-up menu (by faking the Menu
# key). NOVA's menu is on the ☰ button, so holding OK now just acts like a
# normal press.
card_view = root / "java/org/jellyfin/androidtv/ui/card/LegacyImageCardView.java"
content = card_view.read_text(encoding="utf-8")
old = "return activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));"
if content.count(old) != 1:
    raise RuntimeError("LegacyImageCardView long-click menu hook not found")
content = content.replace(old, "return false;", 1)
card_view.write_text(content, encoding="utf-8")


# Home: reload the rows (Discover first) once NOVA's Discover sync on opening the
# app has brought in new titles.
home_rows = root / "java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt"
content = home_rows.read_text(encoding="utf-8")
old = """				api.webSocket.subscribe<LibraryChangedMessage>()
					.onEach { refreshRows(force = true, delayed = false) }
					.launchIn(this)
"""
new = old + """
				org.jellyfin.androidtv.ui.browsing.NovaDiscoverRefresh.updates
					.onEach { refreshRows(force = true, delayed = false) }
					.launchIn(this)
"""
if content.count(old) != 1:
    raise RuntimeError("Home rows library-changed hook not found")
home_rows.write_text(content.replace(old, new, 1), encoding="utf-8")


# Kids lock: the PIN screen activity, and the check before anything plays.
manifest = root / "AndroidManifest.xml"
content = manifest.read_text(encoding="utf-8")
if "NovaUnlockActivity" not in content:
    anchor = '<activity android:name=".ui.playback.ExternalPlayerActivity" />'
    if content.count(anchor) != 1:
        raise RuntimeError("Manifest anchor for NovaUnlockActivity not found")
    content = content.replace(
        anchor,
        anchor + """

        <activity
            android:name=".ui.browsing.NovaUnlockActivity"
            android:exported="false"
            android:screenOrientation="landscape"
            android:theme="@style/Theme.Jellyfin" />""",
        1,
    )
    manifest.write_text(content, encoding="utf-8")

launcher = root / "java/org/jellyfin/androidtv/ui/playback/PlaybackLauncher.kt"
content = launcher.read_text(encoding="utf-8")
old = "\t\tval isAudio = items.any { it.mediaType == MediaType.AUDIO }"
new = """\t\t// Kids lock: titles rated 15 / R or above ask for the PIN first.
\t\tif (!novaUnlocked) {
\t\t\torg.jellyfin.androidtv.ui.browsing.NovaKidsLock.guard(context, items) {
\t\t\t\tnovaUnlocked = true
\t\t\t\ttry {
\t\t\t\t\tlaunch(context, items, position, replace, itemsPosition, shuffle)
\t\t\t\t} finally {
\t\t\t\t\tnovaUnlocked = false
\t\t\t\t}
\t\t\t}
\t\t\treturn
\t\t}
""" + old
if content.count(old) != 1:
    raise RuntimeError("PlaybackLauncher kids lock anchor not found")
content = content.replace(old, new, 1)
old = "\tprivate val BaseItemDto.supportsExternalPlayer"
if content.count(old) != 1:
    raise RuntimeError("PlaybackLauncher field anchor not found")
content = content.replace(old, "\tprivate var novaUnlocked = false\n\n" + old, 1)
launcher.write_text(content, encoding="utf-8")


# Posters: a small "NEW" tag (top-left) on titles added in the last 7 days.
card_view = root / "java/org/jellyfin/androidtv/ui/card/LegacyImageCardView.java"
content = card_view.read_text(encoding="utf-8")
old = """    public void showFavIcon(boolean show) {
        binding.favIcon.setVisibility(show ? VISIBLE : GONE);
    }
"""
new = old + """
    private android.widget.TextView novaNewTag;

    public void setNovaNew(boolean show) {
        if (!show) {
            if (novaNewTag != null) novaNewTag.setVisibility(GONE);
            return;
        }
        if (novaNewTag == null) {
            if (!(binding.mainImage.getParent() instanceof android.widget.RelativeLayout)) return;
            android.widget.RelativeLayout parent = (android.widget.RelativeLayout) binding.mainImage.getParent();
            float density = getResources().getDisplayMetrics().density;
            novaNewTag = new android.widget.TextView(getContext());
            novaNewTag.setText("NEW");
            novaNewTag.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f);
            novaNewTag.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            novaNewTag.setLetterSpacing(0.08f);
            novaNewTag.setTextColor(android.graphics.Color.WHITE);
            novaNewTag.setPadding((int) (6 * density), (int) (2 * density), (int) (6 * density), (int) (2 * density));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(org.jellyfin.androidtv.ui.NovaTheme.accentArgb());
            bg.setCornerRadius(3 * density);
            novaNewTag.setBackground(bg);
            android.widget.RelativeLayout.LayoutParams lp = new android.widget.RelativeLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.addRule(android.widget.RelativeLayout.ALIGN_START, binding.mainImage.getId());
            lp.addRule(android.widget.RelativeLayout.ALIGN_TOP, binding.mainImage.getId());
            lp.setMargins((int) (6 * density), (int) (6 * density), 0, 0);
            parent.addView(novaNewTag, lp);
        }
        novaNewTag.setVisibility(VISIBLE);
    }
"""
if content.count(old) != 1:
    raise RuntimeError("LegacyImageCardView favourite icon anchor not found")
card_view.write_text(content.replace(old, new, 1), encoding="utf-8")

card_presenter = root / "java/org/jellyfin/androidtv/ui/presentation/CardPresenter.java"
content = card_presenter.read_text(encoding="utf-8")
old = "        holder.mCardView.showFavIcon(mShowOverlays && rowItem.isFavorite());\n"
new = old + "        holder.mCardView.setNovaNew(org.jellyfin.androidtv.ui.browsing.NovaNewTag.isNew(rowItem.getBaseItem()));\n"
if content.count(old) != 1:
    raise RuntimeError("CardPresenter favourite anchor not found")
card_presenter.write_text(content.replace(old, new, 1), encoding="utf-8")


# Collections: opening a collection (e.g. The Addams Family Collection) uses the
# NOVA page with the side bar and hero, not Jellyfin's old collection screen.
destinations = root / "java/org/jellyfin/androidtv/ui/navigation/Destinations.kt"
content = destinations.read_text(encoding="utf-8")
old = "\tfun collectionBrowser(item: BaseItemDto) = fragmentDestination<CollectionFragment>("
new = "\tfun collectionBrowser(item: BaseItemDto) = fragmentDestination<org.jellyfin.androidtv.ui.browsing.NovaLibraryFragment>("
if content.count(old) != 1:
    raise RuntimeError("collectionBrowser destination not found")
content = content.replace(old, new, 1).replace("import org.jellyfin.androidtv.ui.browsing.CollectionFragment\n", "")
destinations.write_text(content, encoding="utf-8")


# Home: "Random Movies" keeps the same picks until NOVA is restarted, instead of
# reshuffling every time the rows refresh (coming back to Home, a library update…).
home_rows = root / "java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt"
content = home_rows.read_text(encoding="utf-8")
old = """			repeat(adapter.size()) { i ->
				val rowAdapter = (adapter[i] as? ListRow)?.adapter as? ItemRowAdapter
"""
new = """			repeat(adapter.size()) { i ->
				val listRow = adapter[i] as? ListRow
				if (listRow?.headerItem?.name == "Random Movies" && (listRow.adapter?.size() ?: 0) > 0) return@repeat
				val rowAdapter = listRow?.adapter as? ItemRowAdapter
"""
if content.count(old) != 1:
    raise RuntimeError("Home refreshRows loop not found")
home_rows.write_text(content.replace(old, new, 1), encoding="utf-8")


# The remote's ☰ button: never Jellyfin's pop-up menu (Play / Add to favourites …).
# NOVA's own menu (with Delete) handles it on NOVA's pages.
key_processor = root / "java/org/jellyfin/androidtv/util/KeyProcessor.java"
content = key_processor.read_text(encoding="utf-8")
old = """            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_BUTTON_Y:
                Timber.d("Menu for: %s", rowItem.getFullName(activity));"""
new = """            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (activity != null && rowItem.getBaseItem() != null) {
                    // NOVA: its own menu (with Delete) instead of Jellyfin's pop-up.
                    org.jellyfin.androidtv.ui.browsing.NovaItemMenuHost.show(activity, rowItem.getBaseItem());
                    return true;
                }
                if (true) return false;
                Timber.d("Menu for: %s", rowItem.getFullName(activity));"""
if content.count(old) != 1:
    raise RuntimeError("KeyProcessor menu case not found")
key_processor.write_text(content.replace(old, new, 1), encoding="utf-8")


# Jellyfin screens NOVA no longer opens anywhere: removed so they can't turn up.
# - GenericFolderFragment: the old season/folder list (seasons use NOVA's page).
# - CollectionFragment: the old collection screen (collections use NOVA's page).
destinations = root / "java/org/jellyfin/androidtv/ui/navigation/Destinations.kt"
content = destinations.read_text(encoding="utf-8")
old = """	// TODO only pass item id instead of complete JSON to browsing destinations
	fun folderBrowser(item: BaseItemDto) = fragmentDestination<GenericFolderFragment>(
		Extras.Folder to Json.Default.encodeToString(item),
	)

"""
if content.count(old) != 1:
    raise RuntimeError("folderBrowser destination not found")
content = content.replace(old, "", 1).replace("import org.jellyfin.androidtv.ui.browsing.GenericFolderFragment\n", "")
destinations.write_text(content, encoding="utf-8")
for dead in ("GenericFolderFragment.kt", "CollectionFragment.kt"):
    path = root / "java/org/jellyfin/androidtv/ui/browsing" / dead
    if path.exists():
        path.unlink()
for path in (root / "java").rglob("*"):
    if path.suffix in (".kt", ".java") and path.is_file():
        text = path.read_text(encoding="utf-8")
        for name in ("GenericFolderFragment", "CollectionFragment", "folderBrowser("):
            if name in text:
                raise RuntimeError(f"{name} still used in {path}")


# Soft click when moving around NOVA with the remote (Settings → Navigation sounds).
application = root / "java/org/jellyfin/androidtv/JellyfinApplication.kt"
content = application.read_text(encoding="utf-8")
old = """		if (ACRA.isACRASenderServiceProcess()) return
"""
if content.count(old) != 1:
    raise RuntimeError("JellyfinApplication onCreate anchor not found")
content = content.replace(old, old + """
		org.jellyfin.androidtv.ui.NovaClickSound.install(this)
""", 1)
application.write_text(content, encoding="utf-8")


# Music artists open NOVA's artist page (their albums), not Jellyfin's details page.
item_launcher = root / "java/org/jellyfin/androidtv/ui/itemhandling/ItemLauncher.java"
content = item_launcher.read_text(encoding="utf-8")
old = """                    case MUSIC_ARTIST:
                        navigationRepository.getValue().navigate(Destinations.INSTANCE.itemDetails(baseItem.getId()));"""
new = """                    case MUSIC_ARTIST:
                        navigationRepository.getValue().navigate(Destinations.INSTANCE.novaItem(baseItem));"""
if content.count(old) != 1:
    raise RuntimeError("ItemLauncher MUSIC_ARTIST route not found")
item_launcher.write_text(content.replace(old, new, 1), encoding="utf-8")


# Screensaver while music plays: just dim the screen over NOVA (the page you
# were on stays visible underneath) instead of the full "Now Playing" screensaver.
screensaver = root / "java/org/jellyfin/androidtv/ui/screensaver/InAppScreensaver.kt"
content = screensaver.read_text(encoding="utf-8")
old = """	val visible by interactionTrackerViewModel.visible.collectAsState()
"""
new = """	val visible by interactionTrackerViewModel.visible.collectAsState()
	val mediaManager = org.koin.compose.koinInject<org.jellyfin.androidtv.ui.playback.MediaManager>()
	// Read each time the screensaver appears: music playing → dim only.
	val musicPlaying = remember(visible) { mediaManager.isPlayingAudio }
"""
if content.count(old) != 1:
    raise RuntimeError("InAppScreensaver visible anchor not found")
content = content.replace(old, new, 1)
old = """				.background(Color.Black)
"""
new = """				.background(if (musicPlaying) Color.Black.copy(alpha = 0.78f) else Color.Black)
"""
if content.count(old) != 1:
    raise RuntimeError("InAppScreensaver background anchor not found")
content = content.replace(old, new, 1)
old = """			DreamHost()
"""
new = """			if (!musicPlaying) DreamHost()
"""
if content.count(old) != 1:
    raise RuntimeError("InAppScreensaver DreamHost anchor not found")
content = content.replace(old, new, 1)
screensaver.write_text(content, encoding="utf-8")


# Music sometimes skipped a song: if the next song's stream couldn't be fetched
# from the server first time (a slow moment on the NAS / Wi-Fi), the player
# silently jumped to the one after. Try again a couple of times before skipping.
media_stream_service = Path("jellyfin-src/playback/core/src/main/kotlin/mediastream/MediaStreamService.kt")
content = media_stream_service.read_text(encoding="utf-8")
old = """				val hasMediaStream = entry.ensureMediaStream()
"""
new = """				var hasMediaStream = entry.ensureMediaStream()
				var attempt = 0
				while (!hasMediaStream && attempt < 2) {
					attempt++
					kotlinx.coroutines.delay(1_200L * attempt)
					hasMediaStream = entry.ensureMediaStream()
				}
"""
if content.count(old) != 1:
    raise RuntimeError("MediaStreamService stream resolve anchor not found")
media_stream_service.write_text(content.replace(old, new, 1), encoding="utf-8")


# Hero previews vs music: previews no longer take the audio over from music
# (which paused it), and go silent while music plays (NovaPreviewMute).
exo_backend = Path("jellyfin-src/playback/media3/exoplayer/src/main/kotlin/ExoPlayerBackend.kt")
content = exo_backend.read_text(encoding="utf-8")
old = """		const val MEDIA_ITEM_COUNT_MAX = 10
	}
"""
new = """		const val MEDIA_ITEM_COUNT_MAX = 10

		// NOVA: hero preview players, so they can be silenced while music plays.
		private val previewPlayers = java.util.Collections.newSetFromMap(java.util.WeakHashMap<ExoPlayer, Boolean>())
		@Volatile
		private var previewsMuted = false

		@JvmStatic
		fun setPreviewsMuted(muted: Boolean) {
			previewsMuted = muted
			previewPlayers.toList().forEach { player -> runCatching { player.volume = if (muted) 0f else 1f } }
		}
	}
"""
if content.count(old) != 1:
    raise RuntimeError("ExoPlayerBackend companion anchor not found")
content = content.replace(old, new, 1)
old = """			}.build(), true)
"""
new = """			}.build(), !exoPlayerOptions.enablePreviewLeveling)
"""
if content.count(old) != 1:
    raise RuntimeError("ExoPlayerBackend audio focus anchor not found")
content = content.replace(old, new, 1)
old = """				player.addListener(PlayerListener())
"""
new = """				player.addListener(PlayerListener())
				if (exoPlayerOptions.enablePreviewLeveling) {
					previewPlayers.add(player)
					player.volume = if (previewsMuted) 0f else 1f
				}
"""
if content.count(old) != 1:
    raise RuntimeError("ExoPlayerBackend listener anchor not found")
content = content.replace(old, new, 1)
exo_backend.write_text(content, encoding="utf-8")

application = root / "java/org/jellyfin/androidtv/JellyfinApplication.kt"
content = application.read_text(encoding="utf-8")
old = """		org.jellyfin.androidtv.ui.NovaClickSound.install(this)
"""
if content.count(old) != 1:
    raise RuntimeError("NovaClickSound install line not found")
content = content.replace(old, old + """		org.jellyfin.androidtv.ui.home.NovaPreviewMute.install()
""", 1)
application.write_text(content, encoding="utf-8")


# Home: no "Now Playing" row (music shows what's playing on its own pages).
home_rows_fragment = root / "java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt"
content = home_rows_fragment.read_text(encoding="utf-8")
old = "\t\tnowPlaying.update(requireContext(), adapter as MutableObjectAdapter<Row>)\n"
if content.count(old) != 2:
    raise RuntimeError("HomeRowsFragment now playing updates not found")
content = content.replace(old, "\t\t// NOVA: no Now Playing row on Home.\n")
home_rows_fragment.write_text(content, encoding="utf-8")


# Home: coming back to Home reloads the rows (Random Movies stays the same) if
# they're more than two minutes old, so newly downloaded titles turn up.
home_rows_fragment = root / "java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt"
content = home_rows_fragment.read_text(encoding="utf-8")
old = """			// Returning from playback: keep the same random movie set and only
			// refresh metadata/progress for the currently selected card.
			refreshCurrentItem()
"""
new = old + """			if (android.os.SystemClock.elapsedRealtime() - novaRowsLoadedAt > 120_000L) {
				novaRowsLoadedAt = android.os.SystemClock.elapsedRealtime()
				refreshRows(force = true, delayed = false)
			}
"""
if content.count(old) != 1:
    raise RuntimeError("HomeRowsFragment onResume anchor not found")
content = content.replace(old, new, 1)
old = "\tprivate val nowPlaying by lazy {"
if content.count(old) != 1:
    raise RuntimeError("HomeRowsFragment field anchor not found")
content = content.replace(old, "\tprivate var novaRowsLoadedAt = android.os.SystemClock.elapsedRealtime()\n" + old, 1)
home_rows_fragment.write_text(content, encoding="utf-8")


# Home: only Random Movies (no "Recently added" or Continue Watching rows).
home_rows_fragment = root / "java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt"
content = home_rows_fragment.read_text(encoding="utf-8")
old = """\t\t\tmovies?.let { rows.add(helper.loadRecentlyAdded(listOf(it))) }
\t\t\tindianMovies?.let { rows.add(helper.loadRecentlyAdded(listOf(it))) }
\t\t\tshows?.let { rows.add(helper.loadRecentlyAdded(listOf(it))) }
\t\t\trows.add(helper.loadResumeVideo())
\t\t\tprayers?.let { rows.add(helper.loadRecentlyAdded(listOf(it))) }
\t\t\tmusic?.let { rows.add(helper.loadRecentlyAdded(listOf(it))) }
\t\t\t// Preserve other libraries after the requested Home sections.
\t\t\trows.add(helper.loadRecentlyAdded(remainingViews))
"""
new = """\t\t\t// NOVA: Home is just Random Movies.
"""
if content.count(old) != 1:
    raise RuntimeError("Home rows list not found")
content = content.replace(old, new, 1)
home_rows_fragment.write_text(content, encoding="utf-8")


# Open NOVA when the Fire TV starts or wakes up (Settings → Customization → "Open NOVA on start-up").
manifest = root / "AndroidManifest.xml"
content = manifest.read_text(encoding="utf-8")
if "NovaBootReceiver" not in content:
    anchor = '    <uses-permission android:name="android.permission.INTERNET" />\n'
    if content.count(anchor) != 1:
        raise RuntimeError("Manifest permission anchor not found")
    content = content.replace(anchor, anchor + """    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
""", 1)
    anchor = '<activity android:name=".ui.playback.ExternalPlayerActivity" />'
    if content.count(anchor) != 1:
        raise RuntimeError("Manifest anchor for NovaBootReceiver not found")
    content = content.replace(anchor, anchor + """

        <receiver
            android:name=".ui.NovaBootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.QUICKBOOT_POWERON" />
            </intent-filter>
        </receiver>

        <service
            android:name=".ui.NovaWakeService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Opens NOVA when the TV wakes from sleep" />
        </service>""", 1)
    manifest.write_text(content, encoding="utf-8")

application = root / "java/org/jellyfin/androidtv/JellyfinApplication.kt"
content = application.read_text(encoding="utf-8")
old = """		org.jellyfin.androidtv.ui.home.NovaPreviewMute.install()
"""
if content.count(old) != 1:
    raise RuntimeError("NovaPreviewMute install line not found")
content = content.replace(old, old + """		org.jellyfin.androidtv.ui.NovaAutoStart.start(this)
		org.jellyfin.androidtv.ui.browsing.NovaIndianFilms.init(this)
""", 1)
application.write_text(content, encoding="utf-8")

customization = root / "java/org/jellyfin/androidtv/ui/preference/screen/CustomizationPreferencesScreen.kt"
content = customization.read_text(encoding="utf-8")
old = """					set { value -> org.jellyfin.androidtv.ui.NovaClickSound.setEnabled(requireContext(), value) }
					default { true }
				}
			}
"""
if content.count(old) != 1:
    raise RuntimeError("Navigation sounds setting not found")
content = content.replace(old, old + """
			checkbox {
				title = "Open NOVA on start-up"
				contentOn = "NOVA opens when the Fire TV turns on or wakes up"
				contentOff = "Off: the Fire TV starts on Amazon's home screen"
				bind {
					get { org.jellyfin.androidtv.ui.NovaAutoStart.isEnabled(requireContext()) }
					set { value -> org.jellyfin.androidtv.ui.NovaAutoStart.setEnabled(requireContext(), value) }
					default { true }
				}
			}
""", 1)
customization.write_text(content, encoding="utf-8")


# "Who's watching": NOVA avatars on the profile cards (hold OK / ☰ on a profile → "Choose avatar").
user_card = root / "java/org/jellyfin/androidtv/ui/card/UserCardView.kt"
content = user_card.read_text(encoding="utf-8")
old = """	var image by mutableStateOf<String?>(null)
"""
if content.count(old) != 1:
    raise RuntimeError("UserCardView image property not found")
content = content.replace(old, old + """	/** A NOVA avatar picked for this profile (shown instead of the picture). */
	var avatar by mutableStateOf<Int?>(null)
""", 1)
old = """			image = {
				if (image != null) {"""
if content.count(old) != 1:
    raise RuntimeError("UserCardView image block not found")
content = content.replace(old, """			image = {
				val picked = avatar
				if (picked != null) {
					org.jellyfin.androidtv.ui.startup.NovaAvatarArt(picked, Modifier.fillMaxSize())
				} else if (image != null) {""", 1)
user_card.write_text(content, encoding="utf-8")

server_fragment = root / "java/org/jellyfin/androidtv/ui/startup/fragment/ServerFragment.kt"
content = server_fragment.read_text(encoding="utf-8")
old = """			holder.cardView.image = startupViewModel.getUserImage(server, user)

			holder.cardView.setPopupMenu {
"""
if content.count(old) != 1:
    raise RuntimeError("ServerFragment user card binding not found")
content = content.replace(old, """			holder.cardView.image = startupViewModel.getUserImage(server, user)
			holder.cardView.avatar = org.jellyfin.androidtv.ui.startup.NovaAvatars.get(context, user.id)

			holder.cardView.setPopupMenu {
				// NOVA: pick an avatar for this profile.
				item("Choose avatar") {
					val avatars = org.jellyfin.androidtv.ui.startup.NovaAvatars
					avatars.pick(context, avatars.get(context, user.id)) { picked ->
						avatars.set(context, user.id, picked)
						holder.cardView.avatar = picked
					}
				}
""", 1)
server_fragment.write_text(content, encoding="utf-8")


# Say NOVA, not Jellyfin, on screens you can see.
def nova_rename(path, pairs):
    file = root / path
    text = file.read_text(encoding="utf-8")
    for old, new in pairs:
        if old not in text:
            raise RuntimeError(f"NOVA rename: {old[:50]!r} not found in {path}")
        text = text.replace(old, new)
    file.write_text(text, encoding="utf-8")


nova_rename("java/org/jellyfin/androidtv/ui/preference/category/about.kt", [
    ('title = "Jellyfin app version"', 'title = "NOVA app version"'),
    ('content = "jellyfin-androidtv ${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_TYPE}"',
     'content = "NOVA ${BuildConfig.VERSION_NAME}"'),
])
nova_rename("res/layout/fragment_server_add.xml", [
    ("Enter the Jellyfin server address", "Enter your server address"),
])
for strings in ("res/values/strings.xml", "res/values-en-rGB/strings.xml"):
    file = root / strings
    text = file.read_text(encoding="utf-8")
    text = text.replace(
        "NOVA requires a server to connect with. Visit our documentation at docs.jellyfin.org or scan the QR code to get started with NOVA.",
        "NOVA needs your media server to connect to. Scan the QR code for help getting started.",
    )
    file.write_text(text, encoding="utf-8")
nova_rename("java/org/jellyfin/androidtv/ui/startup/fragment/UserLoginFragment.kt", [
    ('server?.name ?: "Jellyfin"', 'server?.name ?: "NOVA"'),
])
nova_rename("java/org/jellyfin/androidtv/auth/repository/ServerRepository.kt", [
    ('systemInfo.serverName ?: "Jellyfin Server"', 'systemInfo.serverName ?: "NOVA Server"'),
])


# Tidy-up: code left unused by NOVA's changes to Jellyfin's own files.
import re


def nova_tidy(path, removals):
    file = root / path
    text = file.read_text(encoding="utf-8")
    for old in removals:
        if text.count(old) != 1:
            raise RuntimeError(f"NOVA tidy: {old.strip()[:60]!r} not found once in {path}")
        text = text.replace(old, "", 1)
    # Drop imports that nothing uses any more.
    def keep(match):
        name = match.group(1).split(".")[-1]
        body = text.replace(match.group(0), "")
        return match.group(0) if re.search(r"\b" + re.escape(name) + r"\b", body) else ""
    text = re.sub(r"^import ([\w.]+)\n", keep, text, flags=re.M)
    file.write_text(text, encoding="utf-8")


nova_tidy("java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt", [
    "\tprivate val userSettingPreferences by inject<UserSettingPreferences>()\n",
    "\tprivate val playbackManager by inject<PlaybackManager>()\n",
    "\tprivate val nowPlaying by lazy { HomeFragmentNowPlayingRow(lifecycleScope, playbackManager, mediaManager) }\n",
    '\t\t\tval indianMovies = takeView("Indian Movies")\n',
    '\t\t\tval shows = takeView("TV Shows", org.jellyfin.sdk.model.api.CollectionType.TVSHOWS)\n',
    '\t\t\tval prayers = takeView("Prayers")\n',
    '\t\t\tval music = takeView("Music", org.jellyfin.sdk.model.api.CollectionType.MUSIC)\n',
])
home_rows_text = (root / "java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt").read_text(encoding="utf-8")
discover_block = re.search(
    r"\t\t\tval discover = takeView\(\"Discover\"\)\n.*?\}\?\.also \{ remainingViews\.remove\(it\) \}\n",
    home_rows_text,
    flags=re.S,
)
if discover_block is None:
    raise RuntimeError("NOVA tidy: Home's unused Discover lookup not found")
nova_tidy("java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt", [discover_block.group(0)])

nova_tidy("java/org/jellyfin/androidtv/ui/startup/fragment/UserLoginFragment.kt", [
    "\tprivate val skipQuickConnect get() = arguments?.getBoolean(ARG_SKIP_QUICKCONNECT) ?: false\n",
])
nova_tidy("java/org/jellyfin/androidtv/ui/startup/StartupActivity.kt", [])
nova_tidy("java/org/jellyfin/androidtv/ui/startup/fragment/SplashFragment.kt", [])

server_fragment_text = (root / "java/org/jellyfin/androidtv/ui/startup/fragment/ServerFragment.kt").read_text(encoding="utf-8")
server_fragment_text = server_fragment_text.replace("keepToolbar = true, ", "").replace("(keepToolbar = true)", "()")
(root / "java/org/jellyfin/androidtv/ui/startup/fragment/ServerFragment.kt").write_text(server_fragment_text, encoding="utf-8")
nova_tidy("java/org/jellyfin/androidtv/ui/startup/fragment/ServerFragment.kt", [
    "\t\tkeepToolbar: Boolean = false,\n",
])

# The start-up toolbar has no Settings button any more.
nova_tidy("java/org/jellyfin/androidtv/ui/shared/toolbar/StartupToolbar.kt", [
    "    openSettings: () -> Unit,\n",
])
toolbar_fragment = root / "java/org/jellyfin/androidtv/ui/startup/fragment/StartupToolbarFragment.kt"
text = toolbar_fragment.read_text(encoding="utf-8")
settings_arg = re.search(r"\t\t\t\t\topenSettings = \{\n.*?\n\t\t\t\t\t\},\n", text, flags=re.S)
if settings_arg is None:
    raise RuntimeError("NOVA tidy: start-up toolbar Settings action not found")
toolbar_fragment.write_text(text.replace(settings_arg.group(0), "", 1), encoding="utf-8")
nova_tidy("java/org/jellyfin/androidtv/ui/startup/fragment/StartupToolbarFragment.kt", [])

# Jellyfin's old top toolbar: NOVA uses its side bar instead, so nothing uses it.
old_toolbar = root / "java/org/jellyfin/androidtv/ui/shared/toolbar/MainToolbar.kt"
if old_toolbar.exists():
    old_toolbar.unlink()


# YouTube trailers: androidx.webkit lets NOVA tidy up YouTube's player frame
# (see NovaTrailerPlayer).
build_file = root.parent.parent / "build.gradle.kts"
build_text = build_file.read_text(encoding="utf-8")
if "androidx.webkit:webkit" not in build_text:
    marker = "dependencies {\n"
    if marker not in build_text:
        raise RuntimeError("NOVA: app dependencies block not found")
    build_text = build_text.replace(marker, marker + '\timplementation("androidx.webkit:webkit:1.12.1")\n', 1)
    build_file.write_text(build_text, encoding="utf-8")

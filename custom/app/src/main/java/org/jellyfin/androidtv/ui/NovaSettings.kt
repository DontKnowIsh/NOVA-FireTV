package org.jellyfin.androidtv.ui

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.SeekBarPreference
import androidx.preference.TwoStatePreference
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.browsing.NovaStorage
import org.jellyfin.androidtv.ui.preference.custom.ColorListPreference
import org.jellyfin.androidtv.ui.preference.custom.DurationSeekBarPreference
import org.jellyfin.androidtv.ui.preference.custom.RichListDialogFragment
import org.jellyfin.androidtv.ui.preference.custom.RichListPreference
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.java.KoinJavaComponent
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * NOVA's own Settings: the same settings and behaviour as Jellyfin's (they are
 * Jellyfin's own setting screens, loaded behind the scenes), drawn entirely in the
 * NOVA style as a panel on the right that follows the chosen theme.
 */
object NovaSettings {
	/** Opens Jellyfin's original settings screens instead (fallback for rare items). */
	const val EXTRA_LEGACY = "nova_legacy_settings"

	private data class Page(val fragmentClass: String, val args: Bundle, val tag: String)

	fun show(activity: FragmentActivity, rootScreen: String, rootArgs: Bundle) {
		val pages = mutableListOf<Page>()
		var version by mutableIntStateOf(0)
		// The panel draws its own dim; drop the window's so the page isn't darkened twice.
		activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)

		fun load(page: Page): PreferenceFragmentCompat? = runCatching {
			val fm = activity.supportFragmentManager
			fm.findFragmentByTag(page.tag)?.let { fm.beginTransaction().remove(it).commitNow() }
			val fragment = fm.fragmentFactory.instantiate(activity.classLoader, page.fragmentClass)
			fragment.arguments = page.args
			fm.beginTransaction().add(fragment, page.tag).commitNow()
			fragment as? PreferenceFragmentCompat
		}.onFailure { Timber.w(it, "NOVA settings: couldn't open %s", page.fragmentClass) }.getOrNull()

		fun openLegacy(screen: String, args: Bundle) {
			activity.startActivity(
				Intent(activity, activity.javaClass)
					.putExtra(EXTRA_LEGACY, true)
					.putExtra("screen", screen)
					.putExtra("screen_args", args)
			)
		}

		val fragments = mutableMapOf<String, PreferenceFragmentCompat>()

		fun push(fragmentClass: String, args: Bundle) {
			val page = Page(fragmentClass, args, "nova_settings_${pages.size}")
			val fragment = load(page)
			if (fragment == null) {
				openLegacy(fragmentClass, args)
				return
			}
			pages += page
			fragments[page.tag] = fragment
			version++
		}

		fun pop(): Boolean {
			if (pages.size <= 1) return false
			val gone = pages.removeAt(pages.lastIndex)
			fragments.remove(gone.tag)?.let { runCatching { activity.supportFragmentManager.beginTransaction().remove(it).commitNow() } }
			// Reload the screen we return to, so it reflects changes made deeper in.
			val top = pages.last()
			load(top)?.let { fragments[top.tag] = it }
			version++
			return true
		}

		push(rootScreen, rootArgs)

		activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				if (!pop()) activity.finish()
			}
		})

		activity.setContentView(ComposeView(activity).apply {
			setContent {
				JellyfinTheme {
					val v = version
					val page = pages.lastOrNull()
					val fragment = page?.let { fragments[it.tag] }
					SettingsPanel(
						version = v,
						depth = pages.size,
						screen = fragment?.preferenceScreen,
						onChanged = {
							// A theme change applies straight away.
							runCatching {
								NovaTheme.update(KoinJavaComponent.get<UserPreferences>(UserPreferences::class.java)[UserPreferences.appTheme])
							}
							version++
						},
						onOpen = { pref ->
							val target = pref.fragment
							when {
								target != null -> push(target, pref.extras ?: bundleOf())
								pref.intent != null -> runCatching { activity.startActivity(pref.intent) }
								else -> {
									pref.onPreferenceClickListener?.onPreferenceClick(pref)
									version++
								}
							}
						},
						onLegacy = { page?.let { openLegacy(it.fragmentClass, it.args) } },
						onBack = { if (!pop()) activity.finish() },
					)
				}
			}
		})
	}

	@Composable
	private fun SettingsPanel(
		version: Int,
		depth: Int,
		screen: PreferenceGroup?,
		onChanged: () -> Unit,
		onOpen: (Preference) -> Unit,
		onLegacy: () -> Unit,
		onBack: () -> Unit,
	) {
		var picker by remember { mutableStateOf<Preference?>(null) }
		val firstFocus = remember { FocusRequester() }

		LaunchedEffect(depth, screen) {
			delay(80L)
			runCatching { firstFocus.requestFocus() }
		}

		Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
			Column(
				modifier = Modifier
					.align(Alignment.CenterEnd)
					.fillMaxHeight()
					.width(460.dp)
					.background(nova.bg, RoundedCornerShape(topStart = nova.corner(16), bottomStart = nova.corner(16)))
					.border(1.dp, nova.surfaceHigh, RoundedCornerShape(topStart = nova.corner(16), bottomStart = nova.corner(16)))
					.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 12.dp)
			) {
				Header(title = screen?.title?.toString() ?: "Settings", depth = depth, onBack = onBack)
				Spacer(Modifier.height(14.dp))
				key(version) {
					val rows = remember(version, screen) { flatten(screen) }
					LazyColumn(
						modifier = Modifier.fillMaxWidth().weight(1f),
						verticalArrangement = Arrangement.spacedBy(4.dp),
						contentPadding = PaddingValues(bottom = 24.dp),
					) {
						itemsIndexed(rows) { index, row ->
							when (row) {
								is Line.Header -> SectionHeader(row.title)
								is Line.Item -> PrefRow(
									pref = row.pref,
									modifier = if (index == rows.indexOfFirst { it is Line.Item }) Modifier.focusRequester(firstFocus) else Modifier,
									onChanged = onChanged,
									onOpen = onOpen,
									onPick = { picker = it },
									onLegacy = onLegacy,
								)
							}
						}
					}
				}
			}
		}

		picker?.let { pref ->
			OptionPicker(pref = pref, onDismiss = { picker = null }, onPicked = {
				picker = null
				onChanged()
			})
		}
	}

	private sealed interface Line {
		data class Header(val title: String) : Line
		data class Item(val pref: Preference) : Line
	}

	/** Categories become section titles; everything visible becomes a row. */
	private fun flatten(group: PreferenceGroup?): List<Line> {
		if (group == null) return emptyList()
		val rows = mutableListOf<Line>()
		for (i in 0 until group.preferenceCount) {
			val pref = group.getPreference(i)
			if (!pref.isVisible) continue
			if (pref is PreferenceCategory) {
				val children = flatten(pref)
				if (children.isEmpty()) continue
				pref.title?.toString()?.takeIf { it.isNotBlank() }?.let { rows += Line.Header(it) }
				rows += children
			} else {
				rows += Line.Item(pref)
			}
		}
		return rows
	}

	@Composable
	private fun Header(title: String, depth: Int, onBack: () -> Unit) {
		var storage by remember { mutableStateOf(NovaStorage.last) }
		LaunchedEffect(Unit) {
			runCatching { storage = NovaStorage.load(KoinJavaComponent.get<ApiClient>(ApiClient::class.java)) ?: storage }
		}
		Row(verticalAlignment = Alignment.CenterVertically) {
			Image(
				painter = painterResource(R.drawable.nova_n_mark),
				contentDescription = null,
				modifier = Modifier.size(34.dp).clickable { onBack() },
			)
			Spacer(Modifier.width(12.dp))
			Column {
				Text(if (depth > 1) "SETTINGS  ‹  BACK" else "SETTINGS", color = nova.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
				Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
		}
		if (depth == 1) {
			Spacer(Modifier.height(10.dp))
			StorageBar(storage)
		}
	}

	@Composable
	private fun StorageBar(space: NovaStorage.Space?) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.background(nova.surface, RoundedCornerShape(nova.corner(8)))
				.padding(horizontal = 12.dp, vertical = 9.dp)
		) {
			Text("Server storage", color = nova.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
			Spacer(Modifier.height(2.dp))
			Text(NovaStorage.describe(space), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
			if (space != null) {
				Spacer(Modifier.height(6.dp))
				Box(Modifier.fillMaxWidth().height(6.dp).background(nova.surfaceHigh, RoundedCornerShape(3.dp))) {
					Box(
						Modifier
							.fillMaxWidth(space.usedPercent / 100f)
							.height(6.dp)
							.background(if (space.usedPercent >= 90) nova.statusMissing else nova.accent, RoundedCornerShape(3.dp))
					)
				}
			}
		}
	}

	@Composable
	private fun SectionHeader(title: String) {
		Text(
			title.uppercase(),
			color = nova.accent,
			fontSize = 11.sp,
			fontWeight = FontWeight.Bold,
			letterSpacing = 1.4.sp,
			modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp),
		)
	}

	@Composable
	private fun PrefRow(
		pref: Preference,
		modifier: Modifier,
		onChanged: () -> Unit,
		onOpen: (Preference) -> Unit,
		onPick: (Preference) -> Unit,
		onLegacy: () -> Unit,
	) {
		val interactionSource = remember { MutableInteractionSource() }
		val focused by interactionSource.collectIsFocusedAsState()
		val enabled = pref.isEnabled && pref !is EditTextPreference
		val shape = RoundedCornerShape(nova.corner(8))
		val textColour = when {
			focused -> nova.onAccent
			!pref.isEnabled -> nova.textFaint
			else -> nova.textPrimary
		}
		val subColour = if (focused) nova.onAccent.copy(alpha = 0.8f) else nova.textMuted

		// Sliders change with Left / Right while highlighted.
		val sliderKeys = if (pref is SeekBarPreference && enabled) {
			Modifier.onPreviewKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
				val step = when (event.key) {
					Key.DirectionLeft -> -1
					Key.DirectionRight -> 1
					else -> return@onPreviewKeyEvent false
				}
				val increment = pref.seekBarIncrement.coerceAtLeast(1)
				val next = (pref.value + step * increment).coerceIn(pref.min, pref.max)
				if (next != pref.value && pref.callChangeListener(next)) pref.value = next
				onChanged()
				true
			}
		} else Modifier

		Button(
			onClick = {
				if (!enabled) return@Button
				when (pref) {
					is TwoStatePreference -> {
						val next = !pref.isChecked
						if (pref.callChangeListener(next)) pref.isChecked = next
						onChanged()
					}
					is SeekBarPreference -> Unit
					is RichListPreference<*>, is ListPreference -> onPick(pref)
					else -> if (pref.javaClass.simpleName == "ButtonRemapPreference") onLegacy() else onOpen(pref)
				}
			},
			modifier = modifier.fillMaxWidth().then(sliderKeys),
			interactionSource = interactionSource,
			shape = shape,
			colors = ButtonDefaults.colors(
				containerColor = Color.Transparent,
				contentColor = textColour,
				focusedContainerColor = nova.accent,
				focusedContentColor = nova.onAccent,
			),
			contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
		) {
			Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
				PrefIcon(pref.icon, textColour)
				Column(modifier = Modifier.weight(1f)) {
					Text(pref.title?.toString().orEmpty(), color = textColour, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
					val summary = when (pref) {
						is DurationSeekBarPreference -> pref.valueFormatter.display(pref.value)
						is SeekBarPreference -> pref.value.toString()
						else -> runCatching { pref.summary?.toString() }.getOrNull()
					}
					if (!summary.isNullOrBlank()) {
						Text(summary, color = subColour, fontSize = 13.sp, maxLines = 3)
					}
				}
				Spacer(Modifier.width(10.dp))
				Trailing(pref, focused)
			}
		}
	}

	@Composable
	private fun PrefIcon(icon: Drawable?, tint: Color) {
		if (icon == null) return
		val bitmap = remember(icon) { runCatching { icon.toBitmap(48, 48).asImageBitmap() }.getOrNull() } ?: return
		Image(bitmap = bitmap, contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(22.dp))
		Spacer(Modifier.width(14.dp))
	}

	@Composable
	private fun Trailing(pref: Preference, focused: Boolean) {
		when (pref) {
			is TwoStatePreference -> {
				val on = pref.isChecked
				Box(
					modifier = Modifier
						.size(width = 44.dp, height = 24.dp)
						.background(
							when {
								on && focused -> nova.onAccent.copy(alpha = 0.35f)
								on -> nova.accent
								else -> nova.surfaceHigh
							},
							RoundedCornerShape(12.dp),
						)
						.padding(3.dp),
					contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
				) {
					Box(Modifier.size(18.dp).background(Color.White, CircleShape))
				}
			}
			is SeekBarPreference -> Text("‹  ›", color = if (focused) nova.onAccent else nova.textMuted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
			is RichListPreference<*>, is ListPreference -> Text("›", color = if (focused) nova.onAccent else nova.textMuted, fontSize = 20.sp)
			is EditTextPreference -> Unit
			else -> if (pref.fragment != null) Text("›", color = if (focused) nova.onAccent else nova.textMuted, fontSize = 20.sp)
		}
	}

	private data class Option(val title: String, val summary: String?, val selected: Boolean, val section: Boolean, val pick: () -> Unit)

	/** NOVA list pop-up for list settings (theme, languages, quality…). */
	@Composable
	private fun OptionPicker(pref: Preference, onDismiss: () -> Unit, onPicked: () -> Unit) {
		val options = remember(pref) { optionsFor(pref) }
		val focus = remember { FocusRequester() }
		LaunchedEffect(Unit) {
			delay(50L)
			runCatching { focus.requestFocus() }
		}
		val firstSelectable = options.indexOfFirst { it.selected && !it.section }.takeIf { it >= 0 }
			?: options.indexOfFirst { !it.section }

		Dialog(onDismissRequest = onDismiss) {
			Column(
				modifier = Modifier
					.width(440.dp)
					.background(nova.surface, RoundedCornerShape(nova.corner(12)))
					.border(1.dp, nova.surfaceHigh, RoundedCornerShape(nova.corner(12)))
					.padding(20.dp)
			) {
				Text(pref.title?.toString().orEmpty(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
				Spacer(Modifier.height(12.dp))
				LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
					itemsIndexed(options) { index, option ->
						if (option.section) {
							SectionHeader(option.title)
						} else {
							val interactionSource = remember { MutableInteractionSource() }
							val focused by interactionSource.collectIsFocusedAsState()
							Button(
								onClick = {
									option.pick()
									onPicked()
								},
								modifier = Modifier.fillMaxWidth().then(if (index == firstSelectable) Modifier.focusRequester(focus) else Modifier),
								interactionSource = interactionSource,
								shape = RoundedCornerShape(nova.corner(8)),
								colors = ButtonDefaults.colors(
									containerColor = if (option.selected) nova.accentSoft else Color.Transparent,
									contentColor = nova.textPrimary,
									focusedContainerColor = nova.accent,
									focusedContentColor = nova.onAccent,
								),
								contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
							) {
								Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
									Column(modifier = Modifier.weight(1f)) {
										Text(option.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (focused) nova.onAccent else nova.textPrimary)
										option.summary?.takeIf { it.isNotBlank() }?.let {
											Text(it, fontSize = 12.sp, color = if (focused) nova.onAccent.copy(alpha = 0.8f) else nova.textMuted)
										}
									}
									if (option.selected) Text("✓", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (focused) nova.onAccent else nova.accent)
								}
							}
						}
					}
				}
			}
		}
	}

	@Suppress("UNCHECKED_CAST")
	private fun optionsFor(pref: Preference): List<Option> = when (pref) {
		is RichListPreference<*> -> {
			val p = pref as RichListPreference<Any?>
			p.getItems().map { item ->
				when (item) {
					is RichListDialogFragment.RichListItem.RichListSection -> Option(item.title, null, false, true) {}
					is RichListDialogFragment.RichListItem.RichListOption -> Option(item.title, item.summary, item.key == p.value, false) {
						if (p.callChangeListener(item.key)) p.value = item.key
					}
				}
			}
		}
		is ColorListPreference -> pref.items.map { item ->
			val value = item.key.toArgb().toString()
			Option(item.title, item.summary, value == pref.value, false) {
				pref.callChangeListener(item.key)
			}
		}
		is ListPreference -> {
			val names = pref.entries.orEmpty()
			val values = pref.entryValues.orEmpty()
			names.indices.map { i ->
				val value = values.getOrNull(i)?.toString()
				Option(names[i].toString(), null, value != null && value == pref.value, false) {
					if (value != null && pref.callChangeListener(value)) pref.value = value
				}
			}
		}
		else -> emptyList()
	}
}

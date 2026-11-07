package org.jellyfin.androidtv.ui

import android.util.TypedValue
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.AbsSeekBar
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import org.jellyfin.androidtv.preference.UserPreferences
import org.koin.java.KoinJavaComponent

/**
 * Restyles Jellyfin's settings screens (Android TV "leanback" preferences) to
 * match NOVA and the chosen theme: a rounded NOVA panel, rows highlighted in the
 * theme colour, themed checkboxes, switches and sliders.
 *
 * The settings are classic Android views, so they are styled as they appear.
 * Picking a new theme restyles the open settings straight away.
 */
object NovaSettingsStyle {
	private const val STYLED_TAG = 0x4E4F5641 // "NOVA"

	/** Only used for Jellyfin's original settings screens (rare fallback). */
	fun attach(activity: FragmentActivity) {
		val decor = activity.window.decorView
		activity.window.setDimAmount(0.55f)
		var palette = refreshPalette()
		decor.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
			override fun onGlobalLayout() {
				if (!decor.isAttachedToWindow) return
				val latest = refreshPalette()
				val themeChanged = latest != palette
				palette = latest
				style(decor, latest, force = themeChanged)
			}
		})
	}

	/** Re-reads Settings → Theme, so a change made on this screen applies at once. */
	private fun refreshPalette(): NovaPalette = runCatching {
		val theme = KoinJavaComponent.get<UserPreferences>(UserPreferences::class.java)[UserPreferences.appTheme]
		NovaTheme.update(theme)
		NovaTheme.palette
	}.getOrDefault(NovaTheme.palette)

	private fun style(view: View, p: NovaPalette, force: Boolean) {
		val density = view.resources.displayMetrics.density
		val alreadyStyled = view.getTag(STYLED_TAG) == p
		if (!alreadyStyled || force) {
			view.setTag(STYLED_TAG, p)
			styleOne(view, p, density)
		}
		if (view is RecyclerView && view.getTag(STYLED_TAG + 1) == null) {
			view.setTag(STYLED_TAG + 1, true)
			// Rows appear while scrolling; style each one as it is attached.
			view.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
				override fun onChildViewAttachedToWindow(child: View) = style(child, NovaTheme.palette, force = false)
				override fun onChildViewDetachedFromWindow(child: View) = Unit
			})
		}
		if (view is ViewGroup) {
			for (i in 0 until view.childCount) style(view.getChildAt(i), p, force)
		}
	}

	private const val ROLE_TAG = STYLED_TAG + 2
	private const val ROLE_TITLE = "title"
	private const val ROLE_SUMMARY = "summary"
	private const val ROLE_HEADER = "header"

	private fun styleOne(view: View, p: NovaPalette, density: Float) {
		val accent = p.accent.toArgb()
		val onAccent = p.onAccent.toArgb()
		val parent = view.parent

		when {
			// Each setting row inside a list: theme-coloured, rounded focus highlight.
			parent is RecyclerView && view.isFocusable -> {
				val radius = p.corner(6).value * density
				view.background = StateListDrawable().apply {
					addState(intArrayOf(android.R.attr.state_focused), rounded(accent, radius))
					addState(intArrayOf(android.R.attr.state_activated), rounded(p.accentSoft.toArgb(), radius))
					addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
				}
				// Let the row's text and icons follow its focus, so they can switch to
				// the theme's "on highlight" colour (dark on Amber's gold, for example).
				shareRowState(view)
			}

			// Solid panels (settings column, title bar, dialogs): NOVA surfaces.
			view.background is ColorDrawable && (view.background as ColorDrawable).alpha > 200 -> {
				val isTitle = view.javaClass.simpleName.contains("Title", ignoreCase = true) ||
					entryName(view)?.contains("title", ignoreCase = true) == true
				view.background = rounded(
					if (isTitle) p.bgDeep.toArgb() else p.surface.toArgb(),
					if (isTitle) 0f else p.corner(10).value * density,
				)
			}
		}

		when (view) {
			is CompoundButton -> {
				view.buttonTintList = stateTint(accent, p.textMuted.toArgb())
				// Switch thumb/track tints only exist on Android 6+; older Fire TVs skip them.
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && view is android.widget.Switch) {
					view.thumbTintList = stateTint(accent, p.textSecondary.toArgb())
					view.trackTintList = stateTint(p.accent.withAlpha(0x88).toArgb(), p.surfaceHigh.toArgb())
				}
				if (view is androidx.appcompat.widget.SwitchCompat) {
					view.thumbTintList = stateTint(accent, p.textSecondary.toArgb())
					view.trackTintList = stateTint(p.accent.withAlpha(0x88).toArgb(), p.surfaceHigh.toArgb())
				}
			}
			is AbsSeekBar -> {
				view.progressTintList = ColorStateList.valueOf(accent)
				view.thumbTintList = ColorStateList.valueOf(accent)
			}
			is ProgressBar -> view.progressTintList = ColorStateList.valueOf(accent)
			is TextView -> {
				// Work out what the text is once, from Jellyfin's original styling, so a
				// theme change doesn't misread colours NOVA has already applied.
				val role = view.getTag(ROLE_TAG) as? String ?: run {
					val name = entryName(view)
					when {
						name == "summary" -> ROLE_SUMMARY
						name?.contains("category", ignoreCase = true) == true ||
							view.textColors.defaultColor == themeAccent(view) -> ROLE_HEADER
						else -> ROLE_TITLE
					}.also { view.setTag(ROLE_TAG, it) }
				}
				// Larger, TV-friendly text.
				view.setTextSize(
					TypedValue.COMPLEX_UNIT_SP,
					when (role) {
						ROLE_HEADER -> 13f
						ROLE_SUMMARY -> 13f
						else -> 17f
					},
				)
				if (role == ROLE_HEADER) view.typeface = Typeface.DEFAULT_BOLD
				view.setTextColor(
					when (role) {
						ROLE_HEADER -> ColorStateList.valueOf(accent)
						ROLE_SUMMARY -> focusAware(onAccent, p.textSecondary.toArgb(), p.textFaint.toArgb())
						else -> focusAware(onAccent, p.textPrimary.toArgb(), p.textFaint.toArgb())
					}
				)
			}
			is ImageView -> if (isInsideRow(view)) {
				view.imageTintList = focusAware(onAccent, p.textPrimary.toArgb(), p.textFaint.toArgb())
			}
		}
	}

	/** Children of a focused row report "focused" too (except controls with their own state). */
	private fun shareRowState(row: View) {
		if (row !is ViewGroup) return
		for (i in 0 until row.childCount) {
			val child = row.getChildAt(i)
			if (child is CompoundButton || child is ProgressBar) continue
			child.isDuplicateParentStateEnabled = true
			shareRowState(child)
		}
	}

	private fun isInsideRow(view: View): Boolean {
		var current: android.view.ViewParent? = view.parent
		while (current is View) {
			if (current.parent is RecyclerView) return true
			current = current.parent
		}
		return false
	}

	private fun focusAware(focused: Int, normal: Int, disabled: Int) = ColorStateList(
		arrayOf(
			intArrayOf(-android.R.attr.state_enabled),
			intArrayOf(android.R.attr.state_focused),
			intArrayOf(),
		),
		intArrayOf(disabled, focused, normal),
	)

	private fun entryName(view: View): String? =
		if (view.id == View.NO_ID) null else runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()

	/** Jellyfin's own accent colour (used for category titles) in this screen's theme. */
	private fun themeAccent(view: TextView): Int {
		val value = android.util.TypedValue()
		return if (view.context.theme.resolveAttribute(android.R.attr.colorAccent, value, true)) value.data else 0
	}

	private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
		setColor(color)
		cornerRadius = radius
	}

	private fun stateTint(on: Int, off: Int) = ColorStateList(
		arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
		intArrayOf(on, off),
	)
}

package org.jellyfin.androidtv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.AppTheme
import org.koin.java.KoinJavaComponent

/**
 * Colours and shapes for every NOVA screen. Picked from Settings → Theme; the
 * original NOVA black-and-red look is the default ("Dark") and Cinema Red.
 */
data class NovaPalette(
	/** Buttons, focus borders, highlights. */
	val accent: Color,
	/** Text and icons drawn on top of [accent]. */
	val onAccent: Color = Color.White,
	/** Background of the selected side-bar entry. */
	val accentSoft: Color,
	/** Page background. */
	val bg: Color,
	/** Side bar and deepest shadows. */
	val bgDeep: Color,
	/** Panels and rows. */
	val surface: Color,
	/** Buttons and cards resting on a panel. */
	val surfaceRaised: Color,
	/** Chips, borders, placeholders. */
	val surfaceHigh: Color,
	/** Neutral focused buttons. */
	val surfaceFocus: Color,
	val textPrimary: Color,
	val textSecondary: Color,
	val textMuted: Color,
	val textFaint: Color,
	val movieTag: Color,
	val tvTag: Color,
	// Download status colours.
	val statusDownloaded: Color,
	val statusMissing: Color,
	val statusDownloading: Color,
	val statusUnreleased: Color,
	val statusWaiting: Color,
	/** Multiplies every corner radius; Seerr is noticeably rounder. */
	val cornerScale: Float = 1f,
) {
	fun corner(dp: Int): Dp = (dp * cornerScale).dp
	fun corner(dp: Float): Dp = (dp * cornerScale).dp
}

/** Shorthand used by NOVA screens: `nova.accent`, `nova.bg` … */
val nova: NovaPalette get() = NovaTheme.palette

/** `nova.bg.withAlpha(0xE8)` – the same colour with a 0–255 alpha. */
fun Color.withAlpha(alpha: Int): Color = copy(alpha = alpha / 255f)

object NovaTheme {
	// Radarr's colours, which the default NOVA look has always used.
	private val radarrDownloaded = Color(0xFF27C24C)
	private val radarrMissing = Color(0xFFF05050)
	private val radarrDownloading = Color(0xFF7A43B6)
	private val radarrUnreleased = Color(0xFF5D9CEC)
	private val radarrWaiting = Color(0xFFFFA500)

	private fun dark(
		accent: Color,
		bg: Color,
		bgDeep: Color,
		surface: Color,
		accentSoft: Color,
		cornerScale: Float = 1f,
		onAccent: Color = Color.White,
		surfaceRaised: Color = Color(0xFF242424),
		surfaceHigh: Color = Color(0xFF333333),
	) = NovaPalette(
		accent = accent,
		onAccent = onAccent,
		accentSoft = accentSoft,
		bg = bg,
		bgDeep = bgDeep,
		surface = surface,
		surfaceRaised = surfaceRaised,
		surfaceHigh = surfaceHigh,
		surfaceFocus = Color(0xFF555555),
		textPrimary = Color(0xFFE6E6E6),
		textSecondary = Color(0xFFB8B8B8),
		textMuted = Color(0xFF999999),
		textFaint = Color(0xFF777777),
		movieTag = Color(0xFFE50914),
		tvTag = Color(0xFF2F6FDE),
		statusDownloaded = radarrDownloaded,
		statusMissing = radarrMissing,
		statusDownloading = radarrDownloading,
		statusUnreleased = radarrUnreleased,
		statusWaiting = radarrWaiting,
		cornerScale = cornerScale,
	)

	/** The original NOVA look. */
	val Nova = dark(
		accent = Color(0xFFE50914),
		bg = Color(0xFF070707),
		bgDeep = Color(0xFF050505),
		surface = Color(0xFF121212),
		accentSoft = Color(0x332B0A0D),
	)

	/** Seerr / Jellyseerr: slate-blue backgrounds, indigo accents, rounder cards. */
	val Seerr = NovaPalette(
		accent = Color(0xFF6366F1),
		onAccent = Color.White,
		accentSoft = Color(0x334F46E5),
		bg = Color(0xFF111827),
		bgDeep = Color(0xFF0B1120),
		surface = Color(0xFF1F2937),
		surfaceRaised = Color(0xFF283548),
		surfaceHigh = Color(0xFF374151),
		surfaceFocus = Color(0xFF4B5563),
		textPrimary = Color(0xFFF3F4F6),
		textSecondary = Color(0xFFD1D5DB),
		textMuted = Color(0xFF9CA3AF),
		textFaint = Color(0xFF6B7280),
		movieTag = Color(0xFF2563EB),
		tvTag = Color(0xFF9333EA),
		statusDownloaded = Color(0xFF22C55E),
		statusMissing = Color(0xFFEF4444),
		statusDownloading = Color(0xFF6366F1),
		statusUnreleased = Color(0xFF3B82F6),
		statusWaiting = Color(0xFFEAB308),
		cornerScale = 2f,
	)

	/** Cinema Red: pure black, deep crimson, sharp square corners like a cinema screen. */
	val CinemaRed = dark(
		accent = Color(0xFFB0060F),
		bg = Color(0xFF1A0A0C),
		bgDeep = Color(0xFF120608),
		surface = Color(0xFF2A1013),
		accentSoft = Color(0x40B0060F),
		cornerScale = 0.4f,
		surfaceRaised = Color(0xFF35161A),
		surfaceHigh = Color(0xFF4A2026),
	)

	/** Every theme in Settings → Customization → Theme has its own palette. */
	fun paletteFor(theme: AppTheme): NovaPalette = when (theme) {
		// The original NOVA look stays the default.
		AppTheme.DARK -> Nova
		AppTheme.CINEMA_RED -> CinemaRed
		AppTheme.SEERR -> Seerr
		AppTheme.EMERALD -> dark(
			accent = Color(0xFF52B54B),
			bg = Color(0xFF0B1F14),
			bgDeep = Color(0xFF07150D),
			surface = Color(0xFF13301F),
			accentSoft = Color(0x3352B54B),
			cornerScale = 1.2f,
			surfaceRaised = Color(0xFF1A3D28),
			surfaceHigh = Color(0xFF255236),
		)
		AppTheme.MUTED_PURPLE -> dark(
			accent = Color(0xFF627EC8),
			bg = Color(0xFF161428),
			bgDeep = Color(0xFF0F0E1E),
			surface = Color(0xFF221F3D),
			accentSoft = Color(0x33627EC8),
			cornerScale = 1.5f,
			surfaceRaised = Color(0xFF2B2850),
			surfaceHigh = Color(0xFF3A3666),
		)
		AppTheme.MIDNIGHT -> dark(
			accent = Color(0xFF5DADE2),
			bg = Color(0xFF0B1A2B),
			bgDeep = Color(0xFF07121F),
			surface = Color(0xFF13263D),
			accentSoft = Color(0x335DADE2),
			cornerScale = 1.4f,
			onAccent = Color(0xFF04121C),
			surfaceRaised = Color(0xFF1A314D),
			surfaceHigh = Color(0xFF254263),
		)
		AppTheme.AMBER -> dark(
			accent = Color(0xFFFFB300),
			bg = Color(0xFF1E1709),
			bgDeep = Color(0xFF150F05),
			surface = Color(0xFF2C2210),
			accentSoft = Color(0x33FFB300),
			// Gold is light, so text on it is dark to stay readable.
			onAccent = Color(0xFF1A1200),
			surfaceRaised = Color(0xFF382C15),
			surfaceHigh = Color(0xFF4D3D1E),
		)
	}

	@Volatile
	private var current: NovaPalette? = null

	/** The palette for the chosen theme (updated whenever a screen applies the theme). */
	val palette: NovaPalette
		get() = current ?: runCatching {
			paletteFor(KoinJavaComponent.get<UserPreferences>(UserPreferences::class.java)[UserPreferences.appTheme])
		}.getOrDefault(Nova).also { current = it }

	/** Called when the theme is applied to a screen. */
	fun update(theme: AppTheme) {
		current = paletteFor(theme)
	}

	/** For Java views (poster focus border). */
	@JvmStatic
	fun accentArgb(): Int = palette.accent.toArgb()
}

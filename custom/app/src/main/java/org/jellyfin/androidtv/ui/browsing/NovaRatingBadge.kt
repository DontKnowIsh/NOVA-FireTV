package org.jellyfin.androidtv.ui.browsing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.nova

/**
 * NOVA age rating badge: the rating text in an outline box with a coloured edge
 * that shows the age band at a glance (green = all ages … red = adults only).
 * NOVA's own design; the rating text is shown exactly as Jellyfin has it.
 */
@Composable
fun NovaRatingBadge(rating: String, modifier: Modifier = Modifier, small: Boolean = false) {
	val shape = RoundedCornerShape(4.dp)
	Row(
		modifier = modifier
			.height(if (small) 20.dp else 22.dp)
			.clip(shape)
			.border(1.dp, Color(0xFF3A3A42), shape),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(modifier = Modifier.width(if (small) 4.dp else 5.dp).fillMaxHeight().background(ratingColour(rating)))
		Text(
			displayRating(rating),
			color = nova.textPrimary,
			fontSize = if (small) 11.sp else 12.sp,
			fontWeight = FontWeight.Bold,
			modifier = Modifier.padding(horizontal = if (small) 7.dp else 8.dp),
		)
	}
}

private val Green = Color(0xFF2E9E4F)
private val Yellow = Color(0xFFE8B81E)
private val Orange = Color(0xFFEF7D22)
private val Pink = Color(0xFFE0457B)
private val Red = Color(0xFFD42020)
private val Grey = Color(0xFF7A7A82)

/** Age band colour for UK, US, Indian and other common ratings. */
fun ratingColour(raw: String): Color {
	val full = raw.trim().uppercase().replace(" ", "")
	bandOf(full)?.let { return it }
	// Country prefixes like "GB-15", "IN-UA", "DE-16" (but not "TV-MA" or "PG-13").
	val withoutCountry = Regex("^[A-Z]{2}-").replace(full, "")
	if (withoutCountry != full && !full.startsWith("TV-") && !full.startsWith("PG-") && !full.startsWith("NC-")) {
		bandOf(withoutCountry)?.let { return it }
	}
	return Grey
}

private fun bandOf(r: String): Color? = when (r) {
	"U", "G", "TV-Y", "TV-G", "TV-Y7", "ALL", "0", "E", "UC" -> Green
	"PG", "TV-PG" -> Yellow
	"12", "12A", "PG-13", "PG13", "UA", "U/A", "UA7+", "UA13+", "TV-14" -> Orange
	"15", "R", "UA16+", "MA15+", "M" -> Pink
	"18", "R18", "NC-17", "NC17", "A", "TV-MA", "X" -> Red
	else -> when {
		r.startsWith("UA") || r.startsWith("U/A") -> Orange
		r.trimEnd('+').toIntOrNull() != null -> when (r.trimEnd('+').toInt()) {
			in 0..5 -> Green
			in 6..10 -> Yellow
			in 11..14 -> Orange
			in 15..16 -> Pink
			else -> Red
		}
		else -> null
	}
}

/** "2h 50m" for films and episodes, "3 seasons" for shows, "10 episodes" for a season. */
fun novaLengthText(item: org.jellyfin.sdk.model.api.BaseItemDto?): String? {
	item ?: return null
	return when (item.type) {
		org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> item.childCount?.takeIf { it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" }
		org.jellyfin.sdk.model.api.BaseItemKind.SEASON -> item.childCount?.takeIf { it > 0 }?.let { if (it == 1) "1 episode" else "$it episodes" }
		else -> item.runTimeTicks?.takeIf { it > 0 }?.let { ticks ->
			val minutes = (ticks / 600_000_000L).toInt()
			when {
				minutes <= 0 -> null
				minutes < 60 -> "${minutes}m"
				minutes % 60 == 0 -> "${minutes / 60}h"
				else -> "${minutes / 60}h ${minutes % 60}m"
			}
		}
	}
}

/** "• 2h 50m" after the year / rating in NOVA's headers (nothing when unknown). */
@Composable
fun NovaLengthInfo(item: org.jellyfin.sdk.model.api.BaseItemDto?) {
	val text = novaLengthText(item) ?: return
	androidx.compose.foundation.layout.Spacer(Modifier.width(9.dp))
	Text("•", color = nova.textMuted, fontSize = 13.sp)
	androidx.compose.foundation.layout.Spacer(Modifier.width(9.dp))
	Text(text, color = nova.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
}

/** True for ratings the kids lock protects: 15 / R and above (pink and red badges). */
fun isAdultRating(raw: String?): Boolean {
	if (raw.isNullOrBlank()) return false
	val colour = ratingColour(raw)
	return colour == Pink || colour == Red
}

/** "GB-15" → "15", "IN-UA" → "UA" (keeps "PG-13", "TV-MA", "NC-17" as they are). */
fun displayRating(raw: String): String {
	val trimmed = raw.trim()
	val upper = trimmed.uppercase()
	if (upper.startsWith("TV-") || upper.startsWith("PG-") || upper.startsWith("NC-")) return trimmed
	return Regex("^[A-Za-z]{2}-").replace(trimmed, "")
}

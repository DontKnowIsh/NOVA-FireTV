package org.jellyfin.androidtv.ui.browsing

import org.jellyfin.androidtv.ui.nova
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject

/**
 * Cast as a scrollable row of photo cards (photo, name, character).
 * Uses Jellyfin's people first; when Jellyfin has none (common for JellyBridge
 * Discover titles) it falls back to the TMDB cast via Seerr.
 */
@Composable
fun NovaCastRow(item: BaseItemDto, modifier: Modifier = Modifier) {
	val api = koinInject<ApiClient>()

	val jellyfinCast = remember(item.id, item.people) {
		item.people.orEmpty()
			.filter { !it.name.isNullOrBlank() }
			.sortedBy { if (it.role.isNullOrBlank()) 1 else 0 }
			.take(20)
			.map { person ->
				NovaSeerr.CastMember(
					name = person.name.orEmpty(),
					role = person.role.orEmpty(),
					jellyfinId = person.id,
					imageUrl = person.primaryImageTag?.let { tag ->
						runCatching {
							api.imageApi.getItemImageUrl(
								itemId = person.id,
								imageType = ImageType.PRIMARY,
								tag = tag,
								maxHeight = 240,
							)
						}.getOrNull()
					},
				)
			}
	}

	var fallbackCast by remember(item.id) { mutableStateOf<List<NovaSeerr.CastMember>?>(null) }
	LaunchedEffect(item.id, jellyfinCast.isEmpty()) {
		if (jellyfinCast.isNotEmpty()) return@LaunchedEffect
		val full = NovaSeerr.fullItem(api, item)
		val config = NovaSeerr.config(api)
		val type = NovaSeerr.mediaType(full)
		val tmdb = NovaSeerr.tmdbId(full)
		fallbackCast = if (config != null && type != null && tmdb != null) {
			NovaSeerr.cast(config, type, tmdb)
		} else emptyList()
	}

	val cast = jellyfinCast.ifEmpty { fallbackCast.orEmpty() }

	// OK on a cast member: their other films and shows.
	var openPerson by remember(item.id) { mutableStateOf<NovaSeerr.CastMember?>(null) }
	openPerson?.let { member -> NovaPersonDialog(member, onDismiss = { openPerson = null }) }

	Column(modifier = modifier) {
		Text("Cast", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
		Spacer(Modifier.height(8.dp))
		when {
			cast.isNotEmpty() -> LazyRow(
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				contentPadding = PaddingValues(end = 24.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				itemsIndexed(cast, key = { index, member -> "$index-${member.name}" }) { _, member ->
					NovaCastCard(member, onClick = { openPerson = member })
				}
			}
			jellyfinCast.isEmpty() && fallbackCast == null -> Text(
				"Loading cast…",
				color = nova.textMuted,
				fontSize = 13.sp,
			)
			else -> Text(
				"Cast information is not available for this title.",
				color = nova.textMuted,
				fontSize = 13.sp,
			)
		}
	}
}

@Composable
private fun NovaCastCard(member: NovaSeerr.CastMember, onClick: () -> Unit) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(nova.corner(8))

	// Focusable so the remote can scroll along the row.
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = Modifier
			.width(96.dp)
			// Same height for every card (one- or two-line names), so the row
			// doesn't jump up and down as you scroll along it.
			.height(150.dp)
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
			.then(
				if (focused) Modifier.border(2.dp, nova.accent, shape).background(nova.accentSoft, shape)
				else Modifier
			)
			.padding(vertical = 6.dp, horizontal = 4.dp),
	) {
		Box(
			contentAlignment = Alignment.Center,
			modifier = Modifier
				.size(76.dp)
				.clip(CircleShape)
				.background(nova.surfaceRaised),
		) {
			// Initials sit underneath; the photo covers them once it has loaded.
			Text(
				initials(member.name),
				color = nova.textSecondary,
				fontSize = 20.sp,
				fontWeight = FontWeight.Bold,
			)
			if (member.imageUrl != null) {
				Image(
					painter = rememberAsyncImagePainter(member.imageUrl),
					contentDescription = member.name,
					contentScale = ContentScale.Crop,
					modifier = Modifier.size(76.dp).clip(CircleShape),
				)
			}
		}
		Spacer(Modifier.height(6.dp))
		Text(
			member.name,
			color = Color.White,
			fontSize = 12.sp,
			fontWeight = FontWeight.Bold,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			textAlign = TextAlign.Center,
		)
		if (member.role.isNotBlank()) {
			Text(
				member.role,
				color = nova.textMuted,
				fontSize = 11.sp,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				textAlign = TextAlign.Center,
			)
		}
	}
}

private fun initials(name: String): String = name
	.split(' ')
	.filter { it.isNotBlank() }
	.take(2)
	.joinToString("") { it.first().uppercase() }

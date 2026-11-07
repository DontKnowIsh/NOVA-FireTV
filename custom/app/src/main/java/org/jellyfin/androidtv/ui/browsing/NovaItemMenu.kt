package org.jellyfin.androidtv.ui.browsing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.compose.koinInject

/**
 * NOVA long-press menu for a poster: More info, favourite, and (for films
 * and shows you have) Delete from server, protected by the delete PIN.
 *
 * @param onRemoved called after the title was deleted, so the page can drop the poster.
 */
@Composable
fun NovaItemMenu(item: BaseItemDto, onDismiss: () -> Unit, onRemoved: () -> Unit) {
	val api = koinInject<ApiClient>()
	val navigationRepository = koinInject<NavigationRepository>()
	val itemMutationRepository = koinInject<ItemMutationRepository>()
	val scope = rememberCoroutineScope()
	var full by remember(item.id) { mutableStateOf(item) }
	var isDiscover by remember(item.id) { mutableStateOf<Boolean?>(null) }
	var askPin by remember(item.id) { mutableStateOf(false) }
	var working by remember(item.id) { mutableStateOf<String?>(null) }
	var message by remember(item.id) { mutableStateOf<String?>(null) }
	val firstFocus = remember { FocusRequester() }

	LaunchedEffect(item.id) {
		full = runCatching { NovaSeerr.fullItem(api, item) }.getOrDefault(item)
		isDiscover = NovaSeerr.isDiscoverItem(api, item)
		delay(50L)
		runCatching { firstFocus.requestFocus() }
	}

	val favourite = full.userData?.isFavorite == true
	val canDelete = isDiscover == false &&
		(full.type == BaseItemKind.MOVIE || full.type == BaseItemKind.SERIES || full.type == BaseItemKind.EPISODE)

	Dialog(onDismissRequest = onDismiss) {
		Column(
			modifier = Modifier
				.width(400.dp)
				.background(nova.surface, RoundedCornerShape(nova.corner(12)))
				.border(1.dp, nova.surfaceHigh, RoundedCornerShape(nova.corner(12)))
				.padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			NovaTypeTag(full)
			Text(full.name.orEmpty(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
			Text(message ?: working ?: "What would you like to do?", color = if (message != null) Color(0xFFFFC266) else nova.textMuted, fontSize = 13.sp)
			Spacer(Modifier.height(6.dp))

			MenuButton("More info", Modifier.focusRequester(firstFocus)) {
				onDismiss()
				navigationRepository.navigate(Destinations.novaInfo(full))
			}
			if (isDiscover == false) {
				MenuButton(if (favourite) "♥  Remove from favourites" else "♡  Add to favourites") {
					scope.launch {
						runCatching { itemMutationRepository.setFavorite(full.id, !favourite) }
							.onSuccess { data -> full = full.copy(userData = data) }
					}
				}
			}
			if (canDelete) {
				MenuButton("🗑  Delete from server", danger = true) {
					message = null
					askPin = true
				}
			}
			MenuButton("Cancel") { onDismiss() }
		}
	}

	if (askPin) {
		val what = when (full.type) {
			BaseItemKind.SERIES -> "the whole show"
			BaseItemKind.EPISODE -> "this episode"
			else -> "this film"
		}
		NovaPinDialog(
			title = "Delete ${full.name.orEmpty()}?",
			subtitle = "This deletes $what and its files from the server. Enter your PIN.",
			onDismiss = { askPin = false },
			onCorrect = {
				askPin = false
				scope.launch {
					working = "Deleting…"
					when (val outcome = NovaLibraryDelete.delete(api, full)) {
						NovaLibraryDelete.Outcome.Deleted -> {
							working = null
							onRemoved()
							onDismiss()
						}
						is NovaLibraryDelete.Outcome.Failed -> {
							working = null
							message = outcome.reason
						}
					}
				}
			},
		)
	}
}

@Composable
private fun MenuButton(label: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	Button(
		onClick = onClick,
		modifier = modifier.fillMaxWidth(),
		interactionSource = interactionSource,
		shape = RoundedCornerShape(nova.corner(8)),
		colors = ButtonDefaults.colors(
			containerColor = nova.surfaceRaised,
			contentColor = nova.textPrimary,
			focusedContainerColor = if (danger) Color(0xFFB71C1C) else nova.accent,
			focusedContentColor = if (danger) Color.White else nova.onAccent,
		),
		contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
	) {
		Text(
			label,
			fontSize = 15.sp,
			fontWeight = FontWeight.Bold,
			color = when {
				focused && danger -> Color.White
				focused -> nova.onAccent
				danger -> Color(0xFFFF8A80)
				else -> nova.textPrimary
			},
		)
	}
}

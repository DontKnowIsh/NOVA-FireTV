package org.jellyfin.androidtv.ui.browsing

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.nova

/** The letters on NOVA's A–Z bar, "#" first for numbers and symbols. */
val NovaLetters: List<Char> = listOf('#') + ('A'..'Z')

/**
 * Horizontal A–Z bar under the hero buttons. OK on a letter jumps the row to
 * titles starting with it. [focusRequester] lands on the last letter used.
 */
@Composable
fun NovaLetterBar(
	focusRequester: FocusRequester,
	onLetter: (Char) -> Unit,
	onUp: () -> Unit,
	onDown: () -> Unit,
	onLeftEdge: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var active by remember { mutableStateOf('A') }
	Row(
		modifier = modifier.height(30.dp),
		horizontalArrangement = Arrangement.spacedBy(2.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		NovaLetters.forEachIndexed { index, letter ->
			val interactionSource = remember { MutableInteractionSource() }
			val focused by interactionSource.collectIsFocusedAsState()
			val base = Modifier
				.width(24.dp)
				.height(28.dp)
			Box(
				modifier = (if (letter == active) base.focusRequester(focusRequester) else base)
					.onPreviewKeyEvent { event ->
						if (event.type != KeyEventType.KeyDown) {
							return@onPreviewKeyEvent event.key in setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter)
						}
						when (event.key) {
							Key.DirectionUp -> { onUp(); true }
							Key.DirectionDown -> { onDown(); true }
							Key.DirectionLeft -> if (index == 0) { onLeftEdge(); true } else false
							Key.DirectionRight -> index == NovaLetters.lastIndex
							Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
								active = letter
								onLetter(letter)
								true
							}
							else -> false
						}
					}
					.background(
						when {
							focused -> nova.accent
							letter == active -> nova.surfaceHigh.copy(alpha = 0.6f)
							else -> Color.Transparent
						},
						RoundedCornerShape(nova.corner(4)),
					)
					.focusable(interactionSource = interactionSource),
				contentAlignment = Alignment.Center,
			) {
				Text(
					letter.toString(),
					color = if (focused) nova.onAccent else nova.textPrimary,
					fontSize = 14.sp,
					fontWeight = FontWeight.Bold,
				)
			}
		}
	}
}

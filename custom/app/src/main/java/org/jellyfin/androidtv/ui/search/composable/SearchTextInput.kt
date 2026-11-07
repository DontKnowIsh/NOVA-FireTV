package org.jellyfin.androidtv.ui.search.composable

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import org.jellyfin.androidtv.ui.withAlpha
import org.jellyfin.androidtv.ui.nova
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text

/**
 * NOVA search box. The text field is always present (the hint is drawn on top of
 * it when empty) and keeps its own cursor, so the remote keyboard stays connected
 * and letters are never typed in front of each other. Back closes the on-screen
 * keyboard first, then leaves the page as normal.
 */
@Composable
fun SearchTextInput(
	query: String,
	onQueryChange: (query: String) -> Unit,
	onQuerySubmit: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val keyboard = LocalSoftwareKeyboardController.current
	val shape = RoundedCornerShape(nova.corner(8))
	val borderColor = if (focused) nova.accent else nova.surfaceHigh
	val textColor = if (focused) Color.White else nova.textPrimary

	// Local value with the cursor, kept at the end when the query changes from outside.
	var fieldValue by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
	LaunchedEffect(query) {
		if (query != fieldValue.text) fieldValue = TextFieldValue(query, TextRange(query.length))
	}

	ProvideTextStyle(
		LocalTextStyle.current.copy(
			color = textColor,
			fontSize = 16.sp,
		)
	) {
		BasicTextField(
			modifier = modifier.onPreviewKeyEvent { event ->
				// Fire TV sends Back here once the keyboard is closed: tidy up and let it through.
				if (event.key == Key.Back && event.type == KeyEventType.KeyDown) keyboard?.hide()
				false
			},
			value = fieldValue,
			singleLine = true,
			interactionSource = interactionSource,
			onValueChange = { value ->
				fieldValue = value
				if (value.text != query) onQueryChange(value.text)
			},
			keyboardActions = KeyboardActions {
				keyboard?.hide()
				onQuerySubmit()
			},
			keyboardOptions = KeyboardOptions.Default.copy(
				keyboardType = KeyboardType.Text,
				imeAction = ImeAction.Search,
				autoCorrectEnabled = false,
				showKeyboardOnFocus = true,
			),
			textStyle = LocalTextStyle.current,
			cursorBrush = SolidColor(nova.accent),
			decorationBox = { innerTextField ->
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier
						.background(nova.surface.withAlpha(0xEE), shape)
						.border(2.dp, borderColor, shape)
						.padding(horizontal = 14.dp, vertical = 12.dp)
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_search),
						contentDescription = null,
					)
					Spacer(Modifier.width(12.dp))
					Box(contentAlignment = Alignment.CenterStart) {
						if (fieldValue.text.isEmpty()) {
							Text(
								text = "Search movies, TV shows, people...",
								color = nova.textFaint,
								fontSize = 16.sp,
							)
						}
						innerTextField()
					}
				}
			}
		)
	}
}

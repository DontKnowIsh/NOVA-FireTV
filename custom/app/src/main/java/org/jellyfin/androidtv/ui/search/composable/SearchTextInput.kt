package org.jellyfin.androidtv.ui.search.composable

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

@Composable
fun SearchTextInput(
	query: String,
	onQueryChange: (query: String) -> Unit,
	onQuerySubmit: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(8.dp)
	val borderColor = if (focused) Color(0xFFE50914) else Color(0xFF3D3D3D)
	val textColor = if (focused) Color.White else Color(0xFFE0E0E0)

	ProvideTextStyle(
		LocalTextStyle.current.copy(
			color = textColor,
			fontSize = 16.sp,
		)
	) {
		BasicTextField(
			modifier = modifier,
			value = query,
			singleLine = true,
			interactionSource = interactionSource,
			onValueChange = onQueryChange,
			keyboardActions = KeyboardActions { onQuerySubmit() },
			keyboardOptions = KeyboardOptions.Default.copy(
				keyboardType = KeyboardType.Text,
				imeAction = ImeAction.Search,
				autoCorrectEnabled = true,
				showKeyboardOnFocus = true,
			),
			textStyle = LocalTextStyle.current,
			cursorBrush = SolidColor(Color(0xFFE50914)),
			decorationBox = { innerTextField ->
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier
						.background(Color(0xEE151515), shape)
						.border(2.dp, borderColor, shape)
						.padding(horizontal = 14.dp, vertical = 12.dp)
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_search),
						contentDescription = null,
					)
					Spacer(Modifier.width(12.dp))
					if (query.isBlank()) {
						Text(
							text = "Search movies, TV shows, people...",
							color = Color(0xFF777777),
							fontSize = 16.sp,
						)
					} else {
						innerTextField()
					}
				}
			}
		)
	}
}

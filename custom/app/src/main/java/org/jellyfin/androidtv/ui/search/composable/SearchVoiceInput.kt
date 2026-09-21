package org.jellyfin.androidtv.ui.search.composable

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.button.IconButton
import org.jellyfin.androidtv.ui.base.button.IconButtonDefaults
import org.jellyfin.androidtv.util.speech.SpeechRecognizerStatus
import org.jellyfin.androidtv.util.speech.rememberSpeechRecognizer

@Composable
fun SearchVoiceInput(
	onQueryChange: (query: String) -> Unit,
	onQuerySubmit: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	val recognizer = rememberSpeechRecognizer(
		onResult = { query ->
			onQueryChange(query)
			onQuerySubmit()
		},
		onPartialResult = onQueryChange,
		onError = { status ->
			val string = when (status) {
				is SpeechRecognizerStatus.Error -> R.string.speech_error_unknown
				is SpeechRecognizerStatus.PermissionDenied -> if (!status.canRequest) R.string.speech_error_no_permission else null
				SpeechRecognizerStatus.Unavailable -> R.string.speech_error_unavailable
				SpeechRecognizerStatus.Idle,
				is SpeechRecognizerStatus.Listening,
				SpeechRecognizerStatus.RequestingPermission -> null
			}
			if (string != null) Toast.makeText(context, context.getString(string), Toast.LENGTH_LONG).show()
		},
	)

	Box(modifier = modifier.wrapContentSize(), contentAlignment = Alignment.Center) {
		if (recognizer.status is SpeechRecognizerStatus.Listening) {
			val infiniteTransition = rememberInfiniteTransition()
			val scale by infiniteTransition.animateFloat(
				initialValue = 1f,
				targetValue = 1.4f,
				animationSpec = infiniteRepeatable(
					animation = tween(durationMillis = 800, easing = LinearEasing),
					repeatMode = RepeatMode.Reverse,
				),
			)
			Box(
				modifier = Modifier
					.matchParentSize()
					.scale(scale)
					.background(Color(0x33E50914), shape = IconButtonDefaults.Shape)
			)
		}

		val colors = IconButtonDefaults.colors().copy(
			containerColor = if (recognizer.status is SpeechRecognizerStatus.Listening) Color(0xFFE50914) else Color(0xEE151515),
			contentColor = Color.White,
			focusedContainerColor = Color(0xFFE50914),
			focusedContentColor = Color.White,
		)

		IconButton(
			onClick = {
				if (recognizer.status is SpeechRecognizerStatus.Listening) recognizer.stopListening()
				else recognizer.startListening()
			},
			enabled = recognizer.status !is SpeechRecognizerStatus.Unavailable &&
				recognizer.status != SpeechRecognizerStatus.PermissionDenied(false),
			colors = colors,
			modifier = Modifier.onFocusChanged {
				if (!it.isFocused && recognizer.status is SpeechRecognizerStatus.Listening) recognizer.stopListening()
			},
		) {
			Icon(
				imageVector = ImageVector.vectorResource(R.drawable.ic_microphone),
				contentDescription = null,
				modifier = Modifier.size(28.dp),
			)
		}
	}
}

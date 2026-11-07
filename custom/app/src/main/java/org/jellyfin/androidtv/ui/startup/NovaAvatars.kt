package org.jellyfin.androidtv.ui.startup

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * "Who's watching" avatars: each profile can pick one of NOVA's own avatars
 * (saved on this Fire TV). Without one, the Jellyfin profile picture (or the
 * plain person icon) is used as before.
 */
object NovaAvatars {
	private const val PREFS = "nova_settings"

	internal class Avatar(val emoji: String, val name: String, val from: Long, val to: Long)

	internal val all = listOf(
		Avatar("🚀", "Rocket", 0xFFE53935, 0xFF8E24AA),
		Avatar("🌌", "Galaxy", 0xFF5E35B1, 0xFF1E88E5),
		Avatar("🌙", "Moon", 0xFF283593, 0xFF0D47A1),
		Avatar("⭐", "Star", 0xFFFFB300, 0xFFF4511E),
		Avatar("☄️", "Comet", 0xFFFF7043, 0xFFD81B60),
		Avatar("👽", "Alien", 0xFF43A047, 0xFF00897B),
		Avatar("🛸", "UFO", 0xFF00ACC1, 0xFF3949AB),
		Avatar("🌍", "Earth", 0xFF1E88E5, 0xFF43A047),
		Avatar("🦁", "Lion", 0xFFFFA000, 0xFFE65100),
		Avatar("🐼", "Panda", 0xFF546E7A, 0xFF263238),
		Avatar("🦊", "Fox", 0xFFFF6D00, 0xFFBF360C),
		Avatar("🐯", "Tiger", 0xFFFFB300, 0xFFD84315),
		Avatar("🐶", "Dog", 0xFF8D6E63, 0xFF4E342E),
		Avatar("🐱", "Cat", 0xFFAB47BC, 0xFF6A1B9A),
		Avatar("🦄", "Unicorn", 0xFFEC407A, 0xFF7E57C2),
		Avatar("🐉", "Dragon", 0xFF2E7D32, 0xFF1B5E20),
	)

	private fun key(userId: UUID) = "avatar|$userId"

	/** The avatar this profile picked, or null for its usual picture. */
	fun get(context: Context, userId: UUID): Int? =
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(key(userId), -1)
			.takeIf { it in all.indices }

	fun set(context: Context, userId: UUID, index: Int?) {
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
			if (index == null) remove(key(userId)) else putInt(key(userId), index)
		}.apply()
	}

	/** The picker: a grid of avatars, plus "Use profile picture" to go back to the usual one. */
	fun pick(context: Context, current: Int?, onPicked: (Int?) -> Unit) {
		val dialog = ComponentDialog(context)
		val view = ComposeView(context).apply {
			setContent {
				AvatarPicker(
					current = current,
					onPick = {
						dialog.dismiss()
						onPicked(it)
					},
				)
			}
		}
		dialog.setContentView(view)
		dialog.window?.apply {
			setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
			setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
		}
		dialog.show()
	}
}

/** One avatar: the picture on a coloured circle. */
@Composable
internal fun NovaAvatarArt(index: Int, modifier: Modifier = Modifier, emojiSize: Dp = 52.dp) {
	val avatar = NovaAvatars.all.getOrNull(index) ?: return
	Box(
		modifier = modifier
			.clip(CircleShape)
			.background(Brush.linearGradient(listOf(Color(avatar.from), Color(avatar.to)))),
		contentAlignment = Alignment.Center,
	) {
		BasicText(
			avatar.emoji,
			style = TextStyle(fontSize = (emojiSize.value).sp, textAlign = TextAlign.Center),
		)
	}
}

@Composable
private fun AvatarPicker(current: Int?, onPick: (Int?) -> Unit) {
	val first = remember { FocusRequester() }
	Column(
		modifier = Modifier
			.background(Color(0xF2101014), RoundedCornerShape(18.dp))
			.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
			.padding(24.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		BasicText(
			"Choose an avatar",
			style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold),
		)
		Spacer(Modifier.height(16.dp))
		NovaAvatars.all.indices.chunked(4).forEach { row ->
			Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 7.dp)) {
				row.forEach { index ->
					PickerItem(
						selected = index == current,
						focus = if (index == (current ?: 0)) first else null,
						onClick = { onPick(index) },
					) { NovaAvatarArt(index, Modifier.fillMaxSize(), emojiSize = 34.dp) }
				}
			}
		}
		Spacer(Modifier.height(14.dp))
		PickerButton("Use profile picture", onClick = { onPick(null) })
	}
	LaunchedEffect(Unit) {
		delay(80L)
		runCatching { first.requestFocus() }
	}
}

@Composable
private fun PickerItem(
	selected: Boolean,
	focus: FocusRequester?,
	onClick: () -> Unit,
	content: @Composable () -> Unit,
) {
	val interaction = remember { MutableInteractionSource() }
	val focused by interaction.collectIsFocusedAsState()
	Box(
		modifier = (if (focus != null) Modifier.focusRequester(focus) else Modifier)
			.size(64.dp)
			.scale(if (focused) 1.12f else 1f)
			.border(
				width = if (focused || selected) 3.dp else 0.dp,
				color = if (focused) Color.White else if (selected) Color(0xFFE53935) else Color.Transparent,
				shape = CircleShape,
			)
			.padding(4.dp)
			.focusable(interactionSource = interaction)
			.clickable(interactionSource = interaction, indication = null, onClick = onClick),
	) { content() }
}

@Composable
private fun PickerButton(label: String, onClick: () -> Unit) {
	val interaction = remember { MutableInteractionSource() }
	val focused by interaction.collectIsFocusedAsState()
	Box(
		modifier = Modifier
			.background(if (focused) Color.White else Color(0x33FFFFFF), RoundedCornerShape(20.dp))
			.padding(horizontal = 20.dp, vertical = 9.dp)
			.focusable(interactionSource = interaction)
			.clickable(interactionSource = interaction, indication = null, onClick = onClick),
	) {
		BasicText(
			label,
			style = TextStyle(
				color = if (focused) Color(0xFF101014) else Color.White,
				fontSize = 14.sp,
				fontWeight = FontWeight.Bold,
			),
		)
	}
}

package org.jellyfin.androidtv.ui.browsing

import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentActivity
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.nova

/**
 * Settings → Customization → "Change delete PIN" / "Change kids lock PIN": enter
 * the current PIN (or the master code), then the new one twice. The first time,
 * it also asks for an 8-digit master code (for a forgotten PIN); ✕ skips that.
 * "Master code" in Settings opens straight at choosing the master code.
 */
class NovaPinActivity : FragmentActivity() {
	private enum class Step { CURRENT, NEW, CONFIRM, MASTER_CURRENT, MASTER_NEW, MASTER_CONFIRM }

	companion object {
		const val EXTRA_KIND = "nova_pin_kind"
		const val KIND_KIDS = "kids"
		const val KIND_MASTER = "master"
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val kind = intent.getStringExtra(EXTRA_KIND)
		val kids = kind == KIND_KIDS
		val masterOnly = kind == KIND_MASTER
		val name = if (kids) "kids lock PIN" else "delete PIN"
		val current: () -> String = { if (kids) NovaKidsPin.get(this) else NovaDeletePin.get(this) }
		val save: (String) -> Unit = { if (kids) NovaKidsPin.set(this, it) else NovaDeletePin.set(this, it) }
		val firstStep = when {
			!masterOnly -> Step.CURRENT
			NovaMasterCode.isSet(this) -> Step.MASTER_CURRENT
			else -> Step.MASTER_NEW
		}
		setContentView(ComposeView(this).apply {
			setContent {
				var step by remember { mutableStateOf(firstStep) }
				var newPin by remember { mutableStateOf("") }
				var note by remember { mutableStateOf<String?>(null) }

				fun done(message: String) {
					Toast.makeText(this@NovaPinActivity, message, Toast.LENGTH_SHORT).show()
					finish()
				}

				JellyfinTheme {
					Box(modifier = Modifier.fillMaxSize().background(nova.bg), contentAlignment = Alignment.Center) {
						val (title, subtitle) = when (step) {
							Step.CURRENT -> "Change $name" to "Enter your current PIN (it starts as ${NovaDeletePin.DEFAULT})."
							Step.NEW -> "New PIN" to (note ?: "Choose a new ${NovaDeletePin.LENGTH}-digit PIN.")
							Step.CONFIRM -> "Confirm new PIN" to "Enter the new PIN again."
							Step.MASTER_CURRENT -> "Change master code" to "Enter your current master code."
							Step.MASTER_NEW -> "Choose a master code" to (
								note ?: "${NovaMasterCode.LENGTH} digits. It unlocks any PIN if you forget one." +
									if (masterOnly) "" else " ✕ to skip."
								)
							Step.MASTER_CONFIRM -> "Confirm master code" to "Enter the master code again."
						}
						val masterStep = step == Step.MASTER_CURRENT || step == Step.MASTER_NEW || step == Step.MASTER_CONFIRM
						NovaPinPad(
							title = title,
							subtitle = subtitle,
							digits = if (masterStep) NovaMasterCode.LENGTH else NovaDeletePin.LENGTH,
							onEntered = { pin ->
								when (step) {
									Step.CURRENT -> if (pin == current()) {
										step = Step.NEW
										null
									} else "Wrong PIN, try again"
									Step.NEW -> {
										newPin = pin
										note = null
										step = Step.CONFIRM
										null
									}
									Step.CONFIRM -> if (pin == newPin) {
										save(pin)
										if (NovaMasterCode.isSet(this@NovaPinActivity)) {
											done("${name.replaceFirstChar { it.uppercase() }} changed")
										} else {
											// First time: also pick the master code for a forgotten PIN.
											step = Step.MASTER_NEW
										}
										null
									} else {
										note = "They didn't match. Choose the new PIN again."
										step = Step.NEW
										null
									}
									Step.MASTER_CURRENT -> if (NovaMasterCode.matches(this@NovaPinActivity, pin)) {
										step = Step.MASTER_NEW
										null
									} else "Wrong master code"
									Step.MASTER_NEW -> {
										newPin = pin
										note = null
										step = Step.MASTER_CONFIRM
										null
									}
									Step.MASTER_CONFIRM -> if (pin == newPin) {
										NovaMasterCode.set(this@NovaPinActivity, pin)
										done(if (masterOnly) "Master code saved" else "PIN and master code saved")
										null
									} else {
										note = "They didn't match. Choose the master code again."
										step = Step.MASTER_NEW
										null
									}
								}
							},
							onCancel = {
								if (step == Step.MASTER_NEW && !masterOnly) {
									done("${name.replaceFirstChar { it.uppercase() }} changed (no master code)")
								} else {
									finish()
								}
							},
							// Forgot the PIN: the master code lets you set a new one.
							onMaster = if (step == Step.CURRENT) ({ step = Step.NEW }) else null,
						)
					}
				}
			}
		})
	}
}

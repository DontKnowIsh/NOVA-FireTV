package org.jellyfin.androidtv.ui.browsing

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.NovaTheme
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.iptv.NovaIptvActivity
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType

fun Fragment.setupNovaSidebar(
	container: LinearLayout,
	currentFolder: BaseItemDto,
	userViewsRepository: UserViewsRepository,
	itemLauncher: ItemLauncher,
	navigationRepository: NavigationRepository,
) {
	viewLifecycleOwner.lifecycleScope.launch {
		viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
			userViewsRepository.views.collectLatest { views ->
				container.removeAllViews()

				val orderedViews = views.toMutableList().let { remaining ->
					fun takeByName(name: String) = remaining
						.firstOrNull { it.name.equals(name, ignoreCase = true) }
						?.also { remaining.remove(it) }

					fun takeByType(type: CollectionType) = remaining
						.firstOrNull { view -> view.collectionType == type }
						?.also { remaining.remove(it) }

					// Collections, then films, TV and music libraries (in the server's order), then the rest.
					buildList {
						takeByName("Collections")?.let(::add)
						listOf(CollectionType.MOVIES, CollectionType.TVSHOWS, CollectionType.MUSIC).forEach { type ->
							while (true) add(takeByType(type) ?: break)
						}
						addAll(remaining)
					}
				}

				fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

				fun addLabel(text: String, top: Int = 0, bottom: Int = 0) {
					container.addView(TextView(requireContext()).apply {
						this.text = text
						setTextColor(Color.rgb(120, 120, 120))
						textSize = 10f
						typeface = Typeface.DEFAULT_BOLD
						isFocusable = false
						setPadding(dp(8), dp(top), dp(4), dp(bottom))
					})
				}

				fun addButton(
					text: String,
					active: Boolean = false,
					onClick: () -> Unit,
				) {
					val button = TextView(requireContext()).apply {
						this.text = text
						setTextColor(Color.rgb(210, 210, 210))
						textSize = 14f
						typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
						isFocusable = true
						isClickable = true
						setPadding(dp(12), dp(9), dp(8), dp(9))

						fun updateBackground(focused: Boolean) {
							background = GradientDrawable().apply {
								cornerRadius = dp(7).toFloat()
								setColor(
									when {
										focused -> NovaTheme.accentArgb()
										active -> NovaTheme.palette.accentSoft.toArgb()
										else -> Color.TRANSPARENT
									}
								)
								if (focused) {
									setStroke(dp(2), Color.WHITE)
								}
							}
							setTextColor(Color.WHITE)
						}

						updateBackground(false)
						setOnFocusChangeListener { _, hasFocus -> updateBackground(hasFocus) }
						setOnClickListener { onClick() }
						setOnKeyListener { view, keyCode, event ->
							if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
								view.focusSearch(View.FOCUS_RIGHT)?.requestFocus() == true
							} else {
								false
							}
						}
					}

					container.addView(
						button,
						LinearLayout.LayoutParams(
							LinearLayout.LayoutParams.MATCH_PARENT,
							LinearLayout.LayoutParams.WRAP_CONTENT,
						).apply {
							bottomMargin = dp(3)
						}
					)

					if (active) container.tag = button
				}

				container.addView(ImageView(requireContext()).apply {
					setImageResource(R.drawable.nova_n_mark)
					scaleType = ImageView.ScaleType.FIT_CENTER
					contentDescription = "NOVA"
					isFocusable = false
				}, LinearLayout.LayoutParams(dp(52), dp(52)).apply {
					bottomMargin = dp(8)
				})

				addButton("Home") {
					navigationRepository.navigate(Destinations.home)
				}
				addButton("Search") {
					navigationRepository.navigate(Destinations.search())
				}
				addButton("IPTV") {
					requireContext().startActivity(Intent(requireContext(), NovaIptvActivity::class.java))
				}

				addLabel("MY LIBRARIES", top = 12, bottom = 6)

				orderedViews.forEach { view ->
					addButton(
						text = view.name.orEmpty(),
						active = view.id == currentFolder.id,
					) {
						itemLauncher.launchUserView(view)
					}
				}

			}
		}
	}
}

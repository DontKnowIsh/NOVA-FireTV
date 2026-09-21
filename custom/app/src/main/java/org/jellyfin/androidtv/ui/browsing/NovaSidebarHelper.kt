package org.jellyfin.androidtv.ui.browsing

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
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

					fun takeByType(type: CollectionType, excludingName: String? = null) = remaining
						.firstOrNull { view ->
							view.collectionType == type &&
								(excludingName == null || !view.name.equals(excludingName, ignoreCase = true))
						}
						?.also { remaining.remove(it) }

					buildList {
						takeByName("Collections")?.let(::add)
						takeByName("Indian Movies")?.let(::add)
						takeByName("Movies")?.let(::add)
							?: takeByType(CollectionType.MOVIES, "Indian Movies")?.let(::add)
						takeByName("TV Shows")?.let(::add)
							?: takeByType(CollectionType.TVSHOWS)?.let(::add)
						takeByName("Music")?.let(::add)
							?: takeByType(CollectionType.MUSIC)?.let(::add)
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
										focused -> Color.rgb(229, 9, 20)
										active -> Color.rgb(65, 12, 16)
										else -> Color.TRANSPARENT
									}
								)
								if (focused) {
									setStroke(dp(1), Color.argb(180, 255, 255, 255))
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

				container.addView(TextView(requireContext()).apply {
					text = "NOVA"
					setTextColor(Color.rgb(229, 9, 20))
					textSize = 18f
					typeface = Typeface.DEFAULT_BOLD
					letterSpacing = 0.12f
					isFocusable = false
					setPadding(dp(8), dp(2), dp(4), dp(18))
				})

				addButton("Home") {
					navigationRepository.navigate(Destinations.home)
				}
				addButton("Search") {
					navigationRepository.navigate(Destinations.search())
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

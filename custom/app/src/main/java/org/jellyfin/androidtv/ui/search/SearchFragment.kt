package org.jellyfin.androidtv.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.content
import androidx.leanback.app.RowsSupportFragment
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.search.composable.SearchTextInput
import org.jellyfin.androidtv.ui.search.composable.SearchVoiceInput
import org.jellyfin.androidtv.util.speech.rememberSpeechRecognizerAvailability
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
private fun NovaSearchRailButton(
	label: String,
	iconRes: Int,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	active: Boolean = false,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(8.dp)
	val decoratedModifier = if (focused) {
		modifier.border(1.dp, Color(0x99FF5C66), shape)
	} else {
		modifier
	}

	Button(
		onClick = onClick,
		modifier = decoratedModifier,
		interactionSource = interactionSource,
		shape = shape,
		colors = ButtonDefaults.colors(
			containerColor = if (active) Color(0x332B0A0D) else Color.Transparent,
			contentColor = if (active) Color.White else Color(0xFFB8B8B8),
			focusedContainerColor = Color(0xFFE50914),
			focusedContentColor = Color.White,
		),
		contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
		) {
			if (active && !focused) {
				androidx.compose.foundation.layout.Box(
					modifier = Modifier
						.width(3.dp)
						.height(18.dp)
						.background(Color(0xFFE50914), RoundedCornerShape(3.dp))
				)
				Spacer(Modifier.width(7.dp))
			}

			Icon(
				painter = painterResource(iconRes),
				contentDescription = null,
				modifier = Modifier.size(19.dp),
			)
			Spacer(Modifier.width(11.dp))
			Text(
				text = label,
				fontSize = 13.sp,
				fontWeight = if (focused || active) FontWeight.Bold else FontWeight.Medium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

class SearchFragment : Fragment() {
	companion object {
		const val EXTRA_QUERY = "query"
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		JellyfinTheme {
			val viewModel = koinViewModel<SearchViewModel>()
			val searchFragmentDelegate = koinInject<SearchFragmentDelegate> { parametersOf(requireContext()) }
			val navigationRepository = koinInject<NavigationRepository>()
			var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
			val textInputFocusRequester = remember { FocusRequester() }
			val resultFocusRequester = remember { FocusRequester() }
			val railFocusRequester = remember { FocusRequester() }
			val speechRecognizerAvailability = rememberSpeechRecognizerAvailability()

			LaunchedEffect(Unit) {
				val extraQuery = arguments?.getString(EXTRA_QUERY)
				if (!extraQuery.isNullOrBlank()) {
					query = query.copy(text = extraQuery)
					viewModel.searchImmediately(extraQuery)
					resultFocusRequester.requestFocus()
				} else {
					textInputFocusRequester.requestFocus()
				}

				viewModel.searchResultsFlow.collect { results ->
					searchFragmentDelegate.showResults(results)
				}
			}

			Row(
				modifier = Modifier
					.fillMaxSize()
					.background(Color(0xFF050505))
			) {
				Column(
					modifier = Modifier
						.width(188.dp)
						.fillMaxHeight()
						.background(Color(0xFC050505))
						.padding(start = 16.dp, end = 14.dp, top = 20.dp, bottom = 12.dp)
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(start = 7.dp, end = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Icon(
							painter = painterResource(R.drawable.nova_mark),
							contentDescription = "NOVA",
							modifier = Modifier.size(34.dp),
							tint = Color.Unspecified,
						)
						Spacer(Modifier.width(8.dp))
						Column {
							Text(
								text = "NOVA",
								color = Color.White,
								fontSize = 20.sp,
								fontWeight = FontWeight.Black,
								letterSpacing = 2.2.sp,
							)
							Text(
								text = "YOUR MEDIA",
								color = Color(0xFF777777),
								fontSize = 8.sp,
								fontWeight = FontWeight.Bold,
								letterSpacing = 1.2.sp,
							)
						}
					}

					Spacer(Modifier.height(22.dp))

					NovaSearchRailButton(
						label = "Home",
						iconRes = R.drawable.ic_house,
						onClick = { navigationRepository.navigate(Destinations.home) },
						modifier = Modifier
							.fillMaxWidth()
							.focusProperties { right = textInputFocusRequester },
					)

					Spacer(Modifier.height(6.dp))

					NovaSearchRailButton(
						label = "Search",
						iconRes = R.drawable.ic_search,
						onClick = { textInputFocusRequester.requestFocus() },
						active = true,
						modifier = Modifier
							.fillMaxWidth()
							.focusRequester(railFocusRequester)
							.focusProperties { right = textInputFocusRequester },
					)
				}

				Column(
					modifier = Modifier
						.fillMaxSize()
						.background(Color(0xFF080808))
				) {
					Column(
						modifier = Modifier.padding(start = 30.dp, end = 36.dp, top = 24.dp, bottom = 12.dp)
					) {
						Text(
							text = "SEARCH",
							color = Color(0xFFE50914),
							fontSize = 11.sp,
							fontWeight = FontWeight.Bold,
							letterSpacing = 1.6.sp,
						)
						Spacer(Modifier.height(5.dp))
						Text(
							text = "Find something to watch",
							color = Color.White,
							fontSize = 30.sp,
							fontWeight = FontWeight.Bold,
						)
						Spacer(Modifier.height(5.dp))
						Text(
							text = "Search across your movies, TV shows and media library.",
							color = Color(0xFF999999),
							fontSize = 13.sp,
						)
						Spacer(Modifier.height(18.dp))

						Row(
							verticalAlignment = Alignment.CenterVertically,
							modifier = Modifier
								.focusRestorer()
								.focusGroup()
						) {
							if (speechRecognizerAvailability) {
								SearchVoiceInput(
									onQueryChange = { query = query.copy(text = it) },
									onQuerySubmit = {
										viewModel.searchImmediately(query.text)
										resultFocusRequester.requestFocus()
									}
								)
								Spacer(Modifier.width(12.dp))
							}

							SearchTextInput(
								query = query.text,
								onQueryChange = {
									query = query.copy(text = it)
									viewModel.searchDebounced(query.text)
								},
								onQuerySubmit = {
									viewModel.searchImmediately(query.text)
									resultFocusRequester.requestFocus()
								},
								modifier = Modifier
									.weight(1f)
									.focusRequester(textInputFocusRequester)
									.focusProperties { left = railFocusRequester },
							)
						}
					}

					var rowsSupportFragment by remember { mutableStateOf<RowsSupportFragment?>(null) }

					AndroidFragment<RowsSupportFragment>(
						modifier = Modifier
							.focusGroup()
							.focusRequester(resultFocusRequester)
							.focusProperties {
								left = railFocusRequester
								up = textInputFocusRequester
								onExit = {
									val isFirstRowSelected = rowsSupportFragment?.selectedPosition?.let { it <= 0 } ?: false
									when {
										requestedFocusDirection == FocusDirection.Left -> Unit
										requestedFocusDirection != FocusDirection.Up || !isFirstRowSelected -> cancelFocusChange()
										else -> {
											rowsSupportFragment?.selectedPosition = 0
											rowsSupportFragment?.verticalGridView?.clearFocus()
										}
									}
								}
							}
							.padding(top = 2.dp)
							.fillMaxSize(),
						onUpdate = { fragment ->
							rowsSupportFragment = fragment
							fragment.adapter = searchFragmentDelegate.rowsAdapter
							fragment.onItemViewClickedListener = searchFragmentDelegate.onItemViewClickedListener
							fragment.onItemViewSelectedListener = searchFragmentDelegate.onItemViewSelectedListener
						}
					)
				}
			}
		}
	}
}

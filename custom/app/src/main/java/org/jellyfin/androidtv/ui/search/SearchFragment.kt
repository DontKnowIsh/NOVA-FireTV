package org.jellyfin.androidtv.ui.search

import org.jellyfin.androidtv.ui.nova
import org.jellyfin.androidtv.ui.home.NovaSideRail
import org.jellyfin.androidtv.ui.home.NovaRailPage
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.content
import androidx.leanback.app.RowsSupportFragment
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.navigation.NovaRemoteLayout
import org.jellyfin.androidtv.ui.navigation.NovaRowsNavigation
import org.jellyfin.androidtv.ui.search.composable.SearchTextInput
import org.jellyfin.androidtv.ui.search.composable.SearchVoiceInput
import org.jellyfin.androidtv.util.speech.rememberSpeechRecognizerAvailability
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

class SearchFragment : Fragment() {
	companion object {
		const val EXTRA_QUERY = "query"
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View = NovaRemoteLayout(requireContext()).apply {
		val remoteLayout = this
		addView(content {
		JellyfinTheme {
			val viewModel = koinViewModel<SearchViewModel>()
			val searchFragmentDelegate = koinInject<SearchFragmentDelegate> { parametersOf(requireContext()) }
			var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
			val textInputFocusRequester = remember { FocusRequester() }
			var rowsSupportFragment by remember { mutableStateOf<RowsSupportFragment?>(null) }
			var resultNavigation by remember { mutableStateOf<NovaRowsNavigation?>(null) }
			val railFocusRequester = remember { FocusRequester() }
			val speechRecognizerAvailability = rememberSpeechRecognizerAvailability()
			LaunchedEffect(resultNavigation) {
				if (!arguments?.getString(EXTRA_QUERY).isNullOrBlank()) resultNavigation?.requestCardFocus()
			}

			LaunchedEffect(Unit) {
				val extraQuery = arguments?.getString(EXTRA_QUERY)
				if (!extraQuery.isNullOrBlank()) {
					query = query.copy(text = extraQuery)
					viewModel.searchImmediately(extraQuery)
					resultNavigation?.requestCardFocus()
				} else {
					runCatching { textInputFocusRequester.requestFocus() }
				}

				viewModel.searchResultsFlow.collect { results ->
					searchFragmentDelegate.showResults(results)
				}
			}

			Row(
				modifier = Modifier
					.fillMaxSize()
					.background(nova.bgDeep)
			) {
				NovaSideRail(
					current = NovaRailPage.Search,
					focusRequester = railFocusRequester,
					rightFocus = textInputFocusRequester,
				)

				Column(
					modifier = Modifier
						.fillMaxSize()
						.background(nova.bg)
				) {
					Column(
						modifier = Modifier.padding(start = 30.dp, end = 36.dp, top = 24.dp, bottom = 12.dp)
					) {
						Text(
							text = "SEARCH",
							color = nova.accent,
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
							color = nova.textMuted,
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
										resultNavigation?.requestCardFocus()
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
									resultNavigation?.requestCardFocus()
								},
								modifier = Modifier
									.weight(1f)
									.focusRequester(textInputFocusRequester).onPreviewKeyEvent { event ->
										if (event.key == Key.DirectionDown) {
											if (event.type == KeyEventType.KeyDown) resultNavigation?.requestCardFocus()
											true
										} else false
									}
									.focusProperties { left = railFocusRequester },
							)
						}
					}

					AndroidFragment<RowsSupportFragment>(
						modifier = Modifier

							.padding(top = 2.dp)
							.fillMaxSize(),
						onUpdate = { fragment ->
							if (rowsSupportFragment !== fragment) {
								resultNavigation = NovaRowsNavigation(fragment,
									{ remoteLayout.focusCompose { railFocusRequester.requestFocus() } },
									{ remoteLayout.focusCompose { textInputFocusRequester.requestFocus() } })
							}
							rowsSupportFragment = fragment
							val navigation = resultNavigation!!
							remoteLayout.directionHandler = navigation::handle
							fragment.verticalGridView.setOnKeyInterceptListener(navigation::handle)
							fragment.adapter = searchFragmentDelegate.rowsAdapter
							fragment.onItemViewClickedListener = searchFragmentDelegate.onItemViewClickedListener
							fragment.onItemViewSelectedListener = androidx.leanback.widget.OnItemViewSelectedListener { itemHolder, item, rowHolder, row ->
								navigation.onSelection(item, row)
								searchFragmentDelegate.onItemViewSelectedListener.onItemSelected(itemHolder, item, rowHolder, row)
							}
						}
					)
				}
			}
		}
		})
	}
}

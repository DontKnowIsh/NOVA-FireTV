package org.jellyfin.androidtv.ui.browsing

import org.jellyfin.androidtv.ui.withAlpha
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.androidtv.ui.home.NovaSideRail
import org.jellyfin.androidtv.ui.home.NovaRailPage
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.content
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Row as LeanbackRow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.constant.ImageType as CardImageType
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.home.HomeHeroStateStore
import org.jellyfin.androidtv.ui.home.HomeHeroArtwork
import org.jellyfin.androidtv.ui.home.novaHeroImage
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.navigation.NovaRemoteLayout
import org.jellyfin.androidtv.ui.navigation.NovaRowsNavigation
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType as SdkImageType
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

class NovaSeriesFragment : Fragment() {
	internal lateinit var series: BaseItemDto
		private set

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val raw = requireArguments().getString(Extras.Folder)
			?: error("NOVA series screen requires Extras.Folder")
		series = Json.Default.decodeFromString(BaseItemDto.serializer(), raw)
		HomeHeroStateStore.resetToPreview()
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = NovaRemoteLayout(requireContext()).apply {
		val remoteLayout = this
		addView(content {
		val railFocusRequester = remember { FocusRequester() }
		val focusScope = rememberCoroutineScope()
		var rowsFragment by remember { mutableStateOf<NovaSeriesRowsFragment?>(null) }
		var menuItem by remember { mutableStateOf<BaseItemDto?>(null) }

		val hero by HomeHeroStateStore.state.collectAsState()
		val displayItem = hero.item ?: series

		val api = koinInject<ApiClient>()
		val userViewsRepository = koinInject<UserViewsRepository>()
		val itemLauncher = koinInject<ItemLauncher>()
		val userViews by userViewsRepository.views.collectAsState(emptyList())

		val backdrop = remember(displayItem.id) {
			displayItem.novaHeroImage
		}
		val logo = remember(displayItem.id) {
			displayItem.itemImages[SdkImageType.LOGO] ?: displayItem.parentImages[SdkImageType.LOGO]
		}

		LaunchedEffect(series.id) {
			HomeHeroStateStore.update(series)
		}
		LaunchedEffect(rowsFragment) {
			rowsFragment?.view?.post { rowsFragment?.requestCardFocus() }
		}

		JellyfinTheme {
			Box(modifier = Modifier.fillMaxSize().background(nova.bg)) {
				// The show's artwork faintly behind the whole page, so it isn't plain.
				if (backdrop != null) {
					HomeHeroArtwork(
						url = backdrop.getUrl(api, maxWidth = 1280, maxHeight = 720),
						modifier = Modifier.fillMaxSize().alpha(0.35f),
					)
					Box(
						modifier = Modifier.fillMaxSize().background(
							androidx.compose.ui.graphics.Brush.verticalGradient(
								0f to nova.bg.withAlpha(0x55),
								0.45f to nova.bg.withAlpha(0x99),
								1f to nova.bg.withAlpha(0xD8),
							)
						)
					)
				}

				Box(
					modifier = Modifier
						.padding(start = 150.dp)
						.width(54.dp)
						.height(258.dp)
						.background(
							Brush.horizontalGradient(
								0f to nova.bgDeep.withAlpha(0xFC),
								0.30f to nova.bgDeep.withAlpha(0xE8),
								0.58f to nova.bgDeep.withAlpha(0xA8),
								0.80f to nova.bgDeep.withAlpha(0x55),
								1f to Color.Transparent,
							)
						)
				)

				Row(modifier = Modifier.fillMaxSize()) {
					NovaSideRail(
						current = userViews.firstOrNull { it.collectionType == CollectionType.TVSHOWS }
							?.let { NovaRailPage.Library(it.id) } ?: NovaRailPage.None,
						focusRequester = railFocusRequester,
						onRight = { rowsFragment?.requestCardFocus() },
					)

					Column(modifier = Modifier.fillMaxSize().clipToBounds()) {
						Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
							Column(
								modifier = Modifier
									.align(Alignment.CenterStart)
									.fillMaxWidth(0.72f)
									.padding(start = 28.dp, end = 26.dp, top = 22.dp, bottom = 12.dp)
							) {
								if (logo != null) {
									AsyncImage(
										url = logo.getUrl(api, maxWidth = 620, maxHeight = 160),
										blurHash = logo.blurHash,
										scaleType = ImageView.ScaleType.FIT_START,
										modifier = Modifier.fillMaxWidth(0.8f).height(60.dp),
									)
								} else {
									Text(
										displayItem.name.orEmpty(),
										color = Color.White,
										fontSize = 34.sp,
										fontWeight = FontWeight.Bold,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
								}
								Spacer(Modifier.height(9.dp))
								Row(verticalAlignment = Alignment.CenterVertically) {
									displayItem.productionYear?.let {
										Text(it.toString(), color = nova.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
									}
									if (!displayItem.officialRating.isNullOrBlank()) {
										Spacer(Modifier.width(9.dp))
										Text("•", color = nova.textMuted, fontSize = 13.sp)
										Spacer(Modifier.width(9.dp))
										NovaRatingBadge(displayItem.officialRating.orEmpty())
									}
									org.jellyfin.androidtv.ui.browsing.NovaLengthInfo(displayItem)
								}
								Spacer(Modifier.height(10.dp))
								Text(
									displayItem.overview.orEmpty(),
									color = nova.textPrimary,
									fontSize = 15.sp,
									maxLines = 2,
									overflow = TextOverflow.Ellipsis,
									lineHeight = 20.sp,
								)
							}
						}

						AndroidFragment<NovaSeriesRowsFragment>(
							modifier = Modifier

								.fillMaxSize()
								.clipToBounds(),
							onUpdate = { fragment ->
								rowsFragment = fragment
								remoteLayout.directionHandler = fragment::handleNavigation
								remoteLayout.longPressHandler = {
									fragment.focusedItem()?.let { menuItem = it; true } ?: false
								}
								fragment.onNavigateToSidebar = {
									focusScope.launch {
										remoteLayout.focusCompose { railFocusRequester.requestFocus() }
									}
								}
								// No buttons at the top any more: Up / ☰ from the seasons go to the side bar.
								fragment.onNavigateToHero = { remoteLayout.focusCompose { railFocusRequester.requestFocus() } }
								fragment.onNavigateToInfo = { remoteLayout.focusCompose { railFocusRequester.requestFocus() } }
							},
						)
					}
				}
			}
		}
		menuItem?.let { item ->
			NovaItemMenu(
				item = item,
				onDismiss = { menuItem = null },
				onRemoved = {
					rowsFragment?.removeFocusedItem()
					rowsFragment?.view?.post { rowsFragment?.requestCardFocus() }
				},
			)
		}
		})
	}

	override fun onDestroyView() {
		HomeHeroStateStore.resetToPreview()
		super.onDestroyView()
	}
}

class NovaSeriesRowsFragment : RowsSupportFragment() {
	private val itemLauncher by inject<ItemLauncher>()
	private lateinit var series: BaseItemDto
	var onNavigateToSidebar: (() -> Unit)? = null
	var onNavigateToHero: (() -> Unit)? = null
	var onNavigateToInfo: (() -> Unit)? = null

	private val remoteNavigation by lazy {
		NovaRowsNavigation(this, { onNavigateToSidebar?.invoke() },
			{ onNavigateToHero?.invoke() }, { onNavigateToInfo?.invoke() })
	}
	fun requestCardFocus() = remoteNavigation.requestCardFocus()
	fun handleNavigation(event: android.view.KeyEvent) = remoteNavigation.handle(event)

	/** Title on the focused poster (long-press menu). */
	fun focusedItem(): org.jellyfin.sdk.model.api.BaseItemDto? =
		if (view?.hasFocus() != true) null else (remoteNavigation.currentRowItem()?.second as? org.jellyfin.androidtv.ui.itemhandling.BaseRowItem)?.baseItem

	/** Drops the focused poster (after it was deleted). */
	fun removeFocusedItem() {
		val (row, item) = remoteNavigation.currentRowItem() ?: return
		@Suppress("UNCHECKED_CAST")
		(row.adapter as? org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter<Any>)?.remove(item)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		series = (parentFragment as? NovaSeriesFragment)?.series
			?: run {
				val raw = parentFragment?.arguments?.getString(Extras.Folder)
					?: error("NOVA series rows require a series")
				Json.Default.decodeFromString(BaseItemDto.serializer(), raw)
			}

		val rowsAdapter = MutableObjectAdapter<LeanbackRow>(PositionableListRowPresenter())
		adapter = rowsAdapter
		val presenter = CardPresenter(false, CardImageType.POSTER, 190)
		val seasonAdapter = ItemRowAdapter(
			requireContext(),
			BrowsingUtils.createSeasonsRequest(series.id),
			presenter,
			rowsAdapter,
		)
		val row = ListRow(HeaderItem("Seasons"), seasonAdapter)
		rowsAdapter.add(row)
		seasonAdapter.setRow(row)
		seasonAdapter.Retrieve()

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, listRow ->
			if (item !is BaseRowItem || listRow !is ListRow) return@OnItemViewClickedListener
			@Suppress("UNCHECKED_CAST")
			val rowAdapter = listRow.adapter as? MutableObjectAdapter<Any>
				?: return@OnItemViewClickedListener
			itemLauncher.launch(item, rowAdapter, requireContext())
		}

		onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, listRow ->
			if (item !is BaseRowItem || listRow !is ListRow) {
				return@OnItemViewSelectedListener
			}
			remoteNavigation.onSelection(item, listRow)

			HomeHeroStateStore.update(item.baseItem)
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		verticalGridView.setOnKeyInterceptListener(::handleNavigation)
	}

}


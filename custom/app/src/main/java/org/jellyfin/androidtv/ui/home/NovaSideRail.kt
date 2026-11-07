package org.jellyfin.androidtv.ui.home

import org.jellyfin.androidtv.ui.withAlpha
import org.jellyfin.androidtv.ui.nova
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.browsing.NovaBrowseFilters
import org.jellyfin.androidtv.ui.browsing.NovaDownloadDestinations
import org.jellyfin.androidtv.ui.browsing.NovaDownloadsDestinations
import org.jellyfin.androidtv.ui.iptv.NovaIptvActivity
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.CollectionType
import org.koin.compose.koinInject
import java.util.UUID

/** Which side-bar entry is highlighted as "you are here". */
sealed interface NovaRailPage {
	data object Home : NovaRailPage
	data object Favourites : NovaRailPage
	data class Genre(val name: String) : NovaRailPage
	data object Search : NovaRailPage
	data object Download : NovaRailPage
	data object Downloads : NovaRailPage
	data class Library(val id: UUID) : NovaRailPage
	/** Recently Added Movies / TV Shows (value: "movies" / "shows"). */
	data class Recent(val kind: String) : NovaRailPage
	/** Detail pages: nothing highlighted. */
	data object None : NovaRailPage
}

private fun sideRailIcon(name: String, type: CollectionType?): Int = when {
	name.equals("Collections", ignoreCase = true) -> R.drawable.ic_grid
	type == CollectionType.MOVIES -> R.drawable.ic_movie
	type == CollectionType.TVSHOWS -> R.drawable.ic_tv
	type == CollectionType.MUSIC -> R.drawable.ic_music_album
	else -> R.drawable.ic_folder
}

/**
 * The NOVA side bar, identical on every page: Home, Favourites, Categories,
 * Search, Download, Downloads, IPTV and My Libraries.
 *
 * @param focusRequester attached to the highlighted entry (or Home), so pages can
 *   send focus "left" into the bar.
 * @param onRight called when Right is pressed on any entry (pages with Leanback rows).
 * @param rightFocus Compose target for Right, used when [onRight] is null.
 * @param onHome overrides Home (the Home page uses it to reset its preview).
 */
@Composable
fun NovaSideRail(
	current: NovaRailPage,
	focusRequester: FocusRequester,
	modifier: Modifier = Modifier,
	onRight: (() -> Unit)? = null,
	rightFocus: FocusRequester? = null,
	onHome: (() -> Unit)? = null,
) {
	val api = koinInject<ApiClient>()
	val userViewsRepository = koinInject<UserViewsRepository>()
	val navigationRepository = koinInject<NavigationRepository>()
	val itemLauncher = koinInject<ItemLauncher>()
	val context = LocalContext.current
	val userViews by userViewsRepository.views.collectAsState(emptyList())
	// "Download finished" pop-ups work from whichever NOVA page you start on.
	androidx.compose.runtime.LaunchedEffect(Unit) {
		org.jellyfin.androidtv.ui.browsing.NovaDownloadNotifier.ensureStarted(context, api)
	}

	var showGenres by rememberSaveable { mutableStateOf(current is NovaRailPage.Genre) }
	val moviesView = remember(userViews) {
		userViews.firstOrNull { it.name.equals("Movies", ignoreCase = true) }
			?: userViews.firstOrNull { it.collectionType == CollectionType.MOVIES }
	}
	val showsView = remember(userViews) {
		userViews.firstOrNull { it.name.equals("TV Shows", ignoreCase = true) }
			?: userViews.firstOrNull { it.collectionType == CollectionType.TVSHOWS }
	}
	var genres by remember(moviesView?.id) { mutableStateOf<List<String>?>(null) }
	var genresLoading by remember(moviesView?.id) { mutableStateOf(false) }
	var genresFailed by remember(moviesView?.id) { mutableStateOf(false) }

	LaunchedEffect(showGenres, moviesView?.id) {
		val movies = moviesView ?: return@LaunchedEffect
		if (!showGenres || genres != null || genresLoading) return@LaunchedEffect
		genresLoading = true
		genresFailed = false
		try {
			val loaded = runCatching { NovaBrowseFilters.loadMovieGenres(api, movies) }.getOrDefault(emptyList())
			if (loaded.isNotEmpty()) genres = loaded else genresFailed = true
		} finally {
			genresLoading = false
		}
	}

	val orderedViews = remember(userViews) {
		// Discover (JellyBridge) isn't shown any more: browse new titles on Download.
		val remaining = userViews.filterNot { it.name.equals("Discover", ignoreCase = true) }.toMutableList()
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

	val hasCollections = orderedViews.any { it.name.equals("Collections", ignoreCase = true) }

	// The page's focus target sits on the highlighted entry, or on Home when the
	// highlighted entry isn't shown (e.g. a closed category, or a detail page).
	val focusOnHome = when (current) {
		NovaRailPage.Home, NovaRailPage.None -> true
		NovaRailPage.Favourites -> moviesView == null
		is NovaRailPage.Genre -> !showGenres || genres?.contains(current.name) != true
		is NovaRailPage.Library -> orderedViews.none { it.id == current.id }
		is NovaRailPage.Recent -> false
		else -> false
	}

	@Composable
	fun Entry(
		label: String,
		iconRes: Int,
		active: Boolean,
		onClick: () -> Unit,
		indent: Boolean = false,
		holdsFocus: Boolean = active && !focusOnHome,
	) {
		var entryModifier = Modifier.fillMaxWidth()
		if (indent) entryModifier = entryModifier.padding(start = 10.dp)
		if (holdsFocus) entryModifier = entryModifier.focusRequester(focusRequester)
		if (onRight == null && rightFocus != null) entryModifier = entryModifier.focusProperties { right = rightFocus }
		NovaSideRailButton(
			label = label,
			iconRes = iconRes,
			onClick = onClick,
			active = active,
			onRight = onRight,
			modifier = entryModifier,
		)
	}

	// Recently Added Movies / TV Shows: the newest across your libraries.
	@Composable
	fun RecentEntries() {
		val recentMovies = moviesView
		if (recentMovies != null) {
			Entry(
				label = "New Movies",
				iconRes = R.drawable.ic_movie,
				active = current == NovaRailPage.Recent(NovaBrowseFilters.RECENT_MOVIES),
				onClick = {
					navigationRepository.navigate(
						Destinations.novaFilteredLibrary(
							recentMovies,
							NovaBrowseFilters.FILTER_RECENT,
							NovaBrowseFilters.RECENT_MOVIES,
							"Recently Added Movies",
						)
					)
				},
			)
			Spacer(Modifier.height(2.dp))
		}
		if (showsView != null) {
			Entry(
				label = "New TV Shows",
				iconRes = R.drawable.ic_tv,
				active = current == NovaRailPage.Recent(NovaBrowseFilters.RECENT_SHOWS),
				onClick = {
					navigationRepository.navigate(
						Destinations.novaFilteredLibrary(
							showsView,
							NovaBrowseFilters.FILTER_RECENT,
							NovaBrowseFilters.RECENT_SHOWS,
							"Recently Added TV Shows",
						)
					)
				},
			)
			Spacer(Modifier.height(2.dp))
		}
	}

	Column(
		modifier = modifier
			.width(164.dp)
			.fillMaxHeight()
			.background(nova.bgDeep.withAlpha(0xFC))
			.verticalScroll(rememberScrollState())
			.padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 8.dp),
	) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(start = 7.dp, end = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Icon(
				painter = painterResource(R.drawable.nova_n_mark),
				contentDescription = "NOVA",
				modifier = Modifier.size(34.dp),
				tint = Color.Unspecified,
			)
			Spacer(Modifier.width(8.dp))
			Column {
				Text("NOVA", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.2.sp)
				Text("YOUR MEDIA", color = nova.textFaint, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
			}
		}
		Spacer(Modifier.height(12.dp))

		Entry(
			label = "Home",
			iconRes = R.drawable.ic_house,
			active = current == NovaRailPage.Home,
			holdsFocus = focusOnHome,
			onClick = { onHome?.invoke() ?: navigationRepository.navigate(Destinations.home) },
		)

		if (moviesView != null) {
			Spacer(Modifier.height(3.dp))
			Entry(
				label = "Favourites",
				iconRes = R.drawable.ic_star,
				active = current == NovaRailPage.Favourites,
				onClick = {
					navigationRepository.navigate(
						Destinations.novaFilteredLibrary(moviesView, NovaBrowseFilters.FILTER_FAVOURITES, null, "Favourites")
					)
				},
			)
			Spacer(Modifier.height(3.dp))
			Entry(
				label = if (showGenres) "Categories ▾" else "Categories ▸",
				iconRes = R.drawable.ic_grid,
				active = false,
				onClick = { showGenres = !showGenres },
			)
			if (showGenres) {
				val list = genres
				when {
					genresLoading -> RailNote("Loading categories…")
					genresFailed -> RailNote("Could not load — reopen to retry")
					list != null -> list.forEach { genre ->
						Spacer(Modifier.height(3.dp))
						Entry(
							label = genre,
							iconRes = R.drawable.ic_movie,
							active = current == NovaRailPage.Genre(genre),
							indent = true,
							onClick = {
								navigationRepository.navigate(
									Destinations.novaFilteredLibrary(moviesView, NovaBrowseFilters.FILTER_GENRE, genre, genre)
								)
							},
						)
					}
				}
			}
		}

		Spacer(Modifier.height(3.dp))
		Entry(
			label = "Download",
			iconRes = R.drawable.ic_add,
			active = current == NovaRailPage.Download,
			onClick = { navigationRepository.navigate(NovaDownloadDestinations.download) },
		)
		Spacer(Modifier.height(3.dp))
		Entry(
			label = "Downloads",
			iconRes = R.drawable.ic_time,
			active = current == NovaRailPage.Downloads,
			onClick = { navigationRepository.navigate(NovaDownloadsDestinations.downloads) },
		)
		Spacer(Modifier.height(3.dp))
		Entry(
			label = "IPTV",
			iconRes = R.drawable.ic_tv,
			active = false,
			onClick = { context.startActivity(Intent(context, NovaIptvActivity::class.java)) },
		)
		Spacer(Modifier.height(3.dp))
		Entry(
			label = "Settings",
			iconRes = R.drawable.ic_settings,
			active = false,
			onClick = { context.startActivity(ActivityDestinations.userPreferences(context)) },
		)

		Spacer(Modifier.height(12.dp))
		Row(
			modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Box(Modifier.width(18.dp).height(1.dp).background(nova.accent.withAlpha(0x88)))
			Spacer(Modifier.width(8.dp))
			Text("MY LIBRARIES", color = nova.textFaint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.15.sp)
		}
		Spacer(Modifier.height(9.dp))

		if (!hasCollections) RecentEntries()
		orderedViews.forEach { view ->
			Entry(
				label = view.name.orEmpty(),
				iconRes = sideRailIcon(view.name.orEmpty(), view.collectionType),
				active = current == NovaRailPage.Library(view.id),
				onClick = { itemLauncher.launchUserView(view) },
			)
			Spacer(Modifier.height(2.dp))
			// Straight after Collections: the newest films and shows across your libraries.
			if (view.name.equals("Collections", ignoreCase = true)) RecentEntries()
		}
	}
}

@Composable
private fun RailNote(text: String) {
	Text(
		text = text,
		color = nova.textMuted,
		fontSize = 11.sp,
		modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 4.dp),
	)
}

@Composable
private fun NovaSideRailButton(
	label: String,
	iconRes: Int,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	active: Boolean = false,
	onRight: (() -> Unit)? = null,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(nova.corner(8))
	val keyModifier = if (onRight != null) {
		modifier.onPreviewKeyEvent { event ->
			if (event.key == Key.DirectionRight) {
				if (event.type == KeyEventType.KeyDown) onRight()
				true
			} else false
		}
	} else modifier

	Button(
		onClick = onClick,
		modifier = if (focused) keyModifier.border(2.dp, nova.accent, shape) else keyModifier,
		interactionSource = interactionSource,
		shape = shape,
		colors = ButtonDefaults.colors(
			containerColor = if (active) nova.accentSoft else Color.Transparent,
			contentColor = if (active) Color.White else nova.textSecondary,
			focusedContainerColor = nova.accent,
			focusedContentColor = nova.onAccent,
		),
		contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
	) {
		Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
			if (active && !focused) {
				Box(
					modifier = Modifier
						.width(3.dp)
						.height(18.dp)
						.background(nova.accent, RoundedCornerShape(nova.corner(3)))
				)
				Spacer(Modifier.width(7.dp))
			}
			Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(19.dp))
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

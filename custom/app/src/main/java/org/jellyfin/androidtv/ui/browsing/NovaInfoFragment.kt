package org.jellyfin.androidtv.ui.browsing

import org.jellyfin.androidtv.ui.withAlpha
import org.jellyfin.androidtv.ui.nova
import org.jellyfin.androidtv.ui.home.NovaSideRail
import org.jellyfin.androidtv.ui.home.NovaRailPage
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType as SdkImageType
import org.koin.compose.koinInject

class NovaInfoFragment : Fragment() {
	private lateinit var item: BaseItemDto

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val raw = requireArguments().getString(Extras.Folder)
			?: error("NOVA info screen requires Extras.Folder")
		item = Json.Default.decodeFromString(BaseItemDto.serializer(), raw)
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		val api = koinInject<ApiClient>()
		var detailItem by remember(item.id) { mutableStateOf(item) }

		val railFocusRequester = remember { FocusRequester() }
		val actionFocusRequester = remember { FocusRequester() }

		val backdrop = remember(detailItem.id) {
			detailItem.itemBackdropImages.firstOrNull() ?: detailItem.parentBackdropImages.firstOrNull()
		}
		val logo = remember(detailItem.id) {
			detailItem.itemImages[SdkImageType.LOGO] ?: detailItem.parentImages[SdkImageType.LOGO]
		}
		val activeViewId = detailItem.parentId

		// Reload when you come back (e.g. after watching), so Resume shows the right place.
		var reloads by remember { androidx.compose.runtime.mutableIntStateOf(0) }
		val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
		androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
			var first = true
			val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
				if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
					if (first) first = false else reloads++
				}
			}
			lifecycleOwner.lifecycle.addObserver(observer)
			onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
		}

		LaunchedEffect(item.id, reloads) {
			detailItem = runCatching {
				api.userLibraryApi.getItem(itemId = item.id).content
			}.getOrDefault(detailItem)
			if (reloads > 0) return@LaunchedEffect
			// Start on the Download / Favourite button; fall back to the side bar.
			if (runCatching { actionFocusRequester.requestFocus() }.isFailure) {
				runCatching { railFocusRequester.requestFocus() }
			}
		}

		JellyfinTheme {
			Box(modifier = Modifier.fillMaxSize().background(nova.bg)) {
				Box(
					modifier = Modifier
						.padding(start = 164.dp)
						.fillMaxWidth()
						.height(430.dp)
						.clipToBounds()
				) {
					if (backdrop != null) {
						AsyncImage(
							url = backdrop.getUrl(api, maxWidth = 1280, maxHeight = 720),
							blurHash = backdrop.blurHash,
							aspectRatio = 16f / 9f,
							scaleType = ImageView.ScaleType.CENTER_CROP,
							modifier = Modifier.fillMaxSize(),
						)
					}

					Box(
						modifier = Modifier.fillMaxSize().background(
							Brush.horizontalGradient(
								0f to nova.bg.withAlpha(0xE8),
								0.30f to nova.bg.withAlpha(0xA8),
								0.62f to nova.bg.withAlpha(0x44),
								1f to Color.Transparent,
							)
						)
					)
					Box(
						modifier = Modifier.fillMaxSize().background(
							Brush.verticalGradient(
								0f to nova.bg.withAlpha(0x12),
								0.72f to nova.bg.withAlpha(0x18),
								0.92f to nova.bg.withAlpha(0xC8),
								1f to nova.bg,
							)
						)
					)
					Box(
						modifier = Modifier.fillMaxSize().background(
							Brush.horizontalGradient(
								0f to nova.bg.withAlpha(0xC8),
								0.05f to nova.bg.withAlpha(0xA8),
								0.11f to nova.bg.withAlpha(0x66),
								0.18f to nova.bg.withAlpha(0x26),
								0.25f to Color.Transparent,
							)
						)
					)
					Box(
						modifier = Modifier.fillMaxSize().background(
							Brush.verticalGradient(
								0f to Color.Transparent,
								0.67f to Color.Transparent,
								0.76f to nova.bg.withAlpha(0x22),
								0.86f to nova.bg.withAlpha(0x55),
								0.94f to nova.bg.withAlpha(0x99),
								1f to nova.bg.withAlpha(0xE8),
							)
						)
					)
				}

				Box(
					modifier = Modifier
						.padding(start = 150.dp)
						.width(54.dp)
						.height(430.dp)
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
						current = activeViewId?.let { NovaRailPage.Library(it) } ?: NovaRailPage.None,
						focusRequester = railFocusRequester,
					)

					androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
						val pageHeight = maxHeight
						Column(
							modifier = Modifier
								.align(Alignment.TopStart)
								// Wide enough for a useful cast row; the overview keeps its own width.
								.fillMaxWidth(0.94f)
								// Scrolls so the sequels and cast rows can be reached with the remote.
								.verticalScroll(rememberScrollState())
								.padding(start = 28.dp, end = 26.dp, top = 34.dp, bottom = 12.dp)
								// At least a full screen tall, so the cast sits at the bottom
								// however short the description is.
								.heightIn(min = pageHeight - 46.dp),
							verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
						) {
						Column {
							if (logo != null) {
								AsyncImage(
									url = logo.getUrl(api, maxWidth = 620, maxHeight = 160),
									blurHash = logo.blurHash,
									scaleType = ImageView.ScaleType.FIT_START,
									modifier = Modifier.fillMaxWidth(0.8f).height(72.dp),
								)
							} else {
								Text(
									detailItem.name.orEmpty(),
									color = Color.White,
									fontSize = 34.sp,
									fontWeight = FontWeight.Bold,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
							}

							Spacer(Modifier.height(10.dp))
							Row(verticalAlignment = Alignment.CenterVertically) {
								NovaTypeTag(detailItem)
								Spacer(Modifier.width(9.dp))
								detailItem.productionYear?.let {
									Text(it.toString(), color = nova.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
								}
								if (!detailItem.officialRating.isNullOrBlank()) {
									Spacer(Modifier.width(9.dp))
									Text("•", color = nova.textMuted, fontSize = 13.sp)
									Spacer(Modifier.width(9.dp))
									org.jellyfin.androidtv.ui.browsing.NovaRatingBadge(detailItem.officialRating.orEmpty())
								}
								org.jellyfin.androidtv.ui.browsing.NovaLengthInfo(detailItem)
							}
							Spacer(Modifier.height(12.dp))
							Text(
								detailItem.overview.orEmpty(),
								modifier = Modifier.fillMaxWidth(0.72f),
								color = nova.textPrimary,
								fontSize = 15.sp,
								// The whole description; the page scrolls if it's long.
								lineHeight = 20.sp,
							)
							Spacer(Modifier.height(14.dp))
							NovaInfoActions(item = detailItem, firstFocus = actionFocusRequester)
							Spacer(Modifier.height(14.dp))
						}
						Column {
							// Other films in the same series (e.g. The Addams Family 2).
							NovaSequelsRow(item = detailItem, modifier = Modifier.padding(bottom = 14.dp))
							NovaCastRow(item = detailItem)
						}
						}
					}
				}
			}
		}
	}
}

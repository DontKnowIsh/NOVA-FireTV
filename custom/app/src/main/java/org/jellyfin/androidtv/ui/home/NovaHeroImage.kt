package org.jellyfin.androidtv.ui.home

import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

/** Prefer composed landscape artwork, falling back for titles without a thumb. */
internal val BaseItemDto.novaHeroImage
    get() = itemImages[ImageType.THUMB] ?: parentImages[ImageType.THUMB]
        ?: itemBackdropImages.firstOrNull() ?: parentBackdropImages.firstOrNull()

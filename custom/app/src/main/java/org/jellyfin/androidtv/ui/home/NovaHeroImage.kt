package org.jellyfin.androidtv.ui.home

import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

/** Prefer composed landscape artwork, falling back for titles without a thumb. */
internal val BaseItemDto.novaHeroImage
    // Backdrop first: a clean scene with no title text on it (NOVA shows the logo
    // and title itself). Thumb only when there is no backdrop.
    get() = itemBackdropImages.firstOrNull() ?: parentBackdropImages.firstOrNull()
        ?: itemImages[ImageType.THUMB] ?: parentImages[ImageType.THUMB]

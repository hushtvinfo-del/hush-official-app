package com.hushtv.tv

import coil.map.Mapper
import coil.request.Options
import com.hushtv.tv.data.BundleOverrides

/**
 * Coil [Mapper] that short-circuits image loads to bundled assets
 * or server-side overrides before the network is touched. Wired
 * into the [ImageLoader.Builder.components] block of
 * [HushTVApp.newImageLoader].
 *
 * Resolution chain (from [BundleOverrides.resolve]):
 *   1. Server override → may be any URL, network or local.
 *   2. APK-bundled `file:///android_asset/bundled/...` → instant.
 *   3. Pass-through → the original input URL.
 *
 * Pass-through case returns the *same* String reference so Coil's
 * cache-key path is not perturbed for non-bundled images.
 */
internal object HushBundleMapper : Mapper<String, String> {
    override fun map(data: String, options: Options): String =
        BundleOverrides.resolve(data) ?: data
}

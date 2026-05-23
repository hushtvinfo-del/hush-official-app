package com.hushtv.tv

import coil.map.Mapper
import coil.request.Options
import com.hushtv.tv.data.BundleOverrides
import com.hushtv.tv.data.DevBundleCache

/**
 * Coil [Mapper] that short-circuits image loads to bundled assets
 * or server-side overrides before the network is touched. Wired
 * into the [ImageLoader.Builder.components] block of
 * [HushTVApp.newImageLoader].
 *
 * Resolution chain:
 *   1. DEV-flavor TMDB image bundle (Phase 2) — `file:///android_asset/dev_only/img/...`
 *      for any TMDB image whose `{path}` is in img_map.json.
 *      No-op on canada / official.
 *   2. Server override map (BundleOverrides) — may be any URL, network or local.
 *   3. APK-bundled `file:///android_asset/bundled/...` (BundledAssets) — instant.
 *   4. Pass-through → the original input URL.
 *
 * Pass-through case returns the *same* String reference so Coil's
 * cache-key path is not perturbed for non-bundled images.
 */
internal object HushBundleMapper : Mapper<String, String> {
    override fun map(data: String, options: Options): String =
        DevBundleCache.resolveImage(data)
            ?: BundleOverrides.resolve(data)
            ?: data
}

package com.hushtv.tv.data

/**
 * Third-party API keys used by metadata services.
 *
 *  • TMDB  — rich movie/series metadata, cast photos, recommendations
 *  • RPDB  — pre-rated poster images (IMDb / TMDB / RT / Meta baked in)
 *
 * These are client-side keys (read-only TMDB v3, user-owned RPDB).
 * Rotating the keys is safe — just replace the strings below and ship a new build.
 */
object ApiKeys {
    const val TMDB = "8b6f95c2b9bb0121d1b43f88dc65f52d"
    const val RPDB = "t4-383d38c7-ac4e-4e38-9c3f-7c6e9303cbb7"
}

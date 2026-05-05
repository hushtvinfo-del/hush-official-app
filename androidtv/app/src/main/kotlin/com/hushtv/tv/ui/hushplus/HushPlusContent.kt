package com.hushtv.tv.ui.hushplus

import androidx.compose.ui.graphics.Color

/**
 * Static content model for the Hush+ section.
 *
 * Hush+ is a premium add-on suite available exclusively to HushTV
 * subscribers. The model is intentionally shared between TV and
 * Mobile renderers so copy + accent colours never drift.
 *
 * Once the Base44 auth integration is wired up, this file will gain
 * a `requiresEntitlement` flag per addon and the CTAs will route to
 * the install / launch flow. For now the page is informational and
 * the CTAs render as "Coming soon" disabled buttons.
 */
data class HushAddon(
    /** Unique key used by the navigation switcher in both TV/Mobile. */
    val key: String,
    /** Display name as shown in tabs and headers. */
    val name: String,
    /** Short tagline displayed under the name on its detail page. */
    val tagline: String,
    /** 2–3 sentence pitch describing the addon. */
    val description: String,
    /** Bulleted feature list — drives chips / pills on the page. */
    val features: List<String>,
    /** Hero image URL (TMDB / RPDB / base44). Null = icon-only hero. */
    val heroImageUrl: String?,
    /** Per-addon accent — used for borders, focus rings, badges. */
    val accent: Color,
    /** Eyebrow tag rendered above the name (e.g. "NEW ADD-ON"). */
    val eyebrow: String,
)

object HushPlusContent {

    /** Brand accent — same primary accent as the rest of the app
     *  (DodgerBlue post-v1.43 rebrand). Variable name kept as "Cyan"
     *  so we don't have to touch every callsite in this object's
     *  pillar definitions. */
    private val Cyan = Color(0xFF1E90FF)
    private val Amber = Color(0xFFF59E0B)
    private val Magenta = Color(0xFFC026D3)
    private val Crimson = Color(0xFFEF4444)

    val OVERVIEW_KEY = "overview"

    /**
     * Six headline pillars of the Hush+ suite. Rendered as a 3×2 grid
     * on TV and a 2×3 grid on Mobile on the Overview page.
     */
    val pillars: List<Pair<String, String>> = listOf(
        "Massive Content Library" to
            "30,000+ live channels in stunning 4K, plus 250,000+ movies and 30,000+ series in every language.",
        "No Buffering, Netflix-Style Tech" to
            "Cutting-edge VOD streaming auto-adjusts quality so playback stays smooth — just like Netflix.",
        "Sleek, User-Friendly App" to
            "Netflix-inspired interface to manage your favourite live TV, series and movies. Bonus: built-in ad-free YouTube.",
        "Global Access, No VPN Needed" to
            "Buffer-free international streams from anywhere with zero geo restrictions.",
        "Backup for HushTV" to
            "Different servers mean if something's down on HushTV, Hush+ has you covered.",
        "Exclusive to HushTV Members" to
            "Hush+ is available only to active HushTV subscribers — a reward for being part of the community.",
    )

    /**
     * The four addons users can browse to. Order matches the menu:
     * Overview → VOD → Books → Arcade → Tube.
     */
    val addons: List<HushAddon> = listOf(
        HushAddon(
            key = "vod",
            name = "HushVOD+",
            tagline = "The internet's entire movie & TV library. One app.",
            description = "Powered by Nuvio, HushVOD+ taps into every movie and series available on the internet — not just a curated list. If it exists, Nuvio can find it and stream it.",
            features = listOf(
                "Millions of movies & series",
                "720p, 1080p & 4K — every quality",
                "Every language and region",
                "No VPN required, no restrictions",
            ),
            heroImageUrl = "https://1a-1791.com/video/fww1/74/s8/1/4/t/z/3/4tz3z.qR4e-small-How-to-Install-Nuvio-Media-.jpg",
            accent = Cyan,
            eyebrow = "FLAGSHIP STREAMING",
        ),
        HushAddon(
            key = "books",
            name = "HushBooks+",
            tagline = "Your entire imagination, unlocked.",
            description = "Read any eBook or listen to any audiobook in the world — from bestsellers to classics, in every genre and every language.",
            features = listOf(
                "Millions of eBooks",
                "Full audiobook library",
                "Every language & genre",
                "Instant access, no limits",
            ),
            heroImageUrl = "https://media.base44.com/images/public/6935d1601633a095d431b771/7231ce8dc_generated_image.png",
            accent = Amber,
            eyebrow = "NEW ADD-ON",
        ),
        HushAddon(
            key = "arcade",
            name = "HushArcade+",
            tagline = "The ultimate retro gaming vault.",
            description = "Play thousands of classic games from every legendary console — no downloads, no cartridges, no limits.",
            features = listOf(
                "NES",
                "SNES",
                "Sega Genesis",
                "PlayStation 1 & 2",
                "Game Boy",
                "Nintendo 64",
                "Atari",
                "& much more",
            ),
            heroImageUrl = "https://media.base44.com/images/public/6935d1601633a095d431b771/2f87fe6ec_generated_image.png",
            accent = Magenta,
            eyebrow = "NEW ADD-ON",
        ),
        HushAddon(
            key = "tube",
            name = "HushTube+",
            tagline = "YouTube. Without the ads.",
            description = "Built-in YouTube Premium experience — all the videos, none of the interruptions. Background play, offline downloads and 4K streaming included.",
            features = listOf(
                "Ad-free YouTube videos",
                "Background & offline play",
                "4K streaming when available",
                "No subscription juggling",
            ),
            heroImageUrl = null,
            accent = Crimson,
            eyebrow = "INCLUDED",
        ),
        HushAddon(
            key = "xxx",
            name = "HushXXX",
            tagline = "Adult-only. For 18+ members.",
            description = "A dedicated adult library — curated scenes, full cast & studio info, and a fast modern interface. Adults only. Included in Hush+.",
            features = listOf(
                "Curated scene library with full metadata",
                "Performer & studio profiles",
                "Rich categories, search & discovery",
                "Age-gated — 18+ required",
            ),
            heroImageUrl = null,
            accent = Color(0xFFE91E63),       // Hot pink — distinct from the rest of the suite.
            eyebrow = "18+ ONLY",
        ),
    )

    fun findAddon(key: String): HushAddon? = addons.firstOrNull { it.key == key }
}

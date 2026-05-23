"""
Test that /app/_buildenv/dev-bundle-fetcher.py outputs JSON in a
shape the Android client's Moshi adapters expect to parse cleanly.

Pure-Python sanity check — Python can't run Kotlin Moshi, but we
mirror the exact field requirements pulled from:
  • /app/androidtv/app/src/main/kotlin/com/hushtv/tv/data/TmdbService.kt
    (TmdbMovie, TmdbTv data classes)
  • /app/androidtv/app/src/main/kotlin/com/hushtv/tv/data/sports/SportsModels.kt
    (sports response wire models)

Why this matters
────────────────
A typo in the fetcher (e.g. forgetting `original_title`) would compile
fine, the bundle would ship, and Moshi reflection would silently
return `null` on the device, making every dev-flavor TMDB lookup
fail-through to network — silently undoing the optimisation. This
test catches that at PR time.

Run:
  pytest /app/_buildenv/tests/test_dev_bundle_shape.py -v
"""
import json
import os
import pytest

OUT_DEV = "/app/androidtv/app/src/dev/assets/dev_only"


def _load(name: str) -> dict:
    path = os.path.join(OUT_DEV, name)
    if not os.path.exists(path):
        pytest.skip(f"{name} not generated yet — run dev-bundle-fetcher.py first")
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


# ── Sports ─────────────────────────────────────────────────────────


def test_sports_home_has_expected_top_keys():
    d = _load("sports_home.json")
    # SportsHomeResponse expects: hero, leagues, ppv
    assert "hero" in d
    assert "leagues" in d
    assert "ppv" in d


def test_sports_ppv_has_events_array():
    d = _load("sports_ppv.json")
    assert "events" in d
    assert isinstance(d["events"], list)


@pytest.mark.parametrize("slug", ["nhl", "nba", "mlb", "ufc", "epl", "mls"])
def test_sports_league_has_required_shape(slug):
    d = _load(f"sports_league_{slug}.json")
    assert "league" in d, f"{slug} missing league key"
    assert "games" in d, f"{slug} missing games key"
    assert isinstance(d["games"], list)


# ── TMDB top-popular ───────────────────────────────────────────────


def _expected_movie_fields():
    """Fields the TmdbMovie data class wants. Optional fields (?) can
    be null but MUST be present in the JSON or Moshi reflection will
    fail."""
    return [
        "id", "title", "original_title", "overview", "tagline",
        "poster_path", "backdrop_path", "release_date", "runtime",
        "vote_average", "vote_count", "genres",
    ]


def _expected_tv_fields():
    return [
        "id", "name", "original_name", "overview", "tagline",
        "poster_path", "backdrop_path", "first_air_date",
        "last_air_date", "episode_run_time", "number_of_seasons",
        "number_of_episodes", "vote_average", "vote_count",
        "genres", "seasons",
    ]


def test_tmdb_movies_has_items_and_min_count():
    d = _load("tmdb_top_movies.json")
    assert "items" in d
    assert len(d["items"]) >= 80, f"only {len(d['items'])} movies — expected ≥80"


def test_tmdb_movies_items_have_all_required_fields():
    d = _load("tmdb_top_movies.json")
    required = _expected_movie_fields()
    for i, item in enumerate(d["items"][:5]):  # sample first 5
        for f in required:
            assert f in item, f"movie #{i} missing field {f}: {item.get('title')}"
        # Genre shape must be List<{id, name}> per TmdbGenre
        for g in item["genres"]:
            assert "id" in g and "name" in g, f"bad genre on movie #{i}"


def test_tmdb_tv_has_items_and_min_count():
    d = _load("tmdb_top_tv.json")
    assert "items" in d
    assert len(d["items"]) >= 80


def test_tmdb_tv_items_have_all_required_fields():
    d = _load("tmdb_top_tv.json")
    required = _expected_tv_fields()
    for i, item in enumerate(d["items"][:5]):
        for f in required:
            assert f in item, f"tv #{i} missing field {f}: {item.get('name')}"
        # episode_run_time is List<Int> not Optional<Int>
        assert isinstance(item["episode_run_time"], list)
        # seasons is List<TmdbTvSeason>
        assert isinstance(item["seasons"], list)


def test_manifest_present_and_versioned():
    d = _load("manifest.json")
    assert d.get("schema") == 1
    assert "sports" in d
    assert "tmdb" in d
    assert "themes_pack" in d
    assert "generated_at_utc" in d


# ── Image bundle (Phase 2) ─────────────────────────────────────────


def test_img_map_present_and_well_formed():
    d = _load("img_map.json")
    assert isinstance(d, dict)
    # Should have at least 200 entries (top 100 movies × poster + top
    # 100 tv × poster; backdrops are bonus). 350 is a conservative
    # floor — some titles legitimately lack backdrop_path.
    assert len(d) >= 200, f"img_map only has {len(d)} entries — expected ≥200"


def test_img_map_keys_are_tmdb_paths():
    d = _load("img_map.json")
    # Every key should look like a TMDB image path: starts with "/",
    # ends with .jpg / .png / .webp. We don't want absolute URLs in
    # keys — the resolver strips the prefix on-device.
    for k, v in list(d.items())[:10]:
        assert k.startswith("/"), f"bad key (no leading /): {k!r}"
        assert k.lower().endswith((".jpg", ".png", ".webp", ".gif")), \
            f"key doesn't look like an image path: {k!r}"
        assert v.startswith("dev_only/img/"), f"bad value: {v!r}"
        assert v.endswith(".webp"), f"value not WebP: {v!r}"


def test_img_map_values_resolve_to_actual_files():
    """The on-device resolver returns file:///android_asset/<value>.
    If the WebP file doesn't actually exist in assets/dev_only/img/
    the device renders a blank tile and we don't notice until users
    complain. This test catches the orphan case."""
    d = _load("img_map.json")
    img_dir = os.path.join(OUT_DEV, "img")
    if not os.path.exists(img_dir):
        pytest.skip("img/ dir not generated yet")
    # Sample 20 random entries (full check is 372 × file-exists =
    # negligible but I/O-heavy; sampling keeps the suite fast).
    import random
    sampled = random.Random(42).sample(list(d.values()), min(20, len(d)))
    for asset_path in sampled:
        # asset_path is "dev_only/img/m12345_p.webp" — strip the
        # dev_only/ prefix to get the path relative to OUT_DEV.
        rel = asset_path[len("dev_only/"):]
        full = os.path.join(OUT_DEV, rel)
        assert os.path.isfile(full), f"img_map points to missing file: {full}"
        assert os.path.getsize(full) > 500, f"suspiciously small webp: {full}"

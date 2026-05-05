#!/usr/bin/env bash
# tag-release.sh — Tag the current HEAD with the build's versionName.
#
# Usage:
#   /app/_buildenv/tag-release.sh dev        # tags HEAD as v1.43.93-dev
#   /app/_buildenv/tag-release.sh official   # tags HEAD as v1.43.93-official
#
# Idempotent: if the tag already exists at any commit, prints a warning
# and exits 0 without re-tagging. To re-point a tag at a new commit,
# delete it first: `git tag -d v1.43.93-dev`.
#
# Why this exists: the user said "go back to v1.43.87" and we wasted
# multiple iterations because we had to git-log-S the version string
# to find the SHA. With dev/official tags on every release, that
# becomes `git checkout v1.43.87-dev`.

set -euo pipefail

CHANNEL="${1:-dev}"
if [[ "$CHANNEL" != "dev" && "$CHANNEL" != "official" ]]; then
    echo "✗ tag-release: channel must be 'dev' or 'official' (got '$CHANNEL')" >&2
    exit 1
fi

cd /app

VERSION_NAME=$(
    grep -E '^\s*versionName\s*=' androidtv/app/build.gradle.kts \
        | head -1 \
        | sed -E 's/.*"([^"]+)".*/\1/'
)
VERSION_CODE=$(
    grep -E '^\s*versionCode\s*=' androidtv/app/build.gradle.kts \
        | head -1 \
        | sed -E 's/.*=\s*([0-9]+).*/\1/'
)

if [[ -z "$VERSION_NAME" || -z "$VERSION_CODE" ]]; then
    echo "✗ tag-release: couldn't parse versionName / versionCode from build.gradle.kts" >&2
    exit 1
fi

TAG="v${VERSION_NAME}-${CHANNEL}"
SHA=$(git rev-parse HEAD)

if git rev-parse --verify --quiet "refs/tags/$TAG" >/dev/null; then
    EXISTING_SHA=$(git rev-parse "$TAG^{commit}")
    if [[ "$EXISTING_SHA" == "$SHA" ]]; then
        echo "✓ tag-release: $TAG already points at HEAD ($SHA) — nothing to do."
    else
        echo "⚠ tag-release: $TAG already exists at $EXISTING_SHA (current HEAD is $SHA)." >&2
        echo "  Skipping. To re-tag: git tag -d $TAG && /app/_buildenv/tag-release.sh $CHANNEL" >&2
    fi
    exit 0
fi

git tag -a "$TAG" -m "Release $TAG (versionCode $VERSION_CODE)"
echo "✓ tag-release: tagged HEAD ($SHA) as $TAG (versionCode $VERSION_CODE)"
echo "  Roll back to this version: git checkout $TAG"

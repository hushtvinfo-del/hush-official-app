#!/usr/bin/env bash
# checkout-version.sh — Roll the source tree back to a tagged release.
#
# Usage:
#   /app/_buildenv/checkout-version.sh 1.43.87           # defaults to dev
#   /app/_buildenv/checkout-version.sh 1.43.87 dev
#   /app/_buildenv/checkout-version.sh 1.43.87 official
#   /app/_buildenv/checkout-version.sh list              # list all tagged releases
#
# Lists known tags when no version is given. Refuses to run if the
# working tree has uncommitted changes — explicit safety net so we
# don't accidentally throw away in-flight work.

set -euo pipefail

cd /app

if [[ "${1:-}" == "list" || "${1:-}" == "--list" || "${1:-}" == "-l" || -z "${1:-}" ]]; then
    echo "Tagged HushTV releases (most recent first):"
    git tag -l 'v*-dev' 'v*-official' \
        | sort -V -r \
        | while read -r tag; do
            sha=$(git rev-parse --short "$tag^{commit}")
            date=$(git log -1 --format=%ai "$tag^{commit}" 2>/dev/null | awk '{print $1}')
            printf "  %-30s  %s  %s\n" "$tag" "$sha" "$date"
        done
    [[ -z "${1:-}" ]] && echo "" && echo "Usage: $0 <version> [dev|official]"
    exit 0
fi

VERSION="$1"
CHANNEL="${2:-dev}"
TAG="v${VERSION}-${CHANNEL}"

if ! git rev-parse --verify --quiet "refs/tags/$TAG" >/dev/null; then
    echo "✗ Tag '$TAG' not found." >&2
    echo "  Available tags:"
    git tag -l 'v*' | sort -V | sed 's/^/    /' >&2
    exit 1
fi

if ! git diff-index --quiet HEAD --; then
    echo "✗ Working tree has uncommitted changes. Stash or commit first:"
    git status -s | head -10
    exit 1
fi

echo "▶ Checking out $TAG ($(git rev-parse --short $TAG))…"
git checkout "$TAG"

echo ""
echo "✔ Now at $TAG. Rebuild + deploy with:"
echo "    /app/_buildenv/build-and-deploy-dev.sh"
echo ""
echo "  Or return to the latest commit with:"
echo "    git checkout main"

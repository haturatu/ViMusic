#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

VERSION="$(grep '^version=' gradle.properties | cut -d'=' -f2)"
ASSET_PATH="app/build/outputs/apk/release/app-release.apk"
if [[ $# -lt 1 ]]; then
  echo "error: tag argument is required (example: ./release.sh v$VERSION)" >&2
  exit 1
fi
TAG="$1"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "error: required command not found: $1" >&2
    exit 1
  }
}

require_cmd git
require_cmd gh
require_cmd make

if [[ -z "$VERSION" ]]; then
  echo "error: failed to read version from gradle.properties" >&2
  exit 1
fi

if [[ ! -f "$ASSET_PATH" ]]; then
  echo "info: asset not found at '$ASSET_PATH', running make release..."
  make release
fi

if [[ ! -f "$ASSET_PATH" ]]; then
  echo "error: asset file not found: $ASSET_PATH" >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "error: gh is not authenticated. Run: gh auth login" >&2
  exit 1
fi

if ! git rev-parse "$TAG" >/dev/null 2>&1; then
  git tag -a "$TAG" -m "Release $TAG"
fi

git push origin "$TAG"

if gh release view "$TAG" >/dev/null 2>&1; then
  gh release upload "$TAG" "$ASSET_PATH" --clobber
  gh release edit "$TAG" --latest
else
  gh release create "$TAG" "$ASSET_PATH" \
    --title "$TAG" \
    --generate-notes \
    --latest
fi

echo "done: published $TAG with asset $ASSET_PATH"

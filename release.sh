#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

VERSION="$(grep '^version=' gradle.properties | cut -d'=' -f2)"
ASSET_PATH="app/build/outputs/apk/release/app-release.apk"
if [[ $# -lt 1 ]]; then
  cat >&2 <<EOF
usage: ./release.sh <tag>

example:
  ./release.sh v$VERSION

notes:
  - tag must match gradle.properties version (expected: v$VERSION)
  - this script commits only gradle.properties, pushes master and tag, then publishes GitHub release
EOF
  exit 1
fi
TAG="$1"
EXPECTED_TAG="v$VERSION"

if [[ "$TAG" != "$EXPECTED_TAG" ]]; then
  echo "error: tag/version mismatch. expected '$EXPECTED_TAG' from gradle.properties, got '$TAG'" >&2
  exit 1
fi

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "error: required command not found: $1" >&2
    exit 1
  }
}

require_cmd git
require_cmd gh
require_cmd make

make release

if [[ ! -f "$ASSET_PATH" ]]; then
  echo "error: asset file not found: $ASSET_PATH" >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "error: gh is not authenticated. Run: gh auth login" >&2
  exit 1
fi

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$CURRENT_BRANCH" != "master" ]]; then
  echo "error: run release.sh on master branch (current: $CURRENT_BRANCH)" >&2
  exit 1
fi

git add gradle.properties
if ! git diff --cached --quiet; then
  git commit -m "release: $TAG"
else
  echo "info: no staged changes to commit"
fi

git push origin master

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

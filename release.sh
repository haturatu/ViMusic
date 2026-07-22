#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

VERSION="$(grep '^version=' gradle.properties | cut -d'=' -f2)"
if [[ $# -lt 1 ]]; then
  cat >&2 <<EOF
usage: ./release.sh <tag>

example:
  ./release.sh v$VERSION

notes:
  - tag must match gradle.properties version (expected: v$VERSION)
  - this script commits only gradle.properties, then pushes master and the tag
  - the tag starts .github/workflows/release.yml, which publishes HTTP/3 and HTTP/2-only APKs
  - configure these Actions secrets before releasing:
      VIMUSIC_RELEASE_KEYSTORE_BASE64
      VIMUSIC_KEYSTORE_PASSWORD
      VIMUSIC_KEY_ALIAS (optional; defaults to vimusic)
      VIMUSIC_KEY_PASSWORD
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

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$CURRENT_BRANCH" != "master" ]]; then
  echo "error: run release.sh on master branch (current: $CURRENT_BRANCH)" >&2
  exit 1
fi

if [[ -n "$(git status --porcelain -- . ':(exclude)gradle.properties')" ]]; then
  echo "error: commit or stash changes other than gradle.properties before releasing" >&2
  exit 1
fi

git add gradle.properties
if ! git diff --cached --quiet; then
  git commit -m "release: $TAG"
else
  echo "info: no staged changes to commit"
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "error: working tree is not clean after preparing the release commit" >&2
  exit 1
fi

if git rev-parse --verify --quiet "refs/tags/$TAG" >/dev/null; then
  echo "error: tag already exists locally: $TAG" >&2
  exit 1
fi

if git ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1; then
  echo "error: tag already exists on origin: $TAG" >&2
  exit 1
fi

git tag -a "$TAG" -m "Release $TAG"
git push --atomic origin master "$TAG"

echo "done: pushed $TAG; GitHub Actions will build and publish the release"

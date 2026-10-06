#!/usr/bin/env bash
set -euo pipefail
: "${GITHUB_REPOSITORY:?GitHub repository required}"
: "${GITHUB_SHA:?Verified commit required}"
: "${RELEASE_TAG:?Release tag required}"
if [[ ! "$RELEASE_TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.-]+)?$ ]]; then
  echo 'Use a version tag such as v0.1.0 or v0.1.0-preview.1.' >&2; exit 1
fi
apk_path="${1:?APK path required}"
[[ -s "$apk_path" ]] || { echo 'APK is missing or empty.' >&2; exit 1; }
release_dir="$(mktemp -d)"
trap 'rm -rf "$release_dir"' EXIT
cp "$apk_path" "$release_dir/pocket-agent-debug.apk"
if draft_state="$(gh release view "$RELEASE_TAG" --repo "$GITHUB_REPOSITORY" --json isDraft --jq .isDraft 2>/dev/null)"; then
  if [[ "$draft_state" != true ]]; then
    echo 'A published release already uses this tag. Choose a new version tag.' >&2; exit 1
  fi
  gh release upload "$RELEASE_TAG" "$release_dir/pocket-agent-debug.apk" --repo "$GITHUB_REPOSITORY" --clobber
  gh release edit "$RELEASE_TAG" --repo "$GITHUB_REPOSITORY" --target "$GITHUB_SHA" --draft=false --prerelease --notes-file docs/PREVIEW_RELEASE.md
else
  gh release create "$RELEASE_TAG" "$release_dir/pocket-agent-debug.apk" --repo "$GITHUB_REPOSITORY" --target "$GITHUB_SHA" --title "Pocket Agent ${RELEASE_TAG#v} preview" --prerelease --notes-file docs/PREVIEW_RELEASE.md
fi

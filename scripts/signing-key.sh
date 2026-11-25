#!/usr/bin/env bash
# Finds Prism TV's permanent signing key for this CI run and hands it to Gradle (via $GITHUB_ENV).
#
# Android only installs an update over Prism TV when both are signed with the same key, so every
# build must use one permanent key. Where it comes from, in order:
#   1. Repo secrets PRISM_KEYSTORE_B64 + PRISM_KEYSTORE_PASSWORD (PKCS12, alias "prism"). This is where
#      the key belongs (docs/release-signing.md says how to put it there).
#   2. Until then, the older private draft release tagged "prism-signing-key" (read only, never changed).
#   3. Neither: with REQUIRE_KEY=true (push builds, which publish) this step fails, so nothing is ever
#      published with the wrong key. Otherwise (pull requests) the build goes on unsigned-for-release and
#      Gradle signs it with a throwaway debug key for testing; nothing from it is published.
#
# This script never creates, edits, publishes or deletes any release. A new key is never made here:
# a new key would mean every TV has to uninstall and reinstall Prism TV.
#
# Env: GH_TOKEN, GH_REPO, RUNNER_TEMP, GITHUB_ENV, PRISM_KEYSTORE_B64 / PRISM_KEYSTORE_PASSWORD
# (optional), REQUIRE_KEY ("true" to fail when there's no permanent key).
set -euo pipefail

TAG="prism-signing-key"
KEY_DIR="$RUNNER_TEMP/prism-key"
KS="$KEY_DIR/prism.p12"
PW_FILE="$KEY_DIR/prism-p12-password.txt"
mkdir -p "$KEY_DIR"
chmod 700 "$KEY_DIR"

use_key() { # $1 = keystore password
  local pw="$1"
  echo "::add-mask::$pw"
  keytool -list -storetype PKCS12 -keystore "$KS" -storepass "$pw" -alias prism >/dev/null
  {
    echo "PRISM_KEYSTORE=$KS"
    echo "PRISM_KEYSTORE_PASSWORD=$pw"
    echo "PRISM_KEY_ALIAS=prism"
    echo "PRISM_SIGNED=true"
  } >> "$GITHUB_ENV"
  local sha
  sha="$(keytool -list -v -storetype PKCS12 -keystore "$KS" -storepass "$pw" -alias prism | sed -n 's/^.*SHA256: *//p' | head -1)"
  echo "PRISM_SIGNER_SHA256=$sha" >> "$GITHUB_ENV"
  echo "::notice title=Signing::Permanent Prism TV key from ${SOURCE:-?}, certificate SHA-256 $sha"
}

# Lowest-numbered draft release with our tag (the oldest wins if two runs ever raced).
draft_id() {
  gh api --paginate "repos/$GH_REPO/releases?per_page=100" \
    --jq ".[] | select(.draft and .tag_name == \"$TAG\") | .id" 2>/dev/null | sort -n | head -1
}

download_asset() { # $1 = release id, $2 = asset name, $3 = output file
  local aid
  aid="$(gh api "repos/$GH_REPO/releases/$1/assets" --jq ".[] | select(.name == \"$2\") | .id" | head -1)"
  [ -n "$aid" ] || return 1
  gh api -H "Accept: application/octet-stream" "repos/$GH_REPO/releases/assets/$aid" > "$3"
  [ -s "$3" ]
}

fetch_draft_key() { # $1 = release id
  download_asset "$1" prism.p12 "$KS" && download_asset "$1" prism-p12-password.txt "$PW_FILE"
}

# 1. Repo secrets.
if [ -n "${PRISM_KEYSTORE_B64:-}" ] && [ -n "${PRISM_KEYSTORE_PASSWORD:-}" ]; then
  echo "$PRISM_KEYSTORE_B64" | base64 -d > "$KS"
  SOURCE="repo secrets"
  use_key "$PRISM_KEYSTORE_PASSWORD"
  exit 0
fi

# 2. The older private draft release (transitional: read only).
ID="$(draft_id || true)"
if [ -n "$ID" ] && fetch_draft_key "$ID"; then
  SOURCE="draft release $ID"
  use_key "$(cat "$PW_FILE")"
  echo "::warning title=Signing::The signing key was read from the draft release. Move it into repo secrets (docs/release-signing.md); then this workflow no longer needs write access to read it."
  exit 0
fi

# 3. No permanent key available to this run.
[ -n "$ID" ] && echo "::warning title=Signing::Draft release $ID exists but its key files couldn't be read."
echo "PRISM_SIGNED=false" >> "$GITHUB_ENV"
if [ "${REQUIRE_KEY:-false}" = "true" ]; then
  echo "::error title=Signing::No permanent signing key, so this build stops here and nothing is published. Set the PRISM_KEYSTORE_B64 and PRISM_KEYSTORE_PASSWORD repo secrets (docs/release-signing.md)."
  exit 1
fi
echo "::warning title=Signing::No permanent signing key for this run: a test build only, signed with a throwaway key, and never published."

#!/usr/bin/env bash
# Finds Prism TV's permanent signing key for this CI run and hands it to Gradle (via $GITHUB_ENV).
#
# Android only installs an update over Prism TV when both are signed with the same key, so every
# build must use one permanent key. It comes from the repo secrets PRISM_KEYSTORE_B64 (base64 of a
# PKCS12 keystore, alias "prism") and PRISM_KEYSTORE_PASSWORD (docs/release-signing.md).
#
# The key's certificate must match EXPECTED_SHA256 below, the key every install so far is signed with.
# A different key (a wrong or replaced secret) fails the run, so it can never quietly ship a build
# that existing TVs refuse to update to.
#
# Without the secrets: with REQUIRE_KEY=true (push builds, which publish) this step fails, so nothing
# is ever published with the wrong key. Otherwise (pull requests from forks, which can't see secrets)
# Gradle signs a test build with a throwaway debug key; nothing from it is published.
#
# This script never creates a key and never touches any release.
#
# Env: RUNNER_TEMP, GITHUB_ENV, PRISM_KEYSTORE_B64 / PRISM_KEYSTORE_PASSWORD (optional),
# REQUIRE_KEY ("true" to fail when there's no permanent key).
set -euo pipefail

# Public certificate fingerprint (not a secret): the permanent Prism TV key, first used for 0.2.x builds.
EXPECTED_SHA256="5D:D9:6C:D5:90:3D:B2:18:A6:47:3A:97:3A:59:A6:FB:47:65:0B:A8:76:BD:92:16:C0:C2:D0:87:21:75:12:BE"

KEY_DIR="$RUNNER_TEMP/prism-key"
KS="$KEY_DIR/prism.p12"
mkdir -p "$KEY_DIR"
chmod 700 "$KEY_DIR"

if [ -z "${PRISM_KEYSTORE_B64:-}" ] || [ -z "${PRISM_KEYSTORE_PASSWORD:-}" ]; then
  echo "PRISM_SIGNED=false" >> "$GITHUB_ENV"
  if [ "${REQUIRE_KEY:-false}" = "true" ]; then
    echo "::error title=Signing::The PRISM_KEYSTORE_B64 / PRISM_KEYSTORE_PASSWORD repo secrets aren't set, so this build stops here and nothing is published (docs/release-signing.md)."
    exit 1
  fi
  echo "::warning title=Signing::No permanent signing key for this run: a test build only, signed with a throwaway key, and never published."
  exit 0
fi

PW="$PRISM_KEYSTORE_PASSWORD"
echo "::add-mask::$PW"
echo "$PRISM_KEYSTORE_B64" | base64 -d > "$KS"
if ! keytool -list -storetype PKCS12 -keystore "$KS" -storepass "$PW" -alias prism >/dev/null 2>&1; then
  echo "::error title=Signing::The keystore in PRISM_KEYSTORE_B64 couldn't be opened with PRISM_KEYSTORE_PASSWORD (or has no \"prism\" key). Check both secrets (docs/release-signing.md)."
  exit 1
fi
SHA="$(keytool -list -v -storetype PKCS12 -keystore "$KS" -storepass "$PW" -alias prism | sed -n 's/^.*SHA256: *//p' | head -1)"
if [ "$SHA" != "$EXPECTED_SHA256" ]; then
  echo "::error title=Signing::The key in the repo secrets isn't Prism TV's permanent key (certificate SHA-256 $SHA, expected $EXPECTED_SHA256). TVs couldn't update to builds signed with it, so this build stops here."
  exit 1
fi
{
  echo "PRISM_KEYSTORE=$KS"
  echo "PRISM_KEYSTORE_PASSWORD=$PW"
  echo "PRISM_KEY_ALIAS=prism"
  echo "PRISM_SIGNED=true"
  echo "PRISM_SIGNER_SHA256=$SHA"
} >> "$GITHUB_ENV"
echo "::notice title=Signing::Permanent Prism TV key from repo secrets, certificate SHA-256 $SHA"

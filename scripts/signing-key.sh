#!/usr/bin/env bash
# Finds Prism TV's permanent signing key for this CI run and hands it to Gradle (via $GITHUB_ENV).
#
# Android only installs an update over Prism TV when both are signed with the same key, so every
# build must use one permanent key. Where it comes from, in order:
#   1. Repo secrets PRISM_KEYSTORE_B64 + PRISM_KEYSTORE_PASSWORD (PKCS12, alias "prism"), if set.
#   2. The private draft release tagged "prism-signing-key". Draft releases are visible only to
#      people with write access to the repo (and to this workflow's token), never to the public.
#   3. On the first push build with neither: generate a new key and store it in that draft release.
#   4. Otherwise (e.g. pull requests from forks): a throwaway key, and Software Update won't offer it.
#
# Never publish or delete the "prism-signing-key" draft: publishing makes the key public, deleting it
# means the next build gets a new key and every TV has to uninstall and reinstall Prism TV.
#
# Env: GH_TOKEN, GH_REPO, RUNNER_TEMP, GITHUB_ENV, PRISM_KEYSTORE_B64 / PRISM_KEYSTORE_PASSWORD
# (optional), CAN_CREATE ("true" to allow step 3).
set -euo pipefail

TAG="prism-signing-key"
TITLE="Prism TV signing key (private, never publish or delete)"
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

# 2. The private draft release.
ID="$(draft_id || true)"
if [ -n "$ID" ] && fetch_draft_key "$ID"; then
  SOURCE="draft release $ID"
  use_key "$(cat "$PW_FILE")"
  exit 0
fi

# 3. First permanent-key build: create the key and keep it in a private draft release.
if [ "${CAN_CREATE:-false}" = "true" ] && [ -z "$ID" ]; then
  PW="$(openssl rand -hex 24)"
  echo "::add-mask::$PW"
  keytool -genkeypair -noprompt -storetype PKCS12 -keystore "$KS" -alias prism \
    -keyalg RSA -keysize 4096 -validity 36500 \
    -dname "CN=Prism TV, O=Prism TV Launcher" -storepass "$PW" -keypass "$PW"
  printf '%s' "$PW" > "$PW_FILE"
  NOTES="This draft holds the permanent key that signs every Prism TV build, so new versions install over the old one (Settings › Software Update).

- It's a **draft**, so only people with write access to this repo can see it. **Never publish it** (that would make the key public).
- **Don't delete it.** Without it the next build gets a new key, and every TV has to uninstall and reinstall Prism TV.
- Back it up by downloading both files somewhere private. To keep it in repo secrets instead: \`PRISM_KEYSTORE_B64\` = base64 of prism.p12, \`PRISM_KEYSTORE_PASSWORD\` = the text in prism-p12-password.txt. Secrets win over this draft.

Created by CI run ${GITHUB_RUN_ID:-?} for ${GITHUB_SHA:-?}."
  gh release create "$TAG" --draft --title "$TITLE" --notes "$NOTES" "$KS" "$PW_FILE" >/dev/null
  # If another run created one at the same moment, the oldest draft wins and ours is removed.
  WINNER="$(draft_id || true)"
  MINE="$(gh api --paginate "repos/$GH_REPO/releases?per_page=100" \
    --jq ".[] | select(.draft and .tag_name == \"$TAG\") | .id" | sort -n | tail -1)"
  if [ -n "$WINNER" ] && [ "$WINNER" != "$MINE" ]; then
    gh api -X DELETE "repos/$GH_REPO/releases/$MINE" || true
    fetch_draft_key "$WINNER"
    PW="$(cat "$PW_FILE")"
  fi
  SOURCE="draft release $WINNER (created by this run)"
  use_key "$PW"
  exit 0
fi

# 4. No permanent key available to this run.
[ -n "$ID" ] && echo "::warning title=Signing::Draft release $ID exists but its key files couldn't be read."
echo "PRISM_SIGNED=false" >> "$GITHUB_ENV"
echo "::warning title=Signing::No permanent signing key for this run, so it's signed with a throwaway key and Software Update won't offer it."

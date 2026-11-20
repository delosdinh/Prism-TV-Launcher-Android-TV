#!/usr/bin/env bash
# Publishes a folder of CI output (screenshots, logs) to the `ci-results` branch.
# Usage: publish-ci-results.sh <source-dir> <destination-path-in-branch>
set -euo pipefail
SRC="$1"
DEST="$2"
BRANCH=ci-results
[ -d "$SRC" ] || { echo "nothing to publish ($SRC missing)"; exit 0; }
WORK="$(mktemp -d)"
git -C "$WORK" init -q
git -C "$WORK" remote add origin "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"
if git -C "$WORK" fetch -q --depth 1 origin "$BRANCH" 2>/dev/null; then
  git -C "$WORK" checkout -q -b "$BRANCH" FETCH_HEAD
else
  git -C "$WORK" checkout -q --orphan "$BRANCH"
fi
rm -rf "${WORK:?}/$DEST"
mkdir -p "$WORK/$DEST"
cp -r "$SRC"/. "$WORK/$DEST/"
echo "${GITHUB_SHA}" > "$WORK/$DEST/COMMIT"
git -C "$WORK" add -A
git -C "$WORK" -c user.name="github-actions[bot]" -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
  commit -q -m "CI results: $DEST @ ${GITHUB_SHA::7}" || { echo "no changes"; exit 0; }
for i in 1 2 3 4 5; do
  if git -C "$WORK" push -q origin "HEAD:$BRANCH"; then echo "published to $BRANCH:$DEST"; exit 0; fi
  sleep $((i * 3))
  git -C "$WORK" pull -q --rebase origin "$BRANCH" || true
done
echo "publish failed" >&2
exit 1

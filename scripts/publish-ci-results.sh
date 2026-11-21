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
# Identity in the repo config (not just on commit): the rebase after a rejected push needs it too.
# Without it the rebase failed, HEAD ended up on the remote tip and our commit was silently dropped.
git -C "$WORK" config user.name "github-actions[bot]"
git -C "$WORK" config user.email "41898282+github-actions[bot]@users.noreply.github.com"
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
git -C "$WORK" commit -q -m "CI results: $DEST @ ${GITHUB_SHA::7}" || { echo "no changes"; exit 0; }
MINE="$(git -C "$WORK" rev-parse HEAD)"
for i in 1 2 3 4 5; do
  if git -C "$WORK" push -q origin "HEAD:$BRANCH"; then
    # Our commit (or its rebased copy) must be what we pushed, never just the remote tip.
    if git -C "$WORK" log -1 --format=%s | grep -qF "CI results: $DEST @"; then echo "published to $BRANCH:$DEST"; exit 0; fi
    echo "push went through without our commit" >&2
    exit 1
  fi
  sleep $((i * 3))
  git -C "$WORK" pull -q --rebase origin "$BRANCH" || { git -C "$WORK" rebase --abort 2>/dev/null; git -C "$WORK" reset -q --hard "$MINE"; }
done
echo "publish failed" >&2
exit 1

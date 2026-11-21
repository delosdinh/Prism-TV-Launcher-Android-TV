#!/usr/bin/env bash
# "What's new" for Software Update, newest first, CI/docs-only commits left out: every change since the
# last published update.json (up to 6), topped up with earlier ones to at least 4. A box that skipped a few
# builds (or a big batch followed by a small fix) still sees the changes that matter, not just the last.
# Env: GH_TOKEN, GH_REPO, GITHUB_SHA.
set -uo pipefail
skip='^(CI|Docs|Merge)\b'
PREV="$(gh release download preview -p update.json -O - 2>/dev/null |
  python3 -c 'import json, sys; print(json.load(sys.stdin).get("commit", ""))' 2>/dev/null)"
SINCE=0
if [ -n "$PREV" ]; then
  SINCE="$(gh api "repos/$GH_REPO/compare/$PREV...$GITHUB_SHA" --jq '.commits[].commit.message | split("\n")[0]' 2>/dev/null |
    grep -v -E "$skip" | wc -l | tr -d ' ')"
fi
WANT=$(( SINCE > 4 ? SINCE : 4 ))
WANT=$(( WANT > 6 ? 6 : WANT ))
NOTES="$(gh api "repos/$GH_REPO/commits?sha=$GITHUB_SHA&per_page=40" --jq '.[].commit.message | split("\n")[0]' 2>/dev/null |
  grep -v -E "$skip" | head -n "$WANT" | paste -sd '\n' | sed -e 's/$/;/' -e '$ s/;$//' | paste -sd ' ')"
if [ -z "$NOTES" ]; then
  NOTES="$(git log -1 --format=%s)"
  echo "$NOTES" | grep -q -E "$skip" && NOTES="Behind-the-scenes fixes."
fi
printf '%s' "$NOTES"

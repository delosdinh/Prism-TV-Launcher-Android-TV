#!/usr/bin/env bash
# "What's new" for Software Update: the subjects of the commits since the last published update.json
# (newest first, CI/docs-only commits left out, at most 6). Falls back to this commit's subject.
# Env: GH_TOKEN, GH_REPO, GITHUB_SHA.
set -uo pipefail
skip='^(CI|Docs|Merge)\b'
PREV="$(gh release download preview -p update.json -O - 2>/dev/null |
  python3 -c 'import json, sys; print(json.load(sys.stdin).get("commit", ""))' 2>/dev/null)"
NOTES=""
if [ -n "$PREV" ]; then
  NOTES="$(gh api "repos/$GH_REPO/compare/$PREV...$GITHUB_SHA" --jq '.commits[].commit.message | split("\n")[0]' 2>/dev/null |
    grep -v -E "$skip" | tac | head -6 | paste -sd '\n' | sed -e 's/$/;/' -e '$ s/;$//' | paste -sd ' ')"
fi
if [ -z "$NOTES" ]; then
  NOTES="$(git log -1 --format=%s)"
  echo "$NOTES" | grep -q -E "$skip" && NOTES="Behind-the-scenes fixes."
fi
printf '%s' "$NOTES"

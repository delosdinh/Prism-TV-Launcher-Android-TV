#!/usr/bin/env bash
# Converts screen recordings in a folder to looping GIFs (960px wide).
set -uo pipefail
DIR="${1:-shots}"
for f in "$DIR"/*.mp4; do
  [ -f "$f" ] || continue
  out="${f%.mp4}.gif"
  ffmpeg -loglevel error -y -i "$f" \
    -vf "fps=24,scale=960:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=200:stats_mode=diff[p];[b][p]paletteuse=dither=sierra2_4a" \
    "$out" && echo "gif: $out ($(du -h "$out" | cut -f1))"
done

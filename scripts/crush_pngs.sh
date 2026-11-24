#!/usr/bin/env bash
set -euo pipefail

if [[ $# -eq 0 ]]; then
  set -- app/src/test/snapshots/*.png
fi

jobs=$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)
printf '%s\0' "$@" | xargs -0 -n 1 -P "$jobs" bash -ceu '
  file="$1"
  tmp=$(mktemp "${file}.pngcrush.XXXXXX")
  trap "rm -f \"\$tmp\"" EXIT
  pngcrush -q -s -reduce -rem allb "$file" "$tmp"
  old_size=$(wc -c < "$file")
  new_size=$(wc -c < "$tmp")
  if (( new_size < old_size )); then
    mv "$tmp" "$file"
  fi
' _

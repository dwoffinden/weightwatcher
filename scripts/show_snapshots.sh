#!/usr/bin/env bash
set -euo pipefail

DIR="${1:-app/src/test/snapshots}"
IMPLS=(glance_light glance_dark remote_v6_light remote_v6_dark remote_v7_light remote_v7_dark)
SCENARIOS=(active_20m active_2h stale_24h syncing unauthorized no_records multi_3_2x1 multi_5_3x1)
# Relative column widths matching the 1x1, 2x1, and 3x1 widget aspect ratios.
SPANS=(1 1 1 1 1 1 2 3)

LABEL_W=17
HEADER_H=2
COLS=$(tput cols)
LINES=$(tput lines)

TOTAL_SPANS=0
for span in "${SPANS[@]}"; do
  TOTAL_SPANS=$(( TOTAL_SPANS + span ))
done

UNIT_W=$(( (COLS - LABEL_W) / TOTAL_SPANS ))
(( UNIT_W < 6 )) && UNIT_W=6
(( UNIT_W > 14 )) && UNIT_W=14

CELL_H=$(( (LINES - HEADER_H - 2) / ${#IMPLS[@]} ))
(( CELL_H > 8 )) && CELL_H=8

kitten icat --clear
clear

x=$LABEL_W
COL_X=()
COL_W=()
for c in "${!SCENARIOS[@]}"; do
  w=$(( SPANS[c] * UNIT_W ))
  COL_X+=("$x")
  COL_W+=("$w")
  tput cup 0 "$x"
  printf "%-${w}s" "${SCENARIOS[$c]}"
  x=$(( x + w ))
done

for r in "${!IMPLS[@]}"; do
  y=$(( HEADER_H + r * CELL_H ))
  tput cup $(( y + CELL_H / 2 )) 0
  printf "%-${LABEL_W}s" "${IMPLS[$r]}"

  for c in "${!SCENARIOS[@]}"; do
    img="${DIR}/${IMPLS[$r]}_${SCENARIOS[$c]}.png"
    [[ -f "$img" ]] || continue
    kitten icat --place="$(( COL_W[c] - 1 ))x${CELL_H}@${COL_X[c]}x${y}" "$img"
  done
done

tput cup $(( HEADER_H + ${#IMPLS[@]} * CELL_H + 1 )) 0

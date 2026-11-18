#!/usr/bin/env bash
set -euo pipefail

DIR="${1:-app/src/test/snapshots}"
IMPLS=(glance_light glance_dark remote_v6_light remote_v6_dark remote_v7_light remote_v7_dark)
SCENARIOS=(active_20m active_2h stale_24h syncing unauthorized no_records)

LABEL_W=17
HEADER_H=2
COLS=$(tput cols)
LINES=$(tput lines)

CELL_W=$(( (COLS - LABEL_W) / ${#SCENARIOS[@]} ))
CELL_H=$(( (LINES - HEADER_H - 2) / ${#IMPLS[@]} ))
(( CELL_W > 18 )) && CELL_W=18
(( CELL_H > 8 )) && CELL_H=8

kitten icat --clear
clear

for c in "${!SCENARIOS[@]}"; do
  tput cup 0 $(( LABEL_W + c * CELL_W ))
  printf "%-${CELL_W}s" "${SCENARIOS[$c]}"
done

for r in "${!IMPLS[@]}"; do
  y=$(( HEADER_H + r * CELL_H ))
  tput cup $(( y + CELL_H / 2 )) 0
  printf "%-${LABEL_W}s" "${IMPLS[$r]}"

  for c in "${!SCENARIOS[@]}"; do
    img="${DIR}/${IMPLS[$r]}_${SCENARIOS[$c]}.png"
    [[ -f "$img" ]] || continue
    x=$(( LABEL_W + c * CELL_W ))
    kitten icat --place="$(( CELL_W - 1 ))x${CELL_H}@${x}x${y}" "$img"
  done
done

tput cup $(( HEADER_H + ${#IMPLS[@]} * CELL_H + 1 )) 0

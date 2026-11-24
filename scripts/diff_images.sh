#!/usr/bin/env bash
# Visual terminal diff for PNGs using ImageMagick + `kitten icat`.
#
# Usage:
#   ./scripts/diff_images.sh [options] [git-diff args...]
#   GIT_EXTERNAL_DIFF=./scripts/diff_images.sh git --no-pager diff [args...]
#
# Options (wrapper mode):
#   --all   Render pixel-identical images too (normally summarised as one line).
set -euo pipefail

if [[ -t 1 ]]; then
  BOLD=$(tput bold 2>/dev/null || true)
  RED=$(tput setaf 1 2>/dev/null || true)
  GREEN=$(tput setaf 2 2>/dev/null || true)
  YELLOW=$(tput setaf 3 2>/dev/null || true)
  RESET=$(tput sgr0 2>/dev/null || true)
else
  BOLD=""
  RED=""
  GREEN=""
  YELLOW=""
  RESET=""
fi

show_stdin() {
  if [[ ! -t 1 ]]; then
    cat >/dev/null
    printf '  (stdout is not a tty; use git --no-pager diff or ./scripts/diff_images.sh to render images)\n'
    return 0
  fi
  kitten icat --align=left --transfer-mode=stream --stdin=yes
}

show_montage() {
  if [[ ! -t 1 ]]; then
    printf '  (stdout is not a tty; use git --no-pager diff or ./scripts/diff_images.sh to render images)\n'
    return 0
  fi
  montage -depth 8 -pointsize 24 -background "#222222" -fill "#eeeeee" "$@" -geometry "+8+4" png:- \
    | show_stdin
}

run_driver() {
  local path="$1"
  local old_file="$2"
  local new_file="$5"

  case "$path" in
    *.png | *.PNG) ;;
    *)
      GIT_EXTERNAL_DIFF= git --no-pager diff --no-ext-diff --no-index --color=always -- "$old_file" "$new_file" || true
      return 0
      ;;
  esac

  if [[ "$old_file" == "/dev/null" ]]; then
    local new_bytes
    new_bytes=$(wc -c < "$new_file" | tr -d ' ')
    printf '%s%s%s  %s[added, %s B]%s\n' "$BOLD" "$path" "$RESET" "$GREEN" "$new_bytes" "$RESET"
    show_stdin < "$new_file"
    return 0
  fi

  if [[ "$new_file" == "/dev/null" ]]; then
    local old_bytes
    old_bytes=$(wc -c < "$old_file" | tr -d ' ')
    printf '%s%s%s  %s[deleted, %s B]%s\n' "$BOLD" "$path" "$RESET" "$RED" "$old_bytes" "$RESET"
    show_stdin < "$old_file"
    return 0
  fi

  local old_bytes new_bytes delta
  old_bytes=$(wc -c < "$old_file" | tr -d ' ')
  new_bytes=$(wc -c < "$new_file" | tr -d ' ')
  delta=$((new_bytes - old_bytes))

  local tmpdir diff_img
  tmpdir=$(mktemp -d)
  trap 'rm -rf "$tmpdir"' RETURN
  diff_img="${tmpdir}/diff.png"

  local metric_out=""
  local cmp_rc=0
  metric_out=$(compare -metric AE "$old_file" "$new_file" "$diff_img" 2>&1) || cmp_rc=$?

  if [[ $cmp_rc -eq 0 ]]; then
    printf '%s%s%s  (%s -> %s B, %+d B)  %s[identical pixels]%s\n' \
      "$BOLD" "$path" "$RESET" "$old_bytes" "$new_bytes" "$delta" "$GREEN" "$RESET"
    if [[ "${_DIFF_IMAGES_SHOW_ALL:-0}" != "1" ]]; then
      return 0
    fi
    show_montage -label "old" "$old_file" -label "new" "$new_file" -tile 2x1
    return 0
  fi

  if [[ $cmp_rc -eq 1 ]]; then
    local diff_px="${metric_out%% *}"
    printf '%s%s%s  (%s -> %s B, %+d B)  %s[%s differing px]%s\n' \
      "$BOLD" "$path" "$RESET" "$old_bytes" "$new_bytes" "$delta" "$YELLOW" "$diff_px" "$RESET"
    show_montage -label "old" "$old_file" -label "diff" "$diff_img" -label "new" "$new_file" -tile 3x1
    return 0
  fi

  # Exit code 2 typically means image dimensions differ.
  printf '%s%s%s  (%s -> %s B, %+d B)  %s[dimensions changed]%s\n' \
    "$BOLD" "$path" "$RESET" "$old_bytes" "$new_bytes" "$delta" "$YELLOW" "$RESET"
  show_montage -label "old" "$old_file" -label "new" "$new_file" -tile 2x1
}

if [[ -n "${_DIFF_IMAGES_DRIVER:-}" ]] || { [[ $# -eq 7 ]] && [[ "$4" =~ ^([0-7]{6}|\.)$ ]] && [[ "$7" =~ ^([0-7]{6}|\.)$ ]]; }; then
  run_driver "$@"
  exit 0
fi

show_all=0
has_dashdash=0
git_args=()
for arg in "$@"; do
  if [[ "$arg" == "--all" ]]; then
    show_all=1
  else
    if [[ "$arg" == "--" ]]; then
      has_dashdash=1
    fi
    git_args+=("$arg")
  fi
done

script_path=$(cd "$(dirname "$0")" && pwd)/$(basename "$0")
if (( has_dashdash )); then
  _DIFF_IMAGES_DRIVER=1 \
  _DIFF_IMAGES_SHOW_ALL="$show_all" \
  GIT_EXTERNAL_DIFF="$script_path" \
    git --no-pager diff "${git_args[@]}"
else
  _DIFF_IMAGES_DRIVER=1 \
  _DIFF_IMAGES_SHOW_ALL="$show_all" \
  GIT_EXTERNAL_DIFF="$script_path" \
    git --no-pager diff "${git_args[@]}" -- '*.png'
fi

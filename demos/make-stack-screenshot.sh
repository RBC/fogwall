#!/usr/bin/env bash
# Composite two dashboard screenshots into one "stacked cards" image for the README.
#
# Back screenshot (e.g. push detail/timeline) is shown in full, un-cropped. Front
# screenshot (e.g. diff/attestation) is resized narrower, given a drop shadow, and
# layered on top offset to the right, so the back screenshot peeks out on the left,
# top, and bottom. Canvas background is transparent so it blends with GitHub's page
# background in both light and dark theme.
#
# Usage:
#   bash demos/make-stack-screenshot.sh <back.png> <front.png> <output.png>
#
# Example (regenerating the current README image):
#   bash demos/make-stack-screenshot.sh demos/demo-ui1.png demos/demo-ui2.png demos/demo-ui-stack.png
#
# Requires ImageMagick (`magick` on PATH). Tune the variables below if screenshot
# dimensions or content layout change significantly — values here were tuned by eye
# against 1256x1360 (back) and 1854x1451 (front) source screenshots.
set -euo pipefail

BACK_SRC="${1:?usage: make-stack-screenshot.sh <back.png> <front.png> <output.png>}"
FRONT_SRC="${2:?usage: make-stack-screenshot.sh <back.png> <front.png> <output.png>}"
OUT="${3:?usage: make-stack-screenshot.sh <back.png> <front.png> <output.png>}"

WORKDIR="$(mktemp -d)"
trap 'rm -rf "${WORKDIR}"' EXIT

# --- Tunables -----------------------------------------------------------------
BACK_WIDTH=880       # back screenshot resized to this width, full height kept (no crop)
FRONT_WIDTH=810      # front screenshot resized narrower than back, to peek edges
SHADOW_BORDER=30     # transparent padding added before the shadow blur
SHADOW_SPEC="55x12+0+10"  # shadow: <blur>x<sigma>+<x-offset>+<y-offset>

BACK_X=10            # back placement on canvas
BACK_Y=10
FRONT_X_PCT=32       # front's left edge, as a percentage of BACK_WIDTH (the "offset right")
FRONT_Y=180          # front's top edge, in px from canvas top

CANVAS_MARGIN_R=10   # extra transparent margin right of the front card
CANVAS_MARGIN_B=10   # extra transparent margin below the taller of the two cards
# -------------------------------------------------------------------------------

FRONT_X=$(( BACK_WIDTH * FRONT_X_PCT / 100 ))

magick "${BACK_SRC}" +repage -resize "${BACK_WIDTH}x" "${WORKDIR}/back.png"
magick "${FRONT_SRC}" +repage -resize "${FRONT_WIDTH}x" "${WORKDIR}/front.png"

# Add a drop shadow to the front card (this pads its canvas outward).
magick "${WORKDIR}/front.png" -bordercolor none -border "${SHADOW_BORDER}" \
  \( +clone -background black -shadow "${SHADOW_SPEC}" \) +swap \
  -background none -layers merge +repage "${WORKDIR}/front_shadow.png"

BACK_W=$(identify -format "%w" "${WORKDIR}/back.png")
BACK_H=$(identify -format "%h" "${WORKDIR}/back.png")
FS_W=$(identify -format "%w" "${WORKDIR}/front_shadow.png")
FS_H=$(identify -format "%h" "${WORKDIR}/front_shadow.png")

CANVAS_W=$(( FRONT_X + FS_W + CANVAS_MARGIN_R ))
CANVAS_W=$(( CANVAS_W > (BACK_X + BACK_W) ? CANVAS_W : (BACK_X + BACK_W) ))
CANVAS_H=$(( FRONT_Y + FS_H ))
CANVAS_H=$(( CANVAS_H > (BACK_Y + BACK_H) ? CANVAS_H : (BACK_Y + BACK_H) ))
CANVAS_H=$(( CANVAS_H + CANVAS_MARGIN_B ))

magick -size "${CANVAS_W}x${CANVAS_H}" xc:none \
  "${WORKDIR}/back.png" -geometry "+${BACK_X}+${BACK_Y}" -composite \
  "${WORKDIR}/front_shadow.png" -geometry "+${FRONT_X}+${FRONT_Y}" -composite \
  -depth 8 -define png:compression-level=9 -define png:compression-filter=5 \
  "${OUT}"

echo "Wrote ${OUT} ($(identify -format '%wx%h' "${OUT}"))"

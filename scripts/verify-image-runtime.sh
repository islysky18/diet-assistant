#!/usr/bin/env bash
set -euo pipefail

MAGICK_EXECUTABLE="${DIET_ASSISTANT_IMAGEMAGICK_EXECUTABLE:-magick}"
EXPECTED_IMAGEMAGICK_VERSION="${DIET_ASSISTANT_EXPECTED_IMAGEMAGICK_VERSION:-7.1.2-29}"
EXPECTED_LIBHEIF_VERSION="${DIET_ASSISTANT_EXPECTED_LIBHEIF_VERSION:-1.23.1}"
VERIFY_DIR="$(mktemp -d)"
trap 'rm -rf "$VERIFY_DIR"' EXIT

"$MAGICK_EXECUTABLE" -version | grep -F "ImageMagick ${EXPECTED_IMAGEMAGICK_VERSION}"
"$MAGICK_EXECUTABLE" identify -list format | grep -E '^[[:space:]]*HEIC[[:space:]].*r'
"$MAGICK_EXECUTABLE" identify -list format | grep -E '^[[:space:]]*HEIF[[:space:]].*r'
"$MAGICK_EXECUTABLE" identify -list format | grep -F "(${EXPECTED_LIBHEIF_VERSION})"

"$MAGICK_EXECUTABLE" -size 2x2 xc:'#4b7bec' -strip "$VERIFY_DIR/source.png"
heif-enc --quality 90 --output "$VERIFY_DIR/synthetic.heic" "$VERIFY_DIR/source.png"
"$MAGICK_EXECUTABLE" "$VERIFY_DIR/synthetic.heic[0]" -auto-orient -strip -quality 95 "$VERIFY_DIR/decoded.jpg"

test -s "$VERIFY_DIR/decoded.jpg"
test "$(od -An -tx1 -N3 "$VERIFY_DIR/decoded.jpg" | tr -d ' \n')" = "ffd8ff"
"$MAGICK_EXECUTABLE" identify -format '%m %w %h' "$VERIFY_DIR/decoded.jpg" | grep -Fx 'JPEG 2 2'

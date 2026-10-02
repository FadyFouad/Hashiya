#!/usr/bin/env bash
# Renders the raster app icons from docs/brand/*.svg.
# The Android adaptive icon itself is vector (res/drawable/ic_launcher_*.xml); the
# rasters here are the API 24-25 fallbacks, the Play Store icon and the iOS icons.
# Needs rsvg-convert, ImageMagick and Pillow (brew install librsvg imagemagick; pip3 install pillow).
set -euo pipefail
cd "$(dirname "$0")/.."

brand=docs/brand
res=android/app/src/main/res
ios=ios/Hashiya/Assets.xcassets/AppIcon.appiconset
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

rsvg-convert -w 1024 "$brand/app-icon.svg" -o "$tmp/icon.png"
rsvg-convert -w 1024 "$brand/app-icon-dark.svg" -o "$tmp/icon-dark.png"
rsvg-convert -w 1024 "$brand/app-icon-tinted.svg" -o "$tmp/icon-tinted.png"

# iOS: full-bleed and opaque; the system applies the mask.
magick "$tmp/icon.png" -alpha off PNG24:"$ios/AppIcon.png"
magick "$tmp/icon-dark.png" -alpha off PNG24:"$ios/AppIcon-Dark.png"
magick "$tmp/icon-tinted.png" -alpha off -colorspace Gray "$ios/AppIcon-Tinted.png"

# Play Store: 512px full-bleed square; Play applies the mask.
magick "$tmp/icon.png" -alpha off -resize 512x512 android/app/src/main/ic_launcher-playstore.png

# Legacy launcher icons (API 24-25): a 44dp shape with 2dp padding on a 48dp canvas.
python3 - "$tmp/icon.png" "$res" <<'PY'
import sys
from PIL import Image, ImageDraw

src, res = sys.argv[1], sys.argv[2]
icon = Image.open(src).convert("RGBA")
for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    scale = 4  # draw masks large, then downsample for smooth edges
    inner = size * 44 // 48
    offset = (size - inner) // 2
    for name, shape in [("ic_launcher", "square"), ("ic_launcher_round", "circle")]:
        mask = Image.new("L", (inner * scale, inner * scale), 0)
        draw = ImageDraw.Draw(mask)
        box = (0, 0, inner * scale - 1, inner * scale - 1)
        if shape == "circle":
            draw.ellipse(box, fill=255)
        else:
            draw.rounded_rectangle(box, radius=inner * scale * 8 // 44, fill=255)
        mask = mask.resize((inner, inner), Image.LANCZOS)
        shaped = icon.resize((inner, inner), Image.LANCZOS)
        shaped.putalpha(mask)
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        canvas.paste(shaped, (offset, offset), shaped)
        canvas.save(f"{res}/mipmap-{density}/{name}.webp", lossless=True)
PY
echo "App icons written."

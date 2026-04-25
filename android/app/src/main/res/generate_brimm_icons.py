"""Regenerate Brimm launcher icons from a source PNG.

Steps:
  1. Open source.
  2. Auto-trim margins by pixel color delta (handles both white AND near-white anti-aliased edges).
  3. Sample dominant background color from the trimmed image's corner — this becomes the
     adaptive-icon background swatch so the squircle blends seamlessly with whatever mask
     the device applies on Android 8+.
  4. Resize to each mipmap density's launcher size and save as webp.
  5. Resize to 432px and save as ic_launcher_foreground.png (the adaptive foreground asset).

Run from repo root:
  py "android/app/src/main/res/generate_brimm_icons.py"
"""
from __future__ import annotations
from pathlib import Path
from PIL import Image
import sys

HERE = Path(__file__).resolve().parent
SRC = Path(r"C:\Users\12566\projects\pantree-landing\screenshots\_real\1000013687.png")

# Android launcher sizes, in px.
DENSITIES = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}
ADAPTIVE_FOREGROUND_PX = 432  # 108dp × 4 (xxxhdpi), per AOSP recommendation

def near_color(rgb, target, tol=12):
    return all(abs(a - b) <= tol for a, b in zip(rgb[:3], target))

def auto_trim(img: Image.Image) -> Image.Image:
    """Crop margins of any solid color near top-left corner pixel.

    Used instead of getbbox() because getbbox only respects fully-transparent pixels — it
    doesn't catch a white margin around an opaque-on-white image. We sample the corner pixel
    as the "background" and trim every row/col uniformly that color.
    """
    img = img.convert("RGBA")
    w, h = img.size
    px = img.load()
    bg = px[0, 0][:3]

    def row_is_bg(y):
        return all(near_color(px[x, y], bg) for x in range(w))

    def col_is_bg(x):
        return all(near_color(px[x, y], bg) for y in range(h))

    top = 0
    while top < h and row_is_bg(top):
        top += 1
    bottom = h - 1
    while bottom > top and row_is_bg(bottom):
        bottom -= 1
    left = 0
    while left < w and col_is_bg(left):
        left += 1
    right = w - 1
    while right > left and col_is_bg(right):
        right -= 1

    if (top, left, bottom, right) == (0, 0, h - 1, w - 1):
        return img  # nothing to trim
    cropped = img.crop((left, top, right + 1, bottom + 1))
    print(f"trimmed {w}x{h} -> {cropped.width}x{cropped.height}")
    return cropped

def sample_squircle_color(img: Image.Image) -> tuple[int, int, int]:
    """Pick a pixel ~10% in from the corner — that's the squircle interior, not the b."""
    w, h = img.size
    return img.convert("RGB").getpixel((w // 10, h // 10))

def main():
    if not SRC.exists():
        print(f"missing source: {SRC}", file=sys.stderr)
        sys.exit(1)
    src = Image.open(SRC).convert("RGBA")
    trimmed = auto_trim(src)
    bg_rgb = sample_squircle_color(trimmed)
    print(f"sampled background: rgb{bg_rgb} -> #{bg_rgb[0]:02x}{bg_rgb[1]:02x}{bg_rgb[2]:02x}")

    # 1. Per-density legacy launcher icons (square + round both reuse the same PNG).
    for folder, size in DENSITIES.items():
        out_dir = HERE / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        resized = trimmed.resize((size, size), Image.LANCZOS)
        # Drop the legacy webp first to avoid stale companion files.
        for stale in (out_dir / "ic_launcher.webp", out_dir / "ic_launcher_round.webp"):
            if stale.exists():
                stale.unlink()
        # Write fresh PNGs — Android happily loads .png mipmaps the same as .webp.
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            resized.save(out_dir / name, "PNG", optimize=True)
        print(f"wrote {folder}: {size}x{size}")

    # 2. Adaptive foreground at 432px goes in drawable. Replaces any prior ic_launcher_foreground.
    fg_dir = HERE / "drawable"
    fg_dir.mkdir(exist_ok=True)
    fg = trimmed.resize((ADAPTIVE_FOREGROUND_PX, ADAPTIVE_FOREGROUND_PX), Image.LANCZOS)
    # Remove any previous XML/PNG at same name so the resource resolver uses the new asset.
    for stale_name in ("ic_launcher_foreground.xml", "ic_launcher_foreground.webp"):
        stale = fg_dir / stale_name
        if stale.exists():
            stale.unlink()
    fg.save(fg_dir / "ic_launcher_foreground.png", "PNG", optimize=True)
    print(f"wrote drawable/ic_launcher_foreground.png: {ADAPTIVE_FOREGROUND_PX}x{ADAPTIVE_FOREGROUND_PX}")

    # 3. Update the adaptive-icon background color so seamless blending on Android 8+.
    color_hex = f"#{bg_rgb[0]:02x}{bg_rgb[1]:02x}{bg_rgb[2]:02x}"
    colors_dir = HERE / "values"
    colors_path = colors_dir / "ic_launcher_background.xml"
    colors_path.write_text(
        f'<?xml version="1.0" encoding="utf-8"?>\n'
        f'<resources>\n'
        f'    <color name="ic_launcher_background">{color_hex}</color>\n'
        f'</resources>\n',
        encoding="utf-8",
    )
    print(f"wrote values/ic_launcher_background.xml -> {color_hex}")

if __name__ == "__main__":
    main()

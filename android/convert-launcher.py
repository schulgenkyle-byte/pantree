"""Convert unnamed.png into Android launcher icons at every density.

Generates:
  mipmap-mdpi    / ic_launcher.webp         (48)
  mipmap-hdpi    / ic_launcher.webp         (72)
  mipmap-xhdpi   / ic_launcher.webp         (96)
  mipmap-xxhdpi  / ic_launcher.webp         (144)
  mipmap-xxxhdpi / ic_launcher.webp         (192)
  mipmap-*       / ic_launcher_round.webp   (same sizes, circle-masked)
  mipmap-anydpi-v26 / ic_launcher.xml       (adaptive icon)
  drawable       / ic_launcher_foreground.webp (432)
  values         / ic_launcher_background.xml (Cream color)
"""
import os, sys
from PIL import Image, ImageDraw

SRC = "C:/Users/12566/Downloads/unnamed.png"
RES = "C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/android/app/src/main/res"

DENSITIES = {
  "mdpi": 48,
  "hdpi": 72,
  "xhdpi": 96,
  "xxhdpi": 144,
  "xxxhdpi": 192,
}

CREAM = "#F5EFE4"

def trim_whitespace(img):
    """Crop surrounding cream/white border so mark fills more of the icon."""
    bg = img.convert("RGB").getpixel((0, 0))
    gray = Image.new("RGB", img.size, bg)
    diff = Image.eval(Image.new("RGB", img.size), lambda x: 0)
    from PIL import ImageChops
    diff = ImageChops.difference(img.convert("RGB"), gray)
    bbox = diff.getbbox()
    if bbox:
        # pad 4% margin
        w, h = img.size
        pad = int(min(w, h) * 0.04)
        l, t, r, b = bbox
        l = max(0, l - pad); t = max(0, t - pad)
        r = min(w, r + pad); b = min(h, b + pad)
        return img.crop((l, t, r, b))
    return img

def mask_round(img, size):
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size, size), fill=255)
    out = Image.new("RGBA", (size, size))
    out.paste(img, (0, 0), mask)
    return out

def main():
    print(f"Source: {SRC}")
    src = Image.open(SRC).convert("RGBA")
    src = trim_whitespace(src)
    print(f"Cropped source: {src.size}")

    # Square-pad so the mark is centered on a square canvas before resize
    w, h = src.size
    side = max(w, h)
    canvas = Image.new("RGBA", (side, side), (245, 239, 228, 255))  # Cream
    canvas.paste(src, ((side - w) // 2, (side - h) // 2), src)

    for name, size in DENSITIES.items():
        out_dir = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(out_dir, exist_ok=True)
        sq = canvas.resize((size, size), Image.LANCZOS)
        sq.save(os.path.join(out_dir, "ic_launcher.webp"), "WEBP", quality=92)
        rd = mask_round(sq, size)
        rd.save(os.path.join(out_dir, "ic_launcher_round.webp"), "WEBP", quality=92)
        print(f"  wrote mipmap-{name}/ ({size}x{size})")

    # Adaptive icon (API 26+): foreground + background
    # Foreground: 108x108dp with a 66dp safe zone. PNG at 432px has 72px padding each side.
    fg_size = 432
    mark_size = int(fg_size * 0.66)
    fg = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
    mark = canvas.resize((mark_size, mark_size), Image.LANCZOS)
    offset = (fg_size - mark_size) // 2
    fg.paste(mark, (offset, offset), mark)
    drawable_dir = os.path.join(RES, "drawable")
    os.makedirs(drawable_dir, exist_ok=True)
    fg.save(os.path.join(drawable_dir, "ic_launcher_foreground.webp"), "WEBP", quality=92)
    print(f"  wrote drawable/ic_launcher_foreground.webp ({fg_size}x{fg_size})")

    # Adaptive icon XML
    mipmap_anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(mipmap_anydpi, exist_ok=True)
    adaptive_xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
'''
    with open(os.path.join(mipmap_anydpi, "ic_launcher.xml"), "w") as f:
        f.write(adaptive_xml)
    with open(os.path.join(mipmap_anydpi, "ic_launcher_round.xml"), "w") as f:
        f.write(adaptive_xml)
    print("  wrote mipmap-anydpi-v26/ic_launcher.xml + round")

    # Background color
    colors_path = os.path.join(RES, "values", "ic_launcher_background.xml")
    os.makedirs(os.path.dirname(colors_path), exist_ok=True)
    with open(colors_path, "w") as f:
        f.write(f'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">{CREAM}</color>
</resources>
''')
    print("  wrote values/ic_launcher_background.xml")

    # Play Store icon: 512x512 PNG with the full mark on cream
    play = canvas.resize((512, 512), Image.LANCZOS)
    play_path = "C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/android/playstore-icon-512.png"
    play.save(play_path, "PNG")
    print(f"  wrote {play_path} (512x512) for Play Console")

if __name__ == "__main__":
    main()
    print("\nDONE.")

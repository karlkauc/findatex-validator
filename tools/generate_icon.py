#!/usr/bin/env python3
"""Render the TPT Validator app icon at multiple sizes.

Design:
  - Deep-navy rounded-square tile (matches the app's top-bar gradient).
  - White document silhouette with a folded corner and table-row lines
    suggesting tabular data (TPT file).
  - Green tick badge in the lower-right corner = validation / quality.

Outputs:
  - javafx-app/src/main/resources/icons/icon-{16,32,48,64,128,256,512}.png  (UI)
  - package/icon.png   (master 512 px, jpackage --icon for Linux)
  - package/icon.ico   (multi-size, jpackage --icon for Windows)
  - package/icon.icns  (multi-size, jpackage --icon for macOS)
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES_DIR = ROOT / "javafx-app" / "src" / "main" / "resources" / "icons"
PKG_DIR = ROOT / "package"
RES_DIR.mkdir(parents=True, exist_ok=True)
PKG_DIR.mkdir(parents=True, exist_ok=True)

# Palette — aligned with javafx-app/src/main/resources/css/app.css
NAVY_TOP    = (42, 79, 122)    # #2a4f7a
NAVY_BOTTOM = (23, 48, 73)     # #173049
WHITE       = (255, 255, 255)
PAPER_LINE  = (200, 210, 224)
TABLE_HEAD  = (31, 111, 235)   # #1f6feb
GREEN       = (26, 127, 58)    # #1a7f3a
GREEN_DARK  = (16, 99, 44)
SHADOW      = (0, 0, 0, 80)


def render(size: int = 512) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    # 1. Background tile — vertical gradient with rounded corners.
    radius = int(size * 0.18)
    bg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    grad = Image.new("RGBA", (1, size), (0, 0, 0, 0))
    for y in range(size):
        t = y / max(size - 1, 1)
        r = int(NAVY_TOP[0] + (NAVY_BOTTOM[0] - NAVY_TOP[0]) * t)
        g = int(NAVY_TOP[1] + (NAVY_BOTTOM[1] - NAVY_TOP[1]) * t)
        b = int(NAVY_TOP[2] + (NAVY_BOTTOM[2] - NAVY_TOP[2]) * t)
        grad.putpixel((0, y), (r, g, b, 255))
    grad = grad.resize((size, size))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    bg.paste(grad, (0, 0), mask)
    img = Image.alpha_composite(img, bg)

    # 2. Document — white card with folded corner.
    draw = ImageDraw.Draw(img)
    pad = int(size * 0.18)
    doc_w = size - 2 * pad
    doc_h = int(doc_w * 1.18)
    doc_x0 = pad
    doc_y0 = (size - doc_h) // 2 - int(size * 0.02)
    doc_x1 = doc_x0 + doc_w
    doc_y1 = doc_y0 + doc_h
    fold = int(doc_w * 0.22)

    # subtle drop shadow
    shadow_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow_layer)
    offset = max(2, size // 60)
    sd.polygon(
        [(doc_x0 + offset, doc_y0 + offset),
         (doc_x1 - fold + offset, doc_y0 + offset),
         (doc_x1 + offset, doc_y0 + fold + offset),
         (doc_x1 + offset, doc_y1 + offset),
         (doc_x0 + offset, doc_y1 + offset)],
        fill=SHADOW)
    shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(radius=max(2, size // 80)))
    img = Image.alpha_composite(img, shadow_layer)
    draw = ImageDraw.Draw(img)

    # paper body (with cut corner)
    paper_poly = [
        (doc_x0, doc_y0),
        (doc_x1 - fold, doc_y0),
        (doc_x1, doc_y0 + fold),
        (doc_x1, doc_y1),
        (doc_x0, doc_y1),
    ]
    draw.polygon(paper_poly, fill=WHITE)

    # folded corner triangle
    fold_poly = [
        (doc_x1 - fold, doc_y0),
        (doc_x1 - fold, doc_y0 + fold),
        (doc_x1, doc_y0 + fold),
    ]
    draw.polygon(fold_poly, fill=PAPER_LINE)

    # 3. Table rows — header bar plus 4 data rows.
    inner_pad = int(doc_w * 0.10)
    row_x0 = doc_x0 + inner_pad
    row_x1 = doc_x1 - inner_pad
    header_y = doc_y0 + int(doc_h * 0.20)
    header_h = max(int(doc_h * 0.085), 4)
    draw.rounded_rectangle(
        (row_x0, header_y, row_x1, header_y + header_h),
        radius=max(2, header_h // 3),
        fill=TABLE_HEAD,
    )

    rows_top = header_y + int(header_h * 2.4)
    row_h = max(int(doc_h * 0.055), 3)
    row_gap = max(int(doc_h * 0.03), 2)
    for i in range(4):
        y0 = rows_top + i * (row_h + row_gap)
        # alternate full-width vs. truncated rows for a "data" feel
        x_end = row_x1 if i % 2 == 0 else row_x0 + int((row_x1 - row_x0) * 0.65)
        draw.rounded_rectangle(
            (row_x0, y0, x_end, y0 + row_h),
            radius=max(1, row_h // 3),
            fill=PAPER_LINE,
        )

    # 4. Green tick badge — overlaps the bottom-right of the document.
    badge_r = int(size * 0.22)
    badge_cx = doc_x1 - int(badge_r * 0.35)
    badge_cy = doc_y1 - int(badge_r * 0.35)
    # badge shadow
    badge_shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bs = ImageDraw.Draw(badge_shadow)
    bs.ellipse(
        (badge_cx - badge_r + offset, badge_cy - badge_r + offset,
         badge_cx + badge_r + offset, badge_cy + badge_r + offset),
        fill=(0, 0, 0, 110),
    )
    badge_shadow = badge_shadow.filter(ImageFilter.GaussianBlur(radius=max(2, size // 70)))
    img = Image.alpha_composite(img, badge_shadow)
    draw = ImageDraw.Draw(img)

    # white ring + green disk
    ring = max(int(size * 0.018), 2)
    draw.ellipse(
        (badge_cx - badge_r, badge_cy - badge_r, badge_cx + badge_r, badge_cy + badge_r),
        fill=WHITE,
    )
    draw.ellipse(
        (badge_cx - badge_r + ring, badge_cy - badge_r + ring,
         badge_cx + badge_r - ring, badge_cy + badge_r - ring),
        fill=GREEN,
        outline=GREEN_DARK,
        width=max(1, ring // 3),
    )

    # checkmark
    tick_w = max(int(size * 0.022), 3)
    p1 = (badge_cx - int(badge_r * 0.45), badge_cy + int(badge_r * 0.05))
    p2 = (badge_cx - int(badge_r * 0.10), badge_cy + int(badge_r * 0.40))
    p3 = (badge_cx + int(badge_r * 0.55), badge_cy - int(badge_r * 0.30))
    draw.line([p1, p2], fill=WHITE, width=tick_w)
    draw.line([p2, p3], fill=WHITE, width=tick_w)

    return img


def main() -> int:
    sizes = [16, 32, 48, 64, 128, 256, 512]
    master = render(1024).resize((512, 512), Image.LANCZOS)
    for s in sizes:
        out = render(1024).resize((s, s), Image.LANCZOS)
        path = RES_DIR / f"icon-{s}.png"
        out.save(path, format="PNG")
        print(f"Wrote {path.relative_to(ROOT)}")

    master_path = PKG_DIR / "icon.png"
    master.save(master_path, format="PNG")
    print(f"Wrote {master_path.relative_to(ROOT)}")

    ico_sizes = [(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    ico_imgs = [render(1024).resize(s, Image.LANCZOS) for s in ico_sizes]
    ico_path = PKG_DIR / "icon.ico"
    ico_imgs[0].save(ico_path, format="ICO", sizes=ico_sizes, append_images=ico_imgs[1:])
    print(f"Wrote {ico_path.relative_to(ROOT)}")

    # ICNS for macOS bundles. Pillow needs a single source image and derives
    # sizes; we feed it the 1024 px master so retina (256@2x, 512@2x) layers
    # come out crisp.
    icns_src = render(1024)
    icns_path = PKG_DIR / "icon.icns"
    icns_src.save(icns_path, format="ICNS")
    print(f"Wrote {icns_path.relative_to(ROOT)}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

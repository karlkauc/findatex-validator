#!/usr/bin/env python3
"""Render the Open Graph / Twitter card image for the web app.

This is the picture LinkedIn, Teams, Slack and X show when someone pastes
https://www.findatex-validator.eu — the single most-seen asset of the project
outside the app itself. 1200x630 is the format all of them crop to.

Design: the app's navy header gradient, the app icon (reused from
tools/generate_icon.py so the two never drift apart), and the three lines a
reader needs in the 1.5 seconds a link preview gets — what it is, which
templates, and why it is safe to use.

Output:
  - web-app/src/main/frontend/public/og-image.png   (referenced by index.html)

Usage:
  python3 tools/generate_og_image.py
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

from generate_icon import render as render_icon

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "web-app" / "src" / "main" / "frontend" / "public" / "og-image.png"

WIDTH, HEIGHT = 1200, 630

# Palette — same navy ramp as tailwind.config.js / app.css.
NAVY_TOP = (42, 79, 122)
NAVY_BOTTOM = (15, 32, 52)
WHITE = (255, 255, 255)
NAVY_100 = (221, 230, 240)
ACCENT = (125, 175, 235)

FONT_DIR = Path("/usr/share/fonts/truetype/liberation")
BOLD = FONT_DIR / "LiberationSans-Bold.ttf"
REGULAR = FONT_DIR / "LiberationSans-Regular.ttf"


def font(path: Path, size: int) -> ImageFont.FreeTypeFont:
    if not path.exists():
        raise SystemExit(f"Font not found: {path} (install fonts-liberation)")
    return ImageFont.truetype(str(path), size)


def draw_tracked(draw: ImageDraw.ImageDraw, xy, text: str, f, fill, tracking: int) -> None:
    """Letter-spaced text — PIL has no tracking, so step glyph by glyph."""
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=f, fill=fill)
        x += draw.textlength(ch, font=f) + tracking


def build() -> Image.Image:
    img = Image.new("RGB", (WIDTH, HEIGHT), NAVY_BOTTOM)

    # Diagonal-ish gradient: vertical ramp plus a soft light source top-left.
    grad = Image.new("RGB", (1, HEIGHT))
    for y in range(HEIGHT):
        t = y / (HEIGHT - 1)
        grad.putpixel((0, y), tuple(
            int(NAVY_TOP[i] + (NAVY_BOTTOM[i] - NAVY_TOP[i]) * t) for i in range(3)))
    img = grad.resize((WIDTH, HEIGHT))

    glow = Image.new("L", (WIDTH, HEIGHT), 0)
    ImageDraw.Draw(glow).ellipse((-380, -520, 780, 360), fill=42)
    # Blur hard, otherwise the ellipse edge reads as a visible arc across the card.
    glow = glow.filter(ImageFilter.GaussianBlur(140))
    img = Image.composite(Image.new("RGB", (WIDTH, HEIGHT), (74, 118, 170)), img, glow)

    draw = ImageDraw.Draw(img)

    # App icon, left column.
    icon = render_icon(512).resize((208, 208), Image.LANCZOS)
    img.paste(icon, (80, 150), icon)

    text_x = 336
    draw_tracked(draw, (text_x, 154), "FINDATEX DATA TEMPLATES",
                 font(BOLD, 24), ACCENT, tracking=4)

    draw.text((text_x, 196), "FinDatEx Validator", font=font(BOLD, 78), fill=WHITE)
    draw.text((text_x, 292), "TPT · EET · EMT · EPT", font=font(BOLD, 40), fill=ACCENT)

    draw.line((text_x, 366, text_x + 300, 366), fill=(90, 130, 180), width=3)

    draw.text((text_x, 396), "Quality score, every rule violation, Excel report.",
              font=font(REGULAR, 30), fill=NAVY_100)
    draw.text((text_x, 440), "No login. Files are never stored.",
              font=font(REGULAR, 30), fill=NAVY_100)

    # Footer rule + URL.
    draw.line((80, 536, WIDTH - 80, 536), fill=(58, 92, 133), width=2)
    draw.text((80, 560), "www.findatex-validator.eu", font=font(BOLD, 28), fill=WHITE)

    free = "Free · open source"
    f = font(REGULAR, 28)
    draw.text((WIDTH - 80 - draw.textlength(free, font=f), 560), free, font=f, fill=ACCENT)

    return img


if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    build().save(OUT, "PNG", optimize=True)
    print(f"wrote {OUT.relative_to(ROOT)} ({OUT.stat().st_size // 1024} KB)")

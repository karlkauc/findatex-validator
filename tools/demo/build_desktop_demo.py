#!/usr/bin/env python3
"""Compose the README desktop-app GIFs from the frames DesktopDemoRecorder dumps.

    build_desktop_demo.py <frames-dir> <out-dir>

Reads ``manifest.json`` (one entry per frame: file, part, hold time, caption,
cursor position, highlighted control) and, per ``part``, writes
``desktop-<part>.gif``. Each frame gets a caption bar below the window, a
highlight box around the control the caption talks about, and a painted
mouse pointer (screen captures don't contain the real one). Encoding goes
through ffmpeg's palettegen/paletteuse — Pillow's per-frame quantisation
flickers and is twice the size.
"""
from __future__ import annotations

import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

NAVY = (31, 53, 80)          # .top-bar in app.css
ACCENT = (31, 111, 235)      # .primary-button
BAR_H = 78
OUT_WIDTH = 1000
FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]
FONT_BOLD_CANDIDATES = [
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]


def font(candidates: list[str], size: int) -> ImageFont.FreeTypeFont:
    for c in candidates:
        if Path(c).exists():
            return ImageFont.truetype(c, size)
    return ImageFont.load_default()


def wrap(draw: ImageDraw.ImageDraw, text: str, fnt, max_w: int) -> list[str]:
    words, lines, cur = text.split(), [], ""
    for w in words:
        trial = (cur + " " + w).strip()
        if draw.textlength(trial, font=fnt) <= max_w:
            cur = trial
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def draw_cursor(draw: ImageDraw.ImageDraw, x: float, y: float) -> None:
    # Classic arrow pointer, tip at (x, y).
    pts = [(0, 0), (0, 17), (4, 13), (7, 20), (10, 19), (7, 12), (12, 12)]
    poly = [(x + px, y + py) for px, py in pts]
    draw.polygon(poly, fill=(20, 20, 20), outline=(255, 255, 255))
    draw.line(poly + [poly[0]], fill=(255, 255, 255), width=1)


def compose(frame: Image.Image, entry: dict, fnt_regular, fnt_bold) -> Image.Image:
    w, h = frame.size
    canvas = Image.new("RGB", (w, h + BAR_H), NAVY)
    canvas.paste(frame, (0, 0))

    overlay = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    hl = entry.get("highlight")
    if hl:
        x, y, hw, hh = hl
        pad = 6
        box = (x - pad, y - pad, x + hw + pad, y + hh + pad)
        od.rounded_rectangle(box, radius=8, outline=ACCENT + (90,), width=9)
        od.rounded_rectangle(box, radius=8, outline=ACCENT + (255,), width=3)
    canvas = Image.alpha_composite(canvas.convert("RGBA"), overlay).convert("RGB")

    d = ImageDraw.Draw(canvas)
    cx, cy = entry["cursor"]
    if 0 <= cx < w and 0 <= cy < h:
        draw_cursor(d, cx, cy)

    caption = entry.get("caption") or ""
    if caption:
        # "Step 1 — text": bold the lead-in up to the dash.
        lead, sep, rest = caption.partition(" — ")
        if not sep:
            lead, rest = "", caption
        lines = wrap(d, rest, fnt_regular, w - 48 - (d.textlength(lead + sep, font=fnt_bold) if lead else 0))
        ty = h + 14 if len(lines) > 1 else h + 25
        x0 = 24
        if lead:
            d.text((x0, ty), lead + sep, font=fnt_bold, fill=(255, 255, 255))
            x0 += d.textlength(lead + sep, font=fnt_bold)
        for i, line in enumerate(lines):
            d.text((x0 if i == 0 else 24, ty + i * 28), line, font=fnt_regular, fill=(230, 238, 246))
    return canvas


def encode_gif(pngs: list[tuple[Path, float]], out: Path) -> None:
    with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as lst:
        for p, secs in pngs:
            lst.write(f"file '{p}'\nduration {secs:.3f}\n")
        lst.write(f"file '{pngs[-1][0]}'\n")   # concat needs the last file twice
        lst_path = lst.name
    vf = (f"scale={OUT_WIDTH}:-1:flags=lanczos,split[a][b];"
          f"[a]palettegen=max_colors=255:stats_mode=diff[p];"
          f"[b][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", lst_path,
                    "-vf", vf, "-loop", "0", str(out)], check=True)
    Path(lst_path).unlink()


def main() -> None:
    frames_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((frames_dir / "manifest.json").read_text())
    fnt_regular = font(FONT_CANDIDATES, 21)
    fnt_bold = font(FONT_BOLD_CANDIDATES, 21)

    parts: dict[str, list[dict]] = {}
    for e in manifest:
        parts.setdefault(e["part"], []).append(e)

    work = Path(tempfile.mkdtemp(prefix="desktop-demo-"))
    try:
        for part, entries in parts.items():
            pngs = []
            for i, e in enumerate(entries):
                img = compose(Image.open(frames_dir / e["file"]).convert("RGB"), e, fnt_regular, fnt_bold)
                p = work / f"{part}-{i:04d}.png"
                img.save(p)
                pngs.append((p, e["ms"] / 1000.0))
            out = out_dir / f"desktop-{part}.gif"
            encode_gif(pngs, out)
            total = sum(s for _, s in pngs)
            print(f"[demo] {out.name}: {len(pngs)} frames, {total:.1f}s, {out.stat().st_size / 1024:.0f} KB")
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()

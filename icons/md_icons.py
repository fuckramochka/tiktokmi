"""Draws the two Material launcher icons, Material You (legacy) and Material 3 (legacy).

TikTok Mi's note, built from shapes rather than traced: two stems and a beam,
and note heads that are shapes from the Material 3 Expressive library instead
of ovals. Drawn five times larger, rounded by blurring and cutting at the
middle (which softens every corner the same way), then brought down to the
192 pixels a launcher icon is.

    python3 icons/md_icons.py            # writes icons/launcher/material.png
    python3 icons/md_icons.py preview    # and a sheet to look at
"""

import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "icons", "launcher")
BIG = 960          # drawn at five times 192
SIZE = 192


def lobed(cx, cy, radius, lobes, depth, turn=0.0, steps=720):
    """A Material shape as a polygon: a circle whose radius dips `lobes` times by `depth`."""
    points = []
    for i in range(steps):
        t = 2 * math.pi * i / steps
        r = radius * (1 - depth * (1 - math.cos(lobes * (t - math.radians(turn)))) / 2)
        points.append((cx + r * math.cos(t), cy + r * math.sin(t)))
    return points


def soften(mask, amount):
    """Round every corner of a mask the same way: blur, then cut at the middle."""
    return mask.filter(ImageFilter.GaussianBlur(amount)).point(lambda v: 255 if v > 127 else 0)


def note(heads, scale=1.0, dx=0, dy=0):
    """TikTok Mi's beamed note as a mask. `heads(draw, centre, radius)` draws one note head."""
    mask = Image.new("L", (BIG, BIG), 0)
    d = ImageDraw.Draw(mask)

    def p(x, y):
        # the note is laid out on 1000 units and placed in the middle
        return (BIG / 2 + (x - 500) * scale * BIG / 1000 + dx, BIG / 2 + (y - 500) * scale * BIG / 1000 + dy)

    stem = 58
    left, right = 372, 712          # right edges of the stems
    top_left, top_right = 300, 222  # where the beam's lower edge meets each stem
    beam = 118
    foot_left, foot_right = 700, 622
    # beam, a slanted band across both stems
    d.polygon([p(left - stem, top_left - beam + 6), p(right, top_right - beam - 6),
               p(right, top_right), p(left - stem, top_left + 12)], fill=255)
    # stems
    d.polygon([p(left - stem, top_left - beam + 6), p(left, top_left - beam + 2),
               p(left, foot_left), p(left - stem, foot_left)], fill=255)
    d.polygon([p(right - stem, top_right - beam + 4), p(right, top_right - beam - 6),
               p(right, foot_right), p(right - stem, foot_right)], fill=255)
    mask = soften(mask, 6)
    d = ImageDraw.Draw(mask)
    r = 128 * scale * BIG / 1000
    heads(d, p(left - 118, foot_left + 8), r, 0)
    heads(d, p(right - 118, foot_right + 8), r, 1)
    mask = soften(mask, 3)
    # the layout above is not symmetric (a head sticks out on the left, a stem
    # ends the right), so the note is put in the middle by what it covers
    x0, y0, x1, y1 = mask.getbbox()
    centred = Image.new("L", (BIG, BIG), 0)
    centred.paste(mask, (round(BIG / 2 - (x0 + x1) / 2 + dx), round(BIG / 2 - (y0 + y1) / 2 + dy)))
    return centred


def background(colour):
    return Image.new("RGBA", (BIG, BIG), colour)


def paint(base, mask, colour):
    layer = Image.new("RGBA", (BIG, BIG), colour)
    base.paste(layer, (0, 0), mask)
    return base


def margyrial_you():
    """Material You: one tone of the mint for the ground, a deep one for the note, Cookie 7 heads."""
    def heads(d, centre, r, which):
        d.polygon(lobed(centre[0], centre[1], r * 1.14, 7, 0.12, turn=(10, 30)[which]), fill=255)

    icon = background((0xCC, 0xF2, 0xDF, 255))
    paint(icon, note(heads, scale=0.82), (0x12, 0x4E, 0x3A, 255))
    return icon


def margyrial_3():
    """Material 3 Expressive: a soft burst in the container tone, the note on it, Cookie 4 heads."""
    icon = background((0x1E, 0x6A, 0x4F, 255))
    burst = Image.new("L", (BIG, BIG), 0)
    ImageDraw.Draw(burst).polygon(lobed(BIG / 2, BIG / 2, BIG * 0.43, 12, 0.13, turn=15), fill=255)
    paint(icon, soften(burst, 4), (0xA8, 0xF0, 0xCD, 255))

    def heads(d, centre, r, which):
        d.polygon(lobed(centre[0], centre[1], r * 1.16, 4, 0.2, turn=(45, 30)[which]), fill=255)

    paint(icon, note(heads, scale=0.7), (0x00, 0x2E, 0x22, 255))
    return icon


def write():
    made = {}
    for name, draw in (("margyrialyou", margyrial_you), ("margyrial3", margyrial_3)):
        icon = draw().resize((SIZE, SIZE), Image.LANCZOS)
        icon.save(os.path.join(OUT, name + ".png"), optimize=True)
        made[name] = icon
    return made


if __name__ == "__main__":
    made = write()
    if len(sys.argv) > 1 and sys.argv[1] == "preview":
        old = [Image.open(os.path.join(OUT, n + ".png")).convert("RGBA") for n in ("materialyou", "material3")]
        sheet = Image.new("RGBA", (4 * 212 + 20, 232), (240, 240, 240, 255))
        for i, icon in enumerate(old + list(made.values())):
            sheet.paste(icon.resize((192, 192)), (20 + i * 212, 20), icon.resize((192, 192)))
        sheet.save(sys.argv[2] if len(sys.argv) > 2 else os.path.join(OUT, "..", "md_preview.png"))

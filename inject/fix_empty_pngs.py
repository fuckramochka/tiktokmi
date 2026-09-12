#!/usr/bin/env python3
"""Give aapt2 something it will accept in place of TikTok's empty PNGs.

The apk ships about three thousand zero-byte .png files. They survive being
unpacked and repacked as-is, but a rebuild runs every drawable back through
aapt2, and aapt2 refuses a file that does not start with a PNG signature:

    error: failed to read PNG signature: file does not start with PNG signature

So each empty one is replaced by a 1x1 fully transparent PNG. Nothing was
rendering from a zero-byte file to begin with, and the resource keeps its id.
"""

import pathlib
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "work/full"

# A 1x1 fully transparent PNG, written out by hand so this needs no Pillow.
BLANK = bytes([
    0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
    0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
    0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4,
    0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
    0x54, 0x78, 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
    0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4, 0x00,
    0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE,
    0x42, 0x60, 0x82,
])


def main():
    res = pathlib.Path(ROOT) / "res"
    fixed = 0
    for path in res.rglob("*.png"):
        try:
            if path.stat().st_size == 0:
                path.write_bytes(BLANK)
                fixed += 1
        except OSError:
            continue
    print("  empty PNGs replaced:", fixed)


if __name__ == "__main__":
    main()

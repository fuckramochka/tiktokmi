"""What MargyT looks like.

The mint is the Margy banner's background, straight off `assets/banner.png` in
narezany/Margelet; the glyph is a note where Margy has a paper plane. The paths
are drawn in a 108-unit square, the same viewport an adaptive icon uses, and
all of the glyph sits inside the middle 72 units -- the safe zone, the part no
launcher mask can cut off.
"""

MINT = 0xFF8DD1B0
INK = 0xFF1C2C24
WHITE = 0xFFFFFFFF

VIEWPORT = 108

BACKGROUND = [(MINT, "M0,0 h108 v108 h-108 z")]

GLYPH = [
    (WHITE, "M26,76 a11,8 0 1,0 22,0 a11,8 0 1,0 -22,0 z"),   # left note head
    (WHITE, "M60,68 a11,8 0 1,0 22,0 a11,8 0 1,0 -22,0 z"),   # right note head
    (WHITE, "M42,30 h6 v46 h-6 z"),                            # left stem
    (WHITE, "M76,22 h6 v46 h-6 z"),                            # right stem
    (WHITE, "M42,30 L82,22 L82,34 L42,42 Z"),                  # beam
]

# for an icon that is one drawable rather than two layers
COMBINED = BACKGROUND + GLYPH

# the bitmap the legacy densities are scaled down from
MASTER_PNG = "icon_out/play/ic_launcher-512.png"

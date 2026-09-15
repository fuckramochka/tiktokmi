"""What TikTok MI looks like.

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

# Two things were wrong with the first drawing of this. The beam is slanted,
# so its top edge at the right stem's left side sat *below* that stem's flat
# top and a corner of the stem stuck out above it -- the beam now clears both
# stems along their whole width. And the glyph filled the square rather than
# the circle inside it: a launcher that masks icons to a circle was cutting the
# lower left note head. Everything is now inside a radius of 36 from the
# centre, which is the safe zone, so no mask reaches it.
GLYPH = [
    (WHITE, "M32.1,71.2 a8.6,6.2 0 1,0 17.2,0 a8.6,6.2 0 1,0 -17.2,0 z"),  # left head
    (WHITE, "M58.7,64.9 a8.6,6.2 0 1,0 17.2,0 a8.6,6.2 0 1,0 -17.2,0 z"),  # right head
    (WHITE, "M44.6,35.3 h4.7 v35.9 h-4.7 z"),                  # left stem
    (WHITE, "M71.2,29.0 h4.7 v35.9 h-4.7 z"),                  # right stem
    (WHITE, "M44.6,34.5 L75.8,26.7 L75.8,36.1 L44.6,43.9 Z"),  # beam
]

# for an icon that is one drawable rather than two layers
COMBINED = BACKGROUND + GLYPH

# the bitmap the legacy densities are scaled down from
MASTER_PNG = "icon_out/play/ic_launcher-512.png"

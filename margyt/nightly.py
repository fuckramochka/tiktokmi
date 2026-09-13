"""Which colours TikTok itself changes when the theme changes.

The obvious place to look is the wrong one. A colour that differs between
light and dark is normally a resource with a `night` variant, and TikTok has
twenty-two of those in the whole apk -- nowhere near a theme. Nor are there
names to go by: the resource names are stripped from this build.

The theme is in the styles. Every colour in TikTok's design system is a theme
attribute, and the two themes are two styles that give the same attribute two
different values -- `#FF121212` against `#FFFFFFFF`, `#1AFFFFFF` against
`#1A161823`. An attribute that holds exactly one colour is not part of the
switch; an attribute that holds two is, and both of those values are colours
the app repaints when the theme changes.

So this reads the style table and collects those values. That set, and nothing
else, is what the mod's theming is allowed to touch -- which is the whole of
the rule: repaint a colour only where TikTok would have repainted it.

What each value becomes is decided while the app runs, not here: the mod knows
which theme is on, and a colour's distance from that theme's own background is
the distance it keeps from the chosen one.
"""

from __future__ import annotations

import struct
from typing import List, Set

from .arsc import Arsc
from .palette import captures

COLOUR_TYPES = (0x1C, 0x1D, 0x1E, 0x1F)

FLAG_SPARSE = 0x01
FLAG_OFFSET16 = 0x02
ENTRY_FLAG_COMPLEX = 0x0001


def theme_colours(arsc: Arsc, accent_reference: int) -> List[int]:
    """Every colour value that is one side of a light/dark pair, sorted."""
    holds = {}
    for package in arsc.packages:
        for type_id, chunks in package.types.items():
            name = package.type_names.get(type_id - 1) if package.type_names else None
            if name != "style":
                continue
            for chunk in chunks:
                for attribute, value in _bag_colours(arsc, chunk):
                    holds.setdefault(attribute, set()).add(value)

    out: Set[int] = set()
    for values in holds.values():
        if len(values) != 2:
            continue
        first, second = sorted(values)
        # the same colour at two opacities is one colour, not two themes
        if (first & 0xFFFFFF) == (second & 0xFFFFFF):
            continue
        for value in (first, second):
            # the brand colour is the accent's to move, not the theme's
            if captures(value, accent_reference):
                continue
            out.add(value)
    return sorted(out)


def _bag_colours(arsc: Arsc, chunk: int):
    """Every (attribute, colour) a style chunk sets."""
    data = arsc.data
    _kind, header_size, _size = struct.unpack_from("<HHI", data, chunk)
    flags = data[chunk + 9]
    count, entries_start = struct.unpack_from("<II", data, chunk + 12)
    table = chunk + header_size

    for index in range(count):
        if flags & FLAG_SPARSE:
            return  # nothing in this apk's styles is sparse; guessing is worse
        if flags & FLAG_OFFSET16:
            offset = struct.unpack_from("<H", data, table + index * 2)[0]
            if offset == 0xFFFF:
                continue
            offset *= 4
        else:
            offset = struct.unpack_from("<I", data, table + index * 4)[0]
            if offset == 0xFFFFFFFF:
                continue

        entry = chunk + entries_start + offset
        size, entry_flags = struct.unpack_from("<HH", data, entry)
        if not entry_flags & ENTRY_FLAG_COMPLEX:
            continue
        how_many = struct.unpack_from("<I", data, entry + 12)[0]
        at = entry + size
        for i in range(how_many):
            attribute = struct.unpack_from("<I", data, at + i * 12)[0]
            _size, _pad, kind, value = struct.unpack_from("<HBBI", data, at + i * 12 + 4)
            if kind in COLOUR_TYPES:
                yield attribute, value

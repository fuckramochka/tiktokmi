"""The accent colour, baked into the places code cannot reach.

Most of TikTok's pink is reachable at runtime: a constant in the bytecode, or a
colour resource read through `Resources.getColor`, both of which the dex patch
sends through the mod. What is not reachable is the pink written into compiled
XML -- a vector's `fillColor`, a selector's item, a shape's solid -- because
the framework parses those itself, inside its own code, where nothing of ours
runs.

Those are settled here instead, while the apk is being built, and both edits
are the size-preserving kind: a colour is eight bytes of Res_value wherever it
appears, so the new one goes exactly where the old one was.
"""

from __future__ import annotations

from typing import Dict, List, Tuple

from .apkzip import Apk
from .arsc import Arsc
from .axml import Axml

# the colour types a Res_value can be: argb8, rgb8, argb4, rgb4
COLOUR_TYPES = (0x1C, 0x1D, 0x1E, 0x1F)


def bake(apk: Apk, arsc: Arsc, old: int, new: int) -> List[str]:
    """Replace `old` with `new` in the resource table and in compiled XML."""
    if old == new:
        return ["the accent is TikTok's own, nothing to bake"]

    report = []
    report.append("resource entries: %d" % _bake_table(arsc, old, new))

    files = 0
    changes = 0
    for name in apk.names():
        if not (name.startswith("res/") and name.endswith(".xml")):
            continue
        data = apk.read(name)
        if _as_bytes(old) not in data:
            continue
        try:
            axml = Axml.parse(data)
        except Exception:
            continue
        here = 0
        for node in axml.nodes:
            for attribute in node.attributes:
                if attribute.kind in COLOUR_TYPES and attribute.data == old:
                    attribute.data = new
                    here += 1
        if here:
            apk.replace(name, axml.build())
            files += 1
            changes += here
    report.append("compiled xml: %d colours in %d files" % (changes, files))
    return report


def _bake_table(arsc: Arsc, old: int, new: int) -> int:
    """Every typed value in the table that is exactly this colour."""
    changed = 0
    for package in arsc.packages:
        for type_id in list(package.types):
            for entry in _entries(arsc, package, type_id):
                if entry.kind in COLOUR_TYPES and entry.data == old:
                    arsc.set_value(entry, entry.kind, new)
                    changed += 1
    return changed


def _entries(arsc: Arsc, package, type_id: int):
    """Every value of every entry of one type, across its configurations."""
    for entry_id in range(_entry_count(arsc, package, type_id)):
        res_id = (package.id << 24) | (type_id << 16) | entry_id
        for value in arsc.values(res_id):
            yield value


def _entry_count(arsc: Arsc, package, type_id: int) -> int:
    import struct

    most = 0
    for chunk in package.types.get(type_id, []):
        count = struct.unpack_from("<I", arsc.data, chunk + 12)[0]
        most = max(most, count)
    return most


def _as_bytes(colour: int) -> bytes:
    import struct

    return struct.pack("<I", colour)

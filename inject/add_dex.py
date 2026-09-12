#!/usr/bin/env python3
"""Append the injected classes to a built apk as one more dex.

ART loads every classes*.dex in an apk as long as the numbering has no gaps,
so our compiled classes go in as the next one after TikTok's last. Nothing has
to be merged into an existing dex, and apktool never has to see them.

    ./add_dex.py build/margyt-unsigned.apk inject/dex/classes.dex
"""

import re
import sys
import zipfile

DEX = re.compile(r"classes(\d*)\.dex")


def index(name):
    return int(DEX.fullmatch(name).group(1) or 1)


def main():
    apk = sys.argv[1] if len(sys.argv) > 1 else "build/margyt-unsigned.apk"
    dex = sys.argv[2] if len(sys.argv) > 2 else "inject/dex/classes.dex"

    with zipfile.ZipFile(apk) as z:
        present = sorted(index(n) for n in z.namelist() if DEX.fullmatch(n))
    if not present:
        raise SystemExit("%s has no dex files at all" % apk)

    name = "classes%d.dex" % (max(present) + 1)
    if name in ("classes%d.dex" % i for i in present):
        raise SystemExit("%s is already there" % name)

    with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
        z.write(dex, name)

    with zipfile.ZipFile(apk) as z:
        after = sorted(index(n) for n in z.namelist() if DEX.fullmatch(n))
    gaps = [i for i in range(1, max(after) + 1) if i not in after]

    print("  added %s (%d dex in total)" % (name, len(after)))
    print("  numbering gaps: %s" % (gaps or "none"))
    if gaps:
        raise SystemExit("gaps in the dex numbering -- ART will stop at the first one")


if __name__ == "__main__":
    main()

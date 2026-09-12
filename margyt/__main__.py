"""MargyT's command line.

    python3 -m margyt path/to/tiktok.apk
"""

from __future__ import annotations

import argparse
import os
import sys

from .build import Build
from .toolchain import Toolchain

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        prog="margyt",
        description="Build MargyT from an official TikTok apk.",
    )
    parser.add_argument("apk", help="a universal TikTok apk (type APK, not BUNDLE)")
    parser.add_argument("-o", "--out", default=os.path.join(ROOT, "build", "margyt.apk"),
                        help="where the finished apk goes")
    parser.add_argument("--work", default=os.path.join(ROOT, "work"),
                        help="scratch space for the dex files being rewritten")
    parser.add_argument("--tools", default=os.path.join(ROOT, "tools"),
                        help="where the downloaded tools are kept")
    parser.add_argument("--keystore", help="sign with this keystore instead of a debug key")
    args = parser.parse_args(argv)

    if not os.path.exists(args.apk):
        parser.error("no apk at %s" % args.apk)

    tools = Toolchain(args.tools)
    build = Build(args.apk, args.out, ROOT, tools, args.work, args.keystore)
    try:
        build.run()
    except Exception as error:  # a build failure is a message, not a traceback
        print("\n\033[1;31mBuild failed:\033[0m %s" % error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

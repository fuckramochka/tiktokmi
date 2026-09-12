#!/usr/bin/env python3
"""Give Region a Context, as early as the app will allow.

The rewritten call sites are static, so they work without any setup -- but the
country they report is read from SharedPreferences, and that needs a Context.

Two injections, because one is not enough:

  attachBaseContext   right after the super call. TikTok does some 1700 lines
                      of startup work in this method, and anything in there
                      that asks about the SIM has to get our answer already.
  onCreate            at the top. By now getApplicationContext() is real, so
                      this replaces whatever the earlier call settled for.

Both are idempotent.
"""

import glob
import os
import pathlib
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "work/full"
INIT = "Lcat/narezany/tiktok/Region;->init(Landroid/content/Context;)V"

SUPER_ATTACH = (
    "    invoke-super {p0, p1}, Landroid/app/Application;"
    "->attachBaseContext(Landroid/content/Context;)V\n"
)


def main():
    hits = glob.glob(os.path.join(
        ROOT, "smali*/com/ss/android/ugc/aweme/app/host/AwemeHostApplication.smali"))
    if len(hits) != 1:
        raise SystemExit("expected one AwemeHostApplication.smali, found %d" % len(hits))
    path = pathlib.Path(hits[0])
    text = path.read_text(encoding="utf-8")

    if INIT in text:
        print("  %-22s already patched" % path.name)
        return

    # early: the base context is all we have here
    if text.count(SUPER_ATTACH) != 1:
        raise SystemExit("attachBaseContext: expected one super call, found %d"
                         % text.count(SUPER_ATTACH))
    text = text.replace(
        SUPER_ATTACH,
        SUPER_ATTACH + "\n    invoke-static {p1}, %s\n" % INIT,
    )

    # later: p0 is the Application itself
    pattern = re.compile(
        r"(\.method public final onCreate\(\)V\n    \.locals \d+\n\n    \.prologue\n)")
    text, n = pattern.subn(r"\1    invoke-static {p0}, %s\n\n" % INIT, text, count=1)
    if n != 1:
        raise SystemExit("onCreate: could not find the prologue to inject after")

    path.write_text(text, encoding="utf-8")
    print("  %-22s patched (attachBaseContext + onCreate)" % path.name)


if __name__ == "__main__":
    main()

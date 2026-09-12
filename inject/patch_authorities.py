#!/usr/bin/env python3
"""Make every provider authority in the apk unique to this package.

Renaming the package moves the authorities that are spelled with it, and the
manifest patch handles those. Two are not spelled with it at all, and both are
shared with anything else built from the same TikTok:

  com.facebook.app.FacebookContentProvider597615686992125
        The Facebook SDK's provider, keyed to TikTok's Facebook app id.
  com.ss.android.common.multiprocess.SHARE_PROVIDER_AUTHORITY1233
        ByteDance's cross-process preference provider, keyed to TikTok's
        internal app id, 1233.

Android will not install two apps claiming the same authority. So an apk that
kept these installs fine on a clean phone and refuses on a phone that already
has TikTok -- or any other mod of it -- with "App not installed as package
conflicts with an existing package".

They are handled differently because of where they come from:

  The Facebook one is assembled inside the SDK from a prefix that does not
  appear as a string anywhere in the dex, so renaming the manifest would
  desync it from whatever the SDK computes at runtime. The provider is only
  used to hand media to the Facebook app when sharing there, so the element
  goes away instead. Sharing to Facebook stops working; nothing else does.

  The ByteDance one is built from a prefix that is in the dex, twice, as
  "com.ss.android.common.multiprocess.SHARE_PROVIDER_AUTHORITY${APP_ID}". That
  one can be renamed properly, in the manifest and in both constants, and
  keeps working.

Also renames the two authorities that are spelled out in code rather than
derived from getPackageName().
"""

import glob
import os
import pathlib
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "work/full"
OLD = sys.argv[2] if len(sys.argv) > 2 else "com.zhiliaoapp.musically"
NEW = sys.argv[3] if len(sys.argv) > 3 else "cat.narezany.tiktok"

BD_OLD = "com.ss.android.common.multiprocess.SHARE_PROVIDER_AUTHORITY"
BD_NEW = "cat.narezany.tiktok.multiprocess.SHARE_PROVIDER_AUTHORITY"

# Spelled out in code instead of built from getPackageName().
IN_CODE = (".draftprovider", ".wallpapercaller")


def main():
    manifest = pathlib.Path(ROOT) / "AndroidManifest.xml"
    text = manifest.read_text(encoding="utf-8")

    text, dropped = re.subn(
        r'\s*<provider[^>]*com\.facebook\.app\.FacebookContentProvider[^>]*/>',
        "", text)
    print("  facebook provider dropped: %d" % dropped)

    text = text.replace(BD_OLD, BD_NEW)
    manifest.write_text(text, encoding="utf-8")

    smali = 0
    for path in pathlib.Path(ROOT).rglob("*.smali"):
        try:
            s = path.read_text(encoding="utf-8")
        except OSError:
            continue
        before = s
        if BD_OLD in s:
            s = s.replace(BD_OLD, BD_NEW)
        for suffix in IN_CODE:
            if OLD + suffix in s:
                s = s.replace(OLD + suffix, NEW + suffix)
        if s != before:
            path.write_text(s, encoding="utf-8")
            smali += 1
    print("  smali files touched:       %d" % smali)

    # Nothing may be left that another build of TikTok would also claim.
    left = set()
    for value in re.findall(r'android:authorities="([^"]+)"', text):
        for authority in value.split(";"):
            if not authority.startswith(NEW):
                left.add(authority)
    if left:
        raise SystemExit("authorities not unique to this package:\n  "
                         + "\n  ".join(sorted(left)))
    print("  every authority now starts with %s" % NEW)


if __name__ == "__main__":
    main()

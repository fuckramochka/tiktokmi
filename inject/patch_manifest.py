#!/usr/bin/env python3
"""Manifest work: rename the package, declare our screen, drop split metadata.

Renaming is the easy half. The apk's provider authorities are nearly all built
as getPackageName() + suffix, so they follow the rename on their own; only two
are spelled out in code and those are handled next to the smali.

The half that matters is what must NOT move. TikTok signs in with Google
through AppAuth, which redirects to a custom scheme derived from the OAuth
client id -- not from the package name, and not checked against the signing
certificate. Those schemes are what make the flow survive a resign, so they are
counted before the rename and asserted after it.
"""

import pathlib
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "work/full"
OLD = sys.argv[2] if len(sys.argv) > 2 else "com.zhiliaoapp.musically"
NEW = sys.argv[3] if len(sys.argv) > 3 else "cat.narezany.tiktok"

ACTIVITY = "cat.narezany.tiktok.MargyTSettingsActivity"

# Attributes whose values name this app and therefore have to move with it.
#
# Providers and permissions are not cosmetic: two installed apps cannot declare
# the same authority, and they cannot declare the same custom permission with
# different signatures either. Miss one and the install fails outright with
# INSTALL_FAILED_CONFLICTING_PROVIDER or INSTALL_FAILED_DUPLICATE_PERMISSION.
RENAME = (
    "authorities",
    "name",
    "permission",
    "readPermission",
    "writePermission",
    "process",
    "targetPackage",
    "taskAffinity",
    "scheme",
)

# android:host is deliberately absent. Those are deep-link hosts that arrive
# from the server and from web links -- "cct.<package>" is a Custom Tabs host
# some SDK was configured with. They are not ours to renumber; renaming them
# breaks incoming links and gains nothing.
KEEP = ("host",)


def main():
    path = pathlib.Path(ROOT) / "AndroidManifest.xml"
    text = path.read_text(encoding="utf-8")

    schemes = re.findall(r'android:scheme="com\.googleusercontent\.apps\.[^"]+"', text)
    if not schemes:
        raise SystemExit("no AppAuth schemes found -- has the login flow changed?")

    # Play Store split metadata. It points at @<pkg>.df_pipo_bnpl:xml/splits0,
    # a resource that lives in a dynamic feature module and is simply not in a
    # universal apk, so aapt2 fails to link the manifest over it. Nothing
    # sideloaded needs it.
    text, dropped = re.subn(
        r'\s*<meta-data android:name="com\.android\.vending\.splits"[^>]*?/>',
        "", text)

    text = text.replace('package="%s"' % OLD, 'package="%s"' % NEW)

    # Every occurrence inside a whitelisted attribute, not just one at the
    # front of the value: android:authorities is frequently a ';'-separated
    # list, and a prefix-anchored replacement renames the first entry and
    # silently leaves the rest pointing at the original app.
    renamed = [0]

    def fix(match):
        attr, value = match.group(1), match.group(2)
        if attr in KEEP or attr not in RENAME or OLD not in value:
            return match.group(0)
        renamed[0] += value.count(OLD)
        return 'android:%s="%s"' % (attr, value.replace(OLD, NEW))

    text = re.sub(r'android:([A-Za-z]+)="([^"]*)"', fix, text)

    for scheme in schemes:
        if scheme not in text:
            raise SystemExit("the rename ate an AppAuth scheme: %s" % scheme)

    left = re.findall(r'android:([A-Za-z]+)="([^"]*%s[^"]*)"' % re.escape(OLD), text)
    unexpected = [a for a, _ in left if a not in KEEP]
    if unexpected:
        raise SystemExit("still naming the old package in: %s" % sorted(set(unexpected)))

    added = False
    if ACTIVITY not in text:
        text = text.replace(
            "</application>",
            '    <activity android:name="%s"\n'
            '        android:exported="false"\n'
            '        android:theme="@android:style/Theme.Material.NoActionBar" />\n'
            "</application>" % ACTIVITY)
        added = True

    path.write_text(text, encoding="utf-8")
    print("  split metadata dropped: %d" % dropped)
    print("  package refs renamed:   %d" % renamed[0])
    print("  left alone (deep links): %d" % len(left))
    print("  AppAuth schemes intact: %d" % len(schemes))
    print("  settings activity:      %s" % ("declared" if added else "already there"))


if __name__ == "__main__":
    main()

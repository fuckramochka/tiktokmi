#!/usr/bin/env python3
"""Redirect TelephonyManager calls to Lcat/narezany/tiktok/Region;.

    invoke-virtual {v0}, Landroid/telephony/TelephonyManager;->getSimCountryIso()Ljava/lang/String;
    invoke-static  {v0}, Lcat/narezany/tiktok/Region;->getSimCountryIso(Landroid/telephony/TelephonyManager;)Ljava/lang/String;

The instruction format (35c), the register count and the return type all match,
so nothing has to be renumbered -- the receiver simply becomes the first
argument.

Targets are found by signature, not by offset, so this keeps working on a
rebuilt apk as long as the methods themselves are still being called.
"""

import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "work/full"
TM = "Landroid/telephony/TelephonyManager;"
REGION = "Lcat/narezany/tiktok/Region;"

# method -> (original descriptor, descriptor of the static that replaces it)
METHODS = {
    "getSimCountryIso":       ("()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TM),
    "getNetworkCountryIso":   ("()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TM),
    "getSimOperator":         ("()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TM),
    "getNetworkOperator":     ("()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TM),
    "getSimOperatorName":     ("()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TM),
    "getNetworkOperatorName": ("()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TM),
    "getSimState":            ("()I",                  "(%s)I" % TM),
    "getSimState(I)":         ("(I)I",                 "(%sI)I" % TM),
    "isNetworkRoaming":       ("()Z",                  "(%s)Z" % TM),
    "hasIccCard":             ("()Z",                  "(%s)Z" % TM),
    "getSimCarrierId":        ("()I",                  "(%s)I" % TM),
}


def rules():
    out = []
    for key, (original, replacement) in METHODS.items():
        name = key.split("(")[0]
        pattern = re.compile(
            r"invoke-virtual(/range)? (\{[^}]*\}), %s->%s%s"
            % (re.escape(TM), name, re.escape(original))
        )
        target = r"invoke-static\1 \2, %s->%s%s" % (REGION, name, replacement)
        out.append((name + original, pattern, target))
    return out


def main():
    patched = {}
    files = 0

    for dirpath, _, names in os.walk(ROOT):
        for name in names:
            if not name.endswith(".smali"):
                continue
            path = os.path.join(dirpath, name)
            try:
                text = open(path, encoding="utf-8").read()
            except OSError:
                continue
            if TM not in text:
                continue

            before = text
            for label, pattern, target in rules():
                text, hits = pattern.subn(target, text)
                if hits:
                    patched[label] = patched.get(label, 0) + hits

            if text != before:
                open(path, "w", encoding="utf-8").write(text)
                files += 1

    print("  files touched:", files)
    for label in sorted(patched, key=lambda k: -patched[k]):
        print("    %-46s %d" % (label, patched[label]))
    print("  call sites rewritten:", sum(patched.values()))

    if not patched:
        print("  WARNING: nothing matched -- did the method signatures move?")


if __name__ == "__main__":
    main()

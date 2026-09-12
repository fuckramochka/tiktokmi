#!/usr/bin/env python3
"""Перенаправление вызовов TelephonyManager на Lcat/narezany/tiktok/Region;."""
import os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "work/full"
TM = "Landroid/telephony/TelephonyManager;"
R = "Lcat/narezany/tiktok/Region;"

# метод -> (оригинальная сигнатура, сигнатура статика)
MAP = {
    "getSimCountryIso":      ("()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TM),
    "getNetworkCountryIso":  ("()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TM),
    "getSimOperator":        ("()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TM),
    "getNetworkOperator":    ("()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TM),
    "getSimOperatorName":    ("()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TM),
    "getNetworkOperatorName":("()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TM),
    "getSimState":           ("()I",                  "(%s)I" % TM),
    "getSimState(I)":        ("(I)I",                 "(%sI)I" % TM),
    "isNetworkRoaming":      ("()Z",                  "(%s)Z" % TM),
    "hasIccCard":            ("()Z",                  "(%s)Z" % TM),
    "getSimCarrierId":       ("()I",                  "(%s)I" % TM),
}

rules = []
for key, (orig, new) in MAP.items():
    name = key.split("(")[0]
    src = re.compile(r"invoke-virtual(/range)? (\{[^}]*\}), %s->%s%s"
                     % (re.escape(TM), name, re.escape(orig)))
    dst = r"invoke-static\1 \2, %s->%s%s" % (R, name, new)
    rules.append((name + orig, src, dst))

total = {}
files = 0
for dirpath, _, names in os.walk(ROOT):
    if os.sep + "smali" not in dirpath + os.sep and not os.path.basename(dirpath).startswith("smali"):
        pass
    for n in names:
        if not n.endswith(".smali"):
            continue
        p = os.path.join(dirpath, n)
        try:
            s = open(p, encoding="utf-8").read()
        except Exception:
            continue
        if TM not in s:
            continue
        orig_s = s
        for label, src, dst in rules:
            s, k = src.subn(dst, s)
            if k:
                total[label] = total.get(label, 0) + k
        if s != orig_s:
            open(p, "w", encoding="utf-8").write(s)
            files += 1

print("изменено файлов:", files)
for k in sorted(total, key=lambda x: -total[x]):
    print("  %-46s %d" % (k, total[k]))
print("всего замен:", sum(total.values()))

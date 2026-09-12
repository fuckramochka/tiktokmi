"""Rewriting the calls that ask the phone where it is.

Without root there is no Xposed, and without Xposed there is nothing to hook at
runtime -- so the calls are redirected in the bytecode instead. Each one becomes
a static call into the mod:

    invoke-virtual {v0}, Landroid/telephony/TelephonyManager;->getSimCountryIso()Ljava/lang/String;
    invoke-static  {v0}, Lcat/narezany/margyt/Region;->getSimCountryIso(Landroid/telephony/TelephonyManager;)Ljava/lang/String;

The instruction format (35c), the register count and the return type all match,
so nothing around the call has to be renumbered: the receiver just becomes the
first argument.

Targets are found by signature, never by offset or by file name, so a new
TikTok release does not move them. Of the apk's fifty-two dex files, the four
or five that mention telephony at all are the only ones taken apart; the rest
are copied across untouched, which is the difference between a build that takes
minutes and one that takes hours.
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
from typing import Dict, List, Tuple

TELEPHONY = "Landroid/telephony/TelephonyManager;"
REGION = "Lcat/narezany/margyt/Region;"

# method name -> (descriptor as TikTok calls it, descriptor of the static that
# replaces it -- the same, with the receiver moved into the arguments)
TARGETS: List[Tuple[str, str, str]] = [
    ("getSimCountryIso", "()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TELEPHONY),
    ("getNetworkCountryIso", "()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TELEPHONY),
    ("getSimOperator", "()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TELEPHONY),
    ("getNetworkOperator", "()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TELEPHONY),
    ("getSimOperatorName", "()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TELEPHONY),
    ("getNetworkOperatorName", "()Ljava/lang/String;", "(%s)Ljava/lang/String;" % TELEPHONY),
    ("getSimState", "()I", "(%s)I" % TELEPHONY),
    ("getSimState", "(I)I", "(%sI)I" % TELEPHONY),
    ("hasIccCard", "()Z", "(%s)Z" % TELEPHONY),
    ("isNetworkRoaming", "()Z", "(%s)Z" % TELEPHONY),
    ("getSimCarrierId", "()I", "(%s)I" % TELEPHONY),
]


def rules() -> List[Tuple[str, "re.Pattern[str]", str]]:
    out = []
    for name, original, replacement in TARGETS:
        pattern = re.compile(
            r"invoke-virtual(/range)? (\{[^}]*\}), %s->%s%s"
            % (re.escape(TELEPHONY), name, re.escape(original))
        )
        target = r"invoke-static\1 \2, %s->%s%s" % (REGION, name, replacement)
        out.append((name + original, pattern, target))
    return out


def interesting(dex: bytes) -> bool:
    """A quick look at the raw dex before spending a minute on it.

    Every method a dex calls is named in its string table, so a dex that never
    spells `TelephonyManager` cannot be calling one of these.
    """
    if TELEPHONY.encode() not in dex:
        return False
    return any(name.encode() in dex for name, _o, _r in TARGETS)


def rewrite_smali(root: str) -> Dict[str, int]:
    """Rewrite every call site under `root`, counting them by signature."""
    counts: Dict[str, int] = {}
    prepared = rules()
    for dirpath, _dirs, files in os.walk(root):
        for name in files:
            if not name.endswith(".smali"):
                continue
            path = os.path.join(dirpath, name)
            with open(path, encoding="utf-8") as handle:
                text = handle.read()
            if TELEPHONY not in text:
                continue
            before = text
            for label, pattern, target in prepared:
                text, hits = pattern.subn(target, text)
                if hits:
                    counts[label] = counts.get(label, 0) + hits
            if text != before:
                with open(path, "w", encoding="utf-8") as handle:
                    handle.write(text)
    return counts


class Smali:
    """baksmali and smali, from the one jar the build downloads."""

    def __init__(self, jar: str, api: int, jobs: int = 0, heap: str = "4g"):
        self.jar = jar
        self.api = api
        self.jobs = jobs or (os.cpu_count() or 2)
        self.heap = heap

    def _run(self, main: str, args: List[str]) -> None:
        command = ["java", "-Xmx" + self.heap, "-cp", self.jar, main] + args
        result = subprocess.run(command, capture_output=True, text=True)
        if result.returncode != 0:
            raise RuntimeError(
                "%s failed:\n%s\n%s" % (main.split(".")[-2], result.stdout, result.stderr)
            )

    def disassemble(self, dex_path: str, out_dir: str) -> None:
        self._run(
            "com.android.tools.smali.baksmali.Main",
            ["d", "-a", str(self.api), "-j", str(self.jobs), "-o", out_dir, dex_path],
        )

    def assemble(self, smali_dir: str, dex_path: str) -> None:
        self._run(
            "com.android.tools.smali.smali.Main",
            ["a", "-a", str(self.api), "-j", str(self.jobs), "-o", dex_path, smali_dir],
        )


def dex_format(dex: bytes) -> str:
    """The three digits after `dex\n`: 035, 038, 039 ..."""
    return dex[4:7].decode("ascii", "replace")


def patch(dex: bytes, name: str, smali: Smali, workspace: str) -> Tuple[bytes, Dict[str, int]]:
    """Take one dex apart, rewrite its call sites, put it back together."""
    room = os.path.join(workspace, name)
    shutil.rmtree(room, ignore_errors=True)
    os.makedirs(room, exist_ok=True)

    dex_in = os.path.join(room, "in.dex")
    dex_out = os.path.join(room, "out.dex")
    with open(dex_in, "wb") as handle:
        handle.write(dex)

    smali.disassemble(dex_in, os.path.join(room, "smali"))
    counts = rewrite_smali(os.path.join(room, "smali"))
    if not counts:
        shutil.rmtree(room, ignore_errors=True)
        return dex, counts

    smali.assemble(os.path.join(room, "smali"), dex_out)
    with open(dex_out, "rb") as handle:
        patched = handle.read()
    shutil.rmtree(room, ignore_errors=True)

    if dex_format(patched) != dex_format(dex):
        raise RuntimeError(
            "%s came back as dex %s, it went in as dex %s -- an Android old "
            "enough to be in the apk's minSdk would refuse to load it"
            % (name, dex_format(patched), dex_format(dex))
        )
    return patched, counts


def next_dex_name(names: List[str]) -> str:
    """The name a new dex has to take to be loaded: the next in the run.

    The runtime loads classes.dex, then classes2.dex, and stops at the first
    number that is missing -- so an extra dex is only read if it continues the
    sequence.
    """
    used = set()
    for name in names:
        match = re.fullmatch(r"classes(\d*)\.dex", name)
        if match:
            used.add(int(match.group(1) or "1"))
    number = 2
    while number in used:
        number += 1
    return "classes%d.dex" % number

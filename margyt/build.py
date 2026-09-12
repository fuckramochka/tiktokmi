"""The build, start to finish.

    apk in -> manifest, icon, call sites, one more dex -> apk out

Nothing is decoded that does not have to be. The zip is copied entry by entry
with its compression intact, the resource table is patched where it lies, and
of fifty-two dex files only the ones that call telephony are taken apart.
"""

from __future__ import annotations

import os
import time
from typing import List, Optional

from . import artwork, dexpatch, icon as icon_module, manifest as manifest_module
from .apkzip import Apk, STORED
from .arsc import Arsc
from .axml import Axml
from .dexpatch import Smali
from .toolchain import Toolchain

PACKAGE = "cat.narezany.margyt"
SETTINGS_ACTIVITY = PACKAGE + ".SettingsActivity"
LABEL = "MargyT"
SETTINGS_LABEL = "MargyT settings"
SETTINGS_THEME = "Theme_DeviceDefault_Light_NoActionBar"

MIN_API = 27


class Build:
    def __init__(self, apk_path: str, out_path: str, root: str, tools: Toolchain,
                 workspace: str, keystore: Optional[str] = None):
        self.apk_path = apk_path
        self.out_path = out_path
        self.root = root
        self.tools = tools
        self.workspace = workspace
        self.keystore = keystore
        self.started = time.time()

    def say(self, message: str) -> None:
        print("\033[1;36m==>\033[0m %s" % message)

    def detail(self, message: str) -> None:
        print("    " + message)

    # ------------------------------------------------------------------ run

    def run(self) -> str:
        os.makedirs(self.workspace, exist_ok=True)
        os.makedirs(os.path.dirname(os.path.abspath(self.out_path)), exist_ok=True)

        self.say("Opening the apk")
        apk = Apk(self.apk_path)
        self.detail("%d entries" % len(apk.entries))

        manifest = Axml.parse(apk.read("AndroidManifest.xml"))
        package = manifest_module.package_name(manifest)
        self.detail("package %s, staying as it is" % package)
        self.detail("application class %s" % manifest_module.application_class(manifest))

        self.say("Building the mod's own dex")
        dex_path = self.tools.compile_dex(
            os.path.join(self.root, "inject", "java"), self.workspace, MIN_API
        )
        injected = open(dex_path, "rb").read()
        self.detail("%d bytes" % len(injected))

        self.say("Name and icon")
        theme = self.tools.framework_constant(SETTINGS_THEME)
        for where in manifest_module.set_label(manifest, LABEL):
            self.detail("label on %s" % where)
        manifest_module.add_activity(manifest, SETTINGS_ACTIVITY, SETTINGS_LABEL, theme)
        self.detail("%s declared, on the launcher" % SETTINGS_ACTIVITY)

        arsc = Arsc(apk.read("resources.arsc"))
        master = open(os.path.join(self.root, artwork.MASTER_PNG), "rb").read()
        for line in icon_module.replace_everywhere(apk, arsc, manifest, master):
            self.detail(line.strip())

        apk.replace("AndroidManifest.xml", manifest.build())
        if arsc.dirty:
            apk.replace("resources.arsc", arsc.build(), STORED)

        self.say("Rewriting the telephony call sites")
        self.patch_dex_files(apk)

        name = dexpatch.next_dex_name(apk.names())
        apk.add(name, injected)
        self.detail("the mod's classes go in as %s" % name)

        self.say("Writing the apk")
        for gone in apk.drop_signature():
            self.detail("dropped %s" % gone)
        apk.write(self.out_path)
        apk.close()
        self.detail("%.0f MB" % (os.path.getsize(self.out_path) / 1e6))

        self.say("Signing")
        self.tools.sign(self.out_path, self.keystore)

        self.say("Done in %.0f s: %s" % (time.time() - self.started, self.out_path))
        return self.out_path

    # ------------------------------------------------------------------ dex

    def patch_dex_files(self, apk: Apk) -> None:
        smali = Smali(self.tools.smali, MIN_API)
        names = sorted(n for n in apk.names() if n.endswith(".dex"))
        candidates: List[str] = []
        for name in names:
            if dexpatch.interesting(apk.read(name)):
                candidates.append(name)
        self.detail("%d of %d dex files mention it" % (len(candidates), len(names)))

        total = 0
        for name in candidates:
            patched, counts = dexpatch.patch(
                apk.read(name), name, smali, os.path.join(self.workspace, "dex")
            )
            if not counts:
                self.detail("%s: nothing to rewrite after all" % name)
                continue
            apk.replace(name, patched)
            hits = sum(counts.values())
            total += hits
            self.detail(
                "%s: %d call sites (%s)"
                % (name, hits, ", ".join("%s x%d" % (k.split("(")[0], v)
                                         for k, v in sorted(counts.items())))
            )
        self.detail("%d call sites rewritten" % total)
        if not total:
            raise RuntimeError(
                "not one call site matched -- the method signatures have moved, "
                "and the mod would do nothing at all"
            )

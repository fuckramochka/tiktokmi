"""The build, start to finish.

    apk in -> manifest, icon, call sites, one more dex -> apk out

Nothing is decoded that does not have to be. The zip is copied entry by entry
with its compression intact, the resource table is patched where it lies, and
of fifty-two dex files only the ones that call telephony are taken apart.
"""

from __future__ import annotations

import os
import time
from typing import Dict, List, Optional

from . import accent as accent_module, artwork, dexpatch, icon as icon_module, manifest as manifest_module
from .apkzip import Apk, STORED
from .arsc import Arsc
from . import axml as axml_module
from .axml import Axml
from .dexpatch import Smali
from .toolchain import Toolchain

PACKAGE = "cat.narezany.margyt"
SETTINGS_ACTIVITY = PACKAGE + ".SettingsActivity"
STARTUP_PROVIDER = PACKAGE + ".MargyProvider"

# The screen the MargyT row is put at the top of. The row itself is added to
# the view tree while the screen is drawn -- nothing patches the code that
# builds the list -- but the screen has to still be called this for the mod to
# recognise it, so the build checks rather than hopes.
TIKTOK_SETTINGS = "com.ss.android.ugc.aweme.setting.ui.SettingContainerActivity"
LABEL = "MargyT"

# The screen has no launcher entry of its own -- it is opened from the row in
# TikTok's settings -- but an activity still wants a label: it is what the
# recents card and any "open with" list show.
SETTINGS_LABEL = "MargyT settings"
SETTINGS_THEME = "Theme_DeviceDefault_Light_NoActionBar"

# what a renamed provider authority ends in, so two mods of the same app can
# sit on one phone without the installer refusing the second
AUTHORITY_MARKER = ".margyt"


class Build:
    def __init__(self, apk_path: str, out_path: str, root: str, tools: Toolchain,
                 workspace: str, keystore: Optional[str] = None,
                 accent: Optional[int] = None):
        self.apk_path = apk_path
        self.out_path = out_path
        self.root = root
        self.tools = tools
        self.workspace = workspace
        self.keystore = keystore
        self.accent = dexpatch.TIKTOK_PINK if accent is None else accent
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

        # everything built here has to be loadable as far back as the apk goes,
        # and the apk itself is the only honest source for how far back that is
        api = manifest_module.min_sdk(manifest)
        dex_format = dexpatch.dex_format(apk.read("classes.dex"))
        self.detail("minSdk %d, dex %s" % (api, dex_format))

        self.write_baked_colour()

        self.say("Building the mod's own dex")
        dex_path = self.tools.compile_dex(
            os.path.join(self.root, "inject", "java"), self.workspace, api,
            os.path.join(self.root, "inject", "stubs"),
        )
        injected = open(dex_path, "rb").read()
        if dexpatch.dex_format(injected) != dex_format:
            raise RuntimeError(
                "the mod compiled to dex %s and the apk is dex %s -- an Android "
                "on the apk's minSdk (%d) would refuse to load it"
                % (dexpatch.dex_format(injected), dex_format, api)
            )
        missing = dexpatch.missing_targets(injected)
        if missing:
            raise RuntimeError(
                "the rewrites would land on %d method(s) the mod does not define:\n    %s\n"
                "smali assembles a call to a method that does not exist without "
                "complaining -- it would be a NoSuchMethodError on whichever "
                "screen reaches that call site first"
                % (len(missing), "\n    ".join(missing))
            )
        self.detail("%d bytes, dex %s, every rewrite has somewhere to land"
                    % (len(injected), dexpatch.dex_format(injected)))

        self.say("Name and icon")
        theme = self.tools.framework_constant(SETTINGS_THEME)
        for where in manifest_module.set_label(manifest, LABEL):
            self.detail("label on %s" % where)
        self.tools.check_attribute_ids(axml_module.ATTR_IDS)
        manifest_module.add_activity(manifest, SETTINGS_ACTIVITY, SETTINGS_LABEL, theme)
        self.detail("%s declared, reached from TikTok's own settings" % SETTINGS_ACTIVITY)

        if not manifest_module.has_activity(manifest, TIKTOK_SETTINGS):
            raise RuntimeError(
                "%s is not in this apk -- TikTok's settings screen has been "
                "renamed, and the MargyT row would never appear in it"
                % TIKTOK_SETTINGS
            )
        manifest_module.add_provider(
            manifest, STARTUP_PROVIDER, "%s.margyt" % package)
        self.detail("%s declared: the mod starts with the app" % STARTUP_PROVIDER)
        self.detail("the MargyT row goes on top of %s" % TIKTOK_SETTINGS.rsplit(".", 1)[-1])

        self.say("Provider authorities")
        shared = manifest_module.shared_authorities(manifest, package)
        renames = {old: old + AUTHORITY_MARKER for old in shared}
        if renames:
            for new in manifest_module.rename_authorities(manifest, renames):
                self.detail(new)
            self.detail("%d the package name does not cover, now ours alone"
                        % len(renames))
        else:
            self.detail("every authority is spelled with the package name, nothing to do")

        arsc = Arsc(apk.read("resources.arsc"))

        self.say("The accent colour")
        self.detail("#%06X" % (self.accent & 0xFFFFFF))
        for line in accent_module.bake(apk, arsc, dexpatch.TIKTOK_PINK, self.accent):
            self.detail(line)

        master = open(os.path.join(self.root, artwork.MASTER_PNG), "rb").read()
        for line in icon_module.replace_everywhere(apk, arsc, manifest, master):
            self.detail(line.strip())

        apk.replace("AndroidManifest.xml", manifest.build())
        if arsc.dirty:
            apk.replace("resources.arsc", arsc.build(), STORED)

        self.say("Rewriting the bytecode")
        self.patch_dex_files(apk, api, renames)

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

    def write_baked_colour(self) -> None:
        """Tell the mod which colour this apk was built with.

        The dex patch swaps colours by value, so it has to know what value to
        look for -- and after baking, the apk's own colour is no longer the
        pink it shipped with.
        """
        path = os.path.join(self.root, "inject", "java", "cat", "narezany", "margyt",
                            "Baked.java")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(
                "package cat.narezany.margyt;\n\n"
                "/**\n"
                " * Written by the build: the accent colour this apk was made with.\n"
                " *\n"
                " * Everything the mod swaps is swapped by value, and after the build has\n"
                " * baked a colour into the resources and the vectors, that value is this\n"
                " * one rather than the pink TikTok ships.\n"
                " */\n"
                "final class Baked {\n\n"
                "    private Baked() {}\n\n"
                "    static final int ACCENT = 0x%08X;\n"
                "}\n" % self.accent
            )

    # ------------------------------------------------------------------ dex

    def patch_dex_files(self, apk: Apk, api: int, literals: Dict[str, str]) -> None:
        smali = Smali(self.tools.smali, api)
        names = sorted(n for n in apk.names() if n.endswith(".dex"))
        candidates: List[str] = []
        for name in names:
            if dexpatch.interesting(apk.read(name), literals):
                candidates.append(name)
        self.detail("%d of %d dex files mention it" % (len(candidates), len(names)))

        telephony_labels = {label for label, _pattern, _target in dexpatch.rules()}
        calls = 0
        pink = 0
        total = 0
        for name in candidates:
            patched, counts = dexpatch.patch(
                apk.read(name), name, smali, os.path.join(self.workspace, "patch"), literals
            )
            if not counts:
                self.detail("%s: nothing to rewrite after all" % name)
                continue
            apk.replace(name, patched)
            hits = sum(counts.values())
            total += hits
            calls += sum(v for label, v in counts.items() if label in telephony_labels)
            pink += counts.get("the pink itself", 0)
            self.detail(
                "%s: %d (%s)"
                % (name, hits, ", ".join("%s x%d" % (k.split("(")[0], v)
                                         for k, v in sorted(counts.items())))
            )
        self.detail("%d telephony call sites, %d places the accent colour was written "
                    "down, %d rewrites in all" % (calls, pink, total))
        if not pink:
            self.detail("WARNING: the accent colour is not a constant in this apk any "
                        "more, so the colour picker will have nothing to change")
        if not calls:
            raise RuntimeError(
                "not one call site matched -- the method signatures have moved, "
                "and the mod would do nothing at all"
            )

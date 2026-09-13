"""The four tools the build needs, fetched once and kept in `tools/`.

Every URL here is pinned to a version and points at Google's Maven, Maven
Central or a release asset -- no "latest" lookups through an API that rate
limits, and no surprise when a tool changes under the build.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from typing import Optional

# apktool's cli jar is used for one thing only: it carries smali and baksmali,
# assembler and disassembler, in a single jar. The apktool part -- decoding and
# rebuilding resources -- is exactly what this build does not do.
SMALI_JAR = (
    "https://repo1.maven.org/maven2/org/apktool/apktool-cli/3.0.3/apktool-cli-3.0.3.jar"
)
R8_JAR = "https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.7.18/r8-8.7.18.jar"
PLATFORM_ZIP = "https://dl.google.com/android/repository/platform-35_r02.zip"
PLATFORM_MEMBER = "android-35/android.jar"
SIGNER_JAR = (
    "https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/"
    "uber-apk-signer-1.3.0.jar"
)


class Toolchain:
    def __init__(self, directory: str, quiet: bool = False):
        self.directory = directory
        self.quiet = quiet
        os.makedirs(directory, exist_ok=True)

    def _say(self, message: str) -> None:
        if not self.quiet:
            print(message)

    def _fetch(self, url: str, name: str) -> str:
        path = os.path.join(self.directory, name)
        if os.path.exists(path):
            return path
        self._say("  fetching %s" % name)
        temporary = path + ".part"
        with urllib.request.urlopen(url) as response, open(temporary, "wb") as out:
            shutil.copyfileobj(response, out)
        os.replace(temporary, path)
        return path

    @property
    def smali(self) -> str:
        return self._fetch(SMALI_JAR, "smali.jar")

    @property
    def r8(self) -> str:
        return self._fetch(R8_JAR, "r8.jar")

    @property
    def signer(self) -> str:
        return self._fetch(SIGNER_JAR, "uber-apk-signer.jar")

    @property
    def android_jar(self) -> str:
        path = os.path.join(self.directory, "android.jar")
        if os.path.exists(path):
            return path
        archive = self._fetch(PLATFORM_ZIP, "platform.zip")
        self._say("  unpacking android.jar")
        with zipfile.ZipFile(archive) as zf, open(path, "wb") as out:
            shutil.copyfileobj(zf.open(PLATFORM_MEMBER), out)
        os.remove(archive)
        return path

    # ------------------------------------------------------------ using them

    def framework_constant(self, field: str) -> int:
        """Read a constant out of android.jar rather than writing it down.

        Framework resource ids never change, but writing one into the source
        makes it a number nobody can check. javap can just read it.
        """
        result = subprocess.run(
            ["javap", "-cp", self.android_jar, "-constants", "android.R$style"],
            capture_output=True,
            text=True,
        )
        for line in result.stdout.splitlines():
            if (" " + field + " = ") in line:
                return int(line.rsplit("=", 1)[1].strip().rstrip(";"))
        raise RuntimeError("android.jar has no android.R.style.%s" % field)

    def check_attribute_ids(self, ids: dict) -> None:
        """Check the framework attribute ids against android.jar.

        Every id the manifest is written with is a public one that has not
        changed since the day it shipped -- but a table of bare numbers is a
        table nobody can check, and one wrong digit writes an attribute that
        means something else entirely.
        """
        result = subprocess.run(
            ["javap", "-cp", self.android_jar, "-constants", "android.R$attr"],
            capture_output=True,
            text=True,
        )
        known = {}
        for line in result.stdout.splitlines():
            if " = " not in line or not line.strip().endswith(";"):
                continue
            name = line.split()[-3]
            known[name] = int(line.rsplit("=", 1)[1].strip().rstrip(";"))
        for name, value in ids.items():
            if name in known and known[name] != value:
                raise RuntimeError(
                    "android:%s is 0x%08x in android.jar, not 0x%08x"
                    % (name, known[name], value)
                )

    def compile_dex(self, sources_dir: str, out_dir: str, min_api: int) -> str:
        """javac then d8: the mod's own classes, as one dex."""
        classes = os.path.join(out_dir, "classes")
        dex = os.path.join(out_dir, "dex")
        for path in (classes, dex):
            shutil.rmtree(path, ignore_errors=True)
            os.makedirs(path)

        sources = []
        for dirpath, _dirs, files in os.walk(sources_dir):
            sources += [os.path.join(dirpath, f) for f in files if f.endswith(".java")]
        if not sources:
            raise RuntimeError("no java sources under %s" % sources_dir)

        _run(
            ["javac", "-nowarn", "-Xlint:-options", "-encoding", "UTF-8",
             "-cp", self.android_jar, "--release", "17", "-d", classes] + sources
        )
        class_files = []
        for dirpath, _dirs, files in os.walk(classes):
            class_files += [os.path.join(dirpath, f) for f in files if f.endswith(".class")]
        _run(
            ["java", "-cp", self.r8, "com.android.tools.r8.D8", "--release",
             "--min-api", str(min_api), "--lib", self.android_jar, "--output", dex]
            + class_files
        )
        return os.path.join(dex, "classes.dex")

    def sign(self, apk_path: str, key: Optional[str] = None) -> None:
        """Sign in place. Without a key, a debug one is made up on the spot."""
        # its zipalign pass runs before signing, which is where alignment has
        # to be settled: the signature block goes in after every entry is placed
        command = ["java", "-jar", self.signer, "-a", apk_path, "--allowResign", "--overwrite"]
        if key:
            command += ["--ks", key]
        _run(command)


def _run(command: list) -> None:
    environment = dict(os.environ)
    # the wrapper's own JVM options print a banner into every tool's output
    environment.pop("JAVA_TOOL_OPTIONS", None)
    result = subprocess.run(command, capture_output=True, text=True, env=environment)
    if result.returncode != 0:
        sys.stderr.write(result.stdout + result.stderr)
        raise RuntimeError("%s failed" % os.path.basename(command[0]))

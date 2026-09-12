#!/usr/bin/env bash
# Build MargyT from an official TikTok apk.
#
#   ./build.sh path/to/tiktok.apk
#
# The apk is not in this repository; bring your own. It has to be a universal
# one -- type APK, not BUNDLE -- arm64-v8a, nodpi. A split bundle cannot be
# rebuilt: apktool has no way to reassemble the resource table from the pieces.

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
APK="${1:-$HERE/apk/tiktok-original.apk}"
WORK="$HERE/work/full"
OUT="$HERE/build"
TOOLS="$HERE/tools"

PKG_OLD="com.zhiliaoapp.musically"
PKG_NEW="cat.narezany.tiktok"
LABEL="MargyT"
LABEL_RES="l0e"          # @string/l0e -- the name under the icon
ICON_RES="c"             # @mipmap/c
ICON_BG="c1v"            # adaptive icon background layer
ICON_FG="c1w"            # adaptive icon foreground layer

R8_VER="9.4.17"
SDK_API="34"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

# ------------------------------------------------------------------- tools

say "Tools"
mkdir -p "$TOOLS"
fetch() { # url dest
    [ -f "$2" ] || { echo "  fetching $(basename "$2")"; curl -sSL -o "$2" "$1"; }
}
APKTOOL_URL=$(curl -sS https://api.github.com/repos/iBotPeaches/Apktool/releases/latest \
    | python3 -c "import sys,json;print([a['browser_download_url'] for a in json.load(sys.stdin)['assets'] if a['name'].endswith('.jar')][0])")
fetch "$APKTOOL_URL" "$TOOLS/apktool.jar"
SIGNER_URL=$(curl -sS https://api.github.com/repos/patrickfav/uber-apk-signer/releases/latest \
    | python3 -c "import sys,json;print([a['browser_download_url'] for a in json.load(sys.stdin)['assets'] if a['name'].endswith('.jar')][0])")
fetch "$SIGNER_URL" "$TOOLS/uber-apk-signer.jar"
fetch "https://dl.google.com/dl/android/maven2/com/android/tools/r8/$R8_VER/r8-$R8_VER.jar" "$TOOLS/r8.jar"
fetch "https://raw.githubusercontent.com/Sable/android-platforms/master/android-$SDK_API/android.jar" "$TOOLS/android.jar"

[ -f "$APK" ] || { echo "No apk at: $APK"; exit 1; }

# ------------------------------------------------------------------ decode

say "Decoding the apk (slow: ~335 MB of dex into smali)"
if [ ! -d "$WORK" ]; then
    java -Xmx10g -jar "$TOOLS/apktool.jar" d "$APK" -o "$WORK" -f
else
    echo "  $WORK exists, reusing it (delete the directory to start clean)"
fi

# ------------------------------------------------------------------ inject

say "Compiling the injected classes"
rm -rf "$HERE/inject/classes" "$HERE/inject/stubs-classes" "$HERE/inject/dex"
mkdir -p "$HERE/inject/classes" "$HERE/inject/stubs-classes" "$HERE/inject/dex"

# The kotlin.* stubs are for the compiler only: the real classes are already
# inside TikTok. They must not reach the dex or they would shadow the originals.
javac -nowarn -cp "$TOOLS/android.jar" --release 17 \
      -d "$HERE/inject/stubs-classes" $(find "$HERE/inject/stubs" -name '*.java')

javac -nowarn -cp "$TOOLS/android.jar:$HERE/inject/stubs-classes" --release 17 \
      -d "$HERE/inject/classes" $(find "$HERE/inject/src" -name '*.java')

# only our own classes go to D8
java -cp "$TOOLS/r8.jar" com.android.tools.r8.D8 \
     --release --min-api 27 --lib "$TOOLS/android.jar" \
     --output "$HERE/inject/dex" $(find "$HERE/inject/classes" -name '*.class')

# -------------------------------------------------------------- call sites

say "Rewriting TelephonyManager call sites"
python3 "$HERE/inject/patch_callsites.py" "$WORK"

say "Handing Region a Context"
python3 "$HERE/inject/patch_bootstrap.py" "$WORK"

say "Adding the MargyT row to the settings list"
python3 "$HERE/inject/patch_settings_cell.py" "$WORK"

# --------------------------------------------------------------- resources

say "Name and icon"
python3 - "$WORK" "$LABEL" "$LABEL_RES" <<'PY'
import sys, re, pathlib
work, label, res = sys.argv[1], sys.argv[2], sys.argv[3]
p = pathlib.Path(work) / "res/values/strings.xml"
s = p.read_text(encoding="utf-8")
s2 = re.sub(r'(<string name="%s">)[^<]*(</string>)' % re.escape(res),
            r'\g<1>%s\g<2>' % label, s)
p.write_text(s2, encoding="utf-8")
print("  label:", "replaced" if s2 != s else "NOT FOUND (@string/%s)" % res)
PY

for dens in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
    src="$HERE/icon_out/tiktok-sized/mipmap-$dens/$ICON_RES.png"
    dst="$WORK/res/mipmap-$dens/$ICON_RES.png"
    [ -f "$src" ] && [ -d "$(dirname "$dst")" ] && cp "$src" "$dst" && echo "  mipmap-$dens/$ICON_RES.png"
done
for dens in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
    for pair in "$ICON_BG:c1v" "$ICON_FG:c1w"; do
        name="${pair%%:*}"; srcname="${pair##*:}"
        src="$HERE/icon_out/tiktok-sized/drawable-$dens/$srcname.png"
        [ -f "$src" ] || continue
        mkdir -p "$WORK/res/drawable-$dens"
        cp "$src" "$WORK/res/drawable-$dens/$name.png"
    done
done
# the original layers are XML drawables; same-named PNGs would collide, so the
# XML versions go away or aapt complains about the duplicate
rm -f "$WORK/res/drawable/$ICON_BG.xml" "$WORK/res/drawable/$ICON_FG.xml"
echo "  adaptive icon layers replaced with bitmaps"

# ---------------------------------------------------------------- manifest

say "Manifest: $PKG_OLD -> $PKG_NEW"
python3 "$HERE/inject/patch_manifest.py" "$WORK" "$PKG_OLD" "$PKG_NEW"

# Two provider authorities are not spelled with the package name at all and
# are shared with anything else built from the same TikTok. Android refuses to
# install two apps claiming one authority, so these have to move too.
say "Making provider authorities unique"
python3 "$HERE/inject/patch_authorities.py" "$WORK" "$PKG_OLD" "$PKG_NEW"

say "Repairing empty PNGs"
python3 "$HERE/inject/fix_empty_pngs.py" "$WORK"

say "Building"
mkdir -p "$OUT"
java -Xmx10g -jar "$TOOLS/apktool.jar" b "$WORK" -o "$OUT/margyt-unsigned.apk"

say "Adding the injected dex"
python3 "$HERE/inject/add_dex.py" "$OUT/margyt-unsigned.apk" "$HERE/inject/dex/classes.dex"

say "Signing"
java -jar "$TOOLS/uber-apk-signer.jar" -a "$OUT/margyt-unsigned.apk" --allowResign --overwrite

say "Done: $OUT/margyt-unsigned.apk"

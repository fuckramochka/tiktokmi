#!/usr/bin/env bash
# Сборка MargyT из оригинального APK TikTok.
#
#   ./build.sh path/to/tiktok.apk
#
# Оригинальный APK в репозиторий не входит — скачивается отдельно.
# Нужен universal APK (тип APK, не BUNDLE), arm64-v8a, nodpi.

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
APK="${1:-$HERE/apk/tiktok-original.apk}"
WORK="$HERE/work/full"
OUT="$HERE/build"
TOOLS="$HERE/tools"

PKG_OLD="com.zhiliaoapp.musically"
PKG_NEW="cat.narezany.tiktok"
LABEL="MargyT"
LABEL_RES="l0e"          # @string/l0e — название под иконкой
ICON_RES="c"             # @mipmap/c
ICON_BG="c1v"            # фон адаптивной иконки
ICON_FG="c1w"            # глиф адаптивной иконки

R8_VER="9.4.17"
SDK_API="34"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

# ---------------------------------------------------------------- инструменты

say "Инструменты"
mkdir -p "$TOOLS"
fetch() { # url dest
    [ -f "$2" ] || { echo "  качаю $(basename "$2")"; curl -sSL -o "$2" "$1"; }
}
APKTOOL_URL=$(curl -sS https://api.github.com/repos/iBotPeaches/Apktool/releases/latest \
    | python3 -c "import sys,json;print([a['browser_download_url'] for a in json.load(sys.stdin)['assets'] if a['name'].endswith('.jar')][0])")
fetch "$APKTOOL_URL" "$TOOLS/apktool.jar"
SIGNER_URL=$(curl -sS https://api.github.com/repos/patrickfav/uber-apk-signer/releases/latest \
    | python3 -c "import sys,json;print([a['browser_download_url'] for a in json.load(sys.stdin)['assets'] if a['name'].endswith('.jar')][0])")
fetch "$SIGNER_URL" "$TOOLS/uber-apk-signer.jar"
fetch "https://dl.google.com/dl/android/maven2/com/android/tools/r8/$R8_VER/r8-$R8_VER.jar" "$TOOLS/r8.jar"
fetch "https://raw.githubusercontent.com/Sable/android-platforms/master/android-$SDK_API/android.jar" "$TOOLS/android.jar"

[ -f "$APK" ] || { echo "Нет APK: $APK"; exit 1; }

# ------------------------------------------------------------------- разбор

say "Разбор APK (долго: ~335 МБ dex в smali)"
if [ ! -d "$WORK" ]; then
    java -Xmx10g -jar "$TOOLS/apktool.jar" d "$APK" -o "$WORK" -f
else
    echo "  $WORK уже есть, пропускаю (удали каталог, чтобы пересобрать с нуля)"
fi

# ------------------------------------------------------------------- инжект

say "Компиляция инжекта в classes53.dex"
rm -rf "$HERE/inject/classes" "$HERE/inject/dex"
mkdir -p "$HERE/inject/classes" "$HERE/inject/dex"
javac -nowarn -cp "$TOOLS/android.jar" --release 17 \
      -d "$HERE/inject/classes" $(find "$HERE/inject/src" -name '*.java')
java -cp "$TOOLS/r8.jar" com.android.tools.r8.D8 \
     --release --min-api 27 --lib "$TOOLS/android.jar" \
     --output "$HERE/inject/dex" $(find "$HERE/inject/classes" -name '*.class')

# --------------------------------------------------------------- call-sites

say "Перенаправление вызовов TelephonyManager"
python3 "$HERE/inject/patch_callsites.py" "$WORK"

# ---------------------------------------------------------------- ресурсы

say "Название и иконка"
python3 - "$WORK" "$LABEL" "$LABEL_RES" <<'PY'
import sys, re, pathlib
work, label, res = sys.argv[1], sys.argv[2], sys.argv[3]
p = pathlib.Path(work) / "res/values/strings.xml"
s = p.read_text(encoding="utf-8")
s2 = re.sub(r'(<string name="%s">)[^<]*(</string>)' % re.escape(res),
            r'\g<1>%s\g<2>' % label, s)
p.write_text(s2, encoding="utf-8")
print("  название:", "заменено" if s2 != s else "НЕ НАЙДЕНО (@string/%s)" % res)
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
# оригинальные слои — XML-драйблы; растровые того же имени их перекрывают,
# поэтому XML-версии убираем, иначе aapt ругнётся на дубль
rm -f "$WORK/res/drawable/$ICON_BG.xml" "$WORK/res/drawable/$ICON_FG.xml"
echo "  слои адаптивной иконки заменены на растровые"

# ---------------------------------------------------------------- манифест

say "Манифест: $PKG_OLD -> $PKG_NEW"
python3 - "$WORK" "$PKG_OLD" "$PKG_NEW" <<'PY'
import sys, re, pathlib
work, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
p = pathlib.Path(work) / "AndroidManifest.xml"
s = p.read_text(encoding="utf-8")

# Схемы OAuth трогать нельзя: на них завязан вход через Google (AppAuth).
keep = re.findall(r'android:scheme="com\.googleusercontent\.apps\.[^"]+"', s)

s = s.replace('package="%s"' % old, 'package="%s"' % new)
s = re.sub(r'(android:(?:authorities|name|scheme|targetPackage)=")%s' % re.escape(old),
           r'\g<1>%s' % new, s)

for k in keep:
    assert k in s, "потеряна OAuth-схема: %s" % k

# наша активность настроек
if "cat.narezany.tiktok.MargyTSettingsActivity" not in s:
    s = s.replace("</application>",
        '    <activity android:name="cat.narezany.tiktok.MargyTSettingsActivity"\n'
        '        android:exported="false"\n'
        '        android:theme="@android:style/Theme.Material.NoActionBar" />\n'
        '</application>')

p.write_text(s, encoding="utf-8")
print("  пакет переименован, OAuth-схемы на месте (%d шт), активность добавлена" % len(keep))
PY

# захардкоженные authorities (остальные строятся как getPackageName()+суффикс)
say "Захардкоженные authorities в smali"
grep -rl "$PKG_OLD.draftprovider\|$PKG_OLD.wallpapercaller" "$WORK"/smali* 2>/dev/null \
    | while read -r f; do
        sed -i "s/$PKG_OLD\.draftprovider/$PKG_NEW.draftprovider/g; \
                s/$PKG_OLD\.wallpapercaller/$PKG_NEW.wallpapercaller/g" "$f"
        echo "  $(basename "$f")"
      done || true

# ------------------------------------------------------------------ сборка

say "Сборка"
mkdir -p "$OUT"
java -Xmx10g -jar "$TOOLS/apktool.jar" b "$WORK" -o "$OUT/margyt-unsigned.apk"

say "Добавление classes53.dex"
python3 - "$OUT/margyt-unsigned.apk" "$HERE/inject/dex/classes.dex" <<'PY'
import sys, zipfile, shutil, re, os
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk) as z:
    n = max(int(re.search(r'classes(\d*)\.dex', x).group(1) or 1)
            for x in z.namelist() if re.fullmatch(r'classes\d*\.dex', x))
name = "classes%d.dex" % (n + 1)
with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, name)
print("  добавлен как", name)
PY

say "Подпись"
java -jar "$TOOLS/uber-apk-signer.jar" -a "$OUT/margyt-unsigned.apk" --allowResign --overwrite

say "Готово: $OUT/margyt-unsigned.apk"

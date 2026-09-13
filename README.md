<div align="center">

<img src="assets/banner.png" alt="MargyT" width="640">

**A TikTok mod for Android.**
The second in the line after [Margy](https://github.com/narezany/Margelet).
Built from the official apk with the patches in this repository.

[![channel](https://img.shields.io/badge/channel-margeletter-8DD1B0?style=flat-square)](https://t.me/margeletter)
[![forum](https://img.shields.io/badge/forum-margeletforum-8DD1B0?style=flat-square)](https://t.me/margeletforum)
[![licence](https://img.shields.io/badge/licence-MIT-8DD1B0?style=flat-square)](#licence)

</div>

---

Keeps TikTok's own package, `com.zhiliaoapp.musically`, so it goes in **instead
of** the official app rather than beside it: uninstall TikTok first, then
install this. arm64 only. No root.

Everything the mod adds is on one screen: **Settings and privacy → MargyT**,
the first row, and on the launcher as **MargyT settings** as well.

## What it adds

<details>
<summary><b>The country the app thinks you are in</b></summary>

TikTok asks the device where it is: the SIM's country, the carrier, the MCC/MNC
pair, whether a card is present at all. Every one of those answers now comes
from the mod instead of from the phone. Twenty-four countries are on the list,
the Netherlands by default.

The interface language is left alone. Only the region changes — otherwise the
app would switch itself to Dutch along with the country.

This reaches `carrier_region` and `sys_region`, which is what the feed and a
good part of the feature gates read. It does not reach `store_region`: that one
is fixed on the server when the account is registered, and no client can move
it. Your IP is a separate matter and wants a VPN.

Switched off, the mod is not in the way: every redirected call hands the
question straight back to the real one, down to the exception it would have
thrown.
</details>

<details>
<summary><b>Its own name and icon</b></summary>

`MargyT`, in the mint the whole line uses — `#8DD1B0`, straight off the Margy
banner — with a white note where Margy has a white paper plane.

The icon is not added as a new resource; the files behind TikTok's own icon are
rewritten where they lie. The adaptive icon's two layers become vector
drawables compiled by this repository, and each legacy density gets a bitmap
scaled to exactly the size the one it replaces was. The resource id, the table
entry and the density each file was chosen for never move.
</details>

<details>
<summary><b>A row in TikTok's own settings</b></summary>

**Settings and privacy → MargyT**, at the top of the list, drawn like the rows
around it. Inside: a switch for the whole thing and the list of countries, each
with the carrier and MCC/MNC it will report.

The row is not written into the bytecode that builds that list, and it could
not be: the screen is Jetpack Compose. One `ComposeView` draws the title, the
back arrow and every row, and from outside it there is nothing to get between
them. So the row goes above the screen instead of inside the list.

The mod gets there through a start-up hook of its own -- a `<provider>`, which
Android instantiates before the application's onCreate whether anything queries
it or not -- and from there watches for the settings screen. When it appears,
the mod asks the screen where it keeps its pages, wraps that container from the
outside, and puts the row above it. The container itself is left exactly where
it was, because that is what the fragment manager adds pages to and takes them
out of.

Two names hold this up, and both are real rather than obfuscated:
`SettingContainerActivity` and its own `getFragmentContainer()`. Androidx is no
help -- it is in the apk with its method names shortened away, `getFragments()`
included -- and the build checks the manifest still declares that screen,
stopping rather than shipping a mod whose settings cannot be reached.

The row is not styled by hand either. TikTok's rows are Compose, drawn from
colours and dimensions that live in obfuscated Kotlin -- and the resource table
is no help, since TikTok's own colours are called `ag` and `ah` in there. So
the mod draws the screen it is about to sit on into a bitmap of its own and
reads the style out of the pixels: the card colour, the text colour, the margin
the cards keep from the edge, the radius of their corners. Whatever TikTok is
drawing today, in whichever theme, is what the row is built from, and the diary
records the numbers it found.

The screen behind the row is built in code with no layout or style resources at
all: adding a resource would mean rewriting a 25 MB resource table, which is
the one thing this build refuses to do.

There is a launcher entry as well, **MargyT settings**, which opens the same
screen. It is the way in when the row is not there.

</details>

<details>
<summary><b>No root, and no Xposed either</b></summary>

Without root there is no Xposed, and without Xposed there is nothing to hook at
runtime. So the calls are rewritten in the bytecode instead. Every

```smali
invoke-virtual {v0}, Landroid/telephony/TelephonyManager;->getSimCountryIso()Ljava/lang/String;
```

becomes

```smali
invoke-static {v0}, Lcat/narezany/margyt/Region;->getSimCountryIso(Landroid/telephony/TelephonyManager;)Ljava/lang/String;
```

The instruction format (35c), the register count and the return type all match,
so nothing has to be renumbered. The receiver moves to the first argument and is
handed back the real answer whenever the mod is off.

In 46.9.42 that is 26 call sites across three of the apk's fifty-two dex files.
`margyt/dexpatch.py` finds them by signature rather than by offset, which is why
it should survive the next TikTok release.

Nothing patches TikTok's `Application` class either. The mod needs a context to
read its settings and takes it from `ActivityThread.currentApplication()`, which
every Android process has and which no release can rename.
</details>

<details>
<summary><b>Signing in with Google still works</b></summary>

Resigning an apk changes its certificate, and Google Sign-In through Play
Services checks the package name against that certificate's SHA-1. For anything
built here that check can only fail: the package name is still TikTok's and the
certificate is not. Left alone the button leads to a developer error and no way
forward.

TikTok has a second way in, though, and it is the one it falls back to when
Play Services is not around: AppAuth, with a custom-scheme redirect --
`com.googleusercontent.apps.<client_id>` -- and PKCE, in the browser. Google
does not check package or signature for that kind of client; it only checks
that whoever claims the scheme receives the redirect. So the build makes the
Play Services provider report itself unavailable, and the app takes its own
fallback:

```smali
# com/bytedance/lobby/google/GoogleAuth
.method public final isAvailable()Z
    .registers 1
    const/4 v0, 0x0
    return v0
.end method
```

which is enough, because the decision reads:

```java
provider = registry.get("google");
if (provider != null && provider.isAvailable()) return "google";
return "google_web";
```

The class name there is a real one rather than an obfuscated one, which is what
makes it safe to anchor on -- and if a later release moves the method, the build
stops instead of quietly shipping a dead button.

</details>

## How the build works

The short version: **the apk is never taken apart.** No apktool, no aapt2,
nothing that rebuilds a resource table it did not write. The build edits the
bytes that have to change and copies everything else across exactly as it found
it, which is why it takes about two minutes rather than an afternoon.

| what | how |
|---|---|
| the zip | rewritten entry by entry, each one still compressed the way it arrived; `resources.arsc` stays stored and four-byte aligned |
| `AndroidManifest.xml` | parsed and rebuilt by `margyt/axml.py`, which round-trips aapt2's own output byte for byte |
| `resources.arsc` | read to find out where the icon lives, and written only in place: a colour repainted where it lies, never a byte moved |
| the icon | the files behind the existing resource are replaced, so no new id is ever needed |
| the dex | only the three or four files that mention telephony go through baksmali and smali; the other forty-eight are copied |
| the mod | javac and d8, in as the next `classesN.dex` — the run has to be unbroken or the runtime stops reading |

The package name stays TikTok's. Renaming it is what made the earlier version of
this repository hard to trust: provider authorities collide with the official
app, the OAuth redirect scheme ends up claimed twice, and every
`com.zhiliaoapp.musically.something` string inside fifty-two dex files becomes
half true. The cost of keeping it is that the two cannot be installed side by
side — the signature differs, so Android will not put this one over the
official app, and the official app has to go first.

## Building it yourself

You need a JDK (17 or newer) and Python 3. Nothing else: smali, d8, android.jar
and the signer are downloaded into `tools/` on the first run, each pinned to a
version. And a **universal** apk of TikTok — on APKMirror the variant of type
**APK**, not **BUNDLE**, `arm64-v8a`, `nodpi`. A split has no resource table of
its own to read.

```bash
git clone https://github.com/narezany/MargyT
cd MargyT
./build.sh path/to/tiktok.apk
```

The apk lands in `build/`. Roughly two minutes on four cores, most of it smali
reassembling the dex files that were touched.

```
==> Opening the apk
    26151 entries
    package com.zhiliaoapp.musically, staying as it is
    minSdk 23, dex 035
==> Rewriting the telephony call sites
    4 of 52 dex files mention it
    classes22.dex: 19 call sites
    classes32.dex: 5 call sites
    classes4.dex: 2 call sites
```

Everything the build produces is made for the apk's own minSdk, and it checks:
a dex assembled for a newer api is stamped with a newer format, and an Android
that does not know that format refuses the whole app rather than the one file.
TikTok's is dex 035, back to Android 6 — which is not where this would have been
noticed.

If no call site matches, the build stops rather than handing you an apk that
quietly does nothing.

## Tests

```bash
python3 -m unittest discover tests
```

No toolchain, no network, about a second. They run against
`tests/data/fixture.apk` — seven kilobytes, built by aapt2 from `tests/fixture/`
and checked in — which has what the real apk has: a label from a string
resource, an adaptive icon whose layers are vectors, the icon at two densities,
a launcher entry that is an alias rather than an activity.

Rebuild the fixture with `tests/make_fixture.sh` if you change it; that is the
only thing here that wants aapt2.

## What this repository does not contain

- **TikTok's apk**, and nothing derived from it: not the resources, not the
  smali. It is someone else's proprietary code and it is not ours to
  redistribute. The build needs the apk; you bring your own.
- **A signing key.** uber-apk-signer generates a debug one on the spot. Ship the
  result to anyone and they will have to uninstall before they can take an
  update signed by a different key.

## Files here

| | |
|---|---|
| `build.sh` | the whole thing, one command |
| `margyt/axml.py` | binary XML: parse, edit, write |
| `margyt/arsc.py` | the resource table, read and patched in place |
| `margyt/apkzip.py` | the zip, rewritten entry by entry |
| `margyt/dexpatch.py` | the call sites, found by signature |
| `margyt/icon.py` | the icon, replaced file by file |
| `margyt/png.py` | just enough PNG to resize an icon, so Pillow is not needed |
| `margyt/vector.py` | vector drawables, compiled without aapt2 |
| `inject/java/` | the mod itself: the settings screen and the methods the call sites land in |
| `icon_out/` | the icon: the 512px master the build scales from, and the density set |
| `tests/` | the fixture apk and what is asserted about it |

`margyt/dexpatch.py` is the file to read before moving the mod to a newer
TikTok. Everything else is either ours outright or finds its own targets; the
call sites are the part that has to find them again in a rebuilt apk.

## Licence

MIT, for the code in this repository. TikTok's apk is not here and its terms are
its own — this repository is patches, not a redistribution.

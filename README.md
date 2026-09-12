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

Package `cat.narezany.tiktok`. It installs **next to** the official TikTok,
not over it. arm64 only. No root.

Everything the mod adds lives in one place: **Settings → MargyT**, the first row.

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
</details>

<details>
<summary><b>Its own name and icon</b></summary>

`MargyT`, in the mint the whole line uses — `#8DD1B0`, straight off the Margy
banner — with a white note where Margy has a white paper plane. Legacy and
adaptive icons both, at TikTok's own densities, which start at 56dp rather than
the usual 48.
</details>

<details>
<summary><b>A settings screen of its own</b></summary>

**Settings → MargyT**, above everything else, in TikTok's own list rather than
bolted over it. Inside: a switch for the whole thing and the list of countries,
each with the carrier and MCC/MNC it will report.

The screen is built in code, without layout resources. Adding resources means
new ids, and new ids mean a fight with aapt every time the app is rebuilt.
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
invoke-static {v0}, Lcat/narezany/tiktok/Region;->getSimCountryIso(Landroid/telephony/TelephonyManager;)Ljava/lang/String;
```

The instruction format (35c), the register count and the return type all match,
so nothing has to be renumbered. The receiver moves to the first argument and is
ignored; it is there only to keep the arity.

Five of the apk's fifty-two dex files touch telephony at all, twenty-six call
sites between them. `inject/patch_callsites.py` finds them by signature rather
than by offset, which is why it should survive the next TikTok release.
</details>

<details>
<summary><b>Signing in with Google still works</b></summary>

It was supposed to be the thing that broke. Resigning an apk changes its
certificate, and Google Sign-In through Play Services checks the package name
and the certificate's SHA-1 — so a repackaged app cannot use it.

TikTok does not use it. It opens the browser with AppAuth and a custom-scheme
redirect, `com.googleusercontent.apps.<client_id>`, with PKCE. Google does not
check package or signature for that kind of client; it only checks that whoever
claims the scheme receives the redirect. The build keeps all three schemes in
the manifest untouched and the flow goes through.

One catch: leave the official TikTok installed and both apks claim the same
scheme, so Android will ask which one should take the redirect. The scheme is
fixed by TikTok's client id and cannot be changed. Pick MargyT.
</details>

## Building it yourself

You need JDK 17 or newer, Python 3 with Pillow, and a **universal** apk of
TikTok — on APKMirror the variant of type **APK**, not **BUNDLE**, `arm64-v8a`,
`nodpi`. A split bundle cannot be rebuilt: apktool has no way to put the
resource table back together from the pieces.

```bash
git clone https://github.com/narezany/MargyT
cd MargyT
./build.sh path/to/tiktok.apk
```

The script fetches apktool, uber-apk-signer, d8 and android.jar on its own,
takes the apk apart, rewrites the call sites, swaps the name and the icon,
renames the package, puts it back together and signs it. The apk lands in
`build/`.

Taking 335 MB of dex apart takes a while. The decoded tree is left in `work/`
and reused on the next run; delete it to start clean.

The signature is ours, which is the point — with TikTok's own it would install
over the official app instead of beside it.

## What this repository does not contain

- **TikTok's apk**, and nothing derived from it: not the decoded resources, not
  the smali. It is someone else's proprietary code and it is not ours to
  redistribute. The build needs the apk; you bring your own.
- **A signing key.** uber-apk-signer generates a debug one on the spot. Ship the
  result to anyone and they will have to uninstall before they can take an
  update signed by a different key.

## Files here

| | |
|---|---|
| `build.sh` | the whole repack, one command |
| `inject/patch_callsites.py` | rewrites the `TelephonyManager` calls, by signature |
| `inject/src/` | the Java that becomes `classes53.dex` |
| `inject/stubs/` | `kotlin.*` declarations the compiler needs and the dex must not have |
| `icon_out/` | the icon, at every density |
| `assets/` | the banner, and the script that draws it |
| `app/` | an LSPosed module doing the same thing by hooking, for rooted phones |

`inject/patch_callsites.py` is the file to read before moving the mod to a newer
TikTok. Everything else is either ours outright or plain resource swapping; the
call sites are the part that has to find its targets again in a rebuilt apk.

## Licence

MIT, for the code in this repository. TikTok's apk is not here and its terms are
its own — this repository is patches, not a redistribution.

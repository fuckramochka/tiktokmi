<div align="center">

<img src="assets/banner.png" alt="TikTok MI" width="640">

# TikTok MI

**A modern, feature-rich TikTok modification for Android with Material Design 3, privacy enhancements, ad blocking, media downloads, and deep personalization.**

[![version](https://img.shields.io/badge/version-0.4.1-8DD1B0?style=flat-square)](VERSION)
[![plugins](https://img.shields.io/badge/plugins-docs-8DD1B0?style=flat-square)](docs/plugins.md)
[![licence](https://img.shields.io/badge/licence-MIT-8DD1B0?style=flat-square)](#licence)

</div>

---

TikTok MI retains TikTok's original package name, `com.zhiliaoapp.musically`, replacing the official app rather than sitting beside it. It runs on `arm64-v8a` devices and requires **no root**.

All configuration lives in one unified screen: **Settings and privacy → TikTok MI** (at the top of settings). There is no extra launcher icon cluttering your home screen.

Nothing is hidden or silently broken: every toggle explains its scope, the mod maintains an active **Diary** of what it intercepts and applies, and experimental features are documented honestly.

---

## Features

### 🎨 Material Design 3 & Themes
- **Material You Dynamic Palette:** Dynamically extracts colors from your Android 12+ wallpaper and paints the UI using Monet tones.
- **Custom Accent Colour:** Re-tint the entire app's primary pink accent (`#FE2C55`) to any custom hex color (`--accent RRGGBB`), keeping gradients, highlights, and opacities natural.
- **True Theme Repainting:** Repaints text, backgrounds, cards, and dialogs only where TikTok itself allows theme switching.
- **Launcher Icons:** Choose from 10 distinct icon styles directly from settings (Default, Graffiti, Shine, TikTok Cosplay, Material You, Material 3, Doodle, Glitch, Google, Terminal).
- **Miogram Bridge:** 1-click theme and accent synchronization between TikTok MI and Telegram/Miogram.

### 📥 Downloads & Media Tools
- **QualitySheet:** Choose video quality (576p, 480p HEVC) or extract audio only before downloading.
- **No Watermarks:** Download videos and photos completely clean without TikTok logos or username watermarks.
- **Asset Saving:** Long-press to save avatar profile pictures and stickers directly to your device's gallery.

### ⏱ Exact Publication Metadata (TimeSheet)
- Displays the precise upload date and time of any video down to the second.
- Shows day of the week, local timezone, UTC time, and the underlying numeric Aweme ID with one-tap copy functionality.

### 👻 Ghost Mode (Privacy)
- **Stealth Direct Messages:** Read incoming direct messages without triggering "Read" / "Seen" status receipts for the sender.
- **Anonymous Watching:** Watch Stories and LIVE broadcasts completely anonymously without appearing in the viewer list.
- **Ghost Recall:** Detects and retains recalled/deleted messages in chats, logging the action in your diary.

### 🎬 Feed Customization & Ad Blocking
- **Pure Feed:** Completely removes in-feed sponsored posts, commercial video banners, and splash launch advertisements.
- **Streamlined Content:** Toggles to hide LIVE streams, photo carousels, and story posts from your main feed.
- **Pure Black OLED Mode:** Replaces dark-grey surfaces with true `#000000` black for OLED display power savings and high contrast.
- **Scrubbing Everywhere:** Forces the video seekbar on all videos regardless of length.
- **Keyword Filtering:** Hide videos containing blacklisted tags or keywords in captions.
- **Region Matching:** Option to prioritize feed content matching your chosen spoofed location.

### 🔥 Streaks Automation
- **Scheduled Streak Maintenance:** Automatically keeps daily conversation streaks alive at 12:00 or a custom scheduled time.
- **Smart Conversations Filtering:** Scans active chats for grey flame streaks, skipping expired or non-chat identifiers.
- **Reliable Fallback:** Supports both text and sticker modes with automatic fallback so streaks are never broken.

### 🌐 Region & SIM Spoofing
- Spoofs SIM country, operator name, MCC, and MNC pairs across 24 countries (Netherlands by default, USA, Ukraine, Germany, UK, etc.).
- Bypasses regional feed blocks and feature rollouts without altering the app interface language.

### 🧩 Plugin Engine
- Extensible via `.mtp` / `.mip` dex plugins.
- See [docs/plugins.md](docs/plugins.md) for plugin development specifications and sample code in [examples/hello](examples/hello).

### 🔄 In-App Updater & Diary
- Automatic version check against repository releases every 5 minutes.
- Resumable chunk downloading for seamless in-app upgrades.
- Integrated **Diary** with 150-line timestamped logs detailing intercepted flags, spoofed answers, and streaks activity.

---

## Installation & Updating

### First-Time Installation
1. Back up any drafts or offline videos in official TikTok.
2. Uninstall the official TikTok app.
3. Install `tiktokmi.apk`.
4. Open TikTok, sign in, and go to **Profile → Settings and privacy → TikTok MI**.

### ⚠️ Package Conflict (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`)

When attempting to update via the built-in updater or installing a new APK over an existing one, Android might display:
> *"App not installed as package conflicts with an existing package"* / *"Не вдалося встановити через конфлікт пакетів"*.

**Why this happens:**
Android requires that any APK updating an already installed package (`com.zhiliaoapp.musically`) must be signed with the **exact same signing key (certificate)** as the version currently on the device.
- If you installed an APK signed with Key A (e.g. a pre-built release or debug key from another computer), and then try to update with an APK signed with Key B (e.g. built locally with a random keystore), Android's Package Manager immediately halts installation to protect app data.

**How to avoid & resolve:**
1. **Always sign with a consistent keystore:**
   When building, provide a keystore file via `--keystore path/to/keystore.jks` or place `tiktokmi.keystore` in the root of the repository. The build toolchain will automatically pick it up.
2. **If switching signing keys:**
   Uninstall the existing app first, then install the newly signed APK. Subsequent updates signed with that same key will install without conflicts.

---

## Building from Source

### Prerequisites
- **Python 3.8+**
- **JDK 17+** (`javac`, `java`)
- An official universal TikTok APK:
  - Download from APKMirror: architecture `arm64-v8a`, `nodpi`, format **APK** (not BUNDLE).
  - Split bundles cannot be processed because they lack a standalone resource table.

### Build Commands

```bash
# Clone repository
git clone https://github.com/fuckramochka/tiktokmi.git
cd tiktokmi

# Build default APK (signed with default/project keystore into build/tiktokmi.apk)
./build.sh path/to/tiktok.apk

# Build with a custom accent color (HEX)
./build.sh path/to/tiktok.apk --accent 8DD1B0

# Build with a specific keystore
./build.sh path/to/tiktok.apk --keystore /path/to/your.keystore
```

The resulting patched APK is written to `build/tiktokmi.apk`.

### Automated Tests
Run unit tests without needing external toolchains or an APK:
```bash
python3 -m unittest discover tests
```
Runs 59 automated test suites against `tests/data/fixture.apk`.

---

## Project Structure

| Path | Purpose |
|---|---|
| `build.sh` | Main shell wrapper for compiling and patching |
| `tiktokmi/build.py` | Complete build orchestration pipeline |
| `tiktokmi/dexpatch.py` | Dalvik bytecode analysis, anchors, and smali rewrite rules |
| `tiktokmi/toolchain.py` | Pinned toolchain downloader (R8/D8, apktool-cli/smali, uber-apk-signer, android.jar) |
| `tiktokmi/axml.py` | Binary Android XML parser and editor |
| `tiktokmi/arsc.py` | `resources.arsc` table patcher |
| `tiktokmi/palette.py` | Accent color mathematics and color replacement maps |
| `inject/java/mi/tiktokmi/` | Mod runtime source code (MD3 Settings, QualitySheet, TimeSheet, Ghost, Streaks, etc.) |
| `inject/stubs/` | Compilation stubs matching obfuscated TikTok framework classes |
| `badges.json` | Remote badges mapping checked on startup |
| `version.json` | Remote version declaration for the in-app updater |
| `docs/plugins.md` | Plugin format and API documentation |

---

## Known Limitations

- **Store Region vs SIM Region:** The mod spoofs `carrier_region` and `sys_region` (which drive feed algorithms and feature availability). It cannot change `store_region`, which is permanently bound on ByteDance servers during account registration.
- **Profile Banners:** Profile header banners are server-driven. Toggling the Anti A/B banner switch enables the client-side UI flags, but actual banner rendering still requires server-side support for your account.
- **2x Playback Speed Lock:** TikTok natively supports holding down on a video to play at 2x. A custom gesture lock overlay is currently in active development.

---

## Licence

Code in this repository is licensed under the [MIT Licence](LICENSE).  
The Material icons under `icons/` are licensed under Apache 2.0.  
TikTok itself is proprietary software belonging to ByteDance Ltd. This repository contains only patches and tools, and does not distribute TikTok's proprietary APK or binaries.

<p align="center">
  <img src="docs/images/banner.webp" alt="Afya: your scale, read on your phone" width="100%">
</p>

<p align="center">
  <a href="https://github.com/airiermonster/afya/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/airiermonster/afya?color=0F6B62&label=release"></a>
  <img alt="Android 12+" src="https://img.shields.io/badge/Android-12%2B-0F6B62?logo=android&logoColor=white">
  <img alt="Kotlin and Jetpack Compose" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-0F6B62?logo=kotlin&logoColor=white">
  <a href="LICENSE"><img alt="Licence GPL-3.0" src="https://img.shields.io/badge/licence-GPL--3.0-0F6B62"></a>
</p>

**Afya** (Swahili for *health*) is an open-source Android app that reads OKOK / Chipsea Bluetooth
body-composition scales directly — no vendor app, no account, no subscription, no ads. The weight
your scale's own display shows is the number Afya shows first, and every body-composition figure it
derives is worked out on your phone and labelled as the estimate it is.

## Why

Scales in the OKOK family ship with an app that keeps your readings behind an account, a cloud and
a paid tier. The scale itself just broadcasts its figures over Bluetooth to anything listening.
Afya listens, decodes the broadcast, and keeps everything on the phone.

## Features

**Weighing in**
- Reads the scale's Bluetooth broadcast — no pairing — and shows the figure exactly as the scale's
  display does, with the conversion to your chosen unit as a secondary line.
- Files the reading automatically once the scale locks, keeps the screen on while you stand there,
  and still saves a reading that locked just as the phone went to sleep.
- **"Is this you?"** — a weight far from your last two weeks is held until you say whose it is, so
  whoever you lend your phone to never ends up in your history.
- Launcher shortcut, Quick Settings tile and an optional daily reminder that open straight onto the
  weigh-in screen, and skip days you have already weighed in.

**Understanding it**
- **Trend weight**: a ten-day smoothed weight beside each reading, so a heavy morning reads as the
  water swing it usually is.
- Nineteen body-composition estimates (body fat, muscle, water, protein, bone, visceral fat, resting
  energy and more), each with its reference range, a plain word for where it sits, and one sentence
  on what it is.
- Tap any figure for its history across every weigh-in.
- Compositions are only compared between readings taken with the same height, age and sex —
  editing your profile never shows up as a change in your body.
- Goal projection from a least-squares fit of your last four weeks, and daily water and energy
  targets.

**Your records**
- Weight chart with the trend drawn over it, filters by month and by morning / afternoon / evening,
  and side-by-side comparison of any two weigh-ins.
- Eleven tape-measurement sites with their own history, a drink log and a food log.
- Exports: a designed **PDF health record**, **CSV**, and **JSON backups** that can be sealed with a
  passphrase (AES-256-GCM, key stretched with PBKDF2).
- A home-screen widget with your last weight and the month behind it.

**Privacy**
- No account, no analytics, no advertising SDK. Backups are only ever made by you, to a place you choose.
- The only network request Afya can make is the one-time key lookup an *encrypted* scale needs, and
  every such request is listed in **You → Advanced → Network log**.
- Optional **Health Connect** sharing copies the measured weight only — estimates are never written
  where other apps would show them as measurements.

## Install

Download an APK from the [latest release](https://github.com/airiermonster/afya/releases/latest).
Every APK is the same app; they differ only in which processor they carry native code for.

| File | Pick it if |
| --- | --- |
| `afya-<version>-universal-release.apk` | **Not sure? Use this one.** Runs on every Android 12+ phone. |
| `afya-<version>-arm64-v8a-release.apk` | Almost every phone from the last several years (64-bit ARM). |
| `afya-<version>-armeabi-v7a-release.apk` | Older or budget phones with a 32-bit ARM processor. |
| `afya-<version>-x86_64-release.apk` | Emulators and Chromebooks with an Intel or AMD processor. |
| `afya-<version>-x86-release.apk` | Older 32-bit Intel emulators. |

To check your phone's architecture, connect it with USB debugging and run
`adb shell getprop ro.product.cpu.abi`, or just install the universal APK.

1. Open the downloaded APK on your phone and allow your browser or file manager to install unknown apps when asked.
2. Open Afya, allow **Nearby devices** (Bluetooth scanning; Afya never asks for location), fill in
   your height, birthday and sex on the **You** tab, and step on the scale.

Each release lists SHA-256 checksums in `SHA256SUMS.txt`. Verify a download with
`certutil -hashfile <file> SHA256` on Windows or `sha256sum <file>` on Linux and macOS.

**Requirements:** Android 12 or newer, Bluetooth Low Energy, and an OKOK / Chipsea scale that
broadcasts its readings.

## Supported scales

Afya decodes the Chipsea advertising frames used by scales sold under the OKOK app:

| Frame | Status |
| --- | --- |
| `0xC0` broadcast V2 | Verified on hardware (product id 2577), including the scale's own unit and decimals. |
| `0xC0` encrypted | Implemented — needs the scale's one-time key, fetched once and cached. Not yet observed live. |
| `0xCA` broadcast V1, `0xB0` short frame | Implemented from the protocol, not yet seen on real hardware. |

If your scale shows up in **You → Scale** but reads wrongly, turn on **Show raw BLE frames** under
Advanced and open an issue with a few frames — they contain no personal data beyond the weight.

## Body composition, honestly

The vendor computes composition from bioelectrical impedance with a closed native library. Afya does
not use or reimplement it. Every figure comes from published population equations driven by your
weight, height, age and sex:

- Body fat — Deurenberg et al. (1991)
- Total body water — Hall's lean-body hydration constant (water = fat-free mass × 0.732)
- Resting energy — Mifflin–St Jeor (1990)
- Visceral fat area — Yuki et al. (2004)
- Muscle, protein and bone mineral — standard reference fractions of fat-free mass

These are **estimates for a population, not measurements of you**, and the app says so wherever it
shows one. Afya is not a medical device and is not medical advice.

## Build from source

Requirements: JDK 21 and the Android SDK (compile SDK 37).

```bash
git clone https://github.com/airiermonster/afya.git
cd afya
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Release builds are signed with a key you provide. Copy `keystore.properties.example` to
`keystore.properties` (it is git-ignored), point it at your keystore, then:

```bash
./gradlew :app:assembleRelease
```

The APKs land in `app/build/outputs/apk/release/`, one per architecture plus a universal one.

## Project layout

```
app/src/main/java/com/maximillianurio/afya/
  core/    Pure Kotlin: the scale protocol, decoding and de-obfuscation, body-composition equations,
           the report, trend weight, targets, the guest check. Unit-tested on the JVM.
  data/    SQLite history, preferences, backup and its encryption, PDF and CSV export, Health Connect.
  app/     The view model that owns the Bluetooth scan, the store and the database.
  ui/      Jetpack Compose screens, components, theme and the brand mark.
brand/     The logo, lockup and banner as SVG masters.
docs/      HANDOFF.md — the reverse-engineered protocol spec and architecture notes.
```

The unit tests cover every value of the scale's property byte, the 15-bit obfuscation, body
composition against hand-computed values, the trend and projection maths, the guest check, the
profile comparison and the backup encryption.

## Not affiliated

Afya is an independent project. It is not made, endorsed or supported by Chipsea Technologies or by
the makers of the OKOK app. OKOK and Chipsea are the property of their respective owners and are
named here only to say which scales Afya can read. No vendor code, artwork, sounds or text are
included.

## Licence

Afya is free software, released under the [GNU General Public License v3.0](LICENSE).

Made by Maximillian Urio, with love from Tanzania.

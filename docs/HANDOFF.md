# OKOK Scale Reader — engineering handoff

Everything a future engineer or AI agent needs to continue this project without re-deriving it:
what the device speaks, how the app is put together, which decisions are deliberate, what has been
proven on real hardware, and what is still open.

- App: `com.maximillianurio.okokscale`, version 2.0.0 (`versionCode 2`)
- Stack: Kotlin 1.9.24, AGP 8.4.2, compileSdk 34, minSdk 23, targetSdk 34, Views + Material 3
  (no Compose), hand-rolled SQLite (no Room), coroutines only for the one network call
- Written 2026-10-07

---

## 1. Why this app exists

Afya reads a Chipsea-based BLE body-composition scale that is normally driven by the vendor's
"OKOK" app (`com.chipsea.btcontrol.en`). It started as an APK security review of that app and turned
into a replacement client: an app that reads the same hardware with no account, no
cloud sync of body data, no ads, and no dependency on the vendor's UI.

The vendor APK was decompiled with jadx and its BLE path was read end to end. That reverse
engineering is the *only* reason this app works — the protocol is undocumented and the device never
opens a GATT connection. The decompiled tree lives outside this project (`../out/jadx`,
`../work`) and must not be shipped or copied in; this repository only contains the knowledge
extracted from it, in section 2.

---

## 2. The over-the-air protocol

### 2.1 Transport

The scale is a **broadcast-only BLE beacon**. There is no pairing, no GATT service, no characteristic
write. Every measurement is pushed inside the manufacturer-specific data record of an advertising
packet, several times a second while the load cell is active.

`ScaleScanner` therefore starts an **unfiltered** `SCAN_MODE_LOW_LATENCY` LE scan and simply parses
whatever arrives. A filtered scan cannot be used: the scale changes its advertised address.

The AD structure walk is in `ScaleProtocol.manufacturerPayload`. It returns the body of the **last**
`0xFF` (manufacturer data) record *including* the two-byte company id, exactly like the vendor SDK,
so the frame header is byte 0 of the result. Getting this offset wrong is the classic way to lose a
whole afternoon.

### 2.2 Frame headers

Dispatch mirrors `chipseaBroadcastFrame.<init>` in the vendor SDK:

| Header | Name | Layout | Notes |
| --- | --- | --- | --- |
| `0xCA` | okok / broadcast v1 | `[CA][ver][len][prop][sub][wHi][wLo][prod x4]…` | vendor takes `okokBroadcastParser` when `payload[1] == 0x20`, else `okokParser`. Layout as implemented here; never exercised on hardware. |
| `0xB0` | short frame | `[B0][seq][wHi][wLo][prodHi][prodLo][prop]…` | seen on older hardware, untested here |
| `0xC0` | broadcast V2 | `[C0][seq][wHi wLo][rHi rLo][prodHi prodLo][prop][encMac x2][key x4]` | **the frame the test scale sends**, product id 2577 |
| `0xFF` | cloud frame | different layout | not implemented; not needed |

For `0xC0`:

- `weight` = bytes 2–3, big-endian, scaled by the decimal field of `prop`
- `resistance` = bytes 4–5 with bit 7 masked off, divided by 10 → ohms
- `product id` = bytes 6–7
- `scaleProperty` = byte 8 — carries sign, unit, decimals and the lock flag
- encryption flag = **bit 7 of byte 4** (the high resistance byte)
- when encrypted, bytes 9–10 hold an obfuscated MAC and bytes 11–14 the four key bytes

### 2.3 The property byte — two different tables

`scaleProperty` is bit-packed, and the vendor has **two incompatible decoders** for it. Using the
wrong one is silent and produces a plausible-looking number.

**Weight table — correct for `0xC0`/`0xCA`/`0xB0` broadcast frames** (`BytesUtil.getUnit` +
`BytesUtil.getDigit`, both slicing the MSB-first bit string):

```
bit 7      sign (negative display)
bit 6      not read by the vendor decoders
bit 5      device type on V2 frames (BytesUtil.getDeviceTpe4BV2)
bits 4..3  unit:  00 kg | 01 jin | 10 lb | 11 st
bits 2..1  decimals: 01 -> 0 | 10 -> 2 | anything else -> 1
bit 0      cmdId; this app treats it as the lock/"settled" flag
```

In code, `unit = (prop >> 3) & 3`, `decimals = when ((prop >> 1) & 3) { 1 -> 0; 2 -> 2; else -> 1 }`,
`locked = (prop & 1) == 1`. This was cross-checked against the vendor's string slicing for all 256
possible property bytes: zero mismatches.

**Nutrition table — only for the food-scale frames** (`UnitUtil.getUnit`, 4 bits at 6..3, ten
values: g, ml water, ml milk, oz, fl'oz water, fl'oz milk, lb, lb:oz, kg, jin; factors 31.1035,
28.349524, ×1.0288 for milk, 453.59238, 1000, 500). This app used to apply this table to weight
frames. With `prop = 0x24` the nutrition table reads `0100` → fl'oz and multiplies 123.20 by
28.349524, reporting a 123.2 kg person as 3.49 kg. That is the bug behind "on scale it reads 123
but here it says 3.5". `ScaleUnit` in `ScaleProtocol.kt` is now the weight table only; do not
reintroduce the nutrition enum on this path.

Stone (`st`) has a vendor quirk that is preserved: the two weight bytes are *not* a single scaled
integer. High byte = whole stones, low byte / 10 = tenths of a pound, and kg = (stones × 14 +
tenths) × 0.4535924.

### 2.4 Obfuscation and the cloud key

Some `0xC0` frames XOR the weight and resistance pairs with a per-device 4-byte key. The cipher is
the vendor's 15-bit rotate-and-XOR (`ScaleProtocol.decrypt15`, a faithful port of
`okokBroadcasetV2ParserDecrypt`): bit 15 is the sign and passes through; then XOR key[0], rotate
right 1, XOR key[1], rotate right 3, XOR key[2], rotate right 2, XOR key[3], all inside 15 bits.

Two facts make this survivable:

1. **The MAC is self-deobfuscating.** Bytes 9–14 carry the encrypted top two MAC bytes plus the four
   key bytes that decrypt them, so `macFromPayload` recovers the real address with no network at
   all. The advertised address is randomised; the payload is not.
2. **The weight key is provisioned per MAC** by a plain GET with no authentication beyond a static
   header: `https://intl.chips-cloud.com/v3/device/scale/sn?mac=<12 hex chars>`, headers
   `cs-app-id: ebcad75de0d42a844d98a755644e30` and `appSource: intel`; the JSON `data` field is the
   key as hex. `ScaleKeyClient` does this and `MainActivity.maybeFetchKey` calls it once per MAC
   when a frame arrives that is encrypted and whose key is unknown. Keys are cached in
   `LocalStore` under `key_<MAC>` and travel in backups.

This endpoint is a security finding, not a feature request: an unauthenticated oracle that returns
per-device keys for a MAC address. Keep it, because without it encrypted frames are unreadable,
but it is the only network call in the app and it is opt-out-able by disabling nothing — it only
fires for encrypted frames.

### 2.5 Worked example

Reconstructed from the row the app recorded on the test scale (weight 123.20, impedance 600.0 Ω,
product id 2577, prop `0x24`) rather than a byte-for-byte packet dump:

```
C0 2E 30 20 17 70 0A 11 24 …
│  │  └──┬──┘ └──┬──┘ └──┬──┘ └─ prop = 0x24
│  │     │       │       │
│  │   weight  resist  product id
│  seq  12320  0x1770    2577
│        ÷100  &0x7FFF
header   ↓      ÷10 → 600.0 Ω
C0     123.20 kg

prop 0x24 = 0010 0100 → bits 4..3 = 00 kg, bits 2..1 = 10 two decimals, bit 0 = 0 not locked
```

---

## 3. Application architecture

### 3.1 Ownership model

`MainActivity` is the only component that owns state. It holds the single `ScaleScanner`, the
single `LocalStore`, the single `ReadingsDb`, and a `CopyOnWriteArraySet<Listener>`. Fragments never
touch the scanner or the database directly; they register as a `MainActivity.Listener` and are
pushed `onReading` / `onDevices` / `onNotice` / `onRawFrame` / `onHistoryChanged` /
`onProfileChanged`.

Threading rule: the BLE callback arrives on a binder thread, so every scanner callback marshals with
`runOnUiThread` before broadcasting. Anything below `broadcast { }` may assume the main thread.

`ScaleFragment` is the base class every tab extends: it adds/removes the listener on
view create/destroy, calls `refresh()` on resume, and gives `say()` for Snackbars.

### 3.2 Tab host

`BottomNavigationView` + tag-based `show`/`hide` transactions committed with
`commitNowAllowingStateLoss`, tags `tab_<itemId>`. Fragments survive tab switches, which matters
because the weigh-in tab animates a ring and re-fetching from scratch looked wrong.

### 3.3 The recording pipeline

```
advertising packet
  → ScaleScanner.handle            (binder thread)
  → ScaleProtocol.parse            → ScaleReading
  → MainActivity.handleReading     (main thread)
      ├─ auto-bind: first frame with a weight binds the MAC if none is bound
      ├─ broadcast onReading        → hero + tiles update live
      ├─ queueRecord                → settle timer
      └─ maybeFetchKey              → ScaleKeyClient (coroutine, IO)
  → 1200 ms without a new locked frame → commit()
      ├─ repeat guard (same MAC, <30 g apart, <90 s) → dropped
      ├─ BodyComposition.compute() when profile complete AND load plausible
      ├─ ReadingsDb.insert()
      ├─ SoundCue.stable()          (ToneGenerator + Vibrator)
      └─ broadcast onHistoryChanged → Trends + tiles refresh
```

Constants live in `MainActivity.companion`: `SETTLE_MS 1200`, `MIN_GRAMS 100`, `REPEAT_GRAMS 30`,
`REPEAT_WINDOW_MS 90_000`, `STALE_MS 30_000` (how long a device stays in the list before
`forgetStale` drops it).

### 3.4 File map (`app/src/main/java/com/maximillianurio/okokscale/`)

| File | Responsibility |
| --- | --- |
| `ScaleProtocol.kt` | Pure, stateless AD7 decoding: payload extraction, three frame builders, `ScaleUnit`, `decrypt15`, MAC recovery, hex helpers. The only file that knows byte offsets. |
| `ScaleScanner.kt` | LE scan lifecycle, permission/adapter checks, per-MAC `ScanSummary` cache, emit throttling (700 ms or on display change), stale pruning. |
| `ScaleKeyClient.kt` | The one HTTP call; tiny hand-rolled JSON field regexes, gzip-aware. |
| `MainActivity.kt` | Host, listener bus, auto-bind, settle/commit, key fetch, tab switching. |
| `ScaleFragment.kt` | Base fragment: listener registration, `say()`, `refresh()`. |
| `WeighInFragment.kt` | Hero readout + up to 12 metric tiles. |
| `HistoryFragment.kt` | 30/90/all ranges, summary stats, `TrendChartView`, row list. |
| `ProfileFragment.kt` | Sex toggle, age/height/goal inputs, unit picker, healthy-range hint. |
| `SettingsFragment.kt` | Bind/unbind, device list, four switches, SAF backup/restore/CSV, delete-all, diagnostics panel with raw frame feed and manual key entry. |
| `BodyComposition.kt` | Published-equation estimates + reference bands. |
| `ReadingsDb.kt` | SQLiteOpenHelper, one `readings` table, defensive `onUpgrade`. |
| `LocalStore.kt` | SharedPreferences facade: bound MAC, keys, profile, settings, snapshot/restore maps. |
| `Backup.kt` | JSON export/import (schema 2) covering settings + profile + keys + all rows. |
| `Units.kt` | `WeightUnit` (app preference: kg, lb, st:lb, jin) and display formatting; `isChosenUnit` bridges device unit → preference. |
| `Adapters.kt` | `HistoryEntryAdapter`, `MetricAdapter`, `DeviceAdapter`, `DateFmt`. |
| `SoundCue.kt` | Synthesised ACK tone + vibration. Deliberately not an audio asset. |
| `WeighRingView.kt` | SweepGradient progress ring, `animateTo`. |
| `TrendChartView.kt` | Line chart with goal line and range shading. |

Layouts: `activity_main`, `fragment_weigh_in`, `fragment_history`, `fragment_profile`,
`fragment_settings`, `item_metric`, `item_history`, `item_device`. Drawables are hand-authored
vector/shape XML only.

---

## 4. Body composition layer

The vendor computes composition in native code (`libBodyFatScaleAlg.so`) from impedance. That
algorithm is proprietary and was **not** extracted or reimplemented. Instead the app derives
estimates from weight, height, age and sex using published equations, and says so in the UI
(`estimatesNote` on the weigh-in tab):

- BMI = kg / m²
- Body fat % = Deurenberg 1991: `1.20·BMI + 0.23·age − 10.8·(male?1:0) − 5.4`, clamped 3–60
- Fat mass / lean mass from that percentage
- TBW = Hall: `FFM × 0.732` → water %
- Skeletal muscle ≈ `FFM × 0.53`; bone mineral ≈ `FFM × 0.052`
- Visceral area = Yuki 2004 linear BMI/age model, floored at 10 cm²
- BMR = Mifflin-St Jeor 1990
- Metabolic age = `age + (BMI − 22) × 0.75`

`band()` / `position()` give the UI a healthy range and a 0..1 bar position per metric per sex.
`bodyPlausible(grams)` restricts all of it to 20–250 kg: below that the load is a bowl of
ingredients, not a person, and the app shows only weight and impedance rather than inventing a
body-fat percentage for a 200 g object.

Verified numerically against hand-computed values for 73.8 kg / 178 cm / 34 / male: BMI 23.3,
fat 19.6 %, fat mass 14.4 kg, lean 59.4 kg, water 58.9 %, muscle 31.5 kg.

The impedance the scale broadcasts is carried through and displayed as its own tile — it is real
measured data, unlike the derived percentages.

---

## 5. Persistence, backup, privacy

- `readings` table: `id, ts, mac, grams, impedance, bmi, fat_pct, muscle_kg, water_pct, bone_kg,
  visceral_cm2, bmr_kcal, metabolic_age, frame_name, product_id`, index on `ts DESC`.
- Weight is stored in **grams** as the single source of truth; every display unit converts from it.
- `onUpgrade` (v1 → v2) cannot crash launch or silently drop rows: it reads the old table through
  column-name lookups inside `runCatching`, drops, recreates, and re-inserts whatever it salvaged.
- Backup/restore is Storage Access Framework (`CreateDocument`/`OpenDocument`), so no broad file
  permission is requested. JSON holds settings, profile, cached per-MAC keys and all rows; import
  skips rows whose timestamp already exists and reports inserted/skipped. CSV export is history
  only.
- `android:allowBackup="false"`; no analytics, no account, no sync. The only egress is the key
  lookup in 2.4.

---

## 6. Deliberate constraints (do not "fix" these)

1. **No vendor assets.** The request to reuse the OKOK app's sounds, icons and artwork was refused;
   the lock cue is a synthesised `ToneGenerator` ACK plus a 45 ms vibration, and every drawable is
   authored here. If the user supplies rights-cleared files they belong in `app/src/main/res/raw/`.
2. **No vendor BIA algorithm**, per section 4.
3. **No patched/repackaged vendor APK** was ever produced; the security work is evidence and
   write-up only.
4. **The device's native readout stays primary.** The hero shows `weight.display` and the scale's
   own unit; a conversion to the profile unit appears as a secondary `lb = 123.2 kg` line only when
   the two differ. This is a user preference learned the hard way.
5. Views, not Compose; Material 3 dark theme defined in `res/values/themes.xml`
   (`Theme.OkokScale`) with custom snackbar/dialog/nav-indicator styles. Hand-rolled SQLite, not
   Room. Fewer moving parts for a single-activity app.

---

## 7. Build and toolchain notes

- `gradle.properties` pins `org.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64`. Change it on
  another machine or the build fails immediately.
- No sudo on this host: the SDK is at `~/Android/sdk` and Gradle 8.7 at `~/Android/tools/gradle-8.7`.
  `local.properties` (`sdk.dir=/home/maximillian/Android/sdk`) is intentionally excluded from the
  archive; Android Studio regenerates it.
- No git repository and no test source set. `./gradlew` is not present; use the installed Gradle or
  let Android Studio create a wrapper.
- Debug output: `app/build/outputs/apk/debug/app-debug.apk`.

### Field-test harness

The scale only transmits when someone stands on it, so automated UI testing has to cooperate with
the hardware. Two techniques earned their keep:

- **Seed the database out of band** to check rendering without a live read:
  `adb exec-out run-as <pkg> cat databases/readings.db` → edit with host `sqlite3` → push to
  `/data/local/tmp` → `run-as <pkg> sh -c 'cat /data/local/tmp/readings.db >
  databases/readings.db; rm -f databases/readings.db-journal'`. Remember the journal file or the
  app reads stale state.
- **`adb shell uiautomator dump`** for exact tap bounds — screenshots are 1260×2730 and previews are
  scaled 2×, so eyeballed coordinates miss. It fails with "could not get idle state" while the
  scale is actively broadcasting, which is itself a useful signal.
- SAF file pickers cannot see pushed files (not MediaStore-indexed), so backup/restore was
  validated by driving the UI, not by feeding it a file.

---

## 8. What has been proven on hardware

On the test scale (product id 2577, `0xC0` frames, unencrypted):

- Passive scan discovers the scale and decodes weight, unit, decimals, lock flag and impedance.
- Auto-bind picks the scale without user action; the Trends tab records rows on lock.
- Hero readout, ring animation, chime + haptics, metric tiles, trend chart with goal line,
  profile save/restore through SharedPreferences, delete-all confirmation, settings switches, raw
  frame diagnostics panel, backup/restore/CSV flows — all exercised through the UI with screenshots.
- The 123.2 kg / 3.5 kg defect is understood, fixed at the decode layer, and cross-checked against
  the vendor's own bit slicing for all 256 property values.

## 9. Open items

**Needs the user's hardware or decision**

1. Re-run a live weigh-in after the unit-table fix and confirm the hero shows the LCD's number and
   the composition tiles appear. (The last verified capture predates the fix.)
2. The profile on the test device still holds placeholder values entered during testing (34 yrs,
   178 cm, goal 72 kg); every derived metric depends on them.
3. Impedance read a constant 600.0 Ω in both idle and loaded captures. Either the scale holds the
   last value or the resistance offset is subtly wrong. Needs a live A/B with the vendor app open.
4. `0xCA` and `0xB0` paths are ported from the vendor but never exercised — no hardware sends them.
5. Encrypted-frame path (key fetch + `decrypt15`) is implemented and reasoned from the vendor code
   but has not been observed live on this unit.

**Engineering backlog**

6. No unit tests. `ScaleProtocol.decode` and `decrypt15` are pure functions and the obvious place
   to start; the 256-value property-byte cross-check in section 2.3 should become a test.
7. `versionName` in `build.gradle.kts` and `app_version` in `strings.xml` drift independently —
   wire the About row to `BuildConfig.VERSION_NAME` (needs `buildConfig = true`).
8. Launcher icon is the same vector as the in-app mark (`@drawable/ic_scale`); no adaptive icon,
   no mipmap set.
9. Composition tiles are recomputed from weight/height/age/sex only, so they cannot show a trend of
   body fat independent of the equations; storing impedance opens the door to a real BIA model.
10. Target SDK 34 / AGP 8.4.2 — bump when the user's toolchain moves.

**Deferred security-research branches** (the original APK review, still unfinished)

11. Manifest and attack-surface audit write-up.
12. Architecture map of the vendor app.
13. Ghidra pass over `libprotocol.so` and `libBodyFatScaleAlg.so`.
14. Secrets/endpoints inventory — the `cs-app-id` key oracle in 2.4 is the headline finding so far.
15. Formal PDF deliverable of the findings, which the user asked for.

---

## 10. Glossary

- **frame** — one advertising payload, named for its header byte (`0xC0` = "V2 broadcast")
- **prop / scaleProperty** — byte 8 of a `0xC0` frame; sign, unit, decimals, lock
- **lock** — the scale's "this reading has settled" flag; drives auto-record
- **settle** — the 1200 ms grace window the app waits after a locked frame before storing a row
- **key** — 4-byte per-MAC obfuscation key from the vendor cloud, cached locally

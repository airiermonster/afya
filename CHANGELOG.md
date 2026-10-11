# Changelog

## 1.0.0: first public release

The first release of Afya, an on-device reader for OKOK / Chipsea Bluetooth body-composition scales.

### Weighing in
- Reads the scale's Bluetooth broadcast without pairing; the figure on the scale's display is the
  figure on screen, with the conversion to your unit underneath.
- Readings are filed automatically when the scale locks; the screen stays on while you stand on it,
  and a reading that locked just before the phone slept is still saved.
- "Is this you?" holds a weight far from your last two weeks until you say whose it is.
- Launcher shortcut, Quick Settings tile and an optional daily reminder that open on the weigh-in.

### Understanding it
- Trend weight, a ten-day smoothed weight, on Home, Measure, Records and the widget.
- Nineteen body-composition estimates from published equations, each with its range, a plain word
  for where it sits, and its history across every weigh-in.
- Composition is only compared between readings taken with the same profile.
- Goal projection from a 28-day least-squares fit; daily water and energy targets.

### Records and exports
- Weight chart with trend, month and time-of-day filters, and two-reading comparison.
- Tape measurements, drink log and food log.
- PDF health record, CSV export, and JSON backups with an optional passphrase (AES-256-GCM).
- Home-screen widget.

### Privacy
- No account, analytics or ads. The only possible network request is the one-time key lookup for
  encrypted scales, shown in the network log.
- Optional Health Connect sharing of measured weight only.

### Accessibility
- TalkBack labels, roles, headings and live status; 48 dp touch targets; text that scales with the
  system font size; light and dark themes.

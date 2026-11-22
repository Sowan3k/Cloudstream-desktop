# Changelog

## 1.0.4-beta.1 - 23 November 2026

Modified fork of sheikhshariarnehal/Cloudstream-desktop, maintained by Sowan3k.

- Burgundy Wine light/dark theme and reactive appearance changes.
- Correct maximize/restore, fullscreen placement, focus and player keyboard ownership.
- Playback session, source switching, subtitle, completion and resume fixes.
- Truncated-transfer cleanup, protected redirected HLS/DASH routing and per-session cancellation.
- Background bulk pixel conversion and independent audio/video/presentation health monitoring.
- Automatic recovery after a confirmed 45-second stall: one same-source reload, then remaining mirrors; seekable position is retained when restoration succeeds.
- Disable all 14 distinct analytics trackers used by the 15 bundled providers before registration.
- Optional JVM repository presets replace Android-focused defaults in the preset list. Existing saved repositories remain unchanged; no external plugins are silently installed.
- Pinned core/provider source revisions, checksum-pinned Gradle 9.9.2 wrapper, and matching Windows CI.
- Distinct Burgundy installer identity and included GPL license.

Validation uses synthetic loopback media and isolated profiles. Beta status does not establish every live provider, speaker/headphone device, GPU/driver combination or clean-machine MSI installation. Gradle 9.9.2 is beyond Kotlin 2.4.0's fully supported Gradle range, although the local desktop path is tested. See `docs/playback-fixes.md`, `docs/repositories.md` and `THIRD_PARTY.md`.

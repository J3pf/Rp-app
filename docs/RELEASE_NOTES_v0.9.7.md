# Release Notes — v0.9.7

## Summary

Rp-app **v0.9.7** fixes missing chat messages after sequence gaps or reconnects (across sync policy, reducer, and Room persistence) and restores `working`, `waiting`, and slow-breathing `done` agent card animations using a low-power shared multiplatform clock (~30 fps active / ~15 fps done, `preferredFrameRate`, and automatic lifecycle pause) alongside `Rp-relay` **v0.8.5**.

## Component minimums

| Component | Minimum | Where it is published |
| --- | --- | --- |
| Rp-relay | **0.8.0** (tag `v0.8.0`, recommended **0.8.5**) | GitHub release |
| Rp-plugin (Pi) | **0.4.0** (tag `v0.4.0`, recommended **0.4.7**) | GitHub release |

Neither package exists on npm: `@j3pf/rp-relay` and `@j3pf/pi-rp` both return 404. The minimums are **GitHub release versions**, never registry versions.

## What's new since v0.9.6

- **Lossless Chat History Resync**:
  - Sequence-gap resyncs now request a full snapshot + events replay (`sinceSeq = null`, `epoch = null`) and reset `lastSeq` before replaying gap events, preventing dropped mid-conversation messages.
  - Fixed Room `ChatDao.replaceHistory` so empty incremental resumes preserve the last persisted event (`seq == liveFromSeq`), and fixed `ChatRepository.persistHistory` `snapshotSeq` calculation so reopening a chat retains every persisted event after the snapshot.
- **Low-Power Agent Card Animations (Android & iOS)**:
  - Restored the orbiting cyan comet (`working`), amber warning pulse (`waiting`), and slow emerald breathing glow (`done`) driven by a single shared draw-only throttled clock (`~30 fps` active, `~15 fps` done-only) with `Modifier.preferredFrameRate` hints and automatic background pause on both Android and iOS.
  - Animations remain active under Battery Saver / Low Power Mode while still honoring Reduce Motion accessibility settings and severe thermal throttling.

## Install / upgrade

- Download `rp-v0.9.7-direct.apk` from this release and sideload it over the installed build; it upgrades in place (`versionCode` is derived from the commit count, so it always increases).
- Upgrading does not touch your pairing: the machine token, relay URL, and encrypted session keys stay in place.

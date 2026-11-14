# Release Notes — v0.9.3

## Summary

Rp-app **v0.9.3** promotes the full `v0.9.2` release together with verified iOS Simulator/KMP multiplatform fixes (`UrlSessionSocketDriver` dispatch priority and multiplatform `Ulid`) and deterministic `MeshRepositoryWireOrderTest` observer synchronization so every CI workflow (`CI` and `iOS`) is green on `main`.

## Component minimums

| Component | Minimum | Where it is published |
| --- | --- | --- |
| Rp-relay | **0.8.0** (tag `v0.8.0`, recommended **0.8.2**) | GitHub release |
| Rp-plugin (Pi) | **0.4.0** (tag `v0.4.0`, recommended **0.4.3**) | GitHub release |

Neither package exists on npm: `@j3pf/rp-relay` and `@j3pf/pi-rp` both return 404. The minimums are **GitHub release versions**, never registry versions.

## What's new since v0.9.2

- **iOS KMP & Simulator Build Fix**: Replaced `QOS_CLASS_UTILITY` with `DISPATCH_QUEUE_PRIORITY_DEFAULT` in `UrlSessionSocketDriver` and promoted `Ulid` to `:protocol` `commonMain` via `platformRandomBytes` (`SecureRandom` on JVM, `arc4random_buf` on iOS).
- **CI Test Determinism**: Synchronized initial `ConnState.Connected` observer processing in `MeshRepositoryWireOrderTest`.

## Install / upgrade

- Download `rp-v0.9.3-direct.apk` from this release and sideload it over the installed build; it upgrades in place (`versionCode` is derived from the commit count, so it always increases).
- Upgrading does not touch your pairing: the machine token, relay URL, and encrypted session keys stay in place.

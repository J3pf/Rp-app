# Release Notes — v0.9.1

## Summary

Rp-app **v0.9.1** adds the **Terminal (Azul y Oscuro)** theme, renames the green terminal theme to **Terminal (Verde y Oscuro)**, fixes optimistic message reconciliation when the PC terminal already has draft text, clears stuck `"Pensando..."` state on `Completado` (`done`) agents, refines the `"Archivados"` header placement, and fixes iOS WebSocket connection and status bar insets.

## Component minimums

| Component | Minimum | Where it is published |
| --- | --- | --- |
| Rp-relay | **0.8.0** (tag `v0.8.0`, recommended **0.8.1**) | GitHub release |
| Rp-plugin (Pi) | **0.4.0** (tag `v0.4.0`) | GitHub release |

Neither package exists on npm: `@j3pf/rp-relay` and `@j3pf/pi-rp` both return 404. The minimums are **GitHub release versions**, never registry versions.

## What's new

### Themes & Agents UI
- **Terminal (Azul y Oscuro)**: New blue-and-dark terminal palette in Settings.
- **Terminal (Verde y Oscuro)**: Renamed from `Moshi (Verde y Oscuro)`.
- **Cleaner Agents Header**: `"Archivados"` now sits inline inside `AppHeader`'s trailing slot.

### Chat & State Fixes
- **No false send errors after 30s**: Positively ACKed messages move to `ack:` state and no longer expire into `⚠️ Error al enviar · Reintentar` when waiting for transcript confirmation.
- **PC draft prefix reconciliation**: Sending from mobile while text is already typed on the PC terminal replaces the mobile bubble in place with the full submitted message from the transcript.
- **No stuck `"Pensando..."` on `Completado`**: Settling into `done` clears the working indicator cleanly.

### iOS
- Fixed `/ws/app` WebSocket URL resolution, respected top status bar insets, and removed the diagnostic red box.

## Install / upgrade

- Download `rp-v0.9.1-direct.apk` from this release and sideload it over the installed build; it upgrades in place (`versionCode` is derived from the commit count, so it always increases).
- Upgrading does not touch your pairing: the machine token, relay URL, and encrypted session keys stay in place.

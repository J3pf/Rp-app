# Release Notes — v0.9.2

## Summary

Rp-app **v0.9.2** delivers comprehensive 4R security, reliability, and Compose Multiplatform UI parity hardening across Android, iOS, and shared KMP core modules: biometric lock & `NotificationService` wiring on iOS, in-place optimistic bubble reconciliation across snapshots and retries, streaming & banner parity in `shared-ui`, and deterministic push/working notification lifecycle cleanup on Android.

## Component minimums

| Component | Minimum | Where it is published |
| --- | --- | --- |
| Rp-relay | **0.8.0** (tag `v0.8.0`, recommended **0.8.2**) | GitHub release |
| Rp-plugin (Pi) | **0.4.0** (tag `v0.4.0`, recommended **0.4.2**) | GitHub release |

Neither package exists on npm: `@j3pf/rp-relay` and `@j3pf/pi-rp` both return 404. The minimums are **GitHub release versions**, never registry versions.

## What's new

### Chat Reconciliation & Delivery Reliability
- **Zero dropped local bubbles on snapshots**: Pending/failed `UserMsg` entries (`local:`, `ack:`, `failed`) survive `applyEntries`/`applyHistory` reconciliation when `epoch` is unchanged, preserving attachments, delivery method, and command metadata.
- **Accurate `"Pensando…"` & delivery states**: HTTP fallback and `WsReply.Ok(ack = null)` promote local bubbles to `ack:` (stopping false 30s timeout failures), offline queues clear stuck working state, and `"Pensando…"` persists across `DONE` → `ack:` transitions until settled.
- **Race-free stream buffering**: `ChatMessageDispatcher` applies buffered `(AgentEvent, Long)` deltas cleanly inside `updateState` without overwriting concurrent delivery updates.

### Security & Biometrics (Android + iOS)
- **iOS `appLock` & APNs Extension**: Added `IosBiometricAuthorizer` (`LocalAuthentication`) + `LockScreen` gating, embedded `NotificationService.appex` in the Xcode project, and added `criticalAlways` / raw JSON filtering plus keychain sign-out cleanup.
- **Hardened URL/Secret/Push Boundaries**: Enforced biometric confirmation on `Delivery.TYPED` and locked quick-actions, sanitized public `http://` URLs and attachment filenames, added `publicVersion` redaction and corrupted-keyset recovery, and cleared drafts/cache on sign-out.

### Compose Multiplatform UI & Push Polish
- **`shared-ui` Chat & Mesh Parity**: `SharedChatScreen` now consumes `chatState.display`, shows connection/`terminalWait`/`readOnly` banners, wires `ArchivedAgentsRoute`, updates `MeshCanvas` gesture bounds dynamically, and stops idle animations on `DONE` cards.
- **Push Lifecycle Cleanup**: Reconciles stale working notifications across WS and Room and clears them cleanly on `PushKind.ERROR` or muted/disappeared agents.

## Install / upgrade

- Download `rp-v0.9.2-direct.apk` from this release and sideload it over the installed build; it upgrades in place (`versionCode` is derived from the commit count, so it always increases).
- Upgrading does not touch your pairing: the machine token, relay URL, and encrypted session keys stay in place.

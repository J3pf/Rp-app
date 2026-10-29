# Release Notes — v0.6.1

## Summary

Rp-app **v0.6.1** makes a failed agent close diagnosable, and makes forgetting an agent whose Herdr
pane is already gone actually work. It is a small release on purpose: the previous version could leave
an agent impossible to remove with nothing on screen explaining why.

## Component minimums

| Component | Minimum | Where it is published |
| --- | --- | --- |
| Rp-relay | **0.6.1** (tag `v0.6.1`) | GitHub release |
| Rp-plugin (Pi) | **0.3.0** (tag `v0.3.0`) | GitHub release |

**Why the relay minimum moved to 0.6.1:** "Olvidar" only works when the relay treats a missing Herdr
pane as the desired end state. Against relay 0.6.0 the relay answers a missing pane with `conflict`, so
the agent stays impossible to forget no matter what the app does.

Neither package exists on npm: `@j3pf/rp-relay` and `@j3pf/pi-rp` both return 404. The minimums are
**GitHub release versions**, never registry versions — pointing anyone at npm sends them to a 404.

Installing the plugin resolves to `git:github.com/J3pf/Rp-plugin@vX.Y.Z`.

## What you get

- **A failed close tells you why.** Instead of a mute `No se pudo cerrar el agente`, the banner now
  carries the reason the relay sent, for example:
  `No se pudo cerrar el agente: herdr no pudo cerrar el panel: pane_not_found`.
  The next failure is diagnosable from the phone instead of requiring three repositories.
- **Forgetting an agent whose pane is already gone succeeds.** An agent whose Herdr pane disappeared
  hours ago can be removed; it is no longer stuck forever behind an error banner.

## What did not change, on purpose

- A close that fails for any other reason still **fails** and still leaves the local row untouched. The
  archive fallback is not widened: `herdr_unavailable` keeps failing, because the pane may still be
  alive and archiving then creates exactly the ghost agent this lifecycle work exists to prevent.

## Known limits

- **iOS notifies nothing.** There is no `UNUserNotificationCenter` integration and no
  `remote-notification` background mode in `iosApp/`: notifications are Android-only.
- **`TEAM_ID` is empty** in `iosApp/Configuration/Config.xcconfig`. Simulator builds do not need it;
  device signing and TestFlight archiving do.
- **iOS support matrix: arm64 devices and Apple-silicon simulators.** The Kotlin Multiplatform modules
  declare `iosArm64` and `iosSimulatorArm64`; there is no `iosX64` slice, and Compose Multiplatform
  1.12.2 publishes no `iosX64` artifacts.
- 3 lint warnings are expected: the `AndroidGradlePluginVersion` notice about AGP 9.4.1 → 9.5.0.
  Nothing is silenced with `tools:ignore` and AGP is not bumped.

## Pending after publishing

- **TestFlight is build-ready but unproven for upload**: the workflow and its eight documented secrets
  exist, but the human has not supplied them and no upload has been attempted.

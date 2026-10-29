# Release Notes — v0.6.0

## Summary

Rp-app **v0.6.0** fixes the notification zombie when answering a prompt from a push, ships the new
caret-and-node identity, closes the agent lifecycle end to end (close in Herdr → gone in the app;
archived agents listed separately with explicit purge), gives remote review consent its own surface,
and renders running subagents as mesh nodes instead of pills.

## Component minimums

| Component | Minimum | Where it is published |
| --- | --- | --- |
| Rp-relay | **0.6.0** (tag `v0.6.0`) | GitHub release |
| Rp-plugin (Pi) | **0.3.0** (tag `v0.3.0`) | GitHub release |

Neither package exists on npm: `@j3pf/rp-relay` and `@j3pf/pi-rp` both return 404. The minimums are
**GitHub release versions**, never registry versions — pointing anyone at npm sends them to a 404.

Installing the plugin resolves to `git:github.com/J3pf/Rp-plugin@vX.Y.Z`.

## What's new

### Notifications

- Answering a prompt from a push removes the notification instead of leaving a copy that reappears
  when the app opens.

### Identity

- New launcher and status-bar icon: a caret with a node, drawn from the approved artboards.

### Agent lifecycle

- Closing an agent in Herdr closes it in the app too, because the app now consumes the relay's
  lifecycle frames instead of only snapshots.
- Archived agents are listed in their own screen and are only purged when the human asks for it; a
  failed fetch renders an explicit error with retry, never an empty list.

### Review consent

- Provider review prompts with `danger: high` get a dedicated surface: the terminal body is rendered
  verbatim in monospace (no reflow, no truncation), the three options keep their declared `Effect:`
  text in provider order, the session-wide option is labelled as such, and dismissing sends
  `{cancelled:true}` rather than a silent rejection.

### Agent mesh

- Running subagents are part of the diagram as nodes; finished ones collapse to pills. Only Pi and agy
  emit subagent events, so no node is invented for an agent that reports none.

## Known limits

- **iOS notifies nothing.** There is no `UNUserNotificationCenter` integration and no
  `remote-notification` background mode in `iosApp/`: notifications are Android-only in this version.
- **`TEAM_ID` is empty** in `iosApp/Configuration/Config.xcconfig`. Simulator builds do not need it;
  device signing and TestFlight archiving do.
- **iOS support matrix: arm64 devices and Apple-silicon simulators.** The Kotlin Multiplatform modules
  declare `iosArm64` and `iosSimulatorArm64`; there is no `iosX64` slice, so no Intel simulator build
  exists in this release. Compose Multiplatform 1.12.2 publishes no `iosX64` artifacts either. An Intel
  host can compile the arm64 slices, but an arm64 binary cannot run in an x86_64 simulator.
- 3 lint warnings are expected and documented: the `AndroidGradlePluginVersion` notice about AGP
  9.4.1 → 9.5.0. The five dead `UnusedResources` strings were deleted. Nothing is silenced with
  `tools:ignore` and AGP is not bumped.

## Pending after publishing

- **TestFlight is build-ready but unproven for upload**: the workflow and its eight documented secrets
  exist, but the human has not supplied them and no upload has been attempted.
- iOS ships without notifications (see Known limits).

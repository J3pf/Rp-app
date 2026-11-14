# Release Notes — v0.9.4

## Summary

Rp-app **v0.9.4** adds interactive Mesh detail sheets when tapping synthetic `"sub:..."` subagent child nodes across both shared KMP and Android UIs, enriches subagent telemetry cards (`model`, `toolCalls`, `turns`, `result`, `error`), and preserves per-subagent models cleanly alongside `Rp-plugin` **v0.4.4**.

## Component minimums

| Component | Minimum | Where it is published |
| --- | --- | --- |
| Rp-relay | **0.8.0** (tag `v0.8.0`, recommended **0.8.2**) | GitHub release |
| Rp-plugin (Pi) | **0.4.0** (tag `v0.4.0`, recommended **0.4.4**) | GitHub release |

Neither package exists on npm: `@j3pf/rp-relay` and `@j3pf/pi-rp` both return 404. The minimums are **GitHub release versions**, never registry versions.

## What's new since v0.9.3

- **Mesh Synthetic Subagent Node Details**: Shared `resolveMeshNodeDetailTarget` across `SharedAppController` and Android `MeshViewModel` resolves `"sub:..."` child nodes to a focused `MeshNodeDetailSheet` view without invalid `/comms` fetches and opens the parent agent chat directly.
- **Subagent Model & Telemetry Visibility**: Preserves each subagent's own `model` on synthetic layout nodes (`MeshLayoutEngine`) and surfaces `model`, `toolCalls`, `turns`, and compact/JSON `result` or `error` across subagent cards.

## Install / upgrade

- Download `rp-v0.9.4-direct.apk` from this release and sideload it over the installed build; it upgrades in place (`versionCode` is derived from the commit count, so it always increases).
- Upgrading does not touch your pairing: the machine token, relay URL, and encrypted session keys stay in place.

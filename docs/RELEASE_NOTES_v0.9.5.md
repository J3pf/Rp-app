# Release Notes — v0.9.5

## Summary

Rp-app **v0.9.5** refreshes the Home Control Deck, compact agent cards (`14.dp` corners, unified status/model row, cleaner top bar), and compact bottom Quota Dynamic Island, while improving Chat ergonomics with a **`Project · Topic`** top bar, composer interrupt control, overflow slash-command menu, and pinned bottom-anchor auto-scroll alongside `Rp-plugin` **v0.4.5**.

## Component minimums

| Component | Minimum | Where it is published |
| --- | --- | --- |
| Rp-relay | **0.8.0** (tag `v0.8.0`, recommended **0.8.2**) | GitHub release |
| Rp-plugin (Pi) | **0.4.0** (tag `v0.4.0`, recommended **0.4.5**) | GitHub release |

Neither package exists on npm: `@j3pf/rp-relay` and `@j3pf/pi-rp` both return 404. The minimums are **GitHub release versions**, never registry versions.

## What's new since v0.9.4

- **Home Control Deck & Compact Agent Cards**: Compacted vertical spacing, unified status/context/model/mode tags into one `FlowRow`, refined card corners to `14.dp` (`RpShapes.card`) while keeping `QuotaDynamicIsland` at `20.dp`, and removed `herdr` clutter from cards (still preserved in `InfoDialog`).
- **Chat Header & Composer Polish**: Highlighted **`Project · Topic`** in `ChatTopBar`, moved quick slash commands into the overflow menu (`Comandos rápidos (/)`), moved `Interrupt` to the composer primary action circle during active runs, and kept bottom-anchor chat scrolling pinned cleanly with localized tool labels.

## Install / upgrade

- Download `rp-v0.9.5-direct.apk` from this release and sideload it over the installed build; it upgrades in place (`versionCode` is derived from the commit count, so it always increases).
- Upgrading does not touch your pairing: the machine token, relay URL, and encrypted session keys stay in place.

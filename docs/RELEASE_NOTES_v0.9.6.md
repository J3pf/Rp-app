# Release Notes — v0.9.6

## Summary

Rp-app **v0.9.6** adds interactive image attachment preview with optional free-form cropping, 90° rotation, and inline thumbnails in sent chat bubbles, alongside major battery/performance optimizations and 4R Android + iOS hardening paired with `Rp-relay` **v0.8.3** and `Rp-plugin` **v0.4.7**.

## Component minimums

| Component | Minimum | Where it is published |
| --- | --- | --- |
| Rp-relay | **0.8.0** (tag `v0.8.0`, recommended **0.8.3**) | GitHub release |
| Rp-plugin (Pi) | **0.4.0** (tag `v0.4.0`, recommended **0.4.7**) | GitHub release |

Neither package exists on npm: `@j3pf/rp-relay` and `@j3pf/pi-rp` both return 404. The minimums are **GitHub release versions**, never registry versions.

## What's new since v0.9.5

- **Image Attachment Preview, Optional Crop & 90° Rotation**: Picking an image opens a full preview dialog where you can drag corner handles to crop freely, rotate 90° clockwise, or attach the full image immediately; staged thumbnails in the composer remain tappable to inspect, rotate, or re-crop before sending.
- **Sent Image Thumbnails in Chat**: Outgoing user messages now display inline image thumbnails backed by local cache with on-demand relay download fallback and tap-to-preview support.
- **Performance & Battery**: Removed background radio wakeups, aligned heartbeats, fixed network ping-pong, released idle 120Hz LTPO locks, and optimized cold-start crypto, Room v4 batching, `ImageDecoder` attachments, markdown rendering, and off-main mesh layout.
- **Android & iOS Hardening**: Added Apple `CryptoKit` AEAD + `NWPathMonitor` bridges, iOS App Lock & NSE Keychain isolation, off-Main HTTP reads, atomic blob/temp cleanup, and hardened attachment job ownership.

## Install / upgrade

- Download `rp-v0.9.6-direct.apk` from this release and sideload it over the installed build; it upgrades in place (`versionCode` is derived from the commit count, so it always increases).
- Upgrading does not touch your pairing: the machine token, relay URL, and encrypted session keys stay in place.

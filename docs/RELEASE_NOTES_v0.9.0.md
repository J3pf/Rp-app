# Release Notes — v0.9.0

## Summary

Rp-app **v0.9.0** turns agent status into a visual language you can read at a glance (electric-cyan working border, orbiting comet, emerald done glow, amber blocked pulse), adds session context and model telemetry to the chat header, keeps the Herdr agent order stable instead of re-sorting itself, and stops raw JSON from leaking into notification previews.

## Component minimums

| Component | Minimum | Where it is published |
| --- | --- | --- |
| Rp-relay | **0.8.0** (tag `v0.8.0`) | GitHub release |
| Rp-plugin (Pi) | **0.4.0** (tag `v0.4.0`) | GitHub release |

Neither package exists on npm: `@j3pf/rp-relay` and `@j3pf/pi-rp` both return 404. The minimums are **GitHub release versions**, never registry versions — pointing anyone at npm sends them to a 404.

Installing the plugin resolves to `git:github.com/J3pf/Rp-plugin@vX.Y.Z`.

Both minimums are unchanged from v0.8.0: this release is client-only and the new telemetry is additive.

## What's new

### Agent state visual language

- **One treatment per state**:
  - `working` renders an electric-cyan animated border plus an orbiting comet ring, so it reads as activity rather than a generic highlight.
  - `done` settles into an emerald breathing glow.
  - Blocked and prompted agents render an amber warning pulse — the state you must not miss.
  - `idle` and `waiting` stay static, so the moving cards are always the ones that carry news.
- **Animations survive battery saver**: the working border used to freeze into a static outline when the system entered battery saver; `Motion.android.kt` keeps the state animations alive there.
- **Auto-scroll to the new agent**: invoking an agent scrolls it into view in the agents list instead of leaving it off-screen.

### Session context & model telemetry

- **Context usage in the chat header**: a compact chip with the context percentage, a `LinearProgressIndicator` bar that turns warning at 60% and error at 85%, and token counts formatted as `1.2M`, `45k`, or `999`.
- **Where the numbers come from**: `AgentSummary` and Herdr tab metadata now carry `contextTokens`, `contextWindow`, and `contextPercent`, parsed from the Herdr tab id format `#NNNN[~done][@tokens/window]|tabId` via `parseHerdrTabMeta`. Percentages are rounded and clamped to `0..100`.
- **No redundant rows**: the model label and thinking level stay in the strip only when no context telemetry is available.
- **Top bar breakdown**: a session progress indicator with a tappable info breakdown.

### Stable Herdr ordering

- **Herdr order is preserved exactly**: when agents come from Herdr, the list keeps Herdr's visual order (`#NNNN|tabId`) instead of re-sorting on `lastSeenAt` or state changes, so cards stop jumping while you are reading them.
- **Prompts stay reachable**: agents with pending prompts (`openPromptCount > 0` or `waiting_input`) are pinned above the preserved order, so an urgent question never scrolls out of reach.
- **Non-Herdr setups unchanged**: Pi standalone and tmux keep attention-first ordering — prompt > working > idle — tie-broken by the most recent `lastSeenAt`.

### Fixed — notification previews

- **No raw payloads in the shade**: `NotificationFactory` drops `activeTask` previews that are raw JSON or fenced code blocks (`isRawJsonOrCode`) and filters those lines out of multi-line previews, falling back to the topic or project name instead of printing `{"..."}`.
- **Clean tab ids**: Herdr tab ids lose their metadata prefix before reaching the UI (`cleanHerdrTabId`).

## Install / upgrade

- Download `rp-v0.9.0-direct.apk` from this release and sideload it over the installed build; it upgrades in place (`versionCode` is derived from the commit count, so it always increases).
- The signed APK comes from the `direct` flavor, which works without Google Play Services and keeps working with the persistent foreground service.
- Upgrading does not touch your pairing: the machine token, relay URL, and encrypted session keys stay in place.

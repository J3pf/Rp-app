# Release Notes — v0.8.0

## Summary

Rp-app **v0.8.0** introduces competitor-grade Android 16 / One UI 8 Promoted Live Activity (`ProgressStyle`) notifications with multi-agent aggregation, comprehensive battery and thermal optimizations (Smart Pocket Standby, foreground socket suspension, 30 FPS background throttling, and no-op SQLite elision), slash command palettes with descriptions, in-chat search & tool filters, active agent quick-switchers, and live task & Git dirty telemetry.

## Component minimums

| Component | Minimum | Where it is published |
| --- | --- | --- |
| Rp-relay | **0.8.0** (tag `v0.8.0`) | GitHub release |
| Rp-plugin (Pi) | **0.4.0** (tag `v0.4.0`) | GitHub release |

Neither package exists on npm: `@j3pf/rp-relay` and `@j3pf/pi-rp` both return 404. The minimums are **GitHub release versions**, never registry versions — pointing anyone at npm sends them to a 404.

Installing the plugin resolves to `git:github.com/J3pf/Rp-plugin@vX.Y.Z`.

## What's new

### Android 16 / One UI 8 Promoted Live Activity (`ProgressStyle`) Notifications

- **Full 3-Line Rich Layout**:
  - Leverages Android 16's `android.app.Notification$ProgressStyle`, `POST_PROMOTED_NOTIFICATIONS`, and `requestPromotedOngoing = true` (`FLAG_PROMOTED_ONGOING = 0x00040000`).
  - Displays all 3 lines above the progress bar without collapsing:
    - **Line 1 (Title)**: Live assistant output or tool execution preview (`activeTask`, cleaned of markdown headers/bullets).
    - **Line 2 (Subtitle)**: `Project · ⎇ branch · MachineName`.
    - **Line 3 (Body)**: User prompt (`Tú: <topic>`) and completed task count (`Tareas: X/Y`).
- **Unified Multi-Agent Working Notification**:
  - When multiple agents work simultaneously, RemotePi consolidates them into a single ongoing Live Activity card (`⚡ N agentes activos · <Project>: <task>`) with multi-agent bullet summaries, preventing One UI from collapsing notifications into an unreadable `Aggregate_NormalNotificationSection` stack with an arbitrary count badge.
- **Mutual Exclusion & Zero Notification Leakage**:
  - `showWorking` immediately cancels any lingering `rp.settled` notification for that agent.
  - `showSettled` cancels `rp.working` and groups settled items under `rp.group.settled` with an auto-summary when multiple agents finish.
  - The foreground direct-push service slot (`id=4200`) is reused as the Live Activity host when agents are active, preventing duplicate foreground notification clutter in the shade.

### Battery & Thermal Optimization (Waves 1 & 2)

- **Smart Pocket Standby & Auto-Sleep**:
  - Broadcast receiver for `ACTION_SCREEN_OFF`, `ACTION_SCREEN_ON`, and power-saver changes. Automatically sleeps the direct WebSocket connection during long screen-off periods without active agent tasks, saving significant battery.
- **Single-Socket Foreground Multiplexing**:
  - Suspends the background `DirectPushService` WebSocket when the app is actively in the foreground, letting the main interactive socket handle live updates without duplicate network connections.
- **No-Op Agent Upsert Elision**:
  - Compares incoming `AgentSummary` deltas against existing SQLite rows in `AgentsRepository`, skipping unnecessary Room writes and UI invalidations.
- **LTPO 1Hz Refresh & GPU Capping**:
  - Replaced 120Hz continuous card rotation with 1Hz status-pulsing, throttled background `MeshCanvas` rendering to 30 FPS, and added `graphicsLayer` hardware layer isolation to reduce GPU power draw.

### Enhanced Chat UX, Slash Commands & Telemetry

- **Slash Command Palette with Descriptions**:
  - Integrated full launcher-aware catalog with descriptions (`/usage`, `/compact`, `/model`, `/thinking`, `/goal`, `/plan`, `/term`, etc.) and automated space appending.
- **In-Chat Search & Tool Filters**:
  - Filter chat history by tool executions or free-text search queries.
- **Active Agent Quick-Switcher**:
  - One-tap navigation between currently active coding agents directly from the chat screen header.
- **Live Git Dirty (`*`) & Active Task Telemetry**:
  - Live indication of modified working trees and real-time tool summaries in the chat header and agent list.

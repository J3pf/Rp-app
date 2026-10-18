# Changelog

All notable changes to **Rp-app** are documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) and adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0] - 2026-10-18

### Added
- **Modern Agent Cards (Spec S2)**: Replaced bare circle nodes with rich agent cards featuring:
  - Project initial avatar tinted with machine color.
  - Human display names and secondary context line (`machine · branch/model`).
  - Role pill badges (`Orquestador`, `Subordinado`, `Subagente`, `Individual`).
  - Status dot with live state label (`Trabajando`, `Inactivo`, `Desconectado`, etc.).
  - Single-line current activity preview (`node.activity` or `node.todos.current`).
  - TODO progress indicator showing completed and total tasks.
  - Attached subagent chips with real-time running spinners and status tags.
- **Hierarchical Top-Down Layout (Spec S2, S3)**:
  - Redesigned `MeshLayoutEngine` from radial rings to top-down hierarchical flow: Orchestrators at top, subordinates below.
  - Strict bounding-box collision avoidance ensuring cards never overlap for 1..12 agents.
  - Viewport-aware layout adapting dynamically to measured viewport width and height across 360dp, 411dp, landscape, and tablet screens.
  - Viewport containment and clipping strictly below filters and above the live strip.
- **Real-State Motion System (Spec S2)**:
  - Shimmering border glow and typing activity dots on working agents.
  - Animated spinners on running subagents.
  - Comm particles and dashed Bezier curves for in-flight communications.
  - Quiet idle state with no redundant frame redraw loops when no agents are active.
  - Full respect for `LocalReducedMotion` and system `ANIMATOR_DURATION_SCALE == 0`.
- **Display Name Formatting & ID Sanitization (Spec S1)**:
  - Centralized pure display-name engine resolving `node.name` → `basename(cwd)` → `"Agente"` fallback.
  - Treats raw UUIDs, truncated UUIDs with ellipsis, and `subagent-chat-<hex>` identifiers as missing in UI views.
  - Disambiguates duplicate agent names with machine names or sequential numbering.
  - Sanitizes group chips, machine chips, live strip, conversation headers, and accessibility descriptions.
  - Added collapsed "Detalles técnicos" section in node detail bottom sheet with one-tap copy action for raw agent, machine, group, and parent IDs.
- **Comprehensive Visual & Functional Tests (Spec S5)**:
  - Roborazzi screenshots covering 1 agent, 4 agents (Omarchy + Croma scenario), and 12 agents across themes (Cute, Sexy, Omarchy × Light/Dark) on 360dp, 411dp, and tablet viewports.
  - Pure layout engine unit tests asserting zero bounding-box overlaps and viewport containment.

### Removed
- Dead legacy circle drawing code in `MeshCanvas.kt` (`drawNode`, `drawTodos`, `wrapText`, `ellipsize`).

---

## [0.3.4] - 2026-10-16
### Added
- Contextual suggestions and autocomplete for slash commands (`/model`, `/thinking`).
- Interactive select bottom sheets for parameterless model and thinking commands.

## [0.3.3] - 2026-10-16
### Added
- Room v2→v3 database migration preserving theme and agent task states.
- Direct peer conversation history opening from agent detail sheets.
- Intelligent group filtering based on selected machine.

## [0.3.2] - 2026-10-16
### Fixed
- `LenientLongSerializer` supporting decimal numbers and timestamps from relay.
- Tolerant list deserialization for mesh nodes, edges, groups, and subagents.

## [0.3.1] - 2026-10-16
### Fixed
- Object wrapper unwrapping in `RelayApi` for devices, machines, and open prompts.
- Explicit null serialization for `MeshSubscribe` on "Todas" machine filter.
- Timeout (10s) and HTTP fallback for websocket mesh subscription.

## [0.3.0] - 2026-10-16
### Added
- Complete UI refresh with centralized design tokens (`RpSpacing`, `RpShapes`, `RpGlows`, `RpGradients`).
- Bottom-anchored chat reverse layout with floating jump-to-latest button.
- Initial Roborazzi screenshot test suite.

## [0.2.0] - 2026-10-16
### Added
- Initial Agent Mesh screen with topology diagram, live strip, and bottom sheets.

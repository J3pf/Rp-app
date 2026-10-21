# Changelog

All notable changes to **Rp-app** are documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) and adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.5.0] - 2026-10-21

### Added
- **Corporate Org-Chart Layout ("Organigrama Mixto")**:
  - Implemented pure Reingold–Tilford / Walker tidy-tree algorithm in `MeshLayoutEngine`: parent centered horizontally above children, same depth = same horizontal row, mathematical collision avoidance.
  - Multi-root support displaying unparented peer orchestrators and solo agents side by side on row 0 without floating orphan nodes.
  - Compact vertical bullet lists for leaf children hanging under their manager node when exceeding the leaf threshold or available row width.
  - Orthogonal connectors with horizontal bus routing in inter-row channels and single vertical spine for leaf lists.
  - Sane pan/zoom with auto-fit scale down to 0.4x and a visible floating "Recentrar" action button with refresh icon in the viewport.
  - TalkBack accessibility hierarchy semantics announcing reporting structure (e.g. `"X, reporta a Y"`).
  - Roborazzi visual golden screenshots for multi-root and 4-level deep corporate tree scenarios, and contact sheet generation.
- **Dynamic Slash Command Catalogs** (contributed by Luis Eduardo Chacón Wilches):
  - Added launcher-aware slash command catalog for Pi, Antigravity, Claude, Gentle Shell, and generic Shell with argument sub-suggestions.
- **Floating Connection Banner** (contributed by Luis Eduardo Chacón Wilches):
  - Elevated floating pill banner for relay connection states with pulsing status dot.
- **Navigation Transitions & Predictive Back** (contributed by Luis Eduardo Chacón Wilches):
  - Smooth tab-switching and detail slide transitions; enabled Android 13+ predictive back callback.
- **Chat Density & Spacing Controls** (contributed by Luis Eduardo Chacón Wilches):
  - Chat message spacing preferences (`Compact`, `Normal`, `Relaxed`), chat font scale, full-width thinking blocks, and docked quota bar.



### Fixed
- **Test Determinism (no user-facing changes)**:
  - Made `PromptsRepositoryTest`, `RelayConnectionTest`, and `MeshRepositoryTest` coroutine scopes deterministic with `UnconfinedTestDispatcher` and `runTest`.
  - Eliminated wall-clock polling timeouts (`withTimeout(5000)`) under CPU contention, awaiting exact flow state conditions.

## [0.4.0] - 2026-10-18

### Added
- **Modern Agent Cards (Spec S2)**: Replaced bare circle nodes with rich agent cards featuring:
  - Avatar showing the first alphanumeric character of the display name in uppercase (or `A` if none), tinted with machine color.
  - Human display names and secondary context line (`machine · branch/model`).
  - A role badge on the orchestrator card only; subordinate and solo-agent cards have no role badge. `Subagente` is a role label, and subagents render as attached chips on their parent card.
  - Status dot with live state label (`Trabajando`, `Inactivo`, `Desconectado`, etc.).
  - Single-line current activity preview (`node.activity` or `node.todos.current`).
  - TODO progress indicator showing completed and total tasks.
  - Attached subagent chips with real-time running spinners and status tags.
- **Hierarchical Top-Down Layout (Spec S2, S3)**:
  - Redesigned `MeshLayoutEngine` from radial rings to top-down hierarchical flow: Orchestrators at top, subordinates below.
  - Strict bounding-box collision avoidance ensuring cards never overlap for 1..12 agents.
  - Viewport-aware layout adapting dynamically to measured viewport width and height across 360dp, 411dp, landscape, and tablet screens.
  - Mesh viewport recomputation is skipped when the size is unchanged (contributed by Luis Eduardo Chacón Wilches); changed measured card heights still trigger recomputation.
  - Cards have a minimum readable width of 180dp; heights are measured at runtime with an estimate fallback.
  - Auto-fit uses scale 1.0 in landscape viewports at least 600dp wide, otherwise fits when possible with a 0.65 scale floor. If the graph still exceeds the viewport, it is top-anchored with 16dp padding and can be reached by pan/zoom.
  - In landscape layouts below 500dp height, `Máquinas` and `Grupos` filters share one horizontally scrolling row.
  - Viewport clipping keeps the canvas below filters and above the live strip.
- **Real-State Motion System (Spec S2)**:
  - Shimmering border glow and typing activity dots on working agents.
  - Animated spinners on running subagents.
  - Orthogonal bus connectors routed through row gaps; particles travel along the connector for in-flight communications, with dashed styling for pending asks. A quadratic curve is used only as a fallback for non-hierarchical edges.
  - Quiet idle state with no redundant frame redraw loops when no agents are active.
  - Full respect for `LocalReducedMotion` and system `ANIMATOR_DURATION_SCALE == 0`.
- **Display Name Formatting & ID Sanitization (Spec S1)**:
  - Centralized pure display-name engine resolving `node.name` → `basename(cwd)` → `"Agente"` fallback.
  - Treats raw UUIDs, truncated UUIDs with ellipsis, and `subagent-chat-<hex>` identifiers as missing in UI views.
  - Disambiguates duplicate agent names with machine names or sequential numbering.
  - Sanitizes group chips, machine chips, live strip, conversation headers, and accessibility descriptions.
  - Mesh group filter chips prefer human relay group labels with orchestrator fallback and precomputed label lookups (contributed by Luis Eduardo Chacón Wilches).
  - Added collapsed "Detalles técnicos" section in node detail bottom sheet with one-tap copy action for raw agent, machine, group, and parent IDs.
- **Comprehensive Visual & Functional Tests (Spec S5)**:
  - Roborazzi screenshots covering 1 agent, 4 agents (Omarchy + Croma scenario), and 12 agents across themes (Cute, Sexy, Omarchy × Light/Dark) on 360dp, 411dp, and tablet viewports.
  - Pure layout engine unit tests asserting zero bounding-box overlaps and viewport containment.
- **Chat Active Thinking Level & Slash Commands**: Show active thinking level in chat header and agent info dialog (`model · thinkingLevel`), and support uppercase slash command prefixes (e.g. `/Thinking`).
- **Localized Mesh Timestamps**: Conversation date/time formatting follows the device locale and time zone (for example, Spanish formatting on Spanish-language devices).
- **Chat Code Highlighting**: Syntax highlighting runs asynchronously off the main thread and selects its dark-mode palette from the app theme surface luminance.
- **Kotlin Formatting**: Added the ktlint Gradle plugin 14.2.0; `./gradlew ktlintCheck` verifies formatting.
- **Deprecated API Migrations**: Migrated deprecated APIs across app and test code:
  - `hiltViewModel()` to `androidx.hilt.lifecycle.viewmodel.compose`.
  - `LocalClipboardManager` to `LocalClipboard`.
  - Room `fallbackToDestructiveMigration(dropAllTables = true)`.
  - Compose tests from `createComposeRule()` to `junit4.v2`.
  - Tink keyset serialization/parsing from `CleartextKeysetHandle.read` to `TinkJsonProtoKeysetFormat.parseKeyset`.
- **Lint Configuration**: `OldTargetApi` is disabled because target SDK 36 is intentionally retained to avoid Android 17 behavior changes; `ObsoleteSdkInt` is disabled because `mipmap-anydpi-v26` is required by AGP for adaptive icon packaging.
- **Notification Permission Guards**: `NotificationFactory.canNotify()` checks the Android 13+ notification permission and whether notifications are enabled before posting.

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

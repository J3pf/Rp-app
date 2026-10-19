# Release Notes — v0.4.0: Agent Mesh Redesign & Real-Time Flow Experience

## Summary
Version **v0.4.0** delivers a major UX/UI overhaul of the **Agent Mesh ("Red de agentes")** screen in **Rp-app**, addressing user feedback to provide a clear, glanceable, and beautiful agent network topology.

### What's New

#### 1. Modern Agent Cards (Replacing Bare Circles)
- Bare empty circles are replaced with comprehensive, interactive **Agent Cards**:
  - **Project Initial Avatar**: Colored surface tinted with the machine's accent color, showing the first alphanumeric character of the display name in uppercase (or `A` if there is none).
  - **Clean Display Name**: Human-friendly project or directory name without raw UUIDs or hashes.
  - **Secondary Context**: Machine name paired with the git branch or active model (e.g. `Omarchy · main` or `Croma · sonnet-3.5`).
  - **Role Badge**: Only the orchestrator card displays a role badge. Subordinate and solo-agent cards have none; `Subagente` is a role label, and subagents render as attached chips on their parent card.
  - **Status & Real Activity**: Live status badge with typing indicator dots when working, plus single-line activity preview (`node.activity` or current TODO).
  - **TODO Progress Indicator**: Compact linear progress bar tracking completed vs total tasks.
  - **Attached Subagents**: Subagents shown as attached chips directly on their parent card with active execution spinners.

#### 2. Top-Down Hierarchical Flow
- Redesigned `MeshLayoutEngine` from concentric rings to top-down hierarchical flow:
  - Orchestrators are placed at the top level, centered.
  - Subordinates are arranged in balanced horizontal rows below their orchestrator.
  - Mathematical bounding-box collision avoidance guarantees zero overlaps for 1..12 agents.
  - Viewport-aware layout adapts dynamically to measured viewport width and height across 360dp, 411dp, landscape, and tablet screens.

#### 3. Viewport Containment & Auto-Fit Navigation
- Canvas viewport is strictly contained and clipped below header controls and filter chips (`clipToBounds()`), eliminating any overlap with top buttons or bottom strips.
- Auto-fit shows the complete network when possible: the scale is at least 0.65, and is 1.0 in landscape viewports at least 600dp wide. When the whole graph cannot fit at that scale, it is top-anchored with 16dp of padding; pan and zoom reach the rest.
- Cards have a minimum readable width of 180dp. Their heights are measured at runtime, with an estimate used until measurement is available.
- Smooth pinch-to-zoom (0.5x–3.0x) and pan gestures allow navigation, with double-tap to reset.

#### 4. Real-State Motion System
- Animations reflect **real state only**:
  - Working agent: animated border shimmer + typing activity indicator dots (rendered on render thread with zero recompositions).
  - Running subagent: active circular progress spinner.
  - Comm in flight: orthogonal bus connectors routed through row gaps; particles travel along the connector from sender to receiver, accompanied by speech preview bubbles placed in the connector gap without covering cards. A quadratic curve is used only as a fallback for non-hierarchical edges.
  - Idle state: calm, static outline; canvas frame ticker stopped completely when no active comms or pending asks exist to preserve CPU and battery.
  - Offline state: muted, desaturated surface.
  - Full respect for system `ANIMATOR_DURATION_SCALE == 0` and `LocalReducedMotion`.

#### 5. ID Sanitization & Technical Details Drawer
- Zero raw UUIDs or hex hashes appear on the graph, machine chips, group chips, sheets, or list view.
- Centralized display name formatter with pure fallbacks (`node.name` → `basename(cwd)` → `"Agente"`), handling English sentinels (`"Agent"`, `"Mesh Group"`) and preserving `(n)` duplicate suffixes. Machine names sanitized with fallback `"Máquina"`.
- Collapsed **"Detalles técnicos"** section in the node detail sheet provides one-tap clipboard copy for agent IDs, machine IDs, group IDs, parent agent IDs, and full directory paths with 48dp touch targets.

#### 6. Comprehensive Test Coverage & Parity
- **Roborazzi Screenshot Tests**: 66 mesh screenshots (126 total in repository) covering 11 scenarios across Cute, Sexy, and Omarchy themes in light & dark modes: 411dp portrait default, 1 agent solo, 12 agents with in-flight comms, 360dp phone width, landscape (800dp width), tablet (800dp width), reduced motion, collapsed detail sheet, expanded detail sheet, and conversation sheet.
- **Unit Tests**: Formatter, layout collision, viewport containment, density sweep (1080x1800 at 2.625 density), and Compose UI tests all passing with 100% green verification. Zero deprecation warnings across app and tests.

---

## Additional v0.4.0 Improvements
- In landscape layouts below 500dp height, the `Máquinas` and `Grupos` filters share one horizontally scrolling row.
- Chat displays the active thinking level in the header and agent info dialog (alongside the active model), and accepts uppercase prefixes in slash command suggestions.
- Mesh conversation timestamps follow the device locale and time zone (for example, Spanish formatting on Spanish-language devices).
- Chat code highlighting runs asynchronously off the main thread. Dark-mode syntax colors are selected from the app theme surface luminance, not the system theme.
- Added the ktlint Gradle plugin 14.2.0; run `./gradlew ktlintCheck` to check formatting.
- Migrated deprecated APIs across app and test code:
  - `hiltViewModel()` to `androidx.hilt.lifecycle.viewmodel.compose`.
  - `LocalClipboardManager` to `LocalClipboard`.
  - Room `fallbackToDestructiveMigration(dropAllTables = true)`.
  - Compose tests from `createComposeRule()` to `junit4.v2`.
  - Tink keyset serialization/parsing from `CleartextKeysetHandle.read` to `TinkJsonProtoKeysetFormat.parseKeyset`.
- Lint disables `OldTargetApi` because target SDK 36 is intentionally retained to avoid Android 17 behavior changes, and `ObsoleteSdkInt` because `mipmap-anydpi-v26` is required by AGP for adaptive icon packaging.
- `NotificationFactory.canNotify()` guards notification posting by checking the Android 13+ notification permission and whether notifications are enabled.

## Compatibility
- Android 10+ (API 29+).
- Works with older relays (raw ids and the `Agent`/`Mesh Group` sentinels are sanitized client-side); best experience with Rp-relay v0.5.3.

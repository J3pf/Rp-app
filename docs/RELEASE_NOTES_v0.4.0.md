# Release Notes — v0.4.0: Agent Mesh Redesign & Real-Time Flow Experience

## Summary
Version **v0.4.0** delivers a major UX/UI overhaul of the **Agent Mesh ("Red de agentes")** screen in **Rp-app**, addressing user feedback to provide a clear, glanceable, and beautiful agent network topology.

### What's New

#### 1. Modern Agent Cards (Replacing Bare Circles)
- Bare empty circles are replaced with comprehensive, interactive **Agent Cards**:
  - **Project Initial Avatar**: Colored surface tinted with the machine's accent color.
  - **Clean Display Name**: Human-friendly project or directory name without raw UUIDs or hashes.
  - **Secondary Context**: Machine name paired with the git branch or active model (e.g. `Omarchy · main` or `Croma · sonnet-3.5`).
  - **Role Badge**: High-contrast pill labeling `Orquestador`, `Subordinado`, `Subagente`, or `Individual`.
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
- Default **fit-to-content** auto-scaling ensures the entire network fits on screen.
- Smooth pinch-to-zoom (0.5x – 3.0x) and pan gestures allow effortless navigation, with double-tap to reset.

#### 4. Real-State Motion System
- Animations reflect **real state only**:
  - Working agent: animated border shimmer + typing activity indicator dots (rendered on render thread with zero recompositions).
  - Running subagent: active circular progress spinner.
  - Comm in flight: particles riding along the exact drawn Bezier connector curves from border to border, accompanied by speech preview bubbles placed cleanly in the connector gap without covering cards.
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

## Compatibility
- Android 10+ (API 29+).
- Fully compatible with Rp-relay >= 0.5.0, with forward compatibility for Rp-relay 0.5.3 display name sentinels.

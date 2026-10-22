# Release Notes — v0.5.0: Corporate Org-Chart Agent Mesh & Chat Density Controls

## Summary
Version **v0.5.0** delivers a major visual refactor of the **Agent Mesh ("Red de agentes")** screen into a corporate organizational chart ("organigrama mixto"), alongside significant chat density, navigation transitions, and slash command enhancements contributed by **Luis Eduardo Chacón Wilches**.

---

### What's New

#### 1. Corporate Org-Chart Layout ("Organigrama Mixto")
- **Tidy-Tree Pure Algorithm (Reingold–Tilford / Walker style)**:
  - Replaces grid chunking with a pure, deterministic hierarchical tree algorithm.
  - Each parent card is horizontally centered directly above its children and subtrees (`parent.x = (first.x + last.x) / 2`).
  - Nodes at the exact same depth level share the same horizontal row (`same depth = same row`).
  - Dynamic card width calculation ensures cards fit comfortably within available viewport widths when possible while preserving a minimum 180dp readable width.
- **Multiple Roots (Side-by-Side Sibling Trees)**:
  - Unparented nodes (peer orchestrators or solo agents) are aligned side by side on the top tier (level 0) as sibling trees, eliminating orphan floating nodes at the bottom.
- **Compact Vertical Bullet Lists for Leaves**:
  - Subordinate leaf agents (agents with no further subagents or subordinates) exceeding the threshold or row width render as a compact vertical bullet list hanging under their manager.
  - Each leaf item features a status-colored bullet dot, human display name, one-tap sheet detail opening, and TalkBack accessibility semantics.
- **Orthogonal Bus Connectors**:
  - Connectors follow strict orthogonal routes: parent bottom-center → horizontal bus line in row gap → child top-center.
  - Leaf lists use a single vertical spine dropping from the manager bottom-center through the status dots.
  - Cross-hierarchy edges bypass intermediate cards through lateral channels.
- **Canvas Usability & Re-Center Action**:
  - 2D pan and pinch-to-zoom with extended range (0.4x to 2.5x) and auto-fit scaling.
  - Accessible **"Recentrar"** action in the top app bar for one-tap viewport reset without overlapping node cards, alongside double-tap gesture.

#### 2. Chat Density, Navigation & Slash Command Catalog (Contributed by Luis Eduardo Chacón Wilches)
- **Dynamic Slash Command Catalogs**:
  - Launcher-aware slash command suggestions tailored for **Pi**, **Antigravity**, **Claude**, **Gentle Shell**, and generic Shell.
  - Sub-suggestions support (e.g. `/thinking [off|minimal|low|medium|high|xhigh|max]`).
- **Floating Connection Banner**:
  - Elevated floating pill banner for relay connection states with pulsing indicator and smooth vertical transitions.
- **Navigation Transitions & Predictive Back**:
  - Physical slide transitions with distinct `tabEnter`/`tabExit` animations for bottom navigation tabs and `detailEnter`/`detailExit` for sub-screens.
  - Enabled Android 13+ predictive back callback in `AndroidManifest.xml`.
- **Chat Typography & Density Controls**:
  - Added chat message spacing controls (`Compact`, `Normal`, `Relaxed`) and user-configurable chat font scale (`0.85x` to `1.30x`).
  - Ultra-compact message bubble density with full-width thinking blocks and harmonized activity cards.
  - Docked quota and composer bars with continuous auto-scrolling during streaming responses.
  - Live session pulse animation in the Agents list.

#### 3. Accessibility & Quality
- **TalkBack Hierarchy Semantics**: Subordinate nodes announce reporting hierarchy (e.g. `"Subordinado Worker UI, Trabajando, reporta a Orquestador Maestro"`).
- **Quality Gates**:
  - 0 ktlint violations (`./gradlew ktlintCheck`).
  - 0 compiler warnings (`w:` = 0).
  - 0 Android lint warnings/errors (`./gradlew lintDirectDebug`).
  - 0 deprecated APIs.
  - 100% green unit test suite across protocol and app modules.
  - Roborazzi visual test suite updated with new multi-root and deep-tree scenarios across all 3 themes (Cute, Sexy, Omarchy × Light/Dark). Contact sheets generated on demand to `~/.cache/rp-verify/`.

---

## Compatibility
- Requires **Rp-relay >= 0.5.5** and **Rp-plugin >= 0.2.8**.
- Application signed with official release keystore (Certificate SHA-256: `6a7ef9a237db4483fbdacae7a323859df3e98198ae9bd6fb0a16229aecd62bc0`).

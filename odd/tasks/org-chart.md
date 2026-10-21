# Feature: org-chart (Corporate Org-Chart Layout for Agent Mesh)

Status: Ready for Review
Target: v0.5.0 release

## Specs
- S1: (user verbatim) "refactor visual a la pantalla de red de agentes para que sea estilo organigrama empresarial"
- S2: Tidy-tree layout (Reingold–Tilford / Walker style) in `MeshLayoutEngine` as a pure, unit-tested function: parent centered horizontally over its subtree/children, same depth = same row, no bounding-box overlaps, deterministic. Orthogonal connectors only: parent bottom-center → horizontal bus → child top-center.
- S3: Multiple roots (agents without parent) shown side by side as sibling trees on the top row with equal spacing; no orphan floating nodes.
- S4: Leaf children (no subagents) of one parent: when there are more than ~3 or the row would overflow, render as the compact vertical bullet list from the reference ("organigrama mixto" with vertical line, status-colored dot, display name, tap opens node detail sheet). Managers/orchestrators stay as cards.
- S5: Wide trees must remain usable on 360dp phone: 2D pan + pinch zoom with sane min/max (0.4x to 2.5x), initial view fits/centers the root, a visible "re-center/fit" action. No clipped cards, no text truncation that hides the name.
- S6: Keep everything that exists: machine/group filters, "Vista de lista", live strip "En vivo", node detail + conversation sheets, status colors/progress, reduced-motion, landscape, tablet, 3 themes × light/dark, es locale, no raw ids, theme tokens only (no hardcoded colors), TalkBack semantics for the hierarchy (e.g. "X, reporta a Y").
- S7: Tests first: layout engine unit tests (1, 4, 12 agents, multi-root, deep 3+ levels, many leaves) RED→GREEN; update/add Roborazzi screenshots (1/4/12 agents, multi-root, deep tree, 360dp, landscape, tablet, reduced motion, all themes). Send contact sheet path of the new screenshots when ready.
- S8: Quality gate: ktlint, lint 0 warnings, 0 compiler warnings, 0 deprecations, full unit tests; no new dependency unless justified, and any dependency at latest stable (no alpha/beta/rc). Check for outdated deps and report.
- S9: Review Luis's 17 incoming commits for regressions/antipatterns since they ship in this release; report findings (do not rewrite his work without telling me).
- S10: Release prep: bump to 0.5.0, English CHANGELOG + docs/RELEASE_NOTES_v0.5.0.md crediting Luis, signed with the release key (cert 6a7ef9…), APK built in the real repo.

## Tasks
- [x] T1 (S9): Review Luis's 17 incoming commits (63b6772..1a19981) for regressions/antipatterns, verify baseline build and tests [inline] [1a19981]
- [x] T2 (S2, S3, S7): Test-first `MeshLayoutEngine`: author failing unit tests for tidy-tree layout (1, 4, 12 agents, multi-root, deep 3+ levels, leaf grouping, no overlaps, orthogonal bus routes) [inline] [5abff24]
- [x] T3 (S2, S3, S4): Implement pure Reingold–Tilford tidy-tree algorithm with multi-root sibling trees, leaf clustering into vertical lists, and orthogonal bus routing in `MeshLayoutEngine` and `MeshLayoutModels` [inline] [5abff24]
- [x] T4 (S4, S6): Implement compact vertical leaf bullet list composable hanging under manager nodes with tap interaction, status dots, and TalkBack semantics [inline] [c0f1967]
- [x] T5 (S5, S6): Update `MeshCanvas`: render orthogonal bus connectors, integrate leaf list rendering, enhance 2D pan/zoom with sane bounds and add floating/accessible re-center button [inline] [e2eb286]
- [x] T6 (S6, S7): Compose UI tests for hierarchy semantics ("reporta a"), tap interactions on leaf items, and viewport bounds on 360dp/landscape/tablet [inline] [e2eb286]
- [x] T7 (S7): Update and record Roborazzi screenshot tests for 1, 4, 12 agents, multi-root, deep tree, 360dp, landscape, tablet, reduced motion across all themes; generate contact sheet [inline] [5e432e3]
- [x] T8 (S8): Run full quality gates: ktlint, 0 compiler warnings, 0 lint warnings, 0 deprecations, dependencies audit [inline] [5e432e3]
- [x] T9 (S10): Version bump 0.5.0, CHANGELOG, docs/RELEASE_NOTES_v0.5.0.md crediting Luis, and build signed release APK [inline]
- [ ] T10: Orchestrator handoff report with HEAD sha, commit list, test counts, lint/outdated report, screenshot contact sheet path and S# coverage [inline]

## Log
- L1: User verbatim: "refactor visual a la pantalla de red de agentes para que sea estilo organigrama empresarial"
- L2: Orchestrator instructions received: tidy-tree layout (Reingold-Tilford/Walker), orthogonal bus connectors, multi-root side-by-side, compact vertical bullet list for leaves (>3 or overflow), 360dp pan/zoom with re-center button, preserve existing features/a11y/themes, Roborazzi screenshots + contact sheet, review Luis's 17 commits, release v0.5.0 signed APK.
- L3: Audited Luis's 17 commits (63b6772..1a19981). Clean code and conventional commits. Found infinite recomposition loop in `MessageList` LaunchedEffect on `screenshotChatScrolledUp` where `prevItemsSize` mutation and auto-scroll continuously trigger.
- L4: Authored pure Reingold-Tilford tidy-tree engine in `MeshLayoutEngine` with deterministic post-order contour calculation, horizontal bus routing, multi-root side-by-side support, and compact vertical bullet list clustering for leaves.
- L5: Verified full unit test suites (25 layout engine tests, 30+ Compose UI tests) 100% green. Verified ktlintCheck (0 violations), compile Kotlin (0 warnings), Android Lint (0 warnings/errors), 0 deprecations.
- L6: Generated contact sheet at `docs/screenshots/mesh_org_chart_contact_sheet.png`. Built signed release APK `app-direct-release.apk` with certificate `6a7ef9a237db4483fbdacae7a323859df3e98198ae9bd6fb0a16229aecd62bc0`.

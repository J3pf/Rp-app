# Task: mesh-redesign ("Red de agentes" screen refactor)
Feature: mesh-redesign
Status: In Progress (Addressing Orchestrator Review Findings V1-V6)

## Specs
S1: (point 1, "el usuario jamas va a entender esos ids")
No raw id (UUID/ULID/hex, agentId, sessionId, machineId) is visible anywhere in the mesh UI: nodes, group chips, machine chips, detail sheet header, conversation sheet, live feed ("En vivo"), list view, and accessibility labels. One pure display-name formatter: human node.name → basename(cwd) → "Agente" fallback; id-looking names (UUID regex, "subagent-chat-<hex>", equal to agent/session id) are treated as missing. Secondary line: machine name · git branch (or model). Disambiguate duplicates. Group chip uses the orchestrator's display name. Raw ids may only appear inside the node detail sheet in a collapsed "Detalles técnicos" section with a copy action. Unit tests use the exact ids from the screenshot ("01a14fbb-2812-7332-aae5-d003f17b8fe7 · 4", "01a14fbb-44e3-765f-99e1-…", "subagent-chat-01a14fbb-2812-7332").

S2: (point 2, "algo más bonito, algo con movimiento … que se vea visualmente realmente qué está pasando en el flujo")
Replace bare circles with agent cards: avatar (project initial/icon tinted with the machine color), display name, role tag (Orquestador / Subordinado / Subagente), status, ONE line of current activity (node.activity / todos.current), todo progress bar, subagents as small attached chips/cards with their own running state. Hierarchical top-down flow: orchestrator at the top, subordinates below, each agent's subagents attached to it. Motion that reflects REAL state only: working agent → animated border/shimmer + typing/activity indicator; running subagent → spinner/progress; comm in flight → particles/dashes travelling along the connector from sender to receiver, with the message preview bubble; idle → calm/static; offline → muted. No animation when nothing is working (no continuous redraw at idle). Honor reduced motion / animator duration scale 0 with static equivalents. Keep tap → existing detail/conversation sheets and the "Vista de lista" toggle working.

S3: (point 3, "los círculos se pueden sobre poner a los botones de arriba")
The graph lives in its own viewport strictly BELOW the header and filter chips, clipped to bounds; layout computed from the measured viewport size. Cards never overlap each other, the filters, or the "En vivo" panel, for 1..12 agents with up to 4 subagents each, on 360dp, 411dp (the user's phone), landscape and tablet widths; if content exceeds the viewport, allow pan/zoom with a "fit to content" default. Layout-engine unit tests assert no bounding-box overlap and every box inside the viewport.

S4: (quality)
Zero regressions (all existing tests green), no deprecated APIs, theme tokens only (Cute/Sexy/Omarchy × light/dark), a11y (content descriptions with display names + status, 48dp touch targets), animations performant (no allocations per frame, derivedStateOf/graphicsLayer where relevant). Dependencies: verify every dependency you touch is the latest STABLE via maven-metadata.xml (dl.google.com/android/maven2, repo1.maven.org) — no alpha/beta/rc; report the version table. Ask me before any AGP/Kotlin/Gradle bump.

S5: (tests)
Roborazzi screenshots of the new mesh screen for 1, 4 (the exact screenshot scenario: Croma 3 + Omarchy 4 machines, one orchestrator + 3 subordinates) and 12 agents, working/idle/offline + comm-in-flight states, Cute/Sexy/Omarchy × light/dark, 360dp and tablet; ViewModel/formatter/layout unit tests.

S6: (docs + release)
English docs (CHANGELOG, docs/ section for the mesh screen with the display-name rules and motion legend, docs/QA.md manual steps) and English GitHub release notes. Version v0.4.0; release APKs signed with the release key (cert 6a7ef9…, ~/.config/rp/release.jks alias rp-release), built in the real repo (versionCode = commit count).

## Tasks
- [x] T1 (S1, S4): Display name formatter & raw ID sanitization engine with unit tests [inline] [68361e8]
- [x] T2 (S3, S4): Pure hierarchical top-down layout engine with bounding-box collision avoidance & viewport tests [inline] [0252253]
- [x] T3a (S2, S4): Agent card & attached subagent chip composables (tokens, avatar, role, status, activity, TODO bar) [inline] [6d0ac57]
- [x] T3b (S2, S3, S4): Top-down canvas integration, viewport clipping strictly below filters, fit-to-content auto-zoom & pan/zoom [inline] [a4d7f23]
- [x] T3c (S2, S4): Real-state motion system (working shimmer/pulse, subagent spinner, comm in flight, idle quiet) & dead circle code removal [inline] [a4d7f23]
- [x] T4 (S1, S2, S4): Node detail technical section with copy action, list view parity & theme tokens across dialogs [inline] [7fa37d4]
- [x] T5 (S5): Roborazzi screenshot test suite (1, 4, 12 agents, 360dp/tablet, all themes/modes) & regression verification [inline] [60e3c84]
- [x] T6 (S6): Version bump v0.4.0, documentation (CHANGELOG, docs/MESH.md, docs/QA.md), signed APK build & drafted release notes [inline] [15f1edd]
- [x] T7 (B1, V1, V2, V3): End-to-end DP layout, card sizing >= 140dp, density test (1080x1800), non-clipped badge & density-aware canvas [inline] [a0e37dd]
- [x] T8 (B2, W1, W2, W3, W3b, S1): Support Subagente role, anchor hex regex, sanitize machine chips/secondary line, compare comm IDs, route all names [inline] [a0e37dd]
- [x] T9 (W4, W5, W6, W7, S4): Perf drawWithCache, precomputed bubble groupings, canvas idle loop stop, connector-riding particles, dead model cleanup, 48dp copy target [inline] [a0e37dd]
- [x] T10 (S5, V5, V6): Add Compose UI bounding-box test, landscape, 411dp, 12-agents comm, reduced-motion, sheets screenshots & regenerate all goldens [inline] [a0e37dd]
- [x] T11 (S6): Translate QA.md & README mesh section to English, ensure doc-code accuracy, rebuild signed v0.4.0 APK [inline] [a0e37dd]
- [x] T12 (G2): Migrate off deprecated APIs across app and tests (0 deprecations) [inline] [8e19baf]
- [x] T13 (R1, R2, R4): Priority order in card (name > status/activity > secondary line > role badge; badge only on orchestrator), min readable width (180-200dp) column selection, dynamic intrinsic card height, tablet max card width [gentle-ai-worker] [fba54e0]
- [x] T14 (R3, R5, R6): Landscape viewport containment & pan centering, orthogonal/bus connector routing through inter-card gaps, comm bubble clearance >= 24dp [gentle-ai-worker] [fba54e0, f6b7328]
- [x] T15 (R7, R8): DateTimeFormatter localized timestamp with es locale, detail sheet header deduplication [gentle-ai-worker] [71b8d49]
- [x] T16 (R9): Extended Compose UI tests for all 6 diagram scenarios asserting name displayed, no height overflow, >=6 visible chars, and containment inside viewport [gentle-ai-worker] [fba54e0]
- [x] T17: Re-record all 66 Roborazzi mesh screenshots with es-rES locale, inspect all 11 scenarios personally, and rebuild signed v0.4.0 release APK [inline] [6131009]
- [x] T18 (H1): Eliminate all 17 compiler warnings (w:) across protocol, app, and test sources [gentle-ai-worker] [52d785c]
- [x] T19 (H2): Resolve 31 Android lint warnings (ModifierParameter, UseKtx, AutoboxingStateCreation, targeted suppressions) [gentle-ai-worker] [84065e1]
- [x] T20 (H3): Add ktlint Gradle plugin (latest stable), format code across all source sets, document in README, verify ktlintCheck [gentle-ai-worker] [5db5ac2]

## Log
L1: [ORCHESTRATOR → Rp-app] Feature: mesh-redesign ("Red de agentes" screen refactor). Use ODD: create odd/tasks/mesh-redesign.md (## Specs quoting the user verbatim, ## Tasks, ## Log with L1) + Engram mirror odd/mesh-redesign/tasks (project rp-app) BEFORE the first source write. Test-first (observe RED, then GREEN). Code/comments/docs in English; UI copy stays Spanish.
User verbatim: "necesito que orquestes un refactor a la parte de red de agentes, colo veras en las Imágenes necesita un refactor grande 1) el usuario jamas va a entender esos ids, debemos darle una UX/UI cómoda 2) esos círculos no me gustan, me gustaría algo como mas bonito, algo con movimiento si el orquestador o el subordinado o los sub agentes de cualquiera estan trabajando y que se vea visualmente realmente que esta pasando en el flujo 3) como ves los círculos se pueden sobre poner a los botones de arriba y se ve horrible"
Evidence from user phone (main 95ec7ba): node labels and group chip show raw UUIDs ("01a14fbb-2812-7332-aae5-d003f17b8fe7 · 4", "01a14fbb-44e3-765f-99e1-…"); nodes are plain empty circles with "ORQUESTADOR/SUBORDINADO" tags; with "Omarchy" filter a SUBORDINADO circle and ORQUESTADOR circle are drawn ON TOP of "Croma · 3" chip and group chip.
L2: [ORCHESTRATOR review 5878471] NOT approved. V1: diagram cards render as tiny pills with only the initial. V2: fit to content scales down too small, must use available width (min legible scale 1.0 for <=6 agents). V3: role badges clipped on 360dp ("Or qu", "Su bo"). V4: comm bubble overlaps card. V5: screenshot count was 36, missing 411dp + landscape. V6: add Compose UI test for card bounds >= 140dp and no intersection with filters.
L3: [ORCHESTRATOR review second & third batch] B1: layout in DP end to end with density 2.625 test. B2: support Subagente role and remove dead satellite orbit model. W1-W3b: sanitize machine chips, anchor hex regex, route all names, compare IDs in conversation. W4-W7: drawWithCache border, precomputed bubbles, stop idle loop, connector-riding particles. G1: fix screenshot determinism with frozen timestamps. G2: migrate all 31 deprecations to 0 deprecations. All verified green.
L4: [ORCHESTRATOR review round 4 on d025352] NOT approved. R1 BLOCKER: Agent names truncated or missing on subordinate cards because role badge steals width; columns squeezed. R2 BLOCKER: Content clipped vertically inside cards (activity, todos). R3 BLOCKER: Landscape bottom row cut off by LiveStrip. R4: Tablet cards small with truncated names. R5: 12-agent connectors cross intermediate cards (need orthogonal bus routing through gaps). R6: Comm bubble overlaps card bottom border. R7: US timestamps in Spanish UI (use localized es format). R8: Detail sheet header repeats role/machine 3 times. R9: Compose UI tests for all 6 scenarios with TextLayoutResult checks.

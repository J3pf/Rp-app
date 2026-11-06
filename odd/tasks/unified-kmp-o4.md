# unified-kmp-o4 — O4 Package: Agents, Mesh, Shell, Settings, Onboarding, Nav, Adaptive, iOS Host

Branch `unify/o4-shell` in worktree `.worktrees/o4` (base `feat/unified-kmp`).
Orchestrator: session `01a1af49`. Worker: O4 (`01a1af4b-c541`).
Scope: `app/.../ui/{agents,mesh,settings,onboarding,nav}` + `ui/*.kt`, `shared-ui/.../ui/{mesh,agents,settings,onboarding,nav,theme,shared,util,adaptive}`, `iosApp/**`, `.github/workflows/{ios,testflight}.yml`, docs/README/CHANGELOG.

## Specs

Quoted verbatim from `odd/tasks/unified-kmp.md`:

- **S1** "Centralizar la app para cualquier dispositivo"
- **S2** "debes analizar todo lo que hace la app actualmente y mirar que framework tipo react, flutter, etc, es mejor para desarrollar la app una sola vez y que sea instalable en cualquier dispositivo sin importar si es ios, android, celualr, tablet, celular plegable o no"
- **S3** "no quiero si no desarrollar una unica vez y que aplique para todos de una vez"
- **S4** "La app de android y en el stash de la rama de ios DEBES TOMAR SOLO LOS CERTIFICADOS Y DEMÁS que necesites para ios PERO SOLO ESO, nada de codigo, el codigo estable es el de android UNICAMENTE"
- **S5** "asegurando que no se pierda ni una funcionalidad ni que se introduzcan bugs"
- **S6** "QUE LOS 4 usean ODD y sus sub agentes SI o SI"
- **S7** "usando las mejores practicas del framework elegido"
- **S8** "debes validar que los agentes obreros no se queden pegados, con algun job que monitoree o algo asi"
- **S9** "Tú NO puedes escribir una linea de codigo, todo lo deben hacer los obreros y tu debes validar su calidad cuando te entreguen"
- **S10** "Framework: Kotlin Multiplatform + Compose Multiplatform (KMP/CMP). Not Flutter, not React Native."
- **S11** "Source of truth for behavior is the Android code on main."
- **S12** "iOS material allowed (S4), and nothing else." Take only signing/build plumbing: iosApp.entitlements, CODE_SIGN_ENTITLEMENTS in Config.xcconfig, pbxproj entitlements ref + alwaysOutOfDate=1 from stash@{0}.
- **S13** "Adaptive by construction (S2, S3). One layout system driven by window size class (compact/medium/expanded) and fold posture; phone, tablet, foldable (folded/unfolded), landscape, split-screen and iPad all use the same composables. No device-specific forks."
- **S15** "Parity gate (interpretation of S5). Every observable behavior of the Android app maps to a row in a parity matrix with a proof... Roborazzi goldens (138 PNGs tracked in app/src/test/screenshots on main; the README's 48 is stale) are not re-recorded without orchestrator approval."
- **S16** "KMP purity: no android.*, androidx.* (Android-only), javax.crypto.* imports in :protocol, :relay-client or any commonMain; Android implementations live in androidMain/:app adapters."
- **S20** "Done means ./gradlew :protocol:test :relay-client:jvmTest :shared-ui:jvmTest :app:testDirectDebugUnitTest has zero failures (plus the iOS checks of each gate)."

## Tasks

| ID | Specs | Route | Commit |
|---|---|---|---|
| T1 | S5 S11 S15 | Parity matrix `docs/parity/o4-shell.md` + characterization tests on UNCHANGED Android code. Platform ports + Adaptive plan + iOS signing plumbing doc. → **Gate A** | pending |
| T6 | S12 S13 S15 S16 | Migrate Agents, Mesh, Settings, Onboarding, Nav, Themes to shared-ui + adaptive scaffolds + iOS host & signing. → **Gate C4** | pending |
| T9 | S3 S5 | Docs update (README, CHANGELOG, INSTALACION, COMPATIBILIDAD), release notes. | pending |

## Log

- **L1** (orchestrator brief received): Assigned package O4. Initialized worktree registration, feature doc, Engram mirror, todo list.
- **L2** (environment): `gentle-ai-explore` failed twice on T1 exploration — task ids `mw4jqmxx-1-3lj2` and `mw4k65em-2-8e00`, both ending with `assistant reported an error (unavailable)` after several tool calls. Orchestrator approved inline bounded read batches as fallback while the explorer stays broken; heavy units will still be retried through subagents (e.g. `gentle-ai-worker` for characterization tests). T1 may be split: READY T1a (agents, settings, onboarding, nav) then READY T1b (Mesh).
- **L3** (orchestrator): T1 is not high-risk; no `gentle-ai-verify` needed for Gate A. High-risk row for my slice is iOS signing plumbing (S12) in T6 only.
- **L5** (T1a evidence, characterization): Wrote 10 new JVM tests, all green on UNCHANGED Android code — `AgentsParityCharacterizationTest` (4: quick filters launcher split, search trims/case, status chips exact Spanish labels), `ArchivedAndPairParityCharacterizationTest` (5: archived exact empty string, `Cargar más` cursor gating, parsePairingUri accept/reject incl. edge forms, PairContent prefill/submit, busy UI), `SettingsThemeParityCharacterizationTest` (1: theme dialog shows exactly the 4 labels, reports picked id). Verified with `./gradlew :app:testDirectDebugUnitTest --tests "dev.j3pf.rp.ui.agents.*" --tests "dev.j3pf.rp.ui.settings.*" --tests "dev.j3pf.rp.ui.theme.*" --tests "dev.j3pf.rp.ui.nav.*"` → BUILD SUCCESSFUL; agents+settings+nav+theme dirs = 25 tests / 0 failures / 0 skipped. Note: helpers work via `createComposeRule` v2 + Robolectric sdk 34 as the neighboring tests do. Row statuses updated in docs/parity/o4-shell.md; remaining gaps (AG-2/AG-3/AG-11, AR-1/AR-6, AR-2 rest, SE-5..SE-13, OB-4) need the WebSocket/REST VM harness — extracted pure logic at T6 will make them provable without harness copies. Golden host-drift: orchestrator confirmed goldens differ on this Mac even on pristine main; doc marks golden-based proofs `golden - unverified on this host` and no PNGs were touched.
- **L4** (evidence, T1a audit): Docs mapped: README "Main features", docs/QA.md (sections 4-8, 10: Agents/P1-P9, Mesh M1-M16, UI U1-U8, F1.1-F6.4), docs/MESH.md (name resolution, layout engine, motion legend, Detalles técnicos). Android sources (worktree `.worktrees/o4`): `app/src/main/java/dev/j3pf/rp/ui/agents/{AgentsScreen.kt:92, AgentsViewModel.kt:172, ArchivedScreen.kt:174}`, `ui/mesh/{MeshScreen.kt:59, MeshViewModel.kt:572}`, `ui/settings/{SettingsScreens.kt:266, SettingsViewModel.kt:226}`, `ui/onboarding/OnboardingScreens.kt:282`, `ui/nav/RpRoot.kt:359`, `ui/MainViewModel.kt:97`. Existing tests: `app/src/test/.../agents/{AgentsSortingAndCardTest, AgentsLifecycleTest}`, `mesh/{MeshScreenTest, MeshViewModelTest, MeshViewportRenderTest}`, `settings/DevicesScreenTest`, `theme/{MotionTest, ThemeTest}`, `nav/MeshNavigationTest`, plus data-layer `RelayApiMeshTest`, `MeshRepositoryTest`, `AgentsRepositoryCloseTest`. Roborazzi goldens live under `app/src/test/screenshots` (48 reference images per README).

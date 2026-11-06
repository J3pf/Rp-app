# unified-kmp — one codebase for every device

Branch `feat/unified-kmp` (base `main` @ 482cbb9). Orchestrator: session `01a1af49`. Workers: Obrero 1-4 (Herdr panes w9, wA, wB, wC).
Workers read this file down to `## Log`, then work in their own worktree. Owner of every line of code: the workers, never the orchestrator.

## Specs

User requirements, quoted verbatim (Spanish, as written):

- **S1** "Centralizar la app para cualquier dispositivo"
- **S2** "debes analizar todo lo que hace la app actualmente y mirar que framework tipo react, flutter, etc, es mejor para desarrollar la app una sola vez y que sea instalable en cualquier dispositivo sin importar si es ios, android, celualr, tablet, celular plegable o no"
- **S3** "no quiero si no desarrollar una unica vez y que aplique para todos de una vez"
- **S4** "La app de android y en el stash de la rama de ios DEBES TOMAR SOLO LOS CERTIFICADOS Y DEMÁS que necesites para ios PERO SOLO ESO, nada de codigo, el codigo estable es el de android UNICAMENTE"
- **S5** "asegurando que no se pierda ni una funcionalidad ni que se introduzcan bugs"
- **S6** "QUE LOS 4 usean ODD y sus sub agentes SI o SI"
- **S7** "usando las mejores practicas del framework elegido"
- **S8** "debes validar que los agentes obreros no se queden pegados, con algun job que monitoree o algo asi"
- **S9** "Tú NO puedes escribir una linea de codigo, todo lo deben hacer los obreros y tu debes validar su calidad cuando te entreguen"

Orchestrator decisions that answer S2 (evidence: repo at `main`, not opinion):

- **S10 — Framework: Kotlin Multiplatform + Compose Multiplatform (KMP/CMP). Not Flutter, not React Native.**
  Why: `main` is already KMP/CMP. `:protocol` (4.6k LOC), `:relay-client` (4.1k LOC) and `:shared-ui` (44 commonMain files: theme, MeshCanvas, screens) compile for JVM + `iosArm64` + `iosSimulatorArm64`; `iosApp/` embeds `SharedUi.framework`; TestFlight CI exists. `:app` (≈9.3k LOC main + 34 test files) is the only Android-only layer. Rewriting in Flutter/RN would discard ≈8.7k LOC of tested shared code plus the Android code that S4 declares stable, and would make S5 (no lost functionality, no new bugs) far harder to prove. CMP gives one UI codebase for Android phone/tablet/foldable and iOS iPhone/iPad. Desktop/Web are out of scope now; the architecture must not block them.
- **S11 — Source of truth for behavior is the Android code on `main`.** Behavior = what the Android app does today (README "Main features", CHANGELOG, `docs/QA.md`, `docs/MESH.md`, tests). The `:app` module is migrated into shared code; it is the oracle.
- **S12 — iOS material allowed (S4), and nothing else.** From `feat/ios-real-ui` / `stash@{0}` take only signing/build plumbing, never `.kt`/`.swift` source:
  1. `iosApp/iosApp/iosApp.entitlements` (`keychain-access-groups` = `$(AppIdentifierPrefix)$(CFBundleIdentifier)`), copied verbatim from `feat/ios-real-ui`;
  2. `CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements` in `iosApp/Configuration/Config.xcconfig`;
  3. the `project.pbxproj` reference to that entitlements file, and `alwaysOutOfDate = 1` on the "Compile Kotlin Multiplatform Framework" shell phase (from `stash@{0}`; it fixes the stale-framework "ghost build");
  4. CI signing already on `main` (`testflight.yml`, `ExportOptions.plist`, secrets) stays as is.
  Forbidden: `git merge`, `cherry-pick`, `stash pop/apply`, or `git checkout <ref> -- <code>` from any iOS branch (`feat/ios-real-ui`, `fix/ios-black-screen-root-surface`, `diag/*`) or the stash; copying or reading its Kotlin/Swift to reuse it. The iOS adapters (URLSession/WebSocket, Keychain, APNs, …) are written fresh on top of the new shared contracts. Lessons recorded in Engram/CHANGELOG may be used as requirements (e.g. Keychain needs the entitlement; the root composable must paint a `Surface` or dark mode renders black).
- **S13 — Adaptive by construction (S2, S3).** One layout system driven by window size class (compact/medium/expanded) and fold posture; phone, tablet, foldable (folded/unfolded), landscape, split-screen and iPad all use the same composables. No device-specific forks.
- **S14 — In-place upgrade (interpretation of S5).** A device that has the current stable APK (v0.6.1) and updates to the migrated build keeps its pairing, secure session, Room data, theme, TODO state and settings. No forced re-pair, no data loss. Room `MIGRATION_2_3` and secure-storage readability must be proven by test against data produced by the old code.
- **S15 — Parity gate (interpretation of S5).** Every observable behavior of the Android app maps to a row in a parity matrix with a proof (a test that passes on today's Android code and still passes after migration). Existing tests are never weakened, deleted or re-recorded to get green. Roborazzi goldens (48) are not re-recorded without orchestrator approval. A behavior that cannot exist on iOS (e.g. Android battery-optimization screen, foreground `DirectPushService`) is listed as "platform-specific, with the iOS equivalent" and approved by the orchestrator.

Standing repo constraints (existing `AGENTS.md` of Rp-app, not new requirements):

- **S16** KMP purity: no `android.*`, `androidx.*` (Android-only), `javax.crypto.*` imports in `:protocol`, `:relay-client` or any `commonMain`; Android implementations live in androidMain/`:app` adapters.
- **S17** The `direct` flavor works without Google Play Services; `fcm` stays optional; both deduplicate by `promptId`.
- **S18** No plaintext credentials; session keys/device tokens only in Android Keystore / EncryptedSharedPreferences (iOS: Keychain). Never commit `app/google-services.json` or `keystore.properties`.
- **S19** Diff viewer keeps its user-driven discard confirmation before `host.git_discard`.
- **S20** Done means `./gradlew :protocol:test :relay-client:jvmTest :shared-ui:jvmTest :app:testDirectDebugUnitTest` has zero failures (plus the iOS checks of each gate).

## Tasks

| ID | Specs | Route | Commit |
|---|---|---|---|
| T0 | S8 S9 | Orchestrator: branch, 4 worktrees, monitor job, baseline run, briefs | (this commit) |
| T1 | S5 S11 S15 | O1-O4 in parallel: parity matrix + characterization tests on UNCHANGED Android code. No production change. → **Gate A** | |
| T2 | S7 S10 S16 | O1: target architecture ADR + shared ports (`SecureStore`, `SettingsStore`, db, clock, ids, platform capabilities, ViewModel/state-holder pattern, DI-agnostic composition roots). O2-O4 file port requests to O1. → **Gate B** | |
| T3 | S14 S15 S18 | O1: data core in common code (Room KMP, repositories, secure/settings stores, iOS Keychain actual with S12 entitlements) + in-place upgrade proof → **Gate C1** | |
| T4 | S15 S17 | O2: relay session, push, notification actions, retry; iOS URLSession/WebSocket adapters + APNs → **Gate C2** | |
| T5 | S15 S19 | O3: chat, composer, attachments, slash commands, diffs, prompt sheet, pending, new agent → **Gate C3** | |
| T6 | S12 S13 S15 | O4: agents, Mesh, settings, onboarding, navigation, theme, adaptive scaffolds, iOS host + signing → **Gate C4** | |
| T7 | S3 S5 S7 | All: integration, thin Android host, delete migrated Android-only duplicates, Hilt only as Android composition root → **Gate D** | |
| T8 | S5 S9 | Orchestrator + `gentle-ai-verify` + Judgment Day on high-risk slices (secure storage, db, push) → **Gate E** | |
| T9 | S3 | O4: docs (README, CHANGELOG, INSTALACION, COMPATIBILIDAD), release notes. No release/tag without the user. | |

## Work packages (disjoint ownership; edit only what you own)

| Worker | Session | Branch / worktree | Owns |
|---|---|---|---|
| O1 Data core | Obrero 1 (`01a1af4b-28b5`) | `unify/o1-data` · `.worktrees/o1` | `app/.../{data/db,data/repo,data/secure,domain,util,di}` + their tests; new data KMP module(s); `:protocol` additions |
| O2 Network/push | Obrero 2 (`01a1af4b-3360`) | `unify/o2-net` · `.worktrees/o2` | `app/.../{data/net,push}`, root `app/.../*.kt` (Application, services), `:relay-client`; iOS transport + APNs |
| O3 Chat | Obrero 3 (`01a1af4b-1e93`) | `unify/o3-chat` · `.worktrees/o3` | `app/.../ui/{chat,promptsheet,pending,newagent,components}`, `shared-ui/.../ui/{chat,diff,promptsheet,pending,newagent,components}` |
| O4 Agents/Mesh/Shell | Obrero 4 (`01a1af4b-c541`) | `unify/o4-shell` · `.worktrees/o4` | `app/.../ui/{agents,mesh,settings,onboarding,nav}` + `ui/*.kt`, `shared-ui/.../ui/{mesh,agents,settings,onboarding,nav,theme,shared,util,adaptive}`, `iosApp/**`, `.github/workflows/{ios,testflight}.yml`, docs/README/CHANGELOG |

Shared files (`settings.gradle.kts`, `gradle/libs.versions.toml`, module `build.gradle.kts`, `AndroidManifest.xml`): small, additive edits only; rebase on `feat/unified-kmp` before reporting; the later merger resolves conflicts. Contracts are owned by O1: ask O1 (and copy the orchestrator) for a port instead of inventing a parallel one.

## Gates (the orchestrator verifies each one independently with real commands before merging)

- **Gate A (parity):** matrix `docs/parity/o<N>-<slice>.md` (columns: ID, behavior incl. edge cases and exact error strings, Android source `file:line`, proof test, status). Covers every README "Main features", CHANGELOG and `docs/QA.md` item of the slice. Every proof test is green on unchanged Android code. Baseline counts recorded. No production code touched.
- **Gate B (architecture):** ADR + ports compile for JVM, `iosArm64`, `iosSimulatorArm64`; grep proves no Android imports in common code; contracts reviewed by the orchestrator.
- **Gate C (per slice):** the slice runs through shared code on Android; every Gate A proof test still green and unmodified (mechanical signature updates only, listed in the report); Roborazzi untouched; iOS target of the slice compiles (`:shared-ui:compileKotlinIosSimulatorArm64`); high-risk slices (S14, S18) carry an in-place-upgrade test.
- **Gate D (integration):** S20 command + `:app:assembleDirectDebug` + `:app:lintDirectDebug` + iOS simulator build via `xcodebuild` + adaptive-layout tests at phone/tablet/foldable sizes. Test count never lower than baseline.
- **Gate E (independent):** `gentle-ai-verify` per S# and Judgment Day on S14/S18/push. Verdict per S#.

## Worker rules (S6)

Each worker runs ODD for its package: its own feature doc `odd/tasks/unified-kmp-o<N>.md` + Engram mirror `odd/unified-kmp-o<N>/tasks` + `todo` before the first write; `gentle-ai-explore` when understanding needs more than one read batch; `gentle-ai-worker` for 2+ independent units; `gentle-ai-verify` for high-risk items; test-first (RED then GREEN) for behavior changes; Conventional Commits in ≈400-line slices with tests+docs in the same commit; Risk line on every change; no push/PR/merge/tag. Heartbeat: file or git activity at least every 10 minutes, a one-line intercom progress after each task transition, and an immediate intercom `ask` to the orchestrator (never to the human) when blocked.

## Log

- **L1** (user, original request, verbatim):

> Intención
> Centralizar la app para cualquier dispositivo
> Detalles
> Actualmente la app que tenemos estable es para android unicamente, debes analizar todo lo que hace la app actualmente y mirar que framework tipo react, flutter, etc, es mejor para desarrollar la app una sola vez y que sea instalable en cualquier dispositivo sin importar si es ios, android, celualr, tablet, celular plegable o no, etc ya que no quiero si no desarrollar una unica vez y que aplique para todos de una vez
> Ejemplos
> La app de android y en el stash de la rama de ios DEBES TOMAR SOLO LOS CERTIFICADOS Y DEMÁS que necesites para ios PERO SOLO ESO, nada de codigo, el codigo estable es el de android UNICAMENTE
> Acción
> Usando ODD Orquestar los 4 agentes obreros disponibles por intercom para lograr la migracion de la app al nuevo framework asegurando que no se pierda ni una funcionalidad ni que se introduzcan bugs, QUE LOS 4 usean ODD y sus sub agentes SI o SI, usando las mejores practicas del framework elegido, debes validar que los agentes obreros no se queden pegados, con algun job que monitoree o algo asi
> Limites
> Tú NO puedes escribir una linea de codigo, todo lo deben hacer los obreros y tu debes validar su calidad cuando te entreguen

- **L2** (orchestrator): finding that changes the user's premise: `main` is already KMP/CMP; the work is converging `:app` into shared code, not adopting a new framework. Recorded as S10. Risk accepted: APNs needs the Push Notifications capability in the App ID and a regenerated provisioning profile (`PROVISIONING_PROFILE_BASE64`), a human step in the Apple developer portal; workers must flag it, not work around it.

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
  3. `alwaysOutOfDate = 1` on the "Compile Kotlin Multiplatform Framework" shell phase of `project.pbxproj` (from `stash@{0}`; it fixes the stale-framework "ghost build"). Correction (L5): neither the stash nor `feat/ios-real-ui` adds a pbxproj reference to the entitlements file, so there is none to take; `CODE_SIGN_ENTITLEMENTS` in the xcconfig is enough;
  4. CI signing already on `main` (`testflight.yml`, `ExportOptions.plist`, secrets) stays as is.
  Forbidden: `git merge`, `cherry-pick`, `stash pop/apply`, or `git checkout <ref> -- <code>` from any iOS branch (`feat/ios-real-ui`, `fix/ios-black-screen-root-surface`, `diag/*`) or the stash; copying or reading its Kotlin/Swift to reuse it. The iOS adapters (URLSession/WebSocket, Keychain, APNs, …) are written fresh on top of the new shared contracts. Lessons recorded in Engram/CHANGELOG may be used as requirements (e.g. Keychain needs the entitlement; the root composable must paint a `Surface` or dark mode renders black).
- **S13 — Adaptive by construction (S2, S3).** One layout system driven by window size class (compact/medium/expanded) and fold posture; phone, tablet, foldable (folded/unfolded), landscape, split-screen and iPad all use the same composables. No device-specific forks.
- **S14 — In-place upgrade (interpretation of S5).** A device that has the current stable APK (v0.6.1) and updates to the migrated build keeps its pairing, secure session, Room data, theme, TODO state and settings. No forced re-pair, no data loss. Room `MIGRATION_2_3` and secure-storage readability must be proven by test against data produced by the old code.
- **S15 — Parity gate (interpretation of S5).** Every observable behavior of the Android app maps to a row in a parity matrix with a proof (a test that passes on today's Android code and still passes after migration). Existing tests are never weakened, deleted or re-recorded to get green. Roborazzi goldens (138 PNGs tracked in `app/src/test/screenshots` on `main`; the README's 48 is stale) are not re-recorded without orchestrator approval. A behavior that cannot exist on iOS (e.g. Android battery-optimization screen, foreground `DirectPushService`) is listed as "platform-specific, with the iOS equivalent" and approved by the orchestrator.

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

- **L3** (orchestrator, baseline on `main` @ 482cbb9, `./gradlew :protocol:test :relay-client:jvmTest :shared-ui:jvmTest :app:testDirectDebugUnitTest :app:lintDirectDebug --continue` → BUILD SUCCESSFUL in 4m 7s): protocol 73 · relay-client 75 · shared-ui 108 · app 243 = **499 tests, 0 failures, 0 skipped**. Gate D requires a count ≥ 499 plus the new characterization tests, and no failure.

- **L2** (orchestrator): finding that changes the user's premise: `main` is already KMP/CMP; the work is converging `:app` into shared code, not adopting a new framework. Recorded as S10. Risk accepted: APNs needs the Push Notifications capability in the App ID and a regenerated provisioning profile (`PROVISIONING_PROFILE_BASE64`), a human step in the Apple developer portal; workers must flag it, not work around it.

- **L4** (orchestrator, decisions during Gate A):
  - Gate A results so far: O3 `a32e66c` FAIL (weak S19/consent assertions, wrong citations, 20 gaps, 12 missing rows); O1 `ec8917c` conditional FAIL (false row DI-01 vs `fallbackToDestructiveMigration(dropAllTables = true)` at `AppModule.kt:48`, `MIGRATION_2_3` pinned only on defaults, vacuous `SecretStoreScaffoldTest`); O4 T1a `ca60306` under verification; O2 not yet READY. Rule given to all: deferring rows to "after the migration" is rejected; characterization tests are GREEN on unchanged code; real Android defects are recorded as `defect`, not fixed in T1.
  - Decision (user): no Android emulator/device on this machine and none will be installed now. The real-key S14 proof (Keystore readability + true `adb install -r` over v0.6.1) is a **documented blocker of Gate C1**, to be run later by the user. O1 builds the full harness and every report lists it as `UNPROVEN - needs device`. Everything else of S14 is proven here (Room `MigrationTestHelper`, v3 fixture DB, `preferences_pb` fixture, Tink format via Robolectric, DB opened from the iOS simulator test; iPhone 17 Pro simulator available, Xcode 26.5).
  - Roborazzi: the committed goldens do not match this host. Unchanged `main` (482cbb9): `verifyRoborazziDirectDebug --tests '*RoborazziScreenshotTest*'` = 23 tests, 17 failed. Therefore the gate is **differential**: host images recorded from `main` in a scratch worktree (`/tmp/rp-baseline/host-goldens`, 138 PNGs; record then verify passes, so rendering is deterministic). Procedure for a candidate: in a detached worktree copy `/tmp/rp-baseline/host-goldens/.` over `app/src/test/screenshots/` and run `./gradlew :app:verifyRoborazziDirectDebug --tests '*RoborazziScreenshotTest*'`; it must pass. Workers never run record tasks nor commit changed PNGs.
  - Gate B: ADR 0001 (`9e7cb3b`) not approved until the ports exist as compiling interfaces in a `:data-core` KMP module (`compileKotlinJvm`, `compileKotlinIosSimulatorArm64`, no Android imports in commonMain). Android keeps the framework SQLite driver unless proof on both drivers.
  - Infra: watchdog `/tmp/rp-orchestrator/watch.sh` (herdr pane status + worktree activity + model-error detection), re-armed every 30 min. Writer subagents were refused by the launcher for O4 (feature-doc resolution); investigating.

- **L6** (orchestrator, user decision during Gate A): **iOS push = APNs directo en el relay (option B)**. The user chose, over FCM-for-iOS and over deferring iOS background push, that `Rp-relay` gets its own Apple sender. Evidence (O2 read-only survey of Rp-relay): today the relay has FCM only (`FcmSender` data-only, `PushChannelReq{fcmToken?,direct}`, a single token column via `PUT /v1/devices/me/push`, `encryptPush` AES-256-GCM, nonce 12 B, AAD `rp1`, body ≤ 3000 B pre-base64); no platform-typed token, no APNs sender, no `mutable-content`. Consequences: (1) the encrypted envelope `{k,n,c}` rides verbatim in the APNs payload and an iOS Notification Service Extension decrypts it with the `pushKey` from the shared Keychain access group (S12 entitlement) through the shared crypto helper behind `SecureCrypto`; (2) the relay change is a SEPARATE repo and SEPARATE slice (platform-typed device-token registration, Apple sender in the dispatcher with JWT `.p8`, `mutable-content` payload, bad-device-token cleanup, backward compatible with Android `fcmToken`/`direct`), developed in its own worktree of `Rp-relay` with its own tests, **no push, no deploy to 192.168.18.138 without the user**; (3) human steps for the user: Push Notifications capability in the App ID + regenerated provisioning profile, and an APNs auth key (`.p8`, key id, team id) for the relay; (4) iOS push in T4 waits for the contract between app and relay to be written first (docs/adr, owned by O2); Android flavors are untouched.

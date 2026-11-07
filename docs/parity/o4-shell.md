# Parity matrix — o4-shell (agents, settings, onboarding, nav, theme, adaptive, Mesh)

Worker O4 · branch `unify/o4-shell` · Gate A input for `odd/tasks/unified-kmp.md` T1/T6.
Columns: ID | behavior (edge cases + exact strings) | Android source `file:line` | proof | status.
Status vocabulary (orchestrator 2026-11-06): {proven, proof-added, partial, gap-with-reason, device-only, defect, out-of-slice}.

## Scope note (golden counts)

README's "48 reference images" is stale (S15): `app/src/test/screenshots` holds 138 PNGs: agents 18, chat 12, mesh 78, new 6, onboarding 6, pending 6, prompt 6, settings 6. O4 owns agents (18) + mesh (78) + settings (6) + onboarding (6) = 108 goldens; chat/new/pending/prompt goldens belong to O3. None may be re-recorded without orchestrator approval. **Host-drift note (orchestrator, 2026-11-06):** `verifyRoborazziDirectDebug` on UNCHANGED main @ 482cbb9 yields 23 tests / 17 FAILED on this Mac, so committed goldens do not match this host's rendering; rows citing a golden read `golden - unverified on this host` and Gate C/D uses the orchestrator's HOST baseline differential gate.

## A. Agents inbox (AgentsScreen / AgentsViewModel · QA P1/P4, U4, U5)

| ID | Behavior | Android source | Proof | Status |
|---|---|---|---|---|
| AG-1 | Ordering inside a machine group: agents with open prompts first, then online, then working, then most recent `lastSeenAt` (tie→stable) | `shared-ui/.../agents/AgentsModels.kt:22-27` | `AgentsSortingAndCardTest::sortAgentsWithinGroup orders open prompts first, then working, then most recent lastSeenAt` | proven |
| AG-2 | Group ordering across machines: group with prompts ≻ working group ≻ lastSeen; groups without a visible machine still render (synthetic `MachineSummary`) | `app/.../agents/AgentsViewModel.kt:64-92` | `AgentsViewModelParityTest::AG-2 groups are ordered across machines…` (real websocket harness + APP_WELCOME + Room seeding) | proof-added |
| AG-3 | Context filter: `AppSettings.ALL` shows everything; a specific context or machineId filters machines and agents (`machineContext == sel || machineId == sel`); returning to ALL restores both groups | `app/.../agents/AgentsViewModel.kt:59-63` + `AgentsViewModel.select()` | `AgentsViewModelParityTest::AG-3 context filter…` | proof-added |
| AG-4 | Quick filters `Todas / En línea / ✦ agy / π pi` + search across `sessionName/cwd/gitBranch/lastMessagePreview` (case-insensitive, trimmed; offline-only group dropped) | `shared-ui/.../agents/AgentsModels.kt:14-18,46-72` | `AgentsParityCharacterizationTest` (4 tests) | proof-added |
| AG-5 | Agent card at-a-glance: status chip, active task, TODO progress, relative activity time, preview (no horizontal scroll) | `shared-ui/.../components/StatusChip.kt:62-69`, `shared-ui/.../agents/AgentCard.kt:119+` | `AgentsSortingAndCardTest::AgentCard displays status…` (golden part unverified on this host) | proven |
| AG-6 | Status labels exact: `Desconectado <relative time>`, `Esperando respuesta`, `Trabajando`, `Compactando`, `Error`, `Inactivo`; offline wins over any work status even with `lastSeenAt = null` | `shared-ui/.../components/StatusChip.kt:62-69` | `AgentsParityCharacterizationTest::status chips use the exact Spanish labels` | proof-added |
| AG-7 | Resume agent: only when machine `hostOnline`; else snackbar exact `Para reanudar agentes, ejecuta rp-host en $machineName`; success `Agente reanudado en la PC`; failure `No se pudo reanudar: ${message}` | `app/.../agents/AgentsScreen.kt:58-63`, `AgentsViewModel.kt:161-171` | `AgentsResumeParityTest::AG-7 running host resume success…` + `::AG-7 running host resume failure…` (AgentsScreen compose render, real VMs, `HOST_SPAWN` envelope); the `Para reanudar…` fallback branch (AgentsScreen.kt:62-63) is not reachable from observable UI state (canResume already encodes hostOnline) | partial |
| AG-8 | `MachineHeroDropdown`: `Todas las máquinas` entry, contexts then machines, no redundant legacy chip row (QA F3.1) | `shared-ui/.../components/Common.kt:160-182,326` | `AgentsSortingAndCardTest::no context chip row…` | proven |
| AG-9 | Close agent confirmation dialog exact copy: title `¿Cerrar el agente?`, body `Esto cerrará el pane en Herdr y detendrá el agente`, confirm `Cerrar agente`, dismiss `Cancelar` | `app/.../agents/AgentsScreen.kt:76-93`, strings.xml:29-31 | `AgentsLifecycleTest::close confirmation…` + `canceling…` | proven |
| AG-10 | Close outcome paths: `Closed` silent; `Archived` snackbar `No había un pane de Herdr vinculado; el historial quedó archivado.`; `Failed` shows relay's message | `app/.../agents/AgentsViewModel.kt:111-130`, strings.xml:47 | `AgentsLifecycleTest::agents view model confirms close once…` | proven |
| AG-11 | Refresh: sets `isRefreshing`, calls `connection.retryNow()` (no-op while Connected, ReconnectingSocketPolicy.kt:63-66), clears the flag after 600 ms virtual; transient TRUE pinned before the drain | `app/.../agents/AgentsViewModel.kt:90-96` | `AgentsViewModelParityTest::AG-11 refresh marks refreshing and clears it again` | proof-added |

## B. Archived agents (ArchivedScreen · README v0.6.0, QA)

| ID | Behavior | Android source | Proof | Status |
|---|---|---|---|---|
| AR-1 | Screen loads archived on entry; `archivedLoading` while first page flies; successful load maps `AgentArchivedList(agents, nextBefore)` into state | `app/.../agents/ArchivedScreen.kt:50-56`, `AgentsViewModel.kt:124-142` | `AgentsViewModelParityTest::AR-1 initial archive load…` | proof-added |
| AR-2 | Error state with retry button (`action_retry`) instead of empty state | `app/.../agents/ArchivedScreen.kt:109-115`, `AgentsViewModel.kt:137-141` | `AgentsLifecycleTest::failed archived fetch…` | proven |
| AR-3 | Empty state exact `No hay agentes archivados` (only when no error) | `ArchivedScreen.kt:117`, strings.xml:34 | `ArchivedAndPairParityCharacterizationTest::empty archived list…` | proof-added |
| AR-4 | Pagination: `Cargar más` appears iff `nextBefore != null`, hidden while an error shows; VM appends next page and drops the exhausted cursor | `app/.../agents/ArchivedScreen.kt:123-125`, `AgentsViewModel.kt:133-143` | `ArchivedAndPairParityCharacterizationTest::load more button…`/`::load more button is hidden…` + `AgentsViewModelParityTest::AR-4 load more appends…` | proof-added |
| AR-5 | Purge is user-gated: row button `Eliminar definitivamente` → dialog title `¿Eliminar historial de %1$s?`, body `Se eliminará definitivamente el historial…`, keep `Conservar historial`, confirm `Sí, eliminar definitivamente` | `ArchivedScreen.kt:160-196`, strings.xml:42-46 | `AgentsLifecycleTest::archived destructive action…` | proven |
| AR-6 | Purge success removes the row locally; failure snackbar `No se pudo eliminar definitivamente el historial` | `app/.../agents/AgentsViewModel.kt:147-156` | `AgentsViewModelParityTest::AR-6 purge success…` + `AgentsLifecycleTest::archived destructive action…` | proof-added |
| AR-7 | Row content: `agentTitle(sessionName,cwd,agentId)`, `Máquina: %1$s`, `Última actividad: %1$s`, plural `archived_message_count`, 2-line preview | `ArchivedScreen.kt:152-167`, strings.xml:35-36, plurals:37 | partially inside `AgentsLifecycleTest::archived destructive action…` (name + `PC principal`); machine/lastSeen/plurals gap | gap-with-reason |
| AR-8 | Tapping an archived row opens chat read-only (flag via `savedStateHandle`, `archived_read_only_agent`) | `app/.../nav/RpRoot.kt:247-260,284-285` | the flag wiring remains inside RpRoot; D-1 route accessibility is fixed, but no production-shaped archived-row render/navigation test pins the read-only transition | gap-with-reason |

## C. Settings (SettingsScreens/SettingsViewModel; QA P1/P2/P5/P6/P7/P8/P9)

| ID | Behavior | Android source | Proof | Status |
|---|---|---|---|---|
| SE-1 | Theme dialog renders ALL `RpThemeChoice.entries` (5): gentle-cute, gentle-sexy, omarchy-system, system plus AUTO=`Sincronizado con Pi (automático)`; picking one calls `onSelectTheme(choice.id)` and closes the dialog | `shared-ui/.../theme/Theme.kt:13-22,170,251+`, `SettingsContent.kt:331-350`, `app/.../settings/SettingsViewModel.kt:133` | `SettingsThemeParityCharacterizationTest::theme dialog shows exactly the five rp theme choices…` (AUTO text count = 2 = row + dialog; mutation-filtered AUTO keeps the test RED) + `ThemeTest::paletteFor entrega…` | proof-added |
| SE-2 | Default theme when no data: Gentle-Cute; auto theme picks by most-repeated, personal PC first (≥2 PCs) | `shared-ui/.../theme/Theme.kt:170` | `ThemeTest::auto…` (4 tests) | proven |
| SE-3 | Design tokens `RpSpacing/RpShapes/RpGlows/RpGradients/RpMotion` derive from MaterialTheme | `shared-ui/.../theme/DesignTokens.kt:14-39`, `Motion.kt` | `MotionTest` on adaptiveSpec + contrast | proven |
| SE-4 | Devices list: load success marks current device, revoked filtered (QA F1.1/F1.2) | `app/.../settings/SettingsViewModel.kt:85-94` | `DevicesScreenTest::devices list renders…` | proven |
| SE-5 | Device load failure message `No se pudo cargar la lista de dispositivos`; revoke success `Dispositivo revocado` / failure `No se pudo revocar`; revoked removed from local list | `app/.../settings/SettingsViewModel.kt:82-98` | `SettingsActionsParityTest::SE-5 device load failure and revoke success or failure preserve exact messages and local list` | proof-added |
| SE-6 | Edit machine: trims name/context, empty→null; `PC actualizada` / `No se pudo guardar` | `app/.../settings/SettingsViewModel.kt:118-121` | `SettingsActionsParityTest::SE-6 edit machine trims…` | proof-added |
| SE-7 | Machines: refresh on entry, revoke `PC revocada`/`No se pudo revocar` | `app/.../settings/SettingsScreens.kt:91-116`, `SettingsViewModel.kt:132-137` | `SettingsActionsParityTest::SE-7 machines screen refreshes…` | proof-added |
| SE-8 | Add machine: creates code on entry, copyable `/rp pair URL CÓDIGO` command to clipboard, error `No se pudo generar el código` | `app/.../settings/SettingsScreens.kt:120-156`, `SettingsViewModel.kt:139-148` | `SettingsActionsParityTest::SE-8 machine code success is stored…` | proof-added |
| SE-9 | Notifications screen: health refresh on resume, per-machine categories, criticalAlways special category sent to relay, quiet hours `HH:mm` per PC with blank disabling, agent unmute; push via `PUT /v1/devices/me/prefs` | `app/.../settings/SettingsViewModel.kt:67-75,137-219`, `SettingsScreens.kt:143-183` | `SettingsActionsParityTest::SE-9 notification preferences persist locally and are sent to the relay`; the resume-driven health refresh itself is not pinned | partial |
| SE-10 | Test notification: success `Notificación de prueba enviada` / failure `No se pudo enviar la notificación de prueba` | `app/.../settings/SettingsViewModel.kt:212-219` | `SettingsActionsParityTest::SE-10 test notification reports exact success and failure messages` | proof-added |
| SE-11 | Prefs save failure message `No se pudieron guardar las preferencias en el relay` | `app/.../settings/SettingsViewModel.kt:183-188` | `SettingsActionsParityTest::SE-11 relay preference save failure exposes the exact error and submitted preferences` | proof-added |
| SE-12 | Security: `setRequireUnlock/setAppLock/setFlagSecure` persist (QA P7) | `app/.../settings/SettingsViewModel.kt:222-228` | `SettingsActionsParityTest::SE-12 security and chat display preferences persist through SettingsRepository` | proof-added |
| SE-13 | Battery screen: refresh on resume; request via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS package:`; open system battery settings | `app/.../settings/SettingsScreens.kt:214-253` | a real system dialog is required; QA Setup step 3 + §4 P6 cover it | device-only |
| SE-14 | About: versionName/versionCode/flavor from BuildConfig | `app/.../settings/SettingsScreens.kt:261-266` | `AboutContentParityCharacterizationTest::SE-14 about screen renders version, push flavor and licenses header` | proof-added |
| SE-15 | Direct-push channel toggle applies both flavors with dedupe by promptId (QA N10; S17) | `SettingsViewModel.kt:172` → SessionRepository | O2-owned network layer; flagged per worker table | out-of-slice |

## D. Onboarding (OnboardingScreens; README/INSTALACION)

| ID | Behavior | Android source | Proof | Status |
|---|---|---|---|---|
| OB-1 | Pair: busy guard (double-submit ignored), success → done, failure shows relay message | `app/.../onboarding/OnboardingScreens.kt:75-93` | content level: `PairContent` prefill/submit + busy-disabled button in `ArchivedAndPairParityCharacterizationTest`; the VM busy-guard itself untested | partial |
| OB-2 | `parsePairingUri` accepts `rp://pair?u=…&c=…`; deep link prefills URL+code; invalid forms rejected (`https://…` alone, missing `u`/`c`, `rp:pair…`, empty) | `OnboardingScreens.kt:100-104`; `SessionRepository.kt:134-143` | `ArchivedAndPairParityCharacterizationTest::parsePairingUri…` + `::pair content prefill…` | proven |
| OB-3 | QR scanner: CameraX + ML Kit, STRATEGY_KEEP_ONLY_LATEST, single-handled `AtomicBoolean`; invalid shows `Ese QR no es de RP (rp://pair…)`; manual entry always available | `OnboardingScreens.kt:110-207` | camera preview/analyzer cannot run under Robolectric without camera emulation; QA Setup step 2 scans real QRs | device-only |
| OB-4 | Camera permission denied UI: `Se necesita la cámara para escanear el QR` + `Conceder permiso` + `Escribir URL y código` | `app/.../onboarding/OnboardingScreens.kt:141-156` | permission dialog needs a real system grant; QA Setup step 3 | device-only |
| OB-5 | Permissions screen (Android 13+): `POST_NOTIFICATIONS` request; granted state `Permiso concedido ✓` else `Permitir notificaciones`; battery exclusion intent; Continue and `Ahora no` both set `onboardingDone` | `OnboardingScreens.kt:222-281` | `OnboardingParityTest::OB-5 permissions screen exposes notification and battery state and Continue persists completion` (UI state + persistence pinned; ActivityResult launches device-only per QA Setup step 3/N14) | proof-added |
| OB-6 | Pre-condition `< 33` marks notifications granted without dialog; `Build.VERSION.SDK_INT` guards both branches | `app/.../onboarding/OnboardingScreens.kt:229-236` | `OnboardingParityTest::OB-6 below Android 13 notifications are marked granted without a permission prompt and skip persists completion` (API 34 shadow; deterministic variant uses an IO-dispatcher DataStore read; the JVM DataStore load stalls >60s on the JVM DataStore `data$` collector with concurrent gradle loads on some hosts) | partial |
| OB-7 | Navigation: Onboarding → Scan/Pair → Permissions → Agents; resume after sign-out returns to onboarding | `app/.../nav/RpRoot.kt:101-116,224-245` | the sign-out branch still needs an end-to-end RpRoot render/navigation test; the D-1 route-access defect is fixed | gap-with-reason |

## E. Navigation & shell (RpRoot / MainViewModel / theme motion)

| ID | Behavior | Android source | Proof | Status |
|---|---|---|---|---|
| NV-1 | Typed routes: Onboarding/Scan/Pair/Permissions/Agents/Archived/Mesh/Pending/Settings/Chat/NewAgent/Devices/Machines/AddMachine/NotificationSettings/Security/Battery/About | `shared-ui/.../nav/Routes.kt:5-62`, `app/.../nav/RpRoot.kt:224-294` | `NavModelsTest::every route serializes and deserializes` covers all 18 route types; `MeshNavigationTest::bottom bar…` covers the 4 top-level routes; detail screens render via their own tests | proven |
| NV-2 | Bottom bar visible only on the 4 top-level routes; hidden elsewhere; tabs AGENTS/MESH/PENDING/SETTINGS with `pending` badge; save/restore state on switch | `app/.../nav/RpRoot.kt:99-101,334-360` | `RpRootParityTest::NV-2 bottom tabs select the four typed top level routes in order` | proof-added |
| NV-3 | Start destination logic: no session → Onboarding; session but `!onboardingDone` → Permissions; else Agents | `app/.../nav/RpRoot.kt:98-103` | still lacks a production-shaped RpRoot render test; D-1 route accessibility is fixed | gap-with-reason |
| NV-4 | Session lost (logout/revocation) pops everything back to Onboarding | `app/.../nav/RpRoot.kt:108-116` | still lacks an end-to-end session-loss navigation test; D-1 route accessibility is fixed | gap-with-reason |
| NV-5 | `NavTarget.Pair` deep link: unpaired → PairRoute; paired → replace-session dialog `Reemplazar sesión actual` + `Continuar`/`Cancelar` | `app/.../nav/RpRoot.kt:121-126,302-323` | still lacks an end-to-end deep-link/dialog render test; D-1 route accessibility is fixed | gap-with-reason |
| NV-6 | `NavTarget.Agent` → ChatRoute; `NavTarget.Prompt` → ChatRoute(agent) + openPrompt sheet | `app/.../nav/RpRoot.kt:130-146` | still lacks an end-to-end deep-link/chat/prompt render test; D-1 route accessibility is fixed | gap-with-reason |
| NV-7 | Spring/tween transitions: `RpMotion.tabEnter/tabExit/detailEnter/detailExit/detailPopEnter/detailPopExit`; reduced-motion → `EnterTransition.None`/`ExitTransition.None` (ANIMATOR_DURATION_SCALE=0; U7) | `RpRoot.kt:141-199`, `shared-ui/.../theme/Motion.kt` | `MotionTest::adaptiveSpec…` | proven |
| NV-8 | Bottom bar hide/show animations: slide 280/240 ms + fade 200/160 ms, FastOutSlowInEasing | `RpRoot.kt:125-134` | golden coverage only (golden - unverified on this host) | partial |

## F. Theme system (theme/)

| ID | Behavior | Android source | Proof | Status |
|---|---|---|---|---|
| TH-1 | RpTheme exposes choice, typography, tokens; immediate theme change from Settings | `shared-ui/.../theme/Theme.kt:251-285` | goldens | proven |
| TH-2 | Palettes exact spot colors preserved (cute, sexy, omarchy light/dark, Material You default) | `Theme.kt` palettes | `ThemeTest` 4 palette tests | proven |
| TH-3 | `RpColors` semantic colors preserved | `Theme.kt` | `ThemeTest::colores semanticos…` | proven |
| TH-4 | Skeleton tokens keep contrast on dark themes | `Theme.kt` | `ThemeTest::tokens de skeleton…` | proven |
| TH-5 | Reduced-motion detection honors `ANIMATOR_DURATION_SCALE=0` | `shared-ui/.../theme/Motion.kt` + `rememberIsReducedMotion` | `MotionTest` (2 tests) | proven |

## G. Platform ports required (for O1 + orchestrator; nothing invented yet)

| ID | Behavior | Android source | iOS equivalent (T6 design) | Status |
|---|---|---|---|---|
| G-1 | QR scanning (CameraX + ML Kit) | `OnboardingScreens.kt:110-207` | AVFoundation `AVCaptureMetadataOutput` QR | device-only |
| G-2 | Camera permission request | `OnboardingScreens.kt:132-141` | `AVCaptureDevice.authorizationStatus` | device-only |
| G-3 | POST_NOTIFICATIONS + notificationsEnabled check | `SettingsViewModel.kt:68-77` | UNUserNotificationCenter authorization | device-only |
| G-4 | Battery optimization check + intent | `OnboardingScreens.kt:661-676`, `SettingsScreens.kt:244-253` | iOS none (background-modes settings copy only) | device-only |
| G-5 | Clipboard copy | `SettingsScreens.kt:158-161`; Mesh copy buttons | Compose Multiplatform `LocalClipboard`; no port needed | out-of-slice |
| G-6 | Window size class / fold posture | not yet used by Android UI | `currentWindowAdaptiveInfo` (material3-adaptive). SPIKE DONE 2026-11-06 on throwaway branch spike/adaptive @ d7c546d (nothing merges while Gate held): coordinate `org.jetbrains.compose.material3.adaptive:adaptive:1.3.0` RESOLVES for iosSimulatorArm64 (23 variants incl. iosSimulatorArm64ApiElements-published; transitive `org.jetbrains.androidx.window:window-core` is KMP), `currentWindowAdaptiveInfo()` compiles in :shared-ui commonMain — `:shared-ui:compileKotlinIosSimulatorArm64` BUILD SUCCESSFUL in 2m50s. Earlier probe with the invented coordinate 1.12.2 did not resolve (rejected). Negative outcome also recorded; T6 will depend on 1.3.0 via a catalog entry in its own commit. | out-of-slice |
| G-7 | STATUS/BARS insets (safe areas, battery) | shared-ui compose already handles insets via Compose | supported by CMP | proven |
| G-8 | STATUS bar / dark system bar icons | activity theme (O2-owned Application layer) | CMP-supported; flagged for O2 | out-of-slice |
| G-9 | BuildConfig version/flavor | `SettingsScreens.kt:261-266` | `PlatformInfo` port via NSBundle | out-of-slice |

## H. Adaptive plan (S13; plan only, not yet implemented)

Compact <600dp; medium 600-840dp; expanded >840dp (fold posture via `WindowInfoTracker`). Agents: list-detail on medium+ (list left, detail right, select-navigate fallback on compact). Mesh: canvas + filter row on compact; side sheet for node details on medium; expanded gets canvas left 70% + machine/group selector and live strip as a right rail, Recentrar stays top-left. Navigation: bottom bar on compact; NavigationRail on medium; NavigationSuite on expanded. Sheets: `ModalBottomSheet` on compact and medium, side pane on expanded (same rule for O3's shared sheets via one `AdaptivePaneHost`). Foldable: unfolded via width classes; vertical hinge splits list-detail when `FoldingFeature.State.FLAT`. Theme stays side-agnostic; new adaptive goldens only for new layouts and only with orchestrator approval.

## I. iOS signing plumbing to take (S12 rules; plan only, apply after Gate A approval)

| Item | Source per S12 (pbxproj has no entitlements reference in either source) | Exact commands once approved |
|---|---|---|
| `iosApp/iosApp/iosApp.entitlements` (keychain-access-groups `$(AppIdentifierPrefix)$(CFBundleIdentifier)`) | `feat/ios-real-ui` | `git -C /Users/drift/Projects/J3pf/Rp-app/.worktrees/o4 show feat/ios-real-ui:iosApp/iosApp/iosApp.entitlements > iosApp/iosApp/iosApp.entitlements` |
| `CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements` in `iosApp/Configuration/Config.xcconfig` | same | append the one-line assignment to `Config.xcconfig` |
| `alwaysOutOfDate = 1` on the "Compile Kotlin Multiplatform Framework" shell phase in `project.pbxproj` | `stash@{0}` | hand-apply this single buildPhase attribute (no checkout/merge/cherry-pick of code) |
| Nothing else | — | Forbidden: Kotlin/Swift from any iOS ref; merge/cherry-pick/stash pop; CI signing already on main stays as-is |

APNs push later needs a regenerated provisioning profile in Apple portal (S12/L2) — orchestrator step, workers flag only.

## K. Missing rows added per orchestrator verdict (2026-11-06, item 4) + D-1

| ID | Behavior | Android source | Proof | Status |
|---|---|---|---|---|
| MS-1 | Accessibility QA U8: interactive elements carry Spanish `contentDescription`, touch targets >= 48x48dp (~38 contentDescription uses across the slice) | shared-ui components + AgentsScreen/ArchivedScreen/OnboardingScreens | grep-level: 38 contentDescription call sites; touch targets covered indirectly by goldens (golden - unverified on this host) | partial |
| MS-2 | Micro-interactions QA U6: working-agent pulse, press-scale feedback, spring screen transitions | `shared-ui/.../theme/Motion.kt`, `RpRoot.kt` | `MotionTest` pins adaptiveSpec; pulse/scale are visual only | proven |
| MS-3 | Sign-out QA P8: logout clears credentials, DB and channels; app returns to onboarding | `app/.../settings/SettingsViewModel.kt:221-224` | handler invokes `SessionRepository.logout()`; the session-losing transition still needs an end-to-end RpRoot render/navigation test (D-1 route accessibility is fixed) | gap-with-reason |
| MS-4 | Chat font scale and message spacing preserved from settings (CHANGELOG 0.5.0) | `app/.../settings/SettingsScreens.kt:37-38`, `SettingsViewModel.kt:133-134` | `SettingsActionsParityTest::chat font and message spacing labels and choices retain exact Spanish copy` + persistence | proof-added |
| MS-5 | Predictive back support (CHANGELOG 0.5.0) | `MainActivity.kt` / manifest attrs | Application layer is O2-owned; row flagged for O2 | out-of-slice |
| MS-6 | Floating connection banner (CHANGELOG 0.5.0) | `RpRoot.kt` + `MainViewModel.connState` | banner composable lives in shared surfaces; conn state pinned at agent level; exact banner text unpinned | partial |
| MS-7 | App lock / biometrics on open (QA P7) | `MainActivity.kt` + `SecurityContent` | MainActivity is O2/Application surface; composable preferences pinned by SE-12 test | partial |
| MS-8 | 8-char pairing code pairing UI (INSTALACION) | `OnboardingModels.kt`, `OnboardingContent.kt` | **DEFECT (as-is record):** `PairContent` does not validate the code length (no 8-char constraint); no production fix in T1 | defect |
| MS-9 | QA P1/P2 chip-row discrepancy resolved: current UI has no legacy PC chip row `Todas/Empresa/Personal` — `MachineHeroDropdown` (F3.1) is the selector; P2 color editing lives in MachinesContent | `AgentsContent.kt`, `MachinesContent` | current code as source of truth; QA.md is stale for P1 chips | proven |
| MS-10 | Quiet hours per PC (QA P6) and test notification (QA P9) | `SettingsViewModel.kt:184-190,212-219`, `NotificationSettingsContent` | `SettingsActionsParityTest::SE-9…` (quiet hours, criticalAlways, per-machine categories) + `::SE-10…` | proof-added |
| D-1 | **Route-access fix implemented; RpRoot render proof still missing (T6.2):** `ArchivedAgentsRoute` was a private `@Serializable` data object in `RpRoot.kt`, causing serializer inspection to throw `IllegalAccessException` in a production-shaped render attempt. It is now a public serializable declaration in shared `Routes.kt`; `RpRoot` uses that shared declaration and has no private duplicate/import. `NavModelsTest::every route serializes and deserializes` round-trips this route among all 18 types, and shared metadata plus Android host compilation pass. No production-shaped RpRoot render was run after the change, so the original render failure is not yet proven resolved. `RpRootParityTest` remains NV-2-only and its historical comment about the old private-route failure is stale (test file is outside this move's edit surface). | `shared-ui/.../nav/Routes.kt:23-24`, `shared-ui/.../nav/NavModelsTest.kt`, `app/.../nav/RpRoot.kt:71,247-285` | `NavModelsTest::every route serializes and deserializes` (18 routes); `:shared-ui:compileCommonMainKotlinMetadata` + `:app:compileDirectDebugKotlin`; production-shaped post-change RpRoot render not run | partial |

| D-2 | **DEFECT (pre-existing on main @ 482cbb9, found by orchestrator thread dump 2026-11-06 10:12:41):** lock-order inversion between mesh `subscriptionLock` and the socket lock — `MeshRepository.subscribe` (MeshRepository.kt:132) holds `subscriptionLock` while calling `sendSubscribe` (:169) → `RelayConnection.send` (:117) → `ReconnectingSocket.send` (:57) → `ReconnectingSocketPolicy.send` (:72) → `RelayLock.withLock` waiting for the socket lock, while the `OkHttp` ws thread holds that socket lock inside `ReconnectingSocketPolicy$Listener.onOpen` and drives a StateFlow collector into `MeshViewModel.applyInputs` (:294) → `subscribeForCurrentMachine` (:411) → `MeshRepository.subscribe` (:127) waiting for `subscriptionLock`. Opening the Mesh screen while the socket reconnects can freeze the main thread (ANR). Fix scheduled as its own task after Gate A (test-first latch repro + independent verify; concurrency = high-risk item 4). Evidence: unmodified `MeshViewModelTest::screen visibility subscribes and unsubscribes` (MeshViewModelTest.kt:195) under load. **RESOLVED in T6.1** (commit below; not part of T1 scope): MeshRepository.kt now computes the subscription decision under `subscriptionLock` and sends the envelope AFTER releasing it (all three sites: subscribe, unsubscribe/unsubscribe-path, Connected-observer); regression pinned by `MeshSubscriptionLockOrderTest` (fork-isolated ThreadMXBean cycle probe, RED=fork exit 3 pre-fix with verbatim cycle dump, GREEN=fork exit 0 x3 solo runs; `MeshViewModelTest` 10/0 no hang with --rerun-tasks). | `MeshRepository.kt:132,169`, `MeshViewModel.kt:294,411`, `ReconnectingSocketPolicy.kt:72,129`, `RelayLock.kt:6` | verbatim jcmd thread dump from the orchestrator (2 stacks, embedded below) | defect |

Verbatim stacks (orchestrator jcmd, 2026-11-06 10:12:41):


```
// thread 1 (main thread, waits on the SOCKET lock inside RelayLock.withLock):
"SDK 34 Main Thread @coroutine#5636" #39 … waiting for monitor entry
  at dev.j3pf.rp.client.ws.RelayLock.withLock(RelayLock.kt:6)
  - waiting to lock <0x000000079a575768> (a java.lang.Object)
  at dev.j3pf.rp.client.ws.ReconnectingSocketPolicy.send(ReconnectingSocketPolicy.kt:72)
  at dev.j3pf.rp.data.net.ReconnectingSocket.send(ReconnectingSocket.kt:57)
  at dev.j3pf.rp.data.net.RelayConnection$core$2.invoke(RelayConnection.kt:75)
  at dev.j3pf.rp.client.ws.RelayWsClient.send(RelayWsClient.kt:23)
  at dev.j3pf.rp.data.net.RelayConnection.send(RelayConnection.kt:117)
  at dev.j3pf.rp.data.repo.MeshRepository.sendSubscribe(MeshRepository.kt:169)
  at dev.j3pf.rp.data.repo.MeshRepository.subscribe(MeshRepository.kt:132)
  - locked <0x000000079a43ddd0> (a java.lang.Object)
  at dev.j3pf.rp.ui.mesh.MeshViewModel.subscribeForCurrentMachine(MeshViewModel.kt:411)
  at dev.j3pf.rp.ui.mesh.MeshViewModel.onScreenVisible(MeshViewModel.kt:73)
  at dev.j3pf.rp.ui.mesh.MeshViewModelTest$screen visibility subscribes and unsubscribes$1.invokeSuspend(MeshViewModelTest.kt:199)

// thread 2 (okhttp ws thread, holds the socket lock and waits for subscriptionLock):
"OkHttp http://localhost:54560/... @coroutine#5627" #672 … waiting for monitor entry
  at dev.j3pf.rp.data.repo.MeshRepository.subscribe(MeshRepository.kt:127)
  - waiting to lock <0x000000079a43ddd0> (a java.lang.Object)
  at dev.j3pf.rp.ui.mesh.MeshViewModel.subscribeForCurrentMachine(MeshViewModel.kt:411)
  at dev.j3pf.rp.ui.mesh.MeshViewModel.applyInputs(MeshViewModel.kt:294)
  …combine…
  at dev.j3pf.rp.client.ws.ReconnectingSocketPolicy$Listener.onOpen(ReconnectingSocketPolicy.kt:129)
  at dev.j3pf.rp.data.net.OkHttpSocketDriver$connect$1.onOpen(OkHttpSocketDriver.kt:25)
  at okhttp3.internal.ws.RealWebSocket.loopReader(RealWebSocket.kt:314)
```

## L. Mesh (T1b) — README v0.4-0.6 + QA §5/§8 + docs/MESH.md

| ID | Behavior | Android source | Proof | Status |
|---|---|---|---|---|
| ME-1 | Name resolution: node.name → cwd basename → `Agente`; raw ids, "Agent"/"Mesh Group" sentinels, `(2)` suffix preserved | `shared-ui/.../mesh/MeshFormatters.kt:64-96` | `MeshFormattersTest::formatMeshDisplayName filters out raw IDs…` + `::formatMeshDisplayName and group chip handle relay 0_5_3 sentinels…` | proven |
| ME-2 | Duplicate disambiguation: cross-machine `Worker (Omarchy)` / same-machine `Worker (Omarchy · 1)`/`(Omarchy · 2)` | `MeshFormatters.kt:43-62` | `MeshFormattersTest::formatMeshDisplayName disambiguates duplicates` | proven |
| ME-3 | Group chip labels: solo `Sin grupo`, orchestrator display name, Mesh Group sentinel fallback | `MeshFormatters.kt:105-130` | `MeshFormattersTest::formatMeshGroupChipLabel keeps solo and Sin grupo sentinels` + `::falls back to orchestrator…` | proven |
| ME-4 | Reingold-Tilford layout: same depth = same row, orchestrator row above, bounding-box collision avoidance 1-12 agents, multiroot rows | `shared-ui/.../mesh/layout/MeshLayoutEngine.kt` | `MeshViewportRenderTest` (fits/landscape/tablet/deep-tree/multiroot/1/4/12 on 360dp) + `MeshViewModelTest::snapshot calculates layout…` | proven |
| ME-5 | Viewport: auto-fit min 0.65, 1.0 in landscape ≥600dp, zoom clamp 0.4x-2.5x (`MIN_ZOOM`/`MAX_ZOOM`), top-anchor + 16dp, clipToBounds under filters; QA M6's `0.5x-3.0x` is stale | `MeshCanvas.kt:203-214,619,664-665`, `MeshContent.kt:71-74` | visual pins in `MeshViewportRenderTest::diagram card legibility…`; clamp constants are module-private | partial |
| ME-6 | Recentrar button exact label `Recentrar organigrama`, wired to the recenter trigger | `MeshContent.kt:58,74,100` | `MeshScreenTest::recenter action button is displayed and clickable` | proven |
| ME-7 | Machine/group chips filter nodes+edges, persist selection, most-active default group, recalculated on machine change | `app/.../ui/mesh/MeshViewModel.kt` | `MeshViewModelTest::machine and group selections filter layout nodes and edges and persist` + `::preselection chooses group…` (QA F5.3 covered) | proven |
| ME-8 | Node detail sheet: avatar/name/role/machine/branch/model/status/activity/todos/subagents; role badge only for orchestrator; raw IDs only in collapsed `Detalles técnicos` with copy → `Copiado` toggle | `shared-ui/.../mesh/MeshNodeDetailSheet.kt:45+,219-242,278-313` | `MeshScreenTest::tapping node opens node detail sheet` + `::subordinate card gives its header width…`; the copy toggle itself untested | partial |
| ME-9 | Conversation sheet: direction-ordered history, `Cargar mensajes anteriores` pagination via `nextBefore`, opens without edge (F5.2) | `MeshConversationSheet.kt:72-79`, `MeshViewModel.kt` | `MeshScreenTest::conversation sheet localizes…` + `MeshViewModelTest::clicking edge loads conversation and pages older items with nextBefore` + `::onOpenConversation opens conversation even when no edge…` | proven |
| ME-10 | Live strip: latest comms `Sender → Receiver: text`, collapsible | `MeshContent.kt`, `MeshViewModel.kt` | `MeshViewModelTest::incoming comm updates latest-first live strip and active bubbles` | proven |
| ME-11 | Disconnected banner `Desconectado — mostrando datos locales` + `Reintentar`, topology stays visible; list-view toggle `Vista de lista`/`Vista de diagrama` accessible (QA M11/M12) | `MeshContent.kt:118,133-138` | `MeshScreenTest::disconnected banner shows stale data and retry action` + `::toggle to list view displays nodes accessibly` | proven |
| ME-12 | Relay errors: 10s WS timeout → GET /v1/mesh fallback; 404 copy `Tu relay no soporta la Red de agentes…` with Reintentar; generic network error; recovery from error state (QA F2.2) | `app/.../ui/mesh/MeshViewModel.kt` | `MeshScreenTest::404 error shows unsupported relay message…` + `::network error shows generic message…` + `::retry recovery…`; data-level `RelayApiMeshTest`/`MeshRepositoryTest` | proven |
| ME-13 | Subagent nodes/leaf placement: `running`/`working` → own node one level below; everything else → compact pills; unknown status stays a pill; nodeId deterministic and never leaks the raw taskId | `shared-ui/.../mesh/layout/SubagentPlacement.kt:20-49` | `shared-ui/jvmTest/.../mesh/SubagentPlacementTest.kt` (4 tests) | proven |
| ME-14 | Motion: comm particles orthogonal bus routes, pendingAsk dashed pulse, shimmer, running-subagent spinners, active edge glow ≤20s, idle calm; reduced motion / ANIMATOR_DURATION_SCALE=0 disables particles/shimmer/pulses (QA M13) | `MeshCanvas.kt:67-113,342-346,397+,619` | `MotionTest` pins adaptiveSpec; particle/shimmer visuals only golden-covered (golden - unverified on this host) | partial |
| ME-15 | TalkBack: `X, reporta a Y` semantics on subordinate leaf items (QA M12) | `MeshLeafItem.kt` via `formatMeshNodeReportsTo` (`MeshFormatters.kt:173`) | `MeshScreenTest::TalkBack hierarchy semantics include reporta a…` | proven |
| ME-16 | Subscription lifecycle: subscribe on visible, `mesh.unsubscribe` on background, resubscribe ON_START, reference-counted ownership with lastSubscribedMachineId (QA M14/F2.3) | `app/.../ui/mesh/MeshViewModel.kt`, `MeshScreen.kt` | `MeshViewModelTest::screen visibility subscribes and unsubscribes` | proven |
| ME-17 | Tolerant decoding: decimal `startedAt`, malformed subagents/nodes skipped, valid items intact; undecodable root → MeshErrorState+retry (QA F4.1-F4.3) | protocol `Tolerant*Serializer` + VM | `MeshViewModelTest::retry recovery…` + `RelayApiMeshTest`/`MeshRepositoryTest` | proven |
| ME-18 | Device-locale timestamps in conversation sheets (QA M16) | `MeshFormatters.kt` + `PlatformTime` | `MeshFormattersTest::formatters localize mesh roles statuses actions and hidden content` | proven |
| ME-19 | Compact landscape filters share one row < 500dp available height, graph clipped below (QA M15) | `MeshContent.kt:71-74` | `MeshScreenTest::compact landscape filters share a row…` | proven |
| ME-20 | Quota bubbles stay 24dp clear of sender/receiver cards | `shared-ui/.../mesh/MeshCanvasGeometry.kt:5-33` | `MeshScreenTest::communication bubble center stays 24dp clear…` | proven |

## M. Status summary (computed by script from the table above)

Counts across sections A-L and G: total 95 rows — proven=38, proof-added=22, partial=11, gap-with-reason=8, device-only=7, out-of-slice=6, defect=2, D-2=1.
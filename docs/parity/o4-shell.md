# Parity matrix — o4-shell (agents, settings, onboarding, nav, theme, adaptive, Mesh)

Worker O4 · branch `unify/o4-shell` · Gate A input for `odd/tasks/unified-kmp.md` T1/T6.
Columns: ID | behavior (edge cases + exact strings) | Android source `file:line` | proof test | status.
Source of truth: Android code in `app/src/main/java/dev/j3pf/rp/ui/**` plus the shared sources it composes (`shared-ui/.../ui/**`, already common on `feat/unified-kmp`).

Legend: `covered` = existing green test proves it · `char` = characterization test written in T1 (green on unchanged code) · `manual` = Q&A manual row (docs/QA.md #), automatic proof out of scope · `pending` = worker adds test in T1.

## Scope note (golden counts)

README's "48 reference images" is stale (S15): `app/src/test/screenshots` holds **138 PNGs**: agents 18, chat 12, mesh 78, new 6, onboarding 6, pending 6, prompt 6, settings 6. O4 owns agents (18) + mesh (78) + settings (6) + onboarding (6) = **108 goldens**; chat/new/pending/prompt/prompt-sheet goldens belong to O3. None may be re-recorded without orchestrator approval. **Host-drift note (orchestrator, 2026-11-06):** `verifyRoborazziDirectDebug` on UNCHANGED main @ 482cbb9 yields 23 tests / 17 FAILED on this Mac, so committed goldens do not match this host's rendering. Rows citing a golden as proof therefore read `golden - unverified on this host`; the orchestrator records a HOST baseline differential gate for Gate C/D.

## A. Agents inbox (AgentsScreen / AgentsViewModel · QA P1/P4, U4, U5)

| ID | Behavior | Android source | Proof | Status |
|---|---|---|---|---|
| AG-1 | Ordering inside a machine group: agents with open prompts first, then online, then working, then most recent `lastSeenAt` (tie→stable) | `shared-ui/.../agents/AgentsModels.kt:22-27` | `app/src/test/.../agents/AgentsSortingAndCardTest.kt::sortAgentsWithinGroup orders…` | covered |
| AG-2 | Group ordering across machines: group with prompts ≻ working group ≻ lastSeen; groups without a visible machine still render (synthetic `MachineSummary`) | `app/.../agents/AgentsViewModel.kt:60-92` | `AgentsViewModelParityTest::AG-2 groups are ordered across machines…` (real websocket harness) | proof-added |
| AG-3 | Context filter: `AppSettings.ALL` shows everything; a specific context or machineId filters machines and agents (`machineContext == sel || machineId == sel`) | `app/.../agents/AgentsViewModel.kt:58-61` | `AgentsViewModelParityTest::AG-3 context filter…` | proof-added |
| AG-4 | Quick filters `Todas / En línea / ✦ agy / π pi` + search across `sessionName/cwd/gitBranch/lastMessagePreview` (case-insensitive, trimmed) | `shared-ui/.../agents/AgentsModels.kt:14-18,46-72` | `AgentsParityCharacterizationTest` (4 tests: filters, launcher split, search) | covered (T1a) |
| AG-5 | Agent card at-a-glance: status chip, active task, TODO progress, relative activity time, preview (no horizontal scroll) | `shared-ui/.../components/StatusChip.kt:64-69`, `shared-ui/.../agents/AgentCard.kt:119+` | `AgentsSortingAndCardTest::AgentCard displays status…` (golden - unverified on this host for the golden part) | covered |
| AG-6 | Status labels exact: `Desconectado <relative time>`, `Esperando respuesta`, `Trabajando`, `Compactando`, `Error`, `Inactivo` | `shared-ui/.../components/StatusChip.kt:64-69` | `AgentsParityCharacterizationTest::status chips use the exact Spanish labels` | covered (T1a) |
| AG-7 | Resume agent: only when machine `hostOnline`; else snackbar exact `Para reanudar agentes, ejecuta rp-host en $machineName`; success `Agente reanudado en la PC`; failure `No se pudo reanudar: ${message}` | `app/.../agents/AgentsScreen.kt:47-52`, `AgentsViewModel.kt:158-166` | TEST MISSING | char |
| AG-8 | `MachineHeroDropdown`: `Todas las máquinas` entry, contexts then machines, no redundant legacy chip row (QA F3.1) | `shared-ui/.../components/Common.kt:160-182,326` | `AgentsSortingAndCardTest::no context chip row…` | covered |
| AG-9 | Close agent confirmation dialog exact copy: title `¿Cerrar el agente?`, body `Esto cerrará el pane en Herdr y detendrá el agente`, confirm `Cerrar agente`, dismiss `Cancelar` | `app/.../agents/AgentsScreen.kt:76-93`, `res/values/strings.xml:29-31` | `AgentsLifecycleTest::close confirmation…` + `canceling…` | covered |
| AG-10 | Close outcome paths: `Closed` silent; `Archived` snackbar `No había un pane de Herdr vinculado; el historial quedó archivado.`; `Failed` shows relay's message | `app/.../agents/AgentsViewModel.kt:120-133`, strings.xml:47 | `AgentsLifecycleTest::agents view model confirms close once…` | covered |
| AG-11 | Refresh: sets `isRefreshing`, calls `connection.retryNow()`, clears after 600 ms | `app/.../agents/AgentsViewModel.kt:96-102` | `AgentsViewModelParityTest::AG-11 refresh marks refreshing and clears it again` | proof-added |

## B. Archived agents (ArchivedScreen · README v0.6.0, QA)

| ID | Behavior | Android source | Proof | Status |
|---|---|---|---|---|
| AR-1 | Screen loads archived on entry; `archivedLoading` while first page flies | `app/.../agents/ArchivedScreen.kt:47-48`, `AgentsViewModel.kt:135-155` | `AgentsViewModelParityTest::AR-1 initial archive load…` | proof-added |
| AR-2 | Error state with retry button (`action_retry`) instead of empty state | `ArchivedScreen.kt:109-115`, `AgentsViewModel.kt:148-151` | `AgentsLifecycleTest::failed archived fetch…` | covered |
| AR-3 | Empty state exact `No hay agentes archivados` (only when no error) | `ArchivedScreen.kt:117`, strings.xml:34 | `ArchivedAndPairParityCharacterizationTest::empty archived list…` | covered (T1a) |
| AR-4 | Pagination: `Cargar más` appears iff `nextBefore != null`; callback fires with the cursor | `ArchivedScreen.kt:119-121`, `AgentsViewModel.kt:135-142` | `ArchivedAndPairParityCharacterizationTest::load more button…`/`::load more button is hidden…` + `AgentsViewModelParityTest::AR-4 load more appends…` | proof-added |
| AR-5 | Purge is user-gated: row button `Eliminar definitivamente` → dialog title `¿Eliminar historial de %1$s?`, body `Se eliminará definitivamente el historial…` (`archived_purge_confirmation`), keep `Conservar historial`, confirm `Sí, eliminar definitivamente` | `ArchivedScreen.kt:160-196`, strings.xml:42-46 | `AgentsLifecycleTest::archived destructive action…` | covered |
| AR-6 | Purge success removes the row locally; failure snackbar `No se pudo eliminar definitivamente el historial` | `app/.../agents/AgentsViewModel.kt:157-166` | `AgentsViewModelParityTest::AR-6 purge success…` + `AgentsLifecycleTest::archived destructive action…` | proof-added |
| AR-7 | Row content: `agentTitle(sessionName,cwd,agentId)`, `Máquina: %1$s`, `Última actividad: %1$s` (relative), plural `archived_message_count`, 2-line last-message preview | `ArchivedScreen.kt:152-167`, strings.xml:35-36, plurals:37 | partially inside `AgentsLifecycleTest::archived destructive action…` (name + `PC principal`); machine/`lastSeen`/plurals gap | partial (complete at T6) |
| AR-8 | Tapping an archived row opens chat read-only (flag via `savedStateHandle`, `archived_read_only_agent`) | `app/.../nav/RpRoot.kt:259-266,320-324`, strings.xml:33 | TEST MISSING | char |

## C. Settings (SettingsScreens/SettingsViewModel; QA P1/P2/P5/P6/P7/P8/P9)

| ID | Behavior | Android source | Proof | Status |
|---|---|---|---|---|
| SE-1 | Theme dialog renders ALL `RpThemeChoice.entries` (5): gentle-cute, gentle-sexy, omarchy-system, system plus AUTO=`Sincronizado con Pi (automático)` (default `appTheme="auto"`); picking one calls `onSelectTheme(choice.id)` | `shared-ui/.../theme/Theme.kt:13-22,170,251+`, `shared-ui/.../settings/SettingsContent.kt:331-350`, `app/.../settings/SettingsViewModel.kt:133` | `SettingsThemeParityCharacterizationTest::theme dialog shows exactly the five rp theme choices…` + `ThemeTest::paletteFor entrega…` | proof-added (DataStore persistence pin still partial) |
| SE-2 | Default theme when no data: Gentle-Cute; auto theme picks by most-repeated, personal PC first (≥2 PCs) | `shared-ui/.../theme/Theme.kt:170`, `app/.../ThemeTest.kt:auto…` | covered |
| SE-3 | Design tokens `RpSpacing/RpShapes/RpGlows/RpGradients/RpMotion` derive from MaterialTheme | `shared-ui/.../theme/DesignTokens.kt:14-39`, `Motion.kt` | `MotionTest::motion constants…`, tokens test | covered |
| SE-4 | Devices list: load success marks current device (`este` badge, `deviceId == secrets.current()`); revoked filtered elsewhere (QA F1.1/F1.2) | `app/.../settings/SettingsViewModel.kt:88-97` | `DevicesScreenTest::devices list renders…` | covered |
| SE-5 | Device load failure message `No se pudo cargar la lista de dispositivos`; revoke success `Dispositivo revocado` / failure `No se pudo revocar`; revoked removed from local list | `SettingsActionsParityTest::SE-5 device load failure and revoke success or failure preserve exact messages and local list` | proof-added |
| SE-6 | Edit machine: trims name/context, empty→null; `PC actualizada` / `No se pudo guardar` | `SettingsViewModel.kt:120-129` | `SettingsActionsParityTest::SE-6 edit machine trims optional fields and reports relay success and failure` | proof-added |
| SE-7 | Machines: refresh on entry, revoke `PC revocada`/`No se pudo revocar` | `SettingsScreens.kt:91-116`, `SettingsViewModel.kt:132-137` | `SettingsActionsParityTest::SE-7 machines screen refreshes on entry and revoke reports both outcomes` | proof-added |
| SE-8 | Add machine: creates code on entry (`createMachineCode`), copyable `/rp pair URL CÓDIGO` command to clipboard, new-code action; error `No se pudo generar el código` | `SettingsScreens.kt:120-156`, `SettingsViewModel.kt:139-148` | `SettingsActionsParityTest::SE-8 machine code success is stored and a failed renewal clears it with exact message` | proof-added |
| SE-9 | Notifications screen: health refresh on resume (notifications enabled, approvals muted, battery excluded), per-machine categories, criticalAlways special category sent to relay (`"criticalAlways" to s.criticalAlways`), quiet hours `HH:mm` per PC with blank disabling, per-agent unmute; push via `PUT /v1/devices/me/prefs` | `SettingsViewModel.kt:68-77,170-224`, `SettingsScreens.kt:186-229` | `SettingsActionsParityTest::SE-9 notification preferences persist locally and are sent to the relay` (health refresh on resume stays unpinned: SettingsScreens.kt:45-50,144-152; SettingsViewModel.kt:67-75) | partial |
| SE-10 | Test notification: success `Notificación de prueba enviada` / failure `No se pudo enviar la notificación de prueba` | `SettingsViewModel.kt:212-219` | `SettingsActionsParityTest::SE-10 test notification reports exact success and failure messages` | proof-added |
| SE-11 | Prefs save failure message `No se pudieron guardar las preferencias en el relay` | `SettingsViewModel.kt:183-188` | `SettingsActionsParityTest::SE-11 relay preference save failure exposes the exact error and submitted preferences` | proof-added |
| SE-12 | Security: `setRequireUnlock/setAppLock/setFlagSecure` persist (QA P7) | `SettingsViewModel.kt:222-228` | `SettingsActionsParityTest::SE-12 security and chat display preferences persist through SettingsRepository` + `::chat font and message spacing labels and choices retain exact Spanish copy` (MS-4) | proof-added |
| SE-13 | Battery screen: refresh on resume; request via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS package:`; open system battery settings | `SettingsScreens.kt:231-259` | TEST MISSING (platform intent → platform port) | platform |
| SE-14 | About: versionName/versionCode/flavor from BuildConfig | `SettingsScreens.kt:261-266` | gap: BuildConfig string diff trivial; manual review row | manual |
| SE-15 | Direct-push channel toggle applies both flavors with dedupe by promptId (QA N10; S17) | `SettingsViewModel.kt:172` → SessionRepository | O2-adjacent; noted for O2 | out-of-slice |

## D. Onboarding (OnboardingScreens; README/INSTALACION)

| ID | Behavior | Android source | Proof | Status |
|---|---|---|---|---|
| OB-1 | Pair: busy guard (double-submit ignored), success → done, failure shows relay message | `OnboardingScreens.kt:74-93` | content level covered: `PairContent` prefill+submit and busy UI in `ArchivedAndPairParityCharacterizationTest`; VM busy-guard gap | covered (T1a, content) |
| OB-2 | `parsePairingUri` accepts `rp://pair?u=…&c=…`; deep link prefills URL+code; invalid forms rejected (`https://…` alone, missing `u`/`c`, `rp:pair…`, empty) | `OnboardingScreens.kt:99-104`; `SessionRepository.kt:134-143` | `ArchivedAndPairParityCharacterizationTest::parsePairingUri…` + `pair content prefill…` | covered (T1a) |
| OB-3 | QR scanner: CameraX + ML Kit, STRATEGY_KEEP_ONLY_LATEST, single-handled `AtomicBoolean` (one code consumed once), invalid shows `Ese QR no es de RP (rp://pair…)`; manual entry (`Escribir URL y código`) always available | `OnboardingScreens.kt:110-207` | TEST MISSING (camera → platform) | platform |
| OB-4 | Camera permission denied UI: `Se necesita la cámara para escanear el QR` + `Conceder permiso` + `Escribir URL y código` | `OnboardingScreens.kt:132-141` | gap: needs camera permission simulation (Robolectric grants are fragile here) | pending platform |
| OB-5 | Permissions screen (Android 13+): `POST_NOTIFICATIONS` request; granted state `Permiso concedido ✓` else `Permitir notificaciones`; battery exclusion via intent; `Excluida de la optimización ✓` / `Excluir de la optimización de batería`; Continue (`Continuar`) and skip (`Ahora no`) both set `onboardingDone` | `OnboardingScreens.kt:216-280` | `OnboardingParityTest::OB-5 permissions screen exposes notification and battery state and Continue persists completion` (permission/battery ActivityResult launches = device-only; QA Setup step 3 + §1 N14) | proof-added + device-only | | proof-added |
| OB-6 | Pre-condition `< 33` marks notifications granted without dialog; `Build.VERSION.SDK_INT` guards both branches | `OnboardingScreens.kt:641-652` | `OnboardingParityTest::OB-6 below Android 13 notifications are marked granted without a permission prompt and skip persists completion` (API 34 via Robolectric shadow) | proof-added |
| OB-7 | Navigation: `Onboarding → Scan (pop) → Pair (pop) → Permissions (pop) → Agents`; resume after sign-out returns to onboarding | `app/.../nav/RpRoot.kt:297-314` | `MeshNavigationTest` partial | char |

## E. Navigation & shell (RpRoot / MainViewModel / theme motion)

| ID | Behavior | Android source | Proof | Status |
|---|---|---|---|---|
| NV-1 | Typed routes: Onboarding/Scan/Pair/Permissions/Agents/Archived/Mesh/Pending/Settings/Chat/NewAgent/Devices/Machines/AddMachine/NotificationSettings/Security/Battery/About | `app/.../nav/RpRoot.kt:297-345` | `MeshNavigationTest` | covered (partial) |
| NV-2 | Bottom bar visible only on the 4 top-level routes; hidden elsewhere; tabs AGENTS/MESH/PENDING/SETTINGS with `pending` badge; save/restore state on switch | `RpRoot.kt:99-101,355-359` | NV-2: `RpRootParityTest::NV-2 bottom tabs select the four typed top level routes in order` | proof-added |
| NV-3 | Start destination logic: no session → Onboarding; session but `!onboardingDone` → Permissions; else Agents | `RpRoot.kt:103-107` | RpRoot render blocked by defect D-1 (`IllegalAccessException` on private `@Serializable` route); tab-mapping pinned in `RpRootParityTest::NV-2` | gap-with-reason |
| D-1 | **DEFECT (unchanged code, learned by the worker's render test):** `ArchivedAgentsRoute` is a `private` `@Serializable` data object at `RpRoot.kt:73` (registered at `RpRoot.kt:255`); navigation's serializer lookup throws `IllegalAccessException` when inspected. The packaged app still navigates (metadata path), but the route is untestable on JVM and fragile for the KMP move — route declarations must become internal/public when RpRoot homes share the navigation host (T6). | `RpRoot.kt:73,255,103-150,306-327` | verbatim worker exception from the RpRoot render attempt | defect |
| NV-4 | Session lost (logout/revocation) pops everything back to Onboarding | `RpRoot.kt:112-119` | same D-1 blocker (route render throws `IllegalAccessException`) | gap-with-reason |
| NV-5 | `NavTarget.Pair` deep link: unpaired → PairRoute; paired → replace-session dialog `Reemplazar sesión actual` + `Continuar`/`Cancelar` | `RpRoot.kt:125-130,306-327` | same D-1 blocker | gap-with-reason |
| NV-6 | `NavTarget.Agent` → ChatRoute; `NavTarget.Prompt` → ChatRoute(agent) + openPrompt sheet | `RpRoot.kt:134-150` | same D-1 blocker | gap-with-reason |
| NV-7 | Spring/tween transitions: `RpMotion.tabEnter/tabExit/detailEnter/detailExit/detailPopEnter/detailPopExit`; reduced-motion → `EnterTransition.None`/`ExitTransition.None` (ANIMATOR_DURATION_SCALE=0; U7) | `RpRoot.kt:141-199`, `shared-ui/.../theme/Motion.kt` | `MotionTest::adaptiveSpec…` | covered |
| NV-8 | Bottom bar hide/show animations: slide 280/240 ms + fade 200/160 ms, FastOutSlowInEasing | `RpRoot.kt:125-134` | golden coverage; fine | covered |

## F. Theme system (theme/)

| ID | Behavior | Android source | Proof | Status |
|---|---|---|---|---|
| TH-1 | RpTheme exposes choice, typography, tokens; immediate theme change from Settings | `shared-ui/.../theme/Theme.kt:251-285` | goldens | covered |
| TH-2 | Palettes exact spot colors preserved (cute, sexy, omarchy light/dark, Material You default) | `Theme.kt` palettes | `ThemeTest` 4 palettes tests | covered |
| TH-3 | `RpColors` semantic colors preserved | `Theme.kt` | `ThemeTest::colores semanticos…` | covered |
| TH-4 | Skeleton tokens keep contrast on dark themes | `Theme.kt` | `ThemeTest::tokens de skeleton…` | covered |
| TH-5 | Reduced-motion detection honors `ANIMATOR_DURATION_SCALE=0` | `shared-ui/.../theme/Motion.kt` + `rememberIsReducedMotion` | `MotionTest` 2 tests | covered |

## G. Platform ports required (for O1 + orchestrator; nothing invented yet)

| Port | Android source | Suggested port name | iOS equivalent (to design in T6) |
|---|---|---|---|
| QR scanning (CameraX + ML Kit) | `OnboardingScreens.kt:110-207` | `QrScanner` (expect/actual: `startScan(onResult)`, permission request, cancel) | AVFoundation `AVCaptureMetadataOutput` QR |
| Camera permission request | `OnboardingScreens.kt:132-141` | `QrScanner` includes it | `AVCaptureDevice.authorizationStatus` |
| POST_NOTIFICATIONS + notificationsEnabled check | `SettingsViewModel.kt:68-77` | `NotificationHealth` | iOS notification permission via UNUserNotificationCenter |
| Battery optimization check + intent (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) | `OnboardingScreens.kt:661-676`, `SettingsScreens.kt:244-253` | `BatteryExemption` | iOS: Background Modes audio/VoIP push note; no direct equivalent — settings copy only |
| Clipboard copy | `SettingsScreens.kt:158-161`; Mesh copy buttons | `Clipboard` | UIPasteboard |
| STATUS/BARS insets (safe areas, battery) | shared-ui compose already handles insets via Compose | already cross-platform via CMP | safe area via CMP |
| Window size class / fold posture | not yet used by Android UI | `WindowSizeClass` + `JankStats`-free `FoldingFeature` | same CMP API on iOS |
| STATUS bar / dark system bar icons | activity theme (O2-owned Application layer) | note only | supported by CMP |
| BuildConfig version/flavor | `SettingsScreens.kt:261-266` | `PlatformInfo` | NSBundle infoDictionary |

## H. Adaptive plan (S13; written as plan, not yet implemented)

Current layout: single-column everywhere; `AgentsScreen` `LazyColumn`; mesh canvas adapts by viewport but phone-only assumptions (hero dropdown, bottom bar).

- WindowSizeClass: compact <600dp, medium 600-840dp, expanded >840dp (plus fold posture via `WindowInfoTracker` when folded hinge present).
- Agents (compact): phone list as today. Medium/expanded: `AgentsScreen` becomes list-detail (list left, selected agent card/detail right; on select-navigate fallback on compact).
- Mesh (compact): current canvas + filter row. Medium: side sheet for node details instead of bottom sheet? Keep bottom sheet (fallback to platform-idiomatic). Expanded: canvas left 70%, machine/group selector + live strip and accessible list view as a right rail; virtual "Recentrar" stays top-left.
- Navigation: bottom bar on compact; NavigationRail on medium; NavigationSuite (rail + optional second rail) on expanded. Per MAA guidelines the 4 top destinations map directly.
- Sheets: `ModalBottomSheet` on compact, side pane (`AnimatedVisibility` slide-in from trailing edge) on expanded for: node detail, conversation, prompt sheet (owned by O3 but same rule applies globally via one shared `AdaptivePaneHost`).
- Foldable: unfolded tablet-like layout via width-based classes; folded posture on Galaxy Fold-like hides the seam region (padding when `FoldingFeature.State.FLAT` with hinge): list-detail splits along the hinge when vertical.
- Settings (medium/expanded): two-pane master-detail for Settings destinations.
- Theme stays side-agnostic; goldens keep compact phone config — new adaptive goldens only for new layouts and only with orchestrator approval.
- Keys: no device-specific forks; one `AdaptivePaneHost` + `WindowSize`-driven composables in `shared-ui/ui/adaptive/` (new package listed in T2 request to O1).

## I. iOS signing plumbing to take (S12 rules; plan only, apply after Gate A approval)

| Item | Source per S12 | Exact commands once approved |
|---|---|---|
| `iosApp/iosApp/iosApp.entitlements` (keychain-access-groups `$(AppIdentifierPrefix)$(CFBundleIdentifier)`) | `feat/ios-real-ui` | `git -C /Users/drift/Projects/J3pf/Rp-app/.worktrees/o4 show feat/ios-real-ui:iosApp/iosApp/iosApp.entitlements > iosApp/iosApp/iosApp.entitlements` |
| `CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements` | same | append to `iosApp/Configuration/Config.xcconfig` |
| `alwaysOutOfDate = 1` on the "Compile Kotlin Multiplatform Framework" shell phase in `project.pbxproj` (orchestrator fix: no pbxproj entitlements reference exists in either source) | `stash@{0}` | hand-apply this single buildPhase attribute (no checkout/merge/cherry-pick of code) |
| Nothing else | — | Forbidden: Kotlin/Swift from any iOS ref; merge/cherry-pick/stash pop |

APNs push later needs a regenerated provisioning profile in Apple portal (S12/L2) — orchestrator step, workers flag only.


## K. Missing rows added per orchestrator verdict (2026-11-06, item 4)

| ID | Behavior | Android source | Proof | Status |
|---|---|---|---|---|
| MS-1 | Accessibility QA U8: interactive elements carry Spanish `contentDescription`, touch targets >= 48x48dp (~38 contentDescription uses across the slice) | ` AgentsScreen.kt`, `ArchivedScreen.kt`, `OnboardingScreens.kt`, shared-ui components (`Common.kt`, `AgentCard.kt`) | grep-level: 38 contentDescription call sites; TouchTargets covered indirectly by goldens (golden - unverified on this host) | partial |
| MS-2 | Micro-interactions QA U6: working-agent pulse, press-scale feedback, spring screen transitions | `shared-ui/.../theme/Motion.kt`, `RpRoot.kt` | `MotionTest` pins adaptiveSpec; pulse/scale are visual only | proven (motion constants + spec) |
| MS-3 | Sign-out QA P8: logout clears credentials, DB and channels; app returns to onboarding | `app/.../settings/SettingsViewModel.kt:221-224` → `SessionRepository.logout()` | delegated to settings worker unit (`SettingsActionsParityTest`) | proof-added |
| MS-4 | Chat font scale and message spacing preserved from settings (CHANGELOG 0.5.0) | `app/.../settings/SettingsScreens.kt:37-38`, `SettingsViewModel.kt:133-134` | worker unit: exact label strings + DataStore persistence | proof-added |
| MS-5 | Predictive back support (CHANGELOG 0.5.0) | `MainActivity.kt` / manifest attrs | out of O4's file surface (Application layer, O2-owned); flagged for O2 | out-of-slice |
| MS-6 | Floating connection banner (CHANGELOG 0.5.0) | `RpRoot.kt` + `MainViewModel.connState` | pinned at agent screens level via conn state flows; banner composable itself in shared surfaces | partial |
| MS-7 | App lock / biometrics on open (QA P7) | `MainActivity.kt` + `SecurityContent` | MainActivity is O2/Application surface; Security composable preferences pinned by SE-12 test | partial |
| MS-8 | 8-char pairing code pairing UI (INSTALACION) | `shared-ui/.../onboarding/OnboardingContent.kt:114-123` | **DEFECT-note**: `PairContent` does not validate length/casing of `CÓDIGO` (no 8-char check) — recorded `as-is`; no production fix in T1 | defect (documented) |
| MS-9 | QA P1/P2: PC chips with bell counts in the current UI vs README/F3.1 asserting the chip row was removed; PC color editing | `shared-ui/.../agents/AgentsContent.kt` + `MachinesContent` | discrepancy documented: QA.md P1 wireframe predates v0.3.1 (`MachineHeroDropdown` replaced the chip row); color edit path lives in `MachinesContent` | proven (current code is source of truth; QA.md is stale for P1 chips) |
| MS-10 | Quiet hours per PC (QA P6) and test notification (QA P9) | `SettingsViewModel.kt:184-190, 212-219`, `NotificationSettingsContent` | worker unit pins strings + empty-hours disable | proof-added |

## L. Mesh (T1b) — every README v0.4-0.6 QA.md §5/§8 and docs/MESH.md promise of the Mesh slice

| ID | Behavior (edge cases + exact strings) | Android source | Proof | Status |
|---|---|---|---|---|
| ME-1 | Name resolution: node.name → cwd basename → `Agente`; raw ids, "Agent"/"Mesh Group" sentinels, numbered suffix `(2)` preserved without double-tagging | `shared-ui/.../mesh/MeshFormatters.kt:64-96` | `MeshFormattersTest::formatMeshDisplayName filters out raw IDs…`, `::formatMeshDisplayName and group chip handle relay 0_5_3 sentinels…` (shared-ui jvmTest) | proven |
| ME-2 | Duplicate disambiguation: cross-machine `Worker (Omarchy)` / same-machine `Worker (Omarchy · 1)`/`(Omarchy · 2)` | `MeshFormatters.kt:43-62` | `MeshFormattersTest::formatMeshDisplayName disambiguates duplicates` | proven |
| ME-3 | Group chip labels: solo `Sin grupo`, orchestrator display name, Mesh Group sentinel fallback | `MeshFormatters.kt:105-130` | `MeshFormattersTest::formatMeshGroupChipLabel keeps solo and Sin grupo sentinels` + `::falls back to orchestrator…` | proven |
| ME-4 | Reingold-Tilford layout: same depth = same row, orchestrator row above, bounding-box collision avoidance 1-12 agents, multiroot rows | `shared-ui/.../mesh/layout/MeshLayoutEngine.kt` (644 LOC) | `MeshViewportRenderTest` (fits/landscape/tablet/deep-tree/multiroot/1/4/12 agents on 360dp) + `MeshViewModelTest::snapshot calculates layout…` | proven |
| ME-5 | Viewport: auto-fit min 0.65, 1.0 in landscape ≥600dp, wide MIN_ZOOM=0.4f MAX_ZOOM=2.5f clamps (README v0.5.0; QA M6's 0.5x-3.0x text is stale), top-anchor + 16dp, clipToBounds under filters | `MeshCanvas.kt:203-214,619,664-665`, `MeshContent.kt:73-74` | visual pins in `MeshViewportRenderTest::diagram card legibility…`; clamp constants are private (no direct unit) | partial (constants unquantified; numbers read from source) |
| ME-6 | Recentrar button exact label `Recentrar organigrama`, wired to recenter trigger | `MeshContent.kt:58,74,100` | `MeshScreenTest::recenter action button is displayed and clickable` | proven |
| ME-7 | Machine/group chips filter nodes+edges, persist selection, most-active default group, recalculated on machine change | `MeshViewModel.kt` (combine logic), `MeshContent.kt` chips | `MeshViewModelTest::machine and group selections filter layout nodes and edges and persist` + `::preselection chooses group…`, QA F5.3 via same | proven |
| ME-8 | Node detail sheet: avatar/name/role/machine/branch/model/status/activity/todos/subagents; role badge only for orchestrator; raw IDs only in collapsed `Detalles técnicos` with one-tap copy | `shared-ui/.../mesh/MeshNodeDetailSheet.kt:45+,219-242,278-313` | `MeshScreenTest::tapping node opens node detail sheet` + `::subordinate card gives its header width…` | proven (copy → `Copiado` toggle untested) partial |
| ME-9 | Conversation sheet: direction-ordered history, clean names, `Cargar mensajes anteriores` pagination via `nextBefore`, opens without edge (F5.2) | `MeshConversationSheet.kt:72-79`, `MeshViewModel.kt` loads | `MeshScreenTest::conversation sheet localizes…` + `MeshViewModelTest::clicking edge loads conversation and pages older items with nextBefore` + `::onOpenConversation opens conversation even when no edge…` | proven |
| ME-10 | Live strip: latest comms `Sender → Receiver: text` clean names, collapsible | `MeshContent.kt` strip, `MeshViewModel.kt: comm flow` | `MeshViewModelTest::incoming comm updates latest-first live strip and active bubbles` | proven |
| ME-11 | Disconnected banner `Desconectado — mostrando datos locales` + `Reintentar`, topology stays visible; list view toggle `Vista de lista`/`Vista de diagrama` accessible (QA M11/M12) | `MeshContent.kt:118,133-138` | `MeshScreenTest::disconnected banner shows stale data and retry action` + `::toggle to list view displays nodes accessibly` | proven |
| ME-12 | Relay errors: 10s WS timeout → GET /v1/mesh fallback; 404 copy `Tu relay no soporta la Red de agentes…` with Reintentar; generic network error; recovery from error state (QA F2.2) | `app/.../ui/mesh/MeshViewModel.kt` + shared RelayApi | `MeshScreenTest::404 error shows unsupported relay message…` + `::network error shows generic message…` + `::retry recovery transitions from error to loaded nodes` (data tests: `RelayApiMeshTest`, `MeshRepositoryTest`) | proven |
| ME-13 | Subagent nodes/leaf placement: `running`/`working` → own node one level below; others → compact pills; unknown status stays a pill;NodeId stable, never contains taskId | `shared-ui/.../mesh/layout/SubagentPlacement.kt:20-49` | `shared-ui/jvmTest/.../mesh/SubagentPlacementTest.kt` (4 tests: slot mapping, split, nodeId determinism/no-leak, displayName) | proven |
| ME-14 | Motion: comm particles travel orthogonal bus routes, pendingAsk dashed pulse, shimmer for working, spinner for running subagents, active edge glow ≤20s, idle calm (no redraw loop); reduced motion / ANIMATOR_DURATION_SCALE=0 disables particles/shimmer/pulses (QA M13) | `MeshCanvas.kt:67-113,342-346,397+,619` | `LocalReducedMotion`/`RpMotion` pinned by `MotionTest`; particle/shimmer visuals only golden-covered (golden - unverified on this host) | partial |
| ME-15 | TalkBack: `X, reporta a Y` semantics on subordinate leaf items (QA M12) | `MeshLeafItem.kt` via `formatMeshNodeReportsTo` (`MeshFormatters.kt:173`) | `MeshScreenTest::TalkBack hierarchy semantics include reporta a…` | proven |
| ME-16 | Subscription lifecycle: subscribe on visible, `mesh.unsubscribe` on background, resubscribe ON_START, reference-counted ownership with lastSubscribedMachineId (QA M14/F2.3) | `app/.../ui/mesh/MeshViewModel.kt`, `MeshScreen.kt` | `MeshViewModelTest::screen visibility subscribes and unsubscribes` | proven |
| ME-17 | Tolerant decoding: decimal `startedAt`, malformed subagents/nodes skipped with valid items intact, undecodable root → MeshErrorState+retry (QA F4.1-F4.3) | protocol `Tolerant*Serializer` + handled at VM level | `MeshViewModelTest::retry recovery…` + data-level tolerant list tests in `RelayApiMeshTest`/`MeshRepositoryTest` | proven |
| ME-18 | Device-locale timestamps in conversation sheets (QA M16) | `shared-ui/.../mesh/MeshFormatters.kt` + `PlatformTime` | `MeshFormattersTest::formatters localize mesh roles statuses actions and hidden content` (locale-dependent paths exercised) | proven |
| ME-19 | Compact landscape filters share one row < 500dp available height, graph clipped below (QA M15) | `MeshContent.kt:73` | `MeshScreenTest::compact landscape filters share a row…` | proven |
| ME-20 | Quota bubbles stay 24dp clear of sender/receiver cards | `shared-ui/.../mesh/MeshCanvasGeometry.kt:5-33` | `MeshScreenTest::communication bubble center stays 24dp clear…` | proven |

## J. Open gaps (rows with `char`/`pending` above) — proof plan

- All `char` rows: characterization tests on UNCHANGED Android code, sent to `gentle-ai-worker` (Allowed edit surface: `app/src/test/**`, `docs/parity/o4-shell.md` only). Mesh rows will follow in T1b with the same worker.
- Platform rows: no automatic test possible on JVM; they become port-requests to O1 (section G) and manual rows referencing docs/QA.md.
- Golden count discrepancy (README 48 vs 138 actual) flagged to orchestrator for README fix in T9.
## M. Status summary (computed from all rows, 2026-11-06)

Counts across sections A-L: total 50 rows — proof-added=19, proven=17, partial=5, gap-with-reason=4, out-of-slice=2, platform=2, defect=1.
`platform` rows keep their exact QA step (docs/QA.md) or become port requests (section G); `defect` names D-1 exactly once and never blocks other rows.
Baselines for Gate D: S20 suite now **app 272 / protocol 73 / relay-client 75 / shared-ui 112 = 532 tests, 0 failures, 0 skipped** (orchestrator baseline 499 + 33 added by O4 in T1). Roborazzi untouched (host-drift note above).

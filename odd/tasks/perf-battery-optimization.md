# Feature: System Performance, Mobile Battery & 60/120 Hz Rendering Optimization (`Rp-app`)
- **Branch:** `perf/system-optimization` · **Base:** `main` (`4c57dd6`) · **Delivery strategy:** `ask-on-risk`
- **Test runner:** `./gradlew :protocol:test :data-core:jvmTest :data-core:compileIosMainKotlinMetadata :relay-client:compileCommonMainKotlinMetadata :relay-client:jvmTest :shared-ui:jvmTest :shared-ui:compileIosMainKotlinMetadata :shared-ui:ktlintCheck :app:compileDirectDebugKotlin :app:compileFcmDebugKotlin :app:testDirectDebugUnitTest :app:lintDirectDebug` · **Forecast:** ~1,400 authored changed lines (3 work-unit commits)

## Specs

### S1 — Mobile Battery, Radio, Heartbeats, Network Monitor, Reducer & iOS NSE (`APP-PERF-01`, `APP-PERF-02`, `APP-PERF-03`, `APP-PERF-10`, `APP-PERF-14`)
- User request verbatim: `"ahora auditemos el sistema en terminos de rendimiento, bateria, performance"` → `"si atacando las 4 fases completas en orden"`
- Protocol parity: sync fixtures from `Rp-relay` (`app.state`), add `MsgType.APP_STATE = "app.state"` in `Envelope.kt` and `@Serializable data class AppState(val screenOn: Boolean)` in `Models.kt`, with contract tests.
- `APP-PERF-01` (`DirectPushService` & `NotificationFactory`):
  - Disable client OkHttp `pingInterval(0, TimeUnit.SECONDS)` on `DirectPushService` (server-driven heartbeat via `Rp-relay`).
  - Send `{ t: "app.state", d: { screenOn } }` when `ACTION_SCREEN_ON`/`ACTION_SCREEN_OFF` transitions AND upon socket connect.
  - Remove duplicate `factory.showWorking(a)` / `factory.cancelWorking(...)` loop in `DirectPushService.onText` `AGENTS_UPDATE` handler (keep `agents.applyAgentsUpdate(u)` + `idleStandby.value = false`; Room collector drives working notifications).
  - In `NotificationFactory`, debounce cosmetic `renderWorkingNotification()` updates to ≥3 s (while flushing immediately on status/agent change or cancel), remove `pruneOrphanSummaries()` from `showWorking()`, and cache active working notification IDs in memory instead of calling `nm.activeNotifications` 3× per event.
- `APP-PERF-02` (iOS & Android foreground heartbeats):
  - In `UrlSessionSocketDriver.kt` (iOS), replace uncancelled `dispatch_after` pong deadlines with a cancellable `dispatch_source_t` timer cancelled on pong/close, reuse a single `NSURLSession` across reconnects instead of allocating a new one per reconnect, and relax client foreground ping to `45 s` (resetting deadline on inbound traffic).
- `APP-PERF-03` (`AndroidNetworkMonitor`):
  - Add `@Singleton` to `PortsModule.bindNetworkMonitor`.
  - Migrate `AndroidConnectivityCallbackSource.register()` to `ConnectivityManager.registerDefaultNetworkCallback(networkCallback)`.
  - Support fan-out `CopyOnWriteArraySet<NetworkObserver>` and short-circuit `onCapabilitiesChanged` when `previousNetwork == network && online.value` so dual-homed Wi-Fi+5G (Galaxy S25) never triggers reconnect ping-pong loops.
- `APP-PERF-10` (`ChatReducer` & `ChatMessageDispatcher`):
  - Coalesce consecutive `message_delta` events for the same `(contentIndex, kind)` using a `StringBuilder` inside `ChatMessageDispatcher.applyBatchEvents()` before invoking `ChatReducer.reduce`.
  - Skip `parsePartial(raw)` when `raw.length > 4096` (keeping prior `prev.args` until `tool_execution_start`) to eliminate hundreds of thrown `SerializationException`s per streaming tool call.
  - Use mutable local `ArrayList`/`LinkedHashMap` accumulators in `ChatReducer.applyEntries()`.
- `APP-PERF-14` (iOS NSE):
  - Cache `pushKey` in-process in `ServiceExtension.swift` (invalidating and re-reading Keychain on decrypt failure) to cut Keychain IPCs per push without weakening security.
- Risk: `item 3 & item 4 (background service, network callbacks, WS heartbeats, KMP protocol)`.

### S2 — Cold Start, Room SQLite Batching & Migration 3→4, Attachments & iOS Memory Warning (`APP-PERF-02` Android OkHttp, `APP-PERF-04`, `APP-PERF-09`, `APP-PERF-12`)
- `APP-PERF-02` (Android OkHttp): Increase foreground OkHttp `pingInterval` in `AppModule.kt` from `25 s` to `45 s` (server already drives 25 s activity-aware pings).
- `APP-PERF-04` (Cold Start):
  - Dispatch `attachments.cleanup()` off the Main Thread onto `Dispatchers.IO` in `RpApp.onCreate()`.
  - Pack the 6 `Session` fields into a single encrypted JSON entry (`session_v2`) in both Android `SecretStore.kt` and KMP `EncryptedKeyValueSecretStore.kt` with full backward-compatible legacy 6-key read fallback & transparent upgrade on next save (cutting Keystore/Keychain IPCs & decrypts from 6–7 down to 1–2).
  - Remove duplicate bridge registration in `iOSApp.swift` and wire `UIApplication.didReceiveMemoryWarningNotification` -> `SharedAppController` cache eviction.
- `APP-PERF-09` (Room SQLite):
  - Add `@Index("relayId", "agentId", "id")` on `ChatRecordEntity`, bump `RpDatabase` `version = 4`, add additive `MIGRATION_3_4` (`CREATE INDEX IF NOT EXISTS index_chat_records_relayId_agentId_id ON chat_records (relayId, agentId, id)`), register `MIGRATION_3_4` in `AppModule.kt` and all Room migration tests (`DatabaseMigrationTest`, `DestructiveFallbackTest`, `MigrationFidelityTest`), and export Room schema `4.json`.
  - Batch `ChatRepository.persistEvent()` into a 500 ms in-memory buffer flushed in a single Room transaction (flushing immediately on `agent_settled` / `stop()` / `clear()`), cap `trim` keep count to `1000` (down from 5000), and bound initial replay query cleanly.
  - Add batch `getByIds(relayId, ids)` on `AgentDao` & `PromptDao` and eliminate N+1 `dao.get` loops in `AgentsRepository.kt`.
  - Rate-limit `DedupDao.purge()` in memory (once/hour) instead of executing a `DELETE` query on every push dedup check.
- `APP-PERF-12` (Attachments):
  - Decode compressible images directly from `Uri` via `android.graphics.ImageDecoder` (`minSdk = 29`) with target size + automatic EXIF orientation (eliminating the 2× disk temp copy and 2× rotated `Bitmap` peak RAM).
  - Throttle `ProgressBody.writeTo` `onProgress` callbacks in `RelayApi.kt` to ≥5% delta or ≥100 ms intervals.
- Risk: `item 1 & item 2 (Room migration 3->4, SecretStore/Keychain encrypted session_v2 with legacy fallback)`.

### S3 — 60/120 Hz Compose UI, LTPO Release, Markdown, Recomposition Storms, Mesh Layout & iOS `SharedAppController` (`APP-PERF-05`, `APP-PERF-06`, `APP-PERF-07`, `APP-PERF-08`, `APP-PERF-11`, `APP-PERF-13`)
- `APP-PERF-05` (`RpMarkdown`):
  - Wrap `rememberRpMarkdownComponents()`, `markdownColor`, `markdownPadding`, and `markdownTypography` in `remember(...)`.
  - Accept `isStreaming: Boolean = false` in `RpMarkdown`: debounce AST parsing during active streaming (~150 ms) and skip `HighlightedCode` syntax-highlighting lexer passes until `!isStreaming`; for finalized texts > 2000 chars use `immediate = false` (off-main-thread AST parsing) while keeping short/medium and test texts deterministic.
- `APP-PERF-06` (LTPO 120 Hz Lock & Skia Shaders):
  - Stop infinite animations on `waiting_input` cards (`AgentCard.kt`) and idle/waiting `StatusChip.kt` (static high-contrast amber border so Galaxy S25 LTPO drops to 10–24 Hz).
  - Pre-allocate `Brush`/`Stroke` in `drawWithCache` in `AgentCard.kt` and `MeshAgentCard.kt` (no per-frame `Brush.radialGradient`/`Brush.linearGradient`/`List`/`Stroke` inside `drawWithContent`/`onDrawWithContent`).
  - Pre-measure bubble `TextLayoutResult` in `remember(...)` outside `Canvas` `DrawScope` in `MeshCanvas.kt`.
  - Guard the 3 unguarded `CircularProgressIndicator`s in `ChatMessages.kt` with `LocalReducedMotion.current`.
  - Honor `NSProcessInfo.processInfo.isLowPowerModeEnabled` and thermal state (`NSProcessInfoThermalStateSerious`/`Critical`) in `Motion.ios.kt`.
- `APP-PERF-07` (Recomposition Storms):
  - Derive `activeTheme: StateFlow<RpThemeChoice>` with `.distinctUntilChanged()` inside `MainViewModel` instead of collecting `agents` at root `MainActivity.setContent`; `remember(darkTheme, context)` `dynamicDarkColorScheme`/`dynamicLightColorScheme` in `Theme.android.kt`.
  - Scope per-route `collectAsState()` inside each route composable in `RemotePiApp.kt` instead of collecting 27 Flows at root.
  - Pre-derive `otherActiveAgents` with `.distinctUntilChanged()` in `ChatViewModel.kt` before combining with 31 Hz `session.state`.
  - Pass `items` and `streaming: AssistantMsg?` separately into `MessageList` (rendering `streaming` in its own `item(key = "stream")` slot, avoiding 31 Hz O(N) `display` list copy and `filterChatItems` re-scans; keep `ChatState.display` property intact for compatibility), and scope `ToolRun` lookups per bubble/card so a tool update does not recompose unrelated historical `AssistantBubble`s.
- `APP-PERF-08` (`MeshLayoutEngine` & `MeshCanvas`):
  - Remove the synchronous 2nd `MeshLayoutEngine().computeLayout(...)` from `MeshCanvas.kt` and forward debounced `cardHeights` (`snapshotFlow { cardHeights.toMap() }.debounce(32)`) to `onCardHeightsChanged`.
  - Separate topology fingerprint from cosmetic node updates in `MeshViewModel` / `SharedAppController` and compute layout on `Dispatchers.Default`; pre-index `nodesById` in `MeshCanvas` and `leavesByParent` in `MeshLayoutEngine`.
- `APP-PERF-11` (iOS `SharedAppController`):
  - Eliminate duplicate `ChatReducer` reduction by storing the already-reduced `ChatState` reference in `chatHistoryStore`; lower `MAX_CACHED_CHAT_HISTORIES` to `8` and add `onMemoryWarning()` to evict inactive histories; coalesce `onIncomingMeshComm` bubble cleanup into a single replaceable `Job`.
- `APP-PERF-13` (`DiffSheet`, `QuotaDynamicIsland`, `DesignTokens`):
  - Pre-parse diff lines into typed `DiffRow` with keyed `itemsIndexed` and cached `TextStyle` in `DiffSheet.kt`; `remember` `machineIds` and honor `LocalReducedMotion` in `QuotaDynamicIsland.kt`; provide `RpSurfaceTokens` via a `CompositionLocal` computed once in `RpTheme` (`DesignTokens.kt` / `Theme.kt`).
- Risk: `item 3 & item 4 (Compose UI rendering, KMP shared-ui & iOS SharedAppController state)`.

## Tasks

- [x] **T1** (`S1`, delegated parallel writer, `Risk: item 3, 4`, commit `7799b4e`) — Protocol `app.state`, `DirectPushService` & `NotificationFactory` wakeup elimination, iOS `UrlSessionSocketDriver` GCD timer/session fix, `AndroidNetworkMonitor` default callback singleton, `ChatReducer`/`ChatMessageDispatcher` perf & iOS NSE key cache.
- [x] **T2** (`S2`, delegated parallel writer, `Risk: item 1, 2`, commit `ccd329b`) — Cold-start `attachments.cleanup()` off main thread, `session_v2` single-entry crypto with legacy fallback, Room `MIGRATION_3_4` + batched `ChatRepository` + batch `getByIds` + throttled `DedupDao`, `ImageDecoder` attachments + throttled `ProgressBody`, Android 45 s OkHttp ping, `4.json` schemas synced.
- [x] **T3** (`S3`, delegated parallel writer, `Risk: item 3, 4`, commit `32bc5e2`) — `RpMarkdown` async/remembered styles, LTPO 120 Hz & `drawWithCache` shader fixes, `Motion.ios.kt`, root/chat recomposition fixes, single off-main-thread `MeshLayoutEngine` with generation guard, `SharedAppController` single reduction & memory eviction, `iOSApp.swift` memory warning hook, `DiffSheet`/`QuotaDynamicIsland`/`DesignTokens`.
- [x] **T4** (`S1-S3`, inline parent build + verification, commit `749f5b1`) — Resolved S3 `MeshViewModel` TOCTOU concurrency blocker with `stateLock` (`749f5b1`, 5/5 `--rerun-tasks` runs green); verified full KMP + Android matrix (`:protocol:test` 71/71, `:data-core:jvmTest` 47/47 + iOS metadata, `:relay-client:jvmTest` 103/103 + iOS metadata, `:shared-ui:jvmTest` 154/154 + iOS metadata + 4 `ktlint` source sets, `:app:compileDirectDebugKotlin` + `compileFcmDebugKotlin`, `:app:testDirectDebugUnitTest` + 6/6 refreshed Roborazzi goldens `pixelDifferences=0`, `:app:lintDirectDebug` 0 errors). Independent verifier verdict: **PASS (`S1: PASS`, `S2: PASS`, `S3: PASS`)**.

## Log

- **L1 (2026-11-24):** Continuing authorized 4-phase optimization into Repo 3 (`Rp-app`) on branch `perf/system-optimization`. Launching `Rp-plugin` independent verifier in parallel with `Rp-app` T1, T2, and T3 (strictly disjoint edit surfaces; parent owns unified Gradle build/test execution in T4 to avoid daemon lock contention).

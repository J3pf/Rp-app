# Feature: 4R Hardening — `Rp-app` (`v0.9.5`)
Branch: `fix/4r-hardening` | Delivery: `ask-on-risk` | Forecast: ~1,500 authored changed lines | Runner: `cd Rp-app && ./gradlew :protocol:test :data-core:jvmTest :relay-client:compileCommonMainKotlinMetadata :relay-client:jvmTest :shared-ui:jvmTest :app:testDirectDebugUnitTest`
Mirror: pending (workspace root is ambiguous across sibling repos in Engram)

## Specs

### S1 — KMP `:protocol`, `:data-core`, `:relay-client` wire contracts, reducer, dispatcher, delivery & iOS drivers (`B-03`, `B-04`, `C-04`, `C-05`, `C-08`, `C-10`, `C-11`, `C-12`, `W-10`, `W-14`, `W-15`, `W-17`)
- In `:protocol`:
  - `ChatReducer.settle()`: assign a unique settled key (`settled:<lastSeq>:<at>`, disambiguated against `s.items`) so aborted turns never retain `"stream"` and crash `LazyColumn`.
  - `Http.kt` (`NotifyPrefs`): add top-level `val criticalAlways: Boolean? = null`.
  - `Models.kt`: add `PushKind.TEST = "test"`, typed `PushErrorBody` (`detail`), `val redacted: Boolean = false` on `PushPromptBody`/`PushSettledBody`, `@JsonNames("createdAt")` on `Prompt.openedAt`, simplify `ModelInfoSerializer.serialize`.
  - `FixturesTest.kt`: fail when fixtures resource is missing instead of silent `?: return`.
- In `:data-core`:
  - `EncryptedKeyValueSecretStore.kt`: avoid wiping stored session on transient read errors (distinguish not-found vs error via optional status/exception boundary).
- In `:relay-client`:
  - `AppSettings.kt`: add canonical `Rp-relay` wire category mapping (`permission`, `question`, `settled`, `error`, `terminal_wait`) preserving compatibility with persisted Android channel keys.
  - `ChatDeliveryPolicy.kt`: decode `UserMessageAck` JSON from HTTP 200 `ApiResult.Ok` body and return `SendResult.Failed` when `ack.isFailure`; enqueue background send on `CancellationException` if outbox row remains pending; increment `attempts` & prune poisoned outbox rows after `>= 8` attempts.
  - `ChatMessageDispatcher.kt`: run pure in-memory `applyBatchEvents()` & `updateState()` inside `batchLock` and replace O(k²) `pendingStreamEvents` replay with O(1) sequence/epoch tracking.
  - `RelayWsClient.kt`: remove `id` from `pending` inside `try { ... } finally` on coroutine cancellation.
  - `SessionPolicy.kt`: avoid indefinite retries on non-429 4xx in `registerFcmToken`.
  - `UrlSessionSocketDriver.kt`: add 15s pong deadline timer in `schedulePing()`, and check `socket.closeCode.toInt() != 0` in `receiveNext()` / `didCompleteWithError` before routing to `fail(error)`.
  - `UrlSessionRelayTransport.kt`: reuse shared `NSURLSession`, set `timeoutIntervalForResource`, and cap response body bytes.

### S2 — Android `:app` network, biometrics, persistence, repositories, push & ViewModels (`B-01`, `B-02`, `B-04`, `C-04`, `C-05`, `C-06`, `C-07`, `C-09`, `C-11`, `W-02`, `W-04`, `W-06`, `W-11`, `W-12`, `W-13`, `W-15`, `W-17`, `S-01`, `S-02`, `S-03`, `S-05`)
- `OkHttpRelayTransport.kt`, `RelayApi.kt`, `AppModule.kt`: read `it.body.string()` inside `withContext(Dispatchers.IO)` and `try/catch (IOException)` returning `ApiResult.Network`; set `readTimeout(30s)` on `uploadClient`; add `onCancellation = { response.close() }` in `Call.await()`.
- `RpApp.kt` & `BiometricAuthorizerAndroid.kt`: eagerly initialize `BiometricAuthorizer` in `RpApp.onCreate()` and clear `foreground` in `onActivityDestroyed`.
- `SettingsViewModel.kt`: send top-level `criticalAlways` and canonical wire `NotifyCategory` keys in `putPrefs`.
- `PushRouter.kt` & `NotificationFactory.kt`: read `detail` first on `error` pushes (fixing 24h FCM dedup blackhole), handle `PushKind.TEST`, enforce `redacted: true`, use `VISIBILITY_PRIVATE`, and clean unreachable line 845.
- `ChatViewModel.kt`: handle `"agent_blocked"`/`"interactive_unavailable"`/`"herdr_unavailable"` in `SendResult.Failed`; include `DeliveryChoice.COMMAND` in `needsAuth()`; run `session.send` without stranding outbox on Back; cancel per-attachment jobs on `removeAttachment()`.
- `ChatRepository.kt`: wrap `persistHistory()` in Room `@Transaction`, delete overlapping `seq >= minSeq` before inserting `h.events`, and skip initial empty `ChatState()` in `telemetryJob`.
- `MeshRepository.kt`: pass `id = Ulid.new()` to `Envelope.of(MsgType.MESH_SUBSCRIBE, ...)`.
- `DirectPushService.kt` & `AgentsRepository.kt`: keep direct WS alive on standby/Doze when no FCM channel exists; persist background `AGENTS_UPDATE`; preserve `todoCompleted`/`todoTotal`/`activeTask` on `APP_WELCOME`.
- `SettingsRepository.kt` (`ReplaceFileCorruptionHandler`), `AppModule.kt` (`CoroutineExceptionHandler`), repo `bus.messages` `onEach` error isolation, `PromptActionReceiver`/`BootReceiver` guards, `SecretStore.kt` (no destructive reset on transient Keystore errors).
- Move cleartext `network_security_config.xml` to `app/src/debug/res/xml/` and gate `"http"` on `BuildConfig.DEBUG` (with testable default param) in `SessionRepository.kt`.
- `PromptActionReceiver.kt` (compute `authRequired` locally; don't mark `deduper.markAnswered` on `Failed`), `AnswerWorker.kt` (clear/update notification on exhaustion), `RpMessagingService.kt` (cap `FcmTokenWorker` retries + timeout `onMessageReceived`).
- `AgentsViewModel.kt`: guard `loadMoreArchived()` + `.distinctBy { it.agentId }`; lexicographical `compareByDescending` machine-group sorting.
- `AttachmentsRepository.kt` (rethrow `CancellationException`, recycle bitmap in `finally`, delete temp `camera/` photo after `prepare()`), `BiometricGate.kt` (`BIOMETRIC_STRONG or DEVICE_CREDENTIAL` on API 30+), `MeshViewModel.kt` (single bubble cleanup job), `PushRouterTest.kt` (shut down `MockWebServer`).

### S3 — `:shared-ui` KMP controller, iOS parity, UI security/correctness & `MeshLayoutEngine` (`B-04`, `B-05`, `C-02`, `C-05`, `C-10`, `W-01`, `W-04`, `W-07`, `W-09`, `W-11`, `W-12`, `W-16`, `W-17`)
- `SharedAppController.kt` & `RemotePiApp.kt`: fix `loadArchived()` to send `MsgType.AGENT_ARCHIVED` + set `_archivedLoading = true` on pagination + `.distinctBy { it.agentId }`; send top-level `criticalAlways` & canonical wire categories; fix iOS App Lock (start locked when `appLock && session != null`, keep locked on `UNAVAILABLE`, auto-prompt via `LaunchedEffect`, move overlays inside unlocked branch); wire `onAppBackgrounded()`/`onAppForegrounded()` WS lifecycle + `DisposableEffect` cleanup + `LocalReducedMotion`; fix `"/gentle:mode normal"`, `SendResult.Failed`, strict `normalizeRelayUrl()`, QR active-session confirmation, `percentDecodeComponent()` UTF-8, `meshRefreshJob` cancellation, `MESH_UNSUBSCRIBE`, `busyPromptIds` pre-biometric guard, `discardDiffFile()` error surfacing, LRU `chatHistoryStore`, and lexicographical group sorting.
- `AgentCard.kt`, `ChatTopBar.kt`, `ChatComponents.kt`: honor `agent.redacted` on metadata; fix `ChatMessagesList` auto-scroll (`lastItemKey != tracker.settledLastKey`).
- `RpMarkdown.kt` & `ChatMessages.kt`: restrict `LocalUriHandler` to `https://`/`http://`, guard `HighlightedCode` bounds & size with `runCatching` (rethrow `CancellationException`), cap `DiffView` lines.
- `MeshLayoutEngine.kt`: run cycle breaking after group fallback and guard `roots.firstOrNull()`.
- `QrScannerView.ios.kt` & `PlatformAppScope.ios.kt`: `layoutSubviews()` preview frame, serial capture queue, async camera permission, off-factory `onUnavailable()`; `IosBiometricAuthorizer` cancellation invalidation; separate app-private Keychain (`accessGroup = nil`) from shared NSE group (resolved from Info.plist `RPKeychainAccessGroup`, no hardcoded `19410625785`).

### S4 — Native `iosApp/` (`Swift`, signing, entitlements, `NotificationService`) & CI/Release workflows (`B-06`, `C-01`, `C-03`, `C-04`, `C-11`, `C-12`, `W-03`, `W-08`)
- `iosApp/`: update `TEAM_ID = 9LQBT792F9` in `Config.xcconfig`; use `$(AppIdentifierPrefix)dev.j3pf.rp.iosApp` + `aps-environment` in `iosApp.entitlements`; add `NotificationService.entitlements`; set `$(MARKETING_VERSION)` / `$(CURRENT_PROJECT_VERSION)` / `RPKeychainAccessGroup` in `Info.plist`s; update `project.pbxproj` (`0.9.5`, `DEVELOPMENT_TEAM = "$(TEAM_ID)"`, NSE entitlements) and `ExportOptions.plist` (add NSE profile); update `iOSApp.swift` (`UNUserNotificationCenter.requestAuthorization`, `didFailToRegisterForRemoteNotificationsWithError`, `flagSecure` privacy cover, `errSecItemNotFound` distinction, fix `var status` warning); update `ServiceExtension.swift` (Info.plist `RPKeychainAccessGroup`, read `"detail"` on `error`, handle `"test"` & `"cancel"`, cap `seenPromptIds`).
- `.github/workflows/`: fix `${{ inputs.* }}` shell injection in `testflight.yml`; add full KMP test matrix + `permissions: { contents: read }` in `ci.yml`/`ios.yml`/`release.yml`; fail loud on missing release keystore secrets, run tests before writing keystore, shred keystore before `softprops/action-gh-release`.

## Tasks

- [x] **T1** (`S1`) — KMP `:protocol`, `:data-core`, `:relay-client` hardening | Route: delegated (`odd-writer`, parallel with T4) | Status: done | Commit: `2beb3f8` (`:protocol:test`, `:data-core:jvmTest`, `:relay-client:jvmTest`, iOS metadata green)
- [x] **T2** (`S2`) — Android `:app` network, biometrics, persistence, push & ViewModels | Route: delegated (`odd-writer`, Wave 2 parallel with T3) | Status: done | Commit: committed-below (full Gradle matrix + 509/509 unit/Roborazzi + lintDirectDebug 0 errors)
- [x] **T3** (`S3`) — `:shared-ui` controller, iOS parity, UI security & `MeshLayoutEngine` | Route: delegated (`odd-writer`, Wave 2 parallel with T2) | Status: done | Commit: committed-below (147/147 `:shared-ui:jvmTest` + `:shared-ui:compileIosMainKotlinMetadata` green)
- [x] **T4** (`S4`) — Native `iosApp/` signing/NSE/Swift + CI/Release workflows | Route: delegated (`odd-writer`, parallel with T1) | Status: done | Commit: `75047b0` (static/plist/YAML/lint 0 errors)

## Log

- **L1 (2026-11-24)**: User request verbatim: `"OK si hagamole, hasta suggestion o warnings si son reales"` (Stage 3 of `Rp-relay` -> `Rp-plugin` -> `Rp-app`).
- **L2 (2026-11-24)**: Created feature branch `fix/4r-hardening` from `main` (`fdc7d11`) in `Rp-app`. Launching Wave 1 (`T1` + `T4`, 100% disjoint surfaces) alongside `Rp-plugin`'s independent read-only verifier.
- **L3 (2026-11-24)**: `Rp-plugin` independent verifier returned PASS on S1–S4 (0 blockers). `Rp-app` Wave 1 verified & committed: `T1` (`2beb3f8`) and `T4` (`75047b0`). Launching Wave 2 (`T2` `app/**` + `T3` `shared-ui/**`, strictly disjoint surfaces).
- **L4 (2026-11-24)**: Updated 5 legacy characterization tests asserting pre-hardening bugs + fixed `AndroidHardeningTest.tearDown()` `cancelAndJoin()` ordering. Full Gradle matrix (`:protocol:test :data-core:jvmTest :relay-client:compileCommonMainKotlinMetadata :relay-client:jvmTest :shared-ui:jvmTest :shared-ui:compileIosMainKotlinMetadata :app:testDirectDebugUnitTest :app:compileFcmDebugKotlin :app:lintDirectDebug`) PASSED (`BUILD SUCCESSFUL`). Committing `T2` and `T3` and launching independent verifier on `fdc7d11..HEAD`.

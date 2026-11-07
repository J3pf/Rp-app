# O3 M4 composition-root design (docs only — ONE root, two contexts, zero drift)

Owner: O3 (chat slice T5 M4) — contract doc for the composition-root design the orchestrator ordered (2026-11-07 seq 71: "Design it once, explicitly, in docs, before you write it"). O4 implements its JVM Hilt host mirroring the SAME signatures; changes require O3+O4 cc.

## Why this doc exists

`SessionPolicy` is built ONLY inside the Android Hilt graph (`SessionRepository.kt:43`), so iOS cannot reach `ApplePushRegistration`'s deps; meanwhile the shared ViewModels must be constructible on iOS without Hilt, and O4's JVM host must mirror the Android factory shape. One design covers both.

## 1) The single factory seam (both roots consume; no platform type crosses)

```kotlin
// :shared-ui commonMain — plain constructor-injected KMP ViewModels (ADR 0001 §4/§5, no Hilt imports)
class ChatViewModel(
    agentId: String,                 // the one saved-state dependency (O4 decision 2026-11-07)
    chats: ChatSessionGate,          // O1 port (P1) — the ONLY chat/rel-hydration entry (single contract)
    agentsRepo: AgentsGate,          // O1 port (P2)
    settingsStore: SettingsStore,    // O1 port P4 — the EXISTING(SettingsStore) contract (ST-01 incl. the 3 setters)
    hostRepo: HostGitGate,           // O1 port (P3) — S19 chain surface
    attachmentsGate: AttachmentsGate,// O1 port (P6, upload-only)
    connectionState: StateFlow<ConnState>,  // bus-carried (ChatSessionGate flows provide it; NO OkHttp type crosses)
    gate: BiometricAuthorizer,       // frozen port (exists)
) : ViewModel()

class PromptSheetViewModel(
    prompts: PromptSheetGate,        // O1 port (P5)
    settingsStore: SettingsStore,    // P4 (SettingsStore again)
    gate: BiometricAuthorizer,
) : ViewModel()
```

Rules verified across all lanes:
- `SavedStateHandle.toRoute<ChatRoute>()` never crosses; `agentId: String` is the whole saved-state dependency (O4 2026-11-07).
- The `gate` is the frozen `BiometricAuthorizer` port (M4 C1, `f61c8cc`); the `:app` adapter keeps the exact literals.
- NO Hilt import INSIDE :shared-ui; `@HiltViewModel` stays ONLY on the :app originals until C5.

## 2) Android root (`RpRoot` + Hilt) — unchanged shape, factory handoff

- `RpRoot.kt:281-286` keeps `entry.toRoute<ChatRoute>()` (AndroidX navigation生活在 :app) and builds `ChatViewModelFactory(agentId = r.agentId, defaultArgs ...)` — Hilt-assisted like today; O4's JVM Hilt host mirrors THIS shape for render; no drift.
- `ChatRoute(t.agentId)` call shapes at RpRoot.kt:132/139/256 + onOpenChat:245 are untouched.
- The three gate call sites (ChatScreen.kt:318, :350, PromptSheet.kt:95) keep the byte-identical literals via the :app adapter (M4 C1, `f61c8cc`).

## 3) iOS root — fresh, Hilt-free, same factories

```kotlin
// :shared-ui iosMain (or iosApp thin shell) — O1/O4's T6/T7 actuals land here, no Platform ShortcutTypes
class IosAppScope(
    secretStore: SecretStore,            // Keychain S18 (O1's SecretStore port)
    settingsStore: SettingsStore,        // KMP DataStore, same file format
    db: RpDatabase,                      // Room KMP (platformDatabaseBuilder expect/actual)
    bus: MessageBus,                     // :relay-client commonMain
    pushBridge: IosPushRegistrationBridge,  // O4's anchor target (see §4)
    chatConnectionFactory: ConnectionController-ish,  // O2's reserved holder — the SOLE socket hand-off
) {
    fun chatViewModelFactory(agentId: String): ChatViewModel =
        ChatViewModel(
            agentId = agentId,
            chats = ChatSessionGateIos(...),  // O1's common impl over the DAO/bus/session-port flows
            agentsRepo = AgentsGateIos(...),
            settingsStore = settingsStore,
            hostRepo = HostGitGateIos(...),   // NOTE: iOS no-op until O1 lands an iOS actual (S19 gap to C-Gate C)
            attachmentsGate = AttachmentsGateIos(...),
            connectionState = chatConnectionFactory.state,  // StateFlow<ConnState> only
            gate = BiometricAuthorizerIos(...),  // LAContext impl (S12 Keychain groups per ADR)
        )
    fun promptSheetViewModelFactory(): PromptSheetViewModel = ... same pattern ...
}
```

- The iOS root is the ONLY place that owns these reclamation bindings for iOS; no type leaks platform details upward.
- No parallel Session type: iOS does NOT re-invent `SessionPolicy`; the bridge path (§4) is how the iOS push hook plugs in.

## 4) iOS Push anchor (root lifecycle step, per the 2026-11-07 addendum)

Init order (fixed):
1. `secretStore` (session load)
2. `settingsStore`
3. `db` + `bus`
4. `pushBridge` anchor: **the root does `pushBridge.bind { ... }` once** — `register(apnsToken:)` returns `false` while unset (O4's contract). The `ApplePushRegistration` path activates with the SAME root that binds the anchor; until then registration is explicitly inert-by-design (named gap, owned by MY unit — no O4 defect, no parallel Session/SessionPolicy type).
5. `chatConnectionFactory` (a SINGLE ConnectionController hand-off from O2; the root constructs it once behind the shared factories)
6. VM factories become computable only AFTER (1)-(5) — factories never reach a half-bound root.

## 5) What C3 copies expect

`:shared-ui commonTest` fakes (already in `:data-core commonTest`: FakeSettingsStore/FakeBiometricAuthorizer/FakeAttachmentsPlatformHost/FakeVoiceInputHost + the pending per-port fakes O1 adds as units land: FakeChatSessionGate, FakeAgentsGate, FakeHostGitGate per O1 contract tests, FakePromptSheetGate). The harness constructs the shared VMs DIRECTLY with those fakes — the same shape O4's JVM Hilt host mirrors on the Android render (§2) — the whole point of one-design-two-contexts.

## 6) Non-goals / untouched

- `SessionPolicy`/`SessionRepository.kt:43` construction stays the ONLY ApplePushRegistration producer until (4) activates; C5 will NOT delete it.
- No O1 `PlatformPorts.kt` edits from this doc; port requests flow through O1 as usual.
- OkHttp/transport stays `:app` (ADR no-port zone); the shared VM sees only `StateFlow<ConnState>` injected via the ports above.

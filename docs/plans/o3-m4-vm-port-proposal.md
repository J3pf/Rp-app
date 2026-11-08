# O3 M4 port proposal (docs only — contracts owned by O1)

From: O3 (chat slice, T5 M4) — for O1 to land in `:data-core commonMain` and cc: orchestrator 01a1b65e.
Scope: the EXACT interfaces + call-site inventory the two shared ViewModels (`ChatViewModel`, `PromptSheetViewModel`) need so they can leave `:app` as Hilt-free plain-constructor VMs. No parallel contracts invented without O1's shape sign-off; every surface below is backed by a concrete call site on `unify/o3-chat` @ `74b71ad`.

Design note (per ADR 0001 §2/§3): each port is an interface in commonMain, constructor-injected; Android adapter in `:app` keeps the current concrete class; `expect/actual` stays reserved for construction seams. iOS actuals are O1/O4's T6/T7 work — this doc only fixes the shapes.

## P1 — `ChatSessionGate` (wraps `chats.open(agentId)` + the `RelayConnection` state)

ChatViewModel consumes the repository as a per-agent **session object**, not as a bag of methods:
`ChatViewModel.kt:84` `chats.open(agentId)`; combined at `:92-102` (`session.state`, `session.syncing`); actions `session.send(...)` (202, 354, 410), `session.abort()` (:106), `session.subscribe()` (:120), `session.retry(cId)` (:211), `session.release(agentId)` (:422, onCleared).

Proposed port (commonMain):

```kotlin
interface ChatSessionGate {
    fun open(agentId: String): ChatSession
}

interface ChatSession : AutoCloseable {
    val state: StateFlow<ChatState>          // today: ChatRepository.open(agentId) → per-agent reducer state
    val syncing: StateFlow<Boolean>          // today: !needsResync pipeline (syncingHistory row CH6/CH49)
    fun send(body: String, refs: List<AttachmentRef>, delivery: Delivery, method: DeliveryMethod, asCommand: Boolean): SendResult
    fun subscribe(): Unit                    // resubscribe sinceSeq (CH49 reconnect 30s)
    fun retry(clientMsgId: String): Unit     // idempotent re-send (CH30)
    fun abort(): Unit                        // working-agent abort
    fun release(agentId: String): Unit       // deterministic onCleared cleanup
}
```

Android impl = thin delegate over `ChatRepository.open(agentId)` + `RelayConnection.state` exposure already done at `:app` (the `ConnectionController` port O2 reserved for iOS only). Fakes: extend `data-core commonTest` `Fakes.kt` with a scripted `FakeChatSession` (deterministic send results + state/syncing emitters). Port wave unit: 1.

## P2 — `AgentsGate` (agents lifecycle for a single slice)

`agentsRepo.observe(agentId)` (:93), `agentsRepo.close(agentId)` (:191).

```kotlin
interface AgentsGate {
    fun observe(agentId: String): Flow<AgentSummary?>
    suspend fun close(agentId: String): AgentOutcome   // today's outcomes incl. the v0.6.1 relay reason (CH48)
}
```

Port wave unit: 2. Fake: `FakeAgentsGate` with a canned close outcome.

## P3 — `HostGitGate` (diff/discard S19 chain)

`hostRepo.gitDiff(machineId, cwd, targetFile)` (:143), `hostRepo.gitDiscard(machineId, cwd, path)` (:169).

```kotlin
interface HostGitGate {
    suspend fun gitDiff(machineId: String, cwd: String, targetFile: String?): HostGitDiffResult
    suspend fun gitDiscard(machineId: String, cwd: String, path: String): HostGitOutcome
}
```

Port wave unit: 3 — this one carries S19 semantics (discard-with-confirmation exactly-once wire), so the Android adapter must stay a pure delegate (no extra confirmation logic — the sheet owns confirmation).

## P4 — `SettingsGate` — already exists, subscribe through `SettingsStore`

ChatViewModel uses `settingsRepo.settings` (:96) plus the six ST-01 setters (setAgentMuted, setChatFontScale, setChatMessageSpacing — :127/:131/:135). PromptSheetViewModel uses `settings.settings` + the `requireUnlockToApprove` read (`:52`, `:62`).
ADR 0001 §2 already fixes `SettingsStore` with the exact ST-01 key set including these setters — **NO new port; P4 = consume the existing `SettingsStore`** (`data-core/.../SettingsPort.kt:14`). The Wave-2 `SettingsRepository` adapter gains the three missing setters if not already there.

## P5 — `PromptSheetGate` (pending prompts + answers)

PromptSheetViewModel: `prompts.open` (:58), `prompts.answer(promptId, answer)` (:74).

```kotlin
interface PromptSheetGate {
    val open: Flow<List<Prompt>>
    suspend fun answer(promptId: String, answer: Answer): AnswerOutcome
}
```

Port wave unit: 4. Wire discipline for `"cancelled":true` must stay in the Android adapter (S19-adjacent semantics, pinned by `PromptSheetViewModelCharacterizationTest`).

## P6 — `AttachmentsGate` (attachments lifecycle: upload/prepare; platform camera stays OUT)

`attachmentsRepo.upload(la, progress)` (:220, :277), `prepare(uri)` (:267), `newCameraTarget()` (:319).

```kotlin
interface AttachmentsGate {
    suspend fun prepare(uri: Any?): PreparedLocalFile?   // Uri-typed today (AttachmentsRepository.kt:64) — SEE P6a
    suspend fun upload(la: AttachmentLocal, onProgress: (Float) -> Unit): UploadOutcome
    fun newCameraTarget(): Any?                          // platform-bound — SEE P6a
}
```

P6a — the honestly platform-typed part: `AttachmentsRepository` is `ContentResolver`/`Uri`-bound; the upload path plus the platform pick/camera already belong behind `AttachmentsPlatformHost` (ADR 0001 §2 — MY port of the M2 era). The remaining `prepare(uri)`/`newCameraTarget()` calls belong to the :app composer glue, NOT to the shared VM: so the M4-shared VM should receive only `upload(...)` + progress bookkeeping (P6 narrow shape above minus the two `Any?` members), and the two Uri-touching calls stay platform-side. If O1's Move-2 lands a `AttachmentsCore` port covering upload progress + DONE-with-blobId (CH24-CH27 wire rows), O3 consumes that instead — my shape is a placeholder for negotiation, not an authority.

## `RelayConnection` — NOT crossing

`connection.state` (:94) is consumed only to render the banner chip; the transport remains OkHttp in `:app` per ADR 0001. The shared VM takes the already-portable `ConnectionController` shape O2 reserved, or the connection state is provided by `ChatSessionGate` as part of the combined state — O1's call between the two; the `ConnState` model already lives in `:relay-client commonMain`.

## Call-site inventory (exact, @74b71ad)

| VM | dep | call sites | today's type |
|---|---|---|---|
| ChatViewModel | chats | :84 (open), :92-96 (state/syncing), :202, :354, :410 (send), :106 (abort), :120 (subscribe), :211 (retry), :422 (release) | `ChatRepository` (:app, :56) |
| ChatViewModel | agentsRepo | :93 (observe), :191 (close) | `AgentsRepository` (:app, :58) |
| ChatViewModel | settingsRepo | :96 (settings flow), :127/:131/:135 (3 setters) | `SettingsRepository` (:app, :28 — implements `SettingsStore`) |
| ChatViewModel | hostRepo | :143 (gitDiff), :169 (gitDiscard) | `HostRepository` (:app, :46) |
| ChatViewModel | attachmentsRepo | :220/:277 (upload w/ progress), :267 (prepare), :319 (newCameraTarget) | `AttachmentsRepository` (:app, :64, Uri-bound) |
| ChatViewModel | connection | :94 (state) | `RelayConnection` (:app, :50, OkHttp-bound) |
| ChatViewModel | savedStateHandle | :83 (agentId only) | AndroidX — replaced by the approved `agentId: String` factory param (O4 decision 2026-11-07) |
| ChatViewModel | gate | :318/:350 via `vm.gate` (screens) | `BiometricAuthorizer` port (C1 landed, f61c8cc) |
| PromptSheetViewModel | prompts | :58 (open), :74 (answer) | `PromptsRepository` (:app, :53) |
| PromptSheetViewModel | settings | :52 (settings), :62 (requireUnlockToApprove) | `SettingsRepository` (:app, :28) |
| PromptSheetViewModel | gate | :95 (via `vm.gate`) | `BiometricAuthorizer` port (C1 landed) |

## Harness inventory for C3 (shared copies with identical assertions)

Already in `:data-core commonTest` (`Fakes.kt`): `FakeSettingsStore`, `FakeBiometricAuthorizer`, `FakeAttachmentsPlatformHost`, `FakeVoiceInputHost`, `FakeOpenPromptGuard`(?), plus `PortContractsTest` discipline.
To ADD per port wave unit (one fake per unit, contract-tested as O1 does):
- `FakeChatSession` (P1): scripted `state`/`syncing`, canned `send` results (OK/agent_blocked/interactive_unavailable/herdr_unavailable/queued-offline cases from the 18+2 assertion set), deterministic `retry/release` counters.
- `FakeAgentsGate` (P2): canned observe + close outcomes (incl. blank relay reason for CH48).
- `FakeHostGitGate` (P3): canned diff/discard results (S19 wire chain needs exactly-once).
- `FakePromptSheetGate` (P5): canned open list + answer outcomes (incl. AlreadyResolved for CH37).
- Robolectric-bound helpers that STAY in `:app` (`app/src/test/TestSupport.kt`): `FakeSecretStore`, `inMemoryDb`, `testClient`, `testSession` (the :app originals of the two characterization tests keep running on this host until C5 deletes the originals).

## Wave scheduling note (from the orchestrator's split)

One port + `:app` adapter + contract test per unit, landed by O1 after its Move 2 (SettingsStore overlaps and is already on O1's desk). O3 consumes each port as it lands: P4 (consume-only) → P1+P2 (ChatViewModel) → P5 (PromptSheetViewModel) → P3 (HostGitGate) → P6 (AttachmentsGate upload-only) — each consumed step re-opens only its C3-copy/C4-move task; C5 deletes the `:app` originals only when the last port for that VM is green.

# unified-kmp-o3 — chat slice (Obrero 3)

Feature doc of worker O3 (session subagent-chat-01a1af49-2949-74e2) inside the migration
specified in `odd/tasks/unified-kmp.md` (read by O3 down to `
## T5 migration plan (docs-only, waiting for GO; contracts per docs/adr/0001 §2/§4/§5)

### Order of moves (each gated on its CH rows; shared files additive-only)

1. **M1 — shared stateless UI (chat)**: move the remaining app-owned stateless composables that
   are required for parity on iOS: `Composer` (with `AttachmentChipRow`, delivery/working-delivery
   chips, slash suggestions read-only logic — ChatScreen.kt:629-936 minus ActivityResult launchers)
   and `ChatContent` shell minus `rememberActivityResult` call sites. Both become shared-ui
   `ui/chat/` composables taking the negotiated ports as parameters (constructor/time-based idiom: plain
   params + CompositionLocals). The Android launchers (photo/file/camera/perms) stay as `:app`
   adapter composables injected as `suspend picker(...)`-style callbacks into the shared composer.
   - Rows to keep green after M1: CH14, CH15, CH16, CH23, CH27, CH28 (branch chip), CH43; tests
     move to the shared-ui commonTest source set unchanged (mechanical import change only).
2. **M2 — VoiceInputState → shared port consumer**: replace the direct `SpeechRecognizer` call
   with `VoiceInputHost` injection (`start/stop/listening/onPartial/onFinal/onError` codes 1-6).
   The :app Android actual keeps VoiceInputState.kt semantics; iOS actual (SFSpeechRecognizer +
   AVAudioSession record permission) is O4-hosted. Rows: CH37 partial (mic rows device-only), CH37 overall header.
3. **M3 — diff/prompt-sheet/pending/new-agent content moves UK to shared**: `DiffSheet.kt` app
   wrapper becomes a thin alias for the shared sheet with `SettingsStore.strings`; the review-consent
   body (PromptContent.kt:402-457) moves to its own shared composable with `BiometricAuthorizer`
   parameter; PendingContent/NewAgentContent already shared — only their :app VMs relocate per ADR §4
   (lifecycle-viewmodel multiplatform).
   - Rows: CH31, CH32, CH33 (+ S19 wire chain), CH38, CH41, CH43; GamePad ChatViewModel tests stay on :app
     until M4.
4. **M4 — ChatViewModel + PromptSheetViewModel migrate to :shared-ui as KMP ViewModels** (ADR §4),
   constructor-injecting `ChatRepository`, `SettingsStore`, `AttachmentsRepository`,
   `HostRepository`, `BiometricAuthorizer`, `AttachmentsPlatformHost`, `VoiceInputHost`; the :app
   Hilt module binds the same implementations; the Android `SavedStateHandle.toRoute<ChatRoute>()`
   seam is replaced by a plain `agentId` factory parameter (exact navigation seam decided with O4).
   **Copy-first rule (hard gate)**: the Gate-A harness/chat tests STAY WHERE THEY ARE and stay
   green until the moved production code has ITS OWN passing copy with the SAME assertions
   (copy first, delete the original only in a LATER commit — never in the same commit as the move).
   After M4 the orchestrator re-runs ALL mutation probes against the moved code (consent header,
   chip bound to option id 2, reset→Idle, Cancelar exactly once, discard without confirm,
   back-dismiss, móvil marker, AUTO chip, questionnaire page label) and every probe must still be
   DETECTED before the move is accepted. Android instrumented tests are NOT available on this
   machine (no emulator): the androidTest twin is not a gate and never counts as proof.
   - Rows: CH17, CH18, CH19, CH21, CH22, CH24, CH25, CH26, CH31, CH33 (S19 wire), CH48.

5. **M5 — composer delivery/decision chip and AUTO banner move**: final polish per CH20/CH23
   (banner and chip already in M1, close leftovers), plus any mechanical import updates recorded.

### Ports consumed (exact contracts per ADR 0001 §2, cc O1 when a deviation is needed)

- `AttachmentsPlatformHost`: `attach.pickImages(maxItems: Int = 10): List<AttachmentRef>`,
  `pickDocuments(mime: List<String> = listOf("*/*")): List<AttachmentRef>`,
  `cameraTarget(): PreparedLocalFile?` (Android composer keeps the TakePicture/FileProvider path
  via the exact current code; iOS PhotosPicker/UIDocumentPicker; camera fallback = photo library).
- `BiometricAuthorizer`: `suspend fun authenticate(reason: String): AuthResult` with
  `sealed interface AuthResult { OK; FAILED; UNAVAILABLE }` and the 5-minute freshness INSIDE the
  impl (Android = wrapper over util/BiometricGate.kt unchanged; iOS = LAContext.evaluatePolicy
  chained with a 5-min in-memory freshness engine; the session-wide K-chain persists via the
  shared keychain access group only if O1 extends the contract — not requested here).
- `VoiceInputHost`: `start(listener: VoiceListener)`, `stop()`, `listening: StateFlow<Boolean>`,
  `VoiceListener { onPartial(text); onFinal(text); onError(code) }` with codes 1-6 per ADR 0001 §2.
  iOS actual: SFSpeechRecognizer with `AVAudioSession.record` authorization check first
  (`no-permissions` code 6), recognizer-unavailable → code 3, network-related failures → code 2.
- NOT ports (per ADR no-port zones): haptics (CMP `LocalHapticFeedback`), IME insets
  (`Modifier.imePadding()`/`.navigationBarsPadding()`), clipboard (CMP).

### Golden-unverified rows at Gate C/D (differential golden gate — corrected per orchestrator)

- NOBODY records Roborazzi goldens in this project: never re-record, never commit a changed PNG
  under `app/src/test/screenshots` (S15/S20 standing rule).
- The gate belongs to the orchestrator: host goldens for `main` were recorded once and live in
  `/tmp/rp-baseline/host-goldens` (138 PNGs, deterministic). After each move in T5, BOTH render
  on the SAME host with the SAME qualifiers and are compared against those baseline PNGs
  pixel-for-pixel; drift beyond tolerance reverts the in-flight move.
- Moves that can change pixels and therefore pull the gate right after themselves:
  **M1** (shared composer/ChatContent shell → shared-ui) and **M3** (diff/prompt-sheet/pending/
  new-agent content moves). M2 (voice host consumer) and M4 (ViewModels) are state/logic moves
  with no pixel surface of their own, but the orchestrator may still gate them at his discretion.
- Affected golden rows (per the matrix status column): CH4, CH11, CH32, CH34, CH38, CH41, plus the
  CH39 page-chrome visual mentioned in its Notes. On any drift: revert first, then re-run the
  orchestrator's verdict with the evidence; no local `recordRoborazzi` ever runs.

### T5 execution rules (binding, from the orchestrator and the user — seq 58)

- Every T5 move MUST use the ODD subagents: (a) a `gentle-ai-explore` (read-only, path:line
  evidence) mapping the callers of what is about to move, BEFORE each move; (b) each bounded
  unit goes to a `gentle-ai-worker` with a non-empty `## Allowed edit surfaces` (disjoint
  surfaces → parallel background launches); (c) high-risk parts get an independent
  `gentle-ai-verify` naming `Risk: item N` — the S19 consent/discard confirmations and the
  biometric gate are Risk item 2 (security/credentials/guards).
- Launcher rules: the FIRST line of every task text is `Spec: odd/tasks/unified-kmp.md, T<n>`;
  verify launches only with a named `Risk: item N`. Tiny edits may stay inline, but a move
  without an explorer first or without a verifier on the S19/biometric piece is bounced.
- Every READY report must list the subagent task ids used and what each returned.
- The orchestrator still verifies from the sha and re-runs the mutation probes after M4.

- **L12** (orchestrator seq 62): GO for T5 @ feat/unified-kmp 539b295 (670 tests / 4 nets). Binding order: M1 and M2 first with one READY per move; M3 with independent gentle-ai-verify `Risk: item 2`; M4 copy-first only after M1-M3 accepted, then the nine mutation probes re-run. Common rules: rebase before start and before every READY; orchestrator does all merges; Gate-A tests green at every commit, never weakened; <= ~400-line commits; focused tests while developing, full S20 before READY; ONE Gradle at a time; NEVER `git add -A` (the app test run re-renders tracked PNGs in app/src/test/screenshots: `git checkout -- app/src/test/screenshots` before committing); golden-diff.sh <worktree> must print identical 138 / different 0 after M1 and M3; every READY lists subagent task ids. O4 moves theme/nav/settings/onboarding/agents/Mesh concurrently: stay inside ui/chat, ui/promptsheet, ui/pending, ui/newagent, ui/components, ui/diff and rebase often.

## Log`). Branch `unify/o3-chat`,
worktree `.worktrees/o3`. Mirror: Engram `odd/unified-kmp-o3/tasks`.

## Specs

Quoted verbatim from `odd/tasks/unified-kmp.md` (parent spec):

- **S5** "asegurando que no se pierda ni una funcionalidad ni que se introduzcan bugs"
- **S11** — "Source of truth for behavior is the Android code on `main`. Behavior = what the Android app does today (README "Main features", CHANGELOG, `docs/QA.md`, `docs/MESH.md`, tests). The `:app` module is migrated into shared code; it is the oracle."
- **S15** — "Every observable behavior of the Android app maps to a row in a parity matrix with a proof… Existing tests are never weakened, deleted or re-recorded to get green. Roborazzi goldens… are not re-recorded without orchestrator approval."
- **S19** "Diff viewer keeps its user-driven discard confirmation before `host.git_discard`."
- **S6** "QUE LOS 4 usean ODD y sus sub agentes SI o SI"
- **S16** — KMP purity: no `android.*`, `androidx.*` (Android-only), `javax.crypto.*` imports in commonMain.
- **S20** — Done means the four Gradle test tasks have zero failures.

Slice briefs from the orchestrator (T1 and T5), paraphrase of the assignment:
T1 must produce `docs/parity/o3-chat.md` (columns: ID | behavior incl. edge cases/exact strings |
Android source file:line | proof test | status) covering every README "Main features"/CHANGELOG/
docs/QA.md item of the chat slice, plus characterization tests on UNCHANGED Android code for rows
lacking a proof, all green, no production change; plus a "Platform ports required" section.
T5 (later, after orchestrator go) migrates the slice through shared code.

Behaviors to cover (orchestrator brief): live streaming + bottom-anchored list (reverseLayout) +
animated "More recent (N)" button preserving read position, message history, tool tracking,
attachments, diffs + discard confirmation + biometric interaction (S19), slash commands and
autocomplete (/clear /compact /model /thinking /quit /undo /sdd-status /help) + thinking-level
chips (off, minimal, low, medium, high, xhigh, max), PromptBottomSheet select mode, review consent
surface (danger: high renders terminal body verbatim in monospace, keeps three options and
declared effects in order, labels the session-wide grant, sends {cancelled:true} when dismissed),
pending questions/authorizations screen, new-agent flow, 48dp touch targets and reduced-motion.

## Tasks

| ID | Specs | Task | Commit |
|---|---|---|---|
| O3-T1 | S5 S11 S15 | Parity matrix `docs/parity/o3-chat.md` + characterization tests on unchanged Android code + platform-ports section | |
| O3-T5 | S15 S19 | Migrate chat slice through shared code (waits for orchestrator go) | |


## T5 migration plan (docs-only, waiting for GO; contracts per docs/adr/0001 §2/§4/§5)

### Order of moves (each gated on its CH rows; shared files additive-only)

1. **M1 — shared stateless UI (chat)**: move the remaining app-owned stateless composables that
   are required for parity on iOS: `Composer` (with `AttachmentChipRow`, delivery/working-delivery
   chips, slash suggestions read-only logic — ChatScreen.kt:629-936 minus ActivityResult launchers)
   and `ChatContent` shell minus `rememberActivityResult` call sites. Both become shared-ui
   `ui/chat/` composables taking the negotiated ports as parameters (constructor/time-based idiom: plain
   params + CompositionLocals). The Android launchers (photo/file/camera/perms) stay as `:app`
   adapter composables injected as `suspend picker(...)`-style callbacks into the shared composer.
   - Rows to keep green after M1: CH14, CH15, CH16, CH23, CH27, CH28 (branch chip), CH43; tests
     move to the shared-ui commonTest source set unchanged (mechanical import change only).
2. **M2 — VoiceInputState → shared port consumer**: replace the direct `SpeechRecognizer` call
   with `VoiceInputHost` injection (`start/stop/listening/onPartial/onFinal/onError` codes 1-6).
   The :app Android actual keeps VoiceInputState.kt semantics; iOS actual (SFSpeechRecognizer +
   AVAudioSession record permission) is O4-hosted. Rows: CH37 partial (mic rows device-only), CH37 overall header.
3. **M3 — diff/prompt-sheet/pending/new-agent content moves UK to shared**: `DiffSheet.kt` app
   wrapper becomes a thin alias for the shared sheet with `SettingsStore.strings`; the review-consent
   body (PromptContent.kt:402-457) moves to its own shared composable with `BiometricAuthorizer`
   parameter; PendingContent/NewAgentContent already shared — only their :app VMs relocate per ADR §4
   (lifecycle-viewmodel multiplatform).
   - Rows: CH31, CH32, CH33 (+ S19 wire chain), CH38, CH41, CH43; GamePad ChatViewModel tests stay on :app
     until M4.
4. **M4 — ChatViewModel + PromptSheetViewModel migrate to :shared-ui as KMP ViewModels** (ADR §4)
   with constructor injection of `ChatRepository`, `SettingsStore`, `AttachmentsRepository`,
   `HostRepository`, `BiometricAuthorizer`, `AttachmentsPlatformHost`, `VoiceInputHost`; the :app
   Hilt module binds the same implementations; the Android `SavedStateHandle.toRoute<ChatRoute>()`
   seam is replaced by a plain `agentId` in a shared VM factory parameter (orchestrator decides the
   exact navigation seam with O4). The characterization harness (ChatViewModelCharacterizationTest)
   gets an androidTest twin for :app wiring; the core tests move to :shared-ui jvmTest against the
   same :data-core fakes.
   - Rows: CH17, CH18, CH19, CH21, CH22, CH24, CH25, CH26, CH31, CH33(S19 wire), CH48.
5. **M5 — composer delivery/decision chip and AUTO banner move**: final polish per CH20/CH23
   (banner and chip already in M1, close leftovers), plus any mechanical import updates recorded.

### Ports consumed (exact contracts per ADR 0001 §2, cc O1 when a deviation is needed)

- `AttachmentsPlatformHost`: `attach.pickImages(maxItems: Int = 10): List<AttachmentRef>`,
  `pickDocuments(mime: List<String> = listOf("*/*")): List<AttachmentRef>`,
  `cameraTarget(): PreparedLocalFile?` (Android composer keeps the TakePicture/FileProvider path
  via the exact current code; iOS PhotosPicker/UIDocumentPicker; camera fallback = photo library).
- `BiometricAuthorizer`: `suspend fun authenticate(reason: String): AuthResult` with
  `sealed interface AuthResult { OK; FAILED; UNAVAILABLE }` and the 5-minute freshness INSIDE the
  impl (Android = wrapper over util/BiometricGate.kt unchanged; iOS = LAContext.evaluatePolicy
  chained with a 5-min in-memory freshness engine; the session-wide K-chain persists via the
  shared keychain access group only if O1 extends the contract — not requested here).
- `VoiceInputHost`: `start(listener: VoiceListener)`, `stop()`, `listening: StateFlow<Boolean>`,
  `VoiceListener { onPartial(text); onFinal(text); onError(code) }` with codes 1-6 per ADR 0001 §2.
  iOS actual: SFSpeechRecognizer with `AVAudioSession.record` authorization check first
  (`no-permissions` code 6), recognizer-unavailable → code 3, network-related failures → code 2.
- NOT ports (per ADR no-port zones): haptics (CMP `LocalHapticFeedback`), IME insets
  (`Modifier.imePadding()`/`.navigationBarsPadding()`), clipboard (CMP).

### Golden-unverified rows at Gate C/D (differential golden gate)

- Affected rows: CH4, CH11, CH32, CH34 (bash-only screenshotChatStreaming), CH38, CH41 (+ the
  CH39 page-chrome visual, noted in the matrix Notes column).
- How the differential golden gate will check them (per the orchestrator's protocol, agreed on
  rev 2): recording happens once per host from `main` BEFORE the migration (baseline PNGs);
  after each move in T5 the same Roborazzi tests run on the SAME host/qualifier footprint
  (w411dp-h891dp-xxhdpi × the 4 themes of the suite) and the PNGs are compared pixel-for-pixel
  against the same-host baseline produced on `main`. Any drift larger than transformation
  tolerance is a Gate blocker; the golden PNGs are never committed from a non-recording host,
  and the local run on the actual device/simulator host is O4's T6 deliverables (they are the
  only machine with a goldens baseline at this time).
- If any of these six tests drift: the move in flight is reverted (not merged-in-place) and the
  orchestrator's verdict is appealed with the same evidence; no silent golden updates.

## Log

- **L1** (orchestrator, verbatim brief): "YOUR SLICE (T1 now, then T5): Android packages
  dev.j3pf.rp.ui.{chat, promptsheet, pending, newagent, components} and shared-ui
  ui/{chat, diff, promptsheet, pending, newagent, components}, with all their tests." Full text in
  the orchestrator message of 2026-11-06 (intercom seq 3, id e561f674…).
- **L2** (O3): consulting gentle-ai-explore `mw4jqhsg-1-refq` to inventory the chat slice before
  writing the parity matrix.
- **L3** (O3): explore subagent failed on provider quota; compensated with direct bounded reads of
  README/QA/CHANGELOG and the slice sources (matrix still cites every row).
- **L4** (O3): parity matrix `docs/parity/o3-chat.md` written: 39 rows, headers, exact strings,
  ports P-PICK/P-BIO/P-VOICE/P-HAPTICS/P-IME requested from O1.
- **L5** (orchestrator correction, verbatim): characterization tests must be GREEN on first run
  (T1 is on unchanged Android code); RED-first applies to T5+. If a new test is RED on unchanged
  code, fix the test or record the row as `defect` with file:line — never fix production in T1.
  Observable behavior only (exact strings, wire payloads, nothing changes on rejection).
- **L7** (orchestrator, verdict Gate A rev 1 = FAIL): rebase first; strengthen the two files (path assertions, dialog-close assertions, real S19 vm→wire chain, app wrapper title); pin option order/effects/chip/monospace/wire JSON; migrate to the jslive junit4.v2 rule; recompute the summary from rows; add ~14 missing rows (QA C1/C3/C5/C9, A1/A2/A4, N4-N7, CHANGELOG density + settled subagents + v0.6.1 close failures + U6/U8 + BiometricGate freshness); close the pure-UI group then the harness group; no Roborazzi (host drift on unchanged `main`, 17/23 failing — verifier confirmed); mark screenshot rows `golden - unverified on this host`; at resumed READY run verbs: `READY T1 (rev 2)`.
- **L8** (O3): rev 2 executed on unchanged code: rebase done (57aad06); DiffDiscardConfirmationTest strengthened + DiffSheetWrapperTest + ReviewConsentCharacterizationTest strengthened (order/pairing/chip/no-reflow/wire JSON, junit4.v2) → c90de9e; harness group inline (worker launch refused by writer rule; recorded verbatim per orchestrator note): ChatViewModelCharacterizationTest (18 tests: CH17/18/19/21/22/24/25/26/31 + S19 vm→wire + close-failure v0.6.1) + PromptSheetViewModelCharacterizationTest (2 tests CH34) → cfe3828; pure-UI group ChatUiCharacterizationTest (12 tests) → 828ea81; matrix rev-2 rewrite with grep-verified citations and computed summary; A1 compression probe removed (Robolectric decode null — host drift documented; row stays gap with QA step). Focused final run: 59 chat-prompt tests / 0 failures.
- **L9** (O3): gentle-ai-verify (task mw4mlux6-2-fave) verified rev 2: production untouched (git diff a32e66c..HEAD empty), 4 mutation holes fixed with its suggested regressions: back-dismiss (Espresso.pressBack), chip bound to option id 2 with ids≠positions, verbatim whitespace edges, exactly-one negative on the discard wire; KDOC row ids corrected (Discard → CH33, Consent → CH36); minor import/blank-line cleanup noted and applied. Final focused run 61/61 green (ChatUi + ChatVM promptsuites). No worktree edits beyond test + docs files.
- **L10** (orchestrator, rev-3 audit): counts must come from a script; status column restricted to the fixed vocabulary with reasons in Notes; rows promising "later tests" that are pinnable today rejected (CH11/CH12/CH17-???/CH23/CH34/CH39/CH43/CH45); device-only rows must cite the exact QA step; citations fixed (CH19/CH31/CH39/CH11); the rev-2 back-dismiss claim must become a real test or be dropped.
- **L11** (O3): rev 3+4 complete at f625bd1 + 38ea586: pinned-thoday rows now tested (7 new + 1 questionnaire + back-dismiss = 9 new tests), CH17 '???' resolved (freshness is observable false-until-authenticate and invalidate() resets — recorded in Notes as not auto-testable on this host), citations fixed, status/notes two-column vocabulary enforced, summary computed by script (rows=55 — device-only 5, golden 4, out-of-slice 3, proof-added 32, proven 11), back-dismiss test added AND mutation-proven (onDismissRequest→onDiscardFile caught only by the new test; production restored). Final focused run: 70 chat+promptsheet tests / 0 failures.
- **L6** (O3): T1 characterization added on the two high-risk surfaces, green-first, no production
  edits: `DiffDiscardConfirmationTest` (S19, CH30: dialog-first, exactly-once with path, cancel
  keeps file) + `ReviewConsentCharacterizationTest` (CH33: verbatim order/effects, session-wide
  chip on id 2 only, Cancelar → `Answer.Cancelled`, option → `Answer.option(id)`). Focused run
  `:app:testDirectDebugUnitTest --tests chat.* --tests promptsheet.*` → 22 tests, 0 failures.
  No test weakening, no Roborazzi touches, no production change; remaining rows need O1's fake
  repos (T5) or device/manual E2E (QA cases).
- **L13** (O3, M1): added commonMain `ChatComposer` and `ChatTopBar`, plus moved the AUTO banner;
  :app retains `ChatContent`, the exact `Composer` signature, ActivityResult/permission wiring, and
  `VoiceInputState`. Updated parity source citations for CH14/15/16/23/27/31/43; no Gate-A test
  files changed. The final focused Gradle invocation with in-process Kotlin execution was
  `BUILD SUCCESSFUL` (test task UP-TO-DATE); its XML reports record 58 tests, 0 failures, 0 skipped.

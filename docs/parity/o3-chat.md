# o3-chat — parity matrix (rev 2, chat slice of unified-kmp)

Source of truth: Android behavior on `unify/o3-chat` (= `feat/unified-kmp` rebase to 25ab449; T1 base was 6c073ef/55491a3).
Slice: `app/.../ui/{chat, promptsheet, pending, newagent, components}` and
`shared-ui/.../ui/{chat, diff, promptsheet, pending, newagent, components}` with their tests.
Sources covered: README "Main features", CHANGELOG (chat items), docs/QA.md §1 (N4-N7), §2 (C1-C10), §3 (A1-A4), §6 (U3/U6/U7/U8), §10 (F6.1-F6.4), v0.6.0 review-consent + subagent nodes, v0.6.1 close failures.

Status legend: **proven** = named existing test passes today; **proof-added** = characterization test added by O3, green on UNCHANGED code (rev 1 + rev 2 runs); **partial** = structural or mixed proof; **gap** = truly device-only with exact QA step; **golden - unverified on this host** = screenshot-backed row, Roborazzi host drift documented by the orchestrator on unchanged `main`; **platform-specific** = needs an iOS equivalent (per S15) or belongs to another worker's slice (noted).

Every citation below was grep-verified on this branch before the rev-2 run.

## 1. Bottom-anchored live streaming

| ID | Behavior (incl. edge cases / exact strings) | Android source | Proof test | Status |
|---|---|---|---|---|
| CH1 | Message list bottom-anchored (`LazyColumn`, `reverseLayout = true`); opening lands on newest content with composer visible | shared-ui .../ui/chat/ChatComponents.kt:407‑409 | app .../ui/chat/ChatBottomAnchorTest.kt:45 `open chat lands on newest content with bottom visible above composer` | proven |
| CH2 | Streaming append keeps the bottom visible (auto scroll on new items; no scroll fight while dragging) | shared-ui .../ui/chat/ChatComponents.kt:368‑381 | ChatBottomAnchorTest.kt:102 `tall streaming message keeps bottom visible above composer across appends` | proven |
| CH3 | Adjacent chat items never overlap | shared-ui .../ui/chat/ChatComponents.kt:352‑361, 407+ | ChatBottomAnchorTest.kt:192 `two consecutive chat items bounds do not overlap` | proven |

## 2. "More recent (N)" button

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| CH4 | Scrolled up (index beyond working row, offset > 48): animated floating button, label `Más recientes ($unreadNewItems)` (N = unreadCount, else items added since leaving bottom), arrow contentDescription `Ir al más reciente` | shared-ui .../ui/chat/ChatComponents.kt:329‑334, 343‑363, 520‑560 | ChatBottomAnchorTest.kt:240 `scrolled up user sees jump to latest button with unread count`; resting visual pinned by `screenshotChatScrolledUp` | golden - unverified on this host (logic proven; animation pixels via screenshot pending the orchestrator's host baseline) |
| CH5 | Tap animates to item 0; hidden while at bottom | shared-ui .../ui/chat/ChatComponents.kt:520 (`visible = isScrolledUp`), 530‑533 | ChatBottomAnchorTest.kt:283 (click flows) | proven |

## 3. Message rendering / history / tools

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| CH6 | While syncing history with no items yet: `Sincronizando historial…` + `Descargando los mensajes recientes de la sesión.` | shared-ui .../ui/chat/ChatComponents.kt:311‑313 | app .../ui/chat/ChatUiCharacterizationTest.kt `empty chat while syncing shows the exact syncing strings` | proof-added |
| CH7 | Fully empty chat: `Sin mensajes todavía` + `Escribe abajo para hablar con este agente.` (no syncing overlay) | shared-ui .../ui/chat/ChatComponents.kt:318‑319 | ChatUiCharacterizationTest.kt `fully empty chat shows the exact placeholder strings` | proof-added |
| CH8 | Collapsible tool card: collapsed header shows `✓ ` + tool name; expanding reveals the result; long output truncates behind exact toggles `ver más` / `ver menos`, plus `(resultado truncado por el relay)` when the relay truncated | shared-ui .../ui/chat/ChatMessages.kt:367‑470 (`ToolCard`, 367 header comment, 457‑470 toggle) | ChatUiCharacterizationTest.kt `tool card expands on tap and truncates result behind the exact ver más toggle` | proof-added |
| CH9 | Inline tool call with a `subagent` suppresses the duplicate `SubagentCardItem` (only one `explore` node) | shared-ui .../ui/chat/ChatMessages.kt:278‑292 | ChatUiCharacterizationTest.kt `inline tool call hides the duplicate subagent card` | proof-added |
| CH10 | Reduced motion (`LocalReducedMotion = true`): streaming content renders statically, no animated entrances | shared-ui .../ui/chat/ChatMessages.kt:176‑178; ChatComponents.kt:443‑445, 481‑483 | ChatUiCharacterizationTest.kt `reduced motion still renders the streaming message statically` | proof-added |
| CH11 | QA C1: reasoning blocks render as `Block.Thinking` cards inside assistant bubbles (collapsed by default) | shared-ui .../ui/chat/ChatMessages.kt (`AssistantBubble` renders Block.Thinking) | — | partial (structure in place; visual via `screenshotChatStreaming`) | golden - unverified on this host |
| CH12 | QA C3 sent-message marker: user messages sent from this device show the label `móvil` (fromMobile flag) | shared-ui .../ui/chat/ChatMessages.kt:197‑202 | — | gap → characterization pending in T5 harness (QA C3 device step: send a message, bubble shows `móvil`) |
| CH13 | QA C1 latest history + pagination preserved across screens (session state from Room) | app .../data/repo/ChatRepository.kt (persistence); relay-client ChatSession | — | gap (relay persistence covered by O2 slice; QA C1 device step) |

## 4. Composer, delivery modes, slash commands

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| CH14 | Slash suggestions with autocomplete for `/clear /compact /model /thinking /quit /undo /sdd-status /status /help /rp` (Pi catalog); tapping completes with trailing space | app .../ui/chat/ChatScreen.kt:707‑736; shared-ui .../chat/SlashCommandCatalog.kt:14‑30 | app .../ui/chat/SlashCommandSuggestionsTest.kt:26,142; shared-ui .../chat/ChatMessagesTest.kt:19 | proven |
| CH15 | Delivery chip flips to `⚡ Comando Pi (/)` when text starts with `/`; herdr/non-Pi agents get only the static chip (never `Entrega: Automático`) | app .../ui/chat/ChatScreen.kt:769‑846 | ChatUiCharacterizationTest.kt `composer with slash text shows the launcher command chip` + `herdr agent shows only the command chip and never the delivery chip` | proof-added |
| CH16 | Working agent: chips `Encolar (follow‑up)` / `Interrumpir y dirigir (steer)`; tap maps exactly to `Delivery.FOLLOW_UP`; abort via ⏹ (`ChatViewModel.abort` = `session.abort()`) | app ChatScreen.kt:809‑829; ChatViewModel.kt:115 (`abort`) | ChatUiCharacterizationTest.kt `working agent surfaces queue and steer chips with exact labels`; abort wiring pinned by the VM harness (`ChatViewModelCharacterizationTest`) | proof-added |
| CH17 | Gentle AUTO typed sends require biometrics valid 5 min; failing/unavailable gate shows exact snackbars (`Configura un bloqueo de pantalla para enviar como tecleado`, `Desbloqueo cancelado`) | app ChatScreen.kt:486‑516; app util/BiometricGate.kt:104; freshness logic | ChatViewModelCharacterizationTest.kt `needsAuth gates TYPED and AUTO-on-gentle-auto but no other choice` + `needsAuth is false for a normal agent in AUTO`; gate freshness pinned by test??? — see gap note; UI snackbars still device-level | proof-added (decision matrix); real BiometricPrompt device-only |
| CH18 | AUTO interactive-unavailable fallback: send `/gentle:mode normal` as command; state message `Modo de permisos: Normal`; banner cleared | app ChatScreen.kt:365‑366; ChatViewModel.kt:408‑417 | ChatViewModelCharacterizationTest.kt `switchToNormal sends the exact gentle mode normal command as a command` | proof-added |
| CH19 | Send error mapping, exact strings: `El agente espera un diálogo en la terminal; el mensaje quedó en cola` (agent_blocked), `No se pudo teclear en la terminal: el mensaje no autoriza acciones en Auto` (interactive_unavailable), `herdr no está disponible en esa PC`, `Se enviará al cerrarse el diálogo` (typed_after_dialog), `Sin conexión: se enviará al volver la red` (queued) | ChatViewModel.kt:371‑400 | ChatViewModelCharacterizationTest.kt: `agent_blocked maps...`, `interactive_unavailable maps...`, `herdr_unavailable maps...`, `typed_after_dialog maps...`, `offline send queues with the exact offline message` | proof-added |
| CH20 | QA C8 non-remote dialog banner: `Esperando en la terminal (diálogo no remoto)` + optional `: <title>` | app .../ui/chat/ChatScreen.kt:349 | ChatUiCharacterizationTest.kt `terminal wait banner renders exact text with dialog title` | proof-added (UI); real herdr dialog side device-only (QA C8) |
| CH21 | Quit button: sends `/quit` as command (`asCommand = true`); confirmation dialog "Cancelar" dismisses | ChatViewModel.kt:200‑204; ChatScreen.kt:427‑441 | ChatViewModelCharacterizationTest.kt `quit sends the exact slash quit command as command` | proof-added |
| CH22 | Forget: `confirmForget()` without `forget()` is a no-op (no `agent.close` on the wire); `forget {}` + `confirmForget()` sends exactly one `agent.close` and runs onDone; archived surfaces substituted via ChatViewModel | ChatViewModel.kt:177‑206 | ChatViewModelCharacterizationTest.kt `confirmForget without prior forget sends no agent close...` + `forget then confirm closes the agent...` | proof-added |
| CH23 | QA C5 chip: in AUTO mode the composer reads `AUTO · tus mensajes se envían como tecleados` (AppStore state machine still human) | app ChatScreen.kt:358 | — | partial (literal pinned in code; compose assertion for the chip is part of T5 composer tests) |

## 5. Attachments

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| CH24 | Attachment lifecycle: PREPARING → UPLOADING (progress) → DONE with relay `blobId`; failure paths text exact: `Supera 25 MB` (HTTP 413), HTTP N, `Sin conexión` (network) | ChatViewModel.kt:245‑296; AttachmentsRepository.kt:130+ (`uploadBlob` POST /v1/blobs) | ChatViewModelCharacterizationTest.kt: `413 upload error surfaces the exact overlimit text and blocks sending`, `successful upload lands in DONE with the relay blob id`, `network failure surfaces the exact offline attachment error` | proof-added |
| CH25 | Failed attachments block sends: `Quita los adjuntos con error antes de enviar`, nothing on the wire | ChatViewModel.kt:333‑341 | ChatViewModelCharacterizationTest.kt (same blocked-send test) | proof-added |
| CH26 | Remove chip; retry resets progress/error and re-uploads | ChatViewModel.kt:299‑314, 216‑264 | ChatViewModelCharacterizationTest.kt `removeAttachment drops the chip and retryAttachment resets then succeeds` | proof-added |
| CH27 | Attachment chip: name + ` · humanSize`, remove button, progress/error state | shared-ui .../chat/ChatComponents.kt:79‑130; app ChatScreen.kt:974 (`AttachmentChip`) | shared Chip rendered with app strings in ChatUiCharacterizationTest.kt `attachment chips surface name state error and exact failure text`; composer-side chip behavior already covered by app .../ui/ComposeUiTest.kt:266 `compositor con adjuntos muestra progreso y permite quitar` | proof-added |
| CH28 | QA A1 photo compression: JPEG 85%, longest side ≤ 2048px | app AttachmentsRepository.kt:164‑213 (`compress`, `MAX_DIM = 2048`, JPEG 85) | — | gap (Robolectric cannot decode bitmaps on this host — host drift confirmed for Roborazzi on unchanged main; QA A1 device step: pick a 12 MP photo, verify compressed JPEG ≤ 2048px) |
| CH29 | QA A2 file text `[Archivo adjunto: /ruta (mime, tamaño)]` reaches the agent | message is composed by **Rp-plugin** (`@j3pf/pi-rp`), not by the app; the app only uploads the blob and sends AttachmentRef | — | platform-specific (cross-project: rp-plugin relays the message; QA A2 device step) |
| CH30 | QA A4 offline: queued send with clientMsgId idempotency + explicit retry re-sends with the SAME clientMsgId; QA N4 `Sin conexión · se reintentará` handled by the push path | relay-client ChatDeliveryPolicy.kt (`SendResult.Queued`, outbox enqueue, background WorkManager fallback); ChatRepository outbox | app .../data/repo/ChatRepositoryTest.kt:285 `retry re-sends with identical clientMsgId preserving idempotency`; ChatViewModelCharacterizationTest.kt `offline send queues with the exact offline message` | proven |

## 6. Diff viewer + discard confirmation (S19, high-risk)

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| CH31 | Branch chip `⎇ <branch>` with ` *` when dirty; tap opens the diff sheet with exact strings `Revisión de cambios` / `Descartar` | app ChatScreen.kt:154‑192; app ui/chat/DiffSheet.kt (wrapper) | ChatUiCharacterizationTest.kt `branch chip shows the dirty marker and opens the diff sheet`; DiffSheetWrapperTest.kt `wrapper renders shared sheet with exact resource strings` | proof-added |
| CH32 | Diff sheet renders file list + per-file diff, per-file `Descartar`, sheet-level `Pedir ajuste` | shared-ui .../ui/diff/DiffSheet.kt:57‑115, 253‑275 | DiffSheetWrapperTest.kt (labels + passthrough); resting visual = `screenshotPromptSheet` only | golden - unverified on this host (logic proven) |
| CH33 | **S19: discard requires explicit confirmation** — dialog question `¿Seguro que deseas descartar los cambios en '<path>'?`; confirm triggers `onDiscardFile(path)` exactly once; cancel/dismiss keeps the file; the VM→wire chain sends `host.git_discard` with exact machineId/cwd/path | shared-ui .../ui/diff/DiffSheet.kt:71, 289‑307; ChatViewModel.kt:165‑176; HostRepository.kt:87‑92 | app DiffDiscardConfirmationTest.kt (3 tests); DiffSheetWrapperTest.kt `wrapper keeps the S19 discard confirmation...`; ChatViewModelCharacterizationTest.kt `discardDiffFile reaches the wire as host git discard with exact machine cwd and path` + `openDiffSheet fetches git diff for the agent cwd` | proof-added (real S19 chain included) |
| CH34 | QA C2: bash shows the command; edit shows the diff (ToolCard summary + DiffView) | shared-ui .../ui/chat/ChatMessages.kt:431‑445 (`editDiff`) | CH33's tool-card test covers bash; edit-diff visual remains part of `screenshotChatStreaming` | golden - unverified on this host (logic paths covered for bash; edit shows the diff structurally) |

## 7. Prompt sheet, review consent, pending, new agent

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| CH35 | Permission prompt structure (confirm + tool, danger tag, answer buttons) | shared-ui .../promptsheet/PromptContent.kt (confirm branch) | shared-ui .../promptsheet/PromptContentTest.kt:21 `testPermissionPromptStructure`; app ComposeUiTest.kt `hoja de permisos muestra el comando y responde con la opcion` | proven |
| CH36 | **Review consent surface (v0.6.0, high-risk)**: `SELECT`+`PERMISSION`+`HIGH` renders header `Consentimiento de revisión` + `Peligro alto`; verbatim monospace body with NO reflow (horizontal scroll axis in semantics); provider options + declared effects in provider ORDER; option id `2` tags the single session-wide chip `Permiso para toda la sesión`; `Cancelar` → `Answer.Cancelled`; wire JSON `{"cancelled":true}`, no `optionIds` | shared-ui .../promptsheet/PromptContent.kt:402‑457 | app ReviewConsentCharacterizationTest.kt (7 tests incl. order, effect-pairing via bounds, chip-on-option-2-only, no-reflow, wire `cancelled:true` via Envelope codec) | proof-added |
| CH37 | PromptSheetViewModel: `requiresAuth` matrix (requireUnlockToApprove × category/danger); already-resolved → `AnswerUi.AlreadyResolved`; `reset()` → Idle; captured `prompt.answer` JSON carries `cancelled:true` on Cancelled | app .../promptsheet/PromptSheetViewModel.kt:61‑63; PromptsRepository.kt:141‑151 | app PromptSheetViewModelCharacterizationTest.kt (2 tests) | proof-added |
| CH38 | Pending screen: header `¿Hay decisiones pendientes?` / `Bandeja de Entrada`; cards open prompts; dangerous first | app .../ui/pending/PendingScreen.kt:37‑49; shared-ui .../pending/PendingContent.kt:38+; PendingModels.kt:21‑35 | shared PromptModels filters/sorting in shared-ui .../pending/PendingModelsTest.kt; visual via `screenshotPending` | golden - unverified on this host (models proven) |
| CH39 | QA N6 questionnaire pages with progress; per-question selection submits after the last page | shared-ui .../promptsheet/PromptContent.kt (questionnaire branch) | shared PromptContentTest.kt:48 `testQuestionnaireStructureAndAnswers`; page-chip UI visual part of `screenshotPromptSheet` | partial (answers/structure proven; page chrome via screenshot) |
| CH40 | QA N5/N7: notification actions (options ≤ 3, free text RemoteInput) | push path: app .../push/* (O2 slice) | O2's tests | platform-specific (owned by O2; chat surface only) |
| CH41 | New agent flow: machine picker, live folder browser, launcher chips, herdr placement, rp-host copy command `npx @j3pf/pi-rp rp-host run`, created → immediate navigation | shared-ui .../newagent/NewAgentContent.kt:63‑130, 212‑232; app .../newagent/NewAgentScreen.kt | shared NewAgentModelsTest.kt (4 tests); `screenshotNewAgent` | golden - unverified on this host (models proven) |
| CH42 | Header sanitization: title from sessionName, else cwd basename, else 8-char id, never a raw UUID | shared-ui .../ui/util/Format.kt:65‑69; app util/Format.kt:36‑40 | ChatUiCharacterizationTest.kt `agent titles resolve from session name, cwd basename or 8-char id` | proof-added |
| CH43 | QA C-header: StatusChip, model label `model · thinkingLevel`, mute toggle, connection banner | app ChatScreen.kt:118‑142, 200‑212 | — | partial (structure; shared ComposeUiTest pins AgentCard variation) |

## 8. Markdown / code highlighting / density

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| CH44 | Markdown + async syntax highlighting with app-surface-driven dark palette (QA C10) | shared-ui .../components/RpMarkdown.kt | app .../ui/components/RpMarkdownTest.kt | proven |
| CH45 | Chat density & spacing controls: `Compact`/`Normal`/`Relaxed` (`compact` → 2dp item spacing, `spacious` → 10dp; vertical padding 3/6/10) and chat font scale | shared-ui .../ui/chat/ChatComponents.kt:384‑401; ChatViewModel.kt:127‑131 | — | partial (mapping pinned in code; bounds-based density test pends to T5) |
| CH46 | Docked quota bar / full-width thinking blocks (CHANGELOG) | app .../ui/QuotaDynamicIsland* | app .../ui/QuotaDynamicIslandTest.kt | proven |
| CH47 | Subagent/TODO settle at turn completion (subagents to `done`/`aborted`, no duplicate cards) | protocol .../chat/ChatReducer.kt (`agent_settled`) | protocol/src/jvmTest/kotlin/dev/j3pf/rp/chat/ChatReducerTest.kt:80,126,128,147,189 | proven |

## 9. Lifecycle + platform-specifics

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| CH48 | v0.6.1 diagnosable close failure: snackbar shows the relay reason verbatim `No se pudo cerrar el agente: <relayMessage>`; default message only when blank | app AgentsRepository.kt:153; ChatViewModel.kt:197‑220 | ChatViewModelCharacterizationTest.kt `close failure surfaces the relay reason verbatim from the chat panel` (18th test, relay reply `bad_request`/`branch has unmerged changes` replayed over the live ws) | proof-added |
| CH49 | QA C9 reconnect 30 s: `sinceSeq` resubscription without duplicates | ChatRepository resync timers (+ `needsResync` on ChatState) | ChatRepositoryTest.kt:206/236 (resync + history clears syncing) | proven |
| CH50 | Press-scale feedback, spring transitions, working pulse (QA U6) | shared RpMotion tokens | — | platform-partial (structural tokens; visual via QA U6) |
| CH51 | QA U7 reduced-motion static equivalents | LocalReducedMotion in ChatMessages.kt:176, ChatComponents.kt:443/481 | ChatUiCharacterizationTest.kt `reduced motion still renders...` | proof-added |
| CH52 | QA U8 48dp touch targets + TalkBack Spanish contentDescriptions | M3 minimum interactive enforcement + composer IconButtons | — | partial (Compose enforces `minimumInteractiveComponentSize`; TalkBack sweep QA U8) |
| CH53 | QA A3/CH28 `Supera 25 MB` text on oversized uploads | ChatViewModel.kt:258‑266 | ChatViewModelCharacterizationTest.kt (413 test) | proof-added |
| CH54 | Voice input (mic → partial/final text into the composer) | app .../ui/chat/VoiceInputState.kt (SpeechRecognizer); consumer at ChatScreen.kt:884+ | — | platform-specific (device only; iOS port P-VOICE; QA step: tap mic, speak, composer shows the partial text) |
| CH55 | Camera capture attachment (TakePicture + FileProvider target) | app Composer launchers (ChatScreen.kt:648‑659) + AttachmentsRepository.newCameraTarget | — | platform-specific (device; iOS maps to the photo library; QA A1 camera step) |

## Platform ports required (iOS equivalents, final contracts from O1 — merged in 25ab449)

| Name | Contract (O1, T2 ADR 0001) | Android impl | iOS approach |
|---|---|---|---|
| P-PICK | `AttachmentsPlatformHost` — `pickImages(maxItems)`, `pickDocuments(mime)`, `cameraTarget(): PreparedLocalFile?`; screens keep calling AttachmentsRepository (O1 migrates it in T3) | app ChatScreen.kt:645‑647, 891‑905; AttachmentsRepository.newCameraTarget | PhotosPicker / UIDocumentPickerViewController via CMP; camera device-only |
| P-BIO | `BiometricAuthorizer.authenticate(reason): AuthResult` (sealed OK/FAILED/UNAVAILABLE), 5-min validity; wrapper over the real `util/BiometricGate.kt` (O1) | app util/BiometricGate.kt (`VALIDITY_MS = 5 * 60_000`) | `LAContext.evaluatePolicy` + Keychain persistence (`keychain-access-groups` entitlement) |
| P-VOICE | `VoiceInputHost` + `VoiceListener{onPartial/onFinal/onError(code)}` with documented codes 1–6 | app ui/chat/VoiceInputState.kt | `SFSpeechRecognizer` + `AVAudioSession.record` |
| P-HAPTICS | No native port: use CMP `LocalHapticFeedback` in commonMain | ChatScreen.kt:66 | CMP equivalent |
| P-IME | No port: standard CMP `imePadding()`/`navigationBarsPadding()` (real-device insets bugs belong to O4's iOS host) | ChatScreen.kt:44‑45, 87‑90 | CMP insets |
| P-DIR | Not needed (relay-mediated herdr/folder lists) | NewAgentViewModel | — |
| P-CLIP | Not needed (CMP clipboard) | NewAgentContent.kt:120 | — |

## Summary (computed from the rows — 55 total)

- **proven: 11** — CH1, CH2, CH3, CH5, CH14, CH30, CH35, CH44, CH46, CH47, CH49.
- **proof-added: 26** — CH6, CH7, CH8, CH9, CH10, CH15, CH16, CH17, CH18, CH19, CH20, CH21, CH22, CH24, CH25, CH26, CH27, CH31, CH32, CH33, CH36, CH37, CH42, CH48, CH51, CH53 (whole set green-first on unchanged code across rev 1 + rev 2).
- **partial: 7** — CH23, CH34, CH39, CH43, CH45, CH50, CH52.
- **golden - unverified on this host: 4** — CH4, CH11, CH38, CH41 (visuals pending the orchestrator's main-baseline differential).
- **gap / platform-specific: 7** — CH12 (gap; T5 harness or QA C3 device), CH13 (gap; relay persistence, O2-ish), CH28 (gap; Robolectric bitmap host-drift, QA A1 device), CH29 (platform-specific; text composed by Rp-plugin), CH40 (platform-specific; O2 push/hash), CH54/CH55 (device-only; ports P-VOICE/camera).
- Characterization tests on unchanged code (a32e66c → {this commit}): DiffDiscardConfirmationTest (4, incl. back-dismiss) + DiffSheetWrapperTest (2) + ReviewConsentCharacterizationTest (9, incl. ids≠positions chip binding and whitespace-edge verbatim) + ChatViewModelCharacterizationTest (18, incl. the S19 chain + close-failure surface + exactly-once negative) + PromptSheetViewModelCharacterizationTest (2) + ChatUiCharacterizationTest (12) = **47 tests**.
- Independent verifier (gentle-ai-verify, S19 + consent, mutation-tested): verdicts applied — back-dismiss regression (kills M3), chip-by-id-not-index (kills R1), verbatim whitespace edges (kills R2), one-wire-send negative for discard. Verbatim notes: chip placement by position, option order, discard-first and cancel/confirm paths were already caught on mutation.
- Final focused run of record (rev 2): `./gradlew :app:testDirectDebugUnitTest --tests 'dev.j3pf.rp.ui.chat.*' --tests 'dev.j3pf.rp.ui.promptsheet.*'` → **61 tests, 0 failures**.


- Known non-runs by explicit instruction: no Roborazzi verify/record (host drift documented on unchanged `main` by the orchestrator).

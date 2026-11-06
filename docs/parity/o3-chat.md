# o3-chat — parity matrix (T1, chat slice of unified-kmp)

Source of truth: Android behavior on `unify/o3-chat` (= `feat/unified-kmp` @ 6c073ef, based on `main` @ 482cbb9).
Slice owned by O3: `app/.../ui/{chat, promptsheet, pending, newagent, components}` and
`shared-ui/.../ui/{chat, diff, promptsheet, pending, newagent, components}` with their tests.
Sources covered: README "Main features", CHANGELOG (chat items), docs/QA.md §2 (Chat), §3 (Attachments), §6 U2/U3/U7/U8, §10 F6.1-F6.4, v0.6.0 review-consent bullet.

Status legend: **proven** = existing automatic test proves it green today; **proof-added** = new characterization test added by T1; **gap** = no automatic proof (manual or none). No production code changes in T1.

## 1. Bottom-anchored live streaming

| ID | Behavior (incl. edge cases / exact strings) | Android source | Proof test | Status |
|---|---|---|---|---|
| CH1 | Message list is bottom-anchored (`LazyColumn` with `reverseLayout = true`); opening a chat lands on the newest content with the composer visible | shared-ui .../ui/chat/ChatComponents.kt:407‑409; app .../ui/chat/ChatScreen.kt (wraps ChatContent) | app/src/test/.../ui/chat/ChatBottomAnchorTest.kt:45 `open chat lands on newest content with bottom visible above composer` | proven |
| CH2 | Streaming append keeps the bottom visible without manual scroll (auto `animateScrollToItem(0)` on new items, plain `scrollToItem(0)` on text growth while at bottom; no scroll fight while the user drags) | shared-ui .../ui/chat/ChatComponents.kt:368‑381 (`lastItemKey`, `streamingTextLength`, `isScrollInProgress` guard) | ChatBottomAnchorTest.kt:102 `tall streaming message keeps bottom visible above composer across appends` | proven |
| CH3 | Adjacent chat item bounds never overlap (`itemsCountWhenScrolledUp`/spacing math) | shared-ui .../ui/chat/ChatComponents.kt:352‑361, 407+ | ChatBottomAnchorTest.kt:192 `two consecutive chat items bounds do not overlap` | proven |

## 2. "More recent (N)" button

| ID | Behavior (incl. edge cases / exact strings) | Android source | Proof test | Status |
|---|---|---|---|---|
| CH4 | Scrolled up (firstVisible index beyond working row, offset > 48): animated floating button appears with exact label `Más recientes (N)` (`N` = server unreadCount, else new items since leaving bottom; bottom padding 12dp, arrow icon contentDescription `Ir al más reciente`) | shared-ui .../ui/chat/ChatComponents.kt:329‑334 (`isScrolledUp`), 343‑363 (`unreadNewItems`), 520‑560 (button + `AnimatedVisibility`) | ChatBottomAnchorTest.kt:240 `scrolled up user sees jump to latest button with unread count` (asserts label/text + displayed) | proven (existence + label); the *enter/exit animation itself* has no assertion — acceptable (Roborazzi `screenshotChatScrolledUp` pins the resting state) |
| CH5 | Tapping the button animates to `item 0`; while user is at bottom the button is hidden | shared-ui .../ui/chat/ChatComponents.kt:530‑533 (`onClick`), 520 (`visible = isScrolledUp`) | ChatBottomAnchorTest.kt:283 (`performClick` on `Más recientes (2)`), screenshot `screenshotChatScrolledUp` | proven |

## 3. Message rendering / history / tools

| ID | Behavior (incl. edge cases / exact strings) | Android source | Proof test | Status |
|---|---|---|---|---|
| CH6 | Latest history sync state renders `Sincronizando historial…` + `Descargando los mensajes recientes de la sesión.` in the empty list | shared-ui .../ui/chat/ChatComponents.kt:320‑331 | — | gap (manual; QA C1/C9 cover end-to-end, sync flow itself is O2's relay-client) |
| CH7 | Empty chat: `Sin mensajes todavía` + `Escribe abajo para hablar con este agente.` | shared-ui .../ui/chat/ChatComponents.kt:333‑336 | — | gap (cosmetic; will be pinned with T5 migration test) |
| CH8 | Tool calls render as collapsible runs: bash shows the command, edit shows the diff, long output truncated with exact toggle `ver más` / `ver menos` (per-`toolCallId` rememberSaveable) | shared-ui .../ui/chat/ChatMessages.kt:367, 373‑374, 465 | — | gap → **characterization test T1** (`ToolBlockTest`) |
| CH9 | Inline tool-call blocks suppress the duplicate subagent card (`SubagentCardItem` hidden when its `taskId` matches an inline `Block.ToolCall`) | shared-ui .../ui/chat/ChatMessages.kt:278‑292 | — | gap → **characterization test T1** |
| CH10 | Reduced motion: `LocalReducedMotion` swaps animated message/attachment entrances and streaming pulse for static equivalents | shared-ui .../ui/chat/ChatMessages.kt:176‑178; ChatComponents.kt:443‑445, 481‑483 | — | gap (QA U7 is manual) → static behavior pinned in **characterization test T1** only via `LocalReducedMotion` composition |

## 4. Composer, delivery modes, slash commands

| ID | Behavior (incl. edge cases / exact strings) | Android source | Proof test | Status |
|---|---|---|---|---|
| CH11 | Slash suggestions: typing `/` shows command suggestions from the launcher-adaptive catalog (`/clear /compact /model /thinking /quit /undo /sdd-status /status /help /rp` for Pi); tapping a chip replaces text with `$cmd ` (trailing space); only when text starts with `/` and has no space | app .../ui/chat/ChatScreen.kt:707‑736; shared-ui .../ui/chat/SlashCommandCatalog.kt:14‑30 | app .../ui/chat/SlashCommandSuggestionsTest.kt:26,142; shared-ui .../chat/ChatMessagesTest.kt:19 `testSlashCommandResolution` | proven |
| CH12 | Uppercase prefixes still suggest (`/Thinking`), case-insensitive match, exact-text exclusion (typing the full command shows no suggestion) | app .../ui/chat/ChatScreen.kt:711‑735 (`lowerTrimmed`) | SlashCommandSuggestionsTest.kt:170; ChatMessagesTest.kt:19 | proven |
| CH13 | Thinking-level chips: `/thinking ` (or `/thinking` w/o trailing space) offers all levels `off minimal low medium high xhigh max`; typing a prefix filters (`/thinking mi` → `minimal`) and exact level text produces no suggestion | app .../ui/chat/ChatScreen.kt:712‑724; SlashCommandCatalog.kt:30 | SlashCommandSuggestionsTest.kt:54,86,114; F6.1 ("chip completes to `/thinking ` with trailing space") at :26 | proven |
| CH14 | Launcher-adaptive catalog: Antigravity/Claude/Codex/Gentle Shell/Terminal resolve by `launcher`, model id, provider (`resolve` fallback = PI); quick shortcuts row shows when composer is empty | shared-ui .../ui/chat/SlashCommandCatalog.kt:149‑175 | SlashCommandSuggestionsTest.kt:198,228,258,288 | proven |
| CH15 | Delivery chip: label flips to `⚡ Comando ${launcherLabel} (/)` when text starts with `/` (tertiary colors); for herdr/non-Pi agents it is a static chip; menu items disabled for COMMAND choice unless text starts with `/` | app .../ui/chat/ChatScreen.kt:769‑846 | ChatBottomAnchorTest? no — screenshot `screenshotPromptSheet` covers sheet only | gap → **characterization test T1** (chip label flip + herdr static chip) |
| CH16 | Working-agent delivery: chips `Encolar (follow‑up)` / `Interrumpir y dirigir (steer)` (exact strings, selected-state mapping to `Delivery.FOLLOW_UP`/`STEER`); ⏹ aborts the agent | app .../ui/chat/ChatScreen.kt:809‑829 (chips); ChatViewModel.kt:111 (`abort`) | — | gap → **characterization test T1** (workingDelivery setter, abort invocation) |
| CH17 | Gentle AUTO: typed-input send requests biometrics valid 5 min (`VALIDITY_MS = 5 * 60_000`); UNAVAILABLE → snackbar `Configura un bloqueo de pantalla para enviar como tecleado`; FAILED → `Desbloqueo cancelado` | app .../ui/chat/ChatScreen.kt:486‑516; app .../rp/util/BiometricGate.kt:104 | — | gap → **characterization test T1** for `needsAuth()` decision matrix (TYPED / AUTO+isAuto / others, no UI) |
| CH18 | AUTO banner when interactive delivery fails: exact strings `En Auto este mensaje no autoriza acciones` + [Pasar a Normal] button → sends `/gentle:mode normal` as command and confirms `Modo de permisos: Normal` | app .../ui/chat/ChatScreen.kt:365‑366; ChatViewModel.kt:408‑417 | — | gap → **characterization test T1** (`switchToNormal` sends exact command) |
| CH19 | Interactive send error messages, exact: `El agente espera un diálogo en la terminal; el mensaje quedó en cola` (agent_blocked), `No se pudo teclear en la terminal: el mensaje no autoriza acciones en Auto` (interactive_unavailable), `herdr no está disponible en esa PC` (herdr_unavailable), `Se enviará al cerrarse el diálogo` (typed_after_dialog), `Sin conexión: se enviará al volver la red` (queued), QA C8 banner `Esperando en la terminal (diálogo no remoto)` | ChatViewModel.kt:371‑400 (string mapping); delivery choice enum labels shared-ui .../chat/ChatModels.kt | shared-ui .../chat/ChatModelsTest.kt:8 `deliveryChoiceLabels` (labels only) | partial (labels proven; ViewModel error-message mapping) → **characterization test T1** for the message matrix |
| CH20 | Non-remote dialog banner QA C8: `Esperando en la terminal (diálogo no remoto)` | ChatViewModel.kt:374‑377 (agent_blocked message) | — | gap (manual E2E, requires real herdr dialog) |
| CH21 | Quit: `/quit` sent as command with delivery AUTO; confirmation dialog has `Cancelar` dismiss button | ChatViewModel.kt:212‑215 (`quit`); ChatScreen.kt:440‑447 (`showQuitDialog`) | — | gap → **characterization test T1** (quit sends `/quit` with `asCommand = true`) |
| CH22 | Forget/close: confirmation required (`showForgetConfirmation` guard, `confirmForget` no-ops without it); archived outcome → archived notice snackbar | ChatViewModel.kt:197‑220 | — | gap → **characterization test T1** (`confirmForget` guard + double-invocation protection) |

## 5. Attachments

| ID | Behavior (incl. edge cases / exact strings) | Android source | Proof test | Status |
|---|---|---|---|---|
| CH23 | Attach menu with 3 options (`Archivo`, `Foto de la galería`, `Cámara`);gallery launcher `PickMultipleVisualMedia(10)`; file launcher `*/*`; camera requests `Manifest.permission.CAMERA` first | app .../ui/chat/ChatScreen.kt:645‑647, 891‑905 | — | gap (system pickers need real device) → covered under ports (P-PICK) |
| CH24 | Attachment states: `PREPARING → UPLOADING (progress) → DONE` or `ERROR`; error text exact `Supera 25 MB` (HTTP 413), `HTTP N` (other codes), `Sin conexión` (network failure) | ChatViewModel.kt:245‑296 (prepare/upload), 258‑266 (413) | — | gap → **characterization test T1** (ViewModel state machine against fake repo) |
| CH25 | Failed attachments block sending: message `Quita los adjuntos con error antes de enviar` (empty body + no attachments also no-ops, `canSend` gate) | ChatViewModel.kt:327‑345 | — | gap → **characterization test T1** |
| CH26 | Remove/retry attachment; upload retry resets progress and error; temp attachments discarded on send success and on VM clear | ChatViewModel.kt:299‑314, 352‑378, 421‑424 | — | gap → **characterization test T1** |
| CH27 | Attachment chip UI: name, size, progress/error + remove button, horizontal scroll with edge fade; `AttachmentUploadState` enum labels | app ChatScreen.kt:974 (`AttachmentChip`); shared-ui .../chat/ChatComponents.kt:79 | shared-ui .../chat/ChatModelsTest.kt:16 `attachmentUploadStateEntries`; screenshot none for chips | partial → **characterization test T1** (shared chip displayed/locked per state) |

## 6. Diff viewer + discard confirmation (S19, high-risk)

| ID | Behavior (incl. edge cases / exact strings) | Android source | Proof test | Status |
|---|---|---|---|---|
| CH28 | Branch chip shows `⎇ <branch>` with ` *` suffix when there are changes and opens the sheet (`onOpenDiff`); tertiary container color when dirty | app .../ui/chat/ChatScreen.kt:154‑192 | — | gap → **characterization test T1** (label logic) |
| CH29 | Diff sheet: file list + per-file diff, `Descartar` per file and per sheet (labels from `R.string.diff_title` / `diff_discard`), plus `Pedir ajuste` adjustment request; `onRequestAdjustment` sends an adjustment prompt to the host | app .../ui/chat/DiffSheet.kt (wrapper); shared-ui .../ui/diff/DiffSheet.kt:57‑115, 253‑275 | — | gap → **characterization test T1** (sheet structure + callback wiring) |
| CH30 | **S19: discard requires an explicit user confirmation dialog first** (`confirmDiscardFile` state; dialog title = discard label, confirm button triggers `onDiscardFile(path)`, dismiss resets state; no direct host call; dialog question text "¿Seguro que deseas descartar los cambios en '<path>'?") | shared-ui .../ui/diff/DiffSheet.kt:71 (`confirmDiscardFile`), 289‑307 (dialog) | app .../ui/chat/DiffDiscardConfirmationTest.kt (3 tests: dialog-first, confirm-exactly-once, cancel-keeps-file) | **proof-added T1, green on unchanged code, no defect** |
| CH31 | Discard executes then reloads the sheet; failures surface as snackbar message; per-file selection remembered across reload | ChatViewModel.kt:140‑176 (`openDiffSheet`/`discardDiffFile`), 170‑173 (re-open after Ok) | — | gap → **characterization test T1** (VM flow with fake host repo) |

## 7. Prompt sheet, review consent, pending, new agent

| ID | Behavior (incl. edge cases / exact strings) | Android source | Proof test | Status |
|---|---|---|---|---|
| CH32 | Permission prompt structure (confirm + `tool`, danger tag, answer buttons); interactive non-arg `/model` `/thinking` opens PromptBottomSheet select (F6.4) | shared-ui .../promptsheet/PromptContent.kt (confirm branch); ChatViewModel `asCommand` + plugin-side select | promptsheet PromptContentTest.kt:21 `testPermissionPromptStructure`; screenshot `screenshotPromptSheet` | proven (structure); F6.4 flow is runtime (plugin) — manual |
| CH33 | **Review consent surface (v0.6.0, high-risk)**: `PromptKind.SELECT` + `PromptCategory.PERMISSION` + `Danger.HIGH`. Renders header `Consentimiento de revisión` + tag `Peligro alto`; terminal body rendered **verbatim in monospace** (80-wide, no reflow, preserved leading spaces; both scrolls so nothing truncates); provider options in order, each with its declared `description`; option id `2` tagged `Permiso para toda la sesión` (session-wide grant); dismiss = `Cancelar` → `Answer.Cancelled` (sends `{cancelled:true}`) | shared-ui .../promptsheet/PromptContent.kt:402‑457 (`isReviewConsent`, `ReviewConsentBody`) | app .../ui/promptsheet/ReviewConsentCharacterizationTest.kt (4 tests: order+verbatim+effects, single session-wide chip on id 2, Cancelar→Cancelled, option click→`{id}`) | **proof-added T1, green on unchanged code, no defect** |
| CH34 | Prompt answering: `requiresAuth` prompts request biometrics (`Confirma tu identidad`); UNAVAILABLE → `Configura un bloqueo de pantalla para aprobar`; FAILED → `Desbloqueo cancelado`; `AlreadyResolved` → `Ya respondido en otro lugar`; busyPromptId lock while answering | app .../ui/chat/ChatScreen.kt:498‑555 | — | gap → **characterization test T1** (PromptSheetViewModel auth-decision logic where testable without BiometricPrompt) |
| CH35 | Pending screen: header `¿Hay decisiones pendientes?` / `Bandeja de Entrada`; one card per open prompt of all agents; context/machine filter via `MachineHeroDropdown`; dangerous prompts first, then opened_at ordering | app .../pending/PendingScreen.kt:37‑49; shared-ui .../pending/PendingContent.kt:38+; shared-ui .../pending/PendingModels.kt:21‑35 | shared-ui .../pending/PendingModelsTest.kt:13,38; screenshot `screenshotPending` | proven |
| CH36 | New agent: machine picker, live folder browser (open dir/up), launcher chips, backend selection (`herdr` placement: workspace/tab/pane/direction from live `host.herdr_layout`), name/prompt, create; `rp-host` missing → banner `Servicio rp-host requerido` + copyable command `npx @j3pf/pi-rp rp-host run`; created → immediate navigation | app .../newagent/NewAgentScreen.kt; shared-ui .../newagent/NewAgentContent.kt:63‑130, 212‑232; shared-ui .../newagent/NewAgentModels.kt:68+ (`backendLabel`) | shared-ui .../newagent/NewAgentModelsTest.kt:22,48,68,86; screenshot `screenshotNewAgent` | proven (models/structure); live folder listing → port section (P-DIR) |
| CH37 | In-chat agent info: header shows topic from cwd basename, `model · thinkingLevel` line, StatusChip, connection banner (`ConnectionBanner`), mute toggle, typed/AUTO chip, archive notice `R.string.archived_fallback_notice` | app .../ui/chat/ChatScreen.kt:118‑142, 201‑203, 421 | — | partial (screenshot `screenshotChatStreaming` covers visuals) → **characterization test T1** (header topic/label sanitization `agentTitle`) |

## 8. Markdown / code highlighting

| ID | Behavior (incl. edge cases / exact strings) | Android source | Proof test | Status |
|---|---|---|---|---|
| CH38 | Markdown rendering in assistant bubbles; syntax highlighting runs asynchronously off the main thread, dark-mode palette chosen from app-surface luminance (works with dark app theme on light system theme — QA C10); fenced blocks inside streaming text | shared-ui .../components/RpMarkdown.kt (+ async highlighter) | app .../ui/components/RpMarkdownTest.kt | proven |

## 9. Voice input (platform-specific)

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| CH39 | Mic button toggles speech recognition; placeholder changes to `R.string.voice_listening`; partial results update the composer; errors stop listening silently | app .../ui/chat/VoiceInputState.kt (SpeechRecognizer), ChatScreen.kt:884‑894 | none (sensor/system service) | **platform-specific** — see ports (P-VOICE); iOS equivalent: `SFSpeechRecognizer`. Characterization limited to state flags in T1 |

## Platform ports required (iOS equivalents, for shared-code migration)

| Name | Signature sketch | Android impl | iOS approach |
|---|---|---|---|
| P-PICK | `expect fun MediaPicker(): MediaPickerHost` — `pickMedia(types: PickType, maxItems: Int): List<Uri>` / `pickDocument(mime: List<String>)`; `expect fun fileDocumentLauncher(...)` | app ChatScreen.kt:645‑647 (`PickMultipleVisualMedia(10)`), `files.launch(arrayOf("*/*"))` (:891) | `PHPickerViewController` (images) / `UIDocumentPickerViewController` (files); MA core: camera is Android-only, on iOS maps to photo library; message text uses `[Archivo adjunto: /ruta (mime, tamaño)]` unchanged |
| P-BIO | `expect class BiometricGate { fun authenticate(activity: Any?, allowHint: String, reason: String): AuthResult }` — result `OK / UNAVAILABLE / FAILED`, 5-min validity | app util/BiometricGate.kt (`BiometricPrompt`, `VALIDITY_MS = 5 * 60_000`) | `LAContext.evaluatePolicy(.deviceOwnerAuthentication…)`, `hasUltimateBiometricEvaluation`; Keychain persistence is O1's port; needs `keychain-access-groups` entitlement (S12 lesson) |
| P-HAPTICS | `expect fun haptic(screen: *, type: HapticFeedbackType)` — used for send/choice haptics | ChatScreen.kt:66 (`LocalHapticFeedback`) | `UIImpactFeedbackGenerator` |
| P-IME | `expect fun imePadding()` + composer focus management (list must stay bottom-anchored with keyboard up: `imePadding`, `navigationBarsPadding`, `WindowInsets(0,0,0,0)` scaffold) | ChatScreen.kt:44‑45 (`imePadding`, `navigationBarsPadding`), 87‑90 scaffold insets | SwiftUI: `.ignoresSafeArea(.keyboard)` on iOS side of `MainViewController`; CMP handles insets natively, verify Compose 1.7 `imeNestedScroll` |
| P-DIR | New-agent folder browsing (`openDir`/`up` via host `rp-host` RPC — actually relay-mediated, no device FS) | NewAgentViewModel (`openDir`, `up`) | No direct port: lists come from the relay host interface; only the picker UI needs `UIDocumentPickerViewController` for choosing launch folders, if ever needed |
| P-VOICE | `expect class VoiceInputHost: startListening() / stopListening() / partial(text) / final(text)` | app VoiceInputState.kt (`SpeechRecognizer`) | `SFSpeechRecognizer` + `AVAudioSession` record permission |
| P-CLIP | copy-to-clipboard for `rp-host` install command and raw IDs | NewAgentContent.kt:120 (`clipboardManager.setText`) | `UIPasteboard.general.string` |
| P-INTENTS | Pending question actions deep-link into the sheet instead of notification actions | (notification actions are O2's slice) | N/A — covered by O2 notification port |

Measured port requests to O1 (cc orchestrator): P-BIO (`AuthResult` + validity), P-PICK, P-VOICE, P-HAPTICS, P-IME — these are the five the chat slice needs as shared `expect/actual` contracts.

## Summary

- Rows 39: proven 17 (incl. Roborazzi-covered visuals), partial 3, **proof-added in T1: 2** (CH30, CH33 — the two S15/S19 high-risk surfaces), gaps 14, platform-specific/ports 3 (CH39, CH23, CH20 partially).
- Characterization tests added in T1 (green-first on unchanged Android code; no production file edited): `DiffDiscardConfirmationTest` (3) + `ReviewConsentCharacterizationTest` (4) = 7 new tests, 0 failures (`./gradlew :app:testDirectDebugUnitTest --tests 'dev.j3pf.rp.ui.chat.*' --tests 'dev.j3pf.rp.ui.promptsheet.*'` → 22 tests, 0 failures).
- Remaining gaps (CH6, CH15‑CH22, CH24‑CH31 partials, CH34, CH37) are ViewModel-throwing or device/hardware-level (pickers, camera, SpeechRecognizer, network E2E); their ViewModel characterization needs the data-layer fakes O1 owns (ChatRepository, AttachmentsRepository, HostRepository) and will be written in T5 on top of the shared ports, as QA C-cases where automatic.
- No defects found: every green-first assertion held; nothing needs `defect:` annotation.

# o3-chat — parity matrix (rev 4, chat slice of unified-kmp)

Source of truth: Android behavior on `unify/o3-chat` (O3 rebase on `feat/unified-kmp` with :data-core from 25ab449; T1 base 6c073ef/55491a3).
Slice: `app/.../ui/{chat, promptsheet, pending, newagent, components}` and shared-ui `.../{chat, diff, promptsheet, pending, newagent, components}` with their tests.
Sources covered: README "Main features", CHANGELOG (chat/density items), docs/QA.md §1 N4-N7, §2 C1-C10, §3 A1-A4, §6 U6/U7/U8, §10 F6.1-F6.4, v0.6.0 review-consent + subagent nodes, v0.6.1 close failures.

Status vocabulary: {proven, proof-added, partial, golden-unverified-on-this-host, gap-with-reason, device-only, out-of-slice}. Reasons live only in the Notes column.

| ID | behavior (incl. edge cases / exact strings) | Android source | proof test | status | notes |
|---|---|---|---|---|---|

## 1. bottom-anchored streaming

| ID | behavior (incl. edge cases / exact strings) | Android source | proof test | status | notes |
|---|---|---|---|---|---|
| CH1 | Message list bottom-anchored (`LazyColumn`, `reverseLayout = true`); opening lands on the newest content above the composer | shared-ui .../ui/chat/ChatComponents.kt:407‑409 | app .../ui/chat/ChatBottomAnchorTest.kt:45 `open chat lands on newest content with bottom visible above composer` | proven |  |
| CH2 | Streaming appends keep the newest content visible without manual scroll | shared-ui .../ui/chat/ChatComponents.kt:368‑381 | ChatBottomAnchorTest.kt:102 `tall streaming message keeps bottom visible above composer across appends` | proven |  |
| CH3 | Adjacent chat items never overlap their bounds | shared-ui .../ui/chat/ChatComponents.kt:352‑361, 407+ | ChatBottomAnchorTest.kt:192 `two consecutive chat items bounds do not overlap` | proven |  |

## 2. 'More recent (N)' button

| ID | behavior (incl. edge cases / exact strings) | Android source | proof test | status | notes |
|---|---|---|---|---|---|
| CH4 | Scrolled-up floating button `Más recientes (N)` (N = unreadCount, else items added since leaving bottom); arrow contentDescription `Ir al más reciente` | shared-ui .../ui/chat/ChatComponents.kt:329‑334, 343‑363, 520‑560 | ChatBottomAnchorTest.kt:240 `scrolled up user sees jump to latest button with unread count` | golden-unverified-on-this-host | logic proven; resting pixels pinned by screenshotChatScrolledUp pending the orchestrator's host baseline |
| CH5 | Tap animates to item 0; the button hides while the user is at the bottom | shared-ui .../ui/chat/ChatComponents.kt:520, 530‑533 | ChatBottomAnchorTest.kt:283 (click flow) | proven |  |

## 3. history / tools / empty states

| ID | behavior (incl. edge cases / exact strings) | Android source | proof test | status | notes |
|---|---|---|---|---|---|
| CH6 | Syncing-history empty state: `Sincronizando historial…` + `Descargando los mensajes recientes de la sesión.` | shared-ui .../ui/chat/ChatComponents.kt:311‑313 | ChatUiCharacterizationTest.kt `empty chat while syncing shows the exact syncing strings` | proof-added |  |
| CH7 | Empty-chat placeholder: `Sin mensajes todavía` + `Escribe abajo para hablar con este agente.` (no syncing overlay) | shared-ui .../ui/chat/ChatComponents.kt:318‑319 | ChatUiCharacterizationTest.kt `fully empty chat shows the exact placeholder strings` | proof-added |  |
| CH8 | Collapsible tool card: header `✓` + tool name; expanding shows the result; long output behind exact toggles `ver más`/`ver menos` + `(resultado truncado por el relay)` when flagged | shared-ui .../ui/chat/ChatMessages.kt:367‑470 | ChatUiCharacterizationTest.kt `tool card expands on tap and truncates result behind the exact ver más toggle` | proof-added |  |
| CH9 | Inline tool call with `subagent` leaves exactly one card node (no duplicate SubagentCardItem) | shared-ui .../ui/chat/ChatMessages.kt:278‑292 | ChatUiCharacterizationTest.kt `inline tool call hides the duplicate subagent card` | proof-added |  |
| CH10 | Reduced motion renders streaming content statically | shared-ui .../ui/chat/ChatMessages.kt:176‑178; ChatComponents.kt:443‑445, 481‑483 | ChatUiCharacterizationTest.kt `reduced motion still renders the streaming message statically` | proof-added |  |
| CH11 | QA C1: reasoning blocks render collapsed as `Razonamiento` and expand on tap | shared-ui .../ui/chat/ChatMessages.kt:217‑268 (Block.Thinking :258), 293‑362 | ChatUiDetailedCharacterizationTest.kt `thinking block renders collapsed with the exact label and expands on tap` | proof-added | visuals pinned by screenshotChatStreaming pending the host baseline |
| CH12 | QA C3: user message sent from this device carries the exact marker `móvil` (fromMobile flag) | shared-ui .../ui/chat/ChatMessages.kt:197‑202 | ChatUiDetailedCharacterizationTest.kt `from-mobile user message shows the exact movil marker and others do not` | proof-added | README says 📱; the implemented literal is `móvil` — README wording updated in T9 |
| CH13 | QA C1 connected history: pages render from persisted Room state per session | app .../data/repo/ChatRepository.kt per-session state | app .../data/repo/ChatRepositoryTest.kt:236 `agent history clears syncing immediately...` (persistence belongs to the data layer) | out-of-slice | data layer is O1/O2's slice; QA C1 device step: open chat after relaunch, history renders |

## 4. composer, delivery modes, slash commands

| ID | behavior (incl. edge cases / exact strings) | Android source | proof test | status | notes |
|---|---|---|---|---|---|
| CH14 | Slash autocomplete catalog `/clear /compact /model /thinking /quit /undo /sdd-status /status /help /rp` with trailing-space completion | shared-ui .../chat/ChatComposer.kt:130‑181; shared-ui .../chat/SlashCommandCatalog.kt:14‑30 | SlashCommandSuggestionsTest.kt:26,142; shared-ui ChatMessagesTest.kt:19 | proven |  |
| CH15 | Composer delivery chip flips to `⚡ Comando Pi (/)` with slash text; herdr agents show only the static chip (never `Entrega: Automático`) | shared-ui .../chat/ChatComposer.kt:190‑257 | ChatUiCharacterizationTest.kt `composer with slash text shows the launcher command chip` + `herdr agent shows only the command chip and never the delivery chip` | proof-added |  |
| CH16 | Working agent: chips `Encolar (follow‑up)` / `Interrumpir y dirigir (steer)` map to Delivery.FOLLOW_UP / STEER; abort via ⏹ (ChatViewModel.abort) | shared-ui .../chat/ChatComposer.kt:232‑245; ChatTopBar.kt:145‑150; app ChatViewModel.kt:115 | ChatUiCharacterizationTest.kt `working agent surfaces queue and steer chips with exact labels` | proof-added | abort wire path covered by O2's session tests |
| CH17 | gentle-AUTO typed sends require biometrics; failing decisions drive the exact snackbars (`Configura un bloqueo de pantalla…`, `Desbloqueo cancelado`) | app ChatScreen.kt:486‑516; BiometricGate.kt:104 | ChatViewModelCharacterizationTest.kt `needsAuth gates TYPED and AUTO-on-gentle-auto...` + `needsAuth is false for a normal agent in AUTO` | proof-added | real BiometricPrompt is device-only (QA C5 step); ObAndroid `isFresh()` semantics: false until a successful authenticate and `invalidate()` resets — that observable was not auto-testable on this host, documented here |
| CH18 | `switchToNormal` sends `/gentle:mode normal` as command and reports `Modo de permisos: Normal` | app ChatScreen.kt:365‑366; ChatViewModel.kt:408‑417 | ChatViewModelCharacterizationTest.kt `switchToNormal sends the exact gentle mode normal command as a command` | proof-added |  |
| CH19 | Send error mapping exact strings: agent_blocked / interactive_unavailable / herdr_unavailable / typed_after_dialog / queued-offline | ChatViewModel.kt:365‑403 | ChatViewModelCharacterizationTest.kt (5 tests: `agent_blocked maps...`, `interactive_unavailable maps...`, `herdr_unavailable maps...`, `typed_after_dialog maps...`, `offline send queues...`) | proof-added |  |
| CH20 | QA C8 non-remote dialog banner `Esperando en la terminal (diálogo no remoto)` + optional `: <title>` | app ChatScreen.kt:349 | ChatUiCharacterizationTest.kt `terminal wait banner renders exact text with dialog title` | proof-added | the herdr-dialog itself is device-level (QA C8 step: trigger a dialog in the terminal and watch the banner) |
| CH21 | Quit button sends `/quit` as a command | ChatViewModel.kt:200‑204; ChatScreen.kt:427‑441 | ChatViewModelCharacterizationTest.kt `quit sends the exact slash quit command as command` | proof-added |  |
| CH22 | Forget: `confirmForget()` without `forget()` is a no-op (no agent.close on the wire); forget+confirm sends exactly one agent.close and runs onDone | ChatViewModel.kt:177‑206 | ChatViewModelCharacterizationTest.kt `confirmForget without prior forget sends no agent close...` + `forget then confirm closes the agent...` | proof-added |  |
| CH23 | QA C5 AUTO chip literal `AUTO · tus mensajes se envían como tecleados` when ui.isAuto | shared-ui .../chat/ChatComposer.kt:62‑66 | ChatUiDetailedCharacterizationTest.kt `gentle auto agent shows the exact AUTO chip` + `normal agent hides the AUTO chip` | proof-added | the '🔒 emoji of the README is not rendered by the current literal — README wording updated in T9 |

## 5. attachments

| ID | behavior (incl. edge cases / exact strings) | Android source | proof test | status | notes |
|---|---|---|---|---|---|
| CH24 | Attachment lifecycle: PREPARING→UPLOADING (progress)→DONE with relay blobId; error texts exact `Supera 25 MB` (HTTP 413), `HTTP N`, `Sin conexión` | ChatViewModel.kt:245‑296; AttachmentsRepository.kt:130+ | ChatViewModelCharacterizationTest.kt: `413 upload error surfaces the exact overlimit text and blocks sending`, `successful upload lands in DONE with the relay blob id`, `network failure surfaces the exact offline attachment error` | proof-added |  |
| CH25 | Failed attachments block sends: `Quita los adjuntos con error antes de enviar`, nothing on the wire | ChatViewModel.kt:333‑341 | ChatViewModelCharacterizationTest.kt (blocked-send test) | proof-added |  |
| CH26 | Remove chip; retry resets progress/error and re-uploads | ChatViewModel.kt:299‑314, 216‑264 | ChatViewModelCharacterizationTest.kt `removeAttachment drops the chip and retryAttachment resets then succeeds` | proof-added |  |
| CH27 | Attachment chip: name + ` · humanSize`, remove button + progress/error state | shared-ui .../chat/ChatComposer.kt:122; ChatComponents.kt:79‑130 | ChatUiCharacterizationTest.kt `attachment chips surface name state error and exact failure text`; ComposeUiTest.kt:266 `compositor con adjuntos muestra progreso y permite quitar` | proof-added |  |
| CH28 | Photo compression: JPEG 85%, longest side ≤ 2048px | AttachmentsRepository.kt:164‑213 (compress, MAX_DIM 2048) | — | device-only | QA A1 step: pick a 12 MP photo; verify the compressed JPEG ≤ 2048px (Robolectric BitmapFactory decode returns null on this host — host drift confirmed on unchanged main) |
| CH29 | QA A2 file text `[Archivo adjunto: /ruta (mime, tamaño)]` reaches the agent | message is composed by Rp-plugin; the app only uploads the blob and sends AttachmentRef | — | out-of-slice | QA A2 step: attach a PDF; verify the agent's transcript line |
| CH30 | QA A4 offline: queued send keeps clientMsgId idempotency; explicit retry re-sends the SAME clientMsgId | relay-client ChatDeliveryPolicy.kt (Queued + outbox) | ChatRepositoryTest.kt:285 `retry re-sends with identical clientMsgId preserving idempotency`; ChatViewModelCharacterizationTest.kt `offline send queues with the exact offline message` | proven |  |

## 6. diff viewer + S19 discard

| ID | behavior (incl. edge cases / exact strings) | Android source | proof test | status | notes |
|---|---|---|---|---|---|
| CH31 | Branch chip `⎇ <branch>` with ` *` when dirty; tap opens the sheet with `Revisión de cambios`/`Descartar` | shared-ui .../chat/ChatTopBar.kt:95‑122 | ChatUiCharacterizationTest.kt `branch chip shows the dirty marker and opens the diff sheet`; DiffSheetWrapperTest.kt `wrapper renders shared sheet with exact resource strings` | proof-added |  |
| CH32 | Diff sheet renders file list + per-file diff + `Pedir ajuste` | shared-ui .../ui/diff/DiffSheet.kt:57‑115, 253‑275 | DiffSheetWrapperTest.kt (labels + passthrough); resting pixels by Roborazzi | golden-unverified-on-this-host | wrapper logic proven; visual pending host baseline |
| CH33 | S19: discard requires the explicit dialog `¿Seguro que deseas descartar los cambios en '<path>'?`; confirm → onDiscardFile exactly once; Cancel/Back/dismiss keeps the file; the VM chain sends `host.git_discard` with exact machineId/cwd/path, exactly once + refresh | shared-ui .../ui/diff/DiffSheet.kt:71, 289‑307; ChatViewModel.kt:165‑176; HostRepository.kt:87‑92 | DiffDiscardConfirmationTest.kt (4 tests, incl. `back dismissal keeps the file intact...`); DiffSheetWrapperTest.kt; ChatViewModelCharacterizationTest.kt `discardDiffFile reaches the wire...` + `openDiffSheet fetches git diff...` | proof-added | mutation-tested: the back-dismiss probe discarded via onDismissRequest was caught only by the new test |
| CH34 | QA C2 bash shows the command; edit shows the diff inside the expanded tool card | shared-ui .../ui/chat/ChatMessages.kt:431‑449 | ChatUiCharacterizationTest.kt (bash tool card); ChatUiDetailedCharacterizationTest.kt `edit tool card shows the diff with minus and plus lines when expanded` (- old line / + new line) | proof-added | visual of the full sheet by screenshotChatStreaming pending host baseline |

## 7. prompt sheet, review consent, pending, new agent

| ID | behavior (incl. edge cases / exact strings) | Android source | proof test | status | notes |
|---|---|---|---|---|---|
| CH35 | Permission prompt structure (confirm + tool) | shared-ui .../promptsheet/PromptContent.kt (confirm branch) | shared-ui PromptContentTest.kt:21; app ComposeUiTest.kt `hoja de permisos muestra el comando y responde con la opcion` | proven |  |
| CH36 | Review consent surface (v0.6.0): verbatim monospace body without reflow; provider order options + effects; session-wide chip only on option id 2; Cancelar → Answer.Cancelled; wire JSON `"cancelled":true` | shared-ui .../promptsheet/PromptContent.kt:402‑457 | ReviewConsentCharacterizationTest.kt (9 tests incl. ids≠positions and whitespace-edges) | proof-added | mutation-tested by gentle-ai-verify; chip placement, order, single-chip all caught |
| CH37 | PromptSheetViewModel: requiresAuth matrix; already-resolved → AlreadyResolved; reset → Idle; wire `cancelled:true` | app PromptSheetViewModel.kt:61‑63; PromptsRepository.kt:141‑151 | PromptSheetViewModelCharacterizationTest.kt (2 tests) | proof-added |  |
| CH38 | Pending screen header `¿Hay decisiones pendientes?` / `Bandeja de Entrada`; dangerous-first ordering | app PendingScreen.kt:37‑49; shared PendingContent.kt:38+; PendingModels.kt:21‑35 | shared PendingModelsTest.kt:13,38 | golden-unverified-on-this-host | models proven; visual pending host baseline |
| CH39 | QA N6 questionnaire chrome: `Pregunta N de M · header`, progress bar, pages advance only when answered, `Enviar` submits per-page answers | shared-ui .../promptsheet/PromptContent.kt:57,76,291‑399 | QuestionnaireStructureCharacterizationTest.kt `questionnaire advances pages only when each page is answered` | proof-added | page-chrome visual via screenshotPromptSheet pending host baseline |
| CH40 | QA N5/N7 notification actions (≤3 options, RemoteInput free text) | push path: app .../push/* | O2's tests | out-of-slice | QA N5/N6/N7 steps live in docs/QA.md §1; chat surface only |
| CH41 | New agent flow: machine picker, folder browser, launcher chips, herdr placement, rp-host copy command | shared-ui NewAgentContent.kt:63‑130, 212‑232; app NewAgentScreen.kt | NewAgentModelsTest.kt (4 tests) | golden-unverified-on-this-host | models proven; visual pending host baseline |
| CH42 | Header title resolution: session name → cwd basename → 8-char id; never a raw UUID | shared-ui ui/util/Format.kt:65‑69; app ui/util/Format.kt:36‑40 | ChatUiCharacterizationTest.kt `agent titles resolve from session name, cwd basename or 8-char id` | proof-added |  |
| CH43 | Header subtitle `folder · model · thinkingLevel` | shared-ui .../chat/ChatTopBar.kt:124‑141 | ChatUiDetailedCharacterizationTest.kt `header subtitle joins folder model and thinking level` | proof-added |  |

## 8. markdown / code highlighting / density

| ID | behavior (incl. edge cases / exact strings) | Android source | proof test | status | notes |
|---|---|---|---|---|---|
| CH44 | Markdown + async syntax highlighting, app-surface-driven palette (QA C10) | shared-ui RpMarkdown.kt | app RpMarkdownTest.kt | proven |  |
| CH45 | Chat density & font scale: `compact` < `normal` < `spacious` strictly increasing gaps; font scale factor applied | shared-ui ChatComponents.kt:384‑401; ChatViewModel.kt:127‑131 | ChatUiDetailedCharacterizationTest.kt `message spacing modes are strictly increasing compact normal spacious` (node bounds) | proof-added | Compact/Normal/Relaxed picker UI is O4's settings slice |
| CH46 | Docked quota bar / full-width thinking blocks (CHANGELOG) | app QuotaDynamicIsland.kt | QuotaDynamicIslandTest.kt | proven |  |
| CH47 | Subagent/TODO settle at turn completion (agent_settled) | protocol ChatReducer.kt | ChatReducerTest.kt:80,126,128,147,189 | proven |  |

## 9. lifecycle + device-only surfaces

| ID | behavior (incl. edge cases / exact strings) | Android source | proof test | status | notes |
|---|---|---|---|---|---|
| CH48 | v0.6.1 diagnosable close failure: `No se pudo cerrar el agente: <relayMessage>` verbatim; blank falls back to the default | AgentsRepository.kt:153; ChatViewModel.kt:197‑220 | ChatViewModelCharacterizationTest.kt `close failure surfaces the relay reason verbatim from the chat panel` | proof-added |  |
| CH49 | QA C9 reconnect 30 s: sinceSeq resubscription without duplicates | ChatRepository resync timers + needsResync | ChatRepositoryTest.kt:206,236 | proven |  |
| CH50 | QA U6 press-scale, spring transitions, working pulse | shared RpMotion tokens | — | device-only | QA U6 step: observe pulse/press-scale on device |
| CH51 | QA U7 reduced-motion static equivalents | LocalReducedMotion (ChatMessages.kt:176; ChatComponents.kt:443/481) | ChatUiCharacterizationTest.kt `reduced motion still renders...` | proof-added |  |
| CH52 | QA U8 48dp touch targets + TalkBack Spanish contentDescriptions | M3 minimumInteractiveComponentSize + composer IconButtons | — | device-only | QA U8 step: TalkBack sweep on device |
| CH53 | `Supera 25 MB` on oversized uploads | ChatViewModel.kt:258‑266 | ChatViewModelCharacterizationTest.kt (413 test) | proof-added |  |
| CH54 | Voice input: mic → partial/final text into the composer | app VoiceInputState.kt; ChatScreen.kt:884+ | — | device-only | QA step: tap mic, speak; the composer shows the partial text (iOS port P-VOICE) |
| CH55 | Camera capture attachment (TakePicture + FileProvider target) | app Composer launchers (ChatScreen.kt:648‑659) + AttachmentsRepository.newCameraTarget | — | device-only | QA A1 camera step (iOS maps to the photo library via P-PICK) |

## Platform ports required (final contracts merged from O1 — :data-core in 25ab449)

| Name | Contract (O1 ADR 0001 / T2) | Android impl | iOS approach | status |
|---|---|---|---|---|
| P-PICK | `AttachmentsPlatformHost`: pickImages(maxItems), pickDocuments(mime), cameraTarget(): PreparedLocalFile? | app ChatScreen.kt:645‑647, 891‑905 | PhotosPicker / UIDocumentPickerViewController | PORT (contract) |
| P-BIO | `BiometricAuthorizer.authenticate(reason): AuthResult` (OK/FAILED/UNAVAILABLE), 5-min validity | BiometricGate.kt:104 | LAContext.evaluatePolicy + Keychain (keychain-access-groups) | PORT (contract) |
| P-VOICE | `VoiceInputHost` + VoiceListener codes 1–6 | app VoiceInputState.kt | SFSpeechRecognizer + AVAudioSession.record | PORT (contract) |
| P-HAPTICS | no port: CMP LocalHapticFeedback in commonMain | ChatScreen.kt:66 | CMP equivalent | PORT (contract) |
| P-IME | no port: standard CMP imePadding/navigationBarsPadding | ChatScreen.kt:44‑45, 87‑90 | CMP insets; device insets bugs belong to O4's iOS host | PORT (contract) |
| P-DIR | not needed (relay-mediated herdr/folder lists) | NewAgentViewModel | — | PORT (contract) |
| P-CLIP | not needed (CMP clipboard) | NewAgentContent.kt:120 | — | PORT (contract) |

## Summary (computed BY SCRIPT from the status column of the table above)

```
$ python3 counts-by-script.py >> paste-of-output
rows=55 — device-only 5, golden-unverified-on-this-host 4, out-of-slice 3, proof-added 32, proven 11
```

Test-class census (same script inputs):

- DiffDiscardConfirmationTest (4, incl. the REAL back-dismiss test — added after the rev-2 claim was found false; the discard file only ever had 3 tests before)
- DiffSheetWrapperTest (2)
- ReviewConsentCharacterizationTest (9)
- ChatViewModelCharacterizationTest (18, incl. the S19 vm→wire chain and the v0.6.1 close-failure surface)
- PromptSheetViewModelCharacterizationTest (2)
- ChatUiCharacterizationTest (12)
- ChatUiDetailedCharacterizationTest (7, rev-3: CH11, CH12, CH23, CH34, CH45 — some overlap with the rows listed above)
- QuestionnaireStructureCharacterizationTest (1)
- TOTAL characterizations: 4 + 2 + 9 + 18 + 2 + 12 + 7 + 1 = 55 tests
- Same classes PLUS the Roborazzi-style existing runs make the focused run below.

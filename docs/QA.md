# Manual QA (E2E) — rp-app

These tests cannot be fully automated: they require a real phone, a relay, and a Pi agent with the `rp` plugin. Mark each case ✅/❌ and record the device (model and Android version) and build flavor (`direct` / `fcm`).

**Setup**
1. Start the relay (verify that `/healthz` responds) and pair a Pi agent with `rp-plugin` (`/rp pair …`).
2. Install the APK (`app-direct-debug` or `app-fcm-debug`) and pair it using the QR code (`rp-relay pair device`).
3. Grant notification permission and, if possible, exclude RP from battery optimization.
4. For the agent, use a command that triggers an authorization prompt, for example, ask Pi to run `rm -rf build/`.

## 1. Notifications (core requirement)

| # | Case | Steps | Expected result |
|---|---|---|---|
| N1 | Authorization with the app **closed** and screen **locked** | Swipe the app away from Recents, lock the phone, and trigger an authorization prompt in the agent | A heads-up notification appears with title `🏢 PC · agente` and **[Permitir] [Denegar]** buttons. Tapping **Permitir** unlocks the agent in under 2 seconds without opening the app; the notification changes to `Enviando…` and disappears |
| N2 | Reply in the **terminal** | Trigger an authorization prompt and answer it in the agent's terminal | The phone notification disappears automatically (`cancel` push / `prompt.resolved`) |
| N3 | Reply on **another device** | Pair two phones and answer the prompt on one | The notification is removed from the other phone; if tapped in time, a 409 response still removes it |
| N4 | **Airplane mode** | Enable airplane mode, trigger or receive a notification (or leave one open), then tap **Permitir** | Shows `Sin conexión · se reintentará`; after airplane mode is disabled, WorkManager sends the queued reply automatically and removes the notification |
| N5 | Question with up to 3 options | Use `ask_user` with 3 options | A notification action appears for each option |
| N6 | Question with more than 3 options or a questionnaire | Use `ask_user_question` (gentle-pi) with 2 questions | Notification shows **[Abrir]**; the sheet presents pages with progress and submits after the final page |
| N7 | Free text | Trigger an `input` prompt | **Responder** action includes an inline `RemoteInput` field; the text reaches the agent |
| N8 | Agent finished | Let an agent finish a turn | The `Agente terminó` notification channel shows **Responder** (`RemoteInput`), which sends a message |
| N9 | Unlock to approve | Settings → Security → `Requerir desbloqueo para aprobar` | Notification actions request unlock (Android 12+); the sheet requests biometrics for critical authorizations |
| N10 | Two channels without duplicates | Use the `fcm` flavor with the direct channel active | Only one notification is shown per `promptId` |
| N11 | Reboot | Reboot the phone with the direct channel active | `RP conectado · N PCs` service starts again and notifications continue to arrive |
| N12 | Doze | Turn the screen off and leave the phone idle for 1 hour (A0 spike) | Notification arrives through direct and FCM channels; record direct-channel battery use |
| N13 | Groups by PC | System settings → RP → Notifications | One group per PC (`🏠 …`, `🏢 …`) with 5 channels; muting one group does not affect the other |
| N14 | Notification permission guard | On Android 13+, deny `POST_NOTIFICATIONS` and trigger a notification, then grant permission and retry | No notification is posted and the app does not crash while permission is denied; after granting permission, notifications can be posted |

## 2. Chat

| # | Case | Expected result |
|---|---|---|
| C1 | Open an agent | Recent history and live streaming (text, collapsed reasoning, and tools with status) |
| C2 | Bash / edit | Bash displays the command; edit displays the diff; long output is truncated with `ver más` |
| C3 | Send a message (idle agent) | Message bubble shows 📱 and the response streams in |
| C4 | Agent working | **Encolar (follow-up)** / **Interrumpir y dirigir (steer)** appear; ⏹ interrupts the agent |
| C5 | gentle-pi AUTO mode | Chip reads `AUTO · tus mensajes se envían como tecleados 🔒`; sending requests biometrics (valid for 5 minutes) and the message counts as typed input |
| C6 | No interactive delivery | Banner reads `En Auto este mensaje no autoriza acciones` with **[Pasar a Normal]** (sends `/gentle:mode normal`) |
| C7 | `/…` command | Typing text beginning with `/` enables `Comando /…` |
| C8 | Non-remote dialog | Banner reads `Esperando en la terminal (diálogo no remoto)` |
| C9 | Reconnect | Disconnect the network for 30 seconds and restore it; chat resumes with `sinceSeq` and no duplicates |
| C10 | Code highlighting and streaming | Stream a message containing fenced code blocks while the app theme differs from the system theme | Code highlighting does not block the main thread; the syntax palette follows app-surface luminance, including a dark app theme on a light system theme |

## 3. Attachments

| # | Case | Expected result |
|---|---|---|
| A1 | Gallery or camera photo | Compressed to JPEG (85%, 2048px), uploaded with progress, and received by the agent as an image |
| A2 | PDF / file | Uploaded with progress; agent receives `[Archivo adjunto: /ruta (mime, tamaño)]` |
| A3 | Over 25 MB | Attachment displays `Supera 25 MB` error |
| A4 | Offline while sending | Displays `Se enviará al volver la red`; delivered exactly once (idempotency by `clientMsgId`) |

## 4. PCs, agents, and settings

| # | Case | Expected result |
|---|---|---|
| P1 | PC selector | Chips for Todas / Personal / Empresa show color and 🔔 counts; filter Agents and Pending |
| P2 | Edit PC | Changes to name/context/color appear in headers, chips, and channel groups |
| P3 | New agent | On a PC with `rp-host`, select folder → launcher → `herdr` (workspace/tab/panel) or tmux/Windows Terminal; agent appears in the list |
| P4 | Add machine | Settings → `Añadir máquina` displays a copyable `/rp pair URL CÓDIGO` command |
| P5 | Revoke device/PC | It disappears and no longer connects |
| P6 | Quiet hours | Configured per PC; critical authorizations always bypass them when that setting is enabled |
| P7 | App lock / `FLAG_SECURE` | Requests biometrics on open; screenshots are blocked |
| P8 | Sign out | Clears credentials, database, and channels; returns to onboarding |
| P9 | Test notification | Settings → Notifications → `Enviar notificación de prueba` |

## 5. Agent Mesh (Animated Topology & Real-Time Communications — v0.4.0)

| # | Case | Steps | Expected result |
| --- | --- | --- | --- |
| M1 | Access & Navigation | Open the bottom navigation bar and tap **Mesh** | Opens `MeshScreen` cleanly; Mesh tab is highlighted; diagram or list view appears |
| M2 | Hierarchical Top-Down Cards | Observe the screen with agents | Orchestrator is positioned top-center with a machine-tinted avatar and is the only card displaying a role badge; subordinate and solo-agent cards have no role badge. `Subagente` is a role label, and subagents render as attached chips with their own execution state |
| M3 | Viewport Containment & Clipping | Observe the canvas with filters and live strip | Graph is clipped below the header and filters and above the live strip; auto-fit uses scale 1.0 in landscape at widths >=600dp, otherwise fits when possible down to 0.65. If content still does not fit, it starts at the top with 16dp padding and can be reached by panning; cards remain at least 180dp wide and use measured runtime heights with an estimate fallback |
| M4 | Connector Edges & Activity | Trigger recent communication or a pending ask | Active edges glow with an accent border for up to 20s; `pendingAsk` edges pulse with a dashed line; glow turns off after 20s |
| M5 | Comm Particles & Speech Bubbles | Send inter-agent comm (`mesh.comm`) between hierarchical agents | Orthogonal bus connectors route through row gaps; a particle travels along the connector, and the speech bubble preview appears in the connector gap without covering cards |
| M6 | Pan & Zoom Gestures | Pinch-to-zoom, drag with one finger, and double-tap | Zoom scales smoothly (0.5x to 3.0x) with auto-fit as the default; double-tap resets zoom and centers the view |
| M7 | Machine & Group Selectors | Tap the top machine and group chips | Filters visible nodes and edges; chips display clean human names without raw UUIDs; selection persists |
| M8 | Node Detail & Technical Section | Tap a card in diagram or list view | Opens `ModalBottomSheet` with avatar, clean display name, role label, machine, git branch, model, status, activity, TODOs, and subagents; the role badge appears only on the orchestrator card in the graph, while subagents render as attached chips. Raw IDs are located only in collapsed `Detalles técnicos`, with one-tap copy buttons |
| M9 | Agent Conversation History | Tap an edge connector between agents | Opens `ModalBottomSheet` with message history ordered by direction, clean names, action badges, timestamps, and `Cargar mensajes anteriores` pagination |
| M10 | Live Strip | Receive live comms | Collapsible bottom strip shows the latest messages (`Sender → Receiver: text`) using clean human names |
| M11 | Disconnected State | Disconnect the relay or enable airplane mode on Mesh | Shows `Desconectado — mostrando datos locales` at the top while keeping the topology visible, with a `Reintentar` button |
| M12 | Accessibility & List View | Tap `Vista de lista` | Switches to an accessible list view using the same cards, >=48dp touch targets, and full semantic descriptions |
| M13 | Reduced Motion | Set system `ANIMATOR_DURATION_SCALE=0` | Travelling particles, shimmer border, and pulse dots stop; static indicators appear without continuous redraw |
| M14 | Background Lifecycle | Minimize the app or switch apps | Cancels websocket subscription (`mesh.unsubscribe`); automatically resubscribes on resume (`ON_START`) |
| M15 | Compact Landscape Filters | Open Mesh in landscape with available height below 500dp and enough machines/groups to overflow the row | `Máquinas` and `Grupos` filters appear in one horizontal scrolling row; scrolling reaches every filter while the graph remains clipped below the row |
| M16 | Device-Locale Timestamps | Open a Mesh conversation on devices set to Spanish and to another locale | Message date/time formatting follows the device locale and time zone (for example, Spanish formatting on a Spanish device) |

## 6. UI Refresh and Modern Visual Experience (v0.3.0)

| # | Case | Steps | Expected result |
|---|---|---|---|
| U1 | Theme preservation and selector | In Settings, switch among Gentle-Cute, Gentle-Sexy, Omarchy, and System | Every screen immediately adopts the selected palette, with primary colors, backgrounds, and surfaces matching the theme specification |
| U2 | Chat bottom anchoring | Open a chat with messages and receive responses or streamed text | Screen stays at the newest content with the streaming response visible; the user does not need to scroll manually |
| U3 | Floating “More recent” button | Scroll up in chat to read earlier messages while new messages arrive | Reading position is preserved; an animated `Más recientes (N)` button appears and smoothly scrolls to the bottom when tapped |
| U4 | At-a-glance agent card | Observe the main Agents screen | Relevant information (status, active task and indicator, TODO progress, relative activity time, and preview) is immediately readable without horizontal scrolling |
| U5 | Agent priority order | Check agent ordering within each machine group | Agents with pending questions/authorizations appear first, working agents next, followed by agents ordered by most recent activity |
| U6 | Micro-interactions and motion | Interact with cards/buttons and observe working agents | Working-agent pulse, elastic press-scale feedback on cards and buttons, and spring-based (`Spring`) screen transitions appear |
| U7 | System reduced motion | Set device `ANIMATOR_DURATION_SCALE=0` | Continuous animations (working pulse, skeleton shimmer) become static and transitions become instantaneous |
| U8 | Accessibility | Navigate with TalkBack enabled | Interactive elements have descriptive Spanish `contentDescription` values and touch targets of at least 48 × 48dp |

## 7. v0.3.1 Fixes (Protocol, Network Loading, and Machine Filters)

| # | Case | Steps | Expected result |
|---|---|---|---|
| F1.1 | Load paired devices | Settings → Paired devices | Full list loads without errors (unwraps `devices` from the relay response); current device is marked `(este)` and other devices can be revoked |
| F1.2 | Filter revoked devices | Revoke a device or open the app while the relay has revoked devices | Entries with `revoked: true` are filtered out of the active list |
| F1.3 | Load machines and open prompts | After restarting the app, open Agents and Pending | Machines and open prompts load without HTTP deserialization errors (unwraps `machines` and `prompts`) |
| F2.1 | Load all machines in Mesh | Mesh tab → `Todas las máquinas` chip | `MeshSubscribe` sends `{"machineId":null}`; relay 0.5.0 returns the full network snapshot instead of leaving `Cargando red de agentes…` on screen |
| F2.2 | Mesh timeout and HTTP fallback | Delay or interrupt the Mesh websocket for 10 seconds | After 10 seconds without a websocket snapshot, app falls back to `GET /v1/mesh`. A successful relay response loads the map; a 404 shows `Tu relay no soporta la Red de agentes. Actualiza Rp-relay a la versión 0.5.0 o superior.` with **[Reintentar]** |
| F2.3 | Reference-counted subscription ownership | Quickly switch between Mesh and other tabs, or open and dismiss detail sheets | Mesh subscription is not canceled prematurely when an older ViewModel is disposed; the map remains active |
| F3.1 | Clean up Agents filters | Open the main Agents screen with multiple machines or contexts | No redundant `Todas / Empresa / Personal` chip row appears below the selector; contexts and machines remain accessible through `MachineHeroDropdown` (`Todas las máquinas`, contexts, and individual machines) |

## 8. v0.3.2 Consolidation (Tolerant Network Decoding and Decimal Numbers)

| # | Case | Steps | Expected result |
|---|---|---|---|
| F4.1 | Subagent decimal timestamps or numeric tokens | Run Pi subagents whose plugin reports `startedAt` with microseconds (for example, `47725510.340108`) or numeric tokens | Network snapshot decodes without errors; Mesh displays the node and subagents without entering an error screen |
| F4.2 | Malformed subagent or node | Send a snapshot containing a corrupted subagent or node (for example, an unexpected type in `turns`) alongside valid nodes | Only the malformed item is skipped and logged as a warning; remaining valid nodes and subagents render |
| F4.3 | Unreadable root snapshot | Supply an undecodable top-level payload (not a JSON object) | App transitions cleanly to `MeshErrorState` and allows retry |

## 9. v0.3.3 Consolidation (Room v3 Persistence, Conversations, and Groups)

| # | Case | Steps | Expected result |
|---|---|---|---|
| F5.1 | Room 2→3 migration | Upgrade from v0.3.2 to v0.3.3 with saved agents | Agent fields are preserved; new columns (`theme`, `todoCompleted`, `todoTotal`, `activeTask`) receive defaults without data loss |
| F5.2 | Open peer conversation without an existing edge | In Mesh, open a node detail sheet and tap `Ver conversación con X` | Opens the conversation directly between both agents without requiring an edge in the snapshot |
| F5.3 | Filter groups by machine | Select a specific machine in Mesh | Group-chip row shows only groups with members on that machine and automatically selects the most active one |

## 10. `/model` and `/thinking` Command Support (v0.3.4)

| # | Case | Steps | Expected result |
|---|---|---|---|
| F6.1 | `/thinking` command suggestion | Type `/th` in the chat input | `/thinking` suggestion chip appears; tapping it completes the text to `/thinking ` with a trailing space |
| F6.2 | Thinking-level chips | Type `/thinking ` or `/thinking` in the composer | Interactive chips appear for every supported level (`off`, `minimal`, `low`, `medium`, `high`, `xhigh`, `max`) |
| F6.3 | Select and filter a thinking level | Tap a level chip (for example, `medium`) or type a prefix (for example, `/thinking mi`) | Text is replaced with `/thinking medium` or suggestions filter to `minimal`; suggestions disappear after completing the level |
| F6.4 | Interactive command without arguments | Send `/model` or `/thinking` without arguments | Rp-plugin opens an interactive `select` dialog in `PromptBottomSheet`, allowing visual selection |

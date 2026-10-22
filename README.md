# rp-app

An Android app (Kotlin + Jetpack Compose) for viewing and controlling your [Pi](https://github.com/earendil-works/pi) agents through the **RP** relay: live chat, attachments, **question and authorization notifications with direct replies**, and **Agent Mesh** (animated topology and real-time communication flows).

> Status: **implemented** (see [Build from source](#build-from-source)). Design documentation is in `docs/`.

| Document | Contents |
|---|---|
| [docs/INSTALACION.md](docs/INSTALACION.md) | APK installation, relay pairing, and troubleshooting |
| [docs/PLAN-APP.md](docs/PLAN-APP.md) | Detailed app plan |
| [docs/QA.md](docs/QA.md) | Manual E2E tests for notifications, offline mode, Agent Mesh, and more |
| [docs/RESILIENCIA.md](docs/RESILIENCIA.md) | Scalability, resilience, and independence (Google-free push, interactive delivery without `herdr`, Linux/Windows) |
| [docs/COMPATIBILIDAD.md](docs/COMPATIBILIDAD.md) | Verified facts and requirement/environment matrix for `herdr`, `gentle-ai`, `gentle-shell`/`gentle-pi`, and PC separation |
| [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) | Global architecture overview (a copy; canonical in `rp-relay`) |
| [`rp-relay/docs/PROTOCOLO.md`](https://github.com/j3pf/rp-relay/blob/main/docs/PROTOCOLO.md) | Network protocol contract |

## Main features

- **Corporate Org-Chart Layout for Agent Mesh (v0.5.0)**:
  - **Tidy-Tree Layout Engine**: Pure Reingold–Tilford tree algorithm in `MeshLayoutEngine` with horizontal parent centering over subtrees, strict row alignment (`same depth = same row`), multi-root side-by-side positioning on depth 0 with running right contour separation, and no connectors between sibling roots.
  - **Orthogonal Bus Connectors**: Hierarchical parent-to-child connections routed through row channels with vertical drop lines and horizontal buses.
  - **Compact Leaf Bullet Lists**: Subordinate leaf agents hanging under manager nodes cluster into a clean vertical list with status dots and compact progress text when leaves exceed threshold, preventing horizontal viewport overflow.
  - **Mobile Usability & Recentering**: 2D pan and pinch-zoom (0.4x to 2.5x) with sane bounding clamps, top-anchored initial viewport fitting, and a visible "Recentrar" action button in the top bar.
  - **Accessibility & Long Names**: TalkBack hierarchy semantics ("X, reporta a Y"), 2-line wrapped agent names without truncation, and Roborazzi golden coverage across themes and device configurations.
  - Requires **Rp-relay >= 0.5.5** and **Rp-plugin >= 0.2.8**.
- **Agent Mesh Screen Redesign (v0.4.0)**:
  - **Interactive Agent Cards (S2)**: Replaces bare circles with rich cards featuring a machine-tinted avatar showing the first alphanumeric display-name character in uppercase (or `A` if none), a clean display name, secondary line (`machine · branch/model`), live status dot, single-line activity preview, TODO progress bar, and attached subagent chips with real-time execution spinners. Only the orchestrator card displays a role badge; subordinate and solo-agent cards do not. `Subagente` is a role label, and subagents render as attached chips.
  - **Hierarchical Top-Down Layout (S2, S3)**: Replaces radial rings with a top-down hierarchy (orchestrator at the top, subordinates in balanced rows below) and bounding-box collision avoidance for 1–12 agents.
  - **Viewport Containment & Auto-Fit (S3)**: The canvas is clipped below the header and filters. It fits the graph when possible with a 0.65 auto-fit scale floor, and uses scale 1.0 in landscape viewports at least 600dp wide. If content still exceeds the viewport, it is top-anchored with 16dp padding and pan/zoom reaches the rest. Cards have a minimum readable width of 180dp; their heights are measured at runtime with an estimate fallback. Pinch-to-zoom (0.5x–3.0x), pan, and double-tap reset are supported. In landscape layouts below 500dp height, `Máquinas` and `Grupos` filters share one horizontally scrolling row.
  - **Real-State Motion System (S2)**: Working agents show a shimmering border and typing activity dots; running subagents show execution spinners. Orthogonal bus connectors route through row gaps; particles travel along the connector with message previews. A quadratic curve is only a fallback for non-hierarchical edges. Idle state is calm and static with no continuous redraw loops. The system respects `ANIMATOR_DURATION_SCALE=0` and reduced-motion settings.
  - **Display Names & ID Sanitization (S1)**: Raw UUIDs and hashes are hidden from user-facing views. Names resolve from the human-readable name, `basename(cwd)`, or `Agente`. The collapsed `Detalles técnicos` section in the node detail sheet provides one-tap copying for raw IDs.
  - **Architecture Specification & Legend ([docs/MESH.md](docs/MESH.md))**: Documents name-resolution rules, the motion legend, viewport behavior, and the layout engine.
- **Slash Commands `/model` and `/thinking` (v0.3.4)**:
  - Contextual slash-command suggestions and autocomplete in the chat composer, including `/thinking` in the main command list (`/clear`, `/compact`, `/model`, `/thinking`, `/quit`, `/undo`, `/sdd-status`, `/help`).
  - Interactive thinking-level chips (`off`, `minimal`, `low`, `medium`, `high`, `xhigh`, `max`) when entering `/thinking ` or when `/thinking` is the active command.
  - `PromptBottomSheet` in `select` mode provides visual model or thinking-level selection when these commands are sent without arguments.
- **Room v3 Consolidation, Peer Conversations, and Chat State (v0.3.3)**:
  - Verified Room 2→3 migration (`MIGRATION_2_3`) preserves theme, TODO state, and each agent's active task without data loss.
  - Mesh peer conversation history opens directly from a node sheet without requiring a pre-existing edge and preserves the pagination cursor (`nextBefore`).
  - Mesh group chips and default group selection are recalculated according to the selected machine.
  - Chat and subagent state is cleaned up across epoch changes; subagents and TODOs settle to `done`/`aborted` at turn completion, preventing duplicate subagent cards.
  - Fixed websocket subscription races in Mesh using `lastSubscribedMachineId`.
- **Tolerant Network Decoding and Decimal Numbers (v0.3.2)**:
  - `LenientLongSerializer` accepts JSON integers and decimal numbers (rounded half-up to `Long`) for relay `Type.Number` fields, including timestamps and subagent metrics such as `startedAt: 47725510.340108`.
  - Tolerant `MeshSnapshot` list deserializers isolate malformed nodes, edges, groups, and subagents while keeping valid items visible.
- **Network Resilience and Filters (v0.3.1)**:
  - `RelayApi` unwraps object responses for devices, machines, and prompts, and filters revoked devices.
  - `MeshSubscribe` explicitly sends `{"machineId":null}` for the all-machines filter on relays 0.5.0 and later.
  - Mesh falls back to `GET /v1/mesh` after a 10-second websocket timeout, shows a retryable error state, and protects subscription ownership from disposed views.
  - Removed the redundant context-chip row from Agents; contexts and machines are selected through `MachineHeroDropdown`.
- **UI Refresh and Modern Visual Experience (v0.3.0)**:
  - Centralized design tokens (`RpSpacing`, `RpShapes`, `RpGlows`, `RpGradients`, `RpMotion`) derive from `MaterialTheme.colorScheme`.
  - Preserves the Gentle-Cute, Gentle-Sexy, Omarchy/Gentle Shell, and Material You/System palettes, with immediate theme updates from Settings.
  - Chat is anchored to the bottom (`reverseLayout`) and shows the newest message and streaming response without manual scrolling.
  - An animated “More recent (N)” floating button preserves history reading position and counts new messages.
  - Agent cards show wrapped status chips, active task, TODO progress, relative activity time, and a compact preview.
  - Agent ordering prioritizes open prompts and authorizations, then working agents, then recent activity.
  - Spring-based screen transitions, working-agent pulse, press-scale feedback, and list-item animations.
  - Accessibility and reduced-motion support, including 48dp minimum touch targets and static equivalents when `ANIMATOR_DURATION_SCALE=0`.
  - Roborazzi 1.76.0 visual regression suite with 48 reference images covering eight screens across three themes in light and dark modes.
- **Agent Inbox and Live Chat**: Message history, live streaming, tool tracking, diffs, and biometric interaction.
- **Interactive Notifications**: Approve requests and answer questions directly from notifications, with offline retries through WorkManager and deduplication.
- **Agent Mesh (Animated Topology)**:
  - Top-down diagram with orchestrators above subordinate rows and subagents rendered as attached chips.
  - Orthogonal bus connectors route through row gaps; particles travel along the connector for real-time `mesh.comm` activity, with message previews and dashed pending-ask styling. A quadratic curve is only a fallback for non-hierarchical edges.
  - Working-state indicators, TODO progress, pinch zoom, pan, and double-tap reset.
  - Agent and conversation detail sheets with message pagination (`nextBefore`), machine/group filters, a collapsible live strip, and an accessible list view.

## Related projects and references

- Pi (`earendil-works/pi`, v1.2.0): `packages/coding-agent/docs/{extensions,packages,rpc,rpc-extension-ui,json,security,sessions}.md`, `src/core/extensions/{types,runner}.ts`, `src/modes/{interactive/interactive-mode,rpc/rpc-mode}.ts`, and examples such as `permission-gate.ts`, `question.ts`, `notify.ts`, and `send-user-message.ts`.
- [Remote Pi](https://github.com/jacobaraujo7/remote_pi) (Flutter + Rust relay + Pi extension): QR pairing and approvals through `@eko24ive/pi-ask`; it has no real push, which is a key gap this project addresses.
- [Happy](https://github.com/slopus/happy) / Happier (happier-dev): CLI wrapper and Fastify/Socket.IO server with end-to-end encryption.
- [Moshi](https://getmoshi.app/docs): mobile terminal and host-side hook for approval inbox and push notifications.
- [gentle-shell / gentle-pi](https://github.com/Gentleman-Programming/gentle-shell) 4.1.0, [gentle-ai](https://github.com/Gentleman-Programming/gentle-ai), and [herdr](https://github.com/herdrdev/herdr) 0.9.3: user environment; see `docs/COMPATIBILIDAD.md`.

## Download and install (official release)

You can install the app directly on your device without building it:

1. **Download the APK:**
   - 📦 **[RP App v0.5.0 — GitHub Releases](https://github.com/J3pf/Rp-app/releases/tag/v0.5.0)**
   - Official direct-channel release (no Google services): **[rp-v0.5.0-direct.apk](https://github.com/J3pf/Rp-app/releases/download/v0.5.0/rp-v0.5.0-direct.apk)**.
2. **Install on your phone:**
   - **On the phone:** Open the downloaded file and allow your browser or file manager to install unknown apps if prompted.
   - **Over USB with ADB:** Connect a phone with USB debugging enabled and run:
     ```bash
     adb install -r rp-v0.5.0-direct.apk
     ```
3. **Pair with your relay:**
   - On the relay server, run:
     ```bash
     rp-relay pair device
     ```
   - Open **RP**, tap **Scan QR code**, and point the camera at the terminal (or enter the URL and 8-character code manually).
4. **Full guide and troubleshooting:** See [docs/INSTALACION.md](docs/INSTALACION.md) for step-by-step instructions and troubleshooting for Android 13+ permissions, battery optimization, local-network TLS/HTTP certificates, and expired codes.

## Kotlin code formatting

The project uses the ktlint Gradle plugin 14.2.0. Run `./gradlew ktlintCheck` to verify Kotlin formatting, or `./gradlew ktlintFormat` to apply ktlint formatting.

## Build from source

**Quick build (debug APK for your phone):** `scripts/build-apk.sh` installs the SDK if needed, builds the app, and places the APK at `dist/rp-direct-debug.apk`. Use `--install` to install it through ADB; other options include `--fcm`, `--test`, and `--release`. See the manual steps below.

Requirements: JDK 17, Android SDK 36 (`ANDROID_HOME`), and access to Google Maven and Maven Central.

```bash
./gradlew :protocol:test                       # protocol, chat reducer, and push cryptography (pure JVM)
./gradlew :app:testDirectDebugUnitTest         # Robolectric, MockWebServer, and Compose tests
./gradlew :app:assembleDirectDebug             # APK without Google services (direct channel)
./gradlew :app:assembleFcmDebug                # APK with optional FCM (requires app/google-services.json)
```

- **Flavors**: `direct` uses the first-party `remoteMessaging` foreground service and websocket connection to the relay without Google services; `fcm` adds Firebase Cloud Messaging. Both channels can coexist and deduplicate by `promptId`.
- `app/google-services.json` is **not versioned**. Add it manually or inject it in CI (`GOOGLE_SERVICES_JSON`, base64). Without it, the `fcm` flavor builds but FCM is inactive.
- To sign a release, create `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) or use `.github/workflows/release.yml` with `RELEASE_KEYSTORE_B64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` secrets. `versionCode` is the commit count; `versionName` is the semantic version (`app/build.gradle.kts`).
- Network fixtures: `scripts/sync-fixtures.sh [../rp-relay]` copies fixtures from `rp-relay` into `protocol/src/test/resources/fixtures/relay/`; tests decode them using Kotlin types.

## Repository structure

```
protocol/   Pure Kotlin: protocol v1 @Serializable types, PushCrypto (AES-GCM), ULID, and ChatReducer (+ tests)
app/        Compose + Hilt + Room: data/{net,db,repo,secure}, push/, ui/{agents,chat,pending,promptsheet,settings,…}
```

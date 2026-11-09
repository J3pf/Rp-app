# Release Notes — v0.7.0

## Summary

Rp-app **v0.7.0** ships the Unified Kotlin Multiplatform & Compose Multiplatform (`KMP/CMP`) architecture across Android and iOS (`:protocol`, `:data-core`, `:relay-client`, `:shared-ui`), the full 5-screen shared application shell (`RemotePiApp`), native iOS networking + encrypted APNs `NotificationService` extension, adaptive layouts, the new `Moshi (Verde y Oscuro)` theme, open-by-default inline reasoning (`🧠 Razonamiento`), and unified text selection & copying ergonomics.

## Component minimums

| Component | Minimum | Where it is published |
| --- | --- | --- |
| Rp-relay | **0.7.0** (tag `v0.7.0`) for iOS APNs push; **0.6.1** (tag `v0.6.1`) for Android | GitHub release |
| Rp-plugin (Pi) | **0.3.5** (tag `v0.3.5`, minimum `v0.3.0`) | GitHub release |

Neither package exists on npm: `@j3pf/rp-relay` and `@j3pf/pi-rp` both return 404. The minimums are **GitHub release versions**, never registry versions — pointing anyone at npm sends them to a 404.

Installing the plugin resolves to `git:github.com/J3pf/Rp-plugin@vX.Y.Z`.

## What's new

### Unified KMP/CMP Architecture (`:data-core` & `:shared-ui`)

- Extracted `:data-core` with Room KMP (`RpDatabase`, `MIGRATION_2_3`), 17 domain ports (`SecretStore`, `SettingsStore`, `PushEnvelopeCrypto`, `HostGateway`, `AgentsGate`, `PromptSheetGate`, `AttachmentsGate`, `ChatSessionGate`, `BiometricAuthorizer`, etc.), and pure-Kotlin AES-256-GCM encryption (`EncryptedKeyValueSecretStore`, `CommonPushEnvelopeCrypto`).
- Full in-place Android upgrade compatibility from v0.5.0 / v0.6.1 (`adb install -r`), preserving Room databases, DataStore preferences, and Android Keystore encrypted credentials.

### iOS Application Shell & Encrypted APNs Push

- Full 5-screen `RemotePiApp()` navigation shell shared across Android and iOS: Onboarding/Pairing with native QR scanner (`AVFoundation` on iOS, `CameraX`/`MLKit` on Android), Agents inbox, Agent Mesh, Pending Prompts, Settings, Live Chat, New Agent creation, Prompt Bottom Sheet, and Git Diff Sheet.
- Native `UrlSessionRelayTransport` and `UrlSessionSocketDriver` in `:relay-client/iosMain`.
- `NotificationService` app extension (`ServiceExtension.swift`) decrypting `{k,n,c}` push envelopes with `CryptoKit.AES.GCM` (AAD `rp1`) using shared Keychain credentials.

### Chat UX, Themes & Text Selection

- **Open-by-default inline reasoning (`🧠 Razonamiento`)**: assistant thinking blocks start expanded in lightweight italic formatting so reasoning is visible immediately without extra taps.
- **Theme-aware semantic tool pills & inline code**: tool cards (`Run Command`, `Edit File`, `Read`, `Call MCP Tool`, etc.) and inline code spans adapt their accent colors to the active theme.
- **New `Moshi (Verde y Oscuro)` theme**: added `moshi-dark` alongside the pink `Tierno (Cute)` and `Intenso (Sexy)` themes and `Dinámico (Material You)`.
- **Unified text selection & copying**:
  - Single unified `SelectionContainer` across `MessageList` so selecting text in one block cleanly clears any prior selection.
  - Code block header bar with language label and one-tap **Copiar / Copiado** button.
  - **Seleccionar** button next to **Copiar** at the bottom of assistant messages that opens a clean full-message text selection modal.

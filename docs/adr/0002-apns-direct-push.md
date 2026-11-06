# ADR 0002 — APNs direct push (iOS) with the relay sending directly

Status: draft (O2). Owner: O2 (app/relay contract); relay changes are a separate slice on
`unify/apns-relay` (Rp-relay), authorized separately. User decision recorded as L6 in
`odd/tasks/unified-kmp.md` (commit 83a9827): iOS push = APNs sent by the relay directly; Apple is
treated as a third party exactly like Google — the relay only ever ships the opaque `(k,n,c)` envelope
and never sees prompt text in the clear.

## 1. Contract summary

| Aspect | Android (unchanged) | iOS (new) |
|---|---|---|
| Registration | `PUT /v1/devices/me/push` body `{fcmToken?, direct}` (works exactly as today) | same endpoint, body adds platform typing: `{platform:"ios", apnsToken, apnsEnvironment:"sandbox"|"production", direct}`; Android omitting `platform`/`apnsToken` keeps 1:1 compatibility |
| Sender | FCM data message (existing `FcmSender`) | APNs HTTP/2 provider API with the `.p8` auth key (key id + team id), topic = app bundle id |
| Payload | FCM `data {v,k,n,c}` | identical `{k,n,c}` envelope verbatim inside the APNs payload, plus `aps { "mutable-content":1, alert: generic template }` |
| Alert text | none (opaque data) | generic template only, e.g. title `RP`, body feeds the NSE; NO sensitive text in clear (Apple can read it) |
| Decryption | app process (`PushRouter.handleFcm`, `PushCrypto`) | Notification Service Extension (NSE) decrypts Keychain-shared `pushKey` (S12 keychain access group) via O1's `SecureCrypto` port; decrypted body feeds `NotificationCenterPort.showPrompt` |
| Collapse/dedup | FCM `collapseKey`; app dedupes by `promptId` | APNs `apns-collapse-id = promptId` (belt) + unchanged app-side `firstSeen("prompt:<id>")` (suspenders) |
| Bad tokens | FCM `messaging/registration-token-not-registered` → clear `fcm_token` (fcm.ts, dispatcher) | APNs `410 BadDeviceToken`/`403` → clear stored APNs token for that device (same cleanup path) |
| Retry | relay re-pushes an open high-danger prompt at 5 and 10 min (max 3) | identical scheduling; each delivery is a normal APNs send carrying a fresh `pushId` where applicable |
| Environment | n/a | relay selects `api.development.push.apple.com` vs `api.push.apple.com` by the device's `apnsEnvironment` (token AND validation environment are separate ports; the relay stores the value at registration) |

## 2. Size limits

- Relay body cap today: 3000 B pre-encryption (`fitBody`, dispatcher.ts:34-46) so FCM stays under 4 KB after AES-GCM + base64.
- APNs HTTP/2 limit: 4 KB note payload total (headers + alert + custom keys). The encrypted envelope for a trimmed prompt fits ~3-4 KB; contract requirement: NSE must tolerate truncation exactly like the current `fitBody` order — trim `message`, then slice `options` to 3, then clip `title` to 120 B; NSE renders "content hidden" when the body is too thin (matches `MeshComm.displayText` precedents).
- If a QA case exceeds the 4 KB budget: recommend relaying the FALLBACK alert only and letting the foreground/direct WebSocket deliver the full prompt on connection (documented degradation, no special payload).

## 3. Relay-side changes (in the separate `unify/apns-relay` slice, not by O2)

1. `DeviceRow` schema: keep `fcm_token`; add `apns_token TEXT`, `apns_env TEXT`; migration is additive and reversible (relay policy).
2. `PushChannelReq` accepts the platform-typed body (Android clients unchanged).
3. `push/apns.ts`: injectable `ApnsSender` mirroring `FcmSender` (JWT-signed HTTP/2 provider request, 10 s timeout, `410`/`403` handling), wired into `PushDispatcher` next to `fcm`.
4. Payload builder reuses `fitBody` + `encryptPush` (envelope unchanged, AAD `rp1`).
5. `push test` includes the APNs channel in the result array.
6. Endpoints, prefs, re-push scheduling, revocation semantics unchanged.

## 4. Human/user prerequisites (flagged, cannot be worked around)

- Apple Developer portal: Push Notifications capability for the App ID; regenerated provisioning profile (S12 plumbing only).
- APNs Authentication Key `.p8` (key id, team id) provisioned to the relay install; secret stored like other server secrets, never in a repo.

## 5. Consequences for the O2 slice (T4 app side)

- `NotificationCenterPort` + `PromptActionPayload` (approved by O1) feed the NSE output through the same `PromptAnswerDispatcher` so promptId semantics stay identical.
- `SecretStore` must expose the pushKey inside the keychain-access-group so the NSE (separate process) can decrypt it — extra parsing contract for O1 during T4.
- Direct foreground channel (`:relay-client` policy) already covers resiliency: APNs is the "app closed" path, same guarantee level as FCM per RESILIENCIA §3.1.
- Testability: contract-level tests pin payload shapes (fixture-based like `FixturesTest`), NSE decryption is iOS-target (`iosSimulatorArm64` metadata compile at Gate C; interactive device test at Gate D).

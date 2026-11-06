# unified-kmp-o2 — Network, session, push (O2 slice)

Branch `unify/o2-net` in `.worktrees/o2`. Orchestrator session: `01a1af49`. Worker: Obrero 2 (`01a1af4b-3360`).
Slice: `app/.../{data/net,push}`, root `app/.../*.kt` (RpApp, DirectPushService), `:relay-client`; iOS transport + APNs.

## Specs

- **S1** "Centralizar la app para cualquier dispositivo"
- **S2** "debes analizar todo lo que hace la app actualmente y mirar que framework tipo react, flutter, etc, es mejor para desarrollar la app una sola vez y que sea instalable en cualquier dispositivo sin importar si es ios, android, celualr, tablet, celular plegable o no"
- **S3** "no quiero si no desarrollar una unica vez y que aplique para todos de una vez"
- **S4** "La app de android y en el stash de la rama de ios DEBES TOMAR SOLO LOS CERTIFICADOS Y DEMÁS que necesites para ios PERO SOLO ESO, nada de codigo, el codigo estable es el de android UNICAMENTE"
- **S5** "asegurando que no se pierda ni una funcionalidad ni que se introduzcan bugs"
- **S6** "QUE LOS 4 usean ODD y sus sub agentes SI o SI"
- **S7** "usando las mejores practicas del framework elegido"
- **S8** "debes validar que los agentes obreros no se queden pegados, con algun job que monitoree o algo asi"
- **S9** "Tú NO puedes escribir una linea de codigo, todo lo deben hacer los obreros y tu debes validar su calidad cuando te entreguen"
- **S10** Framework: Kotlin Multiplatform + Compose Multiplatform (KMP/CMP).
- **S11** Source of truth for behavior is the Android code on `main`.
- **S12** iOS material allowed: signing/build plumbing only (entitlements, Config.xcconfig, pbxproj reference), NEVER Kotlin/Swift source from iOS branches or stash. iOS adapters written fresh.
- **S14** In-place upgrade: pairing, secure session, Room data, settings preserved.
- **S15** Parity gate: every observable behavior maps to a row in parity matrix with a proof test; no test weakened/deleted/re-recorded.
- **S16** KMP purity: no `android.*`, `androidx.*`, `javax.crypto.*` in `:protocol`, `:relay-client`, or any `commonMain`.
- **S17** Direct flavor foreground `DirectPushService` works without Google Play Services; optional FCM; both deduplicate by `promptId`.
- **S18** No plaintext credentials; session keys/device tokens only in Keystore/Keychain.
- **S19** Diff viewer keeps user-driven discard confirmation before `host.git_discard`.
- **S20** Full verification suite passes with zero failures.

## Tasks

| ID | Specs | Route | Commit |
|---|---|---|---|
| T1 | S5 S11 S15 | Parity matrix `docs/parity/o2-net-push.md` + characterization tests on unchanged Android code + platform ports required | |
| T4 | S15 S17 | Relay session, push, notification actions, retry; iOS URLSession/WebSocket adapters + APNs | |

## Log

- **L1** (user, original request, verbatim):
> Intención
> Centralizar la app para cualquier dispositivo
> Detalles
> Actualmente la app que tenemos estable es para android unicamente, debes analizar todo lo que hace la app actualmente y mirar que framework tipo react, flutter, etc, es mejor para desarrollar la app una sola vez y que sea instalable en cualquier dispositivo sin importar si es ios, android, celualr, tablet, celular plegable o no, etc ya que no quiero si no desarrollar una unica vez y que aplique para todos de una vez
> Ejemplos
> La app de android y en el stash de la rama de ios DEBES TOMAR SOLO LOS CERTIFICADOS Y DEMÁS que necesites para ios PERO SOLO ESO, nada de codigo, el codigo estable es el de android UNICAMENTE
> Acción
> Usando ODD Orquestar los 4 agentes obreros disponibles por intercom para lograr la migracion de la app al nuevo framework asegurando que no se pierda ni una funcionalidad ni que se introduzcan bugs, QUE LOS 4 usean ODD y sus sub agentes SI o SI, usando las mejores practicas del framework elegido, debes validar que los agentes obreros no se queden pegados, con algun job que monitoree o algo asi
> Limites
> Tú NO puedes escribir una linea de codigo, todo lo deben hacer los obreros y tu debes validar su calidad cuando te entreguen

- **L2** (O2): subagent failures (fallback forced, not chosen): gentle-ai-explore task `mw4jq9pe-1-csmx` failed "assistant reported an error (unavailable)"; retry task `mw4k55as-2-d9xf` failed "assistant reported an error (quota)". Per orchestrator instruction, fallback = bounded inline read-only exploration while retrying subagents on heavier units.
- **L3** (orchestrator brief, verbatim):
> ORCHESTRATOR BRIEF - you are O2 (Network, session, push). Orchestrator session: 01a1af49 (subagent-chat-01a1af49-2949-74e2).
> READ FIRST, down to `## Log`: /Users/drift/Projects/J3pf/Rp-app/odd/tasks/unified-kmp.md (specs S1-S20, gates A-E, worker rules). Key: S4/S12 (iOS: signing plumbing only, NEVER read/copy Kotlin/Swift from iOS branches or the stash; iOS adapters are written fresh), S5/S15 (parity: no test weakened/deleted/re-recorded), S16-S20 (KMP purity, direct flavor works without Google Play Services, dedupe by promptId, no plaintext credentials).
> WORKTREE (work ONLY here): /Users/drift/Projects/J3pf/Rp-app/.worktrees/o2 on branch unify/o2-net (from feat/unified-kmp, local.properties already copied). Run session_worktree_register on it first, `cd` into it for every bash command, use absolute paths under it. Never touch the primary checkout or other worktrees. No push/PR/merge/tag. Four workers share this machine: run the narrowest Gradle tasks that prove a change; iOS-native linking only at gates.
> ODD IS MANDATORY (S6), SUBAGENTS TOO: create your own feature doc odd/tasks/unified-kmp-o2.md (Specs quoting the S# you cover + Tasks + Log last) and Engram mirror odd/unified-kmp-o2/tasks + visible todo BEFORE your first write. Use gentle-ai-explore when understanding needs more than one read batch (it can use web for current Ktor/URLSession/APNs/KMP guidance), gentle-ai-worker for 2+ independent units, gentle-ai-verify for high-risk items (push/notification delivery and credentials handling). Test-first RED->GREEN, Conventional Commits in ~400-line slices with tests+docs in the same commit, Risk line on each change. Do not ask the human anything: ask me with intercom ask.
> YOUR SLICE (T1 now, then T4): Android packages dev.j3pf.rp.{data/net, push} + root app/src/main/java/dev/j3pf/rp/*.kt (Application, services) + module :relay-client. Behaviors to cover include: RelayApi (unwraps object responses, filters revoked devices), websocket drivers/reconnect/correlation bus, MeshSubscribe {"machineId":null}, Mesh fallback GET /v1/mesh after 10 s timeout, tolerant decoding (LenientLongSerializer, MeshSnapshot list deserializers), direct flavor DirectPushService (foreground, no GMS) + optional FCM, notification channels, interactive notification actions (approve/answer from the notification), WorkManager offline retry + dedupe by promptId, notification lifecycle (answering from a push clears it - no zombie), battery/permission handling.
> T1 DELIVERABLE (no production code change): docs/parity/o2-net-push.md (columns: ID | behavior incl. edge cases/exact strings | Android source file:line | proof test | status) covering every README "Main features"/CHANGELOG/docs/QA.md/docs/RESILIENCIA.md item of your slice; characterization tests added on UNCHANGED Android code for any row lacking a proof, all green. Include a "Platform ports required" section (name, signature sketch, Android impl, iOS approach incl. URLSession/WebSocket, APNs registration + payload handling + notification actions, retry on iOS). Send port requests to O1 (owner of contracts) and cc me. APNs needs the Push Notifications capability and a regenerated provisioning profile: that is a human step - flag it to me, do not work around it.
> REPORT (short, via intercom send to 01a1af49): 1) ACK within 10 min = your ODD doc path + which S# you cover; 2) PROGRESS one line after each task transition; 3) `READY T1 <branch> <sha> rows=<n> newTests=<n> cmd=<exact test command> result=<pass/fail counts> gaps=<rows without automatic proof + why>`. Then wait for my go for T4 unless I say otherwise. HEARTBEAT: git or file activity at least every 10 minutes (log lines in your ODD doc count); if blocked or waiting, say so immediately via intercom ask - a silent worker gets pinged and then reassigned.
== log L4 ==
- **L4** (O2): gentle-ai-explore task `mw4k6iep-3-jck7` failed "assistant reported an error (quota)". Third attempt; fallback inline continues while retrying subagents on heavier units.
- **L5** (O2): gentle-ai-explore task `mw4kbsnx-4-23a5` failed "assistant reported an error (quota)". Fourth attempt; all subagent attempts failing, T1 evidence being gathered inline with bounded reads.
- **L6** (O2): T1 evidence complete. Parity matrix `docs/parity/o2-net-push.md` (NET-01..NET-16, PUSH-01..PUSH-18, QA/RESILIENCIA mapping, Platform ports table, gaps). Characterization tests added on unchanged code: `app/src/test/java/dev/j3pf/rp/push/PushRouterTest.kt` (6 tests: mute/critical bypass, promptId dedupe, cancel, settled dedupe by pushId, error key, FCM malformed) and `app/src/test/java/dev/j3pf/rp/data/net/ProgressBodyTest.kt` (2 tests). Characterization findings pinned in matrix: PushRouter suppresses (returns true) BEFORE dedupe key registered; okio 16 KiB chunk pins progress reports. Risk: none (test/doc-only).

# unified-kmp-o1 — Data Core KMP Migration

Branch `unify/o1-data` in worktree `.worktrees/o1`. Orchestrator session: `01a1af49`. Worker: Obrero 1 (O1 Data core).
Slice: `app/.../{data/db, data/repo, data/secure, domain, util, di}` + tests; shared data architecture & ports; Room KMP; secure storage.

## Specs

- **S1** "Centralizar la app para cualquier dispositivo"
- **S2** "debes analizar todo lo que hace la app actualmente y mirar que framework tipo react, flutter, etc, es mejor para desarrollar la app una sola vez y que sea instalable en cualquier dispositivo sin importar si es ios, android, celualr, tablet, celular plegable o no"
- **S3** "no quiero si no desarrollar una unica vez y que aplique para todos de una vez"
- **S4** "La app de android y en el stash de la rama de ios DEBES TOMAR SOLO LOS CERTIFICADOS Y DEMÁS que necesites para ios PERO SOLO ESO, nada de codigo, el codigo estable es el de android UNICAMENTE"
- **S5** "asegurando que no se pierda ni una funcionalidad ni que se introduzcan bugs"
- **S6** "QUE LOS 4 usean ODD y sus sub agentes SI o SI"
- **S7** "usando las mejores practicas del framework elegido"
- **S10** "Framework: Kotlin Multiplatform + Compose Multiplatform (KMP/CMP). Not Flutter, not React Native."
- **S11** "Source of truth for behavior is the Android code on `main`."
- **S14** "In-place upgrade: Room v3 consolidation + MIGRATION_2_3, secure session, DB, settings, TODO state, active task preserved"
- **S15** "Parity gate: Every observable behavior maps to a row in parity matrix with a proof test"
- **S16** "KMP purity: no android.*, androidx.*, javax.crypto.* in protocol, relay-client, commonMain"
- **S18** "No plaintext credentials; session keys/device tokens only in Android Keystore / EncryptedSharedPreferences (iOS: Keychain)"
- **S20** "Done means ./gradlew :protocol:test :relay-client:jvmTest :shared-ui:jvmTest :app:testDirectDebugUnitTest has zero failures"

## Tasks

| ID | Specs | Route | Commit |
|---|---|---|---|
| T1 | S5 S11 S15 | Characterization tests on unchanged Android code + parity matrix `docs/parity/o1-data.md` (Gate A) | ec8917c `test(parity): characterize O1 data-core behaviors unchanged, add parity matrix` |
| T2 | S7 S10 S16 | Target architecture ADR `docs/adr/0001-kmp-target-architecture.md` + shared ports contract (Gate B) | 9e7cb3b `docs(adr): decide target KMP architecture and S14 upgrade-proof harness` |
| T3 | S14 S15 S18 S20 | Data core migration to common code (Room KMP, repos, secure/settings store, iOS Keychain actual) + in-place upgrade proof (Gate C1) | pending |

## Log

- **L1** (brief received from orchestrator `01a1af49`):
> ORCHESTRATOR BRIEF - you are O1 (Data core). Orchestrator session: 01a1af49 (subagent-chat-01a1af49-2949-74e2).
> READ FIRST, down to `## Log`: /Users/drift/Projects/J3pf/Rp-app/odd/tasks/unified-kmp.md (specs S1-S20, gates A-E, worker rules). Key: S4/S12 (iOS: signing plumbing only, NEVER read/copy Kotlin/Swift from iOS branches or the stash), S5/S15 (parity: no test weakened/deleted/re-recorded), S14 (in-place upgrade keeps pairing/DB/settings), S16-S20.
> WORKTREE (work ONLY here): /Users/drift/Projects/J3pf/Rp-app/.worktrees/o1 on branch unify/o1-data (from feat/unified-kmp, local.properties already copied). Run session_worktree_register on it first, `cd` into it for every bash command, use absolute paths under it. Never touch the primary checkout or other worktrees. No push/PR/merge/tag. Four workers share this machine: run the narrowest Gradle tasks that prove a change; iOS-native linking only at gates.
> ODD IS MANDATORY (S6), SUBAGENTS TOO: create your own feature doc odd/tasks/unified-kmp-o1.md (Specs quoting the S# you cover + Tasks + Log last) and Engram mirror odd/unified-kmp-o1/tasks + visible todo BEFORE your first write. Use gentle-ai-explore when understanding needs more than one read batch (it can use web for current Room KMP / Compose MP docs), gentle-ai-worker for 2+ independent units, gentle-ai-verify for high-risk items (your slice has two: stored data S14 and credentials S18). Test-first RED->GREEN, Conventional Commits in ~400-line slices with tests+docs in the same commit, Risk line on each change. Do not ask the human anything: ask me with intercom ask.
> YOUR SLICE (T1 now, then T2/T3): Android packages dev.j3pf.rp.{data/db, data/repo, data/secure, domain/model, util, di} + their tests (app/src/main/java/dev/j3pf/rp/..., app/src/test/...). Behaviors to cover include: Room v3 consolidation + MIGRATION_2_3 (theme, TODO state, active task preserved), chat/outbox/retry-id persistence, pagination cursor nextBefore, secure session + device tokens (EncryptedSharedPreferences/Keystore), settings/theme store, machine/agent repositories.
> T1 DELIVERABLE (no production code change): docs/parity/o1-data.md (columns: ID | behavior incl. edge cases/exact strings | Android source file:line | proof test | status) covering every README "Main features"/CHANGELOG/docs/QA.md item of your slice; characterization tests added on UNCHANGED Android code for any row lacking a proof, all green. Also include a "Platform ports required" section (name, signature sketch, Android impl source, iOS approach) - this seeds T2. Then T2 (you own the contracts): docs/adr/0001-kmp-target-architecture.md: module graph, ports, expect/actual-vs-interface policy, ViewModel/state-holder pattern (lifecycle ViewModel KMP), DI-agnostic common code with Hilt kept only as Android composition root + hand-written iOS composition root, Room KMP/BundledSQLiteDriver, settings store, Compose Resources, navigation. Decide and justify against current official docs. O2-O4 will send you port requests; answer them and cc me.
> REPORT (short, via intercom send to 01a1af49): 1) ACK within 10 min = your ODD doc path + which S# you cover; 2) PROGRESS one line after each task transition; 3) `READY T1 <branch> <sha> rows=<n> newTests=<n> cmd=<exact test command> result=<pass/fail counts> gaps=<rows without automatic proof + why>`. After READY T1 keep going to T2 unless I say otherwise. HEARTBEAT: git or file activity at least every 10 minutes (log lines in your ODD doc count); if you are blocked or waiting, say so immediately via intercom ask - a silent worker gets pinged and then reassigned.
- **L2**: Delegated audit of O1 data slice to gentle-ai-explore (task mw4jnkdl-1-q4e6); the model call failed with a quota error, retrying fresh, else explore inline.
- **L3**: T1 baseline on unify/o1-data @ 6c073ef: ./gradlew :app:testDirectDebugUnitTest -> BUILD SUCCESSFUL in 2m16s, 32 suites, tests=243 failures=0 skipped=0 (matches orchestrator L3 count for :app).
- **L4**: Parity matrix docs/parity/o1-data.md written (11+21+34 rows + 8 platform ports); added characterization tests on unchanged code: SettingsRepositoryTest (ST-01..06), DaoCharacterizationTest (DB-04..12), SecretStoreScaffoldTest (SC-04/05). Strict no-production-change for Gate A. Baseline recorded: 243 app tests green.
- **L5**: Port contract decisions: P-BIO/P-PICK approved as interfaces (no expect-class); P-HAPTICS/P-IME rejected as ports (CMP built-ins already cover them), P-VOICE approved with documented error codes. Sent to O3, cc orchestrator. Rationale recorded for T2 ADR.
- **L6**: O3 accepted the five contract decisions (AttachmentsPlatformHost, BiometricAuthorizer, VoiceInputHost; haptics/IME on CMP built-ins). Contracts to be published in docs/adr/0001 at T2. Awaiting full app suite result.
- **L7**: T1 committed on unify/o1-data as ec8917c (single work-unit slice: parity matrix + 3 characterization test classes, 746 insertions, ~842 authored lines total with the ODD doc). Full 4-module S20 verification launched as post-commit confirmation.
- **L8**: ADR 0001 written (11 sections): module graph (:data-core new KMP module), interface-over-expect/actual policy, ViewModel KMP via lifecycle 2.11 multiplatform, Hilt only as Android composition root + hand-written iOS graph, Room KMP 2.8.5 with BundledSQLiteDriver flip gated at C1, datastore-preferences-core settings, org.jetbrains.androidx.navigation, and the S14 upgrade-proof fixture plan (Robolectric crypto-seam fallback; Gate C1 NEEDS one Android emulator session for the connected two-build upgrade test — flagged for orchestrator). S20 suite post-commit: protocol 73 / relay-client 75 / shared-ui 108 / app 268 = 524 tests, 0 failures.
- **L9**: O1 port-request answer to O3 re-delivered with final contract shapes (AttachmentsPlatformHost / BiometricAuthorizer / VoiceInputHost as interfaces; haptics/IME on CMP built-ins) — already sent at 05:53Z, confirmed again per orchestrator request; ADR §11 records it.

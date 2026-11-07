# Parity matrix — O1 Data Core (branch `unify/o1-data`, base `main` @ 482cbb9)

Scope: `app/src/main/java/dev/j3pf/rp/{data/db,data/repo,data/secure,domain/model,util,di}` and their tests.
Behavior oracle: current Android code. Baseline: `:app:testDirectDebugUnitTest` = 32 suites, **243 tests, 0 failures, 0 skipped** (BUILD SUCCESSFUL in 2m16s, worktree @ 6c073ef).

Status legend: `covered` = automated proof exists; `char` = characterization test added on unchanged Android code (T1); `gap` = no automatic proof, reason stated (accepted per S15, port note required).

Status summary (COMPUTED BY SCRIPT over the final status cell of every matrix row): rows=84, proven=63, proof-added=16, partial=1, gap-with-reason=2, device-only=2.


## 1. Room database (entities, DAOs, migrations) — `data/db/`

| ID | Behavior (incl. edge cases / exact strings) | Android source | Proof test | Status |
|---|---|---|---|---|
| DB-01 | Room v3, 6 entities (`machines`, `agents`, `prompts`, `chat_records`, `outbox`, `notification_dedup`), `exportSchema = true` (schemas under `app/schemas`) | `data/db/RpDatabase.kt:8-42` | `db/DatabaseMigrationTest` (helper uses same schema) | proven |
| DB-02 | `MIGRATION_1_2`: `ALTER TABLE machines ADD COLUMN quotasJson TEXT DEFAULT NULL` | `data/db/RpDatabase.kt:35-37` | `DatabaseMigrationTest#migrationFrom1To2To3EndToEnd` | proven |
| DB-03 | `MIGRATION_2_3`: adds `theme TEXT DEFAULT NULL`, `todoCompleted INTEGER NOT NULL DEFAULT 0`, `todoTotal INTEGER NOT NULL DEFAULT 0`, `activeTask TEXT DEFAULT NULL` to `agents`; preserves all pre-existing rows/data | `data/db/RpDatabase.kt:40-48` | `DatabaseMigrationTest#migrationFrom2To3PreservesDataAndAddsNewColumnsWithDefaults`, `#migrationFrom1To2To3EndToEnd`; docs/QA.md F5.1 | proven |
| DB-04 | `AgentDao.observe` returns equipped `AgentWithCounts` (`openPrompts` = count of `prompts` with `state='open'`), ordered `online DESC, lastSeenAt DESC` | `data/db/Daos.kt:50-58` | none (SQL aggregate) | proof-added |
| DB-05 | `MachineDao.syncWelcome` / `AgentDao.syncWelcome`: transactional `upsert(REPLACE)` + `deleteNotIn` — after an `app.welcome` only the ids the relay keeps remain | `data/db/Daos.kt:25-40`, `data/db/Daos.kt:70-90` | none (liveness snapshot pruning) | proof-added |
| DB-06 | `PromptDao.upsertOpen` replay window (`PromptState.REPLAY_WINDOW_MS` = `10 * 60_000L`): a re-published `prompt.opened` or stale `app.welcome` cannot revive a prompt resolved within the window; after the window a legitimately reused id shows again | `data/db/Daos.kt:150-174`, `Entities.kt:64-70` | `PromptDao.recentlyResolved` logic untested standalone | proof-added |
| DB-07 | `PromptDao.resolveMissing`: after `app.welcome`, open prompts not in the relay list are closed with `resolvedBy='cancelled'` | `data/db/Daos.kt:140-147` | indirectly under `PromptDao` flows | proof-added |
| DB-08 | `PromptDao.observeOpen` ordering: `dangerRank DESC, openedAt ASC` (high danger first, oldest first within rank) | `data/db/Daos.kt:112-114` | none | proof-added |
| DB-09 | `ChatDao.deleteEventsBefore`: removes events with `epoch IS NOT :epoch OR seq < :beforeSeq` (keeps entries; history resync pruning) | `data/db/Daos.kt:207-217` | none | proof-added |
| DB-10 | `ChatDao.trim`: keeps only the newest `keep` (5000) rows per agent; every persisted event insert + `persistEvent` each 100 events call `ChatRepository` trim | `data/db/Daos.kt:230-242`, `repo/ChatRepository.kt:191-209` | none | proof-added |
| DB-11 | `OutboxDao`: `put` (REPLACE on `clientMsgId` idempotency key), `get`, `all(relayId)` ordered by `createdAt`, `delete`, `bump` increments `attempts`, `clear` | `data/db/Daos.kt:247-266` | `outbox` is exercised indirectly in ChatRepositoryTest; `bump`/`attempts` untested | proof-added |
| DB-03a | MIGRATION_2_3 preserves EVERY v2 agent column populated (all 20 columns incl. CJK/quotes text, booleans, long lastSeenAt) + v3 defaults (theme NULL, todo 0/0, activeTask NULL) | `data/db/RpDatabase.kt:40-48` v2 columns per `app/schemas/dev.j3pf.rp.data.db.RpDatabase/2.json` | `MigrationFidelityTest#migration2To3SurvivesEveryV2AgentColumnPopulated` | proven |
| DB-03b | MIGRATION_2_3 preserves every OTHER table: machines (incl. v2 quotasJson), prompts, chat_records, outbox, notification_dedup — row counts AND content values | `data/db/RpDatabase.kt:40-48`, schema 2.json/3.json | `MigrationFidelityTest#migration2To3PreservesEveryOtherTable` | proven |
| DB-03c | v0.3.3 claim (README:49): theme/TODO/activeTask write+read through the real AgentDao after migration on the migrated file | `data/db/Daos.kt:65-72`, README.md:49 | `MigrationFidelityTest#v3AgentDaoRoundTripKeepsThemeTodoActiveTask` | proven |
| DB-03d | A v3 DB FILE produced by the CURRENT RpDatabase (real DAO-seeded) re-opens in the current schema and exposes identical rows at VALUE level (agent theme/todo/activeTask, prompt json/state/dangerRank/openedAt/resolved*/NULLs, chat record json/kind/epoch/seq, outbox clientMsgId/json/attempts/createdAt) | `RpDatabase` (fixture: `app/src/test/resources/rp-v3-fixture.db`, generated by `DbFixtureExporterTest` with -Pparity.dbfixture.out) | `MigrationFidelityTest#v3FixtureOpensAndContainsSeededRows` (value assertions, not existence) | partial |
| DB-13 | Destructive-fallback trigger pinned through a test-local builder (kept as a semantics reference; the PRODUCTION pin is DI-02 via AppModuleDatabaseConfigTest) | `di/AppModule.kt:48` (test-local mirror only) | `DestructiveFallbackTest#newerDatabaseFallbackDropsLegacyTablesAndRecreatesCurrentSchema` | proven |
| DB-12 | `DedupDao.firstSeen`: purge older than 24h, then insert; second identical key → `false` (single notification per promptId/pushId); `mark` replaces timestamp; `seenSince(windowMs)` | `data/db/Daos.kt:278-317` | none standalone | proof-added |

## 2. ChatRepository (outbox / retry / persistence) — `data/repo/ChatRepository.kt`

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| CH-01 | `putOutbox` encodes `UserMessage` JSON, retries key = `clientMsgId`; `clearOutbox`/`deleteOutbox` (idempotency) | `repo/ChatRepository.kt:84-104` | `ChatRepositoryTest#\`retry re-sends with identical clientMsgId preserving idempotency\`` (via shared ChatDeliveryPolicy) | proven |
| CH-02 | `isOutboxPending` / `findOutboxMessage` (malformed JSON → null, no throw) / `allPendingOutbox` (no session-equivalent mismatch tolerated) | `repo/ChatRepository.kt:105-124` | exercised via `ChatRepositoryTest` send/retry paths | proven |
| CH-03 | `loadInitialState`: replays `Entry` rows via `ChatReducer.applyEntries` then persisted `AgentEvent` rows sorted by `seq`; `null` when no session or no records | `repo/ChatRepository.kt:130-147` | exercised in `ChatRepositoryTest` (resync/history tests read state) | proven |
| CH-04 | `persistHistory`: on `epochChanged` clears agent; entries re-replace full set (`deleteKind(ENTRY)` + insert, `seq=0`); persisted events filtered to `PERSISTED_EVENTS`, `missing own epoch` inherits `history.epoch`; pruned with `deleteEventsBefore` using `liveFromSeq ^ Long.MAX_VALUE` (null liveFrom → delete all old events) | `repo/ChatRepository.kt:148-189` | `ChatRepositoryTest#\`agent history clears syncing...\``, `\`resync cancels...\`` | proven |
| CH-05 | `persistEvent`: single event insert, trims every 100th insert | `repo/ChatRepository.kt:191-209` | exercised throughout `ChatRepositoryTest` (state replay depends on persisted events) | proven |
| CH-05b | event trim ceiling keeps newest 5000 rows (`trim` on every 100th insert) | `repo/ChatRepository.kt:204-206` | `DAO-C-07` characterization test | proof-added |
| CH-06 | `enqueueBackgroundSend` → WorkManager unique work `send-outbox`, CONNECTED constraint, EXPONENTIAL backoff 15s, expedited fallback (`RUN_AS_NON_EXPEDITED_WORK_REQUEST`) — worker-driven flush; PromptActionHandler reply path keeps failed (401/409) messages in outbox and enqueues retry | `repo/ChatRepository.kt:215-217`, `307-317` | `push/PromptActionHandlerTest#\`responder agente con 401 conserva el mensaje en outbox y encola reintento\`` (:151) | proven |
| CH-07 | `ChatSessionRegistry` reference counting: `open` creates+starts once, `release` stops when no holders | `repo/ChatRepository.kt:249-256` | `ChatSessionRegistryTest` (:relay-client) + usage in ChatRepositoryTest | proven |
| CH-08 | `retry(clientMsgId)` re-sends with identical `clientMsgId`; `removeLocal` drops the rejected item; AUTO delivery/method defaults | `repo/ChatRepository.kt:283-297` | `ChatRepositoryTest#\`user message ack updates state to rejected failed and removeLocal cleans it up\``, `\`retry re-sends with identical clientMsgId preserving idempotency\`` | proven |
| CH-09 | `flushOutbox()` Worker contract: `true` with an empty outbox; `false` when a transient delivery failure keeps the retry key pending (via the shared HTTP policy: Network/5xx/429 keep items; 4xx-except-401/403/429 delete them) | `repo/ChatRepository.kt:300` + `relay-client/ChatDeliveryPolicy.kt:221-253` | `ChatRepositoryTest#\`flushOutbox returns true when the outbox is empty\``, `#\`flushOutbox returns false when the delivery fails\`` | proven |
| CH-10 | `clear()` stops registry, clears chat + outbox for current session (logout path) | `repo/ChatRepository.kt:302-305` | `SessionRepositoryLifecycleTest#\`logoutRevokesBestEffortAndClearsOldRelayDataChannelsCredentialsAndSettings\`` | proven |

## 3. PromptsRepository

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| PR-01 | open prompt flow: `prompt.opened` → open (`state='open'`, `dangerRank`, `openedAt` fallback to `now`), `prompt.resolved` → state `'resolved'` + `resolvedBy`/`resolvedAt` | `repo/PromptsRepository.kt:90-124` | `PromptsRepositoryTest#\`prompt abierto llega al bandeja y se cierra con prompt resolved\`` | proven |
| PR-02 | HTTP fallback when no WS: upload answer over `RelayApi.answer`; HTTP 409 treated as already-answered (no error surfaced); no network → enqueue retry (WorkManager `answer-outbox`) | `repo/PromptsRepository.kt:123-167`, `189-196` | `PromptsRepositoryTest#\`sin websocket responde por HTTP y 409…\``, `\`sin red se encola el reintento\`` | proven |
| PR-03 | WS preferred when connected: answer via `ws_request` (`answer`) | `repo/PromptsRepository.kt:150-160` | `PromptsRepositoryTest#\`con websocket responde por WS\`` | proven |
| PR-04 | `observe(promptId)`/`openFor(agentId)` Stream flows ordered per DB-08 | `repo/PromptsRepository.kt:72-81` | same as PR-01/DB-08 | proven |
| PR-05 | `get(promptId)` returns null w/out active session | `repo/PromptsRepository.kt:84` | covered by PR-01 path | proven |

## 4. AgentsRepository

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| AG-01 | `close outcomes`: `Closed` (relay accepted) / `Archived` (close unsupported → falls back to forget) / `Failed(relayReason)` | `repo/AgentsRepository.kt:113-152` | `AgentsRepositoryCloseTest` (all four outcomes incl. `\`a close error with no relay message keeps the plain text\``) | proven |
| AG-02 | close error after pane gone = Closed not error; herdr-unavailable → fail without forget | `repo/AgentsRepository.kt:113-152` | `AgentsRepositoryCloseTest` (those 2 tests) | proven |
| AG-03 | `agents update` relay events remove/purge local rows; `purge` sends exact id; archived list decode + Spanish failure string | `repo/AgentsRepository.kt:156-194` | `AgentsRepositoryCloseTest#\`an agents update remove deletes the local agent\``, `\`purge sends the exact agent id\``, `\`archived relay failure returns an explicit Spanish error\``, `\`archived list decodes rows and pagination cursor\`` | proven |

## 5. SessionRepository (pairing, logout, settings band)

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| SE-01 | `normalizeUrl` rejects user:pass/query/fragment; requires https except http on localhost / 10.0.2.2 / 127.0.0.1; lowercases host; strips trailing `/`; blank forms/null | `repo/SessionRepository.kt:114-132` | `SessionRepositoryTest#\`normaliza la url del relay\`` | proven |
| SE-02 | `parsePairingUri` only `rp://pair?u=…&c=…` (requires both params); other abilities/null | `repo/SessionRepository.kt:134-140` | `SessionRepositoryTest#\`QR rp pair\`` | proven |
| SE-03 | health-before-pair: storage only replaced after successful pairing; invalid inputs / health failures / pair failures preserve old storage, WorkManager channels and socket | `repo/SessionRepository.kt:88-92` (`SessionPolicy` shared) | `SessionRepositoryLifecycleTest` (3 tests) | proven |
| SE-04 | push registration: no token fetch when no session; reports HTTP failure; uses current credentials + `directPush` preference | `repo/SessionRepository.kt:94-99` | `SessionRepositoryLifecycleTest` (`registrationWithoutSession…`, `registrationReportsHttpFailure…`) | proven |
| SE-05 | `logout`: best-effort revoke, then `connection.stop()`, `WorkManager.cancelAllWork()`, wipe machines/agents/chat/prompts/hosts/mesh/dedup, remove channels, clear credentials+settings | `repo/SessionRepository.kt:100-111` | `SessionRepositoryLifecycleTest#\`logoutRevokes…\``, `logoutWithoutSession…` | proven |
| SE-06 | `applyPushChannels`: persist preference first, HTTP failure ignored; start/stop `DirectPushService`; `registerFcmToken` → `PUT /v1/devices/me/push` | `repo/SessionRepository.kt:94-99` | `SessionRepositoryLifecycleTest#\`applyPushIgnoresHttpFailureAfterPersistingPreference\`` | proven |

## 6. Machines / Host / Mesh repositories

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| MA-01 | `MachinesRepository.refresh` maps `MachineSummary` (`quotasJson`) to entities and syncs (prune not-in); clear on logout | `repo/MachinesRepository.kt:34-115` | indirectly in lifecycle logout test (clear) ; mapping covered by MP-01 | proven |
| HO-01 | `HostRepository` wrappers surface relay failures as `HostResult.Failed` with EXACT strings: no connection = `"Sin conexión con el relay"`; relay ERROR message = the message; ERROR with empty message = the bare code; timeout = `"Tiempo de espera agotado"` | `repo/HostRepository.kt:110-127` (ask()) | `HostRepositoryErrorsTest#notConnectedWrappersReturnFailedWithTheExactNoConnectionMessage`, `#relayErrorSurfacesTheExactMessageAndEmptyMessageKeepsTheBareCode` | proven |
| ME-01 | Mesh subscribe envelope `{"machineId":null}` for `Todas las máquinas` (docs/QA F2.1); unsubscribe; snapshot/update replace; live comm events; reconnect restores subscription; ref Count by owner; decode error payload → error state; clear resets | `repo/MeshRepository.kt:119-200` | `MeshRepositoryTest` (9 tests, incl. F2.2 10s? no — timeout fallback is in RelayApiMesh/RelayConnection tests) | proven |
| ME-02 | Mesh README feature: peer conversation history opens without pre-existing edge and pagination cursor `nextBefore` preserved | `repo/MeshRepository.kt:180-188` | `data/RelayApiMeshTest` (cursor param) + shared-ui tests | proven |

## 7. SecretStore (S18 credentials) — `data/secure/SecretStore.kt`

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| SC-01 | Session model (`relayId/relayUrl/relayName/deviceId/deviceToken/pushKey`) is shared (relay-client commonMain `Session.kt`); persisted forms encrypt with Tink AES256_GCM, keyset prefs `rp_keyset`, master key `android-keystore://rp_master_key` | `relay-client/.../secure/Session.kt` + `data/secure/SecretStore.kt:35-49` | `\`TinkDecryptionTest\`` (format only, upgrade — no real SecretStore interaction) | proven |
| SC-02a | save → round-trip all 6 encrypted fields, no plaintext leaks into prefs; ciphertexts landed encrypted and a fresh instance reads back identical (validated under Robolectric's software AndroidKeyStore, 2026-11-06 probe) | `data/secure/SecretStore.kt:80-98` | `SecretStoreScaffoldTest#saveRoundTripsAllSixFieldsThroughFreshInstance`, `#clearRemovesEverythingForFreshReaders` | proven |
| SC-02b | real Android Keystore master-key readability after device upgrade (the SAME physical key must decrypt the SAME persisted blob) | `data/secure/SecretStore.kt` + real device Keystore | **none** | device-only |
| SC-03a | load tolerance: garbled/tampered field => null session, no crash | `data/secure/SecretStore.kt:110-135` | `SecretStoreScaffoldTest#garbledCiphertextYieldsNullSessionInsteadOfCrashing` (real corrupted base64 in rp_secrets) | proven |
| SC-03b | keyset corruption reset path (`resetCorruptedKeyset()`: Keystore entry delete + prefs clear + one retry) | `data/secure/SecretStore.kt:50-63` | **none** (driving it requires corrupting the real keyset under the real master key) | device-only |
| SC-04 | `newRelayId()` = 8 random bytes as 16 lowercase hex chars; two calls differ | `data/secure/SecretStore.kt:112-115` | none | proof-added |
| SC-05 | `current()`/StateFlow scaffold: fresh install → `null`; `clear()` resets StateFlow and prefs field list | `data/secure/SecretStore.kt:63-68` (StateFlow init), `:107-110` | none | proof-added |
| SC-06 | Secret prefs excluded from backup — superseded by MAN-01 (same facts, status proven there) | see MAN-01 | see MAN-01 | proven |

## 8. SettingsRepository (settings/theme store) — `data/repo/SettingsRepository.kt`

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| ST-01 | Defaults for fresh install: `selectedContext` `AppSettings.ALL`, `directPush=true`, `requireUnlockToApprove=true`, `criticalAlways=true`, `appLock=false`, `flagSecure=false`, `onboardingDone=false`, `batteryAsked=false`, `notifyPrefs=NotifyPrefs()`, `mutedAgents=∅`, `appTheme="auto"`, `chatFontScale=1.0f`, `chatMessageSpacing="normal"`, mesh selections null | `repo/SettingsRepository.kt:33-59` | none | proof-added |
| ST-02 | `chatFontScale` clamped to `[0.80, 1.45]` on load and on `setChatFontScale` (guard: injected out-of-range value re-coerced at read) | `repo/SettingsRepository.kt:54`, `:81` | none | proof-added |
| ST-03 | `setAgentMuted` adds/removes on the `stringSetPreferencesKey` without touching other settings; `setMeshSelectedMachine/Group(null)` removes the key (shows "Todas") | `repo/SettingsRepository.kt:97-104` | none | proof-added |
| ST-04 | `notifyPrefs` stored as JSON string; malformed JSON in storage → default `NotifyPrefs()` (no crash); `setNotifyPrefs` round-trip | `repo/SettingsRepository.kt:44-46` | none | proof-added |
| ST-05 | `wipe()` clears every key (logout path SE-05) | `repo/SettingsRepository.kt:105` | exercised indirectly in `SessionRepositoryLifecycleTest#logout…` (settings cleared) | proven |
| ST-06 | DataStore `IOException` during read → emptyPreferences fallback (no crash on corrupt file); SUPERSEDED by ST-08 which pins it with REAL garbage bytes | `repo/SettingsRepository.kt:36-39` | `DataStoreFixtureTest#corruptPreferencesFileFallsBackToEmptyPreferences` | proven |

## 9. BiometricGate (`util/BiometricGate.kt`)

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| BI-01 | 5-min freshness window (`VALIDITY_MS = 5*60_000L`): not fresh before any auth, fresh within `VALIDITY` after `recordSuccessForTest`, expired exactly at limit; `invalidate` clears immediately | `util/BiometricGate.kt:20-28`, `companion :105` | `TinkDecryptionTest#\`biometric gate is not fresh until explicit action authentication…\`` | proven |
| BI-02 | API 29 device-credential fallback (`setDeviceCredentialAllowed`) and API≥30 `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` combinatorics; `authenticate(force=true)` bypasses freshness; cancellation cancels the prompt; error → `AuthResult.FAILED`; unavailable → `AuthResult.UNAVAILABLE` | `util/BiometricGate.kt:30-52`, `:55-103` | requires live BiometricPrompt UI | gap-with-reason |

## 10. Format / utils (thin delegates to `shared-ui` common utilities) — `util/Format.kt`

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| ST-07 | On-disk settings file: `rp_settings.preferences_pb` (DataStore Preferences format, key set per ST-01) — part of the S14 upgrade surface (in-place install reads the SAME file) | `data/repo/SettingsRepository.kt:24` (delegate `preferencesDataStore(name = "rp_settings")`) | fixture `app/src/test/resources/rp_settings.preferences_pb` (exported by PRODUCTION setters through `DataStoreFixtureExporterTest#productionSettersPersistFixtureValuesAndOptionallyExportBytes`); read-back `DataStoreFixtureTest#onDiskFileFixtureReadsBackExactValues` | proven |
| ST-08 | ST-06 fallback pinned with REAL corruption: garbage bytes in the preferences file produce `AppSettings()` defaults without crashing (DataStore corruption surfaces as an `IOException` subtype, so the production `catch { if (e is IOException) emit(emptyPreferences()) }` path is what fires) | `data/repo/SettingsRepository.kt:36-39` | `DataStoreFixtureTest#corruptPreferencesFileFallsBackToEmptyPreferences` | proven |
| CH-11 | Offline send / delivery semantics: no network -> message queued in outbox and retried with the STABLE `clientMsgId`; delivery is EFFECTIVELY-ONCE, `flushOutbox` keeps failed rows (QA N4/A4, RESILIENCIA 3.2) | `repo/ChatRepository.kt` (delivery wiring) + `:relay-client ChatDeliveryPolicy` | `relay-client ChatDeliveryPolicyTest#httpFallback500OrNetworkEnqueuesBackgroundAndReturnsQueued`, `#retryReusesStableClientMsgIdFromOutbox`, `#flushOutboxDeletesSucceededMessagesAndLeavesFailedOnes`, `#wsDeliveryWithAckFailureReturnsFailedAndDeletesOutbox` | proven |
| CH-12 | Outbox payload survives a schema migration: fixture outbox row (real v3 DB produced by current code) still opens and its JSON is decodable through the tolerant path | `data/db/RpDatabase.kt`, `repo/ChatRepository.kt:110-117` | `MigrationFidelityTest#v3FixtureOpensAndContainsSeededRows` (row-level) + `ChatRepositoryTest` decode paths | proven |
| PR-06 | After `app.welcome`, resolved prompts older than 24 h are PURGED (repository-level trigger: `syncWelcome` calls `dao.purgeResolved(now - 24h)`; the shared base row pins the DAO) | `repo/PromptsRepository.kt:118` | `PromptsRepositoryTest#app welcome purges resolved prompts older than 24h and keeps fresh ones` + `DaoCharacterizationTest#purgeResolvedRemovesOnlyResolvedRowsOlderThanCutoff` | proven |
| APP-01 | Stale local attachment staging files are cleaned at app start (`attachments.cleanup()` default 24 h window; BOTH `cacheDir/attachments` and `cacheDir/camera`; only files older than the window go) | `RpApp.kt:46`, `repo/AttachmentsRepository.kt:144-149` | `AttachmentsCleanupTest#cleanupDeletesOnlyFilesOlderThan24hInBothStagingDirs` | proven |
| SE-07 | docs/QA.md P8 sign-out: credentials + database + notification channels + settings wiped (cross-reference for the audit) | `repo/SessionRepository.kt:100-111` | `SessionRepositoryLifecycleTest#logoutRevokesBestEffortAndClearsOldRelayDataChannelsCredentialsAndSettings`, `#logoutWithoutSessionDoNotRevokeAndStillRemovesChannelsAndSettings` (same as SE-05) | proven |
| MAN-01 | `android:allowBackup="false"` OFF (FLAG_ALLOW_BACKUP unset) + FULL-DOMAIN exclusions: `backup_rules.xml` and `data_extraction_rules.xml` (cloud-backup and device-transfer) exclude the ENTIRE `sharedpref`, `database` and `file` domains with `path="."` (STRONGER than naming secrets: `rp_secrets`/`rp_keyset*` cannot leave the device) | `AndroidManifest.xml:22-24`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` | `secure/BackupRulesTest#allowBackupIsFalseInTheManifest`, `#backupRuleXmlsExcludeTheWholeSharedprefDatabaseAndFileDomains` | proven |
| UT-01 | `lastSegment/relativeTime/humanSize/contextLabel/contextEmoji/agentTitle/parseColor/stableId/truncate` delegate 1:1 to `shared-ui/ui/util` | `util/Format.kt:7-48` | `shared-ui` `AgentCardTest` + `RemotePiAppTest` cover the shared implementations spirit (`hace 5 min`, `hace 2 h`, `Feature X`, titles, etc.) | proven |

## 11. Domain mappers (incl. theme/TODO/activeTask S14 fields) — `domain/model/Mappers.kt`

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| MP-01 | `MachineSummary.toEntity`/`toModel` quotas JSON round-trip; invalid/absent persisted quotas → null (no crash) | `domain/model/Mappers.kt:13-29` | `MappersTest#\`machine summary quotas persist and map back\`` + `\`null or invalid persisted quotas map to null\`` | proven |
| MP-02 | `AgentSummary.toEntity`/`toModel` full round-trip incl. `theme`, `todoCompleted/todoTotal`, `activeTask` (S14 v3 columns), herdr triple → `HerdrInfo` only when any id present, `model` label empty → null | `domain/model/Mappers.kt:33-107` | `MappersTest#\`agent summary theme and todo fields persist and map back\`` | proven |
| MP-03 | `Prompt.toEntity` uses `Danger.rank(danger)` and `openedAt ?: now`; `PromptEntity.toModel` decodes JSON | `domain/model/Mappers.kt:110-121` | exercised in PromptsRepositoryTest (opening order test, DB-08 chained) | proven |

## 12. DI (`di/AppModule.kt`, `di/Qualifiers.kt`)

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| DI-01 | Singletons wired (`AppScope` process-lifetime `CoroutineScope`), `Qualifiers`, Room singleton with `MIGRATION_1_2/2_3` and **`fallbackToDestructiveMigration(dropAllTables = true)`** — CORRECTED by verifier: the destructive fallback EXISTS (`di/AppModule.kt:48`); kept identical | `di/AppModule.kt:42-50`, `di/Qualifiers.kt` | `AppModuleDatabaseConfigTest` (real provider: legacy v4 file wiped; real v2 file migrated in place with data kept) | proven |
| DI-02 | `fallbackToDestructiveMigration(dropAllTables = true)` trigger semantics: any version the shipped migrations cannot reach wipes ALL tables (incl. foreign legacy tables) and recreates at the current version — user data intentionally discarded rather than crashing. Pinned THROUGH THE REAL PROVIDER: mutating `dropAllTables` to false in AppModule makes the test go red (verified mutation probe) | `di/AppModule.kt:48` (behavior pinned, production unchanged) | `AppModuleDatabaseConfigTest#destructiveFallbackThroughTheRealProviderWipesLegacyTablesAndKeepsSchemaFresh` | proven |

## 13. AttachmentsRepository (Android upload base for chat composer)

| ID | Behavior | Android source | Proof test | Status |
|---|---|---|---|---|
| AT-01 | `prepare(uri)`: image compress via `BitmapFactory` (max size, quality), name sanitize (`UNSAFE_FILENAME_REGEX` → `_`, max 120, empty → `"archivo"`), mime from content resolver; `upload` → `RelayApi` blob; `discard` deletes temp file; `cleanup` removes targets older than 24h; `newCameraTarget` FileProvider URI | `repo/AttachmentsRepository.kt:64-220` | Robolectric-capable but heavily Android-gap (Bitmap codec + ContentResolver) | gap-with-reason |

## Platform ports required (seeds T2 ADR) — O1 own slice

| Port | Signature sketch | Android impl source | iOS approach |
|---|---|---|---|
| `SecureStore` | `current(): Session?`, `save(session)`, `clear()`, `session: StateFlow<Session?>`, `current(): secrets-rx session`, `newRelayId(): String` | `data/secure/SecretStore.kt` (Tink+Keystore+EncryptedSharedPreferences-backed prefs) | Keychain (S18) with the S12 entitlement `keychain-access-groups`; keyset stored as Keychain `kSecClassGenericPassword`, AES-GCM via CryptoKit; `newRelayId` via `SecRandomCopyBytes` |
| `SecretStoreCrypto` (internal seam) | `encrypt(value, key): String`, `decrypt(value, key): String?` | `SecretStore.kt:107-142` internal AD fixup (field key as associated data, Base64 NO_WRAP) | same associated-data discipline; unit-testable seam so S14 upgrade proof can run without device Keystore |
| `SettingsStore` (DataStore replacement) | `settings: Flow<AppSettings>` + suspend setters as listed in KeysObserver (ST-01 keys exactly); `wipe()` | `repo/SettingsRepository.kt` (androidx.datastore.preferences on Android) | `NSUserDefaults` + `kotlinx-coroutines` StateFlow flow bridge; encode `notifyPrefs` JSON identically (same JSON, same `mutedAgents` set semantics) |
| `RpDatabase` port | same 6 entities + DAOs; `MIGRATION_2_3` via Room KMP schema | `data/db/*` (Room 2.7, SupportSQLiteDatabase migrations) | Room KMP (BundledSQLiteDriver, `iosArm64`/`iosSimulatorArm64`); migrations via `migrateCallback` path: ship v2 schema + `MIGRATION_2_3` SQL verbatim + S14 upgrade test against v2-produced SQLite bytes |
| `WorkScheduler` | `enqueueBackgroundSend()` (unique work `send-outbox`), answer retry enqueue (PR-02) | `WorkManager` (app) — `ChatRepository.kt:307-317`, `PromptsRepository.kt:189-196` | BGTaskScheduler (iOS app refresh task + BGProcessingTask); direct-push equivalent gated by S17 free-tier degradation: on iOS background push via APNs replaces long-lived socket wakeups |
| `PlatformClock/Uuid/Ulid` | `now(): Long`, `newTerminalId()` etc. | `Ulid` already in `:protocol` commonMain; `System.currentTimeMillis` seam in repos (`now = { System… }` lambdas, e.g. `ChatRepository.kt:251`) | keep as Kotlin lambdas (no port needed beyond injection), iOS same logic |
| `NotificationsRemove` | `removeAll()` on logout | `NotificationChannels` (O2 slice, consumed here SE-05) | iOS: `UNUserNotificationCenter.removeAllDeliveredNotifications()` |
| App-attachments platform surface | `prepare(uri)`, compress, `newCameraTarget` | `repo/AttachmentsRepository.kt:75-173` | iOS: `PhotosPicker`/`fileImporter` + UIImageJPEGRepresentation-equivalent compression; write fresh code, never extract O2 sources (S4/S12) |

## Move 4 — port bind coverage (ADR 0003 §4)

Verification note: 11 focused tests EXECUTED GREEN (`/gradlew :app:testDirectDebugUnitTest --tests dev.j3pf.rp.platform.*` in-process, +1 Robolectric API caveat solved with sdk 30) AND `:app:compileDirectDebugKotlin` green (whole Hilt graph resolves). Structural pins learned REAL platform semantics: WorkInfo records survive cancelAllWork in CANCELLED state; merged manifest (minSdk 29) cannot parse below Robolectric sdk 29. Expense preserved: enqueue block copied byte-for-byte from ChatRepository.kt:311-316.

| Port | Android binding | Evidence (implementation + test) | Status |
|---|---|---|---|
| `BackgroundScheduler` | `BackgroundScheduler` → `WorkManagerBackgroundScheduler` (unique `send-outbox`, connected constraint, exponential 15 s backoff, expedited with non-expedited quota fallback) | `app/src/main/java/dev/j3pf/rp/platform/WorkManagerBackgroundScheduler.kt:21`; `app/src/test/java/dev/j3pf/rp/platform/WorkManagerBackgroundSchedulerTest.kt:44` | proven |
| `DedupStore` | `DedupStore` → `DaoDedupStore` (`DedupDao.firstSeen`) | `app/src/main/java/dev/j3pf/rp/platform/DaoDedupStore.kt:9`; `app/src/test/java/dev/j3pf/rp/platform/DaoDedupStoreTest.kt:35` | proven |
| `OpenPromptGuard` | `OpenPromptGuard` → `DaoOpenPromptGuard` (current session relay + open prompt state) | `app/src/main/java/dev/j3pf/rp/platform/DaoOpenPromptGuard.kt:11`; `app/src/test/java/dev/j3pf/rp/platform/DaoOpenPromptGuardTest.kt:39` | proven |
| `SettingsStore` | `SettingsStore` → existing `SettingsRepository` | `app/src/main/java/dev/j3pf/rp/data/repo/SettingsRepository.kt:28`; `app/src/test/java/dev/j3pf/rp/platform/AndroidPortBindingsTest.kt:24` | proven |
| `NotificationHealth` | `NotificationHealth` → `AndroidNotificationHealth` | `app/src/main/java/dev/j3pf/rp/platform/AndroidNotificationHealth.kt:13`; `app/src/test/java/dev/j3pf/rp/platform/AndroidNotificationHealthTest.kt:33` | proven |
| `BatteryExemption` | `BatteryExemption` → `AndroidBatteryExemption` | `app/src/main/java/dev/j3pf/rp/platform/AndroidBatteryExemption.kt:11`; `app/src/test/java/dev/j3pf/rp/platform/AndroidBatteryExemptionTest.kt:31` | proven |
| `PlatformInfo` | `PlatformInfo` → `AndroidPlatformInfo` (`BuildConfig` values) | `app/src/main/java/dev/j3pf/rp/platform/AndroidPlatformInfo.kt:9`; `app/src/test/java/dev/j3pf/rp/platform/AndroidPlatformInfoTest.kt:14` | proven |

## Move 1 — Room CORE move (ADR 0003 §1, slice A")

**Risk: S14 stored-data schema.** `Entities.kt` and `Daos.kt` moved to `:data-core` with byte-identical contents; `RpDatabase.kt` moved with only the Room KMP constructor and `SQLiteConnection` migration API adjustments. All five migration SQL statements compare byte-identically to the original app source. The app's three former source files now contain only move comments (no duplicate Room declarations).

Room 2.8.5 KSP export: `data-core/schemas/dev.j3pf.rp.data.db.RpDatabase/3.json`; identity hash `b4ad483aeb2f27bb4b65b4b725d2cfd7`. Canonicalized JSON comparison against the unchanged `app/schemas/.../3.json`: **semantically identical** (tables, columns, indices and SQL); no schema patch was made.

Verification: `:data-core:compileKotlinIosArm64` + `:data-core:compileKotlinJvm` passed. Full `:app:testDirectDebugUnitTest` passed: **417 tests, 0 failures, 0 errors, 2 skipped, 76 suites**; `AppModuleDatabaseConfigTest` passed 2/2.

## Move 1 — mapper + characterization-test relocation (slice B")

- Mapper implementation moved to `data-core/src/commonMain/kotlin/dev/j3pf/rp/domain/model/Mappers.kt`; the app path is a relocation comment, not a duplicate implementation. This preserves MP-01..MP-03 behavior.
- `DaoCharacterizationTest` and `MappersTest` moved to `data-core/src/commonTest`. DB-04..DB-12 characterization assertions and MP-01/MP-02 mapper assertions were not edited. Only test imports/lifecycle and the DAO Room bootstrap changed: Robolectric/ApplicationProvider was replaced by common Room `inMemoryDatabaseBuilder` with `BundledSQLiteDriver`.
- Fresh totals: before move — app **431 tests / 78 suites / 0 failures / 0 errors / 2 skipped**, data-core **5 / 1 / 0 / 0 / 0**; after move — app **413 / 76 / 0 / 0 / 2**, data-core **23 / 3 / 0 / 0 / 0**. Net: **436 tests before and after**, 0 failures/errors, 2 skipped; 18 tests relocated.
- Gate results: `:data-core:jvmTest :data-core:compileKotlinIosSimulatorArm64` and `:app:testDirectDebugUnitTest :data-core:jvmTest` both passed with in-process Kotlin and `-Xmx3g`. The full app suite includes `AppModuleDatabaseConfigTest` (2/2 passed).
- Schema parity rechecked: app and data-core v3 JSON compare equal; both carry identity hash `b4ad483aeb2f27bb4b65b4b725d2cfd7`. The production destructive-fallback implementation was not changed; its existing real-provider test passed. No mutation probe was repeated because `AppModule.kt` is outside slice B".

**Risk: item 6 (characterization net must stay byte-equivalent or it stops being a net).**

## Move 2 — settings now common (a7a1098/516ac0a)

- `DataStoreSettingsStore` (dev.j3pf.rp.data.settings) is the new writer; `SettingsRepository` delegates. The ST-* evidence column now cites the common store; ST-08 corrupt fallback and C1/C2 pins live in SettingsContractIdentityTest and DataStoreBidirectionalCompatibilityTest; 5 mutation probes DETECTED (default-body pin / C1 rename / C2 key / ST-08 catch / SE-05 wipe).

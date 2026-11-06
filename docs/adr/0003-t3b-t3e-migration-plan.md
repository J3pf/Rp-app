# T3b → T3e execution plan (data core migration) — docs-only, BLOCKED until O3/O4 land on feat/unified-kmp

Branches: `unify/o1-data` (worktree `.worktrees/o1`), base `feat/unified-kmp` @ c621c38 (ADR 0001 + Gate A parity net merged).
Premise of every step: the Gate A net (77 parity rows; app suite ≥ 288 green; never re-record goldens) must stay green on `feat/unified-kmp` BEFORE each move, and AFTER each move with only mechanical signature updates listed in the report (S15).

## 0. Room KMP schema/version strategy (S14, fixed rules for T3b)

- SAME DB FILE NAME: `rp.db` (AppModule.kt:47). The migrated common builder keeps the name and the composition root supplies only the path (Android `context.getDatabasePath("rp.db")`).
- SAME VERSION: `version = 3`, `exportSchema = true`, schemas exported per-module; before Gate C1 a mechanical diff must show the `:data-core` exported `3.json` equals `app/schemas/.../3.json` (table names, columns, indices, create SQL strings).
- SAME MIGRATIONS VERBATIM: `MIGRATION_1_2`, `MIGRATION_2_3` SQL strings move byte-for-byte; `addMigrations` composition identical.
- DESTRUCTIVE FALLBACK UNCHANGED: `fallbackToDestructiveMigration(dropAllTables = true)` stays in the Android composition root (AppModule) — pinned by `AppModuleDatabaseConfigTest` (DI-02), which MUST go red under the mutation probe (`dropAllTables = false`) after every move; re-run the probe per step.
- DRIVER: Android keeps the framework driver (ADR §6 rule); iOS + JVM tests use `BundledSQLiteDriver`. The Android Bundled flip is NOT part of T3b-T3e; it is a separate gated commit if DB rows are proven on both drivers.

## 1. Move 1 — Room entities/DAOs/DB → `:data-core` (T3b)

Files: `app/.../data/db/{Entities,Daos,RpDatabase}.kt` → `:data-core/commonMain`; `db` package name unchanged.
Expect/actual: NONE for schema; only the builder seam `RpDatabases.builder(path, driver)` in commonMain.
KSP: room-compiler for each target; schema location `:data-core/schemas`.
Rows that MUST stay green after this move: DB-01..DB-13 (incl. DB-03a..d via `MigrationFidelityTest` + the committed `rp-v3-fixture.db`), DB-04..DB-12 (`DaoCharacterizationTest` moves to `:data-core` commonTest — SAME assertions, Robolectric-free for in-memory Room), DI-02/di pin (`AppModuleDatabaseConfigTest` stays in :app, now exercising the MOVED classes = real upgrade proof), MP-01..MP-03 (Mappers move with the entities in the same unit: `domain/model/Mappers.kt` → commonMain; `MappersTest` moves as-is), CH-* rows that read the DAOs (fixtures unchanged), PR-*, ST-* unaffected.
Commit slices: (a) module scaffolding + schema export green on jvm/iosJsSim compile; (b) entities+DAOs+DB+mappers move + their tests move (single ~400-line work unit); (c) `:app` consumes `:data-core` (delete android dupes, Hilt binds unchanged, `AppModuleDatabaseConfigTest` green + mutation re-probe).

**Approved execution re-scope (Room CORE move):** For the current execution, the prior (a)/(b)/(c) labels are superseded by three smaller slices: **A" — Room CORE move** (`Entities.kt`, `Daos.kt`, and `RpDatabase.kt` move together to `:data-core` commonMain, with only the Room KMP constructor and migration API adjustments; export and mechanically verify schema v3); **B" — test moves** (relocate the DAO characterization and mapper tests without changing assertions); **C" — `:app` flip + verify** (consume the moved Room classes from `:data-core`, retain the Android composition root and destructive-fallback behavior, and run the upgrade/parity pins). Keeping the database declaration in A" is required for Room KSP to produce the canonical `:data-core/schemas/.../3.json`; the app schema remains the baseline and is not patched. S14's identity hash and schema semantics remain hard gates.

## 2. Move 2 — Settings/DataStore → `:data-core` (T3c-common)

- `SettingsRepository` becomes a `SettingsStore` IMPLEMENTATION in commonMain backed by `datastore-preferences` (the KMP artifact); the `Context.rpDataStore` delegate stays as the Android wiring (name `"rp_settings"`, same file, ST-07).
- expect/actual ONLY for the preferences FILE path; the AppSettings type comes from `:relay-client`.
- Rows to keep green: ST-01..ST-08 (incl. corrupt fallback ST-06/08 via the committed `rp_settings.preferences_pb`), SE-05/SE-07 (logout wipe through the real SessionRepositoryLifecycleTest).
- Same commit includes `FakeSettingsStore` alignment (fakes in `:data-core` commonTest gain the exact setter list).

## 3. Move 3 — Secure store → ports + Android actual (T3c-secure)

- `SecretStore` splits: contract already in `:data-core` (`SecretStore`, `SecureCrypto`); the TINK/Keystore implementation becomes an androidMain `SecretStoreAndroid` (same prefs names `rp_secrets`/`rp_keyset`/`rp_keyset_prefs`, same field-AD, same Base64 NO_WRAP, same reset path).
- `:app` Hilt binds `SecretStore`→`SecretStoreAndroid`; all SC rows (SC-01..SC-03a) move verbatim into `:data-core` androidTest/Robolectric tests — same assertions, and the SAME encryption format fixture test (TinkDecryptionTest) stays untouched.
- Rows to keep green: SC-01, SC-02a, SC-03a (+ SC-04/SC-05 = `newRelayId` hex contract, session StateFlow scaffold), plus SessionRepositoryCTest rows (SE-01..SE-07) because SessionPolicy consumes the store.
- iOS actual (T3d, CORRECTED): `SecretStoreIos` in `:data-core/iosMain` using Keychain `kSecClassGenericPassword`; the keychain ACCESS-GROUP string is a CONSTRUCTOR PARAMETER of the actual, NOT a constant — the S12 entitlement/Config.xcconfig/pbxproj plumbing stays with O4 (T6); O1 never touches `iosApp/**` in T3.
- iOS crypto engine (orchestrator correction 2026-11-06): CryptoKit is SWIFT-ONLY and is NOT importable through Kotlin cinterop (no ObjC header). T3d opens with a SPIKE that must compile AND round-trip on the iosSimulatorArm64 target before committing: option (a) a Kotlin interface in `:data-core` implemented by the Swift host (CryptoKit AES-GCM) injected at startup and shared with the NSE via the same Swift file; option (b) Security.framework/CommonCrypto via cinterop IF it yields AES-256-GCM with 12-byte nonce, AAD and the same ciphertext||tag layout as today.
- Cross-implementation vector (mandatory either way): a fixed key/nonce/AAD test vector computed from the EXISTING Android helper (`pushCrypto` in `:protocol` jvmMain / relay `encryptPush`) is committed in `:data-core` commonTest; the chosen iOS actual MUST decrypt it (S18/S5).
- PushCrypto ownership: `PushEnvelopeCrypto` stays in `:data-core` commonMain; Android actual wraps the existing `:protocol` jvmMain helper (protocol gets NO lift yet — delegation); the Swift NSE receives `relay push envelope (k,n,c)` and calls the shared helper — thin caller only.

## 4. Move 4 — Background scheduler port binding — HARD ORDERING: lands BEFORE O2's T4 (no exceptions)

- `BackgroundScheduler` port (already in `:data-core`); Android impl = thin adapter over WorkManager (`enqueueSend`/PR-02 answer retry), bound by Hilt; `ChatRepository`/`PromptsRepository` consume the port instead of `Context+WorkManager` direct calls.
- Rows green: CH-06, PR-02 (send/answer enqueue semantics; the `push/PromptActionHandlerTest` 401-retention path keeps passing).

## 5. T3e — upgrade harness (docs + androidTest, NO claims of passing)

Deliverables (on `feat/unified-kmp` or the data branch, docs-first):
1. `docs/upgrade-harness/README.md`: step-by-step for the USER run (`UNPROVEN - needs device`): prerequisites (`adb` at `/usr/local/share/android-commandlinetools/platform-tools`), (i) check out `main`, install direct-debug on a device, run the SEED androidTest (drives production `SecretStore.save`/Room DAOs/`SettingsRepository` — NO relay), record `adb shell am instrument` output; (ii) `adb install -r` the migrated direct-debug APK (NEVER uninstall — Keystore master key + DB must survive); (iii) run the VERIFY androidTest (session equality, DB rows/TODO/activeTask, settings file re-read), paste output into `docs/upgrade-harness/recorded-output/<date>.txt`; only then flip SC-02b/SC-03b to covered.
2. `app/src/androidTest/java/dev/j3pf/rp/upgrade/UpgradeSeedTest.kt` + `UpgradeVerifyTest.kt` (androidTest build only) + the release-checklist line in README.
3. The two remaining device-only rows STAY `device-only` until that output exists.

## 6. Risks / blockers

- `:data-core` KSP room-compiler per target is the main build risk; mitigated by landing the module with schema export FIRST (move 1 slice a) and running the narrowest tasks (`:data-core:compileKotlinJvm :data-core:compileKotlinIosSimulatorArm64 :data-core:jvmTest`).
- The `MIGRATION_2_3` byte-equality check is a Gate C1 precondition (schema diff script).
- O3/O4 acceptance gates stay external blockers (orchestrator-controlled).

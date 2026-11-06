# ADR 0001 — Target KMP/CMP architecture for the Rp-app unification

Author: O1 (Data core) on `unify/o1-data`. Date: 2026-11-06. Status: Proposed → Gate B review.
Specs served: S1 ("Centralizar la app para cualquier dispositivo"), S2/S3 (one codebase, every device), S7 (best practices of the chosen framework), S10 (KMP/CMP, decided at repo level), S11 (Android code is the behavior oracle), S13 (adaptive by construction), S14 (in-place upgrade), S16 (KMP purity), S18 (no plaintext credentials), S5/S15 (no lost functionality, parity with proofs).

Facts verified against current official docs (not opinion): Room KMP with `BundledSQLiteDriver`/`NativeSQLiteDriver` is the documented Android JSON-path (developer.android.com Kotlin Multiplatform Room/SQLite, Room 2.8.x); `addMigrations` is part of the common `RoomDatabase.Builder` API; JetBrains publishes multiplatform androidx artifacts for Navigation (`org.jetbrains.androidx.navigation:navigation-compose`) and Lifecycle/ViewModel under one group id that resolves per platform (kotlinlang.org "How multiplatform Jetpack libraries are packaged"); CMP for iOS is stable since CMP 1.8 (JetBrains blog, 2025-05). This repo already uses Room 2.8.5, CMP 1.12.2, lifecycle 2.11 — the versions are already KMP-capable; no dependency bumps are required by this ADR except the additions listed in §9.

---

## 1. Module graph (after T3–T7)

```
:protocol                  (exists, pure KMP)     @Serializable protocol types, ChatReducer, Ulid
:relay-client              (exists, pure KMP)     HTTP/WS session core, ChatDeliveryPolicy/ChatSessionRegistry, SessionPolicy, AppSettings
:data-core                 (NEW, pure KMP)        Room KMP Db + DAOs (entities unchanged), repositories, SecretStore port + SettingsStore
                                                  port, SecureCrypto port, platform hosts (picker/biometric/voice) as interfaces
:shared-ui                 (exists, CMP)          screens/components/theme; consumes :data-core + ports via constructor injection
:app                       (thinned Android host) Hilt Composition root ONLY + platform adapters kept that are Android-only
                                                  (DirectPushService, OkHttp transport adapters, WorkManager, notification channels)
iosApp/                    (thin Swift host)      SharedUi.framework (MainViewController from :shared-ui), entitlements, Info.plist
```

Rules: `:data-core` and `:shared-ui` never import `android.*`/`androidx.*` Android-only symbols (S16; gate-grepped). The Android-only pieces that have no cross-platform equivalent (foreground service, WorkManager scheduler, OkHttp transport, Keystore/Tink crypto) stay adapters behind ports and live in `:app` (androidMain) or `iosApp`-side `iosMain` files.

## 2. Ports (contracts owned by O1; workers file requests, never invent parallel contracts)

All ports are **interfaces in commonMain**, constructor-injected. `expect/actual` is reserved (§3) for platform *construction* seams only.

| Port | Contract (final shape) | Android impl | iOS impl (fresh, S12) |
|---|---|---|---|
| `SecretStore` | `current(): Session?`, `save(session)`, `clear()`, `session: StateFlow<Session?>`, `newRelayId(): String` | existing `:app` `data/secure/SecretStore.kt` (Tink AES256_GCM, keyset prefs `rp_keyset`, master key Keystore `rp_master_key`, prefs `rp_secrets`, field-scoped AD) | Keychain (S18): `kSecClassGenericPassword` under `keychain-access-groups` entitlement (S12), CryptoKit AES-GCM, same session model from `:relay-client` commonMain |
| `SecureCrypto` (T3 seam inside SecretStore) | `encrypt(field: String, value: String): String`, `decrypt(field: String, value: String): String?` — Base64 NO_WRAP, AD = field key | extract from `SecretStore` unchanged logic | CryptoKit, same AD discipline (contract tested with fakes per S14 fixtures) |
| `SettingsStore` | `settings: Flow<AppSettings>` + the exact ST-01 setter key set (selectedContext, directPush, requireUnlock, criticalAlways, appLock, flagSecure, onboardingDone, batteryAsked, notifyPrefs, mutedAgents, appTheme, chatFontScale, chatMessageSpacing, meshSelectedMachine/Group) + `wipe()` | DataStore `preferencesDataStore(name = "rp_settings")` on `:app`; existing file preserved | `datastore-preferences-core` KMP, same preferences file format; only the file path is platform-specific (§3 seam). Upgrade-compatible by format identity |
| Platform hosts (negotiated with O3, cc'd orchestrator): `AttachmentsPlatformHost` (`pickImages(maxItems=10)`, `pickDocuments(mime)`, `cameraTarget(): PreparedLocalFile?`), `BiometricAuthorizer` (`suspend authenticate(reason): OK/FAILED/UNAVAILABLE`, 5-min freshness INSIDE the impl), `VoiceInputHost` (`start/stop/listening: StateFlow` + `VoiceListener.onPartial/onFinal/onError(code)` with DOCUMENTED codes 1=banned 2=network 3=no-recognizer 4=client 5=busy 6=no-permissions) | O1 owns in `:data-core` (T3) | current Android screens' plumbing moves behind these interfaces | PHPicker/UIDocumentPicker, LAContext+Keychain entitlement, SFSpeechRecognizer+AVAudioSession |
| `WorkScheduler` | `enqueueAnswer` retry, `enqueueSend` key, CONNECTED constraint, EXPONENTIAL 15s backoff, expedited policy | WorkManager (`ChatRepository.kt:307-317`, `PromptsRepository.kt:189-196`) | BGTaskScheduler; wakeups replaced by APNs (needs Apple provisioning step — flag for orchestrator, L2 risk) |
| No-port zones (decision): haptics = CMP `LocalHapticFeedback` in commonMain; IME insets = CMP `Modifier.imePadding()`/`navigationBarsPadding()` in commonMain; folder browsing = relay-mediated `herdr_layout` RPC; clipboard = CMP shared API; transport socket = already-portable OkHttp adapter in `:app` (O2's slice) |  |  |  |

## 3. expect/actual vs interface policy

- **Interface + constructor injection** for every behavior port (table above). Rationale: tests can pass fakes; no platform type leaks; composition roots (§5) bind implementations.
- **expect/actual only** when the platform must construct something the common code cannot: database path/builder (`platformDatabaseBuilder(): RoomDatabase.Builder<RpDatabase>`), settings file path (`settingsPreferencesPath(): Path` equivalent), and the `SecretCrypto` primitives + any keychain wrapper entry point. expect declarations live in `:data-core` commonMain; actuals in androidMain of `:app`/`iosMain` of the shared modules.
- No `expect` for anything the JVM/JS-agnostic Compose layer already provides (§2 "no-port zones").

## 4. ViewModel / state-holder pattern

- Shared ViewModels follow the official KMP path: `androidx.lifecycle:lifecycle-viewmodel` (multiplatform artifact, lifecycle 2.11 already in the version catalog) + `viewModel()` from `lifecycle-viewmodel-compose` where screens need one; the current Android ViewModels move into `:shared-ui`/`:data-core` with **zero behavior change** (parity rows already pin them; S15).
- `LocalViewModelStoreOwner` is provided by CMP (1.12) on iOS; on Android it is the same `ComponentActivity` store so Hilt-assisted creation stays possible via the existing factory seams.
- State holders that are not ViewModels (repositories, ChatSessionRegistry) keep their coroutine-scope injection (`@AppScope` equivalent becomes a plain constructor param named `appScope`).

## 5. DI strategy: DI-agnostic common core, two composition roots

- Common code takes plainer constructors (no `@Inject` inside `:data-core`/`:shared-ui` for NEW shared code); Hilt annotations stay on the CURRENT `:app` classes until T7 deletes their duplicates.
- Android composition root: Hilt remains **only** in `:app` — `@Module @InstallIn` binds the ports (SecretStore→Tink impl, WorkScheduler→WorkManager, hosts→Android impls). Existing `AppModule.kt` (owned by O1) is the model.
- iOS composition root: hand-written (no Koin/DI framework introduced): `iosMain` provides a single `IosAppGraph` object constructing the ports and handing dependencies to shared-ui; documented in `iosApp/README.md`, owned by O4 implementation but the *shape* is this ADR (O4 files port requests, O1 owns contracts per orchestrator layout official README).
- Rationale: Hilt on KMP is Android-first; hand-writing two small composition roots is less machinery than adopting Koin into a repo that has Hilt on Android (no new dependency risk for S5).

## 6. Room KMP / BundledSQLiteDriver

- Entities (`Entities.kt`), DAOs (`Daos.kt`) and `RpDatabase` version 3 move to `:data-core` commonMain unchanged (schema IDs, table/column names, index sets, `exportSchema = true` with schemas exported to the module's `schemas/` dir so Gate D can diff them byte-wise against `app/schemas`).
- Driver rule (single, risk-based): **the existing Android installation keeps the framework SQLite driver unchanged** (framework `Room.databaseBuilder(context, …)` / `androidx.sqlite:sqlite-framework`), so the stable app storage path is not touched. iOS and JVM tests use **`BundledSQLiteDriver`**. Only if DB-04..DB-12 are re-run green on BOTH drivers (framework vs Bundled) may Android flip to Bundled as its own gated commit, never bundled with the migration.
- `addMigrations(MIGRATION_1_2, MIGRATION_2_3)` — the common `Migration` API on `RoomDatabase.Builder` (verified) so the SQL of both migrations is untouched; devices updating from old restores continue to work (S14).

## 7. Settings store, Compose Resources, navigation

- Settings: **DataStore Preferences, KMP artifact `datastore-preferences-core`** (1.2.x catalog entry already KMP-ready; Jetpack ships datastore for iOS). Same preferences file name `rp_settings` and same key set (ST-01) so an upgraded Android install reads the SAME file it wrote before (S14). The platform only supplies the file path (expect/actual per §3). Malformed JSON fallback (ST-04) is a ported behavior with a ported test.
- Compose Resources: strings/fonts/app icons live under `:shared-ui/src/commonMain/composeResources` (official CMP resources mechanism, invoked only from `:shared-ui`); NO resources move out of `:app` for Android-only manifests/notifications (those stay Android-specific).
- Navigation: **`org.jetbrains.androidx.navigation:navigation-compose`** (JetBrains-published multiplatform androidx navigation artifact, official path per kotlinlang.org docs). Android keeps the same `NavHostController` semantics; iOS gets the same route graph. S5 risk note: deep-link handling for Android-specific intents (push taps) is a port-boundary decision owned with O2/O4 during T5/T6 — no behavior change on Android.

## 8. T3 design invariants (data core migration)

- Invariants from T1 that must survive the migration: DB-01..DB-12, CH-01..CH-10 (chat outbox semantics incl. the 5000-row trim ceiling), PR-01..PR-05, SE-01..SE-06 (socket/WorkManager bits move to O2's shared policy), MA-01/HO-01, ME-01/ME-02, ST-01..ST-06, BI-01, MP-01..MP-03, SC-01..SC-06.
- Existing `:app` tests are ported, never weakened (S15): forwarding per orchestrator Gate A as pure re-homing (same assertions), with updates only where the seam changed file.
- Gaps closed by the S14 harness (below), every one with a named artifact in T3's deliverable.

## 9. S14 in-place upgrade proof (highest-risk slice of the migration)

**Principle (per orchestrator's requirement): fixtures produced by the CURRENT Android code, opened unchanged by the migrated code.**

| Artifact | Fixture source | Harness | Device needed? |
|---|---|---|---|
| Room DB v2→v3 | `MigrationTestHelper` on the exported `app/schemas/2.json`/`3.json` DDL — creates the v2 DB **with the production `MIGRATION_2_3` code path unchanged**, inserts representative agent/theme/TODO/activeTask data, migrates to 3 | JVM Robolectric (existing `DatabaseMigrationTest` extended with a **fixture DB file produced by the current code at v3** re-opened by the migrated builder — proves actual byte-format compatibility under the chosen driver) | No |
| Secure session ciphertext | The **current production `SecretStore`... cannot produce portable ciphertext off-device** (Android Keystore master key is hardware-bound) | **Robolectric fallback (accepted, scoped):** the `SecureCrypto` seam test reproduces the encrypted-prefs FORMAT and AD discipline with a Tink keyset JSON fixture created by `TinkDecryptionTest`-style keyset generation; plus a **real-key path** covered by an instrumented test \(see row below\) | Partly (see next row) |
| Real Keystore upgrade | A leftover session written by the CURRENT build on the SAME device | **Android instrumented test (androidTest, `connectedDirectDebugAndroidTest`)**: install the pre-migration build from `main`, let it pair & write, install the migrated build in place \(no uninstall\), assert session tolerance + DB/settings/TODO/activeTask survive | **YES — one Android emulator/device run** (orchestrator runs Gate C1's connected test; the harness script and a stripped test APK are O1 deliverables in T3) |
| Settings file | `rp_settings.preferences_pb` file written by the current code: committed as a byte fixture | migrated `SettingsStore` must re-read exact values across Android + iOS via the same DataStore file format (commonTest JVM + an `iosSimulatorArm64Test` target run) | No \(mac host KMP test\) |
| DB created by current build verbatim | a v3 DB persisted by the CURRENT `:app` (the orchestrator's Gate-C1 connected run captures it) | the same file copied through as an iOS app bundle resource and opened by `BundledSQLiteDriver` \(commonTest\) | No \(mac host\) |

USER DECISION (recorded, 2026-11-06): this machine has NO Android emulator/device and the user chose not to install one. Therefore the real-key in-place upgrade run (S14: real Keystore readability + true `adb install -r` over v0.6.1) is a **DOCUMENTED BLOCKER of Gate C1, to be run later by the user**. Report the two blank rows as `UNPROVEN - needs device`, never hidden behind the Robolectric seam.

Drive of the two-build harness WITHOUT a relay: the OLD build is a checkout of `main` augmented ONLY with a seed `androidTest` that drives the PRODUCTION APIs — no pairing flow, no live relay: `SecretStore.save(Session(...))` on the device (so the ciphertext and keyset are produced by the real device Keystore), representative agent/theme/TODO/activeTask rows through the production Room DAOs, and representative settings through the production `SettingsRepository` (including theme + onboardingDone). Persist state via `adb shell am instrument`; then binary `adb install -r` of the migrated APK; then the verifying `androidTest` asserts: `SecretStore.current()` returns the identical session, Room rows/TODO/activeTask intact, same settings values read back from the same `rp_settings` file. The full harness (seed androidTest on `main`, upgrade script, verifying androidTest, step-by-step README with exact commands and expected output) is built in T3e and the README states plainly it has NOT been executed.

### 9.1 Gate C1 device-run prerequisites (user executes later; adb only, no emulator)

Machine status recorded by the orchestrator: adb binary ONLY at `/usr/local/share/android-commandlinetools/platform-tools/adb` (`platform-tools` installed); NO Android emulator and NO physical device connected; the user chose to run the real-key in-place upgrade run later on their own device. Prerequisites for that run:

1. A device with v0.6.1 (or the direct-debug build of `main`) paired against a live-enough relay setup — or use the seed androidTest from T3e which needs NO relay (it drives the production `SecretStore`/Room DAOs/`SettingsRepository` directly).
2. The seed androidTest from `/main`-checkout (T3e) executed via `export PATH=$PATH:/usr/local/share/android-commandlinetools/platform-tools && adb shell am instrument -w dev.j3pf.rp.test/dev.j3pf.rp.UpgradeSeedRunner` — ODD to be spelled out verbatim in `docs/upgrade-harness/README.md` in T3e, exact expected output included.
3. `adb install -r` of the migrated direct-debug APK (never uninstall — that would clear Keystore-bound master key and DB; `-r` keeps both).
4. The verifying androidTest listing per-row expected output; the to-report rows `SC-02b`/`SC-03b` `UNPROVEN - needs device` flip to covered ONLY by that run's recorded output, committed under `docs/upgrade-harness/recorded-output/`.

## 10. Consequences / risks

- Two rows stay `UNPROVEN - needs device` until the user runs the T3e harness: real-Keystore readability (SC-02/SC-03 real-key half) and the true `adb install -r` in-place install. Every report lists them explicitly.
- Single-source migration risk is bounded by T1's matrix: every row has either a passing proof today or a named gap owner; ported code may not re-record goldens (S15) and `Roborazzi` goldens stay untouched.
- The two genuinely new platform surfaces \(Keychain crypto, BGTaskScheduler/APNs\) carry the open provisioning risk already flagged in the orchestrator's Log L2 (human Apple-portal step); no code can compensate for missing `keychain-access-groups` or APNs entitlements — both surface at Gate C4.
- `fallbackToDestructiveMigration(dropAllTables = true)` (AppModule.kt:48, kept exactly as today): any version the shipped migrations cannot reach — downgrades included — WIPES all user data instead of crashing. This is existing, deliberate behavior and stays; T3b pins its trigger with a test (DI-02). Listed as an S14 risk: an update path that misses a migration erases pairing data silently.

## 11. Ports already negotiated / decided for other slices (published so O2/O3/O4 build on the same contract set)

- O3: `AttachmentsPlatformHost`, `BiometricAuthorizer`, `VoiceInputHost` as interfaces (§2); P-HAPTICS/P-IME dropped (CMP built-ins); O3 accepted (cc orchestrator 2026-11-06 05:53-05:54Z).
- O2 (pending request, decisions pre-committed here): transport socket/HTTP stay in the existing `:relay-client` OkHttp adapters; `ConnectionController` port added only if iOS needs a distinct lifecycle handle; APNs registration belongs to `PushTokenProvider` port behind `SessionRepository`.
- O4 (pending request, decisions pre-committed): navigation via `org.jetbrains.androidx.navigation` (§7); adaptive layout via CMP-only `WindowWidthSize`-equivalent, no forks (S13); entitlement reuse strictly limited to the S12 file list.

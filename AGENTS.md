# AGENTS.md — Rp-app

Android and Kotlin Multiplatform (KMP) client for the RemotePi system. Provides real-time chat with streaming, agent inbox, pending questions/authorizations, Git diff change review, and background notifications.

## Tech Stack & Architecture

- **Language & Runtime**: Kotlin 2.1.20, Java 17.
- **Android Target**: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`.
- **Modular Architecture**:
  - `:protocol`: Pure Kotlin Multiplatform (targets JVM 17, `iosArm64`, `iosSimulatorArm64`). Contains `@Serializable` protocol types, `ChatReducer`, ULID generators, and domain models.
  - `:relay-client`: Pure Kotlin Multiplatform (JVM 17, `iosArm64`, `iosSimulatorArm64`). Handles shared HTTP requests, WebSocket correlation bus, session state management, and chat synchronization.
  - `:shared-ui`: Compose Multiplatform (Android library via AGP 9 `com.android.kotlin.multiplatform.library`, JVM 17, `iosArm64`, `iosSimulatorArm64`). Hosts shared theme, screens, components, and exports `MainViewController` for iOS.
  - `:app`: Android application layer. Implements Jetpack Compose host, Hilt dependency injection, Room database persistence, WorkManager background tasks, OkHttp network adapters, and Android notification channels.

## Commands

```bash
# Verify shared protocol tests (JVM)
./gradlew :protocol:test

# Verify shared relay-client tests & iOS metadata
./gradlew :relay-client:compileCommonMainKotlinMetadata :relay-client:jvmTest

# Verify Compose Multiplatform shared-ui (Android, JVM tests & iOS metadata)
./gradlew :shared-ui:compileCommonMainKotlinMetadata :shared-ui:compileAndroidMain :shared-ui:compileKotlinIosSimulatorArm64 :shared-ui:jvmTest :shared-ui:ktlintCheck

# Run all unit and Compose tests for Android direct flavor
./gradlew :app:testDirectDebugUnitTest

# Assemble direct-debug APK
./gradlew :app:assembleDirectDebug

# Run static analysis / lint
./gradlew :app:lintDirectDebug

# Complete full verification suite
./gradlew testDirectDebugUnitTest :protocol:test :relay-client:jvmTest :app:lintDirectDebug
```

## Boundaries & Non-Obvious Conventions

1. **KMP Purity**:
   - Never import Android packages (`android.*`, `androidx.*`, `javax.crypto.*`) inside `:protocol` or `:relay-client`.
   - Android-specific implementations must stay in `:app` as adapters (e.g., `OkHttpRelayTransport`, `OkHttpSocketDriver`, `AndroidIdGenerator`).
2. **Push & Background Lifecycle**:
   - The app supports two build flavors: `direct` (uses a foreground persistent service `DirectPushService`) and `fcm` (optional Google Firebase Cloud Messaging).
   - Never remove or break the `direct` flavor fallback; it must function completely independently of Google Play Services.
3. **Security & Credentials**:
   - Session keys and device tokens are stored securely in Android `KeyStore` / `EncryptedSharedPreferences`. Never write credentials in plaintext or Room plain tables.
   - Never commit `app/google-services.json` or `keystore.properties`.
4. **Git Diff Viewer**:
   - Diff viewing in `ChatScreen` / `DiffSheet` uses structured file chunks and provides user-driven discard confirmation dialogs before invoking `host.git_discard`.

## Verification Before Turn End

Always execute `./gradlew :protocol:test :relay-client:jvmTest :app:testDirectDebugUnitTest` and confirm zero failures before marking any task as done.

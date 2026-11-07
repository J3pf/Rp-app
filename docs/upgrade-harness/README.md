# Upgrade harness (RP-app, T3e) — IN-PLACE UPGRADE PROOF

> ## ⛔ UNPROVEN - needs device
> This harness is COMPILE-VERIFIED ONLY. The recorded output in
> `docs/upgrade-harness/recorded-output/` does NOT exist yet. Until a human runs
> these steps on a real Android device, the matrix rows SC-02b and SC-03b stay
> `device-only` and MUST NOT be flipped to covered. (There is NO Android emulator
> on this machine — use a physical device.)

## What this proves

Running the two androidTests below around one `adb install -r` proves the S14
in-place upgrade contract: pairing credentials (Tink AES256-GCM backed by
Android Keystore), the Room database at schema v3, and the DataStore settings
survive a package upgrade **in place**, without pairing again — the exact claim
the KMP migration makes to every existing user.

## Prerequisites

- `adb` on PATH — on this machine: `/usr/local/share/android-commandlinetools/platform-tools`.
- A device connected over USB with USB debugging enabled; certificate/keystore intact.
- **NEVER** `adb uninstall dev.j3pf.rp.*` at any point in this procedure. The
  Tink master key lives in the Android Keystore under the app's identity and
  an uninstall destroys it permanently (that is the data loss we are guarding).

## Steps

### 1. Seed (PRE-move build — commit BEFORE the KMP merge of this harness)

```bash
cd ~/Projects/Rp-app   # checkout pinned BEFORE feat/unified-kmp Move 1 merge
./gradlew :app:assembleDirectDebug                                    # build
adb install -r app/build/outputs/apk/direct/debug/app-direct-debug.apk # install WITHOUT uninstall
adb -d shell am instrument -w -e class dev.j3pf.rp.upgrade.UpgradeSeedTest \
  dev.j3pf.rp/androidx.test.defaultInstrumentationPackage 2>&1 | tee /tmp/rp-seed.log
```

If the `am instrument` line complains about the runner package, replace
`androidx.test.defaultInstrumentationPackage` with the app package +
`.test.AndroidJUnitInstrumentation` as printed by
`adb shell pm list instrumentation dev.j3pf.rp.*`.

Alternatively run `./gradlew :app:connectedDirectDebugAndroidTest` (needs the
device attached) - but the plain `am instrument` gives the collected class log
without re-install ping-pong; choose whichever you prefer. The recorded
output must contain the `UpgradeSeedTest.seedsProductionDataForTheInPlaceUpgrade`
OK/OK (1 test) marker.

### 2. Upgrade (MERGED build carrying the KMP data core; `adb install -r`, NEVER uninstall)

```bash
cd ~/Projects/Rp-app   # checkout at feat/unified-kmp (or later) with the Move 1 merge
./gradlew :app:assembleDirectDebug
adb install -r app/build/outputs/apk/direct/debug/app-direct-debug.apk
adb -d shell am instrument -w -e class dev.j3pf.rp.upgrade.UpgradeVerifyTest \
  dev.j3pf.rp/androidx.test.defaultInstrumentationPackage 2>&1 | tee /tmp/rp-verify.log
```

### 3. Record

Create `docs/upgrade-harness/recorded-output/<yyyy-mm-dd>.md` with:

- device model + API level (`adb shell getprop ro.product.model`),
- the FULL `rp-seed.log` and `rp-verify.log`,
- `adb shell dumpsys package ... | grep versionName` before/after,
- a note of the source commits used (pre-move and post-move SHAs).

Then (and only then) the two `device-only` matrix rows in
`docs/parity/o1-data.md` may be flipped to `covered`, citing this file.

## Failure policy

- If the VERIFY step fails: the failure output above is the bug; do NOT delete
  app data, do NOT re-pair to "make it pass", and file the finding as an ODD
  Log row (L-entry) so the migration does not ship unproven.
- The harness itself is read-only on the VERIFY side and writes only the
  seeded production rows on the SEED side — it must NEVER be wired to a live
  relay.

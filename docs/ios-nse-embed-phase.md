# iOS NSE embed phase — one-page plan (docs only; nothing executes without a ruling)

## What a device-installable NSE needs beyond 85366b8/bc2f71a

The compiled NotificationService.appex exists only as its own product. For the OS to use it, the
app target must (1) depend on the NSE target and (2) copy the .appex into `PlugIns/`:

1. **PBXTargetDependency** (e.g. `D300...N00N14`) with `target = NotificationService`, hosted in
   the iosApp target's `dependencies` list (containerProxy = project).
2. **PBXContainerItemProxy** (`D300...N00N15`) linking proxyType=1 from iosApp to NotificationService.
3. **PBXCopyFilesBuildPhase** "Embed App Extensions" (dstSubfolderSpec = 13 = PlugIns), with the
   `NotificationService.appex` PBXBuildFile; added to the iosApp target's `buildPhases` AFTER
   Sources/Frameworks/Resources and BEFORE the Kotlin framework shell phase ordering stays valid
   (phase order: Sources, Frameworks, Resources, Embed App Extensions, Kotlin framework script).
4. `DEPENDENCIES` entry inside the iosApp PBXNativeTarget object (2 added lines).

## Settings that must NOT change

- iosApp target-level XCBuildConfiguration objects (D300...08/09): ZERO keys added/removed.
- Existing build phase objects keep their names/IDs; only the target's `buildPhases` and
  `dependencies` lists gain entries (this is the one intentional touch to the app target object).
- No entitlements, no CODE_SIGN keys, no provisioning in this unit (S12 boundary stands).

## Verification plan (prove embed, prove no disturbance)

1. **Diff audit**: `git diff --stat iosApp/iosApp.xcodeproj/project.pbxproj` shows N added, and
   `git diff` shows the iosApp PBXNativeTarget object changed ONLY by 2 list entries; no deletion
   of any existing line (statistical assertion recorded in the commit message).
2. **Round-trip parse**: `xcodebuild -project ... -list` — both targets again, no parse defect.
3. **Build**: both `xcodebuild -target iosApp ... CODE_SIGNING_ALLOWED=NO` AND
   `xcodebuild -target NotificationService ...` BUILD SUCCEEDED (same flags as NSE-SKELETON).
4. **Bundle content** (the strongest observer): after building the unsigned Debug app scheme,
   `ls "$(xcodebuild -showBuildSettings build PRODUCTS_DIR)/iosApp.app/PlugIns"` shows
   `NotificationService.appex` — proving the embed actually fired without touching app code.
5. **No pixel effect**: no UI change → plain app suite + golden gate unnecessary; a single
   focused `:app:testDirectDebugUnitTest` sanity run only if the orchestrator wants it.

## Boundaries

- Needs a living ruling because it edits the app target (the one line the NSE-SKELETON guard
  protected); unit gets its own commit (rollback = single revert) and `Risk: item 5`.
- Does NOT unlock device runs: S12 still owns signing/entitlements/provisioning (TEAM_ID
  19410625785 parked for T6.5, provisioning path user-parked).

## TestFlight dependency of the embed

The embed is ALSO gated on CI: `.github/workflows/testflight.yml` archives with
`CODE_SIGN_STYLE=Manual`, `CODE_SIGN_IDENTITY="Apple Distribution"` and a single
`PROVISIONING_PROFILE_SPECIFIER` from `PROVISIONING_PROFILE_BASE64`; once the appex is embedded,
export signs BOTH bundles, and the app's existing profile does NOT cover
`dev.j3pf.rp.iosApp.NotificationService`. So before the embed unit ships:

- the user's parked provisioning deliverable must include a SECOND App ID + profile for the
  extension (`$(BUNDLE_ID).NotificationService`), OR one shared wildcard profile that covers both;
- the workflow gains the wiring: either a second secret (`PROVISIONING_PROFILE_BASE64_NSE` +
  `PROVISIONING_PROFILE_SPECIFIER_NSE` passed to exportOptions for the extension bundle) or an
  updated exportOptions where one profile covers app + extension.

Without that wiring the next `archive` fails at export (missing profile for the extension).
Execution of the embed unit stays HELD until the user delivers both pieces (Ruling 2026-11-07:
accepted plan-of-record, `Risk: item 5` confirmed, no new work until the human input lands).

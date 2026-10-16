# UI Refresh — Full UX/UI Overhaul & Modern Animated Experience
Feature: UI Refresh in Rp-app
Status: In Progress

## Specs
S1 Theme values and behavior are preserved: every color value in ui/theme/Theme.kt palettes (Cute/Sexy/Omarchy/Default, light+dark), RpThemeChoice ids+labels, resolveThemeChoice + themeForMachine logic, dynamic color for SYSTEM/AUTO, and RpColors stay unchanged. New design tokens (gradients, glows, shapes, motion, spacing) are ADDED in ui/theme/ and derive from MaterialTheme.colorScheme; no hard-coded colors in screens. Switching theme in Settings re-skins every screen. Add a unit test pinning resolveThemeChoice outcomes and each palette's primary/background/surface values.
S2 Chat lands on the newest content with zero manual scrolling: on open, when a message arrives, and WHILE STREAMING, the BOTTOM of the newest message is visible (current bug: ChatScreen.kt MessageList uses scrollToItem(lastIndex), which aligns the TOP of a tall last item, so long/streamed messages are cut off). Use a bottom-anchored list (reverseLayout or equivalent). If the user scrolled up to read history, do NOT yank them; show an animated "jump to latest" button with a count of new items. Robolectric/Compose tests prove: open -> last item bottom visible; tall streaming message -> its bottom visible; scrolled-up user is not moved and the button appears.
S3 Agents home shows each agent's most recent activity at a glance, without scrolling inside the card: status, active task, todo progress, last message preview, and relative time of last activity are visible on the card. Remove the horizontal-scroll chip rail (wrap/condense instead). Within each machine group, order agents: open prompts first, then working, then most recent lastSeenAt. Card height stays compact (preview max 2 lines).
S4 Motion everywhere, purposeful: animated navigation transitions between screens; animateItem() for list insert/move/remove; a living pulse/shimmer for working agents; animated counters and progress; animated status changes (color/size via animate*AsState / updateTransition); press-scale feedback on cards/buttons; streaming text appears smoothly. Spring-based specs centralized in ui/theme (one Motion token object). Shared-element transition agent card -> chat header if feasible with SharedTransitionLayout.
S5 All animations honor the system "remove animations" setting (animator duration scale 0 => no infinite animations, instant transitions).
S6 Elegant, modern, consistent visual design across ALL screens: Agents, Chat (bubbles, code blocks, tool calls, thinking), Mesh, Pending, New agent, Settings, Onboarding, Prompt sheet, Quota Dynamic Island, connection banner, empty/skeleton states. One design system: spacing scale, typography hierarchy, shape scale, layered surfaces with subtle theme-derived gradients, edge-to-edge with correct insets, haptic feedback on primary actions. Beautiful empty and loading states.
S7 Zero functional regression: every existing route, callback, deep link from notifications, biometric gate, prompt answering, resume/forget, spawn, pairing, mesh, settings option keeps working. All existing tests pass. UI copy stays Spanish (project convention); identifiers/comments English.
S8 Accessibility: contentDescription on actionable icons, touch targets >= 48dp, contrast comes from theme roles, semantics preserved for TalkBack; existing a11y tests keep passing.
S9 Dependencies: ZERO alpha/beta/rc. Any dependency you ADD or CHANGE must be the latest stable on Google Maven / Maven Central at the time of change. Bump Compose BOM 2025.09.00 -> 2026.09.00 (latest stable, verified by me) plus navigation-compose 2.10.2, coil 3.6.3, and other UI libs you touch to their latest stable. Do NOT bump AGP/Kotlin unless the build proves it is required — if so, STOP and report the exact error first. List every version change in the feature doc.
S10 Visual evidence: add Roborazzi 1.76.0 (latest stable) screenshot tests for Agents (populated + empty + skeleton), Chat (with a long streaming message), Mesh, Pending, Settings, New agent, in Gentle-Cute, Gentle-Sexy, Omarchy, each light+dark. Commit the reference PNGs under app/src/test/screenshots (or Roborazzi default). I will review the PNGs personally.
S11 Performance: stable keys and contentType on lazy lists, no unbounded recomposition, infinite animations scoped to visible items only. assembleDebug and assembleRelease both succeed.
S12 Delivery: version bump to 0.3.0 (versionCode +1), README features + docs/QA.md manual cases updated. Push, tag, and GitHub release ONLY after I send "[UI REFRESH — OK TO PUBLISH]". Releasing before my OK is a defect.

## Version Diff Plan
- composeBom: 2025.09.00 -> 2026.09.00
- navigation: 2.9.5 -> 2.10.2 (androidx.navigation:navigation-compose)
- coil: 3.3.0 -> 3.6.3 (io.coil-kt.coil3:coil-compose)
- roborazzi: none -> 1.76.0 (io.github.takahirom.roborazzi plugin + roborazzi, roborazzi-compose, roborazzi-junit-rule)
- versionName: 0.2.0 -> 0.3.0
- versionCode: gitCommitCount() (monotonic commit counter)
- AGP: 8.13.0 -> 9.4.1 (migrated per authorized toolchain update)
- Kotlin: 2.2.20 -> 2.4.21 (migrated per authorized toolchain update)
- KSP: 2.2.20-2.0.4 -> 2.3.12
- Hilt: 2.57.1 -> 2.60.1
- Google Services: 4.4.3 -> 4.5.0
- Gradle: 8.13 -> 9.8.1
- compileSdk: 36 -> 37
- targetSdk: 36 (preserved)
- minSdk: 29 (preserved)
- composeBom: 2025.09.00 -> 2026.09.00
- navigation: 2.9.5 -> 2.10.2
- coil: 3.3.0 -> 3.6.3
- activityCompose: 1.11.0 -> 1.13.0
- lifecycle: 2.9.4 -> 2.11.0
- hiltAndroidx: 1.3.0 -> 1.4.0
- coreKtx: 1.17.0 -> 1.19.1
- serialization: 1.9.0 -> 1.11.0
- coroutines: 1.10.2 -> 1.11.0
- roborazzi: none -> 1.76.0

## Tasks
- T0 (S9): Toolchain migration: install Android platform 37, bump AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12, Hilt 2.60.1, Gradle 9.8.1, Compose BOM 2026.09.00, navigation 2.10.2, coil 3.6.3, activity-compose 1.13.0, lifecycle 2.11.0, hilt-nav/work 1.4.0, core-ktx 1.19.1, serialization 1.11.0, coroutines 1.11.0, roborazzi 1.76.0; compileSdk 37; verify all tests pass + assembleDebug + assembleRelease [DONE - 458dd28]
- T2 (S1): Unit tests pinning resolveThemeChoice and palette colors (Cute/Sexy/Omarchy/Default primary/background/surface) + central design tokens (Glows, Gradients, Spacing, Shapes, Motion) in ui/theme/ [DONE - 0d104af]
- T3 (S4, S5): Motion framework, reduced-motion helper (honoring animator_duration_scale 0), screen-to-screen navigation transitions in RpRoot.kt [DONE - ec6147a]
- T4 (S2): ChatScreen bottom-anchored message list (bottom of newest message visible on open, receive, streaming) + animated jump-to-latest button + Robolectric tests [DONE - afeb905]
- T5 (S3): AgentsScreen activity-at-a-glance redesign: wrap/condense chip rail, show status, active task, todo progress, last message preview (max 2 lines), relative time of last activity, agent sorting (prompts -> working -> lastSeenAt) [DONE - e572ebd]
- T6 (S4, S6): Living agent feedback & animations: status pulse/shimmer for working agents, animated status transitions, counters/progress animations, press-scale tactile feedback [DONE - 71360ac]
- T7 (S6, S7, S8): Screen polish across ALL routes (Agents, Chat, Mesh, Pending, NewAgent, Settings, Onboarding, PromptSheet, QuotaDynamicIsland, ConnectionBanner, empty/skeleton states) with theme-derived gradients, layered elevation, consistent typography, accessibility [DONE - 9054808]
- T8 (S10): Roborazzi screenshot tests for Agents (populated/empty/skeleton), Chat (streaming), Mesh, Pending, Settings, NewAgent in Gentle-Cute, Gentle-Sexy, Omarchy (light + dark) + commit reference PNGs [DONE - 88dc8bd]
- T9 (S7, S11, S12): Verification pass via gentle-ai-verify, assembleDebug & assembleRelease, QA manual cases in docs/QA.md, README feature updates, version 0.3.0 bump [DONE]

## Log
L1: "Necesito que RESPETANDO LOS VALORES Y FUNCIONALIDAD DEL THEME, hagas un refactor para hacer de esta app una app mucho mas llamativa, elegante, full UX/UI, que sea agradable de usar, que no le toque al usuario estar deslizando para ver lo mas reciente de cada agente, literal que sea una app llamativa, moderna, con animaciones, placentera de usar"
L2: [UI REFRESH — TOOLCHAIN GO-AHEAD] User authorized full toolchain migration (AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12, Hilt 2.60.1, Gradle 9.8.1, compileSdk 37, platform 37, all deps latest stable). T0 created as isolated commit.

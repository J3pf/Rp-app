# Agent Mesh — Real-Time Animated Agent Topology and Comms Diagram
Feature: Agent Mesh in Rp-app
Status: In Progress

## Specs
S1: (A1) protocol/: @Serializable types for all of the above + decode tests against relay fixtures (sync-fixtures.sh). Unknown fields are ignored (forward compatible).
App→Relay: `mesh.subscribe { machineId: string|null }` (null = all) → `mesh.snapshot`; `mesh.unsubscribe {}`.
Relay→App `mesh.snapshot` / `mesh.update` (full snapshot, ≤2/s):
{ "machineId":string|null, "generatedAt":epochMs,
  "groups":[ { "groupId","label","orchestratorAgentId":string|null,"machineIds":[..],"memberAgentIds":[..],"confidence":"comms"|"cwd" } ],
  "nodes":[ { "agentId","machineId","machineName","machineColor","name","cwd","gitBranch","model","status","activity":string|null,"online":bool,
              "role":"orchestrator"|"subordinate"|"solo","parentAgentId":string|null,"groupId":string|null,
              "todos":{ "total","done","inProgress","blocked","pending","current":string|null } | null,
              "subagents":[ { taskId, agent, label, status, model?, thinking?, startedAt?, endedAt?, durationMs?, turns?, toolCalls?, tokens?, cost?, error?, result?, tools?:[{toolCallId,name,status,durationMs?}] } ],
              "lastCommAt":epochMs|null,"openPromptCount":int } ],
  "edges":[ { "edgeId","a","b","count","aToB","bToA","lastAt","lastFrom","lastAction","lastPreview":string|null,"pendingAsk":bool,"active":bool } ] }
Relay→App `mesh.comm { comm: { commKey, fromAgentId, toAgentId, fromName, toName, action:"send"|"ask"|"reply"|"handover"|"message", text:string|null, truncated, at, groupId } }` (live, for animations).
HTTP: GET /v1/mesh?machineId= ; GET /v1/mesh/conversation?a=&b=&before=&limit=50 → { items:[CommView], nextBefore } ; GET /v1/mesh/agents/:agentId/comms?limit=50 → { items }.

S2: (A2) Navigation: new entry in the existing main menu (ui/nav/Routes.kt + RpRoot.kt) → MeshScreen, with a clear icon. It must not break any existing route.

S3: (A3) Selectors: machine chips (machine color/icon from the relay, plus "All") and group chips (label = orchestrator, plus a "Solo" bucket); the last selection is persisted; the group with recent activity is preselected.

S4: (A4) Diagram (Compose Canvas, NOT one composable per particle): deterministic radial-hierarchical layout (orchestrator at the center, larger, with a role badge; subordinates on a ring; multi-level supported; subagents as small satellites ORBITING their parent), with stable positions and spring animation when nodes enter or leave. Curved edges; an ACTIVE edge glows; a dashed, pulsing pendingAsk edge; on each mesh.comm, a particle travels from sender to receiver and a speech bubble with the text (ellipsized) stays ~6s next to the edge. Node: status color with a pulse while working, waiting_input as amber attention, error, idle, offline desaturated; a TODO progress ring with a "done/total" label; the current activity below the name. Pinch zoom + pan + double tap to reset. 60fps with 30 nodes (draw on Canvas, throttle state).

S5: (A5) Tap node → ModalBottomSheet with real value: name, role, machine, cwd, branch, model/thinking, status + activity, TODO list (current one highlighted, blocked marked), subagents (status, agent, label, duration, tokens, active tools), its recent conversations (in/out), and actions: "Open chat" (existing chat route) and "View conversation with X". Tap edge → conversation sheet between the two agents: bubbles by direction, action badges (ask/reply/handover), timestamps, paging with nextBefore.

S6: (A6) Collapsible "Live" strip at the bottom: latest group messages "A → B: text".

S7: (A7) Theme: ONLY MaterialTheme.colorScheme/typography/shapes tokens (light/dark/dynamic, ui/theme/Theme.kt); no hardcoded colors except machineColor from the relay; contrast checked in both themes.

S8: (A8) States: loading skeleton, empty (no agents / no communication yet, with an explanation), relay disconnected (stale-data banner with reconnection), redacted text → "Content hidden".

S9: (A9) Architecture without antipatterns: MeshRepository (WS subscription tied to visibility: subscribe on enter / unsubscribe on leave or on background, resubscribe on reconnect), MeshViewModel with an immutable StateFlow<UiState>, a PURE testable Kotlin layout engine (no Android deps), no business logic in composables, Hilt, no leaks, no polling.

S10: (A10) Accessibility: semantics per node ("Orchestrator X, working, 3 of 7 tasks"), a toggle to an accessible LIST view of the same mesh, touch targets ≥48dp, respect reduced motion (ANIMATOR_DURATION_SCALE=0 → no particles).

S11: (A11) Tests: protocol fixture tests, layout engine unit tests (stability, multi-level, satellites, no overlaps), ViewModel tests (subscribe/unsubscribe, merge of updates, comm → animation queue), Compose tests (tap node opens the sheet, empty state, list view). `./gradlew :protocol:test :app:testDirectDebugUnitTest :app:assembleDirectDebug` green. Real screenshots or an emulator recording are a plus (describe how you verified).

S12: (A12) Docs: README + docs/QA.md with the feature's manual cases (existing language, Spanish).

S13: (A13) Delivery: when green → merge to main, bump versionName (minor, 0.2.0), build the APK (scripts/build-apk.sh), tag, PUSH main+tags, PUBLISH a GitHub release with the attached APK and notes; update the README download link.

## Tasks
- T1 (S1, S9): Protocol models, serialization, WS message types, HTTP models & RelayApi additions, fixture decode tests [DONE - 8291c9d]
- T2 (S9): Pure testable Kotlin layout engine (radial-hierarchical, satellites, stable positions, collision avoidance, multi-level) + unit tests [DONE - ea39440]
- T3 (S3, S8, S9): MeshRepository (WS subscribe/unsubscribe lifecycle, snapshot/update/comm streams, HTTP queries, offline/reconnect handling), Settings/Preferences persistence for selected machine/group, MeshViewModel with immutable StateFlow<MeshUiState> + ViewModel unit tests [DONE - 67046f4]
- T4 (S2, S4, S6, S7, S8, S10): MeshScreen UI, Compose Canvas diagram with pinch-to-zoom/pan/double-tap reset, animated nodes (status pulse, TODO ring, activity), glowing active/pending curved edges, comm particles & speech bubbles, reduced motion support, machine/group selector chips, collapsible Live strip, loading/empty/disconnected states, accessible List view toggle & semantics
- T5 (S5, S7, S8): Node detail ModalBottomSheet (agent info, TODOs, subagents, conversations, navigation actions) & Edge conversation ModalBottomSheet (paging nextBefore, direction bubbles, badges) + Compose unit tests
- T6 (S2): Navigation integration in Routes.kt and RpRoot.kt (drawer / bottom bar navigation item with clear icon) without breaking existing routes
- T7 (S11, S12): Verification pass with gentle-ai-verify, QA manual scenarios doc in docs/QA.md (Spanish), README updates, full test suite & assemble validation
- T8 (S13): Merge to main, bump versionName to 0.2.0, build APK via scripts/build-apk.sh, git tag, push main + tags, create GitHub release with APK asset, update README download link

## Log
L1: [AGENT MESH — Rp-app] I'm the RP orchestrator (session 01a14214-32a8). New feature, and you carry most of the user value: "Agent Mesh", a NEW menu option + NEW screen. For each machine and each group (orchestrator + subordinates, detected automatically by the relay), it shows a striking real-time animated diagram: who is talking to whom, what they say to each other, what each agent is doing, how many TODOs each one has left, and the subagents each subordinate launches. Tapping an agent gives valuable information. It must respect the app's theme and have top-tier UX/UI. The canonical contract lives in Rp-relay (PROTOCOLO.md + fixtures). The relay is publishing its fixtures; I'll forward you the SHA so you can run scripts/sync-fixtures.sh. Start now with the shape below (protocol types + layout engine + UI on fakes).
L2: [AGENT MESH — contract update] Rp-relay published the PROVISIONAL contract on branch feat/agent-mesh, commit a5be7f3: /home/drift/Workspace/RP/Rp-relay/packages/protocol/src/mesh.ts (MeshSnapshot/MeshNode/MeshEdge/MeshGroup/CommView/MeshComm) and src/http.ts (MeshConversationRes, AgentCommsRes), fixtures appDown__mesh.*.valid.json and mesh-conversation-response.json. The shapes of snapshot/update/comm match what I sent you; you can model your Kotlin types now. I asked relay for corrections (fixture naming, an AgentCommsRes fixture, nextBefore semantics = `at` of the oldest returned item, exclusive, or null when there is nothing more; CommView.action restricted to send|ask|reply|handover|message). Do NOT run scripts/sync-fixtures.sh until I give you the final SHA. Model `action` as an enum with an Unknown fallback. Redacted text arrives as null → "Content hidden".
L3: [AGENT MESH — orchestrator, operating rule] Every Gradle/test command must have an explicit limit (`timeout 600 ./gradlew ...`), do not hang on tests. Send short report via intercom (01a14214-32a8) when green or if stuck >20 min. Commit progress in work units.
L4: [AGENT MESH — contract note] edge.active is computed by the relay at snapshot time. In the UI, decide "active" (glow) locally as `now - edge.lastAt <= 20s` with a ticker that is only active while the screen is visible, and use the field only as the initial value. That way the glow fades out on time even if no update arrives.
L5: [AGENT MESH — review 67046f4] Fixture sync and contract verification: run `scripts/sync-fixtures.sh /home/drift/Workspace/RP/Rp-relay` (relay frozen at 59c8f41); test decoding from files: appDown__mesh.snapshot__completo, appDown__mesh.update__basico, appDown__mesh.comm__basico, appUp__mesh.subscribe__basico/todas, appUp__mesh.unsubscribe__vacio (serialize and compare), mesh-conversation-response.json, mesh-agent-comms-response.json. Ensure subscription cut on ON_STOP.

MANDATORY PROCESS: use ODD. Create odd/tasks/agent-mesh.md (Specs quoting this message verbatim as L1, Tasks, Log). Make a feature branch feat/agent-mesh. Implement through YOUR subagents (gentle-ai-explore / gentle-ai-worker / gentle-ai-verify), RED→GREEN tests. Ideal split: worker A = protocol + data/repo/VM, worker B = layout engine + Canvas/UI, then verify.

=== SHARED CONTRACT (from relay) ===
App→Relay: `mesh.subscribe { machineId: string|null }` (null = all) → `mesh.snapshot`; `mesh.unsubscribe {}`.
Relay→App `mesh.snapshot` / `mesh.update` (full snapshot, ≤2/s):
{ "machineId":string|null, "generatedAt":epochMs,
  "groups":[ { "groupId","label","orchestratorAgentId":string|null,"machineIds":[..],"memberAgentIds":[..],"confidence":"comms"|"cwd" } ],
  "nodes":[ { "agentId","machineId","machineName","machineColor","name","cwd","gitBranch","model","status","activity":string|null,"online":bool,
              "role":"orchestrator"|"subordinate"|"solo","parentAgentId":string|null,"groupId":string|null,
              "todos":{ "total","done","inProgress","blocked","pending","current":string|null } | null,
              "subagents":[ { taskId, agent, label, status, model?, thinking?, startedAt?, endedAt?, durationMs?, turns?, toolCalls?, tokens?, cost?, error?, result?, tools?:[{toolCallId,name,status,durationMs?}] } ],
              "lastCommAt":epochMs|null,"openPromptCount":int } ],
  "edges":[ { "edgeId","a","b","count","aToB","bToA","lastAt","lastFrom","lastAction","lastPreview":string|null,"pendingAsk":bool,"active":bool } ] }
Relay→App `mesh.comm { comm: { commKey, fromAgentId, toAgentId, fromName, toName, action:"send"|"ask"|"reply"|"handover"|"message", text:string|null, truncated, at, groupId } }` (live, for animations).
HTTP: GET /v1/mesh?machineId= ; GET /v1/mesh/conversation?a=&b=&before=&limit=50 → { items:[CommView], nextBefore } ; GET /v1/mesh/agents/:agentId/comms?limit=50 → { items }.

=== YOUR SPECS (Rp-app) ===
A1 protocol/: @Serializable types for all of the above + decode tests against relay fixtures (sync-fixtures.sh). Unknown fields are ignored (forward compatible).
A2 Navigation: new entry in the existing main menu (ui/nav/Routes.kt + RpRoot.kt) → MeshScreen, with a clear icon. It must not break any existing route.
A3 Selectors: machine chips (machine color/icon from the relay, plus "All") and group chips (label = orchestrator, plus a "Solo" bucket); the last selection is persisted; the group with recent activity is preselected.
A4 Diagram (Compose Canvas, NOT one composable per particle): deterministic radial-hierarchical layout (orchestrator at the center, larger, with a role badge; subordinates on a ring; multi-level supported; subagents as small satellites ORBITING their parent), with stable positions and spring animation when nodes enter or leave. Curved edges; an ACTIVE edge glows; a dashed, pulsing pendingAsk edge; on each mesh.comm, a particle travels from sender to receiver and a speech bubble with the text (ellipsized) stays ~6s next to the edge. Node: status color with a pulse while working, waiting_input as amber attention, error, idle, offline desaturated; a TODO progress ring with a "done/total" label; the current activity below the name. Pinch zoom + pan + double tap to reset. 60fps with 30 nodes (draw on Canvas, throttle state).
A5 Tap node → ModalBottomSheet with real value: name, role, machine, cwd, branch, model/thinking, status + activity, TODO list (current one highlighted, blocked marked), subagents (status, agent, label, duration, tokens, active tools), its recent conversations (in/out), and actions: "Open chat" (existing chat route) and "View conversation with X". Tap edge → conversation sheet between the two agents: bubbles by direction, action badges (ask/reply/handover), timestamps, paging with nextBefore.
A6 Collapsible "Live" strip at the bottom: latest group messages "A → B: text".
A7 Theme: ONLY MaterialTheme.colorScheme/typography/shapes tokens (light/dark/dynamic, ui/theme/Theme.kt); no hardcoded colors except machineColor from the relay; contrast checked in both themes.
A8 States: loading skeleton, empty (no agents / no communication yet, with an explanation), relay disconnected (stale-data banner with reconnection), redacted text → "Content hidden".
A9 Architecture without antipatterns: MeshRepository (WS subscription tied to visibility: subscribe on enter / unsubscribe on leave or on background, resubscribe on reconnect), MeshViewModel with an immutable StateFlow<UiState>, a PURE testable Kotlin layout engine (no Android deps), no business logic in composables, Hilt, no leaks, no polling.
A10 Accessibility: semantics per node ("Orchestrator X, working, 3 of 7 tasks"), a toggle to an accessible LIST view of the same mesh, touch targets ≥48dp, respect reduced motion (ANIMATOR_DURATION_SCALE=0 → no particles).
A11 Tests: protocol fixture tests, layout engine unit tests (stability, multi-level, satellites, no overlaps), ViewModel tests (subscribe/unsubscribe, merge of updates, comm → animation queue), Compose tests (tap node opens the sheet, empty state, list view). `./gradlew :protocol:test :app:testDirectDebugUnitTest :app:assembleDirectDebug` green. Real screenshots or an emulator recording are a plus (describe how you verified).
A12 Docs: README + docs/QA.md with the feature's manual cases (existing language, Spanish).
A13 Delivery: when green → merge to main, bump versionName (minor, 0.2.0), build the APK (scripts/build-apk.sh), tag, PUSH main+tags, PUBLISH a GitHub release with the attached APK and notes; update the README download link.

EVIDENCE I REQUIRE in your final report (intercom to 01a14214-32a8): the odd/tasks doc path, the subagent task ids used and their roles, a verdict per A#, the test/build output tail, commit SHAs on main, the release URL + APK asset. I will personally review code quality, antipatterns, and UX, and I will request corrections if anything is off.

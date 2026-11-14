# Feature: Mesh Subagent Node Detail (`Rp-app`)
Objective: Open subagent details when tapping active `"sub:..."` child nodes in Mesh graph, display full subagent telemetry (`model`, `toolCalls`, `turns`, `result`, `error`), and open parent chat cleanly.
Branch: `feat/mesh-subagent-detail` | Delivery: `ask-on-risk` | Test runner: `./gradlew :protocol:test :shared-ui:ktlintCheck :shared-ui:jvmTest`
Engram mirror: pending (parent workspace ambiguous across subrepos)

## Specs
- S1: Resolve synthetic subagent node taps in `SharedAppController.selectMeshNode(agentId)`:
  - When `agentId` is not directly in `snapshot.nodes`, look up `_meshState.value.layoutResult?.nodes?.get(agentId)?.node`.
  - If that synthetic node has `role == "subagent"` and `parentAgentId != null`, find `parentNode` in `snapshot.nodes` and match its `MeshSubagent` where `SubagentPlacement.nodeId(parentNode.agentId, it.taskId) == agentId`.
  - Populate `MeshNodeDetail(node = syntheticNode.copy(subagents = listOfNotNull(matchedSub), model = matchedSub?.model ?: syntheticNode.model), selectedSubagent = matchedSub, isLoadingComms = false)` without calling `getMeshAgentComms("sub:...")`.
- S2: Enrich `MeshNodeDetailSheet` & `MeshUiState`:
  - Add optional `val selectedSubagent: MeshSubagent? = null` to `MeshNodeDetail`.
  - In `MeshNodeDetailSheet`, when `detail.selectedSubagent != null`, present subagent-focused details (`agent`, `status`, `model`, `duration`, `tokens`, `toolCalls`, `turns`, `tools`, `thinking`, `result`, `error`) cleanly without `"No se reporta progreso de TODOs"` / `"Conversaciones recientes"` noise, and wire `"Abrir chat"` to `onOpenChat(node.parentAgentId ?: node.agentId)`. Also surface `model`, `toolCalls`, `turns`, and `result`/`error` on subagent cards inside normal parent detail sheets.
  - Preserve existing `MeshFormatters`, `SubagentPlacement`, and parent `MeshNodeDetailSheet` behavior (`Detached` / `TODOs` / `Conversaciones recientes` / `Abrir chat`).
- S3: Tests & ktlint:
  - Add unit test in `shared-ui/src/commonTest/` covering `selectMeshNode` on a synthetic `"sub:..."` node (including verifying `nodeDetail` populates `selectedSubagent`, preserves `parentAgentId`, skips invalid subagent HTTP comms call, and handles missing/unknown synthetic ids cleanly).
  - Run `./gradlew :protocol:test :shared-ui:ktlintCheck :shared-ui:jvmTest`.
- S4: Android `MeshViewModel.onNodeClick` parity & Compose UI regression coverage:
  - Extract a shared `resolveMeshNodeDetailTarget` helper in `MeshUiState.kt` so both `SharedAppController.selectMeshNode` and Android `MeshViewModel.onNodeClick` resolve synthetic `"sub:..."` nodes identically without duplication.
  - Add Compose UI tests in `app/src/test/.../MeshScreenTest.kt` and ViewModel test in `app/src/test/.../MeshViewModelTest.kt` covering synthetic subagent detail rendering, `"Abrir chat"` parent vs `subordinate` chat targets, result truncation/JSON/blank handling, and skipping `/comms` fetch.

## Tasks
- T1 (S1, S2, S3) [delegated: odd-worker | Risk: item 3] Implement synthetic subagent Mesh tap handling, enriched `MeshNodeDetailSheet`, and regression tests: done (shared-ui verified)
- T2 (S4) [inline | Risk: item 3] Share `resolveMeshNodeDetailTarget` with Android `MeshViewModel.onNodeClick` and add `MeshScreenTest` + `MeshViewModelTest` coverage: done

## Log
- L1 (2026-11-14): User request: "Vne hay un tema que cuando se crean subagentes y eso en agy.... No se pueden ver desde la aplicación.... creo que para PI si hay algo no? como podemos hacer eso....." -> "SI hagamole!"
- L2 (2026-11-14): Forecast ~140 authored lines across `MeshUiState.kt`, `MeshNodeDetailSheet.kt`, `SharedAppController.kt`, and `shared-ui` commonTest. High risk (item 3: touches shared `SharedAppController` / `MeshNodeDetailSheet` paths) -> independent verifier after worker self-verification.
- L3 (2026-11-14): Verified Android `MeshViewModel.onNodeClick` + `MeshScreenTest` + `MeshViewModelTest` alongside full Gradle matrix (`./gradlew :protocol:test :relay-client:compileCommonMainKotlinMetadata :relay-client:jvmTest :shared-ui:ktlintCheck :shared-ui:jvmTest :app:testDirectDebugUnitTest --console=plain`): `protocol` 71/71, `relay-client` 93/93, `shared-ui` 128/128, `app` 474/476 (2 skipped), exit 0. Risk: item 3 (shared `MeshUiState`/`MeshNodeDetailSheet`/`SharedAppController` paths).

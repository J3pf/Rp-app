# Feature: Contrato de fixtures — la tabla appUp deja de quedarse atrás
Branch: `fix/ci-protocol-fixtures`
Status: Completed

## Specs
S1 El job `Protocolo y reductor (JVM)` de CI (y `main`) falla con
`Tipo appUp no manejado: <type> en <fixture>`.
- Reproducir el rojo igual que CI: clonar/actualizar rp-relay main, `scripts/sync-fixtures.sh`, `:protocol:test`.
- El fallo se nombra con el tipo y el archivo reales; se sigue la evidencia, no el diagnóstico de terceros.

S2 Todo tipo appUp que el relay publica se decodifica con su modelo real y se re-codifica igual al fixture.
- Si falta un modelo en `:protocol`, se añade replicando el tipo del relay: **solo aditivo, sin renombrar**.

S3 La copia versionada de fixtures no puede volver a podrirse en silencio.
- Decisión explícita y justificada en este documento.

## Tasks
- [x] T1 (S1) Reproducir el rojo con los fixtures de rp-relay main y capturar el error exacto. Commit: —
- [x] T2 (S2) Modelos `AgentArchivedRequest`, `GitDiffRequest`, `GitDiscardRequest` + `MsgType.AGENT_CLOSE/AGENT_ARCHIVED/AGENT_PURGE`, y las ramas de la tabla appUp. Commit: —
- [x] T3 (S3) Refresh de la copia versionada + comprobación de drift en CI. Commit: —
- [x] T4 (S1-S3) `:protocol:test` y `:protocol:ktlintCheck` verdes. Commit: —

## Decisión sobre los fixtures versionados (S3)
**Elegida: (a) refrescar en el mismo commit + (c) comprobar drift en CI.** No (b).

Motivo: el contrato se rompió precisamente porque la copia versionada se actualizaba a mano y nadie
comparaba. Borrarla de git (b) haría que `:protocol:test` no probara nada cuando el clon de rp-relay no
esté disponible (portátil sin red, fork, revisión offline) y el test existente "sin fixtures no hace nada"
lo dejaría pasar en silencio: eso cambia un fallo visible por uno invisible. Mantener la copia y comprobar
el drift en CI conserva la prueba offline y convierte la desincronización en un fallo con mensaje y comando
de arreglo. El paso de CI degrada con un `::warning::` solo si el clon de rp-relay falla, nunca falla en
verde silencioso cuando el relay sí está disponible.

## Log
- L1: "PRIORITY CHANGE — do this before Phase B. Phase A is accepted; a pre-existing CI failure is now the blocker. [...] PR #1 CI: `App Android` PASS, `Protocolo y reductor (JVM)` FAIL in 26s → `FixturesTest > todos los fixtures appUp validos...` at `FixturesTest.kt:129`, message `error(\"Tipo appUp no manejado: ...\")`. [...] Root cause, exact: the CI job clones `github.com/j3pf/rp-relay` and syncs 117-118 fixtures into `protocol/src/test/resources/fixtures/relay/`; relay main ships `host.git_diff` and `host.git_discard` fixtures, but your `FixturesTest` appUp switch (lines 80-127) handles none of them [...] Do this now, as its own small branch and PR (`fix/ci-protocol-fixtures`), not folded into the notification PR."
- L2: Rojo reproducido localmente: `scripts/sync-fixtures.sh /Users/drift/Projects/J3pf/Rp-relay` (relay main `2183f88`) trajo 17 fixtures nuevos (126 en total) y `:protocol:test` falló con `Tipo appUp no manejado: agent.archived en appUp__agent.archived__basico.valid.json`. La evidencia amplía el diagnóstico recibido: además de `host.git_diff`/`host.git_discard`, faltaban `agent.archived`, `agent.close` y `agent.purge` (5 tipos, no 2).
- L3: Matiz de contrato descubierto al re-codificar: `agent.archived` publica `before: null` explícito y `RpJson` usa `explicitNulls = false`, así que la re-codificación de Kotlin omite ese nulo. En vez de relajar la igualdad, la comparación usa una forma canónica que descarta entradas `null` en ambos lados; cualquier valor no nulo sigue comparándose exacto. Decisión documentada en el código del test.
- L4: Verde: `:protocol:test` 73/73 y `:protocol:ktlintCheck` BUILD SUCCESSFUL.

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

## Alcance: esta rama es rp-relay **main**, nada más
La copia versionada de esta rama es exactamente lo que publica rp-relay main. Los fixtures de los frames
nuevos de P1 (`agent.close`, `agent.archived`, `agent.purge`, `agent.archived.list`, `host.herdr_close`,
`host.herdr_closed`, `agent.closed`) **no entran aquí**: pertenecen a Phase C y llegan cuando el relay
mergee a main. Las ramas de despacho y los modelos de esos tipos sí se quedan (son aditivos e inofensivos
sin fixtures). Nunca vuelvas a sincronizar desde un worktree de rama para "arreglar" esto: el script
`sync-fixtures.sh` acepta cualquier ruta y no comprueba en qué rama está el clon.

## Log
- L1: "PRIORITY CHANGE — do this before Phase B. Phase A is accepted; a pre-existing CI failure is now the blocker. [...] PR #1 CI: `App Android` PASS, `Protocolo y reductor (JVM)` FAIL in 26s → `FixturesTest > todos los fixtures appUp validos...` at `FixturesTest.kt:129`, message `error(\"Tipo appUp no manejado: ...\")`. [...] Root cause, exact: the CI job clones `github.com/j3pf/rp-relay` and syncs 117-118 fixtures into `protocol/src/test/resources/fixtures/relay/`; relay main ships `host.git_diff` and `host.git_discard` fixtures, but your `FixturesTest` appUp switch (lines 80-127) handles none of them [...] Do this now, as its own small branch and PR (`fix/ci-protocol-fixtures`), not folded into the notification PR."
- L3: Matiz de contrato descubierto al re-codificar: `agent.archived` publica `before: null` explícito y `RpJson` usa `explicitNulls = false`, así que la re-codificación de Kotlin omite ese nulo. En vez de relajar la igualdad, la comparación usa una forma canónica que descarta entradas `null` en ambos lados; cualquier valor no nulo sigue comparándose exacto. Decisión documentada en el código del test.
- L4: Verde: `:protocol:test` 73/73 y `:protocol:ktlintCheck` BUILD SUCCESSFUL.
- L2: Rojo reproducido localmente igual que CI, pero **desde el worktree equivocado**: `scripts/sync-fixtures.sh /Users/drift/Projects/J3pf/Rp-relay` leyó un clon que estaba en la rama `feat/agent-lifecycle-sync` (`2183f88`), no en `main`. Ese error de secuencia hizo que la copia versionada no coincidiera con rp-relay main y el chequeo de drift —que funcionó— pusiera el PR en rojo.
- L3: Frontera real de PR #2, medida contra un clon limpio de main (`git clone --depth 1 https://github.com/j3pf/rp-relay /tmp/relay-main`, main `2c693eb`): la copia versionada en `origin/main` tenía 109 fixtures y relay main publica 117; el delta son **exactamente los 8 fixtures de `host.git_diff` / `host.git_discard`**. Es decir, el fallo original de CI sí era de dos tipos, como decía el diagnóstico; los otros tres (`agent.close`, `agent.archived`, `agent.purge`) eran ruido de mi sincronización desde la rama.
- L4: Matiz de contrato descubierto al re-codificar: `agent.archived` publica `before: null` explícito y `RpJson` usa `explicitNulls = false`, así que la re-codificación de Kotlin omite ese nulo. En vez de relajar la igualdad, la comparación usa una forma canónica que descarta entradas `null` en ambos lados; cualquier valor no nulo sigue comparándose exacto. La decisión queda documentada en el código del test y sigue siendo válida cuando Phase C traiga estos fixtures.
- L5: Arreglo: copia restaurada desde un clon limpio de relay main (117 archivos, `diff -r` idéntico), ramas de despacho y modelos intactos, mensaje del commit corregido y doc reescrita. Criterio de aceptación cumplido: `./scripts/sync-fixtures.sh /tmp/relay-main && git diff --exit-code -- protocol/src/test/resources/fixtures/relay` sin diferencias tras el commit. Verde: `:protocol:test` 73/73, `:protocol:ktlintCheck` BUILD SUCCESSFUL.

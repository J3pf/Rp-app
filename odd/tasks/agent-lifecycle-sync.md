# Feature: Ciclo de vida de agentes Herdr en Rp-app
Branch: `feat/agent-lifecycle-sync` (stacked sobre `fix/ci-protocol-fixtures`, que aporta las costuras de protocolo)
Status: In Progress

## Specs
S1 "1) que si uno elimina un agente de herdr el man también lo borre de la app"
- Un agente cerrado desde Herdr desaparece de la lista y del diagrama **sin acción del usuario**: el relay
  lo quita y la app lo refleja al recibir la actualización.

S2 "agent.forget en la UI conserva su sitio pero gana confirmación"
- Antes de olvidar: diálogo con el texto exacto "Esto cerrará el pane en Herdr y detendrá el agente".
- Confirmar envía `agent.close { agentId }`.
- Si el relay responde `agent_close_unsupported` (no hay pane vinculado), se cae a `agent.forget` (archivar),
  y la UI lo dice sin mentir sobre lo que pasó.

S3 "Archivados"
- Sección "Archivados" que lista los agentes archivados (`agent.archived` → `agent.archived.list`), con
  historial de solo lectura y una acción "Eliminar definitivamente" (`agent.purge`) que pide confirmación.
- Decisión del usuario: "Ocultar + archivar historial".

S4 Contrato espejo en `:protocol` (sin renombrar nada)
- `agent.close`, `agent.archived`, `agent.purge` (app→relay); `agent.archived.list`, `agent.closed`
  (relay→app); `host.herdr_close` (relay→host), `host.herdr_closed` (host→relay); `agent.hello` gana
  `herdr:{paneId,tabId?,workspaceId?}`; código de error `agent_close_unsupported`.

S5 Orden de los fixtures (aprendido en `fix/ci-protocol-fixtures`)
- Los fixtures de los frames nuevos viven en la rama del relay (`feat/agent-lifecycle-sync`) y **no** en
  `main`. Entran a esta rama en su propio commit **después** de que el relay mergee a main, sincronizados
  desde un clon limpio. Nunca se sincroniza desde un worktree de rama.

## Tasks
- [x] T1 (S4) Contrato en `:protocol`: `MsgType.AGENT_CLOSE/AGENT_ARCHIVED/AGENT_PURGE/AGENT_ARCHIVED_LIST/AGENT_CLOSED/HOST_HERDR_CLOSE/HOST_HERDR_CLOSED`, modelos `AgentArchivedRequest`, `ArchivedAgentSummary`, `AgentArchivedList`, `AgentClosed`, `HerdrClose`, `HerdrClosed`, `ErrorCodes.AGENT_CLOSE_UNSUPPORTED`. Commit: este PR
- [x] T2 (S1) `agent.closed` y la retirada por `agents.update.remove` dejan la lista y el diagrama limpios, con test. Commit: este PR
- [x] T3 (S2) Diálogo de confirmación + `agent.close` con fallback a `agent.forget` en `agent_close_unsupported`. Commit: este PR
- [x] T4 (S3) Sección "Archivados" (lista, historial de solo lectura, "Eliminar definitivamente"). Commit: este PR
- [x] T5 (S1-S3) Tests: cierre, fallback, purga, lista archivada, copy del diálogo. Commit: este PR
- [x] T6 (S1-S4) Gates completos. Commit: este PR

## Decisiones
- **Archivados no crea tabla Room.** La lista se pide al relay (`agent.archived`) y vive en el estado de la
  pantalla. Evita una migración de esquema en una entrega con cuatro fases abiertas, y una lista archivada no
  necesita estar disponible sin red: el historial de solo lectura se lee del relay.
- **`agent.purge` es irreversible**: por eso el diálogo nombra el agente y dice qué se pierde, y la acción no es
  el botón por defecto del diálogo.

## Log
- L1: "1) que si uno elimina un agente de herdr el man también lo borre de la app"
- L2: Orquestador (contrato exacto, no renombrar): "`agent.hello` gains optional `herdr:{paneId,tabId?,workspaceId?}`; relay→host `host.herdr_close`/`host.herdr_closed`; `agent.closed{reason:"pane_closed"}`; app→relay `agent.close`." Y: "the relay and plugin sides already ship `host.herdr_close`/`host.herdr_closed` and `agent.closed {reason:"pane_closed"}`, with the pane binding on the already-existing `agents.herdr` column — mirror those names exactly in `:protocol`."
- L3: Contrato verificado contra el código del relay (no contra el diagnóstico): `app.ts:78-90` (`AgentRef`, `AgentClose`, `AgentArchived`, `AgentPurge`, `ArchivedAgentSummary`, `AgentArchivedList`), `host.ts:32-37` (`HostHerdrClose` = `{machineId, paneId}`, `HostHerdrClosed` = `{paneId, ok, error?}`), `agent.ts:73` (`AgentClosed` = `{reason:"pane_closed"}`), `constants.ts:24` (`agent_close_unsupported`). `HerdrInfo` ya existía en `:protocol:66` con `paneId/tabId/workspaceId/session`.
- L4: `:protocol:test` 73/73 y `:protocol:ktlintCheck` verdes tras T1.
- L5: Correcciones que salieron de revisar el trabajo rescatado del worker cancelado, no de confiar en él: (a) `@HiltViewModel` había quedado pegado al `data class` del estado en vez de al view model, así que KSP no compilaba; (b) el handler de `agent.closed` borraba una fila usando `env.id` como `agentId`, cuando ese frame es del puente al relay y no lleva `agentId` — se eliminó en vez de "arreglarlo"; (c) un import inválido de `layout.weight` y un `TopAppBar` sin `@OptIn`; (d) tres llamadas `withTimeout` fuera de una corrutina; (e) el test del diálogo de purga afirmaba por nombre y encontraba 3 nodos. Nada de eso habría aparecido si se hubiera commiteado el stash sin gates.
- L6: Contrato cruzado con los pares ya en main: relay `1dbcc3a`, plugin `98676ae`. Dos hechos de su auditoría que cambian el comportamiento de C: un pane que ya no existe es un resultado normal (el plugin devuelve un fallo tipado, no un crash), así que `not_found` cuenta como cerrado y nunca pinta banner; y la detección de pane cerrado puede tardar la ventana de gracia, así que la retirada llega tarde y un agente brevemente offline no debe parpadear como error. Lo segundo lo cumple el camino existente de `agents.update.remove`: borra y no muestra nada.
- L7: Fronteras de slice: unidades 1-2 (protocolo + repositorio y sus tests) ≈ 594 líneas; unidades 3-4 (UI + doc) van en el segundo slice de la misma rama. El PR de la fase es la unidad de entrega.

## Estado exacto y siguiente comando (para sobrevivir a un compactado)

Rama `feat/agent-lifecycle-sync`, rebasada con `git rebase --onto origin/main 1e899f0` (los dos commits
ya fusionados en main como squash se descartaron; backup local en `backup/phase-c-prebase`). Base actual:
`origin/main` = `be43286`. Copia de fixtures en el árbol: **126** (la de relay main; el "127" de los
informes cuenta el `README.md` del relay, que el script no copia).

Unidades comprometidas (de abajo arriba):
1. `feat(protocol): mirror the Herdr agent lifecycle contract` — Envelope + Models aditivos.
2. `feat(agents): close the Herdr pane, fall back to archiving, and list archives` — `close/archived/purge`
   en `AgentsRepository` + `AgentsRepositoryCloseTest` (8/8).
3. `feat(agents): confirm before closing a pane and add the Archivados section` — VM, diálogos, pantalla
   Archivados, rutas, strings + `AgentsLifecycleTest` (5/5).
4. `docs(odd): …` este documento.
5. `style(agents): ktlint pass on the lifecycle files`.

Gates observados en la base rebasada: `:protocol:test` 73/73, `:app:testDirectDebugUnitTest` verde
(incluye los dos archivos nuevos), `:shared-ui:jvmTest` 102/102, `:app:ktlintCheck` verde tras el commit de
estilo. `:app:lintDirectDebug`: ver el último run, mismo conjunto de 8 avisos preexistentes.

Lo que FALTA en esta fase:
- Re-ejecutar la batería completa en la base rebasada y capturar la salida real (último comando de abajo).
- Empujar la rama y abrir el PR (base `main`), reportando sha + salida de gates.

Comando exacto para retomar:

```
cd /Users/drift/Projects/J3pf/Rp-app
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
export ANDROID_HOME=/usr/local/share/android-commandlinetools
./gradlew :protocol:test :app:testDirectDebugUnitTest :app:ktlintCheck :app:lintDirectDebug \
          :shared-ui:jvmTest :shared-ui:ktlintCheck --console=plain
git log --oneline origin/main..HEAD
git push -u origin feat/agent-lifecycle-sync
```

Nota de comportamiento que no debe perderse: la retirada de un agente cerrado en Herdr llega como
`agents.update.remove` y puede tardar la ventana de gracia; el camino actual borra y no muestra error, que
es lo correcto. Un pane que ya no existe (`not_found`) cuenta como cerrado, no como fallo.

## Revisión nativa (RDD) — NO completada, linaje abierto

- `gentle_review inspect` → `action: start`; START aceptado: linaje **`review-16262249b8a1a3c8`**, estado
  `reviewing`, tier `medium`, 1229 líneas originales, presupuesto de corrección 200, lentes
  `review-risk`, `review-resilience`, `review-reliability`, target `sha256:9e81da4cbdf95d1aac43ee84120cf5f9a641f4fc131cc35076e529c4e5d1e662`.
- `reviewer_results_required`: la captura se intentó **dos veces** (con la previsión de 3 ejecuciones de modelo
  anunciada antes de ejecutar) y ambas fallaron por capacidad del proveedor:
  `429 [ttft_admission_refused] the cell is at capacity; retry shortly` (fallo en `review-risk`, 0 revisores
  preparados, 0 enviados, sin mutación).
- **Consecuencia: el candidato NO está revisado.** No se afirma lo contrario en ningún sitio. El linaje queda
  abierto (no se abandona ni se reinicia) para poder capturar cuando vuelva la capacidad:
  `gentle_review status` con `lineageId=review-16262249b8a1a3c8` y `workspaceRoot=/Users/drift/Projects/J3pf/Rp-app`,
  y enviar la binding exacta que reofrezca.
- **ESTADO: SUPERSEDED.** El split se hizo (decisión del orquestador). Este linaje `review-16262249b8a1a3c8`
  **no se reanuda**: cubre un árbol de 1229 líneas que nadie va a fusionar. El candidato real es PR A en
  `feat/agent-lifecycle-close` (documentado en `odd/tasks/agent-lifecycle-close.md`), que necesita su propio
  `inspect` → `start`. No intentes capturar una binding para este linaje: su árbol ya no existe.

## Estado real de PR A (cerrado) y de esta rama
- PR A se fusionó con squash en `d34c053` (rama `feat/agent-lifecycle-close`, PR #5). Esa rama está cerrada.
- **Esta rama (`feat/agent-archived`) es PR B**: se reconstruyó desde `origin/main` = `d34c053` copiando las
  versiones finales de los archivos que mezclaban A y B, de modo que el diff contra `main` contiene solo
  S3/S5 y no vuelve a proponer lo que A ya fusionó.
- La revisión nativa de PR A quedó **UNAVAILABLE** (linaje `review-a4d11e27f2739625`): intento 1 con
  `429 [ttft_admission_refused]`, intento 2 rechazado por grupo de revisores no vigente, ambos sin mutación.
  Registro completo en `odd/tasks/agent-lifecycle-close.md`. **PR A se fusionó sin veredicto y así está dicho.**

## Pregunta abierta (NO verificada)
¿Puede iniciarse un linaje nuevo mientras otro queda abierto en `reviewing`? No se comprobó: cuando PR A
arrancó, el linaje viejo ya estaba retirado, así que no hubo contención que probar. Solo importa si un
candidato futuro necesita linaje nuevo con otro abierto; entonces **se prueba y se documenta**, no se supone.

## Advertencia de arranque para PR B
El proveedor de revisión estaba sin capacidad en los tres repos durante esta sesión. Si al abrir PR B la
captura vuelve a fallar, el límite es el mismo: 2 intentos, registro literal, entrega sin veredicto.

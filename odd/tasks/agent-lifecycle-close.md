# Feature: Ciclo de vida Herdr — PR A (comportamiento)
Branch: `feat/agent-lifecycle-close`
Base: `origin/main` = `be43286`
Status: PR abierto (#5), gates verdes, revisión nativa NO disponible

## Por qué existe este documento
PR B (Archivados) vive en `feat/agent-lifecycle-sync`. Este es el PR A, el que se fusiona: solo el
comportamiento que puede ocultar o perder datos. Se parte para que la revisión humana sea posible
(1229 líneas en un solo PR era 2.7× la unidad revisable).

## Specs
S1 "que si uno elimina un agente de herdr el man también lo borre de la app" — la retirada llega
como `agents.update.remove`; la app la aplica sin acción del usuario y tolera el retraso de la
ventana de gracia sin pintar error.
S2 "agent.forget conserva su sitio pero gana confirmación" — copia exacta
"Esto cerrará el pane en Herdr y detendrá el agente"; confirmar envía `agent.close`; si el relay
responde `agent_close_unsupported` se cae a `agent.forget` y se informa Archived, nunca Closed;
si responde `not_found` (el pane ya no existía) cuenta como cerrado, no como error.
S4 contrato espejo en `:protocol` (aditivo, sin renombrar).

S3 (Archivados) **no está en este PR**: va en `feat/agent-lifecycle-sync` / PR B.

## Commits (sobre be43286)
- `4f1307d` feat(protocol): mirror the Herdr agent lifecycle contract — S4.
- `a42d1d7` feat(agents): close the Herdr pane, fall back to archiving, and list archives — S1/S2.
- `50cb4f7` feat(agents): confirm before closing a Herdr pane — S2 (diálogo y copia).
- `style(agents): ktlint pass on the close unit` — formato.

## Gates observados (última ejecución, árbol actual)
```
./gradlew :app:testDirectDebugUnitTest :app:ktlintCheck :app:lintDirectDebug
BUILD SUCCESSFUL in 1m43s
app: 235/235 passed, 0 failed
lint issues: 8   (3 AndroidGradlePluginVersion + 5 UnusedResources, todos preexistentes)
```
`:protocol:test` 73/73 con la copia de 126 fixtures (heredada de main).

## Estado de la revisión nativa (PR A) — NO DISPONIBLE
- `inspect` → `action: start`, target `sha256:f2d9ce9e88d0e442eeda9f08187b401d6eb3a26448c93907317dbbbd037cf81f`.
- Primer `start` rechazado con `lenses-invalid` y `lineage_created:false` (sin mutación). Causa: `--lenses-reason`
  es **una sola línea**; mi explicación era un párrafo. Reintento aceptado: linaje **`review-a4d11e27f2739625`**,
  `reviewing`, tier medium, 3 lentes (`review-risk`, `review-resilience`, `review-reliability`), 828 líneas,
  presupuesto de corrección 200.
- Previsión de captura anunciada antes de ejecutar: `pi_host_relay`, 3 ejecuciones de modelo, sin mutación.
- **Intento 1**: falló en `review-risk` con `429 [ttft_admission_refused] the cell is at capacity; retry shortly
  (after 15 s)`; `prepared_reviewers: 0`, `submitted_reviewers: 0`, sin mutación.
- **Intento 2** (tras STATUS fresco que reofreció las mismas 3 bindings y 20 s de espera): rechazado con
  `capture-group-rejected: collectBindings must be the complete distinct current reviewer group in exact
  provider order`; sin mutación. No fue un 429: fue un rechazo de forma, y no se reintentó más porque el
  límite acordado era 2 intentos.
- **Consecuencia: el candidato NO está revisado. No hay veredicto.** El linaje sigue `reviewing`/`collect`,
  `generation 1`, con target `sha256:f2d9ce9e…` y revision `sha256:441c6bb6…`.
- El commit de este documento cambia el árbol del candidato, así que **ese linaje queda inservible para lo
  empujado**. Si vuelve la capacidad, se revisa el candidato actual con un linaje NUEVO, no reanudando este.

## Advertencia para quien retome
Con dos intentos consumidos, no se insiste esta noche: el proveedor está sin capacidad en los tres repos.
La revisión nativa no es requisito de entrega según la política ordinaria del repo; el PR se entrega con
este registro y sin afirmar en ningún sitio que fue revisado.

## Siguiente comando literal
```
cd /Users/drift/Projects/J3pf/Rp-app
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
export ANDROID_HOME=/usr/local/share/android-commandlinetools
git log --oneline origin/main..HEAD
git push -u origin feat/agent-lifecycle-close
gh pr create --base main --head feat/agent-lifecycle-close ...
```
Después: `gentle_review` inspect → start para este candidato, con 2 intentos acotados.

## Log
- L1: "1) que si uno elimina un agente de herdr el man también lo borre de la app"
- L2: Split ordenado por el orquestador: PR A comportamiento (S1/S2/S4), PR B Archivados (S3), cada uno con su linaje y sus intentos de revisión acotados.

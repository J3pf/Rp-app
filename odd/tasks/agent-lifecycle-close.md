# Feature: Ciclo de vida Herdr — PR A (comportamiento)
Branch: `feat/agent-lifecycle-close`
Base: `origin/main` = `be43286`
Status: Parked, gates green, PR not opened

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

## Estado de la revisión nativa
El linaje `review-16262249b8a1a3c8` (candidato de 1229 líneas, tier medium) quedó **SUPERSEDED**
por el split y no se reanuda. Este PR A necesita su propio `inspect` → `start` cuando se abra.

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

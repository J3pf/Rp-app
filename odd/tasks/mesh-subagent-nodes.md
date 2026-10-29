# Feature: subagentes como nodos en la malla (Fase D)
Branch: `feat/mesh-subagent-nodes` (base `b45ee80`, main tras fusionar #7).
Estado: **RED observado, implementación sin empezar.** Pausado por corte de red del humano.

## Specs

- **S1 — Un subagente en ejecución es un nodo del diagrama**, situado exactamente un nivel por debajo
  de su agente en el organigrama. Pedido del humano: «los sub agentes … parte del diagrama, no pills».
- **S2 — Un subagente terminado vuelve a la píldora compacta** que ya existe; deja de ocupar un nodo.
- **S3 — Sin duplicación al cambiar de estado**: la misma identidad (`taskId`) no puede estar a la vez
  como nodo y como píldora, ni quedar posicionada después de terminar.
- **S4 — Nada inventado.** Solo Pi y agy emiten eventos de subagente; claude y codex no tienen fuente, y
  en Pi el único nombre de herramienta conocido es `subagent_run`. Un agente sin subagentes no produce
  nodos: ni placeholders, ni «0 subagentes», ni subagentes inferidos.
- **S5 — Nada de ids crudos en el diagrama.** El nodo muestra `label` (o el nombre del agente) y nunca el
  `taskId` ni el id interno.
- **S6 — Los invariantes de siempre siguen en pie**: sin solapamientos a 360dp, ni en landscape ni en
  tablet; pan y zoom; movimiento reducido; semántica de TalkBack; todos los temas.

Vocabulario de estado observado, y la decisión que se deriva:
`running`/`working` → nodo. `done`/`completed`/`error`/`stopped` → píldora.
Cualquier otro valor (incluido vacío o desconocido) → **píldora**: es la única opción que no inventa
actividad donde no consta. Se compara sin distinguir mayúsculas y sin espacios sobrantes.

## Tasks

| ID | Specs | Ruta | Commit |
| --- | --- | --- | --- |
| T1 | S1–S5 | RED en `shared-ui/src/commonTest/.../layout/SubagentNodePlacementTest.kt` (6 casos) | ver commits |
| T2 | S1, S3, S5 | Regla pura + nodos sintéticos en `MeshLayoutEngine.computeLayout` | pendiente |
| T3 | S2, S3 | Píldoras de terminados en la tarjeta del agente, sin duplicar | pendiente |
| T4 | S6 | 360dp / landscape / tablet, a11y, temas | pendiente |

## Estado observado del RED (2026-10-29)

```
SubagentNodePlacementTest > la tabla de estados decide nodo o pill sin inventar actividad FAILED
SubagentNodePlacementTest > al terminar, el subagente deja de ser nodo y no queda duplicado FAILED
SubagentNodePlacementTest > un subagente en ejecucion es un nodo hijo de su agente FAILED
SubagentNodePlacementTest > el nodo del subagente muestra su etiqueta y nunca su id crudo FAILED
6 tests completed, 4 failed
BUILD FAILED in 13s
```

Los dos que pasan lo hacen por la razón correcta y no por suerte: `un agente sin subagentes no produce
nodos de mas` (hoy el motor no crea ninguno, que es justo el invariante de S4) y
`dos subagentes en ejecucion no se solapan a 360dp`, que hoy es trivialmente cierto porque no emite
nodos; ese caso solo tiene valor como guardia cuando T2 exista.

Cómo encuentra el test los nodos sintéticos: son los que **no estaban en la entrada**. El test no
conoce, ni debe conocer, el formato interno de sus ids.

## Datos que no se pueden inventar (T6 del orquestador)

- Pi y agy emiten `subagent_update`; **claude y codex no tienen fuente de subagentes**.
- En Pi el único nombre de herramienta conocido es `subagent_run`; no se deduce ningún otro.
- Consecuencia: no hay nodo para un agente que no reporta subagentes, y no se rellena el hueco con
  placeholders ni con contadores vacíos.

## Contexto de arquitectura

- `MeshNode.subagents: List<MeshSubagent>` ya existe en `protocol/src/commonMain/.../MeshModels.kt:233`;
  `MeshSubagent` trae `taskId`, `agent`, `label`, `status`.
- `MeshLayoutEngine.computeLayout(nodes, edges, width, height, previousLayout, cardHeights)` es la
  interfaz pública; el motor tiene ~619 líneas y su suite ~773 más este archivo nuevo.
- `NodeDisplayMode { CARD, LEAF_ITEM }`; `PositionedNode` lleva `parentId`, `level` y geometría.
- Hoy: el motor suma 30f de alto cuando `node.subagents.isNotEmpty()` y excluye de la lista compacta a
  los hijos que tienen subagentes. La tarjeta pinta los subagentes como chips.

## Siguiente comando literal

```bash
cd /Users/drift/Projects/J3pf/Rp-app && export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home" && export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :shared-ui:jvmTest --tests "*SubagentNodePlacementTest*" --console=plain
```

Esperado hoy: `6 tests completed, 4 failed`. Después de T2: 6 de 6.

## Log

- L1: «los sub agentes … parte del diagrama, no pills».
- L2: el orquestador fija el límite de datos: solo Pi y agy emiten eventos de subagente; claude y codex ninguno; en Pi solo se conoce `subagent_run`; sin placeholders, sin inferencias, sin ids crudos.
- L3: pausa por corte de red antes de implementar; el RED queda comprometido y el documento es la memoria.

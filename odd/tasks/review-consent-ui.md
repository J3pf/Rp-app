# Feature: consentimiento de revisión remota (C2)
Branch: `feat/review-consent-ui` (base `a8d7c91`).
Estado: **en implementación**. Contrato recibido del plugin, decisiones tomadas, RED observado.

## Specs

- **S1 — El título lo posee la app.** La superficie muestra un título propio, «Consentimiento de
  revisión». El `title` del proveedor (hoy `Review consent: …`) **nunca** se convierte en título de la
  pantalla. *Desviación declarada*: el contrato nombra la cadena `Review consent` en inglés; la app es
  de cara al humano en español y usa «Consentimiento de revisión». Lo que el contrato exige es la
  propiedad del título, no el idioma de la cadena.
- **S2 — El cuerpo se pinta literal.** Es texto renderizado para terminal a ancho 80, no markdown:
  saltos de línea y espacios iniciales exactos, fuente monoespaciada, sin reflow y sin truncado, con
  scroll. Máximo 32 000 caracteres.
- **S3 — Las tres opciones, en el orden del proveedor, con su efecto completo.** Etiqueta y
  `Effect: <effect>` visible para `"0"`, `"1"` y `"2"`. La opción 2 concede además permiso para toda la
  sesión: la UI lo dice, no solo su descripción.
- **S4 — Las respuestas son tokens exactos.** `{optionIds:["0"]}`, `{optionIds:["1"]}`,
  `{optionIds:["2"]}`, `{cancelled:true}`. Nunca se mapean ni se reinterpretan.
- **S5 — Cancelar existe.** Descartar el prompt envía `{cancelled:true}`; jamás un rechazo silencioso.
- **S6 — La rama es estrecha.** Un permiso de herramienta ordinario sigue usando la hoja de permisos
  actual, con su «Denegar con motivo…»; la superficie de consentimiento no ofrece motivo libre.

Identificación de la forma, tomada del contrato: `kind = select`, `category = permission`,
`danger = high`, `tool = null`. Un permiso de herramienta llega como `kind = confirm` con `tool`.

## Tasks

| ID | Specs | Ruta | Commit |
| --- | --- | --- | --- |
| T1 | S1–S6 | RED en `app/src/test/java/dev/j3pf/rp/ui/ComposeUiTest.kt` (4 casos) | `e7df15f` |
| T2 | S1–S4, S6 | `ReviewConsentBody` en `shared-ui` + ruteo antes de `PromptHeader` | `3e2f5e8` (T2+T3+T4) |
| T3 | S5 | Cancelar → `Answer.Cancelled` | `3e2f5e8` |
| T4 | todas | gate completo + lint sin ruido nuevo | `3e2f5e8` |

## Estado observado

- Contrato: plugin P6, PR https://github.com/J3pf/Rp-plugin/pull/2 (`ae701d2`, docs `2442fcc`).
  **Verificado por implementación, no aprobado por revisión**: su propia revisión quedó UNAVAILABLE
  (429, 5 intentos). No citarlo como aprobado.

## Gates observados (2026-10-28)

- RED: `11 tests completed, 3 failed` — fallan «pinta el cuerpo literal» (línea 190: falta el primer
  `Effect:`), «envía el token exacto» y «cancelar envía cancelled»; el cuarto (permiso de herramienta
  conserva su hoja) pasa, que es lo correcto. El primer intento de RED tenía un falso verde: el test de
  tokens enviaba una sola opción y `PermissionBody` acertaba `"2"` por clasificar «Review and allow this
  session» como allow-always; se corrigió el test, no la narrativa.
- GREEN: `:shared-ui:jvmTest` 102/102 (XML 21:51), `:app:testDirectDebugUnitTest` 241/241 (XML 21:48),
  `:protocol:test` 73/73 (UP-TO-DATE, módulo intacto). Total 416/416, 0 fallos.
- `:app:ktlintCheck` y `:shared-ui:ktlintCheck` verdes tras dos correcciones de estilo: encadenado
  multilínea (`chain-method-continuation`) y orden lexicográfico de imports.
- `:app:lintDirectDebug`: **8 issues**, línea base exacta (3 `AndroidGradlePluginVersion` + 5
  `UnusedResources`). Ningún aviso nuevo.

## Log

- L1: pregunta de consentimiento de revisión remota, hoy bloqueada por la forma exacta del prompt del plugin.
- L2: el orquestador entrega el contrato del PR 2 del plugin; fuente verificada por implementación, no por revisión.
- L3: el orquestador decide monoespaciado obligatorio y hoja dedicada para `danger: high`, con el motivo de fidelidad al contrato y peso de la consecuencia.
- L4: se descubre que `PromptContent` enruta toda `category = permission` a `PermissionBody`, que ofrece
  «Denegar con motivo…» (enviaría `text` en vez de `{optionIds:["1"]}`) y reflowa el cuerpo. Un consentimiento
  ahí sale mal por contrato, así que la rama propia se decide por la forma del prompt, no por su etiqueta.
- L5: implementado y verificado. `ReviewConsentBody` se enruta por forma (`select` + `permission` +
  `danger: high`) antes del header, con cuerpo monoespaciado sin reflow, tres opciones en orden con su
  `Effect:` completo, etiqueta visible de permiso de sesión en la opción 2 y `Cancelar` que envía
  `{cancelled:true}`. Gate verde y lint en línea base.

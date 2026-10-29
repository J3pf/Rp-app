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
| T1 | S1–S6 | RED en `app/src/test/java/dev/j3pf/rp/ui/ComposeUiTest.kt` (4 casos) | pendiente |
| T2 | S1–S4, S6 | `ReviewConsentBody` en `shared-ui` + ruteo antes de `PromptHeader` | pendiente |
| T3 | S5 | Cancelar → `Answer.Cancelled` | pendiente |
| T4 | todas | gate completo + lint sin ruido nuevo | pendiente |

## Estado observado

- Contrato: plugin P6, PR https://github.com/J3pf/Rp-plugin/pull/2 (`ae701d2`, docs `2442fcc`).
  **Verificado por implementación, no aprobado por revisión**: su propia revisión quedó UNAVAILABLE
  (429, 5 intentos). No citarlo como aprobado.

## Log

- L1: pregunta de consentimiento de revisión remota, hoy bloqueada por la forma exacta del prompt del plugin.
- L2: el orquestador entrega el contrato del PR 2 del plugin; fuente verificada por implementación, no por revisión.
- L3: el orquestador decide monoespaciado obligatorio y hoja dedicada para `danger: high`, con el motivo de fidelidad al contrato y peso de la consecuencia.
- L4: se descubre que `PromptContent` enruta toda `category = permission` a `PermissionBody`, que ofrece
  «Denegar con motivo…» (enviaría `text` en vez de `{optionIds:["1"]}`) y reflowa el cuerpo. Un consentimiento
  ahí sale mal por contrato, así que la rama propia se decide por la forma del prompt, no por su etiqueta.

# rp-app

App Android (Kotlin + Jetpack Compose) para ver y controlar todos tus agentes [Pi](https://github.com/earendil-works/pi) a través
de tu relay **RP**: chat en vivo, adjuntos y **notificaciones de preguntas/autorizaciones con respuesta directa desde la notificación**.

> Estado: **planeación**. Todavía no hay código.

| Documento | Contenido |
|---|---|
| [docs/PLAN-APP.md](docs/PLAN-APP.md) | Plan detallado de la app |
| [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) | Visión global (copia; canónica en `rp-relay`) |
| [`rp-relay/docs/PROTOCOLO.md`](https://github.com/j3pf/rp-relay/blob/main/docs/PROTOCOLO.md) | Contrato de red |

## Referencias investigadas

- Pi (`earendil-works/pi`, v1.2.0): `packages/coding-agent/docs/{extensions,packages,rpc,rpc-extension-ui,json,security,sessions}.md`,
  `src/core/extensions/{types,runner}.ts`, `src/modes/{interactive/interactive-mode,rpc/rpc-mode}.ts`,
  ejemplos `permission-gate.ts`, `question.ts`, `notify.ts`, `send-user-message.ts`.
- [Remote Pi](https://github.com/jacobaraujo7/remote_pi) (Flutter + relay Rust + extensión Pi): emparejamiento QR, aprobaciones vía
  `@eko24ive/pi-ask`; **sin push real** — es la principal carencia que este proyecto cubre.
- [Happy](https://github.com/slopus/happy) / Happier (happier-dev): wrapper de CLI + servidor Fastify/Socket.IO con cifrado E2E.
- [Moshi](https://getmoshi.app/docs): terminal móvil + hook en el host que genera bandeja de aprobaciones y push.

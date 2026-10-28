# Feature: Notification lifecycle — no zombie notifications after answering from a push
Branch: `fix/notification-zombie`
Status: In Progress

## Specs
S1 "cuando se le da permitir o rechazar desde una push, funciona pero queda una notificacion zombie"
- Después de pulsar Permitir/Denegar/Responder desde una notificación push, no queda ninguna notificación
  asociada a ese prompt en la bandeja (child `rp.prompt`, su resumen de grupo `rp.summary`, ni un
  superviviente "Enviando…"/ongoing) en ~1 s.
- El resumen de grupo se retira solo cuando el agente ya no tiene hijos en la bandeja.

S2 "un resync de WS no debe resucitar un prompt ya respondido"
- Una repetición de `prompt.opened` (o un `app.welcome` generado antes de la respuesta) no vuelve a abrir
  en la bandeja un prompt resuelto dentro de la ventana de replay (10 min).
- Un push repetido del mismo `promptId` ya respondido no vuelve a publicar la notificación, aunque el push
  nunca llegara a persistirse como fila local.

S3 "el estado intencional «Sin conexión · se reintentará» se conserva y se retira cuando la respuesta encolada tiene éxito"
- Sin red: la notificación del prompt queda como `notif_retrying`, no ongoing, dentro de su grupo, y sobrevive.
- Cuando `AnswerWorker` confirma la respuesta, la notificación y su resumen desaparecen.
- "Enviando…" no puede quedar colgada: lleva `setTimeoutAfter` como red de seguridad.

S4 "el deduper se limpia para que un prompt legítimamente reabierto sí aparezca"
- Al asentar un prompt se olvida la clave `prompt:<id>`; la ventana de replay la acota la marca
  `answered:<id>` (10 min), no el TTL de 24 h del deduper.

S5 "una notificación «Terminó» obsoleta no sobrevive a un prompt nuevo del mismo agente"
- `showPrompt` retira el `rp.settled` de ese agente: si está preguntando, no está terminado.

## Tasks
- [ ] T1 (S1) `cancelPrompt` asienta: retira child, prunes resumen huérfano, olvida dedup y marca respondido; `PromptActionHandler` usa esa ruta. `PromptActionReceiver.kt`, `NotificationFactory.kt`, `NotificationDeduper.kt`. Commit: —
- [ ] T2 (S2) Guarda de replay de doble vía: marca `answered:<id>` (push sin fila local) + fila resuelta dentro de la ventana (`PromptDao.recentlyResolved`). `PushRouter.kt`, `Daos.kt`. Commit: —
- [ ] T3 (S2) `prompt.opened`/`app.welcome` no reabren una fila resuelta recién (`PromptDao.upsertOpen`). `PromptsRepository.kt`. Commit: —
- [ ] T4 (S3) Reintento sin red conservado y retirado al asentar; `setTimeoutAfter` en "Enviando…" y grupo preservado al reemplazar. `NotificationFactory.kt`. Commit: —
- [ ] T5 (S4) Olvidar la clave del deduper al asentar. `NotificationDeduper.kt`. Commit: —
- [ ] T6 (S5) `showPrompt` cancela el `settled` obsoleto del agente. `NotificationFactory.kt`. Commit: —
- [ ] T7 (S1-S5) Gates completos (`:app:testDirectDebugUnitTest`, `:app:ktlintCheck`, `:app:lintDirectDebug`) y commit. Commit: —

## Causa raíz (file:line, verificada por el rojo)
1. `NotificationFactory.kt` `postAgentSummary` publica el resumen de grupo `rp.summary` del agente; el
   `cancelPrompt` antiguo solo cancelaba el child `rp.prompt` con el mismo id → resumen huérfano sin hijos
   = la notificación zombie de la bandeja.
2. `PromptActionReceiver.kt` (`handleAnswer`) llamaba a `cancelNotification(...)` en vez de la ruta de
   asentamiento: nunca se podaba el resumen ni se tocaba el deduper.
3. `NotificationDeduper.forget()` no tenía ni un solo call site: la clave `prompt:<id>` vivía 24 h y un
   prompt legítimamente reabierto quedaba silenciado.
4. `PromptsRepository.kt` hacía `dao.upsert(...)` a ciegas en `prompt.opened`: un frame repetido después de
   responder reabría la fila ya resuelta.
5. `showSending` publicaba una notificación ONGOING sin `setTimeoutAfter`: si el flujo moría a medias, no se
   podía descartar nunca.
6. `showRetrying` reemplazaba el child del grupo por una notificación sin grupo: el resumen quedaba huérfano
   también en el estado "se reintentará".

## Log
- L1: "1) que si uno elimina un agente de herdr el man también lo borre de la app 2) rediseñar el logo por que no me gusta para nada, debe ser minimalista y moderno 3) mejorar la gestion de las notificaciones (ejemplo: cuando se le da permitir o rechazar desde una push, funciona pero queda una notificacion zombie) 4) en la red de agentes, los sub agentes de cada agente deberian ser parte del diagrama, no pills dentro de cada agente, para optimizar el espacio, en pills mostrar los que ya terminaron y como parte de la organizacion los que estan corriendo en el momento 5) revisar la app para poderla compilar y publicar para ios tambien y obviamente para android"
- L2: Orquestador multi-repo de RP: Phase A = punto 3, rama `fix/notification-zombie`, reproducir el zombie primero (test rojo), aceptación: sin child, sin resumen de grupo, sin superviviente settled/ongoing en ~1 s; resync WS no resucita; «se reintentará» se conserva y se retira al éxito; deduper limpio para reapertura legítima; reportar causa raíz con file:line.
- L3: La máquina no tenía JDK ni Android SDK (el brief solo daba por ausente Xcode). Se reportó y el orquestador autorizó provisionar: Temurin 21.0.13 (tarball Adoptium, sin sudo), Android cmdline-tools, platform-tools 37.0.1, build-tools 37.0.0 y `platforms;android-37.0` (no existe `platforms;android-37` exacto; AGP 9.4.1 resuelve `compileSdk = 37` contra 37.0 sin tocar el build). `:protocol:test` verde como prueba de toolchain (BUILD SUCCESSFUL, exit 0).
- L4: RED observado con `NotificationZombieTest` (10 casos): 7 fallos por la causa buscada (resumen huérfano en Permitir/Denegar/WS-resolved, "se reintentará" no se retira, deduper sin olvidar, `settled` obsoleto, replay de `prompt.opened` reabre la fila, replay de push resucita). Un primer RED falló por la razón equivocada (el `setUp` sembraba el push con `factory.showPrompt`, saltándose el deduper); se corrigió el test sembrando por `PushRouter.handleDirect`, no la narrativa.
- L5: GREEN: `:app:testDirectDebugUnitTest --tests "dev.j3pf.rp.push.*" --tests "dev.j3pf.rp.data.*"` → BUILD SUCCESSFUL, 90/90 pruebas (NotificationZombieTest 10/10). En el camino apareció un hueco real: el push no siempre persiste la fila del prompt, así que la guarda de replay necesitó además la marca `answered:<id>` del deduper, acotada a 10 min.

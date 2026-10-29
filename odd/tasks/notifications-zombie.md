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
- [x] T1 (S1) `cancelPrompt` asienta: retira child, prunes resumen huérfano, olvida dedup y marca respondido; `PromptActionHandler` usa esa ruta. `PromptActionReceiver.kt`, `NotificationFactory.kt`, `NotificationDeduper.kt`. Commit: 9bf25f3
- [x] T2 (S2) Guarda de replay de doble vía: marca `answered:<id>` (push sin fila local) + fila resuelta dentro de la ventana (`PromptDao.recentlyResolved`). `PushRouter.kt`, `Daos.kt`. Commit: cierre (T7)
- [x] T3 (S2) `prompt.opened`/`app.welcome` no reabren una fila resuelta recién (`PromptDao.upsertOpen`). `PromptsRepository.kt`. Commit: cierre (T7)
- [x] T4 (S3) Reintento sin red conservado y retirado al asentar; `setTimeoutAfter` en "Enviando…" y grupo preservado al reemplazar. `NotificationFactory.kt`. Commit: 9bf25f3
- [x] T5 (S4) Olvidar la clave del deduper al asentar. `NotificationDeduper.kt`. Commit: 9bf25f3
- [x] T6 (S5) `showPrompt` cancela el `settled` obsoleto del agente. `NotificationFactory.kt`. Commit: 9bf25f3
- [x] T7 (S1-S5) Gates completos (`:app:testDirectDebugUnitTest`, `:app:ktlintCheck`, `:app:lintDirectDebug`) y commit. Commit: cierre (T7)

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
7. **Hueco que el análisis estático no vio y el verde destapó:** el push no siempre persiste la fila del
   prompt (el canal directo solo pinta la notificación; la fila llega por `prompt.opened` vía WS). Por eso
   una guarda basada solo en la BD no puede frenar un push duplicado. Se añade la marca `answered:<id>` en
   la tabla del deduper, que sí existe aunque no haya fila.
   **Por qué la ventana es de 10 minutos y no de 24 h:** 24 h es el TTL del deduper y silenciaría de más un
   id reutilizado; 10 min cubre con holgura el replay real (reintento del relay, reconexión, reentrega de
   WorkManager) sin convertir el silencio en permanente. La ventana vive en `PromptState.REPLAY_WINDOW_MS` y
   la usan las dos guardas (marca del deduper y fila resuelta), así que no pueden desincronizarse.

## Log
- L1: "1) que si uno elimina un agente de herdr el man también lo borre de la app 2) rediseñar el logo por que no me gusta para nada, debe ser minimalista y moderno 3) mejorar la gestion de las notificaciones (ejemplo: cuando se le da permitir o rechazar desde una push, funciona pero queda una notificacion zombie) 4) en la red de agentes, los sub agentes de cada agente deberian ser parte del diagrama, no pills dentro de cada agente, para optimizar el espacio, en pills mostrar los que ya terminaron y como parte de la organizacion los que estan corriendo en el momento 5) revisar la app para poderla compilar y publicar para ios tambien y obviamente para android"
- L2: Orquestador multi-repo de RP: Phase A = punto 3, rama `fix/notification-zombie`, reproducir el zombie primero (test rojo), aceptación: sin child, sin resumen de grupo, sin superviviente settled/ongoing en ~1 s; resync WS no resucita; «se reintentará» se conserva y se retira al éxito; deduper limpio para reapertura legítima; reportar causa raíz con file:line.
- L3: La máquina no tenía JDK ni Android SDK (el brief solo daba por ausente Xcode). Se reportó y el orquestador autorizó provisionar: Temurin 21.0.13 (tarball Adoptium, sin sudo), Android cmdline-tools, platform-tools 37.0.1, build-tools 37.0.0 y `platforms;android-37.0` (no existe `platforms;android-37` exacto; AGP 9.4.1 resuelve `compileSdk = 37` contra 37.0 sin tocar el build). `:protocol:test` verde como prueba de toolchain (BUILD SUCCESSFUL, exit 0).
- L4: RED observado con `NotificationZombieTest` (10 casos): 7 fallos por la causa buscada (resumen huérfano en Permitir/Denegar/WS-resolved, "se reintentará" no se retira, deduper sin olvidar, `settled` obsoleto, replay de `prompt.opened` reabre la fila, replay de push resucita). Un primer RED falló por la razón equivocada (el `setUp` sembraba el push con `factory.showPrompt`, saltándose el deduper); se corrigió el test sembrando por `PushRouter.handleDirect`, no la narrativa.
- L5: GREEN: `:app:testDirectDebugUnitTest --tests "dev.j3pf.rp.push.*" --tests "dev.j3pf.rp.data.*"` → BUILD SUCCESSFUL, 90/90 pruebas (NotificationZombieTest 10/10). En el camino apareció un hueco real: el push no siempre persiste la fila del prompt, así que la guarda de replay necesitó además la marca `answered:<id>` del deduper, acotada a 10 min.
- L6: Checkpoints pedidos por el orquestador: A = `10541e0` (test rojo solo: doc + `NotificationZombieTest.kt`, 6 casos, 5 fallan, compila y ktlint limpio), B = `9bf25f3` (asentamiento + deduper + resumen + settled + timeout; 219/219 verdes, ktlint verde), C = cierre (guarda de replay en `PushRouter` + `prompt.opened` que no reabre + los 3 casos restantes; 222/222 verdes, ktlint verde). Frontera de slice: A+B ≈ 510 líneas autoradas, C ≈ 60; el PR de la fase es la unidad de entrega.
- L7: Gate de lint: `:app:lintDirectDebug` BUILD SUCCESSFUL con 8 avisos preexistentes que no introduce este cambio: 3 `AndroidGradlePluginVersion` (9.4.1 → 9.5.0 disponible, `gradle/libs.versions.toml:2`) y 5 `UnusedResources` en `app/src/main/res/values/strings.xml` (`chat_msg_failed`:21, `chat_msg_retry`:22, `action_abort`:25, `diff_no_changes`:28, `diff_request_adjustment`:29). Queda como decisión para Phase F (limpieza de recursos / política de lint), no se tocan aquí.

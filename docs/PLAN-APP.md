# Plan — App Android (`rp-app`)

Contexto: [ARQUITECTURA.md](ARQUITECTURA.md) · [RESILIENCIA.md](RESILIENCIA.md) · [COMPATIBILIDAD.md](COMPATIBILIDAD.md) (herdr, gentle-shell/gentle-pi, separación por PC) · Contrato: `rp-relay/docs/PROTOCOLO.md`.

## 1. Objetivos de la app

1. Emparejarse con mi relay (QR).
2. Ver **todos** los agentes (online/offline) **separados por PC** (personal / empresa), con estado y preguntas pendientes.
3. Chat en vivo con cada agente: streaming de texto, razonamiento, llamadas a herramientas y resultados.
4. Enviar mensajes (normal / steer / follow‑up), **adjuntar** archivos, fotos o capturas, e interrumpir.
5. **Notificaciones** de preguntas y autorizaciones con app cerrada, respondibles **desde la notificación**.
6. Bandeja global de pendientes.

## 2. Stack (decidido)

| Área | Elección |
|---|---|
| Lenguaje / UI | Kotlin 2.x, Jetpack Compose (BOM estable), Material 3, tema dinámico (Material You) + modo oscuro |
| SDKs | `minSdk 29` (Android 10), `targetSdk`/`compileSdk 36` (verificar la exigida por Play en el momento de publicar) |
| Arquitectura | MVVM + UDF: `ViewModel` + `StateFlow`; capas `data` / `domain` / `ui`; un solo módulo `app` + módulo `protocol` (Kotlin puro) |
| DI | Hilt |
| Red | OkHttp 4 (WebSocket + HTTP), kotlinx.serialization (JSON, `ignoreUnknownKeys = true`) |
| Persistencia | Room (caché de agentes, entradas, prompts), DataStore (preferencias), Keystore + `EncryptedSharedPreferences`/Tink para token y `pushKey` |
| Push | **Canal directo propio** (servicio en primer plano `remoteMessaging` + WebSocket al relay, sin Google) + Firebase Cloud Messaging **opcional** (sabor de build `fcm`) |
| Biometría | `androidx.biometric` (BiometricPrompt) para envíos "como tecleado" y aprobaciones críticas |
| QR | CameraX + ML Kit Barcode Scanning (modelo empaquetado, sin Play Services de descarga) |
| Markdown | `com.mikepenz:multiplatform-markdown-renderer-m3` (+ módulo de resaltado de código) |
| Imágenes | Coil 3 |
| Trabajo en segundo plano | WorkManager (reintentos de respuesta/subida), `BroadcastReceiver` con `goAsync()` para acciones de notificación |
| Navegación | Navigation Compose (tipada) |
| Tests | JUnit5/4, Turbine, MockWebServer (OkHttp), Compose UI tests, Robolectric para notificaciones |
| Build | Gradle KTS + version catalog, AGP 8.x, KSP |

`applicationId = "dev.j3pf.rp"` (debe coincidir con la app registrada en Firebase).

## 3. Pantallas

```
Onboarding ─► Escanear QR / introducir URL+código ─► Permiso de notificaciones ─► Agentes
                                                                                    │
  ┌──────────── barra inferior: [Agentes] [Pendientes (badge)] [Ajustes] ──────────┘
  │
  │  Selector de PC fijo arriba (persistente, afecta Agentes y Pendientes):
  │     [ Todas ] [ 🏠 Personal (2) ] [ 🏢 Empresa (1🔔) ]     ← un chip por contexto, con su color y contador
  │
  ├─ Agentes:   lista agrupada por PC (cabecera con nombre, color, online/offline, "rp-host ✓")
  │             tarjeta: nombre sesión · carpeta (último segmento) · rama git · modelo
  │             chips: estado (Trabajando ⏳ / Esperando tu respuesta 🔔 / Inactivo / Desconectado hace 3 h),
  │                    lanzador (gentle-shell / pi), herdr (workspace·pane), modo gentle (NORMAL / AUTO / YOLO)
  │             badge de prompts abiertos · vista previa del último mensaje ("contenido oculto" si la PC redacta)
  │             FAB "Nuevo agente" (fase 3, si la PC tiene rp-host): PC → carpeta → lanzador (gentle-shell/pi)
  │                → dónde: con herdr [Nuevo workspace] (defecto) · [Pestaña en workspace…] · [Junto a un agente…]
  │                  (lista viva de workspaces/pestañas/paneles de herdr de esa PC); sin herdr: tmux (Linux) /
  │                  Windows Terminal (Windows) → prompt inicial opcional
  │
  ├─ Chat del agente:
  │    cabecera: nombre, estado, modelo, botón ⏹ abort (si trabajando), menú (info, olvidar)
  │    lista: burbujas usuario (con "📱" si vino del móvil) / asistente (markdown, streaming)
  │           bloques plegables: razonamiento, llamada de herramienta (nombre + args resumidos;
  │           bash muestra comando; edit muestra diff), resultado (truncado + "ver más")
  │           tarjetas de prompt en línea (mismo componente que la hoja de respuesta)
  │           banner "Esperando en la terminal (diálogo no remoto)" cuando llega terminal_wait
  │    chip de modo AUTO de gentle-pi: "AUTO · tus mensajes se envían como tecleados 🔒" (método `auto` del plugin);
  │       sólo si el plugin informa que `interactive` y `herdr` no están disponibles se muestra el aviso
  │       "En Auto este mensaje no autoriza acciones" con [Pasar a Normal] (envía `/gentle:mode normal` con `asCommand`)
  │    compositor: texto multilínea, 📎 adjuntar (archivo / foto / cámara), método de entrega:
  │       [Automático] (defecto) · [Como tecleado 🔒] · [Mensaje de extensión] · [Comando /…] (si empieza con /)
  │       "Como tecleado" pide BiometricPrompt (válido 5 min) porque cuenta como autorización humana en gentle Auto
  │       cuando el agente trabaja: [Encolar (follow‑up)] [Interrumpir y dirigir (steer)]
  │
  ├─ Pendientes: todos los prompts abiertos de todos los agentes, ordenados por peligro y antigüedad;
  │              tocar → hoja de respuesta
  │
  ├─ Hoja de respuesta (BottomSheet):
  │    permission: herramienta + comando/args completos en monoespaciado, nivel de peligro,
  │                botones grandes [Permitir] [Permitir siempre (sesión)] [Denegar] [Denegar con motivo]
  │    question:   pregunta, opciones como chips/botones (con descripción), texto libre si allowFreeText
  │    questionnaire (ask_user_question de gentle-pi): 1–4 preguntas en páginas con progreso (header),
  │                opción única / múltiple, "preview" en markdown de la opción enfocada, botón Enviar al final
  │    subagente:  igual que question con etiqueta "❀ subagente <nombre>"
  │    dialog:     confirm (Sí/No), select (lista), input (texto)
  │    Si al responder llega 409 → "Ya respondido en otro lugar" y se cierra.
  │
  └─ Ajustes: relay (nombre, URL, estado), dispositivos y máquinas emparejados (revocar),
              emparejar nueva máquina (muestra código), notificaciones por categoría y por agente,
              "Requerir desbloqueo para aprobar", horas silenciosas (fase 4),
              guía de optimización de batería, versión, licencias, cerrar sesión
```

## 4. Capa de datos

### 4.1 `RelayConnection` (singleton)
- WebSocket completo a `/ws/app?mode=foreground` mientras la app está en primer plano (`ProcessLifecycleOwner`).
- **`DirectPushService`** (canal directo, por defecto activado; independiente de Google): servicio en primer plano con
  `foregroundServiceType="remoteMessaging"` (permisos `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_REMOTE_MESSAGING`),
  notificación permanente mínima ("RP conectado · 3 PCs"), WebSocket a `/ws/app?mode=direct` con ping cada 4,5 min,
  reconexión con backoff + `NetworkCallback`; se arranca en `BOOT_COMPLETED` y al abrir la app; recibe `push.notify`
  y responde `push.ack`. Se puede desactivar en Ajustes si se usa sólo FCM.
- Reconexión con backoff (1 → 30 s) + `ConnectivityManager.NetworkCallback` para reintentar al recuperar red.
- Expone `SharedFlow<ServerMessage>` y `StateFlow<ConnState>`.
- Peticiones con `id` → `CompletableDeferred` por id, timeout 10 s.

### 4.2 Repositorios
- `MachinesRepository`: `machines.update` → Room `machines` (contexto, color, icono, online, hostOnline);
  edición vía `PATCH /v1/machines/{id}`; el filtro de PC seleccionado se guarda en DataStore.
- `AgentsRepository`: `agents.update` → Room `agents`; `Flow<List<AgentWithCounts>>` filtrable por `machineContext`.
- Todas las entidades Room llevan `relayId` desde v1 (hoy siempre uno) para poder añadir un segundo relay
  (p. ej. uno de la empresa) en fase 4 sin migración destructiva.
- `ChatRepository(agentId)`: al abrir el chat → `agent.subscribe{sinceSeq=último seq en Room, epoch}`; aplica
  `agent.history` y eventos; mantiene el **mensaje en streaming en memoria** (deltas) y persiste sólo `message_end`,
  `tool_execution_*` finales. Paginación hacia atrás local.
- `PromptsRepository`: `prompt.opened/resolved` → Room `prompts`; `answer(promptId, answer)` por WS si está conectado,
  si no por HTTP `POST /v1/prompts/{id}/answer`; reintento con WorkManager si no hay red (expedited work).
- `AttachmentsRepository`: SAF (`ActivityResultContracts.OpenMultipleDocuments`), Photo Picker
  (`PickMultipleVisualMedia`), cámara (`TakePicture` a `FileProvider`). Compresión opcional de fotos a JPEG 85 % / 2048 px.
  Subida `POST /v1/blobs` con progreso (OkHttp `RequestBody` contando bytes), límite 25 MB.

### 4.3 Modelo de reconstrucción del chat
`ChatItem` sellado: `UserMsg`, `AssistantMsg(blocks: List<Block>)` donde `Block = Text | Thinking | ToolCall`,
`ToolRun(toolCallId, name, args, status, partial, result, isError, durationMs, children)`, `PromptCard`,
`SystemNote(compaction, model change, session renamed)`. Reductor puro `reduce(state, event) → state` con tests
exhaustivos alimentados por los fixtures del protocolo.

## 5. Notificaciones (requisito central)

### 5.1 Canales — **un grupo de canales por PC**
Por cada PC emparejada se crea un `NotificationChannelGroup` (`rp.machine.<machineId>`, nombre = "🏢 PC Empresa") con
los cinco canales de abajo (`rp.<machineId>.approvals`, …). Así, desde los ajustes del sistema, puedes silenciar o
cambiar el sonido de la PC de la empresa sin afectar a la personal. Al revocar/olvidar una PC se borra su grupo.
Además, en la app: silenciar PC, horas silenciosas por PC y "autorizaciones críticas siempre" (se guardan en el relay
con `PUT /v1/devices/me/prefs`, que es quien decide si manda el push).

| Canal (por PC) | Importancia | Uso |
|---|---|---|
| `approvals` | HIGH (heads‑up, sonido, vibración) | `category=permission` |
| `questions` | HIGH | `question` y `dialog` |
| `terminal` | DEFAULT | `terminal_wait` |
| `finished` | DEFAULT | agente terminó (`settled`) |
| `errors` | DEFAULT | agente con error / desconectado con prompts abiertos |

Android 13+: pedir `POST_NOTIFICATIONS` en el onboarding (sin él no hay notificaciones).

### 5.2 Recepción (`RpMessagingService : FirebaseMessagingService`)
1. `onMessageReceived` → descifra `c` con AES‑256‑GCM (`pushKey` del Keystore, AAD `"rp1"`). Falla → ignorar.
2. `k=prompt`: elige el canal de la PC (`machineId`; si el grupo aún no existe se crea al vuelo) y construye la
   notificación (id = hash de `promptId`; título "🏢 PC Empresa · <agente>") con:
   - **confirm** / **permission**: acciones `[Permitir] [Denegar]` (+ "Abrir" al tocar el cuerpo).
   - **select** con ≤3 opciones: una acción por opción. >3: tocar abre la hoja.
   - **input / allowFreeText**: acción "Responder" con `RemoteInput` (texto inline).
   - `setAuthenticationRequired(true)` en acciones de aprobación si el ajuste está activo (API 31+).
   - Agrupadas por agente (`setGroup(agentId)`) con resumen.
   - `setTimeoutAfter` = `expiresAt` si viene.
3. `k=cancel`: `NotificationManagerCompat.cancel(id)` → la notificación desaparece cuando se responde en la terminal
   o en otro dispositivo.
4. `k=settled`: notificación en `finished` con vista previa y acción "Responder" (RemoteInput → `POST /v1/agents/{id}/messages`).
5. `onNewToken` → `PUT /v1/devices/me/push` (WorkManager con reintentos).

### 5.3 Acción desde la notificación (`PromptActionReceiver`)
```
onReceive(intent) { val pending = goAsync()
  scope.launch { try {
      val ok = api.answer(promptId, answer)           // HTTPS directo, timeout 8 s
      if (ok || conflict) notifications.cancel(id)    // 409 = ya respondido → también se quita
      else notifications.showRetrying(id)             // y encola WorkManager expedited
  } finally { pending.finish() } } }
```
Tras pulsar, la notificación se actualiza a "Enviando…" inmediatamente para dar feedback.

### 5.4 Fiabilidad
- Dos canales independientes (directo y FCM opcional) que llevan el mismo cuerpo; `NotificationDeduper` (Room, por
  `promptId`/`pushId`, 24 h) garantiza una sola notificación. Ambos canales llaman al mismo `NotificationFactory`.
- FCM *data* high priority permite ejecutar `onMessageReceived` aunque la app esté en Doze/cerrada (no force‑stopped).
- El canal directo requiere excluir la app de la optimización de batería para máxima fiabilidad en algunos fabricantes;
  el onboarding lo pide explícitamente (en el caso FCM-only es sólo recomendación).
- Pantalla de ajustes con estado: permiso de notificaciones, canal `approvals` no silenciado, exclusión de
  optimización de batería (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` sólo como guía, no obligatorio), y
  botón "Enviar notificación de prueba" (llama a un endpoint del relay que hace push al propio dispositivo).

## 6. Emparejamiento

1. En el servidor: `rp-relay pair device` → QR con `rp://pair?u=https://relay…&c=ABCD2345`.
2. App: escanea (o pega URL+código) → (si el build incluye FCM) obtiene token → `POST /v1/pair/device {code, name: Build.MODEL, fcmToken?}` → `PUT /v1/devices/me/push {direct:true}` → arranca `DirectPushService`
   → guarda `deviceToken` y `pushKey` cifrados (Keystore).
3. Comprueba `/healthz` y abre WS. Deep link `rp://pair` registrado para abrir la app al escanear con la cámara del sistema.
4. Emparejar una máquina desde la app: Ajustes → "Añadir máquina" → `POST /v1/pair/codes` → muestra
   `/rp pair https://relay… CODIGO` listo para copiar.

## 7. Seguridad en el dispositivo
- `deviceToken` y `pushKey` sólo en almacenamiento cifrado respaldado por Keystore; excluidos de backup
  (`android:dataExtractionRules` / `fullBackupContent`).
- `network_security_config`: sólo HTTPS (cleartext prohibido) — excepción opcional de depuración para `10.0.2.2`.
- Opción de bloqueo biométrico al abrir la app (BiometricPrompt, fase 4).
- `FLAG_SECURE` opcional en el chat (fase 4).

## 8. Estructura del proyecto

```
rp-app/
├── app/src/main/java/dev/j3pf/rp/
│   ├── RpApp.kt                    # Hilt, canales de notificación
│   ├── di/
│   ├── data/{net/{RelayConnection,RelayApi},db/{RpDatabase,daos,entities},repo/*,secure/SecretStore}
│   ├── domain/{model/*,chat/ChatReducer.kt}
│   ├── push/{RpMessagingService,PushCrypto,NotificationFactory,PromptActionReceiver}
│   └── ui/{onboarding,agents,chat,pending,promptsheet,settings,components,theme}
├── protocol/src/main/kotlin/…      # data classes @Serializable espejo de @j3pf/rp-protocol
├── protocol/src/test/resources/fixtures/   # copiados de rp-relay/packages/protocol/fixtures (script sync-fixtures.sh)
└── gradle/libs.versions.toml
```

## 9. Tests
| Nivel | Qué |
|---|---|
| Unit | `ChatReducer` (todos los eventos, deltas fuera de orden, reconexión con `sinceSeq`), `PushCrypto` (vector de prueba compartido con el relay), parsing de todos los fixtures |
| Integración | `RelayConnection` + `PromptsRepository` contra MockWebServer (WS y HTTP), incluido 409 |
| UI | Compose tests: lista de agentes, hoja de permisos, compositor con adjuntos |
| Notificaciones | Robolectric: construir notificación de cada tipo y verificar acciones; receiver llamando a la API |
| E2E manual | `docs/QA.md`: app cerrada + pantalla bloqueada → aprobar desde notificación; responder en terminal → notificación desaparece; modo avión → reintento |

## 10. Build y distribución
- `google-services.json` **no** se versiona: CI lo inyecta desde un secreto (`GOOGLE_SERVICES_JSON` base64) y en local
  se coloca a mano en `app/`. Debe pertenecer al mismo proyecto Firebase cuyo service account usa el relay.
- Firma: keystore propio; secretos en GitHub Actions; `./gradlew assembleRelease` → APK firmado en GitHub Releases
  (sideload). Google Play (pista interna) opcional.
- R8/proguard con reglas para kotlinx.serialization.
- Versionado: `versionName` = semver, `versionCode` = conteo de commits.

## 11. Tareas

| # | Tarea | Fase | Est. |
|---|---|---|---|
| A0 | **Spike S2**: app mínima con (a) servicio `remoteMessaging` + WS y (b) FCM data high‑priority; con app cerrada y pantalla apagada 1 h → notificación con acción → POST; medir batería del canal directo | 0 | 1,5 d |
| A1 | Proyecto base: Gradle, Hilt, tema, navegación, CI | 1 | 1 d |
| A2 | Módulo `protocol` + tests de fixtures | 1 | 1 d |
| A3 | Onboarding + QR + almacenamiento seguro + pairing | 1 | 1,5 d |
| A4 | `RelayConnection` + `AgentsRepository` + pantalla Agentes | 1 | 2 d |
| A5 | Chat: reductor, render (markdown, tool cards, streaming), compositor de texto, abort | 1 | 3 d |
| A6 | Prompts: bandeja, hoja de respuesta, tarjetas en línea | 2 | 2 d |
| A7 | Push: servicio, cripto, canales, acciones, RemoteInput, receiver, cancel | 2 | 2,5 d |
| A8 | Adjuntos (SAF, Photo Picker, cámara, subida con progreso) | 3 | 1,5 d |
| A9 | Ajustes (dispositivos, máquinas, prefs de notificación, prueba) | 3 | 1,5 d |
| A10 | Nuevo agente vía rp-host (PC, carpeta, lanzador gentle-shell/pi, backend herdr/tmux) | 3 | 1,5 d |
| A12 | Separación por PC: selector, agrupación, edición de PC (nombre/contexto/color), grupos de canales por PC, prefs por PC | 1–2 | 2 d |
| A13 | Cuestionarios (`questionnaire`), chip/aviso modo AUTO, método de entrega con biometría, chips herdr/gentle | 2 | 2 d |
| A14 | `DirectPushService` + deduplicación + arranque al encender + sabores de build `direct`/`fcm` | 2 | 2 d |
| A11 | Release firmado + docs | 3 | 0,5 d |

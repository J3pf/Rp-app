# Plan — App Android (`rp-app`)

Contexto: [ARQUITECTURA.md](ARQUITECTURA.md). Contrato: `rp-relay/docs/PROTOCOLO.md`.

## 1. Objetivos de la app

1. Emparejarse con mi relay (QR).
2. Ver **todos** los agentes (online/offline), agrupados por máquina, con estado y preguntas pendientes.
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
| Push | Firebase Cloud Messaging (`firebase-messaging`, BoM) |
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
  ├─ Agentes:   lista agrupada por máquina
  │             tarjeta: nombre sesión · carpeta (último segmento) · rama git · modelo
  │             chip de estado (Trabajando ⏳ / Esperando tu respuesta 🔔 / Inactivo / Desconectado hace 3 h)
  │             badge de prompts abiertos · vista previa del último mensaje
  │             FAB "Nuevo agente" (fase 3, si hay rp-host)
  │
  ├─ Chat del agente:
  │    cabecera: nombre, estado, modelo, botón ⏹ abort (si trabajando), menú (info, olvidar)
  │    lista: burbujas usuario (con "📱" si vino del móvil) / asistente (markdown, streaming)
  │           bloques plegables: razonamiento, llamada de herramienta (nombre + args resumidos;
  │           bash muestra comando; edit muestra diff), resultado (truncado + "ver más")
  │           tarjetas de prompt en línea (mismo componente que la hoja de respuesta)
  │           banner "Esperando en la terminal (diálogo no remoto)" cuando llega terminal_wait
  │    compositor: texto multilínea, 📎 adjuntar (archivo / foto / cámara), selector de entrega
  │                cuando el agente trabaja: [Encolar (follow‑up)] [Interrumpir y dirigir (steer)]
  │
  ├─ Pendientes: todos los prompts abiertos de todos los agentes, ordenados por peligro y antigüedad;
  │              tocar → hoja de respuesta
  │
  ├─ Hoja de respuesta (BottomSheet):
  │    permission: herramienta + comando/args completos en monoespaciado, nivel de peligro,
  │                botones grandes [Permitir] [Permitir siempre (sesión)] [Denegar] [Denegar con motivo]
  │    question:   pregunta, opciones como chips/botones, campo de texto libre si allowFreeText
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
- WebSocket a `/ws/app` **sólo mientras la app está en primer plano** (`ProcessLifecycleOwner`: ON_START conecta,
  ON_STOP cierra tras 30 s de gracia). En segundo plano todo llega por FCM. Sin servicio foreground.
- Reconexión con backoff (1 → 30 s) + `ConnectivityManager.NetworkCallback` para reintentar al recuperar red.
- Expone `SharedFlow<ServerMessage>` y `StateFlow<ConnState>`.
- Peticiones con `id` → `CompletableDeferred` por id, timeout 10 s.

### 4.2 Repositorios
- `AgentsRepository`: `agents.update` → Room `agents`; `Flow<List<AgentWithCounts>>`.
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

### 5.1 Canales (creados al iniciar)
| Canal | Importancia | Uso |
|---|---|---|
| `approvals` | HIGH (heads‑up, sonido, vibración) | `category=permission` |
| `questions` | HIGH | `question` y `dialog` |
| `terminal` | DEFAULT | `terminal_wait` |
| `finished` | DEFAULT | agente terminó (`settled`) |
| `errors` | DEFAULT | agente con error / desconectado con prompts abiertos |

Android 13+: pedir `POST_NOTIFICATIONS` en el onboarding (sin él no hay notificaciones).

### 5.2 Recepción (`RpMessagingService : FirebaseMessagingService`)
1. `onMessageReceived` → descifra `c` con AES‑256‑GCM (`pushKey` del Keystore, AAD `"rp1"`). Falla → ignorar.
2. `k=prompt`: construye notificación (id = hash de `promptId`) con:
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
- FCM *data* high priority permite ejecutar `onMessageReceived` aunque la app esté en Doze/cerrada (no force‑stopped).
- Pantalla de ajustes con estado: permiso de notificaciones, canal `approvals` no silenciado, exclusión de
  optimización de batería (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` sólo como guía, no obligatorio), y
  botón "Enviar notificación de prueba" (llama a un endpoint del relay que hace push al propio dispositivo).

## 6. Emparejamiento

1. En el servidor: `rp-relay pair device` → QR con `rp://pair?u=https://relay…&c=ABCD2345`.
2. App: escanea (o pega URL+código) → obtiene token FCM → `POST /v1/pair/device {code, name: Build.MODEL, fcmToken}`
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
| A0 | **Spike S2**: app mínima + FCM data high‑priority con app cerrada → notificación con acción → POST a un endpoint de prueba | 0 | 1 d |
| A1 | Proyecto base: Gradle, Hilt, tema, navegación, CI | 1 | 1 d |
| A2 | Módulo `protocol` + tests de fixtures | 1 | 1 d |
| A3 | Onboarding + QR + almacenamiento seguro + pairing | 1 | 1,5 d |
| A4 | `RelayConnection` + `AgentsRepository` + pantalla Agentes | 1 | 2 d |
| A5 | Chat: reductor, render (markdown, tool cards, streaming), compositor de texto, abort | 1 | 3 d |
| A6 | Prompts: bandeja, hoja de respuesta, tarjetas en línea | 2 | 2 d |
| A7 | Push: servicio, cripto, canales, acciones, RemoteInput, receiver, cancel | 2 | 2,5 d |
| A8 | Adjuntos (SAF, Photo Picker, cámara, subida con progreso) | 3 | 1,5 d |
| A9 | Ajustes (dispositivos, máquinas, prefs de notificación, prueba) | 3 | 1,5 d |
| A10 | Nuevo agente vía rp-host (selector de carpeta) | 3 | 1,5 d |
| A11 | Release firmado + docs | 3 | 0,5 d |

# rp-app

App Android (Kotlin + Jetpack Compose) para ver y controlar todos tus agentes [Pi](https://github.com/earendil-works/pi) a través
de tu relay **RP**: chat en vivo, adjuntos, **notificaciones de preguntas/autorizaciones con respuesta directa desde la notificación**
y **Agent Mesh** (topología animada y diagrama de comunicaciones en tiempo real).

> Estado: **implementado** (ver «Compilar» abajo). El diseño está en `docs/`.

| Documento | Contenido |
|---|---|
| [docs/INSTALACION.md](docs/INSTALACION.md) | Guía de instalación del APK y emparejamiento con el Relay |
| [docs/PLAN-APP.md](docs/PLAN-APP.md) | Plan detallado de la app |
| [docs/QA.md](docs/QA.md) | Pruebas manuales E2E (notificaciones con app cerrada, modo avión, topología Agent Mesh, etc.) |
| [docs/RESILIENCIA.md](docs/RESILIENCIA.md) | Escalabilidad, resiliencia e independencia total (push sin Google, entrega interactiva sin herdr, Linux/Windows) |
| [docs/COMPATIBILIDAD.md](docs/COMPATIBILIDAD.md) | herdr, gentle-ai, gentle-shell/gentle-pi y separación por PC: hechos verificados y matriz requisito × entorno |
| [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) | Visión global (copia; canónica en `rp-relay`) |
| [`rp-relay/docs/PROTOCOLO.md`](https://github.com/j3pf/rp-relay/blob/main/docs/PROTOCOLO.md) | Contrato de red |

## Características principales

- **Consolidación v0.3.3 (Persistencia Room v3, Conversaciones entre Pares y Estado de Chat)**:
  - Migración Room 2→3 verificada (`MIGRATION_2_3`): persistencia de tema, estado de TODOs y tarea activa por agente sin pérdida de datos.
  - Conversaciones entre agentes en Mesh: apertura directa de historiales de conversación entre pares desde la hoja de nodo (`onOpenConversation`) sin requerir arista previa, y preservación del cursor de paginación (`nextBefore`).
  - Filtrado inteligente de grupos en Mesh: recálculo automático de chips de grupos y selección del grupo más activo según la máquina seleccionada.
  - Robustez en Chat y Subagentes: limpieza de subagentes/TODOs ante cambios de época, liquidación a `done`/`aborted` al finalizar el turno y prevención de duplicados visuales de tarjetas de subagentes.
  - Eliminación de condiciones de carrera en suscripciones websocket de Mesh (`lastSubscribedMachineId`).
- **Correcciones v0.3.2 (Decodificación Tolerante y Números Decimales)**:
  - Serializador `LenientLongSerializer`: acepta enteros JSON y números decimales con coma flotante (redondeo half-up a `Long`) en todos los campos que el relay define como `Type.Number` (ej. marcas de tiempo y métricas de subagentes como `startedAt: 47725510.340108`).
  - Decodificación tolerante en `MeshSnapshot`: deserializadores de lista tolerantes para nodos, aristas, grupos y subagentes que aíslan fallos en elementos corruptos y mantienen visibles los demás nodos válidos sin romper la pantalla.
- **Correcciones v0.3.1 (Resiliencia de Red y Filtros)**:
  - Soporte de contratos de respuesta en `RelayApi` (desenvoltorio de objetos para dispositivos, máquinas y prompts) y filtrado de dispositivos revocados.
  - Corrección de serialización en `MeshSubscribe` garantizando envío de `{"machineId":null}` para visualización completa de la red en relays 0.5.0+.
  - Resiliencia en Mesh: detección de errores WS, timeout de 10 s con fallback HTTP (`GET /v1/mesh`), estado explícito de error con botón de reintento y propiedad de suscripción protegida contra desecho de vistas.
  - Eliminación de la fila redundante de chips de contexto en la pantalla de Agentes, unificando la selección en el desplegable superior `MachineHeroDropdown`.
- **UI Refresh & Experiencia Visual Moderna (v0.3.0)**:
  - Sistema de diseño unificado con tokens centralizados (`RpSpacing`, `RpShapes`, `RpGlows`, `RpGradients`, `RpMotion`) derivados de `MaterialTheme.colorScheme`.
  - Preservación íntegra de paletas temáticas (`Gentle-Cute`, `Gentle-Sexy`, `Omarchy / Gentle Shell`, `Material You / Sistema`) con re-skinning instantáneo desde Ajustes.
  - Chat anclado al fondo (`reverseLayout`): visualización inmediata del mensaje más reciente y del fondo de respuestas en streaming sin scroll manual.
  - Botón flotante animado «Más recientes (N)» que respeta la lectura del historial cuando el usuario sube y muestra el contador de nuevos mensajes.
  - Tarjetas de agentes rediseñadas con información de actividad de un vistazo: chips de estado envueltos (sin scroll horizontal), tarea activa en curso, progreso de tareas/TODOs, tiempo relativo de actividad y vista previa compacta.
  - Orden de agentes optimizado: decisiones/prompts abiertos primero, agentes trabajando en segundo lugar, y por última actividad reciente.
  - Micro-interacciones y movimiento con física de resortes (`Spring`): transiciones entre pantallas, pulso vivo en agentes trabajando, feedback táctil al pulsar tarjetas y botones, y animaciones de lista con `animateItem()`.
  - Cumplimiento estricto de accesibilidad y reducción de movimiento (`ANIMATOR_DURATION_SCALE=0` desactiva animaciones infinitas) y targets táctiles ≥ 48 dp.
  - Suite de pruebas de regresión visual con Roborazzi 1.76.0 (48 capturas de referencia que cubren 8 pantallas en los 3 temas y modos claro/oscuro).
- **Bandeja de Agentes y Chat en vivo**: Historial de mensajes, streaming en tiempo real, seguimiento de herramientas, diffs y soporte interactivo con biometría.
- **Notificaciones interactivas**: Autorización y respuesta a preguntas directamente desde la notificación sin abrir la app, con soporte offline (WorkManager) y deduplicación.
- **Agent Mesh (Topología animada)**:
  - Diagrama visual en Canvas de alta velocidad (60 fps) con disposición radial-jerárquica: orquestadores al centro, subordinados en anillos y subagentes orbitando como satélites.
  - Animación de partículas de comunicación en tiempo real (`mesh.comm`) y globos de diálogo junto a las aristas.
  - Aristas activas con brillo dinámico y aristas discontinuas para solicitudes pendientes (`pendingAsk`).
  - Anillos de progreso de TODOs por agente y pulsos animados según su estado de ejecución.
  - Gestos táctiles fluidos: zoom con pellizco, desplazamiento panorámico y doble toque para reiniciar vista.
  - Hojas de detalle completas (`ModalBottomSheet`) por agente (TODOs, subagentes con tokens/herramientas, conversaciones) y entre agentes con paginación de mensajes (`nextBefore`).
  - Filtros y persistencia por máquina y grupo, barra "En vivo" colapsable y vista de lista accesible para lectores de pantalla.

## Referencias investigadas

- Pi (`earendil-works/pi`, v1.2.0): `packages/coding-agent/docs/{extensions,packages,rpc,rpc-extension-ui,json,security,sessions}.md`,
  `src/core/extensions/{types,runner}.ts`, `src/modes/{interactive/interactive-mode,rpc/rpc-mode}.ts`,
  ejemplos `permission-gate.ts`, `question.ts`, `notify.ts`, `send-user-message.ts`.
- [Remote Pi](https://github.com/jacobaraujo7/remote_pi) (Flutter + relay Rust + extensión Pi): emparejamiento QR, aprobaciones vía
  `@eko24ive/pi-ask`; **sin push real** — es la principal carencia que este proyecto cubre.
- [Happy](https://github.com/slopus/happy) / Happier (happier-dev): wrapper de CLI + servidor Fastify/Socket.IO con cifrado E2E.
- [Moshi](https://getmoshi.app/docs): terminal móvil + hook en el host que genera bandeja de aprobaciones y push.
- [gentle-shell / gentle-pi](https://github.com/Gentleman-Programming/gentle-shell) 4.1.0, [gentle-ai](https://github.com/Gentleman-Programming/gentle-ai), [herdr](https://github.com/herdrdev/herdr) 0.9.3: entorno del usuario, ver `docs/COMPATIBILIDAD.md`.

## Descarga e Instalación (Release Oficial)

Puedes instalar la aplicación directamente en tu dispositivo sin necesidad de compilar:

1. **Descargar el APK:**
   - 📦 **[RP App v0.3.3 — GitHub Releases](https://github.com/J3pf/Rp-app/releases/tag/v0.3.3)**
   - Versión Release (canal directo sin Google): **[rp-v0.3.3-direct.apk](https://github.com/J3pf/Rp-app/releases/download/v0.3.3/rp-v0.3.3-direct.apk)** (30 MB).
   - Versión Release con FCM (Google Cloud Messaging opcional): **[rp-v0.3.3-fcm.apk](https://github.com/J3pf/Rp-app/releases/download/v0.3.3/rp-v0.3.3-fcm.apk)** (30 MB).
   - Versión Debug (canal directo): **[rp-direct-debug.apk](https://github.com/J3pf/Rp-app/releases/download/v0.3.3/rp-direct-debug.apk)** (108 MB).
2. **Instalar en el móvil:**
   - **En el teléfono:** Abre el archivo descargado y concede permiso de «Instalar aplicaciones desconocidas» para tu navegador o gestor de archivos.
   - **Vía USB con ADB:** Conecta el teléfono con depuración USB y ejecuta:
     ```bash
     adb install -r rp-v0.3.3-direct.apk
     ```
3. **Vincular con tu Relay:**
   - En el servidor Relay, ejecuta:
     ```bash
     rp-relay pair device
     ```
   - Abre la app **RP**, pulsa **Escanear código QR** y apunta al terminal (o escribe la URL y el código de 8 caracteres manualmente).
4. **Guía completa y Troubleshooting:**
   - Consulta **[docs/INSTALACION.md](docs/INSTALACION.md)** para la guía paso a paso y la resolución detallada de problemas (permisos en Android 13+, optimización de batería, certificados TLS/HTTP en red local y códigos expirados).

## Compilar desde código fuente

**Rápido (APK para tu teléfono):** `scripts/build-apk.sh` — instala el SDK si falta, compila y deja el APK en `dist/rp-direct-debug.apk`
(`--install` lo instala por adb, `--fcm`, `--test`, `--release`). Detalle manual abajo.

Requisitos: JDK 17, Android SDK 36 (`ANDROID_HOME`), acceso a Google Maven y Maven Central.

```bash
./gradlew :protocol:test                       # protocolo, reductor del chat y cripto de push (JVM puro)
./gradlew :app:testDirectDebugUnitTest         # Robolectric, MockWebServer y tests de Compose
./gradlew :app:assembleDirectDebug             # APK sin Google (canal directo propio)
./gradlew :app:assembleFcmDebug                # APK con FCM opcional (necesita app/google-services.json)
```

- **Sabores**: `direct` (sin Google: servicio en primer plano `remoteMessaging` con WebSocket al relay) y `fcm`
  (añade Firebase Cloud Messaging; los dos canales conviven y se deduplican por `promptId`).
- `app/google-services.json` **no** se versiona: cópialo a mano o inyéralo en CI (`GOOGLE_SERVICES_JSON`, base64).
  Sin él, el sabor `fcm` compila pero FCM queda inerte.
- Release firmado: crea `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) o usa
  `.github/workflows/release.yml` (secretos `RELEASE_KEYSTORE_B64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
  `RELEASE_KEY_PASSWORD`). `versionCode` = nº de commits; `versionName` = semver (`app/build.gradle.kts`).
- Contrato de red: `scripts/sync-fixtures.sh [../rp-relay]` copia los fixtures de `rp-relay` a
  `protocol/src/test/resources/fixtures/relay/` y los tests los decodifican con los tipos Kotlin.

## Estructura

```
protocol/   Kotlin puro: tipos @Serializable del protocolo v1, PushCrypto (AES-GCM), ULID y ChatReducer (+ tests)
app/        Compose + Hilt + Room: data/{net,db,repo,secure}, push/, ui/{agents,chat,pending,promptsheet,settings,…}
```

# rp-app

App Android (Kotlin + Jetpack Compose) para ver y controlar todos tus agentes [Pi](https://github.com/earendil-works/pi) a través
de tu relay **RP**: chat en vivo, adjuntos y **notificaciones de preguntas/autorizaciones con respuesta directa desde la notificación**.

> Estado: **implementado** (ver «Compilar» abajo). El diseño está en `docs/`.

| Documento | Contenido |
|---|---|
| [docs/INSTALACION.md](docs/INSTALACION.md) | Guía de instalación del APK y emparejamiento con el Relay |
| [docs/PLAN-APP.md](docs/PLAN-APP.md) | Plan detallado de la app |
| [docs/QA.md](docs/QA.md) | Pruebas manuales E2E (notificaciones con la app cerrada, modo avión, etc.) |
| [docs/RESILIENCIA.md](docs/RESILIENCIA.md) | Escalabilidad, resiliencia e independencia total (push sin Google, entrega interactiva sin herdr, Linux/Windows) |
| [docs/COMPATIBILIDAD.md](docs/COMPATIBILIDAD.md) | herdr, gentle-ai, gentle-shell/gentle-pi y separación por PC: hechos verificados y matriz requisito × entorno |
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
- [gentle-shell / gentle-pi](https://github.com/Gentleman-Programming/gentle-shell) 4.1.0, [gentle-ai](https://github.com/Gentleman-Programming/gentle-ai), [herdr](https://github.com/herdrdev/herdr) 0.9.3: entorno del usuario, ver `docs/COMPATIBILIDAD.md`.

## Descarga e Instalación (Release Oficial)

Puedes instalar la aplicación directamente en tu dispositivo sin necesidad de compilar:

1. **Descargar el APK:**
   - 📦 **[RP App v0.1.0 — GitHub Releases](https://github.com/J3pf/Rp-app/releases/tag/v0.1.0)**
   - Archivo directo: **[rp-direct-debug.apk](https://github.com/J3pf/Rp-app/releases/download/v0.1.0/rp-direct-debug.apk)** (96 MB).
2. **Instalar en el móvil:**
   - **En el teléfono:** Abre el archivo descargado y concede permiso de «Instalar aplicaciones desconocidas» para tu navegador o gestor de archivos.
   - **Vía USB con ADB:** Conecta el teléfono con depuración USB y ejecuta:
     ```bash
     adb install -r rp-direct-debug.apk
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

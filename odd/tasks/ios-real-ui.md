# Feature: la app real en iOS (Fase G)
Branch: `feat/ios-real-ui` (base `475233b`).
Estado: **abierta. T1 delegada; T2 bloquea el resto: sin frame dibujado nada de lo demás es observable.**

## Specs

- **S1 — El iPhone muestra la app, no un marcador.** Pedido literal del humano: «ya no crashea pero queda en negro»,
  y ante la elección de alcance eligió «Cablear la app real en iOS». Hoy `RemotePiApp()` compone un `Text`
  con el texto `RemotePi Shared UI`; eso no es la app.
- **S2 — Las mismas pantallas que ya usa en Android.** Alcance aceptado por el humano:
  «`RemotePiApp` monta agentes, chat, mesh, ajustes y onboarding», y «cliente del relay para iOS.
  almacenamiento con Keychain». Cinco pantallas, no una muestra.
- **S3 — Una sola fuente de verdad de la UI.** Las pantallas viven en `shared-ui/commonMain` y se reusan; prohibido
  copiar una pantalla a `iosMain` o reescribirla "para iOS". Si una pantalla no compila en iOS, se arregla la
  pantalla compartida, no se bifurca.
- **S4 — La verificación es en el iPhone físico, no en el simulador.** Estándar repetido por el humano en toda la
  sesión: «no declares algo terminado sin el resultado real». Un job verde no sustituye abrir la app en su teléfono.
- **S5 — Nada de secretos de sesión fuera del Keychain en iOS.** El `machineToken` va al Keychain; ni `UserDefaults`
  ni archivos planos, igual que en Android va al Keystore.
- **S6 — El negro se explica con evidencia antes de construir encima.** El tema es claro (`background = 0xFFFFF7F9`),
  así que un negro significa que no se dibuja ningún frame. No se escribe UI nueva hasta saber si el fallo es del
  dispositivo o de la composición.

## Tasks

| ID | Specs | Ruta | Commit |
| --- | --- | --- | --- |
| T1 | S6 | `.github/workflows/ios.yml`: capturar la pantalla del simulador, exigir que no sea uniforme y publicar el PNG | pendiente |
| T2 | S6 | diagnóstico y arreglo del frame negro (dispositivo vs composición) | pendiente |
| T3 | S2, S3 | estado compartido en `shared-ui/commonMain` sobre `relay-client` (agentes, chat, mesh) con tests | pendiente |
| T4 | S2, S5 | sesión y ajustes en iOS: `expect/actual` de almacenamiento con Keychain | pendiente |
| T5 | S1, S2, S3 | `RemotePiApp` monta las cinco pantallas con navegación | pendiente |
| T6 | S2 | historial de chat en iOS (sin Room: caché propia) | pendiente |
| T7 | S4 | prueba en el iPhone físico de cada pantalla, una por una | pendiente |

## Log

- **L1** — «The conversation history before this point... The user asked (in Spanish) to set up a job...» no aplica a esta
  funcionalidad. Petición que la abre, literal: «Te conecte mi iphone 12 mini, por que instale la app y crashea de una
  al abrirla», seguida de «ya no crashea pero queda en negro» y de la elección de alcance «Cablear la app real en iOS».
- **L2** — Evidencia del estado actual (2026-10-30): build 243 instalado en el iPhone 12 mini (UDID
  `3895D1AB-5725-59AC-B27B-D077DF12C6AC`); `devicectl device process launch --console` no reporta excepción y el
  proceso sigue vivo; la pantalla queda negra. `shared-ui/commonMain/.../RemotePiApp.kt` compone un `Text`
  de marcador. `relay-client` ya es KMP con `iosArm64`/`iosSimulatorArm64` (24 archivos en `commonMain`).
  Solo Android: `app/src/main/java/dev/j3pf/rp/data/` (Room, OkHttp, 9 repositorios, `SecretStore`).

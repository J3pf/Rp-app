# Feature: la app real en iOS (Fase G)
Branch: `feat/ios-real-ui` (base `475233b`).
Estado: **abierta. T1 hecho. T2 en curso: el simulador dibuja en Debug y en Release; el negro es del dispositivo o del binario iosArm64.**

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
| T1 | S6 | `.github/workflows/ios.yml`: capturar la pantalla del simulador, exigir que no sea uniforme y publicar el PNG | `db3cf74`, `dcb1752` (verde: run 41013318167) |
| T2 | S6 | diagnóstico y arreglo del frame negro (dispositivo vs composición) | en curso: Release descartado, ver L4 |
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
- **L3** — T1 verificado (run 41013318167, verde): `captura: 1206x2622, 4 canales`, `luminancia media: 250.63`,
  `desviación típica: 32.79`, «la pantalla tiene contenido». La captura se mira y muestra fondo casi blanco, barra de
  estado, el texto `RemotePi Shared UI` y el indicador de inicio. El primer intento (run 41011414230) falló por un
  defecto de la sonda, no de la app: `sips` produce BMP con compresión `BI_BITFIELDS` (3) y el lector la rechazaba.
  La sonda vive ahora en `.github/scripts/pantalla-no-uniforme.py` y decodifica el PNG con `zlib`; probada contra la
  captura real (pasa, σ=31.67), contra una imagen negra y una blanca (falla, σ=0.00) y contra un archivo que no es
  PNG (exit 2).
- **L4** — T2, primer experimento (run 41018205584, verde): el mismo job compila además en **Release** para el
  simulador, lanza, captura y publica `ios-launch-screen-release`. Resultado: `Release: el proceso 83116 sigue vivo
  10 s después`, `luminancia media: 250.65`, `desviación típica: 32.75` — prácticamente idéntico a Debug
  (250.66 / 32.69). La captura de Release muestra lo mismo: texto centrado sobre blanco. **Queda descartado el
  defecto de configuración**: Release no dibuja distinto que Debug en el simulador. Quedan dos sospechosos, y este
  experimento no los separa: (a) la GPU o la versión de iOS del iPhone 12 mini, (b) el binario `iosArm64` de Release
  — el simulador es `iosSimulatorArm64`, otro target de Kotlin/Native. El experimento que los separa pide el
  dispositivo: build de Debug en el teléfono, o `os_log` del arranque (Skiko registra el dispositivo Metal y sus
  errores). Ninguno de los dos se hace sin permiso del humano.
  Nota de camino: el build de Release para el simulador fallaba con `Unknown iOS simulator arch: 'x86_64'` porque
  `EXCLUDED_ARCHS[sdk=iphonesimulator*]` solo existía en la configuración Debug **del proyecto**; ahora está en las
  dos (`b71e864`), y el target no la repite.


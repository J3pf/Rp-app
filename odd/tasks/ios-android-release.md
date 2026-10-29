# Feature: compilar y publicar para iOS y Android (Fase F)
Branch: `feat/ios-release` (base `a8d7c91`).
Estado: **CI de iOS y workflow de TestFlight escritos; falta configurar secretos y autorizar el release.**

## Specs

- **S1 — iOS y Android se compilan de verdad, no "deberían".** Pedido literal del humano:
  «compilar y publicar para ios tambien y obviamente para android». Un job verde tiene que significar
  que compiló; un paso que se salta en silencio convierte el verde en mentira.
- **S2 — El CI de macOS fija la versión de Xcode y falla en voz alta si no está.** Sin
  `continue-on-error` y sin `|| true` en las comprobaciones: si `Xcode_26.5.app` no existe, o la versión
  instalada no es `26.5`, el job se cae con `::error::` y `exit 1`.
- **S3 — La prueba de iOS llega hasta instalar y lanzar.** Compilar no basta: el job instala el `.app`
  en un simulador arrancado y exige que `simctl launch` devuelva un pid para `dev.j3pf.rp.iosApp`.
- **S4 — Publicación a TestFlight con secretos documentados, nunca credenciales en el repo.** Si falta
  cualquiera de los ocho secretos, el job falla **antes** de archivar.
- **S5 — Los mínimos de versión se expresan como releases de GitHub, jamás como versiones de npm.**
  Relay `>= 0.6.0`, plugin `>= 0.3.0`; `@j3pf/rp-relay` y `@j3pf/pi-rp` dan 404 en npm.
- **S6 — Nada de productos en el repo.** `build/`, `DerivedData/`, `*.ipa`, `*.xcarchive`, `*.app` y
  `xcuserdata/` quedan ignorados.

## Tasks

| ID | Specs | Ruta | Commit |
| --- | --- | --- | --- |
| T1 | S2, S3, S5 | `.github/workflows/ios.yml` (Xcode 26.5 fijado, sin `continue-on-error`) | pendiente |
| T2 | S4 | `.github/workflows/testflight.yml` + `iosApp/ExportOptions.plist` | pendiente |
| T3 | S6 | `.gitignore` cubre los productos de Xcode y Gradle | pendiente |
| T4 | S5 | `docs/RELEASE_NOTES_v0.6.0.md` (borrador, mínimos como releases) | pendiente |
| T5 | — | Configurar secretos y autorizar `versionName` 0.6.0 | **humano** |

## Estado observado de iOS (2026-10-29)

Máquina: **MacBook Pro 16,1 (Intel, Core i9-9880H)**. Xcode **26.5 / 17F42**, runtime
`iOS 26.5 (26.5 - 23F77)`; el simulador `iPhone 17 Pro` arranca correctamente.

| Prueba | Comando | Resultado observado |
| --- | --- | --- |
| Framework simulador (arm64) | `./gradlew :shared-ui:linkDebugFrameworkIosSimulatorArm64` | `BUILD SUCCESSFUL in 4m 9s` |
| Framework dispositivo (arm64) | `./gradlew :shared-ui:linkDebugFrameworkIosArm64` | `BUILD SUCCESSFUL in 14m 37s`; `lipo -info` → `architecture: arm64` |
| App para el simulador, arquitectura nativa de este host | `xcodebuild … -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build` | `error: Unknown iOS simulator arch: 'x86_64'` (×3), `BUILD FAILED in 6s`, exit 65 |
| **App completa, arm64 forzado** | `xcodebuild … -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' ARCHS=arm64 ONLY_ACTIVE_ARCH=NO CODE_SIGNING_ALLOWED=NO build` | `** BUILD SUCCEEDED **`, exit 0; `.app` en `/tmp/rp-dd-arm/Build/Products/Debug-iphonesimulator/iosApp.app`; `lipo -info` del binario → `architecture: arm64` |

Causa, con evidencia: esta máquina solo tiene simulador **x86_64** (`ARCHS = x86_64`,
`NATIVE_ARCH = x86_64` con `ONLY_ACTIVE_ARCH = YES`), y los tres módulos declaran solo
`iosArm64` + `iosSimulatorArm64`. Añadir `iosX64` se probó y **es imposible** con el stack actual:

```
e: ❌ KMP Dependencies Resolution Failure
Couldn't resolve dependency 'org.jetbrains.compose.runtime:runtime:1.12.2' in 'commonMain' for all target platforms.
The dependency should target platforms: [android, iosArm64, iosSimulatorArm64, iosX64, jvm]
Unresolved platforms: [iosX64]
```

Igual para `org.jetbrains.compose.foundation:foundation:1.12.2`, `org.jetbrains.compose.ui:ui:1.12.2` y
`com.mikepenz:multiplatform-markdown-renderer-m3:0.46.0`. Compose Multiplatform ya no publica x86_64, así
que el cambio de targets se revirtió y ningún `build.gradle.kts` quedó modificado.

**Conclusión honesta**: iOS **compila para dispositivo y para simulador de Apple silicon**; **no puede
EJECUTARSE en este host Intel** porque el proyecto no tiene slice `iosX64` y un binario arm64 no corre
sobre un simulador x86_64. Las pruebas que lo demuestran:

- host: `uname -m` → `x86_64`; `Intel(R) Core(TM) i9-9880H`
- `shared-ui/build.gradle.kts:38-39`: solo `iosArm64()` e `iosSimulatorArm64()`; no hay `iosX64`
- `lipo -info …/iosSimulatorArm64/debugFramework/SharedUi.framework/SharedUi` → `Non-fat file … architecture: arm64`

**`iosX64` NO se añade en esta entrega, por decisión explícita**, y el motivo queda escrito para que
nadie lo "arregle" después: existiría solo para correr un simulador en una máquina Intel de desarrollo;
añade una tercera compilación Kotlin/Native a cada build y a cada corrida de CI; y Apple está retirando
el soporte de simulador en Intel. La prueba real de instalar y lanzar corresponde al runner arm64 del
workflow `iOS`, que es donde puede ejecutarse.

## Intentos de CI de iOS (observados, uno por uno)

| Run | Qué pasó | Consecuencia |
| --- | --- | --- |
| 40809613829 | `[ ! -d /Applications/Xcode_26.5.app ]` falló y el job cayó con `::error::` en 10 s | El pin exacto funcionó, pero **mi lectura de la lista fue errónea**: la cadena `Xcode_26.5.app` que vi en el log venía de mi propio `XCODE_APP` impreso, no de un listado del runner |
| 40810493893 | Solo coincidió `/Applications/Xcode.app` → `Xcode 16.4`, y el chequeo de versión falló en voz alta | El glob no encontraba nada de la 26: la imagen no la tiene |
| 40810867330 | `Process completed with exit code 134` (SIGABRT) | `sort -V` no existe en el `sort` de macOS; el paso murió antes de decidir |
| 40811418435 | El listado real de la imagen llega a `Xcode_26.3.0.app`; el mínimo seguía en `26.5` | El mínimo tenía que bajar a la major 26 |

Lo aprendido, en una línea: **la imagen `macos-15-arm64` (20260907) trae Xcode 16.0–16.4 y 26.0–26.3, nunca 26.5.**
Fijar una *minor* exacta contra un pool que rota produce fallos de imagen, no de proyecto.

Solución implementada: `.github/scripts/select-xcode.py` elige el Xcode instalado más nuevo que cumpla
`XCODE_MIN_VERSION` (`26.0`), ignora bundles rotos y sale con 1 sin imprimir ruta cuando ninguno cumple.
Probado en local: con `26.0` imprime `/Applications/Xcode.app|26.5`; con `99` sale 1; con `abc` sale 2.

## Node de `.gitignore` (defecto encontrado y corregido)

`.gitignore` ignoraba `build/` y `local.properties`, pero no `DerivedData/`, `*.ipa`, `*.xcarchive`,
`*.app` ni `xcuserdata/`. Abrir el proyecto en Xcode habría dejado estado de usuario listo para
commitear.

## Log

- L1: «compilar y publicar para ios tambien y obviamente para android».
- L2: el orquestador fija los mínimos verificados contra etiquetas: relay `>= 0.6.0`, plugin `>= 0.3.0`, ambos como release de GitHub; ni relay ni plugin existen en npm (404).
- L3: la licencia de Xcode se acepta y el runtime de iOS se instala; el esquema real del proyecto es `iosApp`.
- L4: el build de la app falla en esta máquina por arquitectura: Intel x86_64 contra módulos que solo declaran arm64; `iosX64` es imposible con Compose Multiplatform 1.12.2. Decisión del humano primero y reversión después.

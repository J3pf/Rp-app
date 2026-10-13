#!/usr/bin/env bash
# Compila el APK de RP listo para instalar en tu teléfono (Linux / macOS / WSL).
#
# Uso:
#   scripts/build-apk.sh                  # APK debug sabor "direct" (sin Google), firmado con la clave de depuración
#   scripts/build-apk.sh --fcm            # sabor "fcm" (necesita app/google-services.json para que FCM funcione)
#   scripts/build-apk.sh --install        # además lo instala por adb (teléfono con depuración USB o adb inalámbrico)
#   scripts/build-apk.sh --test           # ejecuta antes los tests unitarios
#   scripts/build-apk.sh --release        # APK de release (requiere keystore.properties, ver README)
#
# Instala por su cuenta, si faltan: el SDK de Android (cmdline-tools, plataforma 36, build-tools, platform-tools).
# Necesita JDK 17+ y red hacia dl.google.com / maven.google.com / repo.maven.apache.org.
set -euo pipefail
cd "$(dirname "$0")/.."

FLAVOR=direct; BUILD=Debug; INSTALL=0; TEST=0
for a in "$@"; do
  case "$a" in
    --fcm) FLAVOR=fcm ;;
    --direct) FLAVOR=direct ;;
    --release) BUILD=Release ;;
    --install) INSTALL=1 ;;
    --test) TEST=1 ;;
    -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
    *) echo "Opción desconocida: $a" >&2; exit 2 ;;
  esac
done

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# ── 1) JDK ────────────────────────────────────────────────────────────────
command -v java >/dev/null || die "No hay Java. Instala un JDK 17 (p. ej. 'sudo apt install openjdk-17-jdk' o 'brew install openjdk@17')."
JV=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+)[."].*/\1/')
[[ "${JV:-0}" -ge 17 ]] || die "Se necesita JDK 17 o superior (detectado: $JV)."

# ── 2) Android SDK ────────────────────────────────────────────────────────
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" ]]; then
  for c in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "$HOME/.android-sdk"; do [[ -d "$c" ]] && SDK="$c" && break; done
fi
SDK="${SDK:-$HOME/.android-sdk}"
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
SDKM="$SDK/cmdline-tools/latest/bin/sdkmanager"

if [[ ! -d "$SDK/platforms/android-36" || ! -d "$SDK/build-tools/36.0.0" ]]; then
  if [[ ! -x "$SDKM" ]]; then
    say "Instalando las herramientas de línea de comandos del SDK en $SDK"
    case "$(uname -s)" in
      Linux)  OS=linux ;;
      Darwin) OS=mac ;;
      *) die "Sistema no soportado por este script (usa WSL en Windows)." ;;
    esac
    command -v curl >/dev/null && command -v unzip >/dev/null || die "Hacen falta curl y unzip."
    TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
    curl -fL --retry 3 -o "$TMP/cmd.zip" "https://dl.google.com/android/repository/commandlinetools-${OS}-11076708_latest.zip" \
      || die "No se pudo descargar el SDK desde dl.google.com (¿red o proxy?)."
    unzip -q "$TMP/cmd.zip" -d "$TMP/x"
    mkdir -p "$SDK/cmdline-tools"
    rm -rf "$SDK/cmdline-tools/latest"
    mv "$TMP/x/cmdline-tools" "$SDK/cmdline-tools/latest"
  fi

  say "Aceptando licencias e instalando plataforma 36 / build-tools / platform-tools"
  yes 2>/dev/null | "$SDKM" --licenses >/dev/null || true
  "$SDKM" --install "platforms;android-36" "build-tools;36.0.0" "platform-tools" >/dev/null \
    || die "sdkmanager no pudo instalar los paquetes."
elif [[ -x "$SDKM" ]]; then
  yes 2>/dev/null | "$SDKM" --licenses >/dev/null || true
fi
echo "sdk.dir=$SDK" > local.properties

# ── 3) google-services.json (sólo sabor fcm) ──────────────────────────────
if [[ "$FLAVOR" == fcm && ! -f app/google-services.json ]]; then
  echo "AVISO: falta app/google-services.json: el sabor fcm compilará, pero FCM quedará inerte (el canal directo sigue funcionando)."
fi

# ── 4) Tests y compilación ────────────────────────────────────────────────
G=./gradlew; chmod +x "$G"
if [[ "$TEST" == 1 ]]; then
  say "Tests (protocolo + app)"
  "$G" :protocol:test ":app:test${FLAVOR^}${BUILD}UnitTest"
fi

TASK=":app:assemble${FLAVOR^}${BUILD}"
say "Compilando $TASK (la primera vez descarga dependencias: puede tardar varios minutos)"
"$G" "$TASK"

APK=$(find "app/build/outputs/apk/$FLAVOR/${BUILD,,}" -name '*.apk' | head -1)
[[ -n "$APK" ]] || die "No se encontró el APK generado."
mkdir -p dist
OUT="dist/rp-${FLAVOR}-${BUILD,,}.apk"
cp "$APK" "$OUT"
[[ "$BUILD" == Release && "$APK" == *unsigned* ]] && echo "AVISO: el APK de release está sin firmar (crea keystore.properties)."

say "Listo: $OUT ($(du -h "$OUT" | cut -f1))"

# ── 5) Instalación opcional ───────────────────────────────────────────────
ADB="$SDK/platform-tools/adb"
if [[ "$INSTALL" == 1 ]]; then
  "$ADB" start-server >/dev/null
  "$ADB" devices | sed -n '2,$p' | grep -q 'device$' || die "adb no ve ningún teléfono. Activa 'Depuración USB' y acepta la huella RSA."
  say "Instalando en el teléfono"
  "$ADB" install -r "$OUT"
  "$ADB" shell am start -n "dev.j3pf.rp/.MainActivity" >/dev/null || true
else
  cat <<MSG

Para instalarlo en tu teléfono:
  • Por cable:   scripts/build-apk.sh --install      (o: $ADB install -r $OUT)
  • Sin cable:   pasa $OUT al móvil (Drive, Telegram, cable USB…), ábrelo y permite
                 "Instalar apps de origen desconocido" para esa app.
Luego: crea el QR en tu servidor con 'rp-relay pair device', abre RP y escanéalo.
MSG
fi

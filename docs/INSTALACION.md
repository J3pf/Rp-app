# Guía de Instalación, Vinculación y Troubleshooting — RP App (Android)

Esta guía detalla cómo instalar la aplicación Android **RP**, vincularla con tu servidor **RP Relay** y solucionar problemas comunes de red, permisos y notificaciones.

---

## 1. Requisitos del sistema

- **Dispositivo móvil:**
  - Android 8.0 (API 26) o superior.
  - Android 12+ (API 31+) recomendado para soporte de autenticación biométrica en acciones de notificación.
- **Conectividad:** Acceso de red hacia el servidor **RP Relay** (misma red local, VPN/Tailscale o dominio público con HTTPS).
- **Entorno opcional para compilar desde código:**
  - JDK 17 o superior (`openjdk-17-jdk` / `openjdk-21-jdk`).
  - Android SDK con plataforma 36 y `build-tools 36.0.0` (gestionados automáticamente por `scripts/build-apk.sh`).

---

## 2. Obtención e Instalación del APK

### Opción A: Descarga directa desde la Release oficial (Recomendado)

1. En tu teléfono o PC, ingresa a la release oficial de GitHub:
   👉 **[RP App v0.3.4 — Descargar APK](https://github.com/J3pf/Rp-app/releases/tag/v0.3.4)**
2. Descarga el archivo de instalación oficial:
   - **`rp-v0.3.4-direct.apk`** (30 MB): Sabor Release oficial independiente de Google (utiliza el canal directo de push por WebSocket `remoteMessaging`).

#### Instalación en el móvil:
- **Desde el navegador del teléfono:**
  1. Descarga el APK.
  2. Al pulsar sobre la descarga finalizada, si el sistema lo solicita, activa **«Permitir desde esta fuente»** (Instalar aplicaciones de origen desconocido) para tu navegador o gestor de archivos.
  3. Pulsa **Instalar**.
- **Desde la computadora vía ADB (cable USB o Wi-Fi):**
  1. Habilita las **Opciones de desarrollador** y activa **Depuración USB** en el teléfono.
  2. Ejecuta:
     ```bash
     adb install -r rp-v0.3.4-direct.apk
     ```

### Opción B: Compilación local desde el código fuente

Si prefieres compilar el APK tú mismo:

```bash
# Compilar sabor direct debug
scripts/build-apk.sh

# Compilar e instalar de inmediato en un teléfono conectado por ADB
scripts/build-apk.sh --install

# Ejecutar tests unitarios antes de compilar
scripts/build-apk.sh --test
```
El archivo generado se ubicará en `dist/rp-direct-debug.apk`.

---

## 3. Vinculación con el Relay (Emparejamiento)

Para conectar la app con tus agentes:

### Paso 1: Generar la invitación en el Relay
En el servidor donde corre `rp-relay`, ejecuta:
```bash
rp-relay pair device
```
*(O `npx rp-relay pair device` si ejecutas desde el repositorio).*

El comando imprimirá en pantalla:
- Un **código QR** que contiene la URI `rp://pair?u=<URL>&c=<CODIGO>`.
- La **URL del relay** (ej. `http://192.168.1.50:8787` o `https://relay.tudominio.com`).
- Un **código de 8 caracteres** alfanumérico en Base32 (ej. `BTBJ-M9A1`).
- *Nota:* Este código es de **un solo uso** y tiene una validez de **10 minutos**.

### Paso 2: Vincular en la App
1. Abre la aplicación **RP** en tu teléfono.
2. En la pantalla inicial de bienvenida tendrás dos opciones:
   - **Escanear código QR:** Pulsa el botón, concede permiso de cámara y apunta la cámara a la terminal del servidor. La app detectará la URL y el código automáticamente.
   - **Ingreso manual:** Si estás en otra red o no tienes cámara disponible, pulsa «Ingresar manualmente», escribe la URL del Relay y el código de 8 caracteres.
3. Pulsa **Vincular**.
4. La app se comunicará con `POST /v1/pair/device`, guardará el token de dispositivo cifrado en **Android Keystore** (Tink AES-256-GCM) y abrirá el canal WebSocket seguro.
5. Inmediatamente verás la lista de tus PCs y agentes activos.

---

## 4. Permisos clave y configuración en Android

Para asegurar que las autorizaciones y preguntas de los agentes lleguen sin interrupciones:

1. **Permiso de Notificaciones (`POST_NOTIFICATIONS` en Android 13+):**
   - Concede el permiso cuando la app lo solicite en el primer inicio.
2. **Optimización de Batería (Crítico para push sin Google):**
   - Ve a *Ajustes de Android → Aplicaciones → RP → Batería* y selecciona **«Sin restricciones»** (o *No optimizar*).
   - Esto impide que el sistema cierre el servicio WebSocket en segundo plano (`DirectPushService`) al apagar la pantalla.
3. **Permiso de Cámara:**
   - Requerido únicamente para escanear el QR de emparejamiento.

---

## 5. Resolución de problemas (Troubleshooting)

### 5.1 No llegan notificaciones en Android 13+ (API 33+)
- **Causa:** El permiso de notificaciones fue denegado o los canales están deshabilitados.
- **Solución:**
  1. Ve a *Ajustes del sistema → Aplicaciones → RP → Notificaciones*.
  2. Verifica que el interruptor principal **«Permitir notificaciones»** esté activo.
  3. Revisa la lista de categorías (canales de notificación por PC) y asegúrate de que canales como *Autorizaciones* (`approvals`) y *Preguntas* (`questions`) tengan activado el comportamiento **Alerta / Aviso en pantalla**.

### 5.2 Notificaciones retrasadas o desconexión con pantalla apagada
- **Causa:** Android Doze Mode o gestores de batería de capas de personalización (Xiaomi MIUI/HyperOS, Samsung OneUI, Huawei, Realme, OnePlus) suspenden los procesos en segundo plano.
- **Solución:**
  1. Configura la batería de la app como **Sin restricciones**.
  2. En dispositivos **Xiaomi / HyperOS / MIUI:**
     - Ve a *Ajustes → Aplicaciones → Administrar aplicaciones → RP*.
     - Activa **Inicio automático**.
     - En *Ahorro de batería*, selecciona **Sin restricciones**.
     - En la vista de apps recientes, mantén presionada la tarjeta de RP y activa el ícono del **candado** para evitar que se limpie de la memoria.
  3. En dispositivos **Samsung (OneUI):**
     - Ve a *Ajustes → Cuidado del dispositivo → Batería → Límites de uso en segundo plano*.
     - Asegúrate de que RP esté en la lista de **Aplicaciones que nunca se suspenden**.

### 5.3 Error de conexión HTTP en red local o certificados TLS
- **Causa:** Por defecto, Android restringe el tráfico en texto claro (`http://`) y desconfía de certificados TLS autofirmados.
- **Solución:**
  - **Tráfico HTTP local:** El APK debug incluye configuración de seguridad que permite conexiones en texto claro a `localhost`, `127.0.0.1` y `10.0.2.2`. Para conectar por IP local en LAN (ej. `http://192.168.1.X:8787`), se recomienda:
    - Usar un proxy TLS ligero como Caddy (`caddy reverse-proxy --to :8787`).
    - O utilizar una red privada como **Tailscale** (que provee nombres DNS y certificados TLS válidos con `tailscale cert`).
  - **Certificados autofirmados:** Si utilizas una CA propia (como `mkcert`), instala el certificado raíz `.crt` en tu teléfono:
    - Ve a *Ajustes → Seguridad → Cifrado y credenciales → Instalar certificado → Certificado de CA*.
    - El APK debug tiene habilitado `<debug-overrides>` para confiar en los certificados instalados por el usuario.
  - **En producción / fuera de casa:** Utiliza siempre un dominio con HTTPS público y certificado válido (Let's Encrypt o Cloudflare).

### 5.4 Código de emparejamiento expirado o inválido
- **Causa:** El código de 8 caracteres caducó (expira a los 10 minutos) o ya fue utilizado por otro dispositivo (son de un solo uso por seguridad).
- **Solución:**
  - En el servidor Relay, ejecuta nuevamente `rp-relay pair device` para obtener un código nuevo y vuelve a escanearlo o ingresarlo.

### 5.5 Estado "Desconectado" persistente en la app
- **Causa:** La app no puede alcanzar la dirección del Relay o el puerto está cerrado por un firewall.
- **Solución:**
  1. Comprueba desde el navegador del móvil (o con `curl` desde la misma red Wi-Fi) que el endpoint de salud responda:
     ```text
     http://<IP_DEL_RELAY>:8787/healthz
     ```
     Debe responder: `{"ok":true,...}`.
  2. Si no responde, verifica que el proceso de `rp-relay` esté corriendo y que el firewall del servidor permita el tráfico:
     ```bash
     sudo ufw allow 8787/tcp
     ```
  3. Comprueba que el teléfono y el servidor estén en la misma subred o conectados a la misma VPN.

### 5.6 Las acciones de notificación (Permitir / Denegar) no responden
- **Causa:** Al presionar un botón de acción en la notificación heads-up, la app envía una petición HTTPS inmediata a `/v1/prompts/{id}/answer`. Si el teléfono pierde conectividad en ese instante, la petición falla.
- **Solución:**
  - La app incluye una cola de reintento automático con `WorkManager`. En cuanto el dispositivo recupera la conexión, la respuesta pendiente se despacha automáticamente y la notificación se descarta.

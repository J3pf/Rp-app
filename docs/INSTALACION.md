# Guía de Instalación y Configuración — RP App (Android)

Esta guía detalla los pasos para compilar, instalar en tu teléfono y conectar la aplicación Android **RP** con tu servidor **RP Relay**.

---

## 1. Requisitos del sistema

- **Dispositivo Android:**
  - Versión mínima: Android 8.0 (API 26).
  - Versión recomendada: Android 12+ (API 31+) para soporte nativo de acciones autenticadas con biometría en notificaciones.
- **Entorno de compilación (Linux, macOS o WSL en Windows):**
  - **JDK 17** o superior (`openjdk-17-jdk` / `openjdk-21-jdk`).
  - **Android SDK** con plataforma 36 y `build-tools 36.0.0` (el script `build-apk.sh` los descarga automáticamente si faltan).
  - Acceso a internet hacia repositorios Maven (Google Maven y Maven Central).

---

## 2. Compilación del APK

El repositorio incluye el script automatizado `scripts/build-apk.sh`:

```bash
# APK debug estándar sabor "direct" (independiente de Google, canal directo por WebSocket)
scripts/build-apk.sh

# Para correr los tests unitarios antes de compilar
scripts/build-apk.sh --test

# Para compilar e instalar automáticamente por ADB en un teléfono conectado
scripts/build-apk.sh --install
```

### Sabores disponibles
- **`direct` (por defecto):** No requiere servicios de Google Play ni Firebase. Mantiene un servicio en primer plano (`remoteMessaging`) con conexión WebSocket de bajo consumo al Relay para push instantáneo.
- **`fcm` (`--fcm`):** Soporta Firebase Cloud Messaging. Requiere colocar el archivo `app/google-services.json` configurado con tu proyecto Firebase antes de compilar. Ambos canales conviven y se deduplican automáticamente.

El archivo compilado se genera en:
```text
dist/rp-direct-debug.apk
```

---

## 3. Instalación en el teléfono

Tienes dos métodos para instalar el APK en tu dispositivo:

### Método A: Mediante cable USB / ADB (Recomendado)
1. Activa las **Opciones de desarrollador** en tu teléfono (tocando 7 veces el *Número de compilación* en Ajustes -> Información del teléfono).
2. Habilita la casilla **Depuración USB** (y acepta la clave RSA cuando aparezca el diálogo en pantalla).
3. Ejecuta desde la terminal:
   ```bash
   scripts/build-apk.sh --install
   ```
   *(O manualmente: `adb install -r dist/rp-direct-debug.apk`)*

### Método B: Transferencia manual del archivo
1. Transfiere el archivo `dist/rp-direct-debug.apk` a tu teléfono (por cable USB, Google Drive, Telegram, LocalSend, etc.).
2. Abre el archivo `.apk` desde el explorador de archivos del teléfono.
3. Si el sistema te lo solicita, activa el permiso de **«Instalar aplicaciones de origen desconocido»** para esa app.
4. Pulsa **Instalar**.

---

## 4. Permisos clave en Android

Al abrir la app por primera vez, concédele los siguientes permisos para asegurar su correcto funcionamiento:

1. **Notificaciones (`POST_NOTIFICATIONS` en Android 13+):**
   - Esencial para recibir autorizaciones y preguntas de tus agentes en tiempo real.
2. **Optimización de batería:**
   - Ve a *Ajustes del sistema → Aplicaciones → RP → Batería* y selecciona **Sin restricciones** (o desactiva la optimización).
   - Esto evita que el sistema operativo cierre la conexión del servicio de notificaciones en segundo plano cuando la pantalla esté apagada.
3. **Cámara:**
   - Necesario para escanear el código QR de emparejamiento.

---

## 5. Emparejamiento con el Relay

1. En tu servidor Relay (donde corre `rp-relay`), genera una invitación de emparejamiento ejecutando:
   ```bash
   rp-relay pair device
   ```
   El terminal mostrará un código QR y un código alfanumérico corto de 8 caracteres (válido por 10 minutos).
2. Abre la app **RP** en tu teléfono.
3. En la pantalla de bienvenida, pulsa **Escanear código QR** y apunta al terminal del servidor.
   *(Alternativamente, puedes ingresar la URL de tu relay y el código de 8 caracteres manualmente).*
4. Una vez validado, la app guardará de forma segura el token cifrado en Android Keystore y quedará vinculada permanentemente a tu Relay.

---

## 6. Verificación de funcionamiento

1. **Lista de agentes:** En la pantalla principal verás las PCs registradas y los agentes activos con su estado en vivo (*Trabajando*, *Esperando respuesta*, etc.).
2. **Chat en vivo:** Pulsa sobre cualquier agente para abrir el chat, ver su razonamiento, herramientas en ejecución y enviarle mensajes.
3. **Prueba de notificaciones:**
   - Entra en *Ajustes → Notificaciones*.
   - Pulsa en **«Enviar notificación de prueba»** para comprobar que el canal de notificaciones y las acciones rápidas responden correctamente en tu sistema.

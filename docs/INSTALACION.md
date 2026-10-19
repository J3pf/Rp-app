# Installation, Pairing, and Troubleshooting Guide — RP App (Android)

This guide explains how to install the **RP** Android app, pair it with your **RP Relay** server, and troubleshoot common network, permission, and notification issues.

---

## 1. System Requirements

- **Mobile device:**
  - Android 8.0 (API 26) or later.
  - Android 12+ (API 31+) recommended for biometric authentication on notification actions.
- **Connectivity:** Network access to the **RP Relay** server (same local network, VPN/Tailscale, or a public HTTPS domain).
- **Optional build environment:**
  - JDK 17 or later (`openjdk-17-jdk` / `openjdk-21-jdk`).
  - Android SDK platform 36 and `build-tools 36.0.0` (managed automatically by `scripts/build-apk.sh`).

---

## 2. Downloading and Installing the APK

### Option A: Direct download from the official release (recommended)

1. Open the official GitHub release on your phone or computer:
   👉 **[RP App v0.4.1 — Download APK](https://github.com/J3pf/Rp-app/releases/tag/v0.4.1)**
2. Download the official installer:
   - **[rp-v0.4.1-direct.apk](https://github.com/J3pf/Rp-app/releases/download/v0.4.1/rp-v0.4.1-direct.apk)**: the official release without Google services, using the `remoteMessaging` WebSocket push channel.

#### Install on your phone
- **From the phone browser:**
  1. Download the APK.
  2. If prompted, allow your browser or file manager to **Install unknown apps** from this source.
  3. Tap **Install**.
- **From a computer using ADB (USB cable or Wi-Fi):**
  1. Enable **Developer options** and **USB debugging** on the phone.
  2. Run:
     ```bash
     adb install -r rp-v0.4.1-direct.apk
     ```

### Option B: Build locally from source

If you prefer to build the APK yourself:

```bash
# Build the direct debug flavor
scripts/build-apk.sh

# Build and immediately install on a phone connected through ADB
scripts/build-apk.sh --install

# Run unit tests before building
scripts/build-apk.sh --test
```

The generated APK is placed at `dist/rp-direct-debug.apk`.

---

## 3. Pairing with the Relay

To connect the app to your agents:

### Step 1: Create an invitation on the relay
On the server running `rp-relay`, run:

```bash
rp-relay pair device
```

*(Or run `npx rp-relay pair device` from the repository.)*

The command prints:
- A **QR code** containing the URI `rp://pair?u=<URL>&c=<CODE>`.
- The **relay URL** (for example, `http://192.168.1.50:8787` or `https://relay.example.com`).
- An **8-character alphanumeric code** in Base32 (for example, `BTBJ-M9A1`).
- *Note:* This code is **single-use** and expires after **10 minutes**.

### Step 2: Pair in the app
1. Open **RP** on your phone.
2. On the welcome screen, choose one of these options:
   - **Scan QR code:** Tap the button, grant camera permission, and point the camera at the server terminal. The app detects the URL and code automatically.
   - **Enter manually:** If you are on another network or do not have a camera, tap `Ingresar manualmente` and enter the relay URL and 8-character code.
3. Tap `Vincular`.
4. The app calls `POST /v1/pair/device`, stores the encrypted device token in **Android Keystore** (Tink AES-256-GCM), and opens a secure WebSocket channel.
5. Your PCs and active agents appear in the app.

---

## 4. Key Android Permissions and Settings

To receive agent authorizations and questions reliably:

1. **Notification permission (`POST_NOTIFICATIONS` on Android 13+):**
   - Grant permission when the app requests it on first launch.
2. **Battery optimization (critical for Google-free push):**
   - Go to *Android Settings → Apps → RP → Battery* and select **Unrestricted** (or *Don't optimize*).
   - This prevents the system from stopping the background WebSocket service (`DirectPushService`) when the screen turns off.
3. **Camera permission:**
   - Required only to scan the pairing QR code.

---

## 5. Troubleshooting

### 5.1 Notifications do not arrive on Android 13+ (API 33+)
- **Cause:** Notification permission was denied or notification channels are disabled.
- **Solution:**
  1. Go to *System Settings → Apps → RP → Notifications*.
  2. Make sure the main **Allow notifications** switch is on.
  3. Check the categories (notification channels per PC) and ensure channels such as *Autorizaciones* (`approvals`) and *Preguntas* (`questions`) use **Alert / Pop on screen** behavior.

### 5.2 Notifications are delayed or the connection drops with the screen off
- **Cause:** Android Doze Mode or vendor battery managers (Xiaomi MIUI/HyperOS, Samsung One UI, Huawei, Realme, OnePlus) suspend background processes.
- **Solution:**
  1. Set the app battery mode to **Unrestricted**.
  2. On **Xiaomi / HyperOS / MIUI** devices:
     - Go to *Settings → Apps → Manage apps → RP*.
     - Enable **Autostart**.
     - Under *Battery saver*, select **Unrestricted**.
     - In Recents, long-press the RP card and enable its **lock** icon so it is not cleared from memory.
  3. On **Samsung (One UI)** devices:
     - Go to *Settings → Device care → Battery → Background usage limits*.
     - Make sure RP is in **Never sleeping apps**.

### 5.3 HTTP connection errors on a local network or TLS certificate errors
- **Cause:** Android restricts cleartext (`http://`) traffic by default and does not trust self-signed TLS certificates.
- **Solution:**
  - **Local HTTP traffic:** The debug APK includes security configuration allowing cleartext connections to `localhost`, `127.0.0.1`, and `10.0.2.2`. For a LAN IP address (for example, `http://192.168.1.X:8787`), use one of these options:
    - Use a lightweight TLS proxy such as Caddy (`caddy reverse-proxy --to :8787`).
    - Use a private network such as **Tailscale**, which provides DNS names and valid TLS certificates through `tailscale cert`.
  - **Self-signed certificates:** If you use your own CA (such as `mkcert`), install the root `.crt` certificate on your phone:
    - Go to *Settings → Security → Encryption & credentials → Install a certificate → CA certificate*.
    - The debug APK has `<debug-overrides>` enabled to trust user-installed certificates.
  - **Production / outside your home:** Always use a public HTTPS domain with a valid certificate (Let's Encrypt or Cloudflare).

### 5.4 Pairing code expired or invalid
- **Cause:** The 8-character code expired (after 10 minutes) or was already used by another device (codes are single-use for security).
- **Solution:** Run `rp-relay pair device` on the relay server again to generate a new code, then scan or enter it.

### 5.5 The app remains disconnected
- **Cause:** The app cannot reach the relay address or a firewall is blocking the port.
- **Solution:**
  1. From the phone browser (or with `curl` on the same Wi-Fi network), check that the health endpoint responds:
     ```text
     http://<RELAY_IP>:8787/healthz
     ```
     It should respond with `{"ok":true,...}`.
  2. If it does not respond, check that `rp-relay` is running and the server firewall allows traffic:
     ```bash
     sudo ufw allow 8787/tcp
     ```
  3. Check that the phone and server are on the same subnet or connected to the same VPN.

### 5.6 Notification actions (Allow / Deny) do not respond
- **Cause:** When a heads-up notification action is tapped, the app immediately sends an HTTPS request to `/v1/prompts/{id}/answer`. If the phone loses connectivity at that moment, the request fails.
- **Solution:** The app includes an automatic retry queue using WorkManager. Once connectivity returns, the pending response is sent automatically and the notification is dismissed.

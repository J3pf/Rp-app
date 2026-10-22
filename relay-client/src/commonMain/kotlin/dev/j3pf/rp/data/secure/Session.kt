package dev.j3pf.rp.data.secure

/** Sesión emparejada con un relay. `relayId` permite varios relays en fase 4 sin migrar la base de datos. */
data class Session(
    val relayId: String,
    val relayUrl: String,
    val relayName: String,
    val deviceId: String,
    val deviceToken: String,
    /** Clave AES‑256 (base64) para descifrar los pushes de FCM. */
    val pushKey: String,
) {
    val wsBase: String get() = relayUrl.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
}

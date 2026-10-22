package dev.j3pf.rp.protocol

import kotlinx.serialization.json.JsonElement
import java.util.logging.Level
import java.util.logging.Logger

internal actual fun logDecodeFailure(
    item: JsonElement,
    error: Exception,
) {
    Logger.getLogger("RpProtocol").log(Level.WARNING, "Error al decodificar elemento tolerante, omitiendo: $item", error)
}

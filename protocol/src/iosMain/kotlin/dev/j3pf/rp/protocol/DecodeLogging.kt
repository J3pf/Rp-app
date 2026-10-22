package dev.j3pf.rp.protocol

import kotlinx.serialization.json.JsonElement
import platform.Foundation.NSLog

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal actual fun logDecodeFailure(
    item: JsonElement,
    error: Exception,
) {
    NSLog("%@", "Error al decodificar elemento tolerante, omitiendo: $item\n$error")
}

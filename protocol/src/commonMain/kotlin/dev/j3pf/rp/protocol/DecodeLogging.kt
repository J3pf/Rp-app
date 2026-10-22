package dev.j3pf.rp.protocol

import kotlinx.serialization.json.JsonElement

internal expect fun logDecodeFailure(
    item: JsonElement,
    error: Exception,
)

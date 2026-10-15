package dev.j3pf.rp.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Configuración JSON única del protocolo: tolerante a campos desconocidos, sin nulos explícitos. */
val RpJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }

fun JsonElement?.obj(): JsonObject? = this as? JsonObject

fun JsonElement?.arr(): JsonArray? = this as? JsonArray

/** Cadena si es un primitivo (no `null`). */
fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.longOrNull

fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull

fun JsonElement?.double(): Double? = (this as? JsonPrimitive)?.doubleOrNull

fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

fun JsonObject.str(key: String): String? = this[key].str()

fun JsonObject.long(key: String): Long? = this[key].long()

fun JsonObject.int(key: String): Int? = this[key].int()

fun JsonObject.double(key: String): Double? = this[key].double()

fun JsonObject.bool(key: String): Boolean? = this[key].bool()

fun JsonObject.obj(key: String): JsonObject? = this[key].obj()

fun JsonObject.arr(key: String): JsonArray? = this[key].arr()

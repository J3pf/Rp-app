package dev.j3pf.rp.protocol

import java.security.SecureRandom

private val secureRandom = SecureRandom()

internal actual fun platformRandomBytes(count: Int): ByteArray = ByteArray(count).also(secureRandom::nextBytes)

package dev.j3pf.rp.data.crypto

/**
 * Pure-Kotlin NIST FIPS 197 AES-256 + NIST SP 800-38D GCM authenticated encryption and decryption.
 *
 * Used by [CommonPushEnvelopeCrypto] and [AesGcmSecureCrypto] so `:data-core` commonMain and
 * `iosMain` can authenticate and decrypt AES-256-GCM envelopes (32-byte key, 12-byte nonce,
 * 128-bit tag) identically to JVM/Android `javax.crypto` without external native dependencies.
 */
object Aes256Gcm {
    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    const val TAG_BYTES = 16
    private const val BLOCK_BYTES = 16

    fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray = ByteArray(0),
    ): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 requires a 32-byte key" }
        require(nonce.size == NONCE_BYTES) { "GCM requires a 12-byte nonce" }
        val roundKeys = expandKey(key)
        val h = aesBlock(ByteArray(BLOCK_BYTES), roundKeys)
        val j0 = ByteArray(BLOCK_BYTES)
        nonce.copyInto(j0, 0, 0, NONCE_BYTES)
        j0[15] = 1

        val ciphertext = gctr(roundKeys, inc32(j0), plaintext)
        val s = ghash(h, aad, ciphertext)
        val encJ0 = aesBlock(j0, roundKeys)
        val out = ByteArray(ciphertext.size + TAG_BYTES)
        ciphertext.copyInto(out, 0)
        for (i in 0 until TAG_BYTES) {
            out[ciphertext.size + i] = (encJ0[i].toInt() xor s[i].toInt()).toByte()
        }
        return out
    }

    fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertextWithTag: ByteArray,
        aad: ByteArray = ByteArray(0),
    ): ByteArray? {
        if (key.size != KEY_BYTES || nonce.size != NONCE_BYTES || ciphertextWithTag.size < TAG_BYTES) {
            return null
        }
        val roundKeys = expandKey(key)
        val h = aesBlock(ByteArray(BLOCK_BYTES), roundKeys)
        val j0 = ByteArray(BLOCK_BYTES)
        nonce.copyInto(j0, 0, 0, NONCE_BYTES)
        j0[15] = 1

        val cipherLen = ciphertextWithTag.size - TAG_BYTES
        val ciphertext = ciphertextWithTag.copyOfRange(0, cipherLen)
        val s = ghash(h, aad, ciphertext)
        val encJ0 = aesBlock(j0, roundKeys)

        var diff = 0
        for (i in 0 until TAG_BYTES) {
            val expectedTagByte = (encJ0[i].toInt() xor s[i].toInt()) and 0xFF
            val actualTagByte = ciphertextWithTag[cipherLen + i].toInt() and 0xFF
            diff = diff or (expectedTagByte xor actualTagByte)
        }
        if (diff != 0) return null

        return gctr(roundKeys, inc32(j0), ciphertext)
    }

    private fun gctr(
        roundKeys: IntArray,
        initialCounterBlock: ByteArray,
        input: ByteArray,
    ): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        val out = ByteArray(input.size)
        var cb = initialCounterBlock.copyOf()
        var offset = 0
        while (offset < input.size) {
            val keystream = aesBlock(cb, roundKeys)
            val chunk = minOf(BLOCK_BYTES, input.size - offset)
            for (i in 0 until chunk) {
                out[offset + i] = (input[offset + i].toInt() xor keystream[i].toInt()).toByte()
            }
            offset += chunk
            if (offset < input.size) {
                cb = inc32(cb)
            }
        }
        return out
    }

    private fun inc32(block: ByteArray): ByteArray {
        val next = block.copyOf()
        val counter =
            ((next[12].toInt() and 0xFF) shl 24) or
                ((next[13].toInt() and 0xFF) shl 16) or
                ((next[14].toInt() and 0xFF) shl 8) or
                (next[15].toInt() and 0xFF)
        val incremented = counter + 1
        next[12] = (incremented ushr 24).toByte()
        next[13] = (incremented ushr 16).toByte()
        next[14] = (incremented ushr 8).toByte()
        next[15] = incremented.toByte()
        return next
    }

    private fun ghash(
        h: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        var y = ByteArray(BLOCK_BYTES)
        y = ghashProcess(y, h, aad)
        y = ghashProcess(y, h, ciphertext)

        val lenBlock = ByteArray(BLOCK_BYTES)
        val aadBits = aad.size.toLong() * 8L
        val cipherBits = ciphertext.size.toLong() * 8L
        writeLongBE(aadBits, lenBlock, 0)
        writeLongBE(cipherBits, lenBlock, 8)
        for (i in 0 until BLOCK_BYTES) {
            y[i] = (y[i].toInt() xor lenBlock[i].toInt()).toByte()
        }
        return gf128Mul(y, h)
    }

    private fun ghashProcess(
        initialY: ByteArray,
        h: ByteArray,
        data: ByteArray,
    ): ByteArray {
        var y = initialY
        var offset = 0
        while (offset < data.size) {
            val chunk = minOf(BLOCK_BYTES, data.size - offset)
            for (i in 0 until chunk) {
                y[i] = (y[i].toInt() xor data[offset + i].toInt()).toByte()
            }
            y = gf128Mul(y, h)
            offset += chunk
        }
        return y
    }

    private fun gf128Mul(
        x: ByteArray,
        y: ByteArray,
    ): ByteArray {
        var zHi = 0L
        var zLo = 0L
        var vHi = readLongBE(y, 0)
        var vLo = readLongBE(y, 8)

        for (i in 0 until 128) {
            val byteIdx = i ushr 3
            val bitIdx = 7 - (i and 7)
            val bit = (x[byteIdx].toInt() ushr bitIdx) and 1
            if (bit != 0) {
                zHi = zHi xor vHi
                zLo = zLo xor vLo
            }
            val lsb = vLo and 1L
            vLo = (vLo ushr 1) or (vHi shl 63)
            vHi = vHi ushr 1
            if (lsb != 0L) {
                vHi = vHi xor (0xE1L shl 56)
            }
        }

        val out = ByteArray(BLOCK_BYTES)
        writeLongBE(zHi, out, 0)
        writeLongBE(zLo, out, 8)
        return out
    }

    private fun readLongBE(
        buf: ByteArray,
        offset: Int,
    ): Long {
        var res = 0L
        for (i in 0 until 8) {
            res = (res shl 8) or (buf[offset + i].toLong() and 0xFFL)
        }
        return res
    }

    private fun writeLongBE(
        value: Long,
        buf: ByteArray,
        offset: Int,
    ) {
        for (i in 0 until 8) {
            buf[offset + i] = (value ushr (56 - i * 8)).toByte()
        }
    }

    private fun expandKey(key: ByteArray): IntArray {
        val w = IntArray(60)
        for (i in 0 until 8) {
            w[i] =
                ((key[4 * i].toInt() and 0xFF) shl 24) or
                ((key[4 * i + 1].toInt() and 0xFF) shl 16) or
                ((key[4 * i + 2].toInt() and 0xFF) shl 8) or
                (key[4 * i + 3].toInt() and 0xFF)
        }
        for (i in 8 until 60) {
            var temp = w[i - 1]
            if (i % 8 == 0) {
                temp = subWord(rotWord(temp)) xor RCON[i / 8]
            } else if (i % 8 == 4) {
                temp = subWord(temp)
            }
            w[i] = w[i - 8] xor temp
        }
        return w
    }

    private fun rotWord(w: Int): Int = (w shl 8) or (w ushr 24)

    private fun subWord(w: Int): Int =
        ((SBOX[(w ushr 24) and 0xFF].toInt() and 0xFF) shl 24) or
            ((SBOX[(w ushr 16) and 0xFF].toInt() and 0xFF) shl 16) or
            ((SBOX[(w ushr 8) and 0xFF].toInt() and 0xFF) shl 8) or
            (SBOX[w and 0xFF].toInt() and 0xFF)

    private fun aesBlock(
        input: ByteArray,
        w: IntArray,
    ): ByteArray {
        val state = ByteArray(16)
        input.copyInto(state)
        addRoundKey(state, w, 0)
        for (round in 1..13) {
            subBytes(state)
            shiftRows(state)
            mixColumns(state)
            addRoundKey(state, w, round)
        }
        subBytes(state)
        shiftRows(state)
        addRoundKey(state, w, 14)
        return state
    }

    private fun addRoundKey(
        state: ByteArray,
        w: IntArray,
        round: Int,
    ) {
        val base = round * 4
        for (c in 0 until 4) {
            val word = w[base + c]
            state[4 * c] = (state[4 * c].toInt() xor (word ushr 24)).toByte()
            state[4 * c + 1] = (state[4 * c + 1].toInt() xor (word ushr 16)).toByte()
            state[4 * c + 2] = (state[4 * c + 2].toInt() xor (word ushr 8)).toByte()
            state[4 * c + 3] = (state[4 * c + 3].toInt() xor word).toByte()
        }
    }

    private fun subBytes(state: ByteArray) {
        for (i in 0 until 16) {
            state[i] = SBOX[state[i].toInt() and 0xFF]
        }
    }

    private fun shiftRows(state: ByteArray) {
        val t1 = state[1]
        state[1] = state[5]
        state[5] = state[9]
        state[9] = state[13]
        state[13] = t1

        val t2a = state[2]
        val t2b = state[6]
        state[2] = state[10]
        state[6] = state[14]
        state[10] = t2a
        state[14] = t2b

        val t3 = state[15]
        state[15] = state[11]
        state[11] = state[7]
        state[7] = state[3]
        state[3] = t3
    }

    private fun mixColumns(state: ByteArray) {
        for (c in 0 until 4) {
            val i = 4 * c
            val a0 = state[i].toInt() and 0xFF
            val a1 = state[i + 1].toInt() and 0xFF
            val a2 = state[i + 2].toInt() and 0xFF
            val a3 = state[i + 3].toInt() and 0xFF
            val b0 = xtime(a0)
            val b1 = xtime(a1)
            val b2 = xtime(a2)
            val b3 = xtime(a3)
            state[i] = (b0 xor (b1 xor a1) xor a2 xor a3).toByte()
            state[i + 1] = (a0 xor b1 xor (b2 xor a2) xor a3).toByte()
            state[i + 2] = (a0 xor a1 xor b2 xor (b3 xor a3)).toByte()
            state[i + 3] = ((b0 xor a0) xor a1 xor a2 xor b3).toByte()
        }
    }

    private fun xtime(a: Int): Int = ((a shl 1) xor (if ((a and 0x80) != 0) 0x1B else 0)) and 0xFF

    private val RCON =
        intArrayOf(
            0x00000000,
            0x01000000,
            0x02000000,
            0x04000000,
            0x08000000,
            0x10000000,
            0x20000000,
            0x40000000,
        )

    @Suppress("ktlint:standard:argument-list-wrapping")
    private val SBOX =
        byteArrayOf(
            0x63.toByte(), 0x7c.toByte(), 0x77.toByte(), 0x7b.toByte(), 0xf2.toByte(), 0x6b.toByte(), 0x6f.toByte(), 0xc5.toByte(),
            0x30.toByte(), 0x01.toByte(), 0x67.toByte(), 0x2b.toByte(), 0xfe.toByte(), 0xd7.toByte(), 0xab.toByte(), 0x76.toByte(),
            0xca.toByte(), 0x82.toByte(), 0xc9.toByte(), 0x7d.toByte(), 0xfa.toByte(), 0x59.toByte(), 0x47.toByte(), 0xf0.toByte(),
            0xad.toByte(), 0xd4.toByte(), 0xa2.toByte(), 0xaf.toByte(), 0x9c.toByte(), 0xa4.toByte(), 0x72.toByte(), 0xc0.toByte(),
            0xb7.toByte(), 0xfd.toByte(), 0x93.toByte(), 0x26.toByte(), 0x36.toByte(), 0x3f.toByte(), 0xf7.toByte(), 0xcc.toByte(),
            0x34.toByte(), 0xa5.toByte(), 0xe5.toByte(), 0xf1.toByte(), 0x71.toByte(), 0xd8.toByte(), 0x31.toByte(), 0x15.toByte(),
            0x04.toByte(), 0xc7.toByte(), 0x23.toByte(), 0xc3.toByte(), 0x18.toByte(), 0x96.toByte(), 0x05.toByte(), 0x9a.toByte(),
            0x07.toByte(), 0x12.toByte(), 0x80.toByte(), 0xe2.toByte(), 0xeb.toByte(), 0x27.toByte(), 0xb2.toByte(), 0x75.toByte(),
            0x09.toByte(), 0x83.toByte(), 0x2c.toByte(), 0x1a.toByte(), 0x1b.toByte(), 0x6e.toByte(), 0x5a.toByte(), 0xa0.toByte(),
            0x52.toByte(), 0x3b.toByte(), 0xd6.toByte(), 0xb3.toByte(), 0x29.toByte(), 0xe3.toByte(), 0x2f.toByte(), 0x84.toByte(),
            0x53.toByte(), 0xd1.toByte(), 0x00.toByte(), 0xed.toByte(), 0x20.toByte(), 0xfc.toByte(), 0xb1.toByte(), 0x5b.toByte(),
            0x6a.toByte(), 0xcb.toByte(), 0xbe.toByte(), 0x39.toByte(), 0x4a.toByte(), 0x4c.toByte(), 0x58.toByte(), 0xcf.toByte(),
            0xd0.toByte(), 0xef.toByte(), 0xaa.toByte(), 0xfb.toByte(), 0x43.toByte(), 0x4d.toByte(), 0x33.toByte(), 0x85.toByte(),
            0x45.toByte(), 0xf9.toByte(), 0x02.toByte(), 0x7f.toByte(), 0x50.toByte(), 0x3c.toByte(), 0x9f.toByte(), 0xa8.toByte(),
            0x51.toByte(), 0xa3.toByte(), 0x40.toByte(), 0x8f.toByte(), 0x92.toByte(), 0x9d.toByte(), 0x38.toByte(), 0xf5.toByte(),
            0xbc.toByte(), 0xb6.toByte(), 0xda.toByte(), 0x21.toByte(), 0x10.toByte(), 0xff.toByte(), 0xf3.toByte(), 0xd2.toByte(),
            0xcd.toByte(), 0x0c.toByte(), 0x13.toByte(), 0xec.toByte(), 0x5f.toByte(), 0x97.toByte(), 0x44.toByte(), 0x17.toByte(),
            0xc4.toByte(), 0xa7.toByte(), 0x7e.toByte(), 0x3d.toByte(), 0x64.toByte(), 0x5d.toByte(), 0x19.toByte(), 0x73.toByte(),
            0x60.toByte(), 0x81.toByte(), 0x4f.toByte(), 0xdc.toByte(), 0x22.toByte(), 0x2a.toByte(), 0x90.toByte(), 0x88.toByte(),
            0x46.toByte(), 0xee.toByte(), 0xb8.toByte(), 0x14.toByte(), 0xde.toByte(), 0x5e.toByte(), 0x0b.toByte(), 0xdb.toByte(),
            0xe0.toByte(), 0x32.toByte(), 0x3a.toByte(), 0x0a.toByte(), 0x49.toByte(), 0x06.toByte(), 0x24.toByte(), 0x5c.toByte(),
            0xc2.toByte(), 0xd3.toByte(), 0xac.toByte(), 0x62.toByte(), 0x91.toByte(), 0x95.toByte(), 0xe4.toByte(), 0x79.toByte(),
            0xe7.toByte(), 0xc8.toByte(), 0x37.toByte(), 0x6d.toByte(), 0x8d.toByte(), 0xd5.toByte(), 0x4e.toByte(), 0xa9.toByte(),
            0x6c.toByte(), 0x56.toByte(), 0xf4.toByte(), 0xea.toByte(), 0x65.toByte(), 0x7a.toByte(), 0xae.toByte(), 0x08.toByte(),
            0xba.toByte(), 0x78.toByte(), 0x25.toByte(), 0x2e.toByte(), 0x1c.toByte(), 0xa6.toByte(), 0xb4.toByte(), 0xc6.toByte(),
            0xe8.toByte(), 0xdd.toByte(), 0x74.toByte(), 0x1f.toByte(), 0x4b.toByte(), 0xbd.toByte(), 0x8b.toByte(), 0x8a.toByte(),
            0x70.toByte(), 0x3e.toByte(), 0xb5.toByte(), 0x66.toByte(), 0x48.toByte(), 0x03.toByte(), 0xf6.toByte(), 0x0e.toByte(),
            0x61.toByte(), 0x35.toByte(), 0x57.toByte(), 0xb9.toByte(), 0x86.toByte(), 0xc1.toByte(), 0x1d.toByte(), 0x9e.toByte(),
            0xe1.toByte(), 0xf8.toByte(), 0x98.toByte(), 0x11.toByte(), 0x69.toByte(), 0xd9.toByte(), 0x8e.toByte(), 0x94.toByte(),
            0x9b.toByte(), 0x1e.toByte(), 0x87.toByte(), 0xe9.toByte(), 0xce.toByte(), 0x55.toByte(), 0x28.toByte(), 0xdf.toByte(),
            0x8c.toByte(), 0xa1.toByte(), 0x89.toByte(), 0x0d.toByte(), 0xbf.toByte(), 0xe6.toByte(), 0x42.toByte(), 0x68.toByte(),
            0x41.toByte(), 0x99.toByte(), 0x2d.toByte(), 0x0f.toByte(), 0xb0.toByte(), 0x54.toByte(), 0xbb.toByte(), 0x16.toByte(),
        )
}

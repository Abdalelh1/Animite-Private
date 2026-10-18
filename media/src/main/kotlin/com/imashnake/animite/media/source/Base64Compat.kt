package com.imashnake.animite.media.source

/**
 * Base64Compat — Pure Kotlin Base64 encoder/decoder that works identically
 * on JVM unit tests and Android runtimes without depending on android.util.Base64.
 */
object Base64Compat {

    private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun decode(input: String): ByteArray {
        val clean = input.trim()
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")

        val len = clean.length
        if (len == 0) return ByteArray(0)

        // Count padding
        var pad = 0
        if (clean.endsWith("==")) pad = 2
        else if (clean.endsWith("=")) pad = 1

        val outLen = (len * 3 / 4) - pad
        val out = ByteArray(outLen)

        var outIdx = 0
        var b = 0
        var bCount = 0

        for (i in 0 until len) {
            val c = clean[i]
            if (c == '=') break
            val v = BASE64_ALPHABET.indexOf(c)
            if (v < 0) continue

            b = (b shl 6) or v
            bCount += 6

            if (bCount >= 8) {
                bCount -= 8
                if (outIdx < outLen) {
                    out[outIdx++] = ((b shr bCount) and 0xFF).toByte()
                }
            }
        }

        return out
    }

    fun decodeToString(input: String): String {
        return try {
            String(decode(input), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun encodeToString(bytes: ByteArray): String {
        val sb = StringBuilder()
        var b = 0
        var bCount = 0

        for (byte in bytes) {
            b = (b shl 8) or (byte.toInt() and 0xFF)
            bCount += 8
            while (bCount >= 6) {
                bCount -= 6
                val idx = (b shr bCount) and 0x3F
                sb.append(BASE64_ALPHABET[idx])
            }
        }

        if (bCount > 0) {
            val idx = (b shl (6 - bCount)) and 0x3F
            sb.append(BASE64_ALPHABET[idx])
            while (sb.length % 4 != 0) {
                sb.append('=')
            }
        }

        return sb.toString()
    }
}

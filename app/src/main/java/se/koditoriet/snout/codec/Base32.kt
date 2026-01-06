package se.koditoriet.snout.codec

import se.koditoriet.snout.crypto.BitWriter

fun base32Decode(base32: CharArray): ByteArray {
    val buffer = BitWriter()
    for (c in base32) {
        val bits = when (c) {
            in 'A' .. 'Z' -> c - 'A'
            in '2' .. '7' -> c - ('2' - 26)
            '=' -> break
            else -> throw IllegalArgumentException("invalid base32 string")
        }
        buffer.write(bits.toByte(), 5)
    }
    val bytes = buffer.getBytes()
    buffer.wipe()
    return bytes
}

fun isValidBase32(base32: String): Boolean {
    val base32Array = base32.toCharArray()
    try {
        base32Decode(base32Array)
        return true
    } catch (_: IllegalArgumentException) {
        return false
    } finally {
        base32Array.fill('\u0000')
    }
}

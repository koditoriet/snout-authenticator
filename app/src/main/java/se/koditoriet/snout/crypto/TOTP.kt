package se.koditoriet.snout.crypto

import se.koditoriet.snout.vault.TotpSecret
import kotlin.experimental.and
import kotlin.time.Instant

private val POW10_INT = intArrayOf(
    1,
    10,
    100,
    1_000,
    10_000,
    100_000,
    1_000_000,
    10_000_000,
    100_000_000,
    1_000_000_000
)

fun HmacContext.generateTotpCode(
    totpSecret: TotpSecret,
    time: Instant,
): String {
    val timeBytes = encodeTime(time, totpSecret.period)
    val hmac = hmac(timeBytes)
    return codeToString(hmac, totpSecret.digits)
}

private fun encodeTime(time: Instant, timeStep: Int): ByteArray {
    var timeVal = time.epochSeconds / timeStep
    val timeBytes = ByteArray(8)
    for (i in 7 downTo 0) {
        timeBytes[i] = (timeVal and 0xff).toByte()
        timeVal = timeVal shr 8
    }
    return timeBytes
}

private fun codeToString(hmac: ByteArray, digits: Int): String {
    val offset = (hmac.last() and 0xf).toInt()
    var code = (hmac[offset].toInt() and 0x7f).shl(24)
    code = code or (hmac[offset + 1].toInt() and 0xff).shl(16)
    code = code or (hmac[offset + 2].toInt() and 0xff).shl(8)
    code = code or (hmac[offset + 3].toInt() and 0xff)
    code %= POW10_INT[digits]
    return "$code".padStart(digits, '0')
}

package se.koditoriet.snout.vault

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val LATEST_BACKUP_FORMAT: Int = 2

@Serializable
data class VaultExport(
    val secrets: List<TotpSecret>,
    val passkeys: List<Passkey>,
    val format: Int = LATEST_BACKUP_FORMAT,
) {
    fun encode(): ByteArray =
        json.encodeToString(this).toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(data: ByteArray): VaultExport {
            val export = json.decodeFromString<VaultExport>(data.toString(Charsets.UTF_8))
            if (export.format > LATEST_BACKUP_FORMAT) {
                throw UnknownExportFormatException(
                    latestSupportedFormat = export.format,
                    actualFormat = LATEST_BACKUP_FORMAT
                )
            }
            return export
        }
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class UnknownExportFormatException(
    val latestSupportedFormat: Int,
    val actualFormat: Int,
) : Exception()

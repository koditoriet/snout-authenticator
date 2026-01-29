package se.koditoriet.snout.codec

import android.util.Base64
import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import se.koditoriet.snout.codec.Base64Url.Companion.toBase64Url
import se.koditoriet.snout.crypto.ECAlgorithm
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

private const val B64URL_FLAGS = Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE

@Serializable
@JvmInline
value class Base64Url private constructor(val string: String) {
    fun toByteArray(): ByteArray =
        Base64.decode(string, B64URL_FLAGS)

    class TypeConverters {
        @TypeConverter
        fun fromString(string: String): Base64Url =
            fromBase64UrlString(string)

        @TypeConverter
        fun toString(base64url: Base64Url): String =
            base64url.string
    }

    companion object {
        fun fromBase64UrlString(string: String): Base64Url =
            // Ensure that string is always valid base64url
            Base64.decode(string, B64URL_FLAGS).toBase64Url()
        
        fun ByteArray.toBase64Url(): Base64Url =
            Base64Url(Base64.encodeToString(this, B64URL_FLAGS))
    }
}

fun PublicKey.toBase64Url(): Base64Url =
    encoded.toBase64Url()

fun Base64Url.toPublicKey(algorithm: ECAlgorithm): PublicKey =
    KeyFactory.getInstance(algorithm.secretKeySpecName).run {
        val spec = X509EncodedKeySpec(toByteArray())
        generatePublic(spec)
    }

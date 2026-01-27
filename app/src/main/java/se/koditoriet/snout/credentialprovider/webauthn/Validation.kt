package se.koditoriet.snout.credentialprovider

import android.util.Log
import androidx.core.net.toUri
import androidx.credentials.provider.CallingAppInfo
import se.koditoriet.snout.codec.Base64Url.Companion.toBase64Url
import java.security.MessageDigest

private const val TAG = "WebAuthnValidation"

fun originIsValid(callingAppInfo: CallingAppInfo, rpId: String): Boolean {
    val origin = try {
        appInfoToOrigin(callingAppInfo)
    } catch (e: Exception) {
        Log.e(TAG, "Unable to get origin from calling app info", e)
        return false
    }

    if (origin.startsWith("android:apk-key-hash:")) {
        Log.e(TAG, "Android app origins are not supported yet")
        return false
    }

    val originUri = origin.toUri()

    if (originUri.scheme != "https" && originUri.host != "localhost") {
        Log.e(TAG, "Bad origin URI scheme (must be https): ${originUri.scheme}")
        return false
    }

    if (originUri.host != rpId && !originUri.host!!.endsWith(".$rpId")) {
        Log.e(TAG, "RP is not a suffix of the host part of the origin")
        return false
    }

    return true
}

fun rpIsValid(rpId: String): Boolean {
    return !rpId.startsWith(".")
}

fun appInfoToOrigin(callingAppInfo: CallingAppInfo): String {
    if (callingAppInfo.isOriginPopulated()) {
        return callingAppInfo.getOrigin(privilegedAllowlist)!!
    }

    // If origin is not populated, we're dealing with an app origin
    val cert = callingAppInfo.signingInfo.apkContentsSigners[0].toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val certHash = md.digest(cert)
    return "android:apk-key-hash:${certHash.toBase64Url().string}"
}

/**
 * Derives an rpId from a CallingAppInfo.
 * Only web origins are supported, and and we approximate the rpId by taking the host of the origin URI.
 */
fun appInfoToRpId(callingAppInfo: CallingAppInfo): String {
    require(callingAppInfo.isOriginPopulated())

    val origin = callingAppInfo.getOrigin(privilegedAllowlist)
    require(origin != null)

    val host = origin.toUri().host
    require(host != null)

    return host
}

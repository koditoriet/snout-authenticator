package se.koditoriet.snout.credentialprovider

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CredentialEntry
import androidx.credentials.provider.PublicKeyCredentialEntry
import org.json.JSONArray
import org.json.JSONObject
import se.koditoriet.snout.codec.Base64Url
import se.koditoriet.snout.vault.CredentialId
import se.koditoriet.snout.vault.Vault
import kotlin.random.Random

const val CREDENTIAL_DATA = "CREDENTIAL_DATA"
const val CREDENTIAL_ID = "CREDENTIAL_ID"
private const val TAG = "CredentialProviderUtil"

suspend fun createBeginGetCredentialResponse(
    vault: Vault,
    context: Context,
    request: BeginGetCredentialRequest,
): BeginGetCredentialResponse {
    Log.i(TAG, "Fetching passkeys from vault")
    val credentialEntries = request.beginGetCredentialOptions.flatMap {
        when (it) {
            is BeginGetPublicKeyCredentialOption -> getPasskeys(vault, context, it)
            else -> emptyList()
        }
    }
    Log.i(TAG, "Presenting ${credentialEntries.size} passkeys")
    return BeginGetCredentialResponse(credentialEntries)
}

private suspend fun getPasskeys(
    vault: Vault,
    context: Context,
    option: BeginGetPublicKeyCredentialOption,
): List<CredentialEntry> {
    Log.i(TAG, "Listing eligible passkeys")
    val request = JSONObject(option.requestJson)
    val allowedCredentials = request.optJSONArray("allowedCredentials")?.map {
        CredentialId.fromString(it.getString("id"))
    }
    if (allowedCredentials != null) {
        val allowed = allowedCredentials.joinToString(", ") { it.toString() }
        Log.i(TAG, "RP lists the following credentials as allowed: $allowed")
    } else {
        Log.i(TAG, "RP did not specify allowedCredentials")
    }

    return vault.getPasskeys(request["rpId"] as String).flatMap { passkey ->
        if (allowedCredentials?.contains(passkey.credentialId) ?: true) {
            val data = Bundle().apply { putString(CREDENTIAL_ID, passkey.credentialId.string) }
            listOf(
                PublicKeyCredentialEntry(
                    context = context,
                    username = passkey.userName,
                    pendingIntent = createPendingIntent(context, AuthenticateActivity::class.java, data),
                    beginGetPublicKeyCredentialOption = option,
                    displayName = passkey.displayName,
                )
            )
        } else {
            emptyList()
        }
    }
}

fun createPendingIntent(context: Context, cls: Class<*>, extra: Bundle? = null): PendingIntent =
    Intent(context, cls).run {
        Log.i(TAG, "Creating pending intent for ${cls.simpleName}")
        // TODO: move to strings
        setPackage("se.koditoriet.snout")
        if (extra != null) {
            Log.d(TAG, "Setting extra: $extra")
            putExtra(CREDENTIAL_DATA, extra)
        }
        PendingIntent.getActivity(
            context,
            Random.nextInt(),
            this,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> {
    val results = mutableListOf<T>()
    for (i in 0 ..< length()) {
        results.add(transform(getJSONObject(i)))
    }
    return results
}

package se.koditoriet.snout.codec

import android.net.Uri

val Uri.totpIssuer: String
    get() = path
        ?.split(':', limit = 2)
        ?.first()
        ?.trimStart('/') ?: throw IllegalArgumentException("path component is missing")

val Uri.totpAccount: String?
    get() = path
        ?.split(':', limit = 2)
        ?.drop(1)
        ?.firstOrNull()

val Uri.totpSecret: String
    get() = queryParameter("secret")

val Uri.totpDigits: Int
    get() = queryParameter("digits")
        .toIntOrNull() ?: throw IllegalArgumentException("query parameter 'digits' is not an integer")

val Uri.totpPeriod: Int
    get() = queryParameter("period")
        .toIntOrNull() ?: throw IllegalArgumentException("query parameter 'period' is not an integer")

val Uri.totpAlgorithm: String
    get() = queryParameter("algorithm")

private fun Uri.queryParameter(name: String): String =
    getQueryParameter(name) ?: throw IllegalArgumentException("query parameter '$name' is missing")

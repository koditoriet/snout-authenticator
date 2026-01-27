package se.koditoriet.snout.credentialprovider.webauthn

@JvmInline
value class AuthDataFlag(val flag: Int) {
    companion object {
        /**
         * User Presence
         */
        val UP: AuthDataFlag = AuthDataFlag(0x01)

        /**
         * User Verification
         */
        val UV: AuthDataFlag = AuthDataFlag(0x04)

        /**
         * Backup Eligibility
         */
        val BE: AuthDataFlag = AuthDataFlag(0x08)

        /**
         * Backup Status
         */
        val BS: AuthDataFlag = AuthDataFlag(0x10)

        /**
         * Attestation data attached (this is always set automatically for credential creation)
         */
        val AT: AuthDataFlag = AuthDataFlag(0x40)

        val defaultCreateFlags: Set<AuthDataFlag> = setOf(UP, UV, BE)
        val defaultAuthFlags: Set<AuthDataFlag> = setOf(UP, UV, BE)
    }
}

fun Set<AuthDataFlag>.toInt(): Int =
    fold(0) { a, b -> a or b.flag }

fun Set<AuthDataFlag>.toByte(): Byte =
    toInt().toByte()

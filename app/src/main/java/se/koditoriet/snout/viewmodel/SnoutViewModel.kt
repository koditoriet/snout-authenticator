package se.koditoriet.snout.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import se.koditoriet.snout.Config
import se.koditoriet.snout.Config.BackupKeys
import se.koditoriet.snout.SnoutApp
import se.koditoriet.snout.SortMode
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.crypto.AuthenticatorFactory
import se.koditoriet.snout.crypto.BackupSeed
import se.koditoriet.snout.crypto.EncryptedData
import se.koditoriet.snout.crypto.KeySecurityLevel
import se.koditoriet.snout.synchronization.Sync
import se.koditoriet.snout.vault.CredentialId
import se.koditoriet.snout.vault.NewTotpSecret
import se.koditoriet.snout.vault.Passkey
import se.koditoriet.snout.vault.TotpAlgorithm
import se.koditoriet.snout.vault.TotpSecret
import se.koditoriet.snout.vault.Vault
import kotlin.time.Clock

private const val TAG = "SnoutViewModel"

@OptIn(ExperimentalCoroutinesApi::class)
class SnoutViewModel(private val app: Application) : AndroidViewModel(app) {
    private val vault: Sync<Vault>
        get() = (app as SnoutApp).vault

    private val configDatastore = (app as SnoutApp).config
    val config: Flow<Config>
        get() = configDatastore.data

    val vaultState = vault.unsafeReadOnly { observeState() }
    val secrets = vault.unsafeReadOnly { observeTotpSecrets() }
    val passkeys = vault.unsafeReadOnly { observePasskeys() }

    private val strings = app.appStrings.viewModel

    suspend fun createVault(backupSeed: BackupSeed?) = vault.withLock {
        Log.i(TAG, "Creating vault; enable backups: ${backupSeed != null}")
        val (dbKey, backupKeys) = create(
            requiresAuthentication = config.first().protectAccountList,
            backupSeed = backupSeed,
        )
        configDatastore.updateData {
            it.copy(
                encryptedDbKey = dbKey,
                backupKeys = backupKeys?.let(BackupKeys::fromVaultBackupKeys),
            )
        }
    }

    suspend fun wipeVault() = vault.withLock {
        Log.i(TAG, "Wiping vault")
        wipe()
        configDatastore.updateData { Config.default }
    }

    suspend fun lockVault() = vault.withLock {
        Log.i(TAG, "Locking vault")
        lock()
    }

    suspend fun setSortMode(sortMode: SortMode) = vault.withLock {
        configDatastore.updateData { it.copy(sortMode = sortMode) }
    }

    suspend fun unlockVault(authFactory: AuthenticatorFactory) = vault.withLock {
        if (state == Vault.State.Unlocked) {
            return@withLock
        }
        Log.i(TAG, "Attempting to unlock vault")
        val config = config.first()
        check(config.encryptedDbKey != null)
        authFactory.withReason(
            reason = strings.authUnlockVault,
            subtitle = strings.authUnlockVaultSubtitle,
        ) {
            unlock(it, config.encryptedDbKey, config.backupKeys?.toVaultBackupKeys())
        }
        Log.i(TAG, "Vault unlocked")
    }

    suspend fun exportVault(uri: Uri): Unit = vault.withLock {
        Log.i(TAG, "Exporting backup to $uri")
        app.contentResolver.openOutputStream(uri)!!.use { stream ->
            stream.write(export().encode().toByteArray())
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun importFromFile(uri: Uri): Unit = vault.withLock {
        Log.i(TAG, "Importing secrets from file $uri")
        check(config.first().enableDeveloperFeatures) {
            "tried to use developer feature without being a developer"
        }
        check(state == Vault.State.Unlocked)
        app.contentResolver.openInputStream(uri)!!.use { stream ->
            // Only JSON supported for now
            Json.decodeFromStream<Map<String, JsonImportItem>>(stream).forEach {
                Log.d(TAG, "Adding secret '${it.key} (${it.value.account})'")
                val account = it.value.toNewTotpSecret(it.key)
                addTotpSecret(account)
                account.secretData.secret.fill('\u0000')
            }
        }
    }

    suspend fun restoreVaultFromBackup(backupSeed: BackupSeed, uri: Uri): Unit = vault.withLock {
        Log.i(TAG, "Restoring vault from backup")
        try {
            val backupData = app.contentResolver.openInputStream(uri)!!.use { stream ->
                EncryptedData.decode(stream.readBytes().decodeToString())
            }
            val (dbKey, backupKeys) = create(
                requiresAuthentication = config.first().protectAccountList,
                backupSeed = backupSeed,
            )

            Log.d(TAG, "Importing vault data")
            import(backupSeed, backupData)
            Log.d(TAG, "Updating config data")
            configDatastore.updateData {
                it.copy(
                    encryptedDbKey = dbKey,
                    backupKeys = BackupKeys.fromVaultBackupKeys(backupKeys!!)
                )
            }
            Log.i(TAG, "Backup successfully imported")
        } catch (e: Exception) {
            // Make sure we go back to a clean slate if something went wrong
            // TODO: inform the user what happened
            Log.e(TAG, "Unable to restore backup", e)
            wipe()
        }
    }

    suspend fun addTotpSecret(newSecret: NewTotpSecret) = vault.withLock {
        Log.d(TAG, "Adding new TOTP secret")
        addTotpSecret(newSecret)
    }

    suspend fun updateTotpSecret(totpSecret: TotpSecret) = vault.withLock {
        Log.d(TAG, "Updating TOTP secret with id ${totpSecret.id}")
        updateSecret(totpSecret)
    }

    suspend fun deleteTotpSecret(id: TotpSecret.Id) = vault.withLock {
        Log.d(TAG, "Deleting TOTP secret with id $id")
        deleteSecret(id)
    }

    suspend fun getTotpCodes(
        authFactory: AuthenticatorFactory,
        totpSecret: TotpSecret,
        codes: Int,
        clock: Clock = Clock.System,
    ): List<String> {
        require(codes >= 2)
        Log.d(TAG, "Generating $codes codes for TOTP secret with id ${totpSecret.id}")
        return vault.withLock {
            authFactory.withReason(
                reason = strings.authRevealCode,
                subtitle = strings.authRevealCodeSubtitle,
            ) {
                getTotpCodes(it, totpSecret, clock::now, codes)
            }
        }
    }

    suspend fun getPasskey(credentialId: CredentialId) = vault.withLock {
        getPasskey(credentialId)
    }

    suspend fun deletePasskey(credentialId: CredentialId) = vault.withLock {
        deletePasskey(credentialId)
    }

    suspend fun updatePasskey(passkey: Passkey) = vault.withLock {
        updatePasskey(passkey)
    }

    suspend fun addPasskey(rpId: String, userId: ByteArray, userName: String, displayName: String) = vault.withLock {
        addPasskey(rpId, userId, userName, displayName)
    }

    suspend fun signWithPasskey(authFactory: AuthenticatorFactory, passkey: Passkey, data: ByteArray) = vault.withLock {
        // TODO: explain where and as who the user is signing in?
        authFactory.withReason(strings.authUsePasskey, strings.authUsePasskeySubtitle) {
            signWithPasskey(it, passkey, data)
        }
    }

    suspend fun getSecurityReport(): SecurityReport = vault.withLock {
        getSecurityReport()
    }

    suspend fun disableBackups() = vault.withLock {
        Log.i(TAG, "Disabling backups")
        configDatastore.updateData {
            Log.d(TAG, "Wiping backup keys")
            it.copy(backupKeys = null)
        }
        Log.d(TAG, "Erasing encrypted backup secrets")
        eraseBackups()
    }


    suspend fun setLockOnClose(enabled: Boolean) = vault.withLock {
        configDatastore.updateData {
            it.copy(lockOnClose = enabled)
        }
    }

    suspend fun setLockOnCloseGracePeriod(gracePeriod: Int) = vault.withLock {
        configDatastore.updateData {
            it.copy(lockOnCloseGracePeriod = gracePeriod)
        }
    }

    suspend fun setScreenSecurity(screenSecurityEnabled: Boolean) = vault.withLock {
        configDatastore.updateData {
            it.copy(screenSecurityEnabled = screenSecurityEnabled)
        }
    }

    suspend fun setHideSecretsFromAccessibility(hideSecretsFromAccessibility: Boolean) = vault.withLock {
        configDatastore.updateData {
            it.copy(hideSecretsFromAccessibility = hideSecretsFromAccessibility)
        }
    }

    suspend fun setEnableDeveloperFeatures(enableDeveloperFeatures: Boolean) = vault.withLock {
        configDatastore.updateData {
            it.copy(enableDeveloperFeatures = enableDeveloperFeatures)
        }
    }

    suspend fun rekeyVault(authFactory: AuthenticatorFactory, requireAuthentication: Boolean) = vault.withLock {
        Log.i(TAG, "Rekeying vault")
        val dbKey = authFactory.withReason(
            reason = strings.authToggleBioprompt(requireAuthentication),
            subtitle = strings.authToggleBiopromptSubtitle(requireAuthentication),
        ) {
            rekey(it, requireAuthentication)
        }
        configDatastore.updateData {
            it.copy(
                encryptedDbKey = dbKey,
                protectAccountList = requireAuthentication,
            )
        }
    }
}

data class SecurityReport(
    val backupKeyStatus: KeySecurityLevel?,
    val secretsStatus: Map<KeySecurityLevel, Int>,
    val passkeysStatus: Map<KeySecurityLevel, Int>,
) {
    val totalSecrets: Int
        get() = secretsStatus.values.sum()

    val totalPasskeys: Int
        get() = passkeysStatus.values.sum()
}

@Serializable
class JsonImportItem(
    val secret: String,
    val account: String? = null,
    val digits: Int = 6,
    val period: Int = 30,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
) {
    fun toNewTotpSecret(issuer: String): NewTotpSecret =
        NewTotpSecret(
            metadata = NewTotpSecret.Metadata(
                issuer = issuer,
                account = account,
            ),
            secretData = NewTotpSecret.SecretData(
                secret = secret.toCharArray(),
                digits = digits,
                period = period,
                algorithm = algorithm,
            )
        )
}

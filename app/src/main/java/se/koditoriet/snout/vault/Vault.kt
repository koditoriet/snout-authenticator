package se.koditoriet.snout.vault

import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import se.koditoriet.snout.DbKey
import se.koditoriet.snout.codec.Base64Url.Companion.toBase64Url
import se.koditoriet.snout.codec.base32Decode
import se.koditoriet.snout.codec.toBase64Url
import se.koditoriet.snout.crypto.Authenticator
import se.koditoriet.snout.crypto.BackupSeed
import se.koditoriet.snout.crypto.Cryptographer
import se.koditoriet.snout.crypto.DecryptionContext
import se.koditoriet.snout.crypto.DummyAuthenticator
import se.koditoriet.snout.crypto.EncryptedData
import se.koditoriet.snout.crypto.EncryptionAlgorithm
import se.koditoriet.snout.crypto.HmacAlgorithm
import se.koditoriet.snout.crypto.KeyHandle
import se.koditoriet.snout.crypto.KeyIdentifier
import se.koditoriet.snout.crypto.generateTotpCode
import se.koditoriet.snout.repository.Passkeys
import se.koditoriet.snout.repository.TotpSecrets
import se.koditoriet.snout.repository.VaultRepository
import se.koditoriet.snout.viewmodel.SecurityReport
import java.io.File
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val DB_KEK_IDENTIFIER = KeyIdentifier.Internal("db_kek")
private val BACKUP_SECRET_DEK_IDENTIFIER = KeyIdentifier.Internal("backup_secret_dek")
private val BACKUP_METADATA_DEK_IDENTIFIER = KeyIdentifier.Internal("backup_metadata_dek")
private val INTERNAL_SYMMETRIC_KEY_ALGORITHM = EncryptionAlgorithm.AES_GCM
private const val DB_DEK_SIZE = 32
private const val TAG = "Vault"

@OptIn(ExperimentalCoroutinesApi::class)
class Vault(
    private val repositoryFactory: (dbFile: String, dbKey: ByteArray) -> VaultRepository,
    private val cryptographer: Cryptographer,
    private val dbFile: Lazy<File>,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    private val _state = MutableStateFlow(
        if (!cryptographer.isInitialized()) {
            InternalState.Uninitialized
        } else {
            InternalState.Locked
        }
    )

    val state: State
        get() = _state.value.public

    private val unlockState: UnlockState?
        get() = _state.value.unlockState

    suspend fun unlock(authenticator: Authenticator, dbKey: DbKey, backupKeys: BackupKeys?) {
        check(_state.value != InternalState.Uninitialized)

        if (_state.value !is InternalState.Unlocked) {
            Log.i(TAG, "Unlocking vault")
            val repository = withDbDek(authenticator, dbKey) {
                repositoryFactory(dbFile.value.name, it)
            }
            _state.value = InternalState.Unlocked(UnlockState(dbKey, backupKeys, repository))
        }
    }

    fun lock() {
        check(_state.value != InternalState.Uninitialized)
        Log.i(TAG, "Locking vault")
        unlockState?.repository?.close()
        _state.value = InternalState.Locked
    }

    fun observeState(): Flow<State> =
        _state.asStateFlow().map { it.public }

    fun observePasskeys(): Flow<List<Passkey>> =
        _state.asStateFlow().flatMapLatest {
            when (it) {
                InternalState.Locked -> flowOf(emptyList())
                InternalState.Uninitialized -> flowOf(emptyList())
                is InternalState.Unlocked -> it.unlockState.repository.passkeys().observeAll()
            }
        }

    fun observeTotpSecrets(): Flow<List<TotpSecret>> =
        _state.asStateFlow().flatMapLatest {
            when (it) {
                InternalState.Locked -> flowOf(emptyList())
                InternalState.Uninitialized -> flowOf(emptyList())
                is InternalState.Unlocked -> it.unlockState.repository.totpSecrets().observeAll()
            }
        }

    suspend fun getPasskeys(rpId: String): List<Passkey> = withPasskeyRepository {
        it.getAll(rpId)
    }

    suspend fun reindexPasskeys() = withPasskeyRepository { passkeys ->
        Log.i(TAG, "Reindexing passkeys")
        passkeys.reindexSortOrder()
    }

    suspend fun addPasskey(
        rpId: String,
        userId: ByteArray,
        userName: String,
        displayName: String,
    ): Pair<CredentialId, ECPublicKey> = withPasskeyRepository { passkeys ->
        val keyPairInfo = cryptographer.generateECKeyPair(
            keyIdentifier = null,
            requiresAuthentication = true,
            allowDeviceCredential = true,
            backupKeyHandle = unlockState?.backupKeys?.secretsBackupKey
        )
        val credentialId = ByteArray(32).run {
            secureRandom.nextBytes(this)
            CredentialId(toBase64Url())
        }
        val passkey = Passkey(
            credentialId = credentialId,
            sortOrder = passkeys.getNextSortOrder(),
            userId = UserId(userId.toBase64Url()),
            userName = userName,
            displayName = displayName,
            rpId = rpId,
            keyAlias = keyPairInfo.keyHandle.alias,
            publicKey = keyPairInfo.publicKey.toBase64Url(),
            encryptedBackupPrivateKey = keyPairInfo.encryptedPrivateKey?.encode(),
        )
        passkeys.insert(passkey)
        Pair(credentialId, keyPairInfo.publicKey)
    }

    suspend fun signWithPasskey(
        authenticator: Authenticator,
        passkey: Passkey,
        data: ByteArray,
    ): ByteArray = withPasskeyRepository {
        Log.i(TAG, "Signing data with key ${passkey.keyAlias}")
        cryptographer.withSigningKey(authenticator, passkey.keyHandle) {
            sign(data)
        }
    }

    suspend fun getPasskey(credentialId: CredentialId) = withPasskeyRepository { passkeys ->
        Log.i(TAG, "Fetching passkey with credential ID $credentialId")
        passkeys.get(credentialId)
    }

    suspend fun updatePasskey(passkey: Passkey) = withPasskeyRepository { passkeys ->
        Log.i(TAG, "Updating passkey with credential ID ${passkey.credentialId}")
        passkeys.update(passkey)
    }

    suspend fun deletePasskey(credentialId: CredentialId) = withPasskeyRepository { passkeys ->
        Log.i(TAG, "Deleting passkey with credential ID $credentialId")
        val passkey = passkeys.get(credentialId)
        cryptographer.deleteKey(passkey.keyHandle)
        passkeys.delete(credentialId)
    }

    suspend fun addTotpSecret(newSecret: NewTotpSecret) = withTotpSecretRepository { secrets ->
        Log.i(TAG, "Adding new TOTP secret")
        val secretBytes = base32Decode(newSecret.secretData.secret)
        val encryptedSecret = encryptBackupSecretIfEnabled(secretBytes)?.encode()

        val keyHandle = cryptographer.storeHmacKey(
            keyIdentifier = null,
            hmacAlgorithm = newSecret.secretData.algorithm.toHmacAlgorithm(),
            allowDeviceCredential = true, // TODO: make this configurable?
            requiresAuthentication = true,
            keyMaterial = secretBytes,
        )
        secretBytes.fill(0)

        val secret = TotpSecret(
            id = TotpSecret.Id.None,
            sortOrder = secrets.getNextSortOrder(),
            issuer = newSecret.metadata.issuer,
            account = newSecret.metadata.account,
            digits = newSecret.secretData.digits,
            period = newSecret.secretData.period,
            algorithm = newSecret.secretData.algorithm,
            keyAlias = keyHandle.alias,
            encryptedBackupSecret = encryptedSecret,
        )
        secrets.insert(secret)
    }

    suspend fun updateSecret(totpSecret: TotpSecret) = withTotpSecretRepository { secrets ->
        Log.i(TAG, "Update TOTP secret with id ${totpSecret.id}")
        secrets.update(totpSecret)
    }

    suspend fun deleteSecret(id: TotpSecret.Id) = withTotpSecretRepository { secrets ->
        Log.i(TAG, "Delete TOTP secret with id $id")
        val secret = secrets.get(id)
        cryptographer.deleteKey(secret.keyHandle)
        secrets.delete(id)
    }

    suspend fun reindexSecrets() = withTotpSecretRepository { secrets ->
        Log.i(TAG, "Reindexing TOTP secrets")
        secrets.reindexSortOrder()
    }

    suspend fun getTotpCodes(
        authenticator: Authenticator,
        totpSecret: TotpSecret,
        now: () -> Instant,
        codes: Int,
    ): List<String> = requireUnlocked {
        Log.i(TAG, "Fetching the next $codes TOTP codes for secret with id ${totpSecret.id}")
        cryptographer.withHmacKey(authenticator, totpSecret.keyHandle) {
            val t = now()
            Log.i(TAG, "Codes for TOTP secret with id ${totpSecret.id} start at timestamp $t")
            0.rangeUntil(codes).map { n ->
                val timestamp = t + (n * totpSecret.period).seconds
                generateTotpCode(totpSecret, timestamp)
            }
        }
    }

    suspend fun create(requiresAuthentication: Boolean, backupSeed: BackupSeed?): Pair<DbKey, BackupKeys?> {
        check(state == State.Uninitialized)
        Log.i(TAG, "Creating new vault with backups ${backupSeed?.let { "enabled" } ?: "disabled"}")
        if (requiresAuthentication) {
            Log.i(TAG, "New vault requires authentication to list accounts")
        } else {
            Log.i(TAG, "New vault does not require authentication to list accounts")
        }
        val backupKeys = backupSeed?.let {
            val secretDekMaterial = backupSeed.deriveBackupSecretKey()
            val secretsBackupKey = createBackupKey(secretDekMaterial, BACKUP_SECRET_DEK_IDENTIFIER)
            secretDekMaterial.fill(0)

            val metadataDekMaterial = backupSeed.deriveBackupMetadataKey()
            val metadataBackupKey = createBackupKey(metadataDekMaterial, BACKUP_METADATA_DEK_IDENTIFIER)
            metadataDekMaterial.fill(0)
            BackupKeys(secretsBackupKey, metadataBackupKey)
        }
        val (dbKey, dbDekPlaintext) = createDbKey(requiresAuthentication)
        val repository = repositoryFactory(dbFile.value.name, dbDekPlaintext)
        dbDekPlaintext.fill(0)
        _state.value = InternalState.Unlocked(UnlockState(dbKey, backupKeys, repository))
        return Pair(dbKey, backupKeys)
    }

    suspend fun rekey(
        authenticator: Authenticator,
        requiresAuthentication: Boolean,
    ): DbKey = requireUnlocked { oldUnlockState ->
        check(state == State.Unlocked)
        Log.i(TAG, "Rekeying vault")
        withDbDek(authenticator, oldUnlockState.dbKey) {
            Log.d(TAG, "Deleting old DB KEK")
            cryptographer.deleteKey(KeyHandle.fromAlias<EncryptionAlgorithm>(oldUnlockState.dbKey.kekAlias))
            createDbKey(requiresAuthentication).first.apply {
                _state.value = InternalState.Unlocked(oldUnlockState.copy(dbKey = this))
            }
        }
    }

    fun wipe() {
        unlockState?.repository?.close()
        cryptographer.wipeKeys()
        if (dbFile.value.exists()) {
            dbFile.value.delete()
        }
        _state.value = InternalState.Uninitialized
    }

    suspend fun eraseBackups(): Unit = requireUnlocked { unlockState ->
        unlockState.backupKeys?.let {
            cryptographer.deleteKey(it.metadataBackupKey)
            cryptographer.deleteKey(it.secretsBackupKey)
        }
        unlockState.repository.totpSecrets().eraseBackups()
    }

    suspend fun export(): EncryptedData = requireUnlocked { unlockState ->
        requireBackupsEnabled { backupKeys ->
            val vaultExport = VaultExport(
                secrets = unlockState.repository.totpSecrets().getAll(),
                passkeys = unlockState.repository.passkeys().getAll(),
            )
            Log.i(TAG, "Exporting ${vaultExport.secrets.size} secrets and ${vaultExport.passkeys.size} passkeys")
            val vaultExportBytes = vaultExport.encode()
            cryptographer.withEncryptionKey(DummyAuthenticator, backupKeys.metadataBackupKey) {
                encrypt(vaultExportBytes)
            }
        }
    }

    suspend fun import(backupSeed: BackupSeed, data: EncryptedData) = requireUnlocked { unlockState ->
        Log.i(TAG, "Importing backup")
        val metadataKeyMaterial = backupSeed.deriveBackupMetadataKey()
        val vaultExport = cryptographer.withDecryptionKey(metadataKeyMaterial, INTERNAL_SYMMETRIC_KEY_ALGORITHM) {
            VaultExport.decode(decrypt(data))
        }

        Log.d(TAG, "Backup metadata successfully decoded")
        metadataKeyMaterial.fill(0)

        val secretKeyMaterial = backupSeed.deriveBackupSecretKey()
        val secretDecryptionContext = DecryptionContext.create(secretKeyMaterial, INTERNAL_SYMMETRIC_KEY_ALGORITHM)
        importTotpSecrets(vaultExport.secrets, secretDecryptionContext, unlockState)
        importPasskeys(vaultExport.passkeys, secretDecryptionContext, unlockState)
        secretKeyMaterial.fill(0)
    }

    private suspend fun importTotpSecrets(
        secrets: List<TotpSecret>,
        secretDecryptionContext: DecryptionContext,
        unlockState: UnlockState,
    ) {
        var failedImportedSecrets = 0
        for (secret in secrets.sortedBy { it.sortOrder }) {
            try {
                secretDecryptionContext.importSecret(
                    unlockState.repository.totpSecrets(),
                    secret
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import secret with id ${secret.id} and key alias ${secret.keyAlias}!", e)
                failedImportedSecrets += 1
            }
        }

        if (failedImportedSecrets > 0) {
            Log.e(TAG, "Failed to import $failedImportedSecrets/${secrets.size} TOTP secrets!")
            throw ImportFailedException()
        } else {
            Log.i(TAG, "Successfully imported ${secrets.size} TOTP secrets")
        }
    }

    private suspend fun importPasskeys(
        passkeys: List<Passkey>,
        secretDecryptionContext: DecryptionContext,
        unlockState: UnlockState,
    ) {
        var failedImportedPasskeys = 0
        for (passkey in passkeys.sortedBy { it.sortOrder }) {
            try {
                secretDecryptionContext.importPasskey(
                    unlockState.repository.passkeys(),
                    passkey
                )
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Failed to import passkey with credential id ${passkey.credentialId}" +
                            " and key alias ${passkey.keyAlias}!",
                    e
                )
                failedImportedPasskeys += 1
            }
        }

        if (failedImportedPasskeys > 0) {
            Log.e(TAG, "Failed to import $failedImportedPasskeys/${passkeys.size} passkeys!")
            throw ImportFailedException()
        } else {
            Log.i(TAG, "Successfully imported ${passkeys.size} passkeys")
        }
    }

    suspend fun getSecurityReport(): SecurityReport = requireUnlocked { unlockState ->
        Log.i(TAG, "Generating security report")
        val backupKeysSecurityLevel = unlockState
            .backupKeys
            ?.let { listOf(it.secretsBackupKey.alias, it.metadataBackupKey.alias) }
            ?.map { KeyHandle.fromAlias<EncryptionAlgorithm>(it) }
            ?.minOfOrNull { cryptographer.getKeySecurityLevel(it) }

        SecurityReport(
            backupKeyStatus = backupKeysSecurityLevel,
            secretsStatus = unlockState.repository.totpSecrets().getAll()
                .groupBy { cryptographer.getKeySecurityLevel(it.keyHandle) }
                .mapValues { it.value.size },
            passkeysStatus = unlockState.repository.passkeys().getAll()
                .groupBy { cryptographer.getKeySecurityLevel(it.keyHandle) }
                .mapValues { it.value.size },
        )
    }

    private suspend fun DecryptionContext.importSecret(secrets: TotpSecrets, secret: TotpSecret) {
        val secretBytes = decrypt(EncryptedData.decode(secret.encryptedBackupSecret!!))
        val encryptedSecret = encryptBackupSecretIfEnabled(secretBytes)?.encode()
        val newKeyHandle = cryptographer.storeHmacKey(
            keyIdentifier = null,
            hmacAlgorithm = secret.algorithm.toHmacAlgorithm(),
            allowDeviceCredential = true,
            requiresAuthentication = true,
            keyMaterial = secretBytes
        )
        secretBytes.fill(0)
        val newSecret = secret.copy(
            sortOrder = secrets.getNextSortOrder(),
            keyAlias = newKeyHandle.alias,
            encryptedBackupSecret = encryptedSecret,
        )
        secrets.insert(newSecret)
        Log.d(TAG, "Imported TOTP secret with id ${newSecret.id} and key alias ${newSecret.keyAlias}")
    }

    private suspend fun DecryptionContext.importPasskey(passkeys: Passkeys, passkey: Passkey) {
        val privateKeyBytes = decrypt(EncryptedData.decode(passkey.encryptedBackupPrivateKey!!))
        val encryptedPrivateKey = encryptBackupSecretIfEnabled(privateKeyBytes)?.encode()
        val newKeyHandle = cryptographer.storePrivateKey(
            keyIdentifier = null,
            algorithm = passkey.keyHandle.algorithm,
            allowDeviceCredential = true,
            requiresAuthentication = true,
            keyMaterial = privateKeyBytes,
        )
        privateKeyBytes.fill(0)
        val newPasskey = passkey.copy(
            sortOrder = passkeys.getNextSortOrder(),
            keyAlias = newKeyHandle.alias,
            encryptedBackupPrivateKey = encryptedPrivateKey,
        )
        passkeys.insert(newPasskey)
        Log.d(TAG, "Imported passkey with id ${newPasskey.credentialId} and key alias ${newPasskey.keyAlias}")
    }

    private suspend fun encryptBackupSecretIfEnabled(secretBytes: ByteArray): EncryptedData? =
        unlockState?.backupKeys?.let {
            Log.d(TAG, "Backups are enabled; encrypting secret")
            cryptographer.withEncryptionKey(DummyAuthenticator, it.secretsBackupKey) {
                encrypt(secretBytes)
            }
        }

    private fun createBackupKey(
        keyMaterial: ByteArray,
        keyIdentifier: KeyIdentifier,
    ): KeyHandle<EncryptionAlgorithm> =
        cryptographer.storeSymmetricKey(
            keyIdentifier = keyIdentifier,
            allowDecrypt = false,
            allowDeviceCredential = true,
            requiresAuthentication = false,
            keyMaterial = keyMaterial,
        )

    private suspend fun createDbKey(requiresAuthenticaton: Boolean): Pair<DbKey, ByteArray> {
        Log.d(TAG, "Generating new $INTERNAL_SYMMETRIC_KEY_ALGORITHM DB key")
        val kekSizeBytes = INTERNAL_SYMMETRIC_KEY_ALGORITHM.keySize / 8
        val dbKekKeyMaterial = ByteArray(kekSizeBytes).apply(secureRandom::nextBytes)
        val dbDekPlaintext = ByteArray(DB_DEK_SIZE).apply(secureRandom::nextBytes)
        val dbKekHandle = cryptographer.storeSymmetricKey(
            keyIdentifier = DB_KEK_IDENTIFIER,
            allowDecrypt = true,
            allowDeviceCredential = true,
            requiresAuthentication = requiresAuthenticaton,
            keyMaterial = dbKekKeyMaterial,
            algorithm = INTERNAL_SYMMETRIC_KEY_ALGORITHM,
        )

        val dbDekCiphertext = cryptographer.withEncryptionKey(dbKekKeyMaterial, INTERNAL_SYMMETRIC_KEY_ALGORITHM) {
            encrypt(dbDekPlaintext)
        }
        val dbKey = DbKey(
            kekAlias = dbKekHandle.alias,
            encryptedDek = dbDekCiphertext.encode(),
        )
        return Pair(dbKey, dbDekPlaintext)
    }

    private suspend fun <T> withDbDek(
        authenticator: Authenticator,
        dbKey: DbKey,
        action: suspend (ByteArray) -> T,
    ): T {
        val decodedDbDek = EncryptedData.decode(dbKey.encryptedDek)
        val dbKekHandle = KeyHandle.fromAlias<EncryptionAlgorithm>(dbKey.kekAlias)
        return cryptographer.withDecryptionKey(authenticator, dbKekHandle) {
            val plaintextDek = decrypt(decodedDbDek)
            try {
                action(plaintextDek)
            } finally {
                plaintextDek.fill(0)
            }
        }
    }

    private suspend fun <T> withTotpSecretRepository(action: suspend (TotpSecrets) -> T): T = requireUnlocked {
        action(it.repository.totpSecrets())
    }

    private suspend fun <T> withPasskeyRepository(action: suspend (Passkeys) -> T): T = requireUnlocked {
        action(it.repository.passkeys())
    }

    private suspend fun <T> requireUnlocked(action: suspend (UnlockState) -> T): T {
        check(state == State.Unlocked) {
            "operation requires vault to be unlocked"
        }
        return action(unlockState!!)
    }

    private suspend fun <T> requireBackupsEnabled(action: suspend (BackupKeys) -> T): T = requireUnlocked { state ->
        check(state.backupKeys != null) {
            "operation requires backups to be enabled"
        }
        action(state.backupKeys)
    }

    private sealed class InternalState(val public: State) {
        object Uninitialized : InternalState(State.Uninitialized)
        object Locked : InternalState(State.Locked)
        class Unlocked(val unlockState: UnlockState) : InternalState(State.Unlocked)
    }

    private val InternalState.unlockState: UnlockState?
        get() = when (this) {
            is InternalState.Unlocked -> unlockState
            else -> null
        }

    enum class State {
        Uninitialized,
        Locked,
        Unlocked
    }

    private data class UnlockState(
        val dbKey: DbKey,
        val backupKeys: BackupKeys?,
        val repository: VaultRepository,
    )

    data class BackupKeys(
        val secretsBackupKey: KeyHandle<EncryptionAlgorithm>,
        val metadataBackupKey: KeyHandle<EncryptionAlgorithm>,
    )
}

fun TotpAlgorithm.toHmacAlgorithm(): HmacAlgorithm = when (this) {
    TotpAlgorithm.SHA1 -> HmacAlgorithm.SHA1
    TotpAlgorithm.SHA256 -> HmacAlgorithm.SHA256
    TotpAlgorithm.SHA512 -> HmacAlgorithm.SHA512
}

class ImportFailedException() : Exception()

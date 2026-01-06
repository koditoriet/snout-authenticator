package se.koditoriet.snout.vault

import se.koditoriet.snout.DbKey
import se.koditoriet.snout.codec.base32Decode
import se.koditoriet.snout.crypto.BackupSeed
import se.koditoriet.snout.crypto.EncryptedData
import se.koditoriet.snout.crypto.HmacAlgorithm
import se.koditoriet.snout.crypto.generateTotpCode
import se.koditoriet.snout.crypto.KeyHandle
import se.koditoriet.snout.crypto.KeyIdentifier
import se.koditoriet.snout.crypto.Cryptographer
import se.koditoriet.snout.crypto.DecryptionContext
import se.koditoriet.snout.crypto.SymmetricAlgorithm
import se.koditoriet.snout.repository.TotpSecrets
import se.koditoriet.snout.repository.VaultRepository
import java.io.File
import java.security.SecureRandom
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val DB_KEK_IDENTIFIER = KeyIdentifier.Internal("db_kek")
private val BACKUP_SECRET_DEK_IDENTIFIER = KeyIdentifier.Internal("backup_secret_dek")
private val BACKUP_METADATA_DEK_IDENTIFIER = KeyIdentifier.Internal("backup_metadata_dek")
private val INTERNAL_SYMMETRIC_KEY_ALGORITHM = SymmetricAlgorithm.AES_GCM
private const val DB_DEK_SIZE = 32

class Vault(
    private val repositoryFactory: (dbFile: String, dbKey: ByteArray) -> VaultRepository,
    private val cryptographer: Cryptographer,
    private val dbFile: Lazy<File>,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    private var unlockState: UnlockState? = null

    val state: State
        get() {
            if (!cryptographer.isInitialized()) {
                return State.Uninitialized
            }
            if (unlockState == null) {
                return State.Locked
            }
            return State.Unlocked
        }

    suspend fun unlock(dbKey: DbKey, backupKeys: BackupKeys?) {
        check(state != State.Uninitialized)
        if (state == State.Unlocked) {
            return
        }

        val dbDek = decryptDbDek(dbKey)
        val repository = repositoryFactory(dbFile.value.name, dbDek)
        dbDek.fill(0)
        unlockState = UnlockState(dbKey, backupKeys, repository)
    }

    fun lock() {
        check(state != State.Uninitialized)
        if (state == State.Locked) {
            return
        }
        unlockState!!.repository.close()
        unlockState = null
    }

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun addTotpSecret(newSecret: NewTotpSecret) = withRepository { secrets ->
        val secretBytes = base32Decode(newSecret.secretData.secret)
        val encryptedSecret = encryptBackupSecretIfEnabled(secretBytes)?.encode()

        val keyHandle = cryptographer.storeHmacKey(
            keyIdentifier = null,
            hmacAlgorithm = newSecret.secretData.algorithm.toHmacAlgorithm(),
            allowDeviceCredential = true, // TODO: make this configurable?
            requiresAuthentication = true, // TODO: make this configurable?
            keyMaterial = secretBytes,
        )
        secretBytes.fill(0)

        val secret = TotpSecret(
            id = 0,
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

    suspend fun updateSecret(totpSecret: TotpSecret) = withRepository { secrets ->
        secrets.update(totpSecret)
    }

    suspend fun deleteSecret(totpSecret: TotpSecret) = withRepository { secrets ->
        secrets.delete(totpSecret)
    }

    suspend fun getTotpSecrets(): List<TotpSecret> = withRepository { secrets ->
        secrets.getAll()
    }

    suspend fun getTotpCodes(totpSecret: TotpSecret, now: () -> Instant, codes: Int): List<String> = requireUnlocked {
        cryptographer.withHmacKey(totpSecret.keyHandle) {
            0.rangeUntil(codes).map { n ->
                val t = now()
                val timestamp = t + (n * totpSecret.period).seconds
                generateTotpCode(totpSecret, timestamp)
            }
        }
    }

    suspend fun create(requiresAuthentication: Boolean, backupSeed: BackupSeed?): Pair<DbKey, BackupKeys?> {
        check(state == State.Uninitialized)
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
        unlockState = UnlockState(dbKey, backupKeys, repository)
        return Pair(dbKey, backupKeys)
    }

    suspend fun rekey(requiresAuthentication: Boolean): DbKey = requireUnlocked { oldUnlockState ->
        check(state == State.Unlocked)
        val dbDekPlaintext = decryptDbDek(oldUnlockState.dbKey)
        cryptographer.deleteKey(KeyHandle.fromAlias<SymmetricAlgorithm>(oldUnlockState.dbKey.kekAlias))
        createDbKey(requiresAuthentication).first.apply {
            unlockState = oldUnlockState.copy(dbKey = this)
            dbDekPlaintext.fill(0)
        }
    }

    fun wipe() {
        unlockState?.repository?.close()
        unlockState = null
        cryptographer.wipeKeys()
        if (dbFile.value.exists()) {
            dbFile.value.delete()
        }
    }

    suspend fun eraseBackups(): Unit = requireUnlocked { unlockState ->
        unlockState.backupKeys?.let {
            cryptographer.deleteKey(it.metadataBackupKey)
            cryptographer.deleteKey(it.secretsBackupKey)
        }
        unlockState.repository.totpSecrets().eraseBackups()
    }

    suspend fun export(): EncryptedData = requireUnlocked {
        requireBackupsEnabled { backupKeys ->
            val vaultExport = VaultExport(
                secrets = getTotpSecrets()
            ).encode()
            cryptographer.withEncryptionKey(backupKeys.metadataBackupKey) {
                encrypt(vaultExport)
            }
        }
    }

    suspend fun import(backupSeed: BackupSeed, data: EncryptedData) = requireUnlocked { unlockState ->
        val metadataKeyMaterial = backupSeed.deriveBackupMetadataKey()
        val vaultExport = cryptographer.withDecryptionKey(metadataKeyMaterial, INTERNAL_SYMMETRIC_KEY_ALGORITHM) {
            VaultExport.decode(decrypt(data))
        }
        metadataKeyMaterial.fill(0)

        val secretKeyMaterial = backupSeed.deriveBackupSecretKey()
        val secretDecryptionContext = DecryptionContext.create(secretKeyMaterial, INTERNAL_SYMMETRIC_KEY_ALGORITHM)
        for (oldSecret in vaultExport.secrets.sortedBy { it.sortOrder }) {
            secretDecryptionContext.importSecret(unlockState.repository.totpSecrets(), oldSecret)
        }
        secretKeyMaterial.fill(0)
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
    }

    private suspend fun encryptBackupSecretIfEnabled(secretBytes: ByteArray): EncryptedData? =
        unlockState?.backupKeys?.let {
            cryptographer.withEncryptionKey(it.secretsBackupKey) {
                encrypt(secretBytes)
            }
        }

    private fun createBackupKey(
        keyMaterial: ByteArray,
        keyIdentifier: KeyIdentifier,
    ): KeyHandle<SymmetricAlgorithm> =
        cryptographer.storeSymmetricKey(
            keyIdentifier = keyIdentifier,
            allowDecrypt = false,
            allowDeviceCredential = true, // TODO: make this configurable?
            requiresAuthentication = false,
            keyMaterial = keyMaterial,
        )

    private suspend fun createDbKey(requiresAuthenticaton: Boolean, dbDek: ByteArray? = null): Pair<DbKey, ByteArray> {
        val dbKekHandle = cryptographer.generateSymmetricKey(
            keyIdentifier = DB_KEK_IDENTIFIER,
            requiresAuthentication = requiresAuthenticaton,
            allowDecrypt = true,
            allowDeviceCredential = true,
        )

        val dbDekPlaintext = dbDek ?: ByteArray(DB_DEK_SIZE).apply(secureRandom::nextBytes)
        val dbDekCiphertext = cryptographer.withEncryptionKey(dbKekHandle) {
            encrypt(dbDekPlaintext)
        }
        val dbKey = DbKey(
            kekAlias = dbKekHandle.alias,
            encryptedDek = dbDekCiphertext.encode(),
        )
        return Pair(dbKey, dbDekPlaintext)
    }

    private suspend fun decryptDbDek(dbKey: DbKey): ByteArray {
        val decodedDbDek = EncryptedData.decode(dbKey.encryptedDek)
        val dbKekHandle = KeyHandle.fromAlias<SymmetricAlgorithm>(dbKey.kekAlias)
        return cryptographer.withDecryptionKey(dbKekHandle) {
            decrypt(decodedDbDek)
        }
    }

    private suspend fun <T> withRepository(action: suspend (TotpSecrets) -> T): T = requireUnlocked {
        action(it.repository.totpSecrets())
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
        val secretsBackupKey: KeyHandle<SymmetricAlgorithm>,
        val metadataBackupKey: KeyHandle<SymmetricAlgorithm>,
    )
}

fun TotpAlgorithm.toHmacAlgorithm(): HmacAlgorithm = when (this) {
    TotpAlgorithm.SHA1 -> HmacAlgorithm.SHA1
}

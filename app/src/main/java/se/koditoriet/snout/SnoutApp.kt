package se.koditoriet.snout

import android.app.Application
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.koditoriet.snout.crypto.Cryptographer
import se.koditoriet.snout.repository.VaultRepository
import se.koditoriet.snout.synchronization.Sync
import se.koditoriet.snout.vault.Vault
import kotlin.time.Duration.Companion.seconds

private const val TAG = "SnoutApp"

class SnoutApp : Application() {
    val vault: Sync<Vault>

    val config: DataStore<Config> by dataStore("config", ConfigSerializer)

    private val idleTimeoutScope = CoroutineScope(Dispatchers.IO)

    private val idleTimeout = TimeoutJob(
        name = "LockOnIdle",
        scope = idleTimeoutScope,
        onTimeout = { vault.withLock { lock() } },
    )

    suspend fun startIdleTimeout() {
        val cfg = config.data.first()
        if (cfg.lockOnClose) {
            idleTimeout.start(cfg.lockOnCloseGracePeriod.seconds)
        }
    }

    suspend fun cancelIdleTimeout() {
        idleTimeout.cancel()
    }

    init {
        Log.i(TAG, "Loading sqlcipher native libraries")
        System.loadLibrary("sqlcipher")
        val repositoryFactory = { dbName: String, key: ByteArray ->
            VaultRepository.open(this, dbName, key)
        }

        Log.i(TAG, "Creating vault")
        vault = Sync {
            Vault(
                repositoryFactory = repositoryFactory,
                cryptographer = Cryptographer(),
                dbFile = lazy { getDatabasePath("vault")!! },
            )
        }
    }
}

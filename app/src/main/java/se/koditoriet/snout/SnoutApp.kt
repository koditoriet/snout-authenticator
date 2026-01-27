package se.koditoriet.snout

import android.app.Application
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import se.koditoriet.snout.crypto.Cryptographer
import se.koditoriet.snout.repository.VaultRepository
import se.koditoriet.snout.synchronization.Sync
import se.koditoriet.snout.vault.Vault

private const val TAG = "SnoutApp"

class SnoutApp : Application() {
    val vault: Sync<Vault>
    val config: DataStore<Config> by dataStore("config", ConfigSerializer)

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

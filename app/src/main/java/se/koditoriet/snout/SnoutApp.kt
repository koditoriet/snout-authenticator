package se.koditoriet.snout

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import se.koditoriet.snout.crypto.Cryptographer
import se.koditoriet.snout.repository.VaultRepository
import se.koditoriet.snout.vault.Vault

class SnoutApp : Application() {
    val cryptographer: Cryptographer
    val vault: Vault
    val config: DataStore<Config> by dataStore("config", ConfigSerializer)

    init {
        System.loadLibrary("sqlcipher")
        val repositoryFactory = { dbName: String, key: ByteArray ->
            VaultRepository.open(this, dbName, key)
        }
        cryptographer = Cryptographer()
        vault = Vault(
            repositoryFactory = repositoryFactory,
            cryptographer = cryptographer,
            dbFile = lazy { getDatabasePath("vault")!! },
        )
    }
}

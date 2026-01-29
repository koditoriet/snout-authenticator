package se.koditoriet.snout

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import se.koditoriet.snout.crypto.Cryptographer
import se.koditoriet.snout.repository.VaultRepository
import se.koditoriet.snout.synchronization.Sync
import se.koditoriet.snout.vault.Vault
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val TAG = "SnoutApp"

private val idleTimeoutScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class SnoutApp : Application() {
    val vault: Sync<Vault>

    val config: DataStore<Config> by dataStore("config", ConfigSerializer)

    private val idleTimeout = TimeoutJob(
        name = "LockOnIdle",
        scope = idleTimeoutScope,
        onTimeout = { vault.withLock { lock() } },
    )

    fun startIdleTimeout() {
        idleTimeoutScope.launch {
            val cfg = config.data.first()
            if (cfg.lockOnClose) {
                idleTimeout.start(cfg.lockOnCloseGracePeriod.seconds)
            }
        }
    }

    fun cancelIdleTimeout() {
        idleTimeoutScope.launch {
            idleTimeout.cancel()
        }
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

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Registering automatic lock observers")
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
    }

    private val foregroundObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            Log.i(TAG, "Lost focus")
            startIdleTimeout()
        }

        override fun onStart(owner: LifecycleOwner) {
            Log.i(TAG, "Got back focus")
            cancelIdleTimeout()
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            idleTimeoutScope.launch {
                Log.i(TAG, "Screen off detected; locking vault immediately")
                cancelIdleTimeout()
                vault.withLock { lock() }
            }
        }
    }
}

/**
 * Sets up an action to be executed when a timeout expires.
 * The action can be canceled up until the point where the timeout expires. After that, it's unstoppable.
 */
class TimeoutJob(
    private val name: String,
    private val scope: CoroutineScope,
    private val onTimeout: suspend () -> Unit,
) {
    private var timeoutJob: Job? = null
    private val mutex = Mutex()

    suspend fun start(timeout: Duration) = mutex.withLock {
        // Reset timeout if one is already running
        if (timeoutJob != null) {
            Log.i(TAG, "Timeout job '$name' already pending; stopping it")
            timeoutJob!!.cancel()
        }

        Log.i(TAG, "Executing timeout job '$name' in $timeout")
        timeoutJob = scope.launch {
            delay(timeout)

            // If the timeout has already expired, the job is no longer stoppable
            mutex.withLock {
                Log.i(TAG, "Timeout expired for job '$name'; executing action")
                onTimeout()
                timeoutJob = null
            }
        }
    }

    suspend fun cancel() = mutex.withLock {
        Log.i(TAG, "Canceling timeout for job '$name'")
        timeoutJob?.cancel()
        timeoutJob = null
    }
}

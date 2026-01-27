package se.koditoriet.snout.synchronization

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Sync<T>(itemFactory: () -> T) {
    private val mutex = Mutex()
    private val item by lazy { itemFactory() }

    suspend fun <U> withLock(action: suspend T.() -> U): U =
        mutex.withLock { item.action() }

    fun <U> unsafeReadOnly(read: T.() -> U): U =
        item.read()
}

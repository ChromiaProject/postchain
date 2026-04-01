package net.postchain.integrationtest

import net.postchain.core.GlobalStorageInitializer
import java.sql.Connection

/**
 * Test implementation of [GlobalStorageInitializer] that tracks invocations.
 * Discovered via ServiceLoader from META-INF/services at test runtime.
 *
 * State is held in the companion object because ServiceLoader constructs a fresh instance.
 * Call [reset] in @BeforeEach to isolate tests.
 */
class TrackingGlobalStorageInitializer : GlobalStorageInitializer {

    companion object {
        @Volatile var wasCalled = false
        @Volatile var callCount = 0
        @Volatile var connectionWasOpen = false

        fun reset() {
            wasCalled = false
            callCount = 0
            connectionWasOpen = false
        }
    }

    override fun initializeGlobalStorage(connection: Connection) {
        wasCalled = true
        callCount++
        connectionWasOpen = !connection.isClosed
    }
}

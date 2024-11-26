package net.postchain.base.data

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.StorageBuilder
import org.junit.jupiter.api.Test

class CachedConnectionsTest {
    val appConfig = testDbConfig("cached_connections", 1)

    @Test
    fun `Test that read connections are cached per thread`() {
        val storageWithOneReadConn = StorageBuilder.buildStorage(appConfig)

        val readConn = storageWithOneReadConn.openReadConnection()

        // Verify that we can get more read conns on the same thread without deadlocking
        val readConn2 = storageWithOneReadConn.openReadConnection()
        val readConn3 = storageWithOneReadConn.openReadConnection()

        // Normally we close latest opened connection first but let's verify we can do it any order
        storageWithOneReadConn.closeReadConnection(readConn2)
        assertThat(readConn.conn.isClosed).isFalse()
        storageWithOneReadConn.closeReadConnection(readConn3)
        assertThat(readConn.conn.isClosed).isFalse()
        // Closing last reference to connection
        storageWithOneReadConn.closeReadConnection(readConn)
        assertThat(readConn.conn.isClosed).isTrue()
    }
}

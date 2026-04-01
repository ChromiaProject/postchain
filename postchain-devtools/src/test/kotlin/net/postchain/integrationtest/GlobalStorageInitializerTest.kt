package net.postchain.integrationtest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import net.postchain.devtools.IntegrationTestSetup

class GlobalStorageInitializerTest : IntegrationTestSetup() {

    @BeforeEach
    fun resetTracking() {
        TrackingGlobalStorageInitializer.reset()
    }

    @Test
    fun `GlobalStorageInitializer is called during node startup`() {
        assertFalse(TrackingGlobalStorageInitializer.wasCalled)

        createNodes(1, "/net/postchain/devtools/blockchain_module_lifecycle.xml")

        assertTrue(TrackingGlobalStorageInitializer.wasCalled)
    }

    @Test
    fun `GlobalStorageInitializer receives an open connection`() {
        createNodes(1, "/net/postchain/devtools/blockchain_module_lifecycle.xml")

        assertTrue(TrackingGlobalStorageInitializer.connectionWasOpen)
    }

    @Test
    fun `GlobalStorageInitializer is called exactly once per node startup`() {
        createNodes(1, "/net/postchain/devtools/blockchain_module_lifecycle.xml")

        assertEquals(1, TrackingGlobalStorageInitializer.callCount)
    }
}

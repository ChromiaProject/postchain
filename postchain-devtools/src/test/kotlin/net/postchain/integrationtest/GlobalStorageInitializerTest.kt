package net.postchain.integrationtest

import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.addBlockchainAndStart
import net.postchain.devtools.assertChainStarted
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GlobalStorageInitializerTest : IntegrationTestSetup() {

    private val blockchainConfig = "/net/postchain/devtools/blockchain_global_storage_init.xml"

    @BeforeEach
    fun resetTracking() {
        GlobalStorageInitializerModule.reset()
    }

    @Test
    fun `GlobalStorageInitializer is called during node startup`() {
        createNodes(1, blockchainConfig)

        assertEquals(1, GlobalStorageInitializerModule.globalInitCallCount)
    }

    @Test
    fun `GlobalStorageInitializer receives an open connection`() {
        createNodes(1, blockchainConfig)

        assertTrue(GlobalStorageInitializerModule.connectionWasOpen)
    }

    @Test
    fun `global init is called once while per-chain initializeDB is called per blockchain`() {
        createNodes(1, blockchainConfig)

        // Chain-level DB init fired (blockchain started)
        assertEquals(1, GlobalStorageInitializerModule.dbInitCallCount)
        // Global init fired exactly once — not once per blockchain
        assertEquals(1, GlobalStorageInitializerModule.globalInitCallCount)
    }

    @Test
    fun `global init count stays 1 when a second blockchain is started on the same node`() {
        createNodes(1, blockchainConfig)

        // Start a second chain with the same module config
        val config2 = readBlockchainConfig(blockchainConfig)
        nodes[0].addBlockchainAndStart(2L, config2)
        await.untilAsserted { nodes[0].assertChainStarted(2L) }

        // Both chains triggered their own initializeDB
        assertEquals(2, GlobalStorageInitializerModule.dbInitCallCount)
        // Global init still ran only once — at node startup, not per blockchain
        assertEquals(1, GlobalStorageInitializerModule.globalInitCallCount)
    }
}

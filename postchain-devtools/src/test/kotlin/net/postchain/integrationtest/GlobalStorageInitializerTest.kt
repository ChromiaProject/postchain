package net.postchain.integrationtest

import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.addBlockchainAndStart
import net.postchain.devtools.assertChainStarted
import net.postchain.gtv.GtvFactory.gtv
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GlobalStorageInitializerTest : IntegrationTestSetup() {

    private val blockchainConfig = "/net/postchain/devtools/blockchain_global_storage_init.xml"

    @BeforeEach
    fun resetTracking() {
        ModuleWithGlobalDBInitializer.reset()
    }

    @Test
    fun `GlobalStorageInitializer is called during node startup`() {
        createNodes(1, blockchainConfig)

        assertEquals(1, ModuleWithGlobalDBInitializer.globalInitCallCount)
    }

    @Test
    fun `GlobalStorageInitializer receives an open connection`() {
        createNodes(1, blockchainConfig)

        assertTrue(ModuleWithGlobalDBInitializer.connectionWasOpen)
    }

    @Test
    fun `global init is called once while per-chain initializeDB is called per blockchain`() {
        createNodes(1, blockchainConfig)

        // Global init fired
        assertEquals(1, ModuleWithGlobalDBInitializer.globalInitCallCount)
        // Chain-level DB init fired (blockchain started)
        assertEquals(1, ModuleWithGlobalDBInitializer.chainInitCallCount)

        // Start a second chain with the same module config
        val config = readBlockchainConfig(blockchainConfig)
        val config2 = gtv(config.asDict().toMutableMap().apply { put("nonce", gtv(1)) })
        nodes[0].addBlockchainAndStart(2L, config2)
        await.untilAsserted { nodes[0].assertChainStarted(2L) }

        // Global init fired exactly once — not once per blockchain
        assertEquals(1, ModuleWithGlobalDBInitializer.globalInitCallCount)
        // Each blockchain triggers its own initializeDB
        assertEquals(2, ModuleWithGlobalDBInitializer.chainInitCallCount)
    }

    @Test
    fun `node starts even if GlobalStorageInitializer throws`() {
        ModuleWithGlobalDBInitializer.shouldThrow = true

        createNodes(1, blockchainConfig)

        // Global init was attempted
        assertEquals(1, ModuleWithGlobalDBInitializer.globalInitCallCount)
        // Node started and chain is running despite the failure
        await.untilAsserted { nodes[0].assertChainStarted() }
    }
}

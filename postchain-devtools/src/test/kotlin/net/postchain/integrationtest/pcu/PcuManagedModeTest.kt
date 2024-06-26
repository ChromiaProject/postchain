package net.postchain.integrationtest.pcu

import net.postchain.base.configuration.BaseBlockchainConfiguration
import net.postchain.base.configuration.FaultyConfiguration
import net.postchain.base.configuration.KEY_QUEUE_CAPACITY
import net.postchain.base.configuration.KEY_SYNC
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.extension.getFailedConfigHash
import net.postchain.base.withWriteConnection
import net.postchain.common.hexStringToByteArray
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.mminfra.TestManagedEBFTInfrastructureFactory
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.gtv.GtvFactory.gtv
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

class PcuManagedModeTest : ManagedModeTest() {

    override fun addNodeConfigurationOverrides(nodeSetup: NodeSetup) {
        super.addNodeConfigurationOverrides(nodeSetup)
        nodeSetup.nodeSpecificConfigs.setProperty("infrastructure", TestManagedEBFTInfrastructureFactory::class.qualifiedName)
    }

    @Test
    fun basicTest() {
        startManagedSystem(3, 0, TestManagedEBFTInfrastructureFactory::class.qualifiedName!!)

        val chainSigners = setOf(0, 1, 2)
        val chain = startNewBlockchain(chainSigners, setOf(), null)
        val expectedConfigParamValue = 123456L
        val reconfigHeight = 3L
        addGtxBlockchainConfiguration(
                chain,
                chainSigners.associateWith { nodes[it].pubKey.hexStringToByteArray() },
                null,
                reconfigHeight,
                mapOf(KEY_QUEUE_CAPACITY to gtv(expectedConfigParamValue)),
                true
        )

        buildBlock(chain, 0)
        // initial value is 2500
        assertEquals(2500L, getTxQueueSize(chain))

        buildBlockNoWait(nodes, chain, 2)

        // asserting that pending config was loaded
        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            assertEquals(expectedConfigParamValue, getTxQueueSize(chain))
        }

        // Build a block with pending config
        buildBlock(chain, 3)

        // Simulate that config update was notified to d1
        addGtxBlockchainConfiguration(
                chain,
                chainSigners.associateWith { nodes[it].pubKey.hexStringToByteArray() },
                null,
                reconfigHeight,
                mapOf(KEY_QUEUE_CAPACITY to gtv(expectedConfigParamValue)),
                false
        )

        // Ensure block building continues normally
        buildBlock(chain, 4)
    }

    @Test
    fun failedPendingConfig() {
        startManagedSystem(3, 0, TestManagedEBFTInfrastructureFactory::class.qualifiedName!!)
        val chainSigners = setOf(0, 1, 2)
        val chain = startNewBlockchain(chainSigners, setOf(), null)
        buildBlock(chain, 0)

        val initialConfigHash = nodes.first().getBlockchainInstance(chain).blockchainEngine.getConfiguration().configHash

        // Add a failing pending configuration
        val reconfigHeight = 3L
        addGtxBlockchainConfiguration(
                chain,
                chainSigners.associateWith { nodes[it].pubKey.hexStringToByteArray() },
                null,
                reconfigHeight,
                mapOf(KEY_SYNC to gtv("invalid")),
                true
        )

        buildBlockNoWait(nodes, chain, 2)

        // Wait until we reverted faulty config
        awaitChainRestarted(chain, 2, initialConfigHash)

        // We should not get any restarts with attempts to apply faulty config again
        buildBlock(chain, 4)

        // Verify failed config hash was included in header
        nodes.forEach {
            val blockAtFailedReconfig = it.blockQueries(chain).getBlockAtHeight(reconfigHeight).get()!!
            assertNotNull(blockAtFailedReconfig.header.getFailedConfigHash())
        }

        // Add a new pending config
        val expectedConfigParamValue = 100L
        addGtxBlockchainConfiguration(
                chain,
                chainSigners.associateWith { nodes[it].pubKey.hexStringToByteArray() },
                null,
                reconfigHeight,
                mapOf(KEY_QUEUE_CAPACITY to gtv(expectedConfigParamValue)),
                true
        )

        // Build two blocks to check that we did not apply new pending config
        buildBlock(chain, 6)

        // Simulate removal of faulty config from d1
        markPendingConfigurationAsFaulty(chain, reconfigHeight)

        buildBlockNoWait(nodes, chain, 7)

        // Assert that new working pending config is applied
        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            assertEquals(expectedConfigParamValue, getTxQueueSize(chain))
        }

        // Simulate that config update was notified to d1
        addGtxBlockchainConfiguration(
                chain,
                chainSigners.associateWith { nodes[it].pubKey.hexStringToByteArray() },
                null,
                7,
                mapOf(KEY_QUEUE_CAPACITY to gtv(expectedConfigParamValue)),
                false
        )

        // Ensure block building continues normally
        buildBlock(chain, 8)
    }

    @Test
    fun onlyReportPendingConfigsAsFaulty() {
        startManagedSystem(1, 0, TestManagedEBFTInfrastructureFactory::class.qualifiedName!!)
        val chainSigners = setOf(0)
        val chain = startNewBlockchain(chainSigners, setOf(), null)
        val node = nodes[0]
        buildBlock(chain, 0)

        val initialConfigHash = nodes.first().getBlockchainInstance(chain).blockchainEngine.getConfiguration().configHash

        // Add a faulty pending config and assert that it is reported
        addGtxBlockchainConfiguration(
                chain,
                chainSigners.associateWith { nodes[it].pubKey.hexStringToByteArray() },
                null,
                2,
                mapOf(KEY_SYNC to gtv("invalid")),
                true
        )
        buildBlockNoWait(nodes, chain, 1)

        // Wait until we reverted faulty config
        awaitChainRestarted(chain, 1, initialConfigHash)
        buildBlock(chain, 2)

        val reportPendingFaultyHeader = node.getBlockchainInstance(chain).blockchainEngine.getBlockQueries().getBlockAtHeight(2).get()!!.header
        assertNotNull(reportPendingFaultyHeader.getFailedConfigHash())

        // Fake that we had trouble with a non-pending config and assert that we do not report it
        addGtxBlockchainConfiguration(
                chain,
                chainSigners.associateWith { nodes[it].pubKey.hexStringToByteArray() },
                null,
                4,
                mapOf("dummy" to gtv("dummy")),
        )
        buildBlockNoWait(nodes, chain, 3)
        awaitChainRestarted(chain, 3)

        // Assert that new config was applied
        val newConfigHash = nodes.first().getBlockchainInstance(chain).blockchainEngine.getConfiguration().configHash
        assertFalse(initialConfigHash.contentEquals(newConfigHash))

        // Fake that we had trouble applying it
        withWriteConnection(node.postchainContext.blockBuilderStorage, chain) {
            DatabaseAccess.of(it).addFaultyConfiguration(it, FaultyConfiguration(newConfigHash.wrap(), 4))
            true
        }

        // Assert that we do not report anything
        buildBlock(chain, 4)
        val reportNonPendingFaultyHeader = node.getBlockchainInstance(chain).blockchainEngine.getBlockQueries().getBlockAtHeight(4).get()!!.header
        assertNull(reportNonPendingFaultyHeader.getFailedConfigHash())
    }

    private fun getTxQueueSize(chainId: Long): Long =
            (nodes[0].getBlockchainInstance(chainId).blockchainEngine.getConfiguration() as BaseBlockchainConfiguration)
                    .configData.txQueueSize
}
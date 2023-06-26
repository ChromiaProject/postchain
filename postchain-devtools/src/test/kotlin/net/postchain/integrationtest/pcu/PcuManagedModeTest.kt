package net.postchain.integrationtest.pcu

import net.postchain.base.configuration.BaseBlockchainConfiguration
import net.postchain.base.configuration.KEY_QUEUE_CAPACITY
import net.postchain.base.configuration.KEY_SYNC
import net.postchain.base.extension.getFailedConfigHash
import net.postchain.common.hexStringToByteArray
import net.postchain.concurrent.util.get
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.mminfra.pcu.TestPcuManagedEBFTInfrastructureFactory
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.gtv.GtvFactory.gtv
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

class PcuManagedModeTest : ManagedModeTest() {

    override fun addNodeConfigurationOverrides(nodeSetup: NodeSetup) {
        super.addNodeConfigurationOverrides(nodeSetup)
        nodeSetup.nodeSpecificConfigs.setProperty("infrastructure", TestPcuManagedEBFTInfrastructureFactory::class.qualifiedName)
        nodeSetup.nodeSpecificConfigs.setProperty("pcu", true)
    }

    @Test
    fun basicTest() {
        startManagedSystem(3, 0, TestPcuManagedEBFTInfrastructureFactory::class.qualifiedName!!)

        val chainSigners = setOf(0, 1, 2)
        val chain = startNewBlockchain(chainSigners, setOf(), null)
        val expectedConfigParamValue = 123456L
        val reconfigHeight = 3L
        addBlockchainConfiguration(
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
        addBlockchainConfiguration(
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
        startManagedSystem(3, 0, TestPcuManagedEBFTInfrastructureFactory::class.qualifiedName!!)
        val chainSigners = setOf(0, 1, 2)
        val chain = startNewBlockchain(chainSigners, setOf(), null)
        buildBlock(chain, 0)

        val initialConfigHash = nodes.first().getBlockchainInstance(chain).blockchainEngine.getConfiguration().configHash

        // Add a failing pending configuration
        val reconfigHeight = 3L
        addBlockchainConfiguration(
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
        addBlockchainConfiguration(
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
        addBlockchainConfiguration(
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

    private fun getTxQueueSize(chainId: Long): Long =
            (nodes[0].getBlockchainInstance(chainId).blockchainEngine.getConfiguration() as BaseBlockchainConfiguration)
                    .configData.txQueueSize
}
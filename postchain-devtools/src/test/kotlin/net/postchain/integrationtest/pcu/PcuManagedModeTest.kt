package net.postchain.integrationtest.pcu

import net.postchain.base.configuration.BaseBlockchainConfiguration
import net.postchain.base.configuration.KEY_QUEUE_CAPACITY
import net.postchain.common.hexStringToByteArray
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.currentHeight
import net.postchain.devtools.mminfra.MockManagedNodeDataSource
import net.postchain.devtools.mminfra.pcu.MockPcuManagedNodeDataSource
import net.postchain.devtools.mminfra.pcu.TestPcuManagedEBFTInfrastructureFactory
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.gtv.GtvFactory.gtv
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.concurrent.schedule
import kotlin.test.assertEquals

class PcuManagedModeTest : ManagedModeTest() {

    private val dataSource = MockPcuManagedNodeDataSource()

    override fun createManagedNodeDataSource(): MockManagedNodeDataSource = dataSource

    override fun addNodeConfigurationOverrides(nodeSetup: NodeSetup) {
        super.addNodeConfigurationOverrides(nodeSetup)
        nodeSetup.nodeSpecificConfigs.setProperty("infrastructure", TestPcuManagedEBFTInfrastructureFactory::class.qualifiedName)
    }

    @Test
    fun basicTest() {
        startManagedSystem(1, 0, TestPcuManagedEBFTInfrastructureFactory::class.qualifiedName!!)

        val chain = startNewBlockchain(setOf(0), setOf(), null)
        val expectedConfigParamValue = 123456L
        val reconfigHeight = 3L
        addBlockchainConfiguration(
                chain,
                setOf(0).associateWith { nodes[0].pubKey.hexStringToByteArray() },
                null,
                reconfigHeight,
                mapOf(KEY_QUEUE_CAPACITY to gtv(expectedConfigParamValue))
        )

        Timer().schedule(2000L) {
            dataSource.approveConfig(reconfigHeight)
        }

        // initial value is 2500
        assertEquals(2500L, getTxQueueSize(chain))

        // asserting that pending config was loaded
        buildBlockNoWait(getChainNodes(chain), chain, reconfigHeight)
        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            assertEquals(expectedConfigParamValue, getTxQueueSize(chain))
        }

        // setting a desired height again because strategy object was rebuilt after reconfiguration
        buildBlockNoWait(getChainNodes(chain), chain, reconfigHeight + 1)

        // asserting that block building is continuing, that means that pending config was approved
        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            assertEquals(reconfigHeight + 1, nodes[0].currentHeight(chain))
        }
    }

    private fun getTxQueueSize(chainId: Long): Long =
            (nodes[0].getBlockchainInstance(chainId).blockchainEngine.getConfiguration() as BaseBlockchainConfiguration)
                    .configData.txQueueSize
}
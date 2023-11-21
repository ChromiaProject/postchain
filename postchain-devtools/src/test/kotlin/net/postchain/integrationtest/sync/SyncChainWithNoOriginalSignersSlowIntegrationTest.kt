package net.postchain.integrationtest.sync

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import net.postchain.common.hexStringToByteArray
import net.postchain.concurrent.util.get
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.mminfra.TestManagedEBFTInfrastructureFactory
import net.postchain.devtools.utils.ChainUtil
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * Verifies that we can sync a chain were all original signers are stopped nodes and replaced
 * without any manual workarounds such as adding the new signers as replica nodes.
 */
class SyncChainWithNoOriginalSignersSlowIntegrationTest : ManagedModeTest() {

    @Test
    fun syncWithNoOriginalSigners() {
        startManagedSystem(3, 0, TestManagedEBFTInfrastructureFactory::class.qualifiedName!!)
        // Start with only node 0 as signer
        val chain = startNewBlockchain(setOf(0), setOf(1, 2), null)

        buildBlock(chain, 0)

        // Add node 1 as signer
        addBlockchainConfiguration(
                chain,
                setOf(0, 1).associateWith { nodes[it].pubKey.hexStringToByteArray() },
                null,
                2
        )

        buildBlockNoWait(nodes, chain, 2)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            nodes.forEach {
                val bc = it.retrieveBlockchain()
                assertThat(bc).isNotNull()
                assertThat(bc!!.blockchainEngine.getConfiguration().signers.size).isEqualTo(2)
            }
        }

        // Remove node 0 as signer
        addBlockchainConfiguration(
                chain,
                setOf(1).associateWith { nodes[it].pubKey.hexStringToByteArray() },
                null,
                4
        )

        buildBlockNoWait(nodes, chain, 4)
        await().atMost(30, TimeUnit.SECONDS).untilAsserted {
            nodes.forEach {
                val bc = it.retrieveBlockchain()
                assertThat(bc).isNotNull()
                assertThat(bc!!.blockchainEngine.getConfiguration().signers.size).isEqualTo(1)
            }
        }
        // Ensure we can still build blocks
        buildBlock(chain, 5)

        // Shut down original signer
        nodes[0].shutdown()

        // Wipe and restart node 2
        restartNodeClean(2, ChainUtil.ridOf(chain))

        // Assert that the node 2 can sync the chain even though no original signers are live
        await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            val bc = nodes[2].retrieveBlockchain()
            assertThat(bc).isNotNull()
            assertThat(bc!!.blockchainEngine.getBlockQueries().getLastBlockHeight().get()).isEqualTo(5)
        }
    }
}

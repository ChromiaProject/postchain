package net.postchain.integrationtest.state

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import net.postchain.core.BlockchainState
import net.postchain.devtools.ManagedModeTest
import net.postchain.ebft.worker.ReadOnlyBlockchainProcess
import net.postchain.ebft.worker.ValidatorBlockchainProcess
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

class BlockchainStateTest : ManagedModeTest() {

    @Test
    fun `Test switching blockchain state`() {
        val c1 = basicSystem()
        verifyState(c1, BlockchainState.RUNNING, ValidatorBlockchainProcess::class)

        setBlockchainState(c1, BlockchainState.PAUSED)
        verifyState(c1, BlockchainState.PAUSED, ReadOnlyBlockchainProcess::class)

        setBlockchainState(c1, BlockchainState.RUNNING)
        verifyState(c1, BlockchainState.RUNNING, ValidatorBlockchainProcess::class)

        buildBlock(c1)
    }

    private fun <T : Any> verifyState(chainId: Long, state: BlockchainState, clazz: KClass<in T>) {
        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            getChainNodes(chainId).forEach {
                val chain = it.retrieveBlockchain(chainId)
                assertThat(chain).isNotNull()
                assertThat(chain!!.getBlockchainState()).isEqualTo(state)
                assertThat(chain).isInstanceOf(clazz.java)
            }
        }
    }

    private fun basicSystem(): Long {
        startManagedSystem(3, 0)
        val c1 = startNewBlockchain(setOf(0, 1, 2), setOf(), null)
        buildBlock(c1, 1)
        return c1
    }
}
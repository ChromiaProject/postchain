package net.postchain.integrationtest.state

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.core.BlockchainState
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.currentHeight
import net.postchain.devtools.mminfra.TestManagedBlockchainProcessManager
import net.postchain.devtools.utils.ChainUtil
import net.postchain.ebft.worker.ReadOnlyBlockchainProcess
import net.postchain.ebft.worker.ValidatorBlockchainProcess
import org.awaitility.Awaitility.await
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

    @Test
    fun `Test deleting blockchain`() {
        val c1 = basicSystem()
        verifyState(c1, BlockchainState.RUNNING, ValidatorBlockchainProcess::class)

        // Asserting the initial state
        await().atMost(Duration.ONE_MINUTE).untilAsserted {
            getChainNodes(c1).forEach {
                assertThat(it.retrieveBlockchain(c1)).isNotNull()
                assertThat(
                        (it.processManager as TestManagedBlockchainProcessManager).getCurRemovedBcHeight()
                ).isEqualTo(0L)
            }
        }

        // Removing c1 at a specific height
        setBlockchainState(c1, BlockchainState.REMOVED, nodes.first().currentHeight(c0))
        val heightC1RemovedAt = mockDataSources[0]!!.bridState[ChainUtil.ridOf(c1)]!!.first

        // Asserting that c1 is removed
        await().atMost(Duration.ONE_MINUTE).untilAsserted {
            getChainNodes(c1).forEach {
                assertThat(it.retrieveBlockchain(c1)).isNull()
                val chainId1FromDb = withReadConnection(nodes.first().postchainContext.sharedStorage, c0) { ctx0 ->
                    DatabaseAccess.of(ctx0).getChainId(ctx0, ChainUtil.ridOf(c1))
                }
                assertThat(chainId1FromDb).isNull()
            }
        }

        // building one more c0 block
        buildBlock(c0)

        // Asserting that `ManagedBlockchainProcessManager.currentRemovedBlockchainHeight` equals height blockchain was removed at
        assertThat(
                (nodes.first().processManager as TestManagedBlockchainProcessManager).getCurRemovedBcHeight()
        ).isEqualTo(heightC1RemovedAt)
    }

    private fun <T : Any> verifyState(chainId: Long, state: BlockchainState, clazz: KClass<in T>) {
        await().atMost(Duration.ONE_MINUTE).untilAsserted {
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
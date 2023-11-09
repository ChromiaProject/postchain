package net.postchain.base

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import net.postchain.DynamicValueAnswer
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockQueries
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.toObject
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.mockito.kotlin.*
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BaseBlockBuildingStrategyTest {

    companion object {

        const val MIN_BACKOFF_TIME = 500L
        const val MAX_BACKOFF_TIME = 2000L
        const val MAX_BLOCK_TIME = 10_000L
        const val MIN_INTER_BLOCK_INTERVAL = 1000L
        const val MAX_BLOCK_TRANSACTIONS = 100L
        const val MAX_TX_DELAY = 500L

        private val strategyData: GtvDictionary = GtvDictionary.build(mapOf(
                "maxblocktime" to gtv(MAX_BLOCK_TIME),
                "mininterblockinterval" to gtv(MIN_INTER_BLOCK_INTERVAL),
                "maxblocktransactions" to gtv(MAX_BLOCK_TRANSACTIONS),
                "maxtxdelay" to gtv(MAX_TX_DELAY),
                "minbackofftime" to gtv(MIN_BACKOFF_TIME),
                "maxbackofftime" to gtv(MAX_BACKOFF_TIME),
        ))

        // Mocks
        private var currentMillis = DynamicValueAnswer(0L)

        private val clock: Clock = mock {
            on { millis() } doAnswer currentMillis
        }

        private val blockQueries: BlockQueries = mock {
            val completionStage: CompletionStage<Long> = CompletableFuture.completedStage(-1)
            on { getLastBlockHeight() } doReturn completionStage
        }

        private fun committedBlockData(): BlockData {
            val blockHeader: BaseBlockHeader = mock {
                on { timestamp } doAnswer currentMillis
            }

            return mock {
                on { header } doReturn blockHeader
            }
        }

        private var txQueueSize = DynamicValueAnswer(0)

        private val txQueue: TransactionQueue = mock {
            on { getTransactionQueueSize() } doAnswer txQueueSize
        }

        private val sut = BaseBlockBuildingStrategy(strategyData.toObject(), blockQueries, txQueue, clock)
    }

    @Test
    @Order(1)
    fun test_maxBlockTime_for_block0() {
        currentMillis.value = MIN_INTER_BLOCK_INTERVAL + 1
        assertThat(sut.hasReachedTimeConstraintsForBlockBuilding(false)).isEqualTo(false)

        currentMillis.value = MAX_BLOCK_TIME + 1
        assertThat(sut.hasReachedTimeConstraintsForBlockBuilding(false)).isEqualTo(true)

        // Commiting block0 now
        sut.blockCommitted(committedBlockData())
    }

    @Test
    @Order(2)
    fun test_minInterblockInterval_for_block1() {
        clearInvocations(txQueue)

        // Testing 'mininterblockinterval': building block1 is NOT allowed for 'mininterblockinterval' sec
        assertThat(sut.shouldBuildBlock()).isEqualTo(false)
        assertThat(sut.mustWaitMinimumBuildBlockTime()).isGreaterThan(0)

        // Testing 'mininterblockinterval': building block1 is allowed
        currentMillis.value = currentMillis.value + 1000
        // Force transactionQueueSize >= maxBlockTransactions
        txQueueSize.value = MAX_BLOCK_TRANSACTIONS.toInt() + 1
        assertThat(sut.shouldBuildBlock()).isEqualTo(true) // transactionQueueSize >= maxBlockTransactions
        verify(txQueue, times(1)).getTransactionQueueSize()

        // Commiting block1 now
        sut.blockCommitted(committedBlockData())
    }

    @Test
    @Order(3)
    fun test_maxTxDelay_for_block2() {
        txQueueSize.value = 1 // 1 tx only
        currentMillis.value = currentMillis.value + MIN_INTER_BLOCK_INTERVAL + 1

        // Testing 'maxtxdelay': 'minInterBlockInterval' is NOT passed yet
        assertThat(sut.hasReachedTimeConstraintsForBlockBuilding(true)).isEqualTo(false) // 1 tx only

        // Testing 'maxtxdelay': 'maxtxdelay' already passed.
        currentMillis.value = currentMillis.value + MAX_TX_DELAY + 1
        assertThat(sut.hasReachedTimeConstraintsForBlockBuilding(true)).isEqualTo(true)

        // Commiting block2 now
        sut.blockCommitted(committedBlockData())
    }

    @Test
    @Order(4)
    fun test_minimum_backoff_for_blocks() {
        assertThat(sut.mustWaitBeforeBuildBlock()).isEqualTo(false)
        failCommit(1) // 1 -> 2 ms
        currentMillis.value = currentMillis.value + MIN_BACKOFF_TIME + 2 + 1 // 2 ms for fail count = 1
        assertThat(sut.mustWaitBeforeBuildBlock()).isEqualTo(false)
        sut.blockCommitted(committedBlockData())
    }

    @Test
    @Order(5)
    fun test_backoff_for_blocks() {
        assertThat(sut.mustWaitBeforeBuildBlock()).isEqualTo(false)
        failCommit(8) // 8 -> 256 ms
        currentMillis.value = currentMillis.value + MIN_BACKOFF_TIME + 256 + 1 // 2 ms for fail count = 1
        assertThat(sut.mustWaitBeforeBuildBlock()).isEqualTo(false)
        sut.blockCommitted(committedBlockData())
    }

    @Test
    @Order(6)
    fun test_max_backoff_for_blocks() {
        assertThat(sut.mustWaitBeforeBuildBlock()).isEqualTo(false)
        failCommit(10) // 10 -> 1024 ms
        sut.blockFailed() // 2048
        assertThat(sut.getBackoffTime()).isEqualTo(MAX_BACKOFF_TIME)
        assertThat(sut.mustWaitBeforeBuildBlock()).isEqualTo(true)
        currentMillis.value = currentMillis.value + MAX_BACKOFF_TIME + 1
        assertThat(sut.mustWaitBeforeBuildBlock()).isEqualTo(false)
        sut.blockCommitted(committedBlockData())
    }

    private fun failCommit(times: Int) {
        var failTime = 1
        for (i in 1..times) {
            sut.blockFailed()
            failTime *= 2
            assertThat(sut.getBackoffTime()).isEqualTo(failTime + MIN_BACKOFF_TIME)
        }
        assertThat(sut.mustWaitBeforeBuildBlock()).isEqualTo(true)
    }
}
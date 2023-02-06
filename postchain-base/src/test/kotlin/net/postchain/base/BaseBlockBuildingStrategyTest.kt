package net.postchain.base

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockQueries
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.toObject
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.*
import org.mockito.stubbing.Answer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BaseBlockBuildingStrategyTest {

    companion object {

        // Mocks
        private val blockQueries: BlockQueries = mock {
            val completionStage: CompletionStage<Long> = CompletableFuture.completedStage(-1)
            on { getBestHeight() } doReturn completionStage
        }

        private fun committedBlockData(): BlockData {
            val blockHeader: BaseBlockHeader = mock {
                on { timestamp } doReturn System.currentTimeMillis()
            }

            return mock {
                on { header } doReturn blockHeader
            }
        }

        private val strategyData: GtvDictionary = GtvDictionary.build(mapOf(
                "maxblocktime" to gtv(10_000L),
                "mininterblockinterval" to gtv(1000L),
                "maxblocktransactions" to gtv(100L),
                "maxtxdelay" to gtv(500),
        ))

        private var txQueueSize = DynamicValueAnswer(120)

        private val txQueue: TransactionQueue = mock {
            on { getTransactionQueueSize() } doAnswer txQueueSize
        }

        private val sut = BaseBlockBuildingStrategy(strategyData.toObject(), blockQueries, txQueue)

        class DynamicValueAnswer(var value: Int) : Answer<Int> {
            override fun answer(p0: InvocationOnMock?): Int = value
        }
    }

    @Test
    @Order(1)
    fun test_maxBlockTime_for_block0() {
        // Testing 'maxBlockTime': building block0
        await().untilAsserted {
            assert(sut.shouldBuildBlock()).isEqualTo(true)
        }

        // Commiting block0 now
        sut.blockCommitted(committedBlockData())
    }

    @Test
    @Order(2)
    fun test_minInterblockInterval_for_block1() {
        // Testing 'mininterblockinterval': building block1 is NOT allowed for 'mininterblockinterval' sec
        await().untilAsserted {
            assert(sut.shouldBuildBlock()).isEqualTo(false)
        }

        // Testing 'mininterblockinterval': building block1 is allowed
        await().atMost(1100, TimeUnit.MILLISECONDS).untilAsserted {
            assert(sut.shouldBuildBlock()).isEqualTo(true) // transactionQueueSize >= maxBlockTransactions
        }
        verify(txQueue, times(1)).getTransactionQueueSize()

        // Commiting block1 now
        sut.blockCommitted(committedBlockData())
    }

    @Test
    @Order(3)
    fun test_maxTxDelay_for_block2() {
        txQueueSize.value = 1 // 1 tx only

        // Testing 'maxtxdelay': 'minInterBlockInterval' is NOT passed yet
        await().atMost(1000, TimeUnit.MILLISECONDS).untilAsserted {
            assert(sut.shouldBuildBlock()).isEqualTo(false) // 1 tx only
        }

        // Testing 'maxtxdelay': 'maxtxdelay' already passed.
        // minInterBlockInterval + maxtxdelay + delta ~= 2100
        await().atMost(2100, TimeUnit.MILLISECONDS).untilAsserted {
            assert(sut.shouldBuildBlock()).isEqualTo(true)
        }

        // Commiting block2 now
        sut.blockCommitted(committedBlockData())
    }

}
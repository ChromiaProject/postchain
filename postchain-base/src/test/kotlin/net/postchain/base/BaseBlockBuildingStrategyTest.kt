package net.postchain.base

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.core.BlockData
import net.postchain.core.BlockQueries
import net.postchain.core.BlockchainContext
import net.postchain.core.BlockchainRid.Companion.ZERO_RID
import net.postchain.core.TransactionQueue
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import nl.komponents.kovenant.Promise
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.*
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.*
import org.mockito.stubbing.Answer
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BaseBlockBuildingStrategyTest {

    companion object {

        // Mocks
        private val context: BlockchainContext = BaseBlockchainContext(ZERO_RID, 0, 0L, byteArrayOf())

        private val blockQueries: BlockQueries = mock {
            val height: Promise<Long, Exception> = mock {
                onGeneric { get() } doReturn -1L
            }
            on { getBestHeight() } doReturn height
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

        private val spyingConfigData = BaseBlockchainConfigurationData(strategyData, context, mock())
        private val configData = spy(spyingConfigData) {
            on { getBlockBuildingStrategy() } doReturn strategyData
        }

        private var txQueueSize = DynamicValueAnswer(120)

        private val txQueue: TransactionQueue = mock {
            on { getTransactionQueueSize() } doAnswer txQueueSize
        }

        private val sut = BaseBlockBuildingStrategy(configData, blockQueries, txQueue)

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
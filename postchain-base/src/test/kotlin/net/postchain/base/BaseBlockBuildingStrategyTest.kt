package net.postchain.base

import net.postchain.base.config.BlockBuildingStrategyConfig
import net.postchain.base.config.BlockchainConfig
import net.postchain.core.BlockQueries
import net.postchain.core.TransactionQueue
import nl.komponents.kovenant.Promise
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.TimeUnit

class BaseBlockBuildingStrategyTest {

    @Test
    fun test_after_POS131() {
        val height: Promise<Long, Exception> = mock {
            onGeneric { get() } doReturn -1L
        }
        val blockQueries: BlockQueries = mock {
            on { getBestHeight() } doReturn height
        }

        val txQueue: TransactionQueue = mock {
            on { getTransactionQueueSize() } doReturn 0
        }
        val stategyConfig = BlockBuildingStrategyConfig(1000, 100, 2000, 1000, 25, 10000)
        val config = mock<BlockchainConfig> {
            on { blockBuildingStrategyConfig } doReturn stategyConfig
        }

        val sut = BaseBlockBuildingStrategy(
                config, mock(), blockQueries, txQueue
        )

        // When
        await().pollInterval(1, TimeUnit.SECONDS).atMost(3, TimeUnit.SECONDS).until {
            sut.shouldBuildBlock()
        }
    }

}
package net.postchain.ebft.worker

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.block.BlockQueries
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.math.min

class ForceReadOnlyBlockchainProcessTest {

    @ParameterizedTest
    @ValueSource(longs = [-1L, 5L, 10L, 15L])
    fun `maxExposedHeight is disabled`(maxExposedHeight: Long) {
        // setup
        val completionStage: CompletionStage<Long> = CompletableFuture.completedStage(10L)
        val blockQueries: BlockQueries = mock {
            on { getLastBlockHeight() } doReturn completionStage
        }
        val bcConfig: BlockchainConfiguration = mock {
            on { chainID } doReturn 0
            on { blockchainRid } doReturn BlockchainRid.ZERO_RID
        }
        val bcEngine: BlockchainEngine = mock {
            on { getBlockQueries() } doReturn blockQueries
            on { getConfiguration() } doReturn bcConfig
        }
        val context: WorkerContext = mock {
            on { engine } doReturn bcEngine
            on { blockchainConfiguration } doReturn bcConfig
            on { communicationManager } doReturn mock()
        }

        // action
        val sut = object : ForceReadOnlyBlockchainProcess(context, mock(), maxExposedHeight) {
            fun messageProcessor() = forceReadOnlyMessageProcessor
        }

        // verification
        assertThat(sut.messageProcessor().lastBlockHeight).isEqualTo(
                if (maxExposedHeight == -1L) 10L else min(maxExposedHeight, 10)
        )
    }
}
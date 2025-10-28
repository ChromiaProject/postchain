package net.postchain.ebft.worker

import assertk.assertThat
import assertk.assertions.isTrue
import net.postchain.base.NetworkNodes
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.configuration.KEY_BLOCKSTRATEGY
import net.postchain.base.configuration.KEY_BLOCKSTRATEGY_MAXBLOCKTIME
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainRestartNotifier
import net.postchain.core.BlockchainState
import net.postchain.core.block.BlockQueries
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import java.lang.Thread.sleep
import java.time.Instant
import java.util.concurrent.CompletableFuture

class ReadOnlyBlockchainProcessTest {

    @Test
    fun `restart with sync disabled when there is nothing to sync`() {
        // setup
        val blockQueries: BlockQueries = mock {
            on { getLastBlockHeight() } doReturn CompletableFuture.completedStage(10L)
        }
        val bcConfig: BlockchainConfiguration = mock {
            on { chainID } doReturn 0
            on { blockchainRid } doReturn BlockchainRid.ZERO_RID
            on { signers } doReturn emptyList()
            on { rawConfig } doReturn gtv(
                    KEY_BLOCKSTRATEGY to gtv(
                            KEY_BLOCKSTRATEGY_MAXBLOCKTIME to gtv(1000)
                    )
            )
        }
        val bcEngine: BlockchainEngine = mock {
            on { getBlockQueries() } doReturn blockQueries
            on { getConfiguration() } doReturn bcConfig
            on { blockchainRid } doReturn BlockchainRid.ZERO_RID
        }
        val networkNodesMock = mock<NetworkNodes> {
            on { getPeerIds() } doReturn emptySet()
        }
        val peerCommConfigurationMOck = mock<PeerCommConfiguration> {
            on { networkNodes } doReturn networkNodesMock
        }
        val appConfigMock = mock<AppConfig> {
            on { cryptoSystem } doReturn mock()
            on { getEnvOrLong(eq("POSTCHAIN_FASTSYNC_SYNC_TO_EXACT_HEIGHT"),
                    any(), any()) } doReturn -1
        }
        val restartNotifierMock = mock<BlockchainRestartNotifier>()
        val context: WorkerContext = mock {
            on { engine } doReturn bcEngine
            on { blockchainConfiguration } doReturn bcConfig
            on { communicationManager } doReturn mock()
            on { appConfig } doReturn appConfigMock
            on { nodeDiagnosticContext } doReturn mock()
            on { peerCommConfiguration } doReturn peerCommConfigurationMOck
            on { messageDurationTracker } doReturn mock()
            on { restartNotifier } doReturn restartNotifierMock
        }

        // action
        val start = Instant.now()
        val sut = object : ReadOnlyBlockchainProcess(context, BlockchainState.PAUSED, initialSyncMonitorDelay = 0) {
            fun testAction() = action()
            override fun isProcessRunning(): Boolean {
                sleep(10)
                return !restartNotified.get() && super.isProcessRunning() &&
                        Instant.now().isBefore(start.plusSeconds(30))
            }
        }
        sut.start()
        sut.testAction()
        sut.shutdown()

        // verification
        assertThat(sut.isExpectingNewBlocks()).isTrue()
        Mockito.verify(blockQueries, atLeastOnce())
                .getLastBlockHeight()
        Mockito.verify(restartNotifierMock, times(1))
                .notifyRestart(anyOrNull(), eq(false))
    }

    @Test
    fun `continue to sync when receiving new blocks`() {
        // setup
        var height = 0L
        val blockQueries: BlockQueries = mock {
            on { getLastBlockHeight() } doAnswer {
                CompletableFuture.completedStage(height++)
            }
        }
        val bcConfig: BlockchainConfiguration = mock {
            on { chainID } doReturn 0
            on { blockchainRid } doReturn BlockchainRid.ZERO_RID
            on { signers } doReturn emptyList()
            on { rawConfig } doReturn gtv(
                    KEY_BLOCKSTRATEGY to gtv(
                            KEY_BLOCKSTRATEGY_MAXBLOCKTIME to gtv(100)
                    )
            )
        }
        val bcEngine: BlockchainEngine = mock {
            on { getBlockQueries() } doReturn blockQueries
            on { getConfiguration() } doReturn bcConfig
            on { blockchainRid } doReturn BlockchainRid.ZERO_RID
        }
        val networkNodesMock = mock<NetworkNodes> {
            on { getPeerIds() } doReturn emptySet()
        }
        val peerCommConfigurationMOck = mock<PeerCommConfiguration> {
            on { networkNodes } doReturn networkNodesMock
        }
        val appConfigMock = mock<AppConfig> {
            on { cryptoSystem } doReturn mock()
            on { getEnvOrLong(eq("POSTCHAIN_FASTSYNC_SYNC_TO_EXACT_HEIGHT"),
                    any(), any()) } doReturn -1
        }
        val restartNotifierMock = mock<BlockchainRestartNotifier>()
        val context: WorkerContext = mock {
            on { engine } doReturn bcEngine
            on { blockchainConfiguration } doReturn bcConfig
            on { communicationManager } doReturn mock()
            on { appConfig } doReturn appConfigMock
            on { nodeDiagnosticContext } doReturn mock()
            on { peerCommConfiguration } doReturn peerCommConfigurationMOck
            on { messageDurationTracker } doReturn mock()
            on { restartNotifier } doReturn restartNotifierMock
        }

        // action
        val start = Instant.now()
        val sut = object : ReadOnlyBlockchainProcess(context, BlockchainState.PAUSED, initialSyncMonitorDelay = 0) {
            fun testAction() = action()
            override fun isProcessRunning(): Boolean {
                sleep(10)
                return !restartNotified.get() && super.isProcessRunning() &&
                        Instant.now().isBefore(start.plusSeconds(10))
            }
        }
        sut.start()
        sut.testAction()
        sut.shutdown()

        // verification
        assertThat(sut.isExpectingNewBlocks()).isTrue()
        Mockito.verify(blockQueries, atLeastOnce())
                .getLastBlockHeight()
        Mockito.verify(restartNotifierMock, times(0))
                .notifyRestart(anyOrNull(), eq(false))
    }
}
package net.postchain.ebft.syncmanager.validator

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.base.NetworkNodes
import net.postchain.base.PeerCommConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.NodeRid
import net.postchain.core.block.BlockQueries
import net.postchain.ebft.BlockDatabase
import net.postchain.ebft.BlockManager
import net.postchain.ebft.NodeStateTracker
import net.postchain.ebft.NodeStatus
import net.postchain.ebft.StatusManager
import net.postchain.ebft.message.AppliedConfig
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.MessageDurationTracker
import net.postchain.ebft.message.Status
import net.postchain.ebft.worker.WorkerContext
import net.postchain.metrics.SyncMetrics
import net.postchain.network.CommunicationManager
import net.postchain.network.ReceivedPacket
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class ValidatorSyncManagerTest {

    private val isProcessRunning = true
    private val brid = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    private val blockchainRid = BlockchainRid.buildFromHex(brid)
    private val node0Hex = "03A301697BDFCD704313BA48E51D567543F2A182031EFD6915DDC07BBCC4E16070"
    private val node1Hex = "031B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F"
    private val node0 = node0Hex.hexStringToByteArray()
    private val node1 = node1Hex.hexStringToByteArray()
    private val nodeRid0 = NodeRid.fromHex(node0Hex)
    private val nodeRid1 = NodeRid.fromHex(node1Hex)
    private val height = 10L
    private val lastBlockHeight = 10L
    private val chainId = 42L
    private val signers = mutableListOf(node0, node1)
    private val peerIds = mutableSetOf(nodeRid0, nodeRid1)
    private var ensureAppliedConfigSenderCalled = false
    private val ensureAppliedConfigSender: () -> Boolean = {
        ensureAppliedConfigSenderCalled = true
        true
    }

    private val messageDurationTracker: MessageDurationTracker = mock()
    private val communicationManager: CommunicationManager<EbftMessage> = mock()
    private val blockchainConfiguration: BlockchainConfiguration = mock {
        on { blockchainRid } doReturn blockchainRid
        on { chainID } doReturn chainId
        on { signers } doReturn signers
    }
    private val blockQueries: BlockQueries = mock {
        val completionStage: CompletionStage<Long> = CompletableFuture.completedStage(lastBlockHeight)
        on { getLastBlockHeight() } doReturn completionStage
    }
    private val blockchainEngine: BlockchainEngine = mock {
        on { getBlockQueries() } doReturn blockQueries
        on { getConfiguration() } doReturn blockchainConfiguration
    }
    private val appConfig: AppConfig = mock()
    private val nodeConfig: NodeConfig = mock()
    private val networkNodes: NetworkNodes = mock {
        on { getPeerIds() } doReturn peerIds
    }
    private val peerCommConfiguration: PeerCommConfiguration = mock {
        on { networkNodes } doReturn networkNodes
    }
    private val workerContext: WorkerContext = mock {
        on { messageDurationTracker } doReturn messageDurationTracker
        on { communicationManager } doReturn communicationManager
        on { blockchainConfiguration } doReturn blockchainConfiguration
        on { engine } doReturn blockchainEngine
        on { appConfig } doReturn appConfig
        on { nodeConfig } doReturn nodeConfig
        on { peerCommConfiguration } doReturn peerCommConfiguration
    }
    private val loggingContext: Map<String, String> = mock()
    private val statusManager: StatusManager = mock()
    private val blockManager: BlockManager = mock()
    private val blockDatabase: BlockDatabase = mock()
    private val nodeStateTracker: NodeStateTracker = mock()
    private val revoltTracker: RevoltTracker = mock()
    private val syncMetrics: SyncMetrics = mock()
    private val clock: Clock = mock()

    private lateinit var sut: ValidatorSyncManager

    @BeforeEach
    fun beforeEach() {
        ensureAppliedConfigSenderCalled = false
        sut = spy(ValidatorSyncManager(workerContext, loggingContext, statusManager, blockManager, blockDatabase,
                nodeStateTracker, revoltTracker, syncMetrics, { isProcessRunning }, false, ensureAppliedConfigSender, clock))
    }

    @Nested
    inner class dispatchMessages {

        @Test
        fun `with version 1 should ensure AppliedConfigSender`() {
            // setup
            doNothing().whenever(sut).tryToSwitchToFastSync()
            assertThat(ensureAppliedConfigSenderCalled).isFalse()
            addMessage(nodeRid1, 1, Status(blockchainRid.data, height, false, 0, 0, 0, null, null))
            // execute
            sut.dispatchMessages()
            // verify
            assertThat(ensureAppliedConfigSenderCalled).isTrue()
        }

        @Test
        fun `with version 2 should not ensure AppliedConfigSender`() {
            // setup
            doNothing().whenever(sut).tryToSwitchToFastSync()
            assertThat(ensureAppliedConfigSenderCalled).isFalse()
            addMessage(nodeRid1, 2, Status(blockchainRid.data, height, false, 0, 0, 0, null, null))
            // execute
            sut.dispatchMessages()
            // verify
            assertThat(ensureAppliedConfigSenderCalled).isFalse()
        }

        @Test
        fun `with Status with config hash and we are revolting should process incoming config`() {
            // setup
            val nodeStatus = NodeStatus(height, 0).apply { revolting = true }
            doReturn(nodeStatus).whenever(statusManager).myStatus
            val configHash: ByteArray = "configHash".toByteArray()
            addMessage(nodeRid1, 2, Status(blockchainRid.data, height, false, 0, 0, 0, null, configHash))
            doNothing().whenever(sut).processIncomingConfig(isA(), anyLong())
            doNothing().whenever(sut).tryToSwitchToFastSync()
            // execute
            sut.dispatchMessages()
            // verify
            verify(messageDurationTracker).cleanup()
            verify(sut).processIncomingConfig(configHash, height)
            verify(sut).tryToSwitchToFastSync()
        }

        @Test
        fun `with AppliedConfig and we are revolting should process incoming config`() {
            // setup
            val nodeStatus = NodeStatus(height, 0).apply { revolting = true }
            doReturn(nodeStatus).whenever(statusManager).myStatus
            val configHash: ByteArray = "configHash".toByteArray()
            addMessage(nodeRid1, 1, AppliedConfig(configHash, height))
            doNothing().whenever(sut).processIncomingConfig(isA(), anyLong())
            // execute
            sut.dispatchMessages()
            // verify
            verify(sut).processIncomingConfig(configHash, height)
        }
    }

    private fun addMessage(from: NodeRid, version: Long, message: EbftMessage) {
        val packets = listOf(ReceivedPacket(from, version, message))
        doReturn(packets).whenever(communicationManager).getPackets()
    }
}
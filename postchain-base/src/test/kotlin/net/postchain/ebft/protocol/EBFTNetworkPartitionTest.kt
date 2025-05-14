package net.postchain.ebft.protocol

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import net.postchain.core.block.BlockData
import net.postchain.ebft.BaseBlockManager
import net.postchain.ebft.BaseStatusManager
import net.postchain.ebft.NodeBlockState.Prepared
import net.postchain.ebft.NodeBlockState.HaveBlock
import net.postchain.ebft.NodeBlockState.WaitBlock
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.Status
import net.postchain.ebft.syncmanager.configuration.RateLimitConfiguration
import net.postchain.ebft.syncmanager.validator.ValidatorSyncManager
import net.postchain.ebft.worker.WorkerContext
import net.postchain.network.CommunicationManager
import net.postchain.network.ReceivedPacket
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import kotlin.collections.listOf

/**
 * This test will set up status manager for all nodes so that we can accurately simulate scenarios
 */
class EBFTNetworkPartitionTest : EBFTProtocolBase() {

    // Node 0
    private lateinit var statusManager0: BaseStatusManager
    private lateinit var blockManager0: BaseBlockManager
    private lateinit var syncManager0: ValidatorSyncManager
    private val commManager0: CommunicationManager<EbftMessage> = mock()
    private val workerContext0: WorkerContext = mock {
        on { appConfig } doReturn appConfig
        on { nodeConfig } doReturn nodeConfig
        on { engine } doReturn blockchainEngine
        on { communicationManager } doReturn commManager0
        on { peerCommConfiguration } doReturn peerCommConf
        on { blockchainConfiguration } doReturn blockchainConfiguration
        on { messageDurationTracker } doReturn messageDurationTracker
        on { blockchainConfigurationProvider } doReturn blockchainConfigurationProvider
    }

    // Node 1 status manager is setup in base class

    // Node 2
    private lateinit var statusManager2: BaseStatusManager
    private lateinit var blockManager2: BaseBlockManager
    private lateinit var syncManager2: ValidatorSyncManager
    private val commManager2: CommunicationManager<EbftMessage> = mock()
    private val workerContext2: WorkerContext = mock {
        on { appConfig } doReturn appConfig
        on { nodeConfig } doReturn nodeConfig
        on { engine } doReturn blockchainEngine
        on { communicationManager } doReturn commManager2
        on { peerCommConfiguration } doReturn peerCommConf
        on { blockchainConfiguration } doReturn blockchainConfiguration
        on { messageDurationTracker } doReturn messageDurationTracker
        on { blockchainConfigurationProvider } doReturn blockchainConfigurationProvider
    }

    // Node 3
    private lateinit var statusManager3: BaseStatusManager
    private lateinit var blockManager3: BaseBlockManager
    private lateinit var syncManager3: ValidatorSyncManager
    private val commManager3: CommunicationManager<EbftMessage> = mock()
    private val workerContext3: WorkerContext = mock {
        on { appConfig } doReturn appConfig
        on { nodeConfig } doReturn nodeConfig
        on { engine } doReturn blockchainEngine
        on { communicationManager } doReturn commManager3
        on { peerCommConfiguration } doReturn peerCommConf
        on { blockchainConfiguration } doReturn blockchainConfiguration
        on { messageDurationTracker } doReturn messageDurationTracker
        on { blockchainConfigurationProvider } doReturn blockchainConfigurationProvider
    }

    private val nodeRids = listOf(nodeRid0, nodeRid1, nodeRid2, nodeRid3)
    private lateinit var commManagers: List<CommunicationManager<EbftMessage>>
    private lateinit var statusManagers: List<BaseStatusManager>
    private lateinit var syncManagers: List<ValidatorSyncManager>

    private val nodeConnections = mapOf(
            0 to listOf(1, 2, 3),
            1 to listOf(0, 2, 3),
            2 to listOf(0, 1),
            3 to listOf(0, 1)
    )

    @BeforeEach
    fun setupManagersForAllNodes() {
        // Node 0 setup
        statusManager0 = BaseStatusManager(nodes, 0, 0, nodeStatusMetrics, stateChangeTracker, clock)
        blockManager0 = BaseBlockManager(blockDatabase, statusManager0, blockStrategy, workerContext)
        syncManager0 = ValidatorSyncManager(workerContext0, emptyMap(), statusManager0, blockManager0, blockDatabase, nodeStateTracker, revoltTracker, syncMetrics, { true }, false, { true }, RateLimitConfiguration.fromAppConfig(appConfig), clock)

        // Node 2 setup
        statusManager2 = BaseStatusManager(nodes, 2, 0, nodeStatusMetrics, stateChangeTracker, clock)
        blockManager2 = BaseBlockManager(blockDatabase, statusManager2, blockStrategy, workerContext)
        syncManager2 = ValidatorSyncManager(workerContext2, emptyMap(), statusManager2, blockManager2, blockDatabase, nodeStateTracker, revoltTracker, syncMetrics, { true }, false, { true }, RateLimitConfiguration.fromAppConfig(appConfig), clock)

        // Node 3 setup
        statusManager3 = BaseStatusManager(nodes, 3, 0, nodeStatusMetrics, stateChangeTracker, clock)
        blockManager3 = BaseBlockManager(blockDatabase, statusManager3, blockStrategy, workerContext)
        syncManager3 = ValidatorSyncManager(workerContext3, emptyMap(), statusManager3, blockManager3, blockDatabase, nodeStateTracker, revoltTracker, syncMetrics, { true }, false, { true }, RateLimitConfiguration.fromAppConfig(appConfig), clock)

        commManagers = listOf(commManager0, commManager, commManager2, commManager3)
        statusManagers = listOf(statusManager0, statusManager, statusManager2, statusManager3)
        syncManagers = listOf(syncManager0, syncManager, syncManager2, syncManager3)
    }

    @Test
    fun `Demonstrate how chain can get stuck when two nodes can not talk to each other`() {
        // Node 2 and 3 are not able to connect to each other and won't receive any status updates from each other

        // ---- PART 1 ---- //
        // Node0 moved into prepared state, but the others did not have time to do so before seeing the revolt. This is
        // necessary to trigger the known issue https://chromaway.atlassian.net/browse/POS-953
        // This exact order of events is not the only possible one, it's just an example.

        // Node 0 is primary and builds a block
        val firstBlock = createBlockHeader(blockchainRid, 0L, 0, prevBlockRid, 0)
        val firstBlockRid = firstBlock.blockRID
        val blockData = BlockData(firstBlock, listOf())
        whenever(blockStrategy.shouldBuildBlock()).doReturn(true)
        doReturn(CompletableFuture.completedStage(blockData to signature)).whenever(blockDatabase).buildBlock()

        propagateAllNodeStatuses()
        // Ensure we don't build any new block directly on next round
        whenever(blockStrategy.shouldBuildBlock()).doReturn(false)
        // Now both starts revolting due to timeout
        statusManager0.onStartRevolting()
        statusManager.onStartRevolting()
        propagateAllNodeStatuses()

        // We have to imagine that below events happen roughly at the same time

        // Node 1 has finally fetched it from node 0 and that news reaches node 0 but not yet node 2 and 3
        statusManager.acceptBlock(firstBlockRid, signature)
        propagateNodeStatus(1, listOf(0))

        // Node 2 has fetched it from node 0 and that news reaches node 0 but not yet node 1
        statusManager2.acceptBlock(firstBlockRid, signature)
        propagateNodeStatus(2, listOf(0))

        // Verify states so that we know we have the expected state up until here.
        // The crucial part is that only node 0 has seen enough statuses.
        // We have to imagine that node 2 has sent HaveBlock status to node 1, but it has not arrived yet.
        assertThat(statusManager0.myStatus.state).isEqualTo(Prepared)
        assertThat(statusManager.myStatus.state).isEqualTo(HaveBlock)
        assertThat(statusManager2.myStatus.state).isEqualTo(HaveBlock)
        assertThat(statusManager3.myStatus.state).isEqualTo(WaitBlock)

        // It wont make any difference, but news of node 0 going into Prepared state reaches the other nodes
        propagateNodeStatus(0)

        // Node 3 is revolting at the same time
        // We have to imagine that this reaches node 1 before the node 2 status update reaches it
        statusManager3.onStartRevolting()
        propagateNodeStatus(3)

        // Node 2 status update reaches node 1, but it is too late
        propagateNodeStatus(2, listOf(1))

        // Ok, we are done, simplest way to get node 2 onboard for the next round is that it also times out for revolt
        // here
        statusManager2.onStartRevolting()
        propagateAllNodeStatuses()

        // ---- PART 2 ---- //
        // Now we have the known scenario https://chromaway.atlassian.net/browse/POS-953
        // It's usually fine, but here we illustrate how it can cause a chain to get stuck if two nodes can't connect
        // to each other.

        // We have moved to a new round due to revolt
        assertThat(statusManagers.all { it.myStatus.round == 1L }).isTrue()
        assertThat(statusManager0.myStatus.state).isEqualTo(Prepared)
        assertThat(statusManager.myStatus.state).isEqualTo(WaitBlock)
        assertThat(statusManager2.myStatus.state).isEqualTo(WaitBlock)
        assertThat(statusManager3.myStatus.state).isEqualTo(WaitBlock)

        // Now it is turn for node 1 to build a block
        val secondBlock = createBlockHeader(blockchainRid, 0L, 0, prevBlockRid, 0, ByteArray(32) { 1 })
        val secondBlockRid = secondBlock.blockRID
        val secondBlockData = BlockData(secondBlock, listOf())
        whenever(blockStrategy.shouldBuildBlock()).doReturn(true)
        doReturn(CompletableFuture.completedStage(secondBlockData to signature)).whenever(blockDatabase).buildBlock()
        // Node 1 has built a new block and informs other nodes
        propagateAllNodeStatuses()

        // Node 2 & 3 loads the new block
        // Node 0 does not fetch the new block since it is already in Prepared state
        statusManager2.acceptBlock(secondBlockRid, signature)
        statusManager3.acceptBlock(secondBlockRid, signature)
        propagateAllNodeStatuses()

        // Assert statuses and see that we are now in a state that can only be recovered from by either:
        // 1. Node 2 and 3 connection being healed so they can see each others statuses
        // 2. Node 0 or 1 being restarted
        assertThat(statusManager0.myStatus.state).isEqualTo(Prepared)
        assertThat(statusManager0.myStatus.blockRID.contentEquals(firstBlockRid)).isTrue()

        assertThat(statusManager.myStatus.state).isEqualTo(Prepared)
        assertThat(statusManager.myStatus.blockRID.contentEquals(secondBlockRid)).isTrue()

        assertThat(statusManager2.myStatus.state).isEqualTo(HaveBlock)
        assertThat(statusManager2.myStatus.blockRID.contentEquals(secondBlockRid)).isTrue()

        assertThat(statusManager3.myStatus.state).isEqualTo(HaveBlock)
        assertThat(statusManager3.myStatus.blockRID.contentEquals(secondBlockRid)).isTrue()

        // Now we have an eternal revolt loop until conditions listed above are met
        // When node becomes primary:
        // - Node 0: Will propose the first block, node 2 & 3 will load it but never move to Prepared
        // - Node 1: Will propose the second block, node 2 & 3 will load it but never move to Prepared
        // - Node 2 or 3: Will propose a new block that no other node will load
    }

    private fun propagateNodeStatus(nodeId: Int, propagateToNodes: List<Int> = nodeConnections[nodeId]!!) {
        propagateToNodes.forEach {
            doReturn(listOf(ReceivedPacket(nodeRids[nodeId], 2, getNodeStatus(nodeId))))
                    .whenever(commManagers[it]).getPackets()
            syncManagers[it].update()
        }
    }

    private fun propagateAllNodeStatuses() {
        (0 .. 3).forEach {
            propagateNodeStatus(it)
        }
    }

    private fun getNodeStatus(nodeId: Int): Status {
        val nodeStatus = statusManagers[nodeId].myStatus

        return Status(
                nodeStatus.blockRID,
                nodeStatus.height,
                nodeStatus.revolting,
                nodeStatus.round,
                nodeStatus.serial,
                nodeStatus.state.ordinal,
                nodeStatus.signature
        )
    }
}
package net.postchain.ebft.protocol

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.crypto.Signature
import net.postchain.ebft.BuildBlockIntent
import net.postchain.ebft.DoNothingIntent
import net.postchain.ebft.FetchCommitSignatureIntent
import net.postchain.ebft.FetchUnfinishedBlockIntent
import net.postchain.ebft.NodeBlockState.HaveBlock
import net.postchain.ebft.NodeBlockState.Prepared
import net.postchain.ebft.NodeBlockState.WaitBlock
import net.postchain.ebft.message.BlockSignature
import net.postchain.ebft.message.GetBlockSignature
import net.postchain.ebft.message.GetUnfinishedBlock
import net.postchain.ebft.message.Status
import net.postchain.ebft.message.UnfinishedBlock
import net.postchain.network.ReceivedPacket
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

class EBFTSignerTest : EBFTProtocolBase() {

    @Test
    fun `Test normal EBFT cycle as signer`() {
        /**
         * Input: Receiving [HaveBlock] status from primary block builder (node 0).
         * Expected outcome: Send request for unfinished block.
         * State: [WaitBlock] -> [WaitBlock]
         * Intent: [DoNothingIntent] -> [FetchUnfinishedBlockIntent]
         * Receive: [Status] with block info from node 0
         * Send: [GetUnfinishedBlock] to (random) node 0
         */
        // setup
        verifyIntent(DoNothingIntent)
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid0, 1, Status(blockRid0, 0, false, 0, 1, HaveBlock.ordinal))
        )
        // execute
        syncManager.update()
        // verify
        verifyIntent(FetchUnfinishedBlockIntent(blockRid0))
        argumentCaptor<GetUnfinishedBlock> {
            verify(commManager).sendPacket(capture(), eq(nodeRid0))
            assertThat(firstValue.blockRID).isEqualTo(blockRid0)
        }
        verifyStatus(blockRID = null, height = 0, serial = 0, round = 0, revolting = false, state = WaitBlock)
        reset(commManager)

        /**
         * Input: Receiving [UnfinishedBlock] from primary block builder (node 0).
         * Expected outcome: Transfer to [HaveBlock] state and waiting on consensus to be in [HaveBlock].
         * State: [WaitBlock] -> [HaveBlock]
         * Intent: [FetchUnfinishedBlockIntent] -> [DoNothingIntent]
         * Receive: [UnfinishedBlock] from node 0
         * Send: Broadcast [Status]
         */
        // setup
        doReturn(CompletableFuture.completedStage(signature)).whenever(blockDatabase).loadUnfinishedBlock(isA())
        doReturn(header0).whenever(blockchainConfiguration).decodeBlockHeader(header0.rawData)
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid0, 1, UnfinishedBlock(header0.rawData, emptyList()))
        )
        // execute
        syncManager.update()
        // verify
        verifyIntent(DoNothingIntent)
        verifyStatus(blockRID = blockRid0, height = 0, serial = 1, round = 0, revolting = false, state = HaveBlock)
        reset(commManager)

        /**
         * Input: Receiving [Status] from node 2.
         * Expected outcome: Transfer to [Prepared] state and waiting on consensus to be in [Prepared].
         * State: [HaveBlock] -> [Prepared]
         * Intent: [DoNothingIntent] -> [DoNothingIntent]
         * Receive: [Status] with [HaveBlock] from node 2
         * Send: Broadcast [Status]
         */
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid2, 1, Status(blockRid0, 0, false, 0, 1, HaveBlock.ordinal))
        )
        // execute
        syncManager.update()
        // verify
        verifyIntent(DoNothingIntent)
        verifyStatus(blockRID = blockRid0, height = 0, serial = 2, round = 0, revolting = false, state = Prepared)
        reset(commManager)

        /**
         * Input: Receiving [Status] [Prepared] from node 0 and 2.
         * Expected outcome: Send request for commit signatures to node 0 and 2.
         * State: [Prepared] -> [Prepared]
         * Intent: [DoNothingIntent] -> [FetchCommitSignatureIntent]
         * Receive: [Status] with [Prepared] from node 0 and 2
         * Send: [GetBlockSignature] to node 0 and 2
         */
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid0, 1, Status(blockRid0, 0, false, 0, 2, Prepared.ordinal)),
                ReceivedPacket(nodeRid2, 1, Status(blockRid0, 0, false, 0, 2, Prepared.ordinal))
        )
        // execute
        syncManager.update()
        // verify
        verifyIntent(FetchCommitSignatureIntent(blockRid0, arrayOf(0, 2)))
        argumentCaptor<GetBlockSignature> {
            verify(commManager).sendPacket(capture(), eq(listOf(nodeRid0, nodeRid2)))
            assertThat(firstValue.blockRID).isEqualTo(blockRid0)
        }
        reset(commManager)

        /**
         * Input: Receiving block signatures from node 0 and 2.
         * Expected outcome: Block is committed, and the node is in WaitBlock state and is now the primary node.
         * State: [Prepared] -> [WaitBlock]
         * Intent: [FetchCommitSignatureIntent] -> [CommitBlockIntent] -> [BuildBlockIntent]
         * Receive: [BlockSignature] from node 0 and 2
         * Send: Broadcast [Status]
         */
        // setup
        doReturn(true).whenever(blockDatabase).verifyBlockSignature(isA())
        doReturn(CompletableFuture.completedStage(Unit)).whenever(blockDatabase).commitBlock(isA())
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid0, 1, BlockSignature(blockRid0, Signature(node0, ByteArray(0)))),
                ReceivedPacket(nodeRid2, 1, BlockSignature(blockRid0, Signature(node2, ByteArray(0))))
        )
        // execute
        syncManager.update()
        // verify
        verifyIntent(BuildBlockIntent)
        verify(blockDatabase).commitBlock(isA())
        verifyStatus(blockRID = null, height = 1, serial = 3, round = 0, revolting = false, state = WaitBlock)
        reset(commManager)
    }

    @Test
    fun `If consensus on same height but higher round, use lowest higher consensus round`() {
        /**
         * Input: We receive [Status] from other nodes with higher rounds.
         * Expected outcome: Round in status message should be equal to the lowest round from the consensus number
         * of nodes with higher round.
         * State: [WaitBlock] -> [WaitBlock]
         * Intent: [DoNothingIntent] -> [BuildBlockIntent]
         * Receive: Revolt from other nodes.
         * Send: Broadcast [Status]
         */
        // setup
        assertThat(statusManager.myStatus.round).isEqualTo(0)
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid0, 1, Status(null, 0, true, 4, 1, WaitBlock.ordinal)),
                ReceivedPacket(nodeRid2, 1, Status(null, 0, true, 5, 1, WaitBlock.ordinal)),
                ReceivedPacket(nodeRid3, 1, Status(null, 0, true, 6, 1, WaitBlock.ordinal))
        )
        // execute
        syncManager.update()
        // verify
        assertThat(statusManager.myStatus.round).isEqualTo(4)
        verifyStatus(blockRID = null, height = 0, serial = 1, round = 4, revolting = false, state = WaitBlock)
    }

    @Test
    fun `If consensus on same height but higher round and in HaveBlock, use lowest higher consensus round and reset to WaitBlock`() {
        /**
         * Setup node to state [HaveBlock]
         */
        // setup
        doReturn(CompletableFuture.completedStage(signature)).whenever(blockDatabase).loadUnfinishedBlock(isA())
        doReturn(header0).whenever(blockchainConfiguration).decodeBlockHeader(header0.rawData)

        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid0, 1, Status(blockRid0, 0, false, 0, 1, HaveBlock.ordinal))
        )
        // execute
        syncManager.update()
        // verify
        verifyStatus(blockRID = null, height = 0, serial = 0, round = 0, revolting = false, state = WaitBlock)
        reset(commManager)

        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid0, 1, UnfinishedBlock(header0.rawData, emptyList()))
        )
        // execute
        syncManager.update()
        // verify
        verifyStatus(blockRID = blockRid0, height = 0, serial = 1, round = 0, revolting = false, state = HaveBlock)
        reset(commManager)

        /**
         * Input: We are in [HaveBlock] and receive [Status] from other nodes with higher rounds.
         * Expected outcome: Round in status message should be equal to the lowest round from the consensus number
         * of nodes with higher round and node should be in [WaitBlock].
         * State: [HaveBlock] -> [WaitBlock]
         * Receive: Revolt from other nodes.
         * Send: Broadcast [Status]
         */
        // setup
        assertThat(statusManager.myStatus.round).isEqualTo(0)
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid0, 1, Status(null, 0, true, 4, 1, WaitBlock.ordinal)),
                ReceivedPacket(nodeRid2, 1, Status(null, 0, true, 5, 1, WaitBlock.ordinal)),
                ReceivedPacket(nodeRid3, 1, Status(null, 0, true, 6, 1, WaitBlock.ordinal))
        )
        // execute
        syncManager.update()
        // verify
        assertThat(statusManager.myStatus.round).isEqualTo(4)
        verifyStatus(blockRID = null, height = 0, serial = 3, round = 4, revolting = false, state = WaitBlock)
    }

    @Test
    fun `If not consensus on same height but higher round, do not update round`() {
        /**
         * Input: We receive [Status] from other nodes with higher rounds, but not enough to form consensus.
         * Expected outcome: Round should not be updated
         * State: [WaitBlock] -> [WaitBlock]
         * Intent: [DoNothingIntent] -> [BuildBlockIntent]
         * Receive: Revolt from other nodes.
         * Send: Broadcast [Status]
         */
        // setup
        assertThat(statusManager.myStatus.round).isEqualTo(0)
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid2, 1, Status(null, 0, true, 5, 1, WaitBlock.ordinal)),
                ReceivedPacket(nodeRid3, 1, Status(null, 0, true, 6, 1, WaitBlock.ordinal))
        )
        // execute
        syncManager.update()
        // verify
        assertThat(statusManager.myStatus.round).isEqualTo(0)
        verifyStatus(blockRID = null, height = 0, serial = 0, round = 0, revolting = false, state = WaitBlock)
    }
}
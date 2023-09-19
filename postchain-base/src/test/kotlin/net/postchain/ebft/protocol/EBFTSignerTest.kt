package net.postchain.ebft.protocol

import assertk.assertThat
import assertk.assertions.isEqualTo
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
         * Send: [GetUnfinishedBlock] to node 0
         */
        // setup
        verifyIntent(DoNothingIntent)
        // incoming messages
        messagesToReceive(
                nodeRid0 to Status(blockRid0, 0, false, 0, 1, HaveBlock.ordinal)
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
                nodeRid0 to UnfinishedBlock(header0.rawData, emptyList())
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
                nodeRid2 to Status(blockRid0, 0, false, 0, 1, HaveBlock.ordinal)
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
                nodeRid0 to Status(blockRid0, 0, false, 0, 2, Prepared.ordinal),
                nodeRid2 to Status(blockRid0, 0, false, 0, 2, Prepared.ordinal)
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
                nodeRid0 to BlockSignature(blockRid0, net.postchain.ebft.message.Signature(node0, ByteArray(0))),
                nodeRid2 to BlockSignature(blockRid0, net.postchain.ebft.message.Signature(node2, ByteArray(0)))
        )
        // execute
        syncManager.update()
        // verify
        verifyIntent(BuildBlockIntent)
        verify(blockDatabase).commitBlock(isA())
        verifyStatus(blockRID = null, height = 1, serial = 3, round = 0, revolting = false, state = WaitBlock)
        reset(commManager)
    }
}
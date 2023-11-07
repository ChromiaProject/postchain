package net.postchain.ebft.protocol

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.core.block.BlockData
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
import net.postchain.ebft.message.Status
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

class EBFTPrimaryTest : EBFTProtocolBase() {

    @Test
    fun `Test normal EBFT cycle as primary`() {
        // setup
        becomePrimary()

        /**
         * Input: Block should be built.
         * Expected outcome: Build block and broadcast [HaveBlock]
         * State: [WaitBlock] -> [HaveBlock]
         * Intent: [BuildBlockIntent] -> [DoNothingIntent]
         * Send: Broadcast [Status]
         */
        // setup
        doReturn(false).whenever(blockStrategy).mustWaitBeforeBuildBlock()
        doReturn(false).whenever(blockStrategy).preemptiveBlockBuilding()
        doReturn(true).whenever(blockStrategy).shouldBuildBlock()
        val blockData = BlockData(header0, listOf())
        doReturn(CompletableFuture.completedStage(blockData to signature)).whenever(blockDatabase).buildBlock()
        // execute
        syncManager.update()
        // verify
        verify(blockStrategy).setForceStopBlockBuilding(false)
        verifyIntent(DoNothingIntent)
        verifyStatus(blockRID = blockRid0, height = 1, serial = 4, round = 0, revolting = false, state = HaveBlock, signature = signature)
        reset(commManager)

        /**
         * Input: Other nodes will fetch unfinished block from us and move to [HaveBlock] and broadcast their status.
         * Expected outcome: Transfer to [Prepared] state and waiting on consensus to be in [Prepared].
         * State: [HaveBlock] -> [Prepared]
         * Intent: [DoNothingIntent] -> [DoNothingIntent]
         * Receive: [Status] with [HaveBlock] from node 2 and 3
         * Send: Broadcast [Status]
         */
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid2, 2, Status(blockRid0, 1, false, 0, 4, HaveBlock.ordinal, Signature(node2, ByteArray(0)))),
                ReceivedPacket(nodeRid3, 1, Status(blockRid0, 1, false, 0, 4, HaveBlock.ordinal))
        )
        // execute
        syncManager.update()
        // verify
        verifyIntent(DoNothingIntent)
        verifyStatus(blockRID = blockRid0, height = 1, serial = 5, round = 0, revolting = false, state = Prepared, signature = signature)
        reset(commManager)

        /**
         * Input: Receiving [Status] [Prepared] from node 2 and 3.
         * Expected outcome: Send request for commit signatures to node 2 and 3.
         * State: [Prepared] -> [Prepared]
         * Intent: [DoNothingIntent] -> [FetchCommitSignatureIntent]
         * Receive: [Status] with [Prepared] from node 2 and 3
         * Send: [GetBlockSignature] to node 2 and 3
         */
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid2, 2, Status(blockRid0, 1, false, 0, 5, Prepared.ordinal, Signature(node2, ByteArray(0)))),
                ReceivedPacket(nodeRid3, 1, Status(blockRid0, 1, false, 0, 5, Prepared.ordinal))
        )
        // execute
        syncManager.update()
        // verify
        verifyIntent(FetchCommitSignatureIntent(blockRid0, arrayOf(3)))
        argumentCaptor<GetBlockSignature> {
            verify(commManager).sendPacket(capture(), eq(listOf(nodeRid3)))
            assertThat(firstValue.blockRID).isEqualTo(blockRid0)
        }
        reset(commManager)

        /**
         * Input: Receiving block signatures from node 2 and 3.
         * Expected outcome: Block is committed, and the node is in WaitBlock state and is not the primary node.
         * Intent is [FetchUnfinishedBlockIntent] due to not having got [Status] messages from node 2 and 3 yet.
         * State: [Prepared] -> [WaitBlock]
         * Intent: [FetchCommitSignatureIntent] -> [CommitBlockIntent] -> [FetchUnfinishedBlockIntent]
         * Receive: [BlockSignature] from node 2 and 3
         * Send: Broadcast [Status]
         */
        // setup
        doReturn(true).whenever(blockDatabase).applyAndVerifyBlockSignature(isA())
        doReturn(CompletableFuture.completedStage(Unit)).whenever(blockDatabase).commitBlock(isA())
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid3, 1, BlockSignature(blockRid0, Signature(node3, ByteArray(0))))
        )
        // execute
        syncManager.update()
        // verify
        verifyIntent(FetchUnfinishedBlockIntent(blockRid0))
        verify(blockDatabase).commitBlock(isA())
        verifyStatus(blockRID = null, height = 2, serial = 6, round = 0, revolting = false, state = WaitBlock)
        reset(commManager)

        /**
         * Input: Receiving [Status] from node 2 and 3.
         * Expected outcome: Intent is [DoNothingIntent]
         * State: [WaitBlock] -> [WaitBlock]
         * Intent: [FetchCommitSignatureIntent] -> [DoNothingIntent]
         * Receive: [Status] from node 2 and 3
         */
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid2, 2, Status(null, 2, false, 0, 6, WaitBlock.ordinal)),
                ReceivedPacket(nodeRid3, 1, Status(null, 2, false, 0, 6, WaitBlock.ordinal))
        )
        // execute
        syncManager.update()
        // verify
        verifyIntent(DoNothingIntent)
        reset(commManager)
    }
}
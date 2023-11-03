package net.postchain.ebft.protocol

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.ebft.BuildBlockIntent
import net.postchain.ebft.DoNothingIntent
import net.postchain.ebft.FetchUnfinishedBlockIntent
import net.postchain.ebft.NodeBlockState.HaveBlock
import net.postchain.ebft.NodeBlockState.WaitBlock
import net.postchain.ebft.message.GetUnfinishedBlock
import net.postchain.ebft.message.Status
import net.postchain.ebft.message.UnfinishedBlock
import net.postchain.network.ReceivedPacket
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

class EBFTRevoltTest : EBFTProtocolBase() {

    @Test
    fun `Revolt without revolt from other nodes (yet)`() {
        // execute
        statusManager.onStartRevolting()
        syncManager.update()
        // verify
        verifyStatus(blockRID = null, height = 0, serial = 1, round = 0, revolting = true, state = WaitBlock)
    }

    @Test
    fun `Revolt and other nodes revolt`() {
        /**
         * Input: We revolt and receive revolt [Status] from other non-primary nodes.
         * Expected outcome: Change primary node to node1 (us)
         * State: [WaitBlock] -> [WaitBlock]
         * Intent: [DoNothingIntent] -> [BuildBlockIntent]
         * Receive: Revolt from other nodes.
         * Send: Broadcast [Status]
         */
        // setup
        verifyIntent(DoNothingIntent)
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid2, 1, Status(null, 0, true, 0, 1, WaitBlock.ordinal, null)),
                ReceivedPacket(nodeRid3, 1, Status(null, 0, true, 0, 1, WaitBlock.ordinal, null))
        )
        // execute
        statusManager.onStartRevolting()
        syncManager.update()
        // verify
        verifyIntent(BuildBlockIntent)
        verifyStatus(blockRID = null, height = 0, serial = 2, round = 1, revolting = false, state = WaitBlock)
    }

    @Test
    fun `Primary is malicious and attempts to produce an invalid block`() {
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
                ReceivedPacket(nodeRid0, 1, Status(blockRid0, 0, false, 0, 1, HaveBlock.ordinal, null))
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
         * Input: Receiving [UnfinishedBlock] from primary block builder (node 0) with bad block.
         * Expected outcome: Stay in [WaitBlock] state and keep intent [FetchUnfinishedBlockIntent].
         * State: [WaitBlock] -> [WaitBlock]
         * Intent: [FetchUnfinishedBlockIntent] -> [FetchUnfinishedBlockIntent]
         * Receive: [UnfinishedBlock] from node 0 with bad block
         */
        // setup
        doReturn(header0).whenever(blockchainConfiguration).decodeBlockHeader(header0.rawData)
        doReturn(CompletableFuture.failedFuture<Exception>(Exception())).whenever(blockDatabase).loadUnfinishedBlock(isA())
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid0, 1, UnfinishedBlock(header0.rawData, emptyList()))
        )
        // execute
        syncManager.update()
        // verify
        verifyIntent(FetchUnfinishedBlockIntent(blockRid0))
        verify(commManager, never()).sendPacket(isA(), eq(nodeRid0))
        reset(commManager)

        /**
         * Input: We do not get a valid [UnfinishedBlock] in time, so we revolt.
         * Expected outcome: Stay in the same state as we try to start revolt, but no other nodes have joined.
         * State: [WaitBlock] -> [WaitBlock]
         * Intent: [FetchUnfinishedBlockIntent] -> [FetchUnfinishedBlockIntent]
         * Send: Broadcast [Status]
         */
        // execute
        statusManager.onStartRevolting()
        syncManager.update()
        // verify
        verifyIntent(FetchUnfinishedBlockIntent(blockRid0))
        verifyStatus(blockRID = null, height = 0, serial = 1, round = 0, revolting = true, state = WaitBlock)
        reset(commManager)

        /**
         * Input: We receive revolt [Status] from other non-primary nodes.
         * Expected outcome: Change primary node to node1 (us)
         * State: [WaitBlock] -> [WaitBlock]
         * Intent: [FetchUnfinishedBlockIntent] -> [BuildBlockIntent]
         * Receive: Revolt from other nodes.
         * Send: Broadcast [Status]
         */
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid2, 1, Status(null, 0, true, 0, 1, WaitBlock.ordinal, null)),
                ReceivedPacket(nodeRid3, 1, Status(null, 0, true, 0, 1, WaitBlock.ordinal, null))
        )
        syncManager.update()
        verifyIntent(BuildBlockIntent)
        verifyStatus(blockRID = null, height = 0, serial = 2, round = 1, revolting = false, state = WaitBlock)
    }

    @Test
    fun `Primary revolts against it self should force stop building block`() {
        // setup
        becomePrimary()
        syncManager.update()
        reset(commManager)

        /**
         * Input: We revolt and receive revolt [Status] from other non-primary nodes.
         * Expected outcome: Change primary node, and force-stop any potential ongoing block building
         * State: [WaitBlock] -> [WaitBlock]
         * Intent: [BuildBlockIntent] -> [DoNothingIntent]
         * Receive: Revolt from other nodes.
         * Send: Broadcast [Status]
         */
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid2, 1, Status(null, 1, true, 0, 5, WaitBlock.ordinal, null)),
                ReceivedPacket(nodeRid3, 1, Status(null, 1, true, 0, 5, WaitBlock.ordinal, null))
        )
        // execute
        statusManager.onStartRevolting()
        syncManager.update()
        // verify
        verify(blockStrategy).setForceStopBlockBuilding(true)
        verifyIntent(DoNothingIntent)
        verifyStatus(blockRID = null, height = 1, serial = 5, round = 1, revolting = false, state = WaitBlock)
    }

    @Test
    fun `Revolt should pick lowest consensus round`() {
        /**
         * Input: We revolt and receive revolt [Status] from other nodes with higher rounds.
         * Expected outcome: Round in status message should be equal to the lowest round from the consensus number
         * of nodes with higher round.
         * State: [WaitBlock] -> [WaitBlock]
         * Intent: [DoNothingIntent] -> [BuildBlockIntent]
         * Receive: Revolt from other nodes.
         * Send: Broadcast [Status]
         */
        // setup
        statusManager.onStartRevolting()
        syncManager.update()
        verifyStatus(blockRID = null, height = 0, serial = 1, round = 0, revolting = true, state = WaitBlock)
        reset(commManager)
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid0, 1, Status(null, 0, true, 4, 1, WaitBlock.ordinal, null)),
                ReceivedPacket(nodeRid2, 1, Status(null, 0, true, 5, 1, WaitBlock.ordinal, null)),
                ReceivedPacket(nodeRid3, 1, Status(null, 0, true, 6, 1, WaitBlock.ordinal, null))
        )
        // execute
        syncManager.update()
        // verify
        verifyStatus(blockRID = null, height = 0, serial = 3, round = 4, revolting = false, state = WaitBlock)
    }

    @Test
    fun `Revolt should pick next round if not enough higher rounds in consensus`() {
        /**
         * Input: We revolt and receive revolt [Status] from other nodes.
         * Expected outcome: Round in status message should be equal to our start round + 1.
         * State: [WaitBlock] -> [WaitBlock]
         * Intent: [DoNothingIntent] -> [BuildBlockIntent]
         * Receive: Revolt from other nodes.
         * Send: Broadcast [Status]
         */
        // setup
        // incoming messages
        messagesToReceive(
                ReceivedPacket(nodeRid0, 1, Status(null, 0, true, 0, 1, WaitBlock.ordinal, null)),
                ReceivedPacket(nodeRid2, 1, Status(null, 0, true, 5, 1, WaitBlock.ordinal, null)),
                ReceivedPacket(nodeRid3, 1, Status(null, 0, true, 6, 1, WaitBlock.ordinal, null))
        )
        syncManager.update()
        reset(commManager)
        // execute
        statusManager.onStartRevolting()
        syncManager.update()
        // verify
        verifyStatus(blockRID = null, height = 0, serial = 2, round = 1, revolting = false, state = WaitBlock)
    }
}
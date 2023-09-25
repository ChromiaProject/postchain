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
                nodeRid2 to Status(null, 0, true, 0, 1, WaitBlock.ordinal),
                nodeRid3 to Status(null, 0, true, 0, 1, WaitBlock.ordinal)
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
                nodeRid0 to UnfinishedBlock(header0.rawData, emptyList())
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
                nodeRid2 to Status(null, 0, true, 0, 1, WaitBlock.ordinal),
                nodeRid3 to Status(null, 0, true, 0, 1, WaitBlock.ordinal)
        )
        syncManager.update()
        verifyIntent(BuildBlockIntent)
        verifyStatus(blockRID = null, height = 0, serial = 2, round = 1, revolting = false, state = WaitBlock)
    }
}

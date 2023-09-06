package net.postchain.ebft.syncmanager.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import net.postchain.base.BaseBlockHeader
import net.postchain.base.BlockWitnessProvider
import net.postchain.base.NetworkNodes
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.extension.CONFIG_HASH_EXTRA_HEADER
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.core.BadMessageException
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.NodeRid
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockWitness
import net.postchain.core.block.BlockWitnessBuilder
import net.postchain.ebft.BlockDatabase
import net.postchain.ebft.message.AppliedConfig
import net.postchain.ebft.message.BlockData
import net.postchain.ebft.message.BlockRange
import net.postchain.ebft.message.CompleteBlock
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockHeaderAndBlock
import net.postchain.ebft.message.GetBlockRange
import net.postchain.ebft.message.GetBlockSignature
import net.postchain.ebft.worker.WorkerContext
import net.postchain.gtv.Gtv
import net.postchain.network.CommunicationManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anySet
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

class SlowSynchronizerTest {

    private val currentTimeMillis = 42L
    private val currentSleepMs = 15L
    private val peerIds = mutableSetOf<NodeRid>()
    private val height = 54L
    private val lastBlockHeight = 10L
    private val startHeight = 4L
    private val brid = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    private val blockRID = BlockchainRid.buildFromHex(brid)
    private val node = "0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"
    private val nodeRid = NodeRid.fromHex(node)
    private val toExcludeNodeRid = NodeRid.fromHex("2".repeat(32))
    private val header: ByteArray = "header".toByteArray()
    private val witness: ByteArray = "witness".toByteArray()
    private val transactions: List<ByteArray> = listOf("tx1".toByteArray())
    private val configHash = "configHash".toByteArray()

    private val headerRec: BlockHeaderData = mock {
        on { getHeight() } doReturn height
    }
    private val blockQueries: BlockQueries = mock {
        val completionStage: CompletionStage<Long> = CompletableFuture.completedStage(lastBlockHeight)
        on { getLastBlockHeight() } doReturn completionStage
    }
    private val blockWitness: BlockWitness = mock()
    private val configHashGtv: Gtv = mock {
        on { asByteArray(ArgumentMatchers.anyBoolean()) } doReturn configHash
    }
    private val headerExtraData = mapOf(
            CONFIG_HASH_EXTRA_HEADER to configHashGtv
    )
    private val blockHeader: BaseBlockHeader = mock {
        on { blockHeaderRec } doReturn headerRec
        on { extraData } doReturn headerExtraData
    }
    private val blockWitnessBuilder: BlockWitnessBuilder = mock()
    private val blockWitnessProvider: BlockWitnessProvider = mock {
        on { createWitnessBuilderWithoutOwnSignature(blockHeader) } doReturn blockWitnessBuilder
    }
    private val blockchainConfiguration: BlockchainConfiguration = mock {
        on { decodeBlockHeader(header) } doReturn blockHeader
        on { decodeWitness(witness) } doReturn blockWitness
        on { getBlockHeaderValidator() } doReturn blockWitnessProvider
        on { blockchainRid } doReturn blockRID
        on { configHash } doReturn configHash
    }
    private val blockchainEngine: BlockchainEngine = mock {
        on { getBlockQueries() } doReturn blockQueries
        on { getConfiguration() } doReturn blockchainConfiguration
    }
    private val commManager: CommunicationManager<EbftMessage> = mock()
    private val networkNodes: NetworkNodes = mock {
        on { getPeerIds() } doReturn peerIds
    }
    private val peerCommConf: PeerCommConfiguration = mock {
        on { networkNodes } doReturn networkNodes
    }
    private val workerContext: WorkerContext = mock {
        on { engine } doReturn blockchainEngine
        on { communicationManager } doReturn commManager
        on { peerCommConfiguration } doReturn peerCommConf
        on { blockchainConfiguration } doReturn blockchainConfiguration
    }
    private val blockDatabase: BlockDatabase = mock()
    private val params = SyncParameters()
    private val stateMachine: SlowSyncStateMachine = mock {
        on { getStartHeight() } doReturn startHeight
    }
    private val peerStatuses: SlowSyncPeerStatuses = mock()
    private val slowSyncSleepData: SlowSyncSleepData = mock {
        on { currentSleepMs } doReturn currentSleepMs
    }
    private val clock: Clock = mock {
        on { millis() } doReturn currentTimeMillis
    }
    private val blockDataWithWitness: BlockDataWithWitness = mock()
    private val blockData: BlockData = mock {
        on { header } doReturn header
        on { transactions } doReturn transactions
    }
    private val isProcessRunningProvider: () -> Boolean = mock()
    private val condition: Condition = mock()
    private val reentrantLock: ReentrantLock = spy(ReentrantLock())

    private lateinit var sut: SlowSynchronizer

    @BeforeEach
    fun setup() {
        doReturn(condition).whenever(reentrantLock).newCondition()
        sut = spy(SlowSynchronizer(
                workerContext,
                blockDatabase,
                params,
                clock,
                isProcessRunningProvider,
                { stateMachine },
                { peerStatuses },
                { slowSyncSleepData },
                { reentrantLock }
        ))
    }

    @Test
    fun `syncUntil should process messages and call state machine and on exit clear peer statuses`() {
        // setup
        doReturn(true, false).whenever(isProcessRunningProvider).invoke()
        doNothing().whenever(sut).processMessages(isA())
        // execute
        sut.syncUntil()
        // verify
        verify(sut).processMessages(slowSyncSleepData)
        verify(peerStatuses).clear()
        verify(stateMachine).maybeGetBlockRange(eq(currentTimeMillis), eq(currentSleepMs), isA())
    }

    @Nested
    inner class sendRequest {
        @Test
        fun `with no peers should do nothing`() {
            // execute
            sut.sendRequest(currentTimeMillis, stateMachine, null)
            // verify
            verify(commManager, never()).sendToRandomPeer(isA(), anySet())
        }

        @Test
        fun `with no valid peers should do nothing`() {
            // setup
            peerIds.add(toExcludeNodeRid)
            doReturn(setOf(toExcludeNodeRid)).whenever(peerStatuses).exclNonSyncable(4L, currentTimeMillis)
            // execute
            sut.sendRequest(currentTimeMillis, stateMachine, null)
            // verify
            verify(stateMachine).getStartHeight()
            verify(peerStatuses).exclNonSyncable(anyLong(), anyLong())
            verify(commManager, never()).sendToRandomPeer(isA(), anySet())
        }

        @Test
        fun `with failed commit should acknowledge failed commit and update state machine`() {
            // setup
            peerIds.add(toExcludeNodeRid)
            peerIds.add(nodeRid)
            doReturn(setOf(toExcludeNodeRid)).whenever(peerStatuses).exclNonSyncable(anyLong(), anyLong())
            doReturn(nodeRid to setOf(nodeRid)).whenever(commManager).sendToRandomPeer(isA(), anySet())
            doReturn(true).whenever(stateMachine).hasUnacknowledgedFailedCommit()
            // execute
            sut.sendRequest(currentTimeMillis, stateMachine, null)
            // verify
            verify(stateMachine).acknowledgeFailedCommit()
            verify(stateMachine).updateToWaitForReply(nodeRid, startHeight, currentTimeMillis)
            verify(commManager).sendToRandomPeer(isA(), eq(setOf(nodeRid)))
        }

        @Test
        fun `should update state machine`() {
            // setup
            peerIds.add(toExcludeNodeRid)
            peerIds.add(nodeRid)
            doReturn(setOf(toExcludeNodeRid)).whenever(peerStatuses).exclNonSyncable(anyLong(), anyLong())
            doReturn(nodeRid to setOf(nodeRid)).whenever(commManager).sendToRandomPeer(isA(), anySet())
            doReturn(false).whenever(stateMachine).hasUnacknowledgedFailedCommit()
            // execute
            sut.sendRequest(currentTimeMillis, stateMachine, null)
            // verify
            verify(stateMachine, never()).acknowledgeFailedCommit()
            verify(stateMachine).updateToWaitForReply(nodeRid, startHeight, currentTimeMillis)
            verify(commManager).sendToRandomPeer(isA(), eq(setOf(nodeRid)))
        }

        @Test
        fun `with no picked peer should do nothing`() {
            // setup
            peerIds.add(toExcludeNodeRid)
            peerIds.add(nodeRid)
            doReturn(setOf<NodeRid>()).whenever(peerStatuses).exclNonSyncable(anyLong(), anyLong())
            doReturn(null to setOf(nodeRid)).whenever(commManager).sendToRandomPeer(isA(), anySet())
            // execute
            sut.sendRequest(currentTimeMillis, stateMachine, nodeRid)
            // verify
            verify(stateMachine, never()).acknowledgeFailedCommit()
            verify(stateMachine, never()).updateToWaitForReply(nodeRid, startHeight, currentTimeMillis)
            verify(commManager).sendToRandomPeer(isA(), eq(setOf(toExcludeNodeRid)))
        }
    }

    @Nested
    inner class processMessages {
        @Test
        fun `with no messages should do nothing`() {
            // setup
            doReturn(emptyList<Pair<NodeRid, EbftMessage>>()).whenever(commManager).getPackets()
            // execute
            sut.processMessages(slowSyncSleepData)
            // verify
            verify(peerStatuses, never()).isBlacklisted(isA())
        }

        @Test
        fun `with blacklisted peer should do nothing`() {
            // setup
            doReturn(listOf(nodeRid to GetBlockHeaderAndBlock(lastBlockHeight))).whenever(commManager).getPackets()
            doReturn(true).whenever(peerStatuses).isBlacklisted(isA())
            // execute
            sut.processMessages(slowSyncSleepData)
            // verify
            verify(peerStatuses).isBlacklisted(nodeRid)
            verify(peerStatuses, never()).confirmModern(isA())
        }

        @Test
        fun `with message GetBlockHeaderAndBlock should call internal method`() {
            // setup
            doReturn(listOf(nodeRid to GetBlockHeaderAndBlock(height))).whenever(commManager).getPackets()
            doNothing().whenever(sut).sendBlockHeaderAndBlock(isA(), anyLong(), anyLong())
            // execute
            sut.processMessages(slowSyncSleepData)
            // verify
            verify(peerStatuses).isBlacklisted(nodeRid)
            verify(peerStatuses).confirmModern(nodeRid)
            verify(sut).sendBlockHeaderAndBlock(nodeRid, height, lastBlockHeight)
        }

        @Test
        fun `with message GetBlockAtHeight should call internal method`() {
            // setup
            doReturn(listOf(nodeRid to GetBlockAtHeight(height))).whenever(commManager).getPackets()
            doNothing().whenever(sut).sendBlockAtHeight(isA(), anyLong())
            // execute
            sut.processMessages(slowSyncSleepData)
            // verify
            verify(peerStatuses, never()).confirmModern(nodeRid)
            verify(sut).sendBlockAtHeight(nodeRid, height)
        }

        @Test
        fun `with message GetBlockRange should call internal method`() {
            // setup
            doReturn(listOf(nodeRid to GetBlockRange(height))).whenever(commManager).getPackets()
            doNothing().whenever(sut).sendBlockRangeFromHeight(isA(), anyLong(), anyLong())
            // execute
            sut.processMessages(slowSyncSleepData)
            // verify
            verify(peerStatuses, never()).confirmModern(nodeRid)
            verify(sut).sendBlockRangeFromHeight(nodeRid, height, lastBlockHeight)
        }

        @Test
        fun `with message GetBlockSignature should call internal method`() {
            // setup
            doReturn(listOf(nodeRid to GetBlockSignature(blockRID.data))).whenever(commManager).getPackets()
            doNothing().whenever(sut).sendBlockSignature(isA(), isA())
            // execute
            sut.processMessages(slowSyncSleepData)
            // verify
            verify(peerStatuses, never()).confirmModern(nodeRid)
            verify(sut).sendBlockSignature(nodeRid, blockRID.data)
        }

        @Test
        fun `with message AppliedConfig should call internal method`() {
            // setup
            val configHash = "configHash".toByteArray()
            val message = AppliedConfig(configHash, height)
            doReturn(listOf(nodeRid to message)).whenever(commManager).getPackets()
            doReturn(true).whenever(sut).checkIfWeNeedToApplyPendingConfig(isA(), isA())
            // execute
            sut.processMessages(slowSyncSleepData)
            // verify
            verify(peerStatuses, never()).confirmModern(nodeRid)
            verify(sut).checkIfWeNeedToApplyPendingConfig(nodeRid, message)
        }

        @Test
        fun `with message BlockRange should call internal method and update sleep data`() {
            // setup
            val completeBlock = CompleteBlock(blockData, height, witness)
            val blocks = listOf(completeBlock)
            val processedBlocks = 37
            doReturn(listOf(nodeRid to BlockRange(startHeight, false, blocks))).whenever(commManager).getPackets()
            doReturn(processedBlocks).whenever(sut).handleBlockRange(nodeRid, blocks, startHeight)
            // execute
            sut.processMessages(slowSyncSleepData)
            // verify
            verify(peerStatuses, never()).confirmModern(nodeRid)
            verify(sut).handleBlockRange(nodeRid, blocks, startHeight)
            verify(slowSyncSleepData).updateData(processedBlocks)
        }
    }

    @Nested
    inner class handleBlockRange {
        @Test
        fun `with wrong state should blacklist peer and return 0`() {
            // setup
            doReturn(SlowSyncStates.WAIT_FOR_ACTION).whenever(stateMachine).state
            // execute & verify
            assertThat(sut.handleBlockRange(nodeRid, emptyList(), startHeight)).isEqualTo(0)
            // verify
            verify(stateMachine, never()).isHeightWeWaitingFor(anyLong())
            verify(peerStatuses).maybeBlacklist(eq(nodeRid), startsWith("Slow Sync: We are not waiting for a block range"))
        }

        @Test
        fun `with not expected start height should blacklist peer and return 0`() {
            // setup
            val completeBlock = CompleteBlock(blockData, height, witness)
            val blocks = listOf(completeBlock)
            doReturn(SlowSyncStates.WAIT_FOR_REPLY).whenever(stateMachine).state
            doReturn(false).whenever(stateMachine).isHeightWeWaitingFor(anyLong())
            // execute & verify
            assertThat(sut.handleBlockRange(nodeRid, blocks, startHeight)).isEqualTo(0)
            // verify
            verify(stateMachine).isHeightWeWaitingFor(startHeight)
            verify(peerStatuses).maybeBlacklist(eq(nodeRid), startsWith("Slow Sync: Peer: "))
        }

        @Test
        fun `with waiting blocks and unacknowledged failed commit should wait for blocks to commit and acknowledge failed commit`() {
            // setup
            val completeBlock = CompleteBlock(blockData, height, witness)
            val blocks = listOf(completeBlock)
            doReturn(SlowSyncStates.WAIT_FOR_REPLY).whenever(stateMachine).state
            doReturn(true).whenever(stateMachine).isHeightWeWaitingFor(anyLong())
            doReturn(true).whenever(stateMachine).isWaitingForBlocksToCommit()
            doNothing().whenever(condition).await()
            doReturn(true).whenever(stateMachine).hasUnacknowledgedFailedCommit()
            // execute & verify
            assertThat(sut.handleBlockRange(nodeRid, blocks, startHeight)).isEqualTo(0)
            // verify
            verify(condition).await()
            verify(stateMachine).acknowledgeFailedCommit()
            verify(stateMachine).resetToWaitForAction(currentTimeMillis)
        }

        @Test
        fun `with no blocks should do nothing and return 0`() {
            // setup
            doReturn(SlowSyncStates.WAIT_FOR_REPLY).whenever(stateMachine).state
            doReturn(true).whenever(stateMachine).isHeightWeWaitingFor(anyLong())
            // execute & verify
            assertThat(sut.handleBlockRange(nodeRid, emptyList(), startHeight)).isEqualTo(0)
            // verify
            verify(condition, never()).await()
            verify(stateMachine, never()).acknowledgeFailedCommit()
            verify(stateMachine).resetToWaitForAction(currentTimeMillis)
        }

        @Test
        fun `with failed to handle block header should give up`() {
            // setup
            val completeBlock = CompleteBlock(blockData, height, witness)
            val blocks = listOf(completeBlock, mock())
            doReturn(SlowSyncStates.WAIT_FOR_REPLY).whenever(stateMachine).state
            doReturn(true).whenever(stateMachine).isHeightWeWaitingFor(anyLong())
            doReturn(null).whenever(sut).handleBlockHeader(isA(), isA(), isA(), anyLong())
            // execute & verify
            assertThat(sut.handleBlockRange(nodeRid, blocks, startHeight)).isEqualTo(0)
            // verify
            verify(sut).handleBlockHeader(nodeRid, header, witness, startHeight)
            verify(sut, never()).handleBlock(isA(), isA(), isA(), anyLong(), anyList())
            verify(stateMachine, never()).resetToWaitForAction(anyLong())
        }

        @Test
        fun `should handle blocks`() {
            // setup
            val completeBlock = CompleteBlock(blockData, height, witness)
            val blocks = listOf(completeBlock)
            doReturn(SlowSyncStates.WAIT_FOR_REPLY).whenever(stateMachine).state
            doReturn(true).whenever(stateMachine).isHeightWeWaitingFor(anyLong())
            doReturn(blockHeader to blockWitness).whenever(sut).handleBlockHeader(isA(), isA(), isA(), anyLong())
            doNothing().whenever(sut).handleBlock(isA(), isA(), isA(), anyLong(), anyList())
            // execute & verify
            assertThat(sut.handleBlockRange(nodeRid, blocks, startHeight)).isEqualTo(1)
            // verify
            verify(sut).handleBlockHeader(nodeRid, header, witness, startHeight)
            verify(sut).handleBlock(nodeRid, blockHeader, blockWitness, startHeight, transactions)
            verify(stateMachine).resetToWaitForAction(currentTimeMillis)
        }
    }

    @Nested
    inner class handleBlockHeader {
        @Test
        fun `with empty header and empty witness should blacklist peer and return null`() {
            // execute & verify
            assertThat(sut.handleBlockHeader(nodeRid, "".toByteArray(), "".toByteArray(), startHeight)).isNull()
            // verify
            verify(peerStatuses).maybeBlacklist(eq(nodeRid), startsWith("Slow Sync: Sent empty header at height: "))
        }

        @Test
        fun `with empty header should blacklist peer and return null`() {
            // execute & verify
            assertThat(sut.handleBlockHeader(nodeRid, "".toByteArray(), witness, startHeight)).isNull()
            // verify
            verify(peerStatuses).maybeBlacklist(eq(nodeRid), startsWith("Slow Sync: Why we get a witness without a header? Height: "))
        }

        @Test
        fun `with mismatching peer height should blocklist peer`() {
            // execute & verify
            assertThat(sut.handleBlockHeader(nodeRid, header, witness, startHeight)).isNull()
            // verify
            verify(peerStatuses).maybeBlacklist(eq(nodeRid), startsWith("Slow Sync: Header height="))
        }

        @Test
        fun `with config mismatch should do nothing and return block header and witness`() {
            // setup
            doReturn("".toByteArray()).whenever(blockchainConfiguration).configHash
            // execute & verify
            assertThat(sut.handleBlockHeader(nodeRid, header, witness, height)).isEqualTo(blockHeader to blockWitness)
            // verify
            verify(blockchainConfiguration, never()).getBlockHeaderValidator()
        }

        @Test
        fun `with missing config hash and failed to validate witness should blacklist peer`() {
            // setup
            doReturn(null).whenever(blockHeader).extraData
            doThrow(RuntimeException("Failure")).whenever(blockWitnessProvider).validateWitness(isA(), isA())
            // execute & verify
            assertThat(sut.handleBlockHeader(nodeRid, header, witness, height)).isNull()
            // verify
            verify(blockWitnessProvider).validateWitness(blockWitness, blockWitnessBuilder)
            verify(peerStatuses).maybeBlacklist(eq(nodeRid), startsWith("Slow Sync: Invalid header received"))
        }

        @Test
        fun `with matching config hash and failed to validate witness should blacklist peer`() {
            // setup
            doThrow(RuntimeException("Failure")).whenever(blockWitnessProvider).validateWitness(isA(), isA())
            // execute & verify
            assertThat(sut.handleBlockHeader(nodeRid, header, witness, height)).isNull()
            // verify
            verify(blockWitnessProvider).validateWitness(blockWitness, blockWitnessBuilder)
            verify(peerStatuses).maybeBlacklist(eq(nodeRid), startsWith("Slow Sync: Invalid header received"))
        }

        @Test
        fun `with matching config hash should validate witness and return block header and witness`() {
            // setup
            doNothing().whenever(blockWitnessProvider).validateWitness(isA(), isA())
            // execute & verify
            assertThat(sut.handleBlockHeader(nodeRid, header, witness, height)).isEqualTo(blockHeader to blockWitness)
            // verify
            verify(blockWitnessProvider).validateWitness(blockWitness, blockWitnessBuilder)
            verify(peerStatuses, never()).maybeBlacklist(isA(), anyString())
        }
    }

    @Nested
    inner class handleBlock {
        @Test
        fun `with wrong type of block header should throw exception`() {
            // execute & verify
            assertThrows<BadMessageException> {
                sut.handleBlock(nodeRid, mock(), blockWitness, height, transactions)
            }
        }

        @Test
        fun `should call internal commit`() {
            // setup
            doNothing().whenever(sut).commitBlock(isA(), eq(null), isA(), anyLong())
            // execute
            sut.handleBlock(nodeRid, blockHeader, blockWitness, height, transactions)
            // verify
            verify(stateMachine).updateUncommittedBlockHeight(height)
            argumentCaptor<BlockDataWithWitness>().apply {
                verify(sut).commitBlock(eq(nodeRid), eq(null), capture(), eq(height))
                assertThat(firstValue.header).isEqualTo(blockHeader)
                assertThat(firstValue.witness).isEqualTo(blockWitness)
                assertThat(firstValue.transactions).isEqualTo(transactions)
            }
        }
    }

    @Nested
    inner class commitBlock {
        @Test
        fun `with exception should call internal exception handling and update state`() {
            // setup
            val exception = Exception("Failure")
            val completionStage: CompletionStage<Unit> = CompletableFuture.failedStage(exception)
            doReturn(completionStage).whenever(blockDatabase).addBlock(isA(), any(), any())
            doNothing().whenever(sut).handleAddBlockException(isA(), isA(), any(), isA(), isA())
            // execute
            sut.commitBlock(nodeRid, null, blockDataWithWitness, height)
            // verify
            verify(sut).handleAddBlockException(exception, blockDataWithWitness, null, peerStatuses, nodeRid)
            verify(stateMachine).updateAfterFailedCommit(height)
        }

        @Test
        fun `with no commits waiting should signal all to commit`() {
            // setup
            val exception = Exception("Failure")
            val completionStage: CompletionStage<Unit> = CompletableFuture.failedStage(exception)
            doReturn(completionStage).whenever(blockDatabase).addBlock(isA(), any(), any())
            doReturn(false).whenever(stateMachine).isWaitingForBlocksToCommit()
            doNothing().whenever(sut).handleAddBlockException(isA(), isA(), any(), isA(), isA())
            // execute
            sut.commitBlock(nodeRid, null, blockDataWithWitness, height)
            // verify
            verify(stateMachine).isWaitingForBlocksToCommit()
            verify(condition).signalAll()
        }

        @Test
        fun `with successful commit should update state`() {
            // setup
            val completionStage: CompletionStage<Unit> = CompletableFuture.completedStage(null)
            doReturn(completionStage).whenever(blockDatabase).addBlock(isA(), any(), any())
            // execute
            sut.commitBlock(nodeRid, null, blockDataWithWitness, height)
            // verify
            verify(stateMachine).updateAfterSuccessfulCommit(height)
            verify(stateMachine).isWaitingForBlocksToCommit()
        }
    }
}
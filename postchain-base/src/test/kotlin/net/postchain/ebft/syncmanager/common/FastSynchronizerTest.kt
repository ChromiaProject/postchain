package net.postchain.ebft.syncmanager.common

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.postchain.base.BaseBlockHeader
import net.postchain.base.BlockWitnessProvider
import net.postchain.base.NetworkNodes
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.BadMessageException
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.NodeRid
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.core.PrevBlockMismatchException
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockWitness
import net.postchain.core.block.BlockWitnessBuilder
import net.postchain.ebft.BDBAbortException
import net.postchain.ebft.BlockDatabase
import net.postchain.ebft.message.AppliedConfig
import net.postchain.ebft.message.BlockData
import net.postchain.ebft.message.CompleteBlock
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockHeaderAndBlock
import net.postchain.ebft.message.GetBlockRange
import net.postchain.ebft.message.GetBlockSignature
import net.postchain.ebft.message.Status
import net.postchain.ebft.message.UnfinishedBlock
import net.postchain.ebft.worker.WorkerContext
import net.postchain.network.CommunicationManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anySet
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class FastSynchronizerTest {

    private var isProcessRunning: Boolean = true
    private val brid = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    private val blockRID = BlockchainRid.buildFromHex(brid)
    private val node = "0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"
    private val nodeRid = NodeRid.fromHex(node)
    private val otherNodeRid = NodeRid.fromHex("1".repeat(32))
    private val height = 10L
    private val lastBlockHeight = 10L
    private val header: ByteArray = "header".toByteArray()
    private val witness: ByteArray = "witness".toByteArray()
    private val transactions: List<ByteArray> = listOf("tx1".toByteArray())
    private val currentTimeMillis = 42L
    private val peerIds = mutableSetOf<NodeRid>()

    private val headerRec: BlockHeaderData = mock {
        on { getHeight() } doReturn height
    }
    private val baseBlockHeader: BaseBlockHeader = mock {
        on { blockHeaderRec } doReturn headerRec
        on { rawData } doReturn header
    }
    private val commManager: CommunicationManager<EbftMessage> = mock()
    private val blockQueries: BlockQueries = mock {
        val completionStage: CompletionStage<Long> = CompletableFuture.completedStage(lastBlockHeight)
        on { getLastBlockHeight() } doReturn completionStage
    }
    private val blockWitness: BlockWitness = mock {
        on { getRawData() } doReturn witness
    }
    private val blockWitnessBuilder: BlockWitnessBuilder = mock()
    private val blockWitnessProvider: BlockWitnessProvider = mock {
        on { createWitnessBuilderWithoutOwnSignature(baseBlockHeader) } doReturn blockWitnessBuilder
    }
    private val blockchainConfiguration: BlockchainConfiguration = mock {
        on { decodeBlockHeader(header) } doReturn baseBlockHeader
        on { decodeWitness(witness) } doReturn blockWitness
        on { getBlockHeaderValidator() } doReturn blockWitnessProvider
        on { blockchainRid } doReturn blockRID
    }
    private val blockchainEngine: BlockchainEngine = mock {
        on { getBlockQueries() } doReturn blockQueries
        on { getConfiguration() } doReturn blockchainConfiguration
    }
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
    private val clock: Clock = mock {
        on { millis() } doReturn currentTimeMillis
    }
    private val peerStatuses: FastSyncPeerStatuses = mock()
    private val params = SyncParameters()

    private lateinit var sut: FastSynchronizer

    @BeforeEach
    fun setup() {
        sut = spy(FastSynchronizer(workerContext, blockDatabase, params, peerStatuses, clock) { isProcessRunning })
    }

    ///// processDoneJob /////
    @Test
    fun `processDoneJob should remove old job and increase block height`() {
        // setup
        val job = addJob(height, nodeRid)
        // execute
        sut.processDoneJob(job, false)
        // verify
        assertThat(sut.jobs[job.height]).isNull()
        assertThat(sut.blockHeight).isEqualTo(lastBlockHeight + 1)
    }

    @Test
    fun `processDoneJob with allowed job start should remove old job and start next job and increase block height`() {
        // setup
        doReturn(true).whenever(sut).startJob(anyLong())
        val job = addJob(height, nodeRid)
        // execute
        sut.processDoneJob(job)
        // verify
        verify(sut).startJob(anyLong())
        assertThat(sut.jobs[job.height]).isNull()
        assertThat(sut.blockHeight).isEqualTo(lastBlockHeight + 1)
    }

    @Test
    fun `processDoneJob with PmEngineIsAlreadyClosed should remove job`() {
        // setup
        val job = addJob(height, nodeRid)
        job.addBlockException = PmEngineIsAlreadyClosed("Failure")
        // execute
        sut.processDoneJob(job)
        // verify
        assertThat(sut.jobs[job.height]).isNull()
        assertThat(sut.blockHeight).isEqualTo(lastBlockHeight)
    }

    @Test
    fun `processDoneJob with BDBAbortException should cleanup job and re-submit`() {
        // setup
        doNothing().whenever(sut).commitJobsAsNecessary(any())
        val job = addJob(height, nodeRid)
        job.addBlockException = BDBAbortException(mock())
        job.blockCommitting = true
        // execute
        sut.processDoneJob(job)
        // verify
        assertThat(sut.jobs[job.height]).isNotNull()
        assertThat(sut.blockHeight).isEqualTo(lastBlockHeight)
        assertThat(job.blockCommitting).isFalse()
        assertThat(job.addBlockException).isNull()
        verify(sut).commitJobsAsNecessary(any())
    }

    @Test
    fun `processDoneJob with PrevBlockMismatchException should bail and re-throw exception`() {
        // setup
        val job = addJob(height, nodeRid)
        job.addBlockException = PrevBlockMismatchException("Failure")
        // execute & verify
        assertThat(
                assertThrows<PrevBlockMismatchException> {
                    sut.processDoneJob(job)
                }
        ).isEqualTo(job.addBlockException)
    }

    @Test
    fun `processDoneJob with job should be considered done should increase block height and remove job`() {
        // setup
        val job = addJob(height, nodeRid)
        job.addBlockException = Exception()
        // execute
        sut.processDoneJob(job)
        // verify
        assertThat(sut.jobs[job.height]).isNull()
        assertThat(sut.blockHeight).isEqualTo(lastBlockHeight + 1)
    }

    @Test
    fun `processDoneJob with unknown exception should blacklist peer and remove job`() {
        // setup
        val job = addJob(height + 1, nodeRid)
        sut.jobs[job.height] = job
        job.addBlockException = Exception("Failure")
        // execute
        sut.processDoneJob(job, false)
        // verify
        verify(peerStatuses).maybeBlacklist(eq(nodeRid), anyString())
        assertThat(sut.jobs[job.height]).isNull()
    }

    @Test
    fun `processDoneJob with allowed job start and unknown exception should blacklist peer and restart job`() {
        // setup
        val job = sut.Job(height + 1, nodeRid)
        sut.jobs[job.height] = job
        job.addBlockException = Exception("Failure")
        doNothing().whenever(sut).restartJob(job)
        // execute
        sut.processDoneJob(job)
        // verify
        verify(peerStatuses).maybeBlacklist(nodeRid, "Invalid block $job. Blacklisting peer ${job.peerId}: Failure")
        verify(sut).restartJob(job)
        assertThat(sut.jobs[job.height]).isNotNull()
    }

    ///// processStaleJobs /////
    @Test
    fun `processStaleJobs with job failed restart should restart job`() {
        // setup
        val job = addJob(height, nodeRid)
        job.hasRestartFailed = true
        doNothing().whenever(sut).restartJob(job)
        // execute
        sut.processStaleJobs()
        // verify
        verify(sut).restartJob(job)
    }

    @Test
    fun `processStaleJobs with timed out job and job failed restart should restart job and mark peer unresponsive`() {
        // setup
        params.jobTimeout = 0
        whenever(clock.millis()).doReturnConsecutively(listOf(0, 42))
        val job = addJob(height, nodeRid)
        job.hasRestartFailed = true
        doNothing().whenever(sut).restartJob(job)
        doNothing().whenever(peerStatuses).unresponsive(isA(), anyString())
        // execute
        sut.processStaleJobs()
        // verify
        verify(sut).restartJob(job)
        verify(peerStatuses).unresponsive(nodeRid, "Sync: Marking peer for restarted job $job unresponsive")
    }

    @Test
    fun `processStaleJobs with timed out job and missing job block and is confirmed new node should restart job and mark peer unresponsive`() {
        // setup
        params.jobTimeout = 0
        whenever(clock.millis()).doReturnConsecutively(listOf(0, 42))
        val job = addJob(height, nodeRid)
        doNothing().whenever(sut).restartJob(job)
        doNothing().whenever(peerStatuses).unresponsive(isA(), anyString())
        doReturn(true).whenever(peerStatuses).isConfirmedModern(isA())
        // execute
        sut.processStaleJobs()
        // verify
        verify(sut).restartJob(job)
        verify(peerStatuses).unresponsive(nodeRid, "Sync: Marking modern peer for job $job unresponsive")
    }


    @Test
    fun `processStaleJobs with timed out job and missing job block and is maybe legacy node should restart job and mark peer unresponsive and set node status as not legacy`() {
        // setup
        params.jobTimeout = 0
        whenever(clock.millis()).doReturnConsecutively(listOf(0, 42))
        val job = addJob(height, nodeRid)
        doNothing().whenever(sut).restartJob(job)
        doNothing().whenever(peerStatuses).unresponsive(isA(), anyString())
        doNothing().whenever(peerStatuses).setMaybeLegacy(isA(), anyBoolean())
        doReturn(true).whenever(peerStatuses).isMaybeLegacy(isA())
        // execute
        sut.processStaleJobs()
        // verify
        verify(sut).restartJob(job)
        verify(peerStatuses).setMaybeLegacy(nodeRid, false)
        verify(peerStatuses).unresponsive(nodeRid, "Sync: Marking potentially legacy peer for job $job unresponsive")
    }

    @Test
    fun `processStaleJobs with timed out job and missing job block and is legacy node should restart job and set node status as legacy`() {
        // setup
        params.jobTimeout = 0
        whenever(clock.millis()).doReturnConsecutively(listOf(0, 42))
        val job = addJob(height, nodeRid)
        doNothing().whenever(sut).restartJob(job)
        doNothing().whenever(peerStatuses).setMaybeLegacy(isA(), anyBoolean())
        doReturn(false).whenever(peerStatuses).isMaybeLegacy(isA())
        // execute
        sut.processStaleJobs()
        // verify
        verify(sut).restartJob(job)
        verify(peerStatuses).setMaybeLegacy(nodeRid, true)
    }

    @Test
    fun `processStaleJobs with 2 jobs from same peer should not check if maybe legacy twice if not legacy`() {
        // setup
        params.jobTimeout = 0
        whenever(clock.millis()).doReturnConsecutively(listOf(0, 10, 42))
        val job1 = addJob(height, nodeRid)
        val job2 = addJob(height + 1, nodeRid)
        doNothing().whenever(sut).restartJob(isA())
        doNothing().whenever(peerStatuses).unresponsive(isA(), anyString())
        doNothing().whenever(peerStatuses).setMaybeLegacy(isA(), anyBoolean())
        doReturn(false, true).whenever(peerStatuses).isMaybeLegacy(isA())
        // execute
        sut.processStaleJobs()
        // verify
        argumentCaptor<FastSynchronizer.Job>().apply {
            verify(sut, times(2)).restartJob(capture())
            val jobs = allValues
            assertThat(jobs.size).isEqualTo(2)
            assertThat(jobs[0]).isEqualTo(job1)
            assertThat(jobs[1]).isEqualTo(job2)
        }
        argumentCaptor<Boolean>().apply {
            verify(peerStatuses, times(2)).setMaybeLegacy(eq(nodeRid), capture())
            val isLegacies = allValues
            assertThat(isLegacies.size).isEqualTo(2)
            assertThat(isLegacies[0]).isTrue()
            assertThat(isLegacies[1]).isTrue()
        }
    }

    ///// processMessages /////
    @Test
    fun `processMessages with blacklisted peer should ignore message`() {
        // setup
        val message1: GetBlockHeaderAndBlock = mock()
        val message2: GetBlockHeaderAndBlock = mock()
        val packets = listOf<Pair<NodeRid, EbftMessage>>(nodeRid to message1, nodeRid to message2)
        doReturn(packets).whenever(commManager).getPackets()
        doReturn(true).whenever(peerStatuses).isBlacklisted(nodeRid)
        // execute
        sut.processMessages()
        // verify
        verify(peerStatuses, times(2)).isBlacklisted(nodeRid)
        verify(peerStatuses, never()).confirmModern(nodeRid)
    }

    ///// message: GetBlockAtHeight /////
    @Test
    fun `GetBlockAtHeight should call super method`() {
        // setup
        addMessage(GetBlockAtHeight(height))
        doNothing().whenever(sut).sendBlockAtHeight(isA(), anyLong())
        // execute
        sut.processMessages()
        // verify
        verify(sut).sendBlockAtHeight(nodeRid, height)
    }

    ///// message: GetBlockRange /////
    @Test
    fun `GetBlockRange should call super method`() {
        // setup
        val startAtHeight = 42L
        addMessage(GetBlockRange(startAtHeight))
        doNothing().whenever(sut).sendBlockRangeFromHeight(isA(), anyLong(), anyLong())
        // execute
        sut.processMessages()
        // verify
        verify(sut).sendBlockRangeFromHeight(nodeRid, startAtHeight, height)
    }

    ///// message: GetBlockHeaderAndBlock /////
    @Test
    fun `GetBlockHeaderAndBlock should confirm modern node and call super method`() {
        // setup
        val startAtHeight = 42L
        addMessage(GetBlockHeaderAndBlock(startAtHeight))
        doNothing().whenever(sut).sendBlockHeaderAndBlock(isA(), anyLong(), anyLong())
        doNothing().whenever(peerStatuses).confirmModern(isA())
        // execute
        sut.processMessages()
        // verify
        verify(peerStatuses).confirmModern(nodeRid)
        verify(sut).sendBlockHeaderAndBlock(nodeRid, startAtHeight, height)
    }

    ///// message: GetBlockSignature /////
    @Test
    fun `GetBlockSignature should call super method`() {
        // setup
        addMessage(GetBlockSignature(blockRID.data))
        doNothing().whenever(sut).sendBlockSignature(isA(), isA())
        // execute
        sut.processMessages()
        // verify
        verify(sut).sendBlockSignature(nodeRid, blockRID.data)
    }

    ///// message: BlockHeaderMessage /////
    @Test
    fun `BlockHeaderMessage should confirm modern node and call internal method`() {
        // setup
        addMessage(net.postchain.ebft.message.BlockHeader(header, witness, height))
        doReturn(true).whenever(sut).handleBlockHeader(isA(), isA(), isA(), anyLong())
        doNothing().whenever(peerStatuses).confirmModern(isA())
        // execute
        sut.processMessages()
        // verify
        verify(peerStatuses).confirmModern(nodeRid)
        verify(sut).handleBlockHeader(nodeRid, header, witness, height)
    }

    @Test
    fun `handleBlockHeader with missing job should blacklist peer`() {
        // execute
        assertThat(sut.handleBlockHeader(nodeRid, header, witness, height)).isFalse()
        // verify
        verify(peerStatuses).maybeBlacklist(nodeRid, "Sync: Why do we receive a header for a block height not in our job list? Received: height: $height, peerId: $nodeRid")
    }

    @Test
    fun `handleBlockHeader with header from wrong peer should blacklist peer`() {
        // setup
        val job = addJob(height, nodeRid)
        // execute
        assertThat(sut.handleBlockHeader(otherNodeRid, header, witness, height)).isFalse()
        // verify
        verify(peerStatuses).maybeBlacklist(otherNodeRid, "Sync: Why do we receive a header from a peer when we didn't ask this peer? Received: height: $height, peerId: $otherNodeRid, Requested (job): $job")
    }

    @Test
    fun `handleBlockHeader with job already have header should blacklist peer`() {
        // setup
        val job = addJob(height, nodeRid)
        job.header = mock()
        // execute
        assertThat(sut.handleBlockHeader(nodeRid, header, witness, height)).isFalse()
        // verify
        verify(peerStatuses).maybeBlacklist(nodeRid, "Sync: Why do we receive a header when we already have the header? Received: height: $height, peerId: $nodeRid, Requested (job): $job")
    }

    @Test
    fun `handleBlockHeader with empty header and witness should mark peer drained and restart job`() {
        // setup
        val job = addJob(height, nodeRid)
        val emptyHeader = "".toByteArray()
        val emptyWitness = "".toByteArray()
        doNothing().whenever(sut).restartJob(isA())
        doNothing().whenever(peerStatuses).drained(isA(), anyLong(), anyLong())
        // execute
        assertThat(sut.handleBlockHeader(nodeRid, emptyHeader, emptyWitness, height)).isFalse()
        // verify
        verify(sut).restartJob(job)
        verify(peerStatuses).drained(nodeRid, -1, currentTimeMillis)
    }

    @Test
    fun `handleBlockHeader with empty header but not empty witness should blacklist peer`() {
        // setup
        val job = addJob(height, nodeRid)
        val emptyHeader = "".toByteArray()
        // execute
        assertThat(sut.handleBlockHeader(nodeRid, emptyHeader, witness, height)).isFalse()
        // verify
        verify(peerStatuses).maybeBlacklist(nodeRid, "Sync: Why did we get a witness without a header? Received: height: $height, peerId: $nodeRid, Requested (job): $job")
    }

    @Test
    fun `handleBlockHeader with peer height differ from job height should mark peer drained and restart job`() {
        // setup
        val job = addJob(height + 1, nodeRid)
        doNothing().whenever(sut).restartJob(isA())
        doNothing().whenever(peerStatuses).drained(isA(), anyLong(), anyLong())
        // execute
        assertThat(sut.handleBlockHeader(nodeRid, header, witness, height + 1)).isFalse()
        // verify
        verify(sut).restartJob(job)
        verify(peerStatuses).drained(nodeRid, height, currentTimeMillis)
    }

    /**
     * Here we should ideally test when configs match and validation fails but since we can not mock
     * extension functions, in this case BlockHeader.getConfigHash, we could make a method for it to override
     * it but that would force us to open FastSynchronizer and so on so will accept the loss for now and just
     * verify with missing config.
     */
    @Test
    fun `handleBlockHeader with missing config and failed to validate witness should blacklist peer `() {
        // setup
        val job = addJob(height, nodeRid)
        doThrow(RuntimeException("Failure")).whenever(blockWitnessProvider).validateWitness(isA(), isA())
        // execute
        assertThat(sut.handleBlockHeader(nodeRid, header, witness, height)).isFalse()
        // verify
        verify(blockWitnessProvider).validateWitness(blockWitness, blockWitnessBuilder)
        verify(peerStatuses).maybeBlacklist(nodeRid, "Sync: Invalid header received (Failure). Received: height: $height, peerId: $nodeRid, Requested (job): $job")
    }

    @Test
    fun `handleBlockHeader should validate witness and set header and witness on job`() {
        // setup
        val job = addJob(height, nodeRid)
        doNothing().whenever(blockWitnessProvider).validateWitness(isA(), isA())
        doNothing().whenever(peerStatuses).headerReceived(nodeRid, height)
        // execute
        assertThat(sut.handleBlockHeader(nodeRid, header, witness, height)).isTrue()
        // verify
        verify(blockWitnessProvider).validateWitness(blockWitness, blockWitnessBuilder)
        verify(peerStatuses).headerReceived(nodeRid, height)
        assertThat(job.header).isEqualTo(baseBlockHeader)
        assertThat(job.witness).isEqualTo(blockWitness)
    }

    ///// message: UnfinishedBlock /////
    @Test
    fun `UnfinishedBlock should call internal method`() {
        // setup
        addMessage(UnfinishedBlock(header, transactions))
        doNothing().whenever(sut).handleUnfinishedBlock(isA(), isA(), anyList())
        // execute
        sut.processMessages()
        // verify
        verify(sut).handleUnfinishedBlock(nodeRid, header, transactions)
    }

    @Test
    fun `handleUnfinishedBlock with wrong block header type should throw exception`() {
        // setup
        val blockHeader: BlockHeader = mock()
        doReturn(blockHeader).whenever(blockchainConfiguration).decodeBlockHeader(header)
        // execute & verify
        assertThrows<BadMessageException> {
            sut.handleUnfinishedBlock(nodeRid, header, transactions)
        }
    }

    @Test
    fun `handleUnfinishedBlock with missing job should blacklist peer`() {
        // execute
        sut.handleUnfinishedBlock(nodeRid, header, transactions)
        // verify
        verify(peerStatuses).maybeBlacklist(nodeRid, "Sync: Why did we get an unfinished block of height: $height from peer: $nodeRid ? We didn't ask for it")
    }

    @Test
    fun `handleUnfinishedBlock with duplicate block should blacklist peer`() {
        // setup
        val job = addJob(height, nodeRid)
        job.block = mock()
        // execute
        sut.handleUnfinishedBlock(nodeRid, header, transactions)
        // verify
        verify(peerStatuses).maybeBlacklist(nodeRid, "Sync: We got this block height = $height already, why send it again?. $job")
    }

    @Test
    fun `handleUnfinishedBlock with block from wrong peer should blacklist peer`() {
        // setup
        val job = addJob(height, nodeRid)
        // execute
        sut.handleUnfinishedBlock(otherNodeRid, header, transactions)
        // verify
        verify(peerStatuses).maybeBlacklist(otherNodeRid, "Sync: We didn't expect $otherNodeRid to send us an unfinished block (height = $height). We wanted ${job.peerId} to do it. $job")
    }

    @Test
    fun `handleUnfinishedBlock with missing header in job should blacklist peer`() {
        // setup
        val job = addJob(height, nodeRid)
        // execute
        sut.handleUnfinishedBlock(nodeRid, header, transactions)
        // verify
        verify(peerStatuses).maybeBlacklist(nodeRid, "Sync: We don't have a header yet, why does $nodeRid send us an unfinished block (height = $height )? $job")
    }

    @Test
    fun `handleUnfinishedBlock with mismatched expected header should blacklist peer`() {
        // setup
        val job = addJob(height, nodeRid)
        job.header = mock()
        // execute
        sut.handleUnfinishedBlock(nodeRid, header, transactions)
        // verify
        verify(peerStatuses).maybeBlacklist(nodeRid, "Sync: Peer: ${job.peerId} is sending us an unfinished block (height = $height) with a header that doesn't match the header we expected. $job")
    }

    @Test
    fun `handleUnfinishedBlock should create job block and commit jobs`() {
        // setup
        val job = addJob(height, nodeRid)
        job.witness = mock()
        job.header = baseBlockHeader
        doReturn(header).whenever(baseBlockHeader).rawData
        doNothing().whenever(sut).commitJobsAsNecessary(any())
        // execute
        sut.handleUnfinishedBlock(nodeRid, header, transactions)
        // verify
        assertThat(job.block).isNotNull()
        verify(sut).commitJobsAsNecessary(any())
    }

    ///// message: CompleteBlock /////
    @Test
    fun `CompleteBlock with new node should do nothing`() {
        // setup
        addMessage(CompleteBlock(BlockData(header, transactions), height, witness))
        doReturn(false).whenever(peerStatuses).isMaybeLegacy(nodeRid)
        doReturn(false).whenever(sut).handleBlockHeader(isA(), isA(), isA(), anyLong())
        // execute
        sut.processMessages()
        // verify
        verify(peerStatuses).isMaybeLegacy(nodeRid)
        verify(sut, never()).handleBlockHeader(isA(), isA(), isA(), anyLong())
    }

    @Test
    fun `CompleteBlock with legacy node should add header and witness to job`() {
        // setup
        addMessage(CompleteBlock(BlockData(header, transactions), height, witness))
        doReturn(true).whenever(peerStatuses).isMaybeLegacy(nodeRid)
        doReturn(false).whenever(sut).handleBlockHeader(isA(), isA(), isA(), anyLong())
        // execute
        sut.processMessages()
        // verify
        verify(peerStatuses).isMaybeLegacy(nodeRid)
        verify(sut).handleBlockHeader(nodeRid, header, witness, height)
        verify(sut, never()).handleUnfinishedBlock(isA(), isA(), isA())
    }

    @Test
    fun `CompleteBlock with legacy node and unfinished block should add header and witness to job and handle unfinished block`() {
        // setup
        addMessage(CompleteBlock(BlockData(header, transactions), height, witness))
        doReturn(true).whenever(peerStatuses).isMaybeLegacy(nodeRid)
        doReturn(true).whenever(sut).handleBlockHeader(isA(), isA(), isA(), anyLong())
        doNothing().whenever(sut).handleUnfinishedBlock(isA(), isA(), anyList())
        // execute
        sut.processMessages()
        // verify
        verify(peerStatuses).isMaybeLegacy(nodeRid)
        verify(sut).handleBlockHeader(nodeRid, header, witness, height)
        verify(sut).handleUnfinishedBlock(nodeRid, header, transactions)
    }

    ///// message: Status /////
    @Test
    fun `Status should trigger status received`() {
        // setup
        addMessage(Status(blockRID.data, height, false, 0, 0, 0))
        doNothing().whenever(peerStatuses).statusReceived(isA(), anyLong())
        // execute
        sut.processMessages()
        // verify
        verify(peerStatuses).statusReceived(nodeRid, height - 1)
    }

    ///// message: AppliedConfig /////
    @Test
    fun `AppliedConfig should call internal check`() {
        // setup
        val configHash: ByteArray = "configHash".toByteArray()
        val message = AppliedConfig(configHash, height)
        addMessage(message)
        doReturn(true).whenever(sut).checkIfWeNeedToApplyPendingConfig(isA(), isA())
        // execute
        sut.processMessages()
        // verify
        verify(sut).checkIfWeNeedToApplyPendingConfig(nodeRid, message)
    }

    ///// startJob /////
    @Test
    fun `startJob with modern peer should create job and set calculated peer`() {
        // setup
        val toExcludeNodeRid = NodeRid.fromHex("2".repeat(32))
        val selectedPeer = NodeRid.fromHex("3".repeat(32))
        val peerToDisconnect = NodeRid.fromHex("4".repeat(32))
        peerIds.add(nodeRid)
        peerIds.add(toExcludeNodeRid)
        peerIds.add(selectedPeer)
        peerIds.add(peerToDisconnect)
        val connectedPeers = setOf(nodeRid, selectedPeer)
        doReturn(setOf(toExcludeNodeRid)).whenever(peerStatuses).exclNonSyncable(anyLong(), anyLong())
        doReturn(selectedPeer to connectedPeers).whenever(commManager).sendToRandomPeer(isA(), anySet())
        doNothing().whenever(peerStatuses).markConnected(anySet())
        doNothing().whenever(peerStatuses).markDisconnected(anySet())
        doNothing().whenever(peerStatuses).addPeer(selectedPeer)
        // execute
        assertThat(sut.startJob(height)).isTrue()
        // verify
        verify(peerStatuses).markConnected(connectedPeers)
        verify(peerStatuses).markDisconnected(setOf(peerToDisconnect))
        verify(peerStatuses).addPeer(selectedPeer)
        assertThat(sut.jobs[height]).isNotNull()
    }

    @Test
    fun `startJob with missing modern peer should use legacy peer and create job and set calculated peer`() {
        // setup
        peerIds.add(nodeRid)
        val setOfPeers = setOf(nodeRid)
        doReturn(setOfPeers).whenever(peerStatuses).exclNonSyncable(anyLong(), anyLong())
        doReturn(setOfPeers).whenever(peerStatuses).getLegacyPeers(height)
        doReturn(nodeRid to setOfPeers).whenever(commManager).sendToRandomPeer(isA(), anySet())
        doNothing().whenever(peerStatuses).markConnected(anySet())
        doNothing().whenever(peerStatuses).markDisconnected(anySet())
        // execute
        assertThat(sut.startJob(height)).isTrue()
        // verify
        verify(peerStatuses).markConnected(setOfPeers)
        verify(peerStatuses).markDisconnected(emptySet())
        assertThat(sut.jobs[height]).isNotNull()
    }

    @Test
    fun `startJob with no peers should return false`() {
        // setup
        doReturn(emptySet<NodeRid>()).whenever(peerStatuses).exclNonSyncable(anyLong(), anyLong())
        doReturn(emptySet<NodeRid>()).whenever(peerStatuses).getLegacyPeers(height)
        // execute & verify
        assertThat(sut.startJob(height)).isFalse()
    }

    ///// commitJobsAsNecessary /////
    @Test
    fun `commitJobsAsNecessary with not running process should do nothing`() {
        // setup
        val job = addJob(height, nodeRid)
        job.blockCommitting = false
        isProcessRunning = false
        doNothing().whenever(sut).commitBlock(isA(), eq(null), anyBoolean())
        // execute
        sut.commitJobsAsNecessary(null)
        // verify
        verify(sut, never()).commitBlock(isA(), eq(null), anyBoolean())
    }

    @Test
    fun `commitJobsAsNecessary with missing block in job should do nothing`() {
        // setup
        val job = addJob(height, nodeRid)
        job.blockCommitting = false
        doNothing().whenever(sut).commitBlock(isA(), eq(null), anyBoolean())
        // execute
        sut.commitJobsAsNecessary(null)
        // verify
        verify(sut, never()).commitBlock(isA(), eq(null), anyBoolean())
    }

    @Test
    fun `commitJobsAsNecessary with job still committing should do nothing`() {
        // setup
        val job = addJob(height, nodeRid)
        val blockDataWithWitness = BlockDataWithWitness(baseBlockHeader, transactions, blockWitness)
        job.block = blockDataWithWitness
        job.blockCommitting = true
        doNothing().whenever(sut).commitBlock(isA(), eq(null), anyBoolean())
        // execute
        sut.commitJobsAsNecessary(null)
        // verify
        verify(sut, never()).commitBlock(isA(), eq(null), anyBoolean())
    }

    @Test
    fun `commitJobsAsNecessary with job committed should commit block`() {
        // setup
        val job = addJob(height, nodeRid)
        val blockDataWithWitness = BlockDataWithWitness(baseBlockHeader, transactions, blockWitness)
        job.block = blockDataWithWitness
        job.blockCommitting = false
        doNothing().whenever(sut).commitBlock(isA(), eq(null), anyBoolean())
        // execute
        sut.commitJobsAsNecessary(null)
        // verify
        verify(sut).commitBlock(job, null, true)
    }

    ///// commitBlock /////
    @Test
    fun `commitBlock with missing job block should throw exception`() {
        // setup
        val job = addJob(height, nodeRid)
        // execute
        assertThrows<ProgrammerMistake> {
            sut.commitBlock(job, null, true)
        }
        // verify
        assertThat(job.blockCommitting).isTrue()
    }

    @Test
    fun `commitBlock with failed to add block to database should handle exception`() {
        // setup
        val job = addJob(height, nodeRid)
        val block = BlockDataWithWitness(baseBlockHeader, transactions, blockWitness)
        job.block = block
        val exception = Exception("Failure")
        val completionStage: CompletionStage<Unit> = CompletableFuture.failedStage(exception)
        doReturn(completionStage).whenever(blockDatabase).addBlock(block, null, null)
        doNothing().whenever(sut).handleAddBlockException(isA(), isA(), eq(null), isA(), isA())
        // execute
        sut.commitBlock(job, null, true)
        // verify
        assertThat(job.addBlockException).isEqualTo(exception)
        assertThat(job.blockCommitting).isTrue()
        assertThat(sut.finishedJobs).contains(job)
        verify(sut).handleAddBlockException(exception, block, null, peerStatuses, nodeRid)
    }

    @Test
    fun `commitBlock should add block to database`() {
        // setup
        val job = addJob(height, nodeRid)
        val block = BlockDataWithWitness(baseBlockHeader, transactions, blockWitness)
        job.block = block
        val completionStage: CompletionStage<Unit> = CompletableFuture.completedStage(null)
        doReturn(completionStage).whenever(blockDatabase).addBlock(block, null, null)
        // execute
        sut.commitBlock(job, null, true)
        // verify
        assertThat(job.addBlockException).isNull()
        assertThat(job.blockCommitting).isTrue()
        assertThat(sut.finishedJobs).contains(job)
    }

    ///// restartJob /////
    @Test
    fun `restartJob should try to start job`() {
        // setup
        val job = addJob(height, nodeRid)
        doReturn(true).whenever(sut).startJob(height)
        // execute
        sut.restartJob(job)
        // verify
        assertThat(job.hasRestartFailed).isFalse()
    }

    @Test
    fun `restartJob with failed to start job should set failed restart on job`() {
        // setup
        val job = addJob(height, nodeRid)
        doReturn(false).whenever(sut).startJob(height)
        // execute
        sut.restartJob(job)
        // verify
        assertThat(job.hasRestartFailed).isTrue()
    }

    ///// areResponsiveNodesDrained /////
    @Test
    fun `areResponsiveNodesDrained with nodes left to sync should return false`() {
        // setup
        val timeout = currentTimeMillis - 1
        params.mustSyncUntilHeight = 0
        peerIds.add(nodeRid)
        doReturn(setOf(nodeRid)).whenever(peerStatuses).getSyncableAndConnected(anyLong())
        // execute & verify
        assertThat(sut.areResponsiveNodesDrained(timeout)).isFalse()
    }

    @Test
    fun `areResponsiveNodesDrained with time out should return false`() {
        // setup
        val timeout = currentTimeMillis
        params.mustSyncUntilHeight = 0
        peerIds.add(nodeRid)
        doReturn(emptySet<NodeRid>()).whenever(peerStatuses).getSyncableAndConnected(anyLong())
        // execute & verify
        assertThat(sut.areResponsiveNodesDrained(timeout)).isFalse()
    }

    @Test
    fun `areResponsiveNodesDrained with mustSyncUntilHeight is higher then current height should return false`() {
        // setup
        val timeout = currentTimeMillis - 1
        params.mustSyncUntilHeight = height + 1
        peerIds.add(nodeRid)
        doReturn(emptySet<NodeRid>()).whenever(peerStatuses).getSyncableAndConnected(anyLong())
        // execute & verify
        assertThat(sut.areResponsiveNodesDrained(timeout)).isFalse()
    }

    @Test
    fun `areResponsiveNodesDrained with all conditions met should return true`() {
        // setup
        val timeout = currentTimeMillis - 1
        params.mustSyncUntilHeight = 0
        peerIds.add(nodeRid)
        doReturn(emptySet<NodeRid>()).whenever(peerStatuses).getSyncableAndConnected(anyLong())
        // execute & verify
        assertThat(sut.areResponsiveNodesDrained(timeout)).isTrue()
    }

    ///////////////////////////////////////////////////////////////////////////
    private fun addJob(height: Long, nodeRid: NodeRid): FastSynchronizer.Job {
        val job = sut.Job(height, nodeRid)
        sut.jobs[job.height] = job
        return job
    }

    private fun addMessage(message: EbftMessage) {
        val packets = listOf<Pair<NodeRid, EbftMessage>>(nodeRid to message)
        doReturn(packets).whenever(commManager).getPackets()
        doReturn(false).whenever(peerStatuses).isBlacklisted(nodeRid)
    }
}
// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.base.BaseBlockHeader
import net.postchain.base.extension.getConfigHash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.toHex
import net.postchain.concurrent.util.get
import net.postchain.concurrent.util.whenCompleteUnwrapped
import net.postchain.core.BadDataException
import net.postchain.core.BadMessageException
import net.postchain.core.NodeRid
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.core.PrevBlockMismatchException
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockTrace
import net.postchain.core.block.BlockWitness
import net.postchain.devtools.NameHelper
import net.postchain.ebft.BDBAbortException
import net.postchain.ebft.BlockDatabase
import net.postchain.ebft.message.AppliedConfig
import net.postchain.ebft.message.CompleteBlock
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockHeaderAndBlock
import net.postchain.ebft.message.GetBlockRange
import net.postchain.ebft.message.GetBlockSignature
import net.postchain.ebft.message.Status
import net.postchain.ebft.message.Transaction
import net.postchain.ebft.message.UnfinishedBlock
import net.postchain.ebft.worker.WorkerContext
import java.time.Clock
import java.util.TreeMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import net.postchain.ebft.message.BlockHeader as BlockHeaderMessage

/**
 * This class syncs blocks from its peers by requesting <parallelism> blocks
 * from random peers simultaneously.
 *
 * The peers respond to the requests using a BlockHeader immediately followed
 * by an UnfinishedBlock, but if they don't have the block, they respond with
 * their latest BlockHeader and the height that was requested. If they
 * don't have any blocks at all, they reply with a BlockHeader with empty header
 * and witness.
 *
 * Requests that times out: When a request to a peer has been outstanding for a
 * long time (fastSyncParams.jobTimeout), we must time out and stop using that
 * peer, at least temporarily. Otherwise, it will hold up the syncing process
 * occasionally when that peer has been (randomly) selected.
 *
 * We only use random known peers (from the peerCommConfiguration) to sync from.
 *
 * If there are no live peers, it will wait [params.exitDelay] until it leaves fastsync and starts
 * trying to build blocks on its own. This is not a problem in a real world scenario, since you can wait a minute
 * or so upon first start. But in tests, this can be really annoying. So tests that only runs a single node
 * should set [params.exitDelay] to 0.
 */
class FastSynchronizer(
        workerContext: WorkerContext,
        private val blockDatabase: BlockDatabase,
        val params: SyncParameters,
        val peerStatuses: FastSyncPeerStatuses,
        val isProcessRunning: () -> Boolean,
        val clock: Clock = Clock.systemUTC(),
) : AbstractSynchronizer(workerContext) {

    val jobs = TreeMap<Long, Job>()
    private var lastJob: Job? = null
    private val messageDurationTracker = workerContext.messageDurationTracker

    // This is the communication mechanism from the async commitBlock callback to the main loop
    val finishedJobs = LinkedBlockingQueue<Job>()

    companion object : KLogging()

    inner class Job(val height: Long, var peerId: NodeRid) {
        var header: BlockHeader? = null
        var witness: BlockWitness? = null
        var block: BlockDataWithWitness? = null
        var blockCommitting = false
        var addBlockException: Throwable? = null
        val startTime = currentTimeMillis()
        var hasRestartFailed = false
        override fun toString(): String {
            return "h${height}-${NameHelper.peerName(peerId)}"
        }
    }

    fun syncUntil(exitCondition: () -> Boolean) {
        var polledFinishedJob: Job? = null
        try {
            blockHeight.set(blockQueries.getLastBlockHeight().get())
            logger.debug { syncDebug("Start", blockHeight.get()) }
            while (isProcessRunning() && !exitCondition()) {
                refillJobs()
                processMessages()
                processDoneJobs(polledFinishedJob)
                processStaleJobs()
                // Would be nicer to be able to just peek here, but there is no API for that
                polledFinishedJob = finishedJobs.poll(params.loopInterval, TimeUnit.MILLISECONDS)
            }
        } catch (e: BadDataException) {
            logger.error(e) { "Fatal error, shutting down blockchain for safety reasons. Needs manual investigation." }
            throw e
        } catch (e: Exception) {
            logger.debug(e) { "syncUntil() -- ${"Exception"}" }
        } finally {
            logger.debug { syncDebug("Await commits", blockHeight.get()) }
            processDoneJobs(polledFinishedJob)
            awaitCommits()
            jobs.clear()
            finishedJobs.clear()
            peerStatuses.clear()
            logger.debug { syncDebug("Exit fastsync", blockHeight.get()) }
        }
    }

    /**
     * Terminology:
     * current = our current view of the system
     * final = the actual effective configuration of the blockchain (that we may or may not have yet)
     * drained(h) = peer that we have signalled that it hasn't any blocks *after* height h, ie h is its tip.
     * syncable(h) = responsive peer that isn't (yet) drained(h)
     *
     * This is called by a validator to make reasonably sure it's up-to-date with peers before
     * starting to build blocks.
     *
     * If no peer is responsive for X seconds, we'll assume we're the sole live node and return.
     *
     * Note that if we have contact with all current signers, it doesn't mean that we can trust that group,
     * because we don't know if this is the final configuration. Even if they are current signers, any/all
     * of those nodes could have turned rouge and got excluded from future signer lists. We'll have to
     * hope they'll provide us with correct blocks.
     *
     * When we have synced up to the final configuration, we *can* rely on the 2f+1 rule,
     * but we won't be aware of that when it happens. Any current signer list can be adversarial.
     *
     * All nodes are thus to be regarded as potentially adversarial/unreliable replicas.
     *
     * We consider ourselves up-to-date when
     * (a) at least [exitDelay] ms has passed since start and
     * (b) we have no syncable peers at our own height.
     *
     * The time requirement (a) is to allow for connections to be established to as many peers as
     * possible (within reasonable limit) before considering (b). Otherwise (b) might be trivially true
     * if we only had time to connect to a single or very few nodes.
     */
    fun syncUntilResponsiveNodesDrained() {
        val timeout = currentTimeMillis() + params.exitDelay
        logger.debug { "syncUntilResponsiveNodesDrained() begin with exitDelay: ${params.exitDelay}" }
        syncUntil {
            areResponsiveNodesDrained(timeout)
        }
    }

    internal fun areResponsiveNodesDrained(timeout: Long): Boolean {
        val currentBlockHeight = blockHeight.get()

        val syncableCount = peerStatuses.getSyncableAndConnected(currentBlockHeight + 1).intersect(configuredPeers).size

        // Keep syncing until this becomes true, i.e. to exit we must have:
        val done = timeout < currentTimeMillis()      // 1. must have timeout
                && syncableCount == 0                        // 2. must have no syncable nodes
                && currentBlockHeight >= params.mustSyncUntilHeight // 3. must BC height above the minimum specified height

        if (done) {
            logger.debug { "We are done syncing, height: $currentBlockHeight, must sync until: ${params.mustSyncUntilHeight}." }
        }
        return done
    }

    private fun awaitCommits() {
        // Check also hasRestartFailed to avoid getting stuck in awaitCommits(). If we don't check it
        // AND
        //
        // * j.peerId was blacklisted in previous invocation of processDoneJob AND
        // * restartJob(j) doesn't find a peer to send to, and thus doesn't remove it.
        //
        // then we will count this job as currently committing and wait for more
        // committing blocks as are actually committing.
        val committingJobs = jobs.count { it.value.blockCommitting && !it.value.hasRestartFailed }
        for (i in (0 until committingJobs)) {
            val job = finishedJobs.take()
            processDoneJob(job, false)
        }
    }

    private fun processDoneJobs(polledJob: Job?) {
        var job = polledJob ?: finishedJobs.poll()
        while (job != null) {
            processDoneJob(job)
            job = finishedJobs.poll()
        }
    }

    /**
     * Depending on the result of the job we take different actions.
     * Non-private for testing purposes
     */
    internal fun processDoneJob(job: Job, allowJobStart: Boolean = true) {
        val exception = job.addBlockException
        if (exception == null) {
            logger.debug { "processDoneJob() -- ${"Job $job done"}" }
            // Add new job and remove old job
            if (allowJobStart) {
                startNextJob()
            }
            blockHeight.incrementAndGet()
            removeJob(job)
            // Keep track of last block's job, in case of a BadDataType.PREV_BLOCK_MISMATCH on next job
            // Discard bulky data we don't need
            job.block = null
            job.witness = null
            lastJob = job
        } else {
            if (exception is PmEngineIsAlreadyClosed) {
                doneTrace("Add block failed for job $job because Db Engine is already closed.")
                removeJob(job)
                return
            }

            if (exception is BDBAbortException) {
                // the problem happened because of a previous block, clean up this
                // job and resubmit
                job.blockCommitting = false
                job.addBlockException = null
                commitJobsAsNecessary(null) // TODO: bTrace?
                return
            }

            if (exception is PrevBlockMismatchException) {
                // If we can't connect block, then either
                // previous block is bad or this block is bad. Unfortunately,
                // we can't know which. Ideally, we'd like to take different actions:
                // If this block is bad -> blacklist j.peer
                // If previous block is bad -> Panic shutdown blockchain
                //
                // We take the cautious approach and always shutdown the
                // blockchain. We also log this block's job and last block's job
                logger.info {
                    "processDoneJob() - ${
                        "Previous block mismatch. " +
                                "Previous block ${lastJob?.header?.blockRID?.toHex()} received from ${lastJob?.peerId}, " +
                                "This block ${job.header?.blockRID?.toHex()} received from ${job.peerId}."
                    }"
                }
                throw exception
            }

            // If the job failed because the block is already in the database
            // then it means that fastsync started before all addBlock jobs
            // from normal sync were done. If this has happened, we
            // will increase the blockHeight and consider this job done (but
            // not by us).
            val lastHeight = blockQueries.getLastBlockHeight().get()
            if (lastHeight >= job.height) {
                doneTrace("Add block failed for job $job because block already in db.")
                blockHeight.incrementAndGet() // as if this block was successful.
                removeJob(job)
                return
            }

            val errMsg = "Invalid block $job. Blacklisting peer ${job.peerId}: ${exception.message}"
            logger.error { "processDoneJob() $errMsg" }
            // Peer sent us an invalid block. Blacklist the peer and restart job
            peerStatuses.maybeBlacklist(job.peerId, errMsg)
            if (allowJobStart) {
                restartJob(job)
            } else {
                removeJob(job)
            }
        }
    }

    fun processStaleJobs() {
        val now = currentTimeMillis()
        val toRestart = mutableListOf<Job>()
        // Keep track of peers that we mark legacy. Otherwise, if same peer appears in
        // multiple timed out jobs, we will
        // 1) Mark it as maybeLegacy on first appearance
        // 2) Mark it as unresponsive and not maybeLegacy on second appearance
        // The result is that we won't send legacy request to that peer, since it's marked
        // unresponsive.
        val legacyPeers = mutableSetOf<NodeRid>()
        for (job in jobs.values) {
            if (job.hasRestartFailed) {
                if (job.startTime + params.jobTimeout < now) {
                    peerStatuses.unresponsive(job.peerId, "Sync: Marking peer for restarted job $job unresponsive")
                }
                // These are jobs that couldn't be restarted because there
                // were no peers available at the time. Try again every
                // time, because there is virtually no cost in doing so.
                // It's just a check against some local datastructures.
                toRestart.add(job)
            } else if (job.block == null && job.startTime + params.jobTimeout < now) {
                // We have waited for response from job.peerId for a long time.
                // This might be because it's a legacy node and thus doesn't respond to
                // GetBlockHeaderAndBlock messages or because it's just unresponsive
                if (peerStatuses.isConfirmedModern(job.peerId)) {
                    peerStatuses.unresponsive(job.peerId, "Sync: Marking modern peer for job $job unresponsive")
                } else if (!legacyPeers.contains(job.peerId) && peerStatuses.isMaybeLegacy(job.peerId)) {
                    // Peer is marked as legacy, but still appears unresponsive.
                    // This probably wasn't a legacy node, but simply an unresponsive one.
                    // It *could* still be a legacy node, but we give it another chance to
                    // prove itself a modern node after the timeout
                    peerStatuses.setMaybeLegacy(job.peerId, false)
                    peerStatuses.unresponsive(job.peerId, "Sync: Marking potentially legacy peer for job $job unresponsive")
                } else {
                    // Let's assume this is a legacy node and use GetCompleteBlock for the next try.
                    // If that try is unresponsive too, we'll mark it as unresponsive
                    peerStatuses.setMaybeLegacy(job.peerId, true)
                    legacyPeers.add(job.peerId)
                }
                toRestart.add(job)
            }
        }
        // Avoid ConcurrentModificationException by restartingJob after for loop
        toRestart.forEach {
            restartJob(it)
        }
    }

    /**
     * This makes sure that we have <parallelism> number of jobs running
     * concurrently.
     */
    private fun refillJobs() {
        (jobs.size until params.parallelism).forEach { _ ->
            if (!startNextJob()) {
                // There are no peers to talk to
                return
            }
        }
    }

    /**
     * Non-private for testing purposes
     */
    internal fun restartJob(job: Job) {
        if (!startJob(job.height)) {
            // We had no peers available for this height, we'll have to try
            // again later. see processStaleJobs()
            job.hasRestartFailed = true
        }
    }

    private fun startNextJob(): Boolean {
        return startJob(blockHeight.get() + jobs.size + 1)
    }

    private fun sendLegacyRequest(height: Long): NodeRid? {
        val peers = peerStatuses.getLegacyPeers(height).intersect(configuredPeers)
        if (peers.isEmpty()) return null
        return sendMessageAndUpdateConnectionStatuses(GetBlockAtHeight(height), peers)
    }

    private fun sendRequest(height: Long): NodeRid? {
        val now = currentTimeMillis()
        val peers = configuredPeers.minus(peerStatuses.excludedNonSyncable(height, now)).ifEmpty {
            if (params.mustSyncUntilHeight > -1) {
                peerStatuses.reviveAllBlacklisted()
                configuredPeers.minus(peerStatuses.excludedNonSyncable(height, now))
            } else {
                emptySet()
            }
        }
        if (peers.isEmpty()) return null
        return sendMessageAndUpdateConnectionStatuses(GetBlockHeaderAndBlock(height), peers)
    }

    private fun sendMessageAndUpdateConnectionStatuses(message: EbftMessage, peers: Set<NodeRid>): NodeRid? {
        val (selectedPeer, connectedPeers) = communicationManager.sendToRandomPeer(message, peers)
        selectedPeer?.also { messageDurationTracker.send(it, message) }
        peerStatuses.markConnected(connectedPeers)
        peerStatuses.markDisconnected(peers - connectedPeers)
        return selectedPeer
    }

    /**
     * Non-private for testing purposes
     */
    internal fun startJob(height: Long): Boolean {
        var peer = sendRequest(height)
        if (peer == null) {
            // There were no modern nodes to sync from. Let's try with a legacy node instead
            peer = sendLegacyRequest(height)
            if (peer == null) {
                // there were no peers at all to sync from. give up.
                return false
            }
        }
        val job = Job(height, peer)
        addJob(job)
        logger.trace { "startJob() -- ${"Started job $job"}" }
        return true
    }

    private fun removeJob(job: Job) {
        jobs.remove(job.height)
    }

    private fun addJob(job: Job) {
        peerStatuses.addPeer(job.peerId) // Only adds if peer doesn't exist
        val replaced = jobs.put(job.height, job)
        if (replaced == null) {
            addTrace("Added new job $job")
        } else {
            addTrace("Replaced job $replaced with $job")
        }
    }

    private fun debugJobString(job: Job?, requestedHeight: Long, peerId: NodeRid): String {
        var out = "Received: height: $requestedHeight, peerId: $peerId"
        if (job != null) {
            out += ", Requested (job): $job"
        }
        return out
    }

    internal fun handleBlockHeader(peerId: NodeRid, message: BlockHeaderMessage): Boolean {
        messageDurationTracker.receive(peerId, message)
        return handleBlockHeader(peerId, message.header, message.witness, message.requestedHeight)
    }

    /**
     * Non-private for testing purposes
     */
    internal fun handleBlockHeader(peerId: NodeRid, header: ByteArray, witness: ByteArray, requestedHeight: Long): Boolean {
        val job = jobs[requestedHeight]

        // Didn't expect header for this height or from this peer,
        // We might want to blacklist peers that sends unsolicited headers;
        // They might be adversarial and try to get us to restart jobs
        // as much as they can. But hard to distinguish this from
        // legitimate glitches, for example, that the peer has timed
        // out in an earlier job but just now comes back with the response.
        if (job == null) {
            val dbg = debugJobString(job, requestedHeight, peerId)
            peerStatuses.maybeBlacklist(peerId, "Sync: Why do we receive a header for a block height not in our job list? $dbg")
            return false
        }
        if (peerId != job.peerId) {
            val dbg = debugJobString(job, requestedHeight, peerId)
            peerStatuses.maybeBlacklist(peerId, "Sync: Why do we receive a header from a peer when we didn't ask this peer? $dbg")
            return false
        }
        if (job.header != null) {
            val dbg = debugJobString(job, requestedHeight, peerId)
            peerStatuses.maybeBlacklist(
                    peerId,
                    "Sync: Why do we receive a header when we already have the header? $dbg"
            )
            return false
        }

        if (header.isEmpty()) {
            if (witness.isEmpty()) {
                // The peer says it has no blocks, try another peer
                headerDebug("Peer for job $job drained (sent empty header)")
                val now = currentTimeMillis()
                peerStatuses.drained(peerId, -1, now)
                restartJob(job)
            } else {
                val dbg = debugJobString(job, requestedHeight, peerId)
                peerStatuses.maybeBlacklist(peerId, "Sync: Why did we get a witness without a header? $dbg")
            }
            return false
        }

        val blockHeader = blockchainConfiguration.decodeBlockHeader(header)
        val receivedHeaderHeight = getHeight(blockHeader)

        if (receivedHeaderHeight < job.height) {
            headerDebug("Header height=$receivedHeaderHeight, we asked for ${job.height}. Peer for $job must be drained")
            // The peer didn't have the block we wanted.
            // Remember its height and try another peer.
            val now = currentTimeMillis()
            peerStatuses.drained(peerId, receivedHeaderHeight, now)
            restartJob(job)
            return false
        } else if (receivedHeaderHeight > job.height) {
            peerStatuses.maybeBlacklist(peerId, "Sync: Received a header at height $receivedHeaderHeight but we asked for a lower height ${job.height}")
            return false
        }

        val blockWitness = blockchainConfiguration.decodeWitness(witness)
        // If config is mismatching, we can't validate witness properly.
        // We could potentially verify against the signer list in the new config, but we can't be sure that we have it yet.
        // Anyway, in the worst case scenario, we will simply attempt to load the block and fail.
        if (blockHeader.getConfigHash() == null || blockHeader.getConfigHash().contentEquals(blockchainConfiguration.configHash)) {
            val validator = blockchainConfiguration.getBlockHeaderValidator()
            val witnessBuilder = validator.createWitnessBuilderWithoutOwnSignature(blockHeader)
            try {
                validator.validateWitness(blockWitness, witnessBuilder)
            } catch (e: Exception) {
                val dbg = debugJobString(job, requestedHeight, peerId)
                peerStatuses.maybeBlacklist(peerId, "Sync: Invalid header received (${e.message}). $dbg")
                return false
            }
        }

        job.header = blockHeader
        job.witness = blockWitness
        logger.trace { "handleBlockHeader() -- ${"Header for $job received"}" }
        peerStatuses.headerReceived(peerId, receivedHeaderHeight)
        return true
    }

    internal fun handleUnfinishedBlock(peerId: NodeRid, message: UnfinishedBlock) {
        val header = message.header
        val decodedHeader = blockchainConfiguration.decodeBlockHeader(header)
        if (decodedHeader !is BaseBlockHeader) {
            throw BadMessageException("Expected BaseBlockHeader")
        }
        messageDurationTracker.receive(peerId, message, decodedHeader)
        handleUnfinishedBlock(peerId, header, decodedHeader, message.transactions)
    }

    /**
     * Non-private for testing purposes
     */
    internal fun handleUnfinishedBlock(peerId: NodeRid, header: ByteArray, decodedHeader: BaseBlockHeader, txs: List<ByteArray>) {
        val height = getHeight(decodedHeader)
        val job = jobs[height]
        if (job == null) {
            peerStatuses.maybeBlacklist(peerId, "Sync: Why did we get an unfinished block of height: $height from peer: $peerId ? We didn't ask for it")
            return
        }
        unfinishedTrace("Received for $job")
        var bTrace: BlockTrace? = null
        if (logger.isDebugEnabled) {
            logger.trace { "handleUnfinishedBlock() - Creating block trace with height: $height " }

            bTrace = BlockTrace.build(decodedHeader.blockRID, height)
        }
        val expectedHeader = job.header

        // Validate everything!
        if (job.block != null) {
            peerStatuses.maybeBlacklist(peerId, "Sync: We got this block height = $height already, why send it again?. $job")
            return
        }

        if (peerId != job.peerId) {
            peerStatuses.maybeBlacklist(peerId, "Sync: We didn't expect $peerId to send us an unfinished block (height = $height). We wanted ${job.peerId} to do it. $job")
            return
        }

        if (expectedHeader == null) {
            peerStatuses.maybeBlacklist(peerId, "Sync: We don't have a header yet, why does $peerId send us an unfinished block (height = $height )? $job")
            return
        }

        if (!(expectedHeader.rawData contentEquals header)) {
            peerStatuses.maybeBlacklist(peerId, "Sync: Peer: ${job.peerId} is sending us an unfinished block (height = $height) with a header that doesn't match the header we expected. $job")
            return
        }

        // The witness has already been verified in handleBlockHeader().
        job.block = BlockDataWithWitness(decodedHeader, txs, job.witness!!)

        commitJobsAsNecessary(bTrace)
    }

    /**
     * Non-private for testing purposes
     */
    internal fun commitJobsAsNecessary(bTrace: BlockTrace?) {
        // We have to make sure blocks are committed in the correct order. If we are missing a block we have to wait for it.
        for ((index, job) in jobs.values.withIndex()) {
            if (!isProcessRunning()) return

            // The values are iterated in key-ascending order (see TreeMap)
            if (job.block == null) {
                // The next block to be committed hasn't arrived yet
                unfinishedTrace("Done. Next job, $job, to commit hasn't arrived yet.")
                return
            }
            if (!job.blockCommitting) {
                unfinishedTrace("Committing block for $job")
                commitBlock(job, bTrace, index == 0)
            }
        }
    }

    /**
     * This is used for syncing from old nodes that doesn't have this new FastSynchronizer algorithm
     */
    private fun handleCompleteBlock(peerId: NodeRid, message: CompleteBlock) {
        val blockData = message.data
        val height = message.height
        val witness = message.witness

        messageDurationTracker.receive(peerId, message)

        // We expect height to be the requested height. If the peer didn't have the block we wouldn't
        // get any block at all.
        if (!peerStatuses.isMaybeLegacy(peerId)) {
            // We only expect CompleteBlock from legacy nodes.
            return
        }
        val header = blockData.header
        val saveBlock = handleBlockHeader(peerId, header, witness, height)
        if (!saveBlock) {
            return
        }

        val decodedHeader = blockchainConfiguration.decodeBlockHeader(header)
        if (decodedHeader !is BaseBlockHeader) {
            throw BadMessageException("Expected BaseBlockHeader")
        }
        handleUnfinishedBlock(peerId, header, decodedHeader, blockData.transactions)
    }

    /**
     * NOTE:
     * If one block fails to commit, don't worry about the blocks coming after. This is handled in the BBD.addBlock().
     *
     * Non-private for testing purposes
     */
    internal fun commitBlock(job: Job, bTrace: BlockTrace?, hasNoPrecedingJob: Boolean) {
        // Once we set this flag we must add the job to finishedJobs otherwise we risk a deadlock
        job.blockCommitting = true

        // This job has no preceding job that it has to check status for
        if (hasNoPrecedingJob) {
            addBlockCompletionFuture = null // We want to do cleanup, since the old future is used in "addBlock()" below.
        }

        // We are free to commit this Job, go on and add it to DB
        // (this is usually slow and is therefore handled via a future).
        val block = job.block ?: throw ProgrammerMistake("Attempting to commit an unfinished job")
        addBlockCompletionFuture = blockDatabase
                .addBlock(block, addBlockCompletionFuture, bTrace)
                .whenCompleteUnwrapped(loggingContext, always = { _, exception ->
                    if (exception != null) {
                        handleAddBlockException(exception, block, bTrace, peerStatuses, job.peerId)
                        job.addBlockException = exception
                    }
                    finishedJobs.add(job)
                })
    }

    /**
     * Non-private for testing purposes
     */
    internal fun processMessages() {
        messageDurationTracker.cleanup()
        // TODO: Handle version
        for ((peerId, _, message) in communicationManager.getPackets()) {
            if (peerStatuses.isBlacklisted(peerId)) {
                continue
            }
            if (message is GetBlockHeaderAndBlock || message is BlockHeaderMessage) {
                peerStatuses.confirmModern(peerId)
            }
            try {
                when (message) {
                    is GetBlockAtHeight -> sendBlockAtHeight(peerId, message.height)
                    is GetBlockRange -> sendBlockRangeFromHeight(peerId, message.startAtHeight, blockHeight.get()) // A replica might ask us
                    is GetBlockHeaderAndBlock -> sendBlockHeaderAndBlock(peerId, message.height, blockHeight.get())
                    is GetBlockSignature -> sendBlockSignature(peerId, message.blockRID)
                    is BlockHeaderMessage -> handleBlockHeader(peerId, message)
                    is UnfinishedBlock -> handleUnfinishedBlock(peerId, message)
                    is CompleteBlock -> handleCompleteBlock(peerId, message)
                    is Status -> peerStatuses.statusReceived(peerId, message.height - 1)
                    is AppliedConfig -> if (checkIfWeNeedToApplyPendingConfig(peerId, message)) return

                    is Transaction -> logger.info("Got unexpected transaction from peer $peerId, ignoring")
                    else -> logger.warn { "Unhandled message type: ${message.topic} from peer $peerId" } // WARN b/c this might be buggy?
                }
            } catch (e: Exception) {
                logger.info("Couldn't handle message $message from peer $peerId. Ignoring and continuing", e)
            }
        }
    }

    // -------------
    // Only logging below
    // -------------

    private fun syncDebug(message: String, height: Long) = "syncUntil() -- $message, at height: $height"

    // processDoneJob()
    private fun doneTrace(message: String, e: Exception? = null) {
        logger.trace(e) { "processDoneJob() --- $message" }
    }

    // addJob()
    private fun addTrace(message: String, e: Exception? = null) {
        logger.trace(e) { "addJob() -- $message" }
    }

    private fun headerDebug(message: String, e: Exception? = null) {
        logger.debug(e) { "handleBlockHeader() -- $message" }
    }

    private fun unfinishedTrace(message: String, e: Exception? = null) {
        logger.trace(e) { "handleUnfinishedBlock() -- $message" }
    }

    private fun currentTimeMillis() = clock.millis()
}
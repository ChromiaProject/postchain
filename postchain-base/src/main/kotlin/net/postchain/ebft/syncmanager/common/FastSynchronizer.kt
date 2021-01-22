// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.base.BaseBlockHeader
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.core.*
import net.postchain.core.BlockHeader
import net.postchain.ebft.BlockDatabase
import net.postchain.ebft.message.*
import net.postchain.ebft.message.BlockData
import net.postchain.ebft.worker.WorkerContext
import net.postchain.network.x.XPeerID
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import net.postchain.ebft.message.BlockHeader as BlockHeaderMessage


/**
 * Tuning parameters for FastSychronizer. All times are in ms.
 */
data class FastSyncParameters(var resurrectDrainedTime: Long = 10000,
                              var resurrectUnresponsiveTime: Long = 20000,
                              var parallelism: Int = 10,
                              /**
                               * Don't exit fastsync for at least this amount of time (ms).
                               * This gives the connection manager some time to accumulate
                               * connections so that the random peer selection has more
                               * peers to chose from, to avoid exiting fastsync
                               * prematurely because one peer is connected quicker, giving
                               * us the impression that there is only one reachable node.
                               *
                               * Example: I'm A(height=-1), and B(-1),C(-1),D(0) are peers. When entering FastSync
                               * we're only connected to B.
                               *
                               * * Send a GetBlockHeaderAndBlock(0) to B
                               * * B replies with block 0 and we mark it as drained(0).
                               * * We conclude that we have draied all peers at 0 and exit fastsync
                               * * C and D connections are established.
                               *
                               * We have exited fastsync before we had a chance to sync from C and D
                               *
                               * Sane values:
                               * Replicas: not used
                               * Signers: 60000ms
                               * Tests with single node: 0
                               * Tests with multiple nodes: 1000
                               */
                              var exitDelay: Long = 60000,
                              var pollPeersInterval: Long = 10000,
                              var jobTimeout: Long = 10000,
                              var loopInteval: Long = 100)

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
 * long time (fastSyncParams.jobTimeout), we must timeout and stop using that
 * peer, at least temporarily. Otherwise it will hold up the syncing process
 * every now and then when that peer has been (randomly) selected.
 *
 * When we start this process we have no knowledge of which peers we are connected
 * to. It's important to quickly get to know as many peers as possible, because the more
 * peers we have the more reliable we can determine if we're up-to-date or not.
 *
 * We rely on two discovery mechanisms:
 *
 * 1. Request blocks from random peers via communicationManager.sendToRandomPeer(), which returns
 * the peerId of the peer that the request was sent to.
 *
 * 2. Listen for messages from our peers. For example Status messages from peers that
 * are in normal sync mode are typically sent every ~1s, and block requests from peers in fastsync
 * mode are sent as often as they can, spread randomly across its peers, so the more peers it has,
 * the less often we receive requests from it. On the other hand if there are lots of peers we don't
 * need all peers to sync.
 *
 * These two methods should give us a pretty complete picture of the network within a few seconds.
 *
 * If there are no live peers, it will wait [params.exitDelay] until it leaves fastsync and starts
 * trying to build blocks on its own. This is not a problem in a real world scenario, since you can wait a minute
 * or so upon first start. But in tests, this can be really annoying. So tests that only runs a single node
 * should set [params.exitDelay] to 0.
 */
class FastSynchronizer(private val workerContext: WorkerContext,
                       val blockDatabase: BlockDatabase,
                       val params: FastSyncParameters
): Messaging(workerContext.engine.getBlockQueries(), workerContext.communicationManager) {
    private val blockchainConfiguration = workerContext.engine.getConfiguration()
    private val jobs = TreeMap<Long, Job>()
    private val peerStatuses = PeerStatuses(params)

    // This is the communication mechanism from the async commitBlock callback to main loop
    private val finishedJobs = LinkedBlockingQueue<Job>()

    companion object: KLogging()

    var blockHeight: Long = workerContext.engine.getBlockQueries().getBestHeight().get()
        private set

    inner class Job(val height: Long, var peerId: XPeerID) {
        var header: BlockHeader? = null
        var witness: BlockWitness? = null
        var block: BlockDataWithWitness? = null
        var blockCommitting = false
        var success = false
        val startTime = System.currentTimeMillis()
        var hasRestartFailed = false
        override fun toString(): String {
            return "${this@FastSynchronizer.workerContext.processName}-h${height}-${peerId.shortString()}"
        }
    }

    private val shutdown = AtomicBoolean(false)

    fun debug(message: String, e: Exception? = null) {
        logger.debug("${workerContext.processName}: $message", e)
    }
    
    fun syncUntil(exitCondition: () -> Boolean) {
        try {
            debug("Start fastsync")
            blockHeight = blockQueries.getBestHeight().get()
            debug("Best height $blockHeight")
            while (!shutdown.get() && !exitCondition()) {
                refillJobs()
                processMessages()
                processDoneJobs()
                processStaleJobs()
                sleep(params.loopInteval)
            }
        } catch (e: Exception) {
            debug("Exception in syncWhile()", e)
        } finally {
            debug("Await commits")
            awaitCommits()
            jobs.clear()
            finishedJobs.clear()
            peerStatuses.clear()
            debug("Exit fastsync")
        }
    }

    fun syncUntilShutdown() {
        syncUntil {false}
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
        val timeout = System.currentTimeMillis() + params.exitDelay
        debug("exitDelay: ${params.exitDelay}")
        syncUntil {
            timeout < System.currentTimeMillis() && peerStatuses.countSyncable(blockHeight+1) == 0
        }
    }

    fun shutdown() {
        shutdown.set(true)
    }

    private fun awaitCommits() {
        val committingJobs = jobs.count { it.value.blockCommitting }
        for (i in (0 until committingJobs)) {
            val j = finishedJobs.take()
            processDoneJob(j, true)
        }
    }

    fun processDoneJobs() {
        var j = finishedJobs.poll()
        while (j != null) {
            debug("Processing done job $j")
            processDoneJob(j)
            j = finishedJobs.poll()
        }
    }

    private fun processDoneJob(j: Job, final: Boolean = false) {
        if (j.success) {
            // Add new job and remove old job
            if (!final) {
                startNextJob()
            }
            blockHeight++
            removeJob(j)
        } else {
            // If the job failed because the block is already in the database
            // then it means that fastsync started before all addBlock jobs
            // from normal sync were done. If this has happened, we
            // will increase the blockheight and consider this job done (but
            // not by us).
            val bestHeight = blockQueries.getBestHeight().get()
            if (bestHeight >= j.height) {
                debug("Add block failed for job ${j} because block already in db.")
                blockHeight++ // as if this block was successful.
                removeJob(j)
                return
            }

            debug("Invalid block ${j}. Blacklisting.")
            // Peer sent us an invalid block. Blacklist the peer and restart job
            peerStatuses.blacklist(j.peerId)
            if (!final) {
                restartJob(j)
            }
        }
    }

    fun processStaleJobs() {
        val now = System.currentTimeMillis()
        val toRestart = mutableListOf<Job>()
        for (j in jobs.values) {
            if (j.hasRestartFailed) {
                // These are jobs that couldn't be restarted because there
                // were no peers available at the time. Try again every
                // time, because there is virtually no cost in doing so.
                // It's just a check against a local datastructure.
                toRestart.add(j)
            } else if (j.block == null && j.startTime + params.jobTimeout < now) {
                // We have waited for response from j.peerId fo a long time.
                // This might be because it's a legacy node and thus doesn't respond to
                // GetBlockHeaderAndBlock messages or because it's just unresponsive
                if (peerStatuses.isConfirmedModern(j.peerId)) {
                    debug("Marking job ${j} unresponsive")
                    peerStatuses.unresponsive(j.peerId)
                    toRestart.add(j)
                } else if (peerStatuses.isMaybeLegacy(j.peerId)) {
                    // Peer is marked as legacy, but still appears unresponsive.
                    // This probably wasn't a legacy node, but simply an unresponsive one.
                    // It *could* still be a legacy node, but we give it another chance to
                    // prove itself a modern node after the timeout
                    peerStatuses.setMaybeLegacy(j.peerId, false)
                    debug("Marking job ${j} unresponsive")
                    peerStatuses.unresponsive(j.peerId)
                    toRestart.add(j)
                } else {
                    // Let's assume this is a legacy node and use GetCompleteBlock for the
                    // next try.
                    // If that try is unresponsive too, we'll mark it as unresponsive
                    peerStatuses.setMaybeLegacy(j.peerId, true)
                    toRestart.add(j)
                }
            }
        }
        // Avoid ConcurrentModificationException by restartingJob after for loop
        toRestart.forEach {
            restartJob(it)
        }
    }

    /**
     * This makes sure that we have <parallelism> jobs running
     * concurrently.
     */
    private fun refillJobs() {
        (jobs.size until params.parallelism).forEach {
            startNextJob()
        }
    }

    private fun restartJob(job: Job) {
        if (!startJob(job.height)) {
            // We had no peers available for this height, we'll have to try
            // again later. see processStaleJobs()
            job.hasRestartFailed = true
        }
    }

    private fun startNextJob(): Boolean {
        return startJob(blockHeight + jobs.size + 1)
    }

    private fun startJob(height: Long): Boolean {
        val excludedPeers = peerStatuses.exclNonSyncable(height)
        var peer = communicationManager.sendToRandomPeer(GetBlockHeaderAndBlock(height), excludedPeers)
        if (peer == null) {
            // There were no modern nodes to sync from. Let's try with a legacy node instead
            peer = peerStatuses.getRandomLegacyPeer(height)
            if (peer == null) {
                // there were no peers at all to sync from. give up.
                return false
            }
            // Make a legacy request for block
            communicationManager.sendPacket(GetBlockAtHeight(height), peer)
        }
        val j = Job(height, peer)
        addJob(j)
        debug("Started job $j")
        return true
    }

    private fun removeJob(job: Job) {
        jobs.remove(job.height)
    }

    private fun addJob(job: Job) {
        peerStatuses.addPeer(job.peerId)
        val replaced = jobs.put(job.height, job)
        if (replaced == null) {
            debug("Added new job $job")
        } else {
            debug("Replaced job $replaced with $job")
        }
    }

    private fun handleBlockHeader(peerId: XPeerID, header: ByteArray, witness: ByteArray, requestedHeight: Long): Boolean {
        val j = jobs[requestedHeight]
        if (j == null || j.header != null || peerId != j.peerId) {
            // Didn't expect header for this height or from this peer
            // We might want to blacklist peers that sends unsolicited headers;
            // They might be adversarial and try to get us to restart jobs
            // as much as they can. But hard to distinguish this from
            // legitimate glitches, for example that the peer has timed
            // out in earlier job but just now comes back with the response.
            return false
        }

        if (header.size == 0 && witness.size == 0) {
            // The peer says it has no blocks, try another peer
            debug("Peer for job $j drained at height -1")
            peerStatuses.drained(peerId, -1)
            restartJob(j)
            return false
        }

        val h = blockchainConfiguration.decodeBlockHeader(header)
        val peerBestHeight = getHeight(h)

        if (peerBestHeight != j.height) {
            debug("Peer for $j drained at height $peerBestHeight")
            // The peer didn't have the block we wanted
            // Remember its height and try another peer
            peerStatuses.drained(peerId, peerBestHeight)
            restartJob(j)
            return false
        }

        val w = blockchainConfiguration.decodeWitness(witness)
        if ((blockchainConfiguration as BaseBlockchainConfiguration).verifyBlockHeader(h, w)) {
            j.header = h
            j.witness = w
            debug("Header for ${j} received")
            peerStatuses.headerReceived(peerId, peerBestHeight)
            return true
        } else {
            // There may be two resaons for verification failures.
            // 1. The peer is a scumbag, sending us invalid headers
            // 2. The header is from a configuration that we haven't activated yet.
            // In both cases we can blacklist the peer:
            //
            // 1. blacklisting scumbags is good
            // 2. The blockchain will restart before requestedHeight is added, so the
            // sync process will restart fresh with new configuration. Worst case is if
            // we download parallelism blocks before restarting.
            debug("Invalid header received for $j. Blacklisting.")
            peerStatuses.blacklist(peerId)
            return false
        }
    }

    private fun getHeight(header: BlockHeader): Long {
        // A bit ugly hack. Figure out something better. We shouldn't rely on specific
        // implementation here.
        // Our current implementation, BaseBlockHeader, includes the height, which
        // means that we can trust the height in the header because it's been
        // signed by a quorum of signers.
        // If another BlockHeader implementation is used, that doesn't include
        // the height, we'd have to rely on something else, for example
        // sending the height explicitly, but then we trust only that single
        // sender node to tell the truth.
        // For now we rely on the height being part of the header.
        if (header !is BaseBlockHeader) {
            throw ProgrammerMistake("Expected BaseBlockHeader")
        }
        return header.blockHeaderRec.getHeight()
    }

    private fun handleUnfinishedBlock(peerId: XPeerID, header: ByteArray, txs: List<ByteArray>) {
        val h = blockchainConfiguration.decodeBlockHeader(header)
        if (h !is BaseBlockHeader) {
            throw BadDataMistake(BadDataType.BAD_MESSAGE,"Expected BaseBlockHeader")
        }
        val height = getHeight(h)
        val j = jobs[height]?:return
        debug("handleUnfinishedBlock received for $j")
        val expectedHeader = j.header
        if (j.block != null || peerId != j.peerId ||
                expectedHeader == null ||
                !(expectedHeader.rawData contentEquals header)) {
            // Got a block when we didn't expect one. Ignore it.
            debug("handleUnfinishedBlock didn't expect $j")
            return
        }
        // The witness has already been verified in handleBlockHeader().
        j.block = BlockDataWithWitness(h, txs, j.witness!!)

        for (job in jobs.values) {
            // The values are iterated in key-ascending order (see TreeMap)
            if (job.block == null) {
                // The next block to be committed hasn't arrived yet
                debug("handleUnfinishedBlock done. Next job, ${job}, to commit hasn't arrived yet.")
                return
            }
            if (!job.blockCommitting) {
                debug("handleUnfinishedBlock committing block for ${job}")
                job.blockCommitting = true
                commitBlock(job)
            }
        }
    }

    /**
     * This is used for syncing from old nodes that doesn't have this new FastSynchronizer algorithm
     */
    private fun handleCompleteBlock(peerId: XPeerID, blockData: BlockData, height: Long, witness: ByteArray) {
        // We expect height to be the requested height. If the peer didn't have the block we wouldn't
        // get any block at all.
        if (!peerStatuses.isMaybeLegacy(peerId)) {
            // We only expect CompleteBlock from legacy nodes.
            return
        }

        val saveBlock = handleBlockHeader(peerId, blockData.header, witness, height)
        if (!saveBlock) {
            return
        }
        handleUnfinishedBlock(peerId, blockData.header, blockData.transactions)
    }

    private fun commitBlock(job: Job) {
        val p = blockDatabase.addBlock(job.block!!)
        p.success {_ ->
            job.success = true
            finishedJobs.add(job)
        }
        p.fail {
            // We got an invalid block from peer. Let's blacklist this
            // peer and try another peer
            debug("Exception committing block ${job}", it)
            finishedJobs.add(job)
        }
    }

    private fun processMessages() {
        for (packet in communicationManager.getPackets()) {
            val peerId = packet.first
            if (peerStatuses.isBlacklisted(peerId)) {
                continue
            }
            peerStatuses.addPeer(peerId)
            val message = packet.second
            if (message is GetBlockHeaderAndBlock || message is BlockHeaderMessage ) {
                peerStatuses.confirmModern(peerId)
            }
            try {
                when (message) {
                    is GetBlockAtHeight -> sendBlockAtHeight(peerId, message.height)
                    is GetBlockHeaderAndBlock -> sendBlockHeaderAndBlock(peerId, message.height, blockHeight)
                    is BlockHeaderMessage -> handleBlockHeader(peerId, message.header, message.witness, message.requestedHeight)
                    is UnfinishedBlock -> handleUnfinishedBlock(peerId, message.header, message.transactions)
                    is CompleteBlock -> handleCompleteBlock(peerId, message.data, message.height, message.witness)
                    is Status -> peerStatuses.statusReceived(peerId, message.height-1)
                    else -> debug("Unhandled type ${message} from peer $peerId")
                }
            } catch (e: Exception) {
                logger.info("Couldn't handle message $message from peer $peerId. Ignoring and continuing", e)
            }
        }
    }
}


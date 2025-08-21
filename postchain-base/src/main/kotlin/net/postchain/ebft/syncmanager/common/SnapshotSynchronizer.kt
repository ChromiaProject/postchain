package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.extension.CONFIG_HASH_EXTRA_HEADER
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.core.BlockRid
import net.postchain.core.NodeRid
import net.postchain.ebft.BlockWriter
import net.postchain.ebft.message.BlockHeader
import net.postchain.ebft.message.EbftVersion
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockHeaderAndBlock
import net.postchain.ebft.message.GetBlockRange
import net.postchain.ebft.message.GetBlockSignature
import net.postchain.ebft.message.GetLatestSnapshotBlock
import net.postchain.ebft.syncmanager.configuration.RateLimitConfiguration
import net.postchain.ebft.worker.WorkerContext
import net.postchain.gtv.merkleHash

class SnapshotSynchronizer(
        workerContext: WorkerContext,
        private val blockDatabase: BlockWriter,
        val params: SyncParameters,
        val peerStatuses: PeerStatuses,
        val isProcessRunning: () -> Boolean,
        rateLimitConfiguration: RateLimitConfiguration,
) : AbstractSynchronizer(workerContext, rateLimitConfiguration) {

    companion object : KLogging()

    private var receivedLatestSnapshotHeight = mutableMapOf<NodeRid, BlockHeader>()

    // TODO: Fetch snapshot info and determine if it is worth it
    fun trySnapshotSync() {
        if (shouldDoSnapshotSync()) {
            // Start with blocks, we have a blockdb that simply saves the block without applying txs (after checking signature)
            // TODO: Probably good with some extra parallelism here?
            val fastSynchronizer = FastSynchronizer(
                    workerContext,
                    blockDatabase,
                    params,
                    PeerStatuses(params),
                    { isProcessRunning() },
                    rateLimitConfiguration
            )
            fastSynchronizer.syncUntil { !isProcessRunning() }
            params.syncToExactHeight = -1
        }
    }

    private fun shouldDoSnapshotSync(): Boolean {
        var lastRequestForSnapshotHeight = 0L
        var numberOfTries = 0
        while (isProcessRunning()) {
            if (System.currentTimeMillis() - lastRequestForSnapshotHeight >= params.jobTimeout) {
                if (numberOfTries > 3) break
                val peersLeft = configuredPeers - receivedLatestSnapshotHeight.keys
                if (peersLeft.isEmpty()) break

                peersLeft.forEach {
                    communicationManager.sendPacket(GetLatestSnapshotBlock(), it)
                    logger.info("Requested latest snapshot block from peer $it")
                }
                lastRequestForSnapshotHeight = System.currentTimeMillis()
                numberOfTries++
            }
            processMessages()
            Thread.sleep(params.loopInterval)
        }
        // We need to validate the witness of these headers
        val snapshotCandidates = receivedLatestSnapshotHeight.values.filter { it.header.isNotEmpty() }
                .sortedByDescending { BlockHeaderData.fromBinary(it.header).getHeight() }
        // TODO: Return false if we are below snapshot sync threshold?

        // We try all but unless peers are malicious or we are lacking config it should be fine
        for (candidate in snapshotCandidates) {
            val candidateHeader = BlockHeaderData.fromBinary(candidate.header)
            val candidateHeaderConfig = candidateHeader.getExtra()[CONFIG_HASH_EXTRA_HEADER]?.asByteArray()
            val candidateHeaderRid = BlockRid(candidateHeader.toGtv().merkleHash(blockchainConfiguration.merkleHashCalculator))

            val (validator, witnessBuilder) = if (blockchainConfiguration.configHash.contentEquals(candidateHeaderConfig)) {
                val validator = blockchainConfiguration.getBlockHeaderValidator() // We have the snapshot height config now!
                validator to validator.createWitnessBuilderWithoutOwnSignature(candidateHeaderRid)
            } else {
                // TODO: FETCH THE CONFIG FOR REAL FROM DB!!!
                val validator = blockchainConfiguration.getBlockHeaderValidator() // Let's cheat for now
                validator to validator.createWitnessBuilderWithoutOwnSignature(candidateHeaderRid)
            }

            try {
                validator.validateWitness(BaseBlockWitness.fromBytes(candidate.witness), witnessBuilder)
                logger.info("Received snapshot info from peers, highest valid height was: ${candidateHeader.getHeight()}")
                params.syncToExactHeight  = candidateHeader.getHeight()
                return true
            } catch (e: Exception) {
                logger.warn("Received invalid snapshot header from peer: ${e.message}")
            }
        }

        return false // TODO: What do we do here?
    }

    private fun processMessages() {
        resetServedRequests()
        for ((peerId, _, message) in communicationManager.getPackets()) {
            if (peerStatuses.isBlacklisted(peerId)) {
                continue
            }
            if (message is GetBlockHeaderAndBlock || message is BlockHeader) {
                peerStatuses.confirmModern(peerId)
            }
            try {
                when (message) {
                    // We will answer any get call
                    is GetBlockAtHeight -> sendBlockAtHeight(peerId, message.height)
                    is GetBlockHeaderAndBlock -> sendBlockHeaderAndBlock(peerId, message.height, blockHeight.get())
                    is GetBlockRange -> sendBlockRangeFromHeight(peerId, message.startAtHeight, blockHeight.get())
                    is GetBlockSignature -> sendBlockSignature(peerId, message.blockRID)

                    is BlockHeader -> {
                        receivedLatestSnapshotHeight[peerId] = message
                        if (message.header.isNotEmpty()) {
                            logger.info("GOT LATEST SNAPSHOT HEADER: ${BlockHeaderData.fromBinary(message.header)}")
                        } else {
                            logger.info("GOT LATEST SNAPSHOT HEADER: EMPTY")
                        }
                    }
                    // But we only expect snapshot states
                    // TODO: Handle snapshots

                    is EbftVersion -> logger.debug { "Received EbftVersion from peer $peerId" }
                }
            } catch (e: Exception) {
                logger.info("Couldn't handle message $message from peer $peerId. Ignoring and continuing", e)
            }
        }
    }

    // Snapshot responses must be validated as proofs

    // Maybe we can fetch blocks with existing GetBlockRange?

}
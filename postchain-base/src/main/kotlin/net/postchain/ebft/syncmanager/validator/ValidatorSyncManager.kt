// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.syncmanager.validator

import mu.KLogging
import net.postchain.common.toHex
import net.postchain.core.NodeRid
import net.postchain.core.NodeStateTracker
import net.postchain.core.ProgrammerMistake
import net.postchain.crypto.Signature
import net.postchain.ebft.*
import net.postchain.ebft.message.*
import net.postchain.ebft.rest.contract.serialize
import net.postchain.ebft.syncmanager.BlockDataDecoder.decodeBlockData
import net.postchain.ebft.syncmanager.BlockDataDecoder.decodeBlockDataWithWitness
import net.postchain.ebft.syncmanager.StatusLogInterval
import net.postchain.ebft.syncmanager.common.EBFTNodesCondition
import net.postchain.ebft.syncmanager.common.FastSyncParameters
import net.postchain.ebft.syncmanager.common.FastSynchronizer
import net.postchain.ebft.worker.WorkerContext
import nl.komponents.kovenant.task
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The ValidatorSyncManager handles communications with our peers.
 */
class ValidatorSyncManager(private val workerContext: WorkerContext,
                           private val statusManager: StatusManager,
                           private val blockManager: BlockManager,
                           private val blockDatabase: BlockDatabase,
                           private val nodeStateTracker: NodeStateTracker,
                           private val isProcessRunning: () -> Boolean,
                           startInFastSync: Boolean
) {
    private val blockQueries = workerContext.engine.getBlockQueries()
    private val communicationManager = workerContext.communicationManager
    private val blockchainConfiguration = workerContext.engine.getConfiguration()
    private val revoltTracker = RevoltTracker(10000, statusManager)
    private val statusSender = StatusSender(1000, statusManager, workerContext.communicationManager)
    private val defaultTimeout = 1000
    private var currentTimeout: Int
    private var processingIntent: BlockIntent
    private var processingIntentDeadline = 0L
    private var lastStatusLogged: Long
    private val processName = workerContext.processName
    private var useFastSyncAlgorithm = AtomicBoolean()
    private val fastSynchronizer: FastSynchronizer

    companion object : KLogging()

    init {
        this.currentTimeout = defaultTimeout
        this.processingIntent = DoNothingIntent
        this.lastStatusLogged = Date().time
        val nodeConfig = workerContext.nodeConfig
        val params = FastSyncParameters.fromAppConfig(workerContext.appConfig) {
            it.mustSyncUntilHeight = nodeConfig.mustSyncUntilHeight?.get(blockchainConfiguration.chainID) ?: -1
        }
        fastSynchronizer = FastSynchronizer(workerContext, blockDatabase, params, isProcessRunning)

        // Init useFastSyncAlgorithm
        val lastHeight = blockQueries.getBestHeight().get()
        val startWithFastSync = when {
            lastHeight < params.mustSyncUntilHeight -> true
            else -> startInFastSync
        }
        if (startWithFastSync) {
            useFastSyncAlgorithm.set(startWithFastSync)
            runFastSync()
        }
    }

    private val signersIds = workerContext.blockchainConfiguration.signers.map { NodeRid(it) }
    private fun indexOfValidator(peerId: NodeRid): Int = signersIds.indexOf(peerId)
    private fun validatorAtIndex(index: Int): NodeRid = signersIds[index]

    /**
     * Handle incoming messages
     */
    fun dispatchMessage(packet: Pair<NodeRid, EbftMessage>) {
        if (useFastSyncAlgorithm.get()) {
            fastSynchronizer.processMessage(packet)
            return
        }

        val (xPeerId, message) = packet

        val nodeIndex = indexOfValidator(xPeerId)
        logger.trace { "$processName: Received message type ${message.javaClass.simpleName} from node $nodeIndex" }

        try {
            // validator consensus logic
            when (message) {
                is Status -> {
                    NodeStatus(message.height, message.serial)
                            .apply {
                                blockRID = message.blockRID
                                revolting = message.revolting
                                round = message.round
                                state = NodeState.values()[message.state]
                            }.also {
                                statusManager.onStatusUpdate(nodeIndex, it)
                            }

                    if (tryToSwitchToFastSync()) {
                        runFastSync()
                    }
                }
                is BlockSignature -> {
                    val signature = Signature(message.sig.subjectID, message.sig.data)
                    val smBlockRID = this.statusManager.myStatus.blockRID
                    if (smBlockRID == null) {
                        logger.info("$processName: Received signature not needed")
                    } else if (!smBlockRID.contentEquals(message.blockRID)) {
                        logger.info("$processName: Receive signature for a different block")
                    } else if (this.blockDatabase.verifyBlockSignature(signature)) {
                        this.statusManager.onCommitSignature(nodeIndex, message.blockRID, signature)
                    }
                }
                is CompleteBlock -> {
                    try {
                        blockManager.onReceivedBlockAtHeight(
                                decodeBlockDataWithWitness(message, blockchainConfiguration),
                                message.height)
                    } catch (e: Exception) {
                        logger.error("Failed to add block to database. Resetting state...", e)
                        // reset state to last known from database
                        val currentBlockHeight = blockQueries.getBestHeight().get()
                        statusManager.fastForwardHeight(currentBlockHeight)
                        blockManager.currentBlock = null
                    }
                }
                is UnfinishedBlock -> {
                    blockManager.onReceivedUnfinishedBlock(
                            decodeBlockData(
                                    BlockData(message.header, message.transactions),
                                    blockchainConfiguration)
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("$processName: Couldn't handle message $message. Ignoring and continuing", e)
        }
    }

    private fun runFastSync() {
        task {
            if (logger.isDebugEnabled) {
                logger.debug("$processName Using fast sync") // Doesn't happen very often
            }
            fastSynchronizer.syncUntilResponsiveNodesDrained()
            val currentBlockHeight = blockQueries.getBestHeight().get()
            statusManager.fastForwardHeight(currentBlockHeight)
            blockManager.currentBlock = null
            logFastSyncStatus(currentBlockHeight)
            // turn off fast sync, reset current block to null, and query for the last known state from db to prevent
            // possible race conditions
            useFastSyncAlgorithm.set(false)
        }
    }

    /**
     * Pick a random node from all nodes matching certain conditions
     *
     * @param match function that checks whether a node matches our selection conditions
     * @return index of selected node
     */
    private fun selectRandomNode(match: (NodeStatus) -> Boolean): Int? {
        val matchingIndexes = mutableListOf<Int>()
        statusManager.nodeStatuses.forEachIndexed { index, status ->
            if (match(status)) matchingIndexes.add(index)
        }
        if (matchingIndexes.isEmpty()) return null
        if (matchingIndexes.size == 1) return matchingIndexes[0]
        return matchingIndexes[Math.floor(Math.random() * matchingIndexes.size).toInt()]
    }

    /**
     * Send message to random peer to retrieve the block at [height]
     *
     * @param height the height at which we want the block
     */
    private fun fetchBlockAtHeight(height: Long) {
        val nodeIndex = selectRandomNode { it.height > height } ?: return
        logger.debug{ "$processName: Fetching block at height $height from node $nodeIndex" }
        communicationManager.sendPacket(GetBlockAtHeight(height), validatorAtIndex(nodeIndex))
    }

    /**
     * Send message to fetch commit signatures from [nodes]
     *
     * @param blockRID identifier of the block to fetch signatures for
     * @param nodes list of nodes we want commit signatures from
     */
    private fun fetchCommitSignatures(blockRID: ByteArray, nodes: Array<Int>) {
        val message = GetBlockSignature(blockRID)
        logger.debug{ "$processName: Fetching commit signature for block with RID ${blockRID.toHex()} from nodes ${Arrays.toString(nodes)}" }
        nodes.forEach {
            communicationManager.sendPacket(message, validatorAtIndex(it))
        }
    }

    /**
     * Send message to random peer for fetching latest unfinished block at the same height as us
     *
     * @param blockRID identifier of the unfinished block
     */
    private fun fetchUnfinishedBlock(blockRID: ByteArray) {
        val height = statusManager.myStatus.height
        val nodeIndex = selectRandomNode {
            it.height == height && (it.blockRID?.contentEquals(blockRID) ?: false)
        } ?: return
        logger.debug{ "$processName: Fetching unfinished block with RID ${blockRID.toHex()} from node $nodeIndex " }
        communicationManager.sendPacket(GetUnfinishedBlock(blockRID), validatorAtIndex(nodeIndex))
    }

    /**
     * Process our intent latest intent
     */
    private fun processIntent() {
        val intent = blockManager.processBlockIntent()

        if (intent == processingIntent) {
            if (intent is DoNothingIntent) return
            if (Date().time > processingIntentDeadline) {
                this.currentTimeout = (this.currentTimeout.toDouble() * 1.1).toInt() // exponential back-off
            } else {
                return
            }
        } else {
            currentTimeout = defaultTimeout
        }

        when (intent) {
            DoNothingIntent -> Unit
            is FetchBlockAtHeightIntent -> fetchBlockAtHeight(intent.height)
            is FetchCommitSignatureIntent -> fetchCommitSignatures(intent.blockRID, intent.nodes)
            is FetchUnfinishedBlockIntent -> fetchUnfinishedBlock(intent.blockRID)
            else -> throw ProgrammerMistake("Unrecognized intent: ${intent::class}")
        }
        processingIntent = intent
        processingIntentDeadline = Date().time + currentTimeout
    }


    /**
     * Log status of all nodes including their latest block RID and if they have the signature or not
     */
    private fun logFastSyncStatus(currentBlockHeight: Long) {
        if (logger.isDebugEnabled) {
            val smIntent = statusManager.getBlockIntent()
            val bmIntent = blockManager.getBlockIntent()
            val primary = if (statusManager.isMyNodePrimary()) {
                "I'm primary, "
            } else {
                "(prim = ${statusManager.primaryIndex()}),"
            }
            logger.debug{ "$processName: (Fast sync) Height: $currentBlockHeight. My node: ${statusManager.getMyIndex()}, $primary block mngr: $bmIntent, status mngr: $smIntent" }
        }
        for ((idx, ns) in statusManager.nodeStatuses.withIndex()) {
            val blockRID = ns.blockRID
            val haveSignature = statusManager.commitSignatures[idx] != null
            if (logger.isDebugEnabled) {
                logger.debug {
                    "$processName: (Fast sync) node:$idx he:${ns.height} ro:${ns.round} st:${ns.state}" +
                            (if (ns.revolting) " R" else "") +
                            " blockRID:${blockRID?.toHex() ?: "null"}" +
                            " havesig:$haveSignature"
                }
            }
        }
    }


    /**
     * Log status of all nodes including their latest block RID and if they have the signature or not
     */
    private fun logStatus() {
        if (logger.isDebugEnabled) {
            val smIntent = statusManager.getBlockIntent()
            val bmIntent = blockManager.getBlockIntent()
            val primary = if (statusManager.isMyNodePrimary()) {
                "I'm primary, "
            } else {
                "(prim = ${statusManager.primaryIndex()}),"
            }
            logger.debug{ "$processName: My node: ${statusManager.getMyIndex()}, $primary block mngr: $bmIntent, status mngr: $smIntent" }
        }
        for ((idx, ns) in statusManager.nodeStatuses.withIndex()) {
            val blockRID = ns.blockRID
            val haveSignature = statusManager.commitSignatures[idx] != null
            if (logger.isDebugEnabled) {
                logger.debug {
                    "$processName: node:$idx he:${ns.height} ro:${ns.round} st:${ns.state}" +
                            (if (ns.revolting) " R" else "") +
                            " blockRID:${blockRID?.toHex() ?: "null"}" +
                            " havesig:$haveSignature"
                }
            }
        }
    }

    private fun tryToSwitchToFastSync(): Boolean {
        val fastSyncConditionSatisfied = EBFTNodesCondition(statusManager.nodeStatuses) { status ->
            status.height - statusManager.myStatus.height >= 3
        }.satisfied()
        useFastSyncAlgorithm.set(fastSyncConditionSatisfied)
        return fastSyncConditionSatisfied
    }

    /**
     * Process how we should proceed with the current block, updating the revolt tracker and
     * notify peers of our current status.
     */
    fun update() {
        if (!useFastSyncAlgorithm.get()) {
            // An intent is something that we want to do with our current block.
            // The current intent is fetched from the BlockManager and will result in
            // some messages being sent to peers requesting data like signatures or
            // complete blocks
            processIntent()

            // RevoltTracker will check trigger a revolt if conditions for revolting are met
            // A revolt will be triggerd by calling statusManager.onStartRevolting()
            // Typical revolt conditions
            //    * A timeout happens and round has not increased. Round is increased then 2f+1 nodes
            //      are revolting.
            revoltTracker.update()

            // Sends a status message to all peers when my status has changed or after a timeout
            statusSender.update()

            nodeStateTracker.myStatus = statusManager.myStatus.serialize()
            nodeStateTracker.nodeStatuses = statusManager.nodeStatuses.map { it.serialize() }.toTypedArray()
            nodeStateTracker.blockHeight = statusManager.myStatus.height

            if (Date().time - lastStatusLogged >= StatusLogInterval) {
                logStatus()
                lastStatusLogged = Date().time
            }
        }
    }

    private fun getLastBlockTimestamp(): Long {
        // The field blockManager.lastBlockTimestamp will be set to non-null value
        // after the first block db operation. So we read lastBlockTimestamp value from db
        // until blockManager.lastBlockTimestamp is non-null.
        return blockManager.lastBlockTimestamp ?: blockQueries.getLastBlockTimestamp().get()
    }

    fun getHeight(): Long {
        return if (useFastSyncAlgorithm.get()) fastSynchronizer.blockHeight
        else nodeStateTracker.blockHeight
    }

    fun isInFastSync(): Boolean {
        return useFastSyncAlgorithm.get()
    }
}

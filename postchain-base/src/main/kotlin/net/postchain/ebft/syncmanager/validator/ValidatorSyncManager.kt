// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.syncmanager.validator

import io.micrometer.core.instrument.Counter
import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.withReadConnection
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.toHex
import net.postchain.concurrent.util.get
import net.postchain.concurrent.util.whenCompleteUnwrapped
import net.postchain.core.NodeRid
import net.postchain.crypto.Signature
import net.postchain.ebft.BlockDatabase
import net.postchain.ebft.BlockIntent
import net.postchain.ebft.BlockManager
import net.postchain.ebft.DoNothingIntent
import net.postchain.ebft.FetchBlockAtHeightIntent
import net.postchain.ebft.FetchCommitSignatureIntent
import net.postchain.ebft.FetchUnfinishedBlockIntent
import net.postchain.ebft.NodeBlockState
import net.postchain.ebft.NodeStateTracker
import net.postchain.ebft.NodeStatus
import net.postchain.ebft.StatusManager
import net.postchain.ebft.message.AppliedConfig
import net.postchain.ebft.message.BlockData
import net.postchain.ebft.message.BlockHeader
import net.postchain.ebft.message.BlockRange
import net.postchain.ebft.message.BlockSignature
import net.postchain.ebft.message.CompleteBlock
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockHeaderAndBlock
import net.postchain.ebft.message.GetBlockRange
import net.postchain.ebft.message.GetBlockSignature
import net.postchain.ebft.message.GetUnfinishedBlock
import net.postchain.ebft.message.Status
import net.postchain.ebft.message.Transaction
import net.postchain.ebft.message.UnfinishedBlock
import net.postchain.ebft.syncmanager.BlockDataDecoder.decodeBlockData
import net.postchain.ebft.syncmanager.BlockDataDecoder.decodeBlockDataWithWitness
import net.postchain.ebft.syncmanager.StatusLogInterval
import net.postchain.ebft.syncmanager.common.BlockPacker
import net.postchain.ebft.syncmanager.common.EBFTNodesCondition
import net.postchain.ebft.syncmanager.common.FastSyncPeerStatuses
import net.postchain.ebft.syncmanager.common.FastSynchronizer
import net.postchain.ebft.syncmanager.common.Messaging
import net.postchain.ebft.syncmanager.common.SyncParameters
import net.postchain.ebft.worker.WorkerContext
import net.postchain.getBFTRequiredSignatureCount
import net.postchain.metrics.SyncMetrics
import java.time.Clock
import java.util.Date
import java.util.concurrent.CompletableFuture

/**
 * The ValidatorSyncManager handles communications with our peers.
 */
class ValidatorSyncManager(private val workerContext: WorkerContext,
                           private val loggingContext: Map<String, String>,
                           private val statusManager: StatusManager,
                           private val blockManager: BlockManager,
                           private val blockDatabase: BlockDatabase,
                           private val nodeStateTracker: NodeStateTracker,
                           private val revoltTracker: RevoltTracker,
                           private val syncMetrics: SyncMetrics,
                           isProcessRunning: () -> Boolean,
                           startInFastSync: Boolean,
                           private val clock: Clock = Clock.systemUTC()
) : Messaging(workerContext.engine.getBlockQueries(), workerContext.communicationManager, BlockPacker) {
    private val blockchainConfiguration = workerContext.blockchainConfiguration
    private val statusSender = StatusSender(MAX_STATUS_INTERVAL, workerContext, statusManager, clock)
    private val defaultTimeout = 1000
    private var currentTimeout: Int
    private var processingIntent: BlockIntent
    private var processingIntentDeadline = 0L
    private var lastStatusLogged: Long
    private val messageDurationTracker = workerContext.messageDurationTracker

    @Volatile
    private var useFastSyncAlgorithm: Boolean
    private val fastSynchronizer: FastSynchronizer

    companion object : KLogging() {
        const val MAX_STATUS_INTERVAL = 1_000
        const val STATUS_TIMEOUT = 2_000
    }

    init {
        this.currentTimeout = defaultTimeout
        this.processingIntent = DoNothingIntent
        this.lastStatusLogged = Date().time
        val nodeConfig = workerContext.nodeConfig
        val params = SyncParameters.fromAppConfig(workerContext.appConfig) {
            it.mustSyncUntilHeight = nodeConfig.mustSyncUntilHeight?.get(blockchainConfiguration.chainID) ?: -1
        }
        fastSynchronizer = FastSynchronizer(
                workerContext,
                blockDatabase,
                params,
                FastSyncPeerStatuses(params),
                isProcessRunning
        )

        // Init useFastSyncAlgorithm
        val lastHeight = blockQueries.getLastBlockHeight().get()
        useFastSyncAlgorithm = when {
            lastHeight < params.mustSyncUntilHeight -> true
            else -> startInFastSync
        }
    }

    private val signersIds = workerContext.blockchainConfiguration.signers.map { NodeRid(it) }
    private fun indexOfValidator(peerId: NodeRid): Int = signersIds.indexOf(peerId)
    fun validatorAtIndex(index: Int): NodeRid = signersIds[index]

    /**
     * Handle incoming messages
     */
    internal fun dispatchMessages() {
        messageDurationTracker.cleanup()
        for ((xPeerId, _, message) in communicationManager.getPackets()) {
            val nodeIndex = indexOfValidator(xPeerId)
            val isReadOnlyNode = nodeIndex == -1 // This must be a read-only node since not in the validator list

            logger.trace { "Received message type ${message.javaClass.simpleName} from node $xPeerId ($nodeIndex)" }

            try {
                when (message) {
                    // same case for replica and validator node
                    is GetBlockAtHeight -> sendBlockAtHeight(xPeerId, message.height)
                    is GetBlockRange -> sendBlockRangeFromHeight(xPeerId, message.startAtHeight,
                            this.statusManager.myStatus.height - 1)

                    is GetBlockHeaderAndBlock -> sendBlockHeaderAndBlock(xPeerId, message.height,
                            this.statusManager.myStatus.height - 1)

                    else -> {
                        if (!isReadOnlyNode) { // TODO: [POS-90]: Is it necessary here `isReadOnlyNode`?
                            // validator consensus logic
                            when (message) {
                                is Status -> {
                                    NodeStatus(message.height, message.serial)
                                            .apply {
                                                blockRID = message.blockRID
                                                revolting = message.revolting
                                                round = message.round
                                                state = NodeBlockState.values()[message.state]
                                                if (shouldSetSignature(state, message)) {
                                                    logger.trace { "Got signature from Status for ${blockRID?.toHex()} from $xPeerId" }
                                                    signature = message.signature
                                                }
                                            }.also {
                                                applyConfig(message.configHash, message.height)
                                                statusManager.onStatusUpdate(nodeIndex, it)
                                            }

                                    tryToSwitchToFastSync()
                                }

                                is BlockSignature -> {
                                    messageDurationTracker.receive(xPeerId, message)
                                    val signature = Signature(message.sig.subjectID, message.sig.data)
                                    val smBlockRID = this.statusManager.myStatus.blockRID
                                    if (smBlockRID == null || processingIntent !is FetchCommitSignatureIntent) {
                                        logger.debug("Received signature not needed")
                                    } else if (!smBlockRID.contentEquals(message.blockRID)) {
                                        logger.info("Receive signature for a different block")
                                    } else if (this.blockDatabase.applyAndVerifyBlockSignature(signature)) {
                                        this.statusManager.onCommitSignature(nodeIndex, message.blockRID, signature)
                                    } else {
                                        logger.warn { "BlockSignature from peer: $xPeerId is invalid for block with with RID: ${smBlockRID.toHex()}" }
                                    }
                                }

                                is CompleteBlock -> {
                                    messageDurationTracker.receive(xPeerId, message)
                                    blockManager.onReceivedBlockAtHeight(
                                            decodeBlockDataWithWitness(message, blockchainConfiguration),
                                            message.height)
                                }

                                is UnfinishedBlock -> {
                                    val blockData = decodeBlockData(
                                            BlockData(message.header, message.transactions),
                                            blockchainConfiguration)
                                    messageDurationTracker.receive(xPeerId, message, blockData.header)
                                    blockManager.onReceivedUnfinishedBlock(blockData)
                                }

                                is BlockRange -> {
                                    messageDurationTracker.receive(xPeerId, message)
                                    // Only replicas should receive BlockRanges (via SlowSync)
                                    logger.warn("Why did we get a block range from peer: ${xPeerId}? (Starting " +
                                            "height: ${message.startAtHeight}, blocks: ${message.blocks.size}) ")
                                }

                                is GetUnfinishedBlock -> sendUnfinishedBlock(nodeIndex)
                                is GetBlockSignature -> sendBlockSignature(nodeIndex, message.blockRID)
                                is Transaction -> handleTransaction(message)
                                is BlockHeader -> {
                                    messageDurationTracker.receive(xPeerId, message)
                                    // TODO: This might happen because we've already exited FastSync but other nodes
                                    //  are still responding to our old requests. For this case this is harmless.
                                }

                                is AppliedConfig -> applyConfig(message.configHash, message.height)

                                else -> throw ProgrammerMistake("Unhandled type ${message::class}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Couldn't handle message $message. Ignoring and continuing", e)
            }
        }
    }

    private fun applyConfig(configHash: ByteArray?, height: Long) {
        configHash?.apply {
            if (statusManager.myStatus.revolting) {
                processIncomingConfig(this, height)
            }
        }
    }

    private fun shouldSetSignature(state: NodeBlockState, message: Status) =
            (statusManager.shouldApplySignature(state)
                    && message.signature != null
                    && message.blockRID != null
                    && message.blockRID.contentEquals(statusManager.myStatus.blockRID)
                    && blockDatabase.applyAndVerifyBlockSignature(message.signature))

    internal fun processIncomingConfig(incomingConfigHash: ByteArray, incomingHeight: Long) {
        val bcConfig = workerContext.blockchainConfiguration
        if (incomingHeight == statusManager.myStatus.height && !incomingConfigHash.contentEquals(bcConfig.configHash)) {
            restartWithNewConfigIfPossible()
        }
    }

    private fun restartWithNewConfigIfPossible() {
        val chainId = workerContext.blockchainConfiguration.chainID
        val bcConfigProvider = workerContext.blockchainConfigurationProvider
        try {
            withReadConnection(workerContext.engine.blockBuilderStorage, chainId) { ctx ->
                if (workerContext.engine.hasBuiltFirstBlockAfterConfigUpdate()
                        && bcConfigProvider.activeBlockNeedsConfigurationChange(ctx, chainId, true)) {
                    logger.debug("New config found. Reloading.")
                    workerContext.restartNotifier.notifyRestart(true)
                }
            }
        } catch (e: Exception) {
            logger.error("Couldn't check for config updates, ignoring and continuing", e)
        }
    }

    /**
     * Handle transaction received from peer
     *
     * @param message message including the transaction
     */
    private fun handleTransaction(message: Transaction) {
        // TODO: reject if queue is full
        CompletableFuture.runAsync {
            withLoggingContext(loggingContext) {
                val tx = blockchainConfiguration.getTransactionFactory().decodeTransaction(message.data)
                workerContext.engine.getTransactionQueue().enqueue(tx)
            }
        }
    }

    /**
     * Send message to peer with our commit signature
     *
     * @param nodeIndex node index of receiving peer
     * @param blockRID block identifier
     */
    private fun sendBlockSignature(nodeIndex: Int, blockRID: ByteArray) {
        val currentBlock = this.blockManager.currentBlock
        if (currentBlock != null && currentBlock.header.blockRID.contentEquals(blockRID)) {
            if (!statusManager.myStatus.blockRID!!.contentEquals(currentBlock.header.blockRID)) {
                throw ProgrammerMistake("status manager block RID (${statusManager.myStatus.blockRID!!.toHex()}) out of sync with current block RID (${currentBlock.header.blockRID.toHex()})")
            }
            val signature = statusManager.getCommitSignature()
            if (signature != null) {
                communicationManager.sendPacket(BlockSignature(
                        blockRID,
                        Signature(signature.subjectID, signature.data)),
                        validatorAtIndex(nodeIndex))
            }
        } else {
            val blockSignature = blockDatabase.getBlockSignature(blockRID)
            blockSignature.whenCompleteUnwrapped(loggingContext, onSuccess = { response ->
                val packet = BlockSignature(blockRID, Signature(response.subjectID, response.data))
                communicationManager.sendPacket(packet, validatorAtIndex(nodeIndex))
            }, onError = { error ->
                logger.debug(error) { "Error sending BlockSignature" }
            })
        }
    }

    /**
     * Send message to node with the current unfinished block.
     *
     * @param nodeIndex index of node to send block to
     */
    private fun sendUnfinishedBlock(nodeIndex: Int) {
        val currentBlock = blockManager.currentBlock
        if (currentBlock != null) {
            communicationManager.sendPacket(UnfinishedBlock(currentBlock.header.rawData, currentBlock.transactions.toList()),
                    validatorAtIndex(nodeIndex))
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
        return matchingIndexes.randomOrNull()
    }

    /**
     * Send message to random peer to retrieve the block at [height]
     *
     * @param height the height at which we want the block
     */
    private fun fetchBlockAtHeight(height: Long) {
        val nodeIndex = selectRandomNode { it.height > height } ?: return
        logger.debug { "Fetching block at height $height from node $nodeIndex" }

        val message = GetBlockAtHeight(height)
        val peer = validatorAtIndex(nodeIndex)
        messageDurationTracker.send(peer, message)
        communicationManager.sendPacket(message, peer)
    }

    /**
     * Send message to fetch commit signatures from [nodes]
     *
     * @param blockRID identifier of the block to fetch signatures for
     * @param nodes list of nodes we want commit signatures from
     */
    private fun fetchCommitSignatures(blockRID: ByteArray, nodes: Array<Int>) {
        val message = GetBlockSignature(blockRID)
        logger.debug { "Fetching commit signature for block with RID ${blockRID.toHex()} from nodes ${nodes.contentToString()}" }
        val peers = nodes.map { validatorAtIndex(it) }
        messageDurationTracker.send(peers, message)
        communicationManager.sendPacket(message, peers)
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
        logger.debug { "Fetching unfinished block with RID ${blockRID.toHex()} from node $nodeIndex " }
        val message = GetUnfinishedBlock(blockRID)
        val peer = validatorAtIndex(nodeIndex)
        messageDurationTracker.send(peer, message)
        communicationManager.sendPacket(message, peer)
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
            is FetchBlockAtHeightIntent -> if (!useFastSyncAlgorithm) {
                fetchBlockAtHeight(intent.height)
            }

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
            logger.debug { "(Fast sync) Height: $currentBlockHeight. My node: ${statusManager.getMyIndex()}, $primary block mngr: $bmIntent, status mngr: $smIntent" }
        }
        for ((idx, ns) in statusManager.nodeStatuses.withIndex()) {
            val blockRID = ns.blockRID
            val haveSignature = statusManager.commitSignatures[idx] != null
            if (logger.isDebugEnabled) {
                logger.debug {
                    "(Fast sync) node:$idx he:${ns.height} ro:${ns.round} st:${ns.state}" +
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
            logger.debug { "My node: ${statusManager.getMyIndex()}, $primary block mngr: $bmIntent, status mngr: $smIntent" }
        }
        for ((idx, ns) in statusManager.nodeStatuses.withIndex()) {
            val blockRID = ns.blockRID
            val haveSignature = statusManager.commitSignatures[idx] != null
            if (logger.isDebugEnabled) {
                logger.debug {
                    "node:$idx he:${ns.height} ro:${ns.round} st:${ns.state}" +
                            (if (ns.revolting) " R" else "") +
                            " blockRID:${blockRID?.toHex() ?: "null"}" +
                            " havesig:$haveSignature"
                }
            }
        }
    }

    internal fun tryToSwitchToFastSync() {
        val useFastSyncAlgorithmBefore = useFastSyncAlgorithm
        useFastSyncAlgorithm = EBFTNodesCondition(statusManager.nodeStatuses) { status ->
            status.height - statusManager.myStatus.height >= 3
        }.satisfied()
        if (useFastSyncAlgorithm && useFastSyncAlgorithmBefore != useFastSyncAlgorithm) {
            getValidatorFastSyncSwitchCounter().increment()
        }
    }

    private fun getValidatorFastSyncSwitchCounter(): Counter =
            when (statusManager.myStatus.state) {
                NodeBlockState.WaitBlock -> syncMetrics.validatorFastSyncSwitchWaitBlock
                NodeBlockState.HaveBlock -> syncMetrics.validatorFastSyncSwitchHaveBlock
                NodeBlockState.Prepared -> syncMetrics.validatorFastSyncSwitchPrepared
            }

    /**
     * Process peer messages, how we should proceed with the current block, updating the revolt tracker and
     * notify peers of our current status.
     */
    fun update() {
        if (useFastSyncAlgorithm) {
            logger.debug("Using fast sync") // Doesn't happen very often
            // Wait for any queued blocks to commit/fail before starting sync
            blockManager.waitForRunningOperationsToComplete()
            fastSynchronizer.syncUntilResponsiveNodesDrained()
            // turn off fast sync, reset current block to null, and query for the last known state from db to prevent
            // possible race conditions
            useFastSyncAlgorithm = false
            val currentBlockHeight = blockQueries.getLastBlockHeight().get()
            statusManager.fastForwardHeight(currentBlockHeight)
            blockManager.currentBlock = null
            logFastSyncStatus(currentBlockHeight)
        } else {
            synchronized(statusManager) {
                // Process all messages from peers, one at a time. Some
                // messages may trigger asynchronous code which will
                // send replies at a later time, others will send replies
                // immediately
                dispatchMessages()

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

                nodeStateTracker.myStatus = statusManager.myStatus
                nodeStateTracker.nodeStatuses = statusManager.nodeStatuses
                nodeStateTracker.blockHeight = statusManager.myStatus.height

                if (statusManager.myStatus.revolting) {
                    checkIfConfigReloadIsNeeded()
                }

                if (Date().time - lastStatusLogged >= StatusLogInterval) {
                    logStatus()
                    lastStatusLogged = Date().time
                }
            }
        }
    }

    private fun checkIfConfigReloadIsNeeded() {
        val now = clock.millis()

        val liveSigners = statusManager.nodeStatuses.indices.count {
            it == statusManager.getMyIndex() || now - statusManager.getLatestStatusTimestamp(it) < STATUS_TIMEOUT
        }

        // If the amount of live signers is less than BFT majority it may indicate that there is a new config with removed signers
        if (liveSigners < getBFTRequiredSignatureCount(statusManager.nodeStatuses.size)) {
            restartWithNewConfigIfPossible()
        }
    }

    fun isInFastSync(): Boolean {
        return useFastSyncAlgorithm
    }

    fun currentBlockHeight(): Long? = if (isInFastSync())
        fastSynchronizer.blockHeight.get()
    else
        nodeStateTracker.myStatus?.height?.let { it - 1 }
}

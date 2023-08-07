// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.syncmanager.validator

import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.configuration.BaseBlockchainConfiguration
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
import net.postchain.ebft.syncmanager.common.EBFTNodesCondition
import net.postchain.ebft.syncmanager.common.FastSynchronizer
import net.postchain.ebft.syncmanager.common.Messaging
import net.postchain.ebft.syncmanager.common.SyncParameters
import net.postchain.ebft.worker.WorkerContext
import net.postchain.getBFTRequiredSignatureCount
import net.postchain.gtv.mapper.toObject
import java.util.*
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
                           isProcessRunning: () -> Boolean,
                           startInFastSync: Boolean
) : Messaging(workerContext.engine.getBlockQueries(), workerContext.communicationManager) {
    private val blockchainConfiguration = workerContext.engine.getConfiguration()
    private val revoltTracker = RevoltTracker(statusManager, getRevoltConfiguration(), workerContext.engine)
    private val statusSender = StatusSender(MAX_STATUS_INTERVAL, statusManager, workerContext.communicationManager)
    private val defaultTimeout = 1000
    private var currentTimeout: Int
    private var processingIntent: BlockIntent
    private var processingIntentDeadline = 0L
    private var lastStatusLogged: Long
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
        fastSynchronizer = FastSynchronizer(workerContext, blockDatabase, params, isProcessRunning)

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
    private fun dispatchMessages() {
        for (packet in communicationManager.getPackets()) {
            val (xPeerId, message) = packet
            val nodeIndex = indexOfValidator(xPeerId)
            val isReadOnlyNode = nodeIndex == -1 // This must be a read-only node since not in the validator list

            logger.trace { "Received message type ${message.javaClass.simpleName} from node $nodeIndex" }

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
                                            }.also {
                                                statusManager.onStatusUpdate(nodeIndex, it)
                                            }

                                    tryToSwitchToFastSync()
                                }

                                is BlockSignature -> {
                                    val signature = Signature(message.sig.subjectID, message.sig.data)
                                    val smBlockRID = this.statusManager.myStatus.blockRID
                                    if (smBlockRID == null || processingIntent !is FetchCommitSignatureIntent) {
                                        logger.debug("Received signature not needed")
                                    } else if (!smBlockRID.contentEquals(message.blockRID)) {
                                        logger.info("Receive signature for a different block")
                                    } else if (this.blockDatabase.verifyBlockSignature(signature)) {
                                        this.statusManager.onCommitSignature(nodeIndex, message.blockRID, signature)
                                    } else {
                                        logger.warn { "BlockSignature from peer: $xPeerId is invalid for block with with RID: ${smBlockRID.toHex()}" }
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
                                        statusManager.fastForwardHeight(blockQueries.getLastBlockHeight().get())
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

                                is BlockRange -> {
                                    // Only replicas should receive BlockRanges (via SlowSync)
                                    logger.warn("Why did we get a block range from peer: ${xPeerId}? (Starting " +
                                            "height: ${message.startAtHeight}, blocks: ${message.blocks.size}) ")
                                }

                                is GetUnfinishedBlock -> sendUnfinishedBlock(nodeIndex)
                                is GetBlockSignature -> sendBlockSignature(nodeIndex, message.blockRID)
                                is Transaction -> handleTransaction(message)
                                is BlockHeader -> {
                                    // TODO: This might happen because we've already exited FastSync but other nodes
                                    //  are still responding to our old requests. For this case this is harmless.
                                }

                                is AppliedConfig -> {
                                    if (statusManager.myStatus.revolting) {
                                        processIncomingConfig(message.configHash, message.height)
                                    }
                                }

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

    private fun processIncomingConfig(incomingConfigHash: ByteArray, incomingHeight: Long) {
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
                        net.postchain.ebft.message.Signature(signature.subjectID, signature.data)),
                        validatorAtIndex(nodeIndex))
            }
            return
        }
        val blockSignature = blockDatabase.getBlockSignature(blockRID)
        blockSignature.whenCompleteUnwrapped(loggingContext) { response, error ->
            if (error == null) {
                val packet = BlockSignature(blockRID, net.postchain.ebft.message.Signature(response.subjectID, response.data))
                communicationManager.sendPacket(packet, validatorAtIndex(nodeIndex))
            } else {
                logger.debug(error) { "Error sending BlockSignature" }
            }
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
        logger.debug { "Fetching block at height $height from node $nodeIndex" }
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
        logger.debug { "Fetching commit signature for block with RID ${blockRID.toHex()} from nodes ${Arrays.toString(nodes)}" }
        communicationManager.sendPacket(message, nodes.map { validatorAtIndex(it) })
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

    private fun tryToSwitchToFastSync() {
        useFastSyncAlgorithm = EBFTNodesCondition(statusManager.nodeStatuses) { status ->
            status.height - statusManager.myStatus.height >= 3
        }.satisfied()
    }

    /**
     * Process peer messages, how we should proceed with the current block, updating the revolt tracker and
     * notify peers of our current status.
     */
    fun update() {
        if (useFastSyncAlgorithm) {
            logger.debug("Using fast sync") // Doesn't happen very often
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
        val now = System.currentTimeMillis()

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

    private fun getRevoltConfiguration(): RevoltConfigurationData {
        return if (blockchainConfiguration is BaseBlockchainConfiguration) {
            blockchainConfiguration.configData.revoltConfigData?.toObject()
                    ?: RevoltConfigurationData.default
        } else {
            RevoltConfigurationData.default
        }
    }
}

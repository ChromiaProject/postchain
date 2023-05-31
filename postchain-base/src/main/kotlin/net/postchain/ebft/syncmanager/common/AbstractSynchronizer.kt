package net.postchain.ebft.syncmanager.common

import mu.withLoggingContext
import net.postchain.base.BaseBlockHeader
import net.postchain.base.data.BaseBlockWitnessProvider
import net.postchain.base.extension.getConfigHash
import net.postchain.base.extension.getFailedConfigHash
import net.postchain.base.withReadConnection
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.core.BadDataMistake
import net.postchain.core.BadDataType
import net.postchain.core.NodeRid
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockTrace
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PrivKey
import net.postchain.crypto.PubKey
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.BDBAbortException
import net.postchain.ebft.message.AppliedConfig
import net.postchain.ebft.worker.WorkerContext
import net.postchain.getBFTRequiredSignatureCount
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.managed.ManagedBlockchainConfigurationProvider
import java.util.concurrent.CompletableFuture

abstract class AbstractSynchronizer(
        val workerContext: WorkerContext
) : Messaging(workerContext.engine.getBlockQueries(), workerContext.communicationManager) {

    protected val blockchainConfiguration = workerContext.engine.getConfiguration()
    protected val configuredPeers = workerContext.peerCommConfiguration.networkNodes.getPeerIds()

    // this is used to track pending asynchronous BlockDatabase.addBlock tasks to make sure failure to commit propagates properly
    protected var addBlockCompletionFuture: CompletableFuture<Unit>? = null

    var blockHeight: Long = blockQueries.getLastBlockHeight().get()

    private var pendingConfigPromotingUsAsSigner: ByteArray? = null
    private val relevantSignersThatHaveAppliedConfig = mutableSetOf<PubKey>()

    protected fun getHeight(header: BlockHeader): Long {
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

    protected val procName = BlockchainProcessName(
            workerContext.appConfig.pubKey,
            blockchainConfiguration.blockchainRid
    )

    /**
     * We do this check to avoid getting stuck when chain is waiting for a pending config where we are promoted to signer to be applied
     * In other cases config updates will be handled when adding blocks
     */
    protected fun checkIfWeNeedToApplyPendingConfig(peer: NodeRid, appliedConfig: AppliedConfig): Boolean {
        val incomingConfigHash = appliedConfig.configHash
        val incomingHeight = appliedConfig.height

        if (blockchainConfiguration.chainID == 0L) return false
        val configProvider = workerContext.blockchainConfigurationProvider as? ManagedBlockchainConfigurationProvider
        if (configProvider == null || !configProvider.isPcuEnabled()) return false

        withLoggingContext(CHAIN_IID_TAG to blockchainConfiguration.chainID.toString()) {
            if (blockQueries.getLastBlockHeight().get() + 1 != incomingHeight) return false
            val currentConfigHash = blockchainConfiguration.configHash

            logger.debug { "blockHeight = $blockHeight, blockQueries.getLastBlockHeight() = ${blockQueries.getLastBlockHeight().get()}, currentConfigHash = ${currentConfigHash.wrap()}" }
            logger.debug { "incomingHeight = $incomingHeight, incomingConfigHash = ${incomingConfigHash.wrap()}" }

            if (!currentConfigHash.contentEquals(incomingConfigHash)) {
                val isIncomingConfigPending = withReadConnection(workerContext.engine.storage, blockchainConfiguration.chainID) { ctx ->
                    configProvider.isConfigPending(
                            ctx,
                            blockchainConfiguration.blockchainRid,
                            incomingHeight,
                            incomingConfigHash
                    )
                }

                if (!isIncomingConfigPending) return false

                val pendingSigners = configProvider.getPendingConfigSigners(blockchainConfiguration.blockchainRid, incomingHeight, incomingConfigHash)
                val myPubKey = PubKey(workerContext.appConfig.pubKeyByteArray)
                val peerPubKey = PubKey(peer)
                return if (pendingSigners.contains(myPubKey)) {
                    val promotedNodesAmount = pendingSigners.subtract(blockchainConfiguration.signers.map { PubKey(it) }.toSet()).size
                    val pendingBftRequiredSignatureCount = getBFTRequiredSignatureCount(pendingSigners.size)

                    if (pendingSigners.size - promotedNodesAmount >= pendingBftRequiredSignatureCount) {
                        logger.debug { "Incoming config is pending with us as signer but we don't have to apply it since we are not blocking production of blocks" }
                        return false
                    }

                    if (!incomingConfigHash.contentEquals(pendingConfigPromotingUsAsSigner)) {
                        pendingConfigPromotingUsAsSigner = incomingConfigHash
                        relevantSignersThatHaveAppliedConfig.clear()
                    }

                    if (pendingSigners.contains(peerPubKey) && blockchainConfiguration.signers.any { it.contentEquals(peerPubKey.data) }) {
                        relevantSignersThatHaveAppliedConfig.add(peerPubKey)
                    }

                    logger.debug { "Promoted nodes amount: $promotedNodesAmount, signers that have applied config: $relevantSignersThatHaveAppliedConfig" }
                    // We don't care if other promoted nodes have applied the config or not, they can't build blocks with older config anyway
                    if (relevantSignersThatHaveAppliedConfig.size + promotedNodesAmount >= pendingBftRequiredSignatureCount) {
                        logger.debug { "Incoming config is pending with us as signer, will restart" }
                        workerContext.restartNotifier.notifyRestart(true)
                        true
                    } else {
                        logger.debug { "Incoming config is pending with us as signer, waiting for more nodes to apply it" }
                        false
                    }
                } else {
                    false
                }
            } else {
                return false
            }
        }
    }

    protected fun handleAddBlockException(exception: Throwable, block: BlockDataWithWitness, bTrace: BlockTrace?, peerStatuses: AbstractPeerStatuses<*>, peerId: NodeRid) {
        val height = getHeight(block.header)
        if (exception is PmEngineIsAlreadyClosed) {
            logger.warn { "Exception committing block height $height from peer: $peerId: ${exception.message}${bTrace?.let { ", from bTrace: $it" } ?: ""}" }
        } else if (exception is BDBAbortException) {
            logger.info { "Exception committing block height $height from peer: $peerId: ${exception.message}${bTrace?.let { ", from bTrace: $it" } ?: ""}" }
        } else if (exception is BadDataMistake && exception.type == BadDataType.CONFIGURATION_MISMATCH) {
            if (!checkIfNewConfigurationCanBeLoaded(block)) {
                peerStatuses.maybeBlacklist(peerId, "Received a block with mismatching config but we could not apply any new config")
            }
        } else if (exception is BadDataMistake && exception.type == BadDataType.FAILED_CONFIGURATION_MISMATCH) {
            // We only expect the case of us not having applied the failed config yet here
            val incomingFailedConfigHash = block.header.getFailedConfigHash()
            if (incomingFailedConfigHash != null) {
                if (!checkIfConfigIsPendingAndCanBeLoaded(block, incomingFailedConfigHash)) {
                    peerStatuses.maybeBlacklist(peerId, "Received a block with failing config that we could not apply")
                }
            } else {
                peerStatuses.maybeBlacklist(peerId, "Received a block without expected failed config hash")
            }
        } else {
            logger.warn(exception) { "Exception committing block height $height from peer: $peerId: ${exception.message}${bTrace?.let { ", from bTrace: $it" } ?: ""}" }
        }
    }

    private fun checkIfNewConfigurationCanBeLoaded(block: BlockDataWithWitness): Boolean {
        val bcConfigProvider = workerContext.blockchainConfigurationProvider
        val bcConfig = workerContext.blockchainConfiguration
        val hasNewConfig = withReadConnection(workerContext.engine.storage, bcConfig.chainID) { ctx ->
            bcConfigProvider.activeBlockNeedsConfigurationChange(ctx, bcConfig.chainID, false)
        }

        withLoggingContext(CHAIN_IID_TAG to blockchainConfiguration.chainID.toString()) {

            if (hasNewConfig) {
                logger.warn { "Wrong config used. Chain will be restarted" }
                workerContext.restartNotifier.notifyRestart(false)
                return true
            }

            val headerConfigHash = block.header.getConfigHash()
            return checkIfConfigIsPendingAndCanBeLoaded(block, headerConfigHash)
        }
    }

    private fun checkIfConfigIsPendingAndCanBeLoaded(block: BlockDataWithWitness, configHash: ByteArray?): Boolean {
        val bcConfigProvider = workerContext.blockchainConfigurationProvider
        val bcConfig = workerContext.blockchainConfiguration
        if (bcConfigProvider is ManagedBlockchainConfigurationProvider && configHash != null && bcConfigProvider.isPcuEnabled()) {
            val isIncomingConfigPending = withReadConnection(workerContext.engine.storage, bcConfig.chainID) { ctx ->
                bcConfigProvider.isConfigPending(
                        ctx,
                        blockchainConfiguration.blockchainRid,
                        getHeight(block.header),
                        configHash
                )
            }
            if (isIncomingConfigPending) {
                logger.debug { "Matching pending configuration detected, will validate block signature before reloading" }
                val pendingSigners = bcConfigProvider.getPendingConfigSigners(bcConfig.blockchainRid, getHeight(block.header), configHash)

                val cryptoSystem = workerContext.appConfig.cryptoSystem
                val myKeyPair = KeyPair(PubKey(workerContext.appConfig.pubKeyByteArray), PrivKey(workerContext.appConfig.privKeyByteArray))
                val validator = BaseBlockWitnessProvider(
                        cryptoSystem,
                        cryptoSystem.buildSigMaker(myKeyPair),
                        pendingSigners.map { it.data }.toTypedArray()
                )
                val witnessBuilder = validator.createWitnessBuilderWithoutOwnSignature(block.header)

                try {
                    validator.validateWitness(block.witness, witnessBuilder)
                    logger.debug { "Witness check passed. Reloading chain with pending configuration." }
                    workerContext.restartNotifier.notifyRestart(true)
                    return true
                } catch (e: Exception) {
                    logger.error(e) { "Block signature for block with new pending config is not valid" }
                }
            }
        }
        return false
    }
}
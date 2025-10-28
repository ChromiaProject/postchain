package net.postchain.ebft.syncmanager.common

import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.BaseBlockHeader
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.data.BaseBlockWitnessProvider
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.extension.getConfigHash
import net.postchain.base.extension.getFailedConfigHash
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.toHex
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.core.BadDataException
import net.postchain.core.ConfigurationMismatchException
import net.postchain.core.FailedConfigurationMismatchException
import net.postchain.core.NodeRid
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockTrace
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PrivKey
import net.postchain.crypto.PubKey
import net.postchain.crypto.SigMaker
import net.postchain.ebft.BDBAbortException
import net.postchain.ebft.syncmanager.configuration.RateLimitConfiguration
import net.postchain.ebft.worker.WorkerContext
import net.postchain.getBFTRequiredSignatureCount
import net.postchain.gtv.GtvDecoder
import net.postchain.gtx.GTXBlockchainConfigurationFactory.Companion.validateConfiguration
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.managed.ManagedBlockchainConfigurationProvider
import net.postchain.managed.PendingBlockchainConfiguration
import net.postchain.managed.config.ManagedDataSourceAware
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong

abstract class AbstractSynchronizer(
        val workerContext: WorkerContext,
        rateLimitConfiguration: RateLimitConfiguration,
        val baseBlockWitnessProviderProvider: BaseBlockWitnessProviderProvider = defaultBaseBlockWitnessProviderProvider()
) : Messaging(workerContext.engine.getBlockQueries(), workerContext.communicationManager, BlockPacker, rateLimitConfiguration) {

    companion object : KLogging()

    protected val blockchainConfiguration = workerContext.engine.getConfiguration()
    protected val configuredPeers = workerContext.peerCommConfiguration.networkNodes.getPeerIds()

    // this is used to track pending asynchronous BlockDatabase.addBlock tasks to make sure failure to commit propagates properly
    protected var addBlockCompletionFuture: CompletableFuture<Unit>? = null

    val blockHeight = AtomicLong(blockQueries.getLastBlockHeight().get())

    private var pendingConfigPromotingUsAsSigner: ByteArray? = null
    private val relevantSignersThatHaveAppliedConfig = mutableSetOf<PubKey>()

    protected val loggingContext = mapOf(
            BLOCKCHAIN_RID_TAG to workerContext.blockchainConfiguration.blockchainRid.toHex(),
            CHAIN_IID_TAG to workerContext.blockchainConfiguration.chainID.toString()
    )

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
        // For now, we rely on the height being part of the header.
        if (header !is BaseBlockHeader) {
            throw ProgrammerMistake("Expected BaseBlockHeader")
        }
        return header.blockHeaderRec.getHeight()
    }

    /**
     * We do this check to avoid getting stuck when chain is waiting for a pending config where we are promoted to signer to be applied
     * In other cases config updates will be handled when adding blocks
     */
    internal fun checkIfWeNeedToApplyPendingConfig(peer: NodeRid, incomingConfigHash: ByteArray, incomingHeight: Long): Boolean {
        if (blockchainConfiguration.chainID == 0L) return false
        val configProvider = workerContext.blockchainConfigurationProvider as? ManagedBlockchainConfigurationProvider
                ?: return false

        withLoggingContext(CHAIN_IID_TAG to blockchainConfiguration.chainID.toString()) {
            if (blockQueries.getLastBlockHeight().get() + 1 != incomingHeight) return false
            val currentConfigHash = blockchainConfiguration.configHash

            logger.debug { "blockHeight = ${blockHeight.get()}, blockQueries.getLastBlockHeight() = ${blockQueries.getLastBlockHeight().get()}, currentConfigHash = ${currentConfigHash.wrap()}" }
            logger.debug { "incomingHeight = $incomingHeight, incomingConfigHash = ${incomingConfigHash.wrap()}" }

            if (!currentConfigHash.contentEquals(incomingConfigHash)) {
                return handleNewConfig(configProvider, incomingHeight, incomingConfigHash, peer)
            } else {
                return false
            }
        }
    }

    private fun handleNewConfig(configProvider: ManagedBlockchainConfigurationProvider, incomingHeight: Long, incomingConfigHash: ByteArray, peer: NodeRid): Boolean {
        val pendingConfig = getConfigIfPending(configProvider, incomingHeight, incomingConfigHash) ?: return false

        val pendingSigners = pendingConfig.signers
        val myPubKey = PubKey(workerContext.appConfig.pubKeyByteArray)
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

            val peerPubKey = PubKey(peer)
            if (pendingSigners.contains(peerPubKey) && blockchainConfiguration.signers.any { it.contentEquals(peerPubKey.data) }) {
                relevantSignersThatHaveAppliedConfig.add(peerPubKey)
            }

            logger.debug { "Promoted nodes amount: $promotedNodesAmount, signers that have applied config: $relevantSignersThatHaveAppliedConfig" }
            // We don't care if other promoted nodes have applied the config or not, they can't build blocks with older config anyway
            if (relevantSignersThatHaveAppliedConfig.size + promotedNodesAmount >= pendingBftRequiredSignatureCount) {
                logger.debug { "Incoming config is pending with us as signer, will restart" }
                workerContext.restartNotifier.notifyRestart(pendingConfigPromotingUsAsSigner, null)
                true
            } else {
                logger.debug { "Incoming config is pending with us as signer, waiting for more nodes to apply it" }
                false
            }
        } else {
            false
        }
    }

    internal fun handleAddBlockException(exception: Throwable, block: BlockDataWithWitness, bTrace: BlockTrace?, peerStatuses: PeerStatuses, peerId: NodeRid) {
        try {
            val height = getHeight(block.header)
            when (exception) {
                is PmEngineIsAlreadyClosed, is BDBAbortException -> logger.debug { "Exception committing block height $height from peer: $peerId: ${exception.message}${bTrace?.let { ", from bTrace: $it" } ?: ""}" }
                is ConfigurationMismatchException -> {
                    if (!checkIfNewConfigurationCanBeLoaded(block)) {
                        peerStatuses.maybeBlacklist(peerId, "Received a block with mismatching config but we could not apply any new config")
                    }
                }

                is FailedConfigurationMismatchException -> {
                    // We only expect the case of us not having applied the failed config yet here
                    val incomingFailedConfigHash = block.header.getFailedConfigHash()
                    if (incomingFailedConfigHash != null) {
                        if (!checkIfConfigIsPendingAndCanBeLoaded(block, incomingFailedConfigHash)) {
                            peerStatuses.maybeBlacklist(peerId, "Received a block with failing config that we could not apply")
                        }
                    } else {
                        peerStatuses.maybeBlacklist(peerId, "Received a block without expected failed config hash")
                    }
                }

                is BadDataException -> {
                    peerStatuses.maybeBlacklist(peerId, "Received a block that could not be loaded due to: ${exception.message}")
                    logger.warn(exception) { "Exception committing block height $height from peer: $peerId: ${exception.message}${bTrace?.let { ", from bTrace: $it" } ?: ""}" }
                }

                else -> logger.error(exception) { "Exception committing block height $height from peer: $peerId: ${exception.message}${bTrace?.let { ", from bTrace: $it" } ?: ""}" }
            }
        } catch (e: Exception) {
            logger.error("Could not handle add block exception: ${e.message}", e)
        }
    }

    private fun checkIfNewConfigurationCanBeLoaded(block: BlockDataWithWitness): Boolean {
        val bcConfigProvider = workerContext.blockchainConfigurationProvider
        val bcConfig = workerContext.blockchainConfiguration
        val configCheckResult = withReadConnection(workerContext.engine.blockBuilderStorage, bcConfig.chainID) { ctx ->
            bcConfigProvider.activeBlockNeedsConfigurationChange(ctx, bcConfig.chainID, false)
        }

        withLoggingContext(CHAIN_IID_TAG to blockchainConfiguration.chainID.toString()) {
            if (configCheckResult.changeNeeded) {
                logger.warn { "Wrong config used. Chain will be restarted" }
                workerContext.restartNotifier.notifyRestart(null, null)
                return true
            }

            val headerConfigHash = block.header.getConfigHash()
            return checkIfConfigIsPendingAndCanBeLoaded(block, headerConfigHash) ||
                    checkIfChain0PreloadedConfigCanBeLoaded(block, headerConfigHash)
        }
    }

    private fun checkIfChain0PreloadedConfigCanBeLoaded(block: BlockDataWithWitness, headerConfigHash: ByteArray?): Boolean {

        val chainID = blockchainConfiguration.chainID

        val bcConfigProvider = workerContext.blockchainConfigurationProvider
        if (chainID == 0L &&
                bcConfigProvider is ManagedBlockchainConfigurationProvider &&
                workerContext.blockchainConfiguration is ManagedDataSourceAware &&
                headerConfigHash != null) {

            val height = getHeight(block.header)

            withReadConnection(workerContext.engine.blockBuilderStorage, chainID) { ctx ->
                val lastConfigHeight = bcConfigProvider.getHistoricConfigurationHeight(ctx, chainID, height)
                if (lastConfigHeight != null && lastConfigHeight != height)
                    workerContext.blockchainConfiguration.dataSource
                            .getConfiguration(blockchainConfiguration.blockchainRid.data, height)
                else null
            }?.let { config ->

                logger.info { "Found a missing preloaded management chain config on height $height" }

                val gtvConfig = GtvDecoder.decodeGtv(config)
                val configHash = BlockchainConfigurationData.merkleHash(gtvConfig)

                if (configHash.contentEquals(headerConfigHash)) {

                    return withWriteConnection(workerContext.engine.blockBuilderStorage, chainID) { ctx ->
                        try {
                            validateConfiguration(gtvConfig, blockchainConfiguration.blockchainRid, ctx)
                            DatabaseAccess.of(ctx).addConfigurationData(ctx, height, config)

                            logger.info { "A valid configuration for management chain on height $height was loaded. Chain will be restarted." }

                            workerContext.restartNotifier.notifyRestart(null, null)
                            true
                        } catch (e: Exception) {
                            logger.error("Failed to validate or load configuration on height $height: ${e.message}", e)
                            false
                        }
                    }
                } else {
                    logger.warn { "Loaded configuration with hash ${configHash.toHex()} does not match the block header config hash ${headerConfigHash.toHex()}" }
                }
            }
        }

        return false
    }

    private fun checkIfConfigIsPendingAndCanBeLoaded(block: BlockDataWithWitness, configHash: ByteArray?): Boolean {
        val bcConfigProvider = workerContext.blockchainConfigurationProvider
        if (bcConfigProvider is ManagedBlockchainConfigurationProvider && configHash != null) {
            val pendingConfig = getConfigIfPending(bcConfigProvider, getHeight(block.header), configHash)
            if (pendingConfig != null) {
                logger.debug { "Matching pending configuration detected, will validate block signature before reloading" }
                val pendingSigners = pendingConfig.signers

                val cryptoSystem = workerContext.appConfig.cryptoSystem
                val myKeyPair = KeyPair(PubKey(workerContext.appConfig.pubKeyByteArray), PrivKey(workerContext.appConfig.privKeyByteArray))
                val validator = baseBlockWitnessProviderProvider(
                        cryptoSystem,
                        cryptoSystem.buildSigMaker(myKeyPair),
                        pendingSigners.map { it.data }.toTypedArray()
                )
                val witnessBuilder = validator.createWitnessBuilderWithoutOwnSignature(block.header)

                try {
                    validator.validateWitness(block.witness, witnessBuilder)
                    logger.debug { "Witness check passed. Reloading chain with pending configuration." }
                    workerContext.restartNotifier.notifyRestart(configHash, null)
                    return true
                } catch (e: Exception) {
                    logger.error(e) { "Block signature for block with new pending config is not valid" }
                }
            }
        }
        return false
    }

    protected fun getConfigIfPending(configProvider: ManagedBlockchainConfigurationProvider, height: Long, configHash: ByteArray): PendingBlockchainConfiguration? =
            withReadConnection(workerContext.engine.blockBuilderStorage, blockchainConfiguration.chainID) { ctx ->
                configProvider.getConfigIfPending(
                        ctx,
                        blockchainConfiguration.blockchainRid,
                        height,
                        configHash
                )
            }
}

typealias BaseBlockWitnessProviderProvider = (
        cryptoSystem: CryptoSystem,
        blockSigMaker: SigMaker,
        subjects: Array<ByteArray>
) -> BaseBlockWitnessProvider

private fun defaultBaseBlockWitnessProviderProvider(): BaseBlockWitnessProviderProvider =
        { cryptoSystem, blockSigMaker, subjects ->
            BaseBlockWitnessProvider(
                    cryptoSystem,
                    blockSigMaker,
                    subjects
            )
        }

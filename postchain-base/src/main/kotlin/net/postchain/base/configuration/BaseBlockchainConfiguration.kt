// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.configuration

import mu.KLogging
import mu.withLoggingContext
import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.BaseBlockBuildingStrategyConfigurationData
import net.postchain.base.BaseBlockHeader
import net.postchain.base.BaseBlockQueries
import net.postchain.base.BaseBlockWitness
import net.postchain.base.BaseBlockchainContext
import net.postchain.base.BlockWitnessProvider
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.NullSpecialTransactionHandler
import net.postchain.base.SpecialTransactionHandler
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.data.BaseBlockStore
import net.postchain.base.data.BaseBlockWitnessProvider
import net.postchain.base.data.BaseTransactionFactory
import net.postchain.base.extension.ConfigurationHashBlockBuilderExtension
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.reflection.constructorOf
import net.postchain.core.BadConfigurationException
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainContext
import net.postchain.core.DynamicClassName
import net.postchain.core.EContext
import net.postchain.core.NODE_ID_AUTO
import net.postchain.core.NODE_ID_READ_ONLY
import net.postchain.core.Storage
import net.postchain.core.TransactionFactory
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockWitness
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.toObject
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import java.lang.reflect.InvocationTargetException
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

open class BaseBlockchainConfiguration(
        val configData: BlockchainConfigurationData,
        val cryptoSystem: CryptoSystem,
        partialContext: BlockchainContext,
        val blockSigMaker: SigMaker
) : BlockchainConfiguration {

    companion object : KLogging()

    final override val rawConfig: Gtv = configData.rawConfig
    val baseConfig: Gtv = calculateBaseConfig()
    final override val signers = configData.signers

    override val traits = setOf<String>()

    val blockStore = BaseBlockStore()
    final override val blockchainContext: BlockchainContext = BaseBlockchainContext(
            partialContext.chainID,
            partialContext.blockchainRID,
            resolveNodeID(partialContext.nodeID, partialContext.nodeRID!!),
            partialContext.nodeRID
    )
    final override val chainID = blockchainContext.chainID
    final override val blockchainRid = blockchainContext.blockchainRID
    final override val effectiveBlockchainRID = configData.historicBrid ?: blockchainContext.blockchainRID
    final override val transactionQueueSize = configData.txQueueSize.toInt()
    final override val transactionQueueRecheckInterval: Duration = configData.txQueueRecheckInterval.milliseconds

    override val configHash = GtvToBlockchainRidFactory.calculateBlockchainRid(rawConfig, ::sha256Digest).data

    private val blockBuildingStrategyConstructor = constructorOf<BlockBuildingStrategy>(
            configData.blockStrategyName,
            BaseBlockBuildingStrategyConfigurationData::class.java,
            BlockQueries::class.java,
            TransactionQueue::class.java,
            Clock::class.java
    )

    private fun resolveNodeID(nodeID: Int, subjectID: ByteArray): Int {
        return if (nodeID == NODE_ID_AUTO) {
            signers.indexOfFirst { it.contentEquals(subjectID) }
                    .let { i -> if (i == -1) NODE_ID_READ_ONLY else i }
        } else {
            nodeID
        }
    }

    protected val blockStrategyConfig = configData.blockStrategy?.toObject()
            ?: BaseBlockBuildingStrategyConfigurationData.default

    private val blockWitnessProvider: BlockWitnessProvider = BaseBlockWitnessProvider(
            cryptoSystem,
            blockSigMaker,
            signers.toTypedArray()
    )

    override val blockchainDependencies: List<BlockchainRelatedInfo> = configData.blockchainDependencies

    // Infrastructure settings
    override val syncInfrastructureName = DynamicClassName.build(configData.synchronizationInfrastructure)
    override val syncInfrastructureExtensionNames = DynamicClassName.buildList(configData.synchronizationInfrastructureExtension
            ?: listOf())

    // Only GTX config can have special TX, this is just "Base" so we settle for null
    private val specialTransactionHandler: SpecialTransactionHandler = NullSpecialTransactionHandler()

    override fun decodeBlockHeader(rawBlockHeader: ByteArray): BlockHeader {
        return BaseBlockHeader(rawBlockHeader, GtvMerkleHashCalculator(cryptoSystem))
    }

    override fun decodeWitness(rawWitness: ByteArray): BlockWitness {
        return BaseBlockWitness.fromBytes(rawWitness)
    }

    /**
     * We can get the [BlockWitnessProvider] directly from the config, don't have to go to the [BlockBuilder]
     */
    override fun getBlockHeaderValidator(): BlockWitnessProvider = blockWitnessProvider

    override fun getTransactionFactory(): TransactionFactory {
        return BaseTransactionFactory()
    }

    open fun getSpecialTxHandler(): SpecialTransactionHandler {
        return specialTransactionHandler // Must be overridden in subclass
    }

    open fun makeBBExtensions(): List<BaseBlockBuilderExtension> {
        return listOf()
    }

    override fun makeBlockBuilder(ctx: EContext, isSyncing: Boolean, extraExtensions: List<BaseBlockBuilderExtension>): BlockBuilder {
        addChainIDToDependencies(ctx) // We wait until now with this, b/c now we have an EContext

        val bb = BaseBlockBuilder(
                effectiveBlockchainRID,
                cryptoSystem,
                ctx,
                blockStore,
                getSpecialTxHandler(),
                signers.toTypedArray(),
                blockSigMaker,
                blockWitnessProvider,
                blockchainDependencies,
                makeDefaultBBExtensions() + makeBBExtensions() + extraExtensions,
                effectiveBlockchainRID != blockchainRid,
                blockStrategyConfig.maxBlockSize,
                blockStrategyConfig.maxBlockTransactions,
                blockStrategyConfig.maxSpecialEndTransactionSize,
                isSuppressSpecialTransactionValidation().also {
                    withLoggingContext(BLOCKCHAIN_RID_TAG to blockchainRid.toHex(), CHAIN_IID_TAG to chainID.toString()) {
                        logger.debug { "suppressSpecialTransactionValidation: $it" }
                    }
                },
                configData.maxBlockFutureTime
        )

        return bb
    }

    /**
     * Will add ChainID to the dependency list, if needed.
     */
    @Synchronized
    private fun addChainIDToDependencies(ctx: EContext) {
        if (blockchainDependencies.isNotEmpty()) {
            // Check if we have added ChainId's already
            val first = blockchainDependencies.first()
            if (first.chainId == null) {
                // We have to fill up the cache of ChainIDs
                for (bcInfo in blockchainDependencies) {
                    val depChainId = blockStore.getChainId(ctx, bcInfo.blockchainRid)
                    bcInfo.chainId = depChainId ?: throw BadConfigurationException(
                            "The blockchain configuration claims we depend on: $bcInfo so this BC must exist in DB"
                                    + "(Order is wrong. It must have been configured BEFORE this point in time)")
                }
            }
        }
    }

    override fun makeBlockQueries(storage: Storage): BlockQueries =
            BaseBlockQueries(cryptoSystem, storage, blockStore, chainID, blockchainContext.nodeRID!!)

    override fun hasQuery(name: String): Boolean = false

    override fun getBlockBuildingStrategy(blockQueries: BlockQueries, txQueue: TransactionQueue): BlockBuildingStrategy =
            try {
                blockBuildingStrategyConstructor.newInstance(blockStrategyConfig, blockQueries, txQueue, Clock.systemUTC())
            } catch (e: InvocationTargetException) {
                throw ProgrammerMistake("The constructor of the block building strategy given was " +
                        "unable to finish. Class name given: ${configData.blockStrategyName}, Msg: ${e.message}")
            }

    override fun initializeModules(postchainContext: PostchainContext) {}

    override fun shutdownModules() {}

    private fun makeDefaultBBExtensions(): List<BaseBlockBuilderExtension> =
            if (configData.configConsensusStrategy == ConfigConsensusStrategy.HEADER_HASH) {
                listOf(ConfigurationHashBlockBuilderExtension(configHash))
            } else listOf()

    open fun isSuppressSpecialTransactionValidation(): Boolean = false

    private fun calculateBaseConfig(): Gtv {
        val fullConfig = rawConfig.asDict().toMutableMap()
        fullConfig.remove(KEY_SIGNERS)
        return gtv(fullConfig)
    }
}

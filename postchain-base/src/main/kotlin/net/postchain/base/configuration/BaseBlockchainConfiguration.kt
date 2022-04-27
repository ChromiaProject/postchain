// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.configuration

import mu.KLogging
import net.postchain.base.*
import net.postchain.base.data.*
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.reflection.constructorOf
import net.postchain.core.*
import net.postchain.gtv.mapper.toObject

open class BaseBlockchainConfiguration(
        val configData: BlockchainConfigurationData,
) : BlockchainConfiguration {

    companion object : KLogging()

    override val blockchainContext: BlockchainContext
        get() = configData.context

    override val traits = setOf<String>()
    val cryptoSystem = SECP256K1CryptoSystem()
    val blockStore = BaseBlockStore()
    override val chainID = configData.context.chainID
    override val blockchainRid = configData.context.blockchainRID
    override val effectiveBlockchainRID = configData.historicBrid ?: configData.context.blockchainRID
    override val signers = configData.signers

    protected val blockStrategyConfig = configData.blockStrategy?.toObject() ?: BaseBlockBuildingStrategyConfigurationData.default

    private val blockWitnessProvider: BlockWitnessProvider = BaseBlockWitnessProvider(
            cryptoSystem,
            configData.blockSigMaker,
            signers.toTypedArray()
    )

    val bcRelatedInfosDependencyList: List<BlockchainRelatedInfo> = configData.blockchainDependencies

    // Infrastructure settings
    override val syncInfrastructureName = DynamicClassName.build(configData.synchronizationInfrastructure)
    override val syncInfrastructureExtensionNames = DynamicClassName.buildList(configData.synchronizationInfrastructureExtension ?: listOf())

    // Only GTX config can have special TX, this is just "Base" so we settle for null
    private val specialTransactionHandler: SpecialTransactionHandler = NullSpecialTransactionHandler()

    override fun decodeBlockHeader(rawBlockHeader: ByteArray): BlockHeader {
        return BaseBlockHeader(rawBlockHeader, cryptoSystem)
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
        return specialTransactionHandler // Must be overridden in sub-class
    }

    open fun makeBBExtensions(): List<BaseBlockBuilderExtension> {
        return listOf()
    }

    override fun makeBlockBuilder(ctx: EContext): BlockBuilder {
        addChainIDToDependencies(ctx) // We wait until now with this, b/c now we have an EContext

        val bb = BaseBlockBuilder(
                effectiveBlockchainRID,
                cryptoSystem,
                ctx,
                blockStore,
                getTransactionFactory(),
                getSpecialTxHandler(),
                signers.toTypedArray(),
                configData.blockSigMaker,
                blockWitnessProvider,
                bcRelatedInfosDependencyList,
                makeBBExtensions(),
                effectiveBlockchainRID != blockchainRid,
                blockStrategyConfig.maxBlockSize,
                blockStrategyConfig.maxBlockTransactions
        )

        return bb
    }

    /**
     * Will add ChainID to the dependency list, if needed.
     */
    @Synchronized
    private fun addChainIDToDependencies(ctx: EContext) {
        if (bcRelatedInfosDependencyList.isNotEmpty()) {
            // Check if we have added ChainId's already
            val first = bcRelatedInfosDependencyList.first()
            if (first.chainId == null) {
                // We have to fill up the cache of ChainIDs
                for (bcInfo in bcRelatedInfosDependencyList) {
                    val depChainId = blockStore.getChainId(ctx, bcInfo.blockchainRid)
                    bcInfo.chainId = depChainId ?: throw BadDataMistake(BadDataType.BAD_CONFIGURATION,
                            "The blockchain configuration claims we depend on: $bcInfo so this BC must exist in DB"
                                    + "(Order is wrong. It must have been configured BEFORE this point in time)")
                }
            }
        }
    }

    override fun makeBlockQueries(storage: Storage): BlockQueries {
        return BaseBlockQueries(
                this, storage, blockStore, chainID, configData.context.nodeRID!!)
    }

    override fun initializeDB(ctx: EContext) {
        DependenciesValidator.validateBlockchainRids(ctx, bcRelatedInfosDependencyList)
    }

    override fun getBlockBuildingStrategy(blockQueries: BlockQueries, txQueue: TransactionQueue): BlockBuildingStrategy {
        val strategyClassName = configData.blockStrategyName
        return try {
            constructorOf<BlockBuildingStrategy>(
                    strategyClassName,
                    BaseBlockBuildingStrategyConfigurationData::class.java,
                    BlockQueries::class.java,
                    TransactionQueue::class.java
            ).newInstance(blockStrategyConfig, blockQueries, txQueue)
        } catch (e: UserMistake) {
            throw UserMistake("The block building strategy in the configuration is invalid, " +
                    "Class name given: $strategyClassName.")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw ProgrammerMistake("The constructor of the block building strategy given was " +
                    "unable to finish. Class name given: $strategyClassName, Msg: ${e.message}")
        }
    }
}

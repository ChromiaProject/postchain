// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import net.postchain.base.*
import net.postchain.base.icmf.IcmfController
import net.postchain.core.*

open class BaseBlockchainConfiguration(
        val configData: BaseBlockchainConfigurationData,
        val controller: IcmfController? = null // Only some chains (like anchoring) will have a pump station.
        ): BlockchainConfiguration {

    override val traits = setOf<String>()
    val cryptoSystem = SECP256K1CryptoSystem()
    val blockStore = BaseBlockStore()
    override val chainID = configData.context.chainID
    override val blockchainRid = configData.context.blockchainRID
    override val effectiveBlockchainRID = configData.getHistoricBRID() ?: configData.context.blockchainRID
    val signers = configData.getSigners()

    // Future work: make validation configurable (i.e. create the validator from config setting),
    // Currently we can only use "base"
    private val blockHeaderValidator: BlockHeaderValidator = BaseBlockHeaderValidator(
        cryptoSystem,
        configData.blockSigMaker,
        signers.toTypedArray()
    )

    val bcRelatedInfosDependencyList: List<BlockchainRelatedInfo> = configData.getDependenciesAsList()

    // Infrastructure settings
    override val syncInfrastructureName = DynamicClassName.build(configData.getSyncInfrastructureName())
    override val syncInfrastructureExtensionNames = DynamicClassName.buildList(configData.getSyncInfrastructureExtensions())

    override val icmfListener = configData.getIcmfListener()

    // Only GTX config can have special TX, this is just "Base" so we settle for null
    private val specialTransactionHandler: SpecialTransactionHandler = NullSpecialTransactionHandler()

    val componentMap: Map<String, Any> = configData.getComponentMap() // Used for things that might or might not exist


    override fun decodeBlockHeader(rawBlockHeader: ByteArray): BlockHeader {
        return BaseBlockHeader(rawBlockHeader, cryptoSystem)
    }

    override fun decodeWitness(rawWitness: ByteArray): BlockWitness {
        return BaseBlockWitness.fromBytes(rawWitness)
    }

    /**
     * We can get the [BlockHeaderValidator] directly from the config, don't have to go to the [BlockBuilder]
     */
    override fun getBlockHeaderValidator(): BlockHeaderValidator = blockHeaderValidator

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
            blockHeaderValidator,
            bcRelatedInfosDependencyList,
            makeBBExtensions(),
            effectiveBlockchainRID != blockchainRid,
            configData.getMaxBlockSize(),
            configData.getMaxBlockTransactions())

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
                this, storage, blockStore, chainID, configData.subjectID)
    }

    override fun initializeDB(ctx: EContext) {
        DependenciesValidator.validateBlockchainRids(ctx, bcRelatedInfosDependencyList)
    }

    override fun getBlockBuildingStrategy(blockQueries: BlockQueries, txQueue: TransactionQueue): BlockBuildingStrategy {
        val strategyClassName = configData.getBlockBuildingStrategyName()
        if (strategyClassName == "") {
            return BaseBlockBuildingStrategy(configData, this, blockQueries, txQueue)
        }
        val strategyClass = Class.forName(strategyClassName)

        val ctor = strategyClass.getConstructor(
                BaseBlockchainConfigurationData::class.java,
                BlockchainConfiguration::class.java,
                BlockQueries::class.java,
                TransactionQueue::class.java)

        return ctor.newInstance(configData, this, blockQueries, txQueue) as BlockBuildingStrategy
    }

    /**
     * It's not crystal clear when something is important enough to get a typed "getX()" method in the config, or if it
     * should be hidden behind this generic getter. If you suspect your setting is unusual, put it as a component.
     *
     * @param name is the identifier for the component we need
     * @return a component if found
     */
    override fun <T> getComponent(name: String): T? {
        val obj: Any? = componentMap[name]
        return if (obj != null) {
            obj as T
        } else {
            null
        }
    }
}


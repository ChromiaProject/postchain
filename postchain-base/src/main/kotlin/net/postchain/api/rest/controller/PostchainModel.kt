// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.api.rest.BlockHeight
import net.postchain.api.rest.BlockSignature
import net.postchain.api.rest.BlockchainNodeState
import net.postchain.api.rest.InfraVersion
import net.postchain.api.rest.TransactionsCount
import net.postchain.api.rest.Version
import net.postchain.api.rest.controller.RestApi.Companion.REST_API_VERSION
import net.postchain.api.rest.model.ApiMetadata
import net.postchain.api.rest.model.ApiRejectedTransaction
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.OperationMetadata
import net.postchain.api.rest.model.QueryMetadata
import net.postchain.api.rest.model.TxRid
import net.postchain.base.BaseBlockchainContext
import net.postchain.base.ConfirmationProof
import net.postchain.base.configuration.BaseBlockchainConfiguration
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.configuration.KEY_SIGNERS
import net.postchain.base.data.BaseBlockWitnessProvider
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DependenciesValidator
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.common.exception.UserMistake
import net.postchain.common.reflection.newInstanceOf
import net.postchain.common.types.WrappedByteArray
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.core.AsyncQueryQueue
import net.postchain.core.AsyncQueryResponse
import net.postchain.core.BlockRid
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.DefaultBlockchainConfigurationFactory
import net.postchain.core.NODE_ID_AUTO
import net.postchain.core.Storage
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TransactionInfoExtsTruncated
import net.postchain.core.block.BlockDetail
import net.postchain.core.block.BlockDetailsTruncated
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockQueryHeightFilter
import net.postchain.core.block.BlockQueryTimeFilter
import net.postchain.core.block.MultiSigBlockWitnessBuilder
import net.postchain.crypto.PubKey
import net.postchain.crypto.SigMaker
import net.postchain.debug.DiagnosticData
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DpBlockchainNodeState
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.rest.contract.StateNodeStatus
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.mapper.toObject
import net.postchain.gtv.merkleHash
import net.postchain.gtx.CompositeGTXModule
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GTXModuleAware
import net.postchain.gtx.GtxQuery
import net.postchain.gtx.MetadataProvider
import net.postchain.gtx.UnknownQuery
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.logging.FAILURE_RESULT
import net.postchain.logging.QUERY_NAME_TAG
import net.postchain.logging.RESULT_TAG
import net.postchain.logging.SUCCESS_RESULT
import net.postchain.managed.CHAIN0
import net.postchain.managed.config.Chain0BlockchainConfigurationFactory
import net.postchain.managed.config.DappBlockchainConfigurationFactory
import net.postchain.managed.config.ManagedBlockchainConfiguration
import net.postchain.managed.config.ManagedDataSourceAware
import net.postchain.metrics.PostchainModelMetrics
import net.postchain.metrics.QUERIES_METRIC_DESCRIPTION
import net.postchain.metrics.QUERIES_METRIC_NAME
import java.time.Instant

open class PostchainModel(
        val blockchainConfiguration: BlockchainConfiguration,
        val blockQueries: BlockQueries,
        final override val blockchainRid: BlockchainRid,
        val storage: Storage,
        val postchainContext: PostchainContext,
        private val nodeDiagnosticContext: NodeDiagnosticContext,
        private val diagnosticData: DiagnosticData,
        override val queryCacheTtlSeconds: Long,
        private val asyncQueryQueue: AsyncQueryQueue,
) : Model {

    companion object : KLogging()

    final override val chainIID = blockchainConfiguration.chainID
    protected val metrics = PostchainModelMetrics(chainIID, blockchainRid)

    private val currentRawConfiguration = GtvEncoder.encodeGtv(blockchainConfiguration.rawConfig)

    override var live = true

    override fun postTransaction(tx: ByteArray): Unit = throw NotSupported("Posting a transaction to this blockchain on this node is not supported")

    override fun getTransaction(txRID: TxRid): ByteArray? = blockQueries.getTransactionRawData(txRID.bytes).get()

    override fun getTransactionInfo(txRID: TxRid, includeTxData: Boolean): TransactionInfoExt? =
            blockQueries.getTransactionInfo(txRID.bytes, includeTxData).get()

    override fun getTransactionsInfo(timeFilter: BlockQueryTimeFilter, limit: Int, maxDataSize: Int): TransactionInfoExtsTruncated =
            blockQueries.getTransactionsInfo(timeFilter, limit, maxDataSize).get()

    override fun getTransactionsInfoBySigner(timeFilter: BlockQueryTimeFilter, limit: Int, signer: PubKey, maxDataSize: Int): TransactionInfoExtsTruncated =
            blockQueries.getTransactionsInfoBySigner(timeFilter, limit, signer, maxDataSize).get()

    override fun getLastTransactionNumber(): TransactionsCount =
            TransactionsCount(blockQueries.getLastTransactionNumber().get())

    override fun getBlocksBetweenTimes(timeFilter: BlockQueryTimeFilter, limit: Int, txHashesOnly: Boolean, maxDataSize: Int, excludeEmpty: Boolean): BlockDetailsTruncated =
            blockQueries.getBlocksBetweenTimes(timeFilter, limit, txHashesOnly, maxDataSize, excludeEmpty).get()

    override fun getBlocksBetweenHeights(heightFilter: BlockQueryHeightFilter, limit: Int, txHashesOnly: Boolean, maxDataSize: Int, excludeEmpty: Boolean): BlockDetailsTruncated =
            blockQueries.getBlocksBetweenHeights(heightFilter, limit, txHashesOnly, maxDataSize, excludeEmpty).get()

    override fun getBlock(blockRID: BlockRid, txHashesOnly: Boolean): BlockDetail? =
            blockQueries.getBlock(blockRID.data, txHashesOnly).get()

    override fun getBlock(height: Long, txHashesOnly: Boolean): BlockDetail? {
        val blockRid = blockQueries.getBlockRid(height).get()
        return blockRid?.let { getBlock(BlockRid(it), txHashesOnly) }
    }

    override fun confirmBlock(blockRID: BlockRid): BlockSignature? {
        return blockQueries.getBlock(blockRID.data, true).get()?.let {
            val witnessProvider = BaseBlockWitnessProvider(
                    postchainContext.cryptoSystem,
                    getBlockSigMaker(),
                    blockchainConfiguration.signers.toTypedArray()
            )
            val witnessBuilder = witnessProvider.createWitnessBuilderWithOwnSignature(blockRID) as MultiSigBlockWitnessBuilder
            BlockSignature.fromSignature(witnessBuilder.getMySignature())
        }
    }

    override fun getBlockSigMaker(): SigMaker = when (blockchainConfiguration) {
        is BaseBlockchainConfiguration -> blockchainConfiguration.blockSigMaker
        is ManagedBlockchainConfiguration -> blockchainConfiguration.configuration.blockSigMaker
        else -> throw UserMistake("Unknown blockchain configuration detected: " + blockchainConfiguration.javaClass.simpleName)
    }

    override fun getConfirmationProof(txRID: TxRid): ConfirmationProof? =
            blockQueries.getConfirmationProof(txRID.bytes).get()

    override fun getStatus(txRID: TxRid): ApiStatus =
            throw NotSupported("Checking transaction status is not supported for this blockchain on this node")

    override fun getWaitingTransactions(): List<TxRid> =
            throw NotSupported("Fetching waiting transactions is not supported for this blockchain on this node")

    override fun getWaitingTransaction(txRID: TxRid): Pair<ByteArray, Instant>? =
            throw NotSupported("Fetching waiting transaction is not supported for this blockchain on this node")

    override fun getRejectedTransactions(): List<ApiRejectedTransaction> =
            throw NotSupported("Fetching rejected transactions is not supported for this blockchain on this node")

    override fun checkQueryCorrectness(query: GtxQuery) {
        if (!blockchainConfiguration.hasQuery(query.name)) {
            throw UnknownQuery(query.name)
        }
    }

    override fun query(query: GtxQuery): Gtv = queryInternal(query, withHeight = false).first

    override fun queryWithHeight(query: GtxQuery): Pair<Gtv, Long> = queryInternal(query, withHeight = true)

    private fun queryInternal(query: GtxQuery, withHeight: Boolean): Pair<Gtv, Long> {
        val timerBuilder = Timer.builder(QUERIES_METRIC_NAME)
                .description(QUERIES_METRIC_DESCRIPTION)
                .tag(CHAIN_IID_TAG, chainIID.toString())
                .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
                .tag(QUERY_NAME_TAG, query.name)
        val sample = Timer.start(Metrics.globalRegistry)
        return try {
            val (result, height) = if (withHeight) {
                blockQueries.queryWithHeight(query.name, query.args).get()
            } else {
                blockQueries.query(query.name, query.args).get() to -1L
            }
            sample.stop(timerBuilder
                    .tag(RESULT_TAG, SUCCESS_RESULT)
                    .register(Metrics.globalRegistry))
            result to height
        } catch (e: UnknownQuery) {
            // do not add metrics for unknown queries to avoid blowing up the QUERY_NAME_TAG dimension
            throw e
        } catch (e: Exception) {
            sample.stop(timerBuilder
                    .tag(RESULT_TAG, FAILURE_RESULT)
                    .register(Metrics.globalRegistry))
            throw e
        }
    }

    override fun enqueueQuery(query: GtxQuery) {
        checkQueryCorrectness(query)
        val queryRid = query.toGtv().merkleHash(blockchainConfiguration.merkleHashCalculator).wrap()
        asyncQueryQueue.enqueueQuery(queryRid, query)
    }

    override fun fetchQueryResponse(queryRid: WrappedByteArray): AsyncQueryResponse = asyncQueryQueue.getQueryResponse(queryRid)

    override fun nodeStatusQuery(): StateNodeStatus =
            diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_STATUS]?.value as? StateNodeStatus
                    ?: throw NotFoundError("NotFound")

    @Suppress("UNCHECKED_CAST")
    override fun nodePeersStatusQuery(): List<StateNodeStatus> =
            diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_PEERS_STATUSES]?.value as? List<StateNodeStatus>
                    ?: throw NotFoundError("NotFound")

    override fun getCurrentBlockHeight(): BlockHeight = BlockHeight(blockQueries.getLastBlockHeight().get() + 1)

    override fun getBlockchainNodeState(): BlockchainNodeState {
        val nodeState = diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_STATE]?.value as? DpBlockchainNodeState
                ?: throw NotFoundError("NotFound")
        return BlockchainNodeState(nodeState.name)
    }

    override fun getBlockchainConfiguration(height: Long): ByteArray? = withReadConnection(storage, chainIID) { ctx ->
        if (height < 0) {
            currentRawConfiguration
        } else {
            postchainContext.configurationProvider.getHistoricConfiguration(ctx, chainIID, height)
        }
    }

    override fun validateBlockchainConfiguration(configuration: Gtv) {
        val fixedConfiguration = if (configuration[KEY_SIGNERS] == null) {
            GtvDictionary.build(configuration.asDict() + (KEY_SIGNERS to GtvArray(emptyArray())))
        } else {
            configuration
        }
        val blockConfData = fixedConfiguration.toObject<BlockchainConfigurationData>()
        withWriteConnection(storage, chainIID) { eContext ->
            val blockchainRid = DatabaseAccess.of(eContext).getBlockchainRid(eContext)!!
            val partialContext = BaseBlockchainContext(chainIID, blockchainRid, NODE_ID_AUTO, postchainContext.appConfig.pubKeyByteArray)
            val factory = if (blockchainConfiguration is ManagedDataSourceAware) {
                val factory = newInstanceOf<GTXBlockchainConfigurationFactory>(blockConfData.configurationFactory)
                if (chainIID == CHAIN0) {
                    Chain0BlockchainConfigurationFactory(factory, postchainContext.appConfig, storage)
                } else {
                    DappBlockchainConfigurationFactory(factory, blockchainConfiguration.dataSource)
                }
            } else {
                DefaultBlockchainConfigurationFactory().supply(blockConfData.configurationFactory)
            }

            val blockSigMaker: SigMaker = object : SigMaker {
                override val id: String
                    get() = throw NotImplementedError("SigMaker")

                override fun signMessage(msg: ByteArray) = throw NotImplementedError("SigMaker")
                override fun signDigest(digest: Hash) = throw NotImplementedError("SigMaker")
            }
            val config = factory.makeBlockchainConfiguration(blockConfData, partialContext, blockSigMaker, eContext, postchainContext.cryptoSystem)
            DependenciesValidator.validateBlockchainRids(eContext, config.blockchainDependencies)
            config.initializeModules(postchainContext)
            try {
                GTXBlockchainConfigurationFactory.extraConfigurationValidation(blockConfData, eContext)
            } finally {
                config.shutdownModules()
            }

            false
        }
    }

    override fun getNextBlockchainConfigurationHeight(height: Long): BlockHeight? = withReadConnection(storage, chainIID) { ctx ->
        DatabaseAccess.of(ctx).findNextConfigurationHeight(ctx, height)?.let { BlockHeight(it) }
    }

    override fun getVersion(): Version = Version(REST_API_VERSION)

    override fun getInfrastructureVersion(): InfraVersion =
            InfraVersion(
                    postchain = nodeDiagnosticContext[DiagnosticProperty.VERSION]?.value?.toString().orEmpty(),
                    infrastructure = nodeDiagnosticContext[DiagnosticProperty.INFRASTRUCTURE_NAME]?.value?.toString().orEmpty(),
                    infrastructureVersion = nodeDiagnosticContext[DiagnosticProperty.INFRASTRUCTURE_VERSION]?.value?.toString().orEmpty(),
                    restApi = REST_API_VERSION.toString(),
                    databaseServerVersion = nodeDiagnosticContext[DiagnosticProperty.DATABASE_SERVER_VERSION]?.value?.toString().orEmpty()
            )

    override fun toString(): String = "${this.javaClass.simpleName}(chainId=$chainIID)"

    override fun getMetadata(): ApiMetadata {
        if (blockchainConfiguration !is GTXModuleAware) {
            logger.debug { "Blockchain configuration is not GTXModuleAware, returning empty metadata" }
            return ApiMetadata(mapOf(), mapOf())
        }

        val module = blockchainConfiguration.module
        if (module !is MetadataProvider) {
            logger.debug { "Blockchain configuration does not provide metadata, returning empty metadata" }
            return ApiMetadata(mapOf(), mapOf())
        }

        if (module is CompositeGTXModule) return module.getCompositeMetadata()

        val moduleName = module.javaClass.canonicalName
        return ApiMetadata(
                module.getMetadata().operations.mapValues { OperationMetadata(gtxModule = moduleName, it.value.args) },
                module.getMetadata().queries.mapValues { QueryMetadata(gtxModule = moduleName, it.value.args, it.value.returnType) }
        )
    }
}

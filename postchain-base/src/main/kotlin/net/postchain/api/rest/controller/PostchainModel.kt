// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.api.rest.BlockHeight
import net.postchain.api.rest.BlockSignature
import net.postchain.api.rest.BlockchainNodeState
import net.postchain.api.rest.TransactionsCount
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.TxRid
import net.postchain.base.BaseBlockHeader
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
import net.postchain.common.tx.TransactionStatus.CONFIRMED
import net.postchain.common.tx.TransactionStatus.REJECTED
import net.postchain.common.tx.TransactionStatus.UNKNOWN
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.core.BlockRid
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.DefaultBlockchainConfigurationFactory
import net.postchain.core.NODE_ID_AUTO
import net.postchain.core.Storage
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockDetail
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.MultiSigBlockWitnessBuilder
import net.postchain.crypto.PubKey
import net.postchain.crypto.SigMaker
import net.postchain.debug.DiagnosticData
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DpBlockchainNodeState
import net.postchain.ebft.rest.contract.StateNodeStatus
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.mapper.toObject
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtx.GtxQuery
import net.postchain.gtx.UnknownQuery
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.logging.FAILURE_RESULT
import net.postchain.logging.QUERY_NAME_TAG
import net.postchain.logging.RESULT_TAG
import net.postchain.logging.SUCCESS_RESULT
import net.postchain.metrics.PostchainModelMetrics
import net.postchain.metrics.QUERIES_METRIC_DESCRIPTION
import net.postchain.metrics.QUERIES_METRIC_NAME

open class PostchainModel(
        val blockchainConfiguration: BlockchainConfiguration,
        val txQueue: TransactionQueue,
        val blockQueries: BlockQueries,
        final override val blockchainRid: BlockchainRid,
        val storage: Storage,
        val postchainContext: PostchainContext,
        private val diagnosticData: DiagnosticData,
        override val queryCacheTtlSeconds: Long
) : Model {

    companion object : KLogging()

    final override val chainIID = blockchainConfiguration.chainID
    protected val metrics = PostchainModelMetrics(chainIID, blockchainRid)

    private val currentRawConfiguration = GtvEncoder.encodeGtv(blockchainConfiguration.rawConfig)

    override var live = true

    override fun postTransaction(tx: ByteArray): Unit = throw NotSupported("Posting a transaction on a non-signer node is not supported.")

    override fun getTransaction(txRID: TxRid): ByteArray? = blockQueries.getTransactionRawData(txRID.bytes).get()

    override fun getTransactionInfo(txRID: TxRid): TransactionInfoExt? = blockQueries.getTransactionInfo(txRID.bytes).get()

    override fun getTransactionsInfo(beforeTime: Long, limit: Int): List<TransactionInfoExt> =
            blockQueries.getTransactionsInfo(beforeTime, limit).get()

    override fun getTransactionsInfoBySigner(beforeTime: Long, limit: Int, signer: PubKey): List<TransactionInfoExt> =
            blockQueries.getTransactionsInfoBySigner(beforeTime, limit, signer).get()

    override fun getLastTransactionNumber(): TransactionsCount =
            TransactionsCount(blockQueries.getLastTransactionNumber().get())

    override fun getBlocks(beforeTime: Long, limit: Int, txHashesOnly: Boolean): List<BlockDetail> =
            blockQueries.getBlocks(beforeTime, limit, txHashesOnly).get()

    override fun getBlocksBeforeHeight(beforeHeight: Long, limit: Int, txHashesOnly: Boolean): List<BlockDetail> =
            blockQueries.getBlocksBeforeHeight(beforeHeight, limit, txHashesOnly).get()

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
                    (blockchainConfiguration as BaseBlockchainConfiguration).blockSigMaker,
                    blockchainConfiguration.signers.toTypedArray()
            )
            val blockHeader = BaseBlockHeader(it.header, GtvMerkleHashCalculator(postchainContext.cryptoSystem))
            val witnessBuilder = witnessProvider.createWitnessBuilderWithOwnSignature(blockHeader) as MultiSigBlockWitnessBuilder
            BlockSignature.fromSignature(witnessBuilder.getMySignature())
        }
    }

    override fun getConfirmationProof(txRID: TxRid): ConfirmationProof? =
            blockQueries.getConfirmationProof(txRID.bytes).get()

    override fun getStatus(txRID: TxRid): ApiStatus {
        var status = txQueue.getTransactionStatus(txRID.bytes)

        if (status == UNKNOWN) {
            status = if (blockQueries.isTransactionConfirmed(txRID.bytes).get())
                CONFIRMED else UNKNOWN
        }

        return if (status == REJECTED) {
            val exception = txQueue.getRejectionReason(txRID.bytes.wrap())
            ApiStatus(status, exception?.message)
        } else {
            ApiStatus(status)
        }
    }

    override fun query(query: GtxQuery): Gtv {
        val timerBuilder = Timer.builder(QUERIES_METRIC_NAME)
                .description(QUERIES_METRIC_DESCRIPTION)
                .tag(CHAIN_IID_TAG, chainIID.toString())
                .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
                .tag(QUERY_NAME_TAG, query.name)
        val sample = Timer.start(Metrics.globalRegistry)
        return try {
            val result = blockQueries.query(query.name, query.args).get()
            sample.stop(timerBuilder
                    .tag(RESULT_TAG, SUCCESS_RESULT)
                    .register(Metrics.globalRegistry))
            result
        } catch (e: UnknownQuery) {
            // do not add metrics for unknown queries to avoid blowing up QUERY_NAME_TAG dimension
            throw e
        } catch (e: Exception) {
            sample.stop(timerBuilder
                    .tag(RESULT_TAG, FAILURE_RESULT)
                    .register(Metrics.globalRegistry))
            throw e
        }
    }

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
            val factory = DefaultBlockchainConfigurationFactory().supply(blockConfData.configurationFactory)
            val blockSigMaker: SigMaker = object : SigMaker {
                override fun signMessage(msg: ByteArray) = throw NotImplementedError("SigMaker")
                override fun signDigest(digest: Hash) = throw NotImplementedError("SigMaker")
            }
            val config = factory.makeBlockchainConfiguration(blockConfData, partialContext, blockSigMaker, eContext, postchainContext.cryptoSystem)
            DependenciesValidator.validateBlockchainRids(eContext, config.blockchainDependencies)
            config.initializeModules(postchainContext)

            false
        }
    }

    override fun toString(): String = "${this.javaClass.simpleName}(chainId=$chainIID)"
}

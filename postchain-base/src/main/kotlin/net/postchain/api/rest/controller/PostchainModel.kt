// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.ApiTx
import net.postchain.api.rest.model.TxRID
import net.postchain.base.BaseBlockQueries
import net.postchain.base.BaseBlockchainContext
import net.postchain.base.ConfirmationProof
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DependenciesValidator
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.data.Hash
import net.postchain.common.toHex
import net.postchain.common.tx.TransactionStatus.CONFIRMED
import net.postchain.common.tx.TransactionStatus.REJECTED
import net.postchain.common.tx.TransactionStatus.UNKNOWN
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.core.DefaultBlockchainConfigurationFactory
import net.postchain.core.NODE_ID_AUTO
import net.postchain.core.Storage
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockDetail
import net.postchain.crypto.SigMaker
import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.GtxQuery

open class PostchainModel(
        final override val chainIID: Long,
        val txQueue: TransactionQueue,
        val blockQueries: BaseBlockQueries,
        private val debugInfoQuery: DebugInfoQuery,
        val storage: Storage,
        val postchainContext: PostchainContext
) : Model {

    companion object : KLogging()

    override var live = true

    override fun postTransaction(tx: ApiTx): Unit = throw NotSupported("NotSupported: Posting a transaction on a non-signer node is not supported.")

    override fun getTransaction(txRID: TxRID): ApiTx? {
        return blockQueries.getTransaction(txRID.bytes).get()
                .takeIf { it != null }
                ?.let { ApiTx(it.getRawData().toHex()) }
    }

    override fun getTransactionInfo(txRID: TxRID): TransactionInfoExt? {
        return blockQueries.getTransactionInfo(txRID.bytes).get()
    }

    override fun getTransactionsInfo(beforeTime: Long, limit: Int): List<TransactionInfoExt> {
        return blockQueries.getTransactionsInfo(beforeTime, limit).get()
    }

    override fun getBlocks(beforeTime: Long, limit: Int, txHashesOnly: Boolean): List<BlockDetail> {
        return blockQueries.getBlocks(beforeTime, limit, txHashesOnly).get()
    }

    override fun getBlocksBeforeHeight(beforeHeight: Long, limit: Int, txHashesOnly: Boolean): List<BlockDetail> {
        return blockQueries.getBlocksBeforeHeight(beforeHeight, limit, txHashesOnly).get()
    }

    override fun getBlock(blockRID: ByteArray, txHashesOnly: Boolean): BlockDetail? {
        return blockQueries.getBlock(blockRID, txHashesOnly).get()
    }

    override fun getBlock(height: Long, txHashesOnly: Boolean): BlockDetail? {
        val blockRid = blockQueries.getBlockRid(height).get()
        return blockRid?.let { getBlock(it, txHashesOnly) }
    }

    override fun getConfirmationProof(txRID: TxRID): ConfirmationProof? {
        return blockQueries.getConfirmationProof(txRID.bytes).get()
    }

    override fun getStatus(txRID: TxRID): ApiStatus {
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
        return blockQueries.query(query.name, query.args).get()
    }

    override fun nodeQuery(subQuery: String): String = throw NotSupported("NotSupported: $subQuery")

    override fun debugQuery(subQuery: String?): String {
        return debugInfoQuery.queryDebugInfo(subQuery)
    }

    override fun getCurrentBlockHeight(): BlockHeight {
        return BlockHeight(blockQueries.getBestHeight().get() + 1)
    }

    override fun getBlockchainConfiguration(height: Long): ByteArray? = withReadConnection(storage, chainIID) { ctx ->
        val db = DatabaseAccess.of(ctx)
        if (height < 0) {
            db.getConfigurationDataForHeight(ctx, db.getLastBlockHeight(ctx))
        } else {
            db.getConfigurationData(ctx, height)
        }
    }

    override fun validateBlockchainConfiguration(configuration: Gtv) {
        val blockConfData = configuration.toObject<BlockchainConfigurationData>()
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

    override fun toString(): String {
        return "${this.javaClass.simpleName}(chainId=$chainIID)"
    }
}

// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.rest.model

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import net.postchain.PostchainContext
import net.postchain.api.rest.controller.BlockHeight
import net.postchain.api.rest.controller.DebugInfoQuery
import net.postchain.api.rest.controller.DuplicateTnxException
import net.postchain.api.rest.controller.InvalidTnxException
import net.postchain.api.rest.controller.NotFoundError
import net.postchain.api.rest.controller.NotSupported
import net.postchain.api.rest.controller.PostchainModel
import net.postchain.api.rest.controller.UnavailableException
import net.postchain.api.rest.json.JsonFactory
import net.postchain.api.rest.model.ApiTx
import net.postchain.base.BaseBlockQueries
import net.postchain.common.BlockchainRid
import net.postchain.common.tx.EnqueueTransactionResult
import net.postchain.concurrent.util.get
import net.postchain.core.Storage
import net.postchain.core.TransactionFactory
import net.postchain.core.TransactionQueue
import net.postchain.ebft.NodeStateTracker
import net.postchain.ebft.rest.contract.serialize
import net.postchain.metrics.PostchainModelMetrics

class PostchainEBFTModel(
        chainIID: Long,
        private val nodeStateTracker: NodeStateTracker,
        txQueue: TransactionQueue,
        private val transactionFactory: TransactionFactory,
        blockQueries: BaseBlockQueries,
        debugInfoQuery: DebugInfoQuery,
        blockchainRid: BlockchainRid,
        storage: Storage,
        postchainContext: PostchainContext
) : PostchainModel(chainIID, txQueue, blockQueries, debugInfoQuery, storage, postchainContext) {

    private val metrics = PostchainModelMetrics(chainIID, blockchainRid)

    override fun postTransaction(tx: ApiTx) {
        val sample = Timer.start(Metrics.globalRegistry)

        val decodedTransaction = transactionFactory.decodeTransaction(tx.bytes)

        decodedTransaction.checkCorrectness()

        if (blockQueries.isTransactionConfirmed(decodedTransaction.getRID()).get()) {
            sample.stop(metrics.duplicateTransactions)
            throw DuplicateTnxException("Transaction already in database")
        }

        when (txQueue.enqueue(decodedTransaction)) {
            EnqueueTransactionResult.FULL -> {
                sample.stop(metrics.fullTransactions)
                throw UnavailableException("Transaction queue is full")
            }

            EnqueueTransactionResult.INVALID -> {
                sample.stop(metrics.invalidTransactions)
                throw InvalidTnxException("Transaction is invalid")
            }

            EnqueueTransactionResult.DUPLICATE -> {
                sample.stop(metrics.duplicateTransactions)
                throw DuplicateTnxException("Transaction already in queue")
            }

            EnqueueTransactionResult.OK -> {
                sample.stop(metrics.okTransactions)
            }
        }
    }

    override fun nodeQuery(subQuery: String): String {
        val json = JsonFactory.makeJson()
        return when (subQuery) {
            "height" -> json.toJson(BlockHeight(nodeStateTracker.blockHeight))
            "my_status" -> nodeStateTracker.myStatus?.serialize() ?: throw NotFoundError("NotFound")
            "statuses" -> nodeStateTracker.nodeStatuses?.joinToString(separator = ",", prefix = "[", postfix = "]") { it.serialize() }
                    ?: throw NotFoundError("NotFound")

            else -> throw NotSupported("NotSupported: $subQuery")
        }
    }
}

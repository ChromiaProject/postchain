// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.rest.model

import net.postchain.api.rest.controller.BlockHeight
import net.postchain.api.rest.controller.DebugInfoQuery
import net.postchain.api.rest.controller.NotFoundError
import net.postchain.api.rest.controller.NotSupported
import net.postchain.api.rest.controller.PostchainModel
import net.postchain.api.rest.json.JsonFactory
import net.postchain.base.BaseBlockQueries
import net.postchain.common.BlockchainRid
import net.postchain.core.Storage
import net.postchain.core.TransactionFactory
import net.postchain.core.TransactionQueue
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.NodeStateTracker
import net.postchain.ebft.rest.contract.serialize

class PostchainEBFTModel(
        chainIID: Long,
        private val nodeStateTracker: NodeStateTracker,
        txQueue: TransactionQueue,
        transactionFactory: TransactionFactory,
        blockQueries: BaseBlockQueries,
        debugInfoQuery: DebugInfoQuery,
        private val blockchainRid: BlockchainRid,
        storage: Storage,
        private val nodeDiagnosticContext: NodeDiagnosticContext
) : PostchainModel(chainIID, txQueue, transactionFactory, blockQueries, debugInfoQuery, blockchainRid, storage) {

    override fun nodeQuery(subQuery: String): String {
        val json = JsonFactory.makeJson()
        return when (subQuery) {
            "height" -> json.toJson(BlockHeight(nodeStateTracker.blockHeight))
            "my_status" -> nodeStateTracker.myStatus?.serialize(nodeDiagnosticContext.blockchainErrorQueue(blockchainRid)) ?: throw NotFoundError("NotFound")
            "statuses" -> nodeStateTracker.nodeStatuses?.joinToString(separator = ",", prefix = "[", postfix = "]") { it.serialize(nodeDiagnosticContext.blockchainErrorQueue(blockchainRid)) }
                    ?: throw NotFoundError("NotFound")

            else -> throw NotSupported("NotSupported: $subQuery")
        }
    }
}

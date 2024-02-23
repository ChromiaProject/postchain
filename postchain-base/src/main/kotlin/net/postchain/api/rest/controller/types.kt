// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import net.postchain.api.rest.BlockHeight
import net.postchain.api.rest.BlockSignature
import net.postchain.api.rest.BlockchainNodeState
import net.postchain.api.rest.TransactionsCount
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.TxRid
import net.postchain.base.ConfirmationProof
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockRid
import net.postchain.core.TransactionInfoExt
import net.postchain.core.block.BlockDetail
import net.postchain.crypto.PubKey
import net.postchain.crypto.Signature
import net.postchain.ebft.rest.contract.StateNodeStatus
import net.postchain.gtv.Gtv
import net.postchain.gtx.GtxQuery
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response

sealed interface ChainModel {
    val chainIID: Long
    var live: Boolean
}

interface ExternalModel : ChainModel, HttpHandler {
    val path: String
    override fun invoke(request: Request): Response
}

interface Model : ChainModel {
    val blockchainRid: BlockchainRid
    val queryCacheTtlSeconds: Long

    fun postTransaction(tx: ByteArray)
    fun getTransaction(txRID: TxRid): ByteArray?
    fun getTransactionInfo(txRID: TxRid): TransactionInfoExt?
    fun getTransactionsInfo(beforeTime: Long, limit: Int): List<TransactionInfoExt>
    fun getTransactionsInfoBySigner(beforeTime: Long, limit: Int, signer: PubKey): List<TransactionInfoExt>
    fun getLastTransactionNumber(): TransactionsCount
    fun getBlock(blockRID: BlockRid, txHashesOnly: Boolean): BlockDetail?
    fun getBlock(height: Long, txHashesOnly: Boolean): BlockDetail?
    fun confirmBlock(blockRID: BlockRid): BlockSignature?
    fun getBlocks(beforeTime: Long, limit: Int, txHashesOnly: Boolean): List<BlockDetail>
    fun getBlocksBeforeHeight(beforeHeight: Long, limit: Int, txHashesOnly: Boolean): List<BlockDetail>
    fun getConfirmationProof(txRID: TxRid): ConfirmationProof?
    fun getStatus(txRID: TxRid): ApiStatus
    fun query(query: GtxQuery): Gtv
    fun nodeStatusQuery(): StateNodeStatus
    fun nodePeersStatusQuery(): List<StateNodeStatus>
    fun getCurrentBlockHeight(): BlockHeight
    fun getBlockchainNodeState(): BlockchainNodeState
    fun getBlockchainConfiguration(height: Long = -1): ByteArray?
    fun validateBlockchainConfiguration(configuration: Gtv)
}

class NotSupported(message: String) : Exception(message)
class NotFoundError(message: String) : Exception(message)
class UnavailableException(message: String) : Exception(message)
class InvalidTnxException(message: String) : Exception(message)
class DuplicateTnxException(message: String) : Exception(message)

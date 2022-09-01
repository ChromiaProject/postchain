package net.postchain.client.core

import net.postchain.client.transaction.TransactionBuilder
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtx.Gtx
import java.util.concurrent.CompletionStage

interface PostchainClient {
    fun makeTransaction(): TransactionBuilder
    fun makeTransaction(signers: List<ByteArray>): TransactionBuilder

    fun postTransaction(tx: Gtx): CompletionStage<TransactionResult>
    fun postTransactionSync(tx: Gtx): TransactionResult
    fun postTransactionSyncAwaitConfirmation(tx: Gtx): TransactionResult
    fun awaitConfirmation(txRid: TxRid, retries: Int, pollInterval: Long): TransactionResult
    fun checkTxStatus(txRid: TxRid): CompletionStage<TransactionResult>

    fun query(name: String, gtv: Gtv = GtvDictionary.build(mapOf())): CompletionStage<Gtv>
    fun querySync(name: String, gtv: Gtv = GtvDictionary.build(mapOf())): Gtv
}
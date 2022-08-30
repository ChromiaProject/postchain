package net.postchain.client.core

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtx.data.GTXDataBuilder
import java.util.concurrent.CompletionStage

interface PostchainClient {
    fun makeTransaction(): GTXTransactionBuilder
    fun makeTransaction(signers: Array<ByteArray>): GTXTransactionBuilder

    fun postTransaction(txBuilder: GTXDataBuilder): CompletionStage<TransactionResult>
    fun postTransactionSync(txBuilder: GTXDataBuilder): TransactionResult
    fun postTransactionSyncAwaitConfirmation(txBuilder: GTXDataBuilder): TransactionResult
    fun awaitConfirmation(txRid: TxRid, retries: Int, pollInterval: Long): TransactionResult
    fun checkTxStatus(txRid: TxRid): CompletionStage<TransactionResult>

    fun query(name: String, gtv: Gtv = GtvDictionary.build(mapOf())): CompletionStage<Gtv>
    fun querySync(name: String, gtv: Gtv = GtvDictionary.build(mapOf())): Gtv
}
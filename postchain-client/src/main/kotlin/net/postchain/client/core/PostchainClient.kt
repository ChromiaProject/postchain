package net.postchain.client.core

import net.postchain.client.transaction.TransactionBuilder
import net.postchain.crypto.KeyPair
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtx.Gtx
import java.util.concurrent.CompletionStage

interface PostchainClient {
    /**
     * Creates a [TransactionBuilder] with the default signer list
     */
    fun transactionBuilder(): TransactionBuilder

    /**
     * Creates a [TransactionBuilder] with a given list of signers
     */
    fun transactionBuilder(signers: List<KeyPair>): TransactionBuilder

    /**
     * Post a [Gtx] transaction asynchronously
     */

    fun postTransaction(tx: Gtx): CompletionStage<TransactionResult>

    /**
     * Post a [Gtx] transaction synchronously
     */
    fun postTransactionSync(tx: Gtx): TransactionResult

    /**
     * Post a [Gtx] transaction and wait until it is included in a block
     */
    fun postTransactionSyncAwaitConfirmation(tx: Gtx): TransactionResult

    /**
     * Wait until the given [TxRid] is included in a block
     */
    fun awaitConfirmation(txRid: TxRid, retries: Int, pollInterval: Long): TransactionResult

    /**
     * Check the current status of a [TxRid]
     */
    fun checkTxStatus(txRid: TxRid): CompletionStage<TransactionResult>

    /**
     * Perform an asynchronous query
     */
    fun query(name: String, gtv: Gtv = GtvDictionary.build(mapOf())): CompletionStage<Gtv>

    /**
     * Perform a query
     */
    fun querySync(name: String, gtv: Gtv = GtvDictionary.build(mapOf())): Gtv
}
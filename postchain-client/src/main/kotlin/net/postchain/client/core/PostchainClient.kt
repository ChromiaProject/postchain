package net.postchain.client.core

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.crypto.KeyPair
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtx.Gtx
import java.util.concurrent.CompletionStage
import java.time.Duration

interface PostchainClient {
    val config: PostchainClientConfig
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
    fun awaitConfirmation(txRid: TxRid, retries: Int, pollInterval: Duration): TransactionResult

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

    /**
     * Query current block height
     */
    fun currentBlockHeight(): CompletionStage<Long>

    /**
     * Query current block height
     */
    fun currentBlockHeightSync(): Long

    /**
     * Query block at height
     */
    fun blockAtHeight(height: Long): CompletionStage<Gtv>

    /**
     * Query block at height
     */
    fun blockAtHeightSync(height: Long): Gtv
}
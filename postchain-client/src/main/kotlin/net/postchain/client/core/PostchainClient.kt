package net.postchain.client.core

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.crypto.KeyPair
import net.postchain.gtx.Gtx
import java.io.Closeable
import java.time.Duration

interface PostchainClient : PostchainBlockClient, PostchainQuery, Closeable {
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
     * Post a [Gtx] transaction.
     */

    fun postTransaction(tx: Gtx): TransactionResult

    /**
     * Post a [Gtx] transaction and wait until it is included in a block
     */
    fun postTransactionAwaitConfirmation(tx: Gtx): TransactionResult

    /**
     * Wait until the given [TxRid] is included in a block
     */
    fun awaitConfirmation(txRid: TxRid, retries: Int, pollInterval: Duration): TransactionResult

    /**
     * Check the current status of a [TxRid]
     */
    fun checkTxStatus(txRid: TxRid): TransactionResult
}

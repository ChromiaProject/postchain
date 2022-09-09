package net.postchain.client.transaction

import net.postchain.client.core.PostchainClient
import net.postchain.client.core.TransactionResult

fun TransactionResult.checkTxStatus(client: PostchainClient) = client.checkTxStatus(txRid)

fun TransactionResult.awaitConfirmation(client: PostchainClient, retries: Int, pollInterval: Long) = client.awaitConfirmation(txRid, retries, pollInterval)

package net.postchain.client.transaction

import net.postchain.client.config.STATUS_POLL_COUNT
import net.postchain.client.config.STATUS_POLL_INTERVAL
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.TransactionResult
import java.time.Duration

fun TransactionResult.checkTxStatus(client: PostchainClient) = client.checkTxStatus(txRid)

fun TransactionResult.awaitConfirmation(
    client: PostchainClient,
    retries: Int = STATUS_POLL_COUNT,
    pollInterval: Duration = STATUS_POLL_INTERVAL
) = client.awaitConfirmation(txRid, retries, pollInterval)

package net.postchain.client.core

import net.postchain.client.config.STATUS_POLL_COUNT
import net.postchain.client.config.STATUS_POLL_INTERVAL
import net.postchain.common.BlockchainRid

interface PostchainClientProvider {
    fun createClient(
        url: String,
        blockchainRid: BlockchainRid,
        defaultSigner: DefaultSigner?,
        statusPollCount: Int = STATUS_POLL_COUNT,
        statusPollInterval: Long = STATUS_POLL_INTERVAL
    ): PostchainClient
}

package net.postchain.client.core

import net.postchain.common.BlockchainRid

interface PostchainClientProvider {
    fun createClient(url: String, blockchainRid: BlockchainRid, defaultSigner: DefaultSigner?,
    retrieveTxStatusAttempts: Int = RETRIEVE_TX_STATUS_ATTEMPTS): PostchainClient
}

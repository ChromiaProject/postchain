package net.postchain.client.core

import net.postchain.core.BlockchainRid

interface PostchainClientProvider {
    fun createClient(url: String, blockchainRid: BlockchainRid, defaultSigner: DefaultSigner?): PostchainClient
}

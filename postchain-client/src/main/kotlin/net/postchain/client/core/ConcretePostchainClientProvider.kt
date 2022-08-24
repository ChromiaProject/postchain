package net.postchain.client.core

import net.postchain.common.BlockchainRid

class ConcretePostchainClientProvider : PostchainClientProvider {

    override fun createClient(url: String, blockchainRid: BlockchainRid, defaultSigner: DefaultSigner?, statusPollCount: Int, statusPollInterval: Long): PostchainClient {
        val nodeResolver = object : PostchainNodeResolver {
            override fun getNodeURL(blockchainRID: BlockchainRid) = url
        }
        return ConcretePostchainClient(nodeResolver, blockchainRid, defaultSigner, statusPollCount, statusPollInterval)
    }
}
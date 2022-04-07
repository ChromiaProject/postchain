// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.client.core

import net.postchain.core.BlockchainRid

object PostchainClientFactory {

    fun makeSimpleNodeResolver(serverURL: String): PostchainNodeResolver {
        return object : PostchainNodeResolver {
            override fun getNodeURL(blockchainRID: BlockchainRid): String {
                return serverURL
            }
        }
    }

    fun getClient(resolver: PostchainNodeResolver, blockchainRID: BlockchainRid, defaultSigner: DefaultSigner?): PostchainClient {
        return ConcretePostchainClient(resolver, blockchainRID, defaultSigner)
    }
}
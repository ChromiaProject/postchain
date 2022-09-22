package net.postchain.d1.icmf.integration

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.PostchainClientProvider
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake

object PostchainClientMocks {
    private val mockClients = mutableMapOf<BlockchainRid, PostchainClient>()

    fun createProvider(): MockPostchainClientProvider {
        return MockPostchainClientProvider()
    }

    fun addMockClient(blockchainRid: BlockchainRid, client: PostchainClient) {
        mockClients[blockchainRid] = client
    }

    fun clearMocks() {
        mockClients.clear()
    }

    class MockPostchainClientProvider : PostchainClientProvider {
        override fun createClient(clientConfig: PostchainClientConfig): PostchainClient {
            return mockClients[clientConfig.blockchainRid]
                    ?: throw ProgrammerMistake("No client defined for bcrid: ${clientConfig.blockchainRid}")
        }
    }
}


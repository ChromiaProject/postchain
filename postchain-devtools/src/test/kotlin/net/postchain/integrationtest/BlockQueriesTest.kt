package net.postchain.integrationtest

import assertk.assertThat
import assertk.assertions.isInstanceOf
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.devtools.IntegrationTestSetup
import org.junit.jupiter.api.Test

class BlockQueriesTest : IntegrationTestSetup() {

    @Test
    fun `Block queries should fail with PM closed error when shutdown`() {
        val nodes = createNodes(1, "/net/postchain/devtools/manual/blockchain_config.xml")
        val blockQueries = nodes[0].getBlockchainInstance(1L).blockchainEngine.getBlockQueries()

        nodes[0].stopBlockchain(1L)

        val query = blockQueries.getLastBlockHeight()
        query.whenComplete { _, exception ->
            assertThat(exception != null)
            assertThat(exception).isInstanceOf(PmEngineIsAlreadyClosed::class.java)
        }
    }
}

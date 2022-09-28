package net.postchain.integrationtest.managedmode

import net.postchain.devtools.IntegrationTestSetup
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class Chain0QueryManualModeIT : IntegrationTestSetup() {

    @Test
    fun checkChain0QueriesIsNotAvailable() {
        val nodes = createNodes(1, "/net/postchain/devtools/manual/blockchain_config.xml")

        assertNull(nodes[0].postchainContext.chain0QueryProvider())
    }
}
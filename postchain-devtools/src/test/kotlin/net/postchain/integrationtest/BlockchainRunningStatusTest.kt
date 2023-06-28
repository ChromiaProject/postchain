package net.postchain.integrationtest

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.PostchainTestNode.Companion.DEFAULT_CHAIN_IID
import org.junit.jupiter.api.Test

class BlockchainRunningStatusTest : IntegrationTestSetup() {

    @Test
    fun `Blockchain running status is reported correctly`() {
        val (node) = createNodes(1, "/net/postchain/devtools/manual/blockchain_config.xml")

        buildBlock(DEFAULT_CHAIN_IID, 0)
        assertThat(node.isBlockchainRunning(DEFAULT_CHAIN_IID)).isTrue()

        // Stop and start is reported correctly
        node.stopBlockchain(DEFAULT_CHAIN_IID)
        assertThat(node.isBlockchainRunning(DEFAULT_CHAIN_IID)).isFalse()
        node.startBlockchain()
        assertThat(node.isBlockchainRunning(DEFAULT_CHAIN_IID)).isTrue()

        // Reported as not running when process itself is stopped
        node.getBlockchainInstance().shutdown()
        assertThat(node.isBlockchainRunning(DEFAULT_CHAIN_IID)).isFalse()

        // Restart and build a block
        node.startBlockchain()
        assertThat(node.isBlockchainRunning(DEFAULT_CHAIN_IID)).isTrue()
        buildBlock(DEFAULT_CHAIN_IID, 1)
    }
}
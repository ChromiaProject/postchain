package net.postchain.integrationtest

import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import net.postchain.crypto.DilithiumCryptoSystem
import net.postchain.crypto.devtools.DilithiumKeyPairHelper
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.testinfra.TestTransaction
import org.junit.jupiter.api.Test

class DilithiumIntegrationTest : IntegrationTestSetup() {

    @Test
    fun `Network of nodes using dilithium signature algorithm can build blocks`() {
        configOverrides.setProperty("cryptosystem", DilithiumCryptoSystem::class.java.name)
        val nodes = createNodes(4, "/net/postchain/devtools/dilithium/blockchain_config_4.xml", keyPairCache = DilithiumKeyPairHelper)

        // Build a block and assert tx is included
        val testTx = TestTransaction(0)
        buildBlock(1L, 0, testTx)
        nodes.forEach {
            val txsInBlock = getTxRidsAtHeight(it, 0)
            assertk.assert(txsInBlock.size).isEqualTo(1)
            assertk.assert(txsInBlock[0]).isContentEqualTo(testTx.getRID())
        }
    }
}

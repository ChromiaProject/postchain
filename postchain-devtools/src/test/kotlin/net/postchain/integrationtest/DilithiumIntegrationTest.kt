package net.postchain.integrationtest

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import assertk.isContentEqualTo
import net.postchain.base.BaseBlockWitness
import net.postchain.concurrent.util.get
import net.postchain.crypto.DilithiumCryptoSystem
import net.postchain.crypto.devtools.DilithiumKeyPairHelper
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.testinfra.TestTransaction
import org.junit.jupiter.api.Test

class DilithiumIntegrationTest : IntegrationTestSetup() {

    private val cs = DilithiumCryptoSystem()

    @Test
    fun `Network of nodes using dilithium signature algorithm can build blocks`() {
        configOverrides.setProperty("cryptosystem", DilithiumCryptoSystem::class.java.name)
        val keyPairHelper = DilithiumKeyPairHelper
        val nodes = createNodes(4,
                "/net/postchain/devtools/dilithium/blockchain_config_4.xml",
                keyPairCache = keyPairHelper,
                overrideSigners = listOf(
                        keyPairHelper.pubKey(0),
                        keyPairHelper.pubKey(1),
                        keyPairHelper.pubKey(2),
                        keyPairHelper.pubKey(3)
                )
        )

        // Build a block and assert tx is included
        val testTx = TestTransaction(0)
        buildBlock(1L, 0, testTx)
        nodes.forEach {
            // asserting tx is added
            val txsInBlock = getTxRidsAtHeight(it, 0)
            assertThat(txsInBlock.size).isEqualTo(1)
            assertThat(txsInBlock[0]).isContentEqualTo(testTx.getRID())

            // asserting block signature
            val block = it.blockQueries().getBlockAtHeight(0, true).get()!!
            val signatures = BaseBlockWitness.fromBytes(block.witness.getRawData()).getSignatures()
            assertThat(signatures).isNotEmpty()
            signatures.forEach { s ->
                assertThat(cs.verifyDigest(block.header.blockRID, s)).isTrue()
            }
        }
    }
}

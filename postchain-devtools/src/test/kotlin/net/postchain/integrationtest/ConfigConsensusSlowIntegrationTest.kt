package net.postchain.integrationtest

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import net.postchain.base.extension.CONFIG_HASH_EXTRA_HEADER
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.concurrent.util.get
import net.postchain.devtools.ConfigFileBasedIntegrationTest
import net.postchain.devtools.PostchainTestNode
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.Test

class ConfigConsensusSlowIntegrationTest : ConfigFileBasedIntegrationTest() {
    private val blockchainConfig1FileName = "/net/postchain/devtools/reconfiguration/four_peers/consensus/blockchain_config_1.xml"
    private val blockchainConfig2 = readBlockchainConfig(
            "/net/postchain/devtools/reconfiguration/four_peers/consensus/blockchain_config_2.xml")

    @Test
    fun `Verify that config hash is added to extra header`() {
        createNodes(4, blockchainConfig1FileName)
        buildBlock(0)

        val initialConfig = readBlockchainConfig(blockchainConfig1FileName)
        val initialHash = GtvToBlockchainRidFactory.calculateBlockchainRid(initialConfig, cryptoSystem).data
        nodes.forEach {
            val initialBlock = it.blockQueries().getBlockAtHeight(0L).get()!!
            val decodedBlock = BlockHeaderData.fromBinary(initialBlock.header.rawData)
            assertThat(decodedBlock.getExtra()[CONFIG_HASH_EXTRA_HEADER]!!.asByteArray().contentEquals(initialHash)).isTrue()
        }

        // Add new config
        nodes.forEach {
            it.addConfiguration(PostchainTestNode.DEFAULT_CHAIN_IID, 2, blockchainConfig2)
        }
        buildBlocksWithChainRestart(2)
        val fullHash = GtvToBlockchainRidFactory.calculateBlockchainRid(blockchainConfig2, cryptoSystem).data
        nodes.forEach {
            val newConfigBlock = it.blockQueries().getBlockAtHeight(2L).get()!!
            val decodedBlock = BlockHeaderData.fromBinary(newConfigBlock.header.rawData)
            assertThat(decodedBlock.getExtra()[CONFIG_HASH_EXTRA_HEADER]!!.asByteArray().contentEquals(fullHash)).isTrue()
        }
    }

    @Test
    fun `Verify configuration consensus`() {
        createNodes(4, blockchainConfig1FileName)
        buildBlock(0)

        // Add new configs to all nodes except nr 3
        nodes[0].addConfiguration(PostchainTestNode.DEFAULT_CHAIN_IID, 2, blockchainConfig2)
        nodes[1].addConfiguration(PostchainTestNode.DEFAULT_CHAIN_IID, 2, blockchainConfig2)
        nodes[2].addConfiguration(PostchainTestNode.DEFAULT_CHAIN_IID, 2, blockchainConfig2)

        // Build to height 5
        buildBlocksWithChainRestart(5, listOf(nodes[0], nodes[1], nodes[2]))

        // Node without new config should be stuck at height 1
        assertThat(nodes[3].blockQueries().getLastBlockHeight().get()).isEqualTo(1L)

        // Patch node 3 with correct config and restart
        nodes[3].addConfiguration(PostchainTestNode.DEFAULT_CHAIN_IID, 2, blockchainConfig2)
        nodes[3].stopBlockchain(PostchainTestNode.DEFAULT_CHAIN_IID)
        nodes[3].startBlockchain(PostchainTestNode.DEFAULT_CHAIN_IID)

        // Assert node 3 can reach height 5 now
        Awaitility.await().atMost(Duration.TEN_SECONDS)
                .untilAsserted {
                    assertThat(nodes[3].blockQueries().getLastBlockHeight().get()).isEqualTo(5L)
                }
    }
}

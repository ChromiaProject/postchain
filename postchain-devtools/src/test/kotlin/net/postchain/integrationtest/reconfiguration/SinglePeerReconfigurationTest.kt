// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest.reconfiguration

import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import net.postchain.StorageBuilder
import net.postchain.config.node.NodeConfigurationProviderFactory
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.assertChainStarted
import net.postchain.devtools.getModules
import org.awaitility.Awaitility.await
import org.awaitility.Duration
import org.junit.jupiter.api.Test

class SinglePeerReconfigurationTest : ReconfigurationTest() {

    @Test
    fun reconfigurationAtHeight_is_successful() {
        // Node config
        val appConfig = createAppConfig(0, 1, DEFAULT_CONFIG_FILE)
        val nodeConfigProvider = NodeConfigurationProviderFactory.createProvider(appConfig)
        val chainId = nodeConfigProvider.getConfiguration().activeChainIds.first().toLong()

        // Chains configs
        val blockchainConfig1 = readBlockchainConfig(
                "/net/postchain/devtools/reconfiguration/single_peer/blockchain_config_1.xml")
        val blockchainConfig2 = readBlockchainConfig(
                "/net/postchain/devtools/reconfiguration/single_peer/blockchain_config_2.xml")

        // Wiping of database
        val storage = StorageBuilder.buildStorage(appConfig, true)

        PostchainTestNode(nodeConfigProvider, storage)
                .apply {
                    // Adding chain1 with blockchainConfig1 with DummyModule1
                    addBlockchain(chainId, blockchainConfig1)
                    // Adding chain1's blockchainConfig2 with DummyModule2
                    addConfiguration(chainId, 5, blockchainConfig2)
                    startBlockchain()
                }
                .also {
                    nodes.add(it)
                }

        // Asserting chain1 is started
        await().atMost(Duration.TEN_SECONDS)
                .untilAsserted {
                    nodes[0].assertChainStarted(chainId)
                }

        // Asserting blockchainConfig1 with DummyModule1 is loaded
        assertk.assert(nodes[0].getModules(chainId)[0]).isInstanceOf(DummyModule1::class)

        // Asserting blockchainConfig2 with DummyModule2 is loaded
        await().atMost(Duration.ONE_MINUTE)
                .untilAsserted {
                    val modules = nodes[0].getModules(chainId)
                    assertk.assert(modules).isNotEmpty()
                    assertk.assert(modules.first()).isInstanceOf(DummyModule2::class)
                }

    }

}
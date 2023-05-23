// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest.multiple_chains

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import mu.KLogging
import net.postchain.core.BadDataMistake
import net.postchain.devtools.ConfigFileBasedIntegrationTest
import net.postchain.devtools.PostchainTestNode
import org.junit.jupiter.api.Test

class SinglePeerDoubleChainsDependencyTest : ConfigFileBasedIntegrationTest() {

    companion object : KLogging() {

        const val BAD_DEPENDENCY_RID = "ABABABABABABABABABABABABABABABABABABABABABABABABABABABABABABABAB" // The dummy RID that's in the config file.
    }

    /**
     * What if our configuration tells us we should have a dependency, but we haven't got it?
     */
    @Test
    fun testBreakIfDependencyNotFound() {
        // Building configs
        val nodeConfigFilename = "classpath:/net/postchain/multiple_chains/dependent_bcs/single_peer/node0bc1dep.properties"
        val blockchainConfigFilename = "/net/postchain/devtools/multiple_chains/dependent_bcs/single_peer/blockchain_config_bad_dependency.xml"
        configOverrides.setProperty("testpeerinfos", createPeerInfos(1))
        val appConfig = createAppConfig(0, 1, nodeConfigFilename)

        // Building a PostchainNode
        val node = PostchainTestNode(appConfig, true)
                .also { nodes.add(it) }

        // Launching blockchain
        val blockchainConfig = readBlockchainConfig(blockchainConfigFilename)
        node.addBlockchain(1L, blockchainConfig)
        assertFailure { node.startBlockchain(1L) }.isInstanceOf(BadDataMistake::class)
    }

}

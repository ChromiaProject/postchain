package net.postchain.integrationtest

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.postchain.PostchainContext
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

var connected = mutableMapOf<Long, Boolean>()

class SynchronizationInfrastructureExtensionTest : IntegrationTestSetup() {

    @Test
    fun `Extension is connected and disconnected properly`() {
        createNodes(3, "/net/postchain/devtools/syncinfra_extensions/blockchain_config.xml")

        buildBlock(1L, 0)

        assertThat(connected[1L]!!).isTrue()

        nodes[0].stopBlockchain(1L)

        assertThat(connected[1L]!!).isFalse()
    }

    @Test
    fun `Extension that throws exception when connecting process will be propagated`() {
        assertThrows<ProgrammerMistake> {
            createNodes(3, "/net/postchain/devtools/syncinfra_extensions/blockchain_config_bad_connect.xml")
        }
    }

    @Test
    fun `Extension that throws exception when disconnecting process will be caught`() {
        val sysSetup = SystemSetupFactory.buildSystemSetup(mapOf(1 to "/net/postchain/devtools/syncinfra_extensions/blockchain_config_bad_disconnect.xml"))
        sysSetup.needRestApi = true
        createNodesFromSystemSetup(sysSetup)

        val bcRid = nodes[0].getBlockchainRid(1L)!!
        buildBlock(1L, 0)

        nodes[0].stopBlockchain(1L)

        // Assert that faulty extension does not prevent REST API model from being removed
        assertThat(nodes[0].getRestApiModel(bcRid)).isNull()
    }
}

class DummySynchronizationInfrastructureExtension(postchainContext: PostchainContext) : SynchronizationInfrastructureExtension {

    override fun shutdown() {}

    override fun connectProcess(process: BlockchainProcess) {
        connected[process.blockchainEngine.getConfiguration().chainID] = true
    }

    override fun disconnectProcess(process: BlockchainProcess) {
        connected[process.blockchainEngine.getConfiguration().chainID] = false
    }
}

class FaultyConnectingSynchronizationInfrastructureExtension(postchainContext: PostchainContext) : SynchronizationInfrastructureExtension {
    override fun shutdown() {}
    override fun connectProcess(process: BlockchainProcess) {
        throw Exception("Bad extension")
    }
    override fun disconnectProcess(process: BlockchainProcess) {}
}

class FaultyDisconnectingSynchronizationInfrastructureExtension(postchainContext: PostchainContext) : SynchronizationInfrastructureExtension {
    override fun shutdown() {}
    override fun connectProcess(process: BlockchainProcess) {}
    override fun disconnectProcess(process: BlockchainProcess) {
        throw Exception("Bad extension")
    }
}
package net.postchain.integrationtest

import assertk.assert
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.postchain.PostchainContext
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import org.junit.jupiter.api.Test

var connected = mutableMapOf<Long, Boolean>()

class SynchronizationInfrastructureExtensionTest : IntegrationTestSetup() {

    @Test
    fun `Extension is connected and disconnected properly`() {
        createNodes(3, "/net/postchain/devtools/syncinfra_extensions/blockchain_config.xml")

        buildBlock(1L, 0)

        assert(connected[1L]!!).isTrue()

        nodes[0].stopBlockchain(1L)

        assert(connected[1L]!!).isFalse()
    }

    @Test
    fun `Extension that throws errors when connecting and disconnecting process will be handled`() {
        val sysSetup = SystemSetupFactory.buildSystemSetup(mapOf(1 to "/net/postchain/devtools/syncinfra_extensions/blockchain_config_bad.xml"))
        sysSetup.needRestApi = true
        createNodesFromSystemSetup(sysSetup)
        val bcRid = nodes[0].getBlockchainRid(1L)!!

        buildBlock(1L, 0)

        // Assert that the faulty extension does not prevent REST API model from being installed
        val block = nodes[0].getRestApiModel().getBlock(0L, true)

        assert(block).isNotNull()

        nodes[0].stopBlockchain(1L)

        // Assert that faulty extension does not prevent REST API model from being removed
        assert(nodes[0].getRestApiModel(bcRid)).isNull()
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

class FaultySynchronizationInfrastructureExtension(postchainContext: PostchainContext) : SynchronizationInfrastructureExtension {

    override fun shutdown() {}

    override fun connectProcess(process: BlockchainProcess) {
        throw Exception("Bad extension")
    }

    override fun disconnectProcess(process: BlockchainProcess) {
        throw Exception("Bad extension")
    }
}
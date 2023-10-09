package net.postchain.integrationtest

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.postchain.PostchainContext
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.PostchainTestNode.Companion.DEFAULT_CHAIN_IID
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit

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
    fun `Extension that throws exception when connecting process will roll back config update`() {
        createNodes(3, "/net/postchain/devtools/syncinfra_extensions/blockchain_config.xml")

        buildBlock(1L, 0)
        assertThat(connected[1L]!!).isTrue()

        val faultyExtensionConfig = readBlockchainConfig(
                "/net/postchain/devtools/syncinfra_extensions/blockchain_config_bad_connect.xml"
        )
        nodes.forEach { node ->
            node.addConfiguration(DEFAULT_CHAIN_IID, 2, faultyExtensionConfig)
            withReadConnection(node.postchainContext.sharedStorage, DEFAULT_CHAIN_IID) { ctx ->
                val db = DatabaseAccess.of(ctx)
                assertThat(db.getConfigurationData(ctx, 2)).isNotNull()
            }
        }

        buildBlockNoWait(nodes, DEFAULT_CHAIN_IID, 2)

        nodes.forEach { node ->
            // Wait for faulty config to be reverted
            await().atMost(10, TimeUnit.SECONDS).untilAsserted {
                withReadConnection(node.postchainContext.sharedStorage, DEFAULT_CHAIN_IID) { ctx ->
                    val db = DatabaseAccess.of(ctx)
                    assertThat(db.getConfigurationData(ctx, 2)).isNull()
                }
            }
        }

        // Now ensure that we can build blocks with the original config
        buildBlock(DEFAULT_CHAIN_IID, 3)
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

@Suppress("UNUSED_PARAMETER")
class DummySynchronizationInfrastructureExtension(postchainContext: PostchainContext) : SynchronizationInfrastructureExtension {

    override fun shutdown() {}

    override fun connectProcess(process: BlockchainProcess) {
        connected[process.blockchainEngine.getConfiguration().chainID] = true
    }

    override fun disconnectProcess(process: BlockchainProcess) {
        connected[process.blockchainEngine.getConfiguration().chainID] = false
    }
}

@Suppress("UNUSED_PARAMETER")
class FaultyConnectingSynchronizationInfrastructureExtension(postchainContext: PostchainContext) : SynchronizationInfrastructureExtension {
    override fun shutdown() {}
    override fun connectProcess(process: BlockchainProcess) {
        throw Exception("Bad extension")
    }
    override fun disconnectProcess(process: BlockchainProcess) {}
}

@Suppress("UNUSED_PARAMETER")
class FaultyDisconnectingSynchronizationInfrastructureExtension(postchainContext: PostchainContext) : SynchronizationInfrastructureExtension {
    override fun shutdown() {}
    override fun connectProcess(process: BlockchainProcess) {}
    override fun disconnectProcess(process: BlockchainProcess) {
        throw Exception("Bad extension")
    }
}
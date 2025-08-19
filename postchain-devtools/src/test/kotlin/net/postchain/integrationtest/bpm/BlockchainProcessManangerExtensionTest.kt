package net.postchain.integrationtest.bpm

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.PostchainContext
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.assertChainNotStarted
import net.postchain.devtools.assertChainStarted
import net.postchain.devtools.utils.configuration.BlockchainSetupFactory
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.ebft.BaseEBFTInfrastructureFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ConcurrentHashMap

val connected = ConcurrentHashMap<Long, Boolean>()
val throwException = ConcurrentHashMap<Long, Boolean>()
val lastCommittedHeight = ConcurrentHashMap<Long, Long>()

class BlockchainProcessManagerExtensionTest : IntegrationTestSetup() {

    @AfterEach
    fun cleanup() {
        connected.clear()
        throwException.clear()
        lastCommittedHeight.clear()
    }

    @Test
    fun `Chain is connected and disconnected properly`() {
        val blockchainGtvConfig = readBlockchainConfig("/net/postchain/devtools/manual/blockchain_config.xml")

        val chainId = 1L
        val blockchainSetup = BlockchainSetupFactory.buildFromGtv(chainId.toInt(), blockchainGtvConfig, KeyPairHelper)

        val sysSetup = SystemSetupFactory.buildSystemSetup(listOf(blockchainSetup), KeyPairHelper)
        sysSetup.confInfrastructure = DummyTestInfrastructure::class.qualifiedName!!
        createNodesFromSystemSetup(sysSetup)

        assertThat(connected[chainId]!!).isTrue()

        buildBlock(chainId, 0)
        assertThat(lastCommittedHeight[chainId]!!).isEqualTo(0)

        nodes[0].stopBlockchain(chainId)

        assertThat(connected[chainId]!!).isFalse()
    }

    @Test
    fun `Ensure exceptions on connect and disconnect are handled properly`() {
        val blockchainGtvConfig = readBlockchainConfig("/net/postchain/devtools/manual/blockchain_config.xml")

        val chainId = 1L
        val blockchainSetup = BlockchainSetupFactory.buildFromGtv(chainId.toInt(), blockchainGtvConfig, KeyPairHelper)

        val sysSetup = SystemSetupFactory.buildSystemSetup(listOf(blockchainSetup), KeyPairHelper)
        sysSetup.confInfrastructure = DummyTestInfrastructure::class.qualifiedName!!
        createNodesFromSystemSetup(sysSetup)

        assertThat(connected[chainId]!!).isTrue()

        buildBlock(chainId, 0)
        throwException[chainId] = true
        nodes[0].stopBlockchain(chainId)

        // Assert chain was stopped and removed from processes
        nodes[0].assertChainNotStarted(chainId)

        // Assert that chain can't be started again
        assertThrows<Exception> {
            nodes[0].startBlockchain(chainId)
        }
        nodes[0].assertChainNotStarted(chainId)

        throwException[chainId] = false

        // Assert that chain can be started and build blocks again
        nodes[0].startBlockchain(chainId)
        nodes[0].assertChainStarted(chainId)
        buildBlock(chainId, 1)
        assertThat(lastCommittedHeight[chainId]!!).isEqualTo(1)
    }
}

class DummyTestInfrastructure() : BaseEBFTInfrastructureFactory() {
    override fun getProcessManagerExtensions(postchainContext: PostchainContext, blockchainInfrastructure: BlockchainInfrastructure) =
            listOf(DummyBlockchainProcessManagerExtension(postchainContext))
}

@Suppress("UNUSED_PARAMETER")
class DummyBlockchainProcessManagerExtension(postchainContext: PostchainContext) : BlockchainProcessManagerExtension {

    override fun shutdown() {}

    override fun connectProcess(process: BlockchainProcess) {
        connected[process.blockchainEngine.getConfiguration().chainID] = true

        if (throwException[process.blockchainEngine.getConfiguration().chainID] == true) {
            throw Exception("Bad extension")
        }
    }

    override fun disconnectProcess(process: BlockchainProcess) {
        connected[process.blockchainEngine.getConfiguration().chainID] = false

        if (throwException[process.blockchainEngine.getConfiguration().chainID] == true) {
            throw Exception("Bad extension")
        }
    }

    override fun afterCommit(process: BlockchainProcess, height: Long) {
        lastCommittedHeight[process.blockchainEngine.getConfiguration().chainID] = height
    }
}

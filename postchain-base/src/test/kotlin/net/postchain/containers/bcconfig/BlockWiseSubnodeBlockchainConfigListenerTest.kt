package net.postchain.containers.bcconfig

import mu.KLogging
import net.postchain.base.configuration.KEY_CONFIGURATIONFACTORY
import net.postchain.base.configuration.KEY_GTX
import net.postchain.base.configuration.KEY_GTX_MODULES
import net.postchain.base.configuration.KEY_SIGNERS
import net.postchain.common.BlockchainRid
import net.postchain.common.BlockchainRid.Companion.ZERO_RID
import net.postchain.config.app.AppConfig
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.MockStorage
import net.postchain.configurations.GTXTestModule
import net.postchain.containers.bpm.bcconfig.BlockWiseSubnodeBlockchainConfigListener
import net.postchain.containers.bpm.bcconfig.BlockchainConfigVerifier
import net.postchain.containers.bpm.bcconfig.SubnodeBlockchainConfigurationConfig
import net.postchain.core.NodeRid
import net.postchain.core.Storage
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.network.common.LazyPacket
import net.postchain.network.mastersub.MasterSubQueryManager
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.MsFindNextBlockchainConfigMessage
import net.postchain.network.mastersub.protocol.MsMessage
import net.postchain.network.mastersub.protocol.MsNextBlockchainConfigMessage
import net.postchain.network.mastersub.subnode.SubConnectionManager
import net.postchain.network.peer.XChainPeersConfiguration
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class BlockWiseSubnodeBlockchainConfigListenerTest {

    val blockchainRid = BlockchainRid.buildRepeat(0)

    companion object : KLogging()

    val validConfig = GtvEncoder.encodeGtv(gtv(mapOf(
            KEY_SIGNERS to gtv(listOf()),
            KEY_CONFIGURATIONFACTORY to gtv("BOGUS_FACTORY"),
            KEY_GTX to gtv(mapOf(KEY_GTX_MODULES to gtv(listOf(gtv(GTXTestModule::class.java.name))))))))

    val invalidConfig = GtvEncoder.encodeGtv(gtv(mapOf()))

    @Test
    fun `Life cycle test`() {
        val connectionManager = MockSubConnectionManager()
        val config: SubnodeBlockchainConfigurationConfig = mock {
            on { enabled } doReturn true
            on { sleepTimeout } doReturn 100
        }
        val appConfig: AppConfig = mock {
            on { cryptoSystem } doReturn Secp256K1CryptoSystem()
        }
        val configVerifier = BlockchainConfigVerifier(appConfig)
        val blockchainConfigurationProvider: BlockchainConfigurationProvider = mock {
            on { findNextConfigurationHeight(any(), eq(0L)) } doReturn -1L
            on { findNextConfigurationHeight(any(), eq(1L)) } doReturn 20L
        }
        val mock0 = MockStorage.mockEContext(0L)
        val sut = BlockWiseSubnodeBlockchainConfigListener(
                config, configVerifier, 0L, ZERO_RID, connectionManager, blockchainConfigurationProvider, mock0.storage)

        // Committing block0
        sut.commit(0L)
        assertEquals(1, connectionManager.receivedMessages.size)
        val msg1 = connectionManager.receivedMessages.removeFirst()
        assertEquals(0L, msg1.lastHeight)
        assertEquals(-1L, msg1.nextHeight)

        // Receiving a wrong response: lastHeight = 10
        val response10 = MsNextBlockchainConfigMessage(
                ZERO_RID.data, 10L, null, null, null)
        // receiving a correct response: lastHeight = 0
        val response0 = MsNextBlockchainConfigMessage(
                ZERO_RID.data, 0L, null, null, null)

        // receiving a wrong response after a correct one: lastHeight = 11
        val response11 = MsNextBlockchainConfigMessage(
                ZERO_RID.data, 11L, null, null, null)
        connectionManager.mockResponses[0L to -1L] = mutableListOf(
                response10, response0, response11
        )
        sut.commit(0L)
        assertEquals(2, connectionManager.receivedMessages.size)
        connectionManager.receivedMessages.clear()

        // Committing block1
        sut.commit(1L)
        assertEquals(1, connectionManager.receivedMessages.size)
        val msg2 = connectionManager.receivedMessages.removeFirst()
        assertEquals(1L, msg2.lastHeight)
        assertEquals(20L, msg2.nextHeight)

        // Response's nextHeight = 15 < 20 AND corrupted rawData
        val corruptedConfig = validConfig.copyOf(validConfig.size - 1)
        val response1corrupted = MsNextBlockchainConfigMessage(
                ZERO_RID.data, 1L, 15L, corruptedConfig, configVerifier.calculateHash(validConfig))
        val response1invalid = MsNextBlockchainConfigMessage(
                ZERO_RID.data, 1L, 15L, invalidConfig, configVerifier.calculateHash(invalidConfig))
        // Response's nextHeight = 15 < 20 AND correct rawData
        val response1 = MsNextBlockchainConfigMessage(
                ZERO_RID.data, 1L, 16L, validConfig, configVerifier.calculateHash(validConfig))
        connectionManager.mockResponses[1L to 20L] = mutableListOf(response1corrupted, response1invalid, response1)
        sut.commit(1L)
        sut.commit(1L)
        verify(mock0.db).addConfigurationData(any(), eq(16L), eq(validConfig))
    }

    @Test
    fun `Life cycle test when config fetching is disabled`() {
        val connectionManager: SubConnectionManager = mock()
        val config: SubnodeBlockchainConfigurationConfig = mock {
            on { enabled } doReturn false
        }
        val appConfig: AppConfig = mock {
            on { cryptoSystem } doReturn Secp256K1CryptoSystem()
        }
        val configVerifier = BlockchainConfigVerifier(appConfig)
        val storage: Storage = mock()
        val blockchainConfigurationProvider: BlockchainConfigurationProvider = mock()
        val sut = BlockWiseSubnodeBlockchainConfigListener(
                config, configVerifier, 0L, ZERO_RID, connectionManager, blockchainConfigurationProvider, storage)

        // Committing block0
        sut.commit(0L)
        verify(connectionManager, never()).sendMessageToMaster(eq(0L), any())
    }

    class MockSubConnectionManager : SubConnectionManager {
        override val masterSubQueryManager = MasterSubQueryManager(::sendMessageToMaster)
        val receivedMessages = mutableListOf<MsFindNextBlockchainConfigMessage>()
        val mockResponses = mutableMapOf<Pair<Long, Long>, MutableList<MsNextBlockchainConfigMessage>>()
        lateinit var configListener: MsMessageHandler

        private val executor: ExecutorService = Executors.newSingleThreadScheduledExecutor()

        override fun preAddMsMessageHandler(chainId: Long, handler: MsMessageHandler) {
            configListener = handler
        }

        override fun sendMessageToMaster(chainId: Long, message: MsMessage): Boolean {
            if (message is MsFindNextBlockchainConfigMessage) {
                receivedMessages.add(message)

                val mockResponse = mockResponses[message.lastHeight to message.nextHeight]
                if (mockResponse != null) {
                    sendMessage(mockResponse.removeFirst())
                } else {
                    sendMessage(MsNextBlockchainConfigMessage(
                            BlockchainRid.buildRepeat(0).data,
                            message.lastHeight,
                            null,
                            null,
                            null
                    ))
                }
                return true
            }
            return false
        }

        private fun sendMessage(message: MsMessage) {
            executor.execute {
                configListener.onMessage(message)
            }
        }

        override fun connectChain(chainPeersConfig: XChainPeersConfiguration, autoConnectAll: Boolean, loggingPrefix: () -> String): Unit = throw NotImplementedError()
        override fun disconnectChain(loggingPrefix: () -> String, chainId: Long): Unit = throw NotImplementedError()
        override fun sendPacket(data: LazyPacket, chainId: Long, nodeRid: NodeRid): Unit = throw NotImplementedError()
        override fun broadcastPacket(data: LazyPacket, chainId: Long): Unit = throw NotImplementedError()
        override fun getConnectedNodes(chainId: Long): List<NodeRid> = throw NotImplementedError()
        override fun getNodesTopology(): Map<String, Map<String, String>> = throw NotImplementedError()
        override fun getNodesTopology(chainIid: Long): Map<NodeRid, String> = throw NotImplementedError()
        override fun shutdown() {
            executor.shutdownNow()
            executor.awaitTermination(1000, TimeUnit.MILLISECONDS)
        }
    }
}
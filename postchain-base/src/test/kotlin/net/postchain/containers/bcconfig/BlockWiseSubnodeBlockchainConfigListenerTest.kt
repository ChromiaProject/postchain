package net.postchain.containers.bcconfig

import mu.KLogging
import net.postchain.common.BlockchainRid.Companion.ZERO_RID
import net.postchain.config.app.AppConfig
import net.postchain.config.node.MockStorage
import net.postchain.containers.bpm.bcconfig.BlockWiseSubnodeBlockchainConfigListener
import net.postchain.containers.bpm.bcconfig.SubnodeBlockchainConfigVerifier
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.network.mastersub.protocol.MsFindNextBlockchainConfigMessage
import net.postchain.network.mastersub.protocol.MsNextBlockchainConfigMessage
import net.postchain.network.mastersub.subnode.SubConnectionManager
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import kotlin.test.assertEquals

class BlockWiseSubnodeBlockchainConfigListenerTest {

    private val appConfig: AppConfig = mock() {
        on { cryptoSystem } doReturn Secp256K1CryptoSystem()
    }
    private val configVerifier = SubnodeBlockchainConfigVerifier(appConfig)
    private val ignored = 0L

    companion object : KLogging()

    @Test
    fun `Life cycle test`() {
        val connectionManager: SubConnectionManager = mock()
        val sut = BlockWiseSubnodeBlockchainConfigListener(
                appConfig, mock(), 0L, ZERO_RID, connectionManager)
        val mock0 = MockStorage.mockEContext(0L)
        sut.storage = mock0.storage
        sut.blockchainConfigProvider = mock {
            on { findNextConfigurationHeight(any(), eq(0L)) } doReturn -1L
            on { findNextConfigurationHeight(any(), eq(1L)) } doReturn 20L
        }

        // Commiting block0
        sut.commit(0L, ignored)
        val msg = argumentCaptor<MsFindNextBlockchainConfigMessage>()
        verify(connectionManager).sendMessageToMaster(eq(0L), msg.capture())
        assertEquals(0L, msg.firstValue.lastHeight)
        assertEquals(-1L, msg.firstValue.nextHeight)
        assertEquals(false, sut.checkConfig())

        // Commiting block0 again.
        sut.commit(0L, ignored)
        verify(connectionManager, times(1)).sendMessageToMaster(any(), any())
        assertEquals(false, sut.checkConfig())

        // Commiting block1 when height0 is not reset.
        sut.commit(1L, ignored)
        verify(connectionManager, times(1)).sendMessageToMaster(any(), any())
        assertEquals(false, sut.checkConfig())

        // Receiving a wrong response: lastHeight = 10
        val response10 = MsNextBlockchainConfigMessage(
                ZERO_RID.data, 10L, null, null, null)
        sut.onMessage(response10)
        // receiving a correct response: lastHeight = 0
        val response0 = MsNextBlockchainConfigMessage(
                ZERO_RID.data, 0L, null, null, null)
        sut.onMessage(response0)
        assertEquals(true, sut.checkConfig())
        // receiving a wrong response after a correct one: lastHeight = 11
        val response11 = MsNextBlockchainConfigMessage(
                ZERO_RID.data, 11L, null, null, null)
        sut.onMessage(response11)
        assertEquals(true, sut.checkConfig())

        // Commiting block1 when height0 is reset.
        sut.commit(1L, ignored)
        val msg1 = argumentCaptor<MsFindNextBlockchainConfigMessage>()
        verify(connectionManager, times(2)).sendMessageToMaster(eq(0L), msg1.capture())
        assertEquals(1L, msg1.secondValue.lastHeight)
        assertEquals(20L, msg1.secondValue.nextHeight)
        assertEquals(false, sut.checkConfig())
        // Response's nextHeight = 15 < 20 AND corrupted rawData
        val config15 = GtvEncoder.encodeGtv(gtv("my config"))
        val hash15 = configVerifier.calculateHash(config15)
        val config15corrupted = config15.copyOf(config15.size - 1)
        val response1corrupted = MsNextBlockchainConfigMessage(
                ZERO_RID.data, 1L, 15L, config15corrupted, hash15)
        sut.onMessage(response1corrupted)
        assertEquals(false, sut.checkConfig())
        // Response's nextHeight = 15 < 20 AND correct rawData
        val response1 = MsNextBlockchainConfigMessage(
                ZERO_RID.data, 1L, 15L, config15, hash15)
        sut.onMessage(response1)
        verify(mock0.db).addConfigurationData(any(), eq(15L), eq(config15))
        assertEquals(true, sut.checkConfig())
    }
}
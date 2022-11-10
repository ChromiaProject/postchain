package net.postchain.ebft.remoteconfig

import net.postchain.common.BlockchainRid
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.MockStorage
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultRemoteConfigListenerTest {

    private val chainId = 0L
    private val blockchainRid = BlockchainRid.ZERO_RID
    private val now = System.currentTimeMillis()
    private val config = GtvEncoder.encodeGtv(GtvFactory.gtv("valid config"))
    private val configHash = RemoteConfigVerifier.calculateHash(config)
    private val invalidConfig = config.dropLast(1).toByteArray()

    @Test
    fun `Life cycle test`() {
        val remoteConfigConfig: RemoteConfigConfig = mock()
        val connManager: SubConnectionManager = mock()
        val mockBlockchainConfigProvider: BlockchainConfigurationProvider = mock {
            on { findNextConfigurationHeight(any(), any()) } doReturn 0
        }
        val sut = DefaultRemoteConfigListener(remoteConfigConfig, chainId, blockchainRid, connManager).apply {
            storage = MockStorage.mockEContext(chainId)
            blockchainConfigProvider = mockBlockchainConfigProvider
        }

        // 1. First block check
        assertTrue(sut.checkRemoteConfig(-1))

        // 2a. Assert: No RemoteConfig received then fails
        assertFalse(sut.checkRemoteConfig(now))

        // 2b. Verification: remote config requested
        val message = argumentCaptor<MsFindNextBlockchainConfigMessage>()
        verify(connManager, times(1)).sendMessageToMaster(eq(chainId), message.capture())

        // 3a. Interaction: Then _invalid_ remote config received
        val invalidRemoteConfig = MsNextBlockchainConfigMessage(BlockchainRid.ZERO_RID.data, 0L, invalidConfig, configHash)
        sut.onMessage(invalidRemoteConfig)

        // 3b. Assert: received RemoteConfig check FAILED, received RemoteConfig is NOT recorded
        assertFalse(sut.checkRemoteConfig(now + 1L))

        // 3c. Verification: the NEW remote config requested again
        verify(connManager, times(2)).sendMessageToMaster(eq(chainId), message.capture())

        // 4a. Interaction: Then valid remote config received
        val configHash = RemoteConfigVerifier.calculateHash(config)
        val remoteConfig = MsNextBlockchainConfigMessage(BlockchainRid.ZERO_RID.data, 0L, config, configHash)
        sut.onMessage(remoteConfig)

        // 4b. Assert: RemoteConfig received and RemoteConfig check PASSED
        assert(sut.checkRemoteConfig(now + 2L))
    }
}
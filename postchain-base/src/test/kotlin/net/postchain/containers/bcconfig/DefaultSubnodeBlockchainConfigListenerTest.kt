package net.postchain.containers.bcconfig

import net.postchain.common.BlockchainRid
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.MockStorage
import net.postchain.containers.bpm.bcconfig.DefaultSubnodeBlockchainConfigListener
import net.postchain.containers.bpm.bcconfig.SubnodeBlockchainConfigVerifier
import net.postchain.containers.bpm.bcconfig.SubnodeBlockchainConfigurationConfig
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

class DefaultSubnodeBlockchainConfigListenerTest {

    private val chainId = 0L
    private val blockchainRid = BlockchainRid.ZERO_RID
    private val now = System.currentTimeMillis()
    private val config = GtvEncoder.encodeGtv(GtvFactory.gtv("valid config"))
    private val configHash = SubnodeBlockchainConfigVerifier.calculateHash(config)
    private val invalidConfig = config.dropLast(1).toByteArray()

    @Test
    fun `Life cycle test`() {
        val subnodeBlockchainConfigurationConfig: SubnodeBlockchainConfigurationConfig = mock()
        val connManager: SubConnectionManager = mock()
        val mockBlockchainConfigProvider: BlockchainConfigurationProvider = mock {
            on { findNextConfigurationHeight(any(), any()) } doReturn 0
        }
        val sut = DefaultSubnodeBlockchainConfigListener(subnodeBlockchainConfigurationConfig, chainId, blockchainRid, connManager).apply {
            storage = MockStorage.mockEContext(chainId)
            blockchainConfigProvider = mockBlockchainConfigProvider
        }

        // 1. First block check
        assertTrue(sut.checkConfig())

        // 2a. Assert: No BlockchainConfig received then fails
        sut.lastBlockTimestamp = now
        assertFalse(sut.checkConfig())

        // 2b. Verification: BlockchainConfig requested
        val message = argumentCaptor<MsFindNextBlockchainConfigMessage>()
        verify(connManager, times(1)).sendMessageToMaster(eq(chainId), message.capture())

        // 3a. Interaction: Then _invalid_ BlockchainConfig received
        val invalidConfig = MsNextBlockchainConfigMessage(BlockchainRid.ZERO_RID.data, 0L, invalidConfig, configHash)
        sut.onMessage(invalidConfig)

        // 3b. Assert: received BlockchainConfig check FAILED, received BlockchainConfig is NOT recorded
        sut.lastBlockTimestamp = now + 1L
        assertFalse(sut.checkConfig())

        // 3c. Verification: the NEW BlockchainConfig requested again
        verify(connManager, times(2)).sendMessageToMaster(eq(chainId), message.capture())

        // 4a. Interaction: Then valid BlockchainConfig received
        val configHash = SubnodeBlockchainConfigVerifier.calculateHash(config)
        val blockchainConfig = MsNextBlockchainConfigMessage(BlockchainRid.ZERO_RID.data, 0L, config, configHash)
        sut.onMessage(blockchainConfig)

        // 4b. Assert: BlockchainConfig received and BlockchainConfig check PASSED
        sut.lastBlockTimestamp = now + 2L
        assert(sut.checkConfig())
    }
}
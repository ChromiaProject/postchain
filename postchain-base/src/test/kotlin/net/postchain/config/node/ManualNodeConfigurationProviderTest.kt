// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import assertk.assertions.containsExactly
import net.postchain.base.PeerInfo
import net.postchain.common.hexStringToByteArray
import net.postchain.config.app.AppConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Instant

class ManualNodeConfigurationProviderTest {

    private val peerInfo0 = PeerInfo("127.0.0.1", 9900, "AAAA".hexStringToByteArray(), Instant.EPOCH)
    private val peerInfo1 = PeerInfo("127.0.0.1", 9901, "BBBB".hexStringToByteArray(), Instant.EPOCH)

    @Test
    fun testGetPeerInfoCollection() {
        // Expected
        val expected = arrayOf(peerInfo1, peerInfo0)
        val actual = arrayOf(peerInfo1, peerInfo0)

        // Mock
        val mockStorage = MockStorage.mockAppContext(expected)

        // SUT
        val appConfig = mock<AppConfig> {
            on { pubKey } doReturn "CCCC"
            on { port } doReturn 9870
        }
        val provider = ManualNodeConfigurationProvider(appConfig) { mockStorage.storage }

        // Assert
        val peerInfos = provider.getPeerInfoCollection(mock())
        assertk.assert(peerInfos).containsExactly(*actual)

        verify(mockStorage.db).addPeerInfo(any(), eq("localhost"), eq(9870), eq("CCCC"), eq(null))
    }
}
// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isSameAs
import org.mockito.kotlin.mock
import net.postchain.base.PeerInfo
import net.postchain.base.peerId
import net.postchain.common.hexStringToByteArray
import net.postchain.config.app.AppConfig
import org.junit.Test
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
        val mockStorage = MockStorage.mock(expected)

        // SUT
        val provider = ManualNodeConfigurationProvider(mock()) { mockStorage }

        // Assert
        val peerInfos = provider.getPeerInfoCollection(mock())
        assertk.assert(peerInfos).containsExactly(*actual)
    }
}
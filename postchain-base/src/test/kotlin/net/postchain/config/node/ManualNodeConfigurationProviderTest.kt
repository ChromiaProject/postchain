// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import assertk.assertions.containsExactly
import org.mockito.kotlin.mock
import net.postchain.base.PeerInfo
import net.postchain.common.hexStringToByteArray
import org.junit.jupiter.api.Test
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
        val provider = ManualNodeConfigurationProvider(mock()) { mockStorage }

        // Assert
        val peerInfos = provider.getPeerInfoCollection(mock())
        assertk.assert(peerInfos).containsExactly(*actual)
    }
}
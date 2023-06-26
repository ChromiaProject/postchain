// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import net.postchain.base.PeerInfo
import net.postchain.common.hexStringToByteArray
import net.postchain.core.NodeRid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Instant

class ManagedNodeConfigurationProviderTest {

    private val peerInfo0 = PeerInfo("127.0.0.1", 9900, "AAAA".hexStringToByteArray(), Instant.EPOCH)
    private val peerInfo0New = PeerInfo("127.0.0.1", 9900, "AAAA".hexStringToByteArray(), Instant.now())
    private val peerInfo1 = PeerInfo("127.0.0.1", 9901, "BBBB".hexStringToByteArray(), Instant.EPOCH)
    private val peerInfo1New = PeerInfo("127.0.0.1", 9901, "BBBB".hexStringToByteArray(), Instant.now())
    private val peerInfo2 = PeerInfo("127.0.0.1", 9902, "CCCC".hexStringToByteArray(), Instant.EPOCH)
    private val peerInfo2New = PeerInfo("127.0.0.1", 9902, "CCCC".hexStringToByteArray(), Instant.now())

    /**
     * Implementation of most tests
     */
    private fun mock_then_buildSUT_then_assertPeerInfoCollection(
            manual: Array<PeerInfo>,
            managed: Array<PeerInfo>,
            expected: Array<PeerInfo>
    ) {
        // Mock
        val mockStorage = MockStorage.mockAppContext(manual)

        val mockManagedPeerInfos: PeerInfoDataSource = mock {
            on { getPeerInfos() } doReturn managed
        }

        // SUT
        val provider = ManagedNodeConfigurationProvider(mock(), mockStorage.storage)
        provider.apply {
            setPeerInfoDataSource(mockManagedPeerInfos)
        }

        // Action
        val peerInfoCollection = provider.getPeerInfoCollection()

        // Assert
        assertThat(peerInfoCollection).containsExactly(*expected)
    }

    @Test
    fun getPeerInfoCollection__manualProvider_returns_noPeers_AND_managedDataSource_returns_noPeers() {
        mock_then_buildSUT_then_assertPeerInfoCollection(
                emptyArray(),
                emptyArray(),
                emptyArray()
        )
    }

    @Test
    fun getPeerInfoCollection__manualProvider_returns_singlePeer_AND_managedDataSource_returns_noPeers() {
        mock_then_buildSUT_then_assertPeerInfoCollection(
                arrayOf(peerInfo2),
                emptyArray(),
                arrayOf(peerInfo2)
        )
    }

    @Test
    fun getPeerInfoCollection__manualProvider_returns_twoPeers_AND_managedDataSource_returns_noPeers() {
        mock_then_buildSUT_then_assertPeerInfoCollection(
                arrayOf(peerInfo0, peerInfo1),
                emptyArray(),
                arrayOf(peerInfo0, peerInfo1)
        )
    }

    @Test
    fun getPeerInfoCollection__provider_returns_managedPeerInfos_iff_managedDataSource_isNotNull() {
        // Mock
        val mockStorage = MockStorage.mockAppContext(emptyArray())

        val mockManagedPeerInfos: PeerInfoDataSource = mock {
            on { getPeerInfos() } doReturn arrayOf(peerInfo0)
        }

        val mockManagedPeerInfosAnother: PeerInfoDataSource = mock {
            on { getPeerInfos() } doReturn arrayOf(peerInfo1, peerInfo2)
        }

        // SUT
        val provider = ManagedNodeConfigurationProvider(mock(), mockStorage.storage)

        // Assert
        // 1. managedPeerInfoDataSource field is set
        provider.setPeerInfoDataSource(mockManagedPeerInfos)
        assertThat(
                provider.getPeerInfoCollection()
        ).containsExactly(peerInfo0)

        // 2. managedPeerInfoDataSource field is null
        provider.setPeerInfoDataSource(null)
        assertThat(
                provider.getPeerInfoCollection()
        ).isEmpty()

        // 3. managedPeerInfoDataSource field is set again
        provider.setPeerInfoDataSource(mockManagedPeerInfosAnother)
        assertThat(
                provider.getPeerInfoCollection()
        ).containsExactly(peerInfo1, peerInfo2)
    }

    @Test
    fun getPeerInfoCollection__manualProvider_returns_noPeers_AND_managedDataSource_returns_singlePeer() {
        mock_then_buildSUT_then_assertPeerInfoCollection(
                emptyArray(),
                arrayOf(peerInfo2),
                arrayOf(peerInfo2)
        )
    }

    @Test
    fun getPeerInfoCollection__manualProvider_returns_singlePeer_AND_managedDataSource_returns_twoOtherPeers() {
        mock_then_buildSUT_then_assertPeerInfoCollection(
                arrayOf(peerInfo2),
                arrayOf(peerInfo0, peerInfo1),
                arrayOf(peerInfo2, peerInfo0, peerInfo1)
        )
    }

    @Test
    fun getPeerInfoCollection__manualProvider_returns_singlePeer_AND_managedDataSource_returns_theSame() {
        mock_then_buildSUT_then_assertPeerInfoCollection(
                arrayOf(peerInfo0),
                arrayOf(peerInfo0),
                arrayOf(peerInfo0)
        )
    }

    @Test
    fun getPeerInfoCollection__manualProvider_returns_twoPeers_AND_managedDataSource_returns_theSame() {
        mock_then_buildSUT_then_assertPeerInfoCollection(
                arrayOf(peerInfo0, peerInfo2),
                arrayOf(peerInfo0, peerInfo2),
                arrayOf(peerInfo0, peerInfo2)
        )
    }

    @Test
    fun getPeerInfoCollection__manualProvider_returns_singlePeer_AND_managedDataSource_returns_theSamePeerButNewer() {
        mock_then_buildSUT_then_assertPeerInfoCollection(
                arrayOf(peerInfo0),
                arrayOf(peerInfo0New),
                arrayOf(peerInfo0New)
        )
    }

    @Test
    fun getPeerInfoCollection__manualProvider_returns_singlePeer_AND_managedDataSource_returns_theSamePeerButOlder() {
        mock_then_buildSUT_then_assertPeerInfoCollection(
                arrayOf(peerInfo0New),
                arrayOf(peerInfo0),
                arrayOf(peerInfo0New)
        )
    }

    @Test
    fun getPeerInfoCollection__manualProvider_returns_twoPeers_AND_managedDataSource_returns_threePeersNewerOrOlder() {
        mock_then_buildSUT_then_assertPeerInfoCollection(
                arrayOf(peerInfo0, peerInfo1New),
                arrayOf(peerInfo0New, peerInfo1, peerInfo2New),
                arrayOf(peerInfo0New, peerInfo1New, peerInfo2New)
        )
    }

    @Test
    fun testMerge() {
        val a = listOf(p(1), p(2))
        val b = listOf(p(3), p(2))
        val expectedMerged = setOf(p(1), p(2), p(3))
        val provider = ManagedNodeConfigurationProvider(mock(), MockStorage.mockAppContext().storage)
        val result = provider.merge(a, b)
        assertEquals(expectedMerged.size, result.size)
        assertEquals(expectedMerged, result.toSet())
    }

    fun p(s: Int): NodeRid {
        return NodeRid(byteArrayOf(s.toByte()))
    }
}
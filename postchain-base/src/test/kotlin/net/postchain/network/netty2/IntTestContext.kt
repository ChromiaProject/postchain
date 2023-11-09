// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.netty2

import net.postchain.base.PeerInfo
import net.postchain.core.Shutdownable
import net.postchain.network.common.NodeConnectorEvents
import net.postchain.network.peer.PeerConnectionDescriptor
import net.postchain.network.peer.PeerPacketHandler
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class IntTestContext(
        ownerPeerInfo: PeerInfo,
        peerInfos: Array<PeerInfo>
) : Shutdownable {

    var isShutdown = false

    val packets: PeerPacketHandler = mock()

    val events: NodeConnectorEvents<PeerPacketHandler, PeerConnectionDescriptor> = mock {
        on { onNodeConnected(any()) } doReturn packets
        on { onNodeDisconnected(any()) }.doAnswer { } // FYI: Instead of `doNothing` or `doReturn Unit`
    }

    val peer = NettyPeerConnector<Int>(events)

    val packetCodec = IntMockPacketCodec(ownerPeerInfo, peerInfos)

    override fun shutdown() {
        if (!isShutdown) {
            peer.shutdown()
            isShutdown = true
        }
    }
}
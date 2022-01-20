package net.postchain.network.peer

import net.postchain.network.common.NodeConnection

typealias PeerConnection = NodeConnection<PeerPacketHandler, PeerConnectionDescriptor>

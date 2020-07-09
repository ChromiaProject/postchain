// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.x

import net.postchain.core.ByteArrayKey
import net.postchain.core.Shutdownable

typealias XPeerID = ByteArrayKey
typealias XPacketHandler = (data: ByteArray, peerId: XPeerID) -> Unit
typealias LazyPacket = () -> ByteArray

interface XConnectionManager : NetworkTopology, Shutdownable {
    fun connectChain(chainPeersConfig: XChainPeersConfiguration, autoConnectAll: Boolean, loggingPrefix: () -> String)
    fun connectChainPeer(chainId: Long, targetPeerId: XPeerID)              // TODO: [POS-129]: Tests only
    fun isPeerConnected(chainId: Long, targetPeerId: XPeerID): Boolean      // TODO: [POS-129]: Tests only
    fun getConnectedPeers(chainId: Long): List<XPeerID>                     // TODO: [POS-129]: Tests only
    fun sendPacket(data: LazyPacket, chainId: Long, targetPeerId: XPeerID)
    fun broadcastPacket(data: LazyPacket, chainId: Long)
    fun disconnectChainPeer(chainId: Long, targetPeerId: XPeerID)           // TODO: [POS-129]: Tests only
    fun disconnectChain(chainId: Long, loggingPrefix: () -> String)
}

interface NetworkTopology {
    fun getPeersTopology(): Map<String, Map<String, String>>
    fun getPeersTopology(chainId: Long): Map<XPeerID, String>
}
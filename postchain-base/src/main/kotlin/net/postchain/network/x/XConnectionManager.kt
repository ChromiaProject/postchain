// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.x

import net.postchain.core.ByteArrayKey
import net.postchain.core.Shutdownable

typealias XPeerID = ByteArrayKey
typealias XPacketHandler = (data: ByteArray, peerId: XPeerID) -> Unit

typealias LazyPacket = () -> ByteArray

interface XConnectionManager : NetworkTopology, Shutdownable {
    fun connectChain(chainPeersConfig: XChainPeersConfiguration, autoConnectAll: Boolean, loggingPrefix: () -> String)
    fun connectChainPeer(chainId: Long, peerId: XPeerID)
    fun isPeerConnected(chainId: Long, peerId: XPeerID): Boolean
    fun getConnectedPeers(chainId: Long): List<XPeerID>
    fun sendPacket(data: LazyPacket, chainId: Long, peerId: XPeerID)
    fun broadcastPacket(data: LazyPacket, chainId: Long)
    fun disconnectChainPeer(chainId: Long, peerId: XPeerID)
    fun disconnectChain(chainId: Long, loggingPrefix: () -> String)
}

interface NetworkTopology {
    fun getPeersTopology(): Map<String, Map<String, String>>
    fun getPeersTopology(chainID: Long): Map<XPeerID, String>
}
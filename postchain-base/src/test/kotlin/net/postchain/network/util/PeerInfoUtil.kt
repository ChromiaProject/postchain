package net.postchain.network.util

import net.postchain.base.PeerInfo
import java.net.ServerSocket

fun peerInfoFromPublicKey(pubKey: ByteArray): PeerInfo {
    val availableSocket = ServerSocket(0).apply { close() }
    return PeerInfo(availableSocket.inetAddress.hostName, availableSocket.localPort, pubKey)
}

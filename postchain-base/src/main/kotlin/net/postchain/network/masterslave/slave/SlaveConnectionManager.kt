package net.postchain.network.masterslave.slave

import mu.KLogging
import net.postchain.network.x.XConnectionManager
import net.postchain.network.x.XPeerID

/**
 * [SlaveConnectionManager] has only one connection; it's connection to
 * [net.postchain.network.masterslave.master.MasterConnectionManager] of master node.
 */
interface SlaveConnectionManager : XConnectionManager

/**
 * TODO: [POS-129]: Provide kdoc
 */
abstract class RestrictedSlaveConnectionManager : SlaveConnectionManager {

    companion object : KLogging()

    private fun logNotImplementedError() = logger.error { "Not implemented" }

    override fun connectChainPeer(chainId: Long, peerId: XPeerID) {
        logNotImplementedError()
    }

    override fun isPeerConnected(chainId: Long, peerId: XPeerID): Boolean {
        logNotImplementedError()
        return false
    }

    override fun getConnectedPeers(chainId: Long): List<XPeerID> {
        logNotImplementedError()
        return emptyList()
    }

    override fun disconnectChainPeer(chainId: Long, peerId: XPeerID) {
        logNotImplementedError()
    }

    override fun getPeersTopology(): Map<String, Map<String, String>> {
        logNotImplementedError()
        return emptyMap()
    }

    override fun getPeersTopology(chainID: Long): Map<XPeerID, String> {
        logNotImplementedError()
        return emptyMap()
    }
}

package net.postchain.network.mastersub.master

import net.postchain.base.CryptoSystem
import net.postchain.config.node.NodeConfig
import net.postchain.network.XPacketDecoderFactory
import net.postchain.network.XPacketEncoderFactory
import net.postchain.network.peer.DefaultPeerConnectionManager
import net.postchain.network.peer.PeerConnectionManager

/**
 * This class makes:
 * - the [ConnectionManager], needed for the peer network and
 * - the [MasterConnectionManager] needed for the connections with the sub-nodes
 *
 * Note that when the node shuts down, both these Conn Mgrs should shut down.
 */
class MasterConnectionManagerFactory<PacketType>(
        packetEncoderFactory: XPacketEncoderFactory<PacketType>,
        packetDecoderFactory: XPacketDecoderFactory<PacketType>,
        cryptoSystem: CryptoSystem,
        val nodeConfig: NodeConfig
) {

    private val peerConnectionManager = DefaultPeerConnectionManager(
            packetEncoderFactory,
            packetDecoderFactory,
            cryptoSystem
    )

    private val masterConnectionManager = DefaultMasterConnectionManager(
            nodeConfig
    )

    /**
     * To access the peer network
     */
    fun getPeerConnectionManager(): PeerConnectionManager = peerConnectionManager

    /**
     * To access the sub-nodes
     */
    fun getMasterConnectionManager(): MasterConnectionManager = masterConnectionManager

}

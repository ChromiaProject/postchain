package net.postchain.network.mastersub.subnode

import net.postchain.base.PeerCommConfiguration

interface SubPeerCommConfig : PeerCommConfiguration {
    val peers: List<ByteArray>
}
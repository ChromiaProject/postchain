package net.postchain.network.masterslave.slave

import net.postchain.base.PeerCommConfiguration

interface SlavePeerCommConfig : PeerCommConfiguration {
    val peers: List<ByteArray>
}
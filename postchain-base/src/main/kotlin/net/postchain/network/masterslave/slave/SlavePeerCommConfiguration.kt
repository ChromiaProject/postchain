package net.postchain.network.masterslave.slave

import net.postchain.base.PeerCommConfiguration

interface SlavePeerCommConfiguration : PeerCommConfiguration {
    val singers: List<ByteArray>
}
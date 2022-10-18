package net.postchain.network.mastersub.subnode

import net.postchain.base.PeerCommConfiguration

class DefaultSubPeerCommConfig(
        configuration: PeerCommConfiguration,
        override val peers: List<ByteArray>
) : PeerCommConfiguration by configuration, SubPeerCommConfig

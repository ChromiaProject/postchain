package net.postchain.network.netty

import net.postchain.base.CryptoSystem
import net.postchain.base.PeerInfo
import net.postchain.network.*
import net.postchain.network.x.XConnector
import net.postchain.network.x.XConnectorEvents
import net.postchain.network.x.XConnectorFactory

class NettyConnectorFactory(val encryptionEnabled: Boolean = false): XConnectorFactory {
    override fun createConnector(myPeerInfo: PeerInfo,
                                 identPacketConverter: IdentPacketConverter,
                                 eventReceiver: XConnectorEvents, cryptoSystem: CryptoSystem)
        = NettyConnector(myPeerInfo, eventReceiver, identPacketConverter, cryptoSystem, encryptionEnabled)
}
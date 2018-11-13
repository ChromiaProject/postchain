package net.postchain.network.x

import com.nhaarman.mockitokotlin2.mock
import net.postchain.base.BasePeerCommConfiguration
import net.postchain.base.PeerInfo
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.ebft.EbftPacketConverter
import net.postchain.network.PacketConverter
import net.postchain.test.KeyPairHelper

class IntegrationTestContext(
        connectorFactory: XConnectorFactory,
        blockchainRid: ByteArray,
        peerInfos: Array<PeerInfo>,
        myIndex: Int,
        packetConverter: PacketConverter<Int>
) {
    val peerCommunicationConfig = BasePeerCommConfiguration(peerInfos, blockchainRid, myIndex, mock(), KeyPairHelper.privKey(myIndex))
    val connectionManager = DefaultXConnectionManager(connectorFactory, peerInfos[myIndex], packetConverter, SECP256K1CryptoSystem())
    val communicationManager = DefaultXCommunicationManager(connectionManager, peerCommunicationConfig, 1L, packetConverter)
}

class IntegrationTestContext2(
        connectorFactory: XConnectorFactory,
        blockchainRid: ByteArray,
        peerInfos: Array<PeerInfo>,
        myIndex: Int,
        packetConverter: EbftPacketConverter
) {
    val peerCommunicationConfig = BasePeerCommConfiguration(peerInfos, blockchainRid, myIndex, mock(), KeyPairHelper.privKey(myIndex))
    val connectionManager = DefaultXConnectionManager(connectorFactory, peerInfos[myIndex], packetConverter, SECP256K1CryptoSystem())
    val communicationManager = DefaultXCommunicationManager(connectionManager, peerCommunicationConfig, 1L, packetConverter)
}
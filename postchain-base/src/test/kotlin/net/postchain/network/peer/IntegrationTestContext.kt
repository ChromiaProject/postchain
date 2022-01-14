// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import org.mockito.kotlin.mock
import net.postchain.base.BasePeerCommConfiguration
import net.postchain.base.PeerInfo
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.devtools.KeyPairHelper

class IntegrationTestContext(
        peerInfos: Array<PeerInfo>,
        myIndex: Int
) {
    val peerCommunicationConfig = BasePeerCommConfiguration.build(
            peerInfos, mock(), KeyPairHelper.privKey(myIndex), KeyPairHelper.pubKey(myIndex))

    val connectionManager = DefaultPeerConnectionManager<Int>(
            mock(), mock(), SECP256K1CryptoSystem())

    val communicationManager = DefaultPeerCommunicationManager<Int>(
            connectionManager, peerCommunicationConfig, 1L, mock(), mock(), mock(), mock())

    fun shutdown() {
        communicationManager.shutdown()
        connectionManager.shutdown()
    }
}
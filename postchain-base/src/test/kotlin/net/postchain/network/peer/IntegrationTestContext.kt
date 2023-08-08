// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import net.postchain.base.BasePeerCommConfiguration
import net.postchain.base.PeerInfo
import net.postchain.config.app.AppConfig
import net.postchain.crypto.devtools.KeyPairHelper
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class IntegrationTestContext(
        peerInfos: Array<PeerInfo>,
        myIndex: Int
) {
    val appConfig1: AppConfig = mock {
        on { cryptoSystem } doReturn mock()
        on { privKeyByteArray } doReturn KeyPairHelper.privKey(myIndex)
        on { pubKeyByteArray } doReturn KeyPairHelper.pubKey(myIndex)
    }
    val peerCommunicationConfig = BasePeerCommConfiguration.build(
            peerInfos, appConfig1)

    val connectionManager = DefaultPeerConnectionManager<Int>(
            mock(), mock())

    val communicationManager = DefaultPeerCommunicationManager<Int>(
            connectionManager, peerCommunicationConfig, 1L, mock(), mock(), mock()
    )

    fun shutdown() {
        communicationManager.shutdown()
        connectionManager.shutdown()
    }
}
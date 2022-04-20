// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import net.postchain.base.NetworkNodes
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.PeerID
import net.postchain.base.PeerInfo
import net.postchain.crypto.Key
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Verifier

/**
 * A stupid stub only used in testing (to avoid the need for mocking)
 */
open class PeerCommConfigurationDummy: PeerCommConfiguration {
    override val networkNodes: NetworkNodes = NetworkNodes.buildNetworkNodesDummy()
    override val pubKey: Key = Key(ByteArray(1))
    override fun resolvePeer(peerID: PeerID): PeerInfo? = null
    override fun myPeerInfo(): PeerInfo = throw NotImplementedError("Not impl")
    override fun sigMaker(): SigMaker = throw NotImplementedError("Not impl")
    override fun verifier(): Verifier = throw NotImplementedError("Not impl")
}


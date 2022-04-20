// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.crypto.Key
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Verifier

interface PeerResolver {
    fun resolvePeer(peerID: PeerID): PeerInfo?
}

interface PeerCommConfiguration : PeerResolver {
    val networkNodes: NetworkNodes
    val pubKey: Key
    fun myPeerInfo(): PeerInfo
    fun sigMaker(): SigMaker
    fun verifier(): Verifier
}

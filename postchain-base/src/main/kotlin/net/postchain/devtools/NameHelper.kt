// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools

import net.postchain.base.BlockchainRid
import net.postchain.common.toHex
import net.postchain.network.x.XPeerID

object NameHelper {

    fun peerName(pubKey: String): String = shorten(pubKey)

    fun peerName(pubKey: ByteArray): String = shorten(pubKey.toHex())

    fun peerName(peerId: XPeerID): String = shorten(peerId.toString())

    fun shortHash(byteArrayHash: ByteArray): String = shorten(byteArrayHash.toHex())

    fun blockchainProcessName(pubKey: String, blockchainRid: BlockchainRid): String {
        return "[${shorten(pubKey)}/${shorten(blockchainRid.toHex())}]"
    }

    private fun shorten(word: String) = word.take(8)
}
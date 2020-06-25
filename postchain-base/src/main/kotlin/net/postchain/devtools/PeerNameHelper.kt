// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools

import net.postchain.common.toHex
import net.postchain.network.x.XPeerID

object PeerNameHelper {

    fun peerName(pubKey: String, delimiter: String = ":"): String = shorten(pubKey, delimiter)

    fun peerName(pubKey: ByteArray): String = shorten(pubKey.toHex())

    fun peerName(peerId: XPeerID): String = shorten(peerId.toString())

    fun shortHash(byteArrayHash: ByteArray): String = shorten(byteArrayHash.toHex())

    private fun shorten(word: String, delimiter: String = ":") = word.run {
        "${take(2)}$delimiter${takeLast(2)}"
    }
}
package net.postchain.devtools

import net.postchain.common.toHex
import net.postchain.network.x.XPeerID

object PeerNameHelper {

    fun peerName(pubKey: String): String = shorten(pubKey)

    fun peerName(pubKey: ByteArray): String = shorten(pubKey.toHex())

    fun peerName(peerId: XPeerID): String = shorten(peerId.toString())

    fun shortHash(byteArrayHash: ByteArray): String = shorten(byteArrayHash.toHex())

    private fun shorten(word: String) = word.run {
        "${take(2)}:${takeLast(2)}"
    }
}
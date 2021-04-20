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

    /**
     * In tests, last part of brid is used to identify chains, so include that in the shortend name.
     */
    fun blockchainProcessName(pubKey: String, blockchainRid: BlockchainRid): String {
        return "[${shorten(pubKey)}/${blockchainRid.toHex().take(2)}:${blockchainRid.toHex().takeLast(3)}]"
    }

    private fun shorten(word: String) = word.take(8)
}
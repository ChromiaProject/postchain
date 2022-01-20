// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools

import net.postchain.common.toHex
import net.postchain.core.NodeRid

object NameHelper {

    fun peerName(pubKey: String): String = shorten(pubKey)

    fun peerName(pubKey: ByteArray): String = shorten(pubKey.toHex())

    fun peerName(peerId: NodeRid): String = shorten(peerId.toString())

    fun shortHash(byteArrayHash: ByteArray): String = shorten(byteArrayHash.toHex())

    private fun shorten(word: String) = word.take(8)
}
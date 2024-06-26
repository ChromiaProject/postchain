// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.crypto.devtools

import net.postchain.common.toHex
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PrivKey
import net.postchain.crypto.PubKey
import net.postchain.crypto.secp256k1_derivePubKey

/**
 * A cache that maps an "index" to pairs of pub and private keys (The "index" is a "node index" in the context
 * of Postchain, but could in theory be anything.)
 * The smart thing about this cache is that if the pub/priv keys are not known they are calculated from the index and
 * then put into the chache.
 *
 * Note: Clearly this class should only be used for tests. In real code the keys should not be calculated from an index.
 */
object KeyPairHelper : KeyPairCache {

    private val privKeys = mutableMapOf<Int, Pair<ByteArray, String>>()
    private val pubKeys = mutableMapOf<Int, Pair<ByteArray, String>>()
    private val pubKeyHexToIndex = mutableMapOf<String, Int>()

    init {
        for (i in 0..10) {
            pubKey(i)
        }
    }

    override fun privKey(pubKey: ByteArray): ByteArray {
        return privKeys[pubKeyHexToIndex[pubKey.toHex()]]!!.first
    }

    override fun privKey(index: Int): ByteArray {
        return getCachedPrivKey(index).first
    }

    override fun privKeyHex(index: Int): String {
        return getCachedPrivKey(index).second
    }

    override fun pubKey(index: Int): ByteArray {
        return getCachedPubKey(index).first
    }

    override fun pubKeyHex(index: Int): String {
        return getCachedPubKey(index).second
    }

    fun keyPair(index: Int): KeyPair {
        return KeyPair(PubKey(pubKey(index)), PrivKey(privKey(index)))
    }

    // TODO: [olle] Is there any way to do the same smart calculation? No fun if we return "null" here
    override fun pubKeyFromByteArray(pubKeyHex: String): Int? {
        return pubKeyHexToIndex[pubKeyHex]
    }

    private fun getCachedPrivKey(index: Int): Pair<ByteArray, String> {
        return privKeys.getOrPut(index) {
            // private key index 0 is all zeroes except byte16 which is 1
            // private key index 12 is all 12:s except byte16 which is 1
            // reason for byte16 = 1 is that private key cannot be all zeroes
            // exception: for index == -1 byte15 is used not to exceed the max private key value
            ByteArray(32) { index.toByte() }
                    .apply { set(if (index == -1) 15 else 16, 1) }
                    .let { it to it.toHex() }
        }
    }

    private fun getCachedPubKey(index: Int): Pair<ByteArray, String> {
        val foundPubKey = pubKeys[index]
        if (foundPubKey != null) {
            return foundPubKey
        } else {
            val calculatedPair = secp256k1_derivePubKey(privKey(index)).let { it to it.toHex() }
            updatePubKeyMaps(index, calculatedPair)
            return calculatedPair
        }
    }

    private fun updatePubKeyMaps(index: Int, pair: Pair<ByteArray, String>) {
        pubKeys[index] = pair
        pubKeyHexToIndex[pair.second] = index
    }

}
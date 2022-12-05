// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.crypto.devtools

import net.postchain.common.toHex
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.secp256k1_derivePubKey

/**
 * A cache that maps an "index" to pairs of pub and private keys (The "index" is a "node index" in the context
 * of Postchain, but could in theory be anything.)
 *
 * Note: Clearly this class should only be used for tests.
 */
object KeyPairHelper {

    val cryptoSystem = Secp256K1CryptoSystem()

    private val privKeys = mutableMapOf<Int, Pair<ByteArray, String>>()
    private val pubKeys = mutableMapOf<Int, Pair<ByteArray, String>>()
    private val pubKeyHexToIndex = mutableMapOf<String, Int>()

    // TODO Olle POS-114 Note A bit sad that I had to do this, but it's the usage of [pubKeyFromByteArray()] from BlockchainSetupFactory that breaks
    init {
        for (i in 0..10) {
            pubKey(i)
        }
    }

    fun privKey(pubKey: ByteArray): ByteArray = privKeys[pubKeyHexToIndex[pubKey.toHex()]]!!.first

    fun privKey(index: Int): ByteArray = getCachedPrivKey(index).first

    fun privKeyHex(index: Int): String = getCachedPrivKey(index).second

    fun pubKey(index: Int): ByteArray = getCachedPubKey(index).first

    fun pubKeyHex(index: Int): String = getCachedPubKey(index).second

    // TODO: [olle] Is there any way to do the same smart calculation? No fun if we return "null" here
    fun pubKeyFromByteArray(pubKeyHex: String): Int? = pubKeyHexToIndex[pubKeyHex]

    private fun getCachedPrivKey(index: Int): Pair<ByteArray, String> {
        val privKey = privKeys.getOrPut(index) {
            val generatedPrivKey = cryptoSystem.generatePrivKey()
            generatedPrivKey.data to generatedPrivKey.data.toHex()
        }
        val pubKey = secp256k1_derivePubKey(privKey.first)
        pubKeys[index] = pubKey to pubKey.toHex()
        return privKey
    }

    private fun getCachedPubKey(index: Int): Pair<ByteArray, String> {
        val foundPubKey = pubKeys[index]
        return if (foundPubKey != null) {
            foundPubKey
        } else {
            val calculatedPair = secp256k1_derivePubKey(privKey(index)).let { it to it.toHex() }
            updatePubKeyMaps(index, calculatedPair)
            calculatedPair
        }
    }

    private fun updatePubKeyMaps(index: Int, pair: Pair<ByteArray, String>) {
        pubKeys[index] = pair
        pubKeyHexToIndex[pair.second] = index
    }
}

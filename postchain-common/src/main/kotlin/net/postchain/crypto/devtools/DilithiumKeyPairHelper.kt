package net.postchain.crypto.devtools

import net.postchain.common.toHex
import net.postchain.crypto.DilithiumCryptoSystem
import net.postchain.crypto.PrivKey
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumParameters
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec
import java.security.KeyPairGenerator
import java.security.SecureRandom

/**
 * Note: For test usage only.
 *
 * TODO: Can we reduce code duplication between this and [KeyPairHelper]?
 */
object DilithiumKeyPairHelper : KeyPairCache {
    private val privKeys = mutableMapOf<Int, Pair<ByteArray, String>>()
    private val pubKeys = mutableMapOf<Int, Pair<ByteArray, String>>()
    private val pubKeyHexToIndex = mutableMapOf<String, Int>()
    private val keyPairGenerator: KeyPairGenerator
    private val cryptoSystem = DilithiumCryptoSystem()

    init {
        // Fixed seed so we always generate the same keys
        val scr = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(ByteArray(1)) }
        keyPairGenerator = KeyPairGenerator.getInstance(DilithiumCryptoSystem.algorithm, DilithiumCryptoSystem.provider).apply {
            initialize(DilithiumParameterSpec.fromName(DilithiumParameters.dilithium2.name), scr)
        }

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

    override fun pubKeyFromByteArray(pubKeyHex: String): Int? {
        return pubKeyHexToIndex[pubKeyHex]
    }

    private fun getCachedPrivKey(index: Int): Pair<ByteArray, String> {
        return privKeys.getOrPut(index) {
            keyPairGenerator.generateKeyPair().private.encoded.let { it to it.toHex() }
        }
    }

    private fun getCachedPubKey(index: Int): Pair<ByteArray, String> {
        val foundPubKey = pubKeys[index]
        return if (foundPubKey != null) {
            foundPubKey
        } else {
            val calculatedPair = cryptoSystem.derivePubKey(PrivKey(privKey(index))).let { it.data to it.data.toHex() }
            updatePubKeyMaps(index, calculatedPair)
            calculatedPair
        }
    }

    private fun updatePubKeyMaps(index: Int, pair: Pair<ByteArray, String>) {
        pubKeys[index] = pair
        pubKeyHexToIndex[pair.second] = index
    }
}

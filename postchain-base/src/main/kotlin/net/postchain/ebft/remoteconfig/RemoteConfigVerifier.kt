package net.postchain.ebft.remoteconfig

import net.postchain.common.data.Hash
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash

object RemoteConfigVerifier {

    private val cryptoSystem = Secp256K1CryptoSystem()
    private val merkleHashCalculator = GtvMerkleHashCalculator(cryptoSystem)

    fun calculateHash(rawConfig: ByteArray): Hash {
        return GtvDecoder.decodeGtv(rawConfig).merkleHash(merkleHashCalculator)
    }

    fun verify(rawConfig: ByteArray, hash: Hash): Boolean {
        val hash0 = try {
            calculateHash(rawConfig)
        } catch (e: Exception) {
            return false
        }

        return hash0.contentEquals(hash)
    }
}
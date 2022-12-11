package net.postchain.containers.bpm.bcconfig

import net.postchain.common.data.Hash
import net.postchain.config.app.AppConfig
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash

class BlockchainConfigVerifier(val appConfig: AppConfig) {

    private val merkleHashCalculator = GtvMerkleHashCalculator(appConfig.cryptoSystem)

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
package net.postchain.containers.bcconfig

import assertk.assert
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.isContentEqualTo
import net.postchain.containers.bpm.bcconfig.SubnodeBlockchainConfigVerifier
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SubnodeBlockchainConfigVerifierTest {

    private val cryptoSystem = Secp256K1CryptoSystem()
    private val merkleHashCalculator = GtvMerkleHashCalculator(cryptoSystem)

    @Test
    fun testCalculateHash_of_validConfig() {
        val config = GtvFactory.gtv("valid config")
        val configRaw = GtvEncoder.encodeGtv(config)

        val expected = config.merkleHash(merkleHashCalculator)
        val actual = SubnodeBlockchainConfigVerifier.calculateHash(configRaw)

        assert(actual).isContentEqualTo(expected)
    }

    @Test
    fun testCalculateHash_of_invalidConfig() {
        assertThrows<Exception> {
            SubnodeBlockchainConfigVerifier.calculateHash(byteArrayOf())
        }
    }

    @Test
    fun testVerify_Success() {
        val config = GtvFactory.gtv("valid config")
        val configRaw = GtvEncoder.encodeGtv(config)
        val hash = SubnodeBlockchainConfigVerifier.calculateHash(configRaw)

        val verified = SubnodeBlockchainConfigVerifier.verify(configRaw, hash)
        assert(verified).isTrue()
    }

    @Test
    fun testVerify_Failure() {
        val config = GtvFactory.gtv("valid config")
        val configRaw = GtvEncoder.encodeGtv(config)
        val hash = SubnodeBlockchainConfigVerifier.calculateHash(configRaw)
        val corruptedConfigRaw = configRaw.dropLast(1).toByteArray()

        val verified = SubnodeBlockchainConfigVerifier.verify(corruptedConfigRaw, hash)
        assert(verified).isFalse()
    }
}
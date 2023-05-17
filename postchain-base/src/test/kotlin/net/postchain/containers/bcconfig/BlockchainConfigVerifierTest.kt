package net.postchain.containers.bcconfig

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.isContentEqualTo
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.bcconfig.BlockchainConfigVerifier
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class BlockchainConfigVerifierTest {

    private val appConfig: AppConfig = mock {
        on { cryptoSystem } doReturn Secp256K1CryptoSystem()
    }
    private val sut = BlockchainConfigVerifier(appConfig)

    private val cryptoSystem = Secp256K1CryptoSystem()
    private val merkleHashCalculator = GtvMerkleHashCalculator(cryptoSystem)

    @Test
    fun testCalculateHash_of_validConfig() {
        val config = GtvFactory.gtv("valid config")
        val configRaw = GtvEncoder.encodeGtv(config)

        val expected = config.merkleHash(merkleHashCalculator)
        val actual = sut.calculateHash(configRaw)

        assertThat(actual).isContentEqualTo(expected)
    }

    @Test
    fun testCalculateHash_of_invalidConfig() {
        assertThrows<Exception> {
            sut.calculateHash(byteArrayOf())
        }
    }

    @Test
    fun testVerify_Success() {
        val config = GtvFactory.gtv("valid config")
        val configRaw = GtvEncoder.encodeGtv(config)
        val hash = sut.calculateHash(configRaw)

        val verified = sut.verify(configRaw, hash)
        assertThat(verified).isTrue()
    }

    @Test
    fun testVerify_Failure() {
        val config = GtvFactory.gtv("valid config")
        val configRaw = GtvEncoder.encodeGtv(config)
        val hash = sut.calculateHash(configRaw)
        val corruptedConfigRaw = configRaw.dropLast(1).toByteArray()

        val verified = sut.verify(corruptedConfigRaw, hash)
        assertThat(verified).isFalse()
    }
}
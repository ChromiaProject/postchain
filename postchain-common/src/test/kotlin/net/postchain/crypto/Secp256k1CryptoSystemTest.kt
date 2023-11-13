package net.postchain.crypto

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException

class Secp256k1CryptoSystemTest {
    val sut = Secp256K1CryptoSystem()

    @Test
    fun testSignVerify() {
        for (i in 0..39) {
            val keyPair = sut.generateKeyPair()
            val sigMaker = sut.buildSigMaker(keyPair)
            val data = "Hello".toByteArray()
            val signature = sigMaker.signMessage(data) // TODO: POS-04_sig ???
            val verifier = sut.makeVerifier()
            assertThat(verifier(data, signature), "Positive test failed for privkey ${keyPair.privKey.data.toHex()}").isTrue()
            assertThat(verifier("Hell0".toByteArray(), signature), "Negative test failed for privkey ${keyPair.privKey.data.toHex()}").isFalse()
        }
    }

    @Test
    fun validPubKey() {
        assertThat(sut.validatePubKey("02DBBD2B3466D1B65FD16DD7556DBF44C46ED0D20DE83F70C14C5A7733F923556A".hexStringToByteArray())).isTrue()
    }

    @Test
    fun invalidPubKey() {
        assertThat(sut.validatePubKey("025645654674545654645678786956745932557475856935675675699566774576".hexStringToByteArray())).isFalse()
    }

    @Test
    fun emptyPubKey() {
        assertThat(sut.validatePubKey("".hexStringToByteArray())).isFalse()
    }

    @Test
    fun tooShortPubKey() {
        assertThat(sut.validatePubKey("02030405".hexStringToByteArray())).isFalse()
    }

    @Test
    fun validLongPubKey() {
        assertThat(sut.validatePubKey(
                "041B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F70BEAF8F588B541507FED6A642C5AB42DFDF8120A7F639DE5122D47A69A8E8D1".hexStringToByteArray()
        )).isTrue()
    }

    @Test
    fun invalidLongPubKey() {
        assertThat(sut.validatePubKey(
                "042B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F70BEAF8F588B541507FED6A642C5AB42DFDF8120A7F639DE5122D47A69A8E8D1".hexStringToByteArray()
        )).isFalse()
    }

    @Test
    fun `generates correct keypair and mnemonic size`() {
        val (keyPair, mnemonic) = sut.generateKeyPairWithMnemonic()

        Assertions.assertEquals(mnemonic.split(" ").size, 24)
        Assertions.assertEquals(keyPair.pubKey.data.size, 33)
        Assertions.assertEquals(keyPair.privKey.data.size, 32)
    }

    @Test
    fun `generates correct key from a given 24 word mnemonic`() {
        val mnemonicInput = "picnic shove leader great protect table leg witness walk night cable caution about produce engage armor first burden olive violin cube gentle bulk train"
        val (keyPair, mnemonic) = sut.recoverKeyPairFromMnemonic(mnemonicInput)

        Assertions.assertEquals(mnemonicInput, mnemonic)
        Assertions.assertEquals(keyPair.pubKey.data.size, 33)
        Assertions.assertEquals(keyPair.privKey.data.size, 32)
        Assertions.assertEquals(keyPair.pubKey.data.toHex(), "02AF635148608B9A18DF11241F1862624C3E7CCEDEC0864FEE00B3D4E7093CC4CF")
        Assertions.assertEquals(keyPair.privKey.data.toHex(), "CE59E2F0E7342EFB12A889B4168E3C7D909EC858849C0CA5FFAB78541C22AB65")
    }

    @Test
    fun `generates correct key from a given 12 word mnemonic`() {
        val mnemonicInput = "picnic shove leader great protect table leg witness walk night cable caution"
        val (keyPair, mnemonic) = sut.recoverKeyPairFromMnemonic(mnemonicInput)

        Assertions.assertEquals(mnemonicInput, mnemonic)
        Assertions.assertEquals(keyPair.pubKey.data.size, 33)
        Assertions.assertEquals(keyPair.privKey.data.size, 32)
        Assertions.assertEquals(keyPair.pubKey.data.toHex(), "02B0DDEE98F25AF559A101ED7E14085A054605EE4EADF3C53FDDDCE12FF038FEDD")
        Assertions.assertEquals(keyPair.privKey.data.toHex(), "3A98200C1203EFD1E0F2CEEB0F6A30D8F8BFA2A4DD3D6965FFD9A1D9AB8EE130")
    }

    @Test
    fun `should throw if mnemonic length is not 12 or 24` () {
        val thirteenLongMnemonic = "picnic shove leader great protect table leg witness walk night cable caution thirteen"
        val twentyFiveLongMnemonic = "picnic shove leader great protect table leg witness walk night cable caution about produce engage armor first burden olive violin cube gentle bulk train twenty-five"
        assertThrows<IllegalArgumentException> { sut.recoverKeyPairFromMnemonic(thirteenLongMnemonic) }
        assertThrows<IllegalArgumentException> { sut.recoverKeyPairFromMnemonic(twentyFiveLongMnemonic) }
    }

    @Test
    fun `we can sign with BIP39 generated keys`() {
        val sut = Secp256K1CryptoSystem()
        for (i in 0..39) {
            val (keyPair, _) = sut.generateKeyPairWithMnemonic()
            val sigMaker = sut.buildSigMaker(keyPair)
            val data = "Hello".toByteArray()
            val signature = sigMaker.signMessage(data) // TODO: POS-04_sig ???
            val verifier = sut.makeVerifier()
            assertThat(verifier(data, signature), "Positive test failed for privkey ${keyPair.privKey.data.toHex()}").isTrue()
            assertThat(verifier("Hell0".toByteArray(), signature), "Negative test failed for privkey ${keyPair.privKey.data.toHex()}").isFalse()
        }
    }
}

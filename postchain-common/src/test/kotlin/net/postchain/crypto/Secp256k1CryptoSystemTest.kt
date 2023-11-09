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
        Assertions.assertEquals(keyPair.pubKey.data.toHex(), "0388CCAFF64FC155ADC9DF75B1138D9CA2B33C7B605B16021E1CB7B7E6C8EB0FA4")
        Assertions.assertEquals(keyPair.privKey.data.toHex(), "F1779080FD92FD4B44CE4C3A14274CB1ED8BF11704BA2AD6C334252F45B9AD20")
    }

    @Test
    fun `generates correct key from a given 12 word mnemonic`() {
        val mnemonicInput = "picnic shove leader great protect table leg witness walk night cable caution"
        val (keyPair, mnemonic) = sut.recoverKeyPairFromMnemonic(mnemonicInput)

        Assertions.assertEquals(mnemonicInput, mnemonic)
        Assertions.assertEquals(keyPair.pubKey.data.size, 33)
        Assertions.assertEquals(keyPair.privKey.data.size, 32)
        Assertions.assertEquals(keyPair.pubKey.data.toHex(), "028E95DCD8CEBBEF598A5671C58CDBAC941FC9488764F6A9345D151CF1692FA29A")
        Assertions.assertEquals(keyPair.privKey.data.toHex(), "BB680527828123974992AB68E0A1B8187B87CE26B13008E79E643C5942CB1828")
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

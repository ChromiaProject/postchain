package net.postchain.crypto


import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.common.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException

class DeterministicKeygenTest {
    private val deterministicKeygen = DeterministicKeygen()

    @Test
    fun `generates correct keypair and mnemonic size`() {
        val (keyPair, mnemonic) = deterministicKeygen.createKeyPairWithMnemonic()

        assertEquals(mnemonic.split(" ").size, 24)
        assertEquals(keyPair.pubKey.data.size, 33)
        assertEquals(keyPair.privKey.data.size, 32)
    }

    @Test
    fun `generates correct key from a given 24 word mnemonic`() {
        val mnemonicInput = "picnic shove leader great protect table leg witness walk night cable caution about produce engage armor first burden olive violin cube gentle bulk train"
        val (keyPair, mnemonic) = deterministicKeygen.createKeyPairWithMnemonic(mnemonicInput)

        assertEquals(mnemonicInput, mnemonic)
        assertEquals(keyPair.pubKey.data.size, 33)
        assertEquals(keyPair.privKey.data.size, 32)
        assertEquals(keyPair.pubKey.data.toHex(), "03A1BB610A1AF00D1AC2765829E35DE6302F1BF249B46113BF52D2C97D3EDB15A9")
        assertEquals(keyPair.privKey.data.toHex(), "1310CFC4A192A1399350E24C4E948868B8DFD5C45919DC9DC9C3A23E2962A834")
    }

    @Test
    fun `generates correct key from a given 12 word mnemonic`() {
        val mnemonicInput = "picnic shove leader great protect table leg witness walk night cable caution"
        val (keyPair, mnemonic) = deterministicKeygen.createKeyPairWithMnemonic(mnemonicInput)

        assertEquals(mnemonicInput, mnemonic)
        assertEquals(keyPair.pubKey.data.size, 33)
        assertEquals(keyPair.privKey.data.size, 32)
        assertEquals(keyPair.pubKey.data.toHex(), "034FA11EB35F6975B279FD0EAABACB3B43F9C654F8C1F62AB504A4F18BD1E6B07F")
        assertEquals(keyPair.privKey.data.toHex(), "FA76C4AECCF5AAD36E6AE17D909671E957D8855B9D155879ACD1A208BFD35EA3")
    }

    @Test
    fun `should throw if mnemonic length is not 12 or 24` () {
        val thirteenLongMnemonic = "picnic shove leader great protect table leg witness walk night cable caution thirteen"
        val twentyFiveLongMnemonic = "picnic shove leader great protect table leg witness walk night cable caution about produce engage armor first burden olive violin cube gentle bulk train twenty-five"
        assertThrows<IllegalArgumentException> { deterministicKeygen.createKeyPairWithMnemonic(thirteenLongMnemonic) }
        assertThrows<IllegalArgumentException> { deterministicKeygen.createKeyPairWithMnemonic(twentyFiveLongMnemonic) }
    }

    @Test
    fun `we can sign with BIP39 generated keys`() {
        val sut = Secp256K1CryptoSystem()
        for (i in 0..39) {
            val (keyPair, _) = deterministicKeygen.createKeyPairWithMnemonic()
            val sigMaker = sut.buildSigMaker(keyPair)
            val data = "Hello".toByteArray()
            val signature = sigMaker.signMessage(data) // TODO: POS-04_sig ???
            val verifier = sut.makeVerifier()
            assertThat(verifier(data, signature), "Positive test failed for privkey ${keyPair.privKey.data.toHex()}").isTrue()
            assertThat(verifier("Hell0".toByteArray(), signature), "Negative test failed for privkey ${keyPair.privKey.data.toHex()}").isFalse()
        }
    }
}

package net.postchain.crypto


import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.common.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException

class Bip39SeedKeygenTest {
    private val bip39SeedKeygen = Bip39SeedKeygen()

    @Test
    fun `generates correct keypair and mnemonic size`() {
        val (keyPair, mnemonic) = bip39SeedKeygen.createKeyPairWithMnemonic()

        assertEquals(mnemonic.split(" ").size, 24)
        assertEquals(keyPair.pubKey.data.size, 33)
        assertEquals(keyPair.privKey.data.size, 32)
    }

    @Test
    fun `generates correct key from a given 24 word mnemonic`() {
        val mnemonicInput = "picnic shove leader great protect table leg witness walk night cable caution about produce engage armor first burden olive violin cube gentle bulk train"
        val (keyPair, mnemonic) = bip39SeedKeygen.createKeyPairWithMnemonic(mnemonicInput)

        assertEquals(mnemonicInput, mnemonic)
        assertEquals(keyPair.pubKey.data.size, 33)
        assertEquals(keyPair.privKey.data.size, 32)
        assertEquals(keyPair.pubKey.data.toHex(), "02FA62C6DA369E6ECBCC0CA092AA50103F424E597193345A130767FD2626FA0E89")
        assertEquals(keyPair.privKey.data.toHex(), "A7DA90DBE2536B7253D8E9682E4E37F61BEB302266E4AF28F32BE0DC555EC654")
    }

    @Test
    fun `generates correct key from a given 12 word mnemonic`() {
        val mnemonicInput = "picnic shove leader great protect table leg witness walk night cable caution"
        val (keyPair, mnemonic) = bip39SeedKeygen.createKeyPairWithMnemonic(mnemonicInput)

        assertEquals(mnemonicInput, mnemonic)
        assertEquals(keyPair.pubKey.data.size, 33)
        assertEquals(keyPair.privKey.data.size, 32)
        assertEquals(keyPair.pubKey.data.toHex(), "02FE3587EF1A6A3203221BBBB90F956EB20A54CD89A3EA406B0A8DF9A0506D0903")
        assertEquals(keyPair.privKey.data.toHex(), "F76D42CF4E1C902003D584898ACB94CCC0B259996214E33708CE87C27C3CE5B6")
    }

    @Test
    fun `should throw if mnemonic length is not 12 or 24` () {
        val thirteenLongMnemonic = "picnic shove leader great protect table leg witness walk night cable caution thirteen"
        val twentyFiveLongMnemonic = "picnic shove leader great protect table leg witness walk night cable caution about produce engage armor first burden olive violin cube gentle bulk train twenty-five"
        assertThrows<IllegalArgumentException> { bip39SeedKeygen.createKeyPairWithMnemonic(thirteenLongMnemonic) }
        assertThrows<IllegalArgumentException> { bip39SeedKeygen.createKeyPairWithMnemonic(twentyFiveLongMnemonic) }
    }

    @Test
    fun `we can sign with BIP39 generated keys`() {
        val sut = Secp256K1CryptoSystem()
        for (i in 0..39) {
            val (keyPair, _) = bip39SeedKeygen.createKeyPairWithMnemonic()
            val sigMaker = sut.buildSigMaker(keyPair)
            val data = "Hello".toByteArray()
            val signature = sigMaker.signMessage(data) // TODO: POS-04_sig ???
            val verifier = sut.makeVerifier()
            assertThat(verifier(data, signature), "Positive test failed for privkey ${keyPair.privKey.data.toHex()}").isTrue()
            assertThat(verifier("Hell0".toByteArray(), signature), "Negative test failed for privkey ${keyPair.privKey.data.toHex()}").isFalse()
        }
    }
}

package net.postchain.crypto

import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.crypto.MnemonicCode

class DeterministicKeygen {
    private val mnemonicInstance = MnemonicCode.INSTANCE

    fun createKeyPairWithMnemonic(): Pair<KeyPair, String> {
        val entropy = Secp256K1CryptoSystem().generatePrivKey().data
        val mnemonic = mnemonicInstance.toMnemonic(entropy)
        return createKeyPairWithMnemonic(mnemonic.joinToString(" "))
    }

    fun createKeyPairWithMnemonic(mnemonic: String): Pair<KeyPair, String> {
        validateMnemonic(mnemonic)
        val seed = MnemonicCode.toSeed(mnemonic.split(" "), " ")
        val privKey = HDKeyDerivation.createMasterPrivateKey(seed).privKeyBytes
        val pubKey = secp256k1_derivePubKey(privKey)
        val keyPair = KeyPair(PubKey(pubKey), PrivKey(privKey))
        return keyPair to mnemonic
    }

    private fun validateMnemonic(mnemonic: String) {
        val nrOfWords = mnemonic.split(" ").size
        if (nrOfWords != 12 && nrOfWords != 24) {
            throw IllegalArgumentException("Invalid number of words in mnemonic. Supported number of words are 12 or 24")
        }
    }
}

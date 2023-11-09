package net.postchain.crypto

import net.postchain.bitcoinj.forks.crypto.HDKeyDerivation
import net.postchain.bitcoinj.forks.crypto.MnemonicCode


class DeterministicKeygen {
    private val mnemonicInstance = MnemonicCode()

    fun createKeyPairWithMnemonic(): Pair<KeyPair, String> {
        val entropy = Secp256K1CryptoSystem().generatePrivKey().data
        val mnemonic = mnemonicInstance.toMnemonic(entropy)
        return createKeyPairWithMnemonic(mnemonic.joinToString(" "))
    }

    fun createKeyPairWithMnemonic(mnemonic: String): Pair<KeyPair, String> {
        validateMnemonic(mnemonic)
        val seed = mnemonicInstance.toSeed(mnemonic.split(" "), " ")
        val privKey = HDKeyDerivation.createMasterPrivateKey(seed)
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

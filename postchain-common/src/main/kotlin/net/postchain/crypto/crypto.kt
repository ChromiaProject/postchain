// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.crypto

import net.postchain.common.data.Hash

/**
 * Can sign digests/messages.
 */
interface SigMaker {

    /**
     * @param msg is raw binary data that should be the base for the signature (Usually the raw data will be digested
     *            before it's signed)
     * @return a [Signature] created using the specific algo of the implementation.
     */
    fun signMessage(msg: ByteArray): Signature

    /**
     * Note: To save CPU cycles you should call this method if you already have the digest and just need the signature.
     *
     * @param digest is the "hash" that should be signed
     * @return a [Signature] created using the specific algo of the implementation.
     */
    fun signDigest(digest: Hash): Signature
}

/**
 * Function that will return a boolean depending on if the data and
 * signature applied to that data will properly verify
 * */
typealias Verifier = (ByteArray, Signature) -> Boolean

/**
 * CryptoSystem implements necessary cryptographic functionalities.
 */
interface CryptoSystem {
    fun digest(bytes: ByteArray): ByteArray

    @Deprecated("Pass in KeyPair instead",
            ReplaceWith("buildSigMaker(KeyPair(pubKey, privKey))", imports = ["net.postchain.crypto.KeyPair"]))
    fun buildSigMaker(pubKey: ByteArray, privKey: ByteArray): SigMaker

    fun buildSigMaker(keyPair: KeyPair): SigMaker

    fun verifyDigest(digest: ByteArray, s: Signature): Boolean

    fun makeVerifier(): Verifier

    fun getRandomBytes(size: Int): ByteArray

    fun generateKeyPair(): KeyPair

    fun validatePubKey(pubKey: ByteArray): Boolean
}

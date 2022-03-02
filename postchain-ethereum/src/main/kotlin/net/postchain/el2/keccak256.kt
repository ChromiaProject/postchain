package net.postchain.el2

import net.postchain.base.MessageDigestFactory
import net.postchain.base.decompressKey
import net.postchain.common.data.Hash
import net.postchain.common.data.KECCAK256

/**
 * Get ethereum address from compress public key
 */
fun getEthereumAddress(compressedKey: ByteArray): ByteArray {
    val pubKey = decompressKey(compressedKey)
    return digest(pubKey).takeLast(20).toByteArray()
}

/**
 * Calculate the keccak256 hash digest of a message
 *
 * @param bytes A ByteArray of data consisting of the message we want the hash digest of
 * @return The keccak256 hash digest of [bytes]
 */
fun digest(bytes: ByteArray): Hash {
    val m = MessageDigestFactory.create(KECCAK256)
    return m.digest(bytes)
}

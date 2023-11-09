package net.postchain.crypto

import java.security.MessageDigest

/**
 * Calculate the hash digest of a message
 *
 * @param bytes A ByteArray of data consisting of the message we want the hash digest of
 * @return The hash digest of [bytes]
 */
fun sha256Digest(bytes: ByteArray): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(bytes)
}


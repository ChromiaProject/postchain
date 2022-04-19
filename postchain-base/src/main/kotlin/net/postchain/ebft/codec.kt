// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

import net.postchain.crypto.Verifier
import net.postchain.common.toHex
import net.postchain.core.Signature
import net.postchain.core.UserMistake
import net.postchain.crypto.SigMaker
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.SignedMessage

fun encodeAndSign(message: EbftMessage, sigMaker: SigMaker): ByteArray {
    val signature = sigMaker.signMessage(message.encoded) // TODO POS-04_sig I THINK this is one of the cases where we actually sign the data

    return SignedMessage(message, signature.subjectID, signature.data).encoded
}

fun decodeSignedMessage(bytes: ByteArray): SignedMessage {
    try {
        return SignedMessage.decode(bytes)
    } catch (e: Exception) {
        throw UserMistake("bytes ${bytes.toHex()} cannot be decoded", e)
    }
}

fun decodeWithoutVerification(bytes: ByteArray): SignedMessage {
    try {
        return SignedMessage.decode(bytes)
    } catch (e: Exception) {
        throw UserMistake("bytes cannot be decoded", e)
    }
}

fun decodeAndVerify(bytes: ByteArray, pubKey: ByteArray, verify: Verifier): EbftMessage {
    return tryDecodeAndVerify(bytes, pubKey, verify)
            ?: throw UserMistake("Verification failed")
}

fun decodeAndVerify(bytes: ByteArray, verify: Verifier): EbftMessage? {
    val message = SignedMessage.decode(bytes)
    val verified = verify(message.message.encoded, Signature(message.pubKey, message.signature))

    return if (verified) message.message else null
}

fun tryDecodeAndVerify(bytes: ByteArray, pubKey: ByteArray, verify: Verifier): EbftMessage? {
    val message = SignedMessage.decode(bytes)
    val verified = message.pubKey.contentEquals(pubKey)
            && verify(message.message.encoded, Signature(message.pubKey, message.signature))
    return if (verified) message.message
    else null
}

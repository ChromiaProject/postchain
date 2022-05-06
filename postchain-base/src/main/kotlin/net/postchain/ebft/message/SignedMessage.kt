package net.postchain.ebft.message

import net.postchain.common.toHex
import net.postchain.core.UserMistake
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv

class SignedMessage(val message: EbftMessage, val pubKey: ByteArray, val signature: ByteArray) {

    companion object {
        fun decode(bytes: ByteArray): SignedMessage {
            try {
                val gtvArray = GtvDecoder.decodeGtv(bytes) as GtvArray

                return SignedMessage(EbftMessage.decode(gtvArray[0].asByteArray()), gtvArray[1].asByteArray(), gtvArray[2].asByteArray())
            } catch (e: Exception) {
                throw UserMistake("bytes ${bytes.toHex()} cannot be decoded", e)
            }
        }
    }

    val topic get() = message.topic

    val encoded: ByteArray by lazy { GtvEncoder.encodeGtv(toGtv()) }

    fun toGtv() = gtv(gtv(message.encoded), gtv(pubKey), gtv(signature))
}

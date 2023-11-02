package net.postchain.ebft.message

import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.network.common.LazyPacket

class SignedMessage(val message: EbftMessage, val pubKey: ByteArray, val signature: ByteArray) {

    companion object {
        fun decode(bytes: ByteArray, ebftVersion: Long): SignedMessage {
            try {
                val gtvArray = GtvDecoder.decodeGtv(bytes) as GtvArray

                return SignedMessage(EbftMessage.decode(gtvArray[0].asByteArray(), ebftVersion), gtvArray[1].asByteArray(), gtvArray[2].asByteArray())
            } catch (e: Exception) {
                throw UserMistake("bytes ${bytes.toHex()} cannot be decoded", e)
            }
        }
    }

    val topic get() = message.topic

    fun encoded(ebftVersion: Long): LazyPacket = lazy { GtvEncoder.encodeGtv(toGtv(ebftVersion)) }

    fun toGtv(ebftVersion: Long) = gtv(gtv(message.encoded(ebftVersion).value), gtv(pubKey), gtv(signature))
}

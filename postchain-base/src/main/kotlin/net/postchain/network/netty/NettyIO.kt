package net.postchain.network.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.EventLoopGroup
import io.netty.handler.codec.LengthFieldPrepender
import mu.KLogging
import net.postchain.base.CryptoSystem

/**
 * ruslan.klymenko@zorallabs.com 19.10.18
 */
abstract class NettyIO(protected val group: EventLoopGroup,
                       protected val cryptoSystem: CryptoSystem) {

    companion object : KLogging() {
        val packetSizeLength = 4
        val framePrepender = LengthFieldPrepender(packetSizeLength)
        val keySizeBytes = 32

        fun readPacket(msg: Any): ByteArray {
            val inBuffer = msg as ByteBuf
            val bytes = ByteArray(inBuffer.readableBytes())
            inBuffer.readBytes(bytes)
            return bytes
        }


    }

    init {
        Thread({startSocket()}).start()
    }

    abstract fun startSocket()
}
class DecodedMessageHolder(val byteArray: ByteArray, val serial: Long)
package net.postchain.network.ref.netty

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import mu.KLogging
import net.postchain.network.AbstractPeerConnection
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

abstract class NettyPeerConnection : AbstractPeerConnection {

    protected var handlerContext: ChannelHandlerContext? = null

    protected val packetSizeLength = 4

    companion object : KLogging()

    abstract fun handlePacket(pkt: ByteArray)

    protected fun readOnePacket(msg: Any): ByteArray {
        val inBuffer = msg as ByteBuf
        val packetSizeHolder = ByteArray(packetSizeLength)
        inBuffer.readBytes(packetSizeHolder)
        val packetSize = ByteBuffer.wrap(packetSizeHolder).getInt()

        val bytes = ByteArray(packetSize)
        inBuffer.readBytes(bytes)
        return bytes
    }

    protected fun writeOnePacket(dataStream: ChannelHandlerContext, bytes: ByteArray) {
        val packetSizeBytes = ByteBuffer.allocate(packetSizeLength).putInt(bytes.size).array()
        dataStream.writeAndFlush(Unpooled.copiedBuffer(packetSizeBytes + bytes))
    }

    override fun stop() {
        // todo
    }

    val packetBuffer = LinkedBlockingQueue<ByteArray>()
    override fun sendPacket(b: ByteArray) {
        if(handlerContext == null) {
            packetBuffer.add(b)
        } else {
            while(!packetBuffer.isEmpty()) writeOnePacket(handlerContext!!, packetBuffer.poll())
            writeOnePacket(handlerContext!!, b)
        }
    }
}
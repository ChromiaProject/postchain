package net.postchain.network.ref.netty

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.util.CharsetUtil
import mu.KLogging
import net.postchain.network.AbstractPeerConnection
import net.postchain.network.MAX_QUEUED_PACKETS
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

abstract class NettyPeerConnection : AbstractPeerConnection {
    @Volatile
    protected var keepGoing: Boolean = true
    protected val outboundPackets = LinkedBlockingQueue<ByteArray>(MAX_QUEUED_PACKETS)

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

    protected fun writePacketsWhilePossible(dataStream: ChannelHandlerContext) {
        try {
            while (keepGoing) {
                val bytes = outboundPackets.take()
                if (keepGoing) {
                    writeOnePacket(dataStream, bytes)
                }
            }
        } catch (e: Exception) {
            logger.error(e.toString())
        }
    }

    @Synchronized
    override fun stop() {
        keepGoing = false
        outboundPackets.put(byteArrayOf())
    }

    override fun sendPacket(b: ByteArray) {
        if (!keepGoing) return
        if (outboundPackets.size >= MAX_QUEUED_PACKETS) {
            outboundPackets.poll()
        }
        outboundPackets.put(b)
    }
}
package net.postchain.network.ref.netty

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import mu.KLogging
import net.postchain.network.AbstractPeerConnection
import net.postchain.network.MAX_QUEUED_PACKETS
import java.util.concurrent.LinkedBlockingQueue

abstract class NettyPeerConnection : AbstractPeerConnection {
    @Volatile
    protected var keepGoing: Boolean = true
    private val outboundPackets = LinkedBlockingQueue<ByteArray>(MAX_QUEUED_PACKETS)

    companion object : KLogging()

    abstract fun handlePacket(pkt: ByteArray)

    protected fun readOnePacket(msg: Any): ByteArray {
        val inBuffer = msg as ByteBuf
        val bytes = ByteArray(inBuffer.readableBytes())
        inBuffer.readBytes(bytes)
        return bytes
    }

    protected fun readPacketsWhilePossible(msg: Any) {
        try {
            while (keepGoing) {
                val bytes = readOnePacket(msg)
                if (bytes.size == 0) {
                    continue
                }
                handlePacket(bytes)
            }
        } catch (e: Exception) {
            //outboundPackets.put(byteArrayOf())
            logger.error(e.toString())
        }
    }

    protected fun writeOnePacket(dataStream: ChannelHandlerContext, bytes: ByteArray) {
        dataStream.writeAndFlush(Unpooled.copiedBuffer(bytes))
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
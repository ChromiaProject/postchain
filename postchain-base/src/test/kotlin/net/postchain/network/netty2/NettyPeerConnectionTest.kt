package net.postchain.network.netty2

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import net.postchain.network.MAX_PAYLOAD_SIZE
import net.postchain.network.XPacketCodec
import net.postchain.network.common.LazyPacket
import net.postchain.network.peer.PeerConnectionDescriptor
import net.postchain.network.peer.PeerPacketHandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.concurrent.CompletableFuture

class NettyPeerConnectionTest {

    private class TestPeerConnection : NettyPeerConnection<ByteArray>(mock<XPacketCodec<ByteArray>>()) {
        fun sendSafely(ctx: ChannelHandlerContext, packet: LazyPacket) = sendPacketSafely(ctx, packet)

        override fun accept(handler: PeerPacketHandler) = Unit
        override fun sendPacket(packet: LazyPacket) = Unit
        override fun remoteAddress(): String = "test"
        override fun close(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)
        override fun descriptor(): PeerConnectionDescriptor = throw UnsupportedOperationException()
    }

    @Test
    fun `sendPacketSafely closes unwritable channel without evaluating packet`() {
        val channel = mock<Channel> {
            on { isWritable } doReturn false
        }
        val ctx = mock<ChannelHandlerContext> {
            on { channel() } doReturn channel
        }
        val packet = lazy<ByteArray> { error("packet must not be evaluated for a slow peer") }

        TestPeerConnection().sendSafely(ctx, packet)

        verify(ctx).close()
        verify(ctx, never()).writeAndFlush(any())
    }

    @Test
    fun `write buffer watermarks exceed the maximum frame size`() {
        assertThat(NettyPeerConnection.WRITE_BUFFER_LOW_WATER_MARK).isEqualTo(MAX_PAYLOAD_SIZE)
        assertThat(NettyPeerConnection.WRITE_BUFFER_HIGH_WATER_MARK).isEqualTo(2 * MAX_PAYLOAD_SIZE)
    }

    @Test
    fun `sendPacketSafely keeps writable channel open for consecutive large packets`() {
        val channel = mock<Channel> {
            on { isWritable } doReturn true
        }
        val ctx = mock<ChannelHandlerContext> {
            on { channel() } doReturn channel
        }
        val packet = lazy<ByteArray> { ByteArray(2 * 1024 * 1024) { 1 } }

        repeat(2) {
            TestPeerConnection().sendSafely(ctx, packet)
        }

        verify(ctx, times(2)).writeAndFlush(any())
        verify(ctx, never()).close()
    }

    @Test
    fun `sendPacketSafely writes packet when channel is writable`() {
        val channel = mock<Channel> {
            on { isWritable } doReturn true
        }
        val ctx = mock<ChannelHandlerContext> {
            on { channel() } doReturn channel
        }

        TestPeerConnection().sendSafely(ctx, lazy { byteArrayOf(1) })

        verify(ctx).writeAndFlush(any())
        verify(ctx, never()).close()
    }
}

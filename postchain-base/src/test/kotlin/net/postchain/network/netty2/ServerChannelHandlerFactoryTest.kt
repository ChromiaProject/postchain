package net.postchain.network.netty2

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.netty.channel.ChannelPipeline
import net.postchain.ebft.EbftPacketCodec
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class ServerChannelHandlerFactoryTest {

    @Test
    fun `if readHandshakeTimeout is greater 0, then ReadHandshakeTimeoutHandler will be added to the pipeline`() {
        // Mocks
        val config = ConnectionConfig()
        val pipeline: ChannelPipeline = mock()

        // SUT
        val sut = DefaultServerChannelHandlerFactory(config)
        val readHandshakeTimeoutHandlerCaptor = argumentCaptor<ReadHandshakeTimeoutHandler>()
        val nettyServerPeerConnectionCaptor = argumentCaptor<NettyServerPeerConnection<*>>()

        // Action
        sut.onPostInitChannelHandler(pipeline, mock<EbftPacketCodec>(), mock())

        // Asserts
        verify(pipeline, times(1)).addFirst(readHandshakeTimeoutHandlerCaptor.capture())
        verify(pipeline, times(1)).addLast(nettyServerPeerConnectionCaptor.capture())

        assertThat(readHandshakeTimeoutHandlerCaptor.firstValue.timeoutNanos).isEqualTo(10_000_000_000)
    }

    @Test
    fun `if readHandshakeTimeout is not greater 0, then ReadHandshakeTimeoutHandler will not be added to the pipeline`() {
        // Mocks
        val config = ConnectionConfig(readHandshakeTimeout = 0)
        val pipeline: ChannelPipeline = mock()

        // SUT
        val sut = DefaultServerChannelHandlerFactory(config)
        val nettyServerPeerConnectionCaptor = argumentCaptor<NettyServerPeerConnection<*>>()

        // Action
        sut.onPostInitChannelHandler(pipeline, mock<EbftPacketCodec>(), mock())

        // Asserts
        verify(pipeline, never()).addFirst(any<ReadHandshakeTimeoutHandler>())
        verify(pipeline, times(1)).addLast(nettyServerPeerConnectionCaptor.capture())
    }
}
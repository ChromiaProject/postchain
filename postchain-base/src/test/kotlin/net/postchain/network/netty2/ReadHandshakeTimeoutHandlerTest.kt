package net.postchain.network.netty2

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.netty.channel.embedded.EmbeddedChannel
import org.awaitility.Awaitility.await
import org.awaitility.Duration
import org.junit.jupiter.api.Test

class ReadHandshakeTimeoutHandlerTest {

    @Test
    fun `closes silent channel after active handshake timeout`() {
        val channel = EmbeddedChannel()
        channel.pipeline().addLast(
                ReadHandshakeTimeoutHandler(ConnectionConfig(readHandshakeTimeout = 20)) { false }
        )
        channel.pipeline().fireChannelActive()

        await().atMost(Duration.ONE_SECOND).untilAsserted {
            channel.runScheduledPendingTasks()
            assertThat(channel.isOpen).isFalse()
        }
    }

    @Test
    fun `does not close channel after handshake completed`() {
        val channel = EmbeddedChannel()
        channel.pipeline().addLast(
                ReadHandshakeTimeoutHandler(ConnectionConfig(readHandshakeTimeout = 20)) { true }
        )
        channel.pipeline().fireChannelActive()

        channel.runScheduledPendingTasks()
        assertThat(channel.isOpen).isTrue()
    }

    @Test
    fun `closes active channel when handshake handler is added after activation`() {
        val channel = EmbeddedChannel()
        channel.pipeline().fireChannelActive()
        channel.pipeline().addLast(
                ReadHandshakeTimeoutHandler(ConnectionConfig(readHandshakeTimeout = 20)) { false }
        )

        await().atMost(Duration.ONE_SECOND).untilAsserted {
            channel.runScheduledPendingTasks()
            assertThat(channel.isOpen).isFalse()
        }
    }
}

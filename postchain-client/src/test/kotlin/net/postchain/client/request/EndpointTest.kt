package net.postchain.client.request

import net.postchain.client.MockClock
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class EndpointTest {
    @Test
    fun `Endpoint reachability`() {
        val clock = MockClock()

        val endpoint = Endpoint("", clock)
        assertTrue(endpoint.isReachable())

        endpoint.setUnreachable(10.minutes)
        assertFalse(endpoint.isReachable())

        endpoint.setReachable()
        assertTrue(endpoint.isReachable())

        endpoint.setUnreachable(1.seconds)
        assertFalse(endpoint.isReachable())

        clock.current = clock.current.plusMillis(1001)
        assertTrue(endpoint.isReachable())
    }
}
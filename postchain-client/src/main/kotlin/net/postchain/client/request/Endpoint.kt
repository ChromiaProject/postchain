package net.postchain.client.request

import java.time.Clock
import java.time.Instant
import kotlin.time.Duration

class Endpoint(val url: String, private val clock: Clock = Clock.systemUTC()) {
    @Volatile
    private var whenReachable: Instant = Instant.EPOCH

    fun isReachable() = clock.instant().isAfter(whenReachable)

    fun setUnreachable(duration: Duration) {
        whenReachable = clock.instant().plusMillis(duration.inWholeMilliseconds)
    }

    fun setReachable() {
        whenReachable = Instant.EPOCH
    }
}

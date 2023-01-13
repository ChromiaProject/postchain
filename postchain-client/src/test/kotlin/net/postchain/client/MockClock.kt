package net.postchain.client

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MockClock : Clock() {
    var current: Instant = Instant.EPOCH.plusSeconds(1)

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = throw NotImplementedError()

    override fun instant(): Instant = current
}

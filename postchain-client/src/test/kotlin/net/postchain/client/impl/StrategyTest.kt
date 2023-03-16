package net.postchain.client.impl

import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class StrategyTest {

    @Test
    fun `Status UNKNOWN_HOST is server failure`() {
        assertTrue(isServerFailure(Status.UNKNOWN_HOST))
    }

    @Test
    fun `Status CONNECTION_REFUSED is not server failure`() {
        assertFalse(isServerFailure(Status.CONNECTION_REFUSED))
    }
}

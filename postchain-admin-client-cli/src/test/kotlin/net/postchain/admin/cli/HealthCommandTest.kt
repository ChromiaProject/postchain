package net.postchain.admin.cli

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.terminal
import net.postchain.admin.cli.testbase.HealthServiceCommandTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class HealthCommandTest : HealthServiceCommandTestBase() {

    private lateinit var command: HealthCommand

    @BeforeEach
    fun beforeEach() {
        command = HealthCommand { _, _ -> setupChannel(healthService) }
        command.context { terminal = testTerminal.terminal }
    }

    @Test
    fun `Health check should return health status`() {
        // setup
        doNothing().whenever(healthService).healthCheck()
        // execute
        command.parse(
                arrayOf(
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(healthService).healthCheck()
        testTerminal.assertContains("Healthy")
    }

    @Test
    fun `Failed health check should return unhealthy status`() {
        // setup
        doThrow(RuntimeException()).whenever(healthService).healthCheck()
        // execute
        command.parse(
                arrayOf(
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(healthService).healthCheck()
        testTerminal.assertContains("Unhealthy: NOT_SERVING")
    }
}
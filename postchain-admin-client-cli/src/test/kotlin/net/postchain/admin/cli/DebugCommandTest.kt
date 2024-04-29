package net.postchain.admin.cli

import com.github.ajalt.clikt.core.context
import net.postchain.admin.cli.testbase.DebugServiceCommandTestBase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DebugCommandTest : DebugServiceCommandTestBase() {

    private val debugMessage = "I am alive!"

    @Test
    fun `Debug command should return debug message`() {
        // setup
        doReturn(debugMessage).whenever(debugService).debugInfo()
        val command = DebugCommand { _, _ -> setupChannel(debugService) }
        command.context { console = testConsole }
        // execute
        command.parse(
                arrayOf(
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(debugService).debugInfo()
        testConsole.assertContains("I am alive!")
    }
}
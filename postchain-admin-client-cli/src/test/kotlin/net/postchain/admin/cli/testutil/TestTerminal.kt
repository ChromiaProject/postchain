package net.postchain.admin.cli.testutil

import assertk.assertThat
import assertk.assertions.contains
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

open class TestTerminal : AfterEachCallback {
    private val logger = TerminalRecorder()
    val terminal = Terminal(terminalInterface = logger)

    fun assertContains(text: String) = assertThat(logger.output()).contains(text)

    fun assertContains(texts: List<String>) = assertThat(logger.output()).contains(texts.joinToString(separator = System.lineSeparator()))

    override fun afterEach(p0: ExtensionContext?) {
        logger.clearOutput()
    }
}

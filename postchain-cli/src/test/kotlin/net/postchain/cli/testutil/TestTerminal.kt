package net.postchain.cli.testutil

import assertk.assertThat
import assertk.assertions.contains
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

open class TestTerminal : AfterEachCallback {
    private val logger = TerminalRecorder()
    val terminal = Terminal(terminalInterface = logger)

    private val manySpace = Regex(" +")

    fun assertContains(text: String) {
        assertThat(logger.output().replace(manySpace, " ")).contains(
                text.replace(manySpace, " ").replace("\t", " ")
        )
    }

    fun assertContains(texts: List<String>) {
        assertThat(logger.output().replace(manySpace, " ")).contains(
                texts.joinToString(separator = System.lineSeparator()).replace(manySpace, " ").replace("\t", " ")
        )
    }

    override fun afterEach(p0: ExtensionContext?) {
        logger.clearOutput()
    }
}

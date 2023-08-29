package net.postchain.cli.testutil

import assertk.assertThat
import assertk.assertions.contains
import com.github.ajalt.clikt.output.CliktConsole
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.IOException

open class TestConsole() : CliktConsole, AfterEachCallback {
    private val out = mutableListOf<Pair<String, Boolean>>()

    override fun promptForLine(prompt: String, hideInput: Boolean) = try {
        print(prompt, false)
        readlnOrNull() ?: throw RuntimeException("EOF")
    } catch (err: IOException) {
        throw err
    }

    override fun print(text: String, error: Boolean) {
        out.add(text to error)
    }

    override val lineSeparator: String get() = System.lineSeparator()

    fun assertContains(text: String) = assertThat(out.map { it.first }).contains(text)

    fun assertContains(texts: List<String>) {
        for ((index, text) in texts.withIndex()) {
            assertThat(out[index].first).contains(text)
        }
    }

    override fun afterEach(p0: ExtensionContext?) {
        out.clear()
    }
}
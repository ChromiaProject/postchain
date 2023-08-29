package net.postchain.cli

import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import com.github.ajalt.clikt.core.context
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CommandExportBlockchainIT : CommandITBase() {

    @Test
    fun `Export blockchain`(@TempDir tempDir: Path) {
        // setup
        val command = CommandExportBlockchain()
        command.context { console = testConsole }
        addBlockchain()
        val configurationsFile = tempDir.resolve("configurations.gtv").toFile()
        val blocksFile = tempDir.resolve("blocks.gtv").toFile()
        // execute
        command.parse(
                listOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-cid", chainId.toString(),
                        "--configurations-file", configurationsFile.absolutePath,
                        "--blocks-file", blocksFile.absolutePath,
                        "--overwrite"
                )
        )
        // verify
        assertThat(configurationsFile.exists()).isTrue()
        assertThat(configurationsFile.length()).isGreaterThan(0)
        assertThat(blocksFile.exists()).isTrue()
        assertThat(blocksFile.length()).isGreaterThan(0)
    }
}
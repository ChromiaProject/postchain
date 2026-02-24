package net.postchain.cli

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.terminal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CommandExportBlockchainIT : CommandITBase() {

    @Test
    fun `Export blockchain by cid`(@TempDir tempDir: Path) {
        testBlockchainExport(tempDir, "-cid", chainId.toString())
    }

    @Test
    fun `Export blockchain by brid`(@TempDir tempDir: Path) {
        testBlockchainExport(tempDir, "-brid", brid)
    }

    @Test
    fun `Export configurations only`(@TempDir tempDir: Path) {
        // setup
        val command = CommandExportBlockchain()
        command.context { terminal = testTerminal.terminal }
        addBlockchain()
        val configurationsFile = tempDir.resolve("configurations.gtv").toFile()
        // execute
        command.parse(
                listOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-cid", chainId.toString(),
                        "--configurations-file", configurationsFile.absolutePath,
                        "--overwrite"
                )
        )
        // verify
        assertThat(configurationsFile.exists()).isTrue()
        assertThat(configurationsFile.length()).isGreaterThan(0)
        testTerminal.assertContains("Export completed:")
        testTerminal.assertContains("  - Configurations: exported to ${configurationsFile.absolutePath}")
        testTerminal.assertContains("  - Blocks: not requested")
    }

    @Test
    fun `Export with height range`(@TempDir tempDir: Path) {
        // setup
        val command = CommandExportBlockchain()
        command.context { terminal = testTerminal.terminal }
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
                        "--from-height", "0",
                        "--up-to-height", "0",
                        "--overwrite"
                )
        )
        // verify
        assertThat(configurationsFile.exists()).isTrue()
        assertThat(configurationsFile.length()).isGreaterThan(0)
        assertThat(blocksFile.exists()).isTrue()
        assertThat(blocksFile.length()).isGreaterThan(0)
        testTerminal.assertContains("Export completed:")
        testTerminal.assertContains("  - Configurations: exported to ${configurationsFile.absolutePath}")
        testTerminal.assertContains("  - Blocks: no blocks exported to ${blocksFile.absolutePath}")
    }

    @Test
    fun `Export without overwrite flag fails when files exist`(@TempDir tempDir: Path) {
        // setup
        val command = CommandExportBlockchain()
        command.context { terminal = testTerminal.terminal }
        addBlockchain()
        val configurationsFile = tempDir.resolve("configurations.gtv").toFile()
        val blocksFile = tempDir.resolve("blocks.gtv").toFile()

        // First export
        command.parse(
                listOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-cid", chainId.toString(),
                        "--configurations-file", configurationsFile.absolutePath,
                        "--blocks-file", blocksFile.absolutePath
                )
        )

        // verify files exist
        assertThat(configurationsFile.exists()).isTrue()
        assertThat(blocksFile.exists()).isTrue()

        // Second export without overwrite should fail
        val command2 = CommandExportBlockchain()
        command2.context { terminal = testTerminal.terminal }

        try {
            command2.parse(
                    listOf(
                            "-nc", nodeConfigFile.absolutePath,
                            "-cid", chainId.toString(),
                            "--configurations-file", configurationsFile.absolutePath,
                            "--blocks-file", blocksFile.absolutePath
                    )
            )
            assertThat(false).isTrue() // Should not reach here
        } catch (e: Exception) {
            assertThat(e.message?.contains("already exists") ?: false).isTrue()
        }
    }

    private fun testBlockchainExport(tempDir: Path, chainRefOption: String, chainRefValue: String) {
        // setup
        val command = CommandExportBlockchain()
        command.context { terminal = testTerminal.terminal }
        addBlockchain()
        val configurationsFile = tempDir.resolve("configurations.gtv").toFile()
        val blocksFile = tempDir.resolve("blocks.gtv").toFile()
        // execute
        command.parse(
                listOf(
                        "-nc", nodeConfigFile.absolutePath,
                        chainRefOption, chainRefValue,
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
        testTerminal.assertContains("Export completed:")
        testTerminal.assertContains("  - Configurations: exported to ${configurationsFile.absolutePath}")
        testTerminal.assertContains("  - Blocks: no blocks exported to ${blocksFile.absolutePath}")
    }
}
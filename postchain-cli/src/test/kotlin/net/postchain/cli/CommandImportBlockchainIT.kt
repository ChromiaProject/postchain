package net.postchain.cli

import assertk.assertThat
import assertk.assertions.isNotNull
import com.github.ajalt.clikt.core.context
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class CommandImportBlockchainIT : CommandITBase() {

    @Test
    fun `Import blockchain by cid`(@TempDir tempDir: Path) {
        testImportBlockchain(tempDir, "-cid", chainId.toString())
    }

    @Test
    fun `Import blockchain by brid`(@TempDir tempDir: Path) {
        testImportBlockchain(tempDir, "-brid", brid)
    }

    private fun testImportBlockchain(tempDir: Path, chainRefKey: String, chainRefVal: String) {
        // setup
        val command = CommandImportBlockchain()
        command.context { console = testConsole }
        addBlockchain()
        val configurationsFile = tempDir.resolve("configurations.gtv").toFile()
        val blocksFile = tempDir.resolve("blocks.gtv").toFile()
        exportBlockchain(configurationsFile, blocksFile)
        // execute
        command.parse(
                listOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "--configurations-file", configurationsFile.absolutePath,
                        "--blocks-file", blocksFile.absolutePath,
                        chainRefKey, chainRefVal,
                        "--incremental"
                )
        )
        // verify
        assertThat(CliExecution.findBlockchainRid(appConfig, chainId)).isNotNull()
    }

    private fun exportBlockchain(configurationsFile: File, blocksFile: File) {
        CommandExportBlockchain().parse(
                listOf(
                        "-nc", nodeConfigFile.absolutePath,
                        "-cid", chainId.toString(),
                        "--configurations-file", configurationsFile.absolutePath,
                        "--blocks-file", blocksFile.absolutePath,
                        "--overwrite"
                )
        )
    }
}
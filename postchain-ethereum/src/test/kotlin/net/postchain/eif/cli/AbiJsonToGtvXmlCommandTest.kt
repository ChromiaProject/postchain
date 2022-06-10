package net.postchain.eif.cli

import assertk.assert
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class AbiJsonToGtvXmlCommandTest {

    @Test
    fun `Test extracting ERC20 events as gtv from abi`(@TempDir tempDir: Path) {
        val testTokenAbi = javaClass.getResource("/net.postchain.eif.contracts/TestToken.json").path
        val outputFile = tempDir.resolve("events.xml").toFile()
        AbiJsonToGtvXmlCommand().parse(
            listOf(
                "-s",
                testTokenAbi,
                "-e",
                "Transfer,Approval",
                "-o",
                outputFile.absolutePath
            )
        )

        val expectedGtvXml = javaClass.getResource("/net/postchain/eif/cli/erc20_abi_to_gtv.xml").readText()
        assert(outputFile.readText()).isEqualTo(expectedGtvXml)
    }
}
package net.postchain.admin.cli

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.context
import net.postchain.admin.cli.testbase.PostchainServiceCommandTestBase
import net.postchain.common.BlockchainRid
import net.postchain.gtv.gtvml.GtvMLParser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InitializeBlockchainCommandTest : PostchainServiceCommandTestBase() {

    private val chainId = 1L
    private val brid = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    private val blockchainRid = BlockchainRid.buildFromHex(brid)
    private val blockChainConfig = getResourceFile("blockchain_config.xml")
    private val gtv = GtvMLParser.parseGtvML(blockChainConfig.readText())

    private lateinit var command: InitializeBlockchainCommand

    @BeforeEach
    fun beforeEach() {
        command = InitializeBlockchainCommand { _, _ -> setupChannel(postchainService) }
        command.context { console = testConsole }
    }

    @Test
    fun `Initialize blockchain with not existing blockchain should initialize and start blockchain`() {
        // setup
        doReturn(blockchainRid).whenever(postchainService).initializeBlockchain(anyLong(), anyOrNull(), anyBoolean(), isA(), anyList())
        doReturn(blockchainRid).whenever(postchainService).startBlockchain(anyLong())
        // execute
        command.parse(
                arrayOf(
                        "-cid", chainId.toString(),
                        "-bc", blockChainConfig.absolutePath,
                        "-f",
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).initializeBlockchain(chainId, null, true, gtv)
        verify(postchainService).startBlockchain(chainId)
        testConsole.assertContains("Blockchain has been initialized with blockchain RID: $brid")
    }

    @Test
    fun `Initialize blockchain with existing blockchain should not start blockchain`() {
        // setup
        doReturn(null).whenever(postchainService).initializeBlockchain(anyLong(), anyOrNull(), anyBoolean(), isA(), anyList())
        // execute
        val exception = assertThrows<PrintMessage> {
            command.parse(
                    arrayOf(
                            "-cid", chainId.toString(),
                            "-bc", blockChainConfig.absolutePath,
                            "-f",
                            "--target", "localhost:1234"
                    )
            )
        }
        // verify
        verify(postchainService).initializeBlockchain(chainId, null, true, gtv)
        verify(postchainService, never()).startBlockchain(anyLong())
        assertThat(exception.message).isEqualTo("Failed with: ALREADY_EXISTS: Blockchain already exists")
    }
}
package net.postchain.admin.cli

import com.github.ajalt.clikt.core.context
import net.postchain.admin.cli.testbase.PostchainServiceCommandTestBase
import net.postchain.common.BlockchainRid
import net.postchain.crypto.PubKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RemoveBlockchainReplicaCommandTest : PostchainServiceCommandTestBase() {

    private val brid = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    private val blockchainRid = BlockchainRid.buildFromHex(brid)
    private val pubKey = "0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"

    private lateinit var command: RemoveBlockchainReplicaCommand

    @BeforeEach
    fun beforeEach() {
        command = RemoveBlockchainReplicaCommand { _, _ -> setupChannel(postchainService) }
        command.context { console = testConsole }
    }

    @Test
    fun `Remove replica`() {
        // setup
        doReturn(setOf(blockchainRid)).whenever(postchainService).removeBlockchainReplica(isA(), isA())
        // execute
        command.parse(
                arrayOf(
                        "-brid", brid,
                        "--pubkey", pubKey,
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).removeBlockchainReplica(BlockchainRid.buildFromHex(brid), PubKey(pubKey))
        testConsole.assertContains("message: \"Successfully removed replica: [$brid]\"\n\n")
    }

    @Test
    fun `Remove replica with no replicas`() {
        // setup
        doReturn(emptySet<BlockchainRid>()).whenever(postchainService).removeBlockchainReplica(isA(), isA())
        // execute
        command.parse(
                arrayOf(
                        "-brid", brid,
                        "--pubkey", pubKey,
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).removeBlockchainReplica(BlockchainRid.buildFromHex(brid), PubKey(pubKey))
        testConsole.assertContains("message: \"No replica has been removed\"\n\n")
    }
}
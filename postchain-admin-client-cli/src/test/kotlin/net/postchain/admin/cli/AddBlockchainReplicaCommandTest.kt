package net.postchain.admin.cli

import com.github.ajalt.clikt.core.context
import net.postchain.admin.cli.testbase.PostchainServiceCommandTestBase
import net.postchain.common.BlockchainRid
import net.postchain.crypto.PubKey
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AddBlockchainReplicaCommandTest : PostchainServiceCommandTestBase() {

    private val brid = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    private val pubKey = "0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"

    @Test
    fun `Add blockchain`() {
        // setup
        doReturn(true).whenever(postchainService).addBlockchainReplica(any(), any())
        val command = AddBlockchainReplicaCommand { _, _ -> setupChannel(postchainService) }
        command.context { console = testConsole }
        // execute
        command.parse(
                arrayOf(
                        "-brid", brid,
                        "--pubkey", pubKey,
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(postchainService).addBlockchainReplica(BlockchainRid.buildFromHex(brid), PubKey(pubKey))
        testConsole.assertContains("message: \"Node $pubKey has been added as a replica for chain with brid $brid\"\n\n")
    }
}
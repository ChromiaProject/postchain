package net.postchain.admin.cli

import com.github.ajalt.clikt.core.context
import net.postchain.admin.cli.testbase.PeerServiceCommandTestBase
import net.postchain.base.PeerInfo
import net.postchain.common.hexStringToByteArray
import net.postchain.crypto.PubKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RemovePeerCommandTest : PeerServiceCommandTestBase() {

    private val pubKey = "0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"
    private val peerInfo = PeerInfo("host1", 1, pubKey.hexStringToByteArray())

    private lateinit var command: RemovePeerCommand

    @BeforeEach
    fun beforeEach() {
        command = RemovePeerCommand { _, _ -> setupChannel(peerService) }
        command.context { console = testConsole }
    }

    @Test
    fun `Remove peer with no peer`() {
        // setup
        doReturn(emptyArray<PeerInfo>()).whenever(peerService).removePeer(isA())
        // execute
        command.parse(
                arrayOf(
                        "--pubkey", pubKey,
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(peerService).removePeer(PubKey(pubKey))
        testConsole.assertContains("No peer has been removed")
    }

    @Test
    fun `Remove peer`() {
        // setup
        doReturn(arrayOf(peerInfo)).whenever(peerService).removePeer(isA())
        // execute
        command.parse(
                arrayOf(
                        "--pubkey", pubKey,
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(peerService).removePeer(PubKey(pubKey))
        testConsole.assertContains("Successfully removed peer: PeerInfo(host='host1', port=1, pubKey=$pubKey)")
    }
}
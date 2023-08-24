package net.postchain.admin.cli

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.context
import net.postchain.admin.cli.testbase.PeerServiceCommandTestBase
import net.postchain.crypto.PubKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AddPeerCommandTest : PeerServiceCommandTestBase() {

    private val host = "localhost2"
    private val port = 5678
    private val pubKey = "0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"

    private lateinit var command: AddPeerCommand

    @BeforeEach
    fun beforeEach() {
        command = AddPeerCommand { _, _ -> setupChannel(peerService) }
        command.context { console = testConsole }
    }

    @Test
    fun `Add peer`() {
        // setup
        doReturn(true).whenever(peerService).addPeer(any(), anyString(), anyInt(), anyBoolean())
        // execute
        command.parse(
                arrayOf(
                        "--host", host,
                        "--port", port.toString(),
                        "--pubkey", pubKey,
                        "--override",
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(peerService).addPeer(PubKey(pubKey), host, port, true)
        testConsole.assertContains("Peer was added successfully\n")
    }

    @Test
    fun `Add peer with already existing peer should fail`() {
        // setup
        doReturn(false).whenever(peerService).addPeer(any(), anyString(), anyInt(), anyBoolean())
        // execute
        val exception = assertThrows<PrintMessage> {
            command.parse(
                    arrayOf(
                            "--host", host,
                            "--port", port.toString(),
                            "--pubkey", pubKey,
                            "--target", "localhost:1234"
                    )
            )
        }
        // verify
        verify(peerService).addPeer(PubKey(pubKey), host, port, false)
        assertThat(exception.message).isEqualTo("Failed with: ALREADY_EXISTS: public key already added for a host, use override to add anyway")
    }
}
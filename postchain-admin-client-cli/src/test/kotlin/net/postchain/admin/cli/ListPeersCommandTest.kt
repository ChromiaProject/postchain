package net.postchain.admin.cli

import com.github.ajalt.clikt.core.context
import net.postchain.admin.cli.testbase.PeerServiceCommandTestBase
import net.postchain.base.PeerInfo
import net.postchain.common.toHex
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ListPeersCommandTest : PeerServiceCommandTestBase() {

    private val pubKey1 = "1".repeat(32).toByteArray()
    private val pubKey2 = "2".repeat(32).toByteArray()
    private val pubKey3 = "3".repeat(32).toByteArray()
    private val peer1 = PeerInfo("host1", 1, pubKey1)
    private val peer2 = PeerInfo("host2", 2, pubKey2)
    private val peer3 = PeerInfo("host3", 3, pubKey3)
    private val peerInfos = arrayOf(peer1, peer2, peer3)

    private lateinit var command: ListPeersCommand

    @BeforeEach
    fun beforeEach() {
        command = ListPeersCommand { _, _ -> setupChannel(peerService) }
        command.context { console = testConsole }
    }

    @Test
    fun `List peers should handle no peers`() {
        // setup
        doReturn(emptyArray<PeerInfo>()).whenever(peerService).listPeers()
        // execute
        command.parse(
                arrayOf(
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(peerService).listPeers()
        testConsole.assertContains("No peers found\n")
    }

    @Test
    fun `List peers`() {
        // setup
        doReturn(peerInfos).whenever(peerService).listPeers()
        // execute
        command.parse(
                arrayOf(
                        "--target", "localhost:1234"
                )
        )
        // verify
        verify(peerService).listPeers()
        testConsole.assertContains("Peers (3):\n" +
                "PeerInfo(host='host1', port=1, pubKey=${pubKey1.toHex()})\n" +
                "PeerInfo(host='host2', port=2, pubKey=${pubKey2.toHex()})\n" +
                "PeerInfo(host='host3', port=3, pubKey=${pubKey3.toHex()})\n"
        )
    }
}
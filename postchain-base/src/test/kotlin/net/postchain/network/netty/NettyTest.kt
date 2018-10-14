package net.postchain.network.netty

import net.postchain.base.PeerID
import net.postchain.base.PeerInfo
import net.postchain.network.*
import net.postchain.network.ref.netty.NettyActivePeerConnection
import net.postchain.network.ref.netty.NettyPassivePeerConnection
import net.postchain.network.ref.netty.NettyPeerConnection
import org.junit.Test
import java.lang.RuntimeException

class NettyTest {

    private val connectionPublicKey = "key"

    private val serverMessages = setOf("server1", "server2")
    private val clientMessages = setOf("client1", "client2")

    private var receivedServerMessages = mutableSetOf<String>()
    private var receivedClientMessages = mutableSetOf<String>()


    inner class PacketConverterTest: PacketConverter<String> {
        override fun parseIdentPacket(bytes: ByteArray): IdentPacketInfo {
            if(String(bytes) != connectionPublicKey) throw RuntimeException()
                return IdentPacketInfo(bytes, bytes)
        }

        override fun makeIdentPacket(forPeer: PeerID): ByteArray {
            return forPeer
        }

        override fun encodePacket(packet: String): ByteArray {
            return packet.toByteArray()
        }

        override fun decodePacket(pubKey: ByteArray, bytes: ByteArray): String {
            return String(pubKey) + String(bytes)
        }

    }

    private fun clientPacketHandler(bytes: ByteArray) {
        receivedServerMessages.add(String(bytes))
    }

    private fun serverPacketHandler(bytes: ByteArray) {
        receivedClientMessages.add(String(bytes))
    }

    @Test(timeout = 10_000)
    fun nettyTest() {
        val peerInfo = PeerInfo("localhost", 8080, connectionPublicKey.toByteArray())
        val packetConverter = PacketConverterTest()

        val registerConn = { info: IdentPacketInfo, conn: NettyPeerConnection ->
            println("Connection registered: ${info}, ${conn}")
            this::serverPacketHandler
        }

        val passivePeerConnection = NettyPassivePeerConnection(peerInfo, packetConverter, registerConn)
        serverMessages.forEach {
            passivePeerConnection.sendPacket(it.toByteArray())

        }

        val activeConnection = NettyActivePeerConnection(peerInfo, packetConverter, this::clientPacketHandler)

        activeConnection.start()
        clientMessages.forEach {
            activeConnection.sendPacket(it.toByteArray())
        }
        while(!serverMessages.equals(receivedServerMessages) && !clientMessages.equals(receivedClientMessages)) {
            Thread.sleep(1_000)
        }
    }
}
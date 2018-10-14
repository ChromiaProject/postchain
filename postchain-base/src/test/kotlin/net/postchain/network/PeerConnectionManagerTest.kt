package net.postchain.network

import net.postchain.base.PeerID
import net.postchain.base.PeerInfo
import net.postchain.core.ByteArrayKey
import org.junit.Assert
import org.junit.Test

class PeerConnectionManagerTest {

    val connectionPublicKey = "abc"
    val connectionPublicKey2 = "bcd"

    val activeMessage = "active activeMessage from connection manager"
    val passiveMessage = "passive activeMessage from connection manager"

    var activeHandlerMessage = ""
    var passiveHandlerMessage= ""

    inner class PacketConverterTest: PacketConverter<ByteArray> {
        override fun parseIdentPacket(bytes: ByteArray): IdentPacketInfo {
            return IdentPacketInfo(bytes, bytes)
        }

        override fun makeIdentPacket(forPeer: PeerID): ByteArray {
            return forPeer
        }

        override fun encodePacket(packet: ByteArray): ByteArray {
            return packet
        }

        override fun decodePacket(pubKey: ByteArray, bytes: ByteArray): ByteArray {
            return pubKey + bytes
        }

    }

//    private fun peer1packetHandler(bytes: ByteArray) {
//        receivedMessage = String(bytes)
//        println("Peer 1 handler: " + receivedMessage)
//    }

    private fun peer2packetHandler(bytes: ByteArray) {
        passiveHandlerMessage = String(bytes)
    }

    @Test
    fun testConnectionManager() {

        val host = "localhost"

        val peerInfo = PeerInfo(host, 8080, connectionPublicKey.toByteArray())

        val peerInfo2 = PeerInfo(host, 8081, connectionPublicKey2.toByteArray())

       // val peerInfo3 = PeerInfo(host, 8082, connectionPublicKey3.toByteArray())

        val packetConverter = PacketConverterTest()

        val connectionManager = PeerConnectionManager(peerInfo, packetConverter)
        connectionManager.registerBlockchain(connectionPublicKey.toByteArray(), object: BlockchainDataHandler {
            private fun packetHandler(bytes: ByteArray) {
                println("Blockchain 1 handler: " + String(bytes))
            }
            override fun getPacketHandler(peerPubKey: ByteArray) = this::packetHandler

        })
        val connectionManager2 = PeerConnectionManager(peerInfo2, packetConverter)
        connectionManager2.registerBlockchain(connectionPublicKey2.toByteArray(), object: BlockchainDataHandler {
            private fun packetHandler(bytes: ByteArray) {
                activeHandlerMessage = String(bytes)
            }
            override fun getPacketHandler(peerPubKey: ByteArray) = this::packetHandler

        })


        //connectionManager.connectPeer(peerInfo, packetConverter, this::peer1packetHandler)
        connectionManager.connectPeer(peerInfo2, packetConverter, this::peer2packetHandler)

        Assert.assertFalse(passiveHandlerMessage == activeMessage)
        Assert.assertFalse(activeHandlerMessage == passiveMessage)

        connectionManager.sendPacket(OutboundPacket(activeMessage.toByteArray(), listOf(ByteArrayKey(peerInfo2.pubKey), ByteArrayKey(peerInfo.pubKey))))

        connectionManager2.sendPacket(OutboundPacket(passiveMessage.toByteArray(), listOf(ByteArrayKey(peerInfo2.pubKey), ByteArrayKey(peerInfo.pubKey))))

        Thread.sleep(3_000)
        println(activeHandlerMessage)
        println(passiveHandlerMessage)
        Assert.assertTrue(activeHandlerMessage == activeMessage)
        Assert.assertTrue(passiveHandlerMessage == passiveMessage)
    }
}
// blockchain 2 peer 1 peer 2
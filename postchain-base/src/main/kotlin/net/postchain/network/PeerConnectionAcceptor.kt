package net.postchain.network

import mu.KLogging
import net.postchain.base.DynamicPortPeerInfo
import net.postchain.base.PeerInfo
import net.postchain.network.ref.netty.NettyPassivePeerConnection
import net.postchain.network.ref.netty.NettyPeerConnection
import java.net.ServerSocket
import kotlin.concurrent.thread

class PeerConnectionAcceptor(
        peer: PeerInfo,
        val IdentPacketConverter: IdentPacketConverter,
        val registerConn: (IdentPacketInfo, PeerConnection) -> (ByteArray) -> Unit

) {
    val serverSocket: ServerSocket
    @Volatile
    var keepGoing = true

    companion object : KLogging()

    init {
        if (peer is DynamicPortPeerInfo) {
            serverSocket = ServerSocket(0)
            peer.portAssigned(serverSocket.localPort)
        } else {
            serverSocket = ServerSocket(peer.port)
        }
        logger.info("Starting server on port ${peer.port} done")
        thread(name = "-acceptLoop") { acceptLoop() }
    }

    private fun acceptLoop() {
        try {
            while (keepGoing) {
                val socket = serverSocket.accept()
                logger.info("accept socket")
                PassivePeerConnection(
                        IdentPacketConverter,
                        socket,
                        registerConn
                )
            }
        } catch (e: Exception) {
            logger.error("exiting accept loop")
        }
    }

    fun stop() {
        keepGoing = false
        serverSocket.close()
    }

}
//object NextPortIdHolder {
//    var portId = 8080
//
//}
//class PeerConnectionAcceptor(
//        val peer: PeerInfo,
//        val IdentPacketConverter: IdentPacketConverter,
//        val registerConn: (IdentPacketInfo, NettyPeerConnection) -> (ByteArray) -> Unit
//
//) {
//    // val serverSocket: ServerSocket
//    @Volatile
//    var keepGoing = true
//
//    companion object : KLogging()
//
//    init {
//        if (peer is DynamicPortPeerInfo) {
//            //   serverSocket = ServerSocket(0)
//               peer.portAssigned(NextPortIdHolder.portId++)
//        } else {
//            //        serverSocket = ServerSocket(peer.port)
//        }
//        //  logger.info("Starting server on port ${peer.port} done")
//        //   acceptLoop()
//        thread(name = "-acceptLoop"){acceptLoop()}
//   //     acceptLoop()
//    }
//
//    private fun acceptLoop() {
//        try {
//            NettyPassivePeerConnection(
//                    peer,
//                    IdentPacketConverter,
//                    registerConn
//            )
//        } catch (e: Exception) {
//            logger.error("exiting accept loop")
//        }
//    }
//
//    fun stop() {
//        keepGoing = false
//        //    serverSocket.close()
//    }
//
//}
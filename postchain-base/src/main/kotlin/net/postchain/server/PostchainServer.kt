package net.postchain.server

import io.grpc.Server
import io.grpc.ServerBuilder
import net.postchain.PostchainNode
import net.postchain.config.app.AppConfig
import net.postchain.server.service.DebugService
import net.postchain.server.service.PeerService
import net.postchain.server.service.PostchainService

class PostchainServer(appConfig: AppConfig, wipeDb: Boolean = false, debug: Boolean = false, private val port: Int = 50051) {

    private val postchainNode = PostchainNode(appConfig, wipeDb, debug)
    private val server: Server = ServerBuilder.forPort(port)
        .addService(PostchainService(postchainNode))
        .addService(PeerService(postchainNode.postchainContext))
        .addService(DebugService(postchainNode.postchainContext.nodeDiagnosticContext))
        .build()

    fun start() {
        server.start()
        println("Server started, listening on $port")
        Runtime.getRuntime().addShutdownHook(
            Thread {
                println("*** shutting down gRPC server since JVM is shutting down ***")
                this@PostchainServer.stop()
                println("*** server shut down ***")
            }
        )
    }

    private fun stop() {
        server.shutdown()
    }

    fun blockUntilShutdown() {
        server.awaitTermination()
    }
}

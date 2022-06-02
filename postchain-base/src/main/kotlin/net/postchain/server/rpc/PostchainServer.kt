package net.postchain.server.rpc

import io.grpc.Server
import io.grpc.ServerBuilder
import net.postchain.PostchainNode
import net.postchain.config.app.AppConfig

class PostchainServer(appConfig: AppConfig, wipeDb: Boolean = false, debug: Boolean = false, private val port: Int = 50051) {

    private val posthcainNode = PostchainNode(appConfig, wipeDb, debug)
    private val server: Server = ServerBuilder.forPort(port)
        .addService(PostchainService(posthcainNode))
        .build()

    fun start() {
        server.start()
        println("Server started, listening on $port")
        Runtime.getRuntime().addShutdownHook(
            Thread {
                println("*** shutting down gRPC server since JVM is shutting down")
                this@PostchainServer.stop()
                println("*** server shut down")
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
package net.postchain.server

import io.grpc.Grpc
import io.grpc.InsecureServerCredentials
import io.grpc.Server
import io.grpc.TlsServerCredentials
import net.postchain.PostchainNode
import net.postchain.config.app.AppConfig
import net.postchain.server.config.PostchainServerConfig
import net.postchain.server.service.DebugService
import net.postchain.server.service.PeerService
import net.postchain.server.service.PostchainService

class PostchainServer(appConfig: AppConfig, wipeDb: Boolean = false, debug: Boolean = false, private val config: PostchainServerConfig) {

    private val postchainNode = PostchainNode(appConfig, wipeDb, debug)
    private val credentials = config.sslConfig?.let {
        TlsServerCredentials.create(it.certChainFile, it.privateKeyFile)
    } ?: InsecureServerCredentials.create()
    private val server: Server = Grpc.newServerBuilderForPort(config.port, credentials)
        .addService(PostchainService(postchainNode))
        .addService(PeerService(postchainNode.postchainContext))
        .addService(DebugService(postchainNode.postchainContext.nodeDiagnosticContext))
        .build()

    fun start(initialChainIds: List<Long>? = null) {
        server.start()
        initialChainIds?.forEach { postchainNode.startBlockchain(it) }
        println("Postchain server started, listening on ${config.port}")
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

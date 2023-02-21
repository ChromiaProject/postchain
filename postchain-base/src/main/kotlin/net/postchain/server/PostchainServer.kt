package net.postchain.server

import io.grpc.Grpc
import io.grpc.InsecureServerCredentials
import io.grpc.Server
import io.grpc.TlsServerCredentials
import mu.KLogging
import net.postchain.PostchainNode
import net.postchain.config.app.AppConfig
import net.postchain.server.config.PostchainServerConfig
import net.postchain.server.grpc.DebugServiceGrpcImpl
import net.postchain.server.grpc.HealthServiceGrpcImpl
import net.postchain.server.grpc.PeerServiceGrpcImpl
import net.postchain.server.grpc.PostchainServiceGrpcImpl
import net.postchain.server.service.DebugService
import net.postchain.server.service.HealthService
import net.postchain.server.service.PeerService
import net.postchain.server.service.PostchainService

class PostchainServer(appConfig: AppConfig, wipeDb: Boolean = false, private val config: PostchainServerConfig) {

    companion object : KLogging()

    private val postchainNode = PostchainNode(appConfig, wipeDb)
    private val credentials = config.tlsConfig?.let {
        TlsServerCredentials.create(it.certChainFile, it.privateKeyFile)
    } ?: InsecureServerCredentials.create()
    private val server: Server = Grpc.newServerBuilderForPort(config.port, credentials)
            .addService(HealthServiceGrpcImpl(HealthService(postchainNode.postchainContext.storage)))
            .addService(PostchainServiceGrpcImpl(PostchainService(postchainNode)))
            .addService(PeerServiceGrpcImpl(PeerService(postchainNode.postchainContext)))
            .apply {
                if (postchainNode.postchainContext.debug) addService(DebugServiceGrpcImpl(DebugService(postchainNode.postchainContext.nodeDiagnosticContext)))
            }
            .build()

    fun start(initialChainIds: List<Long>? = null) {
        server.start()
        initialChainIds?.forEach {
            postchainNode.tryStartBlockchain(it)
        }
        logger.info("Postchain server started, listening on ${config.port}")
        Runtime.getRuntime().addShutdownHook(
                Thread {
                    logger.info("*** shutting down gRPC server since JVM is shutting down ***")
                    this@PostchainServer.stop()
                    logger.info("*** server shut down ***")
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

package net.postchain.server

import io.grpc.BindableService
import io.grpc.Grpc
import io.grpc.InsecureServerCredentials
import io.grpc.Server
import io.grpc.TlsServerCredentials
import mu.KLogging
import net.postchain.server.config.PostchainServerConfig
import net.postchain.server.grpc.HealthServiceGrpcImpl
import net.postchain.server.grpc.PeerServiceGrpcImpl
import net.postchain.server.grpc.PostchainServiceGrpcImpl
import net.postchain.server.service.HealthService
import net.postchain.server.service.PeerService
import net.postchain.server.service.PostchainService

class PostchainServer(
        private val nodeProvider: NodeProvider,
        private val config: PostchainServerConfig,
        private val otherServices: List<BindableService> = listOf()
) {

    companion object : KLogging()

    private val credentials = config.tlsConfig?.let {
        TlsServerCredentials.create(it.certChainFile, it.privateKeyFile)
    } ?: InsecureServerCredentials.create()
    private val server: Server = Grpc.newServerBuilderForPort(config.port, credentials)
            .addService(HealthServiceGrpcImpl(HealthService(nodeProvider)))
            .addService(PostchainServiceGrpcImpl(PostchainService(nodeProvider)))
            .addService(PeerServiceGrpcImpl(PeerService(nodeProvider)))
            .apply {
                otherServices.forEach { addService(it) }
            }
            .build()

    fun start(initialChainIds: List<Long>? = null) {
        server.start()
        initialChainIds?.forEach {
            nodeProvider.get().tryStartBlockchain(it)
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

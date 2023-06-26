// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.server.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.grpc.BindableService
import mu.KLogging
import net.postchain.server.NodeProvider
import net.postchain.server.PostchainServer
import net.postchain.server.config.PostchainServerConfig
import net.postchain.server.config.TlsConfig
import net.postchain.server.grpc.DebugServiceGrpcImpl
import net.postchain.server.service.DebugService

abstract class CommandRunServerBase(name: String?, help: String) : CliktCommand(name = name, help = help) {

    companion object : KLogging()

    protected val debug by debugOption()

    private val port by option("-p", "--port", envvar = "POSTCHAIN_SERVER_PORT", help = "Port for the server")
            .int().default(PostchainServerConfig.DEFAULT_RPC_SERVER_PORT)

    private val tlsOptions by TlsOptions().cooccurring()

    protected val services = mutableListOf<BindableService>()

    fun runServer(nodeProvider: NodeProvider, initialChainIds: List<Long>? = null) {
        val serverConfig = tlsOptions?.let {
            PostchainServerConfig(port, TlsConfig(it.certChainFile, it.privateKeyFile))
        } ?: PostchainServerConfig(port)

        if (debug) services.add(DebugServiceGrpcImpl(DebugService(nodeProvider)))

        PostchainServer(nodeProvider, serverConfig, services)
                .apply {
                    start(initialChainIds)
                    blockUntilShutdown()
                }
    }
}

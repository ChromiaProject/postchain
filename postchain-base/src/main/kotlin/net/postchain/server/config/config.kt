package net.postchain.server.config

import java.io.File

data class PostchainServerConfig(
        val port: Int = DEFAULT_RPC_SERVER_PORT,
        val tlsConfig: TlsConfig? = null
) {
    companion object {
        const val DEFAULT_RPC_SERVER_PORT = 50051
    }
}

data class TlsConfig(
        val certChainFile: File,
        val privateKeyFile: File
)

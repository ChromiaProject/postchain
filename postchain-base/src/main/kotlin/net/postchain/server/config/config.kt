package net.postchain.server.config

import java.io.File

data class PostchainServerConfig(
    val port: Int = 50051,
    val tlsConfig: TlsConfig? = null
)

data class TlsConfig(
    val certChainFile: File,
    val privateKeyFile: File
)

package net.postchain.server.config

import java.io.File

data class PostchainServerConfig(
    val port: Int = 50051,
    val sslConfig: SslConfig? = null
)

data class SslConfig(
    val certChainFile: File,
    val privateKeyFile: File
)

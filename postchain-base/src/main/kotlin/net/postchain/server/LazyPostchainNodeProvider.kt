package net.postchain.server

import mu.KLogging
import net.postchain.PostchainNode
import net.postchain.common.exception.AlreadyExists
import net.postchain.config.app.AppConfig
import net.postchain.crypto.PrivKey

class LazyPostchainNodeProvider : NodeProvider {

    companion object : KLogging()

    private lateinit var postchainNode: PostchainNode

    override fun get(): PostchainNode = postchainNode

    fun init(privKey: PrivKey, wipeDb: Boolean = false) {
        if (::postchainNode.isInitialized) throw AlreadyExists("Trying to initialize PostchainNode when already initialized")
        postchainNode = PostchainNode(
                AppConfig.fromEnvironment(false, mapOf("messaging.privkey" to privKey.toString())),
                wipeDb
        )
        logger.info { "Created postchain node" }
    }
}

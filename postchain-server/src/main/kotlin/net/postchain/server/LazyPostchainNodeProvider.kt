package net.postchain.server

import mu.KLogging
import net.postchain.PostchainNode
import net.postchain.common.exception.AlreadyExists
import net.postchain.config.app.AppConfig
import net.postchain.crypto.PrivKey

open class LazyPostchainNodeProvider(val debug: Boolean) : NodeProvider {

    companion object : KLogging()

    protected lateinit var postchainNode: PostchainNode

    override fun get(): PostchainNode = postchainNode

    fun init(privKey: PrivKey, wipeDb: Boolean = false) {
        if (::postchainNode.isInitialized) throw AlreadyExists("Trying to initialize PostchainNode when already initialized")
        createPostchainNode(privKey, wipeDb)
        logger.info { "Created postchain node" }
    }

    protected open fun createPostchainNode(privKey: PrivKey, wipeDb: Boolean) {
        postchainNode = PostchainNode(
                AppConfig.fromEnvironment(debug, mapOf("messaging.privkey" to privKey.toString())),
                wipeDb
        )
    }
}
package net.postchain.devtools

import net.postchain.PostchainNode
import net.postchain.crypto.PrivKey
import net.postchain.server.LazyPostchainNodeProvider
import java.util.concurrent.atomic.AtomicBoolean

class TestLazyPostchainNodeProvider(debug: Boolean, val nodeProvider: () -> PostchainNode) : LazyPostchainNodeProvider(debug) {

    lateinit var privKey: PrivKey
    lateinit var wipeDb: AtomicBoolean // Atomic just to make it possible to have lateinit

    override fun createPostchainNode(privKey: PrivKey, wipeDb: Boolean) {
        this.privKey = privKey
        this.wipeDb = AtomicBoolean(wipeDb)
        postchainNode = nodeProvider()
    }
}
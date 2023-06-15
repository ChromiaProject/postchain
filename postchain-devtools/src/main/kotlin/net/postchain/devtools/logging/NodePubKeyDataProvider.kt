package net.postchain.devtools.logging

import net.postchain.devtools.PostchainTestNode.Companion.threadLocalPubkey
import net.postchain.logging.NODE_PUBKEY_TAG
import org.apache.logging.log4j.core.util.ContextDataProvider

/**
 * When running integration tests with multiple nodes it's sometimes difficult to debug due to their logs being mixed
 * This is a facility for injecting node pub key into the logs
 */
class NodePubKeyDataProvider : ContextDataProvider {
    override fun supplyContextData(): Map<String, String> {
        return if (Thread.currentThread().name.startsWith("ForkJoinPool")) {
            // If we are a thread from the common thread pool it's likely that we don't have the correct
            // node id stored in our thread local context since this thread was created by an arbitrary node
            // so let's not add it to the logs
            mapOf()
        } else {
            threadLocalPubkey.get()?.let { mapOf(NODE_PUBKEY_TAG to it) } ?: mapOf()
        }
    }
}
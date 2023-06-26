package net.postchain.server.service

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.core.Storage
import net.postchain.server.NodeProvider

class HealthService(private val nodeProvider: NodeProvider) {

    val storage: Storage get() = nodeProvider.get().postchainContext.sharedStorage

    /**
     * @throws [Exception] on failed health check
     */
    fun healthCheck() {
        storage.withReadConnection { ctx ->
            DatabaseAccess.of(ctx).getContainerIid(ctx, "")
        }
    }
}

package net.postchain.server.service

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.core.Storage

class HealthService(private val storage: Storage) {
    /**
     * @throws [Exception] on failed health check
     */
    fun healthCheck() {
        storage.withReadConnection { ctx ->
            DatabaseAccess.of(ctx).getContainerIid(ctx, "")
        }
    }
}

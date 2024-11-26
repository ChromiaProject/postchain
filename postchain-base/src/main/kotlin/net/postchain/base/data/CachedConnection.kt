package net.postchain.base.data

import java.sql.Connection

data class CachedConnection(
        val connection: Connection,
        var refCount: Int = 1
)

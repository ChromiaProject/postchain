package net.postchain.core

import java.sql.Connection

data object MockAppContext : AppContext {
    override val conn: Connection
        get() = throw NotImplementedError("not used in mock")
}

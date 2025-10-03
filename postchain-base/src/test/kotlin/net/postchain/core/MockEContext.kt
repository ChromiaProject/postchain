package net.postchain.core

import java.sql.Connection

data class MockEContext(override val chainID: Long) : EContext {
    override val id: String
        get() = "mock"
    override val conn: Connection
        get() = throw NotImplementedError("not used in mock")
}

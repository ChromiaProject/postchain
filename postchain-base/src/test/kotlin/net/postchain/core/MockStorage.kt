package net.postchain.core

class MockStorage : Storage {
    override val readConcurrency: Int
        get() = 1
    override val exitOnFatalError: Boolean
        get() = true

    override fun openReadConnection(): AppContext = MockAppContext

    override fun closeReadConnection(context: AppContext) {}

    override fun openWriteConnection(): AppContext = MockAppContext

    override fun closeWriteConnection(context: AppContext, commit: Boolean) {}

    override fun openReadConnection(chainID: Long): EContext = MockEContext(chainID)

    override fun closeReadConnection(context: EContext) {}

    override fun openWriteConnection(chainID: Long): EContext = MockEContext(chainID)

    override fun closeWriteConnection(context: EContext, commit: Boolean) {}

    override fun getExistingWriteContext(chainID: Long): EContext {
        throw NotImplementedError("not used in mock")
    }

    override fun isSavepointSupported(): Boolean {
        throw NotImplementedError("not used in mock")
    }

    override fun withSavepoint(context: EContext, fn: () -> Unit): Exception? {
        throw NotImplementedError("not used in mock")
    }

    override fun createSharedContext(eContext: EContext) {
        throw NotImplementedError("not used in mock")
    }

    override fun claimSharedContext(eContext: EContext): EContext {
        throw NotImplementedError("not used in mock")
    }

    override fun releaseSharedContext(eContext: EContext) {
        throw NotImplementedError("not used in mock")
    }

    override fun close() {}
}

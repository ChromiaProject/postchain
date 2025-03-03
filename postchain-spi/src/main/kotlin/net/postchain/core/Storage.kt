// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

/**
 * Handles back-end database connection and storage
 */
interface Storage : AutoCloseable {

    val readConcurrency: Int
    val exitOnFatalError: Boolean

    // AppContext
    fun openReadConnection(): AppContext
    fun closeReadConnection(context: AppContext)

    fun openWriteConnection(): AppContext
    fun closeWriteConnection(context: AppContext, commit: Boolean)

    // EContext
    fun openReadConnection(chainID: Long): EContext
    fun closeReadConnection(context: EContext)

    fun openWriteConnection(chainID: Long): EContext
    fun closeWriteConnection(context: EContext, commit: Boolean)

    /**
     * Retrieves an existing write context for the specified chain ID for the calling thread.
     *
     * @param chainID The unique identifier of the chain for which the write context is being retrieved.
     * @return The existing write context as an instance of EContext, or null if no context exists for the given chain ID.
     */
    fun getExistingWriteContext(chainID: Long): EContext?

    // Savepoint
    fun isSavepointSupported(): Boolean
    fun withSavepoint(context: EContext, fn: () -> Unit): Exception?

    // Shared contexts

    /**
     * Creates a shared context based on the provided EContext.
     * This is a facility for sharing an EContext between threads, for situations where a DB transaction is shared
     * between them.
     *
     * Only use this if it's absolutely necessary and you know what you are doing.
     *
     * @param eContext The EContext instance to use for initializing the shared context.
     */
    fun createSharedContext(eContext: EContext)

    /**
     * Claims ownership of an existing shared context, allowing the current caller to utilize it.
     * The implementation is backed by a [java.util.concurrent.locks.ReentrantLock] and works in a similar fashion.
     * I.e. thread can claim the context multiple times, to fully release it `releaseSharedContext` must be called
     * equal amount of times.
     *
     * @param eContext The EContext instance representing the shared context to be claimed.
     * @return The claimed EContext instance for the caller to use. Returned for convenience.
     */
    fun claimSharedContext(eContext: EContext): EContext

    /**
     * Releases a previously claimed shared context. See [claimSharedContext] for more details.
     *
     * @param eContext The EContext instance representing the shared context to be released.
     */
    fun releaseSharedContext(eContext: EContext)
}
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

    // Savepoint
    fun isSavepointSupported(): Boolean
    fun withSavepoint(context: EContext, fn: () -> Unit): Exception?
}
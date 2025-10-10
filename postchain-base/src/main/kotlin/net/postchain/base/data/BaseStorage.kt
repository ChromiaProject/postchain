// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import mu.KLogging
import net.postchain.base.BaseAppContext
import net.postchain.base.BaseEContext
import net.postchain.base.data.SqlUtils.isFatal
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.AppContext
import net.postchain.core.EContext
import net.postchain.core.Storage
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import javax.sql.DataSource
import kotlin.system.exitProcess

class BaseStorage(
        private val readDataSource: DataSource,
        private val writeDataSource: DataSource,
        private val db: DatabaseAccess,
        override val readConcurrency: Int,
        override val exitOnFatalError: Boolean,
        private val savepointSupport: Boolean = true
) : Storage {

    private val cachedConnection = ThreadLocal<CachedConnection>()
    private val openChainWriteConnections = ThreadLocal.withInitial { mutableMapOf<Long, EContext>() }
    private val sharedChainWriteConnections = ConcurrentHashMap<String, ReentrantLock>()

    companion object : KLogging()

    override fun openReadConnection(): AppContext {
        val connection = getReadConnection()

        val context = buildAppContext(connection)
        if (!context.conn.isReadOnly) {
            throw ProgrammerMistake("Connection is not read-only")
        }
        return context
    }

    private fun getAndCacheNewReadConnection(): Connection {
        val newConnection = readDataSource.connection
        cachedConnection.set(CachedConnection(newConnection))
        return newConnection
    }

    override fun closeReadConnection(context: AppContext) {
        closeReadConnection(context.conn)
    }

    override fun openWriteConnection(): AppContext {
        return buildAppContext(writeDataSource.connection)
    }

    override fun closeWriteConnection(context: AppContext, commit: Boolean) {
        closeWriteConnection(context.conn, commit)
    }

    override fun openReadConnection(chainID: Long): EContext {
        val connection = getReadConnection()

        val context = buildEContext(chainID, connection)
        if (!context.conn.isReadOnly) {
            throw ProgrammerMistake("Connection is not read-only")
        }
        return context
    }

    override fun closeReadConnection(context: EContext) {
        closeReadConnection(context.conn)
    }

    override fun openWriteConnection(chainID: Long): EContext {
        return buildEContext(chainID, writeDataSource.connection).also {
            openChainWriteConnections.get()[chainID] = it
        }
    }

    override fun closeWriteConnection(context: EContext, commit: Boolean) {
        openChainWriteConnections.get().remove(context.chainID)
        try {
            closeWriteConnection(context.conn, commit)
        } finally {
            sharedChainWriteConnections.remove(context.id)?.let {
                if (it.isHeldByCurrentThread) it.unlock()
            }
        }
    }

    override fun isSavepointSupported(): Boolean = savepointSupport

    override fun withSavepoint(context: EContext, fn: () -> Unit): Exception? {
        var exception: Exception? = null

        val savepointName = "appendTx${System.nanoTime()}"
        val savepoint = context.conn.setSavepoint(savepointName)
        try {
            fn()
            context.conn.releaseSavepoint(savepoint)
        } catch (e: Exception) {
            logger.debug(e) { "Exception in savepoint $savepointName" }
            context.conn.rollback(savepoint)
            exception = e
        }

        return exception
    }

    override fun createSharedContext(eContext: EContext) {
        sharedChainWriteConnections[eContext.id] = ReentrantLock().also { it.lock() }
    }

    override fun releaseSharedContext(eContext: EContext) {
        val lock = sharedChainWriteConnections[eContext.id]
                ?: throw ProgrammerMistake("This context is not shared. Call createSharedContext first.")
        lock.unlock()
        // Did we release the connection completely?
        if (!lock.isHeldByCurrentThread) openChainWriteConnections.get().remove(eContext.chainID)
    }

    override fun claimSharedContext(eContext: EContext): EContext {
        val lock = sharedChainWriteConnections[eContext.id] ?: throw ProgrammerMistake("This context is not shared")
        lock.lock()
        openChainWriteConnections.get()[eContext.chainID] = eContext
        return eContext
    }

    override fun getExistingWriteContext(chainID: Long): EContext? = openChainWriteConnections.get()[chainID]

    override fun close() {
        try {
            (readDataSource as? AutoCloseable)?.close()
        } catch (e: SQLException) {
            logger.debug(e) { "SQLException in BaseStorage.close()" }
        }
        try {
            (writeDataSource as? AutoCloseable)?.close()
        } catch (e: SQLException) {
            logger.debug(e) { "SQLException in BaseStorage.close()" }
        }
    }

    private fun closeReadConnection(connection: Connection) {
        if (connection.isClosed) {
            // Is this our cached connection, if so get rid of it, if not and it is closed, we should anyway get rid of it
            if (cachedConnection.get()?.connection?.isClosed == true) cachedConnection.set(null)
            throw ProgrammerMistake("trying to close a connection that is already closed")
        }

        if (!connection.isReadOnly) {
            throw ProgrammerMistake("trying to close a writable connection as a read-only connection")
        }
        val cache = cachedConnection.get()
        if (cache.refCount > 1) {
            cache.refCount--
        } else {
            connection.commit()
            connection.close()
            cachedConnection.set(null)
        }
    }

    private fun closeWriteConnection(connection: Connection, commit: Boolean) {
        try {
            with(connection) {
                when {
                    isReadOnly -> throw ProgrammerMistake(
                            "trying to close a read-only connection as a writeable connection")

                    commit -> commit()
                    else -> rollback()
                }

                close()
            }
        } catch (e: SQLException) {
            if (e.isFatal()) {
                logger.error("Fatal database error occurred: ${e.message}")
                if (exitOnFatalError) {
                    exitProcess(1)
                } else {
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    private fun buildAppContext(connection: Connection): AppContext =
            BaseAppContext(connection, db)

    private fun buildEContext(chainID: Long, connection: Connection): EContext =
            BaseEContext(connection, chainID, db)

    private fun getReadConnection(): Connection {
        val connection = cachedConnection.get()?.let {
            it.refCount++
            it.connection
        } ?: getAndCacheNewReadConnection()
        return connection
    }
}

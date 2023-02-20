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

    companion object : KLogging()

    override fun openReadConnection(): AppContext {
        val context = buildAppContext(readDataSource)
        if (!context.conn.isReadOnly) {
            throw ProgrammerMistake("Connection is not read-only")
        }
        return context
    }

    override fun closeReadConnection(context: AppContext) {
        closeReadConnection(context.conn)
    }

    override fun openWriteConnection(): AppContext {
        return buildAppContext(writeDataSource)
    }

    override fun closeWriteConnection(context: AppContext, commit: Boolean) {
        closeWriteConnection(context.conn, commit)
    }

    override fun openReadConnection(chainID: Long): EContext {
        val context = buildEContext(chainID, readDataSource)
        if (!context.conn.isReadOnly) {
            throw ProgrammerMistake("Connection is not read-only")
        }
        return context
    }

    override fun closeReadConnection(context: EContext) {
        closeReadConnection(context.conn)
    }

    override fun openWriteConnection(chainID: Long): EContext {
        return buildEContext(chainID, writeDataSource)
    }

    override fun closeWriteConnection(context: EContext, commit: Boolean) {
        closeWriteConnection(context.conn, commit)
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
        if (!connection.isReadOnly) {
            throw ProgrammerMistake("trying to close a writable connection as a read-only connection")
        }
        connection.close()
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

    private fun buildAppContext(dataSource: DataSource): AppContext =
            BaseAppContext(dataSource.connection, db)

    private fun buildEContext(chainID: Long, dataSource: DataSource): EContext =
            BaseEContext(dataSource.connection, chainID, db)
}

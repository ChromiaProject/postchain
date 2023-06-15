// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain

import net.postchain.base.data.BaseStorage
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DatabaseAccessFactory
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.config.app.AppConfig
import net.postchain.core.Storage
import org.apache.commons.dbcp2.BasicDataSource
import javax.sql.DataSource
import kotlin.time.Duration

object StorageBuilder {

    private const val DB_VERSION = 8

    fun buildStorage(appConfig: AppConfig, maxWaitWrite: Duration = Duration.ZERO, maxWriteTotal: Int = 2,
                     wipeDatabase: Boolean = false, expectedDbVersion: Int = DB_VERSION): Storage {
        val db = DatabaseAccessFactory.createDatabaseAccess(appConfig.databaseDriverclass)
        initStorage(appConfig, wipeDatabase, db, expectedDbVersion)

        // Read DataSource
        val readDataSource = createBasicDataSource(appConfig).apply {
            defaultAutoCommit = true
            maxTotal = appConfig.databaseReadConcurrency
            defaultReadOnly = true
        }

        // Write DataSource
        val writeDataSource = createBasicDataSource(appConfig).apply {
            this.maxWaitMillis = maxWaitWrite.inWholeMilliseconds
            defaultAutoCommit = false
            maxTotal = maxWriteTotal
        }

        return BaseStorage(
                readDataSource,
                writeDataSource,
                db,
                appConfig.databaseReadConcurrency,
                appConfig.exitOnFatalError,
                db.isSavepointSupported())
    }

    private fun initStorage(appConfig: AppConfig, wipeDatabase: Boolean, db: DatabaseAccess, expectedDbVersion: Int) {
        val initDataSource = createBasicDataSource(appConfig)

        if (wipeDatabase) {
            wipeDatabase(initDataSource, appConfig, db)
        }

        createSchemaIfNotExists(initDataSource, appConfig.databaseSchema, db)
        createTablesIfNotExists(initDataSource, db, expectedDbVersion)
        initDataSource.close()
    }

    private fun setCurrentSchema(dataSource: DataSource, schema: String, db: DatabaseAccess) {
        dataSource.connection.use { connection ->
            db.setCurrentSchema(connection, schema)
            connection.commit()
        }
    }

    private fun createBasicDataSource(appConfig: AppConfig, withSchema: Boolean = true): BasicDataSource {
        return BasicDataSource().apply {
            driverClassName = appConfig.databaseDriverclass
            url = appConfig.databaseUrl // + "?loggerLevel=TRACE&loggerFile=db.log"
            username = appConfig.databaseUsername
            password = appConfig.databasePassword
            defaultAutoCommit = false
            addConnectionProperty("binaryTransfer", "false") // workaround for issue in Postgres driver 42.5.1: https://github.com/pgjdbc/pgjdbc/issues/2695

            if (withSchema) {
                /**
                 * [POS-129]: After setting up defaultSchema property by `DataSource.setDefaultSchema(...)`
                 * DataSource.getConnection().schema is null in docker container which is run by
                 * DockerClient on Windows/WSL2:
                 *      Database error: sql-state: 3F000, error-code: 0, message: ERROR: no schema has been selected to create in
                 *        Position: 14 Query: CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT) Parameters: []
                 *
                 * `SET SCHEMA` sql init script fails with the same error:
                 *      val scripts = listOf("SET SCHEMA '${appConfig.databaseSchema}'")
                 */
//                defaultSchema = appConfig.databaseSchema
                val scripts = listOf("SET search_path TO ${appConfig.databaseSchema}") // PostgreSQL specific script
                setConnectionInitSqls(scripts)
            }
        }

    }

    fun wipeDatabase(appConfig: AppConfig) {
        val db = DatabaseAccessFactory.createDatabaseAccess(appConfig.databaseDriverclass)
        wipeDatabase(createBasicDataSource(appConfig), appConfig, db)
    }

    private fun wipeDatabase(dataSource: DataSource, appConfig: AppConfig, db: DatabaseAccess) {
        dataSource.connection.use { connection ->
            if (db.isSchemaExists(connection, appConfig.databaseSchema)) {
                db.dropSchemaCascade(connection, appConfig.databaseSchema)
                connection.commit()
            }
        }
    }

    private fun createSchemaIfNotExists(dataSource: DataSource, schema: String, db: DatabaseAccess) {
        dataSource.connection.use { connection ->
            if (!db.isSchemaExists(connection, schema)) {
                db.createSchema(connection, schema)
                connection.commit()
            }
        }
    }

    private fun createTablesIfNotExists(dataSource: DataSource, db: DatabaseAccess, expectedDbVersion: Int) {
        dataSource.connection.use { connection ->
            db.initializeApp(connection, expectedDbVersion)
            connection.commit()
        }
    }

    fun setupInitialPeers(appConfig: AppConfig, storage: Storage) {
        val hasOwnPeer = storage.withReadConnection {
            DatabaseAccess.of(it).findPeerInfo(it, null, null, appConfig.pubKey).isNotEmpty()
        }
        if (!hasOwnPeer) {
            storage.withWriteConnection {
                DatabaseAccess.of(it).addPeerInfo(it, "localhost", appConfig.port, appConfig.pubKey)
            }
        }
        val genesisPubkey = appConfig.getEnvOrString("POSTCHAIN_GENESIS_PUBKEY", "genesis.pubkey")
        if (genesisPubkey != null) {
            require(appConfig.hasEnvOrKey("POSTCHAIN_GENESIS_HOST", "genesis.host")) { "Node configuration must contain genesis.host if genesis.pubkey is supplied" }
            require(appConfig.hasEnvOrKey("POSTCHAIN_GENESIS_PORT", "genesis.port")) { "Node configuration must contain genesis.port if genesis.pubkey if supplied" }
            val hasGenesisPeer = storage.withReadConnection {
                DatabaseAccess.of(it).findPeerInfo(it, null, null, genesisPubkey).isNotEmpty()
            }
            if (!hasGenesisPeer) {
                storage.withReadConnection {
                    DatabaseAccess.of(it).addPeerInfo(it,
                            appConfig.getEnvOrString("POSTCHAIN_GENESIS_HOST", "genesis.host", ""),
                            appConfig.getEnvOrInt("POSTCHAIN_GENESIS_PORT", "genesis.port", 0),
                            genesisPubkey)
                }
            }
        }
    }
}
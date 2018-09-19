// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain

import net.postchain.base.data.BaseStorage
import org.apache.commons.configuration2.Configuration
import org.apache.commons.dbcp2.BasicDataSource
import org.apache.commons.dbutils.QueryRunner
import javax.sql.DataSource

fun baseStorage(config: Configuration, nodeIndex: Int, wipeDatabase: Boolean = false): BaseStorage {
    val writeDataSource = createBasicDataSource(config)
    writeDataSource.maxWaitMillis = 0
    writeDataSource.defaultAutoCommit = false
    writeDataSource.maxTotal = 1

    if (wipeDatabase) {
        wipeDatabase(writeDataSource, config)
    }

    createSchemaIfNotExists(writeDataSource, config.getString("database.schema"))

    val readDataSource = createBasicDataSource(config)
    readDataSource.defaultAutoCommit = true
    readDataSource.maxTotal = 2
    readDataSource.defaultReadOnly = true

    return BaseStorage(writeDataSource, readDataSource, nodeIndex)
}

fun createBasicDataSource(config: Configuration): BasicDataSource {
    val dataSource = BasicDataSource()
    val schema = config.getString("database.schema", "public")
    dataSource.addConnectionProperty("currentSchema", schema)
    dataSource.driverClassName = config.getString("database.driverclass")
    dataSource.url = config.getString("database.url")
    dataSource.username = config.getString("database.username")
    dataSource.password = config.getString("database.password")
    dataSource.defaultAutoCommit = false
    return dataSource
}

fun wipeDatabase(dataSource: DataSource, config: Configuration) {
    val schema = config.getString("database.schema", "public")
    val queryRunner = QueryRunner()
    val conn = dataSource.connection
    queryRunner.update(conn, "DROP SCHEMA IF EXISTS $schema CASCADE")
    queryRunner.update(conn, "CREATE SCHEMA $schema")
    conn.commit()
    conn.close()
}

private fun createSchemaIfNotExists(dataSource: DataSource, schema: String) {
    val queryRunner = QueryRunner()
    val conn = dataSource.connection
    conn.use { conn ->
        queryRunner.update(conn, "CREATE SCHEMA IF NOT EXISTS $schema")
        conn.commit()
    }
}
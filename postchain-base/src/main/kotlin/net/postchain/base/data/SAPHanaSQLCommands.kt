// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

/**
 * Implementation for SAP HANA
 */
object SAPHanaSQLCommands : SQLCommands {

    override val createTableBlocks: String = "CREATE TABLE blocks" +
            " (block_iid BIGINT NOT NULL PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY," +
            "  block_height BIGINT NOT NULL, " +
            "  block_rid VARBINARY(1000)," +
            "  chain_iid BIGINT NOT NULL," +
            "  block_header_data BLOB," +
            "  block_witness BLOB," +
            "  timestamp BIGINT," +
            "  UNIQUE (chain_iid, block_rid)," +
            "  UNIQUE (chain_iid, block_height))"
    override val createTableSnapshots: String
        get() = TODO("Not yet implemented")

    override val createTableBlockChains: String = "CREATE TABLE blockchains " +
            " (chain_iid BIGINT, blockchain_rid VARBINARY(1000) NOT NULL)"

    override val createTableTransactions: String = "CREATE TABLE transactions (" +
            "    tx_iid BIGINT NOT NULL PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY, " +
            "    chain_iid bigint NOT NULL," +
            "    tx_rid VARBINARY(1000) NOT NULL," +
            "    tx_data BLOB NOT NULL," +
            "    tx_hash VARBINARY(1000) NOT NULL," +
            "    block_iid BIGINT NOT NULL REFERENCES blocks(block_iid)," +
            "    UNIQUE (chain_iid, tx_rid))"

    override val createTableConfiguration = "CREATE TABLE configurations (" +
            " chain_iid bigint NOT NULL" +
            ", height BIGINT NOT NULL" +
            ", configuration_data BLOB NOT NULL" +
            ", PRIMARY KEY (chain_iid, height)" +
            ")"

    override val createTablePeerInfos = "CREATE TABLE ${SQLDatabaseAccess.TABLE_PEERINFOS} (" +
            " ${SQLDatabaseAccess.TABLE_PEERINFOS_FIELD_HOST} VARCHAR(255) NOT NULL" +
            ", ${SQLDatabaseAccess.TABLE_PEERINFOS_FIELD_PORT} BIGINT NOT NULL" +
            ", ${SQLDatabaseAccess.TABLE_PEERINFOS_FIELD_PUBKEY} VARCHAR(255) NOT NULL" +
            ", ${SQLDatabaseAccess.TABLE_PEERINFOS_FIELD_TIMESTAMP} TIMESTAMP NOT NULL" +
            ")"

    override val createTableMeta: String = "CREATE TABLE meta (key VARCHAR(255) PRIMARY KEY, value VARCHAR(1000))"

    override val createTableGtxModuleVersion: String = "CREATE TABLE gtx_module_version (module_name VARCHAR(1000) PRIMARY KEY, version BIGINT NOT NULL)"
    override val createDescribeTableFunction: String
        get() = TODO("Not yet implemented")

    override val insertBlocks: String = "INSERT INTO blocks (chain_iid, block_height) VALUES (?, ?) "

    override val insertSnapshots: String
        get() = TODO("Not yet implemented")

    override val insertTransactions: String = "INSERT INTO transactions (chain_iid, tx_rid, tx_data, tx_hash, block_iid) " +
            "VALUES (?, ?, ?, ?, ?) "

    override val insertConfiguration: String = "UPSERT configurations (chain_iid, height, configuration_data) VALUES (?, ?, ?) "

    override fun isSavepointSupported(): Boolean = false

    override fun dropSchemaCascade(schema: String): String {
        return "DROP SCHEMA \"$schema\" CASCADE"
    }

    override fun createSchema(schema: String): String {
        return "CREATE SCHEMA \"$schema\""
    }

    override fun setCurrentSchema(schema: String): String {
        return "SET SCHEMA \"$schema\""
    }
}
// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.core.Transaction
import java.sql.Connection

class PostgreSQLDatabaseAccess : SQLDatabaseAccess() {

    override fun isSavepointSupported(): Boolean = true

    override fun createSchema(connection: Connection, schema: String) {
        val sql = "CREATE SCHEMA IF NOT EXISTS $schema"
        queryRunner.update(connection, sql)
    }

    override fun setCurrentSchema(connection: Connection, schema: String) {
        val sql = "SET search_path TO $schema"
        queryRunner.update(connection, sql)
    }

    override fun dropSchemaCascade(connection: Connection, schema: String) {
        val sql = "DROP SCHEMA IF EXISTS $schema CASCADE"
        queryRunner.update(connection, sql)
    }

    override fun cmdCreateTableBlocks(ctx: EContext): String {
        return "CREATE TABLE ${tableBlocks(ctx)}" +
                " (block_iid BIGSERIAL PRIMARY KEY," +
                "  block_height BIGINT NOT NULL, " +
                "  block_rid BYTEA," +
                "  block_header_data BYTEA," +
                "  block_witness BYTEA," +
                "  timestamp BIGINT," +
                "  UNIQUE (block_rid)," +
                "  UNIQUE (block_height))"
    }

    override fun cmdCreateTablePage(ctx: EContext, name: String): String {
        return "CREATE TABLE ${tablePages(ctx, name)}" +
                " (snapshot_page_iid BIGSERIAL PRIMARY KEY," +
                " block_height BIGINT NOT NULL, " +
                " level INTEGER NOT NULL, " +
                " left_index BIGINT NOT NULL, " +
                " child_hashes BYTEA NOT NULL)"
    }

    override fun cmdCreateTableEvent(ctx: EContext): String {
        return "CREATE TABLE ${tableEvents(ctx)}" +
                " (event_iid BIGSERIAL PRIMARY KEY," +
                " block_height BIGINT NOT NULL, " +
                " data BYTEA NOT NULL)"
    }

    override fun cmdCreateTableState(ctx: EContext): String {
        return "CREATE TABLE ${tableStates(ctx)}" +
                " (state_iid BIGSERIAL PRIMARY KEY," +
                " block_height BIGINT NOT NULL, " +
                " state_n BIGINT NOT NULL, " +
                " data BYTEA NOT NULL)"
    }

    override fun cmdCreateTableBlockchains(): String {
        return "CREATE TABLE ${tableBlockchains()} " +
                " (chain_iid BIGINT PRIMARY KEY," +
                " blockchain_rid BYTEA NOT NULL)"
    }

    override fun cmdCreateTableTransactions(ctx: EContext): String {
        return "CREATE TABLE ${tableTransactions(ctx)} (" +
                "    tx_iid BIGSERIAL PRIMARY KEY, " +
                "    tx_rid BYTEA NOT NULL," +
                "    tx_data BYTEA NOT NULL," +
                "    tx_hash BYTEA NOT NULL," +
                "    block_iid bigint NOT NULL REFERENCES ${tableBlocks(ctx)}(block_iid)," +
                "    UNIQUE (tx_rid))"
    }

    override fun cmdCreateTableConfigurations(ctx: EContext): String {
        return "CREATE TABLE ${tableConfigurations(ctx)} (" +
                "height BIGINT PRIMARY KEY" +
                ", configuration_data BYTEA NOT NULL" +
                ")"
    }

    override fun cmdCreateTablePeerInfos(): String {
        return "CREATE TABLE ${tablePeerinfos()} (" +
                " $TABLE_PEERINFOS_FIELD_HOST text NOT NULL" +
                ", $TABLE_PEERINFOS_FIELD_PORT integer NOT NULL" +
                ", $TABLE_PEERINFOS_FIELD_PUBKEY text PRIMARY KEY NOT NULL" +
                ", $TABLE_PEERINFOS_FIELD_TIMESTAMP timestamp NOT NULL" +
                ")"
    }

    override fun cmdCreateTableBlockchainReplicas(): String {
        return "CREATE TABLE ${tableBlockchainReplicas()} (" +
                " $TABLE_REPLICAS_FIELD_BRID text NOT NULL" +
                ", $TABLE_REPLICAS_FIELD_PUBKEY text NOT NULL REFERENCES ${tablePeerinfos()} (${TABLE_PEERINFOS_FIELD_PUBKEY})" +
                ", PRIMARY KEY ($TABLE_REPLICAS_FIELD_BRID, $TABLE_REPLICAS_FIELD_PUBKEY))"
    }

    override fun cmdCreateTableMustSyncUntil(): String {
        return "CREATE TABLE ${tableMustSyncUntil()} (" +
                " $TABLE_SYNC_UNTIL_FIELD_CHAIN_IID BIGINT PRIMARY KEY NOT NULL REFERENCES ${tableBlockchains()} (chain_iid)" +
                ", $TABLE_SYNC_UNTIL_FIELD_HEIGHT BIGINT NOT NULL" +
                ")"
    }

    override fun cmdCreateTableMeta(): String {
        return "CREATE TABLE ${tableMeta()} (key TEXT PRIMARY KEY, value TEXT)"
    }

    override fun cmdInsertBlocks(ctx: EContext): String {
        return "INSERT INTO ${tableBlocks(ctx)} (block_height) VALUES (?)"
    }

    override fun cmdInsertTransactions(ctx: EContext): String {
        return "INSERT INTO ${tableTransactions(ctx)} (tx_rid, tx_data, tx_hash, block_iid) " +
                "VALUES (?, ?, ?, ?)"
    }

    override fun cmdInsertPages(ctx: EContext, name: String): String {
        return "INSERT INTO ${tablePages(ctx, name)} (block_height, level, left_index, child_hashes) " +
                "VALUES (?, ?, ?, ?)"
    }

    override fun cmdInsertEvents(ctx: EContext): String {
        return "INSERT INTO ${tableEvents(ctx)} (block_height, data) " + "VALUES (?, ?)"
    }

    override fun cmdInsertStates(ctx: EContext): String {
        return "INSERT INTO ${tableStates(ctx)} (block_height, state_n, data) " + "VALUES (?, ?, ?)"
    }

    override fun cmdInsertConfiguration(ctx: EContext): String {
        return "INSERT INTO ${tableConfigurations(ctx)} (height, configuration_data) " +
                "VALUES (?, ?) ON CONFLICT (height) DO UPDATE SET configuration_data = ?"
    }

    override fun cmdCreateTableGtxModuleVersion(ctx: EContext): String {
        return "CREATE TABLE ${tableGtxModuleVersion(ctx)} " +
                "(module_name TEXT PRIMARY KEY," +
                " version BIGINT NOT NULL)"
    }

    override fun insertBlock(ctx: EContext, height: Long): Long {
        val sql = "INSERT INTO ${tableBlocks(ctx)} (block_height) " +
                "VALUES (?) RETURNING block_iid"
        return queryRunner.query(ctx.conn, sql, longRes, height)
    }

    override fun insertTransaction(ctx: BlockEContext, tx: Transaction): Long {
        val sql = "INSERT INTO ${tableTransactions(ctx)} (tx_rid, tx_data, tx_hash, block_iid) " +
                "VALUES (?, ?, ?, ?) RETURNING tx_iid"

        return queryRunner.query(ctx.conn, sql, longRes, tx.getRID(), tx.getRawData(), tx.getHash(), ctx.blockIID)
    }

    override fun addConfigurationData(ctx: EContext, height: Long, data: ByteArray) {
        queryRunner.insert(ctx.conn, cmdInsertConfiguration(ctx), longRes, height, data, data)
    }
}
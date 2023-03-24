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
        return "CREATE TABLE IF NOT EXISTS ${tableBlocks(ctx)}" +
                " (block_iid BIGSERIAL PRIMARY KEY," +
                "  block_height BIGINT NOT NULL, " +
                "  block_rid BYTEA," +
                "  block_header_data BYTEA," +
                "  block_witness BYTEA," +
                "  timestamp BIGINT," +
                "  UNIQUE (block_rid)," +
                "  UNIQUE (block_height))"
    }

    /**
     * @param ctx is the context
     * @param prefix is what the events will be used for, for example "eif" or "icmf"
     */
    override fun cmdCreateTableEvent(ctx: EContext, prefix: String): String {
        return "CREATE TABLE IF NOT EXISTS ${tableEventLeafs(ctx, prefix)}" +
                " (" +
                " block_height BIGINT NOT NULL," +
                " position BIGINT NOT NULL," +
                " hash BYTEA NOT NULL," +
                " tx_iid BIGINT NOT NULL REFERENCES ${tableTransactions(ctx)}(tx_iid), " +
                " data BYTEA NOT NULL," +
                " UNIQUE (hash))"
    }

     override fun cmdCreateTableState(ctx: EContext, prefix: String): String {
         return "CREATE TABLE IF NOT EXISTS ${tableStateLeafs(ctx, prefix)}" +
                 " (state_iid BIGSERIAL PRIMARY KEY," +
                 " block_height BIGINT NOT NULL, " +
                 " state_n BIGINT NOT NULL, " +
                 " data BYTEA NOT NULL)"
     }

    override fun cmdCreateIndexTableState(ctx: EContext, prefix: String, index: Int): String {
        return "CREATE INDEX IF NOT EXISTS ${indexTableStateLeafs(ctx, prefix, index)}" +
                " ON ${tableStateLeafs(ctx, prefix)} USING btree" +
                " (block_height DESC, state_n DESC)"
    }

    override fun cmdCreateTablePage(ctx: EContext, name: String): String {
        return "CREATE TABLE IF NOT EXISTS ${tablePages(ctx, name)}" +
                " (page_iid BIGSERIAL PRIMARY KEY," +
                " block_height BIGINT NOT NULL, " +
                " level INTEGER NOT NULL, " +
                " left_index BIGINT NOT NULL, " +
                " child_hashes BYTEA NOT NULL)"
    }

    override fun cmdCreateTableContainers(): String {
        return "CREATE TABLE ${tableContainers()} " +
                " (container_iid SERIAL PRIMARY KEY," +
                " name TEXT NOT NULL," +
                " UNIQUE (name))"
    }

    override fun cmdCreateTableBlockchains(): String {
        return "CREATE TABLE ${tableBlockchains()} " +
                " (chain_iid BIGINT PRIMARY KEY," +
                " blockchain_rid BYTEA NOT NULL)"
    }

    override fun cmdCreateTableTransactions(ctx: EContext): String {
        return "CREATE TABLE IF NOT EXISTS ${tableTransactions(ctx)} (" +
                "    tx_iid BIGSERIAL PRIMARY KEY, " +
                "    tx_rid BYTEA NOT NULL," +
                "    tx_data BYTEA NOT NULL," +
                "    tx_hash BYTEA NOT NULL," +
                "    block_iid bigint NOT NULL REFERENCES ${tableBlocks(ctx)}(block_iid)," +
                "    UNIQUE (tx_rid))"
    }

    override fun cmdCreateTableConfigurations(ctx: EContext): String {
        return "CREATE TABLE IF NOT EXISTS ${tableConfigurations(ctx)} (" +
                "height BIGINT PRIMARY KEY" +
                ", configuration_data BYTEA NOT NULL" +
                ", configuration_hash BYTEA NOT NULL" +
                ", UNIQUE (configuration_hash)" +
                ")"
    }

    override fun cmdUpdateTableConfigurationsV4First(chainId: Long): String {
        return "ALTER TABLE ${tableConfigurations(chainId)}" +
                " ADD COLUMN configuration_hash BYTEA NULL"
    }

    override fun cmdUpdateTableConfigurationsV4Second(chainId: Long): String {
        return "ALTER TABLE ${tableConfigurations(chainId)}" +
                " ALTER COLUMN configuration_hash SET NOT NULL"
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

    override fun cmdInsertPage(ctx: EContext, name: String): String {
        return "INSERT INTO ${tablePages(ctx, name)} (block_height, level, left_index, child_hashes) " +
                "VALUES (?, ?, ?, ?)"
    }

    override fun cmdInsertConfiguration(ctx: EContext): String {
        return "INSERT INTO ${tableConfigurations(ctx)} (height, configuration_data, configuration_hash) " +
                "VALUES (?, ?, ?) ON CONFLICT (height) DO UPDATE SET configuration_data = ?, configuration_hash = ?"
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

    /**
     * @param ctx is the context
     * @param prefix is what the state will be used for, for example "eif" or "icmf"
     */
    override fun cmdInsertEvent(ctx: EContext, prefix: String): String {
        return "INSERT INTO ${tableEventLeafs(ctx, prefix)} (block_height, position, hash, tx_iid, data) " + "VALUES (?, ?, ?, ?, ?)"
    }

    /**
     * @param ctx is the context
     * @param prefix is what the state will be used for, for example "eif"
     */
    override fun cmdInsertState(ctx: EContext, prefix: String): String {
        return "INSERT INTO ${tableStateLeafs(ctx, prefix)} (block_height, state_n, data) " + "VALUES (?, ?, ?)"
    }

    /**
     * Deletes data from the table where height is <= given minimum-height-to-keep
     *
     * @param ctx is the context
     * @param prefix is what the state will be used for, for example "eif" or "icmf"
     */
    override fun cmdPruneEvents(ctx: EContext, prefix: String): String {
        return "DELETE FROM ${tableEventLeafs(ctx, prefix)} WHERE height <= ?"
    }

    /**
     * Deletes data from the table where:
     *
     * 1. state_n is between left and right
     * 2. height is <= given minimum-height-to-keep
     *
     * TODO: Haven't tried this, could be off by one in the between
     *
     * @param ctx is the context
     * @param prefix is what the state will be used for, for example "eif"
     */
    override fun cmdPruneStates(ctx: EContext, prefix: String): String {
        return "DELETE FROM ${tableStateLeafs(ctx, prefix)} WHERE (state_n BETWEEN ? and ?) AND height <= ?"
    }

    override fun addConfigurationData(ctx: EContext, height: Long, data: ByteArray) {
        val hash = calcConfigurationHash(data)
        queryRunner.insert(ctx.conn, cmdInsertConfiguration(ctx), longRes, height, data, hash, data, hash)
    }
}
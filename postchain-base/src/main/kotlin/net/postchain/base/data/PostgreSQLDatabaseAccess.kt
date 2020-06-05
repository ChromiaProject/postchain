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
                "  chain_iid BIGINT NOT NULL," +
                "  block_header_data BYTEA," +
                "  block_witness BYTEA," +
                "  timestamp BIGINT," +
                "  UNIQUE (chain_iid, block_rid)," +
                "  UNIQUE (chain_iid, block_height))"
    }

    override fun cmdCreateTableBlockchains(): String {
        return "CREATE TABLE ${tableBlockchains()} " +
                " (chain_iid BIGINT PRIMARY KEY," +
                " blockchain_rid BYTEA NOT NULL)"
    }

    override fun cmdCreateTableTransactions(ctx: EContext): String {
        return "CREATE TABLE ${tableTransactions(ctx)} (" +
                "    tx_iid BIGSERIAL PRIMARY KEY, " +
                "    chain_iid bigint NOT NULL," +
                "    tx_rid bytea NOT NULL," +
                "    tx_data bytea NOT NULL," +
                "    tx_hash bytea NOT NULL," +
                "    block_iid bigint NOT NULL REFERENCES blocks(block_iid)," +
                "    UNIQUE (chain_iid, tx_rid))"
    }

    override fun cmdCreateTableConfigurations(ctx: EContext): String {
        return "CREATE TABLE ${tableConfigurations(ctx)} (" +
                " chain_iid bigint NOT NULL" +
                ", height BIGINT NOT NULL" +
                ", configuration_data bytea NOT NULL" +
                ", PRIMARY KEY (chain_iid, height)" +
                ")"
    }

    override fun cmdCreateTableSnapshots(ctx: EContext): String {
        return "CREATE TABLE ${tableSnapshots(ctx)}" +
                " (snapshot_iid BIGSERIAL PRIMARY KEY, " +
                " root_hash bytea NOT NULL, " +
                " block_height BIGINT NOT NULL, " +
                " node_id integer NOT NULL, " +
                " UNIQUE (root_hash, block_height, node_id))"
    }

    override fun cmdCreateTablePeerInfos(): String {
        return "CREATE TABLE ${tablePeerinfos()} (" +
                " $TABLE_PEERINFOS_FIELD_HOST text NOT NULL" +
                ", $TABLE_PEERINFOS_FIELD_PORT integer NOT NULL" +
                ", $TABLE_PEERINFOS_FIELD_PUBKEY text PRIMARY KEY NOT NULL" +
                ", $TABLE_PEERINFOS_FIELD_TIMESTAMP timestamp NOT NULL" +
                ")"
    }

    override fun cmdCreateTableMeta(): String {
        return "CREATE TABLE ${tableMeta()} (key TEXT PRIMARY KEY, value TEXT)"
    }

    override fun cmdInsertBlocks(ctx: EContext): String {
        return "INSERT INTO ${tableBlocks(ctx)} (chain_iid, block_height) VALUES (?, ?)"
    }

    override fun cmdInsertSnapshots(ctx: EContext): String {
        return "INSERT INTO ${tableSnapshots(ctx)} (root_hash, block_height, node_id) VALUES (?, ?, ?)"
    }

    override fun cmdInsertTransactions(ctx: EContext): String {
        return "INSERT INTO ${tableTransactions(ctx)} (chain_iid, tx_rid, tx_data, tx_hash, block_iid) " +
                "VALUES (?, ?, ?, ?, ?)"
    }

    override fun cmdInsertConfiguration(ctx: EContext): String {
        return "INSERT INTO ${tableConfigurations(ctx)} (chain_iid, height, configuration_data) " +
                "VALUES (?, ?, ?) ON CONFLICT (chain_iid, height) DO UPDATE SET configuration_data = ?"
    }

    override fun cmdCreateTableGtxModuleVersion(ctx: EContext): String {
        return "CREATE TABLE ${tableGtxModuleVersion(ctx)} " +
                "(module_name TEXT PRIMARY KEY," +
                " version BIGINT NOT NULL)"
    }

    override fun cmdCreateDescribeTableFunction(ctx: EContext): String {
        return """
            CREATE OR REPLACE FUNCTION public.describe_table(p_schema_name character varying, p_table_name character varying)
            RETURNS SETOF text AS
            ${'$'}BODY${'$'}
            DECLARE
                v_table_ddl   text;
                column_record record;
                table_rec record;
                constraint_rec record;
                firstrec boolean;
            BEGIN
                FOR table_rec IN
                    SELECT c.relname, c.oid FROM pg_catalog.pg_class c
                        LEFT JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                            WHERE relkind = 'r'
                            AND n.nspname = p_schema_name
                            AND relname~ ('^('||p_table_name||')${'$'}')
                    ORDER BY c.relname
                LOOP
                    FOR column_record IN
                        SELECT
                            b.nspname as schema_name,
                            b.relname as table_name,
                            a.attname as column_name,
                            pg_catalog.format_type(a.atttypid, a.atttypmod) as column_type,
                            CASE WHEN
                                (SELECT substring(pg_catalog.pg_get_expr(d.adbin, d.adrelid) for 128)
                                FROM pg_catalog.pg_attrdef d
                                WHERE d.adrelid = a.attrelid AND d.adnum = a.attnum AND a.atthasdef) IS NOT NULL THEN
                                'DEFAULT '|| (SELECT substring(pg_catalog.pg_get_expr(d.adbin, d.adrelid) for 128)
                                    FROM pg_catalog.pg_attrdef d
                                    WHERE d.adrelid = a.attrelid AND d.adnum = a.attnum AND a.atthasdef)
                            ELSE
                                ''
                            END as column_default_value,
                            CASE WHEN a.attnotnull = true THEN
                                'NOT NULL'
                            ELSE
                                'NULL'
                            END as column_not_null,
                            a.attnum as attnum,
                            e.max_attnum as max_attnum
                        FROM
                            pg_catalog.pg_attribute a
                            INNER JOIN
                            (SELECT c.oid,
                                n.nspname,
                                c.relname
                            FROM pg_catalog.pg_class c
                                LEFT JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                            WHERE c.oid = table_rec.oid
                            ORDER BY 2, 3) b
                            ON a.attrelid = b.oid
                            INNER JOIN
                            (SELECT
                                a.attrelid,
                                max(a.attnum) as max_attnum
                            FROM pg_catalog.pg_attribute a
                            WHERE a.attnum > 0
                                AND NOT a.attisdropped
                            GROUP BY a.attrelid) e
                            ON a.attrelid=e.attrelid
                        WHERE a.attnum > 0
                        AND NOT a.attisdropped
                        ORDER BY a.attnum
                    LOOP
                        IF column_record.attnum = 1 THEN
                            v_table_ddl:='CREATE TABLE '||column_record.schema_name||'.'||column_record.table_name||' (';
                        ELSE
                            v_table_ddl:=v_table_ddl||',';
                        END IF;
    
                        IF column_record.attnum <= column_record.max_attnum THEN
                            v_table_ddl:=v_table_ddl||chr(10)||
                                    '    '||column_record.column_name||' '||column_record.column_type||' '||column_record.column_default_value||' '||column_record.column_not_null;
                        END IF;
                    END LOOP;
    
                    firstrec := TRUE;
                    FOR constraint_rec IN
                        SELECT conname, pg_get_constraintdef(c.oid) as constrainddef
                            FROM pg_constraint c
                                WHERE conrelid=(
                                    SELECT attrelid FROM pg_attribute
                                    WHERE attrelid = (
                                        SELECT oid FROM pg_class WHERE relname = table_rec.relname
                                            AND relnamespace = (SELECT ns.oid FROM pg_namespace ns WHERE ns.nspname = p_schema_name)
                                    ) AND attname='tableoid'
                                )
                    LOOP
                        v_table_ddl:=v_table_ddl||','||chr(10);
                        v_table_ddl:=v_table_ddl||'CONSTRAINT '||constraint_rec.conname;
                        v_table_ddl:=v_table_ddl||chr(10)||'    '||constraint_rec.constrainddef;
                        firstrec := FALSE;
                    END LOOP;
                    v_table_ddl:=v_table_ddl||');';
                    RETURN NEXT v_table_ddl;
                END LOOP;
            END;
            ${'$'}BODY${'$'}
            LANGUAGE plpgsql VOLATILE
            COST 100;
        """.trimIndent()
    }

    override fun insertBlock(ctx: EContext, height: Long): Long {
        val sql = "INSERT INTO ${tableBlocks(ctx)} (chain_iid, block_height) " +
                "VALUES (?, ?) RETURNING block_iid"

        return queryRunner.query(
                ctx.conn,
                sql,
                longRes,
                ctx.chainID,
                height)
    }

    override fun insertSnapshot(ctx: EContext, rootHash: ByteArray, height: Long): Long {
        val sql = "INSERT INTO ${tableSnapshots(ctx)} (root_hash, block_height, node_id) " +
                "VALUES (?, ?, ?) RETURNING snapshot_iid"

        return queryRunner.query(ctx.conn, sql, longRes, rootHash, height, ctx.nodeID)
    }

    override fun insertTransaction(ctx: BlockEContext, tx: Transaction): Long {
        val sql = "INSERT INTO ${tableTransactions(ctx)} (chain_iid, tx_rid, tx_data, tx_hash, block_iid) " +
                "VALUES (?, ?, ?, ?, ?) RETURNING tx_iid"

        return queryRunner.query(
                ctx.conn,
                sql,
                longRes,
                ctx.chainID,
                tx.getRID(),
                tx.getRawData(),
                tx.getHash(),
                ctx.blockIID)
    }

    override fun addConfigurationData(ctx: EContext, height: Long, data: ByteArray) {
        queryRunner.insert(
                ctx.conn,
                cmdInsertConfiguration(ctx),
                longRes,
                ctx.chainID,
                height,
                data,
                data)
    }
}
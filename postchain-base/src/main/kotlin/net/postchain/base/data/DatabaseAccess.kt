// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import mu.KLogging
import net.postchain.base.BaseBlockHeader
import net.postchain.base.BlockchainRid
import net.postchain.base.gtv.RowData
import net.postchain.common.toHex
import net.postchain.core.*
import net.postchain.gtv.*
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.*
import java.sql.Connection

interface DatabaseAccess {
    class BlockInfo(val blockIid: Long, val blockHeader: ByteArray, val witness: ByteArray)
    class BlockInfoExt(val blockRid: ByteArray, val blockHeight: Long, val blockHeader: ByteArray, val witness: ByteArray, val timestamp: Long)

    fun initialize(connection: Connection, expectedDbVersion: Int)
    fun getChainId(ctx: EContext, blockchainRID: BlockchainRid): Long?
    fun checkBlockchainRID(ctx: EContext, blockchainRID: BlockchainRid)

    fun getBlockchainRID(ctx: EContext): BlockchainRid?
    fun insertBlock(ctx: EContext, height: Long): Long
    fun insertTransaction(ctx: BlockEContext, tx: Transaction): Long
    fun finalizeBlock(ctx: BlockEContext, header: BlockHeader)

    fun commitBlock(bctx: BlockEContext, w: BlockWitness)
    fun getBlockHeight(ctx: EContext, blockRID: ByteArray, chainId: Long): Long?
    fun getBlockRID(ctx: EContext, height: Long): ByteArray?
    fun getBlockHeader(ctx: EContext, blockRID: ByteArray): ByteArray
    fun getBlockTransactions(ctx: EContext, blockRID: ByteArray, hashesOnly: Boolean): List<TxDetail>
    fun getWitnessData(ctx: EContext, blockRID: ByteArray): ByteArray
    fun getLastBlockHeight(ctx: EContext): Long
    fun getLastBlockRid(ctx: EContext, chainId: Long): ByteArray?
    fun getBlockHeightInfo(ctx: EContext, bcRid: BlockchainRid): Pair<Long, ByteArray>?
    fun getLastBlockTimestamp(ctx: EContext): Long
    fun getTxRIDsAtHeight(ctx: EContext, height: Long): Array<ByteArray>
    fun getBlockInfo(ctx: EContext, txRID: ByteArray): BlockInfo
    fun getTxHash(ctx: EContext, txRID: ByteArray): ByteArray
    fun getBlockTxRIDs(ctx: EContext, blockIid: Long): List<ByteArray>
    fun getBlockTxHashes(ctx: EContext, blockIid: Long): List<ByteArray>
    fun getTxBytes(ctx: EContext, txRID: ByteArray): ByteArray?
    fun isTransactionConfirmed(ctx: EContext, txRID: ByteArray): Boolean
    fun getBlocks(ctx: EContext, blockHeight: Long, asc: Boolean, limit: Int): List<BlockInfoExt>

    // Blockchain configurations
    fun findConfigurationHeightForBlock(context: EContext, height: Long): Long?

    // Get data to build snapshot
    fun getTxsInRange(context: EContext, limit: Int, offset: Long, original: Long = 0): List<RowData>
    fun getTxsCount(context: EContext): Long
    fun getBlocksInRange(context: EContext, limit: Int, offset: Long, original: Long = 0): List<RowData>
    fun getBlocksCount(context: EContext): Long
    fun getTables(context: EContext): List<String>
    // Query data for rellr app
    fun getDataInRange(context: EContext, tableName: String, limit: Int, offset: Long, original: Long = 0): List<RowData>
    fun getRowCount(context: EContext, tableName: String): Long

    fun getConfigurationData(context: EContext, height: Long): ByteArray?
    fun addConfigurationData(context: EContext, height: Long, data: ByteArray)

    companion object {
        fun of(ctx: EContext): DatabaseAccess {
            return ctx.getInterface(DatabaseAccess::class.java)
                    ?: throw ProgrammerMistake("DatabaseAccess not accessible through EContext")
        }
    }
}

open class SQLDatabaseAccess(val sqlCommands: SQLCommands) : DatabaseAccess {
    var queryRunner = QueryRunner()
    private val intRes = ScalarHandler<Int>()
    val longRes = ScalarHandler<Long>()
    private val signatureRes = BeanListHandler<Signature>(Signature::class.java)
    private val nullableByteArrayRes = ScalarHandler<ByteArray?>()
    private val nullableIntRes = ScalarHandler<Int?>()
    private val nullableLongRes = ScalarHandler<Long?>()
    private val byteArrayRes = ScalarHandler<ByteArray>()
    private val blockDataRes = BeanHandler<BlockData>(BlockData::class.java)
    private val byteArrayListRes = ColumnListHandler<ByteArray>()
    private val mapListHandler = MapListHandler()
    private val stringRes = ScalarHandler<String>()

    companion object : KLogging() {
        const val TABLE_PEERINFOS = "peerinfos"
        const val TABLE_PEERINFOS_FIELD_HOST = "host"
        const val TABLE_PEERINFOS_FIELD_PORT = "port"
        const val TABLE_PEERINFOS_FIELD_PUBKEY = "pub_key"
        const val TABLE_PEERINFOS_FIELD_TIMESTAMP = "timestamp"
    }

    override fun insertBlock(ctx: EContext, height: Long): Long {
        queryRunner.update(ctx.conn, sqlCommands.insertBlocks, ctx.chainID, height)
        return queryRunner.query(ctx.conn, "SELECT block_iid FROM blocks WHERE chain_iid = ? and block_height = ?", longRes, ctx.chainID, height)
    }

    override fun insertTransaction(ctx: BlockEContext, tx: Transaction): Long {
        queryRunner.update(ctx.conn, sqlCommands.insertTransactions, ctx.chainID, tx.getRID(), tx.getRawData(), tx.getHash(), ctx.blockIID)
        return queryRunner.query(ctx.conn, "SELECT tx_iid FROM transactions WHERE chain_iid= ? and tx_rid = ?", longRes, ctx.chainID, tx.getRID())
    }

    override fun finalizeBlock(ctx: BlockEContext, header: BlockHeader) {
        queryRunner.update(ctx.conn,
                "UPDATE blocks SET block_rid = ?, block_header_data = ?, timestamp = ? WHERE chain_iid= ? AND block_iid = ?",
                header.blockRID, header.rawData, (header as BaseBlockHeader).timestamp, ctx.chainID, ctx.blockIID
        )
    }

    override fun commitBlock(bctx: BlockEContext, w: BlockWitness) {
        queryRunner.update(bctx.conn,
                "UPDATE blocks SET block_witness = ? WHERE block_iid=?",
                w.getRawData(), bctx.blockIID)
    }

    override fun getBlockHeight(ctx: EContext, blockRID: ByteArray, chainId: Long): Long? {
        return queryRunner.query(ctx.conn, "SELECT block_height FROM blocks where chain_iid= ? and block_rid = ?",
                nullableLongRes, chainId, blockRID)
    }

    // The combination of CHAIN_ID and BLOCK_HEIGHT is unique
    override fun getBlockRID(ctx: EContext, height: Long): ByteArray? {
        return queryRunner.query(ctx.conn,
                "SELECT block_rid FROM blocks WHERE chain_iid= ? AND block_height = ?",
                nullableByteArrayRes, ctx.chainID, height)
    }

    override fun getBlockHeader(ctx: EContext, blockRID: ByteArray): ByteArray {
        return queryRunner.query(ctx.conn, "SELECT block_header_data FROM blocks where chain_iid= ? and block_rid = ?",
                byteArrayRes, ctx.chainID, blockRID)
    }

    override fun getBlockTransactions(ctx: EContext, blockRID: ByteArray, hashesOnly: Boolean): List<TxDetail> {
        val sql = """
            SELECT tx_rid, tx_hash${if (hashesOnly) "" else ", tx_data"}
            FROM transactions t
            JOIN blocks b ON t.block_iid=b.block_iid
            WHERE b.block_rid=? AND b.chain_iid=?
            ORDER BY tx_iid
        """.trimIndent()

        val txs = queryRunner.query(ctx.conn, sql, mapListHandler, blockRID, ctx.chainID)

        return txs.map { tx ->
            TxDetail(
                    tx["tx_rid"] as ByteArray,
                    tx["tx_hash"] as ByteArray,
                    if (hashesOnly) null else (tx["tx_data"] as ByteArray)
            )
        }
    }

    override fun getWitnessData(ctx: EContext, blockRID: ByteArray): ByteArray {
        return queryRunner.query(ctx.conn,
                "SELECT block_witness FROM blocks WHERE chain_iid= ? AND block_rid = ?",
                byteArrayRes, ctx.chainID, blockRID)
    }

    override fun getLastBlockHeight(ctx: EContext): Long {
        return queryRunner.query(ctx.conn,
                "SELECT block_height FROM blocks WHERE chain_iid= ? ORDER BY block_height DESC LIMIT 1",
                longRes, ctx.chainID) ?: -1L
    }

    override fun getLastBlockRid(ctx: EContext, chainId: Long): ByteArray? {
        return queryRunner.query(ctx.conn,
                "SELECT block_rid FROM blocks WHERE chain_iid= ? ORDER BY block_height DESC LIMIT 1",
                nullableByteArrayRes, chainId)
    }

    override fun getBlockHeightInfo(ctx: EContext, bcRid: BlockchainRid): Pair<Long, ByteArray>? {
        val res = queryRunner.query(ctx.conn, """
                    SELECT b.block_height, b.block_rid
                         FROM blocks b
                         JOIN blockchains bc ON bc.chain_iid= b.chain_iid
                         WHERE bc.blockchain_rid = ?
                         ORDER BY b.block_height DESC LIMIT 1
                         """, mapListHandler, bcRid.data)

        return when (res.size) {
            0 -> {
                null // This is allowed, it (usually) means we don't have any blocks yet
            }
            1 -> {
                val r = res[0]
                val height = r["block_height"] as Long
                val blockRid = r["block_rid"] as ByteArray
                Pair(height, blockRid)
            }
            else -> {
                throw ProgrammerMistake("Incorrect query getBlockHeightInfo got many lines (${res.size})")
            }
        }
    }

    override fun getLastBlockTimestamp(ctx: EContext): Long {
        return queryRunner.query(ctx.conn,
                "SELECT timestamp FROM blocks WHERE chain_iid= ? ORDER BY timestamp DESC LIMIT 1",
                longRes, ctx.chainID) ?: -1L
    }

    override fun getTxRIDsAtHeight(ctx: EContext, height: Long): Array<ByteArray> {
        return queryRunner.query(ctx.conn,
                "SELECT tx_rid FROM " +
                        "transactions t " +
                        "INNER JOIN blocks b ON t.block_iid=b.block_iid " +
                        "where b.block_height=? and b.chain_iid=?",
                ColumnListHandler<ByteArray>(), height, ctx.chainID).toTypedArray()
    }

    override fun getBlockInfo(ctx: EContext, txRID: ByteArray): DatabaseAccess.BlockInfo {
        val block = queryRunner.query(ctx.conn,
                """
                    SELECT b.block_iid, b.block_header_data, b.block_witness
                    FROM blocks b
                    JOIN transactions t ON b.block_iid=t.block_iid
                    WHERE t.chain_iid=? and t.tx_rid=?
                    """, mapListHandler, ctx.chainID, txRID)!!
        if (block.size < 1) throw UserMistake("Can't get confirmation proof for nonexistent tx")
        if (block.size > 1) throw ProgrammerMistake("Expected at most one hit")
        val blockIid = block[0]["block_iid"] as Long
        val blockHeader = block[0]["block_header_data"] as ByteArray
        val witness = block[0]["block_witness"] as ByteArray
        return DatabaseAccess.BlockInfo(blockIid, blockHeader, witness)
    }

    override fun getTxHash(ctx: EContext, txRID: ByteArray): ByteArray {
        return queryRunner.query(ctx.conn,
                "SELECT tx_hash FROM transactions WHERE tx_rid = ? and chain_iid=?",
                byteArrayRes, txRID, ctx.chainID)
    }

    override fun getBlockTxRIDs(ctx: EContext, blockIid: Long): List<ByteArray> {
        return queryRunner.query(ctx.conn,
                "SELECT tx_rid FROM " +
                        "transactions t " +
                        "where t.block_iid=? order by tx_iid",
                ColumnListHandler<ByteArray>(), blockIid)!!
    }

    override fun getBlockTxHashes(ctx: EContext, blockIid: Long): List<ByteArray> {
        return queryRunner.query(ctx.conn,
                "SELECT tx_hash FROM " +
                        "transactions t " +
                        "where t.block_iid=? order by tx_iid",
                ColumnListHandler<ByteArray>(), blockIid)!!
    }

    override fun getTxBytes(ctx: EContext, txRID: ByteArray): ByteArray? {
        return queryRunner.query(ctx.conn, "SELECT tx_data FROM transactions WHERE chain_iid=? AND tx_rid=?",
                nullableByteArrayRes, ctx.chainID, txRID)
    }

    override fun isTransactionConfirmed(ctx: EContext, txRID: ByteArray): Boolean {
        val res = queryRunner.query(ctx.conn,
                """
                        SELECT 1 FROM transactions t
                        WHERE t.chain_iid=? AND t.tx_rid=?
                        """, nullableIntRes, ctx.chainID, txRID)
        return (res != null)
    }

    override fun getBlockchainRID(ctx: EContext): BlockchainRid? {
        val data = queryRunner.query(ctx.conn, "SELECT blockchain_rid FROM blockchains WHERE chain_iid= ?",
                nullableByteArrayRes, ctx.chainID)
        return if (data == null) null else BlockchainRid(data)
    }

    override fun initialize(connection: Connection, expectedDbVersion: Int) {
        /**
         * "CREATE TABLE IF NOT EXISTS" is not good enough for the meta table
         * We need to know whether it exists or not in order to
         * make decisions on upgrade
         */

        if (tableExists(connection, "meta")) {
            // meta table already exists. Check the version
            val versionString = queryRunner.query(connection, "SELECT value FROM meta WHERE key='version'", ScalarHandler<String>())
            val version = versionString.toInt()
            if (version != expectedDbVersion) {
                throw UserMistake("Unexpected version '$version' in database. Expected '$expectedDbVersion'")
            }

        } else {
            // meta table does not exist! Assume database does not exist.
            queryRunner.update(connection, sqlCommands.createTableMeta)
            queryRunner.update(
                    connection,
                    "INSERT INTO meta (key, value) values ('version', ?)",
                    expectedDbVersion)


            // Don't use "CREATE TABLE IF NOT EXISTS" because if they do exist
            // we must throw an error. If these tables exists but meta did not exist,
            // there is some serious problem that needs manual work
            queryRunner.update(connection, sqlCommands.createTableBlockChains)

            queryRunner.update(connection, sqlCommands.createTableBlocks)

            queryRunner.update(connection, sqlCommands.createTableTransactions)

            // Configurations
            queryRunner.update(connection, sqlCommands.createTableConfiguration)

            // PeerInfos
            queryRunner.update(connection, sqlCommands.createTablePeerInfos)

            queryRunner.update(connection, """CREATE INDEX transactions_block_iid_idx ON transactions(block_iid)""")
            queryRunner.update(connection, """CREATE INDEX blocks_chain_iid_timestamp ON blocks(chain_iid, timestamp)""")
            //queryRunner.update(connection, """CREATE INDEX configurations_chain_iid_to_height ON configurations(chain_iid, height)""")
        }
    }

    override fun getChainId(ctx: EContext, blockchainRID: BlockchainRid): Long? {
        return queryRunner.query(ctx.conn,
                "SELECT chain_iid FROM blockchains WHERE blockchain_rid=?",
                nullableLongRes,
                blockchainRID.data)
    }

    override fun checkBlockchainRID(ctx: EContext, blockchainRID: BlockchainRid) {
        // Check that the blockchainRID is present for chain_iid
        val rid = queryRunner.query(
                ctx.conn,
                "SELECT blockchain_rid from blockchains where chain_iid=?",
                nullableByteArrayRes,
                ctx.chainID)

        logger.debug("chainId = ${ctx.chainID} = BC RID ${rid?.toHex() ?: "null"}")
        if (rid == null) {
            logger.info("Blockchain RID: ${blockchainRID.toHex()} doesn't exist in DB, so we add it.")
            queryRunner.update(
                    ctx.conn,
                    "INSERT INTO blockchains (chain_iid, blockchain_rid) values (?, ?)",
                    ctx.chainID,
                    blockchainRID.data)

        } else if (!rid.contentEquals(blockchainRID.data)) {
            throw UserMistake("The blockchainRID in db for chainId ${ctx.chainID} " +
                    "is ${rid.toHex()}, but the expected rid is ${blockchainRID.toHex()}")
        } else {
            logger.debug("Verified that Blockchain RID: ${blockchainRID.toHex()} exists in DB.")
        }
    }

    override fun getBlocks(ctx: EContext, blockHeight: Long, asc: Boolean, limit: Int): List<DatabaseAccess.BlockInfoExt> {
        val blocksInfo = queryRunner.query(ctx.conn,
                "SELECT block_rid, block_height, block_header_data, block_witness, timestamp " +
                        "FROM blocks WHERE block_height ${if (asc) ">" else "<"} ? " +
                        "ORDER BY timestamp ${if (asc) "ASC" else "DESC"} " +
                        "LIMIT ?",
                mapListHandler,
                blockHeight,
                limit)

        return blocksInfo.map { blockInfo ->
            val blockRid = blockInfo["block_rid"] as ByteArray
            val blockHeight = blockInfo["block_height"] as Long
            val blockHeader = blockInfo["block_header_data"] as ByteArray
            val blockWitness = blockInfo["block_witness"] as ByteArray
            val timestamp = blockInfo["timestamp"] as Long
            DatabaseAccess.BlockInfoExt(blockRid, blockHeight, blockHeader, blockWitness, timestamp)
        }
    }

    override fun findConfigurationHeightForBlock(context: EContext, height: Long): Long? {
        return queryRunner.query(context.conn,
                "SELECT height FROM configurations WHERE chain_iid= ? AND height <= ? " +
                        "ORDER BY height DESC LIMIT 1",
                nullableLongRes, context.chainID, height)
    }

    override fun getTables(context: EContext): List<String> {
        return queryRunner.query(context.conn,
                """SELECT table_name FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name""",
                ColumnListHandler(), context.conn.schema)
    }

    override fun getTxsInRange(context: EContext, limit: Int, offset: Long, original: Long): List<RowData> {
        var rows = queryRunner.query(context.conn,
                """SELECT * FROM (
                    SELECT (row_number() OVER (ORDER BY chain_iid) + ?) AS row_id, tx_iid FROM transactions) x 
                    WHERE row_id BETWEEN ? AND ?""", mapListHandler, original, offset+1, offset+limit)

        return rows.map { row ->
            val rowId = row["row_id"] as Long
            val txIid = row["tx_iid"] as Long
            RowData(GtvInteger(rowId), GtvString("transactions"), TxData(txIid).toGtv())
        }
    }

    override fun getTxsCount(context: EContext): Long {
        return queryRunner.query(context.conn, "SELECT count(*) FROM transactions", longRes)
    }

    override fun getBlocksInRange(context: EContext, limit: Int, offset: Long, original: Long): List<RowData> {
        var rows = queryRunner.query(context.conn,
                """SELECT * FROM (
                    SELECT (row_number() OVER (ORDER BY chain_iid) + ?) AS row_id, block_iid, block_rid, block_height, block_header_data, block_witness, timestamp FROM blocks) x  
                    WHERE row_id BETWEEN ? AND ?""", mapListHandler, original, offset+1, offset+limit)

        return rows.map { row ->
            val rowId = row["row_id"] as Long
            val blockIid = row["block_iid"] as Long
//            val blockRid = row["block_rid"] as ByteArray
//            val blockHeight = row["block_height"] as Long
            val blockHeader = row["block_header_data"] as ByteArray
            val blockWitness = row["block_witness"] as ByteArray
//            val timestamp = row["timestamp"] as Long
            RowData(GtvInteger(rowId), GtvString("blocks"), BlockData(blockIid, blockHeader, blockWitness).toGtv())
        }
    }

    override fun getBlocksCount(context: EContext): Long {
        return queryRunner.query(context.conn, "SELECT count(*) FROM blocks", longRes)
    }

    override fun getDataInRange(context: EContext, tableName: String, limit: Int, offset: Long, original: Long): List<RowData> {
        val cols = getTableColumns(context, tableName)

        var query = "SELECT * FROM (SELECT (row_number() OVER (ORDER BY 1) + ?) AS row_id"
        cols.forEach { query += ", ${it.name}" }

        query += " FROM $tableName) x WHERE row_id BETWEEN ? AND ?"

        var rows = queryRunner.query(context.conn, query, mapListHandler, original, offset+1, offset+limit)
        return rows.map { row ->
            val rowId = row["row_id"] as Long
            val data = mutableMapOf<String, Gtv>()
            cols.forEach {
                data[it.name] = when (it.type) {
                    "bigint" -> {
                        GtvInteger(row[it.name] as Long)
                    }
                    "bytea" -> {
                        GtvByteArray(row[it.name] as ByteArray)
                    }
                    "text" -> {
                        GtvString(row[it.name] as String)
                    }
                    else -> {
                        GtvNull
                    }
                }
            }
            RowData(GtvInteger(rowId), GtvString(tableName), GtvFactory.gtv(GtvDictionary.build(data)))
        }
    }

    override fun getRowCount(context: EContext, tableName: String): Long {
        return queryRunner.query(context.conn, "SELECT count(*) FROM $tableName", longRes)
    }

    override fun getConfigurationData(context: EContext, height: Long): ByteArray? {
        return queryRunner.query(context.conn,
                "SELECT configuration_data FROM configurations WHERE chain_iid= ? AND height = ?",
                nullableByteArrayRes, context.chainID, height)
    }

    override fun addConfigurationData(context: EContext, height: Long, data: ByteArray) {
        queryRunner.update(context.conn, sqlCommands.insertConfiguration, context.chainID, height, data)
    }

    fun tableExists(connection: Connection, tableName_: String): Boolean {
        val types: Array<String> = arrayOf("TABLE")

        val tableName = if (connection.metaData.storesUpperCaseIdentifiers()) {
            tableName_.toUpperCase()
        } else {
            tableName_
        }

        val rs = connection.metaData.getTables(null, null, tableName, types)
        while (rs.next()) {
            // avoid wildcard '_' in SQL. Eg: if you pass "employee_salary" that should return something employeesalary which we don't expect
            if (rs.getString(2).toLowerCase() == connection.schema.toLowerCase()
                    && rs.getString(3).toLowerCase() == tableName.toLowerCase()) {
                return true
            }
        }
        return false
    }

    private fun getTableColumns(context: EContext, tableName: String): List<Column> {
        var rows = queryRunner.query(context.conn,
                """SELECT column_name, data_type FROM information_schema.columns WHERE table_schema = ? AND table_name = ?""",
                mapListHandler, context.conn.schema, tableName)

        return rows.map { row ->
            val name = row["column_name"] as String
            val type = row["data_type"] as String

            Column(name, type)
        }
    }

}

data class Column(val name: String, val type: String) {

}

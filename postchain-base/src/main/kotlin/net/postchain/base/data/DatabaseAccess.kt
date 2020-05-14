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
    fun insertSnapshot(ctx: EContext, rootHash: ByteArray, height: Long): Long
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
    fun getBlockchainsInRange(ctx: EContext, limit: Int, offset: Long, original: Long = 0): List<RowData>
    fun getConfigurationsInRange(ctx: EContext, limit: Int, offset: Long, original: Long = 0): List<RowData>
    fun getMetaInRange(ctx: EContext, limit: Int, offset: Long, original: Long = 0): List<RowData>
    fun getPeerInfosInRange(ctx: EContext, limit: Int, offset: Long, original: Long = 0): List<RowData>
    fun getTxsInRange(ctx: EContext, limit: Int, offset: Long, original: Long = 0): List<RowData>
    fun getBlocksInRange(ctx: EContext, limit: Int, offset: Long, original: Long = 0): List<RowData>
    fun getTables(ctx: EContext): List<String>
    // Query data for rellr app
    fun getDataInRange(ctx: EContext, tableName: String, limit: Int, offset: Long, original: Long = 0): List<RowData>
    fun getRowCount(ctx: EContext, tableName: String): Long
    fun getTableDDL(ctx: EContext, tableName: String): String

    fun getConfigurationData(ctx: EContext, height: Long): ByteArray?
    fun addConfigurationData(ctx: EContext, height: Long, data: ByteArray)

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
        return queryRunner.query(ctx.conn, "SELECT block_iid FROM blocks WHERE chain_iid = ? AND block_height = ?", longRes, ctx.chainID, height)
    }

    override fun insertSnapshot(ctx: EContext, rootHash: ByteArray, height: Long): Long {
        queryRunner.update(ctx.conn, sqlCommands.insertSnapshots, ctx.chainID, rootHash, height, ctx.nodeID)
        return queryRunner.query(ctx.conn, "SELECT snapshot_iid FROM snapshots WHERE chain_iid = ? AND root_hash = ? AND block_height = ? AND node_id = ?", longRes, ctx.chainID, rootHash, height, ctx.nodeID)
    }

    override fun insertTransaction(ctx: BlockEContext, tx: Transaction): Long {
        queryRunner.update(ctx.conn, sqlCommands.insertTransactions, ctx.chainID, tx.getRID(), tx.getRawData(), tx.getHash(), ctx.blockIID)
        return queryRunner.query(ctx.conn, "SELECT tx_iid FROM transactions WHERE chain_iid= ? AND tx_rid = ?", longRes, ctx.chainID, tx.getRID())
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

            queryRunner.update(connection, sqlCommands.createTableSnapshots)

            queryRunner.update(connection, sqlCommands.createTableBlocks)

            queryRunner.update(connection, sqlCommands.createTableTransactions)

            // Configurations
            queryRunner.update(connection, sqlCommands.createTableConfiguration)

            // PeerInfos
            queryRunner.update(connection, sqlCommands.createTablePeerInfos)

            queryRunner.update(connection, """CREATE INDEX transactions_block_iid_idx ON transactions(block_iid)""")
            queryRunner.update(connection, """CREATE INDEX blocks_chain_iid_timestamp ON blocks(chain_iid, timestamp)""")
            //queryRunner.update(connection, """CREATE INDEX configurations_chain_iid_to_height ON configurations(chain_iid, height)""")

            // Create function to get the rellr app tables' ddl
            queryRunner.update(connection, sqlCommands.createDescribeTableFunction)
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

    override fun findConfigurationHeightForBlock(ctx: EContext, height: Long): Long? {
        return queryRunner.query(ctx.conn,
                "SELECT height FROM configurations WHERE chain_iid= ? AND height <= ? " +
                        "ORDER BY height DESC LIMIT 1",
                nullableLongRes, ctx.chainID, height)
    }

    override fun getBlockchainsInRange(ctx: EContext, limit: Int, offset: Long, original: Long): List<RowData> {
        var rows = queryRunner.query(ctx.conn,
                """SELECT * FROM (
                    SELECT (row_number() OVER (ORDER BY chain_iid) + ?) AS row_id, chain_iid, blockchain_rid FROM blockchains) x  
                    WHERE row_id BETWEEN ? AND ? AND chain_iid= ?""", mapListHandler, original, offset+1, offset+limit, ctx.chainID)

        return rows.map { row ->
            val rowId = row["row_id"] as Long
            val chainIid = row["chain_iid"] as Long
            val blockchainRid = row["blockchain_rid"] as ByteArray

            val blockchain = BlockchainData(chainIid, blockchainRid)
            RowData(GtvInteger(rowId), GtvString("blockchains"), blockchain.toGtv(), blockchain.toHashGtv())
        }
    }

    override fun getConfigurationsInRange(ctx: EContext, limit: Int, offset: Long, original: Long): List<RowData> {
        var rows = queryRunner.query(ctx.conn,
                """SELECT * FROM (
                    SELECT (row_number() OVER (ORDER BY chain_iid) + ?) AS row_id, chain_iid, height, configuration_data FROM configurations) x  
                    WHERE row_id BETWEEN ? AND ? AND chain_iid= ?""", mapListHandler, original, offset+1, offset+limit, ctx.chainID)

        return rows.map { row ->
            val rowId = row["row_id"] as Long
            val chainIid = row["chain_iid"] as Long
            val height = row["height"] as Long
            val data = row["configuration_data"] as ByteArray

            val config = ConfigurationData(chainIid, height, data)
            RowData(GtvInteger(rowId), GtvString("configurations"), config.toGtv(), config.toHashGtv())
        }
    }

    override fun getMetaInRange(ctx: EContext, limit: Int, offset: Long, original: Long): List<RowData> {
        var rows = queryRunner.query(ctx.conn,
                """SELECT * FROM (
                    SELECT (row_number() OVER (ORDER BY 1) + ?) AS row_id, key, value FROM meta) x  
                    WHERE row_id BETWEEN ? AND ?""", mapListHandler, original, offset+1, offset+limit)

        return rows.map { row ->
            val rowId = row["row_id"] as Long
            val key = row["key"] as String
            val value = row["value"] as String?

            val metadata = MetaData(key, value)
            RowData(GtvInteger(rowId), GtvString("meta"), metadata.toGtv(), metadata.toHashGtv())
        }
    }

    override fun getPeerInfosInRange(ctx: EContext, limit: Int, offset: Long, original: Long): List<RowData> {
        var rows = queryRunner.query(ctx.conn,
                """SELECT * FROM (
                    SELECT (row_number() OVER (ORDER BY 1) + ?) AS row_id, $TABLE_PEERINFOS_FIELD_HOST, $TABLE_PEERINFOS_FIELD_PORT, $TABLE_PEERINFOS_FIELD_PUBKEY, $TABLE_PEERINFOS_FIELD_TIMESTAMP FROM $TABLE_PEERINFOS) x  
                    WHERE row_id BETWEEN ? AND ?""", mapListHandler, original, offset+1, offset+limit)

        return rows.map { row ->
            val rowId = row["row_id"] as Long
            val host = row["$TABLE_PEERINFOS_FIELD_HOST"] as String
            val port = row["$TABLE_PEERINFOS_FIELD_PORT"] as Int
            val pubkey = row["$TABLE_PEERINFOS_FIELD_PUBKEY"] as String
            val timestamp = row["$TABLE_PEERINFOS_FIELD_TIMESTAMP"] as Long

            val peer = PeerInfo(host, port, pubkey, timestamp)
            RowData(GtvInteger(rowId), GtvString(TABLE_PEERINFOS), peer.toGtv(), peer.toHashGtv())
        }
    }

    override fun getTables(ctx: EContext): List<String> {
        return queryRunner.query(ctx.conn,
                """SELECT table_name FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name DESC""",
                ColumnListHandler(), ctx.conn.schema)
    }

    override fun getTxsInRange(ctx: EContext, limit: Int, offset: Long, original: Long): List<RowData> {
        var rows = queryRunner.query(ctx.conn,
                """SELECT * FROM (
                    SELECT (row_number() OVER (ORDER BY chain_iid) + ?) AS row_id, tx_iid, chain_iid, tx_rid, tx_data, tx_hash, block_iid FROM transactions) x 
                    WHERE row_id BETWEEN ? AND ? AND chain_iid= ?""", mapListHandler, original, offset+1, offset+limit, ctx.chainID)

        return rows.map { row ->
            val rowId = row["row_id"] as Long
            val txIid = row["tx_iid"] as Long
            val chainIid = row["chain_iid"] as Long
            val txRid = row["tx_rid"] as ByteArray
            val txData = row["tx_data"] as ByteArray
            val txHash = row["tx_hash"] as ByteArray
            val blockIid = row["block_iid"] as Long

            val tx = TxData(txIid, chainIid, txRid, txData, txHash, blockIid)
            RowData(GtvInteger(rowId), GtvString("transactions"), tx.toGtv(), tx.toHashGtv())
        }
    }

    override fun getBlocksInRange(ctx: EContext, limit: Int, offset: Long, original: Long): List<RowData> {
        var rows = queryRunner.query(ctx.conn,
                """SELECT * FROM (
                    SELECT (row_number() OVER (ORDER BY chain_iid) + ?) AS row_id, chain_iid, block_iid, block_rid, block_height, block_header_data, block_witness, timestamp FROM blocks) x 
                    WHERE row_id BETWEEN ? AND ? AND chain_iid = ?""", mapListHandler, original, offset+1, offset+limit, ctx.chainID)

        return rows.map { row ->
            val rowId = row["row_id"] as Long
            val blockIid = row["block_iid"] as Long
            val blockRid = row["block_rid"] as ByteArray
            val chainIid = row["chain_iid"] as Long
            val blockHeight = row["block_height"] as Long
            val blockHeader = row["block_header_data"] as ByteArray
            val blockWitness = row["block_witness"] as ByteArray
            val timestamp = row["timestamp"] as Long

            val block = BlockData(blockIid, blockRid, chainIid, blockHeight, blockHeader, blockWitness, timestamp)
            RowData(GtvInteger(rowId), GtvString("blocks"), block.toGtv(), block.toHashGtv())
        }
    }

    override fun getDataInRange(ctx: EContext, tableName: String, limit: Int, offset: Long, original: Long): List<RowData> {
        val ddl = getTableDDL(ctx, tableName)
        val cols = getTableDefinition(ctx, tableName)
        val defs = cols.map { it.toGtv() }.toTypedArray()
        var query = "SELECT * FROM (SELECT (row_number() OVER (ORDER BY 1) + ?) AS row_id"
        cols.forEach { query += ", ${it.columnName}" }

        query += " FROM $tableName) x WHERE row_id BETWEEN ? AND ?"

        val rows = queryRunner.query(ctx.conn, query, mapListHandler, original, offset+1, offset+limit)
        return rows.map { row ->
            val rowId = row["row_id"] as Long
            val data = mutableMapOf<String, Gtv>()
            data["definition"] = GtvArray(defs)
            data["ddl"] = GtvString(ddl)
            cols.forEach {
                data[it.columnName] = when (it.dataType) {
                    "bigint" -> {
                        GtvInteger(row[it.columnName] as Long)
                    }
                    "integer" -> {
                        GtvInteger((row[it.columnName] as Int).toLong())
                    }
                    "bytea" -> {
                        GtvByteArray(row[it.columnName] as ByteArray)
                    }
                    "text" -> {
                        GtvString(row[it.columnName] as String)
                    }
                    else -> {
                        GtvNull
                    }
                }
            }
            RowData(GtvInteger(rowId), GtvString(tableName), GtvFactory.gtv(GtvDictionary.build(data)), GtvFactory.gtv(GtvDictionary.build(data)))
        }
    }

    override fun getRowCount(ctx: EContext, tableName: String): Long {
        return queryRunner.query(ctx.conn, "SELECT count(*) FROM $tableName", longRes)
    }

    override fun getTableDDL(ctx: EContext, tableName: String): String {
        return queryRunner.query(ctx.conn, "SELECT * FROM public.describe_table('${ctx.conn.schema}', '$tableName')", stringRes)
    }

    override fun getConfigurationData(ctx: EContext, height: Long): ByteArray? {
        return queryRunner.query(ctx.conn,
                "SELECT configuration_data FROM configurations WHERE chain_iid= ? AND height = ?",
                nullableByteArrayRes, ctx.chainID, height)
    }

    override fun addConfigurationData(ctx: EContext, height: Long, data: ByteArray) {
        queryRunner.update(ctx.conn, sqlCommands.insertConfiguration, ctx.chainID, height, data)
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

    private fun getTableDefinition(context: EContext, tableName: String): List<TableDefinition> {
        var rows = queryRunner.query(context.conn,
                """SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns WHERE table_schema = ? AND table_name = ?""",
                mapListHandler, context.conn.schema, tableName)

        return rows.map { row ->
            val name = row["column_name"] as String
            val type = row["data_type"] as String
            val nullable = row["is_nullable"] as String == "YES"
            val default = row["column_default"] as String?
            TableDefinition(name, type, nullable, default)
        }
    }

}

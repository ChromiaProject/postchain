package net.postchain.base.data

import mu.KLogging
import net.postchain.base.BaseBlockHeader
import net.postchain.base.PeerInfo
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.snapshot.Page
import net.postchain.common.BlockchainRid
import net.postchain.common.data.HASH_LENGTH
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.AppContext
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.core.NodeRid
import net.postchain.core.Transaction
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TxDetail
import net.postchain.core.TxEContext
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockWitness
import net.postchain.crypto.PubKey
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.GtvDecoder
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ColumnListHandler
import org.apache.commons.dbutils.handlers.MapListHandler
import org.apache.commons.dbutils.handlers.ScalarHandler
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant


abstract class SQLDatabaseAccess : DatabaseAccess {

    protected fun tableMeta(): String = "meta"
    protected fun tableContainers(): String = "containers"
    internal fun tableBlockchains(): String = "blockchains"
    protected fun tablePeerinfos(): String = "peerinfos"
    protected fun tableBlockchainReplicas(): String = "blockchain_replicas"
    protected fun tableMustSyncUntil(): String = "must_sync_until"
    internal fun tableConfigurations(ctx: EContext): String = tableName(ctx, "configurations")
    protected fun tableConfigurations(chainId: Long): String = tableName(chainId, "configurations")
    protected fun tableTransactions(ctx: EContext): String = tableName(ctx, "transactions")
    protected fun tableBlocks(ctx: EContext): String = tableName(ctx, "blocks")
    private fun tableBlocks(chainId: Long): String = tableName(chainId, "blocks")
    protected fun tablePages(ctx: EContext, name: String): String = tableName(ctx, "${name}_pages")
    protected fun tableEventLeafs(ctx: EContext, prefix: String): String = tableName(ctx, "${prefix}_event_leafs")
    protected fun tableStateLeafs(ctx: EContext, prefix: String): String = tableName(ctx, "${prefix}_state_leafs")
    protected fun indexTableStateLeafs(ctx: EContext, prefix: String, index: Int): String = tableName(ctx, "idx${index}_${prefix}_state_leafs")

    fun tableGtxModuleVersion(ctx: EContext): String = tableName(ctx, "gtx_module_version")

    override fun tableName(ctx: EContext, table: String): String = tableName(ctx.chainID, table)

    private fun tableName(chainId: Long, table: String): String = "\"c${chainId}.$table\""

    // --- Create Table ---
    protected abstract fun cmdCreateTableMeta(): String
    protected abstract fun cmdCreateTableContainers(): String
    protected abstract fun cmdCreateTableBlockchains(): String
    protected abstract fun cmdCreateTablePeerInfos(): String
    protected abstract fun cmdCreateTableBlockchainReplicas(): String
    protected abstract fun cmdCreateTableMustSyncUntil(): String
    protected abstract fun cmdCreateTableConfigurations(ctx: EContext): String
    protected abstract fun cmdCreateTableTransactions(ctx: EContext): String
    protected abstract fun cmdCreateTableBlocks(ctx: EContext): String
    protected abstract fun cmdInsertBlocks(ctx: EContext): String
    protected abstract fun cmdCreateTablePage(ctx: EContext, name: String): String

    protected abstract fun cmdUpdateTableConfigurationsV4First(chainId: Long): String
    protected abstract fun cmdUpdateTableConfigurationsV4Second(chainId: Long): String

    // Tables not part of the batch creation run
    protected abstract fun cmdCreateTableEvent(ctx: EContext, prefix: String): String
    protected abstract fun cmdCreateTableState(ctx: EContext, prefix: String): String
    protected abstract fun cmdCreateIndexTableState(ctx: EContext, prefix: String, index: Int): String

    // --- Insert ---
    protected abstract fun cmdInsertTransactions(ctx: EContext): String
    protected abstract fun cmdInsertPage(ctx: EContext, name: String): String
    protected abstract fun cmdInsertConfiguration(ctx: EContext): String
    protected abstract fun cmdInsertEvent(ctx: EContext, prefix: String): String
    protected abstract fun cmdInsertState(ctx: EContext, prefix: String): String
    protected abstract fun cmdPruneEvents(ctx: EContext, prefix: String): String
    protected abstract fun cmdPruneStates(ctx: EContext, prefix: String): String
    abstract fun cmdCreateTableGtxModuleVersion(ctx: EContext): String

    var queryRunner = QueryRunner()
    private val intRes = ScalarHandler<Int>()
    val longRes = ScalarHandler<Long>()
    private val nullableByteArrayRes = ScalarHandler<ByteArray?>()
    private val nullableIntRes = ScalarHandler<Int?>()
    private val nullableLongRes = ScalarHandler<Long?>()
    internal val byteArrayRes = ScalarHandler<ByteArray>()
    internal val mapListHandler = MapListHandler()

    companion object : KLogging() {
        const val TABLE_PEERINFOS_FIELD_HOST = "host"
        const val TABLE_PEERINFOS_FIELD_PORT = "port"
        const val TABLE_PEERINFOS_FIELD_PUBKEY = "pub_key"
        const val TABLE_PEERINFOS_FIELD_TIMESTAMP = "timestamp"

        const val TABLE_REPLICAS_FIELD_BRID = "blockchain_rid"
        const val TABLE_REPLICAS_FIELD_PUBKEY = "node"

        const val TABLE_SYNC_UNTIL_FIELD_CHAIN_IID = "chain_iid"
        const val TABLE_SYNC_UNTIL_FIELD_HEIGHT = "block_height"
    }

    override fun isSchemaExists(connection: Connection, schema: String): Boolean {
        val schemas = connection.metaData.schemas

        while (schemas.next()) {
            if (schemas.getString(1).equals(schema, true)) {
                return true
            }
        }

        return false
    }

    override fun insertBlock(ctx: EContext, height: Long): Long {
        queryRunner.update(ctx.conn, cmdInsertBlocks(ctx), height)

        val sql = "SELECT block_iid FROM ${tableBlocks(ctx)} WHERE block_height = ?"
        return queryRunner.query(ctx.conn, sql, longRes, height)
    }

    override fun insertTransaction(ctx: BlockEContext, tx: Transaction): Long {
        queryRunner.update(ctx.conn, cmdInsertTransactions(ctx), tx.getRID(), tx.getRawData(), tx.getHash(), ctx.blockIID)

        val sql = "SELECT tx_iid FROM ${tableTransactions(ctx)} WHERE tx_rid = ?"
        return queryRunner.query(ctx.conn, sql, longRes, tx.getRID())
    }

    override fun finalizeBlock(ctx: BlockEContext, header: BlockHeader) {
        val sql = "UPDATE ${tableBlocks(ctx)} SET block_rid = ?, block_header_data = ?, timestamp = ? WHERE block_iid = ?"
        queryRunner.update(
                ctx.conn, sql, header.blockRID, header.rawData, (header as BaseBlockHeader).timestamp, ctx.blockIID
        )
    }

    override fun commitBlock(ctx: BlockEContext, w: BlockWitness) {
        val sql = "UPDATE ${tableBlocks(ctx)} SET block_witness = ? WHERE block_iid = ?"
        queryRunner.update(ctx.conn, sql, w.getRawData(), ctx.blockIID)
    }

    override fun getBlockHeight(ctx: EContext, blockRID: ByteArray, chainId: Long): Long? {
        val sql = "SELECT block_height FROM ${tableBlocks(chainId)} WHERE block_rid = ?"
        return queryRunner.query(ctx.conn, sql, nullableLongRes, blockRID)
    }

    // The combination of CHAIN_ID and BLOCK_HEIGHT is unique
    override fun getBlockRID(ctx: EContext, height: Long): ByteArray? {
        val sql = "SELECT block_rid FROM ${tableBlocks(ctx)} WHERE block_height = ?"
        return queryRunner.query(ctx.conn, sql, nullableByteArrayRes, height)
    }

    override fun getBlockHeader(ctx: EContext, blockRID: ByteArray): ByteArray {
        val sql = "SELECT block_header_data FROM ${tableBlocks(ctx)} WHERE block_rid = ?"
        return queryRunner.query(ctx.conn, sql, byteArrayRes, blockRID)
    }

    override fun getBlockTransactions(ctx: EContext, blockRID: ByteArray, hashesOnly: Boolean): List<TxDetail> {
        val sql = """
            SELECT tx_rid, tx_hash${if (hashesOnly) "" else ", tx_data"}
            FROM ${tableTransactions(ctx)} t
            JOIN ${tableBlocks(ctx)} b ON t.block_iid=b.block_iid
            WHERE b.block_rid=? ORDER BY tx_iid
        """.trimIndent()

        val txs = queryRunner.query(ctx.conn, sql, mapListHandler, blockRID)

        return txs.map { tx ->
            TxDetail(
                    tx["tx_rid"] as ByteArray,
                    tx["tx_hash"] as ByteArray,
                    if (hashesOnly) null else (tx["tx_data"] as ByteArray)
            )
        }
    }

    override fun getWitnessData(ctx: EContext, blockRID: ByteArray): ByteArray {
        val sql = "SELECT block_witness FROM ${tableBlocks(ctx)} WHERE block_rid = ?"
        return queryRunner.query(ctx.conn, sql, byteArrayRes, blockRID)
    }

    override fun getLastBlockHeight(ctx: EContext): Long {
        val sql = "SELECT block_height FROM ${tableBlocks(ctx)} ORDER BY block_height DESC LIMIT 1"
        return queryRunner.query(ctx.conn, sql, longRes) ?: -1L
    }

    override fun getLastBlockTimestamp(ctx: EContext): Long {
        val sql = "SELECT timestamp FROM ${tableBlocks(ctx)} ORDER BY block_iid DESC LIMIT 1"
        return queryRunner.query(ctx.conn, sql, longRes) ?: -1L
    }

    override fun getLastBlockRid(ctx: EContext, chainId: Long): ByteArray? {
        val sql = "SELECT block_rid FROM ${tableBlocks(chainId)} ORDER BY block_height DESC LIMIT 1"
        return queryRunner.query(ctx.conn, sql, nullableByteArrayRes)
    }

    override fun getBlockHeightInfo(ctx: EContext, bcRid: BlockchainRid): Pair<Long, ByteArray>? {
        val chainId = getChainId(ctx, bcRid) ?: return null

        val sql = "SELECT block_height, block_rid FROM ${tableBlocks(chainId)} ORDER BY block_height DESC LIMIT 1"
        val res = queryRunner.query(ctx.conn, sql, mapListHandler)

        return when (res.size) {
            0 -> null // This is allowed, it (usually) means we don't have any blocks yet
            1 -> {
                val height = res.first()["block_height"] as Long
                val blockRid = res.first()["block_rid"] as ByteArray
                Pair(height, blockRid)
            }
            else -> {
                throw ProgrammerMistake("Incorrect query getBlockHeightInfo got many lines (${res.size})")
            }
        }
    }

    override fun getTxRIDsAtHeight(ctx: EContext, height: Long): Array<ByteArray> {
        val sql = "SELECT tx_rid" +
                " FROM ${tableTransactions(ctx)} t" +
                " INNER JOIN ${tableBlocks(ctx)} b ON t.block_iid=b.block_iid" +
                " WHERE b.block_height = ?" +
                " ORDER BY t.tx_iid"
        return queryRunner.query(ctx.conn, sql, ColumnListHandler<ByteArray>(), height).toTypedArray()
    }

    override fun getBlockInfo(ctx: EContext, txRID: ByteArray): DatabaseAccess.BlockInfo? {
        val sql = """
            SELECT b.block_iid, b.block_header_data, b.block_witness
                    FROM ${tableBlocks(ctx)} b
                    JOIN ${tableTransactions(ctx)} t ON b.block_iid=t.block_iid
                    WHERE t.tx_rid = ?
        """.trimIndent()
        val block = queryRunner.query(ctx.conn, sql, mapListHandler, txRID)!!
        if (block.size < 1) return null
        if (block.size > 1) throw ProgrammerMistake("Expected at most one hit")

        val blockIid = block.first()["block_iid"] as Long
        val blockHeader = block.first()["block_header_data"] as ByteArray
        val witness = block.first()["block_witness"] as ByteArray
        return DatabaseAccess.BlockInfo(blockIid, blockHeader, witness)
    }

    override fun getTransactionInfo(ctx: EContext, txRID: ByteArray): TransactionInfoExt? {
        val sql = """
            SELECT b.block_rid, b.block_height, b.block_header_data, b.block_witness, b.timestamp, t.tx_rid, t.tx_hash, t.tx_data 
                    FROM ${tableBlocks(ctx)} as b 
                    JOIN ${tableTransactions(ctx)} as t ON (t.block_iid = b.block_iid) 
                    WHERE t.tx_rid = ?
                    ORDER BY b.block_height DESC LIMIT 1;
        """.trimIndent()

        val txInfos = queryRunner.query(ctx.conn, sql, mapListHandler, txRID)
        if (txInfos.isEmpty()) return null
        val txInfo = txInfos.first()

        val blockRID = txInfo["block_rid"] as ByteArray
        val blockHeight = txInfo["block_height"] as Long
        val blockHeader = txInfo["block_header_data"] as ByteArray
        val blockWitness = txInfo["block_witness"] as ByteArray
        val blockTimestamp = txInfo["timestamp"] as Long
        val resultTxRID = txInfo["tx_rid"] as ByteArray
        val txHash = txInfo["tx_hash"] as ByteArray
        val txData = txInfo["tx_data"] as ByteArray
        return TransactionInfoExt(
                blockRID, blockHeight, blockHeader, blockWitness, blockTimestamp, resultTxRID, txHash, txData)
    }

    override fun getTransactionsInfo(ctx: EContext, beforeTime: Long, limit: Int): List<TransactionInfoExt> {
        val sql = """
            SELECT b.block_rid, b.block_height, b.block_header_data, b.block_witness, b.timestamp, t.tx_rid, t.tx_hash, t.tx_data 
                    FROM ${tableBlocks(ctx)} as b 
                    JOIN ${tableTransactions(ctx)} as t ON (t.block_iid = b.block_iid) 
                    WHERE b.timestamp < ? 
                    ORDER BY b.block_height DESC, t.tx_iid DESC LIMIT ?;
        """.trimIndent()
        val transactions = queryRunner.query(ctx.conn, sql, mapListHandler, beforeTime, limit)
        return transactions.map { txInfo ->
            val blockRID = txInfo["block_rid"] as ByteArray
            val blockHeight = txInfo["block_height"] as Long
            val blockHeader = txInfo["block_header_data"] as ByteArray
            val blockWitness = txInfo["block_witness"] as ByteArray
            val blockTimestamp = txInfo["timestamp"] as Long
            val txRID = txInfo["tx_rid"] as ByteArray
            val txHash = txInfo["tx_hash"] as ByteArray
            val txData = txInfo["tx_data"] as ByteArray
            TransactionInfoExt(
                    blockRID, blockHeight, blockHeader, blockWitness, blockTimestamp, txRID, txHash, txData)
        }
    }

    override fun getTxHash(ctx: EContext, txRID: ByteArray): ByteArray {
        val sql = "SELECT tx_hash FROM ${tableTransactions(ctx)} WHERE tx_rid = ?"
        return queryRunner.query(ctx.conn, sql, byteArrayRes, txRID)
    }

    override fun getBlockTxRIDs(ctx: EContext, blockIid: Long): List<ByteArray> {
        val sql = "SELECT tx_rid FROM ${tableTransactions(ctx)} t WHERE t.block_iid = ? ORDER BY tx_iid"
        return queryRunner.query(ctx.conn, sql, ColumnListHandler(), blockIid)!!
    }

    override fun getBlockTxHashes(ctx: EContext, blockIid: Long): List<ByteArray> {
        val sql = "SELECT tx_hash FROM ${tableTransactions(ctx)} t WHERE t.block_iid = ? ORDER BY tx_iid"
        return queryRunner.query(ctx.conn, sql, ColumnListHandler(), blockIid)!!
    }

    override fun getTxBytes(ctx: EContext, txRID: ByteArray): ByteArray? {
        val sql = "SELECT tx_data FROM ${tableTransactions(ctx)} WHERE tx_rid=?"
        return queryRunner.query(ctx.conn, sql, nullableByteArrayRes, txRID)
    }

    override fun isTransactionConfirmed(ctx: EContext, txRID: ByteArray): Boolean {
        val sql = "SELECT 1 FROM ${tableTransactions(ctx)} t WHERE t.tx_rid = ?"
        val res = queryRunner.query(ctx.conn, sql, nullableIntRes, txRID)
        return (res != null)
    }

    override fun getBlockchainRid(ctx: EContext): BlockchainRid? {
        val sql = "SELECT blockchain_rid FROM ${tableBlockchains()} WHERE chain_iid = ?"
        val data = queryRunner.query(ctx.conn, sql, nullableByteArrayRes, ctx.chainID)
        return data?.let(::BlockchainRid)
    }

    // ---- Event and State ----

    override fun getEvent(ctx: EContext, prefix: String, eventHash: ByteArray): DatabaseAccess.EventInfo? {
        val sql = """SELECT block_height, position, hash, data 
            FROM ${tableEventLeafs(ctx, prefix)} 
            WHERE hash = ?"""
        val rows = queryRunner.query(ctx.conn, sql, mapListHandler, eventHash)
        if (rows.isEmpty()) return null
        val data = rows.first()
        return DatabaseAccess.EventInfo(
                data["position"] as Long,
                data["block_height"] as Long,
                data["hash"] as Hash,
                data["data"] as ByteArray
        )
    }

    /**
     * Fetch ALL events from the given height
     */
    override fun getEventsOfHeight(ctx: EContext, prefix: String, blockHeight: Long): List<DatabaseAccess.EventInfo> {
        val sql = """SELECT block_height, hash, data, event_iid
            FROM ${tableEventLeafs(ctx, prefix)} 
            WHERE block_height = ?
            ORDER BY event_iid """

        return getEventList(ctx, blockHeight, sql)
    }

    /**
     * Fetch ALL events above the given height
     */
    override fun getEventsAboveHeight(ctx: EContext, prefix: String, blockHeight: Long): List<DatabaseAccess.EventInfo> {
        val sql = """SELECT block_height, hash, data, event_iid
            FROM ${tableEventLeafs(ctx, prefix)} 
            WHERE block_height > ?
            ORDER BY event_iid
            LIMIT ? """

        return getEventList(ctx, blockHeight, sql)
    }

    /**
     * NOTE: We don't bother to set "pos" so it starts from 0, we just use the event_iid raw.
     *       In this case the important thing is the SORTING of the events, not the exact pos number.
     */
    private fun getEventList(ctx: EContext, blockHeight: Long, sql: String, maxEventsLimit: Int = 1000): List<DatabaseAccess.EventInfo> {
        val rows = queryRunner.query(ctx.conn, sql, mapListHandler, blockHeight, maxEventsLimit)
        return if (rows.isEmpty()) {
            ArrayList()
        } else {
            rows.map { data ->
                DatabaseAccess.EventInfo(
                        data["event_iid"] as Long,
                        data["block_height"] as Long,
                        data["hash"] as Hash,
                        data["data"] as ByteArray
                )
            }
        }
    }

    override fun getAccountState(ctx: EContext, prefix: String, height: Long, state_n: Long): DatabaseAccess.AccountState? {
        val sql = """SELECT block_height, state_n, data FROM ${tableStateLeafs(ctx, prefix)} 
            WHERE block_height <= ? AND state_n = ? 
            ORDER BY state_iid DESC LIMIT 1"""
        val rows = queryRunner.query(ctx.conn, sql, mapListHandler, height, state_n)
        if (rows.isEmpty()) return null
        val data = rows.first()
        return DatabaseAccess.AccountState(
                data["block_height"] as Long,
                data["state_n"] as Long,
                data["data"] as ByteArray
        )
    }

    override fun insertEvent(ctx: TxEContext, prefix: String, height: Long, position: Long, hash: Hash, data: ByteArray) {
        queryRunner.update(ctx.conn, cmdInsertEvent(ctx, prefix), height, position, hash, ctx.txIID, data)
    }

    override fun insertState(ctx: EContext, prefix: String, height: Long, state_n: Long, data: ByteArray) {
        queryRunner.update(ctx.conn, cmdInsertState(ctx, prefix), height, state_n, data)
    }

    override fun pruneEvents(ctx: EContext, prefix: String, heightMustBeHigherThan: Long) {
        queryRunner.update(ctx.conn, cmdPruneEvents(ctx, prefix), heightMustBeHigherThan)
    }

    override fun pruneAccountStates(ctx: EContext, prefix: String, left: Long, right: Long, heightMustBeHigherThan: Long) {
        if (left > right) {
            throw ProgrammerMistake("Why is left value lower than right? $left < $right")
        }
        queryRunner.update(ctx.conn, cmdPruneStates(ctx, prefix), left, right, heightMustBeHigherThan)
    }

    override fun insertPage(ctx: EContext, name: String, page: Page) {
        val childHashes = page.childHashes.fold(ByteArray(0)) { total, item -> total.plus(item) }
        queryRunner.update(ctx.conn, cmdInsertPage(ctx, name), page.blockHeight, page.level, page.left, childHashes)
    }

    /**
     * If we didn't prune the old one then we need to query the snapshot page
     * at highest block height that less than or equal to specific height
     */
    override fun getPage(ctx: EContext, name: String, height: Long, level: Int, left: Long): Page? {
        val sql = """
            SELECT child_hashes FROM ${tablePages(ctx, name)} 
            WHERE block_height = (SELECT MAX(block_height) FROM ${tablePages(ctx, name)} 
                                    WHERE block_height <= ? AND level = ? AND left_index = ?)
            AND level = ? AND left_index = ?"""
        val data = queryRunner.query(ctx.conn, sql, nullableByteArrayRes, height, level, left, level, left)
        // if data size is not contain correct length then it regards to error
        if (data == null || data.size % HASH_LENGTH != 0) return null
        val length = data.size / HASH_LENGTH
        val childHashes = Array(length) { ByteArray(HASH_LENGTH) }
        for (i in 0 until length) {
            val start = i * HASH_LENGTH
            val end = start + HASH_LENGTH - 1
            childHashes[i] = data.sliceArray(start..end)
        }
        return Page(height, level, left, childHashes)
    }

    override fun createPageTable(ctx: EContext, prefix: String) {
        queryRunner.update(ctx.conn, cmdCreateTablePage(ctx, prefix))
    }

    override fun createEventLeafTable(ctx: EContext, prefix: String) {
        queryRunner.update(ctx.conn, cmdCreateTableEvent(ctx, prefix))
    }


    // --- Init App ----
    override fun createStateLeafTable(ctx: EContext, prefix: String) {
        queryRunner.update(ctx.conn, cmdCreateTableState(ctx, prefix))
    }

    override fun createStateLeafTableIndex(ctx: EContext, prefix: String, index: Int) {
        queryRunner.update(ctx.conn, cmdCreateIndexTableState(ctx, prefix, index))
    }

    override fun getHighestLevelPage(ctx: EContext, name: String, height: Long): Int {
        val sql = "SELECT COALESCE(MAX(level), 0) FROM ${tablePages(ctx, name)} WHERE block_height <= ?"
        return queryRunner.query(ctx.conn, sql, intRes, height)
    }

    override fun initializeApp(connection: Connection, expectedDbVersion: Int) {
        if (expectedDbVersion !in 1..4) {
            throw UserMistake("Unsupported DB version $expectedDbVersion")
        }

        /**
         * "CREATE TABLE IF NOT EXISTS" is not good enough for the meta table
         * We need to know whether it exists or not in order to
         * make decisions on upgrade
         */
        if (tableExists(connection, tableMeta())) {
            // meta table already exists. Check the version
            val sql = "SELECT value FROM ${tableMeta()} WHERE key='version'"
            val version = queryRunner.query(connection, sql, ScalarHandler<String>()).toInt()

            if (expectedDbVersion < version) {
                throw UserMistake("Will not downgrade database from $version to $expectedDbVersion")
            }

            if (version < 2 && expectedDbVersion >= 2) {
                logger.info("Upgrading to version 2")
                version2(connection)
            }

            if (version < 3 && expectedDbVersion >= 3) {
                logger.info("Upgrading to version 3")
                version3(connection)
            }

            if (version < 4 && expectedDbVersion >= 4) {
                logger.info("Upgrading to version 4")
                version4(connection)
            }

            if (expectedDbVersion > version) {
                queryRunner.update(connection, "UPDATE ${tableMeta()} set value = ? WHERE key = 'version'", expectedDbVersion)
                logger.info("Database version has been updated to version: $expectedDbVersion")
            }
        } else {
            logger.debug("Meta table does not exist. Assume database does not exist and create it (version: $expectedDbVersion).")
            queryRunner.update(connection, cmdCreateTableMeta())
            val sql = "INSERT INTO ${tableMeta()} (key, value) values ('version', ?)"
            queryRunner.update(connection, sql, expectedDbVersion)

            /**
             * NB: Don't use "CREATE TABLE IF NOT EXISTS" because if they do exist
             * we must throw an error. If these tables exist but meta did not exist,
             * there is some serious problem that needs manual work
             */

            version1(connection)

            if (expectedDbVersion >= 2) {
                version2(connection)
            }

            if (expectedDbVersion >= 3) {
                version3(connection)
            }

            if (expectedDbVersion >= 4) {
                version4(connection)
            }
        }
    }

    private fun version1(connection: Connection) {
        queryRunner.update(connection, cmdCreateTablePeerInfos())
        queryRunner.update(connection, cmdCreateTableBlockchains())
    }

    private fun version2(connection: Connection) {
        queryRunner.update(connection, cmdCreateTableBlockchainReplicas())
        queryRunner.update(connection, cmdCreateTableMustSyncUntil())
    }

    private fun version3(connection: Connection) {
        queryRunner.update(connection, cmdCreateTableContainers())
    }

    private fun version4(connection: Connection) {
        queryRunner.query(connection, "SELECT chain_iid FROM ${tableBlockchains()}", mapListHandler)
                .map { it["chain_iid"] as Long }
                .forEach { chainId ->
                    queryRunner.update(connection, cmdUpdateTableConfigurationsV4First(chainId))
                    queryRunner.query(connection, "SELECT height, configuration_data FROM ${tableConfigurations(chainId)}", mapListHandler)
                            .forEach {
                                val height = it["height"] as Long
                                val configurationData = it["configuration_data"] as ByteArray
                                queryRunner.update(connection,
                                        "UPDATE ${tableConfigurations(chainId)} SET configuration_hash=? WHERE height=?",
                                        calcConfigurationHash(configurationData), height)
                            }
                    queryRunner.update(connection, cmdUpdateTableConfigurationsV4Second(chainId))
                }
    }

    protected fun calcConfigurationHash(configurationData: ByteArray) = GtvToBlockchainRidFactory.calculateBlockchainRid(
            GtvDecoder.decodeGtv(configurationData), ::sha256Digest).data

    override fun createContainer(ctx: AppContext, name: String): Int {
        val sql = "INSERT INTO ${tableContainers()} (name) values (?) RETURNING container_iid"
        return queryRunner.insert(ctx.conn, sql, intRes, name)
    }

    override fun getContainerIid(ctx: AppContext, name: String): Int? {
        val sql = "SELECT container_iid FROM ${tableContainers()} WHERE name = ?"
        return queryRunner.query(ctx.conn, sql, nullableIntRes, name)
    }

    override fun initializeBlockchain(ctx: EContext, blockchainRid: BlockchainRid) {
        val initialized = getBlockchainRid(ctx) != null

        queryRunner.update(ctx.conn, cmdCreateTableBlocks(ctx))
        queryRunner.update(ctx.conn, cmdCreateTableTransactions(ctx))
        queryRunner.update(ctx.conn, cmdCreateTableConfigurations(ctx))

        val txIndex = "CREATE INDEX IF NOT EXISTS ${tableName(ctx, "transactions_block_iid_idx")} " +
                "ON ${tableTransactions(ctx)}(block_iid)"
        queryRunner.update(ctx.conn, txIndex)

        val blockIndex = "CREATE INDEX IF NOT EXISTS ${tableName(ctx, "blocks_timestamp_idx")} " +
                "ON ${tableBlocks(ctx)}(timestamp)"
        queryRunner.update(ctx.conn, blockIndex)

        if (!initialized) {
            // Inserting chainId -> blockchainRid
            val sql = "INSERT INTO ${tableBlockchains()} (chain_iid, blockchain_rid) values (?, ?)"
            queryRunner.update(ctx.conn, sql, ctx.chainID, blockchainRid.data)
        }
    }

    override fun getChainId(ctx: EContext, blockchainRid: BlockchainRid): Long? {
        val sql = "SELECT chain_iid FROM ${tableBlockchains()} WHERE blockchain_rid = ?"
        return queryRunner.query(ctx.conn, sql, nullableLongRes, blockchainRid.data)
    }

    override fun getMaxChainId(ctx: EContext): Long? {
        val sql = "SELECT MAX(chain_iid) FROM ${tableBlockchains()}"
        return queryRunner.query(ctx.conn, sql, nullableLongRes)
    }

    override fun getMaxSystemChainId(ctx: EContext): Long? {
        val sql = "SELECT MAX(chain_iid) FROM ${tableBlockchains()} WHERE chain_iid < 100"
        return queryRunner.query(ctx.conn, sql, nullableLongRes)
    }

    override fun getBlock(ctx: EContext, blockRID: ByteArray): DatabaseAccess.BlockInfoExt? {
        val sql = """
            SELECT block_rid, block_height, block_header_data, block_witness, timestamp 
            FROM ${tableBlocks(ctx)} 
            WHERE block_rid = ? 
            LIMIT 1
        """.trimIndent()

        val blockInfos = queryRunner.query(ctx.conn, sql, mapListHandler, blockRID)
        if (blockInfos.isEmpty()) return null
        val blockInfo = blockInfos.first()
        return buildBlockInfoExt(blockInfo)
    }

    override fun getBlocks(ctx: EContext, blockTime: Long, limit: Int): List<DatabaseAccess.BlockInfoExt> {
        val sql = """
            SELECT block_rid, block_height, block_header_data, block_witness, timestamp 
            FROM ${tableBlocks(ctx)} 
            WHERE timestamp < ? 
            ORDER BY timestamp DESC LIMIT ?
        """.trimIndent()
        val blocksInfo = queryRunner.query(ctx.conn, sql, mapListHandler, blockTime, limit)
        return blocksInfo.map { buildBlockInfoExt(it) }
    }

    override fun getBlocksBeforeHeight(ctx: EContext, blockHeight: Long, limit: Int): List<DatabaseAccess.BlockInfoExt> {
        val sql = """
            SELECT block_rid, block_height, block_header_data, block_witness, timestamp 
            FROM ${tableBlocks(ctx)} 
            WHERE block_height < ? 
            ORDER BY block_height DESC LIMIT ?
        """.trimIndent()
        val blocksInfo = queryRunner.query(ctx.conn, sql, mapListHandler, blockHeight, limit)
        return blocksInfo.map { buildBlockInfoExt(it) }
    }

    private fun buildBlockInfoExt(blockInfo: MutableMap<String, Any>): DatabaseAccess.BlockInfoExt {
        val blockRid = blockInfo["block_rid"] as ByteArray
        val blockHeight = blockInfo["block_height"] as Long
        val blockHeader = blockInfo["block_header_data"] as ByteArray
        val blockWitness = blockInfo["block_witness"] as ByteArray
        val timestamp = blockInfo["timestamp"] as Long
        return DatabaseAccess.BlockInfoExt(blockRid, blockHeight, blockHeader, blockWitness, timestamp)
    }

    /**
     * The rule is: a blockchain will continue use a configuration until
     * we say that a new configuration should be used (at a certain height).
     * This query let us go from "block height" to what configuration is used at this height.
     *
     * @param ctx
     * @param height is the height of a block
     * @return the height of the CONFIGURATION used for a block of the given height.
     *         returning "null" here means no configuration is defined for the chain,
     *         which is most likely an error.
     */
    override fun findConfigurationHeightForBlock(ctx: EContext, height: Long): Long? {
        val sql = """
            SELECT height 
            FROM ${tableConfigurations(ctx)} 
            WHERE height <= ? 
            ORDER BY height DESC LIMIT 1
        """.trimIndent()
        return queryRunner.query(ctx.conn, sql, nullableLongRes, height)
    }

    override fun findNextConfigurationHeight(ctx: EContext, height: Long): Long? {
        val sql = """
            SELECT height 
            FROM ${tableConfigurations(ctx)} 
            WHERE height > ? 
            ORDER BY height LIMIT 1
        """.trimIndent()
        return queryRunner.query(ctx.conn, sql, nullableLongRes, height)
    }

    override fun listConfigurations(ctx: EContext): List<Long> {
        val sql = """
            SELECT height 
            FROM ${tableConfigurations(ctx)} 
            ORDER BY height
        """.trimIndent()
        return queryRunner.query(ctx.conn, sql, mapListHandler).map { configuration ->
            configuration["height"] as Long
        }
    }

    override fun removeConfiguration(ctx: EContext, height: Long): Int {
        val lastBlockHeight = getLastBlockHeight(ctx)
        if (lastBlockHeight >= height) {
            throw UserMistake("Cannot remove configuration at $height, since last block is already at $lastBlockHeight")
        }
        return queryRunner.update(ctx.conn, "DELETE FROM ${tableConfigurations(ctx)} WHERE height = ?", height)
    }

    override fun getConfigurationData(ctx: EContext, height: Long): ByteArray? {
        val sql = "SELECT configuration_data FROM ${tableConfigurations(ctx)} WHERE height = ?"
        return queryRunner.query(ctx.conn, sql, nullableByteArrayRes, height)
    }

    override fun getConfigurationData(ctx: EContext, hash: ByteArray): ByteArray? {
        val sql = "SELECT configuration_data FROM ${tableConfigurations(ctx)} WHERE configuration_hash = ?"
        val res = queryRunner.query(ctx.conn, sql, mapListHandler, hash)
        return when (res.size) {
            0 -> null
            1 -> res[0]["configuration_data"] as ByteArray
            else -> throw ProgrammerMistake("Found multiple configurations with hash ${hash.toHex()}")
        }
    }

    override fun getPeerInfoCollection(ctx: AppContext): Array<PeerInfo> {
        return findPeerInfo(ctx, null, null, null)
    }

    override fun findPeerInfo(ctx: AppContext, host: String?, port: Int?, pubKeyPattern: String?): Array<PeerInfo> {
        // Collecting where's conditions
        val conditions = mutableListOf<String>()
        if (host != null) {
            conditions.add("$TABLE_PEERINFOS_FIELD_HOST = '$host'")
        }

        if (port != null) {
            conditions.add("$TABLE_PEERINFOS_FIELD_PORT = '$port'")
        }

        if (pubKeyPattern != null) {
            conditions.add("$TABLE_PEERINFOS_FIELD_PUBKEY ILIKE '%$pubKeyPattern%'")
        }

        // Building a query
        val query = if (conditions.isEmpty()) {
            "SELECT * FROM ${tablePeerinfos()}"
        } else {
            conditions.joinToString(
                    separator = " AND ",
                    prefix = "SELECT * FROM ${tablePeerinfos()} WHERE "
            )
        }

        // Running the query
        val rawPeerInfos: MutableList<MutableMap<String, Any>> = queryRunner.query(
                ctx.conn, query, MapListHandler())

        return rawPeerInfos.map {
            PeerInfo(
                    it[TABLE_PEERINFOS_FIELD_HOST] as String,
                    it[TABLE_PEERINFOS_FIELD_PORT] as Int,
                    (it[TABLE_PEERINFOS_FIELD_PUBKEY] as String).hexStringToByteArray(),
                    (it[TABLE_PEERINFOS_FIELD_TIMESTAMP] as? Timestamp)?.toInstant() ?: Instant.EPOCH
            )
        }.toTypedArray()
    }

    override fun addPeerInfo(ctx: AppContext, peerInfo: PeerInfo): Boolean {
        return addPeerInfo(ctx, peerInfo.host, peerInfo.port, peerInfo.pubKey.toHex())
    }

    override fun addPeerInfo(ctx: AppContext, host: String, port: Int, pubKey: String, timestamp: Instant?): Boolean {
        val time = SqlUtils.toTimestamp(timestamp)
        val sql = """
            INSERT INTO ${tablePeerinfos()} 
            ($TABLE_PEERINFOS_FIELD_HOST, $TABLE_PEERINFOS_FIELD_PORT, $TABLE_PEERINFOS_FIELD_PUBKEY, $TABLE_PEERINFOS_FIELD_TIMESTAMP) 
            VALUES (?, ?, ?, ?) RETURNING $TABLE_PEERINFOS_FIELD_PUBKEY
        """.trimIndent()
        return pubKey == queryRunner.insert(ctx.conn, sql, ScalarHandler<String>(), host, port, pubKey, time)
    }

    override fun updatePeerInfo(ctx: AppContext, host: String, port: Int, pubKey: String, timestamp: Instant?): Boolean {
        val time = SqlUtils.toTimestamp(timestamp)
        val sql = """
            UPDATE ${tablePeerinfos()} 
            SET $TABLE_PEERINFOS_FIELD_HOST = ?, $TABLE_PEERINFOS_FIELD_PORT = ?, $TABLE_PEERINFOS_FIELD_TIMESTAMP = ? 
            WHERE $TABLE_PEERINFOS_FIELD_PUBKEY = ?
        """.trimIndent()
        val updated = queryRunner.update(ctx.conn, sql, host, port, time, pubKey)
        return (updated >= 1)
    }

    override fun removePeerInfo(ctx: AppContext, pubKey: String): Array<PeerInfo> {
        val result = mutableListOf<PeerInfo>()

        val peerInfos = findPeerInfo(ctx, null, null, pubKey)
        peerInfos.forEach { peeInfo ->
            val sql = """
                DELETE FROM ${tablePeerinfos()} 
                WHERE $TABLE_PEERINFOS_FIELD_PUBKEY = '${peeInfo.pubKey.toHex()}'
            """.trimIndent()
            val deleted = queryRunner.update(ctx.conn, sql)

            if (deleted == 1) {
                result.add(peeInfo)
            }
        }

        return result.toTypedArray()
    }

    override fun addBlockchainReplica(ctx: AppContext, brid: BlockchainRid, pubKey: PubKey): Boolean {
        if (existsBlockchainReplica(ctx, brid, pubKey)) {
            return false
        }
        /*
        Due to reference integrity between tables peerInfos and BlockchainReplicas AND the fact that the pubkey string in peerInfos
        can hold both lower and upper characters (historically), we use the exact (case-sensitive) value from the peerInfos table when
        adding the node as blockchain replica.
         */
        val sql = """
            INSERT INTO ${tableBlockchainReplicas()} 
            ($TABLE_REPLICAS_FIELD_BRID, $TABLE_REPLICAS_FIELD_PUBKEY) 
            VALUES (?, (SELECT $TABLE_PEERINFOS_FIELD_PUBKEY FROM ${tablePeerinfos()} WHERE lower($TABLE_PEERINFOS_FIELD_PUBKEY) = lower(?)))
        """.trimIndent()
        queryRunner.insert(ctx.conn, sql, ScalarHandler<String>(), brid.toHex(), pubKey.hex())
        return true
    }

    override fun getBlockchainReplicaCollection(ctx: AppContext): Map<BlockchainRid, List<NodeRid>> {

        val query = "SELECT * FROM ${tableBlockchainReplicas()}"

        val raw: MutableList<MutableMap<String, Any>> = queryRunner.query(
                ctx.conn, query, MapListHandler())

        /*
        Each MutableMap represents a row in the table.
        MutableList is thus a list of rows in the table.
         */
        return raw.groupBy(keySelector = { BlockchainRid((it[TABLE_REPLICAS_FIELD_BRID] as String).hexStringToByteArray()) },
                valueTransform = { NodeRid((it[TABLE_REPLICAS_FIELD_PUBKEY] as String).hexStringToByteArray()) })
    }

    override fun getBlockchainsToReplicate(ctx: AppContext, pubkey: String): Set<BlockchainRid> {
        val query = "SELECT $TABLE_REPLICAS_FIELD_BRID FROM ${tableBlockchainReplicas()} WHERE $TABLE_REPLICAS_FIELD_PUBKEY = ?"

        val result = queryRunner.query(ctx.conn, query, ColumnListHandler<String>(TABLE_REPLICAS_FIELD_BRID), pubkey)
        return result.map { BlockchainRid.buildFromHex(it) }.toSet()
    }

    override fun existsBlockchainReplica(ctx: AppContext, brid: BlockchainRid, pubkey: PubKey): Boolean {
        val query = """
            SELECT count($TABLE_REPLICAS_FIELD_PUBKEY) 
            FROM ${tableBlockchainReplicas()}
            WHERE $TABLE_REPLICAS_FIELD_BRID = '${brid.toHex()}' AND
            lower($TABLE_REPLICAS_FIELD_PUBKEY) = lower('${pubkey.hex()}') 
            """.trimIndent()

        return queryRunner.query(ctx.conn, query, ScalarHandler<Long>()) > 0
    }

    override fun removeBlockchainReplica(ctx: AppContext, brid: BlockchainRid?, pubKey: PubKey): Set<BlockchainRid> {
        val delete = """DELETE FROM ${tableBlockchainReplicas()} 
                WHERE $TABLE_REPLICAS_FIELD_PUBKEY = ?"""
        val res = if (brid == null) {
            val sql = """
                $delete
                RETURNING *
            """.trimIndent()
            queryRunner.query(ctx.conn, sql, ColumnListHandler<String>(TABLE_REPLICAS_FIELD_BRID), pubKey.hex())
        } else {
            val sql = """
                $delete
                AND $TABLE_REPLICAS_FIELD_BRID = ?
                RETURNING *
            """.trimIndent()
            queryRunner.query(ctx.conn, sql, ColumnListHandler(TABLE_REPLICAS_FIELD_BRID), pubKey.hex(), brid.toHex())
        }
        return res.map { BlockchainRid.buildFromHex(it) }.toSet()
    }

    override fun setMustSyncUntil(ctx: AppContext, blockchainRID: BlockchainRid, height: Long): Boolean {
        // If given brid (chainID) already exist in table ( => CONFLICT), update table with the given height parameter.
        val sql = """
            INSERT INTO ${tableMustSyncUntil()} 
            ($TABLE_SYNC_UNTIL_FIELD_CHAIN_IID, $TABLE_SYNC_UNTIL_FIELD_HEIGHT) 
            VALUES ((SELECT chain_iid FROM ${tableBlockchains()} WHERE blockchain_rid = ?), ?) 
            ON CONFLICT ($TABLE_SYNC_UNTIL_FIELD_CHAIN_IID) DO UPDATE SET $TABLE_SYNC_UNTIL_FIELD_HEIGHT = ?
        """.trimIndent()
        queryRunner.insert(ctx.conn, sql, ScalarHandler<String>(), blockchainRID.data, height, height)
        return true
    }

    override fun getMustSyncUntil(ctx: AppContext): Map<Long, Long> {

        val query = "SELECT * FROM ${tableMustSyncUntil()}"
        val raw: MutableList<MutableMap<String, Any>> = queryRunner.query(
                ctx.conn, query, MapListHandler())
        /*
        Each MutableMap represents a row in the table.
        MutableList is thus a list of rows in the table.
         */
        return raw.associate {
            it[TABLE_SYNC_UNTIL_FIELD_CHAIN_IID] as Long to
                    it[TABLE_SYNC_UNTIL_FIELD_HEIGHT] as Long
        }
    }

    override fun getChainIds(ctx: AppContext): Map<BlockchainRid, Long> {
        val sql = "SELECT * FROM ${tableBlockchains()}"
        val raw: MutableList<MutableMap<String, Any>> = queryRunner.query(
                ctx.conn, sql, MapListHandler())

        return raw.associate {
            BlockchainRid(it["blockchain_rid"] as ByteArray) to it["chain_iid"] as Long
        }
    }

    fun tableExists(connection: Connection, tableName: String): Boolean {
        val tableName0 = tableName.removeSurrounding("\"")
        val types: Array<String> = arrayOf("TABLE")
        val rs = connection.metaData.getTables(null, null, null, types)
        while (rs.next()) {
            // Avoid wildcard '_' in SQL. Eg: if you pass "employee_salary" that should return something
            // employee salary which we don't expect
            if (rs.getString("TABLE_SCHEM").equals(connection.schema, true)
                    && rs.getString("TABLE_NAME").equals(tableName0, true)
            ) {
                return true
            }
        }
        return false
    }
}

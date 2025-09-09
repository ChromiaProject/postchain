package net.postchain.base.data

import mu.KLogging
import net.postchain.StorageBuilder
import net.postchain.base.BaseBlockHeader
import net.postchain.base.PeerInfo
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.configuration.FaultyConfiguration
import net.postchain.base.data.DatabaseAccess.StateData
import net.postchain.base.data.SqlUtils.isUniqueViolation
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.snapshot.Page
import net.postchain.common.BlockchainRid
import net.postchain.common.data.HASH_LENGTH
import net.postchain.common.data.Hash
import net.postchain.common.exception.NotFound
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.types.WrappedByteArray
import net.postchain.common.wrap
import net.postchain.core.AppContext
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.core.NodeRid
import net.postchain.core.SignableTransaction
import net.postchain.core.Transaction
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TransactionInfoExtsTruncated
import net.postchain.core.TxDetail
import net.postchain.core.TxEContext
import net.postchain.core.block.BlockDetail
import net.postchain.core.block.BlockDetailsTruncated
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockQueryHeightFilter
import net.postchain.core.block.BlockQueryTimeFilter
import net.postchain.core.block.BlockWitness
import net.postchain.core.block.Filter
import net.postchain.core.block.size
import net.postchain.crypto.PubKey
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.mapper.toObject
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ColumnListHandler
import org.apache.commons.dbutils.handlers.MapListHandler
import org.apache.commons.dbutils.handlers.ScalarHandler
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import kotlin.math.max


abstract class SQLDatabaseAccess : DatabaseAccess {

    internal fun tableMeta(): String = "meta"
    protected fun tableContainers(): String = "containers"
    internal fun tableBlockchains(): String = "blockchains"
    protected fun tablePeerinfos(): String = "peerinfos"
    protected fun tableBlockchainReplicas(): String = "blockchain_replicas"
    protected fun tableMustSyncUntil(): String = "must_sync_until"
    internal fun tableConfigurations(ctx: EContext): String = tableName(ctx, "configurations")
    protected fun tableConfigurations(chainId: Long): String = tableName(chainId, "configurations")
    protected fun tableFaultyConfiguration(chainId: Long): String = tableName(chainId, "sys.faulty_configuration")
    protected fun tableSnapshotContexts(chainId: Long): String = tableName(chainId, "sys.snapshot_contexts")
    protected fun tableSnapshotUpdatedDatum(chainId: Long): String = tableName(chainId, "sys.snapshot_updated_datum")
    protected fun tableSnapshotSyncState(): String = "snapshot_sync_state"
    protected fun tableSnapshotSyncContextState(): String = "snapshot_sync_context_state"
    private fun tableFaultyConfiguration(ctx: EContext): String = tableFaultyConfiguration(ctx.chainID)
    internal fun tableTransactions(ctx: EContext): String = tableName(ctx, "transactions")
    protected fun tableTransactions(chainId: Long): String = tableName(chainId, "transactions")
    internal fun tableTransactionSigners(ctx: EContext): String = tableName(ctx, "sys.transaction_signers")
    protected fun tableTransactionSigners(chainId: Long): String = tableName(chainId, "sys.transaction_signers")
    protected fun indexTableTransactionSigners(chainId: Long): String = tableName(chainId, "c${chainId}_idx_sys_transaction_signers")
    internal fun tableBlocks(ctx: EContext): String = tableName(ctx, "blocks")
    private fun tableBlocks(chainId: Long): String = tableName(chainId, "blocks")
    protected fun tablePages(ctx: EContext, name: String): String = tableName(ctx, "${name}_pages")
    protected fun tableEventLeafs(ctx: EContext, prefix: String): String = tableName(ctx, "${prefix}_event_leafs")
    protected fun tableStateLeafs(ctx: EContext, prefix: String): String = tableName(ctx, "${prefix}_state_leafs")
    protected fun indexTableStateLeafs(ctx: EContext, prefix: String, index: Int): String = tableName(ctx, "idx${index}_${prefix}_state_leafs")

    fun tableGtxModuleVersion(ctx: EContext): String = tableName(ctx, "gtx_module_version")

    override fun tableName(ctx: EContext, table: String): String = tableName(ctx.chainID, table)

    protected fun tableName(chainId: Long, table: String): String = tableName("c${chainId}.$table")

    override fun tableName(table: String) = "\"$table\""

    protected fun functionName(chainId: Long, function: String): String = "\"c${chainId}.$function\""

    protected fun stripQuotes(string: String): String = string.replace("\"", "")

    // --- Create Table ---
    protected abstract fun cmdCreateTableMeta(): String
    protected abstract fun cmdCreateTableContainers(): String
    protected abstract fun cmdCreateTableBlockchains(): String
    protected abstract fun cmdUpdateTableBlockchainsV7(): String
    protected abstract fun cmdCreateTablePeerInfos(): String
    protected abstract fun cmdCreateTableBlockchainReplicas(): String
    protected abstract fun cmdCreateTableMustSyncUntil(): String
    protected abstract fun cmdCreateTableConfigurations(ctx: EContext): String
    protected abstract fun cmdCreateTableTransactions(ctx: EContext): String
    protected abstract fun cmdUpdateTableTransactionsV8First(chainId: Long): String
    protected abstract fun cmdUpdateTableTransactionsV8Second(chainId: Long): String
    protected abstract fun cmdUpdateTableTransactionsV8Third(chainId: Long): String
    protected abstract fun cmdCreateTableTransactionSigners(chainId: Long): String
    protected abstract fun cmdCreateTableTransactionSignersIndex(chainId: Long): String
    protected abstract fun cmdCreateTableBlocks(ctx: EContext): String
    protected abstract fun cmdInsertBlocks(ctx: EContext): String
    protected abstract fun cmdCreateTablePage(ctx: EContext, name: String): String

    protected abstract fun cmdUpdateTableConfigurationsV4First(chainId: Long): String
    protected abstract fun cmdUpdateTableConfigurationsV4Second(chainId: Long): String
    protected abstract fun cmdDropTableConfigurationDataNotNull(chainId: Long): String
    protected abstract fun cmdAddHashVersionToConfigTable(chainId: Long): String

    protected abstract fun cmdCreateTableFaultyConfiguration(chainId: Long): String

    protected abstract fun cmdAddTableBlockchainReplicasPubKeyConstraint(): String
    protected abstract fun cmdAlterHexColumnToBytea(tableName: String, columnName: String): String
    protected abstract fun cmdDropTableConstraint(tableName: String, constraintName: String): String
    protected abstract fun cmdGetTableBlockchainReplicasPubKeyConstraint(): String
    protected abstract fun cmdGetTableConstraints(tableName: String): String
    protected abstract fun cmdGetAllBlockchainTables(chainId: Long): String
    protected abstract fun cmdGetAllBlockchainFunctions(chainId: Long): String

    protected abstract fun cmdCreateTableSnapshotContexts(chainId: Long): String
    protected abstract fun cmdCreateTableSnapshotUpdatedDatum(chainId: Long): String
    protected abstract fun cmdCreateTableSnapshotSyncState(): String
    protected abstract fun cmdCreateTableSnapshotSyncContextState(): String

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

    abstract fun cmdCreateTableGtxModuleVersion(ctx: EContext): String

    var queryRunner = QueryRunner()
    private val intRes = ScalarHandler<Int>()
    val longRes = ScalarHandler<Long>()
    private val stringRes = ScalarHandler<String>()
    private val nullableByteArrayRes = ScalarHandler<ByteArray?>()
    private val nullableIntRes = ScalarHandler<Int?>()
    private val nullableLongRes = ScalarHandler<Long?>()
    internal val byteArrayRes = ScalarHandler<ByteArray>()
    internal val mapListHandler = MapListHandler()

    companion object : KLogging() {
        const val TABLE_META_KEY_VERSION = "version"
        const val TABLE_META_KEY_LAST_CHAIN_IID = "chain_iid"

        const val FIELD_NAME_CHAIN_IID = "chain_iid"

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

    override fun insertTransaction(ctx: BlockEContext, tx: Transaction, transactionNumber: Long): Long {
        queryRunner.update(ctx.conn, cmdInsertTransactions(ctx), tx.getRID(), tx.getRawData(), tx.getHash(), ctx.blockIID, transactionNumber)

        val sql = "SELECT tx_iid FROM ${tableTransactions(ctx)} WHERE tx_rid = ?"
        val txIid = queryRunner.query(ctx.conn, sql, longRes, tx.getRID())

        if (tx is SignableTransaction) {
            insertTransactionSigners(ctx, tx, txIid)
        }

        return txIid
    }

    protected fun insertTransactionSigners(ctx: EContext, tx: SignableTransaction, txIid: Long) {
        tx.signers.forEach {
            queryRunner.update(ctx.conn, "INSERT INTO ${tableTransactionSigners(ctx)} (signer, tx_iid) VALUES (?, ?)", it, txIid)
        }
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

    override fun getTransactionInfo(ctx: EContext, txRID: ByteArray, includeTxData: Boolean): TransactionInfoExt? {
        val sql = """
            SELECT b.block_rid, b.block_height, b.block_header_data, b.block_witness, b.timestamp, t.tx_rid, t.tx_hash ${if (includeTxData) ", t.tx_data" else ""}, t.tx_iid 
                    FROM ${tableBlocks(ctx)} as b 
                    JOIN ${tableTransactions(ctx)} as t ON (t.block_iid = b.block_iid) 
                    WHERE t.tx_rid = ?
                    ORDER BY b.block_height DESC LIMIT 1;
        """.trimIndent()

        val txInfos = queryRunner.query(ctx.conn, sql, mapListHandler, txRID)
        if (txInfos.isEmpty()) return null
        val txInfo = txInfos.first()

        return buildTransactionInfoExt(txInfo)
    }

    override fun getTransactionsInfo(ctx: EContext, timeFilter: BlockQueryTimeFilter, limit: Int, maxDataSize: Int): TransactionInfoExtsTruncated {
        val sql = """
            WITH TransactionInfo AS (
                SELECT b.block_rid, b.block_height, b.block_header_data, b.block_witness, b.timestamp, t.tx_rid, t.tx_hash, t.tx_data, t.tx_iid, 
                    SUM(
                        LENGTH(b.block_rid) * 2 + 
                        LENGTH(CAST(b.block_height AS TEXT)) + 
                        LENGTH(b.block_header_data) * 2 + 
                        LENGTH(b.block_witness) * 2 + 
                        LENGTH(CAST(b.timestamp AS TEXT)) + 
                        LENGTH(t.tx_rid) * 2 + 
                        LENGTH(t.tx_hash) * 2 + 
                        LENGTH(t.tx_data) * 2 
                    ) OVER (ORDER BY b.block_height DESC, t.tx_iid DESC) AS cumulative_size
                FROM ${tableBlocks(ctx)} AS b 
                JOIN ${tableTransactions(ctx)} AS t ON (t.block_iid = b.block_iid) 
                WHERE b.timestamp < ? AND b.timestamp > ?
                ORDER BY b.block_height DESC, t.tx_iid DESC LIMIT ?
            )
            SELECT block_rid, block_height, block_header_data, block_witness, timestamp, tx_rid, tx_hash, tx_data,
            (SELECT COUNT(*) FROM TransactionInfo) - (SELECT COUNT(*) FROM TransactionInfo WHERE cumulative_size <= ?) AS remaining_truncated_count
            FROM TransactionInfo
            WHERE cumulative_size <= ?;
        """.trimIndent()
        val transactions = queryRunner.query(ctx.conn, sql, mapListHandler, timeFilter.beforeTime, timeFilter.afterTime, limit, maxDataSize, maxDataSize)
        val transactionInfoExts = transactions.map(::buildTransactionInfoExt)
        val remainingTruncatedCount = transactions.map(::remainingTruncatedCount).firstOrNull() ?: 0
        return TransactionInfoExtsTruncated(transactionInfoExts, remainingTruncatedCount != 0L)
    }

    override fun getTransactionsInfoBySigner(ctx: EContext, timeFilter: BlockQueryTimeFilter, limit: Int, signer: PubKey, maxDataSize: Int): TransactionInfoExtsTruncated {
        val sql = """
            WITH TransactionInfo AS (
                SELECT b.block_rid, b.block_height, b.block_header_data, b.block_witness, b.timestamp, t.tx_iid, t.tx_rid, t.tx_hash, t.tx_data, 
                    SUM(
                        LENGTH(b.block_rid) * 2 + 
                        LENGTH(CAST(b.block_height AS TEXT)) + 
                        LENGTH(b.block_header_data) * 2 + 
                        LENGTH(b.block_witness) * 2 + 
                        LENGTH(CAST(b.timestamp AS TEXT)) + 
                        LENGTH(t.tx_rid) * 2 + 
                        LENGTH(t.tx_hash) * 2 + 
                        LENGTH(t.tx_data) * 2 
                    ) OVER (ORDER BY b.block_height DESC, t.tx_iid DESC) AS cumulative_size
                FROM ${tableBlocks(ctx)} AS b
                JOIN ${tableTransactions(ctx)} AS t ON (t.block_iid = b.block_iid)
                WHERE t.tx_iid IN (SELECT tx_iid FROM ${tableTransactionSigners(ctx)} WHERE signer = ?) 
                AND b.timestamp < ? AND b.timestamp > ?
                ORDER BY b.block_height DESC, t.tx_iid DESC LIMIT ?
            )
            SELECT block_rid, block_height, block_header_data, block_witness, timestamp, tx_rid, tx_hash, tx_data,
                (SELECT COUNT(*) FROM TransactionInfo) - (SELECT COUNT(*) FROM TransactionInfo WHERE cumulative_size <= ?) AS remaining_truncated_count
            FROM TransactionInfo
            WHERE cumulative_size <= ?;
        """.trimIndent()
        val transactions = queryRunner.query(ctx.conn, sql, mapListHandler, signer.data, timeFilter.beforeTime, timeFilter.afterTime, limit, maxDataSize, maxDataSize)
        val transactionInfoExts = transactions.map(::buildTransactionInfoExt)
        val remainingTruncatedCount = transactions.map(::remainingTruncatedCount).firstOrNull() ?: 0
        return TransactionInfoExtsTruncated(transactionInfoExts, remainingTruncatedCount != 0L)
    }

    private fun buildTransactionInfoExt(txInfo: MutableMap<String, Any>): TransactionInfoExt {
        val blockRID = txInfo["block_rid"] as ByteArray
        val blockHeight = txInfo["block_height"] as Long
        val blockHeader = txInfo["block_header_data"] as ByteArray
        val blockWitness = txInfo["block_witness"] as ByteArray
        val blockTimestamp = txInfo["timestamp"] as Long
        val resultTxRID = txInfo["tx_rid"] as ByteArray
        val txHash = txInfo["tx_hash"] as ByteArray
        val txData = txInfo["tx_data"] as ByteArray?
        return TransactionInfoExt(
                blockRID, blockHeight, blockHeader, blockWitness, blockTimestamp, resultTxRID, txHash, txData)
    }

    private fun remainingTruncatedCount(result: MutableMap<String, Any>): Long {
        return result["remaining_truncated_count"] as Long
    }

    override fun getLastTransactionNumber(ctx: EContext): Long {
        val sql = "SELECT tx_number FROM ${tableTransactions(ctx)} ORDER BY tx_number DESC LIMIT 1"
        return queryRunner.query(ctx.conn, sql, longRes) ?: 0L
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

    @Deprecated("Use getState()", replaceWith = ReplaceWith("getState(ctx, prefix, height, stateN)"))
    override fun getAccountState(ctx: EContext, prefix: String, height: Long, stateN: Long) = getState(ctx, prefix, height, stateN)?.let {
        DatabaseAccess.AccountState(
                it.blockHeight,
                it.stateN,
                it.data
        )
    }

    override fun getState(ctx: EContext, prefix: String, height: Long, stateN: Long): StateData? {
        val sql = """SELECT block_height, state_n, data FROM ${tableStateLeafs(ctx, prefix)} 
            WHERE block_height <= ? AND state_n = ? 
            ORDER BY state_iid DESC LIMIT 1"""
        val rows = queryRunner.query(ctx.conn, sql, mapListHandler, height, stateN)
        if (rows.isEmpty()) return null
        val data = rows.first()
        return StateData(
                data["block_height"] as Long,
                data["state_n"] as Long,
                data["data"] as ByteArray
        )
    }

    override fun getStates(ctx: EContext, prefix: String, height: Long, startN: Long, datumHandler: (stateData: StateData?) -> Boolean) {
        val sql = """SELECT state_n, data FROM ${tableStateLeafs(ctx, prefix)} 
            WHERE block_height <= ? AND state_n >= ? 
            ORDER BY state_n ASC"""
        ctx.conn.prepareStatement(sql).use {
            it.setLong(1, height)
            it.setLong(2, startN)
            val resultSet = it.executeQuery()
            while (resultSet.next()) {
                val data = StateData(height, resultSet.getLong("state_n"), resultSet.getBytes("data"))
                if (!datumHandler(data)) {
                    return
                }
            }
            datumHandler(null)
        }
    }

    override fun getStateNMax(ctx: EContext, prefix: String, height: Long): Long? {
        val sql = """SELECT block_height, MAX(state_n) AS state_n_max FROM ${tableStateLeafs(ctx, prefix)}
            WHERE block_height <= ?
            GROUP BY block_height
            ORDER BY block_height DESC LIMIT 1"""
        val rows = queryRunner.query(ctx.conn, sql, mapListHandler, height)
        if (rows.isEmpty()) return null
        return rows.first()["state_n_max"] as Long
    }

    override fun insertEvent(ctx: TxEContext, prefix: String, height: Long, position: Long, hash: Hash, data: ByteArray) {
        queryRunner.update(ctx.conn, cmdInsertEvent(ctx, prefix), height, position, hash, ctx.txIID, data)
    }

    override fun insertState(ctx: EContext, prefix: String, height: Long, stateN: Long, data: ByteArray) {
        queryRunner.update(ctx.conn, cmdInsertState(ctx, prefix), height, stateN, data)
    }

    override fun pruneEvents(ctx: EContext, prefix: String, heightMustBeHigherThan: Long) {
        queryRunner.update(ctx.conn, cmdPruneEvents(ctx, prefix), heightMustBeHigherThan)
    }

    override fun pruneAccountStates(ctx: EContext, prefix: String, left: Long, right: Long, heightMustBeHigherThan: Long) {
        validateLeafRange(left, right)

        val sql = """
            DELETE FROM ${tableStateLeafs(ctx, prefix)} 
            WHERE (state_n BETWEEN ? and ?) AND block_height <= ?            
        """.trimIndent()

        queryRunner.update(ctx.conn, sql, left, right, heightMustBeHigherThan)
    }

    private fun validateLeafRange(left: Long, right: Long) {
        if (left > right) {
            throw ProgrammerMistake("Invalid range: left value ($left) is greater than right value ($right)")
        }
    }

    /**
     * Deletes prunable account states that are no longer needed at the specified height.
     *
     * @param ctx The chain execution context.
     * @param prefix The table name prefix.
     * @param left The lower bound of the range for account states to be deleted (inclusive).
     * @param right The upper bound of the range for account states to be deleted (inclusive).
     * @param nextSnapshotHeight The height of the next snapshot used to determine which states are prunable.
     */
    override fun safePruneAccountStates(ctx: EContext, prefix: String, left: Long, right: Long, nextSnapshotHeight: Long) {
        validateLeafRange(left, right)

        val sql = """
            DELETE FROM ${tableStateLeafs(ctx, prefix)} 
            WHERE block_height < ? AND state_n BETWEEN ? AND ? 
            AND state_n in (
                SELECT state_n 
                FROM ${tableStateLeafs(ctx, prefix)} 
                WHERE block_height = ? AND state_n BETWEEN ? AND ?
            )
        """.trimIndent()
        queryRunner.update(ctx.conn, sql, nextSnapshotHeight, left, right, nextSnapshotHeight, left, right)
    }

    override fun insertPage(ctx: EContext, pageStoreName: String, page: Page) {
        val childHashes = page.childHashes.fold(ByteArray(0)) { total, item -> total.plus(item) }
        queryRunner.update(ctx.conn, cmdInsertPage(ctx, pageStoreName), page.blockHeight, page.level, page.left, childHashes)
    }

    /**
     * If we didn't prune the old one then we need to query the snapshot page
     * at highest block height that less than or equal to specific height
     */
    override fun getPageEqualOrLowerThanHeight(ctx: EContext, pageStoreName: String, height: Long, level: Int, left: Long): Page? {
        val sql = """
            SELECT child_hashes FROM ${tablePages(ctx, pageStoreName)} 
            WHERE block_height = (
                SELECT MAX(block_height) FROM ${tablePages(ctx, pageStoreName)} 
                WHERE block_height <= ? AND level = ? AND left_index = ?
            )
            AND level = ? AND left_index = ? ORDER BY page_iid DESC
        """.trimIndent()
        val data = queryRunner.query(ctx.conn, sql, nullableByteArrayRes, height, level, left, level, left)
        return createPage(height, level, left, data)
    }

    override fun getPageAtHeight(ctx: EContext, pageStoreName: String, height: Long, level: Int, left: Long): Page? {
        val sql = """
            SELECT child_hashes FROM ${tablePages(ctx, pageStoreName)} 
            WHERE block_height = ? AND level = ? AND left_index = ?
        """.trimIndent()
        val data = queryRunner.query(ctx.conn, sql, nullableByteArrayRes, height, level, left)
        return createPage(height, level, left, data)
    }

    private fun createPage(height: Long, level: Int, left: Long, data: ByteArray?): Page? {
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

    override fun getHighestLevelPageEqualOrLowerThanHeight(ctx: EContext, pageStoreName: String, height: Long): Int {
        val sql = "SELECT COALESCE(MAX(level), 0) FROM ${tablePages(ctx, pageStoreName)} WHERE block_height <= ?"
        return queryRunner.query(ctx.conn, sql, intRes, height)
    }

    override fun getHighestLevelPageAtHeight(ctx: EContext, pageStoreName: String, height: Long): Int {
        val sql = "SELECT COALESCE(MAX(level), 0) FROM ${tablePages(ctx, pageStoreName)} WHERE block_height = ?"
        return queryRunner.query(ctx.conn, sql, intRes, height)
    }

    override fun initializeAppWithCurrentDbVersion(connection: Connection, allowUpgrade: Boolean) {
        initializeApp(connection, StorageBuilder.getCurrentDbVersion(), allowUpgrade)
    }

    override fun initializeApp(connection: Connection, expectedDbVersion: Int, allowUpgrade: Boolean) {
        if (expectedDbVersion !in 1..13) {
            throw UserMistake("Unsupported DB version $expectedDbVersion")
        }

        /**
         * "CREATE TABLE IF NOT EXISTS" is not good enough for the meta table
         * We need to know whether it exists or not in order to
         * make decisions on upgrade
         */
        if (tableExists(connection, tableMeta())) {
            // meta table already exists. Check the version
            val sql = "SELECT value FROM ${tableMeta()} WHERE key='$TABLE_META_KEY_VERSION'"
            val version = queryRunner.query(connection, sql, ScalarHandler<String>()).toInt()

            when {
                expectedDbVersion < version ->
                    throw DbVersionDowngradeDisallowedException("Database downgrade is not allowed from $version to $expectedDbVersion")

                expectedDbVersion != version && !allowUpgrade ->
                    throw DbVersionUpgradeDisallowedException("Database upgrade is not allowed from $version to $expectedDbVersion")
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

            if (version < 5 && expectedDbVersion >= 5) {
                logger.info("Upgrading to version 5")
                version5(connection)
            }

            if (version < 6 && expectedDbVersion >= 6) {
                logger.info("Upgrading to version 6")
                version6(connection)
            }

            if (version < 7 && expectedDbVersion >= 7) {
                logger.info("Upgrading to version 7")
                try {
                    version7(connection)
                } catch (e: SQLException) {
                    if (e.isUniqueViolation()) {
                        throw UserMistake("Blockchains with duplicate RIDs found, please remove before upgrading database")
                    } else {
                        throw e
                    }
                }
            }

            if (version < 8 && expectedDbVersion >= 8) {
                logger.info("Upgrading to version 8")
                version8(connection)
            }

            if (version < 9 && expectedDbVersion >= 9) {
                logger.info("Upgrading to version 9")
                version9(connection)
            }

            if (version < 10 && expectedDbVersion >= 10) {
                logger.info("Upgrading to version 10")
                version10(connection)
            }

            if (version < 11 && expectedDbVersion >= 11) {
                logger.info("Upgrading to version 11")
                version11(connection)
            }

            if (version < 12 && expectedDbVersion >= 12) {
                logger.info("Upgrading to version 12")
                version12(connection)
            }

            if (version < 13 && expectedDbVersion >= 13) {
                logger.info("Upgrading to version 13")
                version13(connection)
            }

            if (expectedDbVersion > version) {
                queryRunner.update(connection, "UPDATE ${tableMeta()} set value = ? WHERE key = '$TABLE_META_KEY_VERSION'", expectedDbVersion)
                logger.info("Database version has been updated to version: $expectedDbVersion")
            }
        } else {
            logger.debug("Meta table does not exist. Assume database does not exist and create it (version: $expectedDbVersion).")
            queryRunner.update(connection, cmdCreateTableMeta())
            val sql = "INSERT INTO ${tableMeta()} (key, value) values ('$TABLE_META_KEY_VERSION', ?)"
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

            if (expectedDbVersion >= 5) {
                version5(connection)
            }

            if (expectedDbVersion >= 6) {
                version6(connection)
            }

            if (expectedDbVersion >= 7) {
                version7(connection)
            }

            if (expectedDbVersion >= 8) {
                version8(connection)
            }

            if (expectedDbVersion >= 9) {
                version9(connection)
            }

            if (expectedDbVersion >= 10) {
                version10(connection)
            }

            if (expectedDbVersion >= 11) {
                version11(connection)
            }

            if (expectedDbVersion >= 12) {
                version12(connection)
            }

            if (expectedDbVersion >= 13) {
                version13(connection)
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
                                        parseBlockchainConfiguration(configurationData).configHash, height)
                            }
                    queryRunner.update(connection, cmdUpdateTableConfigurationsV4Second(chainId))
                }
    }

    private fun version5(connection: Connection) {
        val pubkeyConstraintName = queryRunner.query(connection, cmdGetTableBlockchainReplicasPubKeyConstraint(), ScalarHandler<String>())
        queryRunner.update(connection, cmdDropTableConstraint(tableBlockchainReplicas(), pubkeyConstraintName))
        queryRunner.update(connection, cmdAlterHexColumnToBytea(tablePeerinfos(), TABLE_PEERINFOS_FIELD_PUBKEY))
        queryRunner.update(connection, cmdAlterHexColumnToBytea(tableBlockchainReplicas(), TABLE_REPLICAS_FIELD_PUBKEY))
        queryRunner.update(connection, cmdAlterHexColumnToBytea(tableBlockchainReplicas(), TABLE_REPLICAS_FIELD_BRID))
        queryRunner.update(connection, cmdAddTableBlockchainReplicasPubKeyConstraint())
    }

    private fun version6(connection: Connection) {
        queryRunner.query(connection, "SELECT chain_iid FROM ${tableBlockchains()}", mapListHandler)
                .map { it["chain_iid"] as Long }
                .forEach { chainId -> queryRunner.update(connection, cmdCreateTableFaultyConfiguration(chainId)) }
    }

    private fun version7(connection: Connection) {
        queryRunner.update(connection, cmdUpdateTableBlockchainsV7())
    }

    private fun version8(connection: Connection) {
        queryRunner.query(connection, "SELECT chain_iid FROM ${tableBlockchains()}", mapListHandler)
                .map { it["chain_iid"] as Long }
                .forEach { chainId ->
                    queryRunner.update(connection, cmdUpdateTableTransactionsV8First(chainId))
                    var txCount = 1
                    queryRunner.query(connection, "SELECT tx_iid FROM ${tableTransactions(chainId)} ORDER BY tx_iid", mapListHandler)
                            .forEach {
                                val txIId = it["tx_iid"] as Long
                                queryRunner.update(connection,
                                        "UPDATE ${tableTransactions(chainId)} SET tx_number=? WHERE tx_iid=?",
                                        txCount++, txIId)
                            }
                    queryRunner.update(connection, cmdUpdateTableTransactionsV8Second(chainId))
                    queryRunner.update(connection, cmdUpdateTableTransactionsV8Third(chainId))
                }
    }

    private fun version9(connection: Connection) {
        queryRunner.query(connection, "SELECT chain_iid FROM ${tableBlockchains()}", mapListHandler)
                .map { it["chain_iid"] as Long }
                .forEach { chainId ->
                    queryRunner.update(connection, cmdCreateTableTransactionSigners(chainId))
                    queryRunner.update(connection, cmdCreateTableTransactionSignersIndex(chainId))
                }
    }

    private fun version10(connection: Connection) {
        queryRunner.query(connection, "SELECT chain_iid FROM ${tableBlockchains()}", mapListHandler)
                .map { it["chain_iid"] as Long }
                .forEach { chainId ->
                    queryRunner.update(connection, cmdDropTableConfigurationDataNotNull(chainId))
                }
    }

    private fun version11(connection: Connection) {
        val maxChainIid = queryRunner.query(connection,
                "SELECT MAX(chain_iid) FROM ${tableBlockchains()}", nullableLongRes) ?: -1
        queryRunner.update(connection,
                "INSERT INTO ${tableMeta()} (key, value) values ('$TABLE_META_KEY_LAST_CHAIN_IID', ?)",
                max(maxChainIid, 99))
    }

    private fun version12(connection: Connection) {
        queryRunner.query(connection, "SELECT chain_iid FROM ${tableBlockchains()}", mapListHandler)
                .map { it["chain_iid"] as Long }
                .forEach { chainId ->
                    queryRunner.update(connection, cmdAddHashVersionToConfigTable(chainId))
                }
    }

    private fun version13(connection: Connection) {
        queryRunner.query(connection, "SELECT chain_iid FROM ${tableBlockchains()}", mapListHandler)
                .map { it["chain_iid"] as Long }
                .forEach { chainId ->
                    queryRunner.update(connection, cmdCreateTableSnapshotContexts(chainId))
                    queryRunner.update(connection, cmdCreateTableSnapshotUpdatedDatum(chainId))
                }
        queryRunner.update(connection, cmdCreateTableSnapshotSyncState())
        queryRunner.update(connection, cmdCreateTableSnapshotSyncContextState())
    }

    protected fun parseBlockchainConfiguration(configurationData: ByteArray): BlockchainConfigurationData =
            GtvDecoder.decodeGtv(configurationData).toObject<BlockchainConfigurationData>()

    override fun createContainer(ctx: AppContext, name: String): Int {
        val sql = "INSERT INTO ${tableContainers()} (name) values (?) RETURNING container_iid"
        return queryRunner.insert(ctx.conn, sql, intRes, name)
    }

    override fun getContainerIid(ctx: AppContext, name: String): Int? {
        val sql = "SELECT container_iid FROM ${tableContainers()} WHERE name = ?"
        return queryRunner.query(ctx.conn, sql, nullableIntRes, name)
    }

    override fun getContainerIids(ctx: AppContext): Map<String, Int> {
        val sql = "SELECT name, container_iid FROM ${tableContainers()}"
        val result = queryRunner.query(ctx.conn, sql, mapListHandler)
        return result.associate { it["name"] as String to it["container_iid"] as Int }
    }

    override fun removeContainerIid(ctx: AppContext, name: String) {
        val sql = "DELETE FROM ${tableContainers()} WHERE name = ?"
        queryRunner.update(ctx.conn, sql, name)
    }

    override fun initializeBlockchain(ctx: EContext, blockchainRid: BlockchainRid) {
        val initialized = getBlockchainRid(ctx) != null

        queryRunner.update(ctx.conn, cmdCreateTableBlocks(ctx))
        queryRunner.update(ctx.conn, cmdCreateTableTransactions(ctx))
        queryRunner.update(ctx.conn, cmdCreateTableConfigurations(ctx))
        queryRunner.update(ctx.conn, cmdCreateTableFaultyConfiguration(ctx.chainID))
        queryRunner.update(ctx.conn, cmdCreateTableTransactionSigners(ctx.chainID))
        queryRunner.update(ctx.conn, cmdCreateTableTransactionSignersIndex(ctx.chainID))
        queryRunner.update(ctx.conn, cmdDropTableConfigurationDataNotNull(ctx.chainID))
        queryRunner.update(ctx.conn, cmdCreateTableSnapshotContexts(ctx.chainID))
        queryRunner.update(ctx.conn, cmdCreateTableSnapshotUpdatedDatum(ctx.chainID))
        queryRunner.update(ctx.conn, cmdCreateTableSnapshotSyncState())
        queryRunner.update(ctx.conn, cmdCreateTableSnapshotSyncContextState())

        val txIndex = "CREATE INDEX IF NOT EXISTS ${tableName(ctx, "transactions_block_iid_idx")} " +
                "ON ${tableTransactions(ctx)}(block_iid)"
        queryRunner.update(ctx.conn, txIndex)

        val blockIndex = "CREATE INDEX IF NOT EXISTS ${tableName(ctx, "blocks_timestamp_idx")} " +
                "ON ${tableBlocks(ctx)}(timestamp)"
        queryRunner.update(ctx.conn, blockIndex)

        if (!initialized) {
            // Inserting chainId -> blockchainRid
            try {
                val sql = "INSERT INTO ${tableBlockchains()} (chain_iid, blockchain_rid) values (?, ?)"
                queryRunner.update(ctx.conn, sql, ctx.chainID, blockchainRid.data)
            } catch (e: SQLException) {
                if (e.isUniqueViolation())
                    throw UserMistake("Blockchain with RID ${blockchainRid.toHex()} already exists")
                else
                    throw e
            }
        }
    }

    override fun removeBlockchain(ctx: EContext): Boolean {
        val sql = """DELETE FROM ${tableBlockchains()} 
                WHERE $FIELD_NAME_CHAIN_IID = ?"""
                .trimIndent()
        return queryRunner.update(ctx.conn, sql, ctx.chainID) != 0
    }

    override fun removeAllBlockchainSpecificTables(ctx: EContext, excludeTables: List<String>) {
        val bcTables = queryRunner.query(ctx.conn, cmdGetAllBlockchainTables(ctx.chainID), ColumnListHandler<String>())
                .filter { it.substringAfter(".") !in excludeTables }
        bcTables.forEach { tableName ->
            queryRunner.query(ctx.conn, cmdGetTableConstraints(tableName), ColumnListHandler<String>()).forEach { constraintName ->
                queryRunner.update(ctx.conn, cmdDropTableConstraint(tableName, constraintName))
            }
        }
        bcTables.forEach { tableName ->
            dropTable(ctx.conn, tableName)
        }
    }

    override fun removeAllBlockchainSpecificFunctions(ctx: EContext) {
        val allFunctions = queryRunner.query(ctx.conn, cmdGetAllBlockchainFunctions(ctx.chainID), ColumnListHandler<String>())
        if (allFunctions.isNotEmpty()) {
            val csvFunctions = allFunctions.joinToString(", ") { "\"$it\"" }
            queryRunner.update(ctx.conn, "DROP FUNCTION $csvFunctions CASCADE")
        }
    }

    override fun removeBlockchainFromMustSyncUntil(ctx: EContext): Boolean {
        val sql = """DELETE FROM ${tableMustSyncUntil()} 
                WHERE $FIELD_NAME_CHAIN_IID = ?"""
                .trimIndent()
        return queryRunner.update(ctx.conn, sql, ctx.chainID) != 0
    }

    override fun getBlockchainTables(ctx: EContext): List<String> {
        return queryRunner.query(ctx.conn, cmdGetAllBlockchainTables(ctx.chainID), ColumnListHandler())
    }

    override fun getChainId(ctx: AppContext, blockchainRid: BlockchainRid): Long? {
        val sql = "SELECT chain_iid FROM ${tableBlockchains()} WHERE blockchain_rid = ?"
        return queryRunner.query(ctx.conn, sql, nullableLongRes, blockchainRid.data)
    }

    override fun getLastSystemChainId(ctx: AppContext): Long {
        val sql = "SELECT MAX(chain_iid) FROM ${tableBlockchains()} WHERE chain_iid < 100"
        return queryRunner.query(ctx.conn, sql, nullableLongRes) ?: -1L
    }

    override fun getLastChainId(ctx: AppContext): Long {
        val sql = "SELECT value FROM ${tableMeta()} WHERE key='$TABLE_META_KEY_LAST_CHAIN_IID'"
        return queryRunner.query(ctx.conn, sql, ScalarHandler<String>()).toLong()
    }

    override fun setLastChainId(ctx: EContext) {
        queryRunner.update(ctx.conn,
                "UPDATE ${tableMeta()} set value = ? WHERE key = '$TABLE_META_KEY_LAST_CHAIN_IID'",
                ctx.chainID
        )
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

    override fun getBlocksBetweenTimes(ctx: EContext, timeFilter: BlockQueryTimeFilter, limit: Int,
                                       txHashesOnly: Boolean, maxDataSize: Int, excludeEmpty: Boolean): BlockDetailsTruncated =
            getBlocksWithFilter(ctx, "timestamp", timeFilter, limit, txHashesOnly, maxDataSize, excludeEmpty)

    override fun getBlocksBetweenHeights(ctx: EContext, heightFilter: BlockQueryHeightFilter, limit: Int,
                                         txHashesOnly: Boolean, maxDataSize: Int, excludeEmpty: Boolean): BlockDetailsTruncated =
            getBlocksWithFilter(ctx, "block_height", heightFilter, limit, txHashesOnly, maxDataSize, excludeEmpty)

    private fun getBlocksWithFilter(ctx: EContext, filterColumn: String, filter: Filter, limit: Int,
                                    txHashesOnly: Boolean, maxDataSize: Int, excludeEmpty: Boolean): BlockDetailsTruncated {
        val sql = """
            WITH Blocks AS (
                SELECT block_iid, block_rid, block_height, block_header_data, block_witness, timestamp 
                FROM ${tableBlocks(ctx)} 
                WHERE $filterColumn < ? AND $filterColumn > ? 
                ORDER BY $filterColumn DESC LIMIT ?
            )
            SELECT b.block_rid, b.block_height, b.block_header_data, b.block_witness, b.timestamp, t.tx_rid, t.tx_hash${if (txHashesOnly) "" else ", t.tx_data"} 
            FROM Blocks b ${if (excludeEmpty) "INNER" else "LEFT"} JOIN ${tableTransactions(ctx)} t ON t.block_iid=b.block_iid 
            ORDER BY b.$filterColumn DESC, tx_iid ASC
        """.trimIndent()

        return ctx.conn.prepareStatement(sql).use {
            it.setLong(1, filter.before)
            it.setLong(2, filter.after)
            it.setInt(3, limit)
            it.fetchSize = 100
            it.executeQuery().use { resultSet -> buildBlockDetails(resultSet, txHashesOnly, maxDataSize) }
        }
    }

    private fun buildBlockDetails(resultSet: ResultSet, txHashesOnly: Boolean, maxDataSize: Int): BlockDetailsTruncated {
        var blockHeight: Long = -1
        var blockRid: ByteArray = byteArrayOf()
        var blockHeader: ByteArray = byteArrayOf()
        var blockWitness: ByteArray = byteArrayOf()
        var timestamp: Long = -1
        var transactions = mutableListOf<TxDetail>()
        var cumulativeSize = 0
        var truncated = false
        val blockDetails = buildList {
            while (resultSet.next()) {
                val thisBlockHeight = resultSet.getLong("block_height")
                if (thisBlockHeight != blockHeight) {
                    if (blockHeight > -1) {
                        val blockDetail = BlockDetail(
                                rid = blockRid,
                                prevBlockRID = BlockHeaderData.fromBinary(blockHeader).getPreviousBlockRid(),
                                header = blockHeader,
                                height = blockHeight,
                                transactions = transactions,
                                witness = blockWitness,
                                timestamp = timestamp)
                        transactions = mutableListOf()
                        cumulativeSize += blockDetail.size()
                        if (cumulativeSize <= maxDataSize)
                            add(blockDetail)
                        else {
                            truncated = true
                            break
                        }
                    }
                    blockHeight = thisBlockHeight
                    blockRid = resultSet.getBytes("block_rid")
                    blockHeader = resultSet.getBytes("block_header_data")
                    blockWitness = resultSet.getBytes("block_witness")
                    timestamp = resultSet.getLong("timestamp")
                }
                val txRid = resultSet.getBytes("tx_rid")
                if (!resultSet.wasNull()) {
                    transactions.add(TxDetail(
                            txRid,
                            resultSet.getBytes("tx_hash"),
                            if (txHashesOnly) null else (resultSet.getBytes("tx_data"))
                    ))
                }
            }
            if (blockHeight > -1 && !truncated) {
                val blockDetail = BlockDetail(
                        rid = blockRid,
                        prevBlockRID = BlockHeaderData.fromBinary(blockHeader).getPreviousBlockRid(),
                        header = blockHeader,
                        height = blockHeight,
                        transactions = transactions,
                        witness = blockWitness,
                        timestamp = timestamp)
                transactions = mutableListOf()
                cumulativeSize += blockDetail.size()
                if (cumulativeSize <= maxDataSize)
                    add(blockDetail)
                else {
                    truncated = true
                }
            }
        }
        return BlockDetailsTruncated(blockDetails, truncated)
    }

    override fun getBlocksFromHeight(ctx: EContext, fromHeight: Long, limit: Int): List<DatabaseAccess.BlockInfoExt> {
        val sql = """
            SELECT block_rid, block_height, block_header_data, block_witness, timestamp 
            FROM ${tableBlocks(ctx)} 
            WHERE block_height >= ? 
            ORDER BY block_height ASC LIMIT ?
        """.trimIndent()
        val blocksInfo = queryRunner.query(ctx.conn, sql, mapListHandler, fromHeight, limit)
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

    override fun listConfigurationHashes(ctx: EContext): List<ByteArray> {
        val sql = """
            SELECT configuration_hash
            FROM ${tableConfigurations(ctx)}
            order by height
        """.trimIndent()
        return queryRunner.query(ctx.conn, sql, mapListHandler).map { res ->
            res["configuration_hash"] as ByteArray
        }
    }

    override fun configurationHashExists(ctx: EContext, hash: ByteArray): Boolean {
        val sql = """
            SELECT configuration_hash
            FROM ${tableConfigurations(ctx)}
            WHERE configuration_hash = ?
        """.trimIndent()
        return queryRunner.query(ctx.conn, sql, nullableByteArrayRes, hash) != null
    }

    override fun removeConfiguration(ctx: EContext, height: Long): Int {
        val lastBlockHeight = getLastBlockHeight(ctx)
        if (lastBlockHeight >= height) {
            throw UserMistake("Cannot remove configuration at $height, since last block is already at $lastBlockHeight")
        }
        return queryRunner.update(ctx.conn, "DELETE FROM ${tableConfigurations(ctx)} WHERE height = ?", height)
    }

    override fun getAllConfigurations(ctx: EContext): List<Pair<Long, WrappedByteArray?>> {
        return getAllConfigurations(ctx.conn, ctx.chainID)
    }

    override fun getAllConfigurations(connection: Connection, chainId: Long): List<Pair<Long, WrappedByteArray?>> {
        val sql = """
            SELECT height, configuration_data  
            FROM ${tableConfigurations(chainId)} 
            ORDER BY height
        """.trimIndent()
        return queryRunner.query(connection, sql, mapListHandler).map { configuration ->
            (configuration["height"] as Long) to (configuration["configuration_data"] as ByteArray?)?.wrap()
        }
    }

    override fun getDependenciesOnBlockchain(ctx: EContext): List<BlockchainRid> {
        val brid = getBlockchainRid(ctx)
        val dependentChains = mutableListOf<BlockchainRid>()
        queryRunner.query(ctx.conn, "SELECT blockchain_rid, chain_iid FROM ${tableBlockchains()}", mapListHandler)
                .map { it["chain_iid"] as Long to BlockchainRid(it["blockchain_rid"] as ByteArray) }
                .forEach { (dependentChainId, dependentBrid) ->
                    if (dependentBrid != brid) {
                        getAllConfigurations(ctx.conn, dependentChainId).forEach { (height, conf) ->
                            if (conf == null) {
                                throw ProgrammerMistake("Configuration data at height $height is missing. Blockchain dependencies should be determined via chain0 in managed mode")
                            }
                            BlockchainConfigurationData.fromRaw(conf.data).blockchainDependencies.forEach {
                                if (it.blockchainRid == brid) dependentChains.add(dependentBrid)
                            }
                        }
                    }
                }
        return dependentChains
    }

    override fun getConfigurationData(ctx: EContext, height: Long): ByteArray? {
        val sql = "SELECT configuration_data FROM ${tableConfigurations(ctx)} WHERE height = ?"
        return queryRunner.query(ctx.conn, sql, nullableByteArrayRes, height)
    }

    override fun getConfigurationDataForHeight(ctx: EContext, height: Long): ByteArray? {
        val sql = "SELECT configuration_data FROM ${tableConfigurations(ctx)} WHERE height <= ? ORDER BY height DESC LIMIT 1"
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

    override fun getInitialMerkleHashVersion(ctx: EContext): Long {
        val sql = "SELECT merkle_hash_version FROM ${tableConfigurations(ctx)} ORDER BY height LIMIT 1"
        return queryRunner.query(ctx.conn, sql, nullableLongRes) ?: 1
    }

    override fun getMerkleHashVersionForHeight(ctx: EContext, height: Long): Long {
        val sql = "SELECT merkle_hash_version FROM ${tableConfigurations(ctx)} WHERE height <= ? ORDER BY height DESC LIMIT 1"
        return queryRunner.query(ctx.conn, sql, nullableLongRes, height) ?: 1
    }

    override fun getCurrentMerkleHashVersion(ctx: EContext): Long {
        val sql = "SELECT merkle_hash_version FROM ${tableConfigurations(ctx)} ORDER BY height DESC LIMIT 1"
        return queryRunner.query(ctx.conn, sql, nullableLongRes) ?: 1
    }

    override fun addConfigurationData(ctx: EContext, height: Long, data: ByteArray) {
        val configuration = parseBlockchainConfiguration(data)
        queryRunner.insert(ctx.conn, cmdInsertConfiguration(ctx), longRes, height,
                data, configuration.configHash, configuration.merkleHashVersion,
                data, configuration.configHash, configuration.merkleHashVersion)
    }

    override fun addConfigurationHash(ctx: EContext, height: Long, configHash: ByteArray, merkleHashVersion: Long) {
        queryRunner.insert(ctx.conn, cmdInsertConfiguration(ctx), longRes, height, null, configHash, merkleHashVersion, null, configHash, merkleHashVersion)
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
            if (pubKeyPattern.length % 2 != 0) throw UserMistake("Pubkey pattern is of odd length and not a valid hex string")

            conditions.add("position('\\x$pubKeyPattern'::bytea in $TABLE_PEERINFOS_FIELD_PUBKEY) > 0")
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
            mapPeerInfo(it)
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
        return pubKey == queryRunner.insert(ctx.conn, sql, ScalarHandler<ByteArray>(), host, port, pubKey.hexStringToByteArray(), time).toHex()
    }

    override fun updatePeerInfo(ctx: AppContext, host: String, port: Int, pubKey: PubKey, timestamp: Instant?): Boolean {
        val time = SqlUtils.toTimestamp(timestamp)
        val sql = """
            UPDATE ${tablePeerinfos()} 
            SET $TABLE_PEERINFOS_FIELD_HOST = ?, $TABLE_PEERINFOS_FIELD_PORT = ?, $TABLE_PEERINFOS_FIELD_TIMESTAMP = ? 
            WHERE $TABLE_PEERINFOS_FIELD_PUBKEY = ?
        """.trimIndent()
        val updated = queryRunner.update(ctx.conn, sql, host, port, time, pubKey.data)
        return (updated >= 1)
    }

    override fun removePeerInfo(ctx: AppContext, pubKey: PubKey): Array<PeerInfo> {
        val sql = """
            DELETE FROM ${tablePeerinfos()} 
            WHERE $TABLE_PEERINFOS_FIELD_PUBKEY = ?
            RETURNING *
        """.trimIndent()

        val res: List<MutableMap<String, Any>> = queryRunner.query(ctx.conn, sql, MapListHandler(), pubKey.data)
        return res.map { mapPeerInfo(it) }.toTypedArray()
    }

    private fun mapPeerInfo(dbResult: MutableMap<String, Any>) = PeerInfo(
            dbResult[TABLE_PEERINFOS_FIELD_HOST] as String,
            dbResult[TABLE_PEERINFOS_FIELD_PORT] as Int,
            dbResult[TABLE_PEERINFOS_FIELD_PUBKEY] as ByteArray,
            (dbResult[TABLE_PEERINFOS_FIELD_TIMESTAMP] as? Timestamp)?.toInstant() ?: Instant.EPOCH
    )

    override fun addBlockchainReplica(ctx: AppContext, brid: BlockchainRid, pubKey: PubKey): Boolean {
        if (existsBlockchainReplica(ctx, brid, pubKey)) {
            return false
        }
        val sql = """
            INSERT INTO ${tableBlockchainReplicas()} 
            ($TABLE_REPLICAS_FIELD_BRID, $TABLE_REPLICAS_FIELD_PUBKEY) 
            VALUES (?, ?)
        """.trimIndent()
        queryRunner.insert(ctx.conn, sql, ScalarHandler<String>(), brid.data, pubKey.data)
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
        return raw.groupBy(keySelector = { BlockchainRid(it[TABLE_REPLICAS_FIELD_BRID] as ByteArray) },
                valueTransform = { NodeRid(it[TABLE_REPLICAS_FIELD_PUBKEY] as ByteArray) })
    }

    override fun getBlockchainsToReplicate(ctx: AppContext, pubkey: String): Set<BlockchainRid> {
        val query = "SELECT $TABLE_REPLICAS_FIELD_BRID FROM ${tableBlockchainReplicas()} WHERE $TABLE_REPLICAS_FIELD_PUBKEY = ?"

        val result = queryRunner.query(ctx.conn, query, ColumnListHandler<ByteArray>(TABLE_REPLICAS_FIELD_BRID), pubkey.hexStringToByteArray())
        return result.map { BlockchainRid(it) }.toSet()
    }

    override fun existsBlockchainReplica(ctx: AppContext, brid: BlockchainRid, pubkey: PubKey): Boolean {
        val query = """
            SELECT count($TABLE_REPLICAS_FIELD_PUBKEY) 
            FROM ${tableBlockchainReplicas()}
            WHERE $TABLE_REPLICAS_FIELD_BRID = ? AND
            $TABLE_REPLICAS_FIELD_PUBKEY = ?
            """.trimIndent()

        return queryRunner.query(ctx.conn, query, ScalarHandler<Long>(), brid.data, pubkey.data) > 0
    }

    override fun removeBlockchainReplica(ctx: AppContext, brid: BlockchainRid?, pubKey: PubKey): Set<BlockchainRid> {
        val delete = """DELETE FROM ${tableBlockchainReplicas()} 
                WHERE $TABLE_REPLICAS_FIELD_PUBKEY = ?"""
        val res = if (brid == null) {
            val sql = """
                $delete
                RETURNING *
            """.trimIndent()
            queryRunner.query(ctx.conn, sql, ColumnListHandler<ByteArray>(TABLE_REPLICAS_FIELD_BRID), pubKey.data)
        } else {
            val sql = """
                $delete
                AND $TABLE_REPLICAS_FIELD_BRID = ?
                RETURNING *
            """.trimIndent()
            queryRunner.query(ctx.conn, sql, ColumnListHandler(TABLE_REPLICAS_FIELD_BRID), pubKey.data, brid.data)
        }
        return res.map { BlockchainRid(it) }.toSet()
    }

    override fun removeAllBlockchainReplicas(ctx: EContext): Boolean {
        val brid = getBlockchainRid(ctx) ?: throw NotFound("Blockchain RID not found")
        val sql = """DELETE FROM ${tableBlockchainReplicas()} 
                WHERE $TABLE_REPLICAS_FIELD_BRID = ?"""
                .trimIndent()
        return queryRunner.update(ctx.conn, sql, brid.data) != 0
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

    override fun getFaultyConfiguration(ctx: EContext): FaultyConfiguration? {
        val sql = "SELECT * FROM ${tableFaultyConfiguration(ctx)}"
        val raw = queryRunner.query(ctx.conn, sql, MapListHandler())

        return raw.firstOrNull()?.let {
            FaultyConfiguration(
                    (it["configuration_hash"] as ByteArray).wrap(),
                    it["report_height"] as Long
            )
        }
    }

    override fun addFaultyConfiguration(ctx: EContext, faultyConfiguration: FaultyConfiguration) {
        // First clear any previous faulty config reference
        queryRunner.update(ctx.conn, "DELETE FROM ${tableFaultyConfiguration(ctx)}")

        queryRunner.update(ctx.conn, "INSERT INTO ${tableFaultyConfiguration(ctx)} (configuration_hash, report_height) VALUES (?, ?)",
                faultyConfiguration.configHash.data, faultyConfiguration.reportAtHeight
        )
    }

    override fun updateFaultyConfigurationReportHeight(ctx: EContext, height: Long) {
        queryRunner.update(ctx.conn, "UPDATE ${tableFaultyConfiguration(ctx)} SET report_height = ?", height)
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

    /**
     * Determines the lowest snapshot height to keep.
     *
     * This function identifies the first height above the prunable range based on the specified block height
     * and the number of recent snapshots to keep. All snapshots below the returned height are considered prunable.
     *
     * @param ctx The chain execution context.
     * @param pageStoreName The name of the page store.
     * @param blockHeight The current block height used to determine the snapshot.
     * @param snapshotsToKeep The number of recent snapshots that should be retained.
     * @return The height of the next prunable snapshot, or `null` if no snapshot is eligible for pruning.
     */
    override fun getLowestSnapshotHeightToKeep(ctx: EContext, pageStoreName: String, blockHeight: Long, snapshotsToKeep: Int): Long? {
        val snapshotsToKeepIncludingCurrentHeight = snapshotsToKeep + 1
        val sql = """
            SELECT distinct(block_height) FROM ${tablePages(ctx, pageStoreName)}
            WHERE block_height <= ?
            ORDER BY block_height DESC
            LIMIT ?
        """.trimIndent()

        ctx.conn.prepareStatement(sql).use { statement ->
            statement.setLong(1, blockHeight)
            statement.setInt(2, snapshotsToKeepIncludingCurrentHeight)
            statement.executeQuery().use { resultSet ->
                val list = buildList {
                    while (resultSet.next()) {
                        add(resultSet.getLong(1))
                    }
                }
                if (list.size < snapshotsToKeepIncludingCurrentHeight) {
                    return null
                }
                return list[snapshotsToKeep - 1]
            }
        }
    }

    override fun getLatestSnapshotHeight(ctx: EContext, pageStoreName: String): Long? {
        val sql = "SELECT max(block_height) FROM ${tablePages(ctx, pageStoreName)}"

        return queryRunner.query(ctx.conn, sql, nullableLongRes)
    }

    override fun getSnapshotSyncState(ctx: EContext): SnapshotSyncState? {
        val sql = "SELECT height, root_hash FROM ${tableSnapshotSyncState()} where chain_iid = ?"
        val result = queryRunner.query(ctx.conn, sql, mapListHandler, ctx.chainID)
        if (result.isNotEmpty()) {
            val height = result[0]["height"] as Long?
            val rootHash = result[0]["root_hash"] as ByteArray?
            if (height != null && rootHash != null) {
                return SnapshotSyncState(height, rootHash)
            }
        }
        return null
    }

    override fun setSnapshotSyncState(ctx: EContext, state: SnapshotSyncState) {
        val sql = """
            INSERT INTO ${tableSnapshotSyncState()} (chain_iid, height, root_hash) VALUES (?, ?, ?)
            ON CONFLICT (chain_iid) DO UPDATE SET height = EXCLUDED.height, root_hash = EXCLUDED.root_hash;
        """.trimIndent()
        queryRunner.update(ctx.conn, sql, ctx.chainID, state.height, state.rootHash)
    }

    override fun setSnapshotSyncContextState(ctx: EContext, state: SnapshotSyncContextState) {
        val sql = """
            INSERT INTO ${tableSnapshotSyncContextState()} (chain_iid, context_id, root_hash, dynamic_datum_id_offset, permanent_datum_id_offset, max_datum_id)
                VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (chain_iid, context_id) DO UPDATE SET root_hash = EXCLUDED.root_hash,
                dynamic_datum_id_offset = EXCLUDED.dynamic_datum_id_offset,
                permanent_datum_id_offset = EXCLUDED.permanent_datum_id_offset,
                max_datum_id = EXCLUDED.max_datum_id
        """.trimIndent()
        queryRunner.update(ctx.conn, sql, ctx.chainID, state.contextId, state.contextRootHash, state.dynamicDatumIdOffset, state.permanentDatumIdOffset, state.maxDatumId)
    }

    override fun getAllSnapshotSyncContexts(ctx: EContext): List<SnapshotSyncContextState> {
        val sql = "SELECT context_id, root_hash, dynamic_datum_id_offset, permanent_datum_id_offset, max_datum_id FROM ${tableSnapshotSyncContextState()} WHERE chain_iid = ?"
        return queryRunner.query(ctx.conn, sql, mapListHandler, ctx.chainID)
                .map { SnapshotSyncContextState(
                        it["context_id"] as Long,
                        it["root_hash"] as ByteArray,
                        it["dynamic_datum_id_offset"] as Long,
                        it["permanent_datum_id_offset"] as Long,
                        it["max_datum_id"] as Long,
                ) }
    }

    override fun setSnapshotSyncContextStateOffset(ctx: EContext, contextId: Long, permanent: Boolean, offset: Long) {
        val column = if (permanent)
            "permanent_datum_id_offset"
        else
            "dynamic_datum_id_offset"
        val sql = "UPDATE ${tableSnapshotSyncContextState()} SET $column = ? WHERE chain_iid = ? AND context_id = ?"
        queryRunner.update(ctx.conn, sql, offset, ctx.chainID, contextId)
    }

    override fun removeSnapshotSyncContextState(ctx: EContext, contextId: Long) {
        val sql = "DELETE FROM ${tableSnapshotSyncContextState()} WHERE chain_iid = ? AND context_id = ?"
        queryRunner.update(ctx.conn, sql, ctx.chainID, contextId)
    }

    override fun pruneSnapshotSyncState(ctx: EContext) {
        queryRunner.update(ctx.conn, "DELETE FROM ${tableSnapshotSyncState()} WHERE chain_iid = ?", ctx.chainID)
        queryRunner.update(ctx.conn, "DELETE FROM ${tableSnapshotSyncContextState()}  WHERE chain_iid = ?", ctx.chainID)
    }

    /**
     * Retrieves prunable pages that are no longer required at the specified height.
     *
     * A prunable page is defined as a page whose height is lower than the `lowestHeightToKeep` parameter.
     *
     * @param ctx The chain execution context.
     * @param pageStoreName The name of the page store.
     * @param lowestHeightToKeep The lowest snapshot height to keep.
     * @return A list of prunable pages (those with heights below `lowestHeightToKeep`) that can be removed.
     */
    override fun getPrunablePages(ctx: EContext, pageStoreName: String, lowestHeightToKeep: Long): List<Long> {
        val sql = """
            SELECT page_iid FROM ${tablePages(ctx, pageStoreName)}
            WHERE block_height < ? AND (level, left_index) IN (
                SELECT level, left_index FROM ${tablePages(ctx, pageStoreName)} WHERE block_height = ?
            )
        """.trimIndent()
        ctx.conn.prepareStatement(sql).use { statement ->
            statement.setLong(1, lowestHeightToKeep)
            statement.setLong(2, lowestHeightToKeep)
            statement.executeQuery().use { resultSet ->
                return buildList {
                    while (resultSet.next()) {
                        add(resultSet.getLong(1))
                    }
                }
            }
        }
    }

    /**
     * Deletes prunable pages from the specified page store.
     *
     * @param ctx The chain execution context.
     * @param pageStoreName The name of the page store.
     * @param pageIids A list of page IDs that should be deleted.
     */
    override fun deletePages(ctx: EContext, pageStoreName: String, pageIids: List<Long>): Boolean {
        val sql = """
            DELETE FROM ${tablePages(ctx, pageStoreName)} 
            WHERE page_iid in (${pageIids.joinToString(",")})
        """.trimIndent()
        ctx.conn.prepareStatement(sql).use { statement ->
            return statement.executeUpdate() > 0
        }
    }

    /**
     * Retrieves the left index values for the specified page IDs.
     *
     * @param ctx The chain execution context.
     * @param pageStoreName The name of the page store.
     * @param pageIids A list of page IDs for which the left index is retrieved.
     * @return A list of left index values corresponding to the given page IDs.
     */
    override fun getLeftIndex(ctx: EContext, pageStoreName: String, pageIids: List<Long>): List<Long> {
        val sql = """
            SELECT left_index FROM ${tablePages(ctx, pageStoreName)} 
            WHERE page_iid in (${pageIids.joinToString(",")})
        """.trimIndent()
        ctx.conn.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { resultSet ->
                return buildList {
                    while (resultSet.next()) {
                        add(resultSet.getLong(1))
                    }
                }
            }
        }
    }

    override fun getOrGenerateSnapshotContextId(ctx: EContext, moduleName: String): Long {
        // Do an insert or simply return context id if exists
        val sql = "INSERT INTO ${tableSnapshotContexts(ctx.chainID)} (context_name)" +
                " VALUES (?)" +
                " ON CONFLICT (context_name) DO UPDATE SET context_name = EXCLUDED.context_name" +
                " RETURNING context_id"
        return queryRunner.query(ctx.conn, sql, longRes, moduleName).toLong()
    }

    override fun getSnapshotContextId(ctx: EContext, moduleName: String): Long {
        val sql = "SELECT context_id FROM ${tableSnapshotContexts(ctx.chainID)} WHERE context_name = ?"
        return queryRunner.query(ctx.conn, sql, longRes, moduleName).toLong()
    }

    override fun getSnapshotModuleContextIds(ctx: EContext): List<Long> {
        val sql = "SELECT context_id FROM ${tableSnapshotContexts(ctx.chainID)}"
        return queryRunner.query(ctx.conn, sql, ColumnListHandler())
    }

    override fun getSnapshotContextModule(ctx: EContext, contextId: Long): String {
        val sql = "SELECT context_name FROM ${tableSnapshotContexts(ctx.chainID)} WHERE context_id = ?"
        return queryRunner.query(ctx.conn, sql, stringRes, contextId)
    }

    override fun insertUpdatedDatum(ctx: EContext, contextId: Long, datumInfo: DatumInfo) {
        val sql = "INSERT INTO ${tableSnapshotUpdatedDatum(ctx.chainID)} (context_id, datum_id, datum_hash, datum) VALUES (?, ?, ?, ?)" +
                " ON CONFLICT (context_id, datum_id) DO UPDATE SET datum_hash = ?, datum = ?"
        queryRunner.update(ctx.conn, sql, contextId, datumInfo.id, datumInfo.hash, datumInfo.rawValue, datumInfo.hash, datumInfo.rawValue)
    }

    override fun insertUpdatedDatum(ctx: EContext, contextId: Long, datumInfoList: List<DatumInfo>) {
        if (datumInfoList.isEmpty()) return
        val sql = "INSERT INTO ${tableSnapshotUpdatedDatum(ctx.chainID)} (context_id, datum_id, datum_hash, datum) VALUES (?, ?, ?, ?)" +
                " ON CONFLICT (context_id, datum_id) DO UPDATE SET datum_hash = ?, datum = ?"
        ctx.conn.prepareStatement(sql).use { ps ->
            for (datumInfo in datumInfoList) {
                ps.setLong(1, contextId)
                ps.setLong(2, datumInfo.id)
                ps.setBytes(3, datumInfo.hash)
                ps.setBytes(4, datumInfo.rawValue)
                ps.setBytes(5, datumInfo.hash)
                ps.setBytes(6, datumInfo.rawValue)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    override fun getUpdatedDatumsByContext(ctx: EContext): Map<Long, List<DatumInfo>> {
        val sql = "SELECT context_id, datum_id, datum_hash, datum FROM ${tableSnapshotUpdatedDatum(ctx.chainID)}"
        val results = queryRunner.query(ctx.conn, sql, mapListHandler)

        return results.groupBy(
                { it["context_id"] as Long },
                {
                    DatumInfo(
                            it["datum_id"] as Long,
                            it["datum_hash"] as ByteArray,
                            it["datum"] as ByteArray?
                    )
                }
        )
    }

    override fun clearUpdatedDatums(ctx: EContext) {
        val sql = "DELETE FROM ${tableSnapshotUpdatedDatum(ctx.chainID)}"

        queryRunner.update(ctx.conn, sql)
    }
}

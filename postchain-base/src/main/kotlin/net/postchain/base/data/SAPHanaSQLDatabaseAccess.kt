package net.postchain.base.data

import net.postchain.base.data.SAPQueryExecutor.executeByteArrayQuery
import net.postchain.core.EContext
import net.postchain.core.TxDetail

class SAPHanaSQLDatabaseAccess(sqlCommands: SQLCommands) : SQLDatabaseAccess(sqlCommands) {

    override fun getBlockHeader(ctx: EContext, blockRID: ByteArray): ByteArray {
        return executeByteArrayQuery(
                ctx,
                "SELECT block_header_data FROM blocks where chain_iid = ? and block_rid = ?") {
            it.setLong(1, ctx.chainID)
            it.setBytes(2, blockRID)
        } ?: byteArrayOf()
    }

    override fun getBlockTransactions(ctx: EContext, blockRID: ByteArray, hashesOnly: Boolean): List<TxDetail> {
        val sql = """
            SELECT tx_rid, tx_hash${if (hashesOnly) "" else ", tx_data"}
            FROM transactions t
            JOIN blocks b ON t.block_iid=b.block_iid
            WHERE b.chain_iid = ? AND b.block_rid = ?
            ORDER BY tx_iid"""

        val statement = ctx.conn.prepareStatement(sql)
                .apply {
                    setLong(1, ctx.chainID)
                    setBytes(2, blockRID)
                }

        val resultSet = statement.executeQuery()

        val txs = mutableListOf<TxDetail>()
        while (resultSet.next()) {
            txs.add(TxDetail(
                    resultSet.getBlob(1).binaryStream.readBytes(),
                    resultSet.getBlob(2).binaryStream.readBytes(),
                    if (hashesOnly) null else resultSet.getBlob(3).binaryStream.readBytes()
            ))
        }

        return txs
    }

    override fun getWitnessData(ctx: EContext, blockRID: ByteArray): ByteArray {
        return executeByteArrayQuery(
                ctx,
                "SELECT block_witness FROM blocks WHERE chain_iid = ? AND block_rid = ?") {
            it.setLong(1, ctx.chainID)
            it.setBytes(2, blockRID)
        } ?: byteArrayOf()
    }

    override fun getTxBytes(ctx: EContext, txRID: ByteArray): ByteArray? {
        return executeByteArrayQuery(
                ctx,
                "SELECT tx_data FROM transactions WHERE chain_iid=? AND tx_rid=?") {
            it.setLong(1, ctx.chainID)
            it.setBytes(2, txRID)
        } ?: byteArrayOf()
    }

    override fun getConfigurationData(context: EContext, height: Long): ByteArray? {
        return executeByteArrayQuery(
                context,
                "SELECT configuration_data FROM configurations WHERE chain_iid = ? AND height = ?") {
            it.setLong(1, context.chainID)
            it.setLong(2, height)
        }
    }
}
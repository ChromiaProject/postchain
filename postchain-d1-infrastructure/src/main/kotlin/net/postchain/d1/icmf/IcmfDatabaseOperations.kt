package net.postchain.d1.icmf

import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.core.EContext
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import org.apache.commons.dbutils.handlers.ScalarHandler

object IcmfDatabaseOperations {
    const val PREFIX: String = "sys.x.icmf" // This name should not clash with Rell
    fun DatabaseAccess.tableAnchorHeight(ctx: EContext) = tableName(ctx, "${PREFIX}.anchor_height")
    fun DatabaseAccess.tableMessageHeight(ctx: EContext) = tableName(ctx, "${PREFIX}.message_height")

    fun initialize(ctx: EContext) {
        DatabaseAccess.of(ctx).apply {
            val queryRunner = QueryRunner()
            queryRunner.update(
                    ctx.conn,
                    "CREATE TABLE IF NOT EXISTS ${
                        tableAnchorHeight(ctx)
                    } (cluster TEXT PRIMARY KEY, height BIGINT NOT NULL)"
            )
            queryRunner.update(
                    ctx.conn,
                    "CREATE TABLE IF NOT EXISTS ${
                        tableMessageHeight(ctx)
                    } (sender BYTEA, topic TEXT, height BIGINT NOT NULL, PRIMARY KEY (sender, topic))"
            )
        }
    }

    fun loadLastAnchoredHeight(ctx: EContext, clusterName: String): Long = DatabaseAccess.of(ctx).run {
        QueryRunner().query(
                ctx.conn,
                "SELECT height FROM ${tableAnchorHeight(ctx)} WHERE cluster = ?",
                ScalarHandler(),
                clusterName
        ) ?: -1
    }

    fun saveLastAnchoredHeight(bctx: EContext, clusterName: String, anchorHeight: Long) {
        DatabaseAccess.of(bctx).run {
            QueryRunner().update(
                    bctx.conn,
                    "INSERT INTO ${tableAnchorHeight(bctx)} (cluster, height) VALUES (?, ?) ON CONFLICT (cluster) DO UPDATE SET height = ?",
                    clusterName,
                    anchorHeight,
                    anchorHeight
            )
        }
    }

    fun loadAllLastMessageHeights(ctx: EContext): List<MessageHeightForSender> = DatabaseAccess.of(ctx).run {
        QueryRunner().query(
                ctx.conn,
                "SELECT sender, topic, height FROM ${tableMessageHeight(ctx)}",
                MapListHandler()
        )
    }.map { MessageHeightForSender(BlockchainRid(it["sender"] as ByteArray), it["topic"] as String, it["height"] as Long) }

    fun loadLastMessageHeight(ctx: EContext, sender: BlockchainRid, topic: String): Long = DatabaseAccess.of(ctx).run {
        QueryRunner().query(
                ctx.conn,
                "SELECT height FROM ${tableMessageHeight(ctx)} WHERE sender = ? AND topic = ?",
                ScalarHandler(),
                sender.data,
                topic
        )
    } ?: -1

    fun saveLastMessageHeight(ctx: EContext, sender: BlockchainRid, topic: String, height: Long) {
        DatabaseAccess.of(ctx).run {
            QueryRunner().update(
                    ctx.conn,
                    "INSERT INTO ${tableMessageHeight(ctx)} (sender, topic, height) VALUES (?, ?, ?) ON CONFLICT (sender, topic) DO UPDATE SET height = ?",
                    sender.data,
                    topic,
                    height,
                    height
            )
        }
    }
}

data class MessageHeightForSender(
        val sender: BlockchainRid,
        val topic: String,
        val height: Long
)

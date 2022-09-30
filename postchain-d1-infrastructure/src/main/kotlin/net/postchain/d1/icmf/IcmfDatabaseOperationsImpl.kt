package net.postchain.d1.icmf

import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.core.EContext
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import org.apache.commons.dbutils.handlers.ScalarHandler

class IcmfDatabaseOperationsImpl : IcmfDatabaseOperations {

    companion object {
        const val PREFIX: String = "sys.x.icmf" // This name should not clash with Rell
    }

    private fun DatabaseAccess.tableAnchorHeight(ctx: EContext) = tableName(ctx, "${PREFIX}.anchor_height")
    private fun DatabaseAccess.tableMessageHeight(ctx: EContext) = tableName(ctx, "${PREFIX}.message_height")

    override fun initialize(ctx: EContext) {
        DatabaseAccess.of(ctx).apply {
            val queryRunner = QueryRunner()
            queryRunner.update(
                    ctx.conn,
                    "CREATE TABLE IF NOT EXISTS ${
                        tableAnchorHeight(ctx)
                    } (cluster TEXT, topic TEXT, height BIGINT NOT NULL, PRIMARY KEY (cluster, topic))"
            )
            queryRunner.update(
                    ctx.conn,
                    "CREATE TABLE IF NOT EXISTS ${
                        tableMessageHeight(ctx)
                    } (sender BYTEA, topic TEXT, height BIGINT NOT NULL, PRIMARY KEY (sender, topic))"
            )
        }
    }

    override fun loadLastAnchoredHeight(ctx: EContext, clusterName: String, topic: String): Long = DatabaseAccess.of(ctx).run {
        QueryRunner().query(
                ctx.conn,
                "SELECT height FROM ${tableAnchorHeight(ctx)} WHERE cluster = ? AND topic = ?",
                ScalarHandler(),
                clusterName,
                topic
        )
    } ?: -1

    override fun loadLastAnchoredHeights(ctx: EContext): List<AnchorHeight> = DatabaseAccess.of(ctx).run {
        QueryRunner().query(
                ctx.conn,
                "SELECT cluster, topic, height FROM ${tableAnchorHeight(ctx)}",
                MapListHandler()
        )
    }.map { AnchorHeight(it["cluster"] as String, it["topic"] as String, it["height"] as Long) }

    override fun saveLastAnchoredHeight(ctx: EContext, clusterName: String, topic: String, anchorHeight: Long) {
        DatabaseAccess.of(ctx).run {
            QueryRunner().update(
                    ctx.conn,
                    "INSERT INTO ${tableAnchorHeight(ctx)} (cluster, topic, height) VALUES (?, ?, ?) ON CONFLICT (cluster, topic) DO UPDATE SET height = ?",
                    clusterName,
                    topic,
                    anchorHeight,
                    anchorHeight
            )
        }
    }

    override fun loadAllLastMessageHeights(ctx: EContext): List<MessageHeightForSender> = DatabaseAccess.of(ctx).run {
        QueryRunner().query(
                ctx.conn,
                "SELECT sender, topic, height FROM ${tableMessageHeight(ctx)}",
                MapListHandler()
        )
    }.map { MessageHeightForSender(BlockchainRid(it["sender"] as ByteArray), it["topic"] as String, it["height"] as Long) }

    override fun loadLastMessageHeight(ctx: EContext, sender: BlockchainRid, topic: String): Long = DatabaseAccess.of(ctx).run {
        QueryRunner().query(
                ctx.conn,
                "SELECT height FROM ${tableMessageHeight(ctx)} WHERE sender = ? AND topic = ?",
                ScalarHandler(),
                sender.data,
                topic
        )
    } ?: -1

    override fun saveLastMessageHeight(ctx: EContext, sender: BlockchainRid, topic: String, height: Long) {
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

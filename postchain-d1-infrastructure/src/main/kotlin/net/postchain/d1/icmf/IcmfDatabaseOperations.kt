package net.postchain.d1.icmf

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler

const val PREFIX: String = "sys.x.icmf" // This name should not clash with Rell
fun DatabaseAccess.tableAnchorHeight(ctx: EContext) = tableName(ctx, "${PREFIX}.anchor_height")
fun DatabaseAccess.tableMessageHeight(ctx: EContext) = tableName(ctx, "${PREFIX}.message_height")

object IcmfDatabaseOperations {

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

    fun loadLastAnchoredHeight(ctx: EContext, clusterName: String): Long = DatabaseAccess.of(ctx).let {
        QueryRunner().query(
                ctx.conn,
                "SELECT height FROM ${it.tableAnchorHeight(ctx)} WHERE cluster = ?",
                ScalarHandler(),
                clusterName
        ) ?: -1
    }

    fun saveLastAnchoredHeight(bctx: BlockEContext, clusterName: String, anchorHeight: Long) {
        bctx.addAfterCommitHook {
            DatabaseAccess.of(bctx).apply {
                QueryRunner().update(
                        bctx.conn,
                        "INSERT INTO ${tableAnchorHeight(bctx)} (cluster, height) VALUES (?, ?) ON CONFLICT (cluster) DO UPDATE SET height = ?",
                        clusterName,
                        anchorHeight,
                        anchorHeight
                )
            }
        }
    }
}
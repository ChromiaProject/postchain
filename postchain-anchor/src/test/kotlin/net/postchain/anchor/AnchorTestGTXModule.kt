package net.postchain.anchor

import mu.KLogging
import net.postchain.core.EContext
import net.postchain.gtx.*
import org.apache.commons.dbutils.QueryRunner

private val r = QueryRunner()

/**
 * This module defines the "__anchor_block_header" operation and the main anchor table.
 *
 * NOTE:
 * In production this class will not exist, but instead be replaced by a corresponding Rell file.
 */
class AnchorTestGTXModule: SimpleGTXModule<Unit>(
    Unit, mapOf(
        AnchorSpecialTxExtension.OP_BLOCK_HEADER to :: AnchorTestOp
    ), mapOf()
) {

    companion object: KLogging() {
        const val PREFIX = "anchor" // This is what the Rell module would be called
    }

    /**
     * We need the "anchor_block" table (IRL it comes from Rell code)
     *
     * It just keep track of what blocks have been anchored
     */
    override fun initializeDB(ctx: EContext) {
        val moduleName = this::class.qualifiedName!!
        val version = GTXSchemaManager.getModuleVersion(ctx, moduleName)
        if (version == null) {
            val tableName = table_anchor_blocks(ctx)
            logger.info("About to create table: $tableName")
            val sql = """CREATE TABLE $tableName (
                |blockchain_rid TEXT NOT NULL, 
                |block_height BIGINT,
                |block_hash TEXT NOT NULL, 
                |status BIGINT)""".trimMargin()
            r.update(ctx.conn, sql)
            GTXSchemaManager.setModuleVersion(ctx, moduleName, 0)
        } else {
            logger.debug("No need to create table, since we have a version: $version")
        }
    }

}

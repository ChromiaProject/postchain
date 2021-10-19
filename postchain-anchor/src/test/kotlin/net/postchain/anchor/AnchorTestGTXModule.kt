package net.postchain.anchor

import mu.KLogging
import net.postchain.common.toHex
import net.postchain.core.EContext
import net.postchain.core.ProgrammerMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvNull
import net.postchain.gtx.*
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler

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
    ), mapOf(
        "get_last_anchored_block" to ::getLastBlockForChainRid,
        "get_anchored_block_at_height" to ::getBlockAtHeight
    )
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
                blockchain_rid TEXT NOT NULL, 
                block_rid TEXT NOT NULL, 
                block_height BIGINT,
                status INT)""".trimMargin()
            r.update(ctx.conn, sql)
            GTXSchemaManager.setModuleVersion(ctx, moduleName, 0)
        } else {
            logger.debug("No need to create table, since we have a version: $version")
        }
    }

}

/**
 * @param config (not used)
 * @param ctx - to get to the DB
 * @param args - must have the blockchain RID in it
 * @return the last block we've anchored for a given chain, with some info about height etc
 */
fun getLastBlockForChainRid(config: Unit, ctx: EContext, args: Gtv): Gtv {

    val argsDict = args as GtvDictionary
    val bcRidByte = argsDict["blockchainRid"]!!.asByteArray()
    val bcRidStr = bcRidByte.toHex() // Convert it to string representation since we use that in the db

    val tableName = table_anchor_blocks(ctx)

    val sql = """SELECT  
     block_rid,              
     block_height, 
     status
     FROM $tableName 
     WHERE blockchain_rid = ?
     ORDER BY block_height DESC 
     LIMIT 1 """

    val resultHandler = MapListHandler()

    val res = r.query(ctx.conn, sql, resultHandler, bcRidStr)
    return when (res.size) {
        1 -> {
            val lastBlock = res[0]
            buildGtvReply(lastBlock)
        }
        else -> GtvNull // Meaning we didn't find anything
    }
}

/**
 * @param config (not used)
 * @param ctx - to get to the DB
 * @param args - must have the blockchain RID and height in it
 * @return the block we've anchored at the givet height, with some info about height etc
 */
fun getBlockAtHeight(config: Unit, ctx: EContext, args: Gtv): Gtv {

    val argsDict = args as GtvDictionary
    val bcRidByte = argsDict["blockchainRid"]!!.asByteArray()
    val bcRidStr = bcRidByte.toHex() // Convert it to string representation since we use that in the db
    val height = argsDict["height"]!!.asInteger()

    val tableName = table_anchor_blocks(ctx)

    val sql = """SELECT 
     block_rid,              
     block_height,
     status,
     FROM $tableName 
     WHERE blockchain_rid = ?
     AND block_height = ? """

    val resultHandler = MapListHandler()

    val res = r.query(ctx.conn, sql, resultHandler, bcRidStr, height)
    return when (res.size) {
        1 -> {
            val foundBlock = res[0]
            buildGtvReply(foundBlock)
        }
        0 -> GtvNull // Meaning we didn't find anything
        else -> throw ProgrammerMistake("We cannot have multiple block anchored at height $height for blockchain $bcRidStr!")
    }
}

private fun buildGtvReply(
    foundBlock: MutableMap<String, Any>
) = GtvFactory.gtv(
    mutableMapOf(
        "block_rid" to GtvFactory.gtv(foundBlock["block_rid"] as String),
        "block_height" to GtvFactory.gtv(foundBlock["block_height"] as Long),
        "status" to GtvFactory.gtv(foundBlock["status"] as Long)
    )
)

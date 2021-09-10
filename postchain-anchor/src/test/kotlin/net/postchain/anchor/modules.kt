package net.postchain.anchor

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.GTXSchemaManager
import net.postchain.gtx.SimpleGTXModule
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import java.math.BigInteger


/**
 * This file holds helper classes only used for testing.
 */

private val r = QueryRunner()

private val mapListHandler = MapListHandler()

private fun table_anchor_event(ctx: EContext): String {
    val db = DatabaseAccess.of(ctx)
    return db.tableName(ctx, "eth_events")
}

/**
 * The "real" operation (operation that will be used in production) will be defined in Rell.
 *
 * To make testing easier, we define it here as a Kotlin class.
 */
class AnchorEventOp(u: Unit, opdata: ExtOpData) : GTXOperation(opdata) {

    override fun isCorrect(): Boolean {
        if (data.args.size != 2) return false
        return true
    }

    /**
     * When we "apply" the operation, we fire an event using the built in "emitEvent()" method on the [TxEContext] class.
     * The corresponding action in Rell is:
     *
     *   op_context.emit_event("x_event", x_event_data.to_gtv());
     */
    override fun apply(ctx: TxEContext): Boolean {
        ctx.emitEvent(
            "anchor_event",
            gtv(
                GtvInteger(data.args[0].asInteger()),
                GtvByteArray(data.args[1].asByteArray())
            )
        )

        return true
    }
}

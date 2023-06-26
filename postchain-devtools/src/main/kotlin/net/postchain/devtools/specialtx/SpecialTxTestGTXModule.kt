package net.postchain.devtools.specialtx

import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.special.GTXAutoSpecialTxExtension

/**
 * Simplest possible module that uses the special operations in [GTXAutoSpecialTxExtension].
 *
 * Module includes these operations:
 * 1. one "empty" operation (seems silly to not have any other "normal" operation)
 * 2. special begin block operation from Auto Ext
 * 3. special end block operation from Auto Ext
 */
class SpecialTxTestGTXModule : SimpleGTXModule<Unit>(Unit,
    mapOf(
        "gtx_empty" to ::GtxEmptyOp,
        GTXAutoSpecialTxExtension.OP_BEGIN_BLOCK to ::GtxBeginOp,
        GTXAutoSpecialTxExtension.OP_END_BLOCK to ::GtxEndOp
    ),
    mapOf(
            "gtx_empty_get_value" to { _, _, _ ->
                GtvNull // Idea: Get something here maybe?
            },
            "__begin_block_get" to { _, _, _ ->
                GtvNull // Idea: Get something here maybe?
            },
            "__end_block_get" to { _, _, _ ->
                GtvNull // Idea: Get something here maybe?
            }
    )


) {
    override fun initializeDB(ctx: EContext) {
        // Nothing yet
    }
}

// TODO: This is a bit crude, we might to want to DO something here?

open class GtxEmptyOp(@Suppress("UNUSED_PARAMETER") u: Unit, opdata: ExtOpData) : GTXOperation(opdata) {
    override fun checkCorrectness() {}

    override fun apply(ctx: TxEContext): Boolean {
        return true
    }
}

class GtxBeginOp(u: Unit, opdata: ExtOpData) : GtxEmptyOp(u, opdata)

class GtxEndOp(u: Unit, opdata: ExtOpData) : GtxEmptyOp(u, opdata)
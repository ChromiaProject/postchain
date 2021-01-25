package net.postchain.devtools.l2

import net.postchain.base.l2.L2TxEContext
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.SimpleGTXModule

class L2EventOp(u: Unit, opdata: ExtOpData) : GTXOperation(opdata) {

    override fun isCorrect(): Boolean {
        if (data.args.size != 2) return false
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        L2TxEContext.emitL2Event(ctx,
            gtv(
                GtvInteger(data.args[0].asInteger()),
                GtvByteArray(data.args[1].asByteArray())
            )
        )
        return true
    }
}

class L2StateOp(u: Unit, opdata: ExtOpData) : GTXOperation(opdata) {

    override fun isCorrect(): Boolean {
        if (data.args.size != 3) return false
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        L2TxEContext.emitL2AccountState(ctx, data.args[0].asInteger(),
            gtv(
                GtvInteger(data.args[1].asInteger()),
                GtvByteArray(data.args[2].asByteArray())
            )
        )
        return true
    }
}

class L2TestModule : SimpleGTXModule<Unit>(Unit,
    mapOf(
        "l2_event" to ::L2EventOp,
        "l2_state" to ::L2StateOp
    ),
    mapOf()
) {
    override fun initializeDB(ctx: EContext) {}
}
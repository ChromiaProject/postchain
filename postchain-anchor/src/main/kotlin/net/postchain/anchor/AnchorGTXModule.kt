package net.postchain.anchor

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.icmf.IcmfBBBExtension
import net.postchain.common.data.KECCAK256
import net.postchain.common.hexStringToByteArray
import net.postchain.core.EContext
import net.postchain.gtv.*
import net.postchain.gtx.GTXSpecialTxExtension
import net.postchain.gtx.SimpleGTXModule

class AnchorGTXModule : SimpleGTXModule<Unit>(
    Unit, mapOf(), mapOf(
        "get_anchor_height" to ::anchorHeightQuery,
        "get_anchor_chains" to ::anchorChainsQuery
    )
) {

    companion object {
        const val PREFIX = "anchor"
    }

    /**
     *
     */
    override fun initializeDB(ctx: EContext) {
        val dba = DatabaseAccess.of(ctx)
        dba.createEventLeafTable(ctx, PREFIX)
    }

    /**
     * We need extensions to make anchoring work, especially we need to listen to ICMF messages.
     */
    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        return listOf(IcmfBBBExtension())
    }

    /**
     * We need to write our own special type of operation for each header message we get.
     * That's the responsibility of [AnchorSpecialTxExtension]
     */
    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> {
        return listOf(AnchorSpecialTxExtension())
    }
}

fun anchorHeightQuery(config: Unit, ctx: EContext, args: Gtv): Gtv {
    /*
    val argsDict = args as GtvDictionary
    val blockHeight = argsDict["blockHeight"]!!.asInteger()
    val eventHash = argsDict["eventHash"]!!.asString().hexStringToByteArray()
    val db = DatabaseAccess.of(ctx)
    val blockHeader = GtvEncoder.simpleEncodeGtv(blockHeaderData(db, ctx, blockHeight))
    val blockWitness = blockWitnessData(db, ctx, blockHeight)
    val eventInfo = db.getEvent(ctx, AnchorGTXModule.PREFIX, blockHeight, eventHash) ?: return GtvNull
    val eventData = eventData(eventInfo)
    return GtvFactory.gtv(
        "eventData" to eventData,
        "blockHeader" to gtv(blockHeader),
        "blockWitness" to blockWitness
    ) */
    return GtvNull
}

fun anchorChainsQuery(config: Unit, ctx: EContext, args: Gtv): Gtv {
    /*
    val argsDict = args as GtvDictionary
    val blockHeight = argsDict["blockHeight"]!!.asInteger()
    val accountNumber = argsDict["accountNumber"]!!.asInteger()
    val db = DatabaseAccess.of(ctx)
    val blockHeader = GtvEncoder.simpleEncodeGtv(blockHeaderData(db, ctx, blockHeight))
    val blockWitness = blockWitnessData(db, ctx, blockHeight)
    return GtvFactory.gtv(
        "chains" to accountState,
        "blockHeader" to gtv(blockHeader),
        "blockWitness" to blockWitness
    )
     */
    return GtvNull
}

private fun eventData(event: DatabaseAccess.EventInfo?): Gtv {
    if (event == null) return GtvNull
    return GtvFactory.gtv(
        GtvFactory.gtv(event.blockHeight),
        GtvFactory.gtv(event.pos),
        GtvFactory.gtv(event.hash),
        GtvFactory.gtv(event.data)
    )
}

private fun accountState(state: DatabaseAccess.AccountState?): Gtv {
    if (state == null) return GtvNull
    return GtvFactory.gtv(
        GtvFactory.gtv(state.blockHeight),
        GtvFactory.gtv(state.stateN),
        GtvFactory.gtv(state.data)
    )
}


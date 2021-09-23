package net.postchain.anchor

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.icmf.IcmfBBBExtension
import net.postchain.base.snapshot.LeafStore
import net.postchain.common.data.Hash
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.gtv.*
import net.postchain.gtv.merkle.GtvBinaryTreeFactory
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.GTXSpecialTxExtension
import net.postchain.gtx.SimpleGTXModule

/**
 * This module allows external parties to ask Anchor chain about what heights been anchored.
 *
 * Note regarding modules:
 * We write this module as a complement to the "anchor" module that is written in Rell.
 * The Rell module define the "__anchor_block_header" operation for example, it is not known by this module.
 */
class AnchorGTXModule : SimpleGTXModule<Unit>(
    Unit, mapOf(), mapOf(
        "get_anchor_height" to ::anchorHeightQuery,
        "get_anchor_chains" to ::anchorChainsQuery
    )
) {

    companion object {
        const val PREFIX: String = "sys.x.anchor" // This name should not clash with the Rell "anchor" module
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


// TODO: Olle: impl
fun anchorHeightQuery(config: Unit, ctx: EContext, args: Gtv): Gtv {
    val db = DatabaseAccess.of(ctx)
    /*
    val argsDict = args as GtvDictionary
    val blockHeight = argsDict["blockHeight"]!!.asInteger()
    val eventHash = argsDict["eventHash"]!!.asString().hexStringToByteArray()
    val blockHeader = GtvEncoder.simpleEncodeGtv(blockHeaderData(db, ctx, blockHeight))

    // Go to DB
    val blockWitness = blockWitnessData(db, ctx, blockHeight)

    // Go to DB
    val eventInfo = db.getEvent(ctx, AnchorGTXModule.PREFIX, blockHeight, eventHash) ?: return GtvNull
    val eventData = eventData(eventInfo)
    return GtvFactory.gtv(
        "eventData" to eventData,
        "blockHeader" to gtv(blockHeader),
        "blockWitness" to blockWitness
    ) */
    return GtvNull
}

// TODO: Olle: impl
fun anchorChainsQuery(config: Unit, ctx: EContext, args: Gtv): Gtv {
    val db = DatabaseAccess.of(ctx)
    /*
    val argsDict = args as GtvDictionary
    val blockHeight = argsDict["blockHeight"]!!.asInteger()
    val accountNumber = argsDict["accountNumber"]!!.asInteger()
    val blockHeader = GtvEncoder.simpleEncodeGtv(blockHeaderData(db, ctx, blockHeight))

    // Go to DB
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


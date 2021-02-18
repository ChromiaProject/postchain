package net.postchain.gtx

import net.postchain.base.snapshot.EventPageStore
import net.postchain.common.data.KECCAK256
import net.postchain.core.EContext
import net.postchain.crypto.EthereumL2DigestSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv

class L2OpsGTXModule : SimpleGTXModule<Unit>(Unit, mapOf(), mapOf(
    "event_merkle_proof" to ::eventMerkleProofQuery,
    "account_state_merkle_proof" to ::accountStateMerkleProofQuery
)
) {
    override fun initializeDB(ctx: EContext) {}
}

fun eventMerkleProofQuery(config: Unit, ctx: EContext, args: Gtv): Gtv {
    val event = EventPageStore(ctx, 2, EthereumL2DigestSystem(KECCAK256))
    val argsDict = args as GtvDictionary
    val blockHeight = argsDict["blockHeight"]!!.asInteger()
    val eventHash = argsDict["eventHash"]!!.asByteArray()
    val pos = event.getEventPosition(blockHeight, eventHash)
    val proofs = event.getMerkleProof(blockHeight, pos)
    val gtvProofs = proofs.map { gtv(it) }
    return gtv(
        "proof" to GtvArray(gtvProofs.toTypedArray())
    )
}

fun accountStateMerkleProofQuery(config: Unit, ctx: EContext, args: Gtv): Gtv {
    TODO()
}

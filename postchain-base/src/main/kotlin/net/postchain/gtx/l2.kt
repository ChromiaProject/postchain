package net.postchain.gtx

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.snapshot.EventPageStore
import net.postchain.base.snapshot.SnapshotPageStore
import net.postchain.common.data.KECCAK256
import net.postchain.core.EContext
import net.postchain.crypto.EthereumL2DigestSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull

class L2OpsGTXModule : SimpleGTXModule<Unit>(Unit, mapOf(), mapOf(
    "get_event_merkle_proof" to ::eventMerkleProofQuery,
    "get_account_state_merkle_proof" to ::accountStateMerkleProofQuery
)
) {
    override fun initializeDB(ctx: EContext) {}
}

fun eventMerkleProofQuery(config: Unit, ctx: EContext, args: Gtv): Gtv {
    val argsDict = args as GtvDictionary
    val blockHeight = argsDict["blockHeight"]!!.asInteger()
    val eventHash = argsDict["eventHash"]!!.asByteArray()
    val db = DatabaseAccess.of(ctx)
    val eventInfo = db.getEvent(ctx, blockHeight, eventHash) ?: return GtvNull
    val event = EventPageStore(ctx, 2, EthereumL2DigestSystem(KECCAK256))
    val proofs = event.getMerkleProof(blockHeight, eventInfo.pos)
    val gtvProofs = proofs.map { gtv(it) }
    return gtv(
        "merkleProofs" to GtvArray(gtvProofs.toTypedArray())
    )
}

fun accountStateMerkleProofQuery(config: Unit, ctx: EContext, args: Gtv): Gtv {
    val argsDict = args as GtvDictionary
    val blockHeight = argsDict["blockHeight"]!!.asInteger()
    val accountNumber = argsDict["accountNumber"]!!.asInteger()
    val snapshot = SnapshotPageStore(ctx, 2, EthereumL2DigestSystem(KECCAK256))
    val proofs = snapshot.getMerkleProof(blockHeight, accountNumber)
    val gtvProofs = proofs.map { gtv(it) }
    return gtv(
        "merkleProofs" to GtvArray(gtvProofs.toTypedArray())
    )
}

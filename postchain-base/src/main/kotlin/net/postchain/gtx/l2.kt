package net.postchain.gtx

import net.postchain.base.BaseBlockHeader
import net.postchain.base.BaseBlockWitness
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.encodeSignatureWithV
import net.postchain.base.snapshot.EventPageStore
import net.postchain.base.snapshot.SnapshotPageStore
import net.postchain.common.data.KECCAK256
import net.postchain.core.EContext
import net.postchain.core.MultiSigBlockWitness
import net.postchain.crypto.EthereumL2DigestSystem
import net.postchain.gtv.*
import net.postchain.gtv.GtvFactory.gtv

class L2OpsGTXModule : SimpleGTXModule<Unit>(
    Unit, mapOf(), mapOf(
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
    val blockHeader = blockHeaderData(db, ctx, blockHeight)
    val blockWitness = blockWitnessData(db, ctx, blockHeight)
    val eventInfo = db.getEvent(ctx, blockHeight, eventHash) ?: return GtvNull
    val eventData = eventData(eventInfo)
    val event = EventPageStore(ctx, 2, EthereumL2DigestSystem(KECCAK256))
    val proofs = event.getMerkleProof(blockHeight, eventInfo.pos)
    val gtvProofs = proofs.map { gtv(it) }.toTypedArray()
    return gtv(
        "eventData" to eventData,
        "blockHeader" to blockHeader,
        "blockWitness" to blockWitness,
        "merkleProofs" to GtvArray(gtvProofs)
    )
}

fun accountStateMerkleProofQuery(config: Unit, ctx: EContext, args: Gtv): Gtv {
    val argsDict = args as GtvDictionary
    val blockHeight = argsDict["blockHeight"]!!.asInteger()
    val accountNumber = argsDict["accountNumber"]!!.asInteger()
    val db = DatabaseAccess.of(ctx)
    val blockHeader = blockHeaderData(db, ctx, blockHeight)
    val blockWitness = blockWitnessData(db, ctx, blockHeight)
    val accountState = accountState(db.getAccountState(ctx, blockHeight, accountNumber))
    val snapshot = SnapshotPageStore(ctx, 2, EthereumL2DigestSystem(KECCAK256))
    val proofs = snapshot.getMerkleProof(blockHeight, accountNumber)
    val gtvProofs = proofs.map { gtv(it) }.toTypedArray()
    return gtv(
        "accountState" to accountState,
        "blockHeader" to blockHeader,
        "blockWitness" to blockWitness,
        "merkleProofs" to GtvArray(gtvProofs)
    )
}

private fun eventData(event: DatabaseAccess.EventInfo?): Gtv {
    if (event == null) return GtvNull
    return gtv(gtv(event.blockHeight), gtv(event.pos), gtv(event.hash), gtv(event.data))
}

private fun accountState(state: DatabaseAccess.AccountState?): Gtv {
    if (state == null) return GtvNull
    return gtv(gtv(state.blockHeight), gtv(state.stateN), gtv(state.data))
}

private fun blockHeaderData(
    db: DatabaseAccess,
    ctx: EContext,
    blockHeight: Long
): Gtv {
    val blockRid = db.getBlockRID(ctx, blockHeight) ?: return GtvNull
    return BaseBlockHeader(db.getBlockHeader(ctx, blockRid), SECP256K1CryptoSystem()).blockHeaderRec.toGtv()
}

private fun blockWitnessData(
    db: DatabaseAccess,
    ctx: EContext,
    blockHeight: Long
): Gtv {
    val blockRid = db.getBlockRID(ctx, blockHeight) ?: return GtvNull
    val witness = BaseBlockWitness.fromBytes(db.getWitnessData(ctx, blockRid)) as MultiSigBlockWitness
    val signatures = witness.getSignatures().map {
        gtv(
            "sig" to GtvByteArray(encodeSignatureWithV(blockRid, it.subjectID, it.data)),
            "pubkey" to GtvByteArray(it.subjectID)
        )
    }
    return GtvArray(signatures.toTypedArray())
}

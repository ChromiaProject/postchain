// Copyright (c) 2021 ChromaWay AB. See README for license information.

package net.postchain.el2

import net.postchain.base.*
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.snapshot.EventPageStore
import net.postchain.base.snapshot.SimpleDigestSystem
import net.postchain.base.snapshot.SnapshotPageStore
import net.postchain.common.data.KECCAK256
import net.postchain.common.data.SHA256
import net.postchain.common.hexStringToByteArray
import net.postchain.core.EContext
import net.postchain.core.MultiSigBlockWitness
import net.postchain.gtv.*
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkle.MerkleTree
import net.postchain.gtx.GTXSpecialTxExtension
import net.postchain.gtx.SimpleGTXModule

const val PREFIX: String = "sys.x.el2"
const val EL2: String = "el2"

class EL2GTXModule : SimpleGTXModule<Unit>(
    Unit, mapOf(), mapOf(
        "get_event_merkle_proof" to ::eventMerkleProofQuery,
        "get_account_state_merkle_proof" to ::accountStateMerkleProofQuery
    )
) {

    override fun initializeDB(ctx: EContext) {
        val dba = DatabaseAccess.of(ctx)
        dba.createPageTable(ctx,"${PREFIX}_event")
        dba.createPageTable(ctx,"${PREFIX}_snapshot")
        dba.createEventLeafTable(ctx, PREFIX)
        dba.createStateLeafTable(ctx, PREFIX)
    }

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        return listOf(EthereumL2Implementation(SimpleDigestSystem(KECCAK256), 2))
    }

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> {
        return listOf(EL2SpecialTxExtension())
    }

}

fun eventMerkleProofQuery(config: Unit, ctx: EContext, args: Gtv): Gtv {
    val argsDict = args as GtvDictionary
    val eventHash = argsDict["eventHash"]!!.asString().hexStringToByteArray()
    val db = DatabaseAccess.of(ctx)
    val eventInfo = db.getEvent(ctx, PREFIX, eventHash) ?: return GtvNull
    val blockHeight = eventInfo.blockHeight
    val bh = blockHeaderData(db, ctx, blockHeight)
    val blockHeader = GtvEncoder.simpleEncodeGtv(bh)
    val blockWitness = blockWitnessData(db, ctx, blockHeight)
    val eventProof = eventProof(ctx, blockHeight, eventInfo)
    val el2MerkleProof = el2MerkleProof(db, ctx, blockHeight)
    return gtv(
        "eventData" to gtv(eventInfo.data),
        "blockHeader" to gtv(blockHeader),
        "blockWitness" to blockWitness,
        "eventProof" to eventProof,
        "el2MerkleProof" to el2MerkleProof
    )
}

fun accountStateMerkleProofQuery(config: Unit, ctx: EContext, args: Gtv): Gtv {
    val argsDict = args as GtvDictionary
    val blockHeight = argsDict["blockHeight"]!!.asInteger()
    val accountNumber = argsDict["accountNumber"]!!.asInteger()
    val db = DatabaseAccess.of(ctx)
    val blockHeader = GtvEncoder.simpleEncodeGtv(blockHeaderData(db, ctx, blockHeight))
    val blockWitness = blockWitnessData(db, ctx, blockHeight)
    val accountState = accountState(db.getAccountState(ctx, PREFIX, blockHeight, accountNumber))
    val snapshot = SnapshotPageStore(ctx, 2, SimpleDigestSystem(KECCAK256))
    val proofs = snapshot.getMerkleProof(blockHeight, accountNumber)
    var gtvProofs: List<GtvByteArray> = listOf()
    for (proof in proofs) {
        gtvProofs = gtvProofs.plus(gtv(proof))
    }
    return gtv(
        "accountState" to accountState,
        "blockHeader" to gtv(blockHeader),
        "blockWitness" to blockWitness,
        "merkleProofs" to GtvArray(gtvProofs.toTypedArray())
    )
}

private fun eventProof(ctx: EContext, blockHeight: Long, event: DatabaseAccess.EventInfo?): Gtv {
    if (event == null) return GtvNull
    val es = EventPageStore(ctx, 2, SimpleDigestSystem(KECCAK256))
    val proofs = es.getMerkleProof(blockHeight, event.pos)
    var gtvProofs: List<GtvByteArray> = listOf()
    for (proof in proofs) {
        gtvProofs = gtvProofs.plus(gtv(proof))
    }
    return gtv(
        "leaf" to gtv(event.hash),
        "position" to gtv(event.pos),
        "merkleProofs" to gtv(gtvProofs)
    )
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
    val merkleHashCalculator = GtvMerkleHashCalculator(BlockchainRidFactory.cryptoSystem)
    val blockRid = db.getBlockRID(ctx, blockHeight) ?: return GtvNull
    val bh = BaseBlockHeader(db.getBlockHeader(ctx, blockRid), SECP256K1CryptoSystem()).blockHeaderRec
    return gtv(
        bh.gtvBlockchainRid,
        gtv(blockRid),
        bh.gtvPreviousBlockRid,
        gtv(bh.gtvMerkleRootHash.merkleHash(merkleHashCalculator)),
        gtv(bh.gtvTimestamp.merkleHash(merkleHashCalculator)),
        gtv(bh.gtvHeight.merkleHash(merkleHashCalculator)),
        gtv(bh.gtvDependencies.merkleHash(merkleHashCalculator)),
        gtv(bh.gtvExtra.merkleHash(merkleHashCalculator)),
    )
}

private fun el2MerkleProof(db: DatabaseAccess, ctx: EContext, blockHeight: Long): Gtv {
    val blockRid = db.getBlockRID(ctx, blockHeight) ?: return GtvNull
    val bh = BaseBlockHeader(db.getBlockHeader(ctx, blockRid), SECP256K1CryptoSystem()).blockHeaderRec
    val ds = SimpleDigestSystem(SHA256)
    val merkleTree = MerkleTree(bh.gtvExtra.asDict(), ds)
    val path = merkleTree.getMerklePath(EL2)
    val proofs = merkleTree.getMerkleProof(path)
    var gtvProofs: List<GtvByteArray> = listOf()
    for (proof in proofs) {
        gtvProofs = gtvProofs.plus(gtv(proof))
    }
    val el2Leaf = bh.gtvExtra[EL2]!!
    val el2HashedLeaf = ds.hash(
        ds.digest(encodeGtv(GtvString(EL2))),
        ds.digest(encodeGtv(el2Leaf))
    )
    return gtv(
        "el2Leaf" to gtv(el2Leaf),
        "el2HashedLeaf" to gtv(el2HashedLeaf),
        "el2Position" to gtv(path.toLong()),
        "extraRoot" to gtv(merkleTree.getMerkleRoot()),
        "extraMerkleProofs" to gtv(gtvProofs))
}

private fun blockWitnessData(
    db: DatabaseAccess,
    ctx: EContext,
    blockHeight: Long
): Gtv {
    val blockRid = db.getBlockRID(ctx, blockHeight) ?: return GtvNull
    val witness = BaseBlockWitness.fromBytes(db.getWitnessData(ctx, blockRid)) as MultiSigBlockWitness
    var sigs = listOf<Gtv>()
    val signatures = witness.getSignatures()
        for (s in signatures) {
            sigs = sigs.plus(gtv(
            "sig" to GtvByteArray(encodeSignatureWithV(blockRid, s.subjectID, s.data)),
            "pubkey" to GtvByteArray(getEthereumAddress(s.subjectID))
            )
        )
    }
    return GtvArray(sigs.toTypedArray())
}

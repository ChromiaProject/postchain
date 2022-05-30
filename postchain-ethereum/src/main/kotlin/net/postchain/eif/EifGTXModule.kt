// Copyright (c) 2021 ChromaWay AB. See README for license information.

package net.postchain.eif

import net.postchain.base.*
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.snapshot.EventPageStore
import net.postchain.base.snapshot.SimpleDigestSystem
import net.postchain.base.snapshot.SnapshotPageStore
import net.postchain.common.data.KECCAK256
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.core.EContext
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.encodeSignatureWithV
import net.postchain.gtv.*
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkle.MerkleBasics
import net.postchain.gtv.merkle.path.GtvPath
import net.postchain.gtv.merkle.path.GtvPathFactory
import net.postchain.gtv.merkle.path.GtvPathSet
import net.postchain.gtv.merkle.proof.MerkleProofElement
import net.postchain.gtv.merkle.proof.ProofHashedLeaf
import net.postchain.gtv.merkle.proof.ProofNode
import net.postchain.gtv.merkle.proof.ProofValueLeaf
import net.postchain.gtx.GTXSpecialTxExtension
import net.postchain.gtx.SimpleGTXModule
import org.spongycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.Security

const val PREFIX: String = "sys.x.eif"
const val EIF: String = "eif"

class EifGTXModule : SimpleGTXModule<Unit>(
    Unit, mapOf(), mapOf(
        "get_event_merkle_proof" to ::eventMerkleProofQuery,
        "get_account_state_merkle_proof" to ::accountStateMerkleProofQuery
    )
) {

    init {
        // We add this provider so that we can get keccak-256 message digest instances
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    override fun initializeDB(ctx: EContext) {
        DatabaseAccess.of(ctx).apply {
            createPageTable(ctx, "${PREFIX}_event")
            createPageTable(ctx, "${PREFIX}_snapshot")
            createEventLeafTable(ctx, PREFIX)
            createStateLeafTable(ctx, PREFIX)
        }
    }

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        return listOf(EthereumEifImplementation(SimpleDigestSystem(MessageDigest.getInstance(KECCAK256)), 2))
    }

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> {
        return listOf(EifSpecialTxExtension())
    }

}

fun eventMerkleProofQuery(config: Unit, ctx: EContext, args: Gtv): Gtv {
    val argsDict = args.asDict()
    val eventHash = argsDict["eventHash"]!!.asString().hexStringToByteArray()
    val db = DatabaseAccess.of(ctx)
    val eventInfo = db.getEvent(ctx, PREFIX, eventHash) ?: return GtvNull
    val blockHeight = eventInfo.blockHeight
    val bh = blockHeaderData(db, ctx, blockHeight)
    val blockHeader = SimpleGtvEncoder.encodeGtv(bh)
    val blockWitness = blockWitnessData(db, ctx, blockHeight)
    val eventProof = eventProof(ctx, blockHeight, eventInfo)
    val extraMerkleProof = extraMerkleProof(db, ctx, blockHeight)
    return gtv(
        "eventData" to gtv(eventInfo.data),
        "blockHeader" to gtv(blockHeader),
        "blockWitness" to blockWitness,
        "eventProof" to eventProof,
        "extraMerkleProof" to extraMerkleProof
    )
}

fun accountStateMerkleProofQuery(config: Unit, ctx: EContext, args: Gtv): Gtv {
    val argsDict = args.asDict()
    val blockHeight = argsDict["blockHeight"]!!.asInteger()
    val accountNumber = argsDict["accountNumber"]!!.asInteger()
    val db = DatabaseAccess.of(ctx)
    val blockHeader = SimpleGtvEncoder.encodeGtv(blockHeaderData(db, ctx, blockHeight))
    val blockWitness = blockWitnessData(db, ctx, blockHeight)
    val accountState = accountState(db.getAccountState(ctx, PREFIX, blockHeight, accountNumber))
    val snapshot = SnapshotPageStore(ctx, 2, SimpleDigestSystem(MessageDigest.getInstance(KECCAK256)), PREFIX)
    val proofs = snapshot.getMerkleProof(blockHeight, accountNumber)
    val gtvProofs = proofs.map(::gtv)
    val extraMerkleProof = extraMerkleProof(db, ctx, blockHeight)
    return gtv(
        "accountState" to accountState,
        "blockHeader" to gtv(blockHeader),
        "blockWitness" to blockWitness,
        "stateProofs" to GtvArray(gtvProofs.toTypedArray()),
        "extraMerkleProof" to extraMerkleProof
    )
}

private fun eventProof(ctx: EContext, blockHeight: Long, event: DatabaseAccess.EventInfo?): Gtv {
    if (event == null) return GtvNull
    val es = EventPageStore(ctx, 2, SimpleDigestSystem(MessageDigest.getInstance(KECCAK256)), PREFIX)
    val proofs = es.getMerkleProof(blockHeight, event.pos)
    val gtvProofs = proofs.map(::gtv)
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
    val bh = BaseBlockHeader(db.getBlockHeader(ctx, blockRid), Secp256K1CryptoSystem()).blockHeaderRec
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

private fun extraMerkleProof(db: DatabaseAccess, ctx: EContext, blockHeight: Long): Gtv {
    val cryptoSystem = Secp256K1CryptoSystem()
    val blockRid = db.getBlockRID(ctx, blockHeight) ?: return GtvNull
    val bh = BaseBlockHeader(db.getBlockHeader(ctx, blockRid), cryptoSystem).blockHeaderRec
    val gtvExtra = bh.gtvExtra
    val path: Array<Any> = arrayOf(EIF)
    val gtvPath: GtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
    val gtvPaths = GtvPathSet(setOf(gtvPath))
    val calculator = GtvMerkleHashCalculator(cryptoSystem)
    val extraProofTree = gtvExtra.generateProof(gtvPaths, calculator)
    val merkleProofs = getProofListAndPosition(extraProofTree.root)
    val proofs = merkleProofs.first
    val position = merkleProofs.second
    val gtvProofs = proofs.map(::gtv)
    val leaf = gtvExtra[EIF]!!
    val hashedLeaf = MerkleBasics.hashingFun(
        byteArrayOf(MerkleBasics.HASH_PREFIX_LEAF) + encodeGtv(leaf), cryptoSystem)
    return gtv(
        "leaf" to gtv(leaf),
        "hashedLeaf" to gtv(hashedLeaf),
        "position" to gtv(position),
        "extraRoot" to gtv(gtvExtra.merkleHash(calculator)),
        "extraMerkleProofs" to gtv(gtvProofs))
}

private fun blockWitnessData(
    db: DatabaseAccess,
    ctx: EContext,
    blockHeight: Long
): Gtv {
    val blockRid = db.getBlockRID(ctx, blockHeight) ?: return GtvNull
    val witness = BaseBlockWitness.fromBytes(db.getWitnessData(ctx, blockRid))
    val signatures = witness.getSignatures()
    return gtv(
        signatures.map {
            gtv(
                "sig" to GtvByteArray(encodeSignatureWithV(blockRid, it.subjectID, it.data)),
                "pubkey" to GtvByteArray(getEthereumAddress(it.subjectID))
            )
        }
    )
}

private fun getProofListAndPosition(tree: MerkleProofElement): Pair<List<ByteArray>, Long> {
    val proofs = mutableListOf<ByteArray>()
    var position = 0L
    var currentNode = tree

    while (true) {
        if (currentNode is ProofValueLeaf<*>) {
            break
        }

        val node = currentNode as ProofNode
        val left = node.left
        val right = node.right
        if (right is ProofHashedLeaf) {
            proofs.add(0, right.merkleHash)
            position *= 2
            currentNode = left
        } else if (left is ProofHashedLeaf) {
            proofs.add(0, left.merkleHash)
            position = 2 * position + 1
            currentNode = right
        } else {
            throw ProgrammerMistake("Expected one side to be ${ProofHashedLeaf::class.simpleName}" +
                    " but was left: ${left::class.simpleName} and right: ${right::class.simpleName}")
        }
    }

    return proofs to position
}
